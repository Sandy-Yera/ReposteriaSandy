package com.sandyyera.reposteria.logica.moldes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * De qué tamaño queda cada trozo según cómo se corte el molde (9.4).
 *
 * La distinción que ordena todo: **el corte no es la forma**. La forma decide el área y el
 * volumen, o sea el reescalado; el corte solo dice el tamaño del trozo. Un molde de rosca no
 * se *parece* a un círculo —le falta el centro y su volumen es otro— pero sí se *corta* como
 * uno. Preguntar por el parecido habría dejado abierta la puerta a recalcular el volumen de un
 * molde que se midió con agua.
 */
class CorteTest {

    private val comoNumero: (Double) -> String = { n ->
        if (n == n.toLong().toDouble()) n.toLong().toString() else String.format("%.1f", n)
    }

    private fun rectangulo(largo: Double, ancho: Double, alto: Double) = DimensionesMolde(
        tipoForma = TipoFormaMolde.RECTANGULO, largoCm = largo, anchoCm = ancho,
        alturaMoldeCm = alto
    )

    // --- El corte que vale: el anotado, o el que sugiere la forma ---

    @Test
    fun `un molde guardado antes de la version 4 igual sabe como se corta`() {
        // La columna llegó en la versión 4, así que **todos** los moldes anteriores la tienen
        // en null. `medidaDelTrozo` ya lo resolvía por dentro; lo que fija esta prueba es que
        // la regla, ahora que tiene nombre propio y otros lectores, siga contestando igual.
        val viejo = rectangulo(8.0, 6.0, 10.0)   // formaDelCorte queda en null

        assertNull("Así está guardado", viejo.formaDelCorte)
        assertEquals(FormaDelCorte.CUADRICULA, corteEfectivoDe(viejo))
    }

    @Test
    fun `lo anotado a mano le gana a la sugerencia`() {
        // Cortar un molde rectangular en cuñas es raro pero se puede, y si alguien lo dijo,
        // eso es lo que manda.
        val enCunas = rectangulo(8.0, 6.0, 10.0).copy(formaDelCorte = FormaDelCorte.CUNAS)

        assertEquals(FormaDelCorte.CUNAS, corteEfectivoDe(enCunas))
    }

    @Test
    fun `donde de verdad no se sabe, sigue sin saberse`() {
        // Un exótico que nadie contestó no tiene nada que suponer: inventarle un corte sería
        // exactamente lo que 9.4 viene a evitar.
        val exotico = DimensionesMolde(
            tipoForma = TipoFormaMolde.EXOTICO, volumenExoticoCm3 = 2000.0, alturaMoldeCm = 8.0
        )

        assertNull(corteEfectivoDe(exotico))
    }

    @Test
    fun `un exotico contestado sí sabe cortarse`() {
        val rosca = DimensionesMolde(
            tipoForma = TipoFormaMolde.EXOTICO, volumenExoticoCm3 = 2000.0, alturaMoldeCm = 8.0,
            formaDelCorte = FormaDelCorte.CUNAS
        )

        assertEquals(FormaDelCorte.CUNAS, corteEfectivoDe(rosca))
        // Y con eso ya se puede decir el tamaño del trozo, que era lo que faltaba: 360 / 8.
        assertEquals(
            "porciones de 45°",
            medidaDelTrozo(rosca, corteEfectivoDe(rosca), trozos = 8, comoNumero)
        )
    }

    // --- Lo que se puede deducir de la forma ---

    @Test
    fun `un rectangulo se corta a lo largo y conserva el ancho y el alto`() {
        // El ejemplo de Sandy: un molde de 8 x 6 x 10 en 2 trozos da trozos de 4 x 6 x 10.
        assertEquals(
            "4 × 6 cm, 10 de alto",
            medidaDelTrozo(rectangulo(8.0, 6.0, 10.0), corte = null, trozos = 2, comoNumero)
        )
    }

    @Test
    fun `se corta el lado largo aunque venga escrito segundo`() {
        // Cortar el corto deja tiras que no sirven, y cuál de los dos campos es el mayor
        // depende de cómo lo haya escrito la persona.
        assertEquals(
            "10 × 6 cm, 5 de alto",
            medidaDelTrozo(rectangulo(6.0, 20.0, 5.0), corte = null, trozos = 2, comoNumero)
        )
    }

    @Test
    fun `un cuadrado se corta igual, sin tener que decirlo`() {
        val cuadrado = DimensionesMolde(
            tipoForma = TipoFormaMolde.CUADRADO, ladoCm = 20.0, alturaMoldeCm = 6.0
        )

        assertEquals(
            "5 × 20 cm, 6 de alto",
            medidaDelTrozo(cuadrado, corte = null, trozos = 4, comoNumero)
        )
    }

    @Test
    fun `un circulo se mide en grados y no en centimetros`() {
        // Un trozo de torta redonda es una porción: sus lados no miden lo mismo cerca del
        // centro que en el borde, así que dar centímetros sería inventar.
        val circulo = DimensionesMolde(
            tipoForma = TipoFormaMolde.CIRCULO, diametroCm = 24.0, alturaMoldeCm = 6.0
        )

        assertEquals("porciones de 45°", medidaDelTrozo(circulo, null, 8, comoNumero))
        assertEquals("porciones de 60°", medidaDelTrozo(circulo, null, 6, comoNumero))
    }

