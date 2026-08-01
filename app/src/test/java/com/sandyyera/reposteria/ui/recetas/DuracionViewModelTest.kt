package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.duracion.DURACION_NO_APTA
import com.sandyyera.reposteria.logica.duracion.DURACION_SIN_DATO
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import com.sandyyera.reposteria.logica.duracion.UnidadDuracion
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
 * El paso "Duración" visto desde la pantalla (8.4).
 *
 * Lo propio de acá es que **los tres bloques se editan en memoria y se guardan de una vez**:
 * guardar en cada tecla escribiría y borraría filas mientras la persona todavía decide,
 * porque un bloque a medio escribir se ve igual que uno vaciado a propósito.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DuracionViewModelTest {

    private val despachador = StandardTestDispatcher()

    private lateinit var recetas: RecetaRepositorio
    private var recetaId: Long = 0

    @Before
    fun prepararTodo() {
        Dispatchers.setMain(despachador)
        val dao = RecetaDaoFalso(IngredienteDaoFalso())
        recetas = RecetaRepositorio(dao, HistorialRepositorio(HistorialDaoFalso()))
    }

    @After
    fun dejarTodoComoEstaba() {
        Dispatchers.resetMain()
    }

    private fun probar(cuerpo: suspend TestScope.(DuracionViewModel) -> Unit) =
        runTest(despachador) {
            recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId
            val modelo = DuracionViewModel(recetaId, recetas)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    private fun bloque(modelo: DuracionViewModel, tipo: TipoDuracion) =
        modelo.estado.value.bloques.first { it.tipo == tipo }

    // --- Estado inicial ---

    @Test
    fun `arranca con los tres bloques vacios y en orden`() = probar { modelo ->
        val estado = modelo.estado.value
        assertEquals(
            listOf(TipoDuracion.AMBIENTE, TipoDuracion.REFRIGERADA, TipoDuracion.CONGELADA),
            estado.bloques.map { it.tipo }
        )
        assertTrue("El paso puede quedar en blanco, y arranca así", estado.todoVacio)
        assertTrue("Y vacío se puede guardar igual", estado.puedeGuardar)
    }

    @Test
    fun `un bloque vacio se lee como sin anotar`() = probar { modelo ->
        assertEquals(DURACION_SIN_DATO, bloque(modelo, TipoDuracion.AMBIENTE).comoSeLee)
    }

    // --- Escribir ---

    @Test
    fun `lo escrito se lee en vivo con su unidad`() = probar { modelo ->
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "3")
        modelo.cambiarUnidad(TipoDuracion.AMBIENTE, UnidadDuracion.DIAS)
        advanceUntilIdle()

        assertEquals("3 días", bloque(modelo, TipoDuracion.AMBIENTE).comoSeLee)

        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "1")
        advanceUntilIdle()
        assertEquals(
            "En singular también",
            "1 día",
            bloque(modelo, TipoDuracion.AMBIENTE).comoSeLee
        )
    }

    @Test
    fun `la cantidad solo acepta digitos`() = probar { modelo ->
        // Las duraciones son enteras y el punto de mil no aplica a "99 meses".
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "3a,5")
        advanceUntilIdle()
        assertEquals("35", bloque(modelo, TipoDuracion.AMBIENTE).cantidad)
    }

    @Test
    fun `cada bloque se escribe por separado`() = probar { modelo ->
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "2")
        modelo.cambiarCantidad(TipoDuracion.REFRIGERADA, "7")
        advanceUntilIdle()

        assertEquals("2", bloque(modelo, TipoDuracion.AMBIENTE).cantidad)
        assertEquals("7", bloque(modelo, TipoDuracion.REFRIGERADA).cantidad)
        assertEquals("", bloque(modelo, TipoDuracion.CONGELADA).cantidad)
    }

    @Test
    fun `un cero no deja guardar y avisa en su bloque`() = probar { modelo ->
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "0")
        advanceUntilIdle()

        assertNotNull(bloque(modelo, TipoDuracion.AMBIENTE).error)
        assertFalse(modelo.estado.value.puedeGuardar)
    }

    // --- "No apto" ---

    @Test
    fun `marcar no apto se lee como no apto y cuenta como dato`() = probar { modelo ->
        modelo.cambiarApto(TipoDuracion.CONGELADA, apto = false)
        advanceUntilIdle()

        val congelada = bloque(modelo, TipoDuracion.CONGELADA)
        assertEquals(DURACION_NO_APTA, congelada.comoSeLee)
        assertTrue("Que algo no se pueda congelar es justamente el dato", congelada.diceAlgo)
        assertFalse("Así que el paso ya no está vacío", modelo.estado.value.todoVacio)
    }

    @Test
    fun `marcar no apto no borra lo escrito, por si fue un toque por error`() = probar { modelo ->
        modelo.cambiarCantidad(TipoDuracion.CONGELADA, "6")
        modelo.cambiarApto(TipoDuracion.CONGELADA, apto = false)
        advanceUntilIdle()
        assertEquals("6", bloque(modelo, TipoDuracion.CONGELADA).cantidad)

        modelo.cambiarApto(TipoDuracion.CONGELADA, apto = true)
        advanceUntilIdle()

        assertEquals("Vuelve donde estaba", "6", bloque(modelo, TipoDuracion.CONGELADA).cantidad)
    }

    @Test
    fun `un bloque no apto con una cantidad invalida igual se puede guardar`() = probar { modelo ->
        // Con "no apto" la cantidad se ignora por completo: no tiene sentido retar por un
        // campo que ya no significa nada.
        modelo.cambiarCantidad(TipoDuracion.CONGELADA, "0")
        modelo.cambiarApto(TipoDuracion.CONGELADA, apto = false)
        advanceUntilIdle()

        assertTrue(modelo.estado.value.puedeGuardar)
    }

    // --- El guardado automático (8.4.1) ---

    @Test
    fun `salir del campo guarda ese bloque`() = probar { modelo ->
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "2")
        modelo.cambiarUnidad(TipoDuracion.AMBIENTE, UnidadDuracion.DIAS)
        advanceUntilIdle()

        // Lo que hace la pantalla cuando el campo pierde el foco. Ya no hay botón.
        modelo.guardarBloque(TipoDuracion.AMBIENTE)
        advanceUntilIdle()

        val guardadas = recetas.obtenerDuraciones(recetaId)
        assertEquals(2, guardadas.getValue(TipoDuracion.AMBIENTE).cantidad)
    }

    @Test
    fun `el switch de no apto guarda al instante, sin salir de nada`() = probar { modelo ->
        // Un switch no se toca a medias: se elige. Por eso no espera a perder el foco.
        modelo.cambiarApto(TipoDuracion.CONGELADA, apto = false)
        advanceUntilIdle()

        val guardadas = recetas.obtenerDuraciones(recetaId)
        assertFalse(
            "Que algo no se pueda congelar es justamente el dato",
            guardadas.getValue(TipoDuracion.CONGELADA).apto
        )
    }

    @Test
    fun `la unidad tambien guarda al instante`() = probar { modelo ->
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "3")
        modelo.guardarBloque(TipoDuracion.AMBIENTE)
        advanceUntilIdle()

        modelo.cambiarUnidad(TipoDuracion.AMBIENTE, UnidadDuracion.MESES)
        advanceUntilIdle()

        val guardadas = recetas.obtenerDuraciones(recetaId)
        assertEquals(UnidadDuracion.MESES, guardadas.getValue(TipoDuracion.AMBIENTE).unidad)
    }

    @Test
    fun `escribir sin salir del campo todavia no toca la base`() = probar { modelo ->
        // Es la diferencia con el paso de cantidades, y sigue valiendo: un bloque a medio
        // escribir se ve igual que uno vaciado a propósito.
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "3")
        advanceUntilIdle()

        assertTrue(recetas.obtenerDuraciones(recetaId).isEmpty())
    }

    @Test
    fun `un numero invalido no escribe nada`() = probar { modelo ->
        // El 0 no es una duración -- para eso está el switch de "no apto" -- y el error ya
        // sale bajo el campo, así que no hace falta avisar dos veces.
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "0")
        modelo.guardarBloque(TipoDuracion.AMBIENTE)
        advanceUntilIdle()

        assertTrue(recetas.obtenerDuraciones(recetaId).isEmpty())
        assertNotNull(bloque(modelo, TipoDuracion.AMBIENTE).error)
    }

    @Test
    fun `guardar un bloque no toca los otros dos`() = probar { modelo ->
        // Son independientes, y por eso se guardan de a uno: escribir en ambiente no tiene
        // por qué tocar las filas de refrigerada ni de congelada.
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "2")
        modelo.cambiarCantidad(TipoDuracion.REFRIGERADA, "5")
        modelo.guardarBloque(TipoDuracion.AMBIENTE)
        advanceUntilIdle()

        val guardadas = recetas.obtenerDuraciones(recetaId)
        assertEquals(1, guardadas.size)
        assertNull(guardadas[TipoDuracion.REFRIGERADA])
    }

    @Test
    fun `guardar no anuncia el exito`() = probar { modelo ->
        // Sin botón que apretar, un "se guardó" cada vez que se sale de un campo es ruido, y
        // encima aparecería justo mientras se pasa al bloque siguiente.
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "2")
        modelo.guardarBloque(TipoDuracion.AMBIENTE)
        advanceUntilIdle()

        assertNull(modelo.estado.value.mensaje)
    }

    @Test
    fun `al reabrir el paso vuelve lo guardado`() = probar { modelo ->
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "3")
        modelo.cambiarUnidad(TipoDuracion.AMBIENTE, UnidadDuracion.MESES)
        advanceUntilIdle()

        val reabierto = DuracionViewModel(recetaId, recetas)
        backgroundScope.launch(despachador) { reabierto.estado.collect { } }
        advanceUntilIdle()

        val ambiente = bloque(reabierto, TipoDuracion.AMBIENTE)
        assertEquals("3", ambiente.cantidad)
        assertEquals(UnidadDuracion.MESES, ambiente.unidad)
        assertEquals("3 meses", ambiente.comoSeLee)
    }

    @Test
    fun `vaciar un bloque ya guardado lo borra al salir del campo`() = probar { modelo ->
        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "3")
        modelo.guardarBloque(TipoDuracion.AMBIENTE)
        advanceUntilIdle()
        assertEquals(1, recetas.obtenerDuraciones(recetaId).size)

        modelo.cambiarCantidad(TipoDuracion.AMBIENTE, "")
        modelo.guardarBloque(TipoDuracion.AMBIENTE)
        advanceUntilIdle()

        assertTrue(recetas.obtenerDuraciones(recetaId).isEmpty())
    }
}
