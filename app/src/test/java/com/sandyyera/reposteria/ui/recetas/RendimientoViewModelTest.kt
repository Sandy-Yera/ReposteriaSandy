package com.sandyyera.reposteria.ui.recetas

import com.sandyyera.reposteria.data.HistorialDaoFalso
import com.sandyyera.reposteria.data.IngredienteDaoFalso
import com.sandyyera.reposteria.data.RecetaDaoFalso
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.ModoReescalado
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.rendimiento.PESO_NO_ESPECIFICADO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
 * El paso "Rendimiento" visto desde la pantalla (8.3): trozos y peso.
 *
 * **Ya no prueba el molde**: eso se fue a `MoldeDeRecetaViewModelTest` cuando el molde pasó a
 * ser un paso propio (8.4.1, #2). Lo único del molde que queda acá es `usaMolde`, porque de
 * eso depende que el peso final sea opcional u obligatorio.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RendimientoViewModelTest {

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

    private fun cuadrado(lado: Double, alto: Double) = DimensionesMolde(
        tipoForma = TipoFormaMolde.CUADRADO, ladoCm = lado, alturaMoldeCm = alto
    )

    private fun probar(cuerpo: suspend TestScope.(RendimientoViewModel) -> Unit) =
        runTest(despachador) {
            catalogo.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 2.0))
            val harina = catalogo.obtenerTodosUnaVez().single()
            recetaId = (recetas.crear("Torta de manjar") as ResultadoCrearReceta.Creada).recetaId
            val seccion = recetas.obtenerSecciones(recetaId).single()
            recetas.agregarIngrediente(seccion.id, harina.id, 500.0)

            val modelo = abrirElPaso()
            cuerpo(modelo)
        }

    /**
     * Arma el ViewModel y lo deja escuchando, como haría la pantalla.
     *
     * Está aparte para poder llamarlo **dos veces** en la misma prueba: crear uno nuevo sobre
     * la misma base es lo más parecido a cerrar la app y volver a entrar, que es justo lo que
     * hay que comprobar del aviso del peso reescalado.
     */
    private fun TestScope.abrirElPaso(): RendimientoViewModel {
        val modelo = RendimientoViewModel(recetaId, recetas)
        backgroundScope.launch(despachador) { modelo.estado.collect { } }
        advanceUntilIdle()
        return modelo
    }

    // --- Los dos campos ---

    @Test
    fun `arranca con lo que ya estaba guardado`() = probar { modelo ->
        // Así guardar sin tocar nada no puede cambiar ningún número.
        assertEquals("1", modelo.estado.value.trozos)
        assertEquals("", modelo.estado.value.pesoFinal)
        assertFalse(modelo.estado.value.usaMolde)
    }

    @Test
    fun `sin molde el peso final es obligatorio y no deja guardar`() = probar { modelo ->
        modelo.cambiarTrozos("8")
        advanceUntilIdle()

        assertNotNull(modelo.estado.value.errorPesoFinal)
        assertFalse(modelo.estado.value.puedeGuardar)

        modelo.cambiarPesoFinal("1200")
        advanceUntilIdle()
        assertNull(modelo.estado.value.errorPesoFinal)
        assertTrue(modelo.estado.value.puedeGuardar)
    }

    @Test
    fun `los trozos no aceptan letras ni decimales`() = probar { modelo ->
        // Se filtran al escribir, no se avisa después: "1.000 trozos" no existe.
        modelo.cambiarTrozos("8a,5")
        advanceUntilIdle()
        assertEquals("85", modelo.estado.value.trozos)
    }

    @Test
    fun `el peso por trozo se ve en vivo, y dice No especificado si falta`() = probar { modelo ->
        assertEquals(PESO_NO_ESPECIFICADO, modelo.estado.value.pesoDeCadaTrozo)

        modelo.cambiarTrozos("8")
        modelo.cambiarPesoFinal("1200")
        advanceUntilIdle()

        assertEquals("150", modelo.estado.value.pesoDeCadaTrozo)
    }

    @Test
    fun `el punto de mil se pone solo en el peso`() = probar { modelo ->
        modelo.cambiarPesoFinal("1200")
        advanceUntilIdle()
        assertEquals("1.200", modelo.estado.value.pesoFinal)
    }

    // --- El costo de cada trozo, que es tan del rendimiento como el peso ---

    @Test
    fun `el costo de cada trozo sale de los trozos que se escriben aca`() = probar { modelo ->
        // La receta lleva 500 g de harina a $2 el gramo = $1.000.
        assertEquals(1000.0, modelo.estado.value.costoTotal, 0.001)

        modelo.cambiarTrozos("8")
        advanceUntilIdle()

        assertEquals(125.0, modelo.estado.value.costoDeCadaTrozo, 0.001)
    }

    @Test
    fun `cambiar los ingredientes desde otro paso mueve el costo por trozo`() = probar { modelo ->
        // Los ingredientes se cargan en Cantidades, no acá: el costo **se observa**.
        modelo.cambiarTrozos("4")
        advanceUntilIdle()
        assertEquals(250.0, modelo.estado.value.costoDeCadaTrozo, 0.001)

        val seccion = recetas.obtenerSecciones(recetaId).single()
        catalogo.sembrar(Ingrediente(nombre = "Azúcar", valorPorGramo = 1.0))
        val otro = catalogo.obtenerTodosUnaVez().first { it.nombre == "Azúcar" }
        recetas.agregarIngrediente(seccion.id, otro.id, 200.0)
        advanceUntilIdle()

        assertEquals(1200.0, modelo.estado.value.costoTotal, 0.001)
        assertEquals(300.0, modelo.estado.value.costoDeCadaTrozo, 0.001)
    }

    @Test
    fun `sin trozos escritos no divide por cero`() = probar { modelo ->
        // Vaciar el campo para corregirlo es normal, y `repartirEntreTrozos` lanza con 0.
        modelo.cambiarTrozos("")
        advanceUntilIdle()

        assertEquals("Cae a 1, que es con lo que se siembra la receta",
            1000.0, modelo.estado.value.costoDeCadaTrozo, 0.001)
    }

    @Test
    fun `una receta de ingredientes que valen cero cuesta cero pero no esta vacia`() =
        probar { modelo ->
            // El bug que ya costó una vez: deducir "sin ingredientes" de que el costo sea 0.
            // Un ingrediente puede valer 0 a propósito (6.2).
            val seccion = recetas.obtenerSecciones(recetaId).single()
            recetas.obtenerIngredientes(recetaId).forEach { recetas.quitarIngrediente(it.id) }
            catalogo.sembrar(Ingrediente(nombre = "Agua", valorPorGramo = 0.0))
            val agua = catalogo.obtenerTodosUnaVez().first { it.nombre == "Agua" }
            recetas.agregarIngrediente(seccion.id, agua.id, 300.0)
            advanceUntilIdle()

            assertEquals(0.0, modelo.estado.value.costoTotal, 0.001)
            assertTrue("Tiene una línea cargada, aunque no sume", modelo.estado.value.tieneIngredientes)
        }

    @Test
    fun `una receta sin ingredientes si se distingue`() = probar { modelo ->
        recetas.obtenerIngredientes(recetaId).forEach { recetas.quitarIngrediente(it.id) }
        advanceUntilIdle()

        assertEquals(0.0, modelo.estado.value.costoTotal, 0.001)
        assertFalse(modelo.estado.value.tieneIngredientes)
    }

    // --- El guardado automático (8.4.1) ---

    @Test
    fun `escribir guarda solo, sin ningun boton`() = probar { modelo ->
        modelo.cambiarTrozos("8")
        modelo.cambiarPesoFinal("1200")
        // `advanceUntilIdle` corre también la espera del guardado: en las pruebas el tiempo
        // es virtual, así que no hay que esperar medio segundo de verdad.
        advanceUntilIdle()

        val rendimiento = recetas.obtenerRendimiento(recetaId)!!
        assertEquals(8, rendimiento.trozos)
        assertEquals(1200.0, rendimiento.pesoFinalG!!, 0.001)
    }

    @Test
    fun `lo escrito a medias no borra lo que ya estaba guardado`() = probar { modelo ->
        modelo.cambiarTrozos("8")
        modelo.cambiarPesoFinal("1200")
        advanceUntilIdle()

        // Vaciar el peso para corregirlo: sin molde eso es inválido, y un guardado
        // automático que escribiera igual dejaría la receta sin peso a mitad de una
        // corrección.
        modelo.cambiarPesoFinal("")
        advanceUntilIdle()

        assertEquals(1200.0, recetas.obtenerRendimiento(recetaId)!!.pesoFinalG!!, 0.001)
    }

    @Test
    fun `el guardado espera a que se deje de escribir`() = probar { modelo ->
        // Tecleando "12" se pasa por "1", y con 1 trozo una promoción de 3 no cabría: si se
        // guardara en cada tecla, el rechazo saltaría a mitad de una palabra.
        modelo.cambiarPesoFinal("1200")
        advanceUntilIdle()

        modelo.cambiarTrozos("1")
        modelo.cambiarTrozos("12")
        advanceUntilIdle()

        assertEquals(12, recetas.obtenerRendimiento(recetaId)!!.trozos)
    }

    @Test
    fun `un rechazo del guardado sale junto al campo de los trozos`() = probar { modelo ->
        // El único rechazo posible es el de las promociones que no caben, y es sobre los
        // trozos. Va bajo el campo y no en la franja de abajo: con el teclado abierto esa
        // franja queda tapada, y sin botón que apretar llegaría en un momento que nadie
        // asocia con lo que acaba de hacer.
        modelo.cambiarPesoFinal("1200")
        modelo.cambiarTrozos("8")
        advanceUntilIdle()
        recetaDao.insertarPrecio(
            RecetaPrecio(recetaId = recetaId, modo = ModoPrecio.TROZO, cantidad = 6,
                precioTotal = 5000.0, etiqueta = "Promo 6")
        )

        modelo.cambiarTrozos("2")
        advanceUntilIdle()

        assertNotNull(modelo.estado.value.rechazoAlGuardar)
        assertEquals(
            "Y se ve donde se está escribiendo",
            modelo.estado.value.rechazoAlGuardar,
            modelo.estado.value.errorTrozos
        )
        assertNull("No por abajo", modelo.estado.value.mensaje)
        assertEquals("Sin escribir nada", 8, recetas.obtenerRendimiento(recetaId)!!.trozos)
    }

    @Test
    fun `al corregir los trozos el rechazo desaparece`() = probar { modelo ->
        modelo.cambiarPesoFinal("1200")
        modelo.cambiarTrozos("8")
        advanceUntilIdle()
        recetaDao.insertarPrecio(
            RecetaPrecio(recetaId = recetaId, modo = ModoPrecio.TROZO, cantidad = 6,
                precioTotal = 5000.0, etiqueta = "Promo 6")
        )
        modelo.cambiarTrozos("2")
        advanceUntilIdle()
        assertNotNull(modelo.estado.value.rechazoAlGuardar)

        modelo.cambiarTrozos("10")
        advanceUntilIdle()

        assertNull("El rechazo era sobre el número de antes", modelo.estado.value.rechazoAlGuardar)
        assertEquals(10, recetas.obtenerRendimiento(recetaId)!!.trozos)
    }

    @Test
    fun `guardar solo no anuncia nada por abajo`() = probar { modelo ->
        // Sin botón que apretar, un "se guardó el rendimiento" cada vez que se deja de
        // escribir es ruido puro.
        modelo.cambiarTrozos("8")
        modelo.cambiarPesoFinal("1200")
        advanceUntilIdle()

        assertNull(modelo.estado.value.mensaje)
    }

    @Test
    fun `lo escrito sobrevive a cerrar la app`() = probar { modelo ->
        // Es lo que el botón hacía creer que pasaba y no pasaba: al volver los datos seguían
        // ahí porque sobrevivía el ViewModel, no la fila.
        modelo.cambiarTrozos("8")
        modelo.cambiarPesoFinal("1200")
        advanceUntilIdle()

        val recienAbierto = abrirElPaso()
        assertEquals("8", recienAbierto.estado.value.trozos)
        assertEquals("1.200", recienAbierto.estado.value.pesoFinal)
    }

    // --- Reescalar por peso, que sigue siendo de este paso ---

    @Test
    fun `poner el molde desde el otro paso quita la opcion de reescalar por peso`() =
        probar { modelo ->
            // **Sin tocar nada de esta pantalla.** Es el bug que se vio en el celular: el
            // molde lo pone el paso anterior, y acá se seguía ofreciendo "reescalar por
            // peso" hasta que algo obligara a releer. Al tocarla saltaba el rechazo y recién
            // ahí desaparecía la opción.
            assertTrue(modelo.estado.value.sePuedeReescalarPorPeso)

            recetas.definirMolde(recetaId, cuadrado(20.0, 6.0), null)
            advanceUntilIdle()

            assertFalse(modelo.estado.value.sePuedeReescalarPorPeso)
            assertTrue(modelo.estado.value.usaMolde)
        }

    @Test
    fun `quitar el molde desde el otro paso devuelve la opcion, sin tocar nada`() =
        probar { modelo ->
            // Y al revés, que es como lo describió: quitar el molde y no ver aparecer la
            // opción hasta darle a "Guardar rendimiento".
            recetas.definirMolde(recetaId, cuadrado(20.0, 6.0), null)
            recetas.guardarRendimiento(recetaId, "8", "1.000")
            advanceUntilIdle()
            assertFalse(modelo.estado.value.sePuedeReescalarPorPeso)

            recetas.quitarMolde(recetaId)
            advanceUntilIdle()

            assertTrue(modelo.estado.value.sePuedeReescalarPorPeso)
        }

    @Test
    fun `la advertencia de la promocion se va sola al borrarla`() = probar { modelo ->
        // Se vio en el celular: bajar los trozos con una promo que no cabe deja la
        // advertencia, y borrar la promo desde el otro paso no la sacaba — el aviso quedaba
        // acusando de algo que ya no existía hasta que alguien tocara el campo.
        recetas.crearPrecio(recetaId, ModoPrecio.TROZO, "3", "3.000")
        recetas.guardarRendimiento(recetaId, "3", "1.000")
        advanceUntilIdle()

        modelo.cambiarTrozos("2")
        advanceUntilIdle()
        assertNotNull("La promo de 3 no cabe en 2 trozos", modelo.estado.value.errorTrozos)

        val laPromo = recetas.observarPrecios(recetaId).first().single()
        recetas.eliminarPrecio(laPromo.id)
        advanceUntilIdle()

        assertNull("Sin la promo ya no hay nada que impedir", modelo.estado.value.errorTrozos)
        // Y no solo se limpia el aviso: lo que se había pedido se guarda, que es lo que se
        // quería. Borrar el aviso a secas dejaría la pantalla mostrando un 2 que la base no
        // tiene.
        assertEquals(2, recetas.obtenerRendimiento(recetaId)?.trozos)
    }

    @Test
    fun `reescalar por peso multiplica los ingredientes`() = probar { modelo ->
        modelo.cambiarTrozos("1")
        modelo.cambiarPesoFinal("1000")
        advanceUntilIdle()

        modelo.abrirReescalarPorPeso()
        modelo.cambiarPesoNuevo("2000")
        modelo.confirmarReescaladoPorPeso()
        advanceUntilIdle()

        assertEquals(1000.0, recetas.obtenerIngredientes(recetaId).single().cantidadG, 0.001)
        assertEquals("Y el campo queda con el peso nuevo", "2.000", modelo.estado.value.pesoFinal)
    }

    // --- El aviso de "peso reescalado, compruébalo" (8.4.1, #4) ---

    /** Deja la receta con molde, peso anotado y ya reescalada al doble de volumen. */
    private suspend fun reescalarPorMolde() {
        recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
        recetas.guardarRendimiento(recetaId, "8", "1.000")
        recetas.reescalarPorMolde(recetaId, cuadrado(10.0, 10.0), ModoReescalado.CAPACIDAD, null)
    }

    @Test
    fun `despues de reescalar por molde el peso queda marcado para comprobar`() =
        probar { modelo ->
            reescalarPorMolde()
            val recienAbierto = abrirElPaso()

            assertTrue(recienAbierto.estado.value.pesoSinRevisar)
            assertNotNull(
                "Y la pantalla tiene el texto que mostrar",
                recienAbierto.estado.value.avisoDelPeso
            )
            // El peso se dobló junto con los ingredientes: si no, la receta diría el doble
            // de masa y el mismo peso de producto, y el peso por trozo saldría a la mitad.
            assertEquals("2.000", recienAbierto.estado.value.pesoFinal)
        }

    @Test
    fun `tocar el campo del peso apaga el aviso, aunque no se cambie el numero`() =
        probar { modelo ->
            reescalarPorMolde()
            val recienAbierto = abrirElPaso()
            assertTrue(recienAbierto.estado.value.pesoSinRevisar)

            // Es lo que hace la pantalla cuando el campo recibe el foco. No se escribe nada:
            // lo que confirma el dato es haberlo mirado.
            recienAbierto.marcarPesoRevisado()
            advanceUntilIdle()

            assertFalse(recienAbierto.estado.value.pesoSinRevisar)
            assertNull(recienAbierto.estado.value.avisoDelPeso)
            assertEquals("Y el número no se movió", "2.000", recienAbierto.estado.value.pesoFinal)
        }

    @Test
    fun `el aviso sobrevive a cerrar la app y volver a entrar`() = probar { modelo ->
        // Es la razón de que sea una columna y no un dato de la sesión: quien reescala hoy
        // pesa el producto mañana, cuando salga del horno.
        reescalarPorMolde()
        assertTrue(abrirElPaso().estado.value.pesoSinRevisar)

        // Otro ViewModel sobre la misma base = cerrar la app y volver a entrar.
        assertTrue(abrirElPaso().estado.value.pesoSinRevisar)
    }

    @Test
    fun `una vez comprobado el aviso no vuelve`() = probar { modelo ->
        reescalarPorMolde()
        val primero = abrirElPaso()
        primero.marcarPesoRevisado()
        advanceUntilIdle()

        assertFalse("Ni siquiera al volver a entrar", abrirElPaso().estado.value.pesoSinRevisar)
    }

    @Test
    fun `el peso reescalado desde el paso del molde llega al campo, sin reabrir`() =
        probar { modelo ->
            // Esto es lo que se veía como "el peso no cambió": la base sí lo doblaba, pero
            // el campo seguía mostrando el número viejo porque se sembraba una sola vez al
            // crear el ViewModel.
            recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
            recetas.guardarRendimiento(recetaId, "8", "1.000")
            advanceUntilIdle()
            assertEquals("1.000", modelo.estado.value.pesoFinal)

            recetas.reescalarPorMolde(
                recetaId, cuadrado(10.0, 10.0), ModoReescalado.CAPACIDAD, null
            )
            advanceUntilIdle()

            assertEquals("2.000", modelo.estado.value.pesoFinal)
            assertTrue(modelo.estado.value.pesoSinRevisar)
        }

    @Test
    fun `guardar despues de un reescalado no devuelve el peso viejo`() = probar { modelo ->
        // El daño de que el campo se quedara viejo no era solo verlo mal: al guardar desde
        // esta pantalla se escribía el número viejo encima del recalculado. Se notaba al
        // cerrar y volver a abrir la app, con el peso cambiado sin que nadie lo tocara.
        recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
        recetas.guardarRendimiento(recetaId, "8", "1.000")
        advanceUntilIdle()

        recetas.reescalarPorMolde(recetaId, cuadrado(10.0, 10.0), ModoReescalado.CAPACIDAD, null)
        advanceUntilIdle()

        // Y aunque se escriba otra cosa después, se guarda eso y no el número viejo.
        modelo.cambiarTrozos("4")
        advanceUntilIdle()

        assertEquals(2000.0, recetas.obtenerRendimiento(recetaId)!!.pesoFinalG!!, 0.001)
    }

    @Test
    fun `lo que se esta escribiendo no se pierde por una escritura ajena`() = probar { modelo ->
        // La contracara del arreglo anterior: los campos siguen a la base, pero solo cuando
        // **lo guardado** cambia. `marcarPesoRevisado` escribe en la fila del rendimiento sin
        // tocar el peso, y no puede llevarse por delante lo tecleado a medias.
        recetas.definirMolde(recetaId, cuadrado(10.0, 5.0), null)
        recetas.guardarRendimiento(recetaId, "8", "1.000")
        recetas.reescalarPorMolde(recetaId, cuadrado(10.0, 10.0), ModoReescalado.CAPACIDAD, null)
        advanceUntilIdle()

        modelo.cambiarPesoFinal("1850")
        advanceUntilIdle()

        assertEquals("1.850", modelo.estado.value.pesoFinal)
    }

    @Test
    fun `tocar el campo y escribir enseguida no borra lo tecleado`() = probar { modelo ->
        // El bug: `marcarPesoRevisado` escribe en la fila del rendimiento, y
        // `observarRendimiento` reemite ante cualquier escritura — también ante una que solo
        // mueve un booleano. Cuando esa emisión volvía, lo tecleado ya era distinto de lo
        // guardado y el campo se re-sembraba con el número viejo, bajo el dedo.
        //
        // En el celular es la secuencia normal: tocar el campo para corregir el peso
        // reescalado y empezar a escribir sin esperar.
        reescalarPorMolde()
        val recienAbierto = abrirElPaso()
        assertTrue(recienAbierto.estado.value.pesoSinRevisar)

        recienAbierto.marcarPesoRevisado()
        recienAbierto.cambiarPesoFinal("1950")
        advanceUntilIdle()

        assertEquals("1.950", recienAbierto.estado.value.pesoFinal)
        assertEquals(1950.0, recetas.obtenerRendimiento(recetaId)!!.pesoFinalG!!, 0.001)
    }

    @Test
    fun `escribir en trozos tampoco se pierde por una escritura ajena`() = probar { modelo ->
        modelo.cambiarPesoFinal("1200")
        advanceUntilIdle()

        // Se teclea, y antes de que el guardado ocurra llega otra escritura a la misma fila.
        modelo.cambiarTrozos("8")
        recetas.marcarPesoRevisado(recetaId)
        advanceUntilIdle()

        assertEquals("8", modelo.estado.value.trozos)
        assertEquals(8, recetas.obtenerRendimiento(recetaId)!!.trozos)
    }

    @Test
    fun `escribir en el campo tambien apaga el aviso`() = probar { modelo ->
        // El foco es el camino normal, pero se puede llegar al campo con el "siguiente" del
        // teclado desde los trozos y escribir sin haberlo tocado con el dedo.
        reescalarPorMolde()
        val recienAbierto = abrirElPaso()

        recienAbierto.cambiarPesoFinal("1950")
        advanceUntilIdle()

        assertFalse(recienAbierto.estado.value.pesoSinRevisar)
    }
}
