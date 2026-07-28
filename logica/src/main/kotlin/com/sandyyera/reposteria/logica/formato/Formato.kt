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

/** Cuántos decimales se pueden escribir, que son los que la app después guarda. */
const val MAXIMO_DECIMALES = 2

/**
 * Pone los puntos de mil **mientras se escribe**, sin tocar lo que todavía no está escrito.
 *
 * Es la hermana de [formatearNumero], y existe porque aquella no sirve para esto. Aquella
 * trabaja sobre un número ya terminado, y aplicada tecla por tecla arruina lo que se está
 * escribiendo:
 *
 * | Escrito   | Con `formatearNumero` | Con esta   |
 * |-----------|-----------------------|------------|
 * | `1000,`   | `1.000` (se come la coma, no se pueden escribir decimales) | `1.000,` |
 * | `1000,5`  | `1.000,50` (inventa un cero) | `1.000,5` |
 * | `1,555`   | `1,56` (redondea antes de tiempo) | `1,55` |
 *
 * Reglas, todas al servicio de lo mismo — que lo que se ve sea lo que se escribió:
 * - Agrupa **solo la parte entera**. Lo que va después de la coma queda tal cual, con sus
 *   ceros a la derecha y con la coma sola si todavía no viene nada.
 * - Descarta los puntos que vengan: son separadores de miles, los pone esta función. Por
 *   eso aplicarla sobre su propio resultado no lo cambia.
 * - Descarta cualquier otro carácter, incluido el signo menos. Los tres campos que la usan
 *   no aceptan negativos, así que es una tecla que no tiene nada que hacer ahí.
 * - Corta en [MAXIMO_DECIMALES] decimales, que es lo que la app guarda. Dejar escribir un
 *   tercero mostraría una precisión que se va a perder igual al guardar.
 * - Saca los ceros de más a la izquierda, pero deja el "0" de "0,5".
 *
 * El resultado siempre lo entiende
 * [com.sandyyera.reposteria.logica.validaciones.textoANumero], que es lo que después lo
 * convierte a número.
 */
fun formatearMientrasSeEscribe(texto: String): String {
    val enteros = StringBuilder()
    val decimales = StringBuilder()
    var hayComa = false

    for (caracter in texto) {
        when {
            caracter == ',' && !hayComa -> hayComa = true
            !caracter.isDigit() -> Unit
            !hayComa -> enteros.append(caracter)
            decimales.length < MAXIMO_DECIMALES -> decimales.append(caracter)
        }
    }

    val sinCerosSobrantes = enteros.toString().trimStart('0')
    val parteEntera = when {
        sinCerosSobrantes.isNotEmpty() -> sinCerosSobrantes
        // Se escribieron solo ceros, o se empezó por la coma: hace falta un 0 adelante.
        enteros.isNotEmpty() || hayComa -> "0"
        // No se escribió nada todavía; el campo queda vacío y no con un 0 puesto solo.
        else -> ""
    }

    val agrupada = agruparDeTresEnTres(parteEntera)
    return if (hayComa) "$agrupada,$decimales" else agrupada
}

/** Mete un punto cada tres dígitos, contando desde la derecha: "1000" -> "1.000". */
private fun agruparDeTresEnTres(digitos: String): String {
    if (digitos.length <= 3) return digitos
    val resultado = StringBuilder()
    for ((posicion, digito) in digitos.withIndex()) {
        // Cuántos dígitos quedan a la derecha de este. Se separa cada vez que es múltiplo
        // de 3, salvo al final del todo.
        val faltan = digitos.length - posicion
        resultado.append(digito)
        if (faltan > 1 && (faltan - 1) % 3 == 0) resultado.append('.')
    }
    return resultado.toString()
}
