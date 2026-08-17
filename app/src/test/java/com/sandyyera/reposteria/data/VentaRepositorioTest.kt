package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.MotivoDeMovimiento
import com.sandyyera.reposteria.data.repositorio.AlmacenRepositorio
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.data.repositorio.LineaParaRegistrar
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.data.repositorio.ResultadoRegistrarVenta
import com.sandyyera.reposteria.data.repositorio.VentaRepositorio
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Las ventas (sección 18), sin base de datos ni celular.
 *
 * Lo que se cuida acá es **lo que se congela**: las tres cifras estimadas de cada línea son un
 * hecho del día de la venta, y si se recalcularan al mirar el informe, corregir un precio en
 * agosto movería el informe de marzo. Un informe que cambia hacia atrás no sirve para decidir
 * nada, y es un error que no se ve — los números siguen siendo verosímiles.
 */
class VentaRepositorioTest {

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var recetaDao: RecetaDaoFalso
    private lateinit var almacenDao: AlmacenDaoFalso
    private lateinit var ventaDao: VentaDaoFalso
    private lateinit var recetas: RecetaRepositorio
    private lateinit var almacen: AlmacenRepositorio
    private lateinit var repositorio: VentaRepositorio

    /** Un día cualquiera, como `LocalDate.toEpochDay()`. Da igual cuál: importa que agrupe. */
    private val hoy = 20_700L

    @Before
    fun prepararTodo() {
        catalogo = IngredienteDaoFalso()
        recetaDao = RecetaDaoFalso(catalogo)
        almacenDao = AlmacenDaoFalso(catalogo)
        ventaDao = VentaDaoFalso()
        val historial = HistorialRepositorio(HistorialDaoFalso())
        recetas = RecetaRepositorio(recetaDao, historial)
        almacen = AlmacenRepositorio(
            dao = almacenDao,
            ventas = ventaDao,
            ingredientes = IngredienteRepositorio(catalogo, recetaDao, historial),
            recetas = recetas,
            historial = historial
        )
        repositorio = VentaRepositorio(ventaDao, recetas, almacen, historial)
    }

    /** Una receta de costo 1.000 que se piensa vender a 4.000 el producto entero. */
    private suspend fun receta(titulo: String = "Torta"): Long {
        catalogo.sembrar(Ingrediente(nombre = "Harina $titulo", valorPorGramo = 1.0))
        val harina = catalogo.obtenerTodosUnaVez().first { it.nombre == "Harina $titulo" }
        val id = (recetas.crear(titulo) as ResultadoCrearReceta.Creada).recetaId
        recetas.agregarIngrediente(recetas.obtenerSecciones(id).single().id, harina.id, 1000.0)
        recetas.guardarRendimiento(id, "4", "1.000")
        recetas.crearPrecio(id, ModoPrecio.TROZO, "1", "1.000")   // 1.000 x 4 trozos = 4.000
        return id
    }

    private suspend fun registrar(recetaId: Long, unidades: Int, precio: Double): Long {
        val r = repositorio.registrar(
            fecha = hoy,
            lineas = listOf(LineaParaRegistrar(recetaId, unidades, precio))
        )
        return (r as ResultadoRegistrarVenta.Registrada).ventaId
    }

    // --- Lo que se congela ---

    @Test
    fun `la linea guarda lo estimado de ese dia`() = runBlocking {
        val id = receta()

        val ventaId = registrar(id, unidades = 2, precio = 5000.0)

        val linea = repositorio.observarLineas(ventaId).first().single()
        assertEquals("Torta", linea.tituloReceta)
        assertEquals(5000.0, linea.precioUnitario, 0.001)
        assertEquals("Lo que la app decía que costaba", 1000.0, linea.costoEstimadoUnitario, 0.001)
        assertEquals("Y lo que decía que se cobraría", 4000.0, linea.precioEstimadoUnitario, 0.001)
    }

