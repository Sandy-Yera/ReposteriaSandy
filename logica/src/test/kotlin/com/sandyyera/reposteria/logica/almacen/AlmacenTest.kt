package com.sandyyera.reposteria.logica.almacen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La calculadora de "cuánto queda" del almacén (14.7).
 *
 * Es una resta, y aun así tiene dos reglas que se olvidan al escribirla: que no baje de cero, y
 * que pasarse **se diga** en vez de recortarse en silencio.
 */
class AlmacenTest {

    @Test
    fun `la resta de todos los dias`() {
        // Había 2.500 g de harina, se usaron 500: quedan 2.000.
        assertEquals(2000.0, loQueQueda(2500.0, 500.0), 0.00001)
    }

    @Test
    fun `usar todo deja cero, no un numero raro`() {
        assertEquals(0.0, loQueQueda(500.0, 500.0), 0.00001)
    }

    @Test
    fun `usar mas de lo que habia deja el negativo a la vista`() {
        // **Cambió, y con mejor razón que la que tenía.** Antes se recortaba en cero con el
        // argumento de que "un stock negativo no existe en un estante". Sandy dio las dos
        // lecturas que el cero borraba: puede haber entrado algo sin anotar —y entonces el
        // negativo dice cuánto—, o la receta puede pedir más de lo que de verdad se usa. El
        // cero era el dato cómodo, no el verdadero.
        assertEquals(-100.0, loQueQueda(400.0, 500.0), 0.00001)
    }

    @Test
    fun `pasarse se puede detectar para poder decirlo`() {
        // Recortar en silencio dejaría el stock en cero sin ninguna explicación de por qué el
        // número no coincide con lo que se escribió.
        assertTrue(seUsoDeMas(400.0, 500.0))
        assertFalse(seUsoDeMas(500.0, 500.0))
        assertFalse(seUsoDeMas(2500.0, 500.0))
    }

    @Test
    fun `el resultado viene redondeado como todo lo que se guarda`() {
        // Lo que se ve antes de confirmar tiene que ser exactamente lo que queda escrito.
        assertEquals(0.33333, loQueQueda(1.0, 0.6666666), 0.0)
    }

    @Test
    fun `no usar nada deja lo que habia`() {
        assertEquals(2500.0, loQueQueda(2500.0, 0.0), 0.00001)
    }
}
