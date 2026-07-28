package com.sandyyera.reposteria.logica.formato

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Convierte un número al formato de la app: punto para los miles, coma para los
 * decimales, y sin coma cuando no hay decimales.
 *
 *     1000.0   -> "1.000"
 *     1.55     -> "1,55"
 *     250.0    -> "250"
 *     -0.56    -> "-0,56"
 *     -1234.56 -> "-1.234,56"
 *
 * Ver la sección 6.1 de arquitectura.md.
 */
/**
 * Redondea a 2 decimales, que es la precisión con la que la app guarda y muestra números.
 *
 * Es la misma cuenta que hace [formatearNumero] antes de armar el texto, separada acá
 * porque también hace falta **antes de guardar**: un valor por gramo calculado como
 * 1,6666… se mostraría como "1,67" y se guardaría como 1,6666…, y entonces multiplicarlo
 * por los gramos de una receta no daría lo que la pantalla dejó ver. Guardando lo mismo
 * que se muestra, la cuenta cierra.
 *
 * Lo usan la calculadora de valor por gramo (7.2) y los reescalados de receta (8.3.1).
 */
fun redondearADosDecimales(valor: Double): Double = (valor * 100).roundToLong() / 100.0

fun formatearNumero(valor: Double): String {
    val redondeado = redondearADosDecimales(valor)

    // Se trabaja en positivo y el signo se pega al final. Si se usara redondeado.toLong()
    // directamente, (-0,56) daría 0 y se perdería el "-": una pérdida se vería como ganancia.
    val negativo = redondeado < 0
    val absoluto = abs(redondeado)
    val entero = absoluto.toLong()
    val decimal = ((absoluto - entero) * 100).roundToLong().toInt()

    // Locale.US fija "," como separador de miles para poder cambiarlo por "." de forma
    // predecible. Sin fijarlo se usaría el idioma del celular, donde el separador puede
    // ser otro (un espacio, por ejemplo) y entonces el replace no encontraría nada.
    val enteroFmt = String.format(Locale.US, "%,d", entero).replace(",", ".")
    val signo = if (negativo) "-" else ""

    return if (decimal == 0) "$signo$enteroFmt"
    else "$signo$enteroFmt,${decimal.toString().padStart(2, '0')}"
}
