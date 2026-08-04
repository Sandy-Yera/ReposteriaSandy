package com.sandyyera.reposteria.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.RecetaIngrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaDuracion
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.db.entidades.RecetaRendimiento
import com.sandyyera.reposteria.data.db.entidades.RecetaSeccion
import com.sandyyera.reposteria.data.db.entidades.RecetaSimulacionVenta
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import kotlinx.coroutines.flow.Flow

/** El costo de una receta, para poder pedir varios de una vez. */
data class CostoDeReceta(val recetaId: Long, val costo: Double)

/** Los trozos de una receta, para poder pedir varios de una vez. */
data class TrozosDeReceta(val recetaId: Long, val trozos: Int)

@Dao
interface RecetaDao {

    @Query("SELECT * FROM recetas ORDER BY titulo COLLATE NOCASE")
    fun observarTodas(): Flow<List<Receta>>

    @Query("SELECT * FROM recetas WHERE id = :recetaId")
    suspend fun obtener(recetaId: Long): Receta?

    /**
     * Una receta, avisando cuando cambia. **Es la que hay que usar para mostrarla.**
     *
     * Misma regla que dejó establecida `observarCostos`: *lo que se muestra se observa; la
     * foto de un momento es para calcular*. Acá se paga con el título — se renombra desde
     * dentro de la receta (8.4.1, #3) y el encabezado de los otros pasos tiene que enterarse
     * sin que nadie se acuerde de refrescarlo.
     */
    @Query("SELECT * FROM recetas WHERE id = :recetaId")
    fun observarReceta(recetaId: Long): Flow<Receta?>

    @Insert
    suspend fun insertar(receta: Receta): Long

    @Update
    suspend fun actualizar(receta: Receta)

    @Query("DELETE FROM recetas WHERE id = :recetaId")
    suspend fun eliminarPorId(recetaId: Long)

    // --- Costo ---

    /**
     * Lo que cuesta hacer una receta: cantidad por valor de cada ingrediente, sumado.
     *
     * La suma la hace la base en una sola consulta, en vez de recorrer sección por
     * sección desde Kotlin.
     *
     * El COALESCE es obligatorio: SUM sobre cero filas devuelve NULL en SQLite, no 0, y
     * una receta recién creada todavía no tiene ingredientes. El JOIN lee el valor del
     * ingrediente en este momento, que es lo que se quiere: los precios nunca quedan
     * congelados en la receta.
     */
    @Query(
        """
        SELECT COALESCE(SUM(ri.cantidadG * i.valorPorGramo), 0)
        FROM receta_ingredientes ri
        JOIN receta_secciones rs ON rs.id = ri.seccionId
        JOIN ingredientes i      ON i.id  = ri.ingredienteId
        WHERE rs.recetaId = :recetaId
        """
    )
    suspend fun costoTotalReceta(recetaId: Long): Double

    /**
     * Lo mismo pero para varias recetas de una vez, para la simulación múltiple.
     *
     * Ojo: una receta sin ingredientes **no aparece** en el resultado, porque no tiene
     * filas que agrupar. Quien la llame debe tomar las que falten como costo 0.
     */
    @Query(
        """
        SELECT rs.recetaId AS recetaId,
               COALESCE(SUM(ri.cantidadG * i.valorPorGramo), 0) AS costo
        FROM receta_ingredientes ri
        JOIN receta_secciones rs ON rs.id = ri.seccionId
        JOIN ingredientes i      ON i.id  = ri.ingredienteId
        WHERE rs.recetaId IN (:recetaIds)
        GROUP BY rs.recetaId
        """
    )
    suspend fun costoDeVariasRecetas(recetaIds: List<Long>): List<CostoDeReceta>

