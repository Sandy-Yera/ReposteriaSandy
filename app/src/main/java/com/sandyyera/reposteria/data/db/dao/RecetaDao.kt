package com.sandyyera.reposteria.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.RecetaIngrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.db.entidades.RecetaRendimiento
import com.sandyyera.reposteria.data.db.entidades.RecetaSeccion
import com.sandyyera.reposteria.data.db.entidades.RecetaSimulacionVenta
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

    @Insert
    suspend fun insertarIngrediente(item: RecetaIngrediente): Long

    @Query("UPDATE receta_ingredientes SET cantidadG = :cantidad WHERE id = :itemId")
    suspend fun actualizarCantidad(itemId: Long, cantidad: Double)

    @Query("DELETE FROM receta_ingredientes WHERE id = :itemId")
    suspend fun eliminarIngrediente(itemId: Long)

    // --- Rendimiento ---

    @Query("SELECT * FROM receta_rendimiento WHERE recetaId = :recetaId")
    suspend fun obtenerRendimiento(recetaId: Long): RecetaRendimiento?

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