    @Test
    fun `los grados se redondean, porque nadie corta medio grado`() {
        val circulo = DimensionesMolde(tipoForma = TipoFormaMolde.CIRCULO, diametroCm = 24.0)

        // 360 / 7 = 51,43°
        assertEquals("porciones de 51°", medidaDelTrozo(circulo, null, 7, comoNumero))
    }

    // --- Lo que hay que preguntar ---

    @Test
    fun `el triangulo y el exotico no tienen corte sugerido`() {
        // En el triángulo depende de por dónde se corte; el exótico puede ser cualquier cosa.
        // Esos dos son los que hay que preguntar, y los tres de arriba los que no.
        assertNull(corteSugerido(TipoFormaMolde.TRIANGULO))
        assertNull(corteSugerido(TipoFormaMolde.EXOTICO))
        assertEquals(FormaDelCorte.CUADRICULA, corteSugerido(TipoFormaMolde.RECTANGULO))
        assertEquals(FormaDelCorte.CUADRICULA, corteSugerido(TipoFormaMolde.CUADRADO))
        assertEquals(FormaDelCorte.CUNAS, corteSugerido(TipoFormaMolde.CIRCULO))
    }

    @Test
    fun `un exotico cortado en cunas si se puede medir, sin saber su forma`() {
        // El caso de la rosca y del cono: no se parecen a un círculo -su volumen es otro- pero
        // se cortan como uno, y los grados salen igual.
        val rosca = DimensionesMolde(
            tipoForma = TipoFormaMolde.EXOTICO,
            volumenExoticoCm3 = 1500.0,
            alturaMoldeCm = 8.0,
            formaDelCorte = FormaDelCorte.CUNAS
        )

        assertEquals("porciones de 36°", medidaDelTrozo(rosca, rosca.formaDelCorte, 10, comoNumero))
    }

    @Test
    fun `un triangulo con medidas de corte anotadas a mano se puede medir`() {
        val triangulo = DimensionesMolde(
            tipoForma = TipoFormaMolde.TRIANGULO,
            baseTrianguloCm = 20.0,
            alturaTrianguloCm = 15.0,
            alturaMoldeCm = 5.0,
            formaDelCorte = FormaDelCorte.CUADRICULA,
            largoDeCorteCm = 20.0,
            anchoDeCorteCm = 10.0
        )

        assertEquals(
            "5 × 10 cm, 5 de alto",
            medidaDelTrozo(triangulo, triangulo.formaDelCorte, 4, comoNumero)
        )
    }

    @Test
    fun `sin medidas anotadas no se inventa nada`() {
        // Es la respuesta honesta: de un triángulo a secas no se puede afirmar el tamaño del
        // trozo, y un número inventado sería peor que no decir nada.
        val triangulo = DimensionesMolde(
            tipoForma = TipoFormaMolde.TRIANGULO,
            baseTrianguloCm = 20.0,
            alturaTrianguloCm = 15.0,
            formaDelCorte = FormaDelCorte.CUADRICULA
        )

        assertNull(medidaDelTrozo(triangulo, triangulo.formaDelCorte, 4, comoNumero))
    }

    @Test
    fun `un molde que no se corta no tiene medida de trozo`() {
        // Galletas con forma de persona: cada pieza ES un trozo, y su tamaño es el del molde,
        // que ya se muestra en otra línea.
        val galletas = DimensionesMolde(
            tipoForma = TipoFormaMolde.EXOTICO,
            volumenExoticoCm3 = 300.0,
            formaDelCorte = FormaDelCorte.NO_SE_CORTA
        )

        assertNull(medidaDelTrozo(galletas, galletas.formaDelCorte, 12, comoNumero))
    }

    @Test
    fun `el corte elegido gana sobre el sugerido`() {
        // Se puede cortar un molde rectangular en porciones si a alguien le da la gana.
        assertEquals(
            "porciones de 90°",
            medidaDelTrozo(rectangulo(20.0, 20.0, 5.0), FormaDelCorte.CUNAS, 4, comoNumero)
        )
    }

    @Test
    fun `con cero trozos no se calcula nada, en vez de dividir por cero`() {
        assertNull(medidaDelTrozo(rectangulo(20.0, 10.0, 5.0), null, 0, comoNumero))
    }

    @Test
    fun `el corte no toca el area ni el volumen`() {
        // La razón de que el corte exista como concepto aparte. Si describir un corte
        // cambiara estos números, cambiaría el reescalado y con él las cantidades.
        val sinCorte = rectangulo(30.0, 20.0, 6.0)
        val conCorte = sinCorte.copy(
            formaDelCorte = FormaDelCorte.CUNAS,
            largoDeCorteCm = 5.0,
            anchoDeCorteCm = 5.0
        )

        assertEquals(sinCorte.areaCm2, conCorte.areaCm2, 0.001)
        assertEquals(sinCorte.volumenCm3, conCorte.volumenCm3, 0.001)
    }
}
