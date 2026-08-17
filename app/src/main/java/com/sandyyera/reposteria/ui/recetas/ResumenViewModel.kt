package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResumenDeReceta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Qué parte del resumen está abierta (8.12).
 *
 * **Una sola a la vez**, y esa es la decisión que hace útil el acordeón: con todas abiertas la
 * pantalla es la receta entera desplegada —que es justo lo que ya se puede ver recorriendo los
 * pasos— y el resumen deja de resumir. Con una, lo que se ve arriba es el índice de la receta.
 *
 * `null` es todas cerradas, que es cómo arranca: lo primero que se quiere ver al abrir una receta
 * es de qué partes está hecha, no el contenido de la primera.
 */
data class EstadoResumen(
    val resumen: ResumenDeReceta? = null,
    val abierta: PasoDeReceta? = null,
    val cargando: Boolean = true
) {
    /** Si la receta ya no existe. La pantalla se cierra sola en vez de mostrar un hueco. */
    val desaparecio: Boolean get() = !cargando && resumen == null

    fun estaAbierta(parte: PasoDeReceta): Boolean = abierta == parte
}

/**
 * El cerebro del resumen de una receta (8.12).
 *
 * **Un solo observador para toda la receta**, y de ahí sale casi todo lo que hace: el resumen
 * muestra las siete partes a la vez, así que suscribirse por separado a cada una daría siete
 * recomposiciones por cambio y siete primeros instantes en blanco. El armado vive en el
 * repositorio (`observarResumen`) porque es donde están las consultas; acá solo se agrega qué
 * panel está abierto, que es lo único que esta pantalla decide por su cuenta.
 *
 * **No edita nada.** Editar sigue viviendo en el paso que corresponde y desde acá se salta a él:
 * duplicar los formularios dejaría dos lugares donde arreglar cada error, y las pantallas de los
 * pasos ya tienen resueltos sus casos raros —el peso sin revisar, el bautizo de la primera
 * sección, la promoción que no cabe—.
 */
class ResumenViewModel(
    recetaId: Long,
    recetas: RecetaRepositorio
) : ViewModel() {

    private val abierta = MutableStateFlow<PasoDeReceta?>(null)

    val estado: StateFlow<EstadoResumen> = combine(
        recetas.observarResumen(recetaId),
        abierta
    ) { resumen, cual ->
        EstadoResumen(resumen = resumen, abierta = cual, cargando = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoResumen()
    )

    /**
     * El título, aparte del resto.
     *
     * Existe porque el encabezado de la pantalla lo necesita antes que todo lo demás, y esperar
     * al resumen completo dejaría la barra en blanco el primer instante — el mismo parpadeo que
     * ya costó centralizar el título en la navegación.
     */
    val titulo: StateFlow<String> = estado
        .map { it.resumen?.titulo.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /**
     * Abre una parte, o la cierra si ya estaba abierta.
     *
     * Tocar la que está abierta la cierra en vez de no hacer nada: es lo que se espera de un
     * acordeón, y sin eso no habría forma de volver a ver el índice completo sin abrir otra.
     */
    fun alternar(parte: PasoDeReceta) {
        abierta.value = if (abierta.value == parte) null else parte
    }

    companion object {
        fun fabrica(recetaId: Long, recetas: RecetaRepositorio): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ResumenViewModel(recetaId, recetas) }
            }
    }
}
