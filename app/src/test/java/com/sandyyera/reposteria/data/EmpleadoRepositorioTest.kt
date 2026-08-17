package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.repositorio.EmpleadoRepositorio
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearEmpleado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.sueldos.MotivoDeOmision
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
 * El repositorio de empleados (sección 10), sin base de datos ni celular.
 *
 * Lo que estas pruebas cuidan no son las cuentas —esas ya están probadas puras en
 * `logica/sueldos`— sino **lo que solo se ve cruzando tablas**: que el tope del sueldo se mida
 * contra la ganancia de *ahora* y no contra una guardada, que borrar un empleado se lleve lo suyo
 * y nada más, y que el empleado estándar aguante los tres intentos de tocarlo.
 */
class EmpleadoRepositorioTest {

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var recetaDao: RecetaDaoFalso
    private lateinit var empleadoDao: EmpleadoDaoFalso
    private lateinit var historial: HistorialDaoFalso
    private lateinit var recetas: RecetaRepositorio
    private lateinit var repositorio: EmpleadoRepositorio

    @Before
    fun prepararTodo() {
        catalogo = IngredienteDaoFalso()
        recetaDao = RecetaDaoFalso(catalogo)
        empleadoDao = EmpleadoDaoFalso()
        historial = HistorialDaoFalso()
        recetas = RecetaRepositorio(recetaDao, HistorialRepositorio(historial))
        repositorio = EmpleadoRepositorio(
            dao = empleadoDao,
            recetas = recetas,
            historial = HistorialRepositorio(historial)
        )
    }

    /**
     * Una receta con ingreso bruto 10.000 y costo 3.000: la del ejemplo de 10.1.
     *
     * Se arma pasando por el repositorio de verdad y no sembrando filas: así lo que se prueba es
     * la cadena entera, y si `obtenerDatosCalculo` cambiara de forma, estas pruebas se enterarían.
     */
    private suspend fun recetaDelEjemplo(titulo: String = "Torta de manjar"): Long {
        catalogo.sembrar(Ingrediente(nombre = "Harina $titulo", valorPorGramo = 1.0))
        val harina = catalogo.obtenerTodosUnaVez().first { it.nombre == "Harina $titulo" }
        val id = (recetas.crear(titulo) as ResultadoCrearReceta.Creada).recetaId
        val seccion = recetas.obtenerSecciones(id).single()
        recetas.agregarIngrediente(seccion.id, harina.id, 3000.0)   // costo 3.000
        recetas.guardarRendimiento(id, "8", "1.000")
        recetas.crearPrecio(id, ModoPrecio.TROZO, "1", "1.250")     // 1.250 × 8 = 10.000
        return id
    }

    // --- Los empleados (10.2) ---

    @Test
    fun `el generico ya esta, sin que nadie lo cree`() = runBlocking {
        // Sin el sembrado, la garantía de "siempre presente" sería falsa y la sección arrancaría
        // vacía: por eso la prueba mira la lista, no el sembrado.
        val todos = repositorio.observarTodos().first()

        assertEquals(1, todos.size)
        assertTrue(todos.single().esGenerico)
    }

    @Test
    fun `crear uno lo deja despues del generico`() = runBlocking {
        repositorio.crear("Marcela")

        val todos = repositorio.observarTodos().first()
        assertTrue("El estándar va fijo arriba", todos.first().esGenerico)
        assertEquals("Marcela", todos.last().nombre)
    }

    @Test
    fun `un nombre repetido se avisa pero no se prohibe`() = runBlocking {
        repositorio.crear("Marcela")

        val segundo = repositorio.crear("marcela")

        // Dos personas pueden llamarse igual: se devuelve el que hay para poder preguntar, no
        // se rechaza. Es distinto de los ingredientes, donde el nombre sí es la identidad.
        assertTrue(segundo is ResultadoCrearEmpleado.YaExiste)
        assertEquals(
            "Marcela",
            (segundo as ResultadoCrearEmpleado.YaExiste).existente.nombre
        )
        assertEquals("Y no se creó nada todavía", 2, repositorio.observarTodos().first().size)
    }

