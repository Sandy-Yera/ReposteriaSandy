package com.sandyyera.reposteria.logica.moldes

import kotlin.math.roundToInt

/**
 * Cómo se parte un molde para sacar los trozos (9.4).
 *
 * **No es la forma del molde, y esa distinción es todo el punto.** La forma decide el área y
 * el volumen, o sea el reescalado, que es la operación más delicada de la app. El corte no
 * toca ninguno de esos números: solo dice de qué tamaño queda cada trozo.
 *
 * Nació de una pregunta de Sandy —"¿a qué figura se parece este molde?"— que apuntaba a lo
 * correcto con la palabra equivocada. Un molde de rosca **no se parece** a un círculo: le
 * falta el centro, y su volumen es otro. Pero **se corta** como un círculo, en cuñas, y eso
 * sí es cierto. Preguntando por el parecido quedaba abierta la puerta a recalcular el volumen
 * de un molde exótico —que se mide con agua, justamente porque no hay fórmula— y ahí las
 * cantidades de la receta se irían al tacho en silencio. Preguntando por el corte, no.
 */
enum class FormaDelCorte {
    /** Como una torta redonda: cada trozo es una porción triangular desde el centro. */
    CUNAS,

    /** A lo largo o en cuadros: cada trozo es un rectángulo. */
    CUADRICULA,

    /**
     * No se corta: cada pieza que sale del molde **es** un trozo.
     *
     * Es el caso de las galletas y de cualquier molde de varias cavidades. No es "no sé":
     * es un dato, y por eso existe en vez de dejar la opción en blanco.
     */
    NO_SE_CORTA
}

/**
 * Cómo se corta normalmente un molde de esta forma.
 *
 * Es una sugerencia y no una regla: se puede cortar un molde rectangular en cuñas si a
 * alguien le da la gana. Sirve para que las tres formas donde la respuesta es obvia no haya
 * que contestarlas — y de paso, para que los moldes que ya existen tengan corte sin que nadie
 * los edite.
 *
 * El triángulo y el exótico **no tienen sugerencia** (`null`): en el primero depende de por
 * dónde se corte, y el segundo puede ser cualquier cosa. Esos son justamente los dos que hay
 * que preguntar.
 */
fun corteSugerido(forma: TipoFormaMolde?): FormaDelCorte? = when (forma) {
    TipoFormaMolde.RECTANGULO, TipoFormaMolde.CUADRADO -> FormaDelCorte.CUADRICULA
    TipoFormaMolde.CIRCULO -> FormaDelCorte.CUNAS
    TipoFormaMolde.TRIANGULO, TipoFormaMolde.EXOTICO, null -> null
}

/**
 * Con qué corte se va a trabajar de verdad: el que quedó anotado, o el que sugiere la forma.
 *
 * **Es la única forma correcta de leer el corte de un molde guardado**, y por eso existe en vez
 * de mirar `dimensiones.formaDelCorte` a secas. Esa columna llegó en la versión 4, así que
 * **todos los moldes guardados antes la tienen en `null`**; leerla sin la sugerencia diría que
 * un molde rectangular de toda la vida no se sabe cortar, cuando sí se sabe.
 *
 * La regla ya existía **escondida dentro de [medidaDelTrozo]**, que hace ese mismo `?:` con lo
 * que recibe. Sacarla acá y ponerle nombre fue necesario al aparecer un segundo lector —el paso
 * del molde, que ahora muestra en palabras cómo se corta—: dejarla escondida habría significado
 * escribir el `?:` otra vez, y dos copias de una regla se separan.
 *
 * Sigue devolviendo `null` donde de verdad no se sabe: un triángulo o un molde exótico que
 * nadie contestó. Ahí no hay nada que suponer.
 */
fun corteEfectivoDe(dimensiones: DimensionesMolde): FormaDelCorte? =
    dimensiones.formaDelCorte ?: corteSugerido(dimensiones.tipoForma)