    /**
     * El costo de **todas** las recetas, y que avise sola cuando cambie.
     *
     * Existe por un bug real: la lista de recetas se armaba pidiendo los costos con
     * [costoDeVariasRecetas], una consulta de una sola vez, colgada del `Flow` de la tabla
     * `recetas`. Borrar un ingrediente no toca esa tabla, así que nada volvía a preguntar y
     * la lista seguía mostrando el costo que tenía antes — un número que ya no existía.
     *
     * Devolviendo un `Flow`, Room vigila las **tres** tablas que aparecen acá
     * (`receta_ingredientes`, `receta_secciones` e `ingredientes`) y vuelve a emitir en
     * cuanto cambia cualquiera. Cambiarle el precio a la harina reordena los costos de toda
     * la lista sin que nadie tenga que acordarse de pedirlo.
     *
     * No recibe ids a propósito: con ids habría que volver a suscribirse cada vez que se
     * crea o se borra una receta, que es justo el tipo de "acordarse" que causó el bug. Son
     * decenas de filas, no miles.
     *
     * Sigue valiendo la trampa del `GROUP BY`: una receta sin ingredientes **no aparece**.
     */
    @Query(
        """
        SELECT rs.recetaId AS recetaId,
               COALESCE(SUM(ri.cantidadG * i.valorPorGramo), 0) AS costo
        FROM receta_ingredientes ri
        JOIN receta_secciones rs ON rs.id = ri.seccionId
        JOIN ingredientes i      ON i.id  = ri.ingredienteId
        GROUP BY rs.recetaId
        """
    )
    fun observarCostos(): Flow<List<CostoDeReceta>>

    // --- Consultas que cruzan tablas ---

    /** Qué recetas usan un ingrediente. Alimenta la advertencia antes de borrarlo. */
    @Query(
        """
        SELECT DISTINCT r.* FROM recetas r
        JOIN receta_secciones rs    ON rs.recetaId = r.id
        JOIN receta_ingredientes ri ON ri.seccionId = rs.id
        WHERE ri.ingredienteId = :ingredienteId
        ORDER BY r.titulo COLLATE NOCASE
        """
    )
    suspend fun obtenerRecetasQueUsan(ingredienteId: Long): List<Receta>

    /** Qué recetas siguen enlazadas a un molde del catálogo. */
    @Query(
        """
        SELECT r.* FROM recetas r
        JOIN receta_rendimiento rr ON rr.recetaId = r.id
        WHERE rr.moldeOrigenId = :moldeId
        ORDER BY r.titulo COLLATE NOCASE
        """
    )
    suspend fun obtenerRecetasConMoldeOrigen(moldeId: Long): List<Receta>

    /**
     * Borra un ingrediente de todas las recetas donde aparezca.
     *
     * Hace falta a mano porque entre `receta_ingredientes` e `ingredientes` no hay clave
     * foránea declarada, así que no hay cascada que lo haga solo.
     */
    @Query(
        """
        DELETE FROM receta_ingredientes
        WHERE ingredienteId = :ingredienteId
        """
    )
    suspend fun quitarIngredienteDeTodasLasSecciones(ingredienteId: Long)

    // --- Secciones e ingredientes ---

    @Query("SELECT * FROM receta_secciones WHERE recetaId = :recetaId ORDER BY orden")
    suspend fun obtenerSecciones(recetaId: Long): List<RecetaSeccion>

    @Query("SELECT * FROM receta_secciones WHERE recetaId = :recetaId ORDER BY orden")
    fun observarSecciones(recetaId: Long): Flow<List<RecetaSeccion>>

    @Query("SELECT COUNT(*) FROM receta_secciones WHERE recetaId = :recetaId")
    suspend fun contarSecciones(recetaId: Long): Int

    @Insert
    suspend fun insertarSeccion(seccion: RecetaSeccion): Long

    @Update
    suspend fun actualizarSeccion(seccion: RecetaSeccion)

    @Query("DELETE FROM receta_secciones WHERE id = :seccionId")
    suspend fun eliminarSeccion(seccionId: Long)

    /** Todos los ingredientes de una receta, sin importar en qué sección estén. */
    @Query(
        """
        SELECT ri.* FROM receta_ingredientes ri
        JOIN receta_secciones rs ON rs.id = ri.seccionId
        WHERE rs.recetaId = :recetaId
        ORDER BY rs.orden, ri.orden
        """
    )
    suspend fun obtenerTodosLosIngredientes(recetaId: Long): List<RecetaIngrediente>

    /** Lo mismo, avisando cuando cambia. La que usa la pantalla de cantidades. */
    @Query(
        """
        SELECT ri.* FROM receta_ingredientes ri
        JOIN receta_secciones rs ON rs.id = ri.seccionId
        WHERE rs.recetaId = :recetaId
        ORDER BY rs.orden, ri.orden
        """
    )
    fun observarIngredientesDeReceta(recetaId: Long): Flow<List<RecetaIngrediente>>

