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
import com.sandyyera.reposteria.logica.validaciones.CampoDeMolde
import com.sandyyera.reposteria.logica.validaciones.camposDe
import com.sandyyera.reposteria.logica.validaciones.dimensionesDesde
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

/** Qué hay abierto encima del paso del molde. */
sealed interface DialogoMoldeDeReceta {

    data object Ninguno : DialogoMoldeDeReceta

    /**
     * Elegir el molde: uno del catálogo o uno de prueba.
     *
     * [esReescalado] cambia lo que significa aceptar. En `false` es la primera vez y solo se
     * guardan las medidas — no hay original contra el cual comparar, así que no hay factor
     * ni modo que elegir (9.3). En `true` ya hay molde y hay que decidir qué se conserva.
     */
    data class Elegir(
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
    ) : DialogoMoldeDeReceta {

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

    /** La confirmación antes de dejar de usar molde. */
    data object ConfirmarQuitar : DialogoMoldeDeReceta
}

/** Lo que el paso del molde necesita para dibujarse. */
data class EstadoMoldeDeReceta(
    val receta: Receta? = null,
    val usaMolde: Boolean = false,
    val dimensiones: DimensionesMolde? = null,
    val moldeEnlazado: Molde? = null,
    /** Si hay peso anotado. De eso depende que se pueda quitar el molde (6.2). */
    val tienePesoFinal: Boolean = false,
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
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
 * El cerebro del paso "Molde" de una receta (8.3.1 y 9.3).
 *
 * **Salió de `RendimientoViewModel`, que hacía las dos cosas.** Rendimiento mezclaba dos
 * preguntas que se responden en momentos distintos —qué molde se usa, y cuánto rinde— y una
 * de ellas, el reescalado, es la operación más delicada de la app: multiplica todas las
 * cantidades de la receta. Compartir pantalla con dos campos de texto la dejaba a un toque
 * de distancia de quien solo venía a corregir los trozos (8.4.1, #2).
 *
 * **No confundir con `MoldesViewModel`** (en plural, en `ui/moldes/`): ese es el catálogo de
 * moldes de la app. Este es el molde de **una** receta, y lo único que hace con el catálogo
 * es leerlo para elegir de ahí.
 *
 * Lo propio de acá es la distinción que más caro sale si se confunde: **definir el molde por
 * primera vez no es reescalar**. La primera vez no hay original contra el cual comparar, así
 * que no hay factor, no se elige modo y las cantidades quedan como se escribieron. Del
 * segundo molde en adelante sí, y ahí hay que decidir qué se conserva (9.3).
 */
class MoldeDeRecetaViewModel(
    private val recetaId: Long,
    private val recetas: RecetaRepositorio,
    private val moldes: MoldeRepositorio
) : ViewModel() {

    private val recargar = MutableStateFlow(0)
    private val mensaje = MutableStateFlow<String?>(null)
    private val _dialogo = MutableStateFlow<DialogoMoldeDeReceta>(DialogoMoldeDeReceta.Ninguno)

    /**
     * Lo que hay abierto encima, **fuera del `combine` del estado** (12.2.1).
     *
     * El cuadro de elegir molde tiene buscador y hasta cuatro campos de texto, y un campo
     * que recibe su valor con retraso termina con el cursor donde no va.
     */
    val dialogo: StateFlow<DialogoMoldeDeReceta> = combine(
        _dialogo,
        moldes.observarTodos()
    ) { abierto, catalogo ->
        // Los candidatos se rellenan acá y no se escriben: la lista filtrada es una vista
        // del catálogo, no un dato que el cuadro tenga que mantener al día.
        if (abierto is DialogoMoldeDeReceta.Elegir) {
            abierto.copy(candidatos = filtrarPor(catalogo, abierto.busqueda) { it.nombre })
        } else {
            abierto
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DialogoMoldeDeReceta.Ninguno
    )

    val estado: StateFlow<EstadoMoldeDeReceta> = combine(
        recargar,
        mensaje,
        moldes.observarTodos()
    ) { _, mensajeActual, catalogo ->
        val rendimiento = recetas.obtenerRendimiento(recetaId)
        EstadoMoldeDeReceta(
            receta = recetas.obtener(recetaId),
            usaMolde = rendimiento?.usaMolde ?: false,
            dimensiones = rendimiento?.dimensiones,
            moldeEnlazado = rendimiento?.moldeOrigenId
                ?.let { id -> catalogo.firstOrNull { it.id == id } },
            tienePesoFinal = rendimiento?.pesoFinalG != null,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoMoldeDeReceta()
    )

    private fun volverALeer() {
        recargar.update { it + 1 }
    }

    /**
     * Abre el cuadro de molde, sabiendo solo si es la primera vez o un reescalado.
     *
     * Esa distinción no la decide la pantalla: sale de si la receta ya tiene molde guardado.
     */
    fun abrirElegirMolde() {
        viewModelScope.launch {
            val actual = recetas.obtenerRendimiento(recetaId)
            _dialogo.value = DialogoMoldeDeReceta.Elegir(
                esReescalado = actual?.usaMolde == true && actual.dimensiones != null
            )
        }
    }

    fun cambiarOrigenDelMolde(origen: OrigenDelMolde) = enElegir {
        it.copy(origen = origen, rechazo = null)
    }

    fun buscarMolde(texto: String) = enElegir { it.copy(busqueda = texto) }

    fun elegirMoldeGuardado(molde: Molde) = enElegir { it.copy(elegido = molde, rechazo = null) }

    fun elegirFormaDePrueba(forma: TipoFormaMolde) = enElegir {
        it.copy(forma = forma, rechazo = null)
    }

    fun cambiarMedidaDePrueba(campo: CampoDeMolde, texto: String) = enElegir {
        it.copy(
            medidas = it.medidas + (campo to formatearMientrasSeEscribe(texto)),
            rechazo = null
        )
    }

    fun elegirModoDeReescalado(modo: ModoReescalado) = enElegir {
        it.copy(modo = modo, rechazo = null)
    }

    /**
     * Aplica lo elegido: define el molde, o reescala.
     *
     * Las dos ramas van por funciones distintas del repositorio a propósito. Podría ser una
     * sola que "haga lo que corresponda", pero entonces un error en la condición reescalaría
     * una receta que solo quería estrenar molde, y eso no se ve hasta que las cantidades ya
     * están mal.
     *
     * El aviso de un reescalado **manda a rendimiento**: ahí quedó el peso multiplicado
     * esperando que alguien lo compruebe (8.4.1, #4), y desde acá no se ve.
     */
    fun confirmarMolde() {
        val cuadro = _dialogo.value as? DialogoMoldeDeReceta.Elegir ?: return
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
                    _dialogo.value = DialogoMoldeDeReceta.Ninguno
                    mensaje.value = if (cuadro.esReescalado) {
                        "Se reescaló la receta. Revisa el peso en Rendimiento."
                    } else {
                        "Listo, la receta ya tiene molde"
                    }
                }
                // Al cuadro y no a la franja de abajo: sigue abierto y el motivo
                // -"Demasiado riesgo. Mejor escale con el otro método"- hay que leerlo ahí,
                // que es donde está el selector de modo que lo resuelve.
                is Resultado.NoSePudo -> enElegir {
                    it.copy(guardando = false, rechazo = resultado.motivo)
                }
            }
            volverALeer()
        }
    }

    fun pedirQuitarMolde() {
        _dialogo.value = DialogoMoldeDeReceta.ConfirmarQuitar
    }

    fun confirmarQuitarMolde() {
        viewModelScope.launch {
            when (val r = recetas.quitarMolde(recetaId)) {
                is Resultado.Listo -> mensaje.value = "La receta ya no usa molde"
                is Resultado.NoSePudo -> mensaje.value = r.motivo
            }
            _dialogo.value = DialogoMoldeDeReceta.Ninguno
            volverALeer()
        }
    }

    fun cerrarDialogo() {
        _dialogo.value = DialogoMoldeDeReceta.Ninguno
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun enElegir(
        cambio: (DialogoMoldeDeReceta.Elegir) -> DialogoMoldeDeReceta.Elegir
    ) {
        _dialogo.update { actual ->
            if (actual is DialogoMoldeDeReceta.Elegir) cambio(actual) else actual
        }
    }

    companion object {
        fun fabrica(
            recetaId: Long,
            recetas: RecetaRepositorio,
            moldes: MoldeRepositorio
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { MoldeDeRecetaViewModel(recetaId, recetas, moldes) }
        }
    }
}
