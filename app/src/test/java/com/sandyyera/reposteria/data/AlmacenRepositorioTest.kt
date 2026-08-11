package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.repositorio.AlmacenRepositorio
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.data.repositorio.QueHacerConElNombre
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.data.repositorio.ResultadoAgregarAlAlmacen
import com.sandyyera.reposteria.data.repositorio.ResultadoRenombrarEnAlmacen
import com.sandyyera.reposteria.logica.almacen.RecetaHecha
import com.sandyyera.reposteria.logica.almacen.SentidoDelMovimiento
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas del almacén con los DAO falsos, sin base de datos ni celular (sección 14).
 *
 * **Llegaron tarde y eso costó.** El almacén se rehízo tres veces seguidas —sumar y restar,
 * descontar por recetas hechas, renombrar— sin una sola prueba, y por ahí se coló el ingrediente
 * huérfano que Sandy encontró con "manga"/"mangas". Este archivo existe para que la cuarta vez
 * no dependa de que ella lo note en el celular.
 */
class AlmacenRepositorioTest {

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var recetasDao: RecetaDaoFalso
    private lateinit var almacenDao: AlmacenDaoFalso
    private lateinit var historial: HistorialDaoFalso
    private lateinit var ingredientes: IngredienteRepositorio
    private lateinit var recetas: RecetaRepositorio
    private lateinit var repositorio: AlmacenRepositorio

    @Before
    fun prepararTodo() {
        catalogo = IngredienteDaoFalso()
        recetasDao = RecetaDaoFalso(catalogo)
        almacenDao = AlmacenDaoFalso(catalogo)
        historial = HistorialDaoFalso()
        ingredientes = IngredienteRepositorio(
            dao = catalogo,
            recetaDao = recetasDao,
            historial = HistorialRepositorio(historial)
        )
        recetas = RecetaRepositorio(recetasDao, HistorialRepositorio(historial))
        repositorio = AlmacenRepositorio(
            dao = almacenDao,
            ingredientes = ingredientes,
            recetas = recetas,
            historial = HistorialRepositorio(historial)
        )
    }

    /** Anota algo en el almacén y devuelve el id de su fila. */
    private suspend fun anotar(
        nombre: String,
        cantidad: Double = 1000.0,
        valor: Double = 1.0,
        esObjeto: Boolean = false,
        vaEnRecetas: Boolean = true
    ): Long {
        val r = repositorio.agregar(
            nombre = nombre,
            esObjeto = esObjeto,
            vaEnRecetas = vaEnRecetas,
            cantidad = cantidad,
            valor = valor,
            detalles = null
        )
        assertTrue("No se pudo anotar '$nombre': $r", r is ResultadoAgregarAlAlmacen.Listo)
        return repositorio.observarTodo().first().single { it.nombre == nombre }.id
    }

    private suspend fun idDelIngrediente(nombre: String): Long =
        ingredientes.buscarParecido(nombre)!!.id

    private suspend fun cuantoQueda(nombre: String): Double =
        repositorio.observarTodo().first().single { it.nombre == nombre }.cantidad

    /**
     * Deja una receta con una sección y los ingredientes que se le pasen, y devuelve su id.
     *
     * Va por el repositorio y no armando las entidades a mano: así la prueba usa el mismo camino
     * que la app, y una regla nueva al agregar un ingrediente la rompe acá en vez de dejarla
     * pasar.
     */
    private suspend fun recetaCon(titulo: String, vararg lleva: Pair<Long, Double>): Long {
        val id = (recetas.crear(titulo) as ResultadoCrearReceta.Creada).recetaId
        val seccion = recetas.obtenerSecciones(id).single().id
        lleva.forEach { (ingredienteId, cuanto) ->
            recetas.agregarIngrediente(seccion, ingredienteId, cuanto)
        }
        return id
    }

    // --- Sumar y restar (14.8) ---

    @Test
    fun `entra y sale mueven el mismo numero en dos sentidos`() = runBlocking {
        val id = anotar("Harina", cantidad = 1000.0)

        repositorio.mover(id, 500.0, SentidoDelMovimiento.SALE)
        assertEquals(500.0, cuantoQueda("Harina"), 0.001)

        repositorio.mover(id, 800.0, SentidoDelMovimiento.ENTRA)
        assertEquals(1300.0, cuantoQueda("Harina"), 0.001)
    }

    @Test
    fun `sacar de mas deja el negativo guardado, no un cero`() = runBlocking {
        // Es el dato que Sandy pidió ver: o entró algo sin anotar, o la receta pide de más.
        // Recortarlo en cero borraba las dos lecturas.
        val id = anotar("Harina", cantidad = 300.0)

        repositorio.mover(id, 500.0, SentidoDelMovimiento.SALE)

        assertEquals(-200.0, cuantoQueda("Harina"), 0.001)
    }

