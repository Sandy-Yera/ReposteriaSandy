package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.busqueda.filtrarPor
import com.sandyyera.reposteria.logica.busqueda.marcarRepetidos
import com.sandyyera.reposteria.logica.validaciones.errorEnTituloReceta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Una receta de la lista junto con lo que cuesta hacerla ahora mismo.
 *
 * [repetida] marca las que quedaron con un título que ya usaba otra receta anterior. Se
 * pueden ver y borrar, pero no abrir: son datos de antes de que se prohibieran
 * los títulos repetidos, y borrarlas solas sería hacer desaparecer trabajo sin preguntar.
 */
data class RecetaConCosto(
    val receta: Receta,
    val costoTotal: Double,
    /**
     * Si la receta tiene al menos un ingrediente cargado.
     *
     * **Es un dato aparte del costo, y tiene que serlo.** Antes la pantalla deducía "todavía
     * sin ingredientes" de que el costo fuera 0, y eso está mal: un ingrediente puede valer
     * 0 a propósito —así se dice "esto no suma al costo" (6.2)— y una receta llena de ellos
     * aparecía como vacía. Costar cero y no tener nada cargado son dos cosas distintas y la
     * pantalla necesita distinguirlas.
     */
    val tieneIngredientes: Boolean = false,
    val repetida: Boolean = false,
    /**
     * Si alguna de sus partes traídas tiene un aviso pendiente (8.11.3).
     *
     * Va **también acá afuera** y no solo dentro de la receta porque el aviso es sobre algo que
     * pasó en *otra* receta: sin la marca en la lista, la única forma de enterarse sería entrar
     * a cada una a mirar, que es exactamente lo que un aviso existe para evitar.
     */
    val tieneAvisoDeParte: Boolean = false
)

/** Lo que se muestra al tocar una receta que quedó repetida. */
const val AVISO_RECETA_REPETIDA =
    "Esta receta quedó con un título repetido, que ya no se permite. Solo se puede " +
        "eliminar. Si la necesitas, borra la otra y créala de nuevo."

/** Qué hay abierto encima de la lista de recetas. */
sealed interface DialogoReceta {

    data object Ninguno : DialogoReceta

    /** El aviso al intentar hacer algo con una receta repetida que no sea borrarla. */
    data class Bloqueada(val receta: Receta) : DialogoReceta

    /**
     * El formulario de alta.
     *
     * **Solo crea; ya no renombra.** Cambiarle el título a una receta que ya existe se hace
     * desde adentro, tocando el título en el encabezado del paso de cantidades: acá el toque
     * está tomado por abrirla, que es lo que se hace cien veces por cada renombrado. Por eso
     * este cuadro perdió su campo `editando` en vez de quedárselo por si acaso — una rama
     * que nadie recorre es una rama que nadie prueba.
     *
     * Igual que en ingredientes, [tocado] evita retar por un campo vacío que todavía nadie
     * llenó.
     */
    data class Formulario(
        val titulo: String = "",
        val tocado: Boolean = false,
        val tituloRepetido: String? = null,
        val guardando: Boolean = false
    ) : DialogoReceta {

        val error: String?
            get() = tituloRepetido ?: errorEnTituloReceta(titulo).takeIf { tocado }

        val puedeGuardar: Boolean get() = errorEnTituloReceta(titulo) == null && !guardando
    }

    /**
     * La advertencia previa a borrar (6.3 y 8.11.4).
     *
     * Lo que se pierde *dentro* de la receta se puede enumerar de inmediato: está todo adentro.
     * Lo que hay que consultar es lo de **afuera** — qué otras recetas la usan como parte—, y
     * por eso [usadaPor] llega después, con la misma distinción que la advertencia de borrar un
     * ingrediente: `null` es "todavía consultando" y lista vacía es "no la usa ninguna".
     * Confundirlos dejaría borrar sin haber mostrado la advertencia completa.
     *
     * El tono es distinto al de un ingrediente a propósito: borrar una receta usada por otras
     * **no rompe nada de inmediato** —las copias son independientes y siguen ahí— pero deja un
     * aviso pendiente en cada una, y descubrirlo meses después no tendría explicación.
     */
    data class ConfirmarBorrado(
        val receta: Receta,
        val usadaPor: List<Receta>? = null,
        val borrando: Boolean = false
    ) : DialogoReceta {
        /** Mientras la consulta no vuelva no se puede confirmar: faltaría la mitad del aviso. */
        val sePuedeBorrar: Boolean get() = usadaPor != null && !borrando
    }
}

