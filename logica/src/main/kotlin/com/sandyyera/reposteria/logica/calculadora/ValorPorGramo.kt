package com.sandyyera.reposteria.logica.calculadora

import com.sandyyera.reposteria.logica.formato.redondearParaGuardar
import com.sandyyera.reposteria.logica.validaciones.errorEnNumeroPositivoTexto
import com.sandyyera.reposteria.logica.validaciones.textoANumero

/**
 * La calculadora de valor por gramo (sección 7.2 de arquitectura.md).
 *
 * Resuelve la cuenta que hay que hacer cada vez que sube un precio: se compra un paquete
 * por un precio y con un peso, y lo que la app necesita es cuánto cuesta **un gramo**.
 * Hacerla a mano en la calculadora del celular es donde se cuela el error caro — dividir
 * por 1.000 de más y guardar un ingrediente mil veces más barato de lo que es.
 */

/** Cuántos gramos tiene un kilo. */
const val GRAMOS_POR_KILO = 1000.0

/**
 * En qué unidad viene escrito el peso del paquete.
 *
 * Existe porque en repostería casi todo se compra en kilos y se usa en gramos, y esa
 * conversión de cabeza es justamente el paso donde se equivoca uno.
 */
enum class UnidadDeCompra {
    GRAMO,
    KILO;

    /** Pasa una cantidad escrita en esta unidad a gramos. */
    fun aGramos(cantidad: Double): Double = when (this) {
        GRAMO -> cantidad
        KILO -> cantidad * GRAMOS_POR_KILO
    }
}

/**
 * Cuánto cuesta un gramo, a partir de lo que se pagó y de cuánto trae el paquete.
 *
 * Devuelve el valor **ya redondeado a 2 decimales**, que es exactamente el que se va a
 * mostrar y a guardar. Redondear acá y no solo al mostrarlo es deliberado: si se guardara
 * 1,6666… mientras la pantalla dice "1,67", multiplicar por los gramos de una receta no
 * daría el número que se vio, y esa diferencia no tendría explicación visible.
 *
 * Lanza excepción si la cantidad no es mayor que cero. Es la última red de seguridad —
 * lo normal es que [errorEnCantidadTexto] lo haya frenado antes, en el campo.
 */
fun valorPorGramo(precioTotal: Double, cantidad: Double, unidad: UnidadDeCompra): Double {
    val gramos = unidad.aGramos(cantidad)
    require(gramos > 0) { "La cantidad tiene que ser mayor que cero para poder dividir" }
    return redondearParaGuardar(precioTotal / gramos)
}

/**
 * Revisa el precio pagado tal como está escrito en el campo.
 *
 * Acepta el 0 por el mismo motivo que [com.sandyyera.reposteria.logica.validaciones.errorEnValorPorGramo]:
 * un ingrediente regalado o sacado de la despensa cuesta 0 y eso es un dato válido.
 */
fun errorEnPrecioTexto(texto: String): String? {
    if (texto.isBlank()) return "Escribe cuánto pagaste"
    val numero = textoANumero(texto) ?: return "Escribe un número válido"
    return when {
        numero.isNaN() || numero.isInfinite() -> "Escribe un número válido"
        numero < 0 -> "El precio no puede ser negativo"
        else -> null
    }
}

/**
 * Revisa la cantidad que trae el paquete.
 *
 * A diferencia del precio, acá el **cero no se acepta**: no es un dato raro pero válido,
 * es una división por cero. Un paquete que no trae nada no tiene valor por gramo.
 */
fun errorEnCantidadTexto(texto: String): String? =
    errorEnNumeroPositivoTexto(texto, "Escribe cuánto trae el paquete")

/**
 * Los problemas de la calculadora, uno por campo.
 *
 * Es un tipo aparte de `ErroresIngrediente` aunque tenga la misma forma: los nombres de
 * los campos son parte de lo que significa, y un tipo genérico de "dos textos o nulos"
 * dejaría de decir cuál es cuál justo donde importa, que es al pintarlos bajo su campo.
 */
data class ErroresCalculadora(
    val precio: String?,
    val cantidad: String?
) {
    /** `true` cuando los dos campos sirven y ya se puede hacer la división. */
    val sirve: Boolean get() = precio == null && cantidad == null
}

/** Revisa de una vez los dos campos de la calculadora, mientras se escribe. */
fun revisarCalculadora(precioTexto: String, cantidadTexto: String): ErroresCalculadora =
    ErroresCalculadora(
        precio = errorEnPrecioTexto(precioTexto),
        cantidad = errorEnCantidadTexto(cantidadTexto)
    )

/**
 * Hace la cuenta a partir de lo escrito, o devuelve `null` si todavía no se puede.
 *
 * Es lo que la pantalla llama en cada tecla para ir mostrando el resultado en vivo. No
 * lanza excepción: mientras se escribe, "todavía no alcanza" es lo normal, no un error.
 */
fun calcularValorPorGramo(
    precioTexto: String,
    cantidadTexto: String,
    unidad: UnidadDeCompra
): Double? {
    if (!revisarCalculadora(precioTexto, cantidadTexto).sirve) return null
    val precio = textoANumero(precioTexto) ?: return null
    val cantidad = textoANumero(cantidadTexto) ?: return null
    return valorPorGramo(precio, cantidad, unidad)
}
