package com.sandyyera.reposteria.logica.validaciones

import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.PrecioVigente

/**
 * Las reglas del paso "Rendimiento" (8.3), que no necesitan mirar la base de datos.
 *
 * Mismo formato que el resto del paquete: devuelven el motivo o `null`.
 *
 * Ver las secciones 6.2 y 8.3 de arquitectura.md.
 */

/** En cuántos trozos como máximo tiene sentido cortar un producto. */
const val MAXIMO_TROZOS = 200

/**
 * Revisa en cuántos trozos rinde la receta, tal como está escrito en el campo.
 *
 * El mínimo es 1 y no 0: **cada división por trozos de la app depende de esto**
 * (`costoPorTrozo`, `precioPorTrozoDe`, `pesoPorTrozo`), y por eso la receta se siembra con
 * `trozos = 1` al crearse (8.10). Un 0 acá no es un dato raro pero válido: es una división
 * por cero esperando en otra pantalla.
 *
 * El tope de [MAXIMO_TROZOS] no es una regla del negocio sino una red contra el dedo
 * pegado: escribir 8.000 en vez de 8 deja el costo por trozo en cero y todo lo que sale de
 * ahí sin sentido, sin ningún aviso.
 */
fun errorEnTrozosTexto(texto: String): String? {
    if (texto.isBlank()) return "Escribe en cuántos trozos rinde"
    val numero = textoANumero(texto) ?: return "Escribe un número válido"
    return when {
        numero.isNaN() || numero.isInfinite() -> "Escribe un número válido"
        !esNumeroEntero(numero) -> "Los trozos son un número entero"
        numero < 1 -> "Tiene que rendir al menos 1 trozo"
        numero > MAXIMO_TROZOS -> "¿Seguro? Más de $MAXIMO_TROZOS trozos parece un error"
        else -> null
    }
}

/**
 * Revisa el peso final del producto terminado.
 *
 * **Es obligatorio solo cuando la receta no usa molde** (6.2): sin molde es lo único contra
 * lo que se puede reescalar, así que dejarlo vacío deja la receta sin forma de crecer. Con
 * molde es opcional y su ausencia se muestra como "No especificado".
 *
 * Es un peso **pesado, no calculado**: el mismo molde da pesos distintos según la receta,
 * así que las medidas del molde no lo reemplazan.
 */
fun errorEnPesoFinalTexto(texto: String, usaMolde: Boolean): String? {
    if (texto.isBlank()) {
        return if (usaMolde) null else "Sin molde, el peso final es obligatorio"
    }
    return errorEnNumeroPositivoTexto(texto, "Escribe cuánto pesa el producto terminado")
}

/**
 * Las promociones que quedarían imposibles al bajar los trozos de la receta.
 *
 * Una promo "3 trozos por $1.500" en una receta que pasa a rendir 2 no significa nada: no
 * hay tres trozos que vender. Es el **tope del último trozo** de 6.2.
 *
 * Solo aplica a las promociones por trozo. En modo producto no hay tope: vender 3 productos
 * completos es perfectamente posible por más que cada uno rinda 2 trozos.
 *
 * Devuelve la lista, y no un `Boolean`, porque el aviso tiene que **nombrar cuáles**: decir
 * "hay promociones que no caben" obliga a revisarlas todas a mano.
 */
fun promocionesQueNoCabenEn(trozos: Int, precios: List<PrecioVigente>): List<PrecioVigente> =
    precios.filter { it.modo == ModoPrecio.TROZO && it.cantidad > trozos }

/** Cómo se llama una promoción en un aviso: su etiqueta, o su forma si no tiene. */
fun descripcionDePromocion(precio: PrecioVigente): String =
    precio.etiqueta?.takeIf { it.isNotBlank() }
        ?: "${precio.cantidad} ${if (precio.cantidad == 1) "trozo" else "trozos"}"

/**
 * Los problemas del formulario de rendimiento, uno por campo.
 *
 * Por campo y no como un mensaje único, igual que en el resto: cada aviso va bajo el suyo.
 */
data class ErroresRendimiento(
    val trozos: String? = null,
    val pesoFinal: String? = null
) {
    val sirve: Boolean get() = trozos == null && pesoFinal == null
}

/** Revisa de una vez los dos campos del paso de rendimiento, mientras se escribe. */
fun revisarRendimiento(
    trozosTexto: String,
    pesoFinalTexto: String,
    usaMolde: Boolean
): ErroresRendimiento = ErroresRendimiento(
    trozos = errorEnTrozosTexto(trozosTexto),
    pesoFinal = errorEnPesoFinalTexto(pesoFinalTexto, usaMolde)
)
