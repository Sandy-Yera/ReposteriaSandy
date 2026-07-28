package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoGuardarIngrediente
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas de `IngredienteRepositorio` con los DAO falsos, sin base de datos ni celular.
 *
 * Se corren con `./gradlew :app:test`. Cubren lo que ninguna prueba de `logica/` puede
 * cubrir, porque necesita mirar datos guardados: si un nombre está repetido, y en qué
 * orden pasan las cosas al borrar.
 *
 * Se usa `runBlocking` y no `runTest` a propósito: acá no hay tiempo que adelantar ni
 * corrutinas en segundo plano, solo llamadas `suspend` que terminan. Es una dependencia
 * menos.
 */
class IngredienteRepositorioTest {

    private lateinit var ingredientes: IngredienteDaoFalso
    private lateinit var recetas: RecetaDaoFalso
    private lateinit var historial: HistorialDaoFalso
    private lateinit var repositorio: IngredienteRepositorio

    @Before
    fun prepararTodo() {
        ingredientes = IngredienteDaoFalso()
        recetas = RecetaDaoFalso()
        historial = HistorialDaoFalso()
        repositorio = IngredienteRepositorio(
            dao = ingredientes,
            recetaDao = recetas,
            historial = HistorialRepositorio(historial)
        )
    }

    // --- Crear ---

    @Test
    fun `crear un ingrediente devuelve su id y lo deja guardado`() = runBlocking {
        val resultado = repositorio.crear("Harina", 1.55)

        assertTrue(resultado is ResultadoGuardarIngrediente.Guardado)
        val id = (resultado as ResultadoGuardarIngrediente.Guardado).id
        assertEquals("Harina", repositorio.obtener(id)?.nombre)
    }

    @Test
    fun `crear le saca los espacios de los bordes al nombre`() = runBlocking {
        val id = (repositorio.crear("  Harina  ", 1.55) as ResultadoGuardarIngrediente.Guardado).id
        assertEquals("Harina", repositorio.obtener(id)?.nombre)
    }

    @Test
    fun `un nombre repetido devuelve YaExiste en vez de cerrar la app`() = runBlocking {
        repositorio.crear("Harina", 1.55)

        val resultado = repositorio.crear("Harina", 2.0)

        // Si esto devolviera Guardado, el DAO falso ya habría lanzado la excepción del
        // índice único -- igual que la base de verdad, que es lo que cierra la app.
        assertTrue(resultado is ResultadoGuardarIngrediente.YaExiste)
        assertEquals("Harina", (resultado as ResultadoGuardarIngrediente.YaExiste).existente.nombre)
    }

    @Test
    fun `un repetido escrito distinto tambien se detecta`() = runBlocking {
        repositorio.crear("Azúcar", 1.55)

        // La base sabe ignorar mayúsculas, pero para ella "azucar" y "azúcar" son nombres
        // distintos. Esta comprobación es la única que ve la diferencia.
        assertTrue(repositorio.crear("azucar", 2.0) is ResultadoGuardarIngrediente.YaExiste)
        assertTrue(repositorio.crear("AZÚCAR", 2.0) is ResultadoGuardarIngrediente.YaExiste)
        assertTrue(repositorio.crear("  azúcar ", 2.0) is ResultadoGuardarIngrediente.YaExiste)
    }

    @Test
    fun `un nombre parecido pero distinto si se puede crear`() = runBlocking {
        repositorio.crear("Azúcar", 1.55)

        // "azúcar flor" es otro ingrediente, no el mismo escrito distinto.
        assertTrue(repositorio.crear("Azúcar flor", 2.0) is ResultadoGuardarIngrediente.Guardado)
    }

    @Test
    fun `un dato que no sirve devuelve NoValido y no guarda nada`() = runBlocking {
        assertTrue(repositorio.crear("", 1.55) is ResultadoGuardarIngrediente.NoValido)
        assertTrue(repositorio.crear("Harina", -1.0) is ResultadoGuardarIngrediente.NoValido)
        assertTrue(ingredientes.obtenerTodosUnaVez().isEmpty())
    }

    @Test
    fun `crear deja anotado el evento azul con el nombre`() = runBlocking {
        repositorio.crear("Harina", 1.55)

        val evento = historial.eventos.single()
        assertEquals(TipoEvento.CREACION, evento.tipo)
        assertEquals(EntidadEvento.INGREDIENTE, evento.entidad)
        // La descripción tiene que nombrar lo afectado, nunca un texto genérico.
        assertTrue(evento.descripcion, evento.descripcion.contains("Harina"))
    }

    // --- Editar ---

    @Test
    fun `editar solo el precio no choca con el propio nombre`() = runBlocking {
        val id = (repositorio.crear("Harina", 1.55) as ResultadoGuardarIngrediente.Guardado).id
        val guardado = repositorio.obtener(id)!!

        val resultado = repositorio.actualizar(guardado.copy(valorPorGramo = 2.0))

        assertTrue(resultado is ResultadoGuardarIngrediente.Guardado)
        assertEquals(2.0, repositorio.obtener(id)!!.valorPorGramo, 0.0)
    }

    @Test
    fun `renombrar hacia un nombre ya usado devuelve YaExiste`() = runBlocking {
        repositorio.crear("Harina", 1.55)
        val id = (repositorio.crear("Maicena", 3.0) as ResultadoGuardarIngrediente.Guardado).id

        val resultado = repositorio.actualizar(repositorio.obtener(id)!!.copy(nombre = "harina"))

        assertTrue(resultado is ResultadoGuardarIngrediente.YaExiste)
        // Y no se guardó el cambio.
        assertEquals("Maicena", repositorio.obtener(id)?.nombre)
    }

