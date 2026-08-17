package com.sandyyera.reposteria.logica.almacen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Convertir una receta en un ingrediente (14.13).
 *
 * Son los siropes, almíbares y azúcares invertidos: recetas que no se venden sino que se **usan
 * dentro de otras**. La app ya sabe su costo y su peso, así que el valor por gramo es una cuenta
 * exacta y no una estimación.
 */
class RecetaComoIngredienteTest {

    @Test
    fun `el valor por gramo sale de dividir el costo entre el peso`() {
        // Un almíbar que costó $1.200 y rindió 800 g vale $1,5 el gramo.
        assertEquals(1.5, valorPorGramoDeLaReceta(1200.0, 800.0)!!, 0.00001)
    }

    @Test
    fun `un costo de cero da cero, y eso es un dato`() {
        // Una receta hecha solo de cosas que no se costean vale 0 el gramo. Devolver `null` acá
        // la trataría como incalculable, que es otra cosa — la misma distinción que ya costó un
        // bug en la lista de recetas.
        assertEquals(0.0, valorPorGramoDeLaReceta(0.0, 500.0)!!, 0.00001)
    }

    @Test
    fun `sin peso final no se inventa un valor`() {
        // Suponer un peso inventaría el costo de **todas** las recetas que la usen después.
        assertNull(valorPorGramoDeLaReceta(1200.0, null))
        assertNull("Y con peso 0, además, sería dividir por cero", valorPorGramoDeLaReceta(1200.0, 0.0))
    }

    @Test
    fun `el valor viene redondeado como todo lo que se guarda`() {
        // Cinco decimales, que es lo que un valor por gramo necesita: acá **no** se usa el
        // redondeo de cantidades (8.3.1), porque esto es un precio.
        assertEquals(0.06667, valorPorGramoDeLaReceta(200.0, 3000.0)!!, 0.000001)
    }

    // --- Por qué a veces no se puede ---

    @Test
    fun `una receta con peso e ingredientes se puede guardar`() {
        assertNull(porQueNoSePuedeGuardarComoIngrediente(800.0, tieneIngredientes = true))
    }

    @Test
    fun `sin ingredientes el motivo lo dice antes de tocar nada`() {
        val motivo = porQueNoSePuedeGuardarComoIngrediente(800.0, tieneIngredientes = false)

        assertNotNull(motivo)
        assertTrue("Dice qué falta", motivo!!.contains("ingredientes"))
    }

    @Test
    fun `sin peso el motivo dice dónde arreglarlo`() {
        // El motivo sirve porque nombra el paso: un "no se puede" a secas obliga a adivinar.
        val motivo = porQueNoSePuedeGuardarComoIngrediente(null, tieneIngredientes = true)

        assertNotNull(motivo)
        assertTrue(motivo!!.contains("Rendimiento"))
    }

    @Test
    fun `faltar ingredientes manda sobre faltar el peso`() {
        // Los dos a la vez es lo normal en una receta recién creada, y ahí lo primero que hay
        // que hacer es cargar ingredientes: mandar a Rendimiento sería mandar al paso equivocado.
        val motivo = porQueNoSePuedeGuardarComoIngrediente(null, tieneIngredientes = false)

        assertTrue(motivo!!.contains("ingredientes"))
    }
}
