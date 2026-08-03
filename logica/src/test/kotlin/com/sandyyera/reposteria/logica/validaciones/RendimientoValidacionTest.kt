package com.sandyyera.reposteria.logica.validaciones

import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.PrecioVigente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las reglas del paso "Rendimiento" (6.2 y 8.3). */
class RendimientoValidacionTest {

    // --- Trozos ---

    @Test
    fun `los trozos que sirven`() {
        assertNull(errorEnTrozosTexto("1"))
        assertNull(errorEnTrozosTexto("8"))
        assertNull(errorEnTrozosTexto("$MAXIMO_TROZOS"))
    }

    @Test
    fun `cero trozos no se acepta, porque de ahi salen todas las divisiones`() {
        // `costoPorTrozo`, `precioPorTrozoDe` y `pesoPorTrozo` dividen por esto. Un 0 acá no
        // es un dato raro pero válido: es una división por cero esperando en otra pantalla.
        assertNotNull(errorEnTrozosTexto("0"))
        assertNotNull(errorEnTrozosTexto("-3"))
    }

    @Test
    fun `los trozos son enteros`() {
        // Medio trozo no se vende, y aceptarlo dejaría un "8,5" que al mostrar el peso por
        // trozo da un número que no corresponde a ninguna tajada real.
        assertNotNull(errorEnTrozosTexto("8,5"))
        assertNull(errorEnTrozosTexto("8"))
    }

    @Test
    fun `un numero absurdo de trozos se rechaza como probable error de tipeo`() {
        // No es una regla del negocio: es la red contra escribir 8.000 en vez de 8, que deja
        // el costo por trozo en cero y todo lo que sale de ahí sin sentido.
        assertNotNull(errorEnTrozosTexto("8.000"))
        assertNull(errorEnTrozosTexto("$MAXIMO_TROZOS"))
        assertNotNull(errorEnTrozosTexto("${MAXIMO_TROZOS + 1}"))
    }

    @Test
    fun `y un numero enorme recibe ese mismo aviso, no el de los decimales`() {
        // Este campo ya lo hacía bien —usaba `floor`— y es el que enseñó cómo se revisa. Se
        // deja anotado para que siga así: la comparación contra `toInt()` que había en los
        // otros cuatro campos habría respondido "los trozos son un número entero" sobre un
        // número entero.
        assertEquals(
            "¿Seguro? Más de $MAXIMO_TROZOS trozos parece un error",
            errorEnTrozosTexto("10000000000")
        )
    }

    @Test
    fun `un campo de trozos vacio o que no es numero no sirve`() {
        assertNotNull(errorEnTrozosTexto(""))
        assertNotNull(errorEnTrozosTexto("   "))
        assertNotNull(errorEnTrozosTexto("ocho"))
    }

    // --- Peso final ---

    @Test
    fun `sin molde el peso final es obligatorio`() {
        // Es lo único contra lo que se puede reescalar una salsa: sin él, la receta no tiene
        // forma de crecer.
        assertNotNull(errorEnPesoFinalTexto("", usaMolde = false))
        assertNotNull(errorEnPesoFinalTexto("   ", usaMolde = false))
        assertNull(errorEnPesoFinalTexto("1.200", usaMolde = false))
    }

    @Test
    fun `con molde el peso final se puede dejar vacio`() {
        // Ahí se muestra como "No especificado", que es un estado válido (8.3).
        assertNull(errorEnPesoFinalTexto("", usaMolde = true))
        assertNull(errorEnPesoFinalTexto("1.200", usaMolde = true))
    }

    @Test
    fun `un peso final escrito tiene que ser mayor que cero, con molde o sin el`() {
        // Vacío y cero no son lo mismo: vacío con molde está bien, cero nunca, porque
        // `reescalarRecetaPorPeso` divide por él.
        assertNotNull(errorEnPesoFinalTexto("0", usaMolde = true))
        assertNotNull(errorEnPesoFinalTexto("0", usaMolde = false))
        assertNotNull(errorEnPesoFinalTexto("-500", usaMolde = true))
        assertNotNull(errorEnPesoFinalTexto("mucho", usaMolde = false))
    }

    // --- El formulario completo ---

    @Test
    fun `el formulario sirve cuando los dos campos estan bien`() {
        assertTrue(revisarRendimiento("8", "1.200", usaMolde = true).sirve)
        assertTrue(revisarRendimiento("8", "", usaMolde = true).sirve)
        assertTrue(revisarRendimiento("1", "500", usaMolde = false).sirve)
    }

    @Test
    fun `cada error queda en su campo`() {
        val errores = revisarRendimiento("0", "", usaMolde = false)
        assertFalse(errores.sirve)
        assertNotNull(errores.trozos)
        assertNotNull(errores.pesoFinal)

        val soloElPeso = revisarRendimiento("8", "", usaMolde = false)
        assertNull(soloElPeso.trozos)
        assertNotNull(soloElPeso.pesoFinal)
    }

    // --- El tope del último trozo ---

    private fun promoPorTrozo(cantidad: Int, etiqueta: String? = null) = PrecioVigente(
        modo = ModoPrecio.TROZO, cantidad = cantidad, precioTotal = 1500.0, etiqueta = etiqueta
    )

    private fun promoPorProducto(cantidad: Int) = PrecioVigente(
        modo = ModoPrecio.PRODUCTO, cantidad = cantidad, precioTotal = 9000.0
    )

    @Test
    fun `bajar los trozos deja imposibles las promos que piden mas`() {
        val precios = listOf(promoPorTrozo(1), promoPorTrozo(3, "3 por 1.500"), promoPorTrozo(6))

        val noCaben = promocionesQueNoCabenEn(trozos = 2, precios = precios)

        assertEquals(listOf(3, 6), noCaben.map { it.cantidad })
    }

    @Test
    fun `una promo que cabe justo si se acepta`() {
        // El tope es "no más que los trozos", no "menos que los trozos": vender la torta
        // entera en una promo de 8 trozos es legítimo.
        assertTrue(promocionesQueNoCabenEn(8, listOf(promoPorTrozo(8))).isEmpty())
    }

    @Test
    fun `las promos por producto no tienen tope de trozos`() {
        // Vender 3 productos completos es posible por más que cada uno rinda 2 trozos.
        assertTrue(promocionesQueNoCabenEn(2, listOf(promoPorProducto(3))).isEmpty())
    }

    @Test
    fun `sin promociones no hay nada que avisar`() {
        assertTrue(promocionesQueNoCabenEn(1, emptyList()).isEmpty())
    }

    @Test
    fun `el aviso nombra la promo por su etiqueta, y si no tiene por su forma`() {
        // Decir "hay promociones que no caben" obliga a revisarlas todas a mano.
        assertEquals("3 por 1.500", descripcionDePromocion(promoPorTrozo(3, "3 por 1.500")))
        assertEquals("3 trozos", descripcionDePromocion(promoPorTrozo(3)))
        assertEquals("1 trozo", descripcionDePromocion(promoPorTrozo(1)))
        assertEquals("2 trozos", descripcionDePromocion(promoPorTrozo(2, "   ")))
    }
}
