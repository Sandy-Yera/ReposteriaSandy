package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.MoldeRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.data.repositorio.ResultadoGuardarMolde
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.validaciones.CampoDeMolde
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI

/**
 * Pruebas de `MoldeRepositorio` sobre la base de mentira (`./gradlew :app:test`).
 *
 * Lo que se cubre acá y no en `logica/`: que un molde repetido no se guarde, que corregir
 * una medida se propague a las recetas enlazadas **sin tocar sus ingredientes**, y que
 * borrar un molde no rompa esas recetas.
 */
class MoldeRepositorioTest {

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var recetaDao: RecetaDaoFalso
    private lateinit var moldeDao: MoldeDaoFalso
    private lateinit var historial: HistorialDaoFalso
    private lateinit var recetas: RecetaRepositorio
    private lateinit var repositorio: MoldeRepositorio

    @Before
    fun prepararTodo() {
        catalogo = IngredienteDaoFalso()
        recetaDao = RecetaDaoFalso(catalogo)
        moldeDao = MoldeDaoFalso(recetaDao)
        historial = HistorialDaoFalso()
        val historialRepo = HistorialRepositorio(historial)
        recetas = RecetaRepositorio(recetaDao, historialRepo)
        repositorio = MoldeRepositorio(moldeDao, recetas, historialRepo)
    }

    private fun medidas(vararg pares: Pair<CampoDeMolde, String>) = pares.toMap()

    private fun circulo(diametro: String = "24", alto: String = "7") =
        medidas(CampoDeMolde.DIAMETRO to diametro, CampoDeMolde.ALTURA_MOLDE to alto)

    private suspend fun crearCirculo(nombre: String = "Redondo grande"): Long =
        (repositorio.crear(nombre, TipoFormaMolde.CIRCULO, circulo()) as
            ResultadoGuardarMolde.Guardado).id

    // --- Crear ---

    @Test
    fun `crear un molde lo deja en el catalogo con sus medidas calculadas`() = runBlocking {
        val id = crearCirculo()

        val molde = repositorio.obtener(id)!!
        assertEquals("Redondo grande", molde.nombre)
        assertEquals(TipoFormaMolde.CIRCULO, molde.dimensiones.tipoForma)
        assertEquals(24.0, molde.dimensiones.diametroCm!!, 0.001)
        // El área y el volumen no se guardan: se calculan, así que no pueden quedar viejos.
        assertEquals(PI * 12 * 12, molde.dimensiones.areaCm2, 0.001)
        assertEquals(PI * 12 * 12 * 7, molde.dimensiones.volumenCm3, 0.001)
    }

    @Test
    fun `un molde sin forma o con medidas que no sirven no se guarda`() = runBlocking {
        assertTrue(
            repositorio.crear("Sin forma", null, emptyMap()) is ResultadoGuardarMolde.NoValido
        )
        assertTrue(
            repositorio.crear("Redondo", TipoFormaMolde.CIRCULO, circulo(alto = "0"))
                is ResultadoGuardarMolde.NoValido
        )
        assertTrue(
            repositorio.crear("  ", TipoFormaMolde.CIRCULO, circulo())
                is ResultadoGuardarMolde.NoValido
        )
        assertTrue(repositorio.observarTodos().first().isEmpty())
    }

    @Test
    fun `el aviso de un molde invalido viene por campo, no como un texto suelto`() = runBlocking {
        // Un molde tiene hasta cuatro campos que pueden fallar a la vez y cada aviso va bajo
        // el suyo; un solo mensaje obligaría a la pantalla a adivinar dónde ponerlo.
        val resultado = repositorio.crear(
            nombre = "Rectangular",
            forma = TipoFormaMolde.RECTANGULO,
            medidas = medidas(
                CampoDeMolde.LARGO to "30",
                CampoDeMolde.ANCHO to "",
                CampoDeMolde.ALTURA_MOLDE to "0"
            )
        ) as ResultadoGuardarMolde.NoValido

        assertEquals(
            setOf(CampoDeMolde.ANCHO, CampoDeMolde.ALTURA_MOLDE),
            resultado.errores.medidas.keys
        )
        assertNull(resultado.errores.nombre)
    }

