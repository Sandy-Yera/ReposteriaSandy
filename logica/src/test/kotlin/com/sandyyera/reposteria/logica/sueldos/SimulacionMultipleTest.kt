package com.sandyyera.reposteria.logica.sueldos

import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.PrecioVigente
import com.sandyyera.reposteria.logica.simulacion.SEMANAS_POR_MES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La simulación de varias recetas de un mismo empleado (10.3).
 *
 * Todo se arma **a mano y sin base de datos**, que es la condición de cierre de la Fase 11: si
 * hiciera falta una base para probar esto, la regla del snapshot (6.4) estaría rota — la lectura
 * tiene que ocurrir afuera y el agregado ser una función pura.
 *
 * Lo que estas pruebas cuidan de verdad no son las multiplicaciones, que son fáciles, sino
 * **que una receta a medio configurar no voltee el total de las demás**. Ese es el caso que la
 * especificación pide explícitamente y el que se rompe solo al agregar un cálculo nuevo.
 */
class SimulacionMultipleTest {

    /** Ingreso bruto 10.000 y costo 3.000: la receta del ejemplo de 10.1. */
    private fun torta(titulo: String = "Torta de manjar") = DatosCalculoReceta(
        recetaId = 1L,
        titulo = titulo,
        costoTotal = 3000.0,
        trozos = 8,
        precios = listOf(PrecioVigente(ModoPrecio.TROZO, 1, 1250.0))  // 1.250 × 8 = 10.000
    )

    /** Ingreso bruto 4.000 y costo 1.000. */
    private fun bizcocho(titulo: String = "Bizcocho") = DatosCalculoReceta(
        recetaId = 2L,
        titulo = titulo,
        costoTotal = 1000.0,
        trozos = 4,
        precios = listOf(PrecioVigente(ModoPrecio.TROZO, 1, 1000.0))  // 1.000 × 4 = 4.000
    )

    /** Ingreso bruto 2.000 y costo 500. */
    private fun chocolate(titulo: String = "Chocolate") = DatosCalculoReceta(
        recetaId = 3L,
        titulo = titulo,
        costoTotal = 500.0,
        trozos = 2,
        precios = listOf(PrecioVigente(ModoPrecio.TROZO, 1, 1000.0))  // 1.000 × 2 = 2.000
    )

    /** Una receta a la que nunca se le puso precio. */
    private fun sinPrecio(titulo: String) = DatosCalculoReceta(
        recetaId = 9L,
        titulo = titulo,
        costoTotal = 2000.0,
        trozos = 4,
        precios = emptyList()
    )

    /** Una receta con precio, pero que no alcanza a cubrir el costo. */
    private fun bajoElCosto(titulo: String) = DatosCalculoReceta(
        recetaId = 8L,
        titulo = titulo,
        costoTotal = 5000.0,
        trozos = 4,
        precios = listOf(PrecioVigente(ModoPrecio.TROZO, 1, 500.0))  // 500 × 4 = 2.000 < 5.000
    )

    // --- La suma ---

    @Test
    fun `una sola receta se lee como su sueldo, multiplicado por las unidades`() {
        val resultado = simulacionMultiple(
            recetas = listOf(RecetaEnLaSimulacion(torta(), gananciaEmpleado = 3000.0, unidadesPorDia = 2)),
            diasPorSemana = 4
        )

        // Por unidad: ingreso 10.000, el dueño 7.000, el empleado 3.000. Dos por día.
        assertEquals(20000.0, resultado.ingresoDiario, 0.001)
        assertEquals(14000.0, resultado.yoMeLlevoDiario, 0.001)
        assertEquals(6000.0, resultado.empleadoDiario, 0.001)
    }

    @Test
    fun `tres recetas suman bien, cada una con sus unidades`() {
        // Es el ejemplo de la especificación: "venderé 4 días, y esos 4 días serán 2 bizcochos,
        // 1 torta, 5 chocolates por día".
        val resultado = simulacionMultiple(
            recetas = listOf(
                RecetaEnLaSimulacion(bizcocho(), gananciaEmpleado = 1000.0, unidadesPorDia = 2),
                RecetaEnLaSimulacion(torta(), gananciaEmpleado = 3000.0, unidadesPorDia = 1),
                RecetaEnLaSimulacion(chocolate(), gananciaEmpleado = 500.0, unidadesPorDia = 5)
            ),
            diasPorSemana = 4
        )

        // Bizcocho: 4.000 × 2 = 8.000. Torta: 10.000 × 1. Chocolate: 2.000 × 5 = 10.000.
        assertEquals(28000.0, resultado.ingresoDiario, 0.001)
        // Empleado: 1.000×2 + 3.000×1 + 500×5 = 7.500.
        assertEquals(7500.0, resultado.empleadoDiario, 0.001)
        // Dueño por unidad: bizcocho 3.000 (1.000 de costo + 2.000 de resto), torta 7.000,
        // chocolate 1.500 (500 + 1.000). Con sus unidades: 6.000 + 7.000 + 7.500 = 20.500.
        assertEquals(20500.0, resultado.yoMeLlevoDiario, 0.001)
    }

