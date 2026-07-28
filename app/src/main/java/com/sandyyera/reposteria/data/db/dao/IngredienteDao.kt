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
