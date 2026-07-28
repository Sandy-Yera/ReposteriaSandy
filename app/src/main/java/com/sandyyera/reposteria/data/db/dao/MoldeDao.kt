package com.sandyyera.reposteria.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sandyyera.reposteria.data.db.entidades.Molde
import kotlinx.coroutines.flow.Flow

@Dao
interface MoldeDao {

    @Query("SELECT * FROM moldes ORDER BY nombre COLLATE NOCASE")
    fun observarTodos(): Flow<List<Molde>>

    @Query("SELECT * FROM moldes WHERE id = :moldeId")
    suspend fun obtener(moldeId: Long): Molde?

    @Insert
    suspend fun insertar(molde: Molde): Long

    @Update
    suspend fun actualizar(molde: Molde)

    /**
     * Borra el molde del catálogo.
     *
     * Las recetas enlazadas no se rompen: su `moldeOrigenId` pasa a `null` por la regla
     * de la clave foránea y conservan las medidas que tenían. Solo dejan de recibir
     * correcciones de este molde.
     */
    @Query("DELETE FROM moldes WHERE id = :moldeId")
    suspend fun eliminarPorId(moldeId: Long)
}