    @Test
    fun `confirmando, el repetido si se crea`() = runBlocking {
        repositorio.crear("Marcela")

        val segundo = repositorio.crearAunqueSeRepita("Marcela")

        assertTrue(segundo is ResultadoCrearEmpleado.Creado)
        assertEquals(3, repositorio.observarTodos().first().size)
    }

    @Test
    fun `un nombre vacio se rechaza`() = runBlocking {
        val resultado = repositorio.crear("   ")

        assertTrue(resultado is ResultadoCrearEmpleado.NoValido)
        assertEquals(1, repositorio.observarTodos().first().size)
    }

    @Test
    fun `al generico no se le cambia el nombre`() = runBlocking {
        val resultado = repositorio.renombrar(empleadoDao.generico.id, "Otro nombre")

        assertTrue(resultado is Resultado.NoSePudo)
        assertEquals("Estándar", repositorio.obtener(empleadoDao.generico.id)!!.nombre)
    }

    @Test
    fun `al generico no se le borra, ni pidiendolo`() = runBlocking {
        val resultado = repositorio.eliminar(empleadoDao.generico.id)

        assertTrue(resultado is Resultado.NoSePudo)
        assertNotNull("Sigue estando", repositorio.obtener(empleadoDao.generico.id))
    }

    @Test
    fun `renombrar a uno normal si se puede, y queda en el historial`() = runBlocking {
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id

        repositorio.renombrar(id, "Marcela Pérez")

        assertEquals("Marcela Pérez", repositorio.obtener(id)!!.nombre)
        assertTrue(historial.eventos.any { it.tipo == TipoEvento.EDICION })
    }

    @Test
    fun `borrar un empleado se lleva sus sueldos y lo dice`() = runBlocking {
        // La cascada es de la base, no del repositorio, y por eso el falso la imita: sin ella
        // esta prueba aprobaría una versión que deja sueldos huérfanos.
        val recetaId = recetaDelEjemplo()
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id
        repositorio.guardarSueldo(id, recetaId, "3.000")

        assertEquals(1, repositorio.cuantasRecetasTiene(id))
        repositorio.eliminar(id)

        assertNull(repositorio.obtener(id))
        assertEquals(0, repositorio.cuantasRecetasTiene(id))
        val evento = historial.eventos.last { it.tipo == TipoEvento.ELIMINACION }
        assertTrue(
            "El aviso dice qué se fue con él",
            evento.detalleAdicional!!.contains("1 sueldo")
        )
    }

    // --- Los sueldos (10.1) ---

    @Test
    fun `asignar un sueldo dentro de la ganancia se guarda con su reparto`() = runBlocking {
        val recetaId = recetaDelEjemplo()
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id

        val resultado = repositorio.guardarSueldo(id, recetaId, "3.000")

        assertTrue(resultado is Resultado.Listo)
        val fila = repositorio.observarRecetasDe(id).first().single()
        assertEquals("Torta de manjar", fila.titulo)
        assertEquals(7000.0, fila.gananciaTotal, 0.001)
        assertEquals(3000.0, fila.reparto!!.gananciaEmpleado, 0.001)
        assertEquals("Y el dueño se lleva el resto más el costo", 7000.0, fila.reparto!!.yoMeLlevo, 0.001)
    }

    @Test
    fun `pasarse de la ganancia se rechaza y no se guarda nada`() = runBlocking {
        val recetaId = recetaDelEjemplo()
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id

        val resultado = repositorio.guardarSueldo(id, recetaId, "9.000")

        assertTrue(resultado is Resultado.NoSePudo)
        assertEquals("Nada quedó escrito", 0, repositorio.cuantasRecetasTiene(id))
    }