    @Test
    fun `editar deja anotado un evento verde`() = runBlocking {
        val id = (repositorio.crear("Harina", 1.55) as ResultadoGuardarIngrediente.Guardado).id
        historial.eventos.clear()

        repositorio.actualizar(repositorio.obtener(id)!!.copy(valorPorGramo = 2.0))

        assertEquals(TipoEvento.EDICION, historial.eventos.single().tipo)
    }

    // --- Buscar parecidos ---

    @Test
    fun `buscarParecido no se encuentra a si mismo al editar`() = runBlocking {
        val id = (repositorio.crear("Harina", 1.55) as ResultadoGuardarIngrediente.Guardado).id

        assertNotNull(repositorio.buscarParecido("Harina"))
        assertNull(repositorio.buscarParecido("Harina", exceptoId = id))
    }

    // --- Borrar ---

    @Test
    fun `borrar un ingrediente que no usa nadie lo saca de la lista`() = runBlocking {
        val id = (repositorio.crear("Harina", 1.55) as ResultadoGuardarIngrediente.Guardado).id

        assertTrue(repositorio.recetasAfectadasPorBorrar(id).isEmpty())
        repositorio.confirmarEliminacion(id)

        assertNull(repositorio.obtener(id))
    }

    @Test
    fun `las recetas afectadas se pueden consultar antes de borrar`() = runBlocking {
        val id = (repositorio.crear("Harina", 1.55) as ResultadoGuardarIngrediente.Guardado).id
        recetas.declararUso(id, Receta(id = 1, titulo = "Torta de manjar"))
        recetas.declararUso(id, Receta(id = 2, titulo = "Bizcocho"))

        val afectadas = repositorio.recetasAfectadasPorBorrar(id).map { it.titulo }

        assertEquals(listOf("Bizcocho", "Torta de manjar"), afectadas)
    }

    @Test
    fun `el historial del borrado guarda el nombre y las recetas afectadas`() = runBlocking {
        // Esta es la prueba del orden: el nombre y las recetas hay que leerlos ANTES de
        // borrar, porque después ya no se pueden consultar. Si alguien reordena esas
        // líneas, el evento queda sin nombre y esto lo detecta.
        val id = (repositorio.crear("Harina", 1.55) as ResultadoGuardarIngrediente.Guardado).id
        recetas.declararUso(id, Receta(id = 1, titulo = "Torta de manjar"))
        historial.eventos.clear()

        repositorio.confirmarEliminacion(id)

        val evento = historial.eventos.single()
        assertEquals(TipoEvento.ELIMINACION, evento.tipo)
        assertTrue(evento.descripcion, evento.descripcion.contains("Harina"))
        assertTrue(evento.detalleAdicional.orEmpty(), evento.detalleAdicional!!.contains("Torta de manjar"))
    }

    @Test
    fun `borrar sin recetas afectadas no inventa un detalle adicional`() = runBlocking {
        val id = (repositorio.crear("Harina", 1.55) as ResultadoGuardarIngrediente.Guardado).id
        historial.eventos.clear()

        repositorio.confirmarEliminacion(id)

        assertNull(historial.eventos.single().detalleAdicional)
    }

    @Test
    fun `borrar tambien lo saca de las recetas que lo usaban`() = runBlocking {
        val id = (repositorio.crear("Harina", 1.55) as ResultadoGuardarIngrediente.Guardado).id
        recetas.declararUso(id, Receta(id = 1, titulo = "Torta de manjar"))

        repositorio.confirmarEliminacion(id)

        // Entre receta_ingredientes e ingredientes no hay clave foránea: si el repositorio
        // no lo quita a mano, quedan filas apuntando a un ingrediente que ya no existe.
        assertTrue(repositorio.recetasAfectadasPorBorrar(id).isEmpty())
    }

    @Test
    fun `borrar un id que no existe no hace nada ni anota nada`() = runBlocking {
        repositorio.confirmarEliminacion(999)

        assertTrue(historial.eventos.isEmpty())
    }

    // --- Historial ---

    @Test
    fun `cada evento aprovecha para limpiar lo mas viejo que seis meses`() = runBlocking {
        repositorio.crear("Harina", 1.55)

        // Sin esto el historial crecería para siempre dentro del archivo que se respalda.
        assertEquals(1, historial.limpiezasPedidas.size)
        val esperado = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000
        assertTrue(
            "la fecha de corte quedó lejos de los seis meses",
            kotlin.math.abs(historial.limpiezasPedidas.single() - esperado) < 60_000
        )
    }

    // --- Observar ---

    @Test
    fun `la lista se observa en orden alfabetico`() = runBlocking {
        ingredientes.sembrar(
            Ingrediente(nombre = "Manjar", valorPorGramo = 4.8),
            Ingrediente(nombre = "azúcar", valorPorGramo = 1.5),
            Ingrediente(nombre = "Harina", valorPorGramo = 1.2)
        )

        // `first()` y no `collect`: el flujo de la base no termina nunca, así que
        // recolectarlo entero dejaría la prueba colgada para siempre.
        val nombres = repositorio.observarTodos().first().map { it.nombre }

        assertEquals(listOf("azúcar", "Harina", "Manjar"), nombres)
    }
}
