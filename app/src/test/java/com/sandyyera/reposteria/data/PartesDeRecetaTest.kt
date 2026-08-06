package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.logica.partes.EstadoDelVinculo
import com.sandyyera.reposteria.logica.partes.MOTIVO_UN_SOLO_NIVEL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Traer una receta dentro de otra: la copia, el aviso y las tres salidas (8.11).
 *
 * Es la parte de 8.11 que **no se puede probar en `logica/`**, porque lo que hay que verificar
 * es justamente lo que cruza dos recetas en la base: que la copia quede independiente, que el
 * aviso se encienda al tocar la original, y que actualizar adapte en proporción sin pisar lo
 * que se ajustó a mano. Las reglas puras —la firma, la adaptación, el emparejamiento— viven en
 * `logica/` y tienen sus propias pruebas.
 */
class PartesDeRecetaTest {

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

    private suspend fun crearReceta(titulo: String): Long =
        (repositorio.crear(titulo) as ResultadoCrearReceta.Creada).recetaId

    private suspend fun ingrediente(nombre: String, valorPorGramo: Double = 1.0): Long {
        catalogo.sembrar(Ingrediente(nombre = nombre, valorPorGramo = valorPorGramo))
        return catalogo.obtenerTodosUnaVez().first { it.nombre == nombre }.id
    }

    private suspend fun unicaSeccionDe(recetaId: Long) = dao.obtenerSecciones(recetaId).first()

    /**
     * Un bizcocho de una sección con harina y azúcar, listo para traer.
     *
     * Devuelve el id de la receta. Los ingredientes se dejan en el catálogo con valor 1 para
     * que el costo sea igual a los gramos y no haya que hacer cuentas al leer las pruebas.
     */
    private suspend fun bizcocho(harina: Double = 550.0, azucar: Double = 200.0): Long {
        val id = crearReceta("Bizcocho")
        val seccion = unicaSeccionDe(id).id
        repositorio.agregarIngrediente(seccion, ingrediente("Harina"), harina)
        repositorio.agregarIngrediente(seccion, ingrediente("Azúcar"), azucar)
        return id
    }

    /** Los gramos de un ingrediente dentro de una receta, buscándolo por nombre. */
    private suspend fun gramosDe(recetaId: Long, nombre: String): Double? {
        val ingredienteId = catalogo.obtenerTodosUnaVez().first { it.nombre == nombre }.id
        return dao.obtenerTodosLosIngredientes(recetaId)
            .firstOrNull { it.ingredienteId == ingredienteId }
            ?.cantidadG
    }

    // --- Traer ---

    @Test
    fun `traer una receta copia sus ingredientes y deja el vinculo`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")

        assertEquals(Resultado.Listo, repositorio.traerReceta(torta, origen))

