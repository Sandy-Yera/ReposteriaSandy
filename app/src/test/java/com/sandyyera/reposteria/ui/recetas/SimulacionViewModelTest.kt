package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.simulacion.SEMANAS_POR_MES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * El paso "Ganancias simuladas" visto desde la pantalla (8.7).
 *
 * Lo que hay que cuidar acá es distinto que en el resto: **nada explota** con un número
 * absurdo, porque los dos campos se multiplican contra el ingreso y ya. Un 200 escrito en vez
 * de un 20 no rompe nada, sale como una proyección mensual creíble y diez veces falsa — así
 * que las reglas de los campos son la única defensa.
 *
 * Y la otra mitad: que el ingreso salga del **reparto de la semana** y no de multiplicar el de
 * un producto (8.6.1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulacionViewModelTest {

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

    /** Una receta que cuesta 900 y rinde 3 trozos: la del ejemplo del resto (8.6.1). */
    private fun probar(cuerpo: suspend TestScope.(SimulacionViewModel) -> Unit) =
        runTest(despachador) {
            catalogo.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 3.0))
            val harina = catalogo.obtenerTodosUnaVez().single()
            recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId
            recetas.agregarIngrediente(
                recetas.obtenerSecciones(recetaId).single().id, harina.id, 300.0
            )
            recetas.guardarRendimiento(recetaId, "3", "1.000")

            val modelo = SimulacionViewModel(recetaId, recetas)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    // --- Sin precio no hay nada que proyectar ---

    @Test
    fun `sin precio guardado no se muestra ninguna cifra`() = probar { modelo ->
        modelo.cambiarDias("4")
        modelo.cambiarUnidades("2")
        advanceUntilIdle()

        assertFalse(modelo.estado.value.tienePrecio)
        assertNull(modelo.estado.value.resultado)
    }

    // --- El reparto de la semana (8.6.1) ---

    @Test
    fun `los restos se juntan en la semana en vez de arrastrarse por producto`() =
        probar { modelo ->
            // Receta de 3 trozos, promo de 2. Mirando un producto siempre sobra uno; en la
            // semana, 2 productos son 6 trozos y la promo entra tres veces justas.
            recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "2.000")
            recetas.crearPrecio(recetaId, ModoPrecio.PRODUCTO, "1", "5.000")
            recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "2", "3.000")
            val promo = recetas.observarPrecios(recetaId).first().first { it.cantidad == 2 }
            recetas.elegirPrecioDeReferencia(recetaId, promo.id)
            advanceUntilIdle()

            modelo.cambiarDias("1")
            modelo.cambiarUnidades("2")
            advanceUntilIdle()

            val estado = modelo.estado.value
            assertEquals(3, estado.reparto!!.cuantasVecesEntra)
            assertEquals(0, estado.reparto!!.sueltos)
            assertEquals(9000.0, estado.resultado!!.ingresoSemanal, 0.001)
            // Multiplicar el ingreso de un producto (5.000) daría 10.000: mil que no entran.
            assertNull("Y sin resto no hay nada que avisar", estado.avisoDelResto)
        }

    @Test
    fun `si el total de la semana tampoco divide, se avisa del resto`() = probar { modelo ->
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "2.000")
        recetas.crearPrecio(recetaId, ModoPrecio.PRODUCTO, "1", "5.000")
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "2", "3.000")
        val promo = recetas.observarPrecios(recetaId).first().first { it.cantidad == 2 }
        recetas.elegirPrecioDeReferencia(recetaId, promo.id)
        advanceUntilIdle()

        // Un solo producto en la semana: 3 trozos, una promo y un suelto.
        modelo.cambiarDias("1")
        modelo.cambiarUnidades("1")
        advanceUntilIdle()

        val estado = modelo.estado.value
        assertEquals(1, estado.reparto!!.sueltos)
        assertNotNull(estado.avisoDelResto)
        assertTrue(estado.avisoDelResto!!.contains("1 suelto"))
        assertEquals(5000.0, estado.resultado!!.ingresoSemanal, 0.001)
    }

    // --- De dónde sale el "Entra" (lo que Sandy no podía comprobar) ---

    @Test
    fun `la pantalla dice de que se compone el ingreso de la semana`() = probar { modelo ->
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "2.000")
        recetas.crearPrecio(recetaId, ModoPrecio.PRODUCTO, "1", "5.000")
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "2", "3.000")
        val promo = recetas.observarPrecios(recetaId).first().first { it.cantidad == 2 }
        recetas.elegirPrecioDeReferencia(recetaId, promo.id)
        advanceUntilIdle()

        modelo.cambiarDias("1")
        modelo.cambiarUnidades("2")
        advanceUntilIdle()

        // Sin esta línea el número de abajo es correcto y aun así imposible de verificar.
        val linea = modelo.estado.value.deQueSeCompone!!
        assertTrue("Dice cuánto se vende", linea.contains("6 trozos"))
        assertTrue("Y cuántas veces entra la promoción", linea.contains("3 veces"))
    }

    @Test
    fun `explica por que la semana no es multiplicar un producto`() = probar { modelo ->
        // Este es el aviso que faltaba cuando Sandy comparó las dos pantallas y no le cuadró.
        // 3 trozos con promo de 2: un producto deja siempre un suelto; dos productos son 6
        // trozos y la promo entra tres veces, o sea una más que las dos de uno por uno.
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "2.000")
        recetas.crearPrecio(recetaId, ModoPrecio.PRODUCTO, "1", "5.000")
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "2", "3.000")
        val promo = recetas.observarPrecios(recetaId).first().first { it.cantidad == 2 }
        recetas.elegirPrecioDeReferencia(recetaId, promo.id)
        advanceUntilIdle()

        modelo.cambiarDias("1")
        modelo.cambiarUnidades("2")
        advanceUntilIdle()

        val aviso = modelo.estado.value.porQueNoEsMultiplicar!!
        assertTrue(aviso.contains("1 promoción más"))
    }

    @Test
    fun `cuando la cuenta simple da lo mismo, no se explica nada`() = probar { modelo ->
        // Sin promoción no hay resto que juntar, así que la semana **sí** es el producto
        // multiplicado y decir algo sería ruido.
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "2.000")
        advanceUntilIdle()

        modelo.cambiarDias("3")
        modelo.cambiarUnidades("2")
        advanceUntilIdle()

        assertNull(modelo.estado.value.porQueNoEsMultiplicar)
        assertNotNull("Pero de qué se compone se dice igual", modelo.estado.value.deQueSeCompone)
    }

    // --- Las cifras ---

    @Test
    fun `el costo se multiplica aunque el ingreso se reparta`() = probar { modelo ->
        // Producir dos tortas cuesta el doble. Es la asimetría del paso.
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "2.000")
        advanceUntilIdle()

        modelo.cambiarDias("2")
        modelo.cambiarUnidades("3")
        advanceUntilIdle()

        val r = modelo.estado.value.resultado!!
        assertEquals("6 productos x 900", 5400.0, r.costoSemanal, 0.001)
        assertEquals("6 productos x 3 trozos x 2.000", 36000.0, r.ingresoSemanal, 0.001)
        assertEquals(30600.0, r.gananciaSemanal, 0.001)
    }

    @Test
    fun `lo mensual son 4 coma 33 semanas`() = probar { modelo ->
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "2.000")
        advanceUntilIdle()
        modelo.cambiarDias("1")
        modelo.cambiarUnidades("1")
        advanceUntilIdle()

        val r = modelo.estado.value.resultado!!
        assertEquals(r.ingresoSemanal * SEMANAS_POR_MES, r.ingresoMensual, 0.001)
    }

    // --- Los dos campos ---

    @Test
    fun `cero unidades da cero y no es un error`() = probar { modelo ->
        // Es como se dice "esta receta todavía no la vendo", sin borrar lo configurado.
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "2.000")
        advanceUntilIdle()

        modelo.cambiarDias("4")
        modelo.cambiarUnidades("0")
        advanceUntilIdle()

        assertNull("El 0 en unidades es válido", modelo.estado.value.errorUnidades)
        assertEquals(0.0, modelo.estado.value.resultado!!.ingresoSemanal, 0.001)
    }

    @Test
    fun `cero dias si es un error, y el aviso enseña la salida`() = probar { modelo ->
        modelo.cambiarDias("0")
        advanceUntilIdle()

        val error = modelo.estado.value.errorDias
        assertNotNull(error)
        assertTrue("Manda a poner 0 unidades", error!!.contains("unidades"))
    }

    @Test
    fun `una semana no tiene ocho dias`() = probar { modelo ->
        modelo.cambiarDias("8")
        advanceUntilIdle()

        assertNotNull(modelo.estado.value.errorDias)
        assertFalse(modelo.estado.value.puedeGuardar)
    }

    @Test
    fun `con los campos a medio escribir no se muestra una cifra`() = probar { modelo ->
        // Mostrar un número salido de un dato inválido sería peor que no mostrar nada,
        // porque parecería un resultado.
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "2.000")
        advanceUntilIdle()

        modelo.cambiarDias("")
        modelo.cambiarUnidades("2")
        advanceUntilIdle()

        assertNull(modelo.estado.value.resultado)
    }

    // --- El guardado automático ---

    @Test
    fun `guarda solo, sin boton y sin anunciarlo`() = probar { modelo ->
        modelo.cambiarDias("5")
        modelo.cambiarUnidades("3")
        advanceUntilIdle()

        val guardado = recetaDao.obtenerSimulacionVenta(recetaId)!!
        assertEquals(5, guardado.diasPorSemana)
        assertEquals(3, guardado.unidadesPorDia)
        // Sin botón, un "se guardó" por cada número tecleado sería ruido, y este es el paso
        // donde más se teclea: la gracia es probar combinaciones.
        assertNull(modelo.estado.value.mensaje)
    }

    @Test
    fun `con un numero invalido no escribe nada`() = probar { modelo ->
        modelo.cambiarDias("4")
        modelo.cambiarUnidades("2")
        advanceUntilIdle()

        modelo.cambiarDias("9")
        advanceUntilIdle()

        // Lo guardado sigue siendo lo último que sí servía.
        assertEquals(4, recetaDao.obtenerSimulacionVenta(recetaId)!!.diasPorSemana)
    }

    @Test
    fun `al volver al paso los campos traen lo guardado`() = probar { modelo ->
        modelo.cambiarDias("6")
        modelo.cambiarUnidades("4")
        advanceUntilIdle()

        // Otro ViewModel sobre la misma base: lo más parecido a cerrar la app y volver.
        val otro = SimulacionViewModel(recetaId, recetas)
        backgroundScope.launch(despachador) { otro.estado.collect { } }
        advanceUntilIdle()

        assertEquals("6", otro.estado.value.diasPorSemana)
        assertEquals("4", otro.estado.value.unidadesPorDia)
    }

    @Test
    fun `cambiar el precio desde el otro paso mueve la proyeccion`() = probar { modelo ->
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "1", "2.000")
        advanceUntilIdle()
        modelo.cambiarDias("1")
        modelo.cambiarUnidades("1")
        advanceUntilIdle()
        val antes = modelo.estado.value.resultado!!.ingresoSemanal

        val elPrecio = recetas.observarPrecios(recetaId).first().single()
        recetas.editarPrecio(elPrecio.id, ModoPrecio.TROZO, "1", "3.000")
        advanceUntilIdle()

        // Sin observar el snapshot, esta pantalla seguiría proyectando plata que ya no es.
        assertTrue(modelo.estado.value.resultado!!.ingresoSemanal > antes)
    }
}
