package com.sandyyera.reposteria.logica.formato

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

    // --- Redondeo compartido ---

    @Test
    fun `redondear a dos decimales da el mismo numero que se muestra`() {
        assertEquals(1.55, redondearADosDecimales(1.5468), 0.0)
        assertEquals(1.67, redondearADosDecimales(1.6666666), 0.0)
        assertEquals(1000.0, redondearADosDecimales(1000.0), 0.0)
        assertEquals(-0.56, redondearADosDecimales(-0.5551), 0.0)
    }

    // --- Formato mientras se escribe ---
    // Cada caso de acá es una tecla que se presiona; lo que importa es lo que queda a la
    // vista justo después de presionarla.

    @Test
    fun `pone el punto de mil al llegar al cuarto digito`() {
        assertEquals("1", formatearMientrasSeEscribe("1"))
        assertEquals("10", formatearMientrasSeEscribe("10"))
        assertEquals("100", formatearMientrasSeEscribe("100"))
        assertEquals("1.000", formatearMientrasSeEscribe("1000"))
        assertEquals("10.000", formatearMientrasSeEscribe("10000"))
    }

    @Test
    fun `agrupa de tres en tres en numeros largos`() {
        assertEquals("1.234.567", formatearMientrasSeEscribe("1234567"))
        assertEquals("12.345.678", formatearMientrasSeEscribe("12345678"))
        assertEquals("123.456.789", formatearMientrasSeEscribe("123456789"))
    }

    @Test
    fun `no se come la coma recien escrita`() {
        // Este es el motivo de que esta función exista. formatearNumero devolvía "1.000"
        // y la coma desaparecía en el momento de tocarla, dejando imposible el decimal.
        assertEquals("1.000,", formatearMientrasSeEscribe("1000,"))
        assertEquals("0,", formatearMientrasSeEscribe("0,"))
    }

    @Test
    fun `no inventa decimales que no se escribieron`() {
        // formatearNumero devolvía "1.000,50" al escribir "1000,5".
        assertEquals("1.000,5", formatearMientrasSeEscribe("1000,5"))
        assertEquals("1.000,50", formatearMientrasSeEscribe("1000,50"))
        assertEquals("0,0", formatearMientrasSeEscribe("0,0"))
    }

    @Test
    fun `corta en dos decimales, que es lo que se guarda`() {
        assertEquals("1,55", formatearMientrasSeEscribe("1,555"))
        assertEquals("1,55", formatearMientrasSeEscribe("1,5599999"))
    }

    @Test
    fun `aplicarla sobre su propio resultado no cambia nada`() {
        // Importa porque se llama en cada tecla sobre el texto que ella misma dejó.
        for (escrito in listOf("1.000", "1.000,5", "1.000,55", "0,05", "123.456.789", "0,", "")) {
            assertEquals(
                "reformatear '$escrito' debe devolver lo mismo",
                escrito,
                formatearMientrasSeEscribe(escrito)
            )
        }
    }

    @Test
    fun `descarta lo que no es un digito ni la coma`() {
        // El signo menos también: los tres campos que la usan no aceptan negativos.
        assertEquals("15", formatearMientrasSeEscribe("1a5"))
        assertEquals("1.000", formatearMientrasSeEscribe("-1000"))
        assertEquals("", formatearMientrasSeEscribe("abc"))
        assertEquals("", formatearMientrasSeEscribe(""))
    }

    @Test
    fun `solo la primera coma cuenta`() {
        assertEquals("1,55", formatearMientrasSeEscribe("1,5,5"))
    }

    @Test
    fun `saca los ceros de mas pero deja el de cero coma algo`() {
        assertEquals("5", formatearMientrasSeEscribe("05"))
        assertEquals("1.000", formatearMientrasSeEscribe("0001000"))
        assertEquals("0", formatearMientrasSeEscribe("000"))
        assertEquals("0,5", formatearMientrasSeEscribe("0,5"))
        // Empezar por la coma pone el 0 adelante solo.
        assertEquals("0,5", formatearMientrasSeEscribe(",5"))
    }

    @Test
    fun `lo que deja escrito se puede convertir a numero`() {
        // La cadena completa: lo que se escribe, lo que se ve, y lo que se guarda.
        val aLaVista = formatearMientrasSeEscribe("1000,5")
        assertEquals("1.000,5", aLaVista)
        val numero = com.sandyyera.reposteria.logica.validaciones.textoANumero(aLaVista)
        assertEquals(1000.5, numero!!, 0.0)
        assertEquals("1.000,50", formatearNumero(numero))
    }

    @Test
    fun `borrar hacia atras deshace bien el agrupado`() {
        // Al borrar el último dígito de "1.000" el campo queda con "1.00" y hay que
        // devolver "100", no "1.00".
        assertEquals("100", formatearMientrasSeEscribe("1.00"))
        assertEquals("10", formatearMientrasSeEscribe("1.0"))
        // Y borrar el punto en sí no debe borrar un dígito.
        assertEquals("1.000", formatearMientrasSeEscribe("1000"))
    }
}
