package com.sandyyera.reposteria.logica.partes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La firma de una receta copiada (8.11.5): qué guarda, cómo se guarda y qué dice al comparar.
 *
 * Es lo que hace posible el aviso de "la original cambió" sin calcular un diff, y también el
 * factor con que se adaptan las cantidades. Por eso se prueba el viaje completo: armarla,
 * guardarla como texto, volver a leerla y comparar.
 *
 * **Varias de estas pruebas existen por un defecto que ya estuvo escrito**: la primera
 * versión guardaba las cantidades en un mapa `nombre → gramos`, y con eso renombrar un
 * ingrediente del catálogo levantaba una falsa alarma en cada copia, y dos filas del mismo
 * ingrediente en una sección se aplastaban en una perdiendo una cantidad en silencio. Las que
 * lo cubren están marcadas.
 */
class FirmaTest {

    private fun bizcocho(
        harina: Double = 550.0,
        pasos: Int = 3,
        nombreDeLaHarina: String = "Harina",
        nombreDelBizcocho: String = "Bizcocho"
    ) = FirmaDeReceta(
        secciones = listOf(
            SeccionDeFirma(
                seccionId = 1, nombre = nombreDelBizcocho,
                lineas = listOf(
                    LineaDeFirma(lineaId = 10, nombre = nombreDeLaHarina, gramos = harina),
                    LineaDeFirma(lineaId = 11, nombre = "Azúcar", gramos = 200.0)
                )
            ),
            SeccionDeFirma(
                seccionId = 2, nombre = "Crema",
                lineas = listOf(LineaDeFirma(lineaId = 12, nombre = "Crema de leche", gramos = 300.0))
            )
        ),
        titulos = listOf(TituloDeFirma(seccionId = 1, nombre = nombreDelBizcocho, cuantosPasos = pasos)),
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

    @Test
    fun `se puede buscar una linea por su id, mire en la seccion que mire`() {
        // Es lo que necesita la adaptación en proporción: dado el id de la línea, de cuánto
        // era antes.
        assertEquals(300.0, bizcocho().linea(12)!!.gramos, 0.001)
        assertNull(bizcocho().linea(99))
    }

    // --- Guardar y volver a leer ---

    @Test
    fun `guardar la firma y volver a leerla da exactamente lo mismo`() {
        val firma = bizcocho()
        assertEquals(firma, firmaDesdeTexto(textoDeFirma(firma)))
    }

    @Test
    fun `un nombre con barras no rompe el formato`() {
        // Nada le impide a alguien llamar a una sección "Crema 50|50". Sin escapar, un
        // nombre así partiría la línea en pedazos y la firma se leería mal para siempre, en
        // silencio.
        val firma = FirmaDeReceta(
            secciones = listOf(
                SeccionDeFirma(1, "Crema 50|50", listOf(LineaDeFirma(10, "Azúcar | flor", 120.0)))
            ),
            titulos = listOf(TituloDeFirma(1, "Paso a|b", 1)),
            pasosGenerales = 0
        )
        assertEquals(firma, firmaDesdeTexto(textoDeFirma(firma)))
    }

    @Test
    fun `una barra invertida sola tambien sobrevive`() {
        val firma = FirmaDeReceta(
            secciones = listOf(
                SeccionDeFirma(1, "Con \\ barra", listOf(LineaDeFirma(10, "Otro \\| raro", 5.0)))
            ),
            titulos = emptyList(),
            pasosGenerales = 0
        )
        assertEquals(firma, firmaDesdeTexto(textoDeFirma(firma)))
    }

    @Test
    fun `un salto de linea dentro de un nombre no parte la firma en dos`() {
        // El formato es de líneas, así que un nombre con un salto adentro rompería el
        // archivo entero. Puede llegar pegando texto desde otra app.
        val firma = FirmaDeReceta(
            secciones = listOf(
                SeccionDeFirma(1, "Crema\nde leche", listOf(LineaDeFirma(10, "Azúcar\nflor", 5.0)))
            ),
            titulos = emptyList(),
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
        assertNull("Un id que no es número", firmaDesdeTexto("v1\nS|uno|Bizcocho\nG|0"))
        assertNull("Un gramaje que no es número", firmaDesdeTexto("v1\nS|1|A\nI|1|2|Harina|mucho"))
        assertNull("Campos de menos", firmaDesdeTexto("v1\nS|1"))
        assertNull(
            "Un ingrediente cuya sección no vino antes",
            firmaDesdeTexto("v1\nI|9|2|Harina|100.0\nG|0")
        )
    }

    @Test
    fun `una receta vacia tambien tiene firma`() {
        val vacia = FirmaDeReceta(emptyList(), emptyList(), 0)
        assertEquals(vacia, firmaDesdeTexto(textoDeFirma(vacia)))
    }

    @Test
    fun `una seccion sin ingredientes se conserva como tal`() {
        // No es lo mismo que una sección que no existe: la copia la tiene que recibir igual.
        val firma = FirmaDeReceta(
            secciones = listOf(SeccionDeFirma(1, "Vacía", emptyList())),
            titulos = emptyList(),
            pasosGenerales = 0
        )
        val leida = firmaDesdeTexto(textoDeFirma(firma))
        assertEquals(1, leida!!.cuantasSecciones)
        assertEquals(0, leida.cuantosIngredientes)
    }

    // --- Lo que NO debe avisar (el defecto que ya estuvo escrito) ---

    @Test
    fun `renombrar un ingrediente del catalogo no levanta ninguna alarma`() {
        // Cambiar "Azúcar" por "Azúcar flor" en el catálogo **no toca ninguna receta**. Con
        // el nombre de clave, cada copia habría avisado de una eliminación y un agregado, y
        // la adaptación habría pisado la cantidad ajustada a mano.
        val antes = bizcocho(nombreDeLaHarina = "Harina")
        val ahora = bizcocho(nombreDeLaHarina = "Harina sin polvos")

        assertTrue(frases(antes, ahora).isEmpty())
    }

    @Test
    fun `renombrar una seccion tampoco avisa, y el aviso siguiente usa el nombre nuevo`() {
        // La arquitectura lo dice: renombrar no se detecta. Y cuando sí hay algo que
        // informar, se nombra con el nombre de hoy y no con el de la foto vieja.
        val antes = bizcocho(nombreDelBizcocho = "Bizcocho")
        val ahora = bizcocho(nombreDelBizcocho = "Bizcocho de vainilla", harina = 500.0)

        assertEquals(listOf("'Harina' pasó de 550 a 500 g"), frases(antes, ahora))
    }

    @Test
    fun `el mismo ingrediente dos veces en una seccion no se aplasta`() {
        // No hay índice único ni comprobación que lo impida, así que pasa. Con el nombre de
        // clave, las dos filas se volvían una y una cantidad desaparecía sin dejar rastro.
        val conRepetido = FirmaDeReceta(
            secciones = listOf(
                SeccionDeFirma(
                    seccionId = 1, nombre = "Bizcocho",
                    lineas = listOf(
                        LineaDeFirma(lineaId = 10, nombre = "Harina", gramos = 400.0),
                        LineaDeFirma(lineaId = 11, nombre = "Harina", gramos = 150.0)
                    )
                )
            ),
            titulos = emptyList(),
            pasosGenerales = 0
        )

        assertEquals("Las dos filas siguen siendo dos", 2, conRepetido.cuantosIngredientes)
        assertEquals(conRepetido, firmaDesdeTexto(textoDeFirma(conRepetido)))

        // Y cambiar una sola de las dos se detecta como una sola.
        val cambiada = conRepetido.copy(
            secciones = listOf(
                conRepetido.secciones[0].copy(
                    lineas = listOf(
                        LineaDeFirma(10, "Harina", 400.0),
                        LineaDeFirma(11, "Harina", 100.0)
                    )
                )
            )
        )
        assertEquals(listOf("'Harina' pasó de 150 a 100 g"), frases(conRepetido, cambiada))
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
        assertEquals(
            listOf("'Harina' pasó de 550 a 500 g"),
            frases(bizcocho(harina = 550.0), bizcocho(harina = 500.0))
        )
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
        val ahora = antes.copy(
            secciones = listOf(
                antes.secciones[0].copy(
                    lineas = antes.secciones[0].lineas + LineaDeFirma(13, "Sal", 5.0)
                ),
                antes.secciones[1].copy(lineas = emptyList())
            )
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
        val ahora = antes.copy(
            secciones = antes.secciones.filterNot { it.seccionId == 2L },
            titulos = antes.titulos
        )

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
            secciones = listOf(
                SeccionDeFirma(
                    seccionId = 1, nombre = "Bizcocho",
                    lineas = listOf(
                        LineaDeFirma(10, "Harina", 500.0),
                        LineaDeFirma(11, "Azúcar", 200.0)
                    )
                )
            ),
            titulos = listOf(TituloDeFirma(1, "Bizcocho", 5)),
            pasosGenerales = 2
        )

        val cambios = frases(antes, ahora)
        assertEquals(
            "Primero si falta una parte entera",
            "Se eliminó la sección 'Crema'",
            cambios.first()
        )
        assertTrue(cambios.contains("'Harina' pasó de 550 a 500 g"))
        assertTrue(cambios.contains("Se agregaron 2 pasos en 'Bizcocho'"))
    }
}
