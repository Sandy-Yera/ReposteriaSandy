package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.rendimiento.AVISO_PESO_REESCALADO
import com.sandyyera.reposteria.logica.rendimiento.PESO_NO_ESPECIFICADO
import com.sandyyera.reposteria.logica.rendimiento.pesoPorTrozo
import com.sandyyera.reposteria.logica.validaciones.revisarRendimiento
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Qué hay abierto encima del paso de rendimiento. */
sealed interface DialogoRendimiento {

    data object Ninguno : DialogoRendimiento

    /** Cambiar cuánto rinde una receta sin molde, reescalando sus ingredientes. */
    data class ReescalarPorPeso(
        val pesoNuevo: String = "",
        val guardando: Boolean = false,
        val rechazo: String? = null
    ) : DialogoRendimiento {
        val puedeGuardar: Boolean get() = pesoNuevo.isNotBlank() && !guardando
    }
}

/** Lo que el paso de rendimiento necesita para dibujarse. */
data class EstadoRendimiento(
    val receta: Receta? = null,
    val usaMolde: Boolean = false,
    val trozos: String = "1",
    val pesoFinal: String = "",
    /**
     * Si el peso guardado salió de un reescalado y nadie lo ha mirado todavía (8.4.1, #4).
     *
     * Viene de la base y no de esta sesión: quien reescala hoy pesa el producto mañana,
     * cuando salga del horno, y para entonces la app ya se cerró.
     */
    val pesoSinRevisar: Boolean = false,
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    private val errores get() = revisarRendimiento(trozos, pesoFinal, usaMolde)

    val errorTrozos: String? get() = errores.trozos
    val errorPesoFinal: String? get() = errores.pesoFinal
    val puedeGuardar: Boolean get() = errores.sirve

    /** El aviso de "compruébalo", o `null` si no hay nada que comprobar. */
    val avisoDelPeso: String? get() = AVISO_PESO_REESCALADO.takeIf { pesoSinRevisar }

    /** Cuánto pesa cada trozo, o "No especificado" si no hay peso anotado (8.3). */
    val pesoDeCadaTrozo: String
        get() {
            // Con `textoANumero`, que es la que entiende el formato de la app; parsearlo
            // a mano acá sería una segunda regla sobre cómo se escribe un número.
            val peso = pesoFinal.takeIf { it.isNotBlank() && errores.pesoFinal == null }
                ?.let { textoANumero(it) }
            val cuantos = trozos.toIntOrNull()?.takeIf { it >= 1 } ?: 1
            return pesoPorTrozo(peso, cuantos)
        }

    /**
     * El " g" que acompaña al peso por trozo, o vacío si no hay peso.
     *
     * Va aparte y no dentro de `pesoPorTrozo` porque esa función también devuelve
     * "No especificado", y "No especificado g" no se lee.
     */
    val unidadDelPeso: String get() = if (pesoDeCadaTrozo == PESO_NO_ESPECIFICADO) "" else " g"

    /** Si se ofrece reescalar por peso: solo sin molde (con molde se cambia de molde). */
    val sePuedeReescalarPorPeso: Boolean get() = !usaMolde
}

/**
 * El cerebro del paso "Rendimiento" de una receta (8.3): en cuántos trozos rinde y cuánto pesa.
 *
 * **Ya no se ocupa del molde**: eso se fue a `MoldeDeRecetaViewModel` al hacerse un paso
 * propio (8.4.1, #2). Acá quedan las dos preguntas que sí son de rendimiento, y la única
 * cosa del molde que sigue importando es `usaMolde`, porque de eso depende que el peso final
 * sea opcional u obligatorio (8.3).
 *
 * Lo nuevo es el aviso de `pesoSinRevisar`: al cambiar de molde, el peso del producto se
 * multiplica por el mismo factor que los ingredientes, y eso es una **estimación**. Queda
 * marcado hasta que alguien toque el campo — haya cambiado el número o no, porque lo que
 * confirma el dato es haberlo mirado.
 */
