package com.sandyyera.reposteria.logica.rendimiento

import com.sandyyera.reposteria.logica.formato.formatearNumero

/** Texto que se muestra cuando la receta no tiene peso final anotado. */
const val PESO_NO_ESPECIFICADO = "No especificado"

/** Texto del campo molde cuando la receta no usa ninguno (una salsa, por ejemplo). */
const val SIN_MOLDE = "No utiliza molde"

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
