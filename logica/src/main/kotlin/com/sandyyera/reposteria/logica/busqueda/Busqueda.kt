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
