package com.sandyyera.reposteria.logica.partes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los atajos `:titulo:` y `:ingredientes:` que se escriben dentro de un paso (8.8). */
class AtajosTest {

    @Test
    fun `escribir el atajo completo lo dispara`() {
        val texto = ":titulo:"
        assertEquals(AtajoDePaso.TITULO, atajoAntesDelCursor(texto, texto.length))
    }

    @Test
    fun `los dos puntos de atras son parte del atajo`() {
        // Sin ellos el atajo se dispararía a media palabra, mientras todavía se escribe.
        assertNull(atajoAntesDelCursor(":titul", 6))
        assertNull(atajoAntesDelCursor(":titulo", 7))
    }

    @Test
    fun `la palabra suelta en una frase no dispara nada`() {
        // Es la razón de los dos puntos adelante y atrás: equivocarse escribiendo eso es
        // raro, y así el atajo no aparece al hablar del título en medio de una oración.
        val texto = "Ahora el titulo se decora con crema"
        assertNull(atajoAntesDelCursor(texto, texto.length))
    }

    @Test
    fun `la mayuscula del teclado no lo rompe`() {
        // El teclado del celular pone mayúscula al empezar una oración, y `:Titulo:` es lo
        // mismo que se quiso escribir.
        val texto = "Batir. :Titulo:"
        assertEquals(AtajoDePaso.TITULO, atajoAntesDelCursor(texto, texto.length))
    }

    @Test
    fun `solo cuenta lo que hay hasta el cursor`() {
        // El atajo se dispara donde está la mano, no porque la palabra aparezca en otro
        // renglón que ya se resolvió hace rato.
        val texto = ":ingredientes: y batir"
        assertEquals(AtajoDePaso.INGREDIENTES, atajoAntesDelCursor(texto, 14))
        assertNull(atajoAntesDelCursor(texto, texto.length))
    }

    @Test
    fun `reemplazar deja lo elegido en el lugar del atajo`() {
        val texto = "Poner :ingredientes: en el bol"
        val resultado = reemplazarAtajo(texto, 20, AtajoDePaso.INGREDIENTES, "500 g de harina")

        assertEquals("Poner 500 g de harina en el bol", resultado.texto)
        assertEquals("Y el cursor queda después de lo puesto", 21, resultado.cursor)
    }

    @Test
    fun `reemplaza la aparicion del cursor y no la primera que encuentre`() {
        // En un paso que ya usó el atajo antes, `indexOf` cambiaría el equivocado.
        val texto = ":ingredientes: y después :ingredientes:"
        val resultado = reemplazarAtajo(texto, texto.length, AtajoDePaso.INGREDIENTES, "azúcar")

        assertEquals(":ingredientes: y después azúcar", resultado.texto)
    }

    @Test
    fun `un cursor fuera de rango no revienta`() {
        // Cerrar la app girando el teléfono con el campo a medio editar deja combinaciones
        // así, y ninguna puede cerrar la app.
        assertNull(atajoAntesDelCursor("hola", 99))
        assertNull(atajoAntesDelCursor("hola", -3))
        assertEquals("hola", reemplazarAtajo("hola", 99, AtajoDePaso.TITULO, "x").texto)
    }
}
