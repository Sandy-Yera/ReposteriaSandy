package com.sandyyera.reposteria.logica.validaciones

/**
 * Las reglas de una receta que no necesitan mirar la base de datos.
 *
 * Mismo formato que el resto de este paquete: devuelven el motivo por el que algo no
 * sirve, o `null` si está bien. No lanzan excepción, porque describen datos que la
 * persona todavía está escribiendo.
 *
 * Ver la sección 8.2 de arquitectura.md.
 */

/** Nombre que se le pone sola a la primera sección mientras la receta tenga una sola. */
const val NOMBRE_SECCION_POR_DEFECTO = "General"

/** Revisa el título de una receta. */
fun errorEnTituloReceta(titulo: String): String? {
    val limpio = titulo.trim()
    return when {
        limpio.isEmpty() -> "La receta necesita un título"
        limpio.length > LARGO_MAXIMO_NOMBRE ->
            "El título no puede pasar de $LARGO_MAXIMO_NOMBRE caracteres"
        else -> null
    }
}

/** Revisa el nombre de una sección dentro de una receta ("Bizcocho", "Crema"). */
fun errorEnNombreSeccion(nombre: String): String? {
    val limpio = nombre.trim()
    return when {
        limpio.isEmpty() -> "La sección necesita un nombre"
        limpio.length > LARGO_MAXIMO_NOMBRE ->
            "El nombre no puede pasar de $LARGO_MAXIMO_NOMBRE caracteres"
        else -> null
    }
}

/**
 * Revisa los gramos de un ingrediente dentro de una receta, tal como vienen escritos.
 *
 * **El cero no se acepta**, a diferencia del valor por gramo de un ingrediente: un
 * ingrediente que va en cantidad cero simplemente no está en la receta, y dejarlo
 * guardado con 0 es una fila que no suma nada y confunde al leer la lista.
 */
fun errorEnCantidadEnGramosTexto(texto: String): String? {
    if (texto.isBlank()) return "Escribe cuántos gramos lleva"
    val numero = textoANumero(texto) ?: return "Escribe un número válido"
    return when {
        numero.isNaN() || numero.isInfinite() -> "Escribe un número válido"
        numero <= 0 -> "La cantidad tiene que ser mayor que cero"
        else -> null
    }
}

/**
 * Si hay que mostrar el encabezado con el nombre de cada sección.
 *
 * Con una sola sección no se muestra: una receta de un solo conjunto no necesita que le
 * pongan un título a "todo lo que lleva". Al aparecer la segunda, los encabezados hacen
 * falta para saber qué va en cada parte, y el camino inverso los vuelve a ocultar (8.2).
 */
fun debeMostrarNombreDeSeccion(cantidadDeSecciones: Int): Boolean = cantidadDeSecciones > 1

/**
 * Qué nombre proponer para la sección que hasta ahora era invisible.
 *
 * Al agregar la segunda sección hay que bautizar la primera, y lo más probable es que sea
 * la parte principal de la receta: en "Torta de manjar" la primera sección suele ser el
 * bizcocho, pero proponer el título completo es más útil que proponer "General", que no
 * dice nada. Es solo una sugerencia editable.
 *
 * Si el título viniera vacío se cae a [NOMBRE_SECCION_POR_DEFECTO], para no proponer un
 * campo en blanco.
 */
fun nombreSugeridoParaPrimeraSeccion(tituloReceta: String): String =
    tituloReceta.trim().take(LARGO_MAXIMO_NOMBRE).ifEmpty { NOMBRE_SECCION_POR_DEFECTO }
