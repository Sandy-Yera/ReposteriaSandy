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
 * Cómo se reparten los trozos entre los dos lados de una cuadrícula (9.4.3).
 *
 * Seis trozos de un molde de 26 × 25 no son una sola cosa: pueden salir seis tiras de
 * 4,33 × 25, o dos filas de tres de 8,67 × 12,5. **Las dos son ciertas y las dos son seis
 * trozos**, pero solo una es la que se va a cortar.
 *
 * Lo reportó Sandy con ese molde exacto: la app partía siempre el lado largo en tantas tiras
 * como trozos, y con 6 le daba trozos de 3,25 cm de ancho por 25 de largo — que no es una
 * porción de torta, es una tira. Faltaba decir en cuántos se parte **cada** lado.
 */
data class RepartoDelCorte(val aLoLargo: Int, val aLoAncho: Int) {
    /** Cuántos trozos salen: es el producto, y por eso no se guarda aparte. */
    val trozos: Int get() = aLoLargo * aLoAncho

    /** "3 × 2", para nombrarlo en la pantalla. */
    val comoSeLee: String get() = "$aLoLargo × $aLoAncho"
}

/**
 * Todas las formas de repartir [trozos] en una cuadrícula, sin que sobre ninguno.
 *
 * Devuelve solo los repartos que **dan justo**: 6 sale 1×6, 2×3, 3×2 y 6×1, y nada más. Un
 * reparto que no divide dejaría trozos de dos tamaños distintos, y ahí la medida que se muestra
 * dejaría de ser cierta para algunos — que es peor que no mostrar nada.
 *
 * El orden es de menos a más divisiones del primer lado, que es como quedan ordenados los
 * resultados de más ancho a más angosto.
 */
fun repartosPosibles(trozos: Int): List<RepartoDelCorte> {
    if (trozos < 1) return emptyList()
    return (1..trozos).filter { trozos % it == 0 }.map { RepartoDelCorte(it, trozos / it) }
}

/**
 * El reparto que deja los trozos **más parecidos a un cuadrado**, o `null` si no se sabe medir.
 *
 * Es el que se usa cuando nadie eligió, y **cambió respecto de lo que hacía la app**: antes se
 * partía siempre el lado largo en tantas tiras como trozos, o sea el reparto `trozos × 1`. Eso
 * está bien con 2 o 3 trozos y se pone absurdo con 6 — tiras de 3 cm— , que es exactamente lo
 * que Sandy reportó.
 *
 * "Más parejo" se mide como la proporción entre los dos lados del trozo que queda, buscando la
 * más cercana a 1. No es un capricho estético: un trozo de torta se sirve en un plato, y una
 * tira larga y angosta se rompe al levantarla.
 *
 * **La suposición sigue siendo suposición**, y por eso se puede elegir otro: es el mismo
 * criterio de 9.4.2 con el lado que se corta.
 */
fun repartoMasParejo(largo: Double, ancho: Double, trozos: Int): RepartoDelCorte? {
    if (largo <= 0 || ancho <= 0) return null
    return repartosPosibles(trozos).minWithOrNull(
        compareBy(
            { reparto ->
                val ladoA = largo / reparto.aLoLargo
                val ladoB = ancho / reparto.aLoAncho
                maxOf(ladoA, ladoB) / minOf(ladoA, ladoB)
            },
            // **Empatados, gana el que más parte el primer lado.** Pasa siempre en un molde
            // cuadrado —2 trozos son 1 × 2 o 2 × 1 y dan el mismo trozo dado vuelta—, y ahí
            // elegir el primero de la lista daría "20 × 10" donde se espera "10 × 20". El
            // primer lado es el que se corta (9.4.2), así que desempatar por él es decir lo
            // mismo que ya decía la app antes de que existieran los repartos.
            { reparto -> -reparto.aLoLargo }
        )
    )
}

/**
 * Con qué reparto se va a cortar de verdad: el elegido, o el más parejo.
 *
 * [trozosALoLargo] es lo que quedó anotado. Se usa **solo si divide justo** a [trozos]: si la
 * receta pasó de 6 a 8 trozos, un "3 a lo largo" guardado antes ya no reparte nada, y aplicarlo
 * igual daría 2,67 filas — un número que no existe. Ahí se vuelve al más parejo en vez de
 * mostrar una medida falsa.
 */
fun repartoEfectivo(
    dimensiones: DimensionesMolde,
    trozos: Int,
    trozosALoLargo: Int?
): RepartoDelCorte? {
    val lados = ladosParaCortar(dimensiones) ?: return null
    if (trozosALoLargo != null && trozosALoLargo >= 1 && trozos % trozosALoLargo == 0) {
        return RepartoDelCorte(trozosALoLargo, trozos / trozosALoLargo)
    }
    // **Con los lados anotados a mano, el orden ya es la instrucción**: el primero se parte y
    // el segundo se conserva (9.4.2). Repartir "lo más parejo" ahí sería corregir en silencio
    // lo que alguien escribió — y se probó: un molde anotado para cortar el lado de 4 terminaba
    // partiendo el de 8, que es exactamente lo que esa regla existe para no hacer. Quien anotó
    // los lados y además quiere cuadrícula tiene el selector de reparto al lado.
    if (lados.anotados) return RepartoDelCorte(trozos, 1)
    return repartoMasParejo(lados.largo, lados.ancho, trozos)
}