    @Test
    fun `lo que se lleva cada uno siempre suma el ingreso`() {
        // La invariante del reparto, mirada sobre el total y no receta por receta: si alguna
        // suma se escribiera mal, acá el ingreso dejaría de cuadrar con sus dos mitades.
        val resultado = simulacionMultiple(
            recetas = listOf(
                RecetaEnLaSimulacion(torta(), gananciaEmpleado = 2500.0, unidadesPorDia = 3),
                RecetaEnLaSimulacion(bizcocho(), gananciaEmpleado = 0.0, unidadesPorDia = 7),
                RecetaEnLaSimulacion(chocolate(), gananciaEmpleado = 1500.0, unidadesPorDia = 1)
            ),
            diasPorSemana = 5
        )

        assertEquals(
            resultado.ingresoDiario,
            resultado.yoMeLlevoDiario + resultado.empleadoDiario,
            0.001
        )
        assertEquals(
            "Y también en la semana",
            resultado.ingresoSemanal,
            resultado.yoMeLlevoSemanal + resultado.empleadoSemanal,
            0.001
        )
    }

    @Test
    fun `sin recetas asignadas todo queda en cero y nada se omite`() {
        val resultado = simulacionMultiple(recetas = emptyList(), diasPorSemana = 4)

        assertEquals(0.0, resultado.ingresoDiario, 0.001)
        assertFalse("No hay nada que omitir", resultado.hayOmitidas)
        assertFalse("Y esto no es 'todas omitidas'", resultado.todasOmitidas)
    }

    // --- Lo diario, lo semanal y lo mensual ---

    @Test
    fun `la semana y el mes se derivan del dia, no se guardan aparte`() {
        // Guardar las tres versiones permitiría que quedaran desincronizadas, y nadie revisa eso
        // hasta que un número no cuadra y no hay forma de saber cuál de los tres está mal.
        val resultado = simulacionMultiple(
            recetas = listOf(RecetaEnLaSimulacion(torta(), 3000.0, unidadesPorDia = 1)),
            diasPorSemana = 4
        )

        assertEquals(40000.0, resultado.ingresoSemanal, 0.001)
        assertEquals(40000.0 * SEMANAS_POR_MES, resultado.ingresoMensual, 0.001)
        assertEquals(28000.0, resultado.yoMeLlevoSemanal, 0.001)
        assertEquals(12000.0, resultado.empleadoSemanal, 0.001)
    }

    @Test
    fun `cero dias por semana se trata como uno`() {
        // Proyectar con 0 días daría cero en todo y se leería como "no gana nada", que es una
        // conclusión y no lo que se preguntó.
        val resultado = simulacionMultiple(
            recetas = listOf(RecetaEnLaSimulacion(torta(), 3000.0, unidadesPorDia = 1)),
            diasPorSemana = 0
        )

        assertEquals(1, resultado.diasPorSemana)
        assertEquals(10000.0, resultado.ingresoSemanal, 0.001)
    }

    // --- Lo que no puede voltear el total (10.3) ---

    @Test
    fun `una receta sin precio queda fuera y las demas se calculan igual`() {
        // El caso que pide la especificación con todas las letras: con diez recetas asignadas y
        // una a medio configurar, fallar dejaría al empleado sin ninguna cifra.
        val resultado = simulacionMultiple(
            recetas = listOf(
                RecetaEnLaSimulacion(torta(), gananciaEmpleado = 3000.0, unidadesPorDia = 1),
                RecetaEnLaSimulacion(sinPrecio("Alfajores"), gananciaEmpleado = 0.0, unidadesPorDia = 5),
                RecetaEnLaSimulacion(bizcocho(), gananciaEmpleado = 1000.0, unidadesPorDia = 1)
            ),
            diasPorSemana = 4
        )

        assertEquals("Solo torta y bizcocho", 14000.0, resultado.ingresoDiario, 0.001)
        assertEquals(listOf("Alfajores"), resultado.omitidas.map { it.titulo })
        assertEquals(MotivoDeOmision.SIN_PRECIO, resultado.omitidas.single().motivo)
    }

