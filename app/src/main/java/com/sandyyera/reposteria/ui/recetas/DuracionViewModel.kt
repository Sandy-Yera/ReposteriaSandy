package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import com.sandyyera.reposteria.logica.duracion.UnidadDuracion
import com.sandyyera.reposteria.logica.duracion.describirDuracion
import com.sandyyera.reposteria.logica.validaciones.ORDEN_DE_LOS_BLOQUES
import com.sandyyera.reposteria.logica.validaciones.elBloqueDiceAlgo
import com.sandyyera.reposteria.logica.validaciones.errorEnCantidadDeDuracion
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lo escrito en un bloque de duración.
 *
 * Vive en memoria mientras se edita: **el paso no guarda por su cuenta en cada tecla**, a
 * diferencia de los ingredientes. Acá tres bloques se llenan de corrido y guardar en cada
 * letra escribiría y borraría filas mientras la persona todavía está decidiendo.
 */
data class BloqueDeDuracion(
    val tipo: TipoDuracion,
    val apto: Boolean = true,
    val cantidad: String = "",
    val unidad: UnidadDuracion = UnidadDuracion.DIAS,
    val tocado: Boolean = false
) {
    val error: String? get() = errorEnCantidadDeDuracion(cantidad, apto).takeIf { tocado }

    /** Si este bloque tiene algo que guardar. Un "no apto" **sí** lo tiene. */
    val diceAlgo: Boolean get() = elBloqueDiceAlgo(apto, cantidad)

    /** Cómo se va leyendo, en vivo, tal como quedará guardado. */
    val comoSeLee: String
        get() = describirDuracion(
            apto = apto,
            cantidad = cantidad.takeIf { error == null }?.let { textoANumero(it)?.toInt() },
            unidad = unidad
        )
}

/** Lo que el paso de duración necesita para dibujarse. */
data class EstadoDuracion(
    val receta: Receta? = null,
    val bloques: List<BloqueDeDuracion> = ORDEN_DE_LOS_BLOQUES.map { BloqueDeDuracion(it) },
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    /** Si hay algo escrito que no sirve. Vacío no cuenta: el paso puede quedar en blanco. */
    val puedeGuardar: Boolean
        get() = bloques.all { errorEnCantidadDeDuracion(it.cantidad, it.apto) == null }

    /** Si el paso quedó completamente sin llenar, que es un estado válido (8.4). */
    val todoVacio: Boolean get() = bloques.none { it.diceAlgo }
}

/**
 * El cerebro del paso "Duración" (8.4).
 *
 * Es el paso más liviano de la receta y el único que **puede quedar completamente vacío**:
 * no alimenta ninguna cuenta — ni el costo, ni los precios, ni los sueldos, ni las
 * simulaciones. Por eso acá no hay nada que "recalcular en vivo" y las reglas son blandas:
 * lo que se revisa es que lo escrito signifique algo, no que esté completo.
 *
 * Los tres bloques se editan en memoria y se guardan de una vez al tocar Guardar. Guardar en
 * cada tecla —como sí hace el paso de cantidades— acá escribiría y borraría filas mientras la
 * persona todavía está decidiendo, porque un bloque a medio escribir se ve igual que uno
 * vaciado a propósito.
 */
class DuracionViewModel(
    private val recetaId: Long,
    private val recetas: RecetaRepositorio
) : ViewModel() {

    private val bloques = MutableStateFlow(ORDEN_DE_LOS_BLOQUES.map { BloqueDeDuracion(it) })
    private val mensaje = MutableStateFlow<String?>(null)

    val estado: StateFlow<EstadoDuracion> = combine(
        bloques,
        mensaje,
        // El título **se observa** y no se lee una vez: se renombra desde el paso de
        // cantidades (8.4.1, #3) y este encabezado tiene que enterarse solo.
        recetas.observarReceta(recetaId)
    ) { losBloques, mensajeActual, laReceta ->
        EstadoDuracion(
            receta = laReceta,
            bloques = losBloques,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoDuracion()
    )

    init {
        viewModelScope.launch {
            val guardadas = recetas.obtenerDuraciones(recetaId)
            // Los tipos que no están son los que nadie llenó: su bloque queda en blanco.
            bloques.value = ORDEN_DE_LOS_BLOQUES.map { tipo ->
                val fila = guardadas[tipo] ?: return@map BloqueDeDuracion(tipo)
                BloqueDeDuracion(
                    tipo = tipo,
                    apto = fila.apto,
                    cantidad = fila.cantidad?.toString().orEmpty(),
                    unidad = fila.unidad ?: UnidadDuracion.DIAS,
                    tocado = true
                )
            }
        }
    }

    fun cambiarApto(tipo: TipoDuracion, apto: Boolean) = enBloque(tipo) {
        // Lo escrito **no se borra** al marcar "no apto": si fue un toque por error, volver
        // a marcarlo apto devuelve el número. Lo que no se guarda es otra cosa, y eso lo
        // decide el repositorio.
        it.copy(apto = apto, tocado = true)
    }

    fun cambiarCantidad(tipo: TipoDuracion, texto: String) = enBloque(tipo) {
        // Solo dígitos: las duraciones son enteras y el punto de mil no aplica a "99 meses".
        it.copy(cantidad = texto.filter { caracter -> caracter.isDigit() }, tocado = true)
    }

    fun cambiarUnidad(tipo: TipoDuracion, unidad: UnidadDuracion) = enBloque(tipo) {
        it.copy(unidad = unidad, tocado = true)
    }

    /**
     * Guarda los tres bloques de una vez.
     *
     * Va bloque por bloque y no en una transacción porque cada uno es independiente: si uno
     * falla, que los otros dos hayan quedado guardados es mejor que perderlos todos. Y no
     * pueden fallar a medias por otra razón — la validación ya corrió antes de escribir.
     */
    fun guardar() {
        val actuales = bloques.value
        if (!estado.value.puedeGuardar) {
            bloques.value = actuales.map { it.copy(tocado = true) }
            return
        }

        viewModelScope.launch {
            val fallo = actuales.firstNotNullOfOrNull { bloque ->
                val r = recetas.guardarDuracion(
                    recetaId = recetaId,
                    tipo = bloque.tipo,
                    apto = bloque.apto,
                    cantidadTexto = bloque.cantidad,
                    unidad = bloque.unidad
                )
                (r as? Resultado.NoSePudo)?.motivo
            }
            mensaje.value = fallo ?: "Se guardaron las duraciones"
        }
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun enBloque(tipo: TipoDuracion, cambio: (BloqueDeDuracion) -> BloqueDeDuracion) {
        bloques.update { lista ->
            lista.map { if (it.tipo == tipo) cambio(it) else it }
        }
    }

    companion object {
        fun fabrica(recetaId: Long, recetas: RecetaRepositorio): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { DuracionViewModel(recetaId, recetas) }
            }
    }
}
