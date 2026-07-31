package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import com.sandyyera.reposteria.logica.duracion.UnidadDuracion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El paso "Duración" (8.4) sobre la base de mentira.
 *
 * Lo que se prueba acá y no en `logica/`: que un bloque que se vacía **se borre** en vez de
 * quedar como una fila que dice "cero", que "no apto" sí se guarde aunque no tenga números,
 * y que el paso no toque nada de las otras fases.
 */
class DuracionRepositorioTest {

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var dao: RecetaDaoFalso
    private lateinit var repositorio: RecetaRepositorio
    private var recetaId: Long = 0

    @Before
    fun prepararTodo() = runBlocking {
        catalogo = IngredienteDaoFalso()
        dao = RecetaDaoFalso(catalogo)
        repositorio = RecetaRepositorio(dao, HistorialRepositorio(HistorialDaoFalso()))
        recetaId = (repositorio.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId
    }

    private suspend fun ambiente() = repositorio.obtenerDuraciones(recetaId)[TipoDuracion.AMBIENTE]

    // --- Guardar ---

    @Test
    fun `una receta nueva no tiene ninguna duracion anotada`() = runBlocking {
        // Y eso es normal: el paso es opcional, así que no se siembran filas al crearla.
        assertTrue(repositorio.obtenerDuraciones(recetaId).isEmpty())
    }

    @Test
    fun `guardar un bloque lo deja anotado con su unidad`() = runBlocking {
        val r = repositorio.guardarDuracion(
            recetaId, TipoDuracion.AMBIENTE, apto = true, cantidadTexto = "3",
            unidad = UnidadDuracion.DIAS
        )

        assertTrue(r is Resultado.Listo)
        val bloque = ambiente()!!
        assertTrue(bloque.apto)
        assertEquals(3, bloque.cantidad)
        assertEquals(UnidadDuracion.DIAS, bloque.unidad)
    }

    @Test
    fun `los tres bloques se guardan por separado`() = runBlocking {
        repositorio.guardarDuracion(recetaId, TipoDuracion.AMBIENTE, true, "2", UnidadDuracion.DIAS)
        repositorio.guardarDuracion(
            recetaId, TipoDuracion.REFRIGERADA, true, "1", UnidadDuracion.SEMANAS
        )
        repositorio.guardarDuracion(recetaId, TipoDuracion.CONGELADA, apto = false, "", null)

        val todas = repositorio.obtenerDuraciones(recetaId)
        assertEquals(3, todas.size)
        assertEquals(UnidadDuracion.SEMANAS, todas.getValue(TipoDuracion.REFRIGERADA).unidad)
        assertFalse(todas.getValue(TipoDuracion.CONGELADA).apto)
    }

    @Test
    fun `volver a guardar el mismo bloque lo pisa en vez de acumular`() = runBlocking {
        repositorio.guardarDuracion(recetaId, TipoDuracion.AMBIENTE, true, "3", UnidadDuracion.DIAS)
        repositorio.guardarDuracion(recetaId, TipoDuracion.AMBIENTE, true, "5", UnidadDuracion.DIAS)

        assertEquals(1, repositorio.obtenerDuraciones(recetaId).size)
        assertEquals(5, ambiente()!!.cantidad)
    }

    // --- Vaciar y "no apto" ---

    @Test
    fun `vaciar un bloque lo borra en vez de dejar una fila que no dice nada`() = runBlocking {
        // Una fila con apto = true y cantidad = null es indistinguible de "todavía no lo sé",
        // así que dejarla haría parecer que el paso está llenado.
        repositorio.guardarDuracion(recetaId, TipoDuracion.AMBIENTE, true, "3", UnidadDuracion.DIAS)
        assertEquals(1, repositorio.obtenerDuraciones(recetaId).size)

        repositorio.guardarDuracion(recetaId, TipoDuracion.AMBIENTE, true, "", UnidadDuracion.DIAS)

        assertTrue(repositorio.obtenerDuraciones(recetaId).isEmpty())
    }

    @Test
    fun `no apto si se guarda, aunque no tenga numeros`() = runBlocking {
        // Que algo no se pueda congelar es justamente el dato que uno busca después.
        val r = repositorio.guardarDuracion(
            recetaId, TipoDuracion.CONGELADA, apto = false, cantidadTexto = "", unidad = null
        )

        assertTrue(r is Resultado.Listo)
        val bloque = repositorio.obtenerDuraciones(recetaId).getValue(TipoDuracion.CONGELADA)
        assertFalse(bloque.apto)
        assertNull(bloque.cantidad)
    }

    @Test
    fun `marcar no apto borra el numero que hubiera antes`() = runBlocking {
        // Dejarlo haría que volver a marcarlo apto reviviera un dato que ya nadie confirmó.
        repositorio.guardarDuracion(
            recetaId, TipoDuracion.CONGELADA, true, "6", UnidadDuracion.MESES
        )

        repositorio.guardarDuracion(recetaId, TipoDuracion.CONGELADA, apto = false, "6", null)

        val bloque = repositorio.obtenerDuraciones(recetaId).getValue(TipoDuracion.CONGELADA)
        assertNull(bloque.cantidad)
        assertNull(bloque.unidad)
    }

    @Test
    fun `una cantidad que no sirve no se guarda`() = runBlocking {
        assertTrue(
            repositorio.guardarDuracion(
                recetaId, TipoDuracion.AMBIENTE, true, "0", UnidadDuracion.DIAS
            ) is Resultado.NoSePudo
        )
        assertTrue(
            repositorio.guardarDuracion(
                recetaId, TipoDuracion.AMBIENTE, true, "1,5", UnidadDuracion.DIAS
            ) is Resultado.NoSePudo
        )
        assertTrue(repositorio.obtenerDuraciones(recetaId).isEmpty())
    }

    // --- Lo que NO tiene que pasar: el paso no toca las otras fases ---

    @Test
    fun `anotar duraciones no cambia el costo de la receta`() = runBlocking {
        catalogo.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 2.0))
        val harina = catalogo.obtenerTodosUnaVez().single()
        val seccion = repositorio.obtenerSecciones(recetaId).single()
        repositorio.agregarIngrediente(seccion.id, harina.id, 500.0)
        val antes = repositorio.costoTotal(recetaId)

