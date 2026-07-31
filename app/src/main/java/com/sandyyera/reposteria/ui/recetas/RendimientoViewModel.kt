package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.Molde
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.MoldeRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.logica.busqueda.filtrarPor
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.ModoReescalado
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.rendimiento.PESO_NO_ESPECIFICADO
import com.sandyyera.reposteria.logica.rendimiento.pesoPorTrozo
import com.sandyyera.reposteria.logica.validaciones.CampoDeMolde
import com.sandyyera.reposteria.logica.validaciones.camposDe
import com.sandyyera.reposteria.logica.validaciones.dimensionesDesde
import com.sandyyera.reposteria.logica.validaciones.revisarRendimiento
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * De dónde salen las medidas del molde que se está eligiendo (9.3).
 *
 * Son dos caminos y **no da lo mismo cuál**: el guardado deja la receta enlazada al
 * catálogo, así que una corrección de medidas le llega sola; el de prueba la deja suelta,
 * con sus medidas fijas. Se elige a propósito y no por descarte.
 */
enum class OrigenDelMolde {
    /** Uno del catálogo. La receta queda enlazada y recibe sus correcciones. */
    GUARDADO,

    /** Medidas escritas a mano, sin guardar el molde. La receta queda sin vínculo. */
    PRUEBA
}

/** Qué hay abierto encima del paso de rendimiento. */
sealed interface DialogoRendimiento {

    data object Ninguno : DialogoRendimiento

    /**
     * Elegir el molde: uno del catálogo o uno de prueba.
     *
     * [esReescalado] cambia lo que significa aceptar. En `false` es la primera vez y solo se
     * guardan las medidas — no hay original contra el cual comparar, así que no hay factor
     * ni modo que elegir (9.3). En `true` ya hay molde y hay que decidir qué se conserva.
     */
    data class ElegirMolde(
        val esReescalado: Boolean,
        val origen: OrigenDelMolde = OrigenDelMolde.GUARDADO,
        val busqueda: String = "",
        val elegido: Molde? = null,
        val forma: TipoFormaMolde? = null,
        val medidas: Map<CampoDeMolde, String> = emptyMap(),
        val modo: ModoReescalado = ModoReescalado.CAPACIDAD,
        val guardando: Boolean = false,
        val rechazo: String? = null,
        /** El catálogo ya filtrado por el buscador. Lo rellena el `combine`, no se escribe. */
        val candidatos: List<Molde> = emptyList()
    ) : DialogoRendimiento {

        /** Las medidas que hay que pedir, si se está midiendo a mano. */
        val campos: List<CampoDeMolde>
            get() = if (origen == OrigenDelMolde.PRUEBA) forma?.let { camposDe(it) }.orEmpty()
            else emptyList()

        /** Las dimensiones que van a quedar, o `null` si todavía falta algo. */
        val dimensiones: DimensionesMolde?
            get() = when (origen) {
                OrigenDelMolde.GUARDADO -> elegido?.dimensiones
                OrigenDelMolde.PRUEBA -> dimensionesDesde(forma, medidas)
            }

        /** A qué molde del catálogo queda enlazada la receta. `null` en modo prueba. */
        val moldeOrigenId: Long?
            get() = if (origen == OrigenDelMolde.GUARDADO) elegido?.id else null

        val puedeGuardar: Boolean get() = dimensiones != null && !guardando
    }

    /** Cambiar cuánto rinde una receta sin molde, reescalando sus ingredientes. */
    data class ReescalarPorPeso(
        val pesoNuevo: String = "",
        val guardando: Boolean = false,
        val rechazo: String? = null
    ) : DialogoRendimiento {
        val puedeGuardar: Boolean get() = pesoNuevo.isNotBlank() && !guardando
    }

    /** La confirmación antes de dejar de usar molde. */
    data object ConfirmarQuitarMolde : DialogoRendimiento
}