/**
 * De qué tamaño queda cada trozo con un reparto concreto, o `null` si falta alguna medida.
 *
 * Va aparte de [medidaDelTrozo] porque la pantalla necesita mostrar **la medida de cada reparto
 * ofrecido**, no solo la del elegido: elegir "3 × 2" sin ver que eso da 8,67 × 12,5 es elegir a
 * ciegas, y el número es justamente lo que se está decidiendo.
 */
fun medidaEnCuadricula(
    dimensiones: DimensionesMolde,
    reparto: RepartoDelCorte,
    formatear: (Double) -> String
): String? {
    val lados = ladosParaCortar(dimensiones) ?: return null
    if (reparto.aLoLargo < 1 || reparto.aLoAncho < 1) return null
    val alto = dimensiones.alturaMoldeCm
    return "${formatear(lados.largo / reparto.aLoLargo)} × " +
        "${formatear(lados.ancho / reparto.aLoAncho)} cm" +
        (alto?.let { ", ${formatear(it)} de alto" } ?: "")
}

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
 * En cuadrícula el tamaño depende de **cómo se reparten los trozos entre los dos lados**
 * (ver [RepartoDelCorte]): 30 × 20 en 3 trozos da tres de 10 × 20, y 26 × 25 en 6 da seis de
 * 8,67 × 12,5 y no seis tiras de 4,33. Sin [trozosALoLargo] anotado se usa el reparto más
 * parejo, que es una suposición mejor que la anterior pero sigue siendo una suposición.
 */
fun medidaDelTrozo(
    dimensiones: DimensionesMolde,
    corte: FormaDelCorte?,
    trozos: Int,
    trozosALoLargo: Int? = null,
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
            val reparto = repartoEfectivo(dimensiones, trozos, trozosALoLargo) ?: return null
            medidaEnCuadricula(dimensiones, reparto, formatear)
        }

        // Cada pieza es un trozo: no hay nada que repartir, y el tamaño del trozo es el del
        // molde, que ya se muestra arriba.
        FormaDelCorte.NO_SE_CORTA -> null

        null -> null
    }
}

/**
 * Los dos lados con los que se corta en cuadrícula: **el que se parte primero, el que se
 * conserva después**.
 *
 * Hay dos caminos y el orden entre ellos importa:
 *
 * 1. **Anotados a mano** (`largoDeCorteCm` y `anchoDeCorteCm`): se respetan **tal cual, incluido
 *    cuál va primero**. Eso es lo que arregla la pregunta de Sandy — un molde de 8 × 4 se
 *    cortaba siempre por el 8 "porque es el más grande", y escribiendo `4` y `8` a mano tampoco
 *    servía porque acá se reordenaban igual. Es su molde y su torta: si dice que corta el 4, se
 *    corta el 4. **Una instrucción explícita no se corrige en silencio.**
 * 2. **Deducidos de la forma**, cuando nadie anotó nada: ahí sí se parte el lado más largo, que
 *    es la suposición razonable — cortar el corto deja tiras. Pero es una suposición, no una
 *    regla, y por eso el camino 1 existe y le gana.
 *
 * El triángulo y el molde exótico **solo** tienen el camino 1: de ellos no hay forma de deducir
 * los lados, y sin anotarlos no se puede afirmar nada.
 */
private data class LadosDelCorte(
    val largo: Double,
    val ancho: Double,
    /** Si los escribió una persona. Cambia quién manda sobre el reparto (ver [repartoEfectivo]). */
    val anotados: Boolean
)

private fun ladosParaCortar(d: DimensionesMolde): LadosDelCorte? {
    // `errorEnMedidasDeCorte` exige que estén las dos o ninguna, así que basta con mirar una;
    // se comprueban las dos igual, porque una fila vieja podría tener solo una.
    val anotadoLargo = d.largoDeCorteCm
    val anotadoAncho = d.anchoDeCorteCm
    if (anotadoLargo != null && anotadoAncho != null) {
        return LadosDelCorte(anotadoLargo, anotadoAncho, anotados = true)
    }

    val (uno, otro) = when (d.tipoForma) {
        TipoFormaMolde.RECTANGULO -> (d.largoCm ?: return null) to (d.anchoCm ?: return null)
        TipoFormaMolde.CUADRADO -> (d.ladoCm ?: return null).let { it to it }
        else -> return null
    }
    return if (uno >= otro) LadosDelCorte(uno, otro, anotados = false)
    else LadosDelCorte(otro, uno, anotados = false)
}

/** Cómo se lee cada corte en la pantalla. */
fun nombreDelCorte(corte: FormaDelCorte): String = when (corte) {
    FormaDelCorte.CUNAS -> "En porciones, como una torta redonda"
    FormaDelCorte.CUADRICULA -> "En cuadros o tiras"
    FormaDelCorte.NO_SE_CORTA -> "No se corta: cada pieza es un trozo"
}