        repositorio.guardarDuracion(recetaId, TipoDuracion.AMBIENTE, true, "3", UnidadDuracion.DIAS)
        repositorio.guardarDuracion(recetaId, TipoDuracion.CONGELADA, false, "", null)

        assertEquals(
            "La duración no alimenta ninguna cuenta",
            antes, repositorio.costoTotal(recetaId), 0.001
        )
    }

    @Test
    fun `borrar la receta se lleva sus duraciones`() = runBlocking {
        repositorio.guardarDuracion(recetaId, TipoDuracion.AMBIENTE, true, "3", UnidadDuracion.DIAS)

        repositorio.confirmarEliminacion(recetaId)

        assertTrue(repositorio.obtenerDuraciones(recetaId).isEmpty())
    }

    @Test
    fun `las duraciones de dos recetas no se mezclan`() = runBlocking {
        val otra = (repositorio.crear("Queque") as ResultadoCrearReceta.Creada).recetaId
        repositorio.guardarDuracion(recetaId, TipoDuracion.AMBIENTE, true, "3", UnidadDuracion.DIAS)
        repositorio.guardarDuracion(otra, TipoDuracion.AMBIENTE, true, "7", UnidadDuracion.DIAS)

        assertEquals(3, ambiente()!!.cantidad)
        val laOtra = repositorio.obtenerDuraciones(otra).getValue(TipoDuracion.AMBIENTE)
        assertEquals(7, laOtra.cantidad)
    }

    @Test
    fun `no se puede anotar la duracion de una receta que ya no existe`() = runBlocking {
        repositorio.confirmarEliminacion(recetaId)

        val r = repositorio.guardarDuracion(
            recetaId, TipoDuracion.AMBIENTE, true, "3", UnidadDuracion.DIAS
        )

        assertTrue(r is Resultado.NoSePudo)
    }
}