    @Test
    fun `sumar sobre un negativo lo saca del pozo`() = runBlocking {
        val id = anotar("Harina", cantidad = 300.0)
        repositorio.mover(id, 500.0, SentidoDelMovimiento.SALE)

        repositorio.mover(id, 1000.0, SentidoDelMovimiento.ENTRA)

        assertEquals("Con el recorte en cero habría dado 1.000", 800.0, cuantoQueda("Harina"), 0.001)
    }

    @Test
    fun `un movimiento negativo se rechaza, porque el sentido va aparte`() = runBlocking {
        val id = anotar("Harina")

        assertTrue(repositorio.mover(id, -5.0, SentidoDelMovimiento.SALE) is Resultado.NoSePudo)
        assertEquals(1000.0, cuantoQueda("Harina"), 0.001)
    }

    // --- Renombrar (14.10) ---

    @Test
    fun `el huerfano de un nombre mal escrito se barre al unir`() = runBlocking {
        // **El caso real de Sandy.** Tenía "manga" en ingredientes; en el almacén escribió
        // "mangas", lo que creó un segundo ingrediente (14.5). Al corregir el nombre la fila se
        // unió con el bueno, pero "mangas" se quedó en el catálogo para siempre — y un
        // ingrediente que nadie nombra no se puede encontrar para borrarlo a mano.
        ingredientes.crear("Manga", 5.0)
        val fila = anotar("Mangas")
        assertNotNull("El typo creó su propio ingrediente", ingredientes.buscarParecido("Mangas"))

        val r = repositorio.renombrar(fila, "Manga")

        assertTrue(r is ResultadoRenombrarEnAlmacen.Listo)
        assertEquals("La fila quedó con el bueno", "Manga", repositorio.observarTodo().first().single().nombre)
        assertEquals("Y en el catálogo queda uno solo", 1, catalogo.observarTodos().first().size)
    }

    @Test
    fun `pero no se barre lo que alguna receta usa`() = runBlocking {
        // Ahí no es basura: es una entrada del catálogo que alguien eligió, y borrarla sacaría
        // sus líneas de esas recetas sin avisar.
        ingredientes.crear("Manga", 5.0)
        val fila = anotar("Mangas")
        recetaCon("Torta", idDelIngrediente("Mangas") to 10.0)

        repositorio.renombrar(fila, "Manga")

        assertEquals("Los dos siguen", 2, catalogo.observarTodos().first().size)
        assertNotNull(ingredientes.buscarParecido("Mangas"))
    }

    @Test
    fun `unir con algo que ya tiene su propia fila se rechaza`() = runBlocking {
        // La excepción que nombró Sandy: dos filas para un ingrediente dejarían "cuánta harina
        // queda" con dos respuestas.
        anotar("Manga")
        val fila = anotar("Mangas")

        val r = repositorio.renombrar(fila, "Manga")

        assertTrue(r is ResultadoRenombrarEnAlmacen.NoSePudo)
        assertEquals("Nada se movió", 2, repositorio.observarTodo().first().size)
    }

    @Test
    fun `un nombre que no existe pregunta en vez de elegir solo`() = runBlocking {
        val fila = anotar("Harina")

        val r = repositorio.renombrar(fila, "Harina integral")

        assertTrue(r is ResultadoRenombrarEnAlmacen.HayQueElegir)
        assertEquals("Harina", (r as ResultadoRenombrarEnAlmacen.HayQueElegir).nombreViejo)
        assertEquals("Y no tocó nada todavía", "Harina", repositorio.observarTodo().first().single().nombre)
    }

    @Test
    fun `renombrar cambia el ingrediente, separar crea uno nuevo`() = runBlocking {
        val fila = anotar("Harina")
        val idOriginal = idDelIngrediente("Harina")

        repositorio.renombrar(fila, "Harina integral", QueHacerConElNombre.RENOMBRAR)

        assertEquals("Es el mismo ingrediente, con otro nombre", 1, catalogo.observarTodos().first().size)
        assertEquals("Harina integral", ingredientes.obtener(idOriginal)?.nombre)

        // Y separar, al revés: deja el de antes intacto.
        val otra = anotar("Azúcar")
        val idAzucar = idDelIngrediente("Azúcar")
        repositorio.renombrar(otra, "Azúcar flor", QueHacerConElNombre.SEPARAR)

        assertEquals("Azúcar", ingredientes.obtener(idAzucar)?.nombre)
        assertNotNull("Y apareció el nuevo", ingredientes.buscarParecido("Azúcar flor"))
    }

    // --- Qué es: la unidad y si va en recetas (14.11) ---

    @Test
    fun `cambiar de gramos a unidad no toca el numero del precio`() = runBlocking {
        // Es lo que hace peligroso al cambio y lo que el aviso tiene que decir: el precio no se
        // mueve pero pasa a significar otra cosa.
        anotar("Cajas", valor = 250.0, esObjeto = false)
        val id = idDelIngrediente("Cajas")

        repositorio.cambiarQueEs(id, esObjeto = true, vaEnRecetas = true)

        val i = ingredientes.obtener(id)!!
        assertTrue(i.esObjeto)
        assertEquals(250.0, i.valorPorGramo, 0.001)
    }

