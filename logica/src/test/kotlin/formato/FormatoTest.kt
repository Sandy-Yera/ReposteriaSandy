package formato

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoTest {

    // --- Los ejemplos exactos de la especificación original ---

    @Test
    fun `sin decimales omite la coma`() {
        assertEquals("1.000", formatearNumero(1000.0))
        assertEquals("250", formatearNumero(250.0))
    }

    @Test
    fun `redondea al segundo decimal mas cercano`() {
        // "si es 1,546 sea 1,55"
        assertEquals("1,55", formatearNumero(1.546))
        assertEquals("1,55", formatearNumero(1.55))
    }

    @Test
    fun `usa punto para miles y coma para decimales`() {
        assertEquals("1.000,50", formatearNumero(1000.5))
        assertEquals("1.234.567", formatearNumero(1234567.0))
        assertEquals("1.234.567,89", formatearNumero(1234567.89))
    }

    // --- Negativos: el bug que se corrigió en la revisión ---

    @Test
    fun `conserva el signo en valores entre menos uno y cero`() {
        // Antes devolvía "0,56": mostraba una pérdida como si fuera ganancia.
        assertEquals("-0,56", formatearNumero(-0.56))
        assertEquals("-0,01", formatearNumero(-0.01))
    }

    @Test
    fun `conserva el signo en negativos grandes`() {
        assertEquals("-1.234,56", formatearNumero(-1234.56))
        assertEquals("-1.000", formatearNumero(-1000.0))
    }

    // --- Casos límite ---

    @Test
    fun `cero se muestra sin coma`() {
        assertEquals("0", formatearNumero(0.0))
    }

    @Test
    fun `un decimal se completa a dos cifras`() {
        // 1,5 se muestra como "1,50", no como "1,5"
        assertEquals("1,50", formatearNumero(1.5))
        assertEquals("0,05", formatearNumero(0.05))
    }

    @Test
    fun `el redondeo que llega a entero no deja coma colgando`() {
        // 1000,999 redondea a 1.001 exacto: no debe quedar "1.001,00"
        assertEquals("1.001", formatearNumero(1000.999))
        assertEquals("1", formatearNumero(0.999))
    }

    @Test
    fun `montos tipicos de la app`() {
        assertEquals("12.400", formatearNumero(12400.0))   // costo de una torta
        assertEquals("1,55", formatearNumero(1.5468))      // valor por gramo
        assertEquals("40.000", formatearNumero(40000.0))   // ingreso mensual simulado
    }
}
