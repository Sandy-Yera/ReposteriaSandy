package com.sandyyera.reposteria.logica.partes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La firma de una receta copiada (8.11.5): qué guarda, cómo se guarda y qué dice al comparar.
 *
 * Es lo que hace posible el aviso de "la original cambió" sin calcular un diff, y también el
 * factor con que se adaptan las cantidades. Por eso se prueba el viaje completo: armarla,
 * guardarla como texto, volver a leerla y comparar.
 */
class FirmaTest {

    private fun bizcocho(harina: Double = 550.0, pasos: Int = 3) = FirmaDeReceta(
        secciones = mapOf(
            "Bizcocho" to mapOf("Harina" to harina, "Azúcar" to 200.0),
            "Crema" to mapOf("Crema de leche" to 300.0)
        ),
        pasosPorTitulo = mapOf("Bizcocho" to pasos),
        pasosGenerales = 2
    )

    private fun frases(antes: FirmaDeReceta, ahora: FirmaDeReceta) =
        compararFirmas(antes, ahora).map { it.frase }

    // --- Los contadores que se derivan ---

    @Test
    fun `los contadores salen de lo guardado, no se anotan aparte`() {
        val firma = bizcocho()
        assertEquals(2, firma.cuantasSecciones)
        assertEquals(3, firma.cuantosIngredientes)
        assertEquals(1, firma.cuantosTitulos)
    }

    // --- Guardar y volver a leer ---

    @Test
    fun `guardar la firma y volver a leerla da exactamente lo mismo`() {
        val firma = bizcocho()
        assertEquals(firma, firmaDesdeTexto(textoDeFirma(firma)))
    }

    @Test
    fun `un nombre con barras o iguales no rompe el formato`() {
        // Nada le impide a alguien llamar a una sección "Crema 50|50" o a un ingrediente
        // "Azúcar = flor". Sin escapar, un nombre así partiría la línea en pedazos y la
        // firma se leería mal para siempre, en silencio.
        val firma = FirmaDeReceta(
            secciones = mapOf("Crema 50|50" to mapOf("Azúcar = flor" to 120.0)),
            pasosPorTitulo = mapOf("Paso a|b" to 1),
            pasosGenerales = 0
        )
        val leida = firmaDesdeTexto(textoDeFirma(firma))

        assertEquals(firma, leida)
        assertEquals(120.0, leida!!.secciones.getValue("Crema 50|50").getValue("Azúcar = flor"), 0.001)
    }

    @Test
    fun `una barra invertida sola tambien sobrevive`() {
        val firma = FirmaDeReceta(
            secciones = mapOf("Con \\ barra" to mapOf("Otro \\| raro" to 5.0)),
            pasosPorTitulo = emptyMap(),
            pasosGenerales = 0
        )
        assertEquals(firma, firmaDesdeTexto(textoDeFirma(firma)))
    }

    @Test
    fun `una firma ilegible se descarta en vez de reventar`() {
        // Una firma de una versión vieja del formato, o una fila a medio escribir, no puede
        // impedir abrir la receta: lo que se pierde es el aviso, no la receta.
        assertNull(firmaDesdeTexto(null))
        assertNull(firmaDesdeTexto(""))
        assertNull(firmaDesdeTexto("cualquier cosa"))
        assertNull("Otra versión del formato", firmaDesdeTexto("v9\nG|0"))
        assertNull("Una línea que no se entiende", firmaDesdeTexto("v1\nX|algo"))
        assertNull("Un número que no es número", firmaDesdeTexto("v1\nS|A|Harina=mucho"))
    }

    @Test
    fun `una receta vacia tambien tiene firma`() {
        val vacia = FirmaDeReceta(emptyMap(), emptyMap(), 0)
        assertEquals(vacia, firmaDesdeTexto(textoDeFirma(vacia)))
    }

    // --- Qué cambió ---

