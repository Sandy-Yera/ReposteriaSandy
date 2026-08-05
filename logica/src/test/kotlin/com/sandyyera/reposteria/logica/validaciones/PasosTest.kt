package com.sandyyera.reposteria.logica.validaciones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas del texto de un paso (8.8).
 *
 * Lo que se cuida acá es la asimetría con el resto de los campos escritos: **un paso vacío no
 * es un error, es un paso borrado**. Es el mismo criterio del paso de duración, y el que se
 * olvida al escribirlo como "no puede estar vacío".
 */
class PasosTest {

    @Test
    fun `un paso normal sirve`() {
        assertNull(errorEnTextoDePaso("Batir las claras a punto de nieve."))
        assertTrue(elPasoDiceAlgo("Batir las claras a punto de nieve."))
    }

    // --- El vacío no es un error ---

    @Test
    fun `un paso vacio no da error, porque es un paso que se borra`() {
        // Exigir texto obligaría a llenar el campo antes de poder deshacerse de él.
        assertNull(errorEnTextoDePaso(""))
        assertFalse(elPasoDiceAlgo(""))
    }

    @Test
    fun `un paso de puros espacios esta igual de vacio`() {
        assertFalse(elPasoDiceAlgo("   "))
        assertFalse(elPasoDiceAlgo("\n\t "))
        assertNull(errorEnTextoDePaso("   "))
    }

    // --- El tope ---

    @Test
    fun `justo en el tope todavia cabe`() {
        val alRas = "a".repeat(LARGO_MAXIMO_PASO)

        assertNull(errorEnTextoDePaso(alRas))
    }

    @Test
    fun `pasarse del tope se rechaza`() {
        // Es la red contra pegar sin querer un documento entero dentro de un paso.
        val muyLargo = "a".repeat(LARGO_MAXIMO_PASO + 1)

        assertNotNull(errorEnTextoDePaso(muyLargo))
    }

    @Test
    fun `el aviso dice cuantos van y cuantos caben`() {
        // "Es demasiado largo" a secas, sobre un texto que nadie va a contar a mano, no dice
        // qué hacer: hay que saber por cuánto se pasó.
        val muyLargo = "a".repeat(LARGO_MAXIMO_PASO + 250)
        val aviso = errorEnTextoDePaso(muyLargo)!!

        assertTrue("Dice cuántos lleva", aviso.contains("${LARGO_MAXIMO_PASO + 250}"))
        assertTrue("Y cuántos caben", aviso.contains("$LARGO_MAXIMO_PASO"))
    }

    @Test
    fun `el tope de un paso no es el de un nombre`() {
        // Compartir la función habría obligado a subir el tope de los nombres, o sea a dejar
        // pasar un nombre de sección de 300 caracteres que no cabe en ninguna pantalla.
        val unParrafo = "a".repeat(LARGO_MAXIMO_NOMBRE + 1)

        assertNotNull("Como nombre no cabe", errorEnNombreEscrito(unParrafo))
        assertNull("Como paso sí", errorEnTextoDePaso(unParrafo))
    }

    @Test
    fun `el tope se mide en caracteres y no en palabras`() {
        // Un paso de pocas palabras muy largas tiene que poder guardarse igual.
        assertEquals(1000, LARGO_MAXIMO_PASO)
        assertNull(errorEnTextoDePaso("Precalentar ".repeat(80)))   // 960 caracteres
    }
}
