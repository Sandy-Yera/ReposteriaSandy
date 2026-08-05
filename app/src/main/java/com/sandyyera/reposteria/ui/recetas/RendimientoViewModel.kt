package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.corteEfectivoDe
import com.sandyyera.reposteria.logica.moldes.medidaDelTrozo
import com.sandyyera.reposteria.logica.rendimiento.AVISO_PESO_REESCALADO
import com.sandyyera.reposteria.logica.rendimiento.PESO_NO_ESPECIFICADO
import com.sandyyera.reposteria.logica.rendimiento.pesoPorTrozo
import com.sandyyera.reposteria.logica.rendimiento.repartirEntreTrozos
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
    val usaMolde: Boolean = false,
    val trozos: String = "1",
    val pesoFinal: String = "",
    /**
     * Lo que cuesta hacer la receta entera, observado (8.2).
     *
     * Vive acá para poder mostrar el costo de cada trozo, que es tan del rendimiento como el
     * peso de cada trozo: los dos salen de dividir algo entre los mismos trozos que se
     * escriben en esta pantalla. Cambiar los trozos y no ver moverse el costo por trozo era
     * justamente la pregunta que quedaba sin responder acá.
     */
    val costoTotal: Double = 0.0,
    /**
     * Si la receta tiene al menos un ingrediente cargado.
     *
     * **Es un dato aparte del costo, y tiene que serlo** — la misma lección que ya costó un
     * bug en la lista de recetas: un ingrediente puede valer 0 a propósito (6.2), y una
     * receta hecha solo de esos cuesta 0 sin estar vacía. Deducirlo de `costoTotal <= 0`
     * mandaría a buscar un problema que no existe.
     */
    val tieneIngredientes: Boolean = false,
    /**
     * Si el peso guardado salió de un reescalado y nadie lo ha mirado todavía (8.4.1, #4).
     *
     * Viene de la base y no de esta sesión: quien reescala hoy pesa el producto mañana,
     * cuando salga del horno, y para entonces la app ya se cerró.
     */
    val pesoSinRevisar: Boolean = false,
    /** Las medidas del molde, si usa uno. De acá sale el tamaño de cada trozo (9.4). */
    val dimensionesDelMolde: DimensionesMolde? = null,
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

    /**
     * De qué tamaño queda cada trozo, según cómo se corte el molde (9.4).
     *
     * Va **al lado del peso de cada trozo**, que es la otra mitad de la misma pregunta: aquel
     * dice cuánto pesa lo que se entrega y este de qué porte es. Los dos salen de los trozos,
     * así que cambiar el número los mueve a la vez.
     *
     * Es `null` muchas veces y está bien: sin molde no hay nada que medir, un molde con forma
     * de persona no se corta, y de un triángulo sin medidas anotadas no se puede afirmar
     * nada. **Mejor no decir nada que decir un número inventado.**
     *
     * El corte se pide con `corteEfectivoDe` y no pasando `d.formaDelCorte` a secas. **No es
     * un arreglo**: `medidaDelTrozo` ya aplicaba esa misma sugerencia por dentro, así que un
     * molde anterior a la versión 4 —cuando llegó la columna— siempre respondió bien. Es que
     * la regla dejó de estar escondida al necesitarla también el paso del molde, y decirla en
     * voz alta acá deja a los dos lugares leyendo la misma función.
     */
    val medidaDeCadaTrozo: String?
        get() {
            val d = dimensionesDelMolde ?: return null
            val cuantos = trozos.toIntOrNull()?.takeIf { it >= 1 } ?: return null
            return medidaDelTrozo(d, corteEfectivoDe(d), cuantos, ::formatearNumero)
        }

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

    /**
     * Cuánto cuesta hacer cada trozo. **Es el piso de cualquier precio** (8.5).
     *
     * Se muestra acá y no solo en Gastos porque es la otra mitad de la misma pregunta: el
     * peso de cada trozo dice qué se entrega y este dice qué cuesta entregarlo, y los dos
     * salen de los trozos que se escriben en esta pantalla. Vuelve a aparecer en Gastos, al
     * lado del precio, y ahí no es repetir: allá la pregunta es a cuánto vender, y este
     * número es contra qué se compara.
     *
     * Usa `repartirEntreTrozos`, la misma división de la que sale el peso por trozo y el
     * costo con que se calculan las ganancias: escribirla acá otra vez serían dos verdades
     * sobre el mismo número.
     */
    val costoDeCadaTrozo: Double
        get() = repartirEntreTrozos(costoTotal, trozos.toIntOrNull()?.takeIf { it >= 1 } ?: 1)

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
        recetas.observarRendimiento(recetaId),
        // El costo **se observa**: lo mueven los ingredientes, que se cargan en otro paso.
        // El mapa no trae entrada para las recetas sin ingredientes, y ahí 0 es correcto.
        recetas.observarCostos(),
        combine(trozos, pesoFinal) { t, p -> t to p },
        combine(rechazo, mensaje) { r, m -> r to m }
    ) { rendimiento, costos, escrito, avisos ->
        EstadoRendimiento(
            usaMolde = rendimiento?.usaMolde ?: false,
            trozos = escrito.first,
            pesoFinal = escrito.second,
            costoTotal = costos[recetaId] ?: 0.0,
            // La consulta agrupa por receta, así que solo trae fila para las que tienen algo
            // cargado: estar en el mapa es exactamente "tiene ingredientes".
            tieneIngredientes = recetaId in costos,
            pesoSinRevisar = rendimiento?.pesoReescaladoSinRevisar ?: false,
            // Solo si de verdad usa molde: `quitarMolde` conserva las medidas por si fue un
            // error, así que la fila las tiene igual (la misma trampa del paso anterior).
            dimensionesDelMolde = rendimiento?.dimensiones?.takeIf { rendimiento.usaMolde },
            rechazoAlGuardar = avisos.first,
            mensaje = avisos.second,
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
        // Las dos condiciones de abajo son las que hacen que esto no se lleve por delante lo
        // que se está escribiendo, y cada una tapa un agujero distinto:
        //
        // 1. **Solo si lo guardado cambió de verdad.** `observarRendimiento` reemite ante
        //    *cualquier* escritura en la fila, incluida `marcarPesoRevisado`, que solo mueve
        //    un booleano. Sin esta condición, tocar el campo del peso para corregirlo lanzaba
        //    esa escritura, y cuando volvía —con lo tecleado ya distinto de lo guardado— el
        //    campo se re-sembraba con el número viejo y borraba lo escrito bajo el dedo.
        // 2. **Y solo si no hay un guardado esperando.** Mientras haya algo escrito sin
        //    escribir todavía, el campo manda: lo guardado es lo viejo por definición.
        var ultimoGuardado: Pair<Int, Double?>? = null

        viewModelScope.launch {
            recetas.observarRendimiento(recetaId).collect { fila ->
                val ahora = (fila?.trozos ?: 1) to fila?.pesoFinalG
                val cambioLoGuardado = ultimoGuardado != ahora
                ultimoGuardado = ahora

                if (!cambioLoGuardado || guardadoPendiente?.isActive == true) return@collect
                trozos.value = ahora.first.toString()
                pesoFinal.value = ahora.second?.let { formatearNumero(it) } ?: ""
            }
        }

        // **El rechazo se reintenta cuando desaparece el obstáculo.**
        //
        // El único rechazo posible acá es "esta promoción no cabe si bajas los trozos". Si se
        // borra esa promoción desde el paso de gastos, el aviso se quedaba puesto hasta que
        // alguien tocara el campo — acusando de algo que ya no existe.
        //
        // No basta con borrarlo: los trozos que se pidieron **siguen sin guardarse**, así que
        // limpiar el aviso dejaría la pantalla diciendo un número que la base no tiene. Lo
        // que corresponde es volver a intentar lo que quedó pendiente, que es lo que se
        // quería en primer lugar.
        viewModelScope.launch {
            recetas.observarPrecios(recetaId).collect {
                if (rechazo.value != null) guardar()
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
     * Guarda lo escrito, si sirve. **La llama sola [programarGuardado]**, no un botón.
     *
     * Es `suspend` y no lanza su propia corrutina a propósito: así el trabajo ocurre *dentro*
     * de `guardadoPendiente` y ese `Job` sigue activo mientras se escribe en la base, que es
     * de lo que se agarra la re-siembra de los campos para no pisar lo que se está tecleando.
     *
     * Con lo escrito a medias no hace nada: un campo vacío mientras se corrige un número no
     * puede borrar lo que estaba guardado. Y **no anuncia el éxito**: sin botón que apretar,
     * un "se guardó" cada vez que se deja de escribir es ruido puro. Lo que sí se dice es el
     * rechazo, y va junto al campo de los trozos.
     */
    private suspend fun guardar() {
        if (!estado.value.puedeGuardar) return

        when (val r = recetas.guardarRendimiento(recetaId, trozos.value, pesoFinal.value)) {
            is Resultado.Listo -> rechazo.value = null
            is Resultado.NoSePudo -> rechazo.value = r.motivo
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
