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
fun errorEnCantidadEnGramosTexto(texto: String): String? =
    errorEnNumeroPositivoTexto(texto, "Escribe cuántos gramos lleva")

/**
 * Si hay que mostrar los encabezados con el nombre de cada sección.
 *
 * Se muestran cuando hay dos o más — ahí hacen falta para saber qué va en cada parte — y
 * también cuando queda una sola **pero con un nombre puesto a mano**.
 *
 * Esa segunda parte se agregó al descubrir el caso al revés: con "Bizcocho" y "Salsa", al
 * borrar el bizcocho la salsa quedaba sola y su encabezado desaparecía. El nombre seguía
 * guardado, pero desde la pantalla parecía haberse perdido — y era un nombre que la
 * persona había escrito a propósito. **Lo que uno escribe no se esconde solo.**
 *
 * La única sección que se oculta es la automática, la que todavía se llama
 * [NOMBRE_SECCION_POR_DEFECTO] porque nadie la tocó: esa no la puso nadie y no dice nada.
 */
fun debenMostrarseLosNombresDeSeccion(nombres: List<String>): Boolean = when {
    nombres.size > 1 -> true
    nombres.size == 1 -> !esNombreAutomaticoDeSeccion(nombres.single())
    else -> false
}

/**
 * Si una sección todavía tiene el nombre que le puso la app y no uno elegido.
 *
 * Se compara ignorando mayúsculas y espacios, no por un campo aparte en la base. La
 * contrapartida está aceptada: alguien que bautice una sección exactamente "General" verá
 * que se comporta como la automática. Es un caso raro y sin consecuencias —el nombre queda
 * guardado igual— y evita arrastrar una columna más por una distinción que casi nunca
 * importa.
 */
fun esNombreAutomaticoDeSeccion(nombre: String): Boolean =
    nombre.trim().equals(NOMBRE_SECCION_POR_DEFECTO, ignoreCase = true)

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