/** Lo que el paso de rendimiento necesita para dibujarse. */
data class EstadoRendimiento(
    val receta: Receta? = null,
    val usaMolde: Boolean = false,
    val dimensiones: DimensionesMolde? = null,
    val moldeEnlazado: Molde? = null,
    val trozos: String = "1",
    val pesoFinal: String = "",
    val catalogoDeMoldes: List<Molde> = emptyList(),
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    private val errores get() = revisarRendimiento(trozos, pesoFinal, usaMolde)

    val errorTrozos: String? get() = errores.trozos
    val errorPesoFinal: String? get() = errores.pesoFinal
    val puedeGuardar: Boolean get() = errores.sirve

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

    /** El área y el volumen del molde actual, ya formateados, o `null` si no usa molde. */
    val medidasDelMolde: String?
        get() = dimensiones?.let { d ->
            runCatching {
                "${formatearNumero(d.areaCm2)} cm² · ${formatearNumero(d.volumenCm3)} cm³" +
                    (d.alturaMoldeCm?.let { " · ${formatearNumero(it)} cm de alto" } ?: "")
            }.getOrNull()
        }

    /**
     * Si la receta está recibiendo correcciones del catálogo.
     *
     * Se muestra porque es la diferencia invisible entre los dos orígenes: dos recetas con
     * las mismas medidas se comportan distinto según esto, y sin decirlo nadie lo sabría.
     */
    val enlazadaAlCatalogo: Boolean get() = moldeEnlazado != null
}

/**
 * El cerebro del paso "Rendimiento" de una receta (8.3).
 *
 * Lo propio de acá es la distinción que más caro sale si se confunde: **definir el molde por
 * primera vez no es reescalar**. La primera vez no hay original contra el cual comparar, así
 * que no hay factor, no se elige modo y las cantidades quedan como se escribieron. Del
 * segundo molde en adelante sí, y ahí hay que decidir qué se conserva (9.3).
 */
