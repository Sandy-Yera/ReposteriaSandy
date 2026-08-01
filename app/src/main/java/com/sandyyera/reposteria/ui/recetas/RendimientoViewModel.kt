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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Cuánto silencio se espera antes de guardar solo, en milisegundos.
 *
 * Ni tan corto que escriba en cada tecla —escribiendo "12" se pasa por "1", y ahí una
 * promoción de 3 trozos no cabe—, ni tan largo que cambiar de paso alcance a irse sin
 * guardar. Medio segundo es más de lo que dura una pausa entre dos dígitos y menos de lo que
 * tarda un dedo en llegar a la fila de pasos.
 */
private const val ESPERA_ANTES_DE_GUARDAR_MS = 500L

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
    /**
     * Lo que contestó el repositorio al guardar solo, si rechazó (8.4.1).
     *
     * Va **junto al campo de los trozos** y no en la franja de abajo, por la misma regla de
     * 8.2 que ya valía para los nombres repetidos: un aviso sobre lo que se acaba de escribir
     * con el teclado abierto queda tapado ahí. Y sin botón que apretar, la franja de abajo
     * además llegaría en un momento que nadie asocia con lo que hizo. El único rechazo
     * posible es el de las promociones que no caben, que es exactamente sobre los trozos.
     */
    val rechazoAlGuardar: String? = null,
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    private val errores get() = revisarRendimiento(trozos, pesoFinal, usaMolde)

    val errorTrozos: String? get() = rechazoAlGuardar ?: errores.trozos
    val errorPesoFinal: String? get() = errores.pesoFinal

    /** Si lo escrito sirve para guardarse. Lo consulta el guardado automático. */
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

    /** El motivo del último rechazo del guardado automático, o `null` si guardó bien. */
    private val rechazo = MutableStateFlow<String?>(null)

    /** El guardado que está esperando su turno, para poder cancelarlo si se sigue escribiendo. */
    private var guardadoPendiente: Job? = null

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
        combine(trozos, pesoFinal) { t, p -> t to p },
        rechazo,
        mensaje
    ) { receta, rendimiento, escrito, rechazoActual, mensajeActual ->
        EstadoRendimiento(
            receta = receta,
            usaMolde = rendimiento?.usaMolde ?: false,
            trozos = escrito.first,
            pesoFinal = escrito.second,
            pesoSinRevisar = rendimiento?.pesoReescaladoSinRevisar ?: false,
            rechazoAlGuardar = rechazoActual,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoRendimiento()
    )

    init {
        // Los campos siguen a lo que está guardado, en el formato de la app.
        //
        // **Y siguen también cuando lo cambia otro**: al reescalar por molde, el peso del
        // producto se multiplica desde el paso anterior, y acá el campo se quedaba con el
        // número viejo. Se veía como que "el peso no cambió".
        //
        // La comparación es **por valor y no por texto**, y eso es lo que hace convivir esto
        // con el guardado automático: al guardar solo, la fila vuelve por el `Flow` y si se
        // comparara el texto se re-sembraría el campo en mitad de una palabra — escribir
        // "0008" quedaría en "8" bajo el dedo. Comparando lo que el texto *significa*, el eco
        // del propio guardado no toca nada y un reescalado ajeno sí.
        viewModelScope.launch {
            recetas.observarRendimiento(recetaId).collect { fila ->
                val trozosGuardados = fila?.trozos ?: 1
                if (trozos.value.toIntOrNull() != trozosGuardados) {
                    trozos.value = trozosGuardados.toString()
                }
                if (textoANumero(pesoFinal.value) != fila?.pesoFinalG) {
                    pesoFinal.value = fila?.pesoFinalG?.let { formatearNumero(it) } ?: ""
                }
            }
        }

    }

    // --- Los dos campos ---

    fun cambiarTrozos(texto: String) {
        // Sin decimales ni punto de mil: los trozos son unidades y "1.000 trozos" no existe.
        trozos.value = texto.filter { it.isDigit() }
        // Al escribir, el rechazo anterior deja de aplicar: era sobre el número de antes.
        rechazo.value = null
        programarGuardado()
    }

    fun cambiarPesoFinal(texto: String) {
        pesoFinal.value = formatearMientrasSeEscribe(texto)
        programarGuardado()
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

    /**
     * Programa un guardado para dentro de [ESPERA_ANTES_DE_GUARDAR_MS], cancelando el anterior.
     *
     * Es el guardado automático de 8.4.1. Se fue el botón de "Guardar rendimiento", que
     * parecía inútil porque al volver los datos seguían ahí — pero **no estaban guardados**:
     * lo que sobrevivía era el ViewModel, que Android conserva mientras la app viva. Cerrarla
     * los perdía, y ese es justo el momento en que uno cree tenerlos a salvo.
     *
     * Espera un silencio en vez de escribir en cada tecla, y no es solo por ahorrar
     * escrituras: tecleando "12" se pasa por "1", y con 1 trozo una promoción de 3 no cabe,
     * así que se rechazaría a mitad de una palabra.
     *
     * Se dispara desde los dos `cambiar…` y no colgado del `Flow` de los campos a propósito:
     * esos campos también se re-siembran solos cuando el paso del molde reescala el peso, y
     * eso no es alguien escribiendo — volver a guardarlo no aportaría nada y de paso borraría
     * un rechazo que sigue vigente.
     */
    private fun programarGuardado() {
        guardadoPendiente?.cancel()
        guardadoPendiente = viewModelScope.launch {
            delay(ESPERA_ANTES_DE_GUARDAR_MS)
            guardar()
        }
    }

    /**
     * Guarda lo escrito, si sirve. **La llama sola el guardado automático**, no un botón.
     *
     * Con lo escrito a medias no hace nada: un campo vacío mientras se corrige un número no
     * puede borrar lo que estaba guardado. Y **no anuncia el éxito**: sin botón que apretar,
     * un "se guardó" cada vez que se deja de escribir es ruido puro. Lo que sí se dice es el
     * rechazo, y va junto al campo de los trozos.
     */
    fun guardar() {
        if (!estado.value.puedeGuardar) return

        viewModelScope.launch {
            when (val r = recetas.guardarRendimiento(recetaId, trozos.value, pesoFinal.value)) {
                is Resultado.Listo -> rechazo.value = null
                is Resultado.NoSePudo -> rechazo.value = r.motivo
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
