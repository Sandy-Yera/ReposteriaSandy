package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.ResumenDeUnDia
import com.sandyyera.reposteria.data.db.dao.VentaDao
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.MotivoDeMovimiento
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.data.db.entidades.Venta
import com.sandyyera.reposteria.data.db.entidades.VentaLinea
import com.sandyyera.reposteria.logica.almacen.RecetaHecha
import com.sandyyera.reposteria.logica.almacen.VistaPreviaDelDescuento
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ingresoBruto
import kotlinx.coroutines.flow.Flow

/**
 * Una línea que se está por registrar: qué receta, cuántas y a cuánto (18.1).
 *
 * Es lo que la pantalla arma y **no lo que se guarda**: al guardarla se le agregan las tres
 * cifras estimadas de ese día, que la pantalla no tiene por qué calcular.
 */
data class LineaParaRegistrar(
    val recetaId: Long,
    val unidades: Int,
    val precioUnitario: Double
)

/** Cómo terminó registrar una venta. */
sealed interface ResultadoRegistrarVenta {
    data class Registrada(val ventaId: Long) : ResultadoRegistrarVenta

    data class NoSePudo(val motivo: String) : ResultadoRegistrarVenta
}

/**
 * Las ventas y el informe de estimado contra real (sección 18).
 *
 * **No calcula nada del informe**: las cuatro cifras de un día las suma la base
 * (`observarResumenPorDia`) y lo que se concluye de ellas vive en `logica/ventas`, ya probado sin
 * celular. Acá se consulta, se escribe, y se congela lo que hay que congelar.
 */
