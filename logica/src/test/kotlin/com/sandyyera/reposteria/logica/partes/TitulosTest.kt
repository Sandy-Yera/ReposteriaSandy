package com.sandyyera.reposteria.logica.partes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los títulos bajo los que van los pasos (8.8).
 *
 * La regla que ordena todo: los títulos **son las secciones de la receta, más "General"**, y
 * solo el General se puede repetir. Está en `logica/` y no en la pantalla porque de ella
 * dependen dos cosas que tienen que coincidir: qué se ofrece en el menú y qué se acepta al
 * confirmar. Si cada una la aplicara por su cuenta, el menú podría ofrecer algo que después
 * se rechaza.
 */
class TitulosTest {

    private val bizcocho = 1L
    private val crema = 2L
    private val decoracion = 3L
    private val general: TituloDePaso = null

    // --- Qué se puede repetir ---

    @Test
    fun `solo el General se repite`() {
        assertTrue(elTituloSePuedeRepetir(general))
        assertFalse(elTituloSePuedeRepetir(bizcocho))
    }

    @Test
    fun `el General se puede usar todas las veces que haga falta`() {
        // Un paso general -precalentar el horno, dejar enfriar- aparece naturalmente entre
        // medio de las partes, así que el bloque tiene que poder repetirse.
        assertNull(errorAlUsarTitulo(general, listOf(general, general), TITULO_GENERAL))
    }

    @Test
    fun `una seccion usada dos veces se rechaza, y el aviso dice por que`() {
        val error = errorAlUsarTitulo(crema, listOf(bizcocho, crema), "Crema")
        assertNotNull(error)
        assertTrue("Nombra la sección, no un id", error!!.contains("Crema"))
        assertTrue("Y explica el problema, no solo la regla", error.contains("cada paso"))
    }

    @Test
    fun `una seccion que todavia no se uso pasa`() {
        assertNull(errorAlUsarTitulo(decoracion, listOf(bizcocho, general), "Decoración"))
    }

    // --- Lo que se ofrece en el menú ---

    @Test
    fun `el menu ofrece las secciones sin usar, y el General siempre`() {
        val disponibles = titulosDisponibles(
            seccionesDeLaReceta = listOf(bizcocho, crema, decoracion),
            yaUsados = listOf(bizcocho, general)
        )

        assertEquals(listOf(crema, decoracion, general), disponibles)
    }

    @Test
    fun `el General sigue estando aunque ya se haya usado tres veces`() {
        val disponibles = titulosDisponibles(
            seccionesDeLaReceta = listOf(bizcocho),
            yaUsados = listOf(bizcocho, general, general, general)
        )
        assertEquals(listOf(general), disponibles)
    }

    @Test
    fun `el menu nunca ofrece algo que despues se rechaza`() {
        // Es la razón de que las dos reglas vivan juntas: lo que se ofrece y lo que se
        // acepta no pueden discrepar.
        val secciones = listOf(bizcocho, crema, decoracion)
        val usados = listOf(bizcocho, crema, general)

        titulosDisponibles(secciones, usados).forEach { titulo ->
            assertNull(
                "Se ofreció $titulo pero se rechazaría",
                errorAlUsarTitulo(titulo, usados, "cualquiera")
            )
        }
    }

    @Test
    fun `una receta de una sola seccion igual puede poner pasos generales`() {
        assertEquals(
            listOf(bizcocho, general),
            titulosDisponibles(listOf(bizcocho), yaUsados = emptyList())
        )
    }

    // --- Bloques que se juntan ---

    @Test
    fun `dos generales seguidos son el mismo bloque partido en dos`() {
        // Mostrarlos separados repite el encabezado sin que la separación signifique nada.
        assertTrue(seJuntanLosBloques(general, general))
    }

    @Test
    fun `dos generales separados por una seccion no se juntan`() {
        // Ahí la separación sí significa algo: uno va antes de la crema y el otro después.
        assertFalse(seJuntanLosBloques(general, crema))
        assertFalse(seJuntanLosBloques(crema, general))
    }

    @Test
    fun `dos secciones distintas nunca se juntan`() {
        assertFalse(seJuntanLosBloques(bizcocho, crema))
    }
}
