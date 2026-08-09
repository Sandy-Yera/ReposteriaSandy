package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.MENSAJE_ALTURA_RIESGOSA
import com.sandyyera.reposteria.logica.moldes.ModoReescalado
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El paso "Rendimiento" (8.3): trozos, peso final, molde y reescalado.
 *
 * Lo que se cubre acá y no en `logica/`: que **definir el molde por primera vez no reescale
 * nada** —la confusión que más caro sale de toda la fase—, que un reescalado rechazado no
 * deje la receta a medias, y que bajar los trozos avise antes de romper una promoción.
 */
class RendimientoRepositorioTest {

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var dao: RecetaDaoFalso
    private lateinit var historial: HistorialDaoFalso
    private lateinit var repositorio: RecetaRepositorio

    @Before
    fun prepararTodo() {
        catalogo = IngredienteDaoFalso()
        dao = RecetaDaoFalso(catalogo)
        historial = HistorialDaoFalso()
        repositorio = RecetaRepositorio(dao, HistorialRepositorio(historial))
    }

    private fun cuadrado(lado: Double, alto: Double) = DimensionesMolde(
        tipoForma = TipoFormaMolde.CUADRADO, ladoCm = lado, alturaMoldeCm = alto
    )

    /** Una receta con 500 g de harina a $2, o sea 1.000 de costo. */
    private suspend fun recetaConHarina(titulo: String = "Torta de manjar"): Long {
        catalogo.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 2.0))
        val harina = catalogo.obtenerTodosUnaVez().first { it.nombre == "Harina" }
        val id = (repositorio.crear(titulo) as ResultadoCrearReceta.Creada).recetaId
        val seccion = repositorio.obtenerSecciones(id).single()
        repositorio.agregarIngrediente(seccion.id, harina.id, 500.0)
        return id
    }

    private suspend fun gramosDe(recetaId: Long): Double =
        repositorio.obtenerIngredientes(recetaId).single().cantidadG

    // --- Trozos y peso final ---

    @Test
    fun `guardar trozos y peso final los deja escritos`() = runBlocking {
        val id = recetaConHarina()

        assertTrue(repositorio.guardarRendimiento(id, "8", "1.200") is Resultado.Listo)

        val rendimiento = repositorio.obtenerRendimiento(id)!!
        assertEquals(8, rendimiento.trozos)
        assertEquals(1200.0, rendimiento.pesoFinalG!!, 0.001)
    }

    @Test
    fun `sin molde el peso final no se puede dejar vacio`() = runBlocking {
        val id = recetaConHarina()   // una receta nueva arranca sin molde

        val resultado = repositorio.guardarRendimiento(id, "8", "")

        assertTrue(resultado is Resultado.NoSePudo)
        assertEquals("Y no escribe nada", 1, repositorio.obtenerRendimiento(id)!!.trozos)
    }

    @Test
    fun `con molde el peso final si se puede dejar vacio`() = runBlocking {
        val id = recetaConHarina()
        repositorio.definirMolde(id, cuadrado(20.0, 6.0), moldeOrigenId = null)

        assertTrue(repositorio.guardarRendimiento(id, "8", "") is Resultado.Listo)
        assertNull(repositorio.obtenerRendimiento(id)!!.pesoFinalG)
    }

    @Test
    fun `cero trozos no se guarda`() = runBlocking {
        val id = recetaConHarina()
        assertTrue(repositorio.guardarRendimiento(id, "0", "1.200") is Resultado.NoSePudo)
        assertEquals(1, repositorio.obtenerRendimiento(id)!!.trozos)
    }

    @Test
    fun `bajar los trozos avisa nombrando las promociones que ya no caben`() = runBlocking {
        val id = recetaConHarina()
        repositorio.guardarRendimiento(id, "8", "1.200")
        dao.insertarPrecio(
            RecetaPrecio(
                recetaId = id, modo = ModoPrecio.TROZO, cantidad = 6,
                precioTotal = 6000.0, etiqueta = "Media torta"
            )
        )

        val resultado = repositorio.guardarRendimiento(id, "2", "1.200") as Resultado.NoSePudo

        // El aviso tiene que decir cuál: "hay promociones que no caben" obliga a revisarlas
        // todas a mano.
        assertTrue(resultado.motivo.contains("Media torta"))
        assertEquals("Y no escribe nada", 8, repositorio.obtenerRendimiento(id)!!.trozos)
    }

    @Test
    fun `una promo por producto no impide bajar los trozos`() = runBlocking {
        // Vender 3 productos completos es posible por más que cada uno rinda 2 trozos.
        val id = recetaConHarina()
        repositorio.guardarRendimiento(id, "8", "1.200")
        dao.insertarPrecio(
            RecetaPrecio(
                recetaId = id, modo = ModoPrecio.PRODUCTO, cantidad = 3, precioTotal = 9000.0
            )
        )

        assertTrue(repositorio.guardarRendimiento(id, "2", "1.200") is Resultado.Listo)
    }

    // --- Definir el molde por primera vez ---

    @Test
    fun `definir el molde por primera vez no reescala nada`() = runBlocking {
        // La confusión más cara de la fase: la primera vez no hay molde original contra el
        // cual comparar, así que no hay factor y las cantidades quedan como se escribieron.
        val id = recetaConHarina()

        val resultado = repositorio.definirMolde(id, cuadrado(20.0, 6.0), moldeOrigenId = 7L)

        assertTrue(resultado is Resultado.Listo)
        assertEquals(500.0, gramosDe(id), 0.001)
        val rendimiento = repositorio.obtenerRendimiento(id)!!
        assertTrue(rendimiento.usaMolde)
        assertEquals(7L, rendimiento.moldeOrigenId)
        assertEquals(20.0, rendimiento.dimensiones!!.ladoCm!!, 0.001)
    }

    @Test
    fun `en modo prueba el molde queda sin vinculo`() = runBlocking {
        val id = recetaConHarina()

        repositorio.definirMolde(id, cuadrado(20.0, 6.0), moldeOrigenId = null)

        val rendimiento = repositorio.obtenerRendimiento(id)!!
        assertTrue("Usa molde igual", rendimiento.usaMolde)
        assertNull("Pero no recibe correcciones del catálogo", rendimiento.moldeOrigenId)
    }

    @Test
    fun `definir el molde dos veces no se permite`() = runBlocking {
        // La segunda vez ya hay original: eso es reescalar, y hay que decidir el modo.
        val id = recetaConHarina()
        repositorio.definirMolde(id, cuadrado(20.0, 6.0), null)

        assertTrue(repositorio.definirMolde(id, cuadrado(30.0, 6.0), null) is Resultado.NoSePudo)
        assertEquals(20.0, repositorio.obtenerRendimiento(id)!!.dimensiones!!.ladoCm!!, 0.001)
    }

    // --- Reescalar por molde ---

    @Test
    fun `modo capacidad multiplica los ingredientes por la razon de volumenes`() = runBlocking {
        val id = recetaConHarina()
        repositorio.definirMolde(id, cuadrado(10.0, 5.0), null)   // volumen 500

        val resultado = repositorio.reescalarPorMolde(
            id, cuadrado(10.0, 10.0), ModoReescalado.CAPACIDAD, moldeOrigenId = null
        )

        assertTrue(resultado is Resultado.Listo)
        assertEquals("El doble de volumen pide el doble", 1000.0, gramosDe(id), 0.001)
    }

    @Test
    fun `reescalar no multiplica lo que se cuenta por unidad`() = runBlocking {
        // Pasar la receta a otro molde cambia cuánta masa hay, no cuántas cajas se usan para
        // llevarla. Y multiplicar igual daría "1,5 cajas", que no es una cantidad que exista.
        val id = recetaConHarina()
        catalogo.sembrar(
            Ingrediente(nombre = "Cajas", valorPorGramo = 350.0, esObjeto = true)
        )
        val cajas = catalogo.obtenerTodosUnaVez().first { it.nombre == "Cajas" }
        val seccion = repositorio.obtenerSecciones(id).single()
        repositorio.agregarIngrediente(seccion.id, cajas.id, cantidadG = 0.0, unidades = 1.0)
        repositorio.definirMolde(id, cuadrado(10.0, 5.0), null)

        repositorio.reescalarPorMolde(
            id, cuadrado(10.0, 10.0), ModoReescalado.CAPACIDAD, moldeOrigenId = null
        )

        val lineas = repositorio.obtenerIngredientes(id)
        val laHarina = lineas.first { it.ingredienteId != cajas.id }
        val lasCajas = lineas.first { it.ingredienteId == cajas.id }
        assertEquals("La harina sí se dobla", 1000.0, laHarina.cantidadG, 0.001)
        assertEquals("Las cajas siguen siendo una", 1.0, lasCajas.unidades!!, 0.001)
        assertEquals("Y sin peso, como entraron", 0.0, lasCajas.cantidadG, 0.001)
    }

    @Test
    fun `modo altura multiplica por la razon de areas`() = runBlocking {
        val id = recetaConHarina()
        repositorio.definirMolde(id, cuadrado(10.0, 5.0), null)   // área 100

        repositorio.reescalarPorMolde(
            id, cuadrado(20.0, 5.0), ModoReescalado.ALTURA, moldeOrigenId = null
        )

        assertEquals("Área 400 contra 100: factor 4", 2000.0, gramosDe(id), 0.001)
    }

    @Test
    fun `un reescalado rechazado no deja la receta a medias`() = runBlocking {
        // `factorEscala` lanza excepción; acá tiene que volverse un motivo que se pueda
        // mostrar, y sobre todo no escribir nada.
        val id = recetaConHarina()
        repositorio.definirMolde(id, cuadrado(10.0, 5.0), null)

        val resultado = repositorio.reescalarPorMolde(
            id, cuadrado(10.0, 12.0), ModoReescalado.ALTURA, moldeOrigenId = null
        ) as Resultado.NoSePudo

        assertEquals(MENSAJE_ALTURA_RIESGOSA, resultado.motivo)
        assertEquals("Los ingredientes quedan intactos", 500.0, gramosDe(id), 0.001)
        assertEquals(
            "Y las medidas también",
            10.0,
            repositorio.obtenerRendimiento(id)!!.dimensiones!!.ladoCm!!,
            0.001
        )
    }

    @Test
    fun `un molde mas bajo se rechaza en modo altura y se explica`() = runBlocking {
        val id = recetaConHarina()
        repositorio.definirMolde(id, cuadrado(10.0, 8.0), null)

        val resultado = repositorio.reescalarPorMolde(
            id, cuadrado(10.0, 5.0), ModoReescalado.ALTURA, moldeOrigenId = null
        ) as Resultado.NoSePudo

        assertNotNull(resultado.motivo)
        assertEquals(500.0, gramosDe(id), 0.001)
    }

    @Test
    fun `reescalar cambia el vinculo al molde nuevo`() = runBlocking {
        val id = recetaConHarina()
        repositorio.definirMolde(id, cuadrado(10.0, 5.0), moldeOrigenId = 7L)

        repositorio.reescalarPorMolde(
            id, cuadrado(10.0, 10.0), ModoReescalado.CAPACIDAD, moldeOrigenId = 9L
        )

        assertEquals(9L, repositorio.obtenerRendimiento(id)!!.moldeOrigenId)
    }

    @Test
    fun `reescalar en modo prueba suelta el vinculo que tenia`() = runBlocking {
        val id = recetaConHarina()
        repositorio.definirMolde(id, cuadrado(10.0, 5.0), moldeOrigenId = 7L)

        repositorio.reescalarPorMolde(
            id, cuadrado(10.0, 10.0), ModoReescalado.CAPACIDAD, moldeOrigenId = null
        )

        assertNull(repositorio.obtenerRendimiento(id)!!.moldeOrigenId)
    }

    @Test
    fun `no se puede reescalar por molde una receta que no tiene`() = runBlocking {
        val id = recetaConHarina()

        val resultado = repositorio.reescalarPorMolde(
            id, cuadrado(10.0, 5.0), ModoReescalado.CAPACIDAD, null
        ) as Resultado.NoSePudo

        assertTrue(resultado.motivo.contains("defínelo"))
        assertEquals(500.0, gramosDe(id), 0.001)
    }

    // --- Reescalar por peso ---

    @Test
    fun `una receta sin molde se reescala por peso`() = runBlocking {
        val id = recetaConHarina("Salsa de chocolate")
        repositorio.guardarRendimiento(id, "1", "1.000")

        assertTrue(repositorio.reescalarPorPeso(id, "1.500") is Resultado.Listo)

        assertEquals("Factor 1,5", 750.0, gramosDe(id), 0.001)
        assertEquals(1500.0, repositorio.obtenerRendimiento(id)!!.pesoFinalG!!, 0.001)
    }

    @Test
    fun `una receta con molde no se reescala por peso`() = runBlocking {
        // Ahí el peso final es opcional, así que el cálculo caería sobre un dato que puede
        // no existir y daría un factor que no significa nada.
        val id = recetaConHarina()
        repositorio.definirMolde(id, cuadrado(20.0, 6.0), null)

        val resultado = repositorio.reescalarPorPeso(id, "1.500") as Resultado.NoSePudo

        assertTrue(resultado.motivo.contains("molde"))
        assertEquals(500.0, gramosDe(id), 0.001)
    }

    @Test
    fun `un peso nuevo en cero o vacio no reescala nada`() = runBlocking {
        val id = recetaConHarina("Salsa")
        repositorio.guardarRendimiento(id, "1", "1.000")

        assertTrue(repositorio.reescalarPorPeso(id, "0") is Resultado.NoSePudo)
        assertTrue(repositorio.reescalarPorPeso(id, "") is Resultado.NoSePudo)
        assertEquals(500.0, gramosDe(id), 0.001)
    }

    @Test
    fun `las cantidades reescaladas quedan redondeadas como todo lo que se guarda`() = runBlocking {
        // Si se guardara sin redondear, el subtotal que muestra la pantalla no coincidiría
        // con el que suma la base.
        val id = recetaConHarina("Salsa")
        repositorio.guardarRendimiento(id, "1", "300")

        repositorio.reescalarPorPeso(id, "100")   // factor 1/3

        assertEquals(166.66667, gramosDe(id), 0.000001)
    }

    @Test
    fun `reescalar mueve el costo, porque los gramos cambiaron`() = runBlocking {
        val id = recetaConHarina("Salsa")
        repositorio.guardarRendimiento(id, "1", "1.000")
        assertEquals(1000.0, repositorio.costoTotal(id), 0.001)

        repositorio.reescalarPorPeso(id, "2.000")

        assertEquals(2000.0, repositorio.costoTotal(id), 0.001)
    }

    // --- Quitar el molde ---

    @Test
    fun `quitar el molde exige tener peso final anotado`() = runBlocking {
        val id = recetaConHarina()
        repositorio.definirMolde(id, cuadrado(20.0, 6.0), null)

        assertTrue(repositorio.quitarMolde(id) is Resultado.NoSePudo)
        assertTrue(repositorio.obtenerRendimiento(id)!!.usaMolde)
    }

    @Test
    fun `quitar el molde conserva las medidas por si fue un error`() = runBlocking {
        val id = recetaConHarina()
        repositorio.definirMolde(id, cuadrado(20.0, 6.0), moldeOrigenId = 7L)
        repositorio.guardarRendimiento(id, "8", "1.200")

        assertTrue(repositorio.quitarMolde(id) is Resultado.Listo)

        val rendimiento = repositorio.obtenerRendimiento(id)!!
        assertFalse(rendimiento.usaMolde)
        assertNull("El vínculo sí se corta", rendimiento.moldeOrigenId)
        assertNotNull("Pero las medidas quedan donde estaban", rendimiento.dimensiones)
    }
}