    @Test
    fun `sin cambios no dice nada`() {
        assertTrue(frases(bizcocho(), bizcocho()).isEmpty())
    }

    @Test
    fun `un gramaje distinto se nombra con los dos numeros`() {
        // Es la frase del ejemplo de 8.11.5, y la que además hace falta para adaptar la copia
        // en proporción: sin saber de cuánto a cuánto, no hay factor.
        val cambios = frases(bizcocho(harina = 550.0), bizcocho(harina = 500.0))
        assertEquals(listOf("'Harina' pasó de 550 a 500 g"), cambios)
    }

    @Test
    fun `un redondeo no cuenta como cambio`() {
        // Las cantidades pasan por redondeos a 2 decimales al reescalarse. Avisar de eso
        // sería enseñar a ignorar el aviso.
        assertTrue(frases(bizcocho(harina = 250.0), bizcocho(harina = 250.001)).isEmpty())
    }

    @Test
    fun `agregar y quitar ingredientes se nombra por seccion`() {
        val antes = bizcocho()
        val ahora = FirmaDeReceta(
            secciones = mapOf(
                "Bizcocho" to mapOf("Harina" to 550.0, "Azúcar" to 200.0, "Sal" to 5.0),
                "Crema" to emptyMap()
            ),
            pasosPorTitulo = antes.pasosPorTitulo,
            pasosGenerales = antes.pasosGenerales
        )

        val cambios = frases(antes, ahora)
        assertTrue(cambios.contains("Se eliminó 'Crema de leche' de la sección 'Crema'"))
        assertTrue(cambios.contains("Se agregó 'Sal' a la sección 'Bizcocho'"))
    }

    @Test
    fun `una seccion que se fue se nombra una vez, no ingrediente por ingrediente`() {
        // Si no, borrar una sección de cuatro ingredientes daría cinco frases diciendo lo
        // mismo, y la que importa quedaría enterrada.
        val antes = bizcocho()
        val ahora = antes.copy(secciones = antes.secciones.filterKeys { it != "Crema" })

        assertEquals(listOf("Se eliminó la sección 'Crema'"), frases(antes, ahora))
    }

    @Test
    fun `los pasos se cuentan por titulo, con el singular puesto`() {
        assertEquals(
            listOf("Se agregó un paso en 'Bizcocho'"),
            frases(bizcocho(pasos = 3), bizcocho(pasos = 4))
        )
        assertEquals(
            listOf("Se eliminaron 2 pasos en 'Bizcocho'"),
            frases(bizcocho(pasos = 5), bizcocho(pasos = 3))
        )
    }

    @Test
    fun `los pasos generales se cuentan aparte, y concuerdan en singular`() {
        // "Se agregó un paso generales" es justo la frase que hace dudar de si el número
        // está bien, así que el singular y el plural van por separado.
        val antes = bizcocho()
        assertEquals(
            listOf("Se agregó un paso general"),
            frases(antes, antes.copy(pasosGenerales = 3))
        )
        assertEquals(
            listOf("Se eliminaron 2 pasos generales"),
            frases(antes, antes.copy(pasosGenerales = 0))
        )
    }

    @Test
    fun `varios cambios a la vez salen de lo mas grande a lo mas chico`() {
        val antes = bizcocho()
        val ahora = FirmaDeReceta(
            secciones = mapOf("Bizcocho" to mapOf("Harina" to 500.0, "Azúcar" to 200.0)),
            pasosPorTitulo = mapOf("Bizcocho" to 5),
            pasosGenerales = 2
        )

        val cambios = frases(antes, ahora)
        assertEquals("Primero si falta una parte entera", "Se eliminó la sección 'Crema'", cambios.first())
        assertTrue(cambios.contains("'Harina' pasó de 550 a 500 g"))
        assertTrue(cambios.contains("Se agregaron 2 pasos en 'Bizcocho'"))
        assertNotNull(cambios)
    }
}
