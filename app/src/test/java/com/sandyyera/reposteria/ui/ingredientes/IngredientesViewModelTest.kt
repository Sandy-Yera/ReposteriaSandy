package com.sandyyera.reposteria.ui.ingredientes

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.logica.calculadora.UnidadDeCompra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas del `IngredientesViewModel`, sin base de datos ni celular
 * (`./gradlew :app:test`).
 *
 * Usa el repositorio **de verdad** sobre los DAO falsos, no un repositorio falso: así lo
 * que se prueba es la cadena completa —pantalla, repositorio, "base"— y no una imitación
 * que podría comportarse distinto justo donde importa.
 *
 * Dos cosas hacen falta para que esto corra fuera de un teléfono:
 *
 * 1. `viewModelScope` usa el hilo principal de Android, que en el escritorio no existe.
 *    `Dispatchers.setMain` lo reemplaza por uno de mentira, cuyo reloj avanza a mano con
 *    `advanceUntilIdle()`.
 * 2. `estado` es un `StateFlow` armado con `WhileSubscribed`: **no calcula nada mientras
 *    nadie lo mire**. En la app lo mira la pantalla; acá hay que mirarlo a propósito, y de
 *    eso se encarga [observandoElEstado]. Sin eso, `estado.value` se quedaría para siempre
 *    en el valor inicial y las pruebas fallarían por una razón que no tiene que ver con lo
 *    que se quería probar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IngredientesViewModelTest {

    private val despachador = StandardTestDispatcher()

    private lateinit var ingredientes: IngredienteDaoFalso
    private lateinit var recetas: RecetaDaoFalso
    private lateinit var historial: HistorialDaoFalso
    private lateinit var modelo: IngredientesViewModel

    @Before
    fun prepararTodo() {
        Dispatchers.setMain(despachador)
        ingredientes = IngredienteDaoFalso()
        recetas = RecetaDaoFalso()
        historial = HistorialDaoFalso()
        modelo = IngredientesViewModel(
            IngredienteRepositorio(
                dao = ingredientes,
                recetaDao = recetas,
                historial = HistorialRepositorio(historial)
            )
        )
    }

    @After
    fun dejarTodoComoEstaba() {
        Dispatchers.resetMain()
    }

    /** Deja a alguien mirando el estado, que es lo que hace la pantalla en la app real. */
    private fun TestScope.observandoElEstado() {
        backgroundScope.launch(despachador) { modelo.estado.collect { } }
    }

    private fun probar(cuerpo: suspend TestScope.() -> Unit) = runTest(despachador) {
        observandoElEstado()
        advanceUntilIdle()
        cuerpo()
    }

    // --- Lista y búsqueda ---

    @Test
    fun `arranca vacia y sin estar cargando una vez que alguien mira`() = probar {
        assertTrue(modelo.estado.value.visibles.isEmpty())
        assertFalse(modelo.estado.value.cargando)
        assertTrue(modelo.estado.value.catalogoVacio)
    }

    @Test
    fun `el buscador filtra sin tildes y no vacia el catalogo`() = probar {
        ingredientes.sembrar(
            Ingrediente(nombre = "Plátano", valorPorGramo = 1.0),
            Ingrediente(nombre = "Harina", valorPorGramo = 1.0)
        )
        advanceUntilIdle()

        modelo.buscar("platano")
        advanceUntilIdle()

        assertEquals(listOf("Plátano"), modelo.estado.value.visibles.map { it.nombre })
        // Que la búsqueda no encuentre nada no es lo mismo que no tener ingredientes: son
        // dos mensajes distintos en la pantalla.
        assertFalse(modelo.estado.value.catalogoVacio)
    }

    @Test
    fun `una busqueda sin resultados se distingue del catalogo vacio`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1.0))
        advanceUntilIdle()

        modelo.buscar("chocolate")
        advanceUntilIdle()

        assertTrue(modelo.estado.value.busquedaSinResultados)
        assertFalse(modelo.estado.value.catalogoVacio)
    }

    // --- Alta ---

    @Test
    fun `el formulario nuevo no deja guardar hasta que los dos campos sirvan`() = probar {
        modelo.abrirAlta()
        advanceUntilIdle()
        val vacio = modelo.estado.value.dialogo as DialogoIngrediente.Formulario
        assertFalse(vacio.puedeGuardar)
        // Y no reta por campos que todavía no se tocaron.
        assertNull(vacio.errorNombreVisible)
        assertNull(vacio.errorValorVisible)

        modelo.cambiarNombre("Harina")
        modelo.cambiarValor("1,55")
        advanceUntilIdle()

        assertTrue((modelo.estado.value.dialogo as DialogoIngrediente.Formulario).puedeGuardar)
    }

    @Test
    fun `un campo tocado y mal escrito si muestra el motivo`() = probar {
        modelo.abrirAlta()
        modelo.cambiarValor("abc")
        advanceUntilIdle()

        val formulario = modelo.estado.value.dialogo as DialogoIngrediente.Formulario
        assertNotNull(formulario.errorValorVisible)
        assertFalse(formulario.puedeGuardar)
    }

    @Test
    fun `guardar cierra el formulario, agrega el ingrediente y avisa`() = probar {
        modelo.abrirAlta()
        modelo.cambiarNombre("Harina")
        modelo.cambiarValor("1,55")
        modelo.guardar()
        advanceUntilIdle()

        val estado = modelo.estado.value
        assertEquals(DialogoIngrediente.Ninguno, estado.dialogo)
        assertEquals(listOf("Harina"), estado.visibles.map { it.nombre })
        assertEquals(1.55, estado.visibles.single().valorPorGramo, 0.0)
        assertNotNull(estado.mensaje)
    }

    @Test
    fun `un nombre repetido deja el formulario abierto con el aviso`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Azúcar", valorPorGramo = 1.0))
        advanceUntilIdle()

        modelo.abrirAlta()
        modelo.cambiarNombre("azucar")
        modelo.cambiarValor("2")
        modelo.guardar()
        advanceUntilIdle()

        val formulario = modelo.estado.value.dialogo as DialogoIngrediente.Formulario
        assertNotNull(formulario.nombreRepetido)
        assertFalse("no debe quedar trabado guardando", formulario.guardando)
        // Y no se creó un segundo ingrediente.
        assertEquals(1, modelo.estado.value.visibles.size)
    }

    @Test
    fun `seguir escribiendo el nombre borra el aviso de repetido`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Azúcar", valorPorGramo = 1.0))
        advanceUntilIdle()
        modelo.abrirAlta()
        modelo.cambiarNombre("azucar")
        modelo.cambiarValor("2")
        modelo.guardar()
        advanceUntilIdle()

        modelo.cambiarNombre("azucar flor")
        advanceUntilIdle()

        val formulario = modelo.estado.value.dialogo as DialogoIngrediente.Formulario
        assertNull(formulario.nombreRepetido)
    }

    // --- Edición ---

    @Test
    fun `editar abre el formulario con el valor en el formato de la app`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1234.5))
        advanceUntilIdle()

        modelo.abrirEdicion(modelo.estado.value.visibles.single())
        advanceUntilIdle()

        val formulario = modelo.estado.value.dialogo as DialogoIngrediente.Formulario
        assertEquals("Harina", formulario.nombre)
        assertEquals("1.234,5", formulario.valorPorGramo)
        assertNotNull(formulario.editando)
    }

    // --- Borrado ---

    @Test
    fun `pedir borrado abre el aviso y despues llega la lista de recetas`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1.0))
        advanceUntilIdle()
        val harina = modelo.estado.value.visibles.single()
        recetas.declararUso(harina.id, Receta(id = 1, titulo = "Torta de manjar"))

        modelo.pedirBorrado(harina)
        advanceUntilIdle()

        val aviso = modelo.estado.value.dialogo as DialogoIngrediente.ConfirmarBorrado
        assertEquals(listOf("Torta de manjar"), aviso.recetasAfectadas?.map { it.titulo })
    }

    @Test
    fun `confirmar sin haber consultado las recetas no borra nada`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1.0))
        advanceUntilIdle()
        val harina = modelo.estado.value.visibles.single()

        modelo.pedirBorrado(harina)
        // A propósito no se adelanta el reloj: la consulta sigue en curso y la advertencia
        // todavía no se mostró completa.
        modelo.confirmarBorrado()
        advanceUntilIdle()

        assertEquals(1, modelo.estado.value.visibles.size)
    }

    @Test
    fun `confirmar el borrado lo saca de la lista y avisa`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1.0))
        advanceUntilIdle()

        modelo.pedirBorrado(modelo.estado.value.visibles.single())
        advanceUntilIdle()
        modelo.confirmarBorrado()
        advanceUntilIdle()

        assertTrue(modelo.estado.value.visibles.isEmpty())
        assertEquals(DialogoIngrediente.Ninguno, modelo.estado.value.dialogo)
        assertNotNull(modelo.estado.value.mensaje)
    }

    // --- Calculadora de valor por gramo ---

    @Test
    fun `la calculadora arranca sin nada elegido`() = probar {
        modelo.abrirCalculadora()
        advanceUntilIdle()

        val calculadora = modelo.estado.value.calculadora!!
        assertNull(calculadora.destino)
        assertNull(calculadora.resultado)
        assertFalse(calculadora.puedeTerminar)
        assertFalse(calculadora.faltaElegirDestino)
    }

    @Test
    fun `un kilo a mil pesos da un peso por gramo`() = probar {
        modelo.abrirCalculadora()
        modelo.cambiarPrecio("1000")
        modelo.cambiarCantidad("1")
        modelo.cambiarUnidad(UnidadDeCompra.KILO)
        advanceUntilIdle()

        val calculadora = modelo.estado.value.calculadora!!
        assertEquals(1.0, calculadora.resultado!!, 0.0)
        // El precio se muestra ya con el punto de mil puesto.
        assertEquals("1.000", calculadora.precio)
        assertTrue(calculadora.puedeTerminar)
    }

    @Test
    fun `cambiar de unidad cambia el resultado mil veces`() = probar {
        modelo.abrirCalculadora()
        modelo.cambiarPrecio("1000")
        modelo.cambiarCantidad("1")
        modelo.cambiarUnidad(UnidadDeCompra.GRAMO)
        advanceUntilIdle()

        assertEquals(1000.0, modelo.estado.value.calculadora!!.resultado!!, 0.0)
    }

    @Test
    fun `terminar sin elegir destino avisa y no cambia nada`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1.55))
        advanceUntilIdle()
        modelo.abrirCalculadora()
        modelo.cambiarPrecio("1000")
        modelo.cambiarCantidad("1")
        advanceUntilIdle()

        modelo.terminarCalculadora()
        advanceUntilIdle()

        assertTrue(modelo.estado.value.calculadora!!.faltaElegirDestino)
        assertEquals(1.55, modelo.estado.value.visibles.single().valorPorGramo, 0.0)
    }

    @Test
    fun `elegir Crear abre el formulario con el valor ya puesto`() = probar {
        modelo.abrirCalculadora()
        modelo.cambiarPrecio("1000")
        modelo.cambiarCantidad("1")
        modelo.elegirDestino(DestinoDelValor.Crear)
        advanceUntilIdle()

        modelo.terminarCalculadora()
        advanceUntilIdle()

        assertNull("la calculadora debe cerrarse", modelo.estado.value.calculadora)
        val formulario = modelo.estado.value.dialogo as DialogoIngrediente.Formulario
        assertEquals("1", formulario.valorPorGramo)
        assertEquals("", formulario.nombre)
    }

    @Test
    fun `reemplazar pasa por la confirmacion antes de pisar el valor`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1.55))
        advanceUntilIdle()
        val harina = modelo.estado.value.visibles.single()

        modelo.abrirCalculadora()
        modelo.cambiarPrecio("1000")
        modelo.cambiarCantidad("1")
        modelo.elegirDestino(DestinoDelValor.Reemplazar(harina.id))
        advanceUntilIdle()
        modelo.terminarCalculadora()
        advanceUntilIdle()

        val aviso = modelo.estado.value.dialogo as DialogoIngrediente.ConfirmarReemplazo
        assertEquals(1.55, aviso.ingrediente.valorPorGramo, 0.0)
        assertEquals(1.0, aviso.valorNuevo, 0.0)
        // Todavía no se cambió nada: la calculadora sigue abierta detrás, para que
        // cancelar devuelva a donde se estaba.
        assertEquals(1.55, modelo.estado.value.visibles.single().valorPorGramo, 0.0)
        assertNotNull(modelo.estado.value.calculadora)
    }

    @Test
    fun `confirmar el reemplazo cambia el valor y cierra todo`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1.55))
        advanceUntilIdle()
        val harina = modelo.estado.value.visibles.single()

        modelo.abrirCalculadora()
        modelo.cambiarPrecio("1000")
        modelo.cambiarCantidad("1")
        modelo.elegirDestino(DestinoDelValor.Reemplazar(harina.id))
        advanceUntilIdle()
        modelo.terminarCalculadora()
        advanceUntilIdle()
        modelo.confirmarReemplazo()
        advanceUntilIdle()

        assertEquals(1.0, modelo.estado.value.visibles.single().valorPorGramo, 0.0)
        assertEquals(DialogoIngrediente.Ninguno, modelo.estado.value.dialogo)
        assertNull(modelo.estado.value.calculadora)
        assertNotNull(modelo.estado.value.mensaje)
    }

    @Test
    fun `cancelar el reemplazo deja el valor intacto y la calculadora abierta`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1.55))
        advanceUntilIdle()
        val harina = modelo.estado.value.visibles.single()

        modelo.abrirCalculadora()
        modelo.cambiarPrecio("1000")
        modelo.cambiarCantidad("1")
        modelo.elegirDestino(DestinoDelValor.Reemplazar(harina.id))
        advanceUntilIdle()
        modelo.terminarCalculadora()
        advanceUntilIdle()
        modelo.cerrarDialogo()
        advanceUntilIdle()

        assertEquals(1.55, modelo.estado.value.visibles.single().valorPorGramo, 0.0)
        assertNotNull(modelo.estado.value.calculadora)
    }

    @Test
    fun `volver a tocar lo elegido lo desmarca`() = probar {
        modelo.abrirCalculadora()
        modelo.elegirDestino(DestinoDelValor.Crear)
        advanceUntilIdle()
        assertTrue(modelo.estado.value.calculadora!!.creandoNuevo)

        modelo.elegirDestino(DestinoDelValor.Crear)
        advanceUntilIdle()

        assertNull(modelo.estado.value.calculadora!!.destino)
    }

    @Test
    fun `el valor actual del elegido sale de la lista viva, no de una copia`() = probar {
        ingredientes.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1.55))
        advanceUntilIdle()
        val harina = modelo.estado.value.visibles.single()

        modelo.abrirCalculadora()
        modelo.elegirDestino(DestinoDelValor.Reemplazar(harina.id))
        advanceUntilIdle()

        assertEquals(1.55, modelo.estado.value.calculadora!!.elegido!!.valorPorGramo, 0.0)

        // Si ese ingrediente desaparece mientras la calculadora está abierta, la elección
        // deja de resolverse en vez de quedar apuntando a una copia vieja.
        ingredientes.eliminarPorId(harina.id)
        advanceUntilIdle()

        assertNull(modelo.estado.value.calculadora!!.elegido)
    }

    @Test
    fun `el buscador de la calculadora filtra los candidatos`() = probar {
        ingredientes.sembrar(
            Ingrediente(nombre = "Harina", valorPorGramo = 1.0),
            Ingrediente(nombre = "Plátano", valorPorGramo = 1.0)
        )
        advanceUntilIdle()
        modelo.abrirCalculadora()
        advanceUntilIdle()
        assertEquals(2, modelo.estado.value.calculadora!!.candidatos.size)

        modelo.buscarDestino("platano")
        advanceUntilIdle()

        assertEquals(
            listOf("Plátano"),
            modelo.estado.value.calculadora!!.candidatos.map { it.nombre }
        )
    }

    // --- Mensajes ---

    @Test
    fun `el mensaje se limpia despues de mostrarse, para que no reaparezca al girar`() = probar {
        modelo.abrirAlta()
        modelo.cambiarNombre("Harina")
        modelo.cambiarValor("1")
        modelo.guardar()
        advanceUntilIdle()
        assertNotNull(modelo.estado.value.mensaje)

        modelo.mensajeMostrado()
        advanceUntilIdle()

        assertNull(modelo.estado.value.mensaje)
    }
}