    /** Cuántos gramos suma una receta. Distinto de [costoTotalReceta]: eso suma dinero. */
    @Query(
        """
        SELECT COALESCE(SUM(ri.cantidadG), 0)
        FROM receta_ingredientes ri
        JOIN receta_secciones rs ON rs.id = ri.seccionId
        WHERE rs.recetaId = :recetaId
        """
    )
    suspend fun sumaGramosIngredientes(recetaId: Long): Double

    /**
     * Los ingredientes de **una sección**, para comprobar si uno ya está puesto.
     *
     * Es más chica que [obtenerTodosLosIngredientes] a propósito y no un filtro sobre
     * aquella: el mismo ingrediente en dos secciones distintas es correcto y corriente
     * —almendra en el bizcocho y almendra en la decoración—, así que la pregunta que hay
     * que hacerle a la base es siempre por sección.
     */
    @Query("SELECT * FROM receta_ingredientes WHERE seccionId = :seccionId ORDER BY orden")
    suspend fun obtenerIngredientesDeSeccion(seccionId: Long): List<RecetaIngrediente>

    /**
     * Cómo se llama un ingrediente, solo para poder nombrarlo en un aviso.
     *
     * Vive acá y no se pide prestado el `IngredienteRepositorio` porque este DAO ya conoce
     * la tabla `ingredientes` —el cálculo del costo la cruza— y sumar un repositorio entero
     * como dependencia por un nombre abriría un camino de ida y vuelta entre los dos.
     */
    @Query("SELECT nombre FROM ingredientes WHERE id = :ingredienteId")
    suspend fun nombreDeIngrediente(ingredienteId: Long): String?

    @Insert
    suspend fun insertarIngrediente(item: RecetaIngrediente): Long

    @Query("UPDATE receta_ingredientes SET cantidadG = :cantidad WHERE id = :itemId")
    suspend fun actualizarCantidad(itemId: Long, cantidad: Double)

    @Query("DELETE FROM receta_ingredientes WHERE id = :itemId")
    suspend fun eliminarIngrediente(itemId: Long)

    // --- Rendimiento ---

    @Query("SELECT * FROM receta_rendimiento WHERE recetaId = :recetaId")
    suspend fun obtenerRendimiento(recetaId: Long): RecetaRendimiento?

    /**
     * El rendimiento de una receta, avisando cuando cambia.
     *
     * **La necesitan dos pantallas a la vez** desde que el molde es un paso propio (8.4.1,
     * #2): el paso del molde escribe `usaMolde` y `dimensiones`, y el de rendimiento decide
     * con `usaMolde` si el peso final es obligatorio y si ofrece reescalar por peso. Con una
     * lectura de una sola vez cada ViewModel se quedaba con su foto vieja y las dos
     * pantallas se contradecían — poner el molde y ver todavía "reescalar por peso" del otro
     * lado, o quitarlo y no verla aparecer hasta tocar algo.
     */
    @Query("SELECT * FROM receta_rendimiento WHERE recetaId = :recetaId")
    fun observarRendimiento(recetaId: Long): Flow<RecetaRendimiento?>

    @Query("SELECT trozos FROM receta_rendimiento WHERE recetaId = :recetaId")
    suspend fun obtenerTrozos(recetaId: Long): Int?

    @Query("SELECT usaMolde FROM receta_rendimiento WHERE recetaId = :recetaId")
    suspend fun usaMolde(recetaId: Long): Boolean?

    @Query("SELECT pesoFinalG FROM receta_rendimiento WHERE recetaId = :recetaId")
    suspend fun obtenerPesoFinal(recetaId: Long): Double?

    @Query(
        """
        SELECT recetaId, trozos FROM receta_rendimiento
        WHERE recetaId IN (:recetaIds)
        """
    )
    suspend fun trozosDeVariasRecetas(recetaIds: List<Long>): List<TrozosDeReceta>

    @Insert
    suspend fun insertarRendimiento(rendimiento: RecetaRendimiento)

    @Update
    suspend fun actualizarRendimiento(rendimiento: RecetaRendimiento)

    // --- Duración ---