/** Los grados que tiene una vuelta completa. Para repartir un molde redondo en porciones. */
const val GRADOS_DE_UNA_VUELTA = 360

/**
 * De qué tamaño queda cada trozo, o `null` si no se puede decir con lo que hay.
 *
 * Devolver `null` es una respuesta legítima y frecuente: un molde con forma de persona no se
 * corta, y de un triángulo sin medidas de corte anotadas no se puede afirmar nada. **Es mejor
 * no decir nada que decir un número inventado**, que es lo que pasaría midiendo todo como si
 * fuera un rectángulo.
 *
 * En cuñas la respuesta son **grados y no centímetros**: un trozo de torta redonda es una
 * porción, y sus lados no miden lo mismo cerca del centro que en el borde. Decir "4 × 6 cm"
 * ahí sería falso; decir "porciones de 45°" es exacto.
 *
 * En cuadrícula se corta **el lado largo** y se conservan el corto y la altura, que es como
 * se corta de verdad: un molde de 30 × 20 en 3 trozos da tres de 10 × 20, no tres cuadrados.
 */
fun medidaDelTrozo(
    dimensiones: DimensionesMolde,
    corte: FormaDelCorte?,
    trozos: Int,
    formatear: (Double) -> String
): String? {
    if (trozos < 1) return null
    return when (corte ?: corteSugerido(dimensiones.tipoForma)) {
        FormaDelCorte.CUNAS -> {
            val grados = GRADOS_DE_UNA_VUELTA.toDouble() / trozos
            // Se redondea al grado: "51,43°" es una precisión que nadie va a cortar.
            "porciones de ${grados.roundToInt()}°"
        }

        FormaDelCorte.CUADRICULA -> {
            val (largo, ancho) = ladosParaCortar(dimensiones) ?: return null
            val cortado = largo / trozos
            val alto = dimensiones.alturaMoldeCm
            "${formatear(cortado)} × ${formatear(ancho)} cm" +
                (alto?.let { ", ${formatear(it)} de alto" } ?: "")
        }

        // Cada pieza es un trozo: no hay nada que repartir, y el tamaño del trozo es el del
        // molde, que ya se muestra arriba.
        FormaDelCorte.NO_SE_CORTA -> null

        null -> null
    }
}

/**
 * Los dos lados con los que se corta en cuadrícula, largo primero.
 *
 * El rectángulo y el cuadrado los traen de su propia forma. Las otras tres los traen **solo si
 * alguien los anotó a mano**, que es para lo que existen `largoDeCorteCm` y `anchoDeCorteCm`:
 * de un triángulo o de un molde exótico no hay forma de deducirlos.
 */
private fun ladosParaCortar(d: DimensionesMolde): Pair<Double, Double>? {
    val largo = d.largoDeCorteCm ?: when (d.tipoForma) {
        TipoFormaMolde.RECTANGULO -> d.largoCm
        TipoFormaMolde.CUADRADO -> d.ladoCm
        else -> null
    } ?: return null
    val ancho = d.anchoDeCorteCm ?: when (d.tipoForma) {
        TipoFormaMolde.RECTANGULO -> d.anchoCm
        TipoFormaMolde.CUADRADO -> d.ladoCm
        else -> null
    } ?: return null
    // Se corta el lado largo, sea cual sea de los dos: cortar el corto deja tiras que nadie
    // sirve así.
    return if (largo >= ancho) largo to ancho else ancho to largo
}

/** Cómo se lee cada corte en la pantalla. */
fun nombreDelCorte(corte: FormaDelCorte): String = when (corte) {
    FormaDelCorte.CUNAS -> "En porciones, como una torta redonda"
    FormaDelCorte.CUADRICULA -> "En cuadros o tiras"
    FormaDelCorte.NO_SE_CORTA -> "No se corta: cada pieza es un trozo"
}
