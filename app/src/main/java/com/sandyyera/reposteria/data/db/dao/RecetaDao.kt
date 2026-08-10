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
import com.sandyyera.reposteria.data.db.entidades.RecetaPaso
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

/**
 * Cómo se llama un ingrediente del catálogo, para poder pedir varios de una vez.
 *
 * Existe por la firma de una receta copiada (8.11.5): lleva el nombre de cada ingrediente para
 * poder armar la frase ("'Harina' pasó de 550 a 500 g"), y pedirlo de a uno serían tantas
 * consultas como ingredientes tenga la receta.
 */
data class NombreDeIngrediente(val id: Long, val nombre: String)

/**
 * Una línea de receta con el nombre y el precio de su ingrediente (8.12).
 *
 * Existe para el resumen, que muestra todas las líneas de una vez y no necesita el catálogo para
 * nada más. Trae [unidades] junto a [cantidadG] porque son **una sola cantidad contada de dos
 * formas**: leer una sin la otra deja una bolsa en 0 g, que es exactamente lo que no se quiere
 * mostrar (14.1.1).
 */
data class LineaConIngrediente(
    val id: Long,
    val seccionId: Long,
    val ingredienteId: Long,
    val cantidadG: Double,
    val unidades: Double?,
    val nombre: String,
    val valorPorGramo: Double
) {
    /** La cantidad en su unidad, que es la que multiplica el costo. */
    val cuanto: Double get() = unidades ?: cantidadG

    val esObjeto: Boolean get() = unidades != null

    val subtotal: Double get() = cuanto * valorPorGramo
}

@Dao
interface RecetaDao {

    @Query("SELECT * FROM recetas ORDER BY titulo COLLATE NOCASE")
    fun observarTodas(): Flow<List<Receta>>

    /**
     * Todas las recetas, una sola vez.
     *
     * Es la foto de un momento y por eso no es `Flow`: la usa el cuadro de "asignarle una receta
     * a un empleado", que mientras está abierto no puede ver aparecer recetas nuevas — es la misma
     * decisión que el catálogo de ingredientes en el cuadro del almacén (12.2.1).
     */
    @Query("SELECT * FROM recetas ORDER BY titulo COLLATE NOCASE")
    suspend fun obtenerTodasUnaVez(): List<Receta>

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
     * El COALESCE de afuera es obligatorio: SUM sobre cero filas devuelve NULL en SQLite, no 0,
     * y una receta recién creada todavía no tiene ingredientes. El JOIN lee el valor del
     * ingrediente en este momento, que es lo que se quiere: los precios nunca quedan
     * congelados en la receta.
     *
     * **El `COALESCE(ri.unidades, ri.cantidadG)` es lo que hace que los objetos cuesten** (14.5).
     * Una bolsa o un sticker se cuentan por unidad y `cantidadG` va en 0 —para que reescalar por
     * molde no los toque—, así que multiplicar por los gramos daría siempre cero. Lo que hay que
     * multiplicar es la cantidad **en su unidad**, y `valorPorGramo` ya guarda el precio en esa
     * misma unidad. En una línea normal `unidades` es `NULL` y el COALESCE devuelve los gramos,
     * o sea que nada cambia para lo que se pesa.
     */
    @Query(
        """
        SELECT COALESCE(SUM(COALESCE(ri.unidades, ri.cantidadG) * i.valorPorGramo), 0)
        FROM receta_ingredientes ri
        JOIN receta_secciones rs ON rs.id = ri.seccionId
        JOIN ingredientes i      ON i.id  = ri.ingredienteId
        WHERE rs.recetaId = :recetaId
        """
    )
    suspend fun costoTotalReceta(recetaId: Long): Double

    /**
     * El costo de **una** receta, avisando cuando cambie. Es [costoTotalReceta] como `Flow`.
     *
     * Existe por rendimiento y la diferencia es grande: [observarCostos] recorre y agrupa
     * **todas** las recetas de la base, y estaba siendo usada para mirar una sola —tanto en
     * `observarDatosCalculo` como, indirectamente, en el paso de cantidades—. Con la receta
     * abierta hay dos pantallas suscritas, así que cada tecla que cambiaba un ingrediente
     * disparaba dos recorridos completos de la base para leer un número de una receta.
     *
     * El `WHERE` va antes del agrupamiento, así que esta consulta toca solo las filas de esa
     * receta y se apoya en el índice de `receta_secciones(recetaId)`. Room vigila las mismas
     * tres tablas, así que sigue avisando igual de bien.
     *
     * **No lleva `GROUP BY` y por eso no tiene la trampa de la otra:** `SUM` sobre cero filas
     * devuelve una fila con `NULL`, que el `COALESCE` convierte en 0. Una receta sin
     * ingredientes contesta 0 en vez de no aparecer.
     */
    @Query(
        """
        SELECT COALESCE(SUM(COALESCE(ri.unidades, ri.cantidadG) * i.valorPorGramo), 0)
        FROM receta_ingredientes ri
        JOIN receta_secciones rs ON rs.id = ri.seccionId
        JOIN ingredientes i      ON i.id  = ri.ingredienteId
        WHERE rs.recetaId = :recetaId
        """
    )
    fun observarCostoDeReceta(recetaId: Long): Flow<Double>