/** Lo que la pantalla de recetas necesita para dibujarse. */
data class EstadoRecetas(
    val visibles: List<RecetaConCosto> = emptyList(),
    val hayRecetas: Boolean = false,
    val busqueda: String = "",
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    val catalogoVacio: Boolean get() = !cargando && !hayRecetas
    val busquedaSinResultados: Boolean get() = hayRecetas && visibles.isEmpty()
}

/**
 * El cerebro de la lista de recetas.
 *
 * El costo de cada receta se pide **en lote** y no una por una: con veinte recetas, una
 * consulta por cada una en cada cambio sería veinte viajes a la base para dibujar una
 * pantalla que cabe en la mano.
 */
class RecetasViewModel(
    private val repositorio: RecetaRepositorio
) : ViewModel() {

    private val busqueda = MutableStateFlow("")
    private val _dialogo = MutableStateFlow<DialogoReceta>(DialogoReceta.Ninguno)
    private val mensaje = MutableStateFlow<String?>(null)
    private val _recienCreada = MutableStateFlow<Long?>(null)

    /**
     * Lo que hay abierto encima, por su propio canal.
     *
     * Va aparte de [estado] por la misma razón que en el paso de cantidades: el `combine`
     * de abajo consulta el costo de todas las recetas, así que emite con retraso, y un
     * campo de texto que recibe su valor tarde termina con el cursor donde no va.
     */
    val dialogo: StateFlow<DialogoReceta> = _dialogo

    /**
     * La receta que se acaba de crear, para abrirla de inmediato.
     *
     * Crear una y después tener que buscarla en la lista para empezar a llenarla es un
     * paso de más justo cuando uno ya sabe lo que quiere hacer. La pantalla la abre y
     * llama a [recetaAbierta] para limpiar el aviso.
     */
    val recienCreada: StateFlow<Long?> = _recienCreada

    /**
     * Las recetas con su costo, atados a que **los dos** avisen cuando cambian.
     *
     * Antes esto era `observarTodas().map { costosDe(...) }`: el costo se pedía de una sola
     * vez, colgado del aviso de la tabla `recetas`. Borrar un ingrediente no toca esa tabla,
     * así que nada volvía a preguntar y la lista se quedaba mostrando costos que ya no
     * existían. Se veía tal cual: borrar todos los ingredientes y volver a Recetas, y ahí
     * seguían los mismos números.
     *
     * Con `combine` de dos `Flow`, cualquiera de los dos que cambie rearma la lista, y el de
     * costos lo emite Room al tocarse los ingredientes, las secciones **o** el catálogo. Sin
     * nadie que tenga que acordarse de refrescar.
     */
    private val conCosto = combine(
        repositorio.observarTodas(),
        repositorio.observarCostos(),
        // Los avisos entran como un flujo más y no como una consulta dentro de la
        // transformación, por lo mismo que el costo: dependen de tablas que esta consulta no
        // mira —los ingredientes y los pasos de **otra** receta— y una foto de un momento se
        // quedaría vieja justo cuando hay algo que avisar (8.11.3).
        repositorio.observarRecetasConAviso()
    ) { recetas, costos, conAviso ->
        // Se marcan las repetidas sobre la lista ordenada por antigüedad, no por título:
        // así la que se conserva utilizable es la original y no una cualquiera.
        val porAntiguedad = recetas.sortedBy { it.id }
        val repetidas = marcarRepetidos(porAntiguedad) { it.titulo }
            .withIndex()
            .filter { it.value }
            .map { porAntiguedad[it.index].id }
            .toSet()
        recetas.map { receta ->
            RecetaConCosto(
                receta = receta,
                costoTotal = costos[receta.id] ?: 0.0,
                // La consulta agrupa por receta, así que **solo trae fila para las que
                // tienen algo cargado**: estar en el mapa es exactamente "tiene
                // ingredientes", y un 0 ahí adentro es "los tiene, y no suman nada".
                tieneIngredientes = receta.id in costos,
                repetida = receta.id in repetidas,
                tieneAvisoDeParte = receta.id in conAviso
            )
        }
    }

    val estado: StateFlow<EstadoRecetas> = combine(
        conCosto,
        busqueda,
        mensaje
    ) { todas, textoBuscado, mensajeActual ->
        EstadoRecetas(
            visibles = filtrarPor(todas, textoBuscado) { it.receta.titulo },
            hayRecetas = todas.isNotEmpty(),
            busqueda = textoBuscado,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoRecetas()
    )

    fun buscar(texto: String) {
        busqueda.value = texto
    }

    fun abrirAlta() {
        _dialogo.value = DialogoReceta.Formulario()
    }

    fun cambiarTitulo(texto: String) = enFormulario {
        // Al cambiar el título el aviso de repetido deja de aplicar: era sobre el anterior.
        it.copy(titulo = texto, tocado = true, tituloRepetido = null)
    }

    fun guardar() {
        val formulario = _dialogo.value as? DialogoReceta.Formulario ?: return
        if (!formulario.puedeGuardar) return

        val titulo = formulario.titulo.trim()
        _dialogo.value = formulario.copy(guardando = true)

        viewModelScope.launch {
            when (val resultado = repositorio.crear(titulo)) {
                is ResultadoCrearReceta.Creada -> {
                    _dialogo.value = DialogoReceta.Ninguno
                    // Se abre sola: crear una receta y después tener que buscarla en la
                    // lista para empezar a llenarla es un paso de más justo cuando uno ya
                    // sabe lo que quiere hacer.
                    _recienCreada.value = resultado.recetaId
                }
                is ResultadoCrearReceta.YaExiste -> enFormulario {
                    it.copy(
                        guardando = false,
                        tituloRepetido = "Ya tienes una receta que se llama " +
                            "'${resultado.existente.titulo}'"
                    )
                }
                is ResultadoCrearReceta.NoValido -> {
                    enFormulario { it.copy(guardando = false) }
                    mensaje.value = resultado.motivo
                }
            }
        }
    }

    /**
     * Abre la advertencia de borrado y va a buscar a quién afecta (8.11.4).
     *
     * El cuadro se abre al instante y la lista se completa cuando la consulta vuelve, igual que
     * el de ingredientes. Al llegar se comprueba que el cuadro **siga abierto y sea la misma
     * receta**: entre abrir y responder pudo abrirse otro, y pegarle ahí la lista de una receta
     * distinta enumeraría recetas que no tienen nada que ver.
     */
    fun pedirBorrado(receta: Receta) {
        _dialogo.value = DialogoReceta.ConfirmarBorrado(receta)
        viewModelScope.launch {
            val usadaPor = repositorio.recetasQueUsanEstaReceta(receta.id)
            _dialogo.update { actual ->
                if (actual is DialogoReceta.ConfirmarBorrado && actual.receta.id == receta.id) {
                    actual.copy(usadaPor = usadaPor)
                } else {
                    actual
                }
            }
        }
    }

    fun confirmarBorrado() {
        val aviso = _dialogo.value as? DialogoReceta.ConfirmarBorrado ?: return
        if (!aviso.sePuedeBorrar) return

        _dialogo.value = aviso.copy(borrando = true)

        viewModelScope.launch {
            repositorio.confirmarEliminacion(aviso.receta.id)
            _dialogo.value = DialogoReceta.Ninguno
            mensaje.value = "Se eliminó '${aviso.receta.titulo}'"
        }
    }

    /** Se llama al intentar abrir una receta que quedó repetida. */
    fun avisarBloqueada(receta: Receta) {
        _dialogo.value = DialogoReceta.Bloqueada(receta)
    }

    /** La pantalla avisa que ya abrió la receta recién creada. */
    fun recetaAbierta() {
        _recienCreada.value = null
    }

    fun cerrarDialogo() {
        _dialogo.value = DialogoReceta.Ninguno
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun enFormulario(cambio: (DialogoReceta.Formulario) -> DialogoReceta.Formulario) {
        _dialogo.update { actual ->
            if (actual is DialogoReceta.Formulario) cambio(actual) else actual
        }
    }

    companion object {
        fun fabrica(repositorio: RecetaRepositorio): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { RecetasViewModel(repositorio) }
            }
    }
}