    @Test
    fun `cambiar el precio de la receta despues no mueve la venta`() = runBlocking {
        // **Es la razón de ser del congelado.** Sin él, corregir un precio en agosto movería el
        // informe de marzo, y un informe que cambia hacia atrás no sirve para decidir nada.
        val id = receta()
        val ventaId = registrar(id, unidades = 1, precio = 5000.0)

        recetas.crearPrecio(id, ModoPrecio.TROZO, "1", "9.000")

        val linea = repositorio.observarLineas(ventaId).first().single()
        assertEquals(4000.0, linea.precioEstimadoUnitario, 0.001)
    }

    @Test
    fun `una receta sin precio entra con estimado cero y no revienta`() = runBlocking {
        // `ingresoBruto` lanza con razón sin precio, y llamarlo sin mirar antes fue lo que cerró
        // la app en Empleados. Acá además tiene sentido: se puede vender algo a lo que nunca se
        // le puso precio de referencia, y lo que no se sabe es cuánto se **esperaba** cobrar.
        val id = (recetas.crear("Kuchen") as ResultadoCrearReceta.Creada).recetaId

        val ventaId = registrar(id, unidades = 1, precio = 3000.0)

        val linea = repositorio.observarLineas(ventaId).first().single()
        assertEquals(0.0, linea.precioEstimadoUnitario, 0.001)
        assertEquals("Pero lo cobrado sí se guarda", 3000.0, linea.precioUnitario, 0.001)
    }

    @Test
    fun `una venta sin lineas no se registra`() = runBlocking {
        val r = repositorio.registrar(fecha = hoy, lineas = emptyList())

        assertTrue(r is ResultadoRegistrarVenta.NoSePudo)
    }

    // --- El descuento (18.4) ---

    @Test
    fun `descontar una venta mueve el almacen y deja el rastro marcado como venta`() = runBlocking {
        val id = receta()
        almacen.agregar("Harina Torta", false, true, 5000.0, 1.0, null, reemplazarElPrecio = true)
        val ventaId = registrar(id, unidades = 2, precio = 5000.0)

        val r = repositorio.descontarDelAlmacen(ventaId)

        assertTrue(r is Resultado.Listo)
        // Dos tandas de 1.000 g: quedan 3.000 de los 5.000.
        assertEquals(3000.0, almacenDao.obtenerPorIngrediente(
            catalogo.obtenerTodosUnaVez().first { it.nombre == "Harina Torta" }.id
        )!!.cantidad, 0.001)
        val rastro = ventaDao.movimientos.single()
        assertEquals(MotivoDeMovimiento.VENTA, rastro.motivo)
        assertEquals(ventaId, rastro.ventaId)
        assertEquals("Y de ahí sale el costo real", 2000.0, ventaDao.costoRealDe(ventaId)!!, 0.001)
    }

    @Test
    fun `una venta no descuenta dos veces`() = runBlocking {
        // La venta recuerda si ya lo hizo, que es otra pregunta que la de si existen movimientos:
        // distingue una venta que no descontó de una que descontó cero.
        val id = receta()
        almacen.agregar("Harina Torta", false, true, 5000.0, 1.0, null, reemplazarElPrecio = true)
        val ventaId = registrar(id, unidades = 1, precio = 5000.0)
        repositorio.descontarDelAlmacen(ventaId)

        val segunda = repositorio.descontarDelAlmacen(ventaId)

        assertTrue(segunda is Resultado.NoSePudo)
        assertEquals("Y no se movió de nuevo", 1, ventaDao.movimientos.size)
    }

    @Test
    fun `una venta recien registrada todavia no descontó`() = runBlocking {
        val ventaId = registrar(receta(), unidades = 1, precio = 5000.0)

        assertEquals(false, repositorio.obtener(ventaId)!!.descontoDelAlmacen)
        assertNull("Sin movimientos, no hay costo real que mostrar", ventaDao.costoRealDe(ventaId))
    }
}