class RendimientoViewModel(
    private val recetaId: Long,
    private val recetas: RecetaRepositorio,
    private val moldes: MoldeRepositorio
) : ViewModel() {

    private val recargar = MutableStateFlow(0)
    private val mensaje = MutableStateFlow<String?>(null)
    private val _dialogo = MutableStateFlow<DialogoRendimiento>(DialogoRendimiento.Ninguno)

    /** Lo escrito en los dos campos, por su propio canal (12.2.1). */
    private val trozos = MutableStateFlow("1")
    private val pesoFinal = MutableStateFlow("")

    /**
     * Lo que hay abierto encima, **fuera del `combine`**.
     *
     * Misma razón que en los otros pasos: el cuadro de elegir molde tiene buscador y campos
     * de texto, y un campo que recibe su valor con retraso termina con el cursor donde no va.
     */
    val dialogo: StateFlow<DialogoRendimiento> = combine(
        _dialogo,
        moldes.observarTodos()
    ) { abierto, catalogo ->
        // Los candidatos se rellenan acá y no se escriben: la lista filtrada es una vista
        // del catálogo, no un dato que el cuadro tenga que mantener al día.
        if (abierto is DialogoRendimiento.ElegirMolde) {
            abierto.copy(candidatos = filtrarPor(catalogo, abierto.busqueda) { it.nombre })
        } else {
            abierto
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DialogoRendimiento.Ninguno
    )

    val estado: StateFlow<EstadoRendimiento> = combine(
        recargar,
        trozos,
        pesoFinal,
        mensaje,
        moldes.observarTodos()
    ) { _, trozosEscritos, pesoEscrito, mensajeActual, catalogo ->
        val rendimiento = recetas.obtenerRendimiento(recetaId)
        EstadoRendimiento(
            receta = recetas.obtener(recetaId),
            usaMolde = rendimiento?.usaMolde ?: false,
            dimensiones = rendimiento?.dimensiones,
            moldeEnlazado = rendimiento?.moldeOrigenId
                ?.let { id -> catalogo.firstOrNull { it.id == id } },
            trozos = trozosEscritos,
            pesoFinal = pesoEscrito,
            catalogoDeMoldes = catalogo,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoRendimiento()
    )

    init {
        // Los campos arrancan con lo que ya está guardado, en el formato de la app: así
        // guardar sin tocar nada no puede cambiar ningún número.
        viewModelScope.launch {
            recetas.obtenerRendimiento(recetaId)?.let { r ->
                trozos.value = r.trozos.toString()
                pesoFinal.value = r.pesoFinalG?.let { formatearNumero(it) } ?: ""
            }
        }
    }

    private fun volverALeer() {
        recargar.update { it + 1 }
    }

    // --- Los dos campos ---

    fun cambiarTrozos(texto: String) {
        // Sin decimales ni punto de mil: los trozos son unidades y "1.000 trozos" no existe.
        trozos.value = texto.filter { it.isDigit() }
    }

    fun cambiarPesoFinal(texto: String) {
        pesoFinal.value = formatearMientrasSeEscribe(texto)
    }

    fun guardar() {
        viewModelScope.launch {
            when (val r = recetas.guardarRendimiento(recetaId, trozos.value, pesoFinal.value)) {
                is Resultado.Listo -> mensaje.value = "Se guardó el rendimiento"
                is Resultado.NoSePudo -> mensaje.value = r.motivo
            }
            volverALeer()
        }
    }

    // --- El molde ---

    /**
     * Abre el cuadro de molde, sabiendo solo si es la primera vez o un reescalado.
     *
     * Esa distinción no la decide la pantalla: sale de si la receta ya tiene molde guardado.
     */
    fun abrirElegirMolde() {
        viewModelScope.launch {
            val actual = recetas.obtenerRendimiento(recetaId)
            _dialogo.value = DialogoRendimiento.ElegirMolde(
                esReescalado = actual?.usaMolde == true && actual.dimensiones != null
            )
        }
    }

    fun cambiarOrigenDelMolde(origen: OrigenDelMolde) = enElegirMolde {
        it.copy(origen = origen, rechazo = null)
    }

    fun buscarMolde(texto: String) = enElegirMolde { it.copy(busqueda = texto) }

    fun elegirMoldeGuardado(molde: Molde) = enElegirMolde {
        it.copy(elegido = molde, rechazo = null)
    }

    fun elegirFormaDePrueba(forma: TipoFormaMolde) = enElegirMolde {
        it.copy(forma = forma, rechazo = null)
    }

    fun cambiarMedidaDePrueba(campo: CampoDeMolde, texto: String) = enElegirMolde {
        it.copy(
            medidas = it.medidas + (campo to formatearMientrasSeEscribe(texto)),
            rechazo = null
        )
    }

    fun elegirModoDeReescalado(modo: ModoReescalado) = enElegirMolde {
        it.copy(modo = modo, rechazo = null)
    }

    /**
     * Aplica lo elegido: define el molde, o reescala.
     *
     * Las dos ramas van por funciones distintas del repositorio a propósito. Podría ser una
     * sola que "haga lo que corresponda", pero entonces un error en la condición reescalaría
     * una receta que solo quería estrenar molde, y eso no se ve hasta que las cantidades ya
     * están mal.
     */
    fun confirmarMolde() {
        val cuadro = _dialogo.value as? DialogoRendimiento.ElegirMolde ?: return
        if (!cuadro.puedeGuardar) return
        val dimensiones = cuadro.dimensiones ?: return

        _dialogo.value = cuadro.copy(guardando = true)

        viewModelScope.launch {
            val resultado = if (cuadro.esReescalado) {
                recetas.reescalarPorMolde(recetaId, dimensiones, cuadro.modo, cuadro.moldeOrigenId)
            } else {
                recetas.definirMolde(recetaId, dimensiones, cuadro.moldeOrigenId)
            }

            when (resultado) {
                is Resultado.Listo -> {
                    _dialogo.value = DialogoRendimiento.Ninguno
                    mensaje.value =
                        if (cuadro.esReescalado) "Se reescaló la receta al molde nuevo"
                        else "Listo, la receta ya tiene molde"
                }
                // Al campo y no a la franja de abajo: el cuadro sigue abierto y el motivo
                // -"Demasiado riesgo. Mejor escale con el otro método"- hay que leerlo ahí,
                // que es donde está el selector de modo que lo resuelve.
                is Resultado.NoSePudo -> enElegirMolde {
                    it.copy(guardando = false, rechazo = resultado.motivo)
                }
            }
            volverALeer()
        }
    }

    // --- Quitar el molde y reescalar por peso ---

    fun pedirQuitarMolde() {
        _dialogo.value = DialogoRendimiento.ConfirmarQuitarMolde
    }

    fun confirmarQuitarMolde() {
        viewModelScope.launch {
            when (val r = recetas.quitarMolde(recetaId)) {
                is Resultado.Listo -> mensaje.value = "La receta ya no usa molde"
                is Resultado.NoSePudo -> mensaje.value = r.motivo
            }
            _dialogo.value = DialogoRendimiento.Ninguno
            volverALeer()
        }
    }

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
            volverALeer()
        }
    }

    fun cerrarDialogo() {
        _dialogo.value = DialogoRendimiento.Ninguno
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun enElegirMolde(
        cambio: (DialogoRendimiento.ElegirMolde) -> DialogoRendimiento.ElegirMolde
    ) {
        _dialogo.update { actual ->
            if (actual is DialogoRendimiento.ElegirMolde) cambio(actual) else actual
        }
    }

    companion object {
        fun fabrica(
            recetaId: Long,
            recetas: RecetaRepositorio,
            moldes: MoldeRepositorio
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { RendimientoViewModel(recetaId, recetas, moldes) }
        }
    }
}
