package com.sandyyera.reposteria.logica.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusquedaTest {

    @Test
    fun `encuentra por coincidencia en cualquier parte`() {
        // "El botón buscar debe ser por coincidencia en cualquier lado"
        assertTrue(coincide("manjar", "Torta de manjar"))
        assertTrue(coincide("Torta", "Torta de manjar"))
        assertTrue(coincide("de", "Torta de manjar"))
    }

    @Test
    fun `ignora mayusculas`() {
        assertTrue(coincide("TORTA", "Torta de manjar"))
        assertTrue(coincide("torta", "TORTA DE MANJAR"))
    }

    @Test
    fun `ignora tildes en ambos sentidos`() {
        assertTrue("buscar sin tilde debe encontrar con tilde", coincide("limon", "Mousse de limón"))
        assertTrue("buscar con tilde debe encontrar sin tilde", coincide("limón", "Mousse de limon"))
        assertTrue(coincide("platano", "Plátano"))
        assertTrue(coincide("PLATANO", "plátano"))
    }

    @Test
    fun `la enie se trata como n al buscar`() {
        // Decisión deliberada: al normalizar, la ñ se descompone en n + tilde y la tilde
        // se descarta como cualquier otro acento. Para buscar es lo que conviene -- si se
        // escribe "pina" tiene que aparecer "Piña", sin obligar a cambiar de teclado.
        assertTrue(coincide("pina", "Piña"))
        assertTrue(coincide("nino", "Niño envuelto"))
        // Y al revés también funciona
        assertTrue(coincide("piña", "Pina colada"))
    }

    @Test
    fun `no encuentra lo que no esta`() {
        assertFalse(coincide("chocolate", "Torta de manjar"))
    }

    @Test
    fun `el texto vacio encuentra todo`() {
        // Con el buscador vacío se ve la lista completa.
        assertTrue(coincide("", "Torta de manjar"))
        assertTrue(coincide("", ""))
    }

    // --- sonElMismoTexto: para detectar ingredientes repetidos ---

    @Test
    fun `son el mismo texto ignorando mayusculas tildes y espacios`() {
        assertTrue(sonElMismoTexto("Azúcar", "azucar"))
        assertTrue(sonElMismoTexto("azucar ", "  Azúcar"))
        assertTrue(sonElMismoTexto("PLÁTANO", "platano"))
    }

    @Test
    fun `no son el mismo texto si uno es solo una parte del otro`() {
        // Acá está la diferencia con coincide: "azúcar flor" es otro ingrediente.
        assertFalse(sonElMismoTexto("azucar", "azúcar flor"))
        assertTrue("coincide sí lo encuentra, y debe seguir haciéndolo", coincide("azucar", "azúcar flor"))
    }

    @Test
    fun `nombres distintos no se confunden`() {
        assertFalse(sonElMismoTexto("harina", "maicena"))
        assertFalse(sonElMismoTexto("crema", "cremor"))
    }

    // --- filtrarPor: lo que usan las pantallas con buscador ---

    private val ingredientes = listOf("Azúcar flor", "Harina", "Limón", "Manjar", "Plátano")

    @Test
    fun `el buscador vacio muestra la lista completa`() {
        // No haber escrito nada no es lo mismo que no encontrar nada.
        assertEquals(ingredientes, filtrarPor(ingredientes, "") { it })
        assertEquals(ingredientes, filtrarPor(ingredientes, "   ") { it })
    }

    @Test
    fun `filtra por coincidencia parcial sin tildes`() {
        assertEquals(listOf("Limón"), filtrarPor(ingredientes, "limon") { it })
        assertEquals(listOf("Plátano"), filtrarPor(ingredientes, "PLATANO") { it })
    }

    @Test
    fun `filtra por una parte del medio de la palabra`() {
        // "an" está en Manjar y en Plátano
        assertEquals(listOf("Manjar", "Plátano"), filtrarPor(ingredientes, "an") { it })
    }

    @Test
    fun `sin coincidencias devuelve lista vacia`() {
        assertEquals(emptyList<String>(), filtrarPor(ingredientes, "chocolate") { it })
    }

    @Test
    fun `respeta el orden original`() {
        // La lista viene ordenada de la base; filtrar no debe reordenarla.
        val resultado = filtrarPor(ingredientes, "a") { it }
        assertEquals(resultado.sortedBy { ingredientes.indexOf(it) }, resultado)
    }

    @Test
    fun `funciona sobre cualquier tipo de dato`() {
        // Las cuatro secciones filtran listas distintas con la misma regla.
        data class Receta(val titulo: String, val trozos: Int)
        val recetas = listOf(Receta("Torta de manjar", 8), Receta("Bizcocho", 6))
        assertEquals(listOf(recetas[0]), filtrarPor(recetas, "MANJAR") { it.titulo })
    }

    @Test
    fun `una lista vacia sigue vacia`() {
        assertEquals(emptyList<String>(), filtrarPor(emptyList<String>(), "algo") { it })
    }

    @Test
    fun `funciona con nombres de ingredientes reales`() {
        assertTrue(coincide("azucar", "Azúcar flor"))
        assertTrue(coincide("AZUCAR", "azúcar rubia"))
        assertTrue(coincide("crema", "Crema de leche"))
    }
}
