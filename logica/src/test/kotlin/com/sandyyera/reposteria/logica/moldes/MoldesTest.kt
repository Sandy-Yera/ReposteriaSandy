package com.sandyyera.reposteria.logica.moldes

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class MoldesTest {

    private fun circulo(diametro: Double, alto: Double) = DimensionesMolde(
        tipoForma = TipoFormaMolde.CIRCULO, diametroCm = diametro, alturaMoldeCm = alto
    )

    private fun cuadrado(lado: Double, alto: Double) = DimensionesMolde(
        tipoForma = TipoFormaMolde.CUADRADO, ladoCm = lado, alturaMoldeCm = alto
    )

    private fun rectangulo(largo: Double, ancho: Double, alto: Double) = DimensionesMolde(
        tipoForma = TipoFormaMolde.RECTANGULO, largoCm = largo, anchoCm = ancho, alturaMoldeCm = alto
    )

    private fun triangulo(base: Double, alturaBase: Double, alto: Double) = DimensionesMolde(
        tipoForma = TipoFormaMolde.TRIANGULO,
        baseTrianguloCm = base, alturaTrianguloCm = alturaBase, alturaMoldeCm = alto
    )

    private fun exotico(volumen: Double, alto: Double) = DimensionesMolde(
        tipoForma = TipoFormaMolde.EXOTICO, volumenExoticoCm3 = volumen, alturaMoldeCm = alto
    )

    // --- Área y volumen de cada forma ---

    @Test
    fun `area y volumen de las formas regulares`() {
        assertEquals(100.0, cuadrado(10.0, 5.0).areaCm2, 0.001)
        assertEquals(500.0, cuadrado(10.0, 5.0).volumenCm3, 0.001)

        assertEquals(200.0, rectangulo(20.0, 10.0, 7.0).areaCm2, 0.001)
        assertEquals(1400.0, rectangulo(20.0, 10.0, 7.0).volumenCm3, 0.001)

        assertEquals(PI * 100, circulo(20.0, 8.0).areaCm2, 0.001)   // radio 10
        assertEquals(PI * 100 * 8, circulo(20.0, 8.0).volumenCm3, 0.001)

        assertEquals(50.0, triangulo(10.0, 10.0, 6.0).areaCm2, 0.001)
        assertEquals(300.0, triangulo(10.0, 10.0, 6.0).volumenCm3, 0.001)
    }

    @Test
    fun `en un molde exotico el area se despeja del volumen medido con agua`() {
        val m = exotico(volumen = 1200.0, alto = 8.0)
        assertEquals(1200.0, m.volumenCm3, 0.001)   // el volumen es el medido, no se calcula
        assertEquals(150.0, m.areaCm2, 0.001)       // 1200 / 8
    }

    // --- Modo Capacidad ---

    @Test
    fun `modo capacidad divide volumenes`() {
        // El doble de volumen pide el doble de ingredientes.
        val factor = factorEscala(cuadrado(10.0, 5.0), cuadrado(10.0, 10.0), ModoReescalado.CAPACIDAD)
        assertEquals(2.0, factor, 0.001)
    }

    @Test
    fun `modo capacidad permite un molde mas bajo`() {
        // Bajar de 10 a 5 cm de alto reduce a la mitad: no hay restricción de altura.
        val factor = factorEscala(cuadrado(10.0, 10.0), cuadrado(10.0, 5.0), ModoReescalado.CAPACIDAD)
        assertEquals(0.5, factor, 0.001)
    }

    // --- Modo Altura ---

    @Test
    fun `modo altura divide areas y conserva el grosor`() {
        // Mismo alto, el doble de área: el doble de ingredientes, misma altura de tajada.
        val factor = factorEscala(cuadrado(10.0, 6.0), rectangulo(20.0, 10.0, 6.0), ModoReescalado.ALTURA)
        assertEquals(2.0, factor, 0.001)
    }

    @Test
    fun `modo altura ignora que el molde nuevo sea mas alto`() {
        // El alto extra no entra en el factor: solo importa el área, para no cambiar el grosor.
        val factor = factorEscala(cuadrado(10.0, 5.0), cuadrado(10.0, 20.0), ModoReescalado.ALTURA)
        assertEquals(1.0, factor, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `modo altura rechaza un molde nuevo mas bajo`() {
        factorEscala(cuadrado(10.0, 10.0), cuadrado(20.0, 5.0), ModoReescalado.ALTURA)
    }

    @Test
    fun `modo altura funciona con moldes exoticos despejando el area`() {
        // Original: 1200/8 = 150 de área. Nuevo: 3000/10 = 300. Factor 2.
        val factor = factorEscala(exotico(1200.0, 8.0), exotico(3000.0, 10.0), ModoReescalado.ALTURA)
        assertEquals(2.0, factor, 0.001)
    }

    // --- Casos que podrían reventar ---

    @Test(expected = IllegalArgumentException::class)
    fun `un molde original con medida cero no devuelve infinito`() {
        factorEscala(cuadrado(0.0, 5.0), cuadrado(10.0, 5.0), ModoReescalado.CAPACIDAD)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un molde sin la medida que su forma necesita lanza excepcion`() {
        // Círculo sin diámetro: el error debe ser claro, no un NullPointerException suelto.
        DimensionesMolde(tipoForma = TipoFormaMolde.CIRCULO, alturaMoldeCm = 5.0).areaCm2
    }

    @Test(expected = IllegalStateException::class)
    fun `un molde sin forma definida lanza excepcion`() {
        DimensionesMolde().areaCm2
    }

    @Test
    fun `reescalar y volver deja el factor en uno`() {
        val chico = cuadrado(10.0, 5.0)
        val grande = cuadrado(20.0, 5.0)
        val ida = factorEscala(chico, grande, ModoReescalado.CAPACIDAD)
        val vuelta = factorEscala(grande, chico, ModoReescalado.CAPACIDAD)
        assertEquals(1.0, ida * vuelta, 0.001)
    }
}
