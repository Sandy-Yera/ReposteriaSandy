package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.partes.TITULO_GENERAL
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
 * El paso "Pasos" visto desde la pantalla (8.8).
 *
 * Lo que hay que cuidar acá es distinto que en los otros pasos: **un paso vacío no es un error,
 * es un paso que se borra**. Esa asimetría es la que se escribe mal si se copia el patrón de los
 * demás campos, y la que estas pruebas fijan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasosViewModelTest {

    private val despachador = StandardTestDispatcher()

    private lateinit var recetaDao: RecetaDaoFalso
    private lateinit var recetas: RecetaRepositorio
    private var recetaId: Long = 0

    @Before
    fun prepararTodo() {
        Dispatchers.setMain(despachador)
        recetaDao = RecetaDaoFalso(IngredienteDaoFalso())
        recetas = RecetaRepositorio(recetaDao, HistorialRepositorio(HistorialDaoFalso()))
    }

    @After
    fun dejarTodoComoEstaba() {
        Dispatchers.resetMain()
    }

    private fun probar(cuerpo: suspend TestScope.(PasosViewModel) -> Unit) =
        runTest(despachador) {
            recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId

            val modelo = PasosViewModel(recetaId, recetas)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            backgroundScope.launch(despachador) { modelo.dialogo.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    /** Agrega un paso y devuelve su id, que es lo que las acciones piden. */
    private suspend fun TestScope.agregar(modelo: PasosViewModel, texto: String): Long {
        modelo.agregarPaso()
        advanceUntilIdle()
        val id = recetaDao.obtenerPasos(recetaId).last().id
        modelo.cambiarTexto(id, texto)
        modelo.guardarPaso(id)
        advanceUntilIdle()
        return id
    }

    // --- Estado inicial ---

    @Test
    fun `una receta nueva no tiene pasos, y eso no es un problema`() = probar { modelo ->
        // El paso es opcional: hay recetas que se saben de memoria.
        assertTrue(modelo.estado.value.vacio)
        assertTrue(modelo.estado.value.bloques.isEmpty())
    }

    // --- Escribir ---

    @Test
    fun `un paso nuevo nace vacio y se escribe en su lugar`() = probar { modelo ->
        // Nace vacío a propósito: pedir el texto en un cuadro aparte antes de verlo en la
        // lista serían dos toques más por cada paso, y es lo que más se hace acá.
        modelo.agregarPaso()
        advanceUntilIdle()

        val elPaso = modelo.estado.value.bloques.single().pasos.single().paso
        assertEquals("", elPaso.texto)
        assertNull("Y vacío no es un error", modelo.estado.value.errorDe(elPaso))
    }

    @Test
    fun `lo que se escribe se guarda al salir del campo`() = probar { modelo ->
        val id = agregar(modelo, "Batir las claras.")

        assertEquals("Batir las claras.", recetaDao.obtenerPaso(id)!!.contenido)
    }

    @Test
    fun `mientras se escribe, la base todavia no se toca`() = probar { modelo ->
        // Un campo de texto que espera a que la base conteste se rompe (12.2.1).
        modelo.agregarPaso()
        advanceUntilIdle()
        val id = recetaDao.obtenerPasos(recetaId).single().id

        modelo.cambiarTexto(id, "a medio escribir")
        advanceUntilIdle()

        assertEquals("En la base sigue vacío", "", recetaDao.obtenerPaso(id)!!.contenido)
        assertEquals("Pero en la pantalla se ve", "a medio escribir",
            modelo.estado.value.textoDe(modelo.estado.value.bloques.single().pasos.single().paso))
    }

    @Test
    fun `vaciar un paso lo borra, en vez de dejar una fila en blanco`() = probar { modelo ->
        // Es la asimetría del paso: vaciar el campo es cómo se dice "este paso ya no va".
        // Una fila vacía correría todos los números de abajo por un paso fantasma.
        val id = agregar(modelo, "Algo")

        modelo.cambiarTexto(id, "   ")
        modelo.guardarPaso(id)
        advanceUntilIdle()

        assertNull(recetaDao.obtenerPaso(id))
        assertTrue(modelo.estado.value.vacio)
    }

    @Test
    fun `un paso demasiado largo se avisa y no se guarda`() = probar { modelo ->
        val id = agregar(modelo, "Corto")

        modelo.cambiarTexto(id, "a".repeat(1001))
        modelo.guardarPaso(id)
        advanceUntilIdle()

        assertNotNull(modelo.estado.value.mensaje)
        assertEquals("Lo guardado sigue siendo lo que servía", "Corto",
            recetaDao.obtenerPaso(id)!!.contenido)
    }

    @Test
    fun `al irse de la pantalla se guarda lo que quedo escrito`() = probar { modelo ->
        // Cambiar de paso de la receta puede llevarse el campo sin que alcance a avisar que
        // perdió el foco, y ahí lo recién escrito se perdía en silencio.
        modelo.agregarPaso()
        advanceUntilIdle()
        val id = recetaDao.obtenerPasos(recetaId).single().id
        modelo.cambiarTexto(id, "Escrito y sin confirmar")

        modelo.guardarTodoLoPendiente()
        advanceUntilIdle()

        assertEquals("Escrito y sin confirmar", recetaDao.obtenerPaso(id)!!.contenido)
    }

    // --- Los títulos (8.8) ---

    @Test
    fun `con una sola seccion solo se ofrece el General`() = probar { modelo ->
        // La sección automática no se muestra mientras sea la única, y lo que no se muestra
        // tampoco se ofrece como título: si no, toda receta nueva ofrecería dos "General".
        val id = agregar(modelo, "Un paso")

        modelo.abrirElegirTitulo(id)
        advanceUntilIdle()

        val cuadro = modelo.dialogo.value as DialogoPasos.ElegirTitulo
        assertEquals(listOf(null), cuadro.disponibles)
    }

    @Test
    fun `con dos secciones se ofrecen sus nombres y el General`() = probar { modelo ->
        recetas.agregarSeccion(recetaId, "Crema", "Bizcocho")
        advanceUntilIdle()
        val id = agregar(modelo, "Un paso")

        modelo.abrirElegirTitulo(id)
        advanceUntilIdle()

        val cuadro = modelo.dialogo.value as DialogoPasos.ElegirTitulo
        assertEquals("Las dos secciones más el General", 3, cuadro.disponibles.size)
        assertTrue(cuadro.disponibles.contains(null))
    }

    @Test
    fun `elegir un titulo lo deja guardado y agrupa el paso`() = probar { modelo ->
        recetas.agregarSeccion(recetaId, "Crema", "Bizcocho")
        advanceUntilIdle()
        val crema = recetas.obtenerSecciones(recetaId).first { it.nombreSeccion == "Crema" }
        val id = agregar(modelo, "Batir la crema.")

        modelo.abrirElegirTitulo(id)
        modelo.elegirTitulo(crema.id)
        advanceUntilIdle()

        assertEquals(crema.id, recetaDao.obtenerPaso(id)!!.tituloSeccionId)
        assertEquals("Crema", modelo.estado.value.bloques.single().encabezado)
    }

    @Test
    fun `una seccion ya usada por otro bloque no se vuelve a ofrecer`() = probar { modelo ->
        // Dos bloques "Crema" no dicen en cuál va cada cosa. Solo el General se repite.
        recetas.agregarSeccion(recetaId, "Crema", "Bizcocho")
        advanceUntilIdle()
        val crema = recetas.obtenerSecciones(recetaId).first { it.nombreSeccion == "Crema" }
        // Los dos pasos van **antes** de asignar ningún título: un paso nuevo hereda el del
        // último, así que asignando primero los dos quedarían en el mismo bloque.
        val primero = agregar(modelo, "Batir la crema.")
        val segundo = agregar(modelo, "Otra cosa.")
        modelo.abrirElegirTitulo(primero)
        modelo.elegirTitulo(crema.id)
        advanceUntilIdle()

        modelo.abrirElegirTitulo(segundo)
        advanceUntilIdle()

        val cuadro = modelo.dialogo.value as DialogoPasos.ElegirTitulo
        assertFalse("Crema ya está tomada", cuadro.disponibles.contains(crema.id))
    }

    @Test
    fun `al reabrir el titulo de un bloque, el suyo sigue estando`() = probar { modelo ->
        // Contarse a sí mismo entre los usados lo dejaría fuera de su propia lista.
        recetas.agregarSeccion(recetaId, "Crema", "Bizcocho")
        advanceUntilIdle()
        val crema = recetas.obtenerSecciones(recetaId).first { it.nombreSeccion == "Crema" }
        val id = agregar(modelo, "Batir la crema.")
        modelo.abrirElegirTitulo(id)
        modelo.elegirTitulo(crema.id)
        advanceUntilIdle()

        modelo.abrirElegirTitulo(id)
        advanceUntilIdle()

        val cuadro = modelo.dialogo.value as DialogoPasos.ElegirTitulo
        assertTrue("El suyo sigue ofreciéndose", cuadro.disponibles.contains(crema.id))
    }

    @Test
    fun `el General se puede repetir`() = probar { modelo ->
        recetas.agregarSeccion(recetaId, "Crema", "Bizcocho")
        advanceUntilIdle()
        val crema = recetas.obtenerSecciones(recetaId).first { it.nombreSeccion == "Crema" }
        val primero = agregar(modelo, "Uno")
        val segundo = agregar(modelo, "Dos")
        modelo.abrirElegirTitulo(primero)
        modelo.elegirTitulo(crema.id)
        advanceUntilIdle()

        modelo.abrirElegirTitulo(segundo)
        advanceUntilIdle()

        assertTrue((modelo.dialogo.value as DialogoPasos.ElegirTitulo).disponibles.contains(null))
    }

    // --- Mover ---

    @Test
    fun `mover un paso lo intercambia con su vecino`() = probar { modelo ->
        val primero = agregar(modelo, "Uno")
        val segundo = agregar(modelo, "Dos")

        modelo.moverPaso(segundo, haciaArriba = true)
        advanceUntilIdle()

        assertEquals(
            listOf("Dos", "Uno"),
            modelo.estado.value.bloques.single().pasos.map { it.paso.texto }
        )
        assertEquals("Y la numeración sigue corrida", listOf(1, 2),
            modelo.estado.value.bloques.single().pasos.map { it.numero })
        assertEquals(listOf(segundo, primero),
            modelo.estado.value.bloques.single().pasos.map { it.paso.id })
    }

    @Test
    fun `el primero no se puede subir mas`() = probar { modelo ->
        val primero = agregar(modelo, "Uno")
        agregar(modelo, "Dos")

        modelo.moverPaso(primero, haciaArriba = true)
        advanceUntilIdle()

        assertEquals(
            listOf("Uno", "Dos"),
            modelo.estado.value.bloques.single().pasos.map { it.paso.texto }
        )
    }

    @Test
    fun `mover funciona aunque los ordenes guardados tengan huecos`() = probar { modelo ->
        // Es el caso que rompía la primera versión: borrar un paso deja un hueco, así que los
        // `orden` guardados pueden ser [0, 2, 3] y no [0, 1, 2]. Usando el índice como orden,
        // mover el último hacia arriba lo mandaba al principio de la lista.
        val uno = agregar(modelo, "Uno")
        val dos = agregar(modelo, "Dos")
        val tres = agregar(modelo, "Tres")
        recetas.eliminarPaso(dos)
        advanceUntilIdle()

        modelo.moverPaso(tres, haciaArriba = true)
        advanceUntilIdle()

        assertEquals(
            listOf("Tres", "Uno"),
            modelo.estado.value.bloques.single().pasos.map { it.paso.texto }
        )
        assertEquals(listOf(tres, uno), modelo.estado.value.bloques.single().pasos.map { it.paso.id })
    }

    // --- Borrar ---

    @Test
    fun `un paso vacio se borra sin preguntar`() = probar { modelo ->
        // No hay nada que perder, y un cuadro por cada "agregué uno de más" es puro estorbo.
        modelo.agregarPaso()
        advanceUntilIdle()
        val elPaso = modelo.estado.value.bloques.single().pasos.single().paso

        modelo.pedirBorrado(elPaso)
        advanceUntilIdle()

        assertTrue(modelo.dialogo.value is DialogoPasos.Ninguno)
        assertTrue(modelo.estado.value.vacio)
    }

    @Test
    fun `un paso con texto pregunta antes de borrarse`() = probar { modelo ->
        // Nada se borra de golpe (6.3).
        agregar(modelo, "Algo que costó escribir")
        val elPaso = modelo.estado.value.bloques.single().pasos.single().paso

        modelo.pedirBorrado(elPaso)
        advanceUntilIdle()

        assertTrue(modelo.dialogo.value is DialogoPasos.ConfirmarBorrado)
        assertFalse("Y todavía no se borró", modelo.estado.value.vacio)
    }

    @Test
    fun `confirmar el borrado lo quita y renumera`() = probar { modelo ->
        agregar(modelo, "Uno")
        val segundo = agregar(modelo, "Dos")
        agregar(modelo, "Tres")
        val elDelMedio = modelo.estado.value.bloques.single().pasos
            .first { it.paso.id == segundo }.paso

        modelo.pedirBorrado(elDelMedio)
        modelo.confirmarBorrado()
        advanceUntilIdle()

        val quedaron = modelo.estado.value.bloques.single().pasos
        assertEquals(listOf("Uno", "Tres"), quedaron.map { it.paso.texto })
        assertEquals("Los números no dejan hueco", listOf(1, 2), quedaron.map { it.numero })
    }

    // --- Lo que escriben los otros pasos ---

    @Test
    fun `borrar una seccion deja sus pasos como General, sin perderlos`() = probar { modelo ->
        // El texto lo escribió alguien: hacerlo desaparecer porque se reorganizó la receta
        // sería perder trabajo sin avisar. Es la regla SET_NULL de la clave foránea.
        recetas.agregarSeccion(recetaId, "Crema", "Bizcocho")
        advanceUntilIdle()
        val crema = recetas.obtenerSecciones(recetaId).first { it.nombreSeccion == "Crema" }
        val id = agregar(modelo, "Batir la crema.")
        modelo.abrirElegirTitulo(id)
        modelo.elegirTitulo(crema.id)
        advanceUntilIdle()

        recetas.eliminarSeccion(recetaId, crema.id)
        advanceUntilIdle()

        val quedaron = modelo.estado.value.bloques.single()
        assertEquals("Batir la crema.", quedaron.pasos.single().paso.texto)
        assertNull("Pasó a ser del General", quedaron.titulo)
    }

    @Test
    fun `con un solo bloque General el encabezado no se dibuja`() = probar { modelo ->
        // Repetiría el título de la receta, que ya está arriba.
        agregar(modelo, "Uno")

        assertNull(modelo.estado.value.bloques.single().encabezado)
    }

    @Test
    fun `con dos bloques el General si lleva su encabezado`() = probar { modelo ->
        recetas.agregarSeccion(recetaId, "Crema", "Bizcocho")
        advanceUntilIdle()
        val crema = recetas.obtenerSecciones(recetaId).first { it.nombreSeccion == "Crema" }
        val primero = agregar(modelo, "Batir la crema.")
        agregar(modelo, "Dejar enfriar.")
        modelo.abrirElegirTitulo(primero)
        modelo.elegirTitulo(crema.id)
        advanceUntilIdle()

        val bloques = modelo.estado.value.bloques
        assertEquals(2, bloques.size)
        assertEquals(TITULO_GENERAL, bloques.last().encabezado)
    }
}