    /**
     * Lo mismo pero para varias recetas de una vez, para la simulación múltiple.
     *
     * Ojo: una receta sin ingredientes **no aparece** en el resultado, porque no tiene
     * filas que agrupar. Quien la llame debe tomar las que falten como costo 0.
     */
    @Query(
        """
        SELECT rs.recetaId AS recetaId,
               COALESCE(SUM(COALESCE(ri.unidades, ri.cantidadG) * i.valorPorGramo), 0) AS costo
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
               COALESCE(SUM(COALESCE(ri.unidades, ri.cantidadG) * i.valorPorGramo), 0) AS costo
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

    // --- Partes: recetas que usan otras recetas (8.11) ---

    /**
     * Qué **otras** recetas tienen secciones copiadas de esta.
     *
     * Alimenta la advertencia previa a borrar una receta (8.11.4). Se excluye a sí misma por si
     * alguna vez quedara una sección apuntando a su propia receta: enumerarse a sí misma en el
     * aviso de su propio borrado no diría nada.
     */
    @Query(
        """
        SELECT DISTINCT r.* FROM recetas r
        JOIN receta_secciones rs ON rs.recetaId = r.id
        WHERE rs.recetaOrigenId = :recetaId AND r.id != :recetaId
        ORDER BY r.titulo COLLATE NOCASE
        """
    )
    suspend fun recetasQueUsanLaReceta(recetaId: Long): List<Receta>

    /**
     * Los ids de las recetas que ya están hechas de partes: el tope de un solo nivel (8.11.6).
     *
     * Mira las **dos** columnas y no solo el id, porque una sección cuya original fue borrada
     * sigue siendo una parte traída aunque SQLite le haya puesto el id en `null` (8.11.7).
     * Ofrecer esa receta como parte de una tercera sería justo el nivel que no se quiere.
     */
    @Query(
        """
        SELECT DISTINCT recetaId FROM receta_secciones
        WHERE recetaOrigenId IS NOT NULL OR firmaDelOrigen IS NOT NULL
        """
    )
    suspend fun idsDeRecetasHechasDePartes(): List<Long>

    /** Las secciones traídas de **toda** la base, para saber qué recetas tienen avisos. */
    @Query(
        """
        SELECT * FROM receta_secciones
        WHERE recetaOrigenId IS NOT NULL OR firmaDelOrigen IS NOT NULL
        ORDER BY recetaId, orden
        """
    )
    suspend fun todasLasSeccionesTraidas(): List<RecetaSeccion>

    /**
     * El latido que hace recalcular los avisos cuando cambia una receta original.
     *
     * Un aviso de 8.11.3 depende de los **ingredientes y los pasos de otra receta**, y ninguna
     * de esas tablas aparece en la consulta de secciones: cambiar los gramos de la harina del
     * bizcocho no toca `receta_secciones`, así que nada volvería a preguntar y la torta se
     * quedaría sin avisar. Este `Flow` cierra ese hueco.
     *
     * **Que el número no cambie no importa, y es a propósito**: Room reemite un `Flow` cada vez
     * que se **invalida** una de las tablas de la consulta, sin comparar el resultado anterior.
     * O sea que corregir un gramaje —que no mueve ningún `COUNT`— igual dispara el recálculo.
     * Ponerle un `distinctUntilChanged` encima lo rompería en silencio, dejando avisos que solo
     * aparecen al agregar o quitar filas.
     *
     * Es un `COUNT` y no un `SELECT *` porque lo que se necesita es el aviso de que algo pasó, y
     * traerse todos los ingredientes de la base para tirarlos sería pagar por un dato que no se
     * usa.
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM receta_ingredientes) + (SELECT COUNT(*) FROM receta_pasos)
        """
    )
    fun latidoDePartes(): Flow<Int>

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

    /** Una sección suelta por su id. La usan las acciones de las partes, que reciben solo eso. */
    @Query("SELECT * FROM receta_secciones WHERE id = :seccionId")
    suspend fun obtenerSeccion(seccionId: Long): RecetaSeccion?

