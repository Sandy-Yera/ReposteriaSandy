package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.busqueda.filtrarPor
import com.sandyyera.reposteria.logica.busqueda.marcarRepetidos
import com.sandyyera.reposteria.logica.validaciones.errorEnTituloReceta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Una receta de la lista junto con lo que cuesta hacerla ahora mismo.
 *
 * [repetida] marca las que quedaron con un título que ya usaba otra receta anterior. Se
 * pueden ver y borrar, pero no abrir ni renombrar: son datos de antes de que se prohibieran
 * los títulos repetidos, y borrarlas solas sería hacer desaparecer trabajo sin preguntar.
 */
data class RecetaConCosto(
    val receta: Receta,
    val costoTotal: Double,
    val repetida: Boolean = false
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
     * El formulario de alta o de cambio de título.
     *
     * [editando] es `null` al crear. Igual que en ingredientes, [tocado] evita retar por
     * un campo vacío que todavía nadie llenó.
     */
    data class Formulario(
        val editando: Receta? = null,
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
     * La advertencia previa a borrar (6.3).
     *
     * A diferencia de un ingrediente, acá no hay que consultar nada antes: lo que se pierde
     * está todo adentro de la receta. Por eso el aviso puede enumerarlo de inmediato.
     */
    data class ConfirmarBorrado(
        val receta: Receta,
        val borrando: Boolean = false
    ) : DialogoReceta
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

    private val conCosto = repositorio.observarTodas().map { recetas ->
        val costos = repositorio.costosDe(recetas.map { it.id })
        // Se marcan las repetidas sobre la lista ordenada por antigüedad, no por título:
        // así la que se conserva utilizable es la original y no una cualquiera.
        val porAntiguedad = recetas.sortedBy { it.id }
        val repetidas = marcarRepetidos(porAntiguedad) { it.titulo }
            .withIndex()
            .filter { it.value }
            .map { porAntiguedad[it.index].id }
            .toSet()
        recetas.map { RecetaConCosto(it, costos[it.id] ?: 0.0, it.id in repetidas) }
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

    fun abrirCambioDeTitulo(receta: Receta) {
        _dialogo.value = DialogoReceta.Formulario(
            editando = receta,
            titulo = receta.titulo,
            tocado = true
        )
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
            val original = formulario.editando
            if (original == null) {
                when (val resultado = repositorio.crear(titulo)) {
                    is ResultadoCrearReceta.Creada -> {
                        _dialogo.value = DialogoReceta.Ninguno
                        // Se abre sola: crear una receta y después tener que buscarla en
                        // la lista para empezar a llenarla es un paso de más justo cuando
                        // uno ya sabe lo que quiere hacer.
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
            } else {
                when (val resultado = repositorio.renombrar(original.id, titulo)) {
                    is Resultado.Listo -> {
                        _dialogo.value = DialogoReceta.Ninguno
                        mensaje.value = "Se guardó '$titulo'"
                    }
                    is Resultado.NoSePudo -> enFormulario {
                        it.copy(guardando = false, tituloRepetido = resultado.motivo)
                    }
                }
            }
        }
    }

    fun pedirBorrado(receta: Receta) {
        _dialogo.value = DialogoReceta.ConfirmarBorrado(receta)
    }

    fun confirmarBorrado() {
        val aviso = _dialogo.value as? DialogoReceta.ConfirmarBorrado ?: return
        if (aviso.borrando) return

        _dialogo.value = aviso.copy(borrando = true)

        viewModelScope.launch {
            repositorio.confirmarEliminacion(aviso.receta.id)
            _dialogo.value = DialogoReceta.Ninguno
            mensaje.value = "Se eliminó '${aviso.receta.titulo}'"
        }
    }

    /** Se llama al intentar abrir o renombrar una receta que quedó repetida. */
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
