package com.sandyyera.reposteria.logica.calculadora

import com.sandyyera.reposteria.logica.formato.formatearNumero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ValorPorGramoTest {

    // --- La cuenta ---

    @Test
    fun `el caso de todos los dias, un kilo de harina`() {
        // Un kilo de harina a $1.000 sale a $1 el gramo.
        assertEquals(1.0, valorPorGramo(1000.0, 1.0, UnidadDeCompra.KILO), 0.001)
    }

    @Test
    fun `el mismo paquete escrito en gramos da lo mismo`() {
        // Es la conversión que se hace mal de cabeza: 1 kilo y 1.000 gramos son lo mismo.
        val enKilos = valorPorGramo(1000.0, 1.0, UnidadDeCompra.KILO)
        val enGramos = valorPorGramo(1000.0, 1000.0, UnidadDeCompra.GRAMO)
        assertEquals(enKilos, enGramos, 0.001)
    }

    @Test
    fun `un saco grande sale mas barato por gramo`() {
        // 25 kilos por $20.000 -> $0,80 el gramo.
        assertEquals(0.8, valorPorGramo(20000.0, 25.0, UnidadDeCompra.KILO), 0.001)
    }

    @Test
    fun `acepta paquetes con decimales`() {
        // Una bolsa de 1,5 kilos por $2.500.
        assertEquals(1.67, valorPorGramo(2500.0, 1.5, UnidadDeCompra.KILO), 0.001)
    }

    @Test
    fun `el precio cero es un dato valido`() {
        // Un ingrediente regalado o sacado de la despensa vale 0, y eso se puede guardar.
        assertEquals(0.0, valorPorGramo(0.0, 1.0, UnidadDeCompra.KILO), 0.001)
    }

    @Test
    fun `lo que se guarda es exactamente lo que se muestra`() {
        // 2.500 / 1.500 da 1,6666... Si se guardara así, la pantalla diría "1,67" y
        // multiplicarlo por los gramos de una receta no daría el número que se vio.
        val valor = valorPorGramo(2500.0, 1.5, UnidadDeCompra.KILO)
        assertEquals("1,67", formatearNumero(valor))
        assertEquals(valor, 1.67, 0.0)
    }

    @Test
    fun `no divide por cero`() {
        assertThrows(IllegalArgumentException::class.java) {
            valorPorGramo(1000.0, 0.0, UnidadDeCompra.KILO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            valorPorGramo(1000.0, -1.0, UnidadDeCompra.GRAMO)
        }
    }

    @Test
    fun `debajo de medio centavo por gramo el valor queda en cero`() {
        // El límite de guardar con 2 decimales. 25 kilos por $200 da 0,008 por gramo y
        // sobrevive como 0,01; por $100 da 0,004 y se pierde en el redondeo.
        assertEquals(0.01, valorPorGramo(200.0, 25.0, UnidadDeCompra.KILO), 0.0)
        assertEquals(0.0, valorPorGramo(100.0, 25.0, UnidadDeCompra.KILO), 0.0)

        // No es un error escondido: la calculadora muestra ese 0 antes de aceptar, así
        // que se ve en la pantalla y no después, dentro de una receta.
        assertEquals("0", formatearNumero(valorPorGramo(100.0, 25.0, UnidadDeCompra.KILO)))
    }

    // --- Conversión de unidades ---

    @Test
    fun `los kilos se pasan a gramos`() {
        assertEquals(1000.0, UnidadDeCompra.KILO.aGramos(1.0), 0.001)
        assertEquals(1500.0, UnidadDeCompra.KILO.aGramos(1.5), 0.001)
        assertEquals(GRAMOS_POR_KILO, UnidadDeCompra.KILO.aGramos(1.0), 0.001)
    }

    @Test
    fun `los gramos se quedan como estan`() {
        assertEquals(500.0, UnidadDeCompra.GRAMO.aGramos(500.0), 0.001)
    }

    // --- Validación del precio ---

    @Test
    fun `un precio normal pasa`() {
        assertNull(errorEnPrecioTexto("1000"))
        assertNull(errorEnPrecioTexto("1.000"))
        assertNull(errorEnPrecioTexto("2.500,50"))
    }

    @Test
    fun `el precio cero pasa pero el campo vacio no`() {
        assertNull(errorEnPrecioTexto("0"))
        assertNotNull(errorEnPrecioTexto(""))
        assertNotNull(errorEnPrecioTexto("   "))
    }

    @Test
    fun `un precio negativo o que no es numero no pasa`() {
        assertNotNull(errorEnPrecioTexto("-100"))
        assertNotNull(errorEnPrecioTexto("mil"))
    }

    // --- Validación de la cantidad ---

    @Test
    fun `una cantidad normal pasa`() {
        assertNull(errorEnCantidadTexto("1"))
        assertNull(errorEnCantidadTexto("1,5"))
        assertNull(errorEnCantidadTexto("1.000"))
    }

    @Test
    fun `la cantidad cero no pasa, a diferencia del precio`() {
        // Acá el cero no es un dato raro pero válido: es una división por cero.
        assertNotNull(errorEnCantidadTexto("0"))
        assertNull(errorEnPrecioTexto("0"))
    }

    @Test
    fun `la cantidad vacia, negativa o que no es numero no pasa`() {
        assertNotNull(errorEnCantidadTexto(""))
        assertNotNull(errorEnCantidadTexto("-2"))
        assertNotNull(errorEnCantidadTexto("un kilo"))
    }

    @Test
    fun `el campo vacio y el que no es numero avisan cosas distintas`() {
        assertNotEquals(errorEnCantidadTexto(""), errorEnCantidadTexto("abc"))
    }

    // --- Los dos campos juntos ---

    @Test
    fun `el error va en el campo que lo causo`() {
        val soloCantidad = revisarCalculadora("1000", "0")
        assertNull(soloCantidad.precio)
        assertNotNull(soloCantidad.cantidad)

        val soloPrecio = revisarCalculadora("", "1")
        assertNotNull(soloPrecio.precio)
        assertNull(soloPrecio.cantidad)
    }

    @Test
    fun `la calculadora recien abierta no sirve todavia`() {
        val errores = revisarCalculadora("", "")
        assertNotNull(errores.precio)
        assertNotNull(errores.cantidad)
        assertTrue(!errores.sirve)
    }

    // --- El cálculo en vivo, mientras se escribe ---

    @Test
    fun `mientras falta algo no hay resultado, y no explota`() {
        // Escribir es un proceso: "todavía no alcanza" es lo normal, no un error.
        assertNull(calcularValorPorGramo("", "", UnidadDeCompra.KILO))
        assertNull(calcularValorPorGramo("1000", "", UnidadDeCompra.KILO))
        assertNull(calcularValorPorGramo("1000", "0", UnidadDeCompra.KILO))
        assertNull(calcularValorPorGramo("abc", "1", UnidadDeCompra.KILO))
    }

    @Test
    fun `con los dos campos listos aparece el resultado`() {
        assertEquals(1.0, calcularValorPorGramo("1.000", "1", UnidadDeCompra.KILO)!!, 0.001)
        assertEquals(1.0, calcularValorPorGramo("1.000", "1.000", UnidadDeCompra.GRAMO)!!, 0.001)
    }

    @Test
    fun `cambiar de unidad cambia el resultado mil veces`() {
        // El error que esta calculadora existe para evitar.
        val enGramos = calcularValorPorGramo("1.000", "1", UnidadDeCompra.GRAMO)!!
        val enKilos = calcularValorPorGramo("1.000", "1", UnidadDeCompra.KILO)!!
        assertEquals(1000.0, enGramos, 0.001)
        assertEquals(1.0, enKilos, 0.001)
    }

    @Test
    fun `el resultado se puede escribir y volver a leer sin perderse`() {
        // La pantalla pasa el resultado al formulario como texto, y de ahí vuelve a número.
        val valor = calcularValorPorGramo("2.500", "1,5", UnidadDeCompra.KILO)!!
        val comoTexto = formatearNumero(valor)
        assertEquals("1,67", comoTexto)
        assertEquals(
            valor,
            com.sandyyera.reposteria.logica.validaciones.textoANumero(comoTexto)!!,
            0.0
        )
    }
}
