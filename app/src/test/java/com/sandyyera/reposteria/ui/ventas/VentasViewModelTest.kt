package com.sandyyera.reposteria.ui.ventas

import com.sandyyera.reposteria.data.AlmacenDaoFalso
import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.VentaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.repositorio.AlmacenRepositorio
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.data.repositorio.VentaRepositorio
import com.sandyyera.reposteria.logica.precios.ModoPrecio
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
import java.time.LocalDate

/**
 * La sección Ventas vista desde la pantalla (18).
 *
 * Lo que se fija acá son las decisiones del cuadro de anotar, que ninguna prueba de repositorio
 * puede ver: **que el precio no venga relleno con el estimado** —relleno, lo normal sería aceptarlo
 * y lo real y lo estimado serían el mismo número por defecto, o sea nada que comparar— y **que no
 * se pueda anotar una venta con fecha futura**.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VentasViewModelTest {

    private val despachador = StandardTestDispatcher()

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var recetaDao: RecetaDaoFalso
    private lateinit var ventaDao: VentaDaoFalso
    private lateinit var recetas: RecetaRepositorio
    private lateinit var ventas: VentaRepositorio

    @Before
    fun prepararTodo() {
        Dispatchers.setMain(despachador)
        catalogo = IngredienteDaoFalso()
        recetaDao = RecetaDaoFalso(catalogo)
        ventaDao = VentaDaoFalso()
        val historial = HistorialRepositorio(HistorialDaoFalso())
        recetas = RecetaRepositorio(recetaDao, historial)
        val almacen = AlmacenRepositorio(
            dao = AlmacenDaoFalso(catalogo),
            ventas = ventaDao,
            ingredientes = IngredienteRepositorio(catalogo, recetaDao, historial),
            recetas = recetas,
            historial = historial
        )
        ventas = VentaRepositorio(ventaDao, recetas, almacen, historial)
    }

    @After
    fun dejarTodoComoEstaba() {
        Dispatchers.resetMain()
    }

    private fun probar(cuerpo: suspend TestScope.(VentasViewModel) -> Unit) =
        runTest(despachador) {
            val modelo = VentasViewModel(ventas)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            backgroundScope.launch(despachador) { modelo.delDia.collect { } }
            backgroundScope.launch(despachador) { modelo.dialogo.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    /** Costo 1.000, y 4 trozos a 1.000 cada uno: el producto completo se estima en 4.000. */
    private suspend fun recetaConPrecio(titulo: String = "Torta"): Long {
        catalogo.sembrar(Ingrediente(nombre = "Harina $titulo", valorPorGramo = 1.0))
        val harina = catalogo.obtenerTodosUnaVez().first { it.nombre == "Harina $titulo" }
        val id = (recetas.crear(titulo) as ResultadoCrearReceta.Creada).recetaId
        recetas.agregarIngrediente(recetas.obtenerSecciones(id).single().id, harina.id, 1000.0)
        recetas.guardarRendimiento(id, "4", "1.000")
        recetas.crearPrecio(id, ModoPrecio.TROZO, "1", "1.000")
        return id
    }

    private fun registro(modelo: VentasViewModel) =
        modelo.dialogo.value as DialogoVentas.Registrar

    /** Deja el cuadro abierto con una línea de esa receta ya agregada. */
    private suspend fun TestScope.conUnaLinea(modelo: VentasViewModel): LineaEnEdicion {
        recetaConPrecio()
        modelo.abrirRegistrar()
        modelo.abrirElegirReceta()
        advanceUntilIdle()
        val cuadro = modelo.dialogo.value as DialogoVentas.ElegirReceta
        modelo.elegirReceta(cuadro.candidatas.single())
        return registro(modelo).lineas.single()
    }

    // --- Lo que el cuadro de anotar decide ---

    @Test
    fun `la linea entra con el precio vacio aunque la receta tenga uno`() = probar { modelo ->
        // **Es la decisión que sostiene el módulo.** Con el precio relleno, lo normal sería no
        // tocarlo, y entonces lo real y lo estimado serían el mismo número por defecto: no habría
        // nada que comparar, que es justo lo que Ventas existe para hacer.
        val linea = conUnaLinea(modelo)

        assertEquals("", linea.precio)
        assertEquals("Pero el estimado se ve al lado", "4.000", linea.comoSeLeeLoEstimado)
        assertFalse("Y sin precio escrito no se puede guardar", registro(modelo).puedeGuardar)
    }

    @Test
    fun `decir que se cobro lo estimado lo escribe en el campo`() = probar { modelo ->
        val linea = conUnaLinea(modelo)

        modelo.usarElPrecioEstimado(linea.numero)

        assertEquals("4.000", registro(modelo).lineas.single().precio)
        assertTrue(registro(modelo).puedeGuardar)
    }

    @Test
    fun `la misma receta puede ir dos veces a precios distintos`() = probar { modelo ->
        // Vender dos a precio de lista y una con descuento es exactamente el dato que este módulo
        // viene a capturar. Con la receta como clave, la segunda línea pisaría a la primera.
        conUnaLinea(modelo)
        modelo.abrirElegirReceta()
        advanceUntilIdle()
        val cuadro = modelo.dialogo.value as DialogoVentas.ElegirReceta
        modelo.elegirReceta(cuadro.candidatas.single())

        val lineas = registro(modelo).lineas
        assertEquals(2, lineas.size)
        assertEquals("Y cada una con su número propio", 2, lineas.map { it.numero }.distinct().size)
    }

    @Test
    fun `una receta sin precio se ofrece igual, sin estimado`() = probar { modelo ->
        // Lo que falta es la estimación, no la venta: negarse a anotarla perdería el dato real por
        // no tener el estimado. Al revés que al asignarle una receta a un empleado.
        recetas.crear("Kuchen")
        modelo.abrirRegistrar()
        modelo.abrirElegirReceta()
        advanceUntilIdle()

        val cuadro = modelo.dialogo.value as DialogoVentas.ElegirReceta
        modelo.elegirReceta(cuadro.candidatas.single { it.titulo == "Kuchen" })

        val linea = registro(modelo).lineas.single()
        assertFalse(linea.tienePrecio)
        assertNull("Sin precio no hay nada que ofrecer", linea.comoSeLeeLoEstimado)
    }

    // --- La fecha ---

    @Test
    fun `no se puede anotar una venta en el futuro`() = probar { modelo ->
        // Una venta con fecha futura no es un dato que exista todavía, y en el informe se ordena
        // arriba de todo, encima de lo que sí pasó.
        modelo.abrirRegistrar()

        modelo.moverFecha(1)

        assertEquals(LocalDate.now(), registro(modelo).fecha)
        assertTrue(registro(modelo).esHoy)
    }

    @Test
    fun `hacia atras si se puede, y se puede volver a hoy`() = probar { modelo ->
        modelo.abrirRegistrar()

        modelo.moverFecha(-3)
        assertEquals(LocalDate.now().minusDays(3), registro(modelo).fecha)

        modelo.volverAHoy()
        assertEquals(LocalDate.now(), registro(modelo).fecha)
    }

    @Test
    fun `el cuadro se abre en el dia que se este mirando`() = probar { modelo ->
        // Si se abrió el sábado para revisarlo, lo que se va a anotar es del sábado.
        val sabado = LocalDate.now().minusDays(2)
        modelo.abrirDia(sabado.toEpochDay())

        modelo.abrirRegistrar()

        assertEquals(sabado, registro(modelo).fecha)
    }

    // --- Guardar ---

    @Test
    fun `guardar deja la venta y abre su dia`() = probar { modelo ->
        val linea = conUnaLinea(modelo)
        modelo.cambiarUnidades(linea.numero, "2")
        modelo.cambiarPrecio(linea.numero, "5000")

        modelo.guardarVenta()
        advanceUntilIdle()

        assertTrue(modelo.dialogo.value is DialogoVentas.Ninguno)
        assertEquals(
            "Se abre el día recién anotado",
            LocalDate.now().toEpochDay(),
            modelo.estado.value.abierto
        )
        val guardada = modelo.delDia.value.ventas.single()
        assertEquals(2, guardada.lineas.single().unidades)
        val soloLinea = guardada.lineas.single()
        assertEquals("Lo cobrado es lo escrito", 5000.0, soloLinea.precioUnitario, 0.001)
        assertEquals("Y lo estimado se congeló", 4000.0, soloLinea.precioEstimadoUnitario, 0.001)
    }

    @Test
    fun `una venta recien anotada ofrece descontar y no tiene costo real`() = probar { modelo ->
        val linea = conUnaLinea(modelo)
        modelo.cambiarUnidades(linea.numero, "1")
        modelo.cambiarPrecio(linea.numero, "5000")
        modelo.guardarVenta()
        advanceUntilIdle()

        assertEquals(1, modelo.delDia.value.sinDescontar)
        // Sin movimientos el costo real es `null`, y ese `null` es lo que hace que la pantalla
        // muestre el estimado **diciendo que es estimado** (18.5).
        val hoy = modelo.estado.value.elAbierto
        assertNotNull(hoy)
        assertFalse(hoy!!.cifras.costoEsDeVerdad)
    }
}
