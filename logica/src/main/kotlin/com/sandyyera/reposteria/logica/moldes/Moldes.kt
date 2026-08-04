package com.sandyyera.reposteria.logica.moldes

import kotlin.math.PI

/** Las 5 formas de molde. `EXOTICO` es para figuras irregulares (estrella, corazón). */
enum class TipoFormaMolde { RECTANGULO, CIRCULO, CUADRADO, TRIANGULO, EXOTICO }

/** Qué se conserva al pasar una receta de un molde a otro (sección 8.3.1). */
enum class ModoReescalado {
    /** Conserva el grosor y la proporción de capas. Compara áreas. */
    ALTURA,

    /** Conserva la proporción de volumen: la receta rinde más. Compara volúmenes. */
    CAPACIDAD
}

/**
 * Cuánto más alto puede ser el molde nuevo que el original en Modo Altura.
 *
 * Modo Altura no toca la altura al calcular el factor: la masa sube lo mismo que antes.
 * Si el molde nuevo es bastante más alto, esa masa queda perdida al fondo de un molde
 * grande, y el resultado no se parece al que se quería repetir. Pasado este margen la
 * app no deja seguir y manda a usar Modo Capacidad.
 */
const val MAX_DIFERENCIA_ALTURA_CM = 3.0

/** Advertencia cuando el molde nuevo excede el margen de altura permitido. */
const val MENSAJE_ALTURA_RIESGOSA = "Demasiado riesgo. Mejor escale con el otro método"

/**
 * Geometría de un molde. Calcula sola su área y su volumen.
 *
 * Todos los campos son nulables por una razón técnica, no de diseño: este mismo tipo se
 * guarda embebido y nulable dentro del rendimiento de una receta (las que no usan molde),
 * y Room no admite subcampos no-nulos ahí. Qué campos son obligatorios según la forma lo
 * decide la validación (`validaciones/Validaciones.kt`), no el tipo.
 */
data class DimensionesMolde(
    val tipoForma: TipoFormaMolde? = null,
    val largoCm: Double? = null,           // rectángulo
    val anchoCm: Double? = null,           // rectángulo
    val ladoCm: Double? = null,            // cuadrado
    val diametroCm: Double? = null,        // círculo
    val baseTrianguloCm: Double? = null,   // triángulo: base, para el área
    val alturaTrianguloCm: Double? = null, // triángulo: altura de esa base (NO la del molde)
    val volumenExoticoCm3: Double? = null, // exótico: medido llenando el molde con agua
    val alturaMoldeCm: Double? = null      // profundidad real del molde
) {
    /**
     * Superficie de la base del molde.
     *
     * En un molde exótico no se mide: se despeja del volumen que se midió con agua.
     * Lanza excepción si falta algún dato, porque significa que se construyó un molde
     * sin pasar por la validación.
     */
    val areaCm2: Double
        get() = when (tipoForma) {
            TipoFormaMolde.RECTANGULO -> exigir(largoCm, "largo") * exigir(anchoCm, "ancho")
            TipoFormaMolde.CUADRADO -> exigir(ladoCm, "lado").let { it * it }
            TipoFormaMolde.CIRCULO -> (exigir(diametroCm, "diámetro") / 2).let { PI * it * it }
            TipoFormaMolde.TRIANGULO ->
                exigir(baseTrianguloCm, "base") * exigir(alturaTrianguloCm, "altura del triángulo") / 2
            TipoFormaMolde.EXOTICO ->
                exigir(volumenExoticoCm3, "volumen") / exigir(alturaMoldeCm, "altura del molde")
            null -> error("Molde sin forma definida: no debería haber pasado la validación")
        }

    /** Cuánto cabe en el molde. En los exóticos es el valor medido con agua, sin calcular. */
    val volumenCm3: Double
        get() = if (tipoForma == TipoFormaMolde.EXOTICO) exigir(volumenExoticoCm3, "volumen")
        else areaCm2 * exigir(alturaMoldeCm, "altura del molde")

    private fun exigir(valor: Double?, nombre: String): Double =
        requireNotNull(valor) { "Falta $nombre para un molde de tipo $tipoForma" }
}