    @Test
    fun `una receta que se vende bajo su costo tampoco voltea el total`() {
        // No está en la especificación y hace falta por la misma razón que la anterior:
        // `calcularSueldo` lanza cuando no hay ganancia que repartir, y esa excepción se llevaría
        // por delante el total de todas las demás.
        val resultado = simulacionMultiple(
            recetas = listOf(
                RecetaEnLaSimulacion(torta(), gananciaEmpleado = 3000.0, unidadesPorDia = 1),
                RecetaEnLaSimulacion(bajoElCosto("Pie de limón"), gananciaEmpleado = 0.0, unidadesPorDia = 2)
            ),
            diasPorSemana = 4
        )

        assertEquals(10000.0, resultado.ingresoDiario, 0.001)
        assertEquals(listOf("Pie de limón"), resultado.omitidas.map { it.titulo })
        assertEquals(
            MotivoDeOmision.SE_VENDE_BAJO_EL_COSTO,
            resultado.omitidas.single().motivo
        )
    }

    @Test
    fun `los dos motivos se distinguen, porque se arreglan distinto`() {
        // Mezclarlos mandaría a ponerle precio a algo que ya lo tiene.
        val resultado = simulacionMultiple(
            recetas = listOf(
                RecetaEnLaSimulacion(sinPrecio("Alfajores"), 0.0, 1),
                RecetaEnLaSimulacion(bajoElCosto("Pie de limón"), 0.0, 1)
            ),
            diasPorSemana = 4
        )

        assertEquals(
            listOf("sin precio definido", "se vende bajo su costo"),
            resultado.omitidas.map { it.motivo.comoSeLee }
        )
    }

    @Test
    fun `todas omitidas se distingue de no vender nada`() {
        // Un cero a secas se leería como "no vende nada", que es una conclusión. Acá el empleado
        // sí tiene recetas, pero ninguna se puede proyectar todavía.
        val resultado = simulacionMultiple(
            recetas = listOf(RecetaEnLaSimulacion(sinPrecio("Alfajores"), 0.0, 5)),
            diasPorSemana = 4
        )

        assertEquals(0.0, resultado.ingresoDiario, 0.001)
        assertTrue(resultado.todasOmitidas)
    }

    @Test
    fun `una receta que empata su costo si entra, con sueldo cero`() {
        // El borde exacto de lo que se omite: ganancia total 0 es calculable —el dueño recupera
        // el costo y nadie gana— y sacarla del total sería esconder una receta que sí se vende.
        val empata = DatosCalculoReceta(
            recetaId = 7L,
            titulo = "Galletas",
            costoTotal = 2000.0,
            trozos = 4,
            precios = listOf(PrecioVigente(ModoPrecio.TROZO, 1, 500.0))  // 500 × 4 = 2.000
        )

        val resultado = simulacionMultiple(
            recetas = listOf(RecetaEnLaSimulacion(empata, gananciaEmpleado = 0.0, unidadesPorDia = 3)),
            diasPorSemana = 4
        )

        assertFalse("Se puede calcular, así que entra", resultado.hayOmitidas)
        assertEquals(6000.0, resultado.ingresoDiario, 0.001)
        assertEquals("El dueño recupera el costo", 6000.0, resultado.yoMeLlevoDiario, 0.001)
        assertEquals(0.0, resultado.empleadoDiario, 0.001)
    }

    @Test
    fun `pedir mas ganancia de la que hay sigue lanzando, no se esconde`() {
        // Esto no es una receta a medio configurar sino un sueldo mal asignado. Meterlo en
        // `omitidas` dejaría al empleado con un total silenciosamente menor.
        val error = runCatching {
            simulacionMultiple(
                recetas = listOf(
                    RecetaEnLaSimulacion(torta(), gananciaEmpleado = 99000.0, unidadesPorDia = 1)
                ),
                diasPorSemana = 4
            )
        }.exceptionOrNull()

        assertTrue("Tiene que avisar, no callarse", error is IllegalArgumentException)
    }

    @Test
    fun `una receta con cero unidades por dia no aporta pero tampoco se omite`() {
        // "No la vendo hoy" es una decisión tomada, no un dato faltante: listarla como omitida
        // mandaría a arreglar algo que está bien.
        val resultado = simulacionMultiple(
            recetas = listOf(
                RecetaEnLaSimulacion(torta(), gananciaEmpleado = 3000.0, unidadesPorDia = 0),
                RecetaEnLaSimulacion(bizcocho(), gananciaEmpleado = 1000.0, unidadesPorDia = 1)
            ),
            diasPorSemana = 4
        )

        assertEquals(4000.0, resultado.ingresoDiario, 0.001)
        assertFalse(resultado.hayOmitidas)
    }
}