    @Test
    fun `no se puede crear otro molde con el mismo nombre, ni cambiando tildes`() = runBlocking {
        crearCirculo("Corazón chico")

        assertTrue(
            repositorio.crear("corazon chico", TipoFormaMolde.CIRCULO, circulo())
                is ResultadoGuardarMolde.YaExiste
        )
        assertTrue(
            repositorio.crear("  CORAZÓN CHICO ", TipoFormaMolde.CIRCULO, circulo())
                is ResultadoGuardarMolde.YaExiste
        )
        assertEquals(1, repositorio.observarTodos().first().size)
    }

    @Test
    fun `crear un molde queda anotado en el historial`() = runBlocking {
        crearCirculo("Redondo grande")

        val evento = historial.eventos.last()
        assertEquals(TipoEvento.CREACION, evento.tipo)
        assertTrue(
            "La descripción tiene que nombrar el molde",
            evento.descripcion.contains("Redondo grande")
        )
    }

    // --- Editar y propagar ---

    @Test
    fun `corregir un molde propaga las medidas a las recetas enlazadas`() = runBlocking {
        val moldeId = crearCirculo()
        val recetaId = recetaEnlazadaA(moldeId)

        val resultado = repositorio.actualizar(
            moldeId, "Redondo grande", TipoFormaMolde.CIRCULO, circulo(diametro = "26")
        )

        assertTrue(resultado is ResultadoGuardarMolde.Guardado)
        val rendimiento = recetaDao.obtenerRendimiento(recetaId)!!
        assertEquals(26.0, rendimiento.dimensiones!!.diametroCm!!, 0.001)
    }

    @Test
    fun `corregir un molde no toca las cantidades de ingredientes`() = runBlocking {
        // Esta es la distinción de 5.2: corregir una medida mal tomada **no es reescalar**.
        val moldeId = crearCirculo()
        val recetaId = recetaEnlazadaA(moldeId)
        catalogo.sembrar(Ingrediente(nombre = "Harina", valorPorGramo = 1.5))
        val harina = catalogo.obtenerTodosUnaVez().first { it.nombre == "Harina" }
        val seccion = recetas.obtenerSecciones(recetaId).first()
        recetas.agregarIngrediente(seccion.id, harina.id, 500.0)

        repositorio.actualizar(
            moldeId, "Redondo grande", TipoFormaMolde.CIRCULO, circulo(diametro = "40")
        )

        assertEquals(500.0, recetas.obtenerIngredientes(recetaId).single().cantidadG, 0.001)
        assertEquals(750.0, recetas.costoTotal(recetaId), 0.001)
    }

    @Test
    fun `una receta que ya no esta enlazada no recibe la correccion`() = runBlocking {
        val moldeId = crearCirculo()
        val recetaId = recetaEnlazadaA(moldeId)
        // Se desvincula, como si se hubiera reescalado a un molde de prueba (9.3).
        recetaDao.desvincularMolde(moldeId)

        repositorio.actualizar(
            moldeId, "Redondo grande", TipoFormaMolde.CIRCULO, circulo(diametro = "26")
        )

        // Conserva las medidas que tenía: 24, no 26.
        val rendimiento = recetaDao.obtenerRendimiento(recetaId)!!
        assertEquals(24.0, rendimiento.dimensiones!!.diametroCm!!, 0.001)
    }

    @Test
    fun `renombrar un molde hacia un nombre ya usado no cambia nada`() = runBlocking {
        val uno = crearCirculo("Redondo grande")
        crearCirculo("Redondo chico")

        val resultado = repositorio.actualizar(
            uno, "redondo chico", TipoFormaMolde.CIRCULO, circulo()
        )

        assertTrue(resultado is ResultadoGuardarMolde.YaExiste)
        assertEquals("Redondo grande", repositorio.obtener(uno)!!.nombre)
    }

