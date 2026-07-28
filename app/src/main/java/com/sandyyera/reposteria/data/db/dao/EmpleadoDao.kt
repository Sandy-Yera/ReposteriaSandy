package com.sandyyera.reposteria.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sandyyera.reposteria.data.db.entidades.Empleado
import com.sandyyera.reposteria.data.db.entidades.EmpleadoRecetaSueldo
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultiple
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultipleDetalle
import kotlinx.coroutines.flow.Flow

@Dao
interface EmpleadoDao {

    /**
     * Los empleados, con el genérico siempre primero.
     *
     * `esGenerico DESC` lo deja arriba porque en SQLite `true` es 1 y `false` es 0.
     */
    @Query("SELECT * FROM empleados ORDER BY esGenerico DESC, nombre COLLATE NOCASE")
    fun observarTodos(): Flow<List<Empleado>>

    @Query("SELECT * FROM empleados WHERE id = :empleadoId")
    suspend fun obtener(empleadoId: Long): Empleado?

    @Query("SELECT * FROM empleados WHERE esGenerico = 1 LIMIT 1")
    suspend fun obtenerGenerico(): Empleado?

    @Insert
    suspend fun insertar(empleado: Empleado): Long

    @Update
    suspend fun actualizar(empleado: Empleado)

    /**
     * Borra un empleado.
     *
     * La condición `esGenerico = 0` es una red de seguridad: el genérico es el modelo
     * estándar y no debe poder eliminarse ni por error. La pantalla tampoco ofrece la
     * acción, pero acá queda garantizado.
     */
    @Query("DELETE FROM empleados WHERE id = :empleadoId AND esGenerico = 0")
    suspend fun eliminarPorId(empleadoId: Long)

    // --- Sueldos por receta ---

    @Query("SELECT * FROM empleado_receta_sueldo WHERE empleadoId = :empleadoId")
    suspend fun obtenerSueldos(empleadoId: Long): List<EmpleadoRecetaSueldo>

    @Query(
        """
        SELECT * FROM empleado_receta_sueldo
        WHERE empleadoId = :empleadoId AND recetaId = :recetaId
        """
    )
    suspend fun obtenerSueldo(empleadoId: Long, recetaId: Long): EmpleadoRecetaSueldo?

    /**
     * Guarda el sueldo de una receta, reemplazando el anterior si ya había uno.
     *
     * El índice único sobre (empleadoId, recetaId) hace que un segundo intento choque;
     * con REPLACE ese choque se resuelve pisando la fila vieja, que es justo lo que se
     * quiere: un empleado tiene un solo sueldo por receta.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarSueldo(sueldo: EmpleadoRecetaSueldo): Long

    @Query("DELETE FROM empleado_receta_sueldo WHERE id = :sueldoId")
    suspend fun eliminarSueldo(sueldoId: Long)

    // --- Simulación múltiple ---

    @Query("SELECT * FROM empleado_simulacion_multiple WHERE empleadoId = :empleadoId")
    suspend fun obtenerSimulacionMultiple(empleadoId: Long): EmpleadoSimulacionMultiple?

    /**
     * Los días por semana que se usan al simular todas las recetas juntas.
     *
     * Es distinto del `diasPorSemana` de cada sueldo: aquel es propio de una receta y
     * este es el compartido. Si todavía no se ha configurado, se toma 1.
     */
    @Query("SELECT diasPorSemana FROM empleado_simulacion_multiple WHERE empleadoId = :empleadoId")
    suspend fun obtenerDiasCompartidos(empleadoId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarSimulacionMultiple(simulacion: EmpleadoSimulacionMultiple)

    @Query("SELECT * FROM empleado_simulacion_multiple_detalle WHERE empleadoId = :empleadoId")
    suspend fun obtenerDetalle(empleadoId: Long): List<EmpleadoSimulacionMultipleDetalle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarDetalle(detalle: EmpleadoSimulacionMultipleDetalle): Long

    @Query("DELETE FROM empleado_simulacion_multiple_detalle WHERE id = :detalleId")
    suspend fun eliminarDetalle(detalleId: Long)
}
