package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.precioDeReferencia
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
 * El paso "Gastos y Ganancias" visto desde la pantalla (8.5 y 8.6).
 *
 * Los números de las pruebas son **los ejemplos de la arquitectura**, no inventados: costo
 * 1.400 en 6 trozos con precio 500 da trozo ganador 3 y ganancia 100 ahí; con la promo de
 * "2 trozos por $1.500" el trozo ganador pasa a 2, también con ganancia 100. Que sean esos
 * es lo que permite comprobar que la pantalla no está haciendo su propia cuenta por un lado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GastosViewModelTest {

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

    /**
     * Arma la receta del ejemplo: cuesta 1.400 y rinde 6 trozos.
     *
     * El costo sale de un ingrediente de verdad y no de un número puesto a mano, porque una
     * de las cosas que hay que probar es justamente que este paso lo lee de allá.
     */
    private fun probar(cuerpo: suspend TestScope.(GastosViewModel) -> Unit) =
        runTest(despachador) {
            catalogo.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1.4))
            val harina = catalogo.obtenerTodosUnaVez().single()
            recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId
            val seccion = recetas.obtenerSecciones(recetaId).single()
            recetas.agregarIngrediente(seccion.id, harina.id, 1000.0)   // 1.000 g x 1,4 = 1.400
            recetas.guardarRendimiento(recetaId, "6", "1.000")

            val modelo = GastosViewModel(recetaId, recetas)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            advanceUntilIdle()
            cuerpo(modelo)
        }

    private fun formulario(modelo: GastosViewModel) =
        modelo.dialogo.value as DialogoGastos.Formulario

    private suspend fun TestScope.ponerPrecio(
        modelo: GastosViewModel,
        cantidad: String,
        total: String,
        modo: ModoPrecio = ModoPrecio.TROZO,
        etiqueta: String = ""
    ) {
        modelo.abrirPrecioNuevo()
        modelo.elegirModo(modo)
        modelo.cambiarCantidad(cantidad)
        modelo.cambiarPrecioTotal(total)
        if (etiqueta.isNotEmpty()) modelo.cambiarEtiqueta(etiqueta)
        modelo.guardarPrecio()
        advanceUntilIdle()
    }

    // --- Estado inicial ---

    @Test
    fun `sin precios muestra el costo pero ninguna ganancia`() = probar { modelo ->
        val estado = modelo.estado.value

        assertEquals(1400.0, estado.costoTotal, 0.001)
        assertEquals("Cada trozo cuesta 1.400 / 6", 233.33, estado.costoDeCadaTrozo, 0.01)
        assertFalse(estado.tienePrecio)
        // Ninguna cifra en 0: mostrar 0 diría que se vende y no deja nada, que es otra cosa.
        assertNull(estado.ingresoDelProducto)
        assertNull(estado.gananciaDeCadaTrozo)
        assertNull(estado.gananciaDelProducto)
        assertNull(estado.elTrozoGanador)
    }

    // --- Los ejemplos de la arquitectura ---

    @Test
    fun `el ejemplo base da trozo ganador 3 con ganancia 100`() = probar { modelo ->
        ponerPrecio(modelo, cantidad = "1", total = "500")

        val estado = modelo.estado.value
        val ganador = estado.elTrozoGanador!!
        assertEquals(3, ganador.numero)
        assertEquals(100.0, ganador.ganancia, 0.001)
        assertTrue("Con 6 trozos, el tercero existe", ganador.alcanzable)
        assertEquals(3000.0, estado.ingresoDelProducto!!, 0.001)   // 500 x 6
        assertEquals(1600.0, estado.gananciaDelProducto!!, 0.001)  // 3.000 - 1.400
    }

    @Test
    fun `con la promo de dos por mil quinientos el trozo ganador pasa a 2`() = probar { modelo ->
        // El precio por trozo sube de 500 a 750, así que el costo se cubre antes.
        ponerPrecio(modelo, cantidad = "2", total = "1.500", etiqueta = "2x1.500")
        modelo.elegirReferencia(modelo.estado.value.filas.single().precio)
        advanceUntilIdle()

        val ganador = modelo.estado.value.elTrozoGanador!!
        assertEquals(2, ganador.numero)
        assertEquals(100.0, ganador.ganancia, 0.001)
        assertTrue(ganador.alcanzable)
    }

    @Test
    fun `una receta que se vende bajo su costo avisa en vez de dar un trozo imposible`() =
        probar { modelo ->
            // 8 trozos harían falta y la receta rinde 6: el número existe pero es mentira.
            ponerPrecio(modelo, cantidad = "1", total = "200")

            val estado = modelo.estado.value
            assertFalse("Cae fuera de la receta", estado.elTrozoGanador!!.alcanzable)
            assertTrue("Y por eso hay que avisarlo", estado.laReferenciaPierdePlata)
        }

    // --- La referencia ---

    @Test
    fun `agregar una promo que deja mas no cambia las cifras hasta elegirla`() = probar { modelo ->
        ponerPrecio(modelo, cantidad = "1", total = "500")
        val conSoloElBase = modelo.estado.value.gananciaDelProducto

        ponerPrecio(modelo, cantidad = "2", total = "1.500", etiqueta = "2x1.500")

        // Sin elegir nada manda la de MENOR ganancia, que sigue siendo la de 500.
        assertEquals(conSoloElBase!!, modelo.estado.value.gananciaDelProducto!!, 0.001)
        assertFalse(modelo.estado.value.referenciaElegidaAMano)
    }

    @Test
    fun `y elegirla cambia las cifras en el momento`() = probar { modelo ->
        ponerPrecio(modelo, cantidad = "1", total = "500")
        ponerPrecio(modelo, cantidad = "2", total = "1.500", etiqueta = "2x1.500")
        val laPromo = modelo.estado.value.filas.first { it.comoSeLlama == "2x1.500" }

        modelo.elegirReferencia(laPromo.precio)
        advanceUntilIdle()

        val estado = modelo.estado.value
        assertTrue(estado.referenciaElegidaAMano)
        assertEquals(4500.0, estado.ingresoDelProducto!!, 0.001)   // 750 x 6
        assertTrue(estado.filas.first { it.comoSeLlama == "2x1.500" }.esReferencia)
    }

    @Test
    fun `una promo que pierde plata no se puede elegir como referencia`() = probar { modelo ->
        ponerPrecio(modelo, cantidad = "1", total = "500")
        ponerPrecio(modelo, cantidad = "6", total = "600", etiqueta = "Liquidación")
        // Hay que elegir la buena primero: sin nadie elegido manda la de MENOR ganancia, que
        // es precisamente la mala (ver la prueba de abajo).
        modelo.elegirReferencia(modelo.estado.value.filas.first { it.precio.cantidad == 1 }.precio)
        advanceUntilIdle()
        val laMala = modelo.estado.value.filas.first { it.comoSeLlama == "Liquidación" }
        assertTrue("Esta pierde plata", laMala.pierdePlata)

        modelo.elegirReferencia(laMala.precio)
        advanceUntilIdle()

        assertNotNull("Se avisa el motivo", modelo.estado.value.mensaje)
        assertFalse(
            "Y la referencia queda como estaba",
            modelo.estado.value.filas.first { it.comoSeLlama == "Liquidación" }.esReferencia
        )
    }

    @Test
    fun `el respaldo si puede caer en una que pierde, y por eso hay que avisarlo`() =
        probar { modelo ->
            // Es la grieta de 8.6, y no se puede tapar sin contradecirse: `precioDeMenorGanancia`
            // elige a propósito la que menos deja, o sea la MÁS probable de estar en pérdida,
            // mientras que `errorAlElegirReferencia` prohíbe elegir esa misma a mano. Con dos
            // precios guardados y ninguno elegido, la que manda es la mala.
            //
            // No se arregla prohibiendo: al mismo estado se llega sin tocar los precios, con
            // que suba un ingrediente. Lo que corresponde es **decirlo**, y eso es
            // `laReferenciaPierdePlata`.
            ponerPrecio(modelo, cantidad = "1", total = "500")
            ponerPrecio(modelo, cantidad = "6", total = "600", etiqueta = "Liquidación")

            val estado = modelo.estado.value
            assertFalse("Nadie eligió nada", estado.referenciaElegidaAMano)
            assertTrue(
                "Y sin embargo manda la que pierde",
                estado.filas.first { it.comoSeLlama == "Liquidación" }.esReferencia
            )
            assertTrue("Por eso la pantalla lo avisa", estado.laReferenciaPierdePlata)
        }

    @Test
    fun `pero igual se guarda y se ve, con su ganancia en negativo`() = probar { modelo ->
        // Esa es justamente la información que hace falta para descartarla (8.6).
        ponerPrecio(modelo, cantidad = "6", total = "600", etiqueta = "Liquidación")

        val fila = modelo.estado.value.filas.single()
        assertTrue(fila.pierdePlata)
        assertTrue(fila.gananciaPorTrozo < 0)
    }

    @Test
    fun `la fila marcada es la que de verdad manda, no la que tiene la columna`() =
        probar { modelo ->
            // Sin nadie elegido, la marcada tiene que ser la de menor ganancia y no ninguna.
            ponerPrecio(modelo, cantidad = "1", total = "500")
            ponerPrecio(modelo, cantidad = "2", total = "1.500")

            val marcadas = modelo.estado.value.filas.filter { it.esReferencia }
            assertEquals("Exactamente una", 1, marcadas.size)
            assertEquals("Y es la de 500", 500.0, marcadas.single().precio.precioTotal, 0.001)
        }

    @Test
    fun `dos promociones identicas no se marcan las dos`() = probar { modelo ->
        // `PrecioVigente` no lleva id y es un `data class`: comparadas por valor, dos promos
        // iguales -un "2 por 1.500" cargado dos veces, error corriente- son la misma. Por eso
        // la referencia se resuelve por id.
        ponerPrecio(modelo, cantidad = "2", total = "1.500")
        ponerPrecio(modelo, cantidad = "2", total = "1.500")

        assertEquals(2, modelo.estado.value.filas.size)
        assertEquals(1, modelo.estado.value.filas.count { it.esReferencia })
    }

    @Test
    fun `la fila marcada coincide con lo que dice la logica pura`() = probar { modelo ->
        // Las dos reglas viven separadas por obligación —`precioDeReferencia` no puede
        // devolver un id— así que hay que comprobar que no se separen de verdad.
        ponerPrecio(modelo, cantidad = "1", total = "500")
        ponerPrecio(modelo, cantidad = "2", total = "1.500")
        ponerPrecio(modelo, cantidad = "3", total = "2.400")

        val estado = modelo.estado.value
        val segunLaLogica = precioDeReferencia(estado.datos!!)
        val segunLaPantalla = estado.filas.single { it.esReferencia }

        assertEquals(segunLaLogica.precioTotal, segunLaPantalla.precio.precioTotal, 0.001)
        assertEquals(segunLaLogica.cantidad, segunLaPantalla.precio.cantidad)
    }

    // --- El resto de una promoción que no divide exacto (8.6.1) ---

    @Test
    fun `una promo que deja un trozo suelto lo dice y lo cobra al precio individual`() =
        probar { modelo ->
            // La receta rinde 6. Con una promo de 4 sobran 2, que no se venden a precio de
            // promoción: se venden sueltos. Antes la app multiplicaba y mostraba de menos.
            ponerPrecio(modelo, cantidad = "1", total = "1.000")
            ponerPrecio(modelo, cantidad = "4", total = "2.000")
            val promo = modelo.estado.value.filas.first { it.precio.cantidad == 4 }
            modelo.elegirReferencia(promo.precio)
            advanceUntilIdle()

            val estado = modelo.estado.value
            assertEquals(2, estado.reparto!!.sueltos)
            assertNotNull("Se avisa mientras se aplica la regla", estado.avisoDelResto)
            assertTrue(estado.avisoDelResto!!.contains("2 trozos sueltos"))
            // 2.000 de la promo + 2 x 1.000 de los sueltos.
            assertEquals(4000.0, estado.ingresoDelProducto!!, 0.001)
        }

    @Test
    fun `sin resto no hay nada que avisar`() = probar { modelo ->
        ponerPrecio(modelo, cantidad = "1", total = "1.000")
        ponerPrecio(modelo, cantidad = "3", total = "2.400")
        modelo.elegirReferencia(modelo.estado.value.filas.first { it.precio.cantidad == 3 }.precio)
        advanceUntilIdle()

        // 6 trozos entre promos de 3: entra dos veces justas.
        assertEquals(0, modelo.estado.value.reparto!!.sueltos)
        assertNull(modelo.estado.value.avisoDelResto)
        assertEquals(4800.0, modelo.estado.value.ingresoDelProducto!!, 0.001)
    }

    @Test
    fun `si falta el precio individual lo dice en vez de mostrar un total corto`() =
        probar { modelo ->
            ponerPrecio(modelo, cantidad = "4", total = "2.000")

            val estado = modelo.estado.value
            assertTrue(estado.reparto!!.faltaElPrecioSuelto)
            assertTrue(estado.avisoDelResto!!.contains("precio individual"))
        }

    @Test
    fun `la pantalla pide los dos precios base y sabe cuales faltan`() = probar { modelo ->
        assertEquals(
            listOf(ModoPrecio.TROZO, ModoPrecio.PRODUCTO),
            modelo.estado.value.basesQueFaltan
        )

        ponerPrecio(modelo, cantidad = "1", total = "1.000")

        assertEquals(listOf(ModoPrecio.PRODUCTO), modelo.estado.value.basesQueFaltan)

        ponerPrecio(modelo, cantidad = "1", total = "5.500", modo = ModoPrecio.PRODUCTO)

        assertTrue(modelo.estado.value.basesQueFaltan.isEmpty())
    }

    @Test
    fun `las promociones se distinguen de los precios base`() = probar { modelo ->
        // La pantalla las dibuja en dos listas: los base sostienen a las promociones.
        ponerPrecio(modelo, cantidad = "1", total = "1.000")
        ponerPrecio(modelo, cantidad = "2", total = "1.800")

        val filas = modelo.estado.value.filas
        assertTrue(filas.first { it.precio.cantidad == 1 }.esBase)
        assertFalse(filas.first { it.precio.cantidad == 2 }.esBase)
    }

    // --- Editar y borrar ---

    @Test
    fun `editar un precio no le quita la referencia`() = probar { modelo ->
        ponerPrecio(modelo, cantidad = "1", total = "500")
        ponerPrecio(modelo, cantidad = "2", total = "1.500")
        val laPromo = modelo.estado.value.filas.first { it.precio.cantidad == 2 }
        modelo.elegirReferencia(laPromo.precio)
        advanceUntilIdle()

        modelo.abrirEditarPrecio(laPromo.precio)
        modelo.cambiarPrecioTotal("1.800")
        modelo.guardarPrecio()
        advanceUntilIdle()

        val despues = modelo.estado.value.filas.first { it.precio.cantidad == 2 }
        assertEquals(1800.0, despues.precio.precioTotal, 0.001)
        assertTrue("Editar un precio no cambia cuál manda", despues.esReferencia)
    }

    @Test
    fun `abrir el editor y guardar sin tocar nada no altera el numero`() = probar { modelo ->
        // El precio vuelve al campo con el formato de la app, que es el que `textoANumero`
        // lee de vuelta. Si no calzaran, abrir y cerrar cambiaría el precio solo.
        ponerPrecio(modelo, cantidad = "1", total = "1.234,56")
        val antes = modelo.estado.value.filas.single().precio

        modelo.abrirEditarPrecio(antes)
        modelo.guardarPrecio()
        advanceUntilIdle()

        assertEquals(antes.precioTotal, modelo.estado.value.filas.single().precio.precioTotal, 0.001)
    }

    @Test
    fun `borrar el precio que mandaba deja mandando al que queda`() = probar { modelo ->
        ponerPrecio(modelo, cantidad = "1", total = "500")
        ponerPrecio(modelo, cantidad = "2", total = "1.500")
        val elBase = modelo.estado.value.filas.first { it.precio.cantidad == 1 }

        modelo.pedirBorrado(elBase)
        modelo.confirmarBorrado()
        advanceUntilIdle()

        val filas = modelo.estado.value.filas
        assertEquals(1, filas.size)
        assertTrue("El que queda pasa a mandar", filas.single().esReferencia)
        assertEquals(4500.0, modelo.estado.value.ingresoDelProducto!!, 0.001)
    }

    @Test
    fun `borrar el ultimo deja la receta como si nunca hubiera pasado por el paso`() =
        probar { modelo ->
            ponerPrecio(modelo, cantidad = "1", total = "500")

            modelo.pedirBorrado(modelo.estado.value.filas.single())
            modelo.confirmarBorrado()
            advanceUntilIdle()

            assertFalse(modelo.estado.value.tienePrecio)
            assertNull(modelo.estado.value.gananciaDelProducto)
        }

    // --- El formulario ---

    @Test
    fun `no deja guardar un precio en cero`() = probar { modelo ->
        modelo.abrirPrecioNuevo()
        modelo.cambiarCantidad("1")
        modelo.cambiarPrecioTotal("0")

        assertFalse(formulario(modelo).puedeGuardar)
        assertNotNull(formulario(modelo).errorPrecioTotal)
    }

    @Test
    fun `no deja una promo de mas trozos de los que rinde la receta`() = probar { modelo ->
        // El tope del último trozo (6.2): la receta rinde 6.
        modelo.abrirPrecioNuevo()
        modelo.cambiarCantidad("8")
        modelo.cambiarPrecioTotal("4.000")

        val cuadro = formulario(modelo)
        assertFalse(cuadro.puedeGuardar)
        assertTrue("El aviso dice cuántos rinde", cuadro.errorCantidad!!.contains("6"))
    }

    @Test
    fun `en modo producto ese tope no aplica`() = probar { modelo ->
        // Vender 8 productos completos es posible por más que cada uno rinda 6.
        modelo.abrirPrecioNuevo()
        modelo.elegirModo(ModoPrecio.PRODUCTO)
        modelo.cambiarCantidad("8")
        modelo.cambiarPrecioTotal("24.000")

        assertTrue(formulario(modelo).puedeGuardar)
    }

    @Test
    fun `el cuadro recien abierto no muestra errores todavia`() = probar { modelo ->
        modelo.abrirPrecioNuevo()

        val cuadro = formulario(modelo)
        assertNull("Nadie escribió nada aún", cuadro.errorPrecioTotal)
        assertFalse("Pero tampoco se puede guardar", cuadro.puedeGuardar)
    }

    @Test
    fun `el rechazo del repositorio queda dentro del cuadro`() = probar { modelo ->
        // Con el teclado abierto, la franja de abajo no se ve (8.2).
        modelo.abrirPrecioNuevo()
        modelo.cambiarCantidad("1")
        modelo.cambiarPrecioTotal("500")
        // Se le cambia los trozos por debajo para que el repositorio rechace lo que la
        // pantalla creía válido: es el caso que obliga a tener las dos comprobaciones.
        recetas.guardarRendimiento(recetaId, "1", "1.000")
        advanceUntilIdle()
        modelo.cambiarCantidad("4")
        modelo.guardarPrecio()
        advanceUntilIdle()

        assertTrue("El cuadro sigue abierto", modelo.dialogo.value is DialogoGastos.Formulario)
        assertNotNull(formulario(modelo).rechazo)
    }

    // --- Lo que escriben los otros pasos ---

    @Test
    fun `cambiar los ingredientes cambia las ganancias sin tocar este paso`() = probar { modelo ->
        ponerPrecio(modelo, cantidad = "1", total = "500")
        val antes = modelo.estado.value.gananciaDelProducto!!

        catalogo.sembrar(Ingrediente(nombre = "Manjar", valorPorGramo = 4.0))
        val manjar = catalogo.obtenerTodosUnaVez().first { it.nombre == "Manjar" }
        recetas.agregarIngrediente(recetas.obtenerSecciones(recetaId).single().id, manjar.id, 100.0)
        advanceUntilIdle()

        // El costo subió 400, así que la ganancia del producto baja exactamente eso.
        assertEquals(antes - 400.0, modelo.estado.value.gananciaDelProducto!!, 0.001)
    }

    @Test
    fun `cambiar los trozos cambia el costo por trozo y el trozo ganador`() = probar { modelo ->
        ponerPrecio(modelo, cantidad = "1", total = "500")
        assertEquals(3, modelo.estado.value.elTrozoGanador!!.numero)

        recetas.guardarRendimiento(recetaId, "12", "1.000")
        advanceUntilIdle()

        val estado = modelo.estado.value
        assertEquals("1.400 / 12", 116.67, estado.costoDeCadaTrozo, 0.01)
        assertEquals("El trozo ganador no se movió: depende del costo total", 3,
            estado.elTrozoGanador!!.numero)
        assertEquals("Pero el ingreso sí: 500 x 12", 6000.0, estado.ingresoDelProducto!!, 0.001)
    }

    @Test
    fun `una receta sin ingredientes lo dice, en vez de mostrar ganancias de mentira`() =
        runTest(despachador) {
            // La lección que ya costó un bug: costo 0 no es lo mismo que receta vacía.
            recetaId = (recetas.crear("Salsa") as ResultadoCrearReceta.Creada).recetaId
            val modelo = GastosViewModel(recetaId, recetas)
            backgroundScope.launch(despachador) { modelo.estado.collect { } }
            advanceUntilIdle()

            assertFalse(modelo.estado.value.tieneIngredientes)
            assertEquals(0.0, modelo.estado.value.costoTotal, 0.001)
        }
}
