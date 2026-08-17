package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * El título de la receta abierta, **uno solo para los cuatro pasos**.
 *
 * Nació de un parpadeo que se veía en el celular: al cambiar de paso, el nombre de la receta
 * desaparecía por un instante y volvía. La causa no era el dibujo sino de quién salía el
 * texto — cada paso lo sacaba de su propio estado, o sea que **la misma fila se observaba
 * cuatro veces**. Un `StateFlow` empieza por su valor inicial y Room contesta después, así
 * que la primera vez que se entra a un paso su barra se dibuja con el título todavía vacío.
 * Cuatro observaciones son cuatro primeras veces.
 *
 * Con esto se observa una sola vez, al abrir la receta, y para cuando se cambia de paso el
 * título ya está. Los tres pasos que solo lo mostraban dejaron de pedir la receta entera:
 * es una consulta menos en cada uno, no una más acá.
 *
 * **Vive mientras la receta esté abierta**, no mientras se vea un paso: lo crea
 * `NavegacionPrincipal`, que es quien sabe qué receta hay abierta. Ese es todo el truco — si
 * lo creara cada pantalla volveríamos al parpadeo, con otro nombre.
 *
 * No expone la receta entera sino el texto ya resuelto. Quien necesita la fila completa es
 * el paso de cantidades, que además la renombra, y esa sigue siendo suya.
 */
class TituloDeRecetaViewModel(
    recetaId: Long,
    recetas: RecetaRepositorio
) : ViewModel() {

    val titulo: StateFlow<String> = recetas.observarReceta(recetaId)
        .map { it?.titulo.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            // Sin `WhileSubscribed`: mientras la receta esté abierta alguien lo está
            // mirando siempre, porque los cuatro pasos dibujan esta barra. Cortar la
            // consulta al cambiar de paso sería volver a empezar justo cuando no se debe.
            started = SharingStarted.Eagerly,
            initialValue = ""
        )

    companion object {
        fun fabrica(recetaId: Long, recetas: RecetaRepositorio) = viewModelFactory {
            initializer { TituloDeRecetaViewModel(recetaId, recetas) }
        }
    }
}
