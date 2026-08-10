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
            medidaDelTrozo(rosca, corteEfectivoDe(rosca), trozos = 8, formatear = comoNumero)
        )
    }

    // --- Lo que se puede deducir de la forma ---

    @Test
    fun `un rectangulo se corta a lo largo y conserva el ancho y el alto`() {
        // El ejemplo de Sandy: un molde de 8 x 6 x 10 en 2 trozos da trozos de 4 x 6 x 10.
        assertEquals(
            "4 × 6 cm, 10 de alto",
            medidaDelTrozo(rectangulo(8.0, 6.0, 10.0), corte = null, trozos = 2, formatear = comoNumero)
        )
    }

    // --- Quién manda sobre cuál lado se corta ---

    @Test
    fun `lo anotado a mano manda sobre la suposicion del lado largo`() {
        // La pregunta de Sandy con un molde de 8 × 4: "¿eligió 8 porque es más grande? ¿y si
        // yo quisiera que fuera el 4?". Antes no había forma de decirlo — escribir 4 y 8 a
        // mano tampoco servía, porque acá se reordenaban igual y volvía a cortar el 8.
        // **Una instrucción explícita no se corrige en silencio.**
        val cortandoElCorto = rectangulo(8.0, 4.0, 10.0).copy(
            formaDelCorte = FormaDelCorte.CUADRICULA,
            largoDeCorteCm = 4.0,
            anchoDeCorteCm = 8.0
        )

        assertEquals(
            "2 × 8 cm, 10 de alto",
            medidaDelTrozo(cortandoElCorto, FormaDelCorte.CUADRICULA, 2, formatear = comoNumero)
        )
    }

    @Test
    fun `sin anotar nada se reparte lo mas parejo posible`() {
        // El otro lado de lo mismo: quien no dice nada recibe una suposición, y la suposición
        // pasó a ser el reparto que deja los trozos más parecidos a un cuadrado.
        val sinDecirNada = rectangulo(8.0, 4.0, 10.0)

        assertEquals(
            "4 × 4 cm, 10 de alto",
            medidaDelTrozo(sinDecirNada, FormaDelCorte.CUADRICULA, 2, formatear = comoNumero)
        )
    }

    @Test
    fun `anotar los lados en el mismo orden que la forma no cambia nada`() {
        // Escribir 8 y 4 sobre un molde de 8 × 4 tiene que dar lo mismo que no escribir nada:
        // si diera distinto, confirmar la suposición la rompería.
        val confirmando = rectangulo(8.0, 4.0, 10.0).copy(
            formaDelCorte = FormaDelCorte.CUADRICULA,
            largoDeCorteCm = 8.0,
            anchoDeCorteCm = 4.0
        )

        assertEquals(
            medidaDelTrozo(rectangulo(8.0, 4.0, 10.0), FormaDelCorte.CUADRICULA, 2, formatear = comoNumero),
            medidaDelTrozo(confirmando, FormaDelCorte.CUADRICULA, 2, formatear = comoNumero)
        )
    }

    @Test
    fun `se corta el lado largo aunque venga escrito segundo`() {
        // Cortar el corto deja tiras que no sirven, y cuál de los dos campos es el mayor
        // depende de cómo lo haya escrito la persona.
        assertEquals(
            "10 × 6 cm, 5 de alto",
            medidaDelTrozo(rectangulo(6.0, 20.0, 5.0), corte = null, trozos = 2, formatear = comoNumero)
        )
    }

    @Test
    fun `un cuadrado en cuatro da cuatro cuadrados, no cuatro tiras`() {
        // **Esta prueba decía "5 × 20" hasta que apareció el reparto**, y era justo lo que
        // Sandy reportó como molesto: partir siempre el lado largo en tantas tiras como trozos
        // deja tiras de 5 cm de un molde de 20. Dos por dos es lo que uno corta de verdad.
        val cuadrado = DimensionesMolde(
            tipoForma = TipoFormaMolde.CUADRADO, ladoCm = 20.0, alturaMoldeCm = 6.0
        )

        assertEquals(
            "10 × 10 cm, 6 de alto",
            medidaDelTrozo(cuadrado, corte = null, trozos = 4, formatear = comoNumero)
        )
    }

    @Test
    fun `el molde de Sandy, que es el que motivó el reparto`() {
        // Un rectángulo de 26 × 25 × 10 en 6 trozos. Partiendo solo el lado largo daba tiras
        // de 4,33 × 25; el reparto más parejo da 3 × 2, o sea trozos de 8,67 × 12,5.
        val elDeSandy = rectangulo(26.0, 25.0, 10.0)

        assertEquals(
            "8.7 × 12.5 cm, 10 de alto",
            medidaDelTrozo(elDeSandy, corte = null, trozos = 6, formatear = comoNumero)
        )
    }

    @Test
    fun `en un cuadrado empatan dos repartos y gana el que parte el primer lado`() {
        // En un molde cuadrado, 2 trozos son 1 × 2 o 2 × 1 y dan **el mismo trozo dado vuelta**,
        // así que la proporción empata. Sin desempate ganaba el primero de la lista y salía
        // "20 × 10" donde la app decía "10 × 20" desde siempre. El primer lado es el que se
        // corta (9.4.2), así que desempatar por él conserva lo que ya se leía.
        val cuadrado = DimensionesMolde(
            tipoForma = TipoFormaMolde.CUADRADO, ladoCm = 20.0, alturaMoldeCm = 6.0
        )

        assertEquals(
            "10 × 20 cm, 6 de alto",
            medidaDelTrozo(cuadrado, corte = null, trozos = 2, formatear = comoNumero)
        )
    }

    @Test
    fun `el reparto elegido a mano manda sobre el mas parejo`() {
        // Es la misma regla que con el lado que se corta: una instrucción explícita no se
        // corrige en silencio. Si alguien quiere las seis tiras, se cortan seis tiras.
        val elDeSandy = rectangulo(26.0, 25.0, 10.0)

        assertEquals(
            "4.3 × 25 cm, 10 de alto",
            medidaDelTrozo(elDeSandy, corte = null, trozos = 6, trozosALoLargo = 6, formatear = comoNumero)
        )
    }

    @Test
    fun `con los lados anotados a mano el reparto no se corrige solo`() {
        // La otra mitad de 9.4.2, y una regresión que esta prueba pilló: al llegar el reparto
        // parejo, un molde anotado para cortar el lado de 4 terminaba partiendo el de 8 porque
        // eso dejaba trozos más cuadrados. Anotar los lados **es** la instrucción, y una
        // instrucción explícita no se corrige en silencio.
        val cortandoElCorto = rectangulo(8.0, 4.0, 10.0).copy(
            formaDelCorte = FormaDelCorte.CUADRICULA,
            largoDeCorteCm = 4.0,
            anchoDeCorteCm = 8.0
        )

        assertEquals(
            "1 × 8 cm, 10 de alto",
            medidaDelTrozo(cortandoElCorto, FormaDelCorte.CUADRICULA, 4, formatear = comoNumero)
        )
    }

    @Test
    fun `un reparto que ya no divide se descarta en vez de dar un numero falso`() {
        // La receta pasó de 6 a 8 trozos y el "3 a lo largo" guardado antes ya no reparte
        // nada: aplicarlo daría 2,67 filas, que no existen. Se vuelve al más parejo.
        val elDeSandy = rectangulo(26.0, 25.0, 10.0)

        assertEquals(
            medidaDelTrozo(elDeSandy, corte = null, trozos = 8, formatear = comoNumero),
            medidaDelTrozo(elDeSandy, corte = null, trozos = 8, trozosALoLargo = 3, formatear = comoNumero)
        )
    }

    @Test
    fun `un circulo se mide en grados y no en centimetros`() {
        // Un trozo de torta redonda es una porción: sus lados no miden lo mismo cerca del
        // centro que en el borde, así que dar centímetros sería inventar.
        val circulo = DimensionesMolde(
            tipoForma = TipoFormaMolde.CIRCULO, diametroCm = 24.0, alturaMoldeCm = 6.0
        )

        assertEquals("porciones de 45°", medidaDelTrozo(circulo, null, 8, formatear = comoNumero))
        assertEquals("porciones de 60°", medidaDelTrozo(circulo, null, 6, formatear = comoNumero))
    }

    @Test
    fun `los grados se redondean, porque nadie corta medio grado`() {
        val circulo = DimensionesMolde(tipoForma = TipoFormaMolde.CIRCULO, diametroCm = 24.0)

        // 360 / 7 = 51,43°
        assertEquals("porciones de 51°", medidaDelTrozo(circulo, null, 7, formatear = comoNumero))
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

        assertEquals("porciones de 36°", medidaDelTrozo(rosca, rosca.formaDelCorte, 10, formatear = comoNumero))
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
            medidaDelTrozo(triangulo, triangulo.formaDelCorte, 4, formatear = comoNumero)
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

        assertNull(medidaDelTrozo(triangulo, triangulo.formaDelCorte, 4, formatear = comoNumero))
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

        assertNull(medidaDelTrozo(galletas, galletas.formaDelCorte, 12, formatear = comoNumero))
    }

    @Test
    fun `el corte elegido gana sobre el sugerido`() {
        // Se puede cortar un molde rectangular en porciones si a alguien le da la gana.
        assertEquals(
            "porciones de 90°",
            medidaDelTrozo(rectangulo(20.0, 20.0, 5.0), FormaDelCorte.CUNAS, 4, formatear = comoNumero)
        )
    }

    @Test
    fun `con cero trozos no se calcula nada, en vez de dividir por cero`() {
        assertNull(medidaDelTrozo(rectangulo(20.0, 10.0, 5.0), null, 0, formatear = comoNumero))
    }

    // --- Decir en palabras lo que el rótulo "1 × 5" no decía (9.4.4) ---

    @Test
    fun `el molde de 26 por 20 en 5, que es el que motivó las palabras`() {
        // Lo que Sandy vio y no pudo elegir: dos opciones rotuladas `1 × 5` y `5 × 1`, que se
        // leen como el mismo número dado vuelta. Son cortes distintos —tiras de 26 × 4 contra
        // tiras de 5,2 × 20— y la frase es la que lo dice sin tener que adivinar.
        val elDeSandy = rectangulo(26.0, 20.0, 8.0)

        assertEquals(
            "el lado de 26 entero, el de 20 en 5",
            comoSeCortanLosLados(elDeSandy, RepartoDelCorte(1, 5), comoNumero)
        )
        assertEquals(
            "el lado de 26 en 5, el de 20 entero",
            comoSeCortanLosLados(elDeSandy, RepartoDelCorte(5, 1), comoNumero)
        )
    }

    @Test
    fun `con una cuadricula de verdad se nombran los dos cortes`() {
        assertEquals(
            "el lado de 26 en 3, el de 25 en 2",
            comoSeCortanLosLados(rectangulo(26.0, 25.0, 10.0), RepartoDelCorte(3, 2), comoNumero)
        )
    }

    @Test
    fun `sin lados que nombrar no se inventa la frase`() {
        // Mismo criterio que `medidaEnCuadricula`: de un exótico sin medidas de corte no se
        // puede afirmar cuál lado se parte, así que no se dice nada.
        val exotico = DimensionesMolde(
            tipoForma = TipoFormaMolde.EXOTICO, volumenExoticoCm3 = 1000.0, alturaMoldeCm = 5.0
        )

        assertNull(comoSeCortanLosLados(exotico, RepartoDelCorte(2, 1), comoNumero))
    }

    @Test
    fun `la lista ofrecida trae medida, palabras y cuál gana`() {
        // La lista que ven las dos pantallas. Se arma una sola vez: escrita en cada ViewModel,
        // la frase en palabras habría quedado en una de las dos.
        val elDeSandy = rectangulo(26.0, 20.0, 8.0)

        val opciones = opcionesDeReparto(elDeSandy, trozos = 5, trozosALoLargo = null, comoNumero)

        assertEquals("5 es primo: 1×5 y 5×1", 2, opciones.size)
        assertEquals("el lado de 26 entero, el de 20 en 5", opciones[0].comoSeCorta)
        assertEquals("26 × 4 cm, 8 de alto", opciones[0].medida)
        // Sin nada anotado gana el más parejo: 5,2 × 20 (proporción 3,85) contra 26 × 4
        // (proporción 6,5). O sea que la app parte el lado de 26, no el de 20.
        assertEquals(5, opciones.single { it.elegido }.reparto.aLoLargo)
        assertEquals("el lado de 26 en 5, el de 20 entero", opciones[1].comoSeCorta)
    }

    @Test
    fun `sin nada que elegir la lista viene vacía`() {
        val redondo = DimensionesMolde(
            tipoForma = TipoFormaMolde.CIRCULO, diametroCm = 20.0, alturaMoldeCm = 6.0
        )

        assertEquals("En cuñas no hay lados que repartir", emptyList<OpcionDeReparto>(), opcionesDeReparto(redondo, 6, null, comoNumero))
        assertEquals("Y con un solo trozo no se elige nada", emptyList<OpcionDeReparto>(), opcionesDeReparto(rectangulo(26.0, 20.0, 8.0), 1, null, comoNumero))
    }

    @Test
    fun `las medidas de corte anotadas explican que hacen`() {
        // El campo no es neutro: escribirlo apaga el reparto más parejo. Eso tiene que estar
        // dicho al lado del campo, que es donde Sandy no sabía qué poner.
        val anotado = rectangulo(26.0, 20.0, 8.0)
            .copy(largoDeCorteCm = 26.0, anchoDeCorteCm = 20.0)

        val frase = queHacenLasMedidasDeCorte(anotado, comoNumero)
        assertEquals(
            "Con esto anotado se parte el lado de 26 y el de 20 queda entero. Déjalos vacíos y " +
                "la app reparte los trozos lo más parejo posible.",
            frase
        )
        assertNull("Sin nada anotado no hay nada que explicar", queHacenLasMedidasDeCorte(rectangulo(26.0, 20.0, 8.0), comoNumero))
    }

    @Test
    fun `anotar los lados del propio molde no dispara ningun aviso`() {
        // Es el uso previsto: los mismos lados, en el orden en que se cortan. Avisar acá sería
        // retar por hacer justo lo que el campo pide.
        val comoEsta = rectangulo(26.0, 20.0, 8.0)
            .copy(largoDeCorteCm = 26.0, anchoDeCorteCm = 20.0)
        val alReves = rectangulo(26.0, 20.0, 8.0)
            .copy(largoDeCorteCm = 20.0, anchoDeCorteCm = 26.0)

        assertNull(avisoDeMedidasDeCorteAjenas(comoEsta, comoNumero))
        assertNull(avisoDeMedidasDeCorteAjenas(alReves, comoNumero))
    }

    @Test
    fun `unas medidas de corte que no son las del molde se avisan sin bloquear`() {
        // La pregunta literal de Sandy: "¿qué pasa si pongo un número menor al del molde?".
        // Pasa que los trozos se miden sobre esa parte, y eso es legítimo —hay bordes que no
        // se cortan— pero no puede pasar callado.
        val recortado = rectangulo(26.0, 20.0, 8.0)
            .copy(largoDeCorteCm = 20.0, anchoDeCorteCm = 15.0)

        assertEquals(
            "Este molde mide 26 × 20 y para cortar anotaste 20 × 15: los trozos se van a medir " +
                "sobre esa parte y no sobre el molde entero.",
            avisoDeMedidasDeCorteAjenas(recortado, comoNumero)
        )
        // Y sigue midiendo sobre lo anotado, que es de lo que avisa.
        assertEquals(
            "4 × 15 cm, 8 de alto",
            medidaDelTrozo(recortado, FormaDelCorte.CUADRICULA, 5, formatear = comoNumero)
        )
    }

    @Test
    fun `en un triangulo no hay contra que comparar y no se avisa`() {
        // Ahí las medidas de corte son la única fuente que existe: no hay lados propios.
        val triangulo = DimensionesMolde(
            tipoForma = TipoFormaMolde.TRIANGULO, baseTrianguloCm = 20.0,
            alturaTrianguloCm = 15.0, alturaMoldeCm = 5.0,
            largoDeCorteCm = 8.0, anchoDeCorteCm = 4.0
        )

        assertNull(avisoDeMedidasDeCorteAjenas(triangulo, comoNumero))
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
