package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.precios.MENSAJE_PROMOCION_CON_PERDIDAS
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.precioEfectivoPorTrozo
import com.sandyyera.reposteria.logica.validaciones.NOMBRE_SECCION_POR_DEFECTO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas de `RecetaRepositorio` sobre la base de mentira (`./gradlew :app:test`).
 *
 * Acá se prueba lo que ninguna prueba de `logica/` alcanza: que el costo se arme con el
 * precio actual del ingrediente, que la sección invisible se bautice al aparecer la
 * segunda sin mover ingredientes de lugar, y que elegir un precio de referencia que pierde
 * plata no escriba nada.
 */
class RecetaRepositorioTest {

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

    private suspend fun crearReceta(titulo: String = "Torta de manjar"): Long =
        (repositorio.crear(titulo) as ResultadoCrearReceta.Creada).recetaId

    /** Deja un ingrediente en el catálogo y devuelve su id. */
    private suspend fun ingrediente(nombre: String, valorPorGramo: Double): Long {
        catalogo.sembrar(Ingrediente(nombre = nombre, valorPorGramo = valorPorGramo))
        return catalogo.obtenerTodosUnaVez().first { it.nombre == nombre }.id
    }

    // --- Crear, renombrar, borrar ---

    @Test
    fun `crear una receta la deja lista para recibir ingredientes`() = runBlocking {
        val id = crearReceta()

        // Nace con su sección, su rendimiento y su simulación, para que no haya huecos
        // mientras se llena el asistente.
        val secciones = repositorio.obtenerSecciones(id)
        assertEquals(1, secciones.size)
        assertEquals(NOMBRE_SECCION_POR_DEFECTO, secciones.single().nombreSeccion)
        assertEquals(1, dao.obtenerTrozos(id))
        assertNotNull(dao.obtenerSimulacionVenta(id))
    }

    @Test
    fun `una receta sin titulo no se crea`() = runBlocking {
        assertTrue(repositorio.crear("   ") is ResultadoCrearReceta.NoValido)
        assertTrue(repositorio.crear("") is ResultadoCrearReceta.NoValido)

        // Y no quedó nada a medias: ni la receta ni su sección automática.
        assertTrue(repositorio.observarTodas().first().isEmpty())
    }

    @Test
    fun `crear y renombrar dejan su evento en el historial`() = runBlocking {
        val id = crearReceta("Torta")
        assertEquals(TipoEvento.CREACION, historial.eventos.single().tipo)

        repositorio.renombrar(id, "Torta de manjar")

        val edicion = historial.eventos.last()
        assertEquals(TipoEvento.EDICION, edicion.tipo)
        // La descripción tiene que nombrar los dos títulos para que el historial sirva.
        assertTrue(edicion.descripcion, edicion.descripcion.contains("Torta de manjar"))
        assertEquals("Torta de manjar", repositorio.obtener(id)?.titulo)
    }

    @Test
    fun `borrar la receta se lleva todo lo que colgaba de ella`() = runBlocking {
        val id = crearReceta()
        val harina = ingrediente("Harina", 1.0)
        val seccion = repositorio.obtenerSecciones(id).single()
        repositorio.agregarIngrediente(seccion.id, harina, 500.0)

        repositorio.confirmarEliminacion(id)

        assertNull(repositorio.obtener(id))
        assertTrue(repositorio.obtenerSecciones(id).isEmpty())
        assertTrue(repositorio.obtenerIngredientes(id).isEmpty())
        assertEquals(TipoEvento.ELIMINACION, historial.eventos.last().tipo)
    }

    // --- El costo (8.2 y decisión #3) ---

    @Test
    fun `el costo total es la suma de cantidad por valor de cada ingrediente`() = runBlocking {
        val id = crearReceta()
        val seccion = repositorio.obtenerSecciones(id).single().id
        repositorio.agregarIngrediente(seccion, ingrediente("Harina", 1.2), 500.0)   // 600
        repositorio.agregarIngrediente(seccion, ingrediente("Azúcar", 1.55), 200.0)  // 310

        assertEquals(910.0, repositorio.costoTotal(id), 0.001)
    }

    @Test
    fun `una receta recien creada cuesta cero y no revienta`() = runBlocking {
        // SUM sobre cero filas da NULL en SQLite, no 0: por eso la consulta lleva COALESCE.
        assertEquals(0.0, repositorio.costoTotal(crearReceta()), 0.001)
    }

    @Test
    fun `subir el precio del ingrediente sube el costo de la receta`() = runBlocking {
        // Decisión #3: nunca hay precios congelados dentro de la receta.
        val id = crearReceta()
        val harina = ingrediente("Harina", 1.0)
        repositorio.agregarIngrediente(repositorio.obtenerSecciones(id).single().id, harina, 500.0)
        assertEquals(500.0, repositorio.costoTotal(id), 0.001)

        catalogo.actualizar(catalogo.obtener(harina)!!.copy(valorPorGramo = 2.0))

        assertEquals(1000.0, repositorio.costoTotal(id), 0.001)
    }

