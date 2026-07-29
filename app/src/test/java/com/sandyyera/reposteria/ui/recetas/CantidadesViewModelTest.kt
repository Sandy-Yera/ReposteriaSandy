package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas del paso "Cantidades" (8.2), sin base de datos ni celular.
 *
 * Cubren el "hecho cuando" de la Fase 3: que el costo coincida con la cuenta a mano, que
 * agregarle una segunda sección a una receta simple pida el nombre de la primera, y que
 * los ingredientes ya cargados no se muevan de lugar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CantidadesViewModelTest {

    private val despachador = StandardTestDispatcher()

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var recetaDao: RecetaDaoFalso
    private lateinit var recetas: RecetaRepositorio
    private lateinit var ingredientes: IngredienteRepositorio
    private var recetaId: Long = 0

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

    private fun probar(cuerpo: suspend TestScope.(CantidadesViewModel) -> Unit) =
        runTest(despachador) {
            recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId
            val modelo = CantidadesViewModel(recetaId, recetas, ingredientes)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    /** Deja un ingrediente en el catálogo. El id se lee después desde el estado. */
    private fun sembrar(nombre: String, valorPorGramo: Double) {
        catalogo.sembrar(Ingrediente(nombre = nombre, valorPorGramo = valorPorGramo))
    }

    // --- Estado inicial ---

    @Test
    fun `una receta nueva arranca con una seccion sin encabezado y sin costo`() = probar { modelo ->
        val estado = modelo.estado.value

        assertEquals("Torta de manjar", estado.receta?.titulo)
        assertEquals(1, estado.secciones.size)
        // Con una sola sección no se muestra su nombre: la receta es de un solo conjunto.
        assertFalse(estado.mostrarNombresDeSeccion)
        assertTrue(estado.sinIngredientes)
        assertEquals(0.0, estado.costoTotal, 0.001)
    }

    // --- Ingredientes y costo ---

    @Test
    fun `agregar un ingrediente sube el costo y arma la linea completa`() = probar { modelo ->
        sembrar("Harina", 1.2)
        advanceUntilIdle()
        val harina = modelo.estado.value.catalogo.single()
        val seccion = modelo.estado.value.secciones.single().seccion.id

        modelo.abrirAgregarIngrediente(seccion)
        modelo.elegirIngrediente(harina)
        modelo.cambiarCantidadEscrita("500")
        modelo.guardarIngrediente()
        advanceUntilIdle()

        val estado = modelo.estado.value
        val linea = estado.secciones.single().lineas.single()
        assertEquals("Harina", linea.ingrediente.nombre)
        assertEquals(500.0, linea.item.cantidadG, 0.001)
        assertEquals(600.0, linea.subtotal, 0.001)
        // Y el total que se muestra viene de la base, no de sumar las líneas acá.
        assertEquals(600.0, estado.costoTotal, 0.001)
        assertFalse(estado.sinIngredientes)
    }

    @Test
    fun `el costo mostrado es el mismo que suman las lineas`() = probar { modelo ->
        // Son dos caminos distintos hacia el mismo número -- la consulta de la base y la
        // suma de los subtotales de la pantalla -- y si se separaran nadie se enteraría.
        sembrar("Harina", 1.2)
        sembrar("Manjar", 4.8)
        advanceUntilIdle()
        val seccion = modelo.estado.value.secciones.single().seccion.id
        for ((ingrediente, gramos) in modelo.estado.value.catalogo.zip(listOf("500", "250"))) {
            modelo.abrirAgregarIngrediente(seccion)
            modelo.elegirIngrediente(ingrediente)
            modelo.cambiarCantidadEscrita(gramos)
            modelo.guardarIngrediente()
            advanceUntilIdle()
        }

        val estado = modelo.estado.value
        val sumaDeLineas = estado.secciones.sumOf { s -> s.lineas.sumOf { it.subtotal } }
        assertEquals(1800.0, estado.costoTotal, 0.001)
        assertEquals(estado.costoTotal, sumaDeLineas, 0.001)
    }

    @Test
    fun `no se puede guardar sin elegir ingrediente ni sin gramos`() = probar { modelo ->
        sembrar("Harina", 1.2)
        advanceUntilIdle()
        val seccion = modelo.estado.value.secciones.single().seccion.id

        modelo.abrirAgregarIngrediente(seccion)
        advanceUntilIdle()
        val recienAbierto = modelo.estado.value.dialogo as DialogoCantidades.PonerIngrediente
        assertFalse(recienAbierto.puedeGuardar)

        modelo.elegirIngrediente(modelo.estado.value.catalogo.single())
        advanceUntilIdle()
        // Con ingrediente pero sin gramos, sigue sin poder guardarse.
        assertFalse((modelo.estado.value.dialogo as DialogoCantidades.PonerIngrediente).puedeGuardar)

        modelo.cambiarCantidadEscrita("0")
        advanceUntilIdle()
        // Cero gramos tampoco: eso no es estar en la receta.
        val conCero = modelo.estado.value.dialogo as DialogoCantidades.PonerIngrediente
        assertFalse(conCero.puedeGuardar)
        assertNotNull(conCero.errorCantidad)
    }

    @Test
    fun `cambiar los gramos recalcula el costo`() = probar { modelo ->
        sembrar("Harina", 1.2)
        advanceUntilIdle()
        val seccion = modelo.estado.value.secciones.single().seccion.id
        modelo.abrirAgregarIngrediente(seccion)
        modelo.elegirIngrediente(modelo.estado.value.catalogo.single())
        modelo.cambiarCantidadEscrita("500")
        modelo.guardarIngrediente()
        advanceUntilIdle()

        modelo.abrirCambiarCantidad(modelo.estado.value.secciones.single().lineas.single())
        modelo.cambiarCantidadEscrita("1.000")
        modelo.guardarIngrediente()
        advanceUntilIdle()

        assertEquals(1200.0, modelo.estado.value.costoTotal, 0.001)
    }

    @Test
    fun `quitar un ingrediente baja el costo`() = probar { modelo ->
        sembrar("Harina", 1.2)
        advanceUntilIdle()
        val seccion = modelo.estado.value.secciones.single().seccion.id
        modelo.abrirAgregarIngrediente(seccion)
        modelo.elegirIngrediente(modelo.estado.value.catalogo.single())
        modelo.cambiarCantidadEscrita("500")
        modelo.guardarIngrediente()
        advanceUntilIdle()

        modelo.quitarIngrediente(modelo.estado.value.secciones.single().lineas.single())
        advanceUntilIdle()

        assertEquals(0.0, modelo.estado.value.costoTotal, 0.001)
        assertTrue(modelo.estado.value.sinIngredientes)
    }

    @Test
    fun `crear un ingrediente sin salir de la receta lo deja elegido`() = probar { modelo ->
        val seccion = modelo.estado.value.secciones.single().seccion.id
        modelo.abrirAgregarIngrediente(seccion)

        modelo.crearIngredienteRapido("Ralladura de naranja")
        advanceUntilIdle()

        val dialogo = modelo.estado.value.dialogo as DialogoCantidades.PonerIngrediente
        assertEquals("Ralladura de naranja", dialogo.elegido?.nombre)
        // Nace en 0 y se avisa, para que no pase inadvertido que falta ponerle precio.
        assertEquals(0.0, dialogo.elegido!!.valorPorGramo, 0.0)
        assertNotNull(modelo.estado.value.mensaje)
    }

    // --- Secciones (8.2) ---

    @Test
    fun `al agregar la segunda seccion pide bautizar la primera`() = probar { modelo ->
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()

        val dialogo = modelo.estado.value.dialogo as DialogoCantidades.Seccion
        // Propone el título de la receta, no "General", que no diría nada.
        assertEquals("Torta de manjar", dialogo.nombreDeLaPrimera)
    }

    @Test
    fun `bautizar la primera no mueve sus ingredientes de lugar`() = probar { modelo ->
        sembrar("Harina", 1.2)
        advanceUntilIdle()
        val primeraSeccion = modelo.estado.value.secciones.single().seccion.id
        modelo.abrirAgregarIngrediente(primeraSeccion)
        modelo.elegirIngrediente(modelo.estado.value.catalogo.single())
        modelo.cambiarCantidadEscrita("500")
        modelo.guardarIngrediente()
        advanceUntilIdle()

        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.cambiarNombreDeSeccion("Relleno")
        modelo.guardarSeccion()
        advanceUntilIdle()

        val estado = modelo.estado.value
        assertEquals(listOf("Bizcocho", "Relleno"), estado.secciones.map { it.seccion.nombreSeccion })
        // Ahora sí se muestran los encabezados, y la harina sigue en la misma sección.
        assertTrue(estado.mostrarNombresDeSeccion)
        assertEquals(primeraSeccion, estado.secciones.first().seccion.id)
        assertEquals("Harina", estado.secciones.first().lineas.single().ingrediente.nombre)
        assertTrue(estado.secciones[1].lineas.isEmpty())
        assertEquals(600.0, estado.costoTotal, 0.001)
    }

    @Test
    fun `con dos secciones ya no vuelve a pedir bautizar`() = probar { modelo ->
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.cambiarNombreDeSeccion("Relleno")
        modelo.guardarSeccion()
        advanceUntilIdle()

        modelo.abrirAgregarSeccion()
        advanceUntilIdle()

        val dialogo = modelo.estado.value.dialogo as DialogoCantidades.Seccion
        assertEquals(null, dialogo.nombreDeLaPrimera)
    }

    @Test
    fun `volver a una sola seccion vuelve a ocultar los encabezados`() = probar { modelo ->
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.cambiarNombreDeSeccion("Relleno")
        modelo.guardarSeccion()
        advanceUntilIdle()
        assertTrue(modelo.estado.value.mostrarNombresDeSeccion)

        modelo.pedirBorrarSeccion(modelo.estado.value.secciones.last())
        modelo.confirmarBorrarSeccion()
        advanceUntilIdle()

        // La que queda conserva el nombre "Bizcocho" en la base, pero deja de mostrarse:
        // no hace falta renombrarla de vuelta a "General".
        assertEquals(1, modelo.estado.value.secciones.size)
        assertFalse(modelo.estado.value.mostrarNombresDeSeccion)
    }

    @Test
    fun `borrar una seccion se lleva sus ingredientes y baja el costo`() = probar { modelo ->
        sembrar("Manjar", 4.8)
        advanceUntilIdle()
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.cambiarNombreDeSeccion("Relleno")
        modelo.guardarSeccion()
        advanceUntilIdle()

        val relleno = modelo.estado.value.secciones.last()
        modelo.abrirAgregarIngrediente(relleno.seccion.id)
        modelo.elegirIngrediente(modelo.estado.value.catalogo.single())
        modelo.cambiarCantidadEscrita("250")
        modelo.guardarIngrediente()
        advanceUntilIdle()
        assertEquals(1200.0, modelo.estado.value.costoTotal, 0.001)

        modelo.pedirBorrarSeccion(modelo.estado.value.secciones.last())
        modelo.confirmarBorrarSeccion()
        advanceUntilIdle()

        assertEquals(0.0, modelo.estado.value.costoTotal, 0.001)
    }

    @Test
    fun `no se puede quedar sin ninguna seccion`() = probar { modelo ->
        modelo.pedirBorrarSeccion(modelo.estado.value.secciones.single())
        modelo.confirmarBorrarSeccion()
        advanceUntilIdle()

        // Los ingredientes necesitan dónde colgar: sin secciones la receta no recibe nada.
        assertEquals(1, modelo.estado.value.secciones.size)
        assertNotNull(modelo.estado.value.mensaje)
    }

    @Test
    fun `renombrar una seccion cambia su encabezado`() = probar { modelo ->
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.cambiarNombreDeSeccion("Relleno")
        modelo.guardarSeccion()
        advanceUntilIdle()

        modelo.abrirRenombrarSeccion(modelo.estado.value.secciones.first().seccion)
        modelo.cambiarNombreEnRenombrado("Masa")
        modelo.guardarRenombrado()
        advanceUntilIdle()

        assertEquals("Masa", modelo.estado.value.secciones.first().seccion.nombreSeccion)
    }
}
