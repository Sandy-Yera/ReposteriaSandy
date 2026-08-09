package com.sandyyera.reposteria.logica.partes

/**
 * Los atajos que se escriben dentro de un paso (8.8).
 *
 * Van con **dos puntos adelante y atrás** —`:titulo:`, `:ingredientes:`— y no sueltos:
 * equivocarse escribiendo eso es raro, y así el atajo no se dispara solo al escribir la
 * palabra en medio de una frase. "Ahora el titulo se decora" no abre ninguna lista.
 */
enum class AtajoDePaso(val escritura: String, val queHace: String) {
    /**
     * La ayuda: muestra esta misma lista.
     *
     * **Va primero a propósito.** Sandy lo pidió para el momento en que ya no se ve el botón de
     * arriba: *"cuando este muy abajo y no recuerde los comandos, simplemente hago :info: y podré
     * ver el aviso"*. Un atajo que solo sirve si te acuerdas de los atajos sería inútil, así que
     * este es el único que hay que recordar.
     */
    INFO(":info:", "Muestra esta lista de atajos"),

    /** Elegir bajo qué título va este paso: una sección de la receta, o "General". */
    TITULO(":titulo:", "Elige bajo qué título va este paso"),

    /** Insertar un ingrediente de la receta dentro del texto del paso. */
    INGREDIENTES(":ingredientes:", "Escribe un ingrediente de la receta")
}

/**
 * Qué atajo hay escrito justo antes del cursor, o `null` si ninguno.
 *
 * Mira **lo que hay hasta el cursor** y no el texto completo: el atajo se dispara al terminar
 * de escribirlo, ahí donde está la mano, no porque la palabra aparezca en otro renglón que ya
 * se resolvió hace rato.
 *
 * Se compara en minúsculas porque el teclado del celular pone mayúscula al empezar una
 * oración, y `:Titulo:` es lo mismo que quiso escribir.
 */
fun atajoAntesDelCursor(texto: String, cursor: Int): AtajoDePaso? {
    val hastaElCursor = texto.take(cursor.coerceIn(0, texto.length)).lowercase()
    return AtajoDePaso.entries.firstOrNull { hastaElCursor.endsWith(it.escritura) }
}

/**
 * Saca el atajo del texto, dejando en su lugar lo que se eligió.
 *
 * Recibe el mismo cursor con que se detectó, así reemplaza **esa** aparición y no la primera
 * que encuentre: en un paso que ya usó `:ingredientes:` dos veces, `indexOf` cambiaría la
 * equivocada.
 *
 * Devuelve el texto y dónde queda el cursor, por lo mismo que `formatearMientrasSeEscribe`:
 * el largo cambia, así que conservar la posición como número la deja donde no va.
 */
fun reemplazarAtajo(
    texto: String,
    cursor: Int,
    atajo: AtajoDePaso,
    porEsto: String
): TextoDePaso {
    val hasta = cursor.coerceIn(0, texto.length)
    val desde = hasta - atajo.escritura.length
    if (desde < 0) return TextoDePaso(texto, hasta)

    val resultado = texto.take(desde) + porEsto + texto.substring(hasta)
    return TextoDePaso(resultado, desde + porEsto.length)
}

/** Un texto de paso y dónde quedó el cursor. Mismo papel que `TextoConCursor` en formato. */
data class TextoDePaso(val texto: String, val cursor: Int)
