package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas de la lista de recetas, sin base de datos ni celular.
 *
 * **No existían**, y este archivo nace de un error que se vio en el celular: una receta
 * hecha con ingredientes que valen 0 aparecía como "todavía sin ingredientes". Es el tipo
 * de cosa que ninguna prueba de repositorio puede ver, porque el repositorio devuelve el
 * número correcto —0— y el error está en lo que la pantalla concluye de ese número.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecetasViewModelTest {

    private val despachador = StandardTestDispatcher()

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var recetaDao: RecetaDaoFalso
    private lateinit var recetas: RecetaRepositorio
    private lateinit var ingredientes: IngredienteRepositorio

    @Before
    fun prepararTodo() {
        Dispatchers.setMain(despachador)
        catalogo = IngredienteDaoFalso()
        recetaDao = RecetaDaoFalso(catalogo)
        val historial = HistorialRepositorio(HistorialDaoFalso())
        recetas = RecetaRepositorio(recetaDao, historial)
        ingredientes = IngredienteRepositorio(catalogo, recetaDao, historial)
    }

    @After
    fun dejarTodoComoEstaba() {
        Dispatchers.resetMain()
    }

    private fun probar(cuerpo: suspend TestScope.(RecetasViewModel) -> Unit) =
        runTest(despachador) {
            val modelo = RecetasViewModel(recetas)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    private suspend fun crearReceta(titulo: String): Long =
        (recetas.crear(titulo) as ResultadoCrearReceta.Creada).recetaId

    private suspend fun ingrediente(nombre: String, valor: Double): Long {
        catalogo.sembrar(Ingrediente(nombre = nombre, valorPorGramo = valor))
        return catalogo.obtenerTodosUnaVez().first { it.nombre == nombre }.id
    }

    private suspend fun ponerEn(recetaId: Long, ingredienteId: Long, gramos: Double) {
        recetas.agregarIngrediente(
            recetas.obtenerSecciones(recetaId).first().id, ingredienteId, gramos
        )
    }

    private fun fila(modelo: RecetasViewModel, titulo: String) =
        modelo.estado.value.visibles.first { it.receta.titulo == titulo }

    // --- Tener ingredientes y costar cero son dos cosas distintas ---

    @Test
    fun `una receta con ingredientes que valen cero no aparece como vacia`() = probar { modelo ->
        // El error visto en el celular. Un ingrediente puede valer 0 a propósito: es cómo se
        // dice "esto no suma al costo" (6.2). Deducir "no tiene nada" de que cueste 0 manda
        // a buscar un problema que no existe.
        val agua = ingrediente("Agua", 0.0)
        val receta = crearReceta("Jarabe")
        ponerEn(receta, agua, 500.0)
        advanceUntilIdle()

        val jarabe = fila(modelo, "Jarabe")
        assertEquals(0.0, jarabe.costoTotal, 0.001)
        assertTrue("Tiene un ingrediente cargado, aunque no sume", jarabe.tieneIngredientes)
    }

    @Test
    fun `una receta recien creada si aparece como vacia`() = probar { modelo ->
        crearReceta("Torta de manjar")
        advanceUntilIdle()

        val torta = fila(modelo, "Torta de manjar")
        assertEquals(0.0, torta.costoTotal, 0.001)
        assertFalse(torta.tieneIngredientes)
    }

    @Test
    fun `quitarle el ultimo ingrediente la devuelve a vacia`() = probar { modelo ->
        val harina = ingrediente("Harina", 2.0)
        val receta = crearReceta("Torta de manjar")
        ponerEn(receta, harina, 500.0)
        advanceUntilIdle()
        assertTrue(fila(modelo, "Torta de manjar").tieneIngredientes)

        recetas.quitarIngrediente(recetas.obtenerIngredientes(receta).single().id)
        advanceUntilIdle()

        assertFalse(fila(modelo, "Torta de manjar").tieneIngredientes)
    }

    @Test
    fun `un ingrediente borrado del catalogo deja la receta como vacia`() = probar { modelo ->
        // La consulta del costo es un INNER JOIN: una fila que apunta a un ingrediente que
        // ya no existe no suma ni cuenta. Es coherente con la pantalla de cantidades, que
        // tampoco dibuja esa línea.
        val harina = ingrediente("Harina", 2.0)
        val receta = crearReceta("Torta de manjar")
        ponerEn(receta, harina, 500.0)
        advanceUntilIdle()

        ingredientes.confirmarEliminacion(harina)
        advanceUntilIdle()

        val torta = fila(modelo, "Torta de manjar")
        assertFalse(torta.tieneIngredientes)
        assertEquals(0.0, torta.costoTotal, 0.001)
    }

    // --- El costo se mantiene vivo ---

    @Test
    fun `subirle el precio a un ingrediente mueve el costo sin pedir nada`() = probar { modelo ->
        val harina = ingrediente("Harina", 1.0)
        val receta = crearReceta("Torta de manjar")
        ponerEn(receta, harina, 500.0)
        advanceUntilIdle()
        assertEquals(500.0, fila(modelo, "Torta de manjar").costoTotal, 0.001)

        ingredientes.actualizar(Ingrediente(id = harina, nombre = "Harina", valorPorGramo = 3.0))
        advanceUntilIdle()

        assertEquals(1500.0, fila(modelo, "Torta de manjar").costoTotal, 0.001)
    }

    // --- Estado de la lista ---

    @Test
    fun `sin recetas se distingue el catalogo vacio de una busqueda sin resultados`() =
        probar { modelo ->
            assertTrue(modelo.estado.value.catalogoVacio)
            assertFalse(modelo.estado.value.busquedaSinResultados)

            crearReceta("Torta de manjar")
            advanceUntilIdle()
            modelo.buscar("queque")
            advanceUntilIdle()

            assertFalse(modelo.estado.value.catalogoVacio)
            assertTrue(modelo.estado.value.busquedaSinResultados)
        }

    @Test
    fun `el buscador encuentra sin importar tildes ni mayusculas`() = probar { modelo ->
        crearReceta("Torta de lúcuma")
        advanceUntilIdle()

        modelo.buscar("LUCUMA")
        advanceUntilIdle()

        assertEquals(1, modelo.estado.value.visibles.size)
    }

    // --- Crear, renombrar, borrar ---

    @Test
    fun `crear una receta la abre sola`() = probar { modelo ->
        modelo.abrirAlta()
        modelo.cambiarTitulo("Torta de manjar")
        modelo.guardar()
        advanceUntilIdle()

        // Crear una y después tener que buscarla para llenarla es un paso de más.
        assertNotNull(modelo.recienCreada.value)
        assertTrue(modelo.dialogo.value is DialogoReceta.Ninguno)

        modelo.recetaAbierta()
        assertNull("El aviso se limpia para no reabrirla", modelo.recienCreada.value)
    }

    @Test
    fun `un titulo repetido avisa dentro del cuadro y no crea nada`() = probar { modelo ->
        crearReceta("Torta de manjar")
        advanceUntilIdle()

        modelo.abrirAlta()
        modelo.cambiarTitulo("  TORTA DE MANJÁR  ")
        modelo.guardar()
        advanceUntilIdle()

        val cuadro = modelo.dialogo.value as DialogoReceta.Formulario
        assertNotNull("El aviso va junto al campo", cuadro.error)
        assertEquals(1, modelo.estado.value.visibles.size)
        assertNull("Y no se abrió ninguna receta nueva", modelo.recienCreada.value)
    }

    @Test
    fun `borrar una receta la saca de la lista`() = probar { modelo ->
        crearReceta("Torta de manjar")
        advanceUntilIdle()

        modelo.pedirBorrado(modelo.estado.value.visibles.single().receta)
        // El `advanceUntilIdle` de acá no es de adorno y no estaba antes: desde 8.11.4,
        // `pedirBorrado` sale a consultar **a qué otras recetas afecta** el borrado, y hasta
        // que esa respuesta no llega el cuadro tiene el botón apagado. Sin esperarla, esta
        // prueba estaría comprobando que se puede borrar sin haber mostrado media advertencia.
        advanceUntilIdle()
        modelo.confirmarBorrado()
        advanceUntilIdle()

        assertTrue(modelo.estado.value.visibles.isEmpty())
        assertNotNull(modelo.estado.value.mensaje)
    }

    @Test
    fun `no se puede confirmar el borrado antes de saber a quien afecta`() = probar { modelo ->
        // La otra mitad de lo de arriba, y la que de verdad importa: el cuadro se abre al
        // instante y la lista de afectadas llega después (8.11.4). Confirmar en esa ventana
        // no puede borrar nada, porque sería borrar sin haber leído por qué convenía pensarlo.
        crearReceta("Torta de manjar")
        advanceUntilIdle()

        modelo.pedirBorrado(modelo.estado.value.visibles.single().receta)
        modelo.confirmarBorrado()
        advanceUntilIdle()

        assertEquals("La receta sigue ahí", 1, modelo.estado.value.visibles.size)
        val cuadro = modelo.dialogo.value as DialogoReceta.ConfirmarBorrado
        assertNotNull("Y el cuadro sigue abierto, ya con la respuesta", cuadro.usadaPor)
        assertTrue("Ahora sí se puede confirmar", cuadro.sePuedeBorrar)
    }

    // --- Las repetidas de antes de la regla ---

    @Test
    fun `las repetidas que ya estaban se marcan y se conserva la original`() = probar { modelo ->
        // No se pueden crear por el repositorio, así que se siembran como estaban guardadas.
        recetaDao.insertar(Receta(titulo = "Torta"))
        recetaDao.insertar(Receta(titulo = "torta"))
        advanceUntilIdle()

        val filas = modelo.estado.value.visibles.sortedBy { it.receta.id }
        assertFalse("La primera es la original y se sigue pudiendo usar", filas[0].repetida)
        assertTrue("La segunda queda bloqueada", filas[1].repetida)
    }
}
