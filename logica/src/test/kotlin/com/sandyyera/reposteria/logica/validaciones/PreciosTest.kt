package com.sandyyera.reposteria.logica.validaciones

import com.sandyyera.reposteria.logica.precios.ModoPrecio
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas del formulario de un precio (6.2 y 8.5).
 *
 * Lo que se cuida acá son las dos divisiones por cero que reventarían mucho después y en otra
 * pantalla: `precioPorTrozoDe` divide por la cantidad, y `trozoGanador` por el precio.
 */
class PreciosTest {

    private fun cantidad(texto: String, modo: ModoPrecio = ModoPrecio.TROZO, trozos: Int = 8) =
        errorEnCantidadDePrecio(texto, modo, trozos)

    // --- El precio ---

    @Test
    fun `un precio normal pasa`() {
        assertNull(errorEnPrecioTotalTexto("1.500"))
    }

    @Test
    fun `el precio en cero se rechaza, a diferencia del valor de un ingrediente`() {
        // Un ingrediente regalado cuesta 0 y eso es un dato. Un precio de venta en 0 no es
        // "lo regalo": es una división por cero esperando en precioPorTrozoDe.
        assertNotNull(errorEnPrecioTotalTexto("0"))
        assertNotNull(errorEnPrecioTotalTexto(""))
        assertNotNull(errorEnPrecioTotalTexto("-500"))
        assertNotNull(errorEnPrecioTotalTexto("mil quinientos"))
    }

    // --- La cantidad, y el tope del último trozo ---

    @Test
    fun `una promo que cabe en la receta pasa`() {
        assertNull(cantidad("3", trozos = 8))
        assertNull("Justo el último trozo también cabe", cantidad("8", trozos = 8))
    }

    @Test
    fun `en modo trozo no cabe una promo de mas trozos de los que rinde`() {
        // "3 trozos por $1.500" en una receta que rinde 2 no significa nada.
        val error = cantidad("3", modo = ModoPrecio.TROZO, trozos = 2)
        assertNotNull(error)
        assertTrue("El aviso dice cuántos rinde, no solo que no se puede", error!!.contains("2"))
    }

    @Test
    fun `en modo producto no hay tope de la receta`() {
        // Vender 3 productos completos es posible por más que cada uno rinda 2. Confundir
        // las dos cosas es justo lo que hubo que separar.
        assertNull(cantidad("3", modo = ModoPrecio.PRODUCTO, trozos = 2))
        assertNull(cantidad("10", modo = ModoPrecio.PRODUCTO, trozos = 2))
    }

    @Test
    fun `la cantidad es entera y al menos 1`() {
        assertNotNull(cantidad("0"))
        assertNotNull(cantidad("-1"))
        assertNotNull("Media promoción no existe", cantidad("2,5"))
        assertNotNull(cantidad(""))
        assertNotNull(cantidad("dos"))
    }

    @Test
    fun `hay un tope contra el dedo pegado, incluso en modo producto`() {
        // "200 trozos por $1.500" no es una promoción, es un 2 escrito tres veces.
        assertNotNull(
            cantidad(
                (MAXIMA_CANTIDAD_DE_PRECIO + 1).toString(),
                modo = ModoPrecio.PRODUCTO,
                trozos = 9999
            )
        )
    }

    @Test
    fun `el aviso del tope de la receta gana sobre el general, porque explica mas`() {
        val error = cantidad("50", modo = ModoPrecio.TROZO, trozos = 8)
        assertTrue("Nombra la receta y no un tope abstracto", error!!.contains("8"))
    }

    // --- La etiqueta ---

    @Test
    fun `la etiqueta es opcional`() {
        assertNull(errorEnEtiquetaDePrecio(""))
        assertNull(errorEnEtiquetaDePrecio("Promo 3 trozos"))
    }

    @Test
    fun `la etiqueta usa el mismo tope que cualquier nombre escrito a mano`() {
        assertNull(errorEnEtiquetaDePrecio("P".repeat(LARGO_MAXIMO_NOMBRE)))
        assertNotNull(errorEnEtiquetaDePrecio("P".repeat(LARGO_MAXIMO_NOMBRE + 1)))
    }

    // --- El formulario completo ---

    @Test
    fun `un formulario bien lleno sirve`() {
        val errores = revisarPrecio("1.500", "3", ModoPrecio.TROZO, trozosDeLaReceta = 8)
        assertTrue(errores.sirve)
        assertNull(errores.precioTotal)
        assertNull(errores.cantidad)
    }

    @Test
    fun `cada aviso queda en su campo, para poder pintarlo debajo del suyo`() {
        // Con el teclado abierto, un mensaje en la franja de abajo no se ve (8.2).
        val errores = revisarPrecio("0", "99", ModoPrecio.TROZO, trozosDeLaReceta = 8)
        assertFalse(errores.sirve)
        assertNotNull(errores.precioTotal)
        assertNotNull(errores.cantidad)
        assertNull(errores.etiqueta)
    }
}
