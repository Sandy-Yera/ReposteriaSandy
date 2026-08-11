package com.sandyyera.reposteria.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sandyyera.reposteria.data.db.entidades.MovimientoDeAlmacen
import com.sandyyera.reposteria.data.db.entidades.Venta
import com.sandyyera.reposteria.data.db.entidades.VentaLinea
import kotlinx.coroutines.flow.Flow

/**
 * Lo que un día dejó, sumando sus ventas (18.2).
 *
 * Las cuatro cifras salen de **una sola consulta** y no de recorrer las líneas desde Kotlin, por
 * lo mismo que el costo de una receta: sumar en la base es una pasada, y sumar afuera son tantas
 * consultas como líneas tenga el día.
 *
 * [costoReal] es `null` —y no 0— cuando **ninguna venta de ese día descontó del almacén**. La
 * diferencia es la que da sentido al informe: sin movimientos no se sabe lo que costó de verdad, y
 * un 0 diría que fue gratis. La pantalla muestra el estimado diciendo que es estimado (18.5).
 */
data class ResumenDeUnDia(
    val fecha: Long,
    val ingresoReal: Double,
    val ingresoEstimado: Double,
    val costoEstimado: Double,
    val costoReal: Double?
)

@Dao
interface VentaDao {

    // --- Las ventas ---

    /**
     * Las ventas, de la más nueva a la más vieja.
     *
     * Ordena por `fecha` y desempata por `id`: dos ventas del mismo día tienen la misma fecha
     * —se guarda el día, no el instante (18.1)— y sin el segundo criterio el orden entre ellas
     * quedaría a criterio del motor, o sea distinto entre una consulta y la siguiente.
     */
    @Query("SELECT * FROM ventas ORDER BY fecha DESC, id DESC")
    fun observarTodas(): Flow<List<Venta>>

    @Query("SELECT * FROM ventas WHERE id = :ventaId")
    suspend fun obtener(ventaId: Long): Venta?

    @Insert
    suspend fun insertar(venta: Venta): Long

    @Update
    suspend fun actualizar(venta: Venta)

    @Query("DELETE FROM ventas WHERE id = :ventaId")
    suspend fun eliminar(ventaId: Long)

    // --- Sus líneas ---

    @Query("SELECT * FROM venta_lineas WHERE ventaId = :ventaId ORDER BY id")
    fun observarLineas(ventaId: Long): Flow<List<VentaLinea>>

    @Query("SELECT * FROM venta_lineas WHERE ventaId = :ventaId ORDER BY id")
    suspend fun obtenerLineas(ventaId: Long): List<VentaLinea>

    @Insert
    suspend fun insertarLinea(linea: VentaLinea): Long

    @Query("DELETE FROM venta_lineas WHERE id = :lineaId")
    suspend fun eliminarLinea(lineaId: Long)

    // --- Los movimientos del almacén ---

    @Insert
    suspend fun insertarMovimiento(movimiento: MovimientoDeAlmacen): Long

    @Query("SELECT * FROM movimientos_almacen WHERE ventaId = :ventaId ORDER BY id")
    suspend fun movimientosDe(ventaId: Long): List<MovimientoDeAlmacen>

    /**
     * Los últimos movimientos, para poder mirar de dónde salió un número.
     *
     * Con tope y no completo: el historial crece todos los días y una lista sin límite se pone
     * lenta justo cuando ya hay suficientes datos para que sirva.
     */
    @Query("SELECT * FROM movimientos_almacen ORDER BY fecha DESC, id DESC LIMIT :cuantos")
    fun observarUltimosMovimientos(cuantos: Int): Flow<List<MovimientoDeAlmacen>>

    /**
     * Lo que costó de verdad lo que se descontó por una venta.
     *
     * `-SUM(cantidad * valorUnitario)` con el signo cambiado porque las salidas se guardan
     * **negativas** (ver `MovimientoDeAlmacen.cantidad`): lo que salió del almacén es lo que
     * costó, y en positivo es como se lee.
     *
     * Devuelve `null` cuando esa venta no tiene movimientos, y ese `null` es información: quiere
     * decir "no descontó", que es distinto de "costó 0".
     */
    @Query(
        """
        SELECT -SUM(cantidad * valorUnitario) FROM movimientos_almacen
        WHERE ventaId = :ventaId AND motivo = 'VENTA'
        """
    )
    suspend fun costoRealDe(ventaId: Long): Double?

    // --- El informe ---

    /**
     * Estimado contra real, día por día (18.2).
     *
     * El `LEFT JOIN` contra los movimientos es lo que permite que un día **sin** descuento salga
     * igual en la lista, con su costo real en `null` en vez de desaparecer: un informe que
     * esconde los días que no descontaron haría creer que se vendió menos.
     *
     * La suma de los movimientos va en una subconsulta agrupada por día y no en el `JOIN`
     * directo: uniendo las dos tablas planas, cada línea de venta multiplicaría cada movimiento y
     * los totales saldrían inflados por el producto de las dos cantidades. Es el error clásico de
     * juntar dos tablas de detalle contra la misma cabecera.
     */
    @Query(
        """
        SELECT v.fecha AS fecha,
               COALESCE(SUM(l.unidades * l.precioUnitario), 0)          AS ingresoReal,
               COALESCE(SUM(l.unidades * l.precioEstimadoUnitario), 0)  AS ingresoEstimado,
               COALESCE(SUM(l.unidades * l.costoEstimadoUnitario), 0)   AS costoEstimado,
               (
                   SELECT -SUM(m.cantidad * m.valorUnitario)
                   FROM movimientos_almacen m
                   WHERE m.motivo = 'VENTA' AND m.fecha = v.fecha
               ) AS costoReal
        FROM ventas v
        LEFT JOIN venta_lineas l ON l.ventaId = v.id
        GROUP BY v.fecha
        ORDER BY v.fecha DESC
        """
    )
    fun observarResumenPorDia(): Flow<List<ResumenDeUnDia>>
}
