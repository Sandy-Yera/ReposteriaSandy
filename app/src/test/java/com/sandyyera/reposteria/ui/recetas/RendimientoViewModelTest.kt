package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.MoldeDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Molde
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.MoldeRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.ModoReescalado
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.rendimiento.PESO_NO_ESPECIFICADO
import com.sandyyera.reposteria.logica.validaciones.CampoDeMolde
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
 * El paso "Rendimiento" visto desde la pantalla (8.3).
 *
 * Lo que se prueba acá y no en el repositorio: que el cuadro de molde **sepa solo** si es la
 * primera vez o un reescalado, y que un rechazo quede a la vista dentro del cuadro en vez de
 * en la franja de abajo, que con el teclado abierto no se ve.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RendimientoViewModelTest {

    private val despachador = StandardTestDispatcher()

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var recetaDao: RecetaDaoFalso
    private lateinit var moldeDao: MoldeDaoFalso
    private lateinit var recetas: RecetaRepositorio
    private lateinit var moldes: MoldeRepositorio
    private var recetaId: Long = 0

    @Before
    fun prepararTodo() {
        Dispatchers.setMain(despachador)
        catalogo = IngredienteDaoFalso()
        recetaDao = RecetaDaoFalso(catalogo)
        moldeDao = MoldeDaoFalso(recetaDao)
        val historial = HistorialRepositorio(HistorialDaoFalso())
        recetas = RecetaRepositorio(recetaDao, historial)
        moldes = MoldeRepositorio(moldeDao, recetas, historial)
    }

    @After
    fun dejarTodoComoEstaba() {
        Dispatchers.resetMain()
    }

    private fun cuadrado(lado: Double, alto: Double) = DimensionesMolde(
        tipoForma = TipoFormaMolde.CUADRADO, ladoCm = lado, alturaMoldeCm = alto
    )

    private fun probar(cuerpo: suspend TestScope.(RendimientoViewModel) -> Unit) =
        runTest(despachador) {
            catalogo.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 2.0))
            val harina = catalogo.obtenerTodosUnaVez().single()
            recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId
            val seccion = recetas.obtenerSecciones(recetaId).single()
            recetas.agregarIngrediente(seccion.id, harina.id, 500.0)

            val modelo = RendimientoViewModel(recetaId, recetas, moldes)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            backgroundScope.launch(despachador) { modelo.dialogo.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    private fun cuadroDeMolde(modelo: RendimientoViewModel) =
        modelo.dialogo.value as DialogoRendimiento.ElegirMolde

    private suspend fun gramos(): Double = recetas.obtenerIngredientes(recetaId).single().cantidadG

    // --- Los dos campos ---

    @Test
    fun `arranca con lo que ya estaba guardado`() = probar { modelo ->
        // Así guardar sin tocar nada no puede cambiar ningún número.
        assertEquals("1", modelo.estado.value.trozos)
        assertEquals("", modelo.estado.value.pesoFinal)
        assertFalse(modelo.estado.value.usaMolde)
    }

    @Test
    fun `sin molde el peso final es obligatorio y no deja guardar`() = probar { modelo ->
        modelo.cambiarTrozos("8")
        advanceUntilIdle()

        assertNotNull(modelo.estado.value.errorPesoFinal)
        assertFalse(modelo.estado.value.puedeGuardar)

        modelo.cambiarPesoFinal("1200")
        advanceUntilIdle()
        assertNull(modelo.estado.value.errorPesoFinal)
        assertTrue(modelo.estado.value.puedeGuardar)
    }

    @Test
    fun `los trozos no aceptan letras ni decimales`() = probar { modelo ->
        // Se filtran al escribir, no se avisa después: "1.000 trozos" no existe.
        modelo.cambiarTrozos("8a,5")
        advanceUntilIdle()
        assertEquals("85", modelo.estado.value.trozos)
    }

    @Test
    fun `el peso por trozo se ve en vivo, y dice No especificado si falta`() = probar { modelo ->
        assertEquals(PESO_NO_ESPECIFICADO, modelo.estado.value.pesoDeCadaTrozo)

        modelo.cambiarTrozos("8")
        modelo.cambiarPesoFinal("1200")
        advanceUntilIdle()

        assertEquals("150", modelo.estado.value.pesoDeCadaTrozo)
    }

    @Test
    fun `el punto de mil se pone solo en el peso`() = probar { modelo ->
        modelo.cambiarPesoFinal("1200")
        advanceUntilIdle()
        assertEquals("1.200", modelo.estado.value.pesoFinal)
    }

    @Test
    fun `guardar deja los datos escritos`() = probar { modelo ->
        modelo.cambiarTrozos("8")
        modelo.cambiarPesoFinal("1200")
        modelo.guardar()
        advanceUntilIdle()

        val rendimiento = recetas.obtenerRendimiento(recetaId)!!
        assertEquals(8, rendimiento.trozos)
        assertEquals(1200.0, rendimiento.pesoFinalG!!, 0.001)
    }

    // --- Definir el molde por primera vez ---

    @Test
    fun `la primera vez el cuadro no es un reescalado y no pide modo`() = probar { modelo ->
        modelo.abrirElegirMolde()
        advanceUntilIdle()

        assertFalse(
            "Sin molde previo no hay nada que conservar",
            cuadroDeMolde(modelo).esReescalado
        )
    }

    @Test
    fun `elegir un molde guardado la primera vez no reescala nada`() = probar { modelo ->
        moldeDao.sembrar(Molde(nombre = "Redondo", dimensiones = cuadrado(20.0, 6.0)))
        advanceUntilIdle()
        modelo.abrirElegirMolde()
        advanceUntilIdle()

        modelo.elegirMoldeGuardado(cuadroDeMolde(modelo).candidatos.single())
        modelo.confirmarMolde()
        advanceUntilIdle()

        assertEquals("Los ingredientes quedan como estaban", 500.0, gramos(), 0.001)
        assertTrue(modelo.estado.value.usaMolde)
        assertTrue("Y queda enlazada al catálogo", modelo.estado.value.enlazadaAlCatalogo)
    }

    @Test
    fun `un molde de prueba deja la receta sin vinculo`() = probar { modelo ->
        modelo.abrirElegirMolde()
        advanceUntilIdle()
        modelo.cambiarOrigenDelMolde(OrigenDelMolde.PRUEBA)
        modelo.elegirFormaDePrueba(TipoFormaMolde.CUADRADO)
        modelo.cambiarMedidaDePrueba(CampoDeMolde.LADO, "20")
        modelo.cambiarMedidaDePrueba(CampoDeMolde.ALTURA_MOLDE, "6")
        advanceUntilIdle()

        assertTrue(cuadroDeMolde(modelo).puedeGuardar)
        modelo.confirmarMolde()
        advanceUntilIdle()

        assertTrue(modelo.estado.value.usaMolde)
        assertFalse(
            "No recibe correcciones del catálogo",
            modelo.estado.value.enlazadaAlCatalogo
        )
    }

    @Test
    fun `el cuadro de prueba pide solo los campos de la forma elegida`() = probar { modelo ->
        modelo.abrirElegirMolde()
        advanceUntilIdle()
        modelo.cambiarOrigenDelMolde(OrigenDelMolde.PRUEBA)

        modelo.elegirFormaDePrueba(TipoFormaMolde.CIRCULO)
        advanceUntilIdle()
        assertEquals(
            listOf(CampoDeMolde.DIAMETRO, CampoDeMolde.ALTURA_MOLDE),
            cuadroDeMolde(modelo).campos
        )
    }

    @Test
    fun `sin nada elegido no se puede confirmar el molde`() = probar { modelo ->
        modelo.abrirElegirMolde()
        advanceUntilIdle()

        assertFalse(cuadroDeMolde(modelo).puedeGuardar)
    }

    // --- Reescalar ---

    @Test
    fun `con molde ya puesto el cuadro sabe que es un reescalado`() = probar { modelo ->
        recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
        advanceUntilIdle()

        modelo.abrirElegirMolde()
        advanceUntilIdle()

        assertTrue(cuadroDeMolde(modelo).esReescalado)
    }

    @Test
    fun `reescalar a un molde del doble de volumen dobla los ingredientes`() = probar { modelo ->
        recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
        moldeDao.sembrar(Molde(nombre = "Alto", dimensiones = cuadrado(10.0, 10.0)))
        advanceUntilIdle()

        modelo.abrirElegirMolde()
        advanceUntilIdle()
        modelo.elegirMoldeGuardado(cuadroDeMolde(modelo).candidatos.single())
        modelo.elegirModoDeReescalado(ModoReescalado.CAPACIDAD)
        modelo.confirmarMolde()
        advanceUntilIdle()

        assertEquals(1000.0, gramos(), 0.001)
    }

    @Test
    fun `un reescalado rechazado deja el motivo dentro del cuadro`() = probar { modelo ->
        // Y no en la franja de abajo: con el teclado abierto no se ve, y además el selector
        // de modo que resuelve el problema está justo ahí adentro.
        recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
        moldeDao.sembrar(Molde(nombre = "Muy alto", dimensiones = cuadrado(10.0, 12.0)))
        advanceUntilIdle()

        modelo.abrirElegirMolde()
        advanceUntilIdle()
        modelo.elegirMoldeGuardado(cuadroDeMolde(modelo).candidatos.single())
        modelo.elegirModoDeReescalado(ModoReescalado.ALTURA)
        modelo.confirmarMolde()
        advanceUntilIdle()

        val cuadro = cuadroDeMolde(modelo)
        assertNotNull("El cuadro sigue abierto, con el motivo", cuadro.rechazo)
        assertFalse(cuadro.guardando)
        assertNull("Y no por abajo", modelo.estado.value.mensaje)
        assertEquals("Sin tocar los ingredientes", 500.0, gramos(), 0.001)
    }

    @Test
    fun `al cambiar de modo el rechazo anterior desaparece`() = probar { modelo ->
        recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
        moldeDao.sembrar(Molde(nombre = "Muy alto", dimensiones = cuadrado(10.0, 12.0)))
        advanceUntilIdle()
        modelo.abrirElegirMolde()
        advanceUntilIdle()
        modelo.elegirMoldeGuardado(cuadroDeMolde(modelo).candidatos.single())
        modelo.elegirModoDeReescalado(ModoReescalado.ALTURA)
        modelo.confirmarMolde()
        advanceUntilIdle()
        assertNotNull(cuadroDeMolde(modelo).rechazo)

        modelo.elegirModoDeReescalado(ModoReescalado.CAPACIDAD)
        advanceUntilIdle()

        assertNull("El rechazo era sobre el modo anterior", cuadroDeMolde(modelo).rechazo)
    }

    // --- Quitar el molde y reescalar por peso ---

    @Test
    fun `quitar el molde sin peso final avisa y no lo quita`() = probar { modelo ->
        recetas.definirMolde(recetaId, cuadrado(20.0, 6.0), null)
        advanceUntilIdle()

        modelo.pedirQuitarMolde()
        modelo.confirmarQuitarMolde()
        advanceUntilIdle()

        assertTrue(modelo.estado.value.usaMolde)
        assertNotNull(modelo.estado.value.mensaje)
    }

    @Test
    fun `reescalar por peso multiplica los ingredientes`() = probar { modelo ->
        modelo.cambiarTrozos("1")
        modelo.cambiarPesoFinal("1000")
        modelo.guardar()
        advanceUntilIdle()

        modelo.abrirReescalarPorPeso()
        modelo.cambiarPesoNuevo("2000")
        modelo.confirmarReescaladoPorPeso()
        advanceUntilIdle()

        assertEquals(1000.0, gramos(), 0.001)
        assertEquals("Y el campo queda con el peso nuevo", "2.000", modelo.estado.value.pesoFinal)
    }
}
