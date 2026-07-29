package com.sandyyera.reposteria.logica.validaciones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecetasTest {

    // --- Título ---

    @Test
    fun `un titulo normal pasa`() {
        assertNull(errorEnTituloReceta("Torta de manjar"))
        assertNull(errorEnTituloReceta("  Bizcocho  "))
    }

    @Test
    fun `un titulo vacio o de puros espacios no pasa`() {
        assertNotNull(errorEnTituloReceta(""))
        assertNotNull(errorEnTituloReceta("   "))
    }

    @Test
    fun `un titulo demasiado largo no pasa`() {
        assertNull(errorEnTituloReceta("a".repeat(LARGO_MAXIMO_NOMBRE)))
        assertNotNull(errorEnTituloReceta("a".repeat(LARGO_MAXIMO_NOMBRE + 1)))
    }

    // --- Nombre de sección ---

    @Test
    fun `un nombre de seccion normal pasa`() {
        assertNull(errorEnNombreSeccion("Bizcocho"))
        assertNull(errorEnNombreSeccion("Crema pastelera"))
    }

    @Test
    fun `una seccion sin nombre no pasa`() {
        assertNotNull(errorEnNombreSeccion(""))
        assertNotNull(errorEnNombreSeccion("  "))
    }

    // --- Cantidad en gramos ---

    @Test
    fun `una cantidad normal pasa`() {
        assertNull(errorEnCantidadEnGramosTexto("250"))
        assertNull(errorEnCantidadEnGramosTexto("1.000"))
        assertNull(errorEnCantidadEnGramosTexto("12,5"))
    }

    @Test
    fun `cero gramos no pasa, a diferencia del valor por gramo`() {
        // Un ingrediente en cantidad cero simplemente no está en la receta. Dejarlo
        // guardado con 0 es una fila que no suma nada y confunde al leer la lista.
        assertNotNull(errorEnCantidadEnGramosTexto("0"))
        // El valor por gramo sí acepta el cero: son reglas distintas a propósito.
        assertNull(errorEnValorPorGramoTexto("0"))
    }

    @Test
    fun `una cantidad vacia, negativa o que no es numero no pasa`() {
        assertNotNull(errorEnCantidadEnGramosTexto(""))
        assertNotNull(errorEnCantidadEnGramosTexto("-100"))
        assertNotNull(errorEnCantidadEnGramosTexto("un poco"))
    }

    @Test
    fun `el campo vacio y el que no es numero avisan cosas distintas`() {
        assertNotEquals(
            errorEnCantidadEnGramosTexto(""),
            errorEnCantidadEnGramosTexto("abc")
        )
    }

    // --- Secciones visibles o no (8.2) ---

    @Test
    fun `con una sola seccion no se muestra el encabezado`() {
        // Una receta de un solo conjunto no necesita que le pongan título a "todo lo que
        // lleva": la sección automática existe en la base pero no se ve.
        assertFalse(debeMostrarNombreDeSeccion(1))
    }

    @Test
    fun `desde la segunda seccion si se muestra`() {
        assertTrue(debeMostrarNombreDeSeccion(2))
        assertTrue(debeMostrarNombreDeSeccion(5))
    }

    @Test
    fun `volver a una sola seccion vuelve a ocultar el encabezado`() {
        // El camino inverso también vale: borrar la crema deja otra vez una receta simple.
        assertTrue(debeMostrarNombreDeSeccion(2))
        assertFalse(debeMostrarNombreDeSeccion(1))
    }

    @Test
    fun `una receta sin secciones tampoco muestra encabezados`() {
        // No debería pasar (crearReceta siembra una), pero no puede reventar ni mostrar
        // un encabezado vacío.
        assertFalse(debeMostrarNombreDeSeccion(0))
    }

    // --- Nombre sugerido al bautizar la primera sección ---

    @Test
    fun `propone el titulo de la receta y no la palabra General`() {
        // Proponer "General" no dice nada; el título es lo más probable: en "Torta de
        // manjar" la primera sección suele ser el bizcocho de la torta.
        assertEquals("Torta de manjar", nombreSugeridoParaPrimeraSeccion("Torta de manjar"))
        assertNotEquals(
            NOMBRE_SECCION_POR_DEFECTO,
            nombreSugeridoParaPrimeraSeccion("Torta de manjar")
        )
    }

    @Test
    fun `le saca los espacios de los bordes al titulo`() {
        assertEquals("Bizcocho", nombreSugeridoParaPrimeraSeccion("  Bizcocho  "))
    }

    @Test
    fun `si el titulo esta vacio propone el nombre por defecto`() {
        assertEquals(NOMBRE_SECCION_POR_DEFECTO, nombreSugeridoParaPrimeraSeccion(""))
        assertEquals(NOMBRE_SECCION_POR_DEFECTO, nombreSugeridoParaPrimeraSeccion("   "))
    }

    @Test
    fun `lo que propone siempre sirve como nombre de seccion`() {
        // La sugerencia no puede salir ya inválida: sería pedir que la corrijan sin que
        // la persona haya escrito nada.
        for (titulo in listOf("Torta", "", "   ", "a".repeat(LARGO_MAXIMO_NOMBRE + 20))) {
            val sugerido = nombreSugeridoParaPrimeraSeccion(titulo)
            assertNull(
                "la sugerencia para '$titulo' no sirve: $sugerido",
                errorEnNombreSeccion(sugerido)
            )
        }
    }
}
