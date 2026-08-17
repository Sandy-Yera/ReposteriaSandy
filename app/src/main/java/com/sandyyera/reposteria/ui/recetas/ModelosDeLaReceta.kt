package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Dónde viven los ViewModel de **la receta que está abierta**, para poder soltarlos al cerrarla.
 *
 * Nació de una lentitud que Sandy notó usando la app: se movía bien al principio y se iba
 * poniendo pesada. La causa no estaba en ninguna pantalla sino en cuánto duraban.
 *
 * `viewModel()` sin dueño propio guarda en el de la **Activity**, así que los seis pasos de cada
 * receta —más el del título— quedaban vivos hasta cerrar la app. Abrir diez recetas dejaba
 * decenas de ViewModel en memoria, y no dormidos: cuatro de ellos observan la base con un
 * `viewModelScope.launch { ... .collect { } }`, que **no se detiene** cuando la pantalla deja de
 * mirarse (a diferencia del `WhileSubscribed(5s)` de los estados). Room le avisa a todos los
 * observadores registrados, así que guardar un precio terminaba re-ejecutando la consulta de
 * precios una vez por cada receta que se hubiera abierto en la sesión. Eso no se ve al probar
 * una receta y aparece justo cuando se lleva un rato usando la app, que es lo que ella describió.
 *
 * **Es un ViewModel y no un `remember`**, y esa es toda la gracia: así sobrevive a girar el
 * teléfono —lo que se está escribiendo en un cuadro no se pierde— y aun así se puede vaciar a
 * mano, cosa que el de la Activity no permite (`clear()` se lleva todo, incluidos los de las
 * listas). Un `remember` habría arreglado la memoria rompiendo el giro.
 */
class ModelosDeLaReceta : ViewModel() {

    private val porReceta = mutableMapOf<Long, DueñoDeUnaReceta>()

    /** El dueño de los ViewModel de esa receta, creándolo la primera vez. */
    fun de(recetaId: Long): ViewModelStoreOwner =
        porReceta.getOrPut(recetaId) { DueñoDeUnaReceta() }

    /**
     * Suelta los ViewModel de una receta. Se llama **al cerrarla**, no al cambiar de paso.
     *
     * Cambiar de paso tiene que conservarlos: volver de gastos a cantidades no puede perder
     * el cuadro que estaba abierto ni obligar a releer todo. Lo que sobra es la receta que ya
     * se cerró.
     */
    fun cerrar(recetaId: Long) {
        porReceta.remove(recetaId)?.viewModelStore?.clear()
    }

    /**
     * Al morir la Activity se limpian todos.
     *
     * Sin esto quedaría exactamente el problema que esta clase viene a resolver, un piso más
     * arriba: los `ViewModelStore` propios no cuelgan de ningún otro, así que nadie los
     * vaciaría y sus `onCleared` no correrían nunca.
     */
    override fun onCleared() {
        porReceta.values.forEach { it.viewModelStore.clear() }
        porReceta.clear()
    }

    private class DueñoDeUnaReceta : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}