    @Test
    fun `un molde puede guardarse con su propio nombre sin chocar consigo mismo`() = runBlocking {
        val id = crearCirculo("Redondo grande")

        val resultado = repositorio.actualizar(
            id, "Redondo Grande", TipoFormaMolde.CIRCULO, circulo(alto = "9")
        )

        assertTrue(resultado is ResultadoGuardarMolde.Guardado)
        assertEquals(9.0, repositorio.obtener(id)!!.dimensiones.alturaMoldeCm!!, 0.001)
    }

    @Test
    fun `cambiar de forma reemplaza las medidas y no deja las viejas puestas`() = runBlocking {
        val id = crearCirculo()

        repositorio.actualizar(
            id, "Redondo grande", TipoFormaMolde.CUADRADO,
            medidas(CampoDeMolde.LADO to "20", CampoDeMolde.ALTURA_MOLDE to "6")
        )

        val dimensiones = repositorio.obtener(id)!!.dimensiones
        assertEquals(TipoFormaMolde.CUADRADO, dimensiones.tipoForma)
        assertEquals(20.0, dimensiones.ladoCm!!, 0.001)
        assertNull("Un cuadrado con diámetro guardado se contradice", dimensiones.diametroCm)
    }

    // --- Borrar ---

    @Test
    fun `borrar un molde deja las recetas con sus medidas, solo sin vinculo`() = runBlocking {
        val moldeId = crearCirculo()
        val recetaId = recetaEnlazadaA(moldeId)

        repositorio.confirmarEliminacion(moldeId)

        assertNull(repositorio.obtener(moldeId))
        val rendimiento = recetaDao.obtenerRendimiento(recetaId)!!
        assertNull("El vínculo se pierde", rendimiento.moldeOrigenId)
        assertNotNull("Las medidas no", rendimiento.dimensiones)
        assertEquals(24.0, rendimiento.dimensiones!!.diametroCm!!, 0.001)
    }

    @Test
    fun `antes de borrar se puede saber a que recetas afecta`() = runBlocking {
        val moldeId = crearCirculo()
        recetaEnlazadaA(moldeId, "Torta de manjar")
        recetaEnlazadaA(moldeId, "Torta de lúcuma")

        val afectadas = repositorio.recetasAfectadasPorBorrar(moldeId)

        assertEquals(listOf("Torta de lúcuma", "Torta de manjar"), afectadas.map { it.titulo })
    }

    @Test
    fun `el historial nombra las recetas que quedaron sin vinculo`() = runBlocking {
        val moldeId = crearCirculo("Redondo grande")
        recetaEnlazadaA(moldeId, "Torta de manjar")

        repositorio.confirmarEliminacion(moldeId)

        val evento = historial.eventos.last()
        assertEquals(TipoEvento.ELIMINACION, evento.tipo)
        assertTrue(evento.descripcion.contains("Redondo grande"))
        assertTrue(
            "Hay que poder saber después a quién afectó",
            evento.detalleAdicional!!.contains("Torta de manjar")
        )
    }

    /** Crea una receta con rendimiento enlazado a [moldeId], con las medidas de ese molde. */
    private suspend fun recetaEnlazadaA(
        moldeId: Long,
        titulo: String = "Torta de manjar"
    ): Long {
        val recetaId = (recetas.crear(titulo) as ResultadoCrearReceta.Creada).recetaId
        val rendimiento = recetaDao.obtenerRendimiento(recetaId)!!
        recetaDao.actualizarRendimiento(
            rendimiento.copy(
                usaMolde = true,
                moldeOrigenId = moldeId,
                dimensiones = repositorio.obtener(moldeId)!!.dimensiones
            )
        )
        return recetaId
    }
}
