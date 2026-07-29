package com.sandyyera.reposteria.ui.moldes

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.MoldeDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.MoldeRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.validaciones.CampoDeMolde
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
import kotlin.math.PI

/**
 * Pruebas del catálogo de moldes (9.2), sin base de datos ni celular.
 *
 * Lo propio de esta pantalla es que el formulario cambia de forma: qué campos pedir depende
 * de la forma elegida, y eso no puede quedar librado a un `when` escrito en el Composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoldesViewModelTest {

    private val despachador = StandardTestDispatcher()

    private lateinit var moldeDao: MoldeDaoFalso
    private lateinit var repositorio: MoldeRepositorio

    @Before
    fun prepararTodo() {
        Dispatchers.setMain(despachador)
        val catalogo = IngredienteDaoFalso()
        val recetaDao = RecetaDaoFalso(catalogo)
        moldeDao = MoldeDaoFalso(recetaDao)
        val historial = HistorialRepositorio(HistorialDaoFalso())
        repositorio = MoldeRepositorio(
            dao = moldeDao,
            recetas = RecetaRepositorio(recetaDao, historial),
            historial = historial
        )
    }

    @After
    fun dejarTodoComoEstaba() {
        Dispatchers.resetMain()
    }

    private fun probar(cuerpo: suspend TestScope.(MoldesViewModel) -> Unit) =
        runTest(despachador) {
            val modelo = MoldesViewModel(repositorio)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    private fun formulario(modelo: MoldesViewModel) =
        modelo.dialogo.value as DialogoMolde.Formulario

    /** Llena el formulario de un molde redondo, sin guardarlo. */
    private fun llenarCirculo(modelo: MoldesViewModel, nombre: String = "Redondo grande") {
        modelo.abrirAlta()
        modelo.cambiarNombre(nombre)
        modelo.elegirForma(TipoFormaMolde.CIRCULO)
        modelo.cambiarMedida(CampoDeMolde.DIAMETRO, "24")
        modelo.cambiarMedida(CampoDeMolde.ALTURA_MOLDE, "7")
    }

    // --- Estado inicial ---

    @Test
    fun `sin moldes se distingue el catalogo vacio de una busqueda sin resultados`() =
        probar { modelo ->
            assertTrue(modelo.estado.value.catalogoVacio)
            assertFalse(modelo.estado.value.busquedaSinResultados)

            llenarCirculo(modelo)
            modelo.guardar()
            advanceUntilIdle()

            modelo.buscar("cuadrado")
            advanceUntilIdle()
            assertFalse(modelo.estado.value.catalogoVacio)
            assertTrue(modelo.estado.value.busquedaSinResultados)
        }

    // --- El formulario cambia de forma ---

    @Test
    fun `cada forma pide sus campos y ninguno mas`() = probar { modelo ->
        modelo.abrirAlta()
        // Sin forma elegida no se pide ninguna medida: no se puede exigir sin saber cuál.
        assertTrue(formulario(modelo).campos.isEmpty())

        modelo.elegirForma(TipoFormaMolde.CIRCULO)
        assertEquals(
            listOf(CampoDeMolde.DIAMETRO, CampoDeMolde.ALTURA_MOLDE),
            formulario(modelo).campos
        )

        modelo.elegirForma(TipoFormaMolde.TRIANGULO)
        assertEquals(
            listOf(
                CampoDeMolde.BASE_TRIANGULO,
                CampoDeMolde.ALTURA_TRIANGULO,
                CampoDeMolde.ALTURA_MOLDE
            ),
            formulario(modelo).campos
        )
    }

    @Test
    fun `cambiar de forma no borra lo que ya se habia escrito para la otra`() = probar { modelo ->
        // Quien probó "círculo", anotó el diámetro y pasa a "cuadrado" para comparar, al
        // volver encuentra su diámetro donde lo dejó en vez de tener que medir de nuevo.
        llenarCirculo(modelo)

        modelo.elegirForma(TipoFormaMolde.CUADRADO)
        assertEquals("24", formulario(modelo).medidas[CampoDeMolde.DIAMETRO])
        // Pero el diámetro ya no se pide ni impide nada: no está entre los campos.
        assertFalse(CampoDeMolde.DIAMETRO in formulario(modelo).campos)

        modelo.elegirForma(TipoFormaMolde.CIRCULO)
        assertTrue(formulario(modelo).puedeGuardar)
    }

    @Test
    fun `solo se puede guardar cuando la forma y sus medidas estan completas`() =
        probar { modelo ->
            modelo.abrirAlta()
            assertFalse(formulario(modelo).puedeGuardar)

            modelo.cambiarNombre("Redondo grande")
            assertFalse("Falta la forma", formulario(modelo).puedeGuardar)

            modelo.elegirForma(TipoFormaMolde.CIRCULO)
            assertFalse("Faltan las medidas", formulario(modelo).puedeGuardar)

            modelo.cambiarMedida(CampoDeMolde.DIAMETRO, "24")
            assertFalse("Falta el alto", formulario(modelo).puedeGuardar)

            modelo.cambiarMedida(CampoDeMolde.ALTURA_MOLDE, "7")
            assertTrue(formulario(modelo).puedeGuardar)
        }

    @Test
    fun `el area y el volumen se ven mientras se escribe, no al guardar`() = probar { modelo ->
        // Son la única forma de darse cuenta ahí mismo de que se anotó un 3 donde iba un 30.
        modelo.abrirAlta()
        modelo.elegirForma(TipoFormaMolde.CIRCULO)
        modelo.cambiarMedida(CampoDeMolde.DIAMETRO, "24")
        assertNull(
            "Con el alto en blanco todavía no hay nada que mostrar",
            formulario(modelo).vistaPrevia
        )

        modelo.cambiarMedida(CampoDeMolde.ALTURA_MOLDE, "7")

        val (area, volumen) = formulario(modelo).vistaPrevia!!
        assertEquals(PI * 12 * 12, area, 0.001)
        assertEquals(PI * 12 * 12 * 7, volumen, 0.001)
    }

    @Test
    fun `la vista previa no espera a que el molde tenga nombre`() = probar { modelo ->
        // Esconder el volumen hasta que lo bauticen sería tapar justo el número que dice si
        // se midió bien.
        modelo.abrirAlta()
        modelo.elegirForma(TipoFormaMolde.CUADRADO)
        modelo.cambiarMedida(CampoDeMolde.LADO, "20")
        modelo.cambiarMedida(CampoDeMolde.ALTURA_MOLDE, "6")

        assertNotNull(formulario(modelo).vistaPrevia)
        assertFalse("Pero sin nombre no se guarda", formulario(modelo).puedeGuardar)
    }

    @Test
    fun `el punto de mil se pone solo en las medidas`() = probar { modelo ->
        modelo.abrirAlta()
        modelo.elegirForma(TipoFormaMolde.EXOTICO)
        modelo.cambiarMedida(CampoDeMolde.VOLUMEN_EXOTICO, "1500")

        assertEquals("1.500", formulario(modelo).medidas[CampoDeMolde.VOLUMEN_EXOTICO])
    }

    // --- Guardar ---

    @Test
    fun `guardar deja el molde en la lista y cierra el cuadro`() = probar { modelo ->
        llenarCirculo(modelo)
        modelo.guardar()
        advanceUntilIdle()

        assertTrue(modelo.dialogo.value is DialogoMolde.Ninguno)
        assertEquals(listOf("Redondo grande"), modelo.estado.value.visibles.map { it.nombre })
        assertNotNull(modelo.estado.value.mensaje)
    }

    @Test
    fun `un nombre repetido avisa dentro del cuadro y no lo cierra`() = probar { modelo ->
        llenarCirculo(modelo, "Redondo grande")
        modelo.guardar()
        advanceUntilIdle()

        llenarCirculo(modelo, "REDONDO GRANDE")
        modelo.guardar()
        advanceUntilIdle()

        // Junto al campo y no en la franja de abajo, que con el teclado abierto queda tapada.
        val cuadro = formulario(modelo)
        assertNotNull(cuadro.errorNombre)
        assertFalse(cuadro.guardando)
        assertEquals(1, modelo.estado.value.visibles.size)

        // Y al corregirlo, el aviso desaparece.
        modelo.cambiarNombre("Redondo chico")
        assertNull(formulario(modelo).errorNombre)
    }

    // --- Editar ---

    @Test
    fun `abrir la edicion trae las medidas ya escritas y con el formato de la app`() =
        probar { modelo ->
            modelo.abrirAlta()
            modelo.cambiarNombre("Corazón")
            modelo.elegirForma(TipoFormaMolde.EXOTICO)
            modelo.cambiarMedida(CampoDeMolde.VOLUMEN_EXOTICO, "1500")
            modelo.cambiarMedida(CampoDeMolde.ALTURA_MOLDE, "6")
            modelo.guardar()
            advanceUntilIdle()

            modelo.abrirEdicion(modelo.estado.value.visibles.single())

            val cuadro = formulario(modelo)
            assertEquals("Corazón", cuadro.nombre)
            assertEquals(TipoFormaMolde.EXOTICO, cuadro.forma)
            // Con el punto de mil, que es el mismo formato que `textoANumero` lee de vuelta:
            // abrir y guardar sin cambiar nada no puede alterar ningún número.
            assertEquals("1.500", cuadro.medidas[CampoDeMolde.VOLUMEN_EXOTICO])
            assertEquals("6", cuadro.medidas[CampoDeMolde.ALTURA_MOLDE])
            assertTrue(cuadro.puedeGuardar)
        }

    @Test
    fun `editar y guardar sin cambiar nada deja las mismas medidas`() = probar { modelo ->
        llenarCirculo(modelo)
        modelo.guardar()
        advanceUntilIdle()
        val antes = modelo.estado.value.visibles.single()

        modelo.abrirEdicion(antes)
        modelo.guardar()
        advanceUntilIdle()

        val despues = modelo.estado.value.visibles.single()
        assertEquals(antes.dimensiones.diametroCm!!, despues.dimensiones.diametroCm!!, 0.001)
        assertEquals(antes.dimensiones.alturaMoldeCm!!, despues.dimensiones.alturaMoldeCm!!, 0.001)
        assertEquals(1, modelo.estado.value.visibles.size)
    }

    // --- Borrar ---

    @Test
    fun `el cuadro de borrado se abre al instante y completa la lista despues`() =
        probar { modelo ->
            llenarCirculo(modelo)
            modelo.guardar()
            advanceUntilIdle()

            modelo.pedirBorrado(modelo.estado.value.visibles.single())

            // Antes de que vuelva la consulta el cuadro ya está abierto: esperar haría
            // parecer que el botón no responde.
            val reciente = modelo.dialogo.value as DialogoMolde.ConfirmarBorrado
            assertNull("null es 'todavía consultando'", reciente.recetasAfectadas)

            advanceUntilIdle()
            val completo = modelo.dialogo.value as DialogoMolde.ConfirmarBorrado
            assertEquals(
                "Lista vacía es 'no lo usa ninguna receta', que no es lo mismo",
                emptyList<Any>(),
                completo.recetasAfectadas
            )
        }

    @Test
    fun `confirmar el borrado lo saca de la lista`() = probar { modelo ->
        llenarCirculo(modelo)
        modelo.guardar()
        advanceUntilIdle()

        modelo.pedirBorrado(modelo.estado.value.visibles.single())
        advanceUntilIdle()
        modelo.confirmarBorrado()
        advanceUntilIdle()

        assertTrue(modelo.estado.value.visibles.isEmpty())
        assertTrue(modelo.dialogo.value is DialogoMolde.Ninguno)
    }

    // --- Buscador ---

    @Test
    fun `el buscador encuentra sin importar tildes ni mayusculas`() = probar { modelo ->
        modelo.abrirAlta()
        modelo.cambiarNombre("Corazón chico")
        modelo.elegirForma(TipoFormaMolde.CUADRADO)
        modelo.cambiarMedida(CampoDeMolde.LADO, "20")
        modelo.cambiarMedida(CampoDeMolde.ALTURA_MOLDE, "6")
        modelo.guardar()
        advanceUntilIdle()

        modelo.buscar("corazon")
        advanceUntilIdle()

        assertEquals(1, modelo.estado.value.visibles.size)
    }
}
