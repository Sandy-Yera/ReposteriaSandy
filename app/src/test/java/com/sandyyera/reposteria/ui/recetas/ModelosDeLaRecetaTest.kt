package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Que los ViewModel de una receta se suelten al cerrarla.
 *
 * Es una prueba de **memoria y no de pantalla**, y por eso se puede escribir sin celular: lo
 * único que hay que comprobar es quién queda vivo. Sin esto, cada receta abierta dejaba sus
 * seis pasos —más el del título— en el `ViewModelStore` de la Activity hasta cerrar la app, y
 * con ellos sus observadores de la base, que **no se detienen** al dejar de mirarse (a
 * diferencia del `WhileSubscribed(5s)` de los estados). Guardar un precio terminaba
 * despertando a los de todas las recetas abiertas en la sesión. Eso no se nota probando una
 * receta y aparece después de un rato de uso, que es como lo describió Sandy: "al moverme por
 * la app, se veía algo lento".
 *
 * Se usa `viewModelStore.put(...)` directo y **no un `ViewModelProvider`**: aquel construye por
 * reflexión y arrastra piezas del entorno de Android que en una prueba de JVM pura no están.
 * Acá lo que se prueba es el ciclo de vida, no cómo se instancian.
 */
class ModelosDeLaRecetaTest {

    /** Un ViewModel de mentira que anota si lo limpiaron. */
    private class ModeloEspia : ViewModel() {
        var limpiado = false
            private set

        public override fun onCleared() {
            limpiado = true
        }
    }

    /** Deja un espía guardado en el dueño de esa receta, como haría una pantalla. */
    private fun ponerEspia(dueño: ViewModelStoreOwner, clave: String = "paso"): ModeloEspia =
        ModeloEspia().also { dueño.viewModelStore.put(clave, it) }

    @Test
    fun `el mismo id devuelve el mismo dueño, para no rehacer los pasos al cambiar de paso`() {
        // Cambiar de paso no puede perder lo que estaba abierto ni obligar a releer todo.
        val modelos = ModelosDeLaReceta()

        assertSame(modelos.de(7), modelos.de(7))
    }

    @Test
    fun `dos recetas no comparten sus modelos`() {
        // Sin esto, abrir una segunda receta reutilizaría el ViewModel de la primera y
        // mostraría los ingredientes equivocados. Antes lo garantizaba la clave
        // `"cantidades-<id>"`; ahora lo garantiza el dueño, así que se comprueba acá.
        val modelos = ModelosDeLaReceta()

        assertNotSame(modelos.de(7), modelos.de(8))
    }

    @Test
    fun `cerrar una receta limpia sus modelos`() {
        val modelos = ModelosDeLaReceta()
        val espia = ponerEspia(modelos.de(7))
        assertFalse("Recién creado, nadie lo limpió", espia.limpiado)

        modelos.cerrar(7)

        assertTrue("Al cerrar la receta se suelta, y con él sus observadores", espia.limpiado)
    }

    @Test
    fun `cerrar una receta no toca las otras`() {
        val modelos = ModelosDeLaReceta()
        val deLa7 = ponerEspia(modelos.de(7))
        val deLa8 = ponerEspia(modelos.de(8))

        modelos.cerrar(7)

        assertTrue(deLa7.limpiado)
        assertFalse("La 8 sigue abierta y no se la puede llevar por delante", deLa8.limpiado)
    }

    @Test
    fun `volver a abrir la misma receta arma modelos nuevos`() {
        // Lo contrario sería peor que no limpiar: entregar un ViewModel ya limpiado, con sus
        // corrutinas canceladas, se ve como una pantalla que no responde.
        val modelos = ModelosDeLaReceta()
        val primeraVez = modelos.de(7)
        modelos.cerrar(7)

        assertNotSame(primeraVez, modelos.de(7))
    }

    @Test
    fun `cerrar una receta que no estaba abierta no hace nada`() {
        // La X puede llegar dos veces —un toque doble— y eso no puede reventar.
        val modelos = ModelosDeLaReceta()
        modelos.cerrar(7)
        modelos.cerrar(7)
    }

    @Test
    fun `los duenos no se acumulan al abrir y cerrar muchas recetas`() {
        // Es la prueba del problema en sí: cien recetas abiertas y cerradas tienen que dejar
        // la memoria como estaba. Se mide por los limpiados, que es lo observable desde acá.
        val modelos = ModelosDeLaReceta()

        val espias = (1L..100L).map { id ->
            ponerEspia(modelos.de(id)).also { modelos.cerrar(id) }
        }

        assertEquals("Ninguno quedó vivo", 100, espias.count { it.limpiado })
    }
}
