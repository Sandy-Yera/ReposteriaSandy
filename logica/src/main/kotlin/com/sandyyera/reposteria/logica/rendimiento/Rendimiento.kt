package com.sandyyera.reposteria.logica.rendimiento

import com.sandyyera.reposteria.logica.formato.formatearNumero

/** Texto que se muestra cuando la receta no tiene peso final anotado. */
const val PESO_NO_ESPECIFICADO = "No especificado"

/** Texto del campo molde cuando la receta no usa ninguno (una salsa, por ejemplo). */
const val SIN_MOLDE = "No utiliza molde"

/**
 * El aviso que acompaña a un peso final que salió de un reescalado (8.4.1, #4).
 *
 * **Pide comprobar y no da por bueno**, y esa palabra es todo el punto: al cambiar de molde
 * el peso se multiplica por el mismo factor que los ingredientes, pero la proporción es una
 * estimación, no una medición. El peso real depende de cuánta masa quede pegada al molde y
 * de cuánta agua se evapore en el horno, y ninguna de las dos cosas escala con el área.
 *
 * Es constante y no un texto suelto en la pantalla por lo mismo que `MENSAJE_ALTURA_RIESGOSA`:
 * lo que produce el aviso y lo que lo muestra tienen que decir exactamente lo mismo.
 */
const val AVISO_PESO_REESCALADO =
    "El peso de este producto ha sido reescalado automáticamente. " +
        "Por favor, compruebe el peso."

/**
 * Cuánto pesa cada trozo, ya formateado para mostrar.
 *
 * Devuelve texto y no un número porque el peso final es opcional en las recetas con
 * molde: cuando no está, lo que corresponde mostrar es "No especificado", no un cero
 * que se confundiría con un dato real.
 */
fun pesoPorTrozo(pesoFinalG: Double?, trozos: Int): String {
    require(trozos >= 1) { "Una receta siempre tiene al menos 1 trozo (llegó $trozos)" }
    return if (pesoFinalG == null) PESO_NO_ESPECIFICADO
    else formatearNumero(pesoFinalG / trozos)
}
