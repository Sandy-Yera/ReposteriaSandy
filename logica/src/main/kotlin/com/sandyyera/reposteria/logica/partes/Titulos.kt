package com.sandyyera.reposteria.logica.partes

/**
 * Bajo qué título va un paso (8.8).
 *
 * Los títulos **son las secciones de la receta, más "General"**. Se representa con el id de
 * la sección, y `null` es el General: no es una sección con nombre reservado sino la ausencia
 * de sección, y esa diferencia importa — si "General" fuera un nombre, bautizar una sección
 * "General" (que se puede, 8.2) la volvería indistinguible del otro.
 */
typealias TituloDePaso = Long?

/** Cómo se lee el título "General" en la pantalla. */
const val TITULO_GENERAL = "General"

/**
 * Si un título se puede usar más de una vez en el mismo listado (8.8).
 *
 * **Solo "General".** Un paso general es algo que no pertenece a ninguna parte —precalentar el
 * horno, dejar enfriar— y esos aparecen naturalmente entre medio de las partes, así que el
 * bloque tiene que poder repetirse.
 *
 * Los que nombran una sección **se usan una sola vez**: dos bloques "Crema" en el mismo
 * listado no dicen en cuál va cada cosa.
 */
fun elTituloSePuedeRepetir(titulo: TituloDePaso): Boolean = titulo == null

/**
 * Revisa si se puede abrir un bloque con este título, o devuelve el motivo.
 *
 * Recibe **los títulos ya usados** y no consulta nada: quién los tiene es la pantalla, y
 * pasarlos hace que esta regla se pueda probar sin base de datos.
 *
 * [nombreDelTitulo] entra solo para armar el mensaje. Se pide en vez de deducirlo porque acá
 * no hay forma de saber cómo se llama la sección `12`, y un aviso que dijera "el título 12 ya
 * está usado" no le sirve a nadie.
 */
fun errorAlUsarTitulo(
    titulo: TituloDePaso,
    yaUsados: List<TituloDePaso>,
    nombreDelTitulo: String
): String? = when {
    elTituloSePuedeRepetir(titulo) -> null
    titulo in yaUsados ->
        "Ya hay un bloque '$nombreDelTitulo'. Dos no dirían en cuál va cada paso."
    else -> null
}

/**
 * Los títulos que quedan disponibles para abrir un bloque nuevo.
 *
 * "General" **siempre está**, aunque ya se haya usado; las secciones desaparecen de la lista
 * al usarse. Devolver la lista en vez de un `Boolean` por cada una es lo que permite que la
 * pantalla dibuje el menú sin volver a aplicar la regla por su cuenta — y que no pueda
 * ofrecer algo que después se rechaza.
 *
 * El orden es el de [seccionesDeLaReceta], con el General **al final**: es el que se elige
 * menos veces y el único que no nombra una parte de la receta.
 */
fun titulosDisponibles(
    seccionesDeLaReceta: List<Long>,
    yaUsados: List<TituloDePaso>
): List<TituloDePaso> = seccionesDeLaReceta.filterNot { it in yaUsados } + listOf(null)

/**
 * Si dos bloques seguidos se pueden juntar en uno.
 *
 * Solo pasa con los generales, que son los únicos repetibles: dos bloques "General" pegados
 * uno tras otro **son el mismo bloque partido en dos**, y mostrarlos separados repite el
 * encabezado sin que la separación signifique nada. Entre medio de otras secciones sí tiene
 * sentido que haya varios.
 *
 * No los junta —eso es de quien dibuja—, solo dice cuándo corresponde.
 */
fun seJuntanLosBloques(uno: TituloDePaso, elSiguiente: TituloDePaso): Boolean =
    uno == null && elSiguiente == null
