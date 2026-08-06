package com.sandyyera.reposteria.logica.partes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Los tres estados de una sección traída (8.11.3 y 8.11.4).
 *
 * Existe por un caso que no se ve leyendo el código de la pantalla: `recetaOrigenId` es
 * `SET_NULL`, así que **borrar la receta original corta el vínculo antes de que nadie decida
 * nada**. Sin la firma para distinguirlas, una sección huérfana y una desvinculada a mano se
 * ven idénticas en la base, y la app o pregunta de más (a una que ya dijo "no me molestes") o
 * de menos (a una cuyo origen desapareció sin avisar).
 */
class VinculoTest {

    @Test
    fun `con id y con firma el vinculo esta vivo`() {
        assertEquals(EstadoDelVinculo.VIVO, estadoDelVinculo(recetaOrigenId = 7, hayFirma = true))
    }

    @Test
    fun `sin id pero con firma, la original fue borrada`() {
        // Es lo que deja SQLite al eliminar la receta original: pone el id en null y no toca
        // la firma, porque no sabe que existe.
        assertEquals(
            EstadoDelVinculo.ORIGINAL_BORRADA,
            estadoDelVinculo(recetaOrigenId = null, hayFirma = true)
        )
    }

    @Test
    fun `sin id y sin firma no hay vinculo`() {
        assertEquals(
            EstadoDelVinculo.SIN_VINCULO,
            estadoDelVinculo(recetaOrigenId = null, hayFirma = false)
        )
    }

    @Test
    fun `desvincular tiene que limpiar las dos columnas, no solo el id`() {
        // Esta es la prueba que justifica que la función exista. Limpiando solo el id, la
        // sección queda diciendo "mi original desapareció" y vuelve a preguntar para siempre,
        // que es justo lo contrario de lo que se pidió al desvincular.
        val soloElId = estadoDelVinculo(recetaOrigenId = null, hayFirma = true)
        assertEquals(EstadoDelVinculo.ORIGINAL_BORRADA, soloElId)

        val lasDos = estadoDelVinculo(recetaOrigenId = null, hayFirma = false)
        assertEquals(EstadoDelVinculo.SIN_VINCULO, lasDos)
    }

    @Test
    fun `una seccion propia y una desvinculada son el mismo estado`() {
        // A propósito: lo que significa SIN_VINCULO es "no avisa nunca más", y las dos se
        // comportan igual en todo lo que sigue. Distinguirlas pediría una columna más para
        // no cambiar nada.
        assertEquals(
            estadoDelVinculo(recetaOrigenId = null, hayFirma = false),
            estadoDelVinculo(recetaOrigenId = null, hayFirma = false)
        )
    }

    @Test
    fun `con id pero sin firma no se avisa nada`() {
        // No debería existir —se escriben juntas—, pero si pasara, avisar sin firma sería
        // prometer un "¿Qué cambió?" que no se puede calcular contra nada.
        assertEquals(
            EstadoDelVinculo.SIN_VINCULO,
            estadoDelVinculo(recetaOrigenId = 7, hayFirma = false)
        )
    }

    // --- Lo que se guarda en la columna ---

    private val vinculo = VinculoConLaOriginal(
        seccionDeOrigen = 12,
        firma = FirmaDeReceta(
            secciones = listOf(
                SeccionDeFirma(12, "Bizcocho", listOf(LineaDeFirma(34, 7, "Harina", 550.0))),
                SeccionDeFirma(13, "Crema", listOf(LineaDeFirma(35, 9, "Crema de leche", 300.0)))
            ),
            titulos = listOf(TituloDeFirma(12, "Bizcocho", 3)),
            pasosGenerales = 2
        )
    )

    @Test
    fun `guardar el vinculo y volver a leerlo da exactamente lo mismo`() {
        assertEquals(vinculo, vinculoDesdeTexto(textoDelVinculo(vinculo)))
    }

    @Test
    fun `el vinculo guarda de que seccion de la original salio esta copia`() {
        // Es la mitad que no está en la firma y sin la cual, con una receta de tres partes
        // traída entera, nada dice qué copia corresponde a qué original: los ids de la copia
        // son propios y el nombre pudo cambiar al chocar.
        val leido = vinculoDesdeTexto(textoDelVinculo(vinculo))!!
        assertEquals(12L, leido.seccionDeOrigen)
        assertEquals("Y la firma sigue siendo de la receta entera", 2, leido.firma.cuantasSecciones)
    }

    @Test
    fun `un vinculo ilegible se descarta en vez de reventar`() {
        assertNull(vinculoDesdeTexto(null))
        assertNull(vinculoDesdeTexto(""))
        assertNull("Sin el envoltorio", vinculoDesdeTexto(textoDeFirma(vinculo.firma)))
        assertNull("Un id que no es número", vinculoDesdeTexto("P|doce\nv2\nG|0"))
        assertNull("Un envoltorio desconocido", vinculoDesdeTexto("Z|12\nv2\nG|0"))
        assertNull("La firma rota, aunque el envoltorio esté bien", vinculoDesdeTexto("P|12\nv1\nG|0"))
    }
}
