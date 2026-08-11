package com.sandyyera.reposteria.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sandyyera.reposteria.data.db.entidades.ArticuloDeAlmacen
import kotlinx.coroutines.flow.Flow

/**
 * Una fila del almacén ya cruzada con el catálogo (sección 14).
 *
 * El cruce se hace **en la consulta y no en memoria**, al revés que en el paso de cantidades de
 * una receta: allá la pantalla ya tenía el catálogo cargado para el buscador, y acá no hay
 * ninguna otra razón para traérselo entero.
 *
 * Desde 14.5 **todo lo del almacén tiene ingrediente**: anotar algo acá lo crea también en el
 * catálogo. El `LEFT JOIN` se queda igual por las filas de antes de ese cambio que la migración
 * no haya podido enlazar — es preferible que una de esas se vea con el valor en blanco a que
 * desaparezca de la lista sin que nadie se entere.
 */
data class ArticuloConValor(
    val id: Long,
    val ingredienteId: Long?,
    /** El nombre del catálogo si está enlazado, y el propio si quedó suelto. Lo resuelve el SQL. */
    val nombre: String,
    val cantidad: Double,
    val valorPorGramo: Double?,
    /** Si se cuenta por unidad. `null` solo en una fila vieja sin ingrediente. */
    val esObjeto: Boolean?,
    /** Si se ofrece al armar una receta. `null` solo en una fila vieja sin ingrediente. */
    val vaEnRecetas: Boolean?,
    /** Dónde se compró, cuándo, si era oferta (14.6). Se lee al editar, no en la lista. */
    val detalles: String?,
    val actualizadoEn: Long
)

@Dao
interface AlmacenDao {

    /**
     * Todo el almacén, con el nombre y el valor de cada cosa, avisando cuando cambie.
     *
     * `COALESCE(i.nombre, a.nombre)` es lo que deja convivir las dos mitades en una sola lista:
     * lo enlazado se nombra desde el catálogo —así un renombre llega solo— y lo suelto con su
     * propio nombre. Sin eso harían falta dos consultas y dos listas, y la pantalla tendría que
     * volver a juntarlas.
     *
     * Room vigila las **dos** tablas de la consulta, así que cambiarle el precio a la harina
     * mueve el valor del stock sin que nadie tenga que acordarse de pedirlo — la misma regla que
     * dejó `observarCostos`: *lo que se muestra se observa*.
     *
     * Ordena por nombre y no por fecha: se usa para buscar algo, no para ver qué se tocó último.
     */
    @Query(
        """
        SELECT a.id            AS id,
               a.ingredienteId AS ingredienteId,
               COALESCE(i.nombre, a.nombre) AS nombre,
               a.cantidad      AS cantidad,
               i.valorPorGramo AS valorPorGramo,
               i.esObjeto      AS esObjeto,
               i.vaEnRecetas   AS vaEnRecetas,
               a.detalles      AS detalles,
               a.actualizadoEn AS actualizadoEn
        FROM almacen a
        LEFT JOIN ingredientes i ON i.id = a.ingredienteId
        ORDER BY nombre COLLATE NOCASE
        """
    )
    fun observarTodo(): Flow<List<ArticuloConValor>>

    @Query("SELECT * FROM almacen WHERE id = :articuloId")
    suspend fun obtener(articuloId: Long): ArticuloDeAlmacen?

    /**
     * La fila de almacén de un ingrediente, si la tiene (14.9).
     *
     * La usa el descuento por recetas hechas, que trabaja por `ingredienteId` —así es como salen
     * de las recetas— y no por el `id` de la fila. El índice es único, así que hay a lo más una.
     */
    @Query("SELECT * FROM almacen WHERE ingredienteId = :ingredienteId")
    suspend fun obtenerPorIngrediente(ingredienteId: Long): ArticuloDeAlmacen?

    /** Qué ingredientes del catálogo ya están en el almacén, para no ofrecerlos dos veces. */
    @Query("SELECT ingredienteId FROM almacen WHERE ingredienteId IS NOT NULL")
    suspend fun ingredientesYaEnElAlmacen(): List<Long>

    @Insert
    suspend fun insertar(articulo: ArticuloDeAlmacen): Long

    @Update
    suspend fun actualizar(articulo: ArticuloDeAlmacen)

    @Delete
    suspend fun eliminar(articulo: ArticuloDeAlmacen)
}
