package com.sandyyera.reposteria.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredienteDao {

    /**
     * Todos los ingredientes, en orden alfabético.
     *
     * Devuelve un [Flow]: la pantalla se entera sola cuando algo cambia, sin tener que
     * volver a preguntar después de cada guardado.
     */
    @Query("SELECT * FROM ingredientes ORDER BY nombre COLLATE NOCASE")
    fun observarTodos(): Flow<List<Ingrediente>>

    /** La lista completa una sola vez, sin quedar observando. */
    @Query("SELECT * FROM ingredientes ORDER BY nombre COLLATE NOCASE")
    suspend fun obtenerTodosUnaVez(): List<Ingrediente>

    @Query("SELECT * FROM ingredientes WHERE id = :ingredienteId")
    suspend fun obtener(ingredienteId: Long): Ingrediente?

    /**
     * Los que se ofrecen al armar una receta (14.5).
     *
     * Deja fuera lo que se marcó como que **no va en recetas** —una vela decorativa, el papel de
     * horno— sin sacarlo del catálogo ni del almacén. Es lo que permite llevar la cuenta de todo
     * sin que este buscador se llene de cosas que nunca van en una receta.
     */
    @Query("SELECT * FROM ingredientes WHERE vaEnRecetas = 1 ORDER BY nombre COLLATE NOCASE")
    fun observarParaRecetas(): Flow<List<Ingrediente>>

    /**
     * Los ids que **no tienen fila en el almacén**, para el aviso de 14.4.
     *
     * Se pregunta por los que faltan y no por los que están porque eso es lo que se muestra, y
     * porque la respuesta suele ser corta: lo normal es tener casi todo anotado. Room vigila las
     * dos tablas, así que agregar algo al almacén apaga su aviso sin que nadie lo pida.
     */
    @Query(
        """
        SELECT i.id FROM ingredientes i
        WHERE NOT EXISTS (SELECT 1 FROM almacen a WHERE a.ingredienteId = i.id)
        """
    )
    fun observarSinAlmacen(): Flow<List<Long>>

    @Insert
    suspend fun insertar(ingrediente: Ingrediente): Long

    @Update
    suspend fun actualizar(ingrediente: Ingrediente)

    @Delete
    suspend fun eliminar(ingrediente: Ingrediente)

    /**
     * Borra la fila directamente por su id.
     *
     * Es el borrado crudo: no avisa a qué recetas afecta ni deja rastro en el historial.
     * Eso lo hace el repositorio, que es quien debe llamarse desde la pantalla.
     */
    @Query("DELETE FROM ingredientes WHERE id = :ingredienteId")
    suspend fun eliminarPorId(ingredienteId: Long)
}