class VentaRepositorio(
    private val dao: VentaDao,
    private val recetas: RecetaRepositorio,
    // Para descontar lo vendido reutilizando la máquina de 14.9 (18.4). La flecha va en este
    // sentido: el almacén no sabe de ventas, salvo por la tabla de movimientos que él mismo llena.
    private val almacen: AlmacenRepositorio,
    private val historial: HistorialRepositorio
) {

    fun observarTodas(): Flow<List<Venta>> = dao.observarTodas()

    fun observarLineas(ventaId: Long): Flow<List<VentaLinea>> = dao.observarLineas(ventaId)

    suspend fun obtener(ventaId: Long): Venta? = dao.obtener(ventaId)

    /** Estimado contra real, día por día (18.2). Lo que dibuja el informe. */
    fun observarResumenPorDia(): Flow<List<ResumenDeUnDia>> = dao.observarResumenPorDia()

    /** Las ventas de un día, con sus líneas aparte. Lo que se ve al abrir un día del informe. */
    fun observarDelDia(fecha: Long): Flow<List<Venta>> = dao.observarDelDia(fecha)

    fun observarLineasDelDia(fecha: Long): Flow<List<VentaLinea>> = dao.observarLineasDelDia(fecha)

    /**
     * Las recetas que se pueden vender, con su costo y sus precios del momento.
     *
     * **Se ofrecen todas, también las que no tienen precio.** Al revés que al asignarle una receta
     * a un empleado —donde sin precio no hay ganancia que repartir y la receta ni se ofrece—, acá
     * lo que falta es la estimación y no la venta: se puede haber vendido algo a lo que nunca se le
     * puso precio de referencia, y negarse a anotarlo perdería la venta de verdad por no tener la
     * estimada. Lo que la app no sabe entra en 0 y se dice (18.1).
     */
    suspend fun recetasParaVender(): List<DatosCalculoReceta> {
        val ids = recetas.obtenerTodasUnaVez().map { it.id }
        return recetas.obtenerDatosCalculo(ids).values.sortedBy { it.titulo.lowercase() }
    }

    /**
     * Registra una venta: un día y lo que se vendió (18.1).
     *
     * **Las tres cifras estimadas se congelan acá**, al contrario que en todo el resto de la app.
     * En una receta el costo se calcula en vivo a propósito, para que subir un ingrediente mueva
     * todo lo que cuelga de él; en una venta pasa lo contrario — lo que se estimó **ese día** es
     * un hecho, y recalcularlo después haría que el informe de marzo cambiara al corregir un
     * precio en agosto. Un informe que se mueve hacia atrás no sirve para decidir nada.
     *
     * **Una receta sin precio entra con estimado 0 y no revienta.** `ingresoBruto` lanza con razón
     * cuando no hay ningún precio, y llamarlo sin mirar antes fue exactamente lo que cerró la app
     * en Empleados. Acá además tiene sentido: se puede vender algo a lo que nunca se le puso
     * precio de referencia, y lo que no se sabe es cuánto se **esperaba** cobrar.
     *
     * El título de la receta se **copia** a la línea (18.1): si la receta se borra después, no
     * queda de dónde leerlo, y un informe con una línea sin nombre no se puede leer.
     */
    suspend fun registrar(
        fecha: Long,
        lineas: List<LineaParaRegistrar>,
        notas: String? = null
    ): ResultadoRegistrarVenta {
        if (lineas.isEmpty()) {
            return ResultadoRegistrarVenta.NoSePudo("Agrega al menos una receta vendida")
        }
        val datos = recetas.obtenerDatosCalculo(lineas.map { it.recetaId }.distinct())

        val ventaId = dao.insertar(Venta(fecha = fecha, notas = notas?.trim()?.ifBlank { null }))
        for (linea in lineas) {
            val d = datos[linea.recetaId]
            dao.insertarLinea(
                VentaLinea(
                    ventaId = ventaId,
                    recetaId = linea.recetaId,
                    tituloReceta = d?.titulo ?: "Receta borrada",
                    unidades = linea.unidades,
                    precioUnitario = linea.precioUnitario,
                    costoEstimadoUnitario = d?.costoTotal ?: 0.0,
                    // El guardia no es de adorno: sin precio, `ingresoBruto` lanza.
                    precioEstimadoUnitario = d?.takeIf { it.tienePrecio }?.let { ingresoBruto(it) }
                        ?: 0.0
                )
            )
        }

        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se registró una venta de ${lineas.size} " +
                if (lineas.size == 1) "receta" else "recetas",
            detalleAdicional = lineas.joinToString { datos[it.recetaId]?.titulo ?: "?" }
        )
        return ResultadoRegistrarVenta.Registrada(ventaId)
    }

    /**
     * Qué le pasaría al almacén si se descontara esta venta. **No escribe nada** (18.4).
     *
     * Reutiliza la máquina de 14.9 y no una copia, que es lo que garantiza que el almacén se mueva
     * igual por los dos caminos: una venta de 3 tortas son 3 tandas de esa receta.
     */
    suspend fun vistaPreviaDelDescuento(ventaId: Long): VistaPreviaDelDescuento =
        almacen.vistaPreviaDeDescontar(loQueSeVendio(ventaId))

    /**
     * Descuenta del almacén lo que llevó esta venta, ya con la vista previa mirada (18.4).
     *
     * **Es opcional por venta y no automático a ciegas**: lo vendido hoy pudo hornearse ayer, y
     * descontar al vender contaría dos veces lo que ya se descontó al cocinar. Por eso la pregunta
     * se contesta por venta.
     *
     * **No se descuenta dos veces.** La venta recuerda si ya lo hizo (`descontoDelAlmacen`), que es
     * otra pregunta que la de si existen movimientos: distingue una venta que no descontó de una
     * que descontó cero.
     */
    suspend fun descontarDelAlmacen(ventaId: Long): Resultado {
        val venta = dao.obtener(ventaId) ?: return Resultado.NoSePudo("Esa venta ya no existe")
        if (venta.descontoDelAlmacen) {
            return Resultado.NoSePudo("Esta venta ya descontó del almacén")
        }

        val previa = almacen.vistaPreviaDeDescontar(loQueSeVendio(ventaId))
        val resultado = almacen.descontar(
            previa = previa,
            queSeHizo = "la venta",
            motivo = MotivoDeMovimiento.VENTA,
            ventaId = ventaId,
            // **El día de la venta y no el de hoy.** El costo real de un día sale de sumar los
            // movimientos con esa fecha (18.2): descontar el lunes una venta del sábado cargaría
            // el costo al lunes, y el sábado quedaría como si no hubiera descontado.
            fecha = venta.fecha
        )
        if (resultado is Resultado.NoSePudo) return resultado

        dao.actualizar(venta.copy(descontoDelAlmacen = true))
        return Resultado.Listo
    }

    /**
     * Borra la venta, con sus líneas y sus movimientos por la cascada.
     *
     * **Borrar la venta no devuelve lo descontado al almacén**, y es deliberado: lo que salió del
     * frasco salió. Deshacer el stock exigiría saber que nadie lo corrigió a mano entremedio, y
     * suponerlo dejaría un número inventado donde antes había uno mirado.
     */
    suspend fun eliminar(ventaId: Long) {
        val venta = dao.obtener(ventaId) ?: return
        dao.eliminar(ventaId)
        historial.registrar(
            tipo = TipoEvento.ELIMINACION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se eliminó una venta",
            detalleAdicional = if (venta.descontoDelAlmacen) {
                "Lo que descontó del almacén no se devuelve"
            } else {
                null
            }
        )
    }

    /** Las líneas de una venta como las lee la máquina del descuento (14.9). */
    private suspend fun loQueSeVendio(ventaId: Long): List<RecetaHecha> =
        dao.obtenerLineas(ventaId).mapNotNull { linea ->
            RecetaHecha(
                recetaId = linea.recetaId ?: return@mapNotNull null,
                titulo = linea.tituloReceta,
                // Una venta de 3 tortas son 3 tandas de esa receta: la misma máquina, sin copia.
                tandas = linea.unidades.toDouble()
            )
        }
}