    @Test
    fun `apagar va en recetas saca sus lineas y deja el ingrediente`() = runBlocking {
        anotar("Vela")
        val id = idDelIngrediente("Vela")
        recetaCon("Torta", id to 1.0)

        repositorio.cambiarQueEs(id, esObjeto = false, vaEnRecetas = false)

        assertTrue("Salió de la receta", repositorio.recetasQueUsan(id).isEmpty())
        assertNotNull("Pero sigue en el catálogo", ingredientes.obtener(id))
        assertFalse(ingredientes.obtener(id)!!.vaEnRecetas)
        assertEquals("Y en el almacén", 1, repositorio.observarTodo().first().size)
    }

    // --- Descontar por recetas hechas (14.9) ---

    @Test
    fun `descontar mueve solo lo anotado y dice lo que no esta`() = runBlocking {
        anotar("Harina", cantidad = 2000.0)
        // El azúcar la usa la receta y nadie la anota en el almacén: tiene que salir dicha.
        ingredientes.crear("Azúcar", 2.0)
        val recetaId = recetaCon(
            "Torta",
            idDelIngrediente("Harina") to 500.0,
            idDelIngrediente("Azúcar") to 200.0
        )

        val previa = repositorio.vistaPreviaDeDescontar(
            listOf(RecetaHecha(recetaId, "Torta", 2.0))
        )

        assertEquals(1, previa.cuantasFilas)
        assertEquals("Dos tandas", 1000.0, previa.filas.single().seUsa, 0.001)
        assertEquals(1000.0, previa.filas.single().quedara, 0.001)
        assertEquals("Azúcar", previa.sinAnotar.single().nombre)
        assertEquals("Y todavía no escribió nada", 2000.0, cuantoQueda("Harina"), 0.001)

        repositorio.descontar(previa, "'Torta'")

        assertEquals(1000.0, cuantoQueda("Harina"), 0.001)
    }

    @Test
    fun `descontar sin nada anotado no escribe ni miente`() = runBlocking {
        val previa = repositorio.vistaPreviaDeDescontar(emptyList())

        assertFalse(previa.hayAlgoQueDescontar)
        assertTrue(repositorio.descontar(previa, "nada") is Resultado.NoSePudo)
    }

    // --- La cascada del borrado ---

    @Test
    fun `borrar el ingrediente se lleva su fila de almacen`() = runBlocking {
        anotar("Harina")
        val id = idDelIngrediente("Harina")

        ingredientes.confirmarEliminacion(id)

        assertTrue(repositorio.observarTodo().first().isEmpty())
    }

    @Test
    fun `sacar del almacen no borra el ingrediente`() = runBlocking {
        // Al revés que la cascada, y es a propósito: dejar de llevarle la cuenta a la harina no
        // es dejar de usarla en las recetas.
        val fila = anotar("Harina")
        val id = idDelIngrediente("Harina")

        repositorio.eliminar(fila, "Harina")

        assertTrue(repositorio.observarTodo().first().isEmpty())
        assertNotNull(ingredientes.obtener(id))
    }

    @Test
    fun `un ingrediente no puede tener dos filas de almacen`() = runBlocking {
        anotar("Harina")

        val r = repositorio.agregar(
            nombre = "Harina",
            esObjeto = false,
            vaEnRecetas = true,
            cantidad = 500.0,
            valor = 1.0,
            detalles = null
        )

        assertTrue(r is ResultadoAgregarAlAlmacen.NoSePudo)
        assertEquals(1, repositorio.observarTodo().first().size)
    }

    @Test
    fun `el nombre y la unidad se leen del catalogo y no se copian`() = runBlocking {
        // Es la regla de 14.5: guardarlos también en la fila daría dos versiones del mismo dato
        // y una quedaría vieja al primer renombre.
        anotar("Harina", esObjeto = false)
        val id = idDelIngrediente("Harina")

        ingredientes.actualizar(ingredientes.obtener(id)!!.copy(nombre = "Harina 000", esObjeto = true))

        val fila = repositorio.observarTodo().first().single()
        assertEquals("Harina 000", fila.nombre)
        assertEquals(true, fila.esObjeto)
    }

    @Test
    fun `crear algo en el almacen lo deja tambien en el catalogo`() = runBlocking {
        anotar("Sprinkles", valor = 12.0, esObjeto = true, vaEnRecetas = false)

        val i = ingredientes.buscarParecido("Sprinkles")
        assertNotNull(i)
        assertTrue(i!!.esObjeto)
        assertFalse(i.vaEnRecetas)
        assertNull("Y no quedó nombre suelto en la fila", almacenDao.obtenerPorIngrediente(i.id)!!.nombre.ifBlank { null })
    }
}
