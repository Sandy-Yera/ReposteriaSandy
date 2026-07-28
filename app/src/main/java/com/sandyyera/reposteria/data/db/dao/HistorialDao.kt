package com.sandyyera.reposteria.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sandyyera.reposteria.data.db.entidades.EventoCambio
import kotlinx.coroutines.flow.Flow

@Dao
interface HistorialDao {

    /** Los cambios, del más reciente al más antiguo, que es como se leen. */
    @Query("SELECT * FROM eventos_cambio ORDER BY creadoEn DESC")
    fun observarTodos(): Flow<List<EventoCambio>>

    @Insert
    suspend fun insertar(evento: EventoCambio): Long

    /**
     * Borra los eventos más viejos que cierta fecha.
     *
     * Se llama al registrar cada evento nuevo, así la limpieza no necesita su propio
     * proceso en segundo plano. Sin esto el historial crecería para siempre dentro del
     * mismo archivo que se sube como respaldo.
     */
    @Query("DELETE FROM eventos_cambio WHERE creadoEn < :anteriorA")
    suspend fun borrarAnterioresA(anteriorA: Long)
}
