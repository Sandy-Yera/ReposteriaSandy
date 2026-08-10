package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import com.sandyyera.reposteria.logica.duracion.UnidadDuracion
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.simulacion.SEMANAS_POR_MES
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
 * El resumen de una receta (8.12).
 *
 * Lo que hay que cuidar acá no es el dibujo sino **que lo que dice sea lo mismo que dicen los
 * pasos**: el resumen vuelve a contar las cifras de siete pantallas, y si las recalculara con sus
 * propias reglas terminaría diciendo de un molde algo distinto que el paso del molde. Por eso las
 * pruebas comparan contra lo guardado, no contra un texto escrito a mano dos veces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumenViewModelTest {

    private val despachador = StandardTestDispatcher()

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var recetaDao: RecetaDaoFalso
    private lateinit var recetas: RecetaRepositorio
    private var recetaId: Long = 0

    @Before
    fun prepararTodo() {
        Dispatchers.setMain(despachador)
        catalogo = IngredienteDaoFalso()
        recetaDao = RecetaDaoFalso(catalogo)
        recetas = RecetaRepositorio(recetaDao, HistorialRepositorio(HistorialDaoFalso()))
    }

    @After
    fun dejarTodoComoEstaba() {
        Dispatchers.resetMain()
    }

    private fun probar(cuerpo: suspend TestScope.(ResumenViewModel) -> Unit) =
        runTest(despachador) {
            recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId

            val modelo = ResumenViewModel(recetaId, recetas)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    /** Deja un ingrediente en el catálogo y devuelve su id. */
    private suspend fun ingrediente(
        nombre: String,
        valor: Double,
        esObjeto: Boolean = false
    ): Long {
        catalogo.sembrar(
            Ingrediente(nombre = nombre, valorPorGramo = valor, esObjeto = esObjeto)
        )
        return catalogo.obtenerTodosUnaVez().first { it.nombre == nombre }.id
    }

    // --- Lo que trae ---

    @Test
    fun `una receta recien creada se resume sin inventar nada`() = probar { modelo ->
        // Todo vacío es un estado normal y no un error: lo que no se puede hacer es rellenarlo
        // con ceros que se lean como decisiones tomadas.
        val resumen = modelo.estado.value.resumen!!

        assertEquals("Torta de manjar", resumen.titulo)
        assertTrue(resumen.sinIngredientes)
        assertTrue(resumen.sinPrecios)
        assertTrue(resumen.sinPasos)
        assertEquals(0.0, resumen.costoTotal, 0.001)
        assertNull("Sin molde no se inventa uno", resumen.molde)
        assertNull("Sin precio no hay nada que proyectar", resumen.simulacion)
        assertTrue(resumen.duraciones.isEmpty())
    }

    @Test
    fun `los ingredientes salen con su cantidad y su subtotal`() = probar { modelo ->
        val seccion = recetas.obtenerSecciones(recetaId).single()
        recetas.agregarIngrediente(seccion.id, ingrediente("Harina", 1.2), 500.0)
        advanceUntilIdle()

        val resumen = modelo.estado.value.resumen!!
        val linea = resumen.secciones.single().lineas.single()
        assertEquals("500 g", linea.cuanto)
        assertEquals("Harina", linea.nombre)
        assertEquals(600.0, linea.subtotal, 0.001)
        assertEquals("Y el total sale de la base", 600.0, resumen.costoTotal, 0.001)
    }

    @Test
    fun `lo que se cuenta por unidad se lee en unidades y suma al costo`() = probar { modelo ->
        val seccion = recetas.obtenerSecciones(recetaId).single()
        val bolsas = ingrediente("Bolsas", 40.0, esObjeto = true)
        recetas.agregarIngrediente(seccion.id, bolsas, cantidadG = 0.0, unidades = 3.0)
        advanceUntilIdle()

        val resumen = modelo.estado.value.resumen!!
        val linea = resumen.secciones.single().lineas.single()
        assertEquals("3 unidades", linea.cuanto)
        assertEquals(120.0, linea.subtotal, 0.001)
        assertEquals(120.0, resumen.costoTotal, 0.001)
    }

    @Test
    fun `con una sola parte sin nombre no se escribe encabezado`() = probar { modelo ->
        // La misma regla que en cantidades (8.2): decir "General" arriba repite el título de
        // la receta. Si el resumen decidiera esto por su cuenta, las dos pantallas discreparían.
        val seccion = recetas.obtenerSecciones(recetaId).single()
        recetas.agregarIngrediente(seccion.id, ingrediente("Harina", 1.0), 500.0)
        advanceUntilIdle()

        assertNull(modelo.estado.value.resumen!!.secciones.single().nombre)
    }

    @Test
    fun `con dos partes cada una lleva su nombre y su costo`() = probar { modelo ->
        recetas.agregarSeccion(recetaId, "Crema", nombreDeLaPrimera = "Bizcocho")
        advanceUntilIdle()
        val secciones = recetas.obtenerSecciones(recetaId)
        recetas.agregarIngrediente(secciones[0].id, ingrediente("Harina", 1.0), 500.0)
        recetas.agregarIngrediente(secciones[1].id, ingrediente("Manjar", 4.0), 250.0)
        advanceUntilIdle()

        val resumen = modelo.estado.value.resumen!!
        assertEquals(listOf("Bizcocho", "Crema"), resumen.secciones.map { it.nombre })
        assertEquals(500.0, resumen.secciones[0].costo, 0.001)
        assertEquals(1000.0, resumen.secciones[1].costo, 0.001)
        assertEquals("El total cuadra con la suma de las partes", 1500.0, resumen.costoTotal, 0.001)
    }

    @Test
    fun `una duracion anotada se lee como en su paso`() = probar { modelo ->
        recetas.guardarDuracion(recetaId, TipoDuracion.AMBIENTE, true, "3", UnidadDuracion.DIAS)
        advanceUntilIdle()

        val duraciones = modelo.estado.value.resumen!!.duraciones
        assertEquals(1, duraciones.size)
        assertTrue("Nombra el tipo y la cantidad", duraciones.single().contains("3"))
    }

    @Test
    fun `la simulacion trae lo que se gana, no solo lo que se configuro`() = probar { modelo ->
        // Lo reportó Sandy: decía "2 por día, 4 días" y eso es lo que se anotó, no lo que se
        // gana — que es justamente lo que uno viene a mirar al resumen.
        val seccion = recetas.obtenerSecciones(recetaId).single()
        recetas.agregarIngrediente(seccion.id, ingrediente("Harina", 1.0), 1200.0)
        recetas.guardarRendimiento(recetaId, "6", "1.200")
        advanceUntilIdle()
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "500")
        recetas.guardarSimulacion(recetaId, "4", "2")
        advanceUntilIdle()

        val simulacion = modelo.estado.value.resumen!!.simulacion!!
        assertEquals("2 por día, 4 días a la semana", simulacion.cuanto)
        // Cuesta 1.200 y rinde 6 trozos a 500: ingreso 3.000, ganancia 1.800 por producto.
        // Dos por día, cuatro días: 14.400 a la semana.
        assertEquals(14400.0, simulacion.gananciaSemanal, 0.001)
        assertEquals(14400.0 * SEMANAS_POR_MES, simulacion.gananciaMensual, 0.001)
    }

    @Test
    fun `sin precio no se inventa una simulacion`() = probar { modelo ->
        // Aunque los días y las unidades estén configurados: sin precio no hay nada que
        // proyectar, y un "0 al mes" se leería como una conclusión.
        recetas.guardarSimulacion(recetaId, "4", "2")
        advanceUntilIdle()

        assertNull(modelo.estado.value.resumen!!.simulacion)
    }

    @Test
    fun `los precios traen su ganancia por trozo`() = probar { modelo ->
        val seccion = recetas.obtenerSecciones(recetaId).single()
        recetas.agregarIngrediente(seccion.id, ingrediente("Harina", 1.0), 1200.0)
        recetas.guardarRendimiento(recetaId, "6", "1.200")
        advanceUntilIdle()
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "500")
        advanceUntilIdle()

        val precio = modelo.estado.value.resumen!!.precios.single()
        // Cuesta 1.200 entre 6 trozos: 200 por trozo. Vendido a 500, gana 300.
        assertEquals(300.0, precio.gananciaPorTrozo, 0.001)
    }

    @Test
    fun `el rendimiento dice los trozos y el peso`() = probar { modelo ->
        recetas.guardarRendimiento(recetaId, "6", "1.200")
        advanceUntilIdle()

        val rendimiento = modelo.estado.value.resumen!!.rendimiento
        assertEquals(6, rendimiento.trozos)
        assertEquals(1200.0, rendimiento.pesoFinalG!!, 0.001)
        assertFalse("Nadie reescaló nada", rendimiento.pesoSinRevisar)
    }

    @Test
    fun `los pasos salen agrupados y numerados corrido`() = probar { modelo ->
        recetas.agregarPaso(recetaId, titulo = null, esGeneralAnidado = false)
        advanceUntilIdle()
        val primero = recetaDao.obtenerPasos(recetaId).single().id
        recetas.guardarTextoDePaso(primero, "Batir las claras.")
        recetas.agregarPaso(recetaId, titulo = null, esGeneralAnidado = false)
        advanceUntilIdle()
        val segundo = recetaDao.obtenerPasos(recetaId).last().id
        recetas.guardarTextoDePaso(segundo, "Hornear.")
        advanceUntilIdle()

        val bloques = modelo.estado.value.resumen!!.bloquesDePasos
        assertEquals(1, bloques.size)
        assertEquals(
            listOf("1. Batir las claras.", "2. Hornear."),
            bloques.single().pasos
        )
    }

    // --- El acordeón ---

    @Test
    fun `arranca con todo cerrado`() = probar { modelo ->
        // Lo primero que se quiere ver al abrir una receta es de qué partes está hecha, no el
        // contenido de la primera.
        assertNull(modelo.estado.value.abierta)
        PasoDeReceta.entries.forEach {
            assertFalse(modelo.estado.value.estaAbierta(it))
        }
    }

    @Test
    fun `abrir una parte cierra la anterior`() = probar { modelo ->
        // Con todas abiertas esto sería la receta desplegada en una tira larga, que es justo lo
        // que ya se puede ver recorriendo los pasos.
        modelo.alternar(PasoDeReceta.CANTIDADES)
        advanceUntilIdle()
        assertTrue(modelo.estado.value.estaAbierta(PasoDeReceta.CANTIDADES))

        modelo.alternar(PasoDeReceta.PASOS)
        advanceUntilIdle()

        assertTrue(modelo.estado.value.estaAbierta(PasoDeReceta.PASOS))
        assertFalse(modelo.estado.value.estaAbierta(PasoDeReceta.CANTIDADES))
    }

    @Test
    fun `tocar la parte abierta la cierra`() = probar { modelo ->
        // Sin esto no habría forma de volver a ver el índice completo sin abrir otra.
        modelo.alternar(PasoDeReceta.CANTIDADES)
        advanceUntilIdle()

        modelo.alternar(PasoDeReceta.CANTIDADES)
        advanceUntilIdle()

        assertNull(modelo.estado.value.abierta)
    }

    // --- Lo que se mueve solo ---

    @Test
    fun `cambiarle el precio a un ingrediente mueve el resumen sin pedirlo`() = probar { modelo ->
        // *Lo que se muestra se observa.* Un resumen que se queda con el costo de hace un rato
        // es un número que se cree y ya no existe.
        val seccion = recetas.obtenerSecciones(recetaId).single()
        val harina = ingrediente("Harina", 1.0)
        recetas.agregarIngrediente(seccion.id, harina, 500.0)
        advanceUntilIdle()
        assertEquals(500.0, modelo.estado.value.resumen!!.costoTotal, 0.001)

        catalogo.actualizar(catalogo.obtener(harina)!!.copy(valorPorGramo = 2.0))
        advanceUntilIdle()

        assertEquals(1000.0, modelo.estado.value.resumen!!.costoTotal, 0.001)
    }

    @Test
    fun `una receta borrada deja de resumirse en vez de mostrar un hueco`() = probar { modelo ->
        assertNotNull(modelo.estado.value.resumen)

        recetas.confirmarEliminacion(recetaId)
        advanceUntilIdle()

        assertNull(modelo.estado.value.resumen)
        assertTrue(modelo.estado.value.desaparecio)
    }

    @Test
    fun `un ingrediente borrado del catalogo saca su linea, no la deja a medias`() =
        probar { modelo ->
            // El `JOIN` del resumen es INNER, igual que el del costo: una fila sin ingrediente
            // no tiene nombre ni precio que mostrar, y sumarla como 0 mentiría sobre el total.
            val seccion = recetas.obtenerSecciones(recetaId).single()
            val harina = ingrediente("Harina", 1.0)
            recetas.agregarIngrediente(seccion.id, harina, 500.0)
            recetas.agregarIngrediente(seccion.id, ingrediente("Azúcar", 2.0), 100.0)
            advanceUntilIdle()

            catalogo.eliminarPorId(harina)
            advanceUntilIdle()

            val lineas = modelo.estado.value.resumen!!.secciones.single().lineas
            assertEquals(listOf("Azúcar"), lineas.map { it.nombre })
        }
}