class RendimientoViewModel(
    private val recetaId: Long,
    private val recetas: RecetaRepositorio
) : ViewModel() {

    private val mensaje = MutableStateFlow<String?>(null)
    private val _dialogo = MutableStateFlow<DialogoRendimiento>(DialogoRendimiento.Ninguno)

    /** Lo escrito en los dos campos, por su propio canal (12.2.1). */
    private val trozos = MutableStateFlow("1")
    private val pesoFinal = MutableStateFlow("")

    /** Lo que hay abierto encima, fuera del `combine` del estado (12.2.1). */
    val dialogo: StateFlow<DialogoRendimiento> = _dialogo

    /**
     * Lo que muestra la pantalla, **observando la base y no leyéndola una vez** (12.2.1).
     *
     * `usaMolde` lo escribe el paso anterior, no este. Con una lectura de una sola vez esta
     * pantalla se quedaba con la foto de cuando se abrió: poner el molde y seguir viendo
     * "Reescalar la receta a otro peso", o quitarlo y no verlo aparecer hasta tocar algo.
     * Peor todavía, el peso que reescala el molde tampoco llegaba, así que guardar de acá
     * volvía a escribir el peso viejo encima del recalculado.
     */
    val estado: StateFlow<EstadoRendimiento> = combine(
        recetas.observarReceta(recetaId),
        recetas.observarRendimiento(recetaId),
        trozos,
        pesoFinal,
        mensaje
    ) { receta, rendimiento, trozosEscritos, pesoEscrito, mensajeActual ->
        EstadoRendimiento(
            receta = receta,
            usaMolde = rendimiento?.usaMolde ?: false,
            trozos = trozosEscritos,
            pesoFinal = pesoEscrito,
            pesoSinRevisar = rendimiento?.pesoReescaladoSinRevisar ?: false,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoRendimiento()
    )

    init {
        // Los campos siguen a lo que está guardado, en el formato de la app: así guardar sin
        // tocar nada no puede cambiar ningún número.
        //
        // **Y siguen también cuando lo cambia otro**, que es lo que antes no pasaba: al
        // reescalar por molde, el peso del producto se multiplica desde el paso anterior, y
        // acá el campo seguía mostrando el número viejo. Se veía como que "el peso no
        // cambió", y encima guardar desde esta pantalla escribía el viejo de vuelta.
        //
        // El `distinctUntilChanged` es lo que impide que esto pise lo que se está
        // escribiendo: solo se re-siembra cuando **lo guardado** cambia de verdad. Sin él,
        // cualquier escritura que no toque estos dos campos —`marcarPesoRevisado`, por
        // ejemplo— borraría lo tecleado a medias.
        viewModelScope.launch {
            recetas.observarRendimiento(recetaId)
                .map { it?.trozos to it?.pesoFinalG }
                .distinctUntilChanged()
                .collect { (trozosGuardados, pesoGuardado) ->
                    trozos.value = (trozosGuardados ?: 1).toString()
                    pesoFinal.value = pesoGuardado?.let { formatearNumero(it) } ?: ""
                }
        }
    }

    // --- Los dos campos ---

    fun cambiarTrozos(texto: String) {
        // Sin decimales ni punto de mil: los trozos son unidades y "1.000 trozos" no existe.
        trozos.value = texto.filter { it.isDigit() }
    }

    fun cambiarPesoFinal(texto: String) {
        pesoFinal.value = formatearMientrasSeEscribe(texto)
        // Escribir en el campo es haberlo mirado, así que el aviso ya no aplica. Va acá
        // además de en `marcarPesoRevisado` porque se puede llegar al campo sin tocarlo —
        // con el "siguiente" del teclado desde los trozos, por ejemplo.
        marcarPesoRevisado()
    }

    /**
     * Apaga el aviso de "peso reescalado, compruébalo" (8.4.1, #4).
     *
     * La llama la pantalla **cuando el campo del peso recibe el foco**: tocarlo para mirarlo
     * es exactamente lo que el aviso pide. Exigir además que se edite obligaría a borrar y
     * reescribir el mismo número solo para callar un aviso.
     *
     * Se escribe en la base y no solo en el estado, porque el aviso tiene que sobrevivir a
     * cerrar la app; y se corta sola si ya estaba apagada, para no escribir en cada foco.
     */
    fun marcarPesoRevisado() {
        if (!estado.value.pesoSinRevisar) return
        viewModelScope.launch {
            recetas.marcarPesoRevisado(recetaId)
        }
    }

    fun guardar() {
        viewModelScope.launch {
            when (val r = recetas.guardarRendimiento(recetaId, trozos.value, pesoFinal.value)) {
                is Resultado.Listo -> mensaje.value = "Se guardó el rendimiento"
                is Resultado.NoSePudo -> mensaje.value = r.motivo
            }
        }
    }

    // --- Reescalar por peso (solo sin molde) ---

    fun abrirReescalarPorPeso() {
        _dialogo.value = DialogoRendimiento.ReescalarPorPeso(pesoNuevo = pesoFinal.value)
    }

    fun cambiarPesoNuevo(texto: String) {
        _dialogo.update { actual ->
            if (actual is DialogoRendimiento.ReescalarPorPeso) {
                actual.copy(pesoNuevo = formatearMientrasSeEscribe(texto), rechazo = null)
            } else {
                actual
            }
        }
    }

    fun confirmarReescaladoPorPeso() {
        val cuadro = _dialogo.value as? DialogoRendimiento.ReescalarPorPeso ?: return
        if (!cuadro.puedeGuardar) return

        _dialogo.update { (it as DialogoRendimiento.ReescalarPorPeso).copy(guardando = true) }

        viewModelScope.launch {
            when (val r = recetas.reescalarPorPeso(recetaId, cuadro.pesoNuevo)) {
                is Resultado.Listo -> {
                    _dialogo.value = DialogoRendimiento.Ninguno
                    pesoFinal.value = cuadro.pesoNuevo
                    mensaje.value = "Se reescaló la receta"
                }
                is Resultado.NoSePudo -> _dialogo.update { actual ->
                    if (actual is DialogoRendimiento.ReescalarPorPeso) {
                        actual.copy(guardando = false, rechazo = r.motivo)
                    } else {
                        actual
                    }
                }
            }
        }
    }

    fun cerrarDialogo() {
        _dialogo.value = DialogoRendimiento.Ninguno
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    companion object {
        fun fabrica(
            recetaId: Long,
            recetas: RecetaRepositorio
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { RendimientoViewModel(recetaId, recetas) }
        }
    }
}
