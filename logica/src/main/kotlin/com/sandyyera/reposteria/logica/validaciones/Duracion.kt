package com.sandyyera.reposteria.logica.validaciones

import com.sandyyera.reposteria.logica.duracion.TipoDuracion

/**
 * Las reglas del paso "Duración" (8.4).
 *
 * Es el paso más liviano de la receta y **el único que puede quedar completamente vacío**:
 * no alimenta ninguna cuenta. No entra en el costo, ni en los precios, ni en los sueldos,
 * ni en las simulaciones. Es información para leer después, cuando uno se pregunta si esa
 * torta aguanta hasta el fin de semana.
 *
 * Por eso las reglas de acá son más blandas que las del resto: lo que se valida es que lo
 * escrito **signifique algo**, no que esté completo.
 *
 * Ver la sección 8.4 de arquitectura.md.
 */

/**
 * Cuánto es lo máximo que se puede anotar en un bloque.
 *
 * Como el resto de los topes de la app, no es una regla del negocio: es la red contra el
 * dedo pegado. "300 meses" no es una duración, es un 3 que se escribió tres veces.
 */
const val MAXIMA_CANTIDAD_DE_DURACION = 99

/**
 * Revisa la cantidad de un bloque de duración.
 *
 * **Vacío está bien**: un bloque sin llenar significa "no lo sé", que es una respuesta
 * legítima y la más común. Lo que no sirve es un 0 —"dura cero días" no dice nada, y para
 * eso está el switch de "no apto"— ni un número con decimales, porque las unidades ya
 * bajan de escala (medio día son 12 horas).
 *
 * [apto] en `false` hace que no se revise nada: ahí la cantidad se ignora por completo.
 */
fun errorEnCantidadDeDuracion(texto: String, apto: Boolean): String? {
    if (!apto || texto.isBlank()) return null
    val numero = textoANumero(texto) ?: return "Escribe un número válido"
    return when {
        numero.isNaN() || numero.isInfinite() -> "Escribe un número válido"
        !esNumeroEntero(numero) -> "Usa un número entero, o cambia la unidad"
        numero < 1 -> "Si no corresponde guardarlo así, marca 'No apto'"
        numero > MAXIMA_CANTIDAD_DE_DURACION ->
            "Más de $MAXIMA_CANTIDAD_DE_DURACION parece un error: prueba con otra unidad"
        else -> null
    }
}

/**
 * Si un bloque tiene algo que guardar.
 *
 * Un bloque marcado "no apto" **sí tiene algo que guardar**, aunque no tenga números: que
 * algo no se pueda congelar es justamente el dato. Ese es el caso que se olvida al escribir
 * esto como "tiene cantidad".
 */
fun elBloqueDiceAlgo(apto: Boolean, cantidadTexto: String): Boolean =
    !apto || (cantidadTexto.isNotBlank() && errorEnCantidadDeDuracion(cantidadTexto, apto) == null)

/**
 * El orden en que se muestran los tres bloques.
 *
 * De la forma más común a la menos: casi todo se deja a temperatura ambiente, bastante se
 * refrigera, y congelar es la excepción. Poner primero lo que se llena siempre evita
 * desplazar la pantalla para el caso corriente.
 */
val ORDEN_DE_LOS_BLOQUES: List<TipoDuracion> = listOf(
    TipoDuracion.AMBIENTE,
    TipoDuracion.REFRIGERADA,
    TipoDuracion.CONGELADA
)
