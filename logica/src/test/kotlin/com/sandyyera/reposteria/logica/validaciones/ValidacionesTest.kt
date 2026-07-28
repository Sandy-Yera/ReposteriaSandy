package com.sandyyera.reposteria.logica.validaciones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidacionesTest {

    // --- Nombre ---

    @Test
    fun `un nombre normal pasa`() {
        assertNull(errorEnNombreIngrediente("Harina"))
        assertNull(errorEnNombreIngrediente("Azúcar flor"))
    }

    @Test
    fun `un nombre vacio o de puros espacios no pasa`() {
        assertNotNull(errorEnNombreIngrediente(""))
        assertNotNull(errorEnNombreIngrediente("   "))
    }

    @Test
    fun `los espacios de los bordes no cuentan`() {
        assertNull(errorEnNombreIngrediente("  Harina  "))
    }

    @Test
    fun `un nombre demasiado largo no pasa`() {
        assertNull(errorEnNombreIngrediente("a".repeat(LARGO_MAXIMO_NOMBRE)))
        assertNotNull(errorEnNombreIngrediente("a".repeat(LARGO_MAXIMO_NOMBRE + 1)))
    }

    // --- Valor por gramo ---

    @Test
    fun `un valor normal pasa`() {
        assertNull(errorEnValorPorGramo(1.55))
        assertNull(errorEnValorPorGramo(1000.0))
    }

    @Test
    fun `el cero pasa a proposito`() {
        // Hay ingredientes que no se costean; ponerlos en cero es la forma de decirlo.
        assertNull(errorEnValorPorGramo(0.0))
    }

    @Test
    fun `un valor negativo no pasa`() {
        assertNotNull(errorEnValorPorGramo(-0.01))
    }

    @Test
    fun `un valor que no es numero no pasa`() {
        assertNotNull(errorEnValorPorGramo(Double.NaN))
        assertNotNull(errorEnValorPorGramo(Double.POSITIVE_INFINITY))
    }

    // --- Texto a número ---

    @Test
    fun `acepta la coma como separador decimal`() {
        // Es lo que se escribe en el teclado del celular, no el punto.
        assertEquals(1.55, textoANumero("1,55")!!, 0.001)
        assertEquals(0.5, textoANumero("0,5")!!, 0.001)
    }

    @Test
    fun `acepta el punto como separador de miles`() {
        assertEquals(1000.0, textoANumero("1.000")!!, 0.001)
        assertEquals(1234.56, textoANumero("1.234,56")!!, 0.001)
    }

    @Test
    fun `acepta numeros sin separadores`() {
        assertEquals(250.0, textoANumero("250")!!, 0.001)
    }

    @Test
    fun `ignora espacios de los bordes`() {
        assertEquals(250.0, textoANumero("  250  ")!!, 0.001)
    }

    @Test
    fun `devuelve nulo si no es un numero`() {
        assertNull(textoANumero(""))
        assertNull(textoANumero("abc"))
        assertNull(textoANumero("1,2,3"))
    }

    @Test
    fun `lo que escribe la persona vuelve igual al mostrarlo`() {
        // Escribir "1.234,56", convertir y volver a formatear debe dar lo mismo.
        val numero = textoANumero("1.234,56")!!
        assertEquals(
            "1.234,56",
            com.sandyyera.reposteria.logica.formato.formatearNumero(numero)
        )
    }
}