    @Test
    fun `el costo suma los ingredientes de todas las secciones`() = runBlocking {
        val id = crearReceta("Torta de manjar")
        val primera = repositorio.obtenerSecciones(id).single().id
        repositorio.agregarIngrediente(primera, ingrediente("Harina", 1.0), 500.0)

        repositorio.agregarSeccion(id, "Crema", nombreDeLaPrimera = "Bizcocho")
        val crema = repositorio.obtenerSecciones(id).first { it.nombreSeccion == "Crema" }.id
        repositorio.agregarIngrediente(crema, ingrediente("Manjar", 4.0), 250.0)

        assertEquals(1500.0, repositorio.costoTotal(id), 0.001)
    }

    // --- Secciones (8.2) ---

    @Test
    fun `con una sola seccion propone bautizarla con el titulo de la receta`() = runBlocking {
        val id = crearReceta("Torta de manjar")
        assertEquals("Torta de manjar", repositorio.nombreQueFaltaBautizar(id))
    }

    @Test
    fun `con dos secciones ya no hay nada que bautizar`() = runBlocking {
        val id = crearReceta("Torta de manjar")
        repositorio.agregarSeccion(id, "Crema", nombreDeLaPrimera = "Bizcocho")

        assertNull(repositorio.nombreQueFaltaBautizar(id))
    }

    @Test
    fun `no se puede agregar la segunda seccion sin bautizar la primera`() = runBlocking {
        val id = crearReceta("Torta de manjar")

        val resultado = repositorio.agregarSeccion(id, "Crema")

        assertTrue(resultado is Resultado.NoSePudo)
        // Y no se creó nada a medias: sigue habiendo una sola sección.
        assertEquals(1, repositorio.obtenerSecciones(id).size)
    }

    @Test
    fun `al bautizar la primera, sus ingredientes no se mueven de lugar`() = runBlocking {
        // Esto es lo que pide 8.2 explícitamente: la sección que era invisible pasa a
        // tener nombre, pero lo que ya estaba cargado se queda donde estaba.
        val id = crearReceta("Torta de manjar")
        val primera = repositorio.obtenerSecciones(id).single().id
        repositorio.agregarIngrediente(primera, ingrediente("Harina", 1.0), 500.0)

        repositorio.agregarSeccion(id, "Crema", nombreDeLaPrimera = "Bizcocho")

        val secciones = repositorio.obtenerSecciones(id)
        assertEquals(listOf("Bizcocho", "Crema"), secciones.map { it.nombreSeccion })
        // Mismo id de sección que antes, y el ingrediente sigue ahí.
        assertEquals(primera, secciones.first().id)
        assertEquals(listOf(primera), repositorio.obtenerIngredientes(id).map { it.seccionId })
    }

    @Test
    fun `un nombre de seccion vacio no se acepta`() = runBlocking {
        val id = crearReceta()
        assertTrue(repositorio.agregarSeccion(id, "  ", "Bizcocho") is Resultado.NoSePudo)
        assertTrue(repositorio.agregarSeccion(id, "Crema", "  ") is Resultado.NoSePudo)
        assertEquals(1, repositorio.obtenerSecciones(id).size)
    }

    @Test
    fun `no se puede borrar la unica seccion`() = runBlocking {
        val id = crearReceta()
        val unica = repositorio.obtenerSecciones(id).single().id

        assertTrue(repositorio.eliminarSeccion(id, unica) is Resultado.NoSePudo)
        assertEquals(1, repositorio.obtenerSecciones(id).size)
    }

    @Test
    fun `borrar una seccion se lleva sus ingredientes`() = runBlocking {
        val id = crearReceta("Torta de manjar")
        repositorio.agregarSeccion(id, "Crema", nombreDeLaPrimera = "Bizcocho")
        val crema = repositorio.obtenerSecciones(id).first { it.nombreSeccion == "Crema" }
        repositorio.agregarIngrediente(crema.id, ingrediente("Manjar", 4.0), 250.0)
        assertEquals(1000.0, repositorio.costoTotal(id), 0.001)

        repositorio.eliminarSeccion(id, crema.id)

        assertEquals(0.0, repositorio.costoTotal(id), 0.001)
        assertEquals(1, repositorio.obtenerSecciones(id).size)
    }

    // --- El snapshot (6.4) ---

    @Test
    fun `el snapshot trae costo, trozos y precios de una vez`() = runBlocking {
        val id = crearReceta("Torta de manjar")
        repositorio.agregarIngrediente(
            repositorio.obtenerSecciones(id).single().id, ingrediente("Harina", 1.0), 1400.0
        )
        dao.actualizarRendimiento(dao.obtenerRendimiento(id)!!.copy(trozos = 6))
        dao.insertarPrecio(RecetaPrecio(recetaId = id, modo = ModoPrecio.TROZO, precioTotal = 500.0))

        val datos = repositorio.obtenerDatosCalculo(listOf(id)).getValue(id)

        assertEquals("Torta de manjar", datos.titulo)
        assertEquals(1400.0, datos.costoTotal, 0.001)
        assertEquals(6, datos.trozos)
        assertEquals(500.0, precioEfectivoPorTrozo(datos), 0.001)
    }

