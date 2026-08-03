package com.sandyyera.reposteria.logica.validaciones

import com.sandyyera.reposteria.logica.precios.ModoPrecio

/**
 * Las reglas del formulario de un precio o promoción (6.2 y 8.5).
 *
 * Es lo que faltaba para la Fase 7: las fórmulas que usan los precios ya estaban escritas y
 * probadas, pero **nada revisaba lo que se escribe antes de crearlos**, y de ahí salen dos
 * divisiones por cero que reventarían mucho después y en otra pantalla —`precioPorTrozoDe` y
 * `trozoGanador`— sin ninguna pista de dónde vinieron.
 */

/**
 * Cuántos trozos o productos como máximo puede cubrir una promoción.
 *
 * Como el resto de los topes de la app, no es una regla del negocio: es la red contra el dedo
 * pegado. "200 trozos por $1.500" no es una promoción, es un 2 que se escribió tres veces. El
 * tope real en modo trozo lo pone la receta (ver [errorEnCantidadDePrecio]); este solo existe
 * para el modo producto, que no tiene ninguno.
 */
const val MAXIMA_CANTIDAD_DE_PRECIO = 200

/**
 * Revisa el precio total escrito.
 *
 * **Rechaza el 0**, a diferencia del valor por gramo de un ingrediente: un ingrediente
 * regalado cuesta 0 y eso es un dato; un precio de venta en 0 no es "lo regalo", es una
 * división por cero esperando en `precioPorTrozoDe`.
 */
fun errorEnPrecioTotalTexto(texto: String): String? =
    errorEnNumeroPositivoTexto(texto, "Escribe a cuánto lo vendes")

/**
 * Revisa cuántos trozos (o productos) cubre el precio.
 *
 * [trozosDeLaReceta] es el **tope del último trozo** de 6.2: no tiene sentido una promo de
 * "3 trozos por $1.500" en una receta que rinde 2. **Solo aplica en modo trozo** — vender 3
 * productos completos es perfectamente posible por más que cada uno rinda 2, y confundir las
 * dos cosas fue justamente lo que hizo falta separar.
 *
 * Se pide entero: media promoción no existe.
 */
fun errorEnCantidadDePrecio(
    texto: String,
    modo: ModoPrecio,
    trozosDeLaReceta: Int
): String? {
    if (texto.isBlank()) return "Escribe cuántos lleva"
    val numero = textoANumero(texto) ?: return "Escribe un número válido"
    return when {
        numero.isNaN() || numero.isInfinite() -> "Escribe un número válido"
        numero != numero.toInt().toDouble() -> "Tiene que ser un número entero"
        numero < 1 -> "Tiene que ser al menos 1"
        numero > MAXIMA_CANTIDAD_DE_PRECIO ->
            "Más de $MAXIMA_CANTIDAD_DE_PRECIO parece un error"
        // El tope de la receta va al final: es el más específico y el que más explica.
        modo == ModoPrecio.TROZO && numero > trozosDeLaReceta ->
            "Esta receta rinde $trozosDeLaReceta trozos: la promoción no cabe"
        else -> null
    }
}

/**
 * Revisa la etiqueta de una promoción, que es opcional.
 *
 * Vacía está bien: una promoción sin nombre se describe sola por su forma ("3 trozos"), y eso
 * ya lo resuelve `descripcionDePromocion`. Lo único que se revisa es que quepa, con el mismo
 * tope que cualquier otro nombre escrito a mano.
 */
fun errorEnEtiquetaDePrecio(texto: String): String? =
    if (texto.trim().length > LARGO_MAXIMO_NOMBRE) {
        "El nombre no puede pasar de $LARGO_MAXIMO_NOMBRE caracteres"
    } else {
        null
    }

/** Los problemas del formulario de un precio, uno por campo. */
data class ErroresPrecio(
    val precioTotal: String? = null,
    val cantidad: String? = null,
    val etiqueta: String? = null
) {
    val sirve: Boolean get() = precioTotal == null && cantidad == null && etiqueta == null
}

/**
 * Revisa de una vez el formulario completo, mientras se escribe.
 *
 * Va por campo y no como un solo mensaje por lo mismo que `ErroresIngrediente`: la pantalla
 * tiene que poder mostrar cada aviso **bajo el campo que lo causó**, y con el teclado abierto
 * un mensaje en la franja de abajo no se ve (8.2).
 *
 * **No reemplaza la comprobación del repositorio.** Esta corre en cada tecla para habilitar o
 * no el botón; la que decide de verdad es la de allá, que además sabe algo que acá no se
 * puede saber: si el precio elegido como referencia pierde plata (`errorAlElegirReferencia`).
 */
fun revisarPrecio(
    precioTotalTexto: String,
    cantidadTexto: String,
    modo: ModoPrecio,
    trozosDeLaReceta: Int,
    etiqueta: String = ""
): ErroresPrecio = ErroresPrecio(
    precioTotal = errorEnPrecioTotalTexto(precioTotalTexto),
    cantidad = errorEnCantidadDePrecio(cantidadTexto, modo, trozosDeLaReceta),
    etiqueta = errorEnEtiquetaDePrecio(etiqueta)
)
