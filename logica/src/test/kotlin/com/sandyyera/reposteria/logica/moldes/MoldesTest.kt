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
    fun `modo altura ignora el alto extra dentro del margen permitido`() {
        // El alto extra no entra en el factor: solo importa el área, para no cambiar el grosor.
        val factor = factorEscala(cuadrado(10.0, 5.0), cuadrado(10.0, 7.0), ModoReescalado.ALTURA)
        assertEquals(1.0, factor, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `modo altura rechaza un molde nuevo mas bajo`() {
        factorEscala(cuadrado(10.0, 10.0), cuadrado(20.0, 5.0), ModoReescalado.ALTURA)
    }

    // --- Margen de altura: hasta 3 cm más alto, no más ---

    @Test
    fun `modo altura acepta hasta 3 centimetros de diferencia`() {
        // Justo en el límite todavía se puede.
        val factor = factorEscala(cuadrado(10.0, 5.0), cuadrado(10.0, 8.0), ModoReescalado.ALTURA)
        assertEquals(1.0, factor, 0.001)
    }

    @Test
    fun `modo altura rechaza pasar de 3 centimetros con la advertencia acordada`() {
        // 5 -> 8,1 cm: apenas por sobre el margen, y ya no se permite.
        val error = runCatching {
            factorEscala(cuadrado(10.0, 5.0), cuadrado(10.0, 8.1), ModoReescalado.ALTURA)
        }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class.java, error!!::class.java)
        assertEquals(MENSAJE_ALTURA_RIESGOSA, error.message)
    }

    @Test
    fun `un molde mucho mas alto se rechaza aunque el area calce`() {
        val error = runCatching {
            factorEscala(cuadrado(10.0, 5.0), cuadrado(10.0, 20.0), ModoReescalado.ALTURA)
        }.exceptionOrNull()

        assertEquals(MENSAJE_ALTURA_RIESGOSA, error!!.message)
    }

    @Test
    fun `el margen de altura no aplica en modo capacidad`() {
        // Modo Capacidad sí toma en cuenta la altura al calcular, así que no corre riesgo:
        // 5 -> 20 cm es exactamente lo que este modo está pensado para resolver.
        val factor = factorEscala(cuadrado(10.0, 5.0), cuadrado(10.0, 20.0), ModoReescalado.CAPACIDAD)
        assertEquals(4.0, factor, 0.001)
    }

    @Test
    fun `el margen se mide en centimetros y no en proporcion`() {
        // De 2 a 5 cm son 3 cm de diferencia: se permite, aunque sea más del doble de alto.
        val ok = factorEscala(cuadrado(10.0, 2.0), cuadrado(10.0, 5.0), ModoReescalado.ALTURA)
        assertEquals(1.0, ok, 0.001)

        // De 20 a 24 cm son 4 cm: se rechaza, aunque proporcionalmente sea un cambio menor.
        val error = runCatching {
            factorEscala(cuadrado(10.0, 20.0), cuadrado(10.0, 24.0), ModoReescalado.ALTURA)
        }.exceptionOrNull()
        assertEquals(MENSAJE_ALTURA_RIESGOSA, error!!.message)
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

    // --- Las medidas en texto (para reconocer el molde) ---

    private val comoNumero: (Double) -> String = { n ->
        if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
    }

    @Test
    fun `un rectangulo se lee por sus dos lados y su alto`() {
        // La pregunta que esto contesta es "¿cuál era el de 20 por 30?", que ni el área ni el
        // volumen responden.
        assertEquals(
            "30 × 20 cm, 6 de alto",
            medidasEnTexto(rectangulo(30.0, 20.0, 6.0), comoNumero)
        )
    }

    @Test
    fun `un cuadrado escribe sus dos lados aunque sean el mismo`() {
        // "20 cm" a secas no dice si es el lado o el diámetro.
        assertEquals("20 × 20 cm, 6 de alto", medidasEnTexto(cuadrado(20.0, 6.0), comoNumero))
    }

    @Test
    fun `un circulo dice diametro y no un lado`() {
        assertEquals(
            "24 cm de diámetro, 6 de alto",
            medidasEnTexto(circulo(24.0, 6.0), comoNumero)
        )
    }

    @Test
    fun `un triangulo distingue su altura de la del molde`() {
        // Las dos alturas del triángulo son justo lo que se confunde (ver CampoDeMolde).
        val triangulo = DimensionesMolde(
            tipoForma = TipoFormaMolde.TRIANGULO,
            baseTrianguloCm = 20.0,
            alturaTrianguloCm = 15.0,
            alturaMoldeCm = 6.0
        )

        assertEquals(
            "base 20 cm, 15 cm de punta a base, 6 de alto",
            medidasEnTexto(triangulo, comoNumero)
        )
    }

    @Test
    fun `un molde exotico dice su volumen, porque no tiene largo ni ancho`() {
        val exotico = DimensionesMolde(
            tipoForma = TipoFormaMolde.EXOTICO,
            volumenExoticoCm3 = 1200.0,
            alturaMoldeCm = 7.0
        )

        assertEquals("1200 cm³ medidos con agua, 7 de alto", medidasEnTexto(exotico, comoNumero))
    }

    @Test
    fun `sin las medidas devuelve null en vez de reventar`() {
        // A diferencia de `areaCm2`, esto es para mostrar: un molde a medio guardar no puede
        // voltear una pantalla.
        assertEquals(null, medidasEnTexto(DimensionesMolde(), comoNumero))
        assertEquals(
            null,
            medidasEnTexto(DimensionesMolde(tipoForma = TipoFormaMolde.CIRCULO), comoNumero)
        )
    }

    @Test
    fun `sin altura muestra igual lo que se midio`() {
        // La altura es opcional en la práctica hasta que alguien la anota; esconder el resto
        // por eso sería tapar lo único que hay.
        assertEquals(
            "30 × 20 cm",
            medidasEnTexto(
                DimensionesMolde(
                    tipoForma = TipoFormaMolde.RECTANGULO, largoCm = 30.0, anchoCm = 20.0
                ),
                comoNumero
            )
        )
    }
}
