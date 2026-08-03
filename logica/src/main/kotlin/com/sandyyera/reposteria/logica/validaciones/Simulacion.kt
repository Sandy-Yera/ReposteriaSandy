package com.sandyyera.reposteria.logica.validaciones

/**
 * Las reglas de los dos campos de "Ganancias simuladas" (6.2 y 8.7).
 *
 * Es el hueco que quedaba de la Fase 8: `simulacion()` estaba escrita y probada desde hace
 * fases, pero **nada revisaba lo que se escribe antes de llamarla**. Los dos campos se
 * multiplican directamente contra el ingreso, así que un número absurdo no revienta nada — y
 * eso es justamente el problema: sale una proyección de treinta millones sin que nada avise.
 */

/** Los días que tiene una semana. No es una constante configurable: es un calendario. */
const val DIAS_MAXIMOS_POR_SEMANA = 7

/**
 * Cuántas unidades por día como máximo se aceptan.
 *
 * Como el resto de los topes de la app, no es una regla del negocio sino la red contra el
 * dedo pegado. La diferencia con los otros es que acá **nada explota**: el número se
 * multiplica y ya, así que sin tope un 200 escrito en vez de un 20 sale como una proyección
 * mensual perfectamente creíble y diez veces falsa.
 */
const val MAXIMAS_UNIDADES_POR_DIA = 500

/**
 * Revisa cuántos días a la semana se vende.
 *
 * Entre 1 y 7. **El 0 se rechaza**: "no la vendo ningún día" no se dice dejando los días en
 * cero — eso deja la simulación entera en cero y parece un error de la app. Para eso están
 * las unidades por día, que sí aceptan el 0.
 */
fun errorEnDiasPorSemanaTexto(texto: String): String? {
    if (texto.isBlank()) return "Escribe cuántos días la vendes"
    val numero = textoANumero(texto) ?: return "Escribe un número válido"
    return when {
        numero.isNaN() || numero.isInfinite() -> "Escribe un número válido"
        numero != numero.toInt().toDouble() -> "Tiene que ser un número entero de días"
        numero < 1 -> "Al menos un día. Si no la vendes, pon 0 unidades por día"
        numero > DIAS_MAXIMOS_POR_SEMANA ->
            "Una semana tiene $DIAS_MAXIMOS_POR_SEMANA días"
        else -> null
    }
}

/**
 * Revisa cuántas unidades se venden por día.
 *
 * **Acepta el 0 a propósito**, al revés que los días: es como se dice "esta receta todavía no
 * la vendo" sin borrar el resto de lo configurado. La simulación da cero, que es la respuesta
 * correcta y no un error.
 */
fun errorEnUnidadesPorDiaTexto(texto: String): String? {
    if (texto.isBlank()) return "Escribe cuántas vendes por día"
    val numero = textoANumero(texto) ?: return "Escribe un número válido"
    return when {
        numero.isNaN() || numero.isInfinite() -> "Escribe un número válido"
        numero != numero.toInt().toDouble() -> "Tiene que ser un número entero de unidades"
        numero < 0 -> "No puede ser negativo"
        numero > MAXIMAS_UNIDADES_POR_DIA ->
            "Más de $MAXIMAS_UNIDADES_POR_DIA al día parece un error"
        else -> null
    }
}

/** Los problemas de los dos campos de la simulación, uno por campo. */
data class ErroresSimulacion(
    val diasPorSemana: String? = null,
    val unidadesPorDia: String? = null
) {
    val sirve: Boolean get() = diasPorSemana == null && unidadesPorDia == null
}

/**
 * Revisa los dos campos de una vez, mientras se escribe.
 *
 * Va por campo y no como un solo mensaje por lo mismo que el resto: cada aviso se pinta bajo
 * el campo que lo causó, porque con el teclado abierto la franja de abajo no se ve (8.2).
 */
fun revisarSimulacion(diasTexto: String, unidadesTexto: String): ErroresSimulacion =
    ErroresSimulacion(
        diasPorSemana = errorEnDiasPorSemanaTexto(diasTexto),
        unidadesPorDia = errorEnUnidadesPorDiaTexto(unidadesTexto)
    )
