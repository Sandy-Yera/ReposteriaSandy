package com.sandyyera.reposteria.logica.almacen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los filtros del almacén (14.14).
 *
 * Lo que de verdad se cuida acá son las **dos escrituras de cada comparación**: Sandy pidió que
 * `>=` y `=>` funcionaran las dos, y eso se rompe con un cambio de orden que ninguna pantalla
 * delata — probando `=` primero, un `=>300` se lee como "igual a >300" y deja de andar.
 */
class FiltrosTest {

    private fun filtro(texto: String): FiltroDeCantidad =
        (loQueSeBusca(texto) as LoQueSeBusca.PorCantidad).filtro

    // --- Las cinco comparaciones ---

    @Test
    fun `el igual, el mayor y el menor se entienden`() {
        assertEquals(Comparacion.IGUAL, filtro("=300").comparacion)
        assertEquals(Comparacion.MAYOR, filtro(">300").comparacion)
        assertEquals(Comparacion.MENOR, filtro("<300").comparacion)
        assertEquals(300.0, filtro("=300").cantidad, 0.0001)
    }

    @Test
    fun `las dos formas de mayor o igual valen lo mismo`() {
        // Lo pidió Sandy con todas las letras. Cuál sale primero depende de por dónde uno
        // empiece a pensar la frase, y rechazar una obligaría a recordar cuál eligió la app.
        assertEquals(Comparacion.MAYOR_O_IGUAL, filtro(">=300").comparacion)
        assertEquals(Comparacion.MAYOR_O_IGUAL, filtro("=>300").comparacion)
        assertEquals(filtro(">=300"), filtro("=>300"))
    }

    @Test
    fun `las dos formas de menor o igual valen lo mismo`() {
        assertEquals(Comparacion.MENOR_O_IGUAL, filtro("<=300").comparacion)
        assertEquals(Comparacion.MENOR_O_IGUAL, filtro("=<300").comparacion)
        assertEquals(filtro("<=300"), filtro("=<300"))
    }

    // --- Qué deja pasar cada uno ---

    @Test
    fun `el igual deja pasar solo esa cantidad`() {
        val f = filtro("=300")

        assertTrue(f.deja(300.0))
        assertFalse(f.deja(299.0))
        assertFalse(f.deja(301.0))
    }

    @Test
    fun `el mayor deja fuera el numero justo y el mayor o igual lo deja pasar`() {
        // Es la única diferencia entre los dos, y la que hace que valga la pena tener ambos.
        assertFalse(filtro(">300").deja(300.0))
        assertTrue(filtro(">=300").deja(300.0))
        assertFalse(filtro("<300").deja(300.0))
        assertTrue(filtro("<=300").deja(300.0))
    }

    @Test
    fun `un 300 que quedo en 299 con nueves sigue siendo 300`() {
        // Las cantidades pasan por divisiones al descontar recetas. Sin tolerancia, un frasco
        // que dice 300 en pantalla no aparecería al buscar "=300", que es incomprensible.
        assertTrue(filtro("=300").deja(299.9999999))
        assertTrue(filtro("=300").deja(300.0000001))
        assertFalse("Pero no cualquier cosa cercana", filtro("=300").deja(299.99))
    }

    @Test
    fun `sirve igual para gramos y para unidades`() {
        // El filtro no guarda unidad a propósito: `=3` tiene que encontrar las 3 cajas y los
        // 3 gramos. Filtrar por unidad es la otra mitad de la pantalla.
        val f = filtro("=3")

        assertTrue("3 unidades", f.deja(3.0))
        assertTrue("3 gramos", f.deja(3.0))
    }

    // --- Cuándo NO es un filtro ---

    @Test
    fun `un numero sin signo se busca como nombre`() {
        // Si no, un ingrediente llamado "Colorante 300" sería imposible de encontrar.
        val leido = loQueSeBusca("300")

        assertTrue(leido is LoQueSeBusca.PorNombre)
        assertEquals("300", (leido as LoQueSeBusca.PorNombre).texto)
    }

    @Test
    fun `el buscador vacio es una busqueda por nombre vacia`() {
        assertTrue(loQueSeBusca("") is LoQueSeBusca.PorNombre)
        assertTrue(loQueSeBusca("   ") is LoQueSeBusca.PorNombre)
    }

    @Test
    fun `una palabra normal se busca como nombre`() {
        assertTrue(loQueSeBusca("azúcar") is LoQueSeBusca.PorNombre)
    }

    // --- El filtro mal escrito se dice, no se ignora ---

