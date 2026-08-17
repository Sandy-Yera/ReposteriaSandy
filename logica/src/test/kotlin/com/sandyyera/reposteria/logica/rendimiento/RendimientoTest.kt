package com.sandyyera.reposteria.logica.rendimiento

import org.junit.Assert.assertEquals
import org.junit.Test

class RendimientoTest {

    @Test
    fun `divide el peso final entre los trozos`() {
        assertEquals("125", pesoPorTrozo(1000.0, 8))
        assertEquals("250", pesoPorTrozo(1000.0, 4))
    }

    @Test
    fun `el resultado respeta el formato de numeros de la app`() {
        // 1.000 / 3 = 333,333... -> se muestra redondeado a 5 decimales, con coma
        assertEquals("333,33333", pesoPorTrozo(1000.0, 3))
        // Un peso grande lleva punto de miles
        assertEquals("1.250", pesoPorTrozo(10000.0, 8))
    }

    @Test
    fun `sin peso final anotado muestra No especificado`() {
        // "en caso de dejar el campo vacío del peso final (...) debe leerse como 'No especificado'"
        assertEquals(PESO_NO_ESPECIFICADO, pesoPorTrozo(null, 8))
        assertEquals("No especificado", pesoPorTrozo(null, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cero trozos lanza excepcion en vez de dividir por cero`() {
        pesoPorTrozo(1000.0, 0)
    }

    @Test
    fun `una receta de un solo trozo pesa lo mismo que el total`() {
        assertEquals("500", pesoPorTrozo(500.0, 1))
    }
}
