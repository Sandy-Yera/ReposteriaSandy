package com.sandyyera.reposteria.logica.validaciones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // --- Valor por gramo, tal como viene del campo de texto ---

    @Test
    fun `el valor escrito con coma pasa`() {
        assertNull(errorEnValorPorGramoTexto("1,55"))
        assertNull(errorEnValorPorGramoTexto("1.234,56"))
        assertNull(errorEnValorPorGramoTexto("250"))
    }

    @Test
    fun `el cero escrito pasa, pero el campo en blanco no`() {
        // Escribir 0 es una decisión; dejarlo vacío casi siempre es un olvido.
        assertNull(errorEnValorPorGramoTexto("0"))
        assertNotNull(errorEnValorPorGramoTexto(""))
        assertNotNull(errorEnValorPorGramoTexto("   "))
    }

    @Test
    fun `el campo vacio y el que no es numero avisan cosas distintas`() {
        // Si dijeran lo mismo, la persona no sabría si le falta escribir o si escribió mal.
        assertNotEquals(errorEnValorPorGramoTexto(""), errorEnValorPorGramoTexto("abc"))
    }

    @Test
    fun `el valor escrito negativo no pasa`() {
        assertNotNull(errorEnValorPorGramoTexto("-5"))
    }

    // --- Formulario completo ---

    @Test
    fun `un formulario bien lleno sirve`() {
        val errores = revisarIngrediente("Harina", "1,55")
        assertNull(errores.nombre)
        assertNull(errores.valorPorGramo)
        assertTrue(errores.sirve)
    }

    @Test
    fun `el error va en el campo que lo causo`() {
        // Nombre malo, valor bueno: solo debe quejarse del nombre.
        val soloNombre = revisarIngrediente("", "1,55")
        assertNotNull(soloNombre.nombre)
        assertNull(soloNombre.valorPorGramo)
        assertFalse(soloNombre.sirve)

        // Y al revés.
        val soloValor = revisarIngrediente("Harina", "abc")
        assertNull(soloValor.nombre)
        assertNotNull(soloValor.valorPorGramo)
        assertFalse(soloValor.sirve)
    }

    @Test
    fun `un formulario recien abierto no sirve todavia`() {
        // Los dos campos vacíos: el botón de guardar tiene que arrancar deshabilitado.
        assertFalse(revisarIngrediente("", "").sirve)
    }

    @Test
    fun `avisa de los dos campos a la vez`() {
        val errores = revisarIngrediente("", "abc")
        assertNotNull(errores.nombre)
        assertNotNull(errores.valorPorGramo)
    }
}