        val secciones = dao.obtenerSecciones(torta)
        assertEquals("La 'General' vacía se fue y quedó solo la traída", 1, secciones.size)
        assertEquals("Bizcocho", secciones.first().nombreSeccion)
        assertEquals(origen, secciones.first().recetaOrigenId)
        assertNotNull("Y con su firma", secciones.first().firmaDelOrigen)
        assertEquals(550.0, gramosDe(torta, "Harina")!!, 0.001)
    }

    @Test
    fun `la copia es independiente de la original en los dos sentidos`() = runBlocking {
        // Es la regla que ordena todo 8.11.1. Si esto falla, todo lo demás sobra.
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        val laDeLaTorta = dao.obtenerTodosLosIngredientes(torta)
            .first { it.cantidadG == 550.0 }
        repositorio.cambiarCantidad(laDeLaTorta.id, 275.0)

        assertEquals("La original no se movió", 550.0, gramosDe(origen, "Harina")!!, 0.001)

        val laDelBizcocho = dao.obtenerTodosLosIngredientes(origen)
            .first { it.cantidadG == 550.0 }
        repositorio.cambiarCantidad(laDelBizcocho.id, 500.0)

        assertEquals("Y la copia tampoco", 275.0, gramosDe(torta, "Harina")!!, 0.001)
    }

    @Test
    fun `llegan todas las secciones, no una sola con todo adentro`() = runBlocking {
        val origen = bizcocho()
        repositorio.agregarSeccion(origen, "Crema", nombreDeLaPrimera = "Masa")
        val crema = dao.obtenerSecciones(origen).first { it.nombreSeccion == "Crema" }
        repositorio.agregarIngrediente(crema.id, ingrediente("Crema de leche"), 300.0)

        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        val nombres = dao.obtenerSecciones(torta).map { it.nombreSeccion }
        assertEquals(listOf("Masa", "Crema"), nombres)
    }

    @Test
    fun `un nombre que choca se renombra en vez de rechazar la copia`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        // La torta ya tiene una sección llamada igual que la que va a llegar.
        repositorio.agregarSeccion(torta, "Bizcocho", nombreDeLaPrimera = "Decoración")

        assertEquals(Resultado.Listo, repositorio.traerReceta(torta, origen))

        val nombres = dao.obtenerSecciones(torta).map { it.nombreSeccion }
        assertTrue("La que llegó se renombró: $nombres", nombres.contains("Bizcocho 2"))
        assertTrue("Y la que estaba se quedó igual", nombres.contains("Bizcocho"))
    }

    @Test
    fun `la seccion invisible de la original llega con el nombre de la receta`() = runBlocking {
        // Una receta de una sola parte tiene su sección todavía llamada "General", y ese
        // nombre nunca se ve allá porque es la única. Copiado tal cual aparecería un encabezado
        // "General" al lado de "Crema": un nombre que nadie escribió y que no dice de qué parte
        // habla. Entra con el título de la receta, que es lo que uno diría en voz alta.
        val origen = bizcocho()
        assertEquals(
            "En la original sigue siendo la invisible",
            "General",
            unicaSeccionDe(origen).nombreSeccion
        )

        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        assertEquals("Bizcocho", dao.obtenerSecciones(torta).first().nombreSeccion)
    }

    @Test
    fun `una seccion bautizada a mano llega con su nombre, no con el de la receta`() = runBlocking {
        val origen = bizcocho()
        repositorio.agregarSeccion(origen, "Almíbar", nombreDeLaPrimera = "Masa")

        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        assertEquals(listOf("Masa", "Almíbar"), dao.obtenerSecciones(torta).map { it.nombreSeccion })
    }

    @Test
    fun `la seccion sembrada se elimina solo si esta vacia`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        // Con algo cargado, la "General" ya no se puede tirar: hay trabajo adentro. Y ahí hay
        // que bautizarla, igual que al agregar una sección a mano.
        repositorio.agregarIngrediente(
            unicaSeccionDe(torta).id, ingrediente("Manjar"), 400.0
        )

        val sinBautizo = repositorio.traerReceta(torta, origen)
        assertTrue("Sin nombre para la que ya existe se rechaza", sinBautizo is Resultado.NoSePudo)

        assertEquals(
            Resultado.Listo,
            repositorio.traerReceta(torta, origen, nombreDeLaPrimera = "Relleno")
        )
        val nombres = dao.obtenerSecciones(torta).map { it.nombreSeccion }
        assertEquals(listOf("Relleno", "Bizcocho"), nombres)
        assertEquals("Y el manjar sigue donde estaba", 400.0, gramosDe(torta, "Manjar")!!, 0.001)
    }

    @Test
    fun `los pasos generales de la original llegan como generales anidados`() = runBlocking {
        val origen = bizcocho()
        val seccionDelOrigen = unicaSeccionDe(origen).id
        val general = repositorio.agregarPaso(origen)
        repositorio.guardarTextoDePaso(general, "Precalentar el horno")
        val conTitulo = repositorio.agregarPaso(origen, titulo = seccionDelOrigen)
        repositorio.guardarTextoDePaso(conTitulo, "Batir hasta que doble")

        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        val copiados = dao.obtenerPasos(torta)
        assertEquals(2, copiados.size)

        val elGeneral = copiados.first { it.contenido == "Precalentar el horno" }
        assertNull("Sigue sin título", elGeneral.tituloSeccionId)
        assertTrue("Pero es de la parte que se trajo", elGeneral.esGeneralAnidado)

        val elDeTitulo = copiados.first { it.contenido == "Batir hasta que doble" }
        val laCopia = dao.obtenerSecciones(torta).first { it.nombreSeccion == "Bizcocho" }
        assertEquals("Y apunta a la sección de acá, no a la de allá", laCopia.id, elDeTitulo.tituloSeccionId)
        assertFalse(elDeTitulo.esGeneralAnidado)
    }

    // --- El tope de un solo nivel (8.11.6) ---

    @Test
    fun `una receta hecha de partes no se puede traer ni se ofrece`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        val postre = crearReceta("Postre")
        val rechazo = repositorio.traerReceta(postre, torta)
        assertEquals(Resultado.NoSePudo(MOTIVO_UN_SOLO_NIVEL), rechazo)

        val ofrecidas = repositorio.recetasParaTraer(postre)
        val laTorta = ofrecidas.first { it.receta.id == torta }
        assertEquals(MOTIVO_UN_SOLO_NIVEL, laTorta.motivoNoDisponible)
        assertNull("El bizcocho sí se puede", ofrecidas.first { it.receta.id == origen }.motivoNoDisponible)
    }

    @Test
    fun `una receta no se ofrece a si misma`() = runBlocking {
        val torta = crearReceta("Torta")
        bizcocho()
        assertTrue(repositorio.recetasParaTraer(torta).none { it.receta.id == torta })
    }

    // --- El aviso (8.11.3) ---

    @Test
    fun `sin cambios en la original no hay nada que avisar`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        val partes = repositorio.partesDe(torta)
        assertEquals(1, partes.size)
        assertEquals(EstadoDelVinculo.VIVO, partes.first().estado)
        assertFalse(partes.first().hayQueAvisar)
        assertEquals("Bizcocho", partes.first().tituloDelOrigen)
    }

    @Test
    fun `cambiar un gramaje en la original enciende el aviso con la frase`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        val laHarina = dao.obtenerTodosLosIngredientes(origen).first { it.cantidadG == 550.0 }
        repositorio.cambiarCantidad(laHarina.id, 500.0)

        val parte = repositorio.partesDe(torta).first()
        assertTrue(parte.hayQueAvisar)
        assertEquals(listOf("'Harina' pasó de 550 a 500 g"), parte.cambios)
    }

    @Test
    fun `las secciones traidas juntas son un solo grupo`() = runBlocking {
        // El "¿Qué cambió?" es de la receta original entera, así que repetirlo por sección
        // diría lo mismo tantas veces como partes tenga.
        val origen = bizcocho()
        repositorio.agregarSeccion(origen, "Crema", nombreDeLaPrimera = "Masa")
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        val partes = repositorio.partesDe(torta)
        assertEquals("Un grupo, no dos", 1, partes.size)
        assertEquals(2, partes.first().seccionIds.size)
    }

    // --- Actualizar (8.11.3) ---

    @Test
    fun `actualizar adapta en proporcion y no pisa lo ajustado a mano`() = runBlocking {
        // El ejemplo exacto de 8.11.3: la original pasa de 550 a 500, acá se usaban 275 (la
        // mitad), y queda 250 — la mitad de la nueva, igual que antes era la mitad de la vieja.
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        val enLaTorta = dao.obtenerTodosLosIngredientes(torta).first { it.cantidadG == 550.0 }
        repositorio.cambiarCantidad(enLaTorta.id, 275.0)

        val enElBizcocho = dao.obtenerTodosLosIngredientes(origen).first { it.cantidadG == 550.0 }
        repositorio.cambiarCantidad(enElBizcocho.id, 500.0)

        val laSeccion = dao.obtenerSecciones(torta).first()
        assertEquals(Resultado.Listo, repositorio.actualizarParte(laSeccion.id))

        assertEquals(250.0, gramosDe(torta, "Harina")!!, 0.001)
        assertEquals("El azúcar no cambió allá, así que acá tampoco", 200.0, gramosDe(torta, "Azúcar")!!, 0.001)
    }

    @Test
    fun `un ingrediente nuevo en la original llega con su cantidad`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        repositorio.agregarIngrediente(unicaSeccionDe(origen).id, ingrediente("Sal"), 5.0)
        repositorio.actualizarParte(dao.obtenerSecciones(torta).first().id)

        assertEquals(5.0, gramosDe(torta, "Sal")!!, 0.001)
    }

    @Test
    fun `un ingrediente que la original elimino se va, y lo agregado a mano se queda`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        // Uno propio, que la original nunca tuvo.
        repositorio.agregarIngrediente(
            dao.obtenerSecciones(torta).first().id, ingrediente("Canela"), 3.0
        )
        // Y allá se elimina el azúcar.
        val elAzucar = dao.obtenerTodosLosIngredientes(origen).first { it.cantidadG == 200.0 }
        repositorio.quitarIngrediente(elAzucar.id)

        repositorio.actualizarParte(dao.obtenerSecciones(torta).first().id)

        assertNull("El azúcar se fue porque había venido de allá", gramosDe(torta, "Azúcar"))
        assertEquals("La canela se queda: la original nunca la tuvo", 3.0, gramosDe(torta, "Canela")!!, 0.001)
    }

    @Test
    fun `despues de actualizar el aviso se apaga`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        val laHarina = dao.obtenerTodosLosIngredientes(origen).first { it.cantidadG == 550.0 }
        repositorio.cambiarCantidad(laHarina.id, 500.0)
        assertTrue(repositorio.partesDe(torta).first().hayQueAvisar)

        repositorio.actualizarParte(dao.obtenerSecciones(torta).first().id)
        assertFalse(repositorio.partesDe(torta).first().hayQueAvisar)
    }

    @Test
    fun `mantener apaga el aviso sin tocar ninguna cantidad`() = runBlocking {
        // Es la diferencia con actualizar, y la que se olvida al implementarlo: mantener no
        // puede ser "no hacer nada", porque entonces el mismo aviso quedaría encendido para
        // siempre y no habría cómo distinguir "no lo miré" de "lo miré y lo dejo así".
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        val laHarina = dao.obtenerTodosLosIngredientes(origen).first { it.cantidadG == 550.0 }
        repositorio.cambiarCantidad(laHarina.id, 500.0)

        assertEquals(Resultado.Listo, repositorio.mantenerParte(dao.obtenerSecciones(torta).first().id))

        assertEquals("La copia se quedó como estaba", 550.0, gramosDe(torta, "Harina")!!, 0.001)
        assertFalse("Pero el aviso se apagó", repositorio.partesDe(torta).first().hayQueAvisar)
    }

    @Test
    fun `mantener deja el vinculo vivo, y el proximo cambio vuelve a preguntar`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        val laHarina = dao.obtenerTodosLosIngredientes(origen).first { it.cantidadG == 550.0 }
        repositorio.cambiarCantidad(laHarina.id, 500.0)
        repositorio.mantenerParte(dao.obtenerSecciones(torta).first().id)

        repositorio.cambiarCantidad(laHarina.id, 450.0)

        val parte = repositorio.partesDe(torta).first()
        assertEquals(EstadoDelVinculo.VIVO, parte.estado)
        assertEquals(listOf("'Harina' pasó de 500 a 450 g"), parte.cambios)
    }

    // --- Desvincular (8.11.3) ---

    @Test
    fun `desvincular limpia las dos columnas y no toca los ingredientes`() = runBlocking {
        // Limpiando solo el id, la sección quedaría diciendo que su original desapareció y
        // volvería a preguntar para siempre — lo contrario de lo que se pidió.
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        assertEquals(Resultado.Listo, repositorio.desvincularParte(dao.obtenerSecciones(torta).first().id))

        val seccion = dao.obtenerSecciones(torta).first()
        assertNull(seccion.recetaOrigenId)
        assertNull(seccion.firmaDelOrigen)
        assertEquals(550.0, gramosDe(torta, "Harina")!!, 0.001)
        assertTrue("Y ya no aparece como parte", repositorio.partesDe(torta).isEmpty())
    }

    @Test
    fun `una desvinculada no vuelve a avisar aunque la original cambie`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)
        repositorio.desvincularParte(dao.obtenerSecciones(torta).first().id)

        val laHarina = dao.obtenerTodosLosIngredientes(origen).first { it.cantidadG == 550.0 }
        repositorio.cambiarCantidad(laHarina.id, 500.0)

        assertTrue(repositorio.partesDe(torta).isEmpty())
    }

    @Test
    fun `desvincular libera el tope de un solo nivel`() = runBlocking {
        // La receta deja de estar hecha de partes, así que vuelve a poder usarse dentro de otra.
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)
        repositorio.desvincularParte(dao.obtenerSecciones(torta).first().id)

        val postre = crearReceta("Postre")
        assertNull(repositorio.recetasParaTraer(postre).first { it.receta.id == torta }.motivoNoDisponible)
    }

    // --- Si la original fue borrada (8.11.4) ---

    @Test
    fun `borrar la original deja la copia entera y pidiendo una decision`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        repositorio.confirmarEliminacion(origen)

        val parte = repositorio.partesDe(torta).first()
        assertEquals(EstadoDelVinculo.ORIGINAL_BORRADA, parte.estado)
        assertTrue("Avisa aunque no haya ningún cambio que contar", parte.hayQueAvisar)
        assertEquals("Y los ingredientes siguen enteros", 550.0, gramosDe(torta, "Harina")!!, 0.001)
    }

    @Test
    fun `una huerfana no se confunde con una desvinculada a mano`() = runBlocking {
        // Es la distinción de 8.11.7, y en la base las dos tienen `recetaOrigenId` en null: lo
        // único que las separa es la firma. Sin eso, o se pregunta de más o de menos.
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)
        repositorio.agregarSeccion(torta, "Propia", nombreDeLaPrimera = "Bizcocho traído")

        repositorio.confirmarEliminacion(origen)

        val partes = repositorio.partesDe(torta)
        assertEquals("Solo la que vino de otra receta", 1, partes.size)
        assertEquals(EstadoDelVinculo.ORIGINAL_BORRADA, partes.first().estado)
    }

    @Test
    fun `borrar la parte se lleva sus pasos`() = runBlocking {
        val origen = bizcocho()
        val seccionDelOrigen = unicaSeccionDe(origen).id
        val paso = repositorio.agregarPaso(origen, titulo = seccionDelOrigen)
        repositorio.guardarTextoDePaso(paso, "Batir hasta que doble")

        val torta = crearReceta("Torta")
        repositorio.agregarIngrediente(unicaSeccionDe(torta).id, ingrediente("Manjar"), 400.0)
        repositorio.traerReceta(torta, origen, nombreDeLaPrimera = "Relleno")
        repositorio.confirmarEliminacion(origen)

        val laTraida = dao.obtenerSecciones(torta).first { it.nombreSeccion == "Bizcocho" }
        assertEquals(Resultado.Listo, repositorio.borrarParte(laTraida.id))

        assertEquals(listOf("Relleno"), dao.obtenerSecciones(torta).map { it.nombreSeccion })
        assertTrue("El paso se fue con ella", dao.obtenerPasos(torta).isEmpty())
    }

    @Test
    fun `borrar la parte no deja la receta sin ninguna seccion`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        repositorio.traerReceta(torta, origen)

        // La sembrada se eliminó al traer, así que el grupo es todo lo que hay.
        val resultado = repositorio.borrarParte(dao.obtenerSecciones(torta).first().id)
        assertTrue(resultado is Resultado.NoSePudo)
        assertEquals(1, dao.obtenerSecciones(torta).size)
    }

    // --- A quién afecta borrar una receta (8.11.4) ---

    @Test
    fun `borrar una receta dice a que otras afecta`() = runBlocking {
        val origen = bizcocho()
        val torta = crearReceta("Torta")
        val postre = crearReceta("Postre")
        repositorio.traerReceta(torta, origen)
        repositorio.traerReceta(postre, origen)

        val afectadas = repositorio.recetasQueUsanEstaReceta(origen).map { it.titulo }
        assertEquals(listOf("Postre", "Torta"), afectadas)
    }

    @Test
    fun `una receta que nadie usa no afecta a ninguna`() = runBlocking {
        val origen = bizcocho()
        assertTrue(repositorio.recetasQueUsanEstaReceta(origen).isEmpty())
    }
}