    /**
     * Las duraciones anotadas de una receta.
     *
     * **Puede venir vacía, y eso es normal**: el paso es opcional y la mayoría de las recetas
     * no lo llena. No hay que sembrar filas al crear la receta —a diferencia del rendimiento,
     * que sí se siembra porque de él sale la división por trozos— porque una fila de duración
     * en blanco no se distingue de una que dice "no lo sé".
     */
    @Query("SELECT * FROM receta_duracion WHERE recetaId = :recetaId")
    suspend fun obtenerDuraciones(recetaId: Long): List<RecetaDuracion>

    /**
     * Guarda o reemplaza una duración.
     *
     * `REPLACE` se apoya en la clave primaria compuesta `(recetaId, tipo)`: cada receta tiene
     * como máximo una fila por tipo de guardado, así que volver a guardar el mismo bloque lo
     * pisa en vez de acumular.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarDuracion(duracion: RecetaDuracion)

    /** Borra un bloque de duración, para cuando se vacía lo que estaba anotado. */
    @Query("DELETE FROM receta_duracion WHERE recetaId = :recetaId AND tipo = :tipo")
    suspend fun eliminarDuracion(recetaId: Long, tipo: TipoDuracion)

    // --- Precios ---

    @Query("SELECT * FROM receta_precios WHERE recetaId = :recetaId ORDER BY id")
    suspend fun obtenerPrecios(recetaId: Long): List<RecetaPrecio>

    @Query("SELECT * FROM receta_precios WHERE recetaId IN (:recetaIds) ORDER BY recetaId, id")
    suspend fun preciosDeVariasRecetas(recetaIds: List<Long>): List<RecetaPrecio>

    @Insert
    suspend fun insertarPrecio(precio: RecetaPrecio): Long

    @Update
    suspend fun actualizarPrecio(precio: RecetaPrecio)

    @Query("DELETE FROM receta_precios WHERE id = :precioId")
    suspend fun eliminarPrecio(precioId: Long)

    @Query("UPDATE receta_precios SET esReferencia = 0 WHERE recetaId = :recetaId")
    suspend fun quitarReferenciaATodos(recetaId: Long)

    @Query("UPDATE receta_precios SET esReferencia = 1 WHERE id = :precioId")
    suspend fun marcarComoReferencia(precioId: Long)

    /**
     * Deja [precioId] como **único** precio de referencia de la receta.
     *
     * Va en una transacción y apaga los demás antes de encender este. Hacerlo en dos pasos
     * sueltos deja una ventana en la que hay dos referencias o ninguna, y ahí las cifras
     * automáticas pasan a depender de qué fila devuelva primero la consulta.
     *
     * **No comprueba si ese precio pierde plata**: eso necesita el costo de la receta, que
     * es otra consulta, y lo revisa el repositorio antes de llamar acá (8.6).
     */
    @Transaction
    suspend fun fijarPrecioDeReferencia(recetaId: Long, precioId: Long) {
        quitarReferenciaATodos(recetaId)
        marcarComoReferencia(precioId)
    }

    // --- Simulación de venta ---

    @Insert
    suspend fun insertarSimulacionVenta(simulacion: RecetaSimulacionVenta)

    @Query("SELECT * FROM receta_simulacion_venta WHERE recetaId = :recetaId")
    suspend fun obtenerSimulacionVenta(recetaId: Long): RecetaSimulacionVenta?

    @Update
    suspend fun actualizarSimulacionVenta(simulacion: RecetaSimulacionVenta)

    // --- Creación ---

    /**
     * Crea una receta con todo lo que necesita para existir sin huecos.
     *
     * Va en una transacción y siembra el rendimiento, la simulación de venta y una
     * primera sección. El `trozos = 1` del rendimiento es deliberado: así ninguna
     * división por trozos puede reventar mientras la receta está a medio armar en el
     * asistente. Los precios no se siembran, porque un precio en 0 sería falso.
     */
    @Transaction
    suspend fun crearReceta(titulo: String, nombrePrimeraSeccion: String = "General"): Long {
        val recetaId = insertar(Receta(titulo = titulo))
        insertarRendimiento(RecetaRendimiento(recetaId = recetaId, usaMolde = false, trozos = 1))
        insertarSimulacionVenta(RecetaSimulacionVenta(recetaId = recetaId))
        insertarSeccion(RecetaSeccion(recetaId = recetaId, nombreSeccion = nombrePrimeraSeccion, orden = 0))
        return recetaId
    }
}
