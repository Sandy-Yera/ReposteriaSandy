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
import com.sandyyera.reposteria.logica.validaciones.errorEnTituloReceta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Una receta de la lista junto con lo que cuesta hacerla ahora mismo. */
data class RecetaConCosto(val receta: Receta, val costoTotal: Double)

/** Qué hay abierto encima de la lista de recetas. */
sealed interface DialogoReceta {

    data object Ninguno : DialogoReceta

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
        val guardando: Boolean = false
    ) : DialogoReceta {

        val error: String? get() = errorEnTituloReceta(titulo).takeIf { tocado }
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
    val dialogo: DialogoReceta = DialogoReceta.Ninguno,
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
    private val dialogo = MutableStateFlow<DialogoReceta>(DialogoReceta.Ninguno)
    private val mensaje = MutableStateFlow<String?>(null)

    private val conCosto = repositorio.observarTodas().map { recetas ->
        val costos = repositorio.costosDe(recetas.map { it.id })
        recetas.map { RecetaConCosto(it, costos[it.id] ?: 0.0) }
    }

    val estado: StateFlow<EstadoRecetas> = combine(
        conCosto,
        busqueda,
        dialogo,
        mensaje
    ) { todas, textoBuscado, dialogoActual, mensajeActual ->
        EstadoRecetas(
            visibles = filtrarPor(todas, textoBuscado) { it.receta.titulo },
            hayRecetas = todas.isNotEmpty(),
            busqueda = textoBuscado,
            dialogo = dialogoActual,
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
        dialogo.value = DialogoReceta.Formulario()
    }

    fun abrirCambioDeTitulo(receta: Receta) {
        dialogo.value = DialogoReceta.Formulario(
            editando = receta,
            titulo = receta.titulo,
            tocado = true
        )
    }

    fun cambiarTitulo(texto: String) = enFormulario { it.copy(titulo = texto, tocado = true) }

    fun guardar() {
        val formulario = dialogo.value as? DialogoReceta.Formulario ?: return
        if (!formulario.puedeGuardar) return

        val titulo = formulario.titulo.trim()
        dialogo.value = formulario.copy(guardando = true)

        viewModelScope.launch {
            val original = formulario.editando
            if (original == null) {
                when (val resultado = repositorio.crear(titulo)) {
                    is ResultadoCrearReceta.Creada -> {
                        dialogo.value = DialogoReceta.Ninguno
                        mensaje.value = "Se creó '$titulo'"
                    }
                    is ResultadoCrearReceta.NoValido -> {
                        enFormulario { it.copy(guardando = false) }
                        mensaje.value = resultado.motivo
                    }
                }
            } else {
                when (val resultado = repositorio.renombrar(original.id, titulo)) {
                    is Resultado.Listo -> {
                        dialogo.value = DialogoReceta.Ninguno
                        mensaje.value = "Se guardó '$titulo'"
                    }
                    is Resultado.NoSePudo -> {
                        enFormulario { it.copy(guardando = false) }
                        mensaje.value = resultado.motivo
                    }
                }
            }
        }
    }

    fun pedirBorrado(receta: Receta) {
        dialogo.value = DialogoReceta.ConfirmarBorrado(receta)
    }

    fun confirmarBorrado() {
        val aviso = dialogo.value as? DialogoReceta.ConfirmarBorrado ?: return
        if (aviso.borrando) return

        dialogo.value = aviso.copy(borrando = true)

        viewModelScope.launch {
            repositorio.confirmarEliminacion(aviso.receta.id)
            dialogo.value = DialogoReceta.Ninguno
            mensaje.value = "Se eliminó '${aviso.receta.titulo}'"
        }
    }

    fun cerrarDialogo() {
        dialogo.value = DialogoReceta.Ninguno
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun enFormulario(cambio: (DialogoReceta.Formulario) -> DialogoReceta.Formulario) {
        dialogo.update { actual ->
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