    @Test
    fun `una receta sin ingredientes entra al snapshot con costo cero`() = runBlocking {
        // La consulta en lote agrupa, así que una receta sin filas simplemente no aparece
        // en el resultado. Si el repositorio no la tomara como 0, quedaría fuera del mapa
        // y la pantalla se caería al buscarla.
        val id = crearReceta()

        val datos = repositorio.obtenerDatosCalculo(listOf(id))

        assertEquals(1, datos.size)
        assertEquals(0.0, datos.getValue(id).costoTotal, 0.001)
    }

    // --- El precio de referencia (8.6) ---

    private suspend fun recetaConDosPrecios(): Triple<Long, Long, Long> {
        val id = crearReceta("Torta de manjar")
        repositorio.agregarIngrediente(
            repositorio.obtenerSecciones(id).single().id, ingrediente("Harina", 1.0), 3000.0
        )
        dao.actualizarRendimiento(dao.obtenerRendimiento(id)!!.copy(trozos = 6))
        // Costo 3.000 entre 6 trozos: producir cada trozo cuesta 500.
        val base = dao.insertarPrecio(
            RecetaPrecio(recetaId = id, modo = ModoPrecio.TROZO, precioTotal = 600.0)
        )
        val quePierde = dao.insertarPrecio(
            RecetaPrecio(
                recetaId = id, modo = ModoPrecio.TROZO, cantidad = 2, precioTotal = 800.0,
                etiqueta = "2x800"
            )
        )   // 400 por trozo: pierde 100
        return Triple(id, base, quePierde)
    }

    @Test
    fun `elegir un precio de referencia lo deja como unico marcado`() = runBlocking {
        val (id, base, _) = recetaConDosPrecios()

        val motivo = repositorio.elegirPrecioDeReferencia(id, base)

        assertNull(motivo)
        val marcados = dao.obtenerPrecios(id).filter { it.esReferencia }
        assertEquals(1, marcados.size)
        assertEquals(base, marcados.single().id)
    }

    @Test
    fun `una promocion que pierde plata se rechaza y no cambia nada`() = runBlocking {
        val (id, base, quePierde) = recetaConDosPrecios()
        repositorio.elegirPrecioDeReferencia(id, base)

        val motivo = repositorio.elegirPrecioDeReferencia(id, quePierde)

        assertEquals(MENSAJE_PROMOCION_CON_PERDIDAS, motivo)
        // "Cancelar y volver al valor que tenía" es simplemente no haber escrito: la
        // referencia sigue siendo la anterior, sin ningún paso de deshacer.
        val marcados = dao.obtenerPrecios(id).filter { it.esReferencia }
        assertEquals(base, marcados.single().id)
    }

    @Test
    fun `cambiar la referencia apaga la anterior`() = runBlocking {
        val (id, base, _) = recetaConDosPrecios()
        val masCara = dao.insertarPrecio(
            RecetaPrecio(recetaId = id, modo = ModoPrecio.TROZO, precioTotal = 900.0)
        )
        repositorio.elegirPrecioDeReferencia(id, base)

        repositorio.elegirPrecioDeReferencia(id, masCara)

        val marcados = dao.obtenerPrecios(id).filter { it.esReferencia }
        assertEquals(1, marcados.size)
        assertEquals(masCara, marcados.single().id)
    }

    @Test
    fun `cambiar la referencia cambia las cifras automaticas`() = runBlocking {
        val (id, base, _) = recetaConDosPrecios()
        val masCara = dao.insertarPrecio(
            RecetaPrecio(recetaId = id, modo = ModoPrecio.TROZO, precioTotal = 900.0)
        )

        repositorio.elegirPrecioDeReferencia(id, base)
        val conBase = repositorio.obtenerDatosCalculo(listOf(id)).getValue(id)
        assertEquals(600.0, precioEfectivoPorTrozo(conBase), 0.001)

        repositorio.elegirPrecioDeReferencia(id, masCara)
        val conCara = repositorio.obtenerDatosCalculo(listOf(id)).getValue(id)
        assertEquals(900.0, precioEfectivoPorTrozo(conCara), 0.001)
    }

    @Test
    fun `elegir referencia deja constancia en el historial`() = runBlocking {
        val (id, base, _) = recetaConDosPrecios()
        historial.eventos.clear()

        repositorio.elegirPrecioDeReferencia(id, base)

        assertEquals(TipoEvento.EDICION, historial.eventos.single().tipo)
    }

    @Test
    fun `un rechazo no deja constancia, porque no pasó nada`() = runBlocking {
        val (id, _, quePierde) = recetaConDosPrecios()
        historial.eventos.clear()

        repositorio.elegirPrecioDeReferencia(id, quePierde)

        assertTrue(historial.eventos.isEmpty())
    }

    @Test
    fun `elegir un precio que ya no existe avisa en vez de reventar`() = runBlocking {
        val (id, _, _) = recetaConDosPrecios()
        assertNotNull(repositorio.elegirPrecioDeReferencia(id, 9999))
    }
}
