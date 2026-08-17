package com.sandyyera.reposteria.logica.validaciones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas de los dos campos de "Ganancias simuladas" (6.2 y 8.7).
 *
 * Lo particular de este paso es que **nada explota** con un número absurdo: los dos campos se
 * multiplican contra el ingreso y ya. Por eso las reglas están, y por eso son distintas entre
 * sí — los días no aceptan el 0 y las unidades sí.
 */
class SimulacionTest {

    // --- Días por semana ---

    @Test
    fun `de uno a siete dias pasa`() {
        (1..DIAS_MAXIMOS_POR_SEMANA).forEach { assertNull(errorEnDiasPorSemanaTexto("$it")) }
    }

    @Test
    fun `cero dias se rechaza, y el aviso dice como decir que no se vende`() {
        // Dejar los días en cero deja la simulación entera en cero y parece un error de la
        // app. Lo que corresponde es poner 0 unidades por día, y el aviso lo dice.
        val error = errorEnDiasPorSemanaTexto("0")
        assertNotNull(error)
        assertTrue("El aviso enseña la salida", error!!.contains("unidades"))
    }

    @Test
    fun `una semana no tiene ocho dias`() {
        assertNotNull(errorEnDiasPorSemanaTexto("8"))
        assertNotNull(errorEnDiasPorSemanaTexto("30"))
    }

    @Test
    fun `los dias son enteros`() {
        assertNotNull(errorEnDiasPorSemanaTexto("2,5"))
        assertNotNull(errorEnDiasPorSemanaTexto(""))
        assertNotNull(errorEnDiasPorSemanaTexto("tres"))
        assertNotNull(errorEnDiasPorSemanaTexto("-1"))
    }

    // --- Unidades por día ---

    @Test
    fun `cero unidades si se acepta, y es como se dice que todavia no se vende`() {
        // Al revés que los días: acá el 0 no borra nada de lo configurado, y la simulación
        // da cero, que es la respuesta correcta y no un error.
        assertNull(errorEnUnidadesPorDiaTexto("0"))
    }

    @Test
    fun `una cantidad normal pasa`() {
        assertNull(errorEnUnidadesPorDiaTexto("2"))
        assertNull(errorEnUnidadesPorDiaTexto("$MAXIMAS_UNIDADES_POR_DIA"))
    }

    @Test
    fun `hay un tope contra el dedo pegado`() {
        // Nada explota con un 200 escrito en vez de un 20: sale una proyección mensual
        // creíble y diez veces falsa, que es peor que un error.
        assertNotNull(errorEnUnidadesPorDiaTexto("${MAXIMAS_UNIDADES_POR_DIA + 1}"))
    }

    @Test
    fun `las unidades son enteras y no negativas`() {
        assertNotNull(errorEnUnidadesPorDiaTexto("1,5"))
        assertNotNull(errorEnUnidadesPorDiaTexto("-3"))
        assertNotNull(errorEnUnidadesPorDiaTexto(""))
    }

    // --- Números enormes ---

    @Test
    fun `un numero enorme recibe el aviso del tope, no el de los decimales`() {
        // Con el dedo pegado en la tecla salen once dígitos. Ese número ES entero, así que
        // lo que corresponde decir es que se pasó del tope. Comparar contra
        // `toInt().toDouble()` respondía "tiene que ser un número entero" —un aviso falso,
        // y el único que no enseña cómo arreglarlo.
        assertEquals("Una semana tiene 7 días", errorEnDiasPorSemanaTexto("10000000000"))
        assertEquals(
            "Más de $MAXIMAS_UNIDADES_POR_DIA al día parece un error",
            errorEnUnidadesPorDiaTexto("10000000000")
        )
    }

    // --- Los dos juntos ---

    @Test
    fun `el caso del ejemplo de la arquitectura sirve`() {
        // 5.000 x 4 dias x 2 unidades = 40.000 semanal (8.7).
        assertTrue(revisarSimulacion("4", "2").sirve)
    }

    @Test
    fun `cada aviso queda en su campo`() {
        val errores = revisarSimulacion("0", "1,5")
        assertFalse(errores.sirve)
        assertNotNull(errores.diasPorSemana)
        assertNotNull(errores.unidadesPorDia)
    }

    @Test
    fun `un campo malo no ensucia el otro`() {
        val errores = revisarSimulacion("9", "2")
        assertNotNull(errores.diasPorSemana)
        assertNull(errores.unidadesPorDia)
    }
}
