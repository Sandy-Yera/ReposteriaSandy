package com.sandyyera.reposteria.logica.busqueda

import java.text.Normalizer

private val MARCAS_DE_ACENTO = Regex("\\p{Mn}+")

/**
 * Quita los acentos de un texto dejando la letra base: "plátano" -> "platano".
 *
 * Se separa la letra de su acento (forma NFD) y se borran las marcas sueltas.
 *
 * Esto también convierte la **ñ en n**, y es deliberado: buscando "pina" tiene que
 * aparecer "Piña" sin obligar a cambiar de teclado. La contrapartida es que al validar
 * nombres repetidos, "pina" y "piña" se consideran parecidos y se avisa — avisar de más
 * es preferible a dejar entrar dos veces el mismo ingrediente escrito distinto.
 */
internal fun sinTildes(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(MARCAS_DE_ACENTO, "")

/**
 * Dice si lo buscado aparece en cualquier parte del campo, ignorando mayúsculas y tildes.
 *
 * Buscar "limon" encuentra "Mousse de limón", y buscar "PLATANO" encuentra "Plátano".
 * Lo usan las 4 pantallas con buscador (sección 12.2 de arquitectura.md).
 */
fun coincide(textoBusqueda: String, campo: String): Boolean =
    sinTildes(campo).contains(sinTildes(textoBusqueda), ignoreCase = true)

/**
 * Dice si dos textos son el mismo nombre, ignorando mayúsculas, tildes y espacios sobrantes.
 *
 * A diferencia de [coincide], que busca una parte dentro de otra, acá los dos textos tienen
 * que ser el mismo completo: "Azúcar" y "azucar " son el mismo ingrediente, pero "azúcar
 * flor" no lo es.
 *
 * Sirve para avisar de un ingrediente repetido antes de crearlo. La base de datos por su
 * cuenta no alcanza: sabe ignorar mayúsculas, pero para ella "azucar" y "azúcar" son
 * nombres distintos.
 */
fun sonElMismoTexto(a: String, b: String): Boolean =
    sinTildes(a.trim()).equals(sinTildes(b.trim()), ignoreCase = true)

/**
 * Deja de una lista solo lo que coincide con lo buscado.
 *
 * Con el buscador vacío devuelve la lista completa, que es lo que corresponde: no haber
 * escrito nada no es lo mismo que no encontrar nada.
 *
 * Es genérica a propósito. Las cuatro secciones con buscador —ingredientes, recetas,
 * moldes y empleados— filtran listas de tipos distintos por un texto distinto, pero la
 * regla de qué cuenta como coincidencia tiene que ser una sola. [texto] dice de dónde
 * sacar lo que se compara en cada caso.
 *
 *     filtrarPor(ingredientes, "limon") { it.nombre }
 *     filtrarPor(recetas, "torta") { it.titulo }
 */
fun <T> filtrarPor(items: List<T>, busqueda: String, texto: (T) -> String): List<T> {
    if (busqueda.isBlank()) return items
    return items.filter { coincide(busqueda, texto(it)) }
}

/**
 * De una lista, marca cuáles repiten un nombre que ya apareció antes.
 *
 * Devuelve una lista de `Boolean` en el mismo orden: `true` en los que están de más.
 * **El primero de cada nombre nunca se marca** — se da por bueno el que llegó primero, y
 * los siguientes son los que sobran. Por eso el orden de entrada importa: hay que pasarla
 * ordenada por antigüedad para que el que se conserve sea el original y no uno cualquiera.
 *
 * Compara con [sonElMismoTexto], así que "Torta", "torta" y "TORTA" cuentan como el mismo.
 *
 * Existe para los datos que ya estaban antes de prohibir los repetidos: no se pueden
 * borrar solos —serían datos reales desapareciendo sin aviso— pero sí se pueden señalar
 * para que la pantalla los trate distinto.
 */
fun <T> marcarRepetidos(items: List<T>, texto: (T) -> String): List<Boolean> {
    val yaVistos = mutableListOf<String>()
    return items.map { item ->
        val nombre = texto(item)
        val repetido = yaVistos.any { sonElMismoTexto(it, nombre) }
        if (!repetido) yaVistos += nombre
        repetido
    }
}
