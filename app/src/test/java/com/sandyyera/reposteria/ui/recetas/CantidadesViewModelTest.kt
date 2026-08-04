package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.ModoReescalado
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
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

    // --- El mismo ingrediente dos veces en la misma sección ---

    private suspend fun ponerIngrediente(
        modelo: CantidadesViewModel,
        ingrediente: Ingrediente,
        seccion: Long,
        gramos: String
    ) {
        modelo.abrirAgregarIngrediente(seccion)
        modelo.elegirIngrediente(ingrediente)
        modelo.cambiarCantidadEscrita(gramos)
        modelo.guardarIngrediente()
    }

    @Test
    fun `el cuadro no ofrece un ingrediente que ya esta en la seccion`() = probar { modelo ->
        // Se vio en el celular: la misma sección aceptaba "Harina" dos veces. Lo primero es
        // no ofrecerlo, por lo mismo que `titulosDisponibles`: lo que se ofrece y lo que se
        // acepta no pueden discrepar.
        sembrar("Harina", 1.2)
        sembrar("Manjar", 4.8)
        advanceUntilIdle()
        val harina = modelo.estado.value.catalogo.first { it.nombre == "Harina" }
        val manjar = modelo.estado.value.catalogo.first { it.nombre == "Manjar" }
        val seccion = modelo.estado.value.secciones.single().seccion.id
        ponerIngrediente(modelo, harina, seccion, "500")
        advanceUntilIdle()

        modelo.abrirAgregarIngrediente(seccion)
        advanceUntilIdle()

        val cuadro = modelo.dialogo.value as DialogoCantidades.PonerIngrediente
        assertNotNull("La harina ya está puesta", cuadro.motivoNoDisponible(harina))
        assertNull("El manjar todavía no", cuadro.motivoNoDisponible(manjar))
    }

    @Test
    fun `y si igual se intenta, el repositorio lo rechaza sin cerrar el cuadro`() =
        probar { modelo ->
            // La segunda red: que la pantalla no ofrezca no basta, porque quien decide de
            // verdad es el repositorio. El cuadro **queda abierto** con el motivo adentro, y
            // no se cierra dejando un aviso en la franja de abajo que el teclado tapa (8.2).
            sembrar("Harina", 1.2)
            advanceUntilIdle()
            val harina = modelo.estado.value.catalogo.single()
            val seccion = modelo.estado.value.secciones.single().seccion.id
            ponerIngrediente(modelo, harina, seccion, "500")
            advanceUntilIdle()

            ponerIngrediente(modelo, harina, seccion, "200")
            advanceUntilIdle()

            val cuadro = modelo.dialogo.value as DialogoCantidades.PonerIngrediente
            assertNotNull("El motivo se ve dentro del cuadro", cuadro.rechazo)
            assertTrue(cuadro.rechazo!!.contains("Harina"))
            assertEquals(1, modelo.estado.value.secciones.single().lineas.size)
            assertEquals(600.0, modelo.estado.value.costoTotal, 0.001)
        }

    @Test
    fun `al cambiar el ingrediente elegido el rechazo se va`() = probar { modelo ->
        // El rechazo era sobre el anterior: dejarlo puesto acusaría al que se acaba de elegir.
        sembrar("Harina", 1.2)
        sembrar("Manjar", 4.8)
        advanceUntilIdle()
        val harina = modelo.estado.value.catalogo.first { it.nombre == "Harina" }
        val manjar = modelo.estado.value.catalogo.first { it.nombre == "Manjar" }
        val seccion = modelo.estado.value.secciones.single().seccion.id
        ponerIngrediente(modelo, harina, seccion, "500")
        advanceUntilIdle()
        ponerIngrediente(modelo, harina, seccion, "200")
        advanceUntilIdle()

        modelo.elegirIngrediente(manjar)

        assertNull((modelo.dialogo.value as DialogoCantidades.PonerIngrediente).rechazo)
    }

    @Test
    fun `cambiarle los gramos a una fila que ya existe sigue funcionando`() = probar { modelo ->
        // El caso que la comprobación no puede romper: editar una línea es elegir el mismo
        // ingrediente que ya está, y ahí no hay nada repetido.
        sembrar("Harina", 1.2)
        advanceUntilIdle()
        val harina = modelo.estado.value.catalogo.single()
        val seccion = modelo.estado.value.secciones.single().seccion.id
        ponerIngrediente(modelo, harina, seccion, "500")
        advanceUntilIdle()

        modelo.abrirCambiarCantidad(modelo.estado.value.secciones.single().lineas.single())
        advanceUntilIdle()
        val cuadro = modelo.dialogo.value as DialogoCantidades.PonerIngrediente
        assertNull("Editando, el propio ingrediente sigue disponible", cuadro.motivoNoDisponible(harina))
        modelo.cambiarCantidadEscrita("750")
        modelo.guardarIngrediente()
        advanceUntilIdle()

        assertEquals(750.0, modelo.estado.value.secciones.single().lineas.single().item.cantidadG, 0.001)
        assertEquals(1, modelo.estado.value.secciones.single().lineas.size)
    }

    @Test
    fun `el mismo ingrediente en otra seccion si se puede`() = probar { modelo ->
        // Almendra en el bizcocho y almendra en la decoración es correcto y corriente.
        sembrar("Almendra", 8.0)
        advanceUntilIdle()
        val almendra = modelo.estado.value.catalogo.single()
        val primera = modelo.estado.value.secciones.single().seccion.id
        ponerIngrediente(modelo, almendra, primera, "100")
        advanceUntilIdle()
        modelo.abrirAgregarSeccion()
        // El cuadro se abre dentro de una corrutina: va a preguntar qué nombre proponer para
        // la sección que hasta ahora era invisible. Sin dejarla correr, lo que se escriba
        // después cae en un cuadro que todavía no existe y se pierde.
        advanceUntilIdle()
        modelo.cambiarNombreDeSeccion("Decoración")
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.guardarSeccion()
        advanceUntilIdle()
        val decoracion = modelo.estado.value.secciones
            .first { it.seccion.nombreSeccion == "Decoración" }.seccion.id

        modelo.abrirAgregarIngrediente(decoracion)
        advanceUntilIdle()
        val cuadro = modelo.dialogo.value as DialogoCantidades.PonerIngrediente
        assertNull("En esta sección todavía no está", cuadro.motivoNoDisponible(almendra))

        modelo.elegirIngrediente(almendra)
        modelo.cambiarCantidadEscrita("30")
        modelo.guardarIngrediente()
        advanceUntilIdle()

        assertEquals(2, modelo.estado.value.secciones.sumOf { it.lineas.size })
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
        val recienAbierto = modelo.dialogo.value as DialogoCantidades.PonerIngrediente
        assertFalse(recienAbierto.puedeGuardar)

        modelo.elegirIngrediente(modelo.estado.value.catalogo.single())
        advanceUntilIdle()
        // Con ingrediente pero sin gramos, sigue sin poder guardarse.
        assertFalse((modelo.dialogo.value as DialogoCantidades.PonerIngrediente).puedeGuardar)

        modelo.cambiarCantidadEscrita("0")
        advanceUntilIdle()
        // Cero gramos tampoco: eso no es estar en la receta.
        val conCero = modelo.dialogo.value as DialogoCantidades.PonerIngrediente
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

        val dialogo = modelo.dialogo.value as DialogoCantidades.PonerIngrediente
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

        val dialogo = modelo.dialogo.value as DialogoCantidades.Seccion
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

        val dialogo = modelo.dialogo.value as DialogoCantidades.Seccion
        assertEquals(null, dialogo.nombreDeLaPrimera)
    }

    @Test
    fun `volver a una sola seccion conserva su nombre a la vista`() = probar { modelo ->
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

        // La que queda conserva su nombre Y lo sigue mostrando: lo escribió alguien.
        assertEquals(1, modelo.estado.value.secciones.size)
        assertEquals("Bizcocho", modelo.estado.value.secciones.single().seccion.nombreSeccion)
        assertTrue(modelo.estado.value.mostrarNombresDeSeccion)
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

    // --- El nombre de una sección no se pierde al quedar sola ---

    @Test
    fun `la seccion que queda sola sigue mostrando el nombre que le pusieron`() = probar { modelo ->
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.cambiarNombreDeSeccion("Salsa")
        modelo.guardarSeccion()
        advanceUntilIdle()

        // Se borra la PRIMERA, no la última: la que queda es "Salsa".
        modelo.pedirBorrarSeccion(modelo.estado.value.secciones.first())
        modelo.confirmarBorrarSeccion()
        advanceUntilIdle()

        val secciones = modelo.estado.value.secciones
        assertEquals(1, secciones.size)
        assertEquals("Salsa", secciones.single().seccion.nombreSeccion)
        // Y su encabezado se sigue viendo, aunque esté sola.
        assertTrue(modelo.estado.value.mostrarNombresDeSeccion)
    }

    @Test
    fun `una seccion ya bautizada no vuelve a pedir bautizo`() = probar { modelo ->
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.cambiarNombreDeSeccion("Salsa")
        modelo.guardarSeccion()
        advanceUntilIdle()
        modelo.pedirBorrarSeccion(modelo.estado.value.secciones.first())
        modelo.confirmarBorrarSeccion()
        advanceUntilIdle()

        // Queda "Salsa" sola. Agregar otra no puede proponer renombrarla.
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()

        val dialogo = modelo.dialogo.value as DialogoCantidades.Seccion
        assertEquals(null, dialogo.nombreDeLaPrimera)

        modelo.cambiarNombreDeSeccion("Crema")
        modelo.guardarSeccion()
        advanceUntilIdle()
        assertEquals(
            listOf("Salsa", "Crema"),
            modelo.estado.value.secciones.map { it.seccion.nombreSeccion }
        )
    }

    // --- Costo por sección ---

    /** Deja la receta con dos secciones y un ingrediente en cada una. */
    private suspend fun TestScope.dosSeccionesConIngredientes(modelo: CantidadesViewModel) {
        sembrar("Harina", 1.2)
        sembrar("Chocolate", 8.0)
        advanceUntilIdle()

        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.cambiarNombreDeSeccion("Salsa")
        modelo.guardarSeccion()
        advanceUntilIdle()

        val catalogoDeLaPantalla = modelo.estado.value.catalogo
        val harina = catalogoDeLaPantalla.first { it.nombre == "Harina" }
        val chocolate = catalogoDeLaPantalla.first { it.nombre == "Chocolate" }
        val secciones = modelo.estado.value.secciones

        modelo.abrirAgregarIngrediente(secciones[0].seccion.id)
        modelo.elegirIngrediente(harina)
        modelo.cambiarCantidadEscrita("500")
        modelo.guardarIngrediente()
        advanceUntilIdle()

        modelo.abrirAgregarIngrediente(secciones[1].seccion.id)
        modelo.elegirIngrediente(chocolate)
        modelo.cambiarCantidadEscrita("200")
        modelo.guardarIngrediente()
        advanceUntilIdle()
    }

    @Test
    fun `cada seccion sabe lo que cuesta ella sola`() = probar { modelo ->
        dosSeccionesConIngredientes(modelo)

        val secciones = modelo.estado.value.secciones
        assertEquals(600.0, secciones[0].costo, 0.001)    // 500 g × $1,2
        assertEquals(1600.0, secciones[1].costo, 0.001)   // 200 g × $8
    }

    @Test
    fun `la suma de las secciones da el total que trae la base`() = probar { modelo ->
        // Esto es lo que hay que proteger: el costo por sección se suma en memoria y el
        // total sale de una consulta. Son dos caminos, y si se separaran la pantalla
        // mostraría partes que no dan el entero que tiene arriba.
        dosSeccionesConIngredientes(modelo)

        val estado = modelo.estado.value
        assertEquals(estado.costoTotal, estado.secciones.sumOf { it.costo }, 0.001)
    }

    @Test
    fun `una seccion vacia cuesta cero y no rompe la suma`() = probar { modelo ->
        dosSeccionesConIngredientes(modelo)
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeSeccion("Decoración")
        modelo.guardarSeccion()
        advanceUntilIdle()

        val estado = modelo.estado.value
        assertEquals(0.0, estado.secciones.last().costo, 0.001)
        assertEquals(estado.costoTotal, estado.secciones.sumOf { it.costo }, 0.001)
    }

    @Test
    fun `el costo por seccion aparece recien con dos secciones`() = probar { modelo ->
        // Con una sola, su costo es el total que ya está arriba en grande: el mismo número
        // dos veces en la misma pantalla hace dudar de si son dos cosas distintas.
        assertFalse(modelo.estado.value.mostrarCostoPorSeccion)

        dosSeccionesConIngredientes(modelo)

        assertTrue(modelo.estado.value.mostrarCostoPorSeccion)
    }

    @Test
    fun `una seccion sola con nombre propio muestra su nombre pero no su costo`() =
        probar { modelo ->
            // Las dos reglas se parecen pero no son la misma: el nombre se muestra porque lo
            // escribió alguien, el costo no porque no agrega nada.
            modelo.abrirRenombrarSeccion(modelo.estado.value.secciones.single().seccion)
            modelo.cambiarNombreEnRenombrado("Salsa")
            modelo.guardarRenombrado()
            advanceUntilIdle()

            val estado = modelo.estado.value
            assertTrue(estado.mostrarNombresDeSeccion)
            assertFalse(estado.mostrarCostoPorSeccion)
        }

    // --- Nombres de sección repetidos ---

    @Test
    fun `no deja crear dos secciones con el mismo nombre y lo dice`() = probar { modelo ->
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.cambiarNombreDeSeccion("Salsa de chocolate")
        modelo.guardarSeccion()
        advanceUntilIdle()

        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeSeccion("SALSA DE CHOCOLATÉ")
        modelo.guardarSeccion()
        advanceUntilIdle()

        assertEquals(2, modelo.estado.value.secciones.size)
        // El aviso va **dentro del cuadro**, no en la franja de abajo: con el teclado
        // abierto esa franja queda tapada y el cuadro parece no haber hecho nada.
        val cuadro = modelo.dialogo.value as DialogoCantidades.Seccion
        assertNotNull("Tiene que decir por qué no se pudo", cuadro.error)
        assertNull("Y no por abajo, que no se ve", modelo.estado.value.mensaje)
        assertEquals("SALSA DE CHOCOLATÉ", cuadro.nombre)
    }

    @Test
    fun `al corregir el nombre el aviso de repetido desaparece`() = probar { modelo ->
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.cambiarNombreDeSeccion("Salsa")
        modelo.guardarSeccion()
        advanceUntilIdle()

        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeSeccion("Salsa")
        modelo.guardarSeccion()
        advanceUntilIdle()
        assertNotNull((modelo.dialogo.value as DialogoCantidades.Seccion).error)

        modelo.cambiarNombreDeSeccion("Crema")

        // El rechazo era sobre lo anterior: al escribir otra cosa deja de aplicar.
        assertNull((modelo.dialogo.value as DialogoCantidades.Seccion).error)
    }

    @Test
    fun `renombrar hacia un nombre ya usado avisa y deja el cuadro abierto`() = probar { modelo ->
        modelo.abrirAgregarSeccion()
        advanceUntilIdle()
        modelo.cambiarNombreDeLaPrimera("Bizcocho")
        modelo.cambiarNombreDeSeccion("Salsa")
        modelo.guardarSeccion()
        advanceUntilIdle()

        val salsa = modelo.estado.value.secciones.first { it.seccion.nombreSeccion == "Salsa" }
        modelo.abrirRenombrarSeccion(salsa.seccion)
        modelo.cambiarNombreEnRenombrado("bizcocho")
        modelo.guardarRenombrado()
        advanceUntilIdle()

        // El cuadro sigue abierto, con lo escrito y con el motivo bajo el campo.
        val dialogo = modelo.dialogo.value as DialogoCantidades.RenombrarSeccion
        assertNotNull(dialogo.error)
        assertNull("El aviso no va abajo: el teclado lo taparía", modelo.estado.value.mensaje)
        assertEquals("bizcocho", dialogo.nombre)
        assertFalse(dialogo.guardando)
        assertEquals(
            listOf("Bizcocho", "Salsa"),
            modelo.estado.value.secciones.map { it.seccion.nombreSeccion }
        )
    }

    @Test
    fun `un ingrediente que vale cero igual cuenta como ingrediente`() = probar { modelo ->
        // Un ingrediente puede valer 0 a propósito: es cómo se dice "esto no suma al costo"
        // (6.2). La receta cuesta 0 pero no está vacía, y la pantalla no puede confundirlas.
        sembrar("Agua", 0.0)
        advanceUntilIdle()
        val seccion = modelo.estado.value.secciones.single().seccion.id

        modelo.abrirAgregarIngrediente(seccion)
        modelo.elegirIngrediente(modelo.estado.value.catalogo.single())
        modelo.cambiarCantidadEscrita("500")
        modelo.guardarIngrediente()
        advanceUntilIdle()

        val estado = modelo.estado.value
        assertEquals(0.0, estado.costoTotal, 0.001)
        assertFalse("Tiene una línea cargada, aunque no sume", estado.sinIngredientes)
        assertEquals(1, estado.secciones.single().lineas.size)
    }

    // --- Lo que cambia desde otro paso ---

    @Test
    fun `reescalar desde el paso del molde se ve aca sin tocar nada`() = probar { modelo ->
        // Es el bug que se veía como "al volver a un molde menor no reescala el
        // ingrediente": la base sí lo cambiaba, pero esta pantalla mostraba su copia vieja
        // hasta que alguna acción de acá disparara una relectura. Por eso "a veces funciona
        // si se insiste".
        sembrar("Harina", 2.0)
        advanceUntilIdle()
        val seccion = modelo.estado.value.secciones.single().seccion.id
        modelo.abrirAgregarIngrediente(seccion)
        modelo.elegirIngrediente(modelo.estado.value.catalogo.single())
        modelo.cambiarCantidadEscrita("500")
        modelo.guardarIngrediente()
        advanceUntilIdle()
        assertEquals(1000.0, modelo.estado.value.costoTotal, 0.001)

        // Nadie toca esta pantalla: el reescalado ocurre en el paso del molde.
        recetas.definirMolde(
            recetaId,
            DimensionesMolde(
                tipoForma = TipoFormaMolde.CUADRADO, ladoCm = 10.0, alturaMoldeCm = 5.0
            ),
            moldeOrigenId = null
        )
        recetas.reescalarPorMolde(
            recetaId,
            DimensionesMolde(
                tipoForma = TipoFormaMolde.CUADRADO, ladoCm = 10.0, alturaMoldeCm = 10.0
            ),
            ModoReescalado.CAPACIDAD,
            moldeOrigenId = null
        )
        advanceUntilIdle()

        assertEquals(
            "Los gramos que se ven son los de la base",
            1000.0,
            modelo.estado.value.secciones.single().lineas.single().item.cantidadG,
            0.001
        )
        assertEquals("Y el costo también", 2000.0, modelo.estado.value.costoTotal, 0.001)
    }

    // --- El título, que ahora se cambia desde adentro ---

    @Test
    fun `renombrar la receta desde adentro cambia el titulo del encabezado`() = probar { modelo ->
        modelo.abrirRenombrarReceta()
        advanceUntilIdle()

        // Llega con el título puesto: se corrige, no se vuelve a escribir entero.
        val abierto = modelo.dialogo.value as DialogoCantidades.RenombrarReceta
        assertEquals("Torta de manjar", abierto.titulo)

        modelo.cambiarTituloDeLaReceta("Torta de manjar y nuez")
        modelo.guardarTituloDeLaReceta()
        advanceUntilIdle()

        assertTrue(modelo.dialogo.value is DialogoCantidades.Ninguno)
        // El encabezado de esta misma pantalla lo muestra, así que tiene que releerse.
        assertEquals("Torta de manjar y nuez", modelo.estado.value.receta?.titulo)
    }

    @Test
    fun `un titulo repetido avisa junto al campo y no cambia nada`() = probar { modelo ->
        recetas.crear("Kuchen de nuez")
        advanceUntilIdle()

        modelo.abrirRenombrarReceta()
        advanceUntilIdle()
        modelo.cambiarTituloDeLaReceta("  KUCHEN DE NUÉZ  ")
        modelo.guardarTituloDeLaReceta()
        advanceUntilIdle()

        // El cuadro queda abierto con lo escrito: el aviso va bajo el campo porque con el
        // teclado abierto la franja de abajo queda tapada (12.2.1).
        val dialogo = modelo.dialogo.value as DialogoCantidades.RenombrarReceta
        assertNotNull(dialogo.error)
        assertNull("El aviso no va abajo", modelo.estado.value.mensaje)
        assertEquals("  KUCHEN DE NUÉZ  ", dialogo.titulo)
        assertFalse(dialogo.guardando)
        assertEquals("Torta de manjar", modelo.estado.value.receta?.titulo)
    }

    @Test
    fun `un titulo vacio no se puede guardar`() = probar { modelo ->
        modelo.abrirRenombrarReceta()
        advanceUntilIdle()
        modelo.cambiarTituloDeLaReceta("   ")

        val dialogo = modelo.dialogo.value as DialogoCantidades.RenombrarReceta
        assertFalse(dialogo.puedeGuardar)
        assertNotNull(dialogo.error)

        // Y aunque se pida igual, no escribe: puedeGuardar corta antes.
        modelo.guardarTituloDeLaReceta()
        advanceUntilIdle()
        assertEquals("Torta de manjar", modelo.estado.value.receta?.titulo)
    }

    @Test
    fun `al escribir de nuevo se limpia el rechazo anterior`() = probar { modelo ->
        recetas.crear("Kuchen de nuez")
        advanceUntilIdle()

        modelo.abrirRenombrarReceta()
        advanceUntilIdle()
        modelo.cambiarTituloDeLaReceta("Kuchen de nuez")
        modelo.guardarTituloDeLaReceta()
        advanceUntilIdle()
        assertNotNull((modelo.dialogo.value as DialogoCantidades.RenombrarReceta).rechazo)

        // El rechazo era sobre el título de antes; al seguir escribiendo deja de aplicar.
        modelo.cambiarTituloDeLaReceta("Kuchen de nuez y almendra")
        val dialogo = modelo.dialogo.value as DialogoCantidades.RenombrarReceta
        assertNull(dialogo.rechazo)
        assertNull(dialogo.error)
        assertTrue(dialogo.puedeGuardar)
    }
}
