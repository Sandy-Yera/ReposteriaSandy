package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * El título de la receta abierta, que ahora se observa **una sola vez para los cuatro pasos**.
 *
 * Antes lo observaba cada paso por su cuenta, y eso se veía en el celular: al cambiar de paso
 * el encabezado parpadeaba una vez por paso. La causa es que un `StateFlow` empieza por su
 * valor inicial y la base contesta después, así que cada observación nueva tiene su propio
 * instante en blanco. Estas pruebas cuidan las dos mitades: que el título **llegue**, y que
 * siga llegando cuando lo renombran desde el paso de cantidades.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TituloDeRecetaViewModelTest {

    private val despachador = StandardTestDispatcher()

    private lateinit var recetas: RecetaRepositorio
    private var recetaId: Long = 0

    @Before
    fun prepararTodo() {
        Dispatchers.setMain(despachador)
        recetas = RecetaRepositorio(
            RecetaDaoFalso(IngredienteDaoFalso()),
            HistorialRepositorio(HistorialDaoFalso())
        )
    }

    @After
    fun dejarTodoComoEstaba() {
        Dispatchers.resetMain()
    }

    // Con receptor `TestScope`, igual que el resto de las pruebas de ViewModel: sin él,
    // `advanceUntilIdle()` dentro del cuerpo no encuentra a quién avanzarle el reloj.
    private fun probar(cuerpo: suspend TestScope.(TituloDeRecetaViewModel) -> Unit) =
        runTest(despachador) {
            recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId
            val modelo = TituloDeRecetaViewModel(recetaId, recetas)
            advanceUntilIdle()
            cuerpo(modelo)
        }

    @Test
    fun `el titulo llega`() = probar { modelo ->
        assertEquals("Torta de manjar", modelo.titulo.value)
    }

    @Test
    fun `y se actualiza solo cuando lo renombran desde cantidades`() = probar { modelo ->
        // Los cuatro pasos muestran este mismo texto, y se renombra desde el primero. Sin
        // observar, el encabezado de los otros tres se quedaría con el nombre viejo.
        recetas.renombrar(recetaId, "Torta de manjar y nuez")
        advanceUntilIdle()

        assertEquals("Torta de manjar y nuez", modelo.titulo.value)
    }

    @Test
    fun `empieza vacio y no con un texto de relleno`() = runTest(despachador) {
        // El valor inicial es "" a propósito: cualquier otra cosa —"Cargando…", "Receta"—
        // sería un título falso dibujado en la barra durante ese instante, que es
        // exactamente el parpadeo que esto vino a sacar, con mejor letra.
        recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId

        val modelo = TituloDeRecetaViewModel(recetaId, recetas)

        assertEquals("", modelo.titulo.value)
    }

    @Test
    fun `una receta que ya no existe deja el titulo vacio, no revienta`() = probar { modelo ->
        // Borrar la receta abierta cierra la pantalla, pero el aviso de la base puede llegar
        // antes que el cierre: acá lo que no puede pasar es un `null` desreferenciado.
        recetas.confirmarEliminacion(recetaId)
        advanceUntilIdle()

        assertEquals("", modelo.titulo.value)
    }
}