/**
 * Por cuánto hay que multiplicar cada ingrediente al pasar la receta a otro molde.
 *
 * - [ModoReescalado.ALTURA] divide áreas: la tajada queda del mismo grosor. Por eso exige
 *   que el molde nuevo no sea más bajo que el original — si lo fuera, la masa no cabría
 *   a la misma altura y el resultado no sería comparable.
 * - [ModoReescalado.CAPACIDAD] divide volúmenes: la receta rinde más, aceptando que el
 *   alto cambie.
 *
 * Lanza excepción si el molde original tiene área o volumen cero (medida en 0), en vez
 * de devolver infinito en silencio.
 */
fun factorEscala(
    original: DimensionesMolde,
    nuevo: DimensionesMolde,
    modo: ModoReescalado
): Double = when (modo) {
    ModoReescalado.ALTURA -> {
        val alturaOriginal = requireNotNull(original.alturaMoldeCm) { "El molde original no tiene altura" }
        val alturaNueva = requireNotNull(nuevo.alturaMoldeCm) { "El molde nuevo no tiene altura" }
        require(alturaNueva >= alturaOriginal) {
            "En Modo Altura el molde nuevo no puede ser más bajo que el original " +
                "($alturaNueva cm contra $alturaOriginal cm)"
        }
        require(alturaNueva - alturaOriginal <= MAX_DIFERENCIA_ALTURA_CM) { MENSAJE_ALTURA_RIESGOSA }
        dividir(nuevo.areaCm2, original.areaCm2, "área")
    }

    ModoReescalado.CAPACIDAD -> dividir(nuevo.volumenCm3, original.volumenCm3, "volumen")
}

private fun dividir(nuevo: Double, original: Double, que: String): Double {
    require(original > 0) { "El $que del molde original es cero: revisa sus medidas" }
    return nuevo / original
}

/**
 * Las medidas del molde tal como se tomaron, en una línea.
 *
 * Existe porque hasta ahora las pantallas solo mostraban el **área y el volumen**, que son
 * números calculados: sirven para comparar dos moldes, pero no responden la pregunta con la
 * que uno se para frente al mueble — *¿cuál era el de 20 por 30?*. Un molde se reconoce por
 * lo que se le mide con la regla, no por sus 600 cm².
 *
 * Devuelve `null` si falta alguna medida, en vez de lanzar como hacen `areaCm2` y
 * `volumenCm3`: esto es para mostrar, y un molde a medio guardar no puede voltear una
 * pantalla. Por lo mismo **no calcula nada**; solo lee lo que hay.
 *
 * La altura va al final y separada por una coma, no multiplicando: "20 × 30 cm, 6 de alto"
 * se lee bien, y "20 × 30 × 6" invita a pensar que los tres son lo mismo cuando la altura es
 * la que decide si la masa cabe.
 */
fun medidasEnTexto(dimensiones: DimensionesMolde, formatear: (Double) -> String): String? {
    val base = when (dimensiones.tipoForma) {
        TipoFormaMolde.RECTANGULO -> {
            val largo = dimensiones.largoCm ?: return null
            val ancho = dimensiones.anchoCm ?: return null
            "${formatear(largo)} × ${formatear(ancho)} cm"
        }

        // Se escriben los dos lados aunque sean el mismo: "20 cm" a secas no dice si es el
        // lado o el diámetro, y esta línea existe justamente para reconocer el molde.
        TipoFormaMolde.CUADRADO -> {
            val lado = dimensiones.ladoCm ?: return null
            "${formatear(lado)} × ${formatear(lado)} cm"
        }

        TipoFormaMolde.CIRCULO -> {
            val diametro = dimensiones.diametroCm ?: return null
            "${formatear(diametro)} cm de diámetro"
        }

        TipoFormaMolde.TRIANGULO -> {
            val base = dimensiones.baseTrianguloCm ?: return null
            val altura = dimensiones.alturaTrianguloCm ?: return null
            "base ${formatear(base)} cm, ${formatear(altura)} cm de punta a base"
        }

        // Acá no hay largo ni ancho que mostrar: es una forma irregular y su volumen se midió
        // llenándola con agua. Decir el volumen es lo único honesto que se puede decir.
        TipoFormaMolde.EXOTICO -> {
            val volumen = dimensiones.volumenExoticoCm3 ?: return null
            "${formatear(volumen)} cm³ medidos con agua"
        }

        null -> return null
    }
    val alto = dimensiones.alturaMoldeCm ?: return base
    return "$base, ${formatear(alto)} de alto"
}
