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
 * El paso "Molde" de una receta (8.3.1 y 9.3), que antes era la mitad de arriba de Rendimiento.
 *
 * Lo que se prueba acá y no en el repositorio: que el cuadro de molde **sepa solo** si es la
 * primera vez o un reescalado, que un rechazo quede a la vista dentro del cuadro en vez de en
 * la franja de abajo —que con el teclado abierto no se ve—, y que reescalar arrastre el peso
 * del producto además de los ingredientes (8.4.1, #4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoldeDeRecetaViewModelTest {

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

    private fun probar(cuerpo: suspend TestScope.(MoldeDeRecetaViewModel) -> Unit) =
        runTest(despachador) {
            catalogo.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 2.0))
            val harina = catalogo.obtenerTodosUnaVez().single()
            recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId
            val seccion = recetas.obtenerSecciones(recetaId).single()
            recetas.agregarIngrediente(seccion.id, harina.id, 500.0)

            val modelo = MoldeDeRecetaViewModel(recetaId, recetas, moldes)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            backgroundScope.launch(despachador) { modelo.dialogo.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    private fun cuadro(modelo: MoldeDeRecetaViewModel) =
        modelo.dialogo.value as DialogoMoldeDeReceta.Elegir

    private suspend fun gramos(): Double = recetas.obtenerIngredientes(recetaId).single().cantidadG

    private suspend fun pesoGuardado(): Double? = recetas.obtenerRendimiento(recetaId)?.pesoFinalG

    private suspend fun pesoSinRevisar(): Boolean =
        recetas.obtenerRendimiento(recetaId)?.pesoReescaladoSinRevisar ?: false

    // --- Lo que la tarjeta muestra ---

    @Test
    fun `al quitar el molde no queda el rastro de sus medidas`() = probar { modelo ->
        // `quitarMolde` conserva las medidas a propósito, por si fue un error. Pero la
        // tarjeta las seguía mostrando debajo de "No utiliza molde", que es un rastro de algo
        // que ya no está.
        moldeDao.sembrar(Molde(nombre = "Redondo", dimensiones = cuadrado(20.0, 6.0)))
        advanceUntilIdle()
        modelo.abrirElegirMolde()
        advanceUntilIdle()
        modelo.elegirMoldeGuardado(cuadro(modelo).candidatos.single())
        modelo.confirmarMolde()
        recetas.guardarRendimiento(recetaId, "8", "1.000")
        advanceUntilIdle()
        assertNotNull("Con molde sí se ven", modelo.estado.value.medidasDelMolde)

        modelo.pedirQuitarMolde()
        modelo.confirmarQuitarMolde()
        advanceUntilIdle()

        assertFalse(modelo.estado.value.usaMolde)
        assertNull("Sin molde no queda rastro", modelo.estado.value.medidasDelMolde)
        assertNull(modelo.estado.value.areaYVolumen)
    }

    @Test
    fun `la tarjeta dice cuanto mide el molde, no solo su area`() = probar { modelo ->
        // Frente al mueble uno busca el de 20 por 20, no el de 400 cm².
        moldeDao.sembrar(Molde(nombre = "Cuadrado", dimensiones = cuadrado(20.0, 6.0)))
        advanceUntilIdle()
        modelo.abrirElegirMolde()
        advanceUntilIdle()
        modelo.elegirMoldeGuardado(cuadro(modelo).candidatos.single())
        modelo.confirmarMolde()
        advanceUntilIdle()

        val medidas = modelo.estado.value.medidasDelMolde!!
        assertTrue("Dice los lados", medidas.contains("20"))
        assertTrue("Y el alto", medidas.contains("6"))
        assertTrue("El área queda aparte", modelo.estado.value.areaYVolumen!!.contains("cm²"))
    }

    // --- Cuál es el molde en uso ---

    @Test
    fun `el molde que la receta usa se marca y no se puede elegir`() = probar { modelo ->
        // Lo pidió Sandy al probar quitar y poner molde: al abrir la lista no había forma de
        // saber en cuál estaba. Elegirlo tampoco haría nada, así que además no se toca — el
        // mismo criterio de la ficha del paso actual.
        moldeDao.sembrar(Molde(nombre = "Redondo 20", dimensiones = cuadrado(20.0, 6.0)))
        moldeDao.sembrar(Molde(nombre = "Redondo 24", dimensiones = cuadrado(24.0, 6.0)))
        advanceUntilIdle()
        modelo.abrirElegirMolde()
        advanceUntilIdle()
        val elVeinte = cuadro(modelo).candidatos.first { it.nombre == "Redondo 20" }
        modelo.elegirMoldeGuardado(elVeinte)
        modelo.confirmarMolde()
        advanceUntilIdle()

        modelo.abrirElegirMolde()
        advanceUntilIdle()

        val abierto = cuadro(modelo)
        assertEquals(elVeinte.id, abierto.moldeActualId)
        assertNotNull("El que usa se marca", abierto.motivoNoDisponible(elVeinte))
        assertNull(
            "Y los demás siguen disponibles",
            abierto.motivoNoDisponible(abierto.candidatos.first { it.nombre == "Redondo 24" })
        )
    }

    @Test
    fun `sin molde no hay ninguno marcado`() = probar { modelo ->
        moldeDao.sembrar(Molde(nombre = "Redondo 20", dimensiones = cuadrado(20.0, 6.0)))
        advanceUntilIdle()

        modelo.abrirElegirMolde()
        advanceUntilIdle()

        val abierto = cuadro(modelo)
        assertNull(abierto.moldeActualId)
        assertNull(abierto.motivoNoDisponible(abierto.candidatos.single()))
    }

    @Test
    fun `un molde medido a mano no marca ninguno del catalogo`() = probar { modelo ->
        // En modo prueba la receta no queda enlazada: no hay catálogo al cual apuntar, y
        // marcar uno "parecido" diría algo que no es cierto.
        moldeDao.sembrar(Molde(nombre = "Redondo 20", dimensiones = cuadrado(20.0, 6.0)))
        advanceUntilIdle()
        modelo.abrirElegirMolde()
        advanceUntilIdle()
        modelo.cambiarOrigenDelMolde(OrigenDelMolde.PRUEBA)
        modelo.elegirFormaDePrueba(TipoFormaMolde.CUADRADO)
        modelo.cambiarMedidaDePrueba(CampoDeMolde.LADO, "20")
        modelo.cambiarMedidaDePrueba(CampoDeMolde.ALTURA_MOLDE, "6")
        modelo.confirmarMolde()
        advanceUntilIdle()

        modelo.abrirElegirMolde()
        advanceUntilIdle()

        assertNull(cuadro(modelo).moldeActualId)
    }

    // --- Definir el molde por primera vez ---

    @Test
    fun `la primera vez el cuadro no es un reescalado y no pide modo`() = probar { modelo ->
        modelo.abrirElegirMolde()
        advanceUntilIdle()

        assertFalse("Sin molde previo no hay nada que conservar", cuadro(modelo).esReescalado)
    }

    @Test
    fun `elegir un molde guardado la primera vez no reescala nada`() = probar { modelo ->
        moldeDao.sembrar(Molde(nombre = "Redondo", dimensiones = cuadrado(20.0, 6.0)))
        advanceUntilIdle()
        modelo.abrirElegirMolde()
        advanceUntilIdle()

        modelo.elegirMoldeGuardado(cuadro(modelo).candidatos.single())
        modelo.confirmarMolde()
        advanceUntilIdle()

        assertEquals("Los ingredientes quedan como estaban", 500.0, gramos(), 0.001)
        assertTrue(modelo.estado.value.usaMolde)
        assertTrue("Y queda enlazada al catálogo", modelo.estado.value.enlazadaAlCatalogo)
    }

    @Test
    fun `definir el molde por primera vez no marca el peso para comprobar`() = probar { modelo ->
        // Estrenar molde no es reescalar: no hay factor, así que el peso no se toca y no hay
        // nada que ir a comprobar.
        recetas.guardarRendimiento(recetaId, "8", "1.000")
        moldeDao.sembrar(Molde(nombre = "Redondo", dimensiones = cuadrado(20.0, 6.0)))
        advanceUntilIdle()
        modelo.abrirElegirMolde()
        advanceUntilIdle()

        modelo.elegirMoldeGuardado(cuadro(modelo).candidatos.single())
        modelo.confirmarMolde()
        advanceUntilIdle()

        assertEquals(1000.0, pesoGuardado()!!, 0.001)
        assertFalse(pesoSinRevisar())
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

        assertTrue(cuadro(modelo).puedeGuardar)
        modelo.confirmarMolde()
        advanceUntilIdle()

        assertTrue(modelo.estado.value.usaMolde)
        assertFalse("No recibe correcciones del catálogo", modelo.estado.value.enlazadaAlCatalogo)
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
            cuadro(modelo).campos
        )
    }

    @Test
    fun `sin nada elegido no se puede confirmar el molde`() = probar { modelo ->
        modelo.abrirElegirMolde()
        advanceUntilIdle()

        assertFalse(cuadro(modelo).puedeGuardar)
    }

    // --- Reescalar ---

    @Test
    fun `con molde ya puesto el cuadro sabe que es un reescalado`() = probar { modelo ->
        recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
        advanceUntilIdle()

        modelo.abrirElegirMolde()
        advanceUntilIdle()

        assertTrue(cuadro(modelo).esReescalado)
    }

    @Test
    fun `reescalar a un molde del doble de volumen dobla los ingredientes`() = probar { modelo ->
        recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
        moldeDao.sembrar(Molde(nombre = "Alto", dimensiones = cuadrado(10.0, 10.0)))
        advanceUntilIdle()

        modelo.abrirElegirMolde()
        advanceUntilIdle()
        modelo.elegirMoldeGuardado(cuadro(modelo).candidatos.single())
        modelo.elegirModoDeReescalado(ModoReescalado.CAPACIDAD)
        modelo.confirmarMolde()
        advanceUntilIdle()

        assertEquals(1000.0, gramos(), 0.001)
    }

    @Test
    fun `reescalar dobla tambien el peso del producto y lo deja por comprobar`() =
        probar { modelo ->
            // Sin esto la receta se contradice: el doble de masa y el mismo peso final, con
            // lo que el peso por trozo sale a la mitad de lo que corresponde y nada avisa.
            recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
            recetas.guardarRendimiento(recetaId, "8", "1.000")
            moldeDao.sembrar(Molde(nombre = "Alto", dimensiones = cuadrado(10.0, 10.0)))
            advanceUntilIdle()

            modelo.abrirElegirMolde()
            advanceUntilIdle()
            modelo.elegirMoldeGuardado(cuadro(modelo).candidatos.single())
            modelo.elegirModoDeReescalado(ModoReescalado.CAPACIDAD)
            modelo.confirmarMolde()
            advanceUntilIdle()

            assertEquals(2000.0, pesoGuardado()!!, 0.001)
            assertTrue("Queda pidiendo que lo comprueben", pesoSinRevisar())
            assertNotNull(
                "Y el aviso manda a rendimiento, que es donde se ve el peso",
                modelo.estado.value.mensaje
            )
        }

    @Test
    fun `sin peso anotado el reescalado no inventa uno ni pide comprobar nada`() =
        probar { modelo ->
            // Con molde el peso final es opcional (8.3): si no está, no hay nada que
            // multiplicar y tampoco nada que ir a mirar.
            recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
            moldeDao.sembrar(Molde(nombre = "Alto", dimensiones = cuadrado(10.0, 10.0)))
            advanceUntilIdle()

            modelo.abrirElegirMolde()
            advanceUntilIdle()
            modelo.elegirMoldeGuardado(cuadro(modelo).candidatos.single())
            modelo.confirmarMolde()
            advanceUntilIdle()

            assertNull(pesoGuardado())
            assertFalse(pesoSinRevisar())
        }

    @Test
    fun `un reescalado rechazado deja el motivo dentro del cuadro`() = probar { modelo ->
        // Y no en la franja de abajo: con el teclado abierto no se ve, y además el selector
        // de modo que resuelve el problema está justo ahí adentro.
        recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
        recetas.guardarRendimiento(recetaId, "8", "1.000")
        moldeDao.sembrar(Molde(nombre = "Muy alto", dimensiones = cuadrado(10.0, 12.0)))
        advanceUntilIdle()

        modelo.abrirElegirMolde()
        advanceUntilIdle()
        modelo.elegirMoldeGuardado(cuadro(modelo).candidatos.single())
        modelo.elegirModoDeReescalado(ModoReescalado.ALTURA)
        modelo.confirmarMolde()
        advanceUntilIdle()

        val abierto = cuadro(modelo)
        assertNotNull("El cuadro sigue abierto, con el motivo", abierto.rechazo)
        assertFalse(abierto.guardando)
        assertNull("Y no por abajo", modelo.estado.value.mensaje)
        assertEquals("Sin tocar los ingredientes", 500.0, gramos(), 0.001)
        assertEquals("Ni el peso", 1000.0, pesoGuardado()!!, 0.001)
        assertFalse("Ni el aviso", pesoSinRevisar())
    }

    @Test
    fun `al cambiar de modo el rechazo anterior desaparece`() = probar { modelo ->
        recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
        moldeDao.sembrar(Molde(nombre = "Muy alto", dimensiones = cuadrado(10.0, 12.0)))
        advanceUntilIdle()
        modelo.abrirElegirMolde()
        advanceUntilIdle()
        modelo.elegirMoldeGuardado(cuadro(modelo).candidatos.single())
        modelo.elegirModoDeReescalado(ModoReescalado.ALTURA)
        modelo.confirmarMolde()
        advanceUntilIdle()
        assertNotNull(cuadro(modelo).rechazo)

        modelo.elegirModoDeReescalado(ModoReescalado.CAPACIDAD)
        advanceUntilIdle()

        assertNull("El rechazo era sobre el modo anterior", cuadro(modelo).rechazo)
    }

    // --- Quitar el molde ---

    @Test
    fun `sin peso final la pantalla no deja siquiera confirmar el quitado`() = probar { modelo ->
        recetas.definirMolde(recetaId, cuadrado(20.0, 6.0), null)
        advanceUntilIdle()

        // Y lo dice antes: el repositorio lo rechazaría igual, pero enterarse al confirmar
        // es enterarse cuando ya se decidió.
        assertFalse(modelo.estado.value.tienePesoFinal)
    }

    @Test
    fun `anotar el peso en el otro paso habilita quitar el molde, sin tocar nada aca`() =
        probar { modelo ->
            // El caso que costó insistir en el celular: la advertencia decía que faltaba el
            // peso, se anotaba en Rendimiento, se volvía… y el botón seguía apagado, porque
            // esta pantalla se había quedado con su foto de cuando se abrió.
            recetas.definirMolde(recetaId, cuadrado(20.0, 6.0), null)
            advanceUntilIdle()
            assertFalse(modelo.estado.value.tienePesoFinal)

            recetas.guardarRendimiento(recetaId, "8", "1.000")
            advanceUntilIdle()

            assertTrue(modelo.estado.value.tienePesoFinal)
        }

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
    fun `con peso final el molde se quita y conserva las medidas`() = probar { modelo ->
        recetas.definirMolde(recetaId, cuadrado(20.0, 6.0), null)
        recetas.guardarRendimiento(recetaId, "8", "1.000")
        advanceUntilIdle()

        modelo.pedirQuitarMolde()
        modelo.confirmarQuitarMolde()
        advanceUntilIdle()

        assertFalse(modelo.estado.value.usaMolde)
        // Las medidas se quedan por si fue un error y hay que volver atrás (5.2).
        assertNotNull(recetas.obtenerRendimiento(recetaId)!!.dimensiones)
    }
}
