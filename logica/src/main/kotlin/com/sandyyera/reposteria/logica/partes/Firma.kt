package com.sandyyera.reposteria.logica.partes

import com.sandyyera.reposteria.logica.formato.formatearNumero

/**
 * La foto de una receta al momento de copiarla dentro de otra (8.11.5).
 *
 * **No es un diff.** Un diff de verdad —qué línea cambió, qué palabra— es caro de calcular y
 * más caro de leer. Esto guarda un puñado de números y los compara con los de ahora, que
 * alcanza para decir qué se movió de estructura y de cantidades.
 *
 * Lleva **la cantidad de cada ingrediente** y no solo cuántos hay, y esa es la parte que la
 * convierte en algo más que contadores: al actualizar una copia las cantidades se adaptan
 * *en proporción* (8.11.3), y para calcular esa proporción hay que saber de cuánto a cuánto
 * cambió cada ingrediente. Contando ingredientes no se puede.
 *
 * **Lo que no detecta, y está aceptado:** reescribir el texto de un paso sin cambiar cuántos
 * hay, y renombrar una sección. Es el precio de no hacer un diff.
 */
data class FirmaDeReceta(
    /** Nombre de sección → (nombre de ingrediente → cuántos gramos lleva). */
    val secciones: Map<String, Map<String, Double>>,
    /** Título de los pasos → cuántos pasos van bajo él (8.8). */
    val pasosPorTitulo: Map<String, Int>,
    /** Cuántos pasos hay sin título, los "General" de la receta. */
    val pasosGenerales: Int
) {
    val cuantasSecciones: Int get() = secciones.size

    val cuantosIngredientes: Int get() = secciones.values.sumOf { it.size }

    val cuantosTitulos: Int get() = pasosPorTitulo.size
}

/** Qué cambió en la receta original desde que se copió. Cada uno se lee tal cual (8.11.5). */
data class CambioDetectado(val frase: String)

/**
 * Compara dos fotos de la misma receta y arma las frases de "¿Qué cambió?".
 *
 * Devuelve **frases y no una estructura** porque es lo único que se hace con esto: mostrarlo.
 * Una estructura obligaría a cada pantalla a decidir cómo se lee cada caso, y ahí es donde
 * aparecen los "1 ingredientes".
 *
 * El orden va de lo más grande a lo más chico —secciones, ingredientes, cantidades, pasos—
 * porque es el orden en que uno se pregunta qué pasó: primero si falta una parte entera,
 * después si falta algo dentro de ella.
 *
 * Una lista vacía significa que **nada de lo que esto mira cambió**, que no es lo mismo que
 * "la receta está idéntica": ver arriba lo que no detecta.
 */
fun compararFirmas(antes: FirmaDeReceta, ahora: FirmaDeReceta): List<CambioDetectado> {
    val cambios = mutableListOf<String>()

    val seccionesIdas = antes.secciones.keys - ahora.secciones.keys
    val seccionesNuevas = ahora.secciones.keys - antes.secciones.keys
    seccionesIdas.sorted().forEach { cambios += "Se eliminó la sección '$it'" }
    seccionesNuevas.sorted().forEach { cambios += "Se agregó la sección '$it'" }

    // Solo las que siguen existiendo: en las que se fueron o llegaron enteras, hablar de sus
    // ingredientes uno por uno sería repetir la misma noticia varias veces.
    (antes.secciones.keys intersect ahora.secciones.keys).sorted().forEach { seccion ->
        val deAntes = antes.secciones.getValue(seccion)
        val deAhora = ahora.secciones.getValue(seccion)

        (deAntes.keys - deAhora.keys).sorted().forEach {
            cambios += "Se eliminó '$it' de la sección '$seccion'"
        }
        (deAhora.keys - deAntes.keys).sorted().forEach {
            cambios += "Se agregó '$it' a la sección '$seccion'"
        }
        (deAntes.keys intersect deAhora.keys).sorted().forEach { ingrediente ->
            val viejo = deAntes.getValue(ingrediente)
            val nuevo = deAhora.getValue(ingrediente)
            if (!sonElMismoGramaje(viejo, nuevo)) {
                cambios += "'$ingrediente' pasó de ${formatearNumero(viejo)} a " +
                    "${formatearNumero(nuevo)} g"
            }
        }
    }

    (antes.pasosPorTitulo.keys - ahora.pasosPorTitulo.keys).sorted().forEach {
        cambios += "Se eliminaron los pasos de '$it'"
    }
    (ahora.pasosPorTitulo.keys - antes.pasosPorTitulo.keys).sorted().forEach {
        cambios += "Se agregaron pasos en '$it'"
    }
    (antes.pasosPorTitulo.keys intersect ahora.pasosPorTitulo.keys).sorted().forEach { titulo ->
        val diferencia = ahora.pasosPorTitulo.getValue(titulo) - antes.pasosPorTitulo.getValue(titulo)
        if (diferencia != 0) cambios += frasePasos(diferencia, "en '$titulo'", "en '$titulo'")
    }

    val generales = ahora.pasosGenerales - antes.pasosGenerales
    if (generales != 0) cambios += frasePasos(generales, "general", "generales")

    return cambios.map { CambioDetectado(it) }
}

/**
 * Si dos gramajes son el mismo número para esta app.
 *
 * Se comparan con una tolerancia y no con `==` porque las cantidades pasan por redondeos a
 * 2 decimales al reescalarse: 249,999999 y 250 son el mismo dato, y avisar de esa diferencia
 * sería enseñar a ignorar el aviso.
 */
private fun sonElMismoGramaje(uno: Double, otro: Double): Boolean =
    kotlin.math.abs(uno - otro) < 0.005

/**
 * "Se agregaron 2 pasos generales" / "Se eliminó un paso en 'Crema'".
 *
 * Recibe [enSingular] y [enPlural] por separado y no un texto solo porque "general" también
 * concuerda: con uno solo salía "Se agregó un paso generales", que es justo el tipo de frase
 * que hace dudar de si el número está bien.
 */
private fun frasePasos(diferencia: Int, enSingular: String, enPlural: String): String {
    val cuantos = kotlin.math.abs(diferencia)
    return if (cuantos == 1) {
        "Se ${if (diferencia > 0) "agregó" else "eliminó"} un paso $enSingular"
    } else {
        "Se ${if (diferencia > 0) "agregaron" else "eliminaron"} $cuantos pasos $enPlural"
    }
}
