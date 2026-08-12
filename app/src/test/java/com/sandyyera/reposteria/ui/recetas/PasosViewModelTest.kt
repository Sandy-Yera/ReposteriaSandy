package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.partes.TITULO_GENERAL
import com.sandyyera.reposteria.logica.validaciones.SIN_PASO_PREVIO
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

    private fun probar(cuerpo: suspend TestScope.(PasosViewModel) -> Unit) =
        runTest(despachador) {
            recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId

            val modelo = PasosViewModel(recetaId, recetas)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            backgroundScope.launch(despachador) { modelo.dialogo.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    /** Deja un ingrediente en el catálogo y devuelve su id, para poder ponerlo en la receta. */
    private suspend fun ingredienteLlamado(nombre: String): Long {
        catalogo.sembrar(Ingrediente(nombre = nombre, valorPorGramo = 1.0))
        return catalogo.obtenerTodosUnaVez().first { it.nombre == nombre }.id
    }

    /**
     * El cursor donde queda al terminar de escribir, que es de donde salen los atajos (8.8).
     *
     * Existe para que cada llamada diga **dónde estaba la mano**, y no para ahorrar letras:
     * `cambiarTexto` mira si hay un atajo justo antes del cursor, así que pasar cualquier
     * posición cambiaría lo que la prueba está probando.
     */
    private fun cursorAlFinal(texto: String) = texto.length

    /** Agrega un paso y devuelve su id, que es lo que las acciones piden. */
    private suspend fun TestScope.agregar(modelo: PasosViewModel, texto: String): Long {
        modelo.agregarPaso()
        advanceUntilIdle()
        val id = recetaDao.obtenerPasos(recetaId).last().id
        modelo.cambiarTexto(id, texto, cursorAlFinal(texto))
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

        modelo.cambiarTexto(id, "a medio escribir", cursorAlFinal("a medio escribir"))
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

        modelo.cambiarTexto(id, "   ", cursorAlFinal("   "))
        modelo.guardarPaso(id)
        advanceUntilIdle()

        assertNull(recetaDao.obtenerPaso(id))
        assertTrue(modelo.estado.value.vacio)
    }

    @Test
    fun `un paso demasiado largo se avisa y no se guarda`() = probar { modelo ->
        val id = agregar(modelo, "Corto")

        modelo.cambiarTexto(id, "a".repeat(1001), cursorAlFinal("a".repeat(1001)))
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
        modelo.cambiarTexto(id, "Escrito y sin confirmar", cursorAlFinal("Escrito y sin confirmar"))

        modelo.guardarTodoLoPendiente()
        advanceUntilIdle()

        assertEquals("Escrito y sin confirmar", recetaDao.obtenerPaso(id)!!.contenido)
    }

    // --- Los atajos (8.8) ---

    @Test
    fun `escribir dos puntos info abre la ayuda`() = probar { modelo ->
        val id = agregar(modelo, "Batir")

        val texto = "Batir :info:"
        modelo.cambiarTexto(id, texto, cursorAlFinal(texto))
        advanceUntilIdle()

        assertTrue(
            "El atajo abre la ayuda en el momento, sin esperar a guardar",
            modelo.dialogo.value is DialogoPasos.Ayuda
        )
    }

    @Test
    fun `cerrar la ayuda saca el dos puntos info del texto`() = probar { modelo ->
        // Si se quedara escrito, un atajo se convertiría en basura dentro de la receta: nadie
        // quiere leer ":info:" al seguir los pasos.
        val id = agregar(modelo, "Batir")
        val texto = "Batir :info:"
        modelo.cambiarTexto(id, texto, cursorAlFinal(texto))
        advanceUntilIdle()

        modelo.cerrarAyuda()
        advanceUntilIdle()

        val paso = modelo.estado.value.bloques.single().pasos.single().paso
        assertEquals("Batir ", modelo.estado.value.textoDe(paso))
        assertTrue(modelo.dialogo.value is DialogoPasos.Ninguno)
    }

    @Test
    fun `el boton de arriba abre la misma ayuda y no borra nada`() = probar { modelo ->
        // Se llega sin haber escrito ningún atajo, así que no hay nada que sacar del texto.
        val id = agregar(modelo, "Batir todo")

        modelo.abrirAyuda()
        advanceUntilIdle()
        assertTrue(modelo.dialogo.value is DialogoPasos.Ayuda)

        modelo.cerrarAyuda()
        advanceUntilIdle()

        val paso = modelo.estado.value.bloques.single().pasos.single().paso
        assertEquals("Batir todo", modelo.estado.value.textoDe(paso))
        assertEquals("Y no se pide mover ningún cursor", null, modelo.cursorPedido.value)
        // El id se usa para leer el paso; queda acá para que la prueba diga sobre cuál habla.
        assertEquals(id, paso.id)
    }

    @Test
    fun `escribir dos puntos ingredientes ofrece los de esta receta con su cantidad`() =
        probar { modelo ->
            val seccion = recetas.obtenerSecciones(recetaId).single()
            recetas.agregarIngrediente(seccion.id, ingredienteLlamado("Harina"), 500.0)
            recetas.agregarIngrediente(seccion.id, ingredienteLlamado("Azúcar"), 200.0)
            advanceUntilIdle()
            val id = agregar(modelo, "Mezclar")

            val texto = "Mezclar :ingredientes:"
            modelo.cambiarTexto(id, texto, cursorAlFinal(texto))
            advanceUntilIdle()

            val cuadro = modelo.dialogo.value as DialogoPasos.ElegirIngrediente
            val grupo = cuadro.grupos.single()
            assertNull("Con una sola parte sin nombre no se escribe encabezado", grupo.nombre)
            assertEquals(
                listOf("500 g de Harina", "200 g de Azúcar"),
                grupo.lineas.map { it.comoSeLee }
            )
        }

    @Test
    fun `el mismo ingrediente en dos partes se ve con su cantidad de cada una`() =
        probar { modelo ->
            // Es la razón de agrupar por sección: aplanarlo dejaría dos filas iguales sin decir
            // cuál es cuál, o una sola con la cantidad equivocada.
            val harina = ingredienteLlamado("Harina")
            recetas.agregarSeccion(recetaId, "Almíbar", nombreDeLaPrimera = "Bizcocho")
            advanceUntilIdle()
            val secciones = recetas.obtenerSecciones(recetaId)
            recetas.agregarIngrediente(secciones[0].id, harina, 600.0)
            recetas.agregarIngrediente(secciones[1].id, harina, 50.0)
            advanceUntilIdle()
            val id = agregar(modelo, "Mezclar")

            val texto = "Mezclar :ingredientes:"
            modelo.cambiarTexto(id, texto, cursorAlFinal(texto))
            advanceUntilIdle()

            val cuadro = modelo.dialogo.value as DialogoPasos.ElegirIngrediente
            assertEquals(listOf("Bizcocho", "Almíbar"), cuadro.grupos.map { it.nombre })
            assertEquals("600 g de Harina", cuadro.grupos[0].lineas.single().comoSeLee)
            assertEquals("50 g de Harina", cuadro.grupos[1].lineas.single().comoSeLee)
        }

    @Test
    fun `una parte sin ingredientes aparece igual, dicho`() = probar { modelo ->
        // Esconderla dejaría dudando entre "no tiene nada" y "se perdió".
        recetas.agregarSeccion(recetaId, "Almíbar", nombreDeLaPrimera = "Bizcocho")
        advanceUntilIdle()
        val secciones = recetas.obtenerSecciones(recetaId)
        recetas.agregarIngrediente(secciones[0].id, ingredienteLlamado("Harina"), 600.0)
        advanceUntilIdle()
        val id = agregar(modelo, "Mezclar")

        val texto = "Mezclar :ingredientes:"
        modelo.cambiarTexto(id, texto, cursorAlFinal(texto))
        advanceUntilIdle()

        val cuadro = modelo.dialogo.value as DialogoPasos.ElegirIngrediente
        assertEquals(2, cuadro.grupos.size)
        assertTrue(cuadro.grupos[1].lineas.isEmpty())
        assertFalse("Pero el cuadro no está vacío", cuadro.vacio)
    }

    @Test
    fun `elegir un ingrediente lo escribe en el lugar del atajo`() = probar { modelo ->
        val seccion = recetas.obtenerSecciones(recetaId).single()
        recetas.agregarIngrediente(seccion.id, ingredienteLlamado("Harina"), 500.0)
        advanceUntilIdle()
        val id = agregar(modelo, "Mezclar")
        val texto = "Mezclar :ingredientes: con agua"
        modelo.cambiarTexto(id, texto, "Mezclar :ingredientes:".length)
        advanceUntilIdle()

        modelo.elegirIngrediente("500 g de Harina")
        advanceUntilIdle()

        val paso = modelo.estado.value.bloques.single().pasos.single().paso
        assertEquals("Mezclar 500 g de Harina con agua", modelo.estado.value.textoDe(paso))
        assertEquals(
            "Y el cursor queda después de lo escrito",
            PosicionDelCursor(id, "Mezclar 500 g de Harina".length),
            modelo.cursorPedido.value
        )
    }

    @Test
    fun `crear un titulo desde los pasos bautiza la parte que ya estaba`() = probar { modelo ->
        // El caso de Sandy: una receta sin partes, donde el menú solo ofrecía el General y la
        // única salida era irse al paso de cantidades a crear una sección.
        val id = agregar(modelo, "Batir la crema")
        val texto = "Batir la crema :titulo:"
        modelo.cambiarTexto(id, texto, cursorAlFinal(texto))
        advanceUntilIdle()

        val cuadro = modelo.dialogo.value as DialogoPasos.ElegirTitulo
        assertEquals("Sin partes, solo estaba el General", listOf(null), cuadro.disponibles)

        modelo.empezarTituloNuevo()
        advanceUntilIdle()
        val creando = (modelo.dialogo.value as DialogoPasos.ElegirTitulo).creando!!
        assertNotNull("Hay que bautizar la que ya estaba", creando.nombreDeLaPrimera)

        modelo.cambiarNombreDelTitulo("Crema")
        modelo.guardarTituloNuevo()
        advanceUntilIdle()

        val secciones = recetas.obtenerSecciones(recetaId)
        assertEquals(2, secciones.size)
        assertEquals("Crema", secciones.last().nombreSeccion)
        val paso = modelo.estado.value.bloques.flatMap { it.pasos }.single().paso
        assertEquals("El paso queda bajo la parte nueva", secciones.last().id, paso.titulo)
        assertEquals("Y el atajo no se queda escrito", "Batir la crema ",
            modelo.estado.value.textoDe(paso))
    }

    @Test
    fun `un titulo repetido se rechaza dentro del cuadro`() = probar { modelo ->
        // El aviso va junto al campo y no en la franja de abajo: con el teclado abierto esa
        // franja queda tapada y el cuadro parece no haber hecho nada (8.2).
        recetas.agregarSeccion(recetaId, "Crema", nombreDeLaPrimera = "Bizcocho")
        advanceUntilIdle()
        val id = agregar(modelo, "Batir")

        modelo.abrirElegirTitulo(id)
        advanceUntilIdle()
        modelo.empezarTituloNuevo()
        advanceUntilIdle()
        modelo.cambiarNombreDelTitulo("Crema")
        modelo.guardarTituloNuevo()
        advanceUntilIdle()

        val creando = (modelo.dialogo.value as DialogoPasos.ElegirTitulo).creando!!
        assertNotNull("El rechazo se queda en el cuadro", creando.rechazo)
        assertEquals("Y no se creó nada", 2, recetas.obtenerSecciones(recetaId).size)
    }

    @Test
    fun `escribir dos puntos titulo abre el menu de titulos y lo saca del texto`() =
        probar { modelo ->
            recetas.agregarSeccion(recetaId, "Crema", "Bizcocho")
            advanceUntilIdle()
            val id = agregar(modelo, "Batir")
            val texto = "Batir :titulo:"
            modelo.cambiarTexto(id, texto, cursorAlFinal(texto))
            advanceUntilIdle()

            val cuadro = modelo.dialogo.value as DialogoPasos.ElegirTitulo
            assertNotNull("Viene marcado como venido de un atajo", cuadro.atajo)

            modelo.elegirTitulo(cuadro.disponibles.first { it != null })
            advanceUntilIdle()

            val paso = modelo.estado.value.bloques.flatMap { it.pasos }.single().paso
            assertEquals("El atajo no se queda escrito", "Batir ", modelo.estado.value.textoDe(paso))
        }

    @Test
    fun `tocar el numero abre el mismo menu sin tocar el texto`() = probar { modelo ->
        // Se llega sin atajo escrito, así que elegir no puede borrarle nada al paso.
        recetas.agregarSeccion(recetaId, "Crema", "Bizcocho")
        advanceUntilIdle()
        val id = agregar(modelo, "Batir la crema")

        modelo.abrirElegirTitulo(id)
        advanceUntilIdle()

        val cuadro = modelo.dialogo.value as DialogoPasos.ElegirTitulo
        assertNull("Sin atajo de por medio", cuadro.atajo)

        modelo.elegirTitulo(cuadro.disponibles.first { it != null })
        advanceUntilIdle()

        val paso = modelo.estado.value.bloques.flatMap { it.pasos }.single().paso
        assertEquals("Batir la crema", modelo.estado.value.textoDe(paso))
    }

    @Test
    fun `la palabra suelta no dispara nada`() = probar { modelo ->
        // La misma regla que en `logica/`, comprobada desde acá: el atajo necesita sus dos
        // puntos, o escribir "el titulo lleva crema" abriría un menú a mitad de la frase.
        val id = agregar(modelo, "Batir")
        val texto = "Ahora el titulo se decora"
        modelo.cambiarTexto(id, texto, cursorAlFinal(texto))
        advanceUntilIdle()

        assertTrue(modelo.dialogo.value is DialogoPasos.Ninguno)
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

    // --- El "antes de empezar" (8.8) ---

    @Test
    fun `el campo del antes de empezar arranca vacio, no con la frase por defecto`() =
        probar { modelo ->
            // La receta dice "No necesita" porque nadie escribió nada. Mostrarlo dentro del
            // campo obligaría a borrarlo a mano antes de poder poner lo propio.
            assertEquals(SIN_PASO_PREVIO, modelo.estado.value.pasoPrevio)
            assertEquals("", modelo.estado.value.textoDelPasoPrevio)
        }

    @Test
    fun `escribir el antes de empezar lo guarda al salir del campo`() = probar { modelo ->
        modelo.cambiarPasoPrevio("Tener el bizcocho del día anterior")
        // Mientras se escribe, la base todavía no lo tiene: se guarda al abandonar el campo,
        // igual que un paso.
        assertEquals(SIN_PASO_PREVIO, modelo.estado.value.pasoPrevio)

        modelo.guardarPasoPrevio()
        advanceUntilIdle()

        assertEquals("Tener el bizcocho del día anterior", modelo.estado.value.pasoPrevio)
        assertEquals(
            "Y el campo ya muestra lo guardado",
            "Tener el bizcocho del día anterior",
            modelo.estado.value.textoDelPasoPrevio
        )
    }

    @Test
    fun `vaciar el antes de empezar repone la frase por defecto`() = probar { modelo ->
        modelo.cambiarPasoPrevio("Almíbar frío")
        modelo.guardarPasoPrevio()
        advanceUntilIdle()

        modelo.cambiarPasoPrevio("")
        modelo.guardarPasoPrevio()
        advanceUntilIdle()

        // Guardarlo en blanco dejaría el resumen diciendo "Antes de empezar:" y nada más, que
        // se lee como un dato que falta en vez de como un "no hace falta".
        assertEquals(SIN_PASO_PREVIO, modelo.estado.value.pasoPrevio)
        assertEquals("", modelo.estado.value.textoDelPasoPrevio)
    }

    @Test
    fun `irse de la pantalla guarda tambien el antes de empezar`() = probar { modelo ->
        // `guardarTodoLoPendiente` se llama al desmontarse. Si se olvidara de este campo, lo
        // escrito se perdería al cambiar de paso de la receta sin tocar nada más.
        modelo.cambiarPasoPrevio("Manjar hecho")
        modelo.guardarTodoLoPendiente()
        advanceUntilIdle()

        assertEquals("Manjar hecho", modelo.estado.value.pasoPrevio)
    }
}
