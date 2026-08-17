package com.sandyyera.reposteria.logica.validaciones

/**
 * Las reglas del texto de un paso de receta (8.8).
 *
 * **Un paso no es un nombre**, y por eso no reutiliza `errorEnNombreEscrito`: aquel mide 60
 * caracteres, que alcanzan para "Bizcocho de chocolate" y no para "Batir las claras a punto de
 * nieve con una pizca de sal, incorporando el azúcar en tres tandas". Son dos cosas distintas
 * con dos topes distintos, y compartir la función habría obligado a subir el tope de los
 * nombres —o sea a dejar pasar un nombre de sección de 300 caracteres, que no cabe en ninguna
 * pantalla.
 */

/**
 * Cuántos caracteres puede tener un paso.
 *
 * Como el resto de los topes de la app (`MAXIMO_TROZOS`, `MAXIMA_CANTIDAD_DE_PRECIO`), **no es
 * una regla del negocio: es la red contra el dedo pegado** — o acá, contra pegar sin querer un
 * documento entero dentro de un paso. Mil caracteres son unas 150 palabras: mucho más de lo que
 * ocupa cualquier paso real, y poco comparado con lo que llega de un pegado accidental.
 */
const val LARGO_MAXIMO_PASO = 1000

/**
 * Lo que dice el "antes de empezar" cuando no hay nada que tener listo de antemano (8.8).
 *
 * Existe como constante y no como texto suelto porque **se escribe en dos lados**: es el valor con
 * que nace una receta y es lo que se vuelve a guardar cuando alguien vacía el campo. Con la frase
 * copiada a mano en los dos, bastaba cambiar una para que la mitad de las recetas dijera una cosa
 * y la otra mitad otra.
 *
 * Es una frase y no un vacío a propósito: el resumen la muestra como "Antes de empezar: …", y un
 * hueco ahí se lee como que falta llenarlo. "No necesita" contesta la pregunta.
 */
const val SIN_PASO_PREVIO = "No necesita"

/**
 * Si el "antes de empezar" dice algo, o sea si hay algo que tener listo antes de arrancar.
 *
 * La frase por defecto **cuenta como vacío**: una receta recién creada dice "No necesita" porque
 * nadie escribió nada todavía, no porque alguien haya decidido eso. Sin esto, el campo se abriría
 * con "No necesita" escrito adentro y habría que borrarlo a mano antes de poder poner lo propio.
 */
fun elPasoPrevioDiceAlgo(texto: String): Boolean =
    texto.isNotBlank() && texto.trim() != SIN_PASO_PREVIO

/**
 * Si un paso dice algo, o sea si hay algo que guardar.
 *
 * Es el gemelo de `elBloqueDiceAlgo` del paso de duración, y por el mismo motivo: **un paso que
 * quedó en blanco no es un error que corregir, es un paso que se borró**. Quien vacía el campo
 * está diciendo "este paso ya no va", y guardarlo como una fila vacía dejaría un número en la
 * lista que no dice nada — con la numeración corrida de 8.8, además, correría todos los de
 * abajo por un paso fantasma.
 *
 * Los espacios no cuentan: un paso con tres espacios está tan vacío como uno sin nada.
 */
fun elPasoDiceAlgo(texto: String): Boolean = texto.isNotBlank()

/**
 * Revisa el texto de un paso: devuelve el motivo del problema, o `null` si está bien.
 *
 * **Acepta el vacío a propósito**, igual que `errorEnCantidadDeDuracion`: ahí no hay nada que
 * corregir, hay un paso que se borra (ver [elPasoDiceAlgo]). Exigir texto obligaría a llenar un
 * campo antes de poder deshacerse de él, que es lo contrario de lo que la persona quiere hacer.
 *
 * Lo único que rechaza es pasarse del tope, y ahí el aviso **dice cuántos van y cuántos caben**:
 * "es demasiado largo" a secas, sobre un texto que uno no va a contar a mano, no dice qué hacer.
 */
fun errorEnTextoDePaso(texto: String): String? = when {
    !elPasoDiceAlgo(texto) -> null
    texto.length > LARGO_MAXIMO_PASO ->
        "El paso es muy largo (${texto.length} de $LARGO_MAXIMO_PASO). Pártelo en dos."
    else -> null
}
