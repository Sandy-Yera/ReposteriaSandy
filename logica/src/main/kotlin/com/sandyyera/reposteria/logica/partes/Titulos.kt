package com.sandyyera.reposteria.logica.partes

import com.sandyyera.reposteria.logica.validaciones.NOMBRE_SECCION_POR_DEFECTO
import com.sandyyera.reposteria.logica.validaciones.debenMostrarseLosNombresDeSeccion

/**
 * Bajo qué título va un paso (8.8).
 *
 * Los títulos **son las secciones de la receta, más "General"**. Se representa con el id de
 * la sección, y `null` es el General: no es una sección con nombre reservado sino la ausencia
 * de sección, y esa diferencia importa — si "General" fuera un nombre, bautizar una sección
 * "General" (que se puede, 8.2) la volvería indistinguible del otro.
 */
typealias TituloDePaso = Long?

/**
 * Cómo se lee el título "General" en la pantalla.
 *
 * Es **la misma palabra** que [NOMBRE_SECCION_POR_DEFECTO] a propósito, y por eso se toma de
 * ahí en vez de escribirla otra vez: las dos dicen lo mismo —"esto no es de ninguna parte en
 * particular"—, una para la sección que la app crea sola y la otra para el paso que no
 * pertenece a ninguna sección. Escritas por separado, cambiar una dejaría la otra atrás sin
 * que nada avisara.
 */
const val TITULO_GENERAL = NOMBRE_SECCION_POR_DEFECTO

/**
 * Una sección de la receta, con lo justo para decidir si puede ser título.
 *
 * Hace falta el nombre y no solo el id porque de él depende si la sección **se muestra**, y
 * una sección que no se muestra tampoco se ofrece (ver [titulosDisponibles]).
 */
data class SeccionParaTitulo(val id: Long, val nombre: String)

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
 * [usadosPorOtrosBloques] son los títulos de **los demás** bloques, no los de todos. La
 * diferencia importa al editar uno que ya existe: si el bloque que se está tocando aportara
 * su propio título a la lista, confirmar "Crema" sobre un bloque que ya era "Crema" se
 * rechazaría a sí mismo. Se recibe la lista en vez de consultarla porque quien la tiene es la
 * pantalla, y pasarla hace que esta regla se pueda probar sin base de datos.
 *
 * [nombreDelTitulo] entra solo para armar el mensaje. Se pide en vez de deducirlo porque acá
 * no hay forma de saber cómo se llama la sección `12`, y un aviso que dijera "el título 12 ya
 * está usado" no le sirve a nadie.
 */
fun errorAlUsarTitulo(
    titulo: TituloDePaso,
    usadosPorOtrosBloques: List<TituloDePaso>,
    nombreDelTitulo: String
): String? = when {
    elTituloSePuedeRepetir(titulo) -> null
    titulo in usadosPorOtrosBloques ->
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
 * **La sección automática no se ofrece mientras siga invisible.** Toda receta nace con una
 * sección llamada "General" que no se muestra hasta que la renombran o llega una segunda
 * (8.2), así que sin esta parte el menú de **cualquier receta recién creada** mostraría dos
 * "General": la sección que nadie vio nunca y el bloque sin sección. Quién es visible ya lo
 * sabe `debenMostrarseLosNombresDeSeccion`, y se usa esa misma en vez de escribir la regla de
 * nuevo — que dos partes de la app discrepen sobre qué secciones existen sería peor que el
 * problema original. En ese estado queda solo el General, que además es lo correcto: una
 * receta de una sola parte no necesita decir a cuál pertenece cada paso.
 *
 * La visibilidad la decide **solo esta función**, porque es la única que ve los nombres;
 * [errorAlUsarTitulo] se ocupa nada más de la repetición.
 *
 * El orden es el de [seccionesDeLaReceta], con el General **al final**: es el que se elige
 * menos veces y el único que no nombra una parte de la receta. La lista nunca queda vacía.
 */
fun titulosDisponibles(
    seccionesDeLaReceta: List<SeccionParaTitulo>,
    usadosPorOtrosBloques: List<TituloDePaso>
): List<TituloDePaso> {
    val seMuestran = debenMostrarseLosNombresDeSeccion(seccionesDeLaReceta.map { it.nombre })
    val visibles = if (seMuestran) seccionesDeLaReceta else emptyList()
    return visibles.map { it.id }.filterNot { it in usadosPorOtrosBloques } + listOf(null)
}

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
