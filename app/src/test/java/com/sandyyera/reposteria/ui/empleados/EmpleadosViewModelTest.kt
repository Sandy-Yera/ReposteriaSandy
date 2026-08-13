package com.sandyyera.reposteria.ui.empleados

import com.sandyyera.reposteria.data.EmpleadoDaoFalso
import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.repositorio.EmpleadoRepositorio
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaDeUnEmpleado
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearEmpleado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
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
 * La sección Empleados vista desde la pantalla (10).
 *
 * Nace de dos errores que solo se ven acá, en el ViewModel, y que ninguna prueba de repositorio
 * podía detectar: **el campo de las unidades no se podía vaciar** —mostraba el `Int` guardado, así
 * que borrarlo lo repintaba— y **asignar una receta sin precio cerraba la app**.
 *
 * Los dos tienen la misma forma: el repositorio contestaba bien y lo que estaba mal era lo que la
 * pantalla concluía de esa respuesta.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmpleadosViewModelTest {

    private val despachador = StandardTestDispatcher()

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var recetaDao: RecetaDaoFalso
    private lateinit var empleadoDao: EmpleadoDaoFalso
    private lateinit var recetas: RecetaRepositorio
    private lateinit var empleados: EmpleadoRepositorio

    @Before
    fun prepararTodo() {
        Dispatchers.setMain(despachador)
        catalogo = IngredienteDaoFalso()
        recetaDao = RecetaDaoFalso(catalogo)
        empleadoDao = EmpleadoDaoFalso()
        val historial = HistorialRepositorio(HistorialDaoFalso())
        recetas = RecetaRepositorio(recetaDao, historial)
        empleados = EmpleadoRepositorio(empleadoDao, recetas, historial)
    }

    @After
    fun dejarTodoComoEstaba() {
        Dispatchers.resetMain()
    }

    private fun probar(cuerpo: suspend TestScope.(EmpleadosViewModel) -> Unit) =
        runTest(despachador) {
            val modelo = EmpleadosViewModel(empleados)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            backgroundScope.launch(despachador) { modelo.delEmpleado.collect { } }
            backgroundScope.launch(despachador) { modelo.dialogo.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    /** Una receta que se puede repartir: ingreso 10.000, costo 3.000 (el ejemplo de 10.1). */
    private suspend fun recetaConPrecio(titulo: String = "Torta de manjar"): Long {
        catalogo.sembrar(Ingrediente(nombre = "Harina $titulo", valorPorGramo = 1.0))
        val harina = catalogo.obtenerTodosUnaVez().first { it.nombre == "Harina $titulo" }
        val id = (recetas.crear(titulo) as ResultadoCrearReceta.Creada).recetaId
        recetas.agregarIngrediente(recetas.obtenerSecciones(id).single().id, harina.id, 3000.0)
        recetas.guardarRendimiento(id, "8", "1.000")
        recetas.crearPrecio(id, ModoPrecio.TROZO, "1", "1.250")
        return id
    }

    /** Una receta sin ningún precio: la que cerraba la app al asignarla. */
    private suspend fun recetaSinPrecio(titulo: String = "Kuchen"): Long =
        (recetas.crear(titulo) as ResultadoCrearReceta.Creada).recetaId

    private suspend fun empleadoNuevo(nombre: String = "Ana"): Long =
        (empleados.crear(nombre) as ResultadoCrearEmpleado.Creado).id

    /** Deja al empleado abierto con la receta ya asignada, que es donde vive el campo. */
    private suspend fun TestScope.conUnaRecetaAsignada(
        modelo: EmpleadosViewModel
    ): RecetaDeUnEmpleado {
        val empleadoId = empleadoNuevo()
        val recetaId = recetaConPrecio()
        empleados.guardarSueldo(empleadoId, recetaId, "1.000")
        modelo.abrir(empleadoId)
        advanceUntilIdle()
        return modelo.delEmpleado.value.recetas.single()
    }

    // --- El campo que no se podía borrar ---

    @Test
    fun `el campo de las unidades arranca mostrando lo guardado`() = probar { modelo ->
        val receta = conUnaRecetaAsignada(modelo)

        assertEquals("1", modelo.delEmpleado.value.unidadesDe(receta))
    }

    @Test
    fun `el campo se puede dejar vacio para escribir otro numero`() = probar { modelo ->
        // Era el bug: el valor salía del `Int` guardado, así que borrar el 1 no cambiaba nada y
        // el 1 volvía a aparecer. Se podía escribir delante y convertirlo en 10, pero no
        // reemplazarlo — y vaciarlo es el paso obligado para poner otro número.
        val receta = conUnaRecetaAsignada(modelo)

        modelo.cambiarUnidades(receta, "")
        advanceUntilIdle()

        assertEquals("", modelo.delEmpleado.value.unidadesDe(receta))
    }

    @Test
    fun `el campo vacio no es un error ni se guarda como cero`() = probar { modelo ->
        // Quien borra para escribir un 5 no está diciendo "cero", y guardarlo movería la
        // simulación a cero por un instante en cada corrección.
        val receta = conUnaRecetaAsignada(modelo)

        modelo.cambiarUnidades(receta, "")
        advanceUntilIdle()

        assertNull(
            "No se reta por un campo a medio cambiar",
            modelo.delEmpleado.value.errorDeUnidades(receta)
        )
        val guardado = modelo.delEmpleado.value.recetas.single().sueldo.unidadesPorDia
        assertEquals("Y lo guardado sigue siendo lo de antes", 1, guardado)
    }

    @Test
    fun `escribir un numero valido si lo guarda`() = probar { modelo ->
        val receta = conUnaRecetaAsignada(modelo)

        modelo.cambiarUnidades(receta, "5")
        advanceUntilIdle()

        assertEquals(5, modelo.delEmpleado.value.recetas.single().sueldo.unidadesPorDia)
    }

    @Test
    fun `salir del campo lo devuelve a mostrar lo guardado`() = probar { modelo ->
        // Irse dejándolo vacío no puede quedar contradiciendo a la simulación de abajo.
        val receta = conUnaRecetaAsignada(modelo)
        modelo.cambiarUnidades(receta, "")
        advanceUntilIdle()

        modelo.soltarUnidades(receta)
        advanceUntilIdle()

        assertEquals("1", modelo.delEmpleado.value.unidadesDe(receta))
    }

    @Test
    fun `un numero fuera de rango se avisa y no se guarda`() = probar { modelo ->
        val receta = conUnaRecetaAsignada(modelo)

        modelo.cambiarUnidades(receta, "9999")
        advanceUntilIdle()

        assertNotNull(modelo.delEmpleado.value.errorDeUnidades(receta))
        assertEquals(
            "Lo guardado no se toca",
            1,
            modelo.delEmpleado.value.recetas.single().sueldo.unidadesPorDia
        )
    }

    // --- La receta sin precio, que cerraba la app ---

    @Test
    fun `una receta sin precio se ofrece pero diciendo por que no se puede`() = probar { modelo ->
        // Antes se ofrecía igual y elegirla cerraba la app: el reparto pasa por
        // `precioDeMenorGanancia`, que lanza cuando no hay ningún precio. Se dice por qué en vez
        // de esconderla, porque una receta que desaparece se lee como que la app se rompió.
        empleadoNuevo().also { modelo.abrir(it) }
        recetaSinPrecio()
        advanceUntilIdle()

        modelo.abrirElegirReceta()
        advanceUntilIdle()

        val cuadro = modelo.dialogo.value as DialogoEmpleados.ElegirReceta
        val sinPrecio = cuadro.candidatas.single { it.datos.titulo == "Kuchen" }
        assertNotNull("Dice el motivo", sinPrecio.porQueNo)
        assertFalse("Y queda marcada como no elegible", sinPrecio.sePuede)
    }

    @Test
    fun `elegir una receta sin precio avisa en vez de abrir el cuadro`() = probar { modelo ->
        empleadoNuevo().also { modelo.abrir(it) }
        recetaSinPrecio()
        advanceUntilIdle()
        modelo.abrirElegirReceta()
        advanceUntilIdle()
        val cuadro = modelo.dialogo.value as DialogoEmpleados.ElegirReceta
        val sinPrecio = cuadro.candidatas.single { it.datos.titulo == "Kuchen" }

        modelo.elegirReceta(sinPrecio)
        advanceUntilIdle()

        assertNotNull("Se dice qué pasó", modelo.aviso.value)
        assertTrue(
            "Y no se abrió el cuadro del sueldo",
            modelo.dialogo.value !is DialogoEmpleados.Sueldo
        )
    }
}