    @Test
    fun `un signo sin numero avisa en vez de buscar nada`() {
        // Tratarlo como nombre dejaría la lista vacía sin explicación, y el error quedaría
        // invisible en la pantalla.
        val leido = loQueSeBusca(">")

        assertTrue(leido is LoQueSeBusca.MalEscrito)
        assertTrue((leido as LoQueSeBusca.MalEscrito).motivo.contains("Falta el número"))
    }

    @Test
    fun `un signo seguido de letras avisa cual es el problema`() {
        val leido = loQueSeBusca(">abc")

        assertTrue(leido is LoQueSeBusca.MalEscrito)
        assertTrue(
            "El aviso nombra lo que no se entendió",
            (leido as LoQueSeBusca.MalEscrito).motivo.contains("abc")
        )
    }

    @Test
    fun `no se puede filtrar por menos de nada`() {
        assertTrue(loQueSeBusca(">-5") is LoQueSeBusca.MalEscrito)
    }

    // --- Detalles de escritura ---

    @Test
    fun `los espacios alrededor no molestan`() {
        assertEquals(300.0, filtro("  >=  300  ").cantidad, 0.0001)
    }

    @Test
    fun `acepta la coma decimal, como el resto de la app`() {
        // Es lo que sale del teclado del celular, y `textoANumero` ya sabe leerlo.
        assertEquals(1.5, filtro(">1,5").cantidad, 0.0001)
    }

    @Test
    fun `se puede leer en una frase lo que se entendio`() {
        // La pantalla lo muestra para que no haya que adivinar si el filtro se tomó bien.
        assertEquals("300 o más", filtro(">=300").comoSeLee)
        assertEquals("más de 300", filtro(">300").comoSeLee)
        assertEquals("exactamente 300", filtro("=300").comoSeLee)
        assertEquals("300 o menos", filtro("<=300").comoSeLee)
    }

    @Test
    fun `un numero redondo se lee sin decimales de adorno`() {
        assertEquals("exactamente 300", filtro("=300").comoSeLee)
        assertEquals("exactamente 1,5", filtro("=1,5").comoSeLee.replace('.', ','))
    }

    // --- Las casillas de categoría ---

    @Test
    fun `sin ninguna casilla marcada pasa todo`() {
        assertTrue(dejanPasar(emptySet(), esObjeto = true, vaEnRecetas = true))
        assertTrue(dejanPasar(emptySet(), esObjeto = false, vaEnRecetas = false))
    }

    @Test
    fun `marcar va en recetas deja fuera lo que no va`() {
        val marcas = setOf(MarcaDeAlmacen.VA_EN_RECETAS)

        assertTrue(dejanPasar(marcas, esObjeto = false, vaEnRecetas = true))
        assertFalse(dejanPasar(marcas, esObjeto = false, vaEnRecetas = false))
    }

    @Test
    fun `marcar por unidad deja fuera lo que se pesa`() {
        val marcas = setOf(MarcaDeAlmacen.POR_UNIDAD)

        assertTrue(dejanPasar(marcas, esObjeto = true, vaEnRecetas = true))
        assertFalse(dejanPasar(marcas, esObjeto = false, vaEnRecetas = true))
    }

    @Test
    fun `marcar las dos de un par es lo mismo que no marcar ninguna`() {
        // Podría tratarse como "no pasa nada" —son opuestas— pero eso deja la lista vacía sin
        // decir por qué, y nadie lee dos casillas encendidas como un error.
        val lasDos = setOf(MarcaDeAlmacen.VA_EN_RECETAS, MarcaDeAlmacen.NO_VA_EN_RECETAS)

        assertTrue(dejanPasar(lasDos, esObjeto = true, vaEnRecetas = true))
        assertTrue(dejanPasar(lasDos, esObjeto = true, vaEnRecetas = false))
    }

    @Test
    fun `los dos pares se aplican uno sobre otro`() {
        val marcas = setOf(MarcaDeAlmacen.VA_EN_RECETAS, MarcaDeAlmacen.POR_UNIDAD)

        assertTrue("Cumple las dos", dejanPasar(marcas, esObjeto = true, vaEnRecetas = true))
        assertFalse("Falla la unidad", dejanPasar(marcas, esObjeto = false, vaEnRecetas = true))
        assertFalse("Falla lo de recetas", dejanPasar(marcas, esObjeto = true, vaEnRecetas = false))
    }

    @Test
    fun `la chuleta menciona las dos formas de cada comparacion`() {
        // Es la ayuda que Sandy pidió para no tener que acordarse. Si alguna forma se cae del
        // texto, deja de existir para quien la lee.
        val todo = COMO_FILTRAR_POR_CANTIDAD.joinToString(" ")

        listOf("=300", ">300", "<300", ">=300", "=>300", "<=300", "=<300").forEach {
            assertTrue("Falta $it en la ayuda", todo.contains(it))
        }
    }
}
