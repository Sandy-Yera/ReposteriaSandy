package com.sandyyera.reposteria.logica.calendario

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los metadatos del día de una venta (16.3).
 *
 * Se calculan y no se guardan: con la fecha alcanza. Guardarlos serían seis columnas diciendo lo
 * mismo que una, y que además quedan mal si alguien corrige la fecha después.
 */
class CalendarioTest {

    @Test
    fun `el dieciocho de dos mil veintiseis es viernes`() {
        val datos = datosDelDia(LocalDate.of(2026, 9, 18))

        assertEquals("18/9/2026", datos.comoNumero)
        assertEquals("viernes 18 de septiembre", datos.comoTexto)
        assertEquals(18, datos.dia)
        assertEquals(9, datos.mes)
        assertTrue(datos.esFeriado)
        assertEquals("Independencia Nacional", datos.comoSeLlamaElDia)
        assertFalse("Viernes no es fin de semana", datos.esFinDeSemana)
    }

    @Test
    fun `un dia comun no tiene nada que decir de si mismo`() {
        val datos = datosDelDia(LocalDate.of(2026, 3, 11))

        assertNull(datos.especial)
        assertFalse(datos.esFeriado)
        assertEquals("miércoles 11 de marzo", datos.comoTexto)
    }

    @Test
    fun `el sabado y el domingo se distinguen del feriado`() {
        // Son cosas distintas: un sábado no es un festivo, y mezclarlos perdería justo la
        // diferencia que sirve para comparar.
        val sabado = datosDelDia(LocalDate.of(2026, 3, 14))

        assertTrue(sabado.esFinDeSemana)
        assertFalse(sabado.esFeriado)
        assertNull(sabado.especial)
    }

    // --- Pascua, de la que cuelgan Viernes y Sábado Santo ---

    @Test
    fun `el domingo de pascua calza con los anios conocidos`() {
        // Contra fechas reales y no contra la fórmula: comprobar el algoritmo consigo mismo no
        // comprueba nada. Estos cuatro cubren siglo distinto, año bisiesto y los dos extremos
        // del rango en que Pascua puede caer (22 de marzo a 25 de abril).
        assertEquals(LocalDate.of(2024, 3, 31), domingoDePascua(2024))
        assertEquals(LocalDate.of(2025, 4, 20), domingoDePascua(2025))
        assertEquals(LocalDate.of(2026, 4, 5), domingoDePascua(2026))
        assertEquals(LocalDate.of(2038, 4, 25), domingoDePascua(2038))
    }

    @Test
    fun `viernes y sabado santo se mueven con la pascua`() {
        val feriados = feriadosDe(2026)

        assertEquals("Viernes Santo", feriados[LocalDate.of(2026, 4, 3)]?.nombre)
        assertEquals("Sábado Santo", feriados[LocalDate.of(2026, 4, 4)]?.nombre)
        assertNull("El domingo de Pascua no es feriado legal", feriados[LocalDate.of(2026, 4, 5)])
    }

    // --- Las fechas que mueven una repostería ---

    @Test
    fun `el dia de la madre es el segundo domingo de mayo`() {
        assertEquals(LocalDate.of(2026, 5, 10), diaDeLaMadre(2026))
        assertEquals(LocalDate.of(2025, 5, 11), diaDeLaMadre(2025))
        // 2027 empieza mayo en sábado: el primer domingo es el 2 y el segundo el 9. Es el caso
        // que se equivoca si uno cuenta "el 8 + lo que falte" en vez de buscar el domingo.
        assertEquals(LocalDate.of(2027, 5, 9), diaDeLaMadre(2027))
    }

    @Test
    fun `el dia del padre es el tercer domingo de junio`() {
        assertEquals(LocalDate.of(2026, 6, 21), diaDelPadre(2026))
        assertEquals(LocalDate.of(2025, 6, 15), diaDelPadre(2025))
    }

    @Test
    fun `una fecha comercial se marca sin ser feriado`() {
        val madre = datosDelDia(diaDeLaMadre(2026))

        assertEquals("Día de la Madre", madre.comoSeLlamaElDia)
        assertFalse("No es feriado legal, y esa diferencia es el dato", madre.esFeriado)
        assertTrue("Cae domingo, siempre", madre.esFinDeSemana)
    }

    @Test
    fun `si un feriado y una fecha comercial caen juntos manda el feriado`() {
        // Pasa de verdad: el día de la madre puede caer el 1 de mayo. Manda el feriado porque
        // es el que decide si se abre.
        val datos = datosDelDia(LocalDate.of(2033, 5, 1))

        assertEquals(LocalDate.of(2033, 5, 8), diaDeLaMadre(2033))
        assertEquals("Día del Trabajo", datos.comoSeLlamaElDia)
        assertTrue(datos.esFeriado)
    }

    @Test
    fun `los quince feriados permanentes estan y ninguno se repite`() {
        val feriados = feriadosDe(2026)

        assertEquals(15, feriados.size)
        assertTrue("Todos marcados como feriado", feriados.values.all { it.esFeriado })
        assertEquals("Sin nombres repetidos", 15, feriados.values.map { it.nombre }.toSet().size)
    }
}
