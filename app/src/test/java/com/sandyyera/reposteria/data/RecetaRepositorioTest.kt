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
import org.junit.Assert.assertFalse
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

    // --- Ingredientes repetidos dentro de una sección ---

    @Test
    fun `el mismo ingrediente no entra dos veces en la misma seccion`() = runBlocking {
        // Se vio en el celular: la misma sección aceptaba "Harina" dos veces. Dos filas del
        // mismo ingrediente no son un dato — son una cantidad partida en dos que se suma
        // bien y se lee mal, así que el costo cuadra mientras la lista miente.
        val id = crearReceta()
        val seccion = repositorio.obtenerSecciones(id).single().id
        val harina = ingrediente("Harina", 1.0)
        repositorio.agregarIngrediente(seccion, harina, 500.0)

        val segundoIntento = repositorio.agregarIngrediente(seccion, harina, 200.0)

        assertTrue(segundoIntento is Resultado.NoSePudo)
        assertEquals(1, repositorio.obtenerIngredientes(id).size)
        assertEquals(500.0, repositorio.obtenerIngredientes(id).single().cantidadG, 0.001)
    }

    @Test
    fun `el aviso nombra el ingrediente y la cantidad que ya tiene`() = runBlocking {
        // Sin esos dos datos el aviso obliga a salir a mirar cuál era y cuánto llevaba.
        val id = crearReceta()
        val seccion = repositorio.obtenerSecciones(id).single().id
        val harina = ingrediente("Harina", 1.0)
        repositorio.agregarIngrediente(seccion, harina, 500.0)

        val motivo = (repositorio.agregarIngrediente(seccion, harina, 200.0)
            as Resultado.NoSePudo).motivo

        assertTrue("Nombra el ingrediente", motivo.contains("Harina"))
        assertTrue("Y dice cuánto lleva", motivo.contains("500"))
    }

    @Test
    fun `en dos secciones distintas si se puede repetir`() = runBlocking {
        // Almendra en el bizcocho y almendra en la decoración es correcto y corriente. Por
        // eso la comprobación es por sección y nunca por receta.
        val id = crearReceta()
        val primera = repositorio.obtenerSecciones(id).single().id
        repositorio.agregarSeccion(id, "Decoración", "Bizcocho")
        val decoracion = repositorio.obtenerSecciones(id).first { it.nombreSeccion == "Decoración" }
        val almendra = ingrediente("Almendra", 8.0)
        repositorio.agregarIngrediente(primera, almendra, 100.0)

        val enLaOtra = repositorio.agregarIngrediente(decoracion.id, almendra, 30.0)

        assertTrue(enLaOtra is Resultado.Listo)
        assertEquals(2, repositorio.obtenerIngredientes(id).size)
    }

    // --- Precios (8.6) ---

    private suspend fun recetaConCosto(): Long {
        val id = crearReceta()
        repositorio.agregarIngrediente(
            repositorio.obtenerSecciones(id).single().id, ingrediente("Harina", 1.4), 1000.0
        )
        repositorio.guardarRendimiento(id, "6", "1.000")
        return id
    }

    @Test
    fun `crear un precio lo deja guardado y anotado`() = runBlocking {
        val id = recetaConCosto()

        val r = repositorio.crearPrecio(id, ModoPrecio.TROZO, "1", "500")

        assertTrue(r is Resultado.Listo)
        val guardado = repositorio.observarPrecios(id).first().single()
        assertEquals(500.0, guardado.precioTotal, 0.001)
        assertEquals(1, guardado.cantidad)
        assertEquals(TipoEvento.CREACION, historial.eventos.last().tipo)
    }

    @Test
    fun `el primero no queda marcado como referencia, y no hace falta`() = runBlocking {
        // `precioDeReferencia` cae solo en el de menor ganancia cuando nadie eligió, así que
        // con un precio único ese manda igual. Marcarlo diría que alguien lo decidió, y la
        // pantalla usa esa diferencia para distinguir "lo elegiste tú" del respaldo.
        val id = recetaConCosto()

        repositorio.crearPrecio(id, ModoPrecio.TROZO, "1", "500")

        assertFalse(repositorio.observarPrecios(id).first().single().esReferencia)
    }

    @Test
    fun `no se puede crear una promo de mas trozos de los que rinde`() = runBlocking {
        val id = recetaConCosto()   // rinde 6
        ponerLasBases(id)

        val r = repositorio.crearPrecio(id, ModoPrecio.TROZO, "8", "4.000")

        assertTrue(r is Resultado.NoSePudo)
        assertTrue(repositorio.observarPrecios(id).first().none { it.cantidad == 8 })
    }

    // --- Los dos precios base van primero (8.6.1) ---

    /** Deja puestos los dos precios base, que es lo que toda promoción necesita debajo. */
    private suspend fun ponerLasBases(id: Long) {
        repositorio.crearPrecio(id, ModoPrecio.TROZO, "1", "500")
        repositorio.crearPrecio(id, ModoPrecio.PRODUCTO, "1", "3.000")
    }

    @Test
    fun `sin los precios base no se puede guardar una promocion`() = runBlocking {
        // Lo pidió Sandy: la app la dejaba empezar por "2 trozos por $20.000" sin haber dicho
        // nunca cuánto vale un trozo, y esa promoción no tiene con qué cobrar el suelto.
        val id = recetaConCosto()

        val r = repositorio.crearPrecio(id, ModoPrecio.TROZO, "2", "900")

        assertTrue(r is Resultado.NoSePudo)
        assertTrue(repositorio.observarPrecios(id).first().isEmpty())
    }

    @Test
    fun `la comprobacion vive en el repositorio y no solo en la pantalla`() = runBlocking {
        // Con solo el del trozo puesto, la promoción todavía no pasa: falta el del producto.
        val id = recetaConCosto()
        repositorio.crearPrecio(id, ModoPrecio.TROZO, "1", "500")

        assertTrue(repositorio.crearPrecio(id, ModoPrecio.TROZO, "2", "900") is Resultado.NoSePudo)

        repositorio.crearPrecio(id, ModoPrecio.PRODUCTO, "1", "3.000")
        assertTrue(repositorio.crearPrecio(id, ModoPrecio.TROZO, "2", "900") is Resultado.Listo)
    }

    @Test
    fun `no se puede tener dos veces el mismo precio base`() = runBlocking {
        // `precioBasePorTrozo` se queda con el primero que encuentra, así que el segundo
        // quedaría guardado sin alimentar nada — y en la lista los dos se ven casi iguales.
        val id = recetaConCosto()
        repositorio.crearPrecio(id, ModoPrecio.TROZO, "1", "500")

        val r = repositorio.crearPrecio(id, ModoPrecio.TROZO, "1", "700")

        assertTrue(r is Resultado.NoSePudo)
        assertEquals(1, repositorio.observarPrecios(id).first().size)
    }

    @Test
    fun `los dos base son de modos distintos y no chocan entre si`() = runBlocking {
        val id = recetaConCosto()
        repositorio.crearPrecio(id, ModoPrecio.TROZO, "1", "500")

        val r = repositorio.crearPrecio(id, ModoPrecio.PRODUCTO, "1", "3.000")

        assertTrue(r is Resultado.Listo)
        assertEquals(2, repositorio.observarPrecios(id).first().size)
    }

    @Test
    fun `editar un precio conserva cual es la referencia`() = runBlocking {
        // Escribir la fila entera sin este cuidado apagaría la referencia en silencio.
        val id = recetaConCosto()
        ponerLasBases(id)
        repositorio.crearPrecio(id, ModoPrecio.TROZO, "2", "1.500")
        val laPromo = repositorio.observarPrecios(id).first().first { it.cantidad == 2 }
        repositorio.elegirPrecioDeReferencia(id, laPromo.id)

        repositorio.editarPrecio(laPromo.id, ModoPrecio.TROZO, "2", "1.800")

        val despues = repositorio.observarPrecios(id).first().first { it.cantidad == 2 }
        assertEquals(1800.0, despues.precioTotal, 0.001)
        assertTrue(despues.esReferencia)
    }

    @Test
    fun `se puede dejar la referencia perdiendo plata, y es a proposito`() = runBlocking {
        // `errorAlElegirReferencia` protege el acto de ELEGIR una promo que pierde, que es una
        // decisión. Bajarle el precio a la que ya manda es otra cosa, y bloquearlo sería una
        // regla que no se sostiene: al mismo estado se llega sin tocar precios, con que suba
        // el costo de un ingrediente en otra pantalla. Lo que corresponde es avisarlo.
        val id = recetaConCosto()
        repositorio.crearPrecio(id, ModoPrecio.TROZO, "1", "500")
        val elUnico = repositorio.observarPrecios(id).first().single()

        val r = repositorio.editarPrecio(elUnico.id, ModoPrecio.TROZO, "1", "50")

        assertTrue(r is Resultado.Listo)
        assertEquals(50.0, repositorio.observarPrecios(id).first().single().precioTotal, 0.001)
    }

    @Test
    fun `borrar un precio lo nombra en el historial antes de que desaparezca`() = runBlocking {
        val id = recetaConCosto()
        ponerLasBases(id)
        repositorio.crearPrecio(id, ModoPrecio.TROZO, "2", "1.500", etiqueta = "Promo sábado")
        val elPrecio = repositorio.observarPrecios(id).first().first { it.cantidad == 2 }

        repositorio.eliminarPrecio(elPrecio.id)

        assertTrue(repositorio.observarPrecios(id).first().none { it.cantidad == 2 })
        val evento = historial.eventos.last()
        assertEquals(TipoEvento.ELIMINACION, evento.tipo)
        assertTrue("Lo nombra", evento.descripcion.contains("Promo sábado"))
    }

    @Test
    fun `el snapshot observado se entera de lo que escriben los otros pasos`() = runBlocking {
        val id = recetaConCosto()
        repositorio.crearPrecio(id, ModoPrecio.TROZO, "1", "500")
        assertEquals(1400.0, repositorio.observarDatosCalculo(id).first()!!.costoTotal, 0.001)

        repositorio.agregarIngrediente(
            repositorio.obtenerSecciones(id).single().id, ingrediente("Manjar", 4.0), 100.0
        )

        val despues = repositorio.observarDatosCalculo(id).first()!!
        assertEquals("Subió 400", 1800.0, despues.costoTotal, 0.001)
        assertEquals(6, despues.trozos)
        assertEquals(1, despues.precios.size)
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

    // --- El costo de UNA receta observado (rendimiento) ---

    @Test
    fun `observarCosto da lo mismo que costoTotal`() = runBlocking {
        // Son la misma consulta, una de una vez y la otra colgada de un `Flow`. Si se
        // separaran, la pantalla mostraría un costo y los cálculos usarían otro.
        val id = crearReceta()
        val seccion = repositorio.obtenerSecciones(id).single().id
        repositorio.agregarIngrediente(seccion, ingrediente("Harina", 1.2), 500.0)

        assertEquals(repositorio.costoTotal(id), repositorio.observarCosto(id).first(), 0.001)
    }

    @Test
    fun `observarCosto contesta cero para una receta sin ingredientes`() = runBlocking {
        // **Es la diferencia con `observarCostos`**, y es la razón de que exista: el mapa de
        // aquella no trae entrada para una receta sin ingredientes, porque su `GROUP BY` no
        // le da fila. Esta no agrupa, así que contesta 0 y quien la lea no tiene que
        // acordarse de rellenar el hueco.
        assertEquals(0.0, repositorio.observarCosto(crearReceta()).first(), 0.001)
    }

    @Test
    fun `observarCosto avisa cuando cambia el precio de un ingrediente de otra pantalla`() =
        runBlocking {
            // Es lo que tenía que seguir funcionando al dejar de mirar el costo de todas las
            // recetas para leer el de una: quien mueve el costo es el catálogo, en otro paso.
            val id = crearReceta()
            val harina = ingrediente("Harina", 1.2)
            repositorio.agregarIngrediente(
                repositorio.obtenerSecciones(id).single().id, harina, 500.0
            )
            assertEquals(600.0, repositorio.observarCosto(id).first(), 0.001)

            catalogo.actualizar(catalogo.obtener(harina)!!.copy(valorPorGramo = 2.0))

            assertEquals(1000.0, repositorio.observarCosto(id).first(), 0.001)
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

    // --- Títulos repetidos ---

    @Test
    fun `no se puede crear una receta con un titulo que ya existe`() = runBlocking {
        crearReceta("Torta de manjar")

        val resultado = repositorio.crear("Torta de manjar")

        assertTrue(resultado is ResultadoCrearReceta.YaExiste)
        assertEquals(1, repositorio.observarTodas().first().size)
    }

    @Test
    fun `los repetidos se detectan cambiando mayusculas y tildes`() = runBlocking {
        crearReceta("Torta de limón")

        assertTrue(repositorio.crear("torta de limon") is ResultadoCrearReceta.YaExiste)
        assertTrue(repositorio.crear("TORTA DE LIMÓN") is ResultadoCrearReceta.YaExiste)
        assertTrue(repositorio.crear("  Torta de Limon  ") is ResultadoCrearReceta.YaExiste)
        assertEquals(1, repositorio.observarTodas().first().size)
    }

    @Test
    fun `un titulo parecido pero distinto si se puede crear`() = runBlocking {
        crearReceta("Torta de limón")
        assertTrue(repositorio.crear("Torta de limón grande") is ResultadoCrearReceta.Creada)
    }

    @Test
    fun `renombrar hacia un titulo ya usado se rechaza y no guarda`() = runBlocking {
        crearReceta("Torta de manjar")
        val id = crearReceta("Bizcocho")

        val resultado = repositorio.renombrar(id, "torta de manjar")

        assertTrue(resultado is Resultado.NoSePudo)
        assertEquals("Bizcocho", repositorio.obtener(id)?.titulo)
    }

    @Test
    fun `renombrar una receta con su propio titulo no choca consigo misma`() = runBlocking {
        val id = crearReceta("Torta de manjar")
        assertTrue(repositorio.renombrar(id, "Torta de Manjar") is Resultado.Listo)
        assertEquals("Torta de Manjar", repositorio.obtener(id)?.titulo)
    }

    // --- La sección bautizada no vuelve a preguntar ---

    @Test
    fun `una seccion unica ya bautizada no tiene nada que bautizar`() = runBlocking {
        val id = crearReceta("Torta de manjar")
        val unica = repositorio.obtenerSecciones(id).single()
        repositorio.renombrarSeccion(unica, "Salsa")

        assertNull(repositorio.nombreQueFaltaBautizar(id))
    }

    @Test
    fun `agregar otra seccion no pisa el nombre que ya tenia la unica`() = runBlocking {
        val id = crearReceta("Torta de manjar")
        repositorio.renombrarSeccion(repositorio.obtenerSecciones(id).single(), "Salsa")

        // Sin pasar nombreDeLaPrimera: no hace falta, porque ya tiene uno propio.
        val resultado = repositorio.agregarSeccion(id, "Crema")

        assertTrue(resultado is Resultado.Listo)
        assertEquals(
            listOf("Salsa", "Crema"),
            repositorio.obtenerSecciones(id).map { it.nombreSeccion }
        )
    }

    // --- Nombres de sección repetidos ---

    @Test
    fun `no se puede agregar una seccion con el nombre de otra que ya esta`() = runBlocking {
        val id = crearReceta("Torta de manjar")
        repositorio.agregarSeccion(id, "Salsa de chocolate", nombreDeLaPrimera = "Bizcocho")

        val resultado = repositorio.agregarSeccion(id, "Salsa de chocolate")

        assertTrue(resultado is Resultado.NoSePudo)
        assertEquals(
            listOf("Bizcocho", "Salsa de chocolate"),
            repositorio.obtenerSecciones(id).map { it.nombreSeccion }
        )
    }

    @Test
    fun `las mayusculas, las tildes y los espacios no hacen distinta a una seccion`() =
        runBlocking {
            val id = crearReceta("Torta de manjar")
            repositorio.agregarSeccion(id, "Salsa de chocolate", nombreDeLaPrimera = "Bizcocho")

            assertTrue(repositorio.agregarSeccion(id, "SALSA DE CHOCOLATE") is Resultado.NoSePudo)
            assertTrue(repositorio.agregarSeccion(id, "salsa de chocolaté") is Resultado.NoSePudo)
            assertTrue(
                repositorio.agregarSeccion(id, "  Salsa De Chocolate  ") is Resultado.NoSePudo
            )
            assertEquals(2, repositorio.obtenerSecciones(id).size)
        }

    @Test
    fun `dos recetas distintas si pueden tener una seccion con el mismo nombre`() = runBlocking {
        // La regla es por receta, no global: casi toda torta tiene su "Bizcocho".
        val una = crearReceta("Torta de manjar")
        val otra = crearReceta("Torta de lúcuma")
        repositorio.agregarSeccion(una, "Crema", nombreDeLaPrimera = "Bizcocho")

        val resultado = repositorio.agregarSeccion(otra, "Crema", nombreDeLaPrimera = "Bizcocho")

        assertTrue(resultado is Resultado.Listo)
    }

    @Test
    fun `renombrar una seccion hacia un nombre ya usado no cambia nada`() = runBlocking {
        val id = crearReceta("Torta de manjar")
        repositorio.agregarSeccion(id, "Crema", nombreDeLaPrimera = "Bizcocho")
        val crema = repositorio.obtenerSecciones(id).first { it.nombreSeccion == "Crema" }

        val resultado = repositorio.renombrarSeccion(crema, "bizcocho")

        assertTrue(resultado is Resultado.NoSePudo)
        assertEquals(
            listOf("Bizcocho", "Crema"),
            repositorio.obtenerSecciones(id).map { it.nombreSeccion }
        )
    }

    @Test
    fun `una seccion puede renombrarse a si misma sin chocar consigo`() = runBlocking {
        // Corregirle una tilde o una mayúscula a su propio nombre tiene que poder hacerse.
        val id = crearReceta("Torta de manjar")
        repositorio.agregarSeccion(id, "Salsa de chocolate", nombreDeLaPrimera = "Bizcocho")
        val salsa = repositorio.obtenerSecciones(id).first { it.nombreSeccion.startsWith("Salsa") }

        val resultado = repositorio.renombrarSeccion(salsa, "Salsa De Chocolate")

        assertTrue(resultado is Resultado.Listo)
        val nombres = repositorio.obtenerSecciones(id).map { it.nombreSeccion }
        assertTrue("Salsa De Chocolate" in nombres)
    }

    @Test
    fun `el bautizo de la primera no puede chocar con la seccion que se esta creando`() =
        runBlocking {
            // La sugerencia para bautizar es el título de la receta, así que este choque no
            // es rebuscado: "Salsa de chocolate" como receta y como sección nueva.
            val id = crearReceta("Salsa de chocolate")

            val resultado = repositorio.agregarSeccion(
                recetaId = id,
                nombre = "Salsa de chocolate",
                nombreDeLaPrimera = "Salsa de chocolate"
            )

            assertTrue(resultado is Resultado.NoSePudo)
            assertEquals(1, repositorio.obtenerSecciones(id).size)
            // Y la primera quedó como estaba: se revisa antes de escribir nada.
            assertEquals(
                NOMBRE_SECCION_POR_DEFECTO,
                repositorio.obtenerSecciones(id).single().nombreSeccion
            )
        }

    // --- El costo que se muestra tiene que seguir vivo ---

    @Test
    fun `borrar un ingrediente del catalogo baja el costo de la receta sin pedirlo`() =
        runBlocking {
            // El bug: la lista de recetas mostraba el costo de antes de borrar. El costo se
            // pedía de una sola vez, colgado del aviso de la tabla `recetas`, y borrar un
            // ingrediente no toca esa tabla, así que nadie volvía a preguntar.
            val harina = ingrediente("Harina", 1.5)
            val id = crearReceta("Torta de manjar")
            val seccion = repositorio.obtenerSecciones(id).single()
            repositorio.agregarIngrediente(seccion.id, harina, 500.0)

            assertEquals(750.0, repositorio.observarCostos().first()[id]!!, 0.001)

            // Lo mismo que hace `IngredienteRepositorio.confirmarEliminacion`.
            dao.quitarIngredienteDeTodasLasSecciones(harina)
            catalogo.eliminarPorId(harina)

            // Sin volver a suscribirse ni pedir nada: el mismo Flow ya emite el valor nuevo.
            val despues = repositorio.observarCostos().first()
            assertNull("Una receta sin ingredientes no trae fila; se toma como 0", despues[id])
        }

    @Test
    fun `cambiarle el precio a un ingrediente cambia el costo que se muestra`() = runBlocking {
        val harina = ingrediente("Harina", 1.0)
        val id = crearReceta("Torta de manjar")
        repositorio.agregarIngrediente(repositorio.obtenerSecciones(id).single().id, harina, 200.0)

        assertEquals(200.0, repositorio.observarCostos().first()[id]!!, 0.001)

        catalogo.actualizar(Ingrediente(id = harina, nombre = "Harina", valorPorGramo = 3.0))

        assertEquals(600.0, repositorio.observarCostos().first()[id]!!, 0.001)
    }

    @Test
    fun `observarCostos y costosDe dan el mismo numero`() = runBlocking {
        // Son dos caminos al mismo dato -uno en vivo, otro de una sola vez- y si se
        // separaran, la lista y las fórmulas mostrarían costos distintos de la misma receta.
        val harina = ingrediente("Harina", 1.5)
        val azucar = ingrediente("Azúcar", 2.0)
        val id = crearReceta("Torta de manjar")
        val seccion = repositorio.obtenerSecciones(id).single()
        repositorio.agregarIngrediente(seccion.id, harina, 500.0)
        repositorio.agregarIngrediente(seccion.id, azucar, 250.0)

        assertEquals(
            repositorio.costosDe(listOf(id)).getValue(id),
            repositorio.observarCostos().first().getValue(id),
            0.001
        )
    }

    // --- Pasos (8.8) ---

    @Test
    fun `los pasos nuevos se ponen al final aunque haya huecos`() = runBlocking {
        // El orden sale de MAX+1 y no de contar: contar da mal apenas se borra uno del medio,
        // y ahí dos pasos nuevos seguidos se pisarían.
        val id = crearReceta()
        val uno = repositorio.agregarPaso(id)
        val dos = repositorio.agregarPaso(id)
        repositorio.eliminarPaso(uno)

        val tres = repositorio.agregarPaso(id)

        assertEquals(listOf(dos, tres), repositorio.observarPasos(id).first().map { it.id })
    }

    @Test
    fun `un paso que queda en blanco se borra en vez de guardarse vacio`() = runBlocking {
        // Vaciar el campo es cómo se dice "este paso ya no va". Es la misma decisión que
        // `guardarDuracion`, y sin ella una fila vacía correría la numeración de abajo.
        val id = crearReceta()
        val paso = repositorio.agregarPaso(id)
        repositorio.guardarTextoDePaso(paso, "Algo")

        repositorio.guardarTextoDePaso(paso, "   ")

        assertTrue(repositorio.observarPasos(id).first().isEmpty())
    }

    @Test
    fun `un paso demasiado largo se rechaza sin tocar lo guardado`() = runBlocking {
        val id = crearReceta()
        val paso = repositorio.agregarPaso(id)
        repositorio.guardarTextoDePaso(paso, "Lo que servía")

        val r = repositorio.guardarTextoDePaso(paso, "a".repeat(1001))

        assertTrue(r is Resultado.NoSePudo)
        assertEquals("Lo que servía", repositorio.observarPasos(id).first().single().contenido)
    }

    @Test
    fun `una seccion no se puede usar como titulo dos veces`() = runBlocking {
        // Dos bloques "Crema" no dicen en cuál va cada cosa (8.8). Solo el General se repite.
        val id = crearReceta()
        repositorio.agregarSeccion(id, "Crema")
        val crema = repositorio.obtenerSecciones(id).first { it.nombreSeccion == "Crema" }
        val uno = repositorio.agregarPaso(id)
        val dos = repositorio.agregarPaso(id)
        assertTrue(repositorio.cambiarTituloDePaso(uno, crema.id) is Resultado.Listo)

        val r = repositorio.cambiarTituloDePaso(dos, crema.id)

        assertTrue(r is Resultado.NoSePudo)
        assertNull(repositorio.observarPasos(id).first().first { it.id == dos }.tituloSeccionId)
    }

    @Test
    fun `el General si se puede repetir`() = runBlocking {
        val id = crearReceta()
        val uno = repositorio.agregarPaso(id)
        val dos = repositorio.agregarPaso(id)

        assertTrue(repositorio.cambiarTituloDePaso(uno, null) is Resultado.Listo)
        assertTrue(repositorio.cambiarTituloDePaso(dos, null) is Resultado.Listo)
    }

    @Test
    fun `ponerle un titulo propio a un paso deja de marcarlo como general anidado`() =
        runBlocking {
            // Son estados excluyentes: dejar el `true` puesto dibujaría con sangría un paso
            // que ya no viene de otra receta.
            val id = crearReceta()
            repositorio.agregarSeccion(id, "Crema")
            val crema = repositorio.obtenerSecciones(id).first { it.nombreSeccion == "Crema" }
            val paso = repositorio.agregarPaso(id, esGeneralAnidado = true)

            repositorio.cambiarTituloDePaso(paso, crema.id)

            val guardado = repositorio.observarPasos(id).first().single()
            assertEquals(crema.id, guardado.tituloSeccionId)
            assertFalse(guardado.esGeneralAnidado)
        }

    @Test
    fun `mover un paso funciona aunque los ordenes tengan huecos`() = runBlocking {
        // Es el caso que rompía la primera versión: con `orden` en [0, 2], usar el índice como
        // orden mandaba el último al principio en vez de una posición.
        val id = crearReceta()
        val uno = repositorio.agregarPaso(id)
        val dos = repositorio.agregarPaso(id)
        val tres = repositorio.agregarPaso(id)
        repositorio.eliminarPaso(dos)

        assertTrue(repositorio.moverPaso(tres, haciaArriba = true))

        assertEquals(listOf(tres, uno), repositorio.observarPasos(id).first().map { it.id })
    }

    @Test
    fun `el primero no se puede subir y el ultimo no se puede bajar`() = runBlocking {
        val id = crearReceta()
        val uno = repositorio.agregarPaso(id)
        val dos = repositorio.agregarPaso(id)

        assertFalse(repositorio.moverPaso(uno, haciaArriba = true))
        assertFalse(repositorio.moverPaso(dos, haciaArriba = false))
        assertEquals(listOf(uno, dos), repositorio.observarPasos(id).first().map { it.id })
    }

    @Test
    fun `borrar una seccion deja sus pasos como General en vez de llevarselos`() = runBlocking {
        // El texto lo escribió alguien: hacerlo desaparecer porque se reorganizó la receta
        // sería perder trabajo sin avisar. Es la regla SET_NULL de la clave foránea.
        val id = crearReceta()
        repositorio.agregarSeccion(id, "Crema")
        val crema = repositorio.obtenerSecciones(id).first { it.nombreSeccion == "Crema" }
        val paso = repositorio.agregarPaso(id)
        repositorio.guardarTextoDePaso(paso, "Batir la crema.")
        repositorio.cambiarTituloDePaso(paso, crema.id)

        repositorio.eliminarSeccion(id, crema.id)

        val quedo = repositorio.observarPasos(id).first().single()
        assertEquals("Batir la crema.", quedo.contenido)
        assertNull(quedo.tituloSeccionId)
    }
}