    /** Las secciones de varias recetas de una consulta, para armar sus firmas en lote (8.11.5). */
    @Query("SELECT * FROM receta_secciones WHERE recetaId IN (:recetaIds) ORDER BY recetaId, orden")
    suspend fun seccionesDeVariasRecetas(recetaIds: List<Long>): List<RecetaSeccion>

    @Insert
    suspend fun insertarSeccion(seccion: RecetaSeccion): Long

    @Update
    suspend fun actualizarSeccion(seccion: RecetaSeccion)

    /**
     * Reescribe varias secciones de una vez, para tocar todo un grupo traído junto.
     *
     * Mismo motivo que `actualizarPasos`: **Room envuelve en una transacción los `@Update` de una
     * colección**, y un bucle desde el repositorio no —`@Transaction` es una anotación de DAO—.
     * Desvincular la mitad de un grupo dejaría unas secciones avisando y otras no, con la misma
     * receta original detrás.
     */
    @Update
    suspend fun actualizarSecciones(lasQueCambian: List<RecetaSeccion>)

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

    /**
     * Lo mismo pero **ya cruzado con el catálogo**, para el resumen de la receta (8.12).
     *
     * El cruce va en la consulta y no en memoria, al revés que en el paso de cantidades: allá la
     * pantalla ya tiene el catálogo cargado para el buscador, y acá no hay ninguna otra razón
     * para traérselo entero — es la misma decisión que en el almacén.
     *
     * El `JOIN` es **INNER a propósito**: una fila cuyo ingrediente se borró del catálogo no
     * aparece, igual que no suma al costo (8.2). Dibujarla a medias mostraría un renglón sin
     * nombre ni precio, y sumarla como 0 mentiría sobre el total.
     */
    @Query(
        """
        SELECT ri.id            AS id,
               ri.seccionId     AS seccionId,
               ri.ingredienteId AS ingredienteId,
               ri.cantidadG     AS cantidadG,
               ri.unidades      AS unidades,
               i.nombre         AS nombre,
               i.valorPorGramo  AS valorPorGramo
        FROM receta_ingredientes ri
        JOIN receta_secciones rs ON rs.id = ri.seccionId
        JOIN ingredientes i      ON i.id  = ri.ingredienteId
        WHERE rs.recetaId = :recetaId
        ORDER BY rs.orden, ri.orden
        """
    )
    fun observarLineasConIngrediente(recetaId: Long): Flow<List<LineaConIngrediente>>

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

    /** Lo mismo para varios de una vez, que es lo que necesita armar una firma (8.11.5). */
    @Query("SELECT id, nombre FROM ingredientes WHERE id IN (:ingredienteIds)")
    suspend fun nombresDeIngredientes(ingredienteIds: List<Long>): List<NombreDeIngrediente>

    /**
     * Los ingredientes de varias secciones de una consulta.
     *
     * Es la versión en lote de [obtenerIngredientesDeSeccion], y hace falta por lo mismo: armar
     * la firma de una receta de cinco secciones no puede costar cinco consultas, y armar las de
     * todas las recetas que alguien usó como parte, veinticinco.
     */
    @Query("SELECT * FROM receta_ingredientes WHERE seccionId IN (:seccionIds) ORDER BY seccionId, orden, id")
    suspend fun ingredientesDeVariasSecciones(seccionIds: List<Long>): List<RecetaIngrediente>

    @Insert
    suspend fun insertarIngrediente(item: RecetaIngrediente): Long

    /**
     * Cambia lo que lleva una línea.
     *
     * Las dos columnas se escriben **juntas** porque son una sola cantidad contada de dos formas
     * (14.5): un objeto va con `cantidadG = 0` y sus unidades aparte, y lo que se pesa al revés.
     * Actualizar solo una dejaría una línea que dice "2 cajas" y pesa 500 g.
     */
    @Query(
        "UPDATE receta_ingredientes SET cantidadG = :cantidad, unidades = :unidades " +
            "WHERE id = :itemId"
    )
    suspend fun actualizarCantidad(itemId: Long, cantidad: Double, unidades: Double?)

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
     * Lo mismo, avisando cuando cambie. La usa el resumen de la receta (8.12).
     *
     * Existe además de la de una vez y no en su lugar: el paso de duración lee una foto para
     * rellenar sus campos —y ahí observar rompería el campo de texto (12.2.1)—, mientras que el
     * resumen **muestra** lo anotado y tiene que enterarse solo. *Lo que se muestra se observa;
     * la foto de un momento es para calcular.*
     */
    @Query("SELECT * FROM receta_duracion WHERE recetaId = :recetaId")
    fun observarDuraciones(recetaId: Long): Flow<List<RecetaDuracion>>

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