    @Test
    fun `el tope se mide contra la ganancia de ahora, no contra una guardada`() = runBlocking {
        // Este es el caso que solo se ve cruzando tablas: el sueldo cabía cuando se asignó, y
        // después subió el costo del ingrediente. Un tope congelado lo habría dejado pasar.
        val recetaId = recetaDelEjemplo()
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id
        assertTrue(repositorio.guardarSueldo(id, recetaId, "6.000") is Resultado.Listo)

        // La harina pasa de 1 a 2 por gramo: el costo sube a 6.000 y la ganancia baja a 4.000.
        val harina = catalogo.obtenerTodosUnaVez().first()
        catalogo.actualizar(harina.copy(valorPorGramo = 2.0))

        val resultado = repositorio.guardarSueldo(id, recetaId, "6.000")
        assertTrue("Ya no cabe", resultado is Resultado.NoSePudo)
    }

    @Test
    fun `un sueldo que dejo de caber se muestra sin reparto en vez de reventar`() = runBlocking {
        // Lo ya guardado no se borra solo cuando el costo sube: la pantalla tiene que poder
        // mostrarlo para que alguien lo arregle, y `calcularSueldo` ahí lanza con razón.
        val recetaId = recetaDelEjemplo()
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id
        repositorio.guardarSueldo(id, recetaId, "6.000")

        // El costo pasa a 12.000: la receta se vende bajo su costo.
        val harina = catalogo.obtenerTodosUnaVez().first()
        catalogo.actualizar(harina.copy(valorPorGramo = 4.0))

        val fila = repositorio.observarRecetasDe(id).first().single()
        assertTrue("Se muestra igual, sin reparto", fila.sinRepartoPosible)
        assertEquals("Y con su nombre, para poder arreglarlo", "Torta de manjar", fila.titulo)
    }

    @Test
    fun `una receta sin precio no se puede asignar, y se dice por que`() = runBlocking {
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id
        val sinPrecio = (recetas.crear("Alfajores") as ResultadoCrearReceta.Creada).recetaId

        val resultado = repositorio.guardarSueldo(id, sinPrecio, "100")

        assertTrue(resultado is Resultado.NoSePudo)
        assertTrue(
            (resultado as Resultado.NoSePudo).motivo.contains("precio")
        )
    }

    @Test
    fun `cambiar el sueldo pisa el anterior en vez de dejar dos`() = runBlocking {
        val recetaId = recetaDelEjemplo()
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id

        repositorio.guardarSueldo(id, recetaId, "3.000")
        repositorio.guardarSueldo(id, recetaId, "4.000")

        val filas = repositorio.observarRecetasDe(id).first()
        assertEquals("Un empleado tiene un solo sueldo por receta", 1, filas.size)
        assertEquals(4000.0, filas.single().sueldo.gananciaEmpleado, 0.001)
    }

    @Test
    fun `borrar la receta saca su sueldo de la lista del empleado`() = runBlocking {
        val recetaId = recetaDelEjemplo()
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id
        repositorio.guardarSueldo(id, recetaId, "3.000")

        recetas.confirmarEliminacion(recetaId)
        empleadoDao.alBorrarLaReceta(recetaId)   // la cascada de 5.4

        assertTrue(repositorio.observarRecetasDe(id).first().isEmpty())
    }

    @Test
    fun `no se ofrecen las recetas que el empleado ya tiene`() = runBlocking {
        val unaId = recetaDelEjemplo("Torta de manjar")
        recetaDelEjemplo("Bizcocho")
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id
        repositorio.guardarSueldo(id, unaId, "3.000")

        val faltan = repositorio.recetasQueFaltanPor(id)

        assertEquals(listOf("Bizcocho"), faltan.map { it.titulo })
    }

    // --- La simulación múltiple (10.3) ---

