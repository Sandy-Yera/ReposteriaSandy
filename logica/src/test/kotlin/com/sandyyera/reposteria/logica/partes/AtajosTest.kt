package com.sandyyera.reposteria.logica.partes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los atajos `:info:`, `:titulo:` y `:ingredientes:` que se escriben dentro de un paso (8.8). */
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
    fun `la ayuda tambien es un atajo, y va primera`() {
        // Sandy lo pidió para el momento en que ya no se ve el botón de arriba. Que vaya primera
        // no es cosmético: es lo que hace que aparezca arriba en la lista de la ayuda, donde se
        // busca cuando uno no recuerda ninguno de los otros.
        val texto = "Batir todo :info:"
        assertEquals(AtajoDePaso.INFO, atajoAntesDelCursor(texto, texto.length))
        assertEquals(AtajoDePaso.INFO, AtajoDePaso.entries.first())
    }

    @Test
    fun `cada atajo dice que hace, para que la ayuda salga sola`() {
        // La ayuda de la pantalla se arma recorriendo el enum. Si alguna entrada quedara sin
        // explicación, esa ayuda mostraría un atajo mudo — y nadie se enteraría hasta verlo.
        AtajoDePaso.entries.forEach { atajo ->
            assertTrue("${atajo.name} sin escritura", atajo.escritura.isNotBlank())
            assertTrue("${atajo.name} sin explicación", atajo.queHace.isNotBlank())
        }
    }

    @Test
    fun `sacar el atajo sin poner nada deja el texto limpio`() {
        // Es lo que pasa con `:info:` y con `:titulo:`: se escriben para pedir algo, no para
        // leerse después dentro de la receta.
        val texto = "Batir la mezcla :info:"
        val resultado = reemplazarAtajo(texto, texto.length, AtajoDePaso.INFO, "")

        assertEquals("Batir la mezcla ", resultado.texto)
        assertEquals("El cursor queda donde estaba el atajo", 16, resultado.cursor)
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