    /** Lo mismo, avisando cuando cambian. **La que hay que usar para mostrarlos.** */
    @Query("SELECT * FROM receta_precios WHERE recetaId = :recetaId ORDER BY id")
    fun observarPrecios(recetaId: Long): Flow<List<RecetaPrecio>>

    @Query("SELECT * FROM receta_precios WHERE recetaId IN (:recetaIds) ORDER BY recetaId, id")
    suspend fun preciosDeVariasRecetas(recetaIds: List<Long>): List<RecetaPrecio>

    /** Un precio suelto por su id. La usan editar y borrar, que reciben solo eso. */
    @Query("SELECT * FROM receta_precios WHERE id = :precioId")
    suspend fun obtenerPrecioPorId(precioId: Long): RecetaPrecio?

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

    /** Lo mismo, avisando cuando cambia. La que usa la pantalla (8.7). */
    @Query("SELECT * FROM receta_simulacion_venta WHERE recetaId = :recetaId")
    fun observarSimulacionVenta(recetaId: Long): Flow<RecetaSimulacionVenta?>

    @Update
    suspend fun actualizarSimulacionVenta(simulacion: RecetaSimulacionVenta)

    // --- Pasos (8.8) ---

    /**
     * Los pasos de una receta, en su orden, avisando cuando cambien.
     *
     * `ORDER BY orden` y no por `id`: los pasos se reordenan, y el id solo dice cuál se creó
     * antes. Con `id` un paso movido volvería a su lugar viejo al recargar la pantalla.
     *
     * El desempate por `id` es para las filas que compartan `orden`, cosa que no debería pasar
     * pero deja el resultado **estable**: sin él, dos pasos empatados podrían salir en un orden
     * distinto en cada consulta y la lista bailaría sola.
     */
    @Query("SELECT * FROM receta_pasos WHERE recetaId = :recetaId ORDER BY orden, id")
    fun observarPasos(recetaId: Long): Flow<List<RecetaPaso>>

    @Query("SELECT * FROM receta_pasos WHERE recetaId = :recetaId ORDER BY orden, id")
    suspend fun obtenerPasos(recetaId: Long): List<RecetaPaso>

    @Query("SELECT * FROM receta_pasos WHERE id = :pasoId")
    suspend fun obtenerPaso(pasoId: Long): RecetaPaso?

    @Insert
    suspend fun insertarPaso(paso: RecetaPaso): Long

    @Update
    suspend fun actualizarPaso(paso: RecetaPaso)

    @Query("DELETE FROM receta_pasos WHERE id = :pasoId")
    suspend fun eliminarPaso(pasoId: Long)

    /**
     * El mayor `orden` que hay en la receta, o `null` si todavía no hay pasos.
     *
     * Sirve para poner uno nuevo al final sin traerse la lista entera. Devuelve `Int?` y no
     * `Int` a propósito: con `0` no se distinguiría "no hay pasos" de "hay uno en la posición
     * 0", y el primero tiene que empezar en 0 y el segundo en 1.
     */
    @Query("SELECT MAX(orden) FROM receta_pasos WHERE recetaId = :recetaId")
    suspend fun ultimoOrdenDePaso(recetaId: Long): Int?

    /** Los pasos de varias recetas de una consulta, para armar sus firmas en lote (8.11.5). */
    @Query("SELECT * FROM receta_pasos WHERE recetaId IN (:recetaIds) ORDER BY recetaId, orden, id")
    suspend fun pasosDeVariasRecetas(recetaIds: List<Long>): List<RecetaPaso>

    /**
     * Borra los pasos que van bajo una sección.
     *
     * Hace falta a mano porque la clave foránea es `SET_NULL`: borrar una sección deja sus pasos
     * como General, que es lo correcto al reorganizar una receta (5.5.1). Irse con la sección es
     * la decisión explícita de 8.11.4 —"Borrar: se elimina la sección **y también sus pasos**"—
     * y por eso se pide aparte y antes de borrar la sección.
     */
    @Query("DELETE FROM receta_pasos WHERE tituloSeccionId IN (:seccionIds)")
    suspend fun eliminarPasosDeSecciones(seccionIds: List<Long>)

    /**
     * Reescribe varios pasos de una vez, para renumerarlos al mover uno.
     *
     * **Room envuelve solo los `@Update` de una colección en una transacción**, así que las n
     * escrituras entran o no entran juntas. Es la razón de que la renumeración se haga con una
     * sola llamada y no con un bucle desde el repositorio: allá `@Transaction` no significa
     * nada —es una anotación de DAO— y un bucle a medias dejaría la lista con dos pasos en la
     * misma posición.
     */
    @Update
    suspend fun actualizarPasos(losQueCambian: List<RecetaPaso>)

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