    @Test
    fun `la simulacion suma las recetas del detalle`() = runBlocking {
        val torta = recetaDelEjemplo("Torta de manjar")
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id
        repositorio.guardarSueldo(id, torta, "3.000")
        repositorio.guardarDiasCompartidos(id, 4)
        repositorio.guardarUnidadesEnLaSimulacion(id, torta, unidadesPorDia = 2)

        val resultado = repositorio.simulacionDeTodasSusRecetas(id)

        assertEquals(20000.0, resultado.ingresoDiario, 0.001)
        assertEquals(6000.0, resultado.empleadoDiario, 0.001)
        assertEquals(4, resultado.diasPorSemana)
        assertEquals(80000.0, resultado.ingresoSemanal, 0.001)
    }

    @Test
    fun `una receta del detalle sin sueldo asignado va con cero para el empleado`() = runBlocking {
        // Asignar una receta a la simulación y no asignarle sueldo significa justamente eso: el
        // dueño se lleva todo. No es un hueco que haya que rellenar.
        val torta = recetaDelEjemplo()
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id
        repositorio.guardarUnidadesEnLaSimulacion(id, torta, unidadesPorDia = 1)

        val resultado = repositorio.simulacionDeTodasSusRecetas(id)

        assertEquals(10000.0, resultado.ingresoDiario, 0.001)
        assertEquals(0.0, resultado.empleadoDiario, 0.001)
        assertEquals(10000.0, resultado.yoMeLlevoDiario, 0.001)
    }

    @Test
    fun `una receta sin precio en el detalle no voltea el total`() = runBlocking {
        val torta = recetaDelEjemplo()
        val sinPrecio = (recetas.crear("Alfajores") as ResultadoCrearReceta.Creada).recetaId
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id
        repositorio.guardarSueldo(id, torta, "3.000")
        repositorio.guardarUnidadesEnLaSimulacion(id, torta, 1)
        repositorio.guardarUnidadesEnLaSimulacion(id, sinPrecio, 5)

        val resultado = repositorio.simulacionDeTodasSusRecetas(id)

        assertEquals("La torta se calcula igual", 10000.0, resultado.ingresoDiario, 0.001)
        assertEquals(listOf("Alfajores"), resultado.omitidas.map { it.titulo })
        assertEquals(MotivoDeOmision.SIN_PRECIO, resultado.omitidas.single().motivo)
    }

    @Test
    fun `sin dias configurados se toma uno, no cero`() = runBlocking {
        val torta = recetaDelEjemplo()
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id
        repositorio.guardarUnidadesEnLaSimulacion(id, torta, 1)

        val resultado = repositorio.simulacionDeTodasSusRecetas(id)

        assertEquals(1, resultado.diasPorSemana)
        assertEquals("Y la semana no queda en cero", 10000.0, resultado.ingresoSemanal, 0.001)
    }

    @Test
    fun `los dias de la simulacion multiple son otros que los de cada receta`() = runBlocking {
        // Es el error que la especificación avisa con todas las letras: son dos "días" distintos
        // y cambiar uno no puede mover el otro.
        val torta = recetaDelEjemplo()
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id
        repositorio.guardarSueldo(id, torta, "3.000", diasPorSemana = 6, unidadesPorDia = 9)
        repositorio.guardarDiasCompartidos(id, 4)
        repositorio.guardarUnidadesEnLaSimulacion(id, torta, unidadesPorDia = 2)

        val resultado = repositorio.simulacionDeTodasSusRecetas(id)

        assertEquals("Usa los compartidos, no los de la receta", 4, resultado.diasPorSemana)
        assertEquals("Y las unidades del detalle, no las del sueldo", 20000.0, resultado.ingresoDiario, 0.001)
        val fila = repositorio.observarRecetasDe(id).first().single()
        assertEquals("Los de la receta siguen donde estaban", 6, fila.sueldo.diasPorSemana)
    }

    @Test
    fun `un empleado sin recetas simula en cero sin omitir nada`() = runBlocking {
        val id = (repositorio.crear("Marcela") as ResultadoCrearEmpleado.Creado).id

        val resultado = repositorio.simulacionDeTodasSusRecetas(id)

        assertEquals(0.0, resultado.ingresoDiario, 0.001)
        assertFalse(resultado.hayOmitidas)
    }
}
