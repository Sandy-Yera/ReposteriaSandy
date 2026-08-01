package com.sandyyera.reposteria.logica.partes

import com.sandyyera.reposteria.logica.formato.formatearNumero

/**
 * Una línea de ingrediente dentro de la firma.
 *
 * **La clave es [lineaId], no el nombre**, y esa decisión se pagó cara antes de tomarla. La
 * primera versión guardaba las cantidades en un mapa `nombre → gramos`, y eso tenía dos
 * agujeros que no se ven hasta que hay datos reales:
 *
 * 1. **Un ingrediente del catálogo se puede renombrar.** Cambiar "Azúcar" por "Azúcar flor"
 *    no toca ninguna receta, pero con el nombre de clave toda copia habría avisado
 *    *"Se eliminó 'Azúcar'"* y *"Se agregó 'Azúcar flor'"* — una alarma sobre algo que no
 *    pasó, y peor, la adaptación en proporción habría tratado el ingrediente como nuevo y
 *    pisado la cantidad ajustada a mano.
 * 2. **El mismo ingrediente puede estar dos veces en una sección**: no hay índice único que
 *    lo impida ni comprobación en `agregarIngrediente`. Con el nombre de clave, las dos
 *    filas se aplastaban en una y una cantidad desaparecía **en silencio**.
 *
 * El id de la fila no se renombra y no se repite, así que cierra los dos.
 *
 * [nombre] queda solo para la frase que se muestra. Se lee del estado **actual** cuando la
 * línea sigue existiendo, así un renombre se ve con el nombre nuevo sin generar aviso.
 */
data class LineaDeFirma(
    val lineaId: Long,
    val nombre: String,
    val gramos: Double
)

/** Una sección de la receta original, con lo que llevaba al momento de copiarla. */
data class SeccionDeFirma(
    /** El id de la sección **en la receta original**. Es la clave, por lo mismo de arriba. */
    val seccionId: Long,
    val nombre: String,
    val lineas: List<LineaDeFirma>
)

/** Cuántos pasos había bajo un título, que es una sección de la receta original (8.8). */
data class TituloDeFirma(
    val seccionId: Long,
    val nombre: String,
    val cuantosPasos: Int
)

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
 * cambió cada línea. Contando ingredientes no se puede.
 *
 * **Lo que no detecta, y está aceptado:** reescribir el texto de un paso sin cambiar cuántos
 * hay, y renombrar una sección o un ingrediente — eso último ahora es a propósito y no un
 * descuido: renombrar no cambia la receta, así que avisar sería una falsa alarma.
 */
data class FirmaDeReceta(
    val secciones: List<SeccionDeFirma>,
    val titulos: List<TituloDeFirma>,
    val pasosGenerales: Int
) {
    val cuantasSecciones: Int get() = secciones.size

    val cuantosIngredientes: Int get() = secciones.sumOf { it.lineas.size }

    val cuantosTitulos: Int get() = titulos.size

    /** La línea con ese id, mirando en todas las secciones. La usa la adaptación (8.11.3). */
    fun linea(lineaId: Long): LineaDeFirma? =
        secciones.firstNotNullOfOrNull { seccion ->
            seccion.lineas.firstOrNull { it.lineaId == lineaId }
        }
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

    val seccionesAntes = antes.secciones.associateBy { it.seccionId }
    val seccionesAhora = ahora.secciones.associateBy { it.seccionId }

    // Los nombres salen del estado **actual** cuando la sección sigue existiendo: así una
    // sección renombrada se nombra como se llama hoy, sin que el renombre genere aviso.
    (seccionesAntes.keys - seccionesAhora.keys).forEach {
        cambios += "Se eliminó la sección '${seccionesAntes.getValue(it).nombre}'"
    }
    (seccionesAhora.keys - seccionesAntes.keys).forEach {
        cambios += "Se agregó la sección '${seccionesAhora.getValue(it).nombre}'"
    }

    // Solo las que siguen existiendo: en las que se fueron o llegaron enteras, hablar de sus
    // ingredientes uno por uno sería repetir la misma noticia varias veces.
    seccionesAntes.keys.filter { it in seccionesAhora }.forEach { id ->
        val deAntes = seccionesAntes.getValue(id).lineas.associateBy { it.lineaId }
        val laDeAhora = seccionesAhora.getValue(id)
        val deAhora = laDeAhora.lineas.associateBy { it.lineaId }
        val donde = "la sección '${laDeAhora.nombre}'"

        (deAntes.keys - deAhora.keys).forEach {
            cambios += "Se eliminó '${deAntes.getValue(it).nombre}' de $donde"
        }
        (deAhora.keys - deAntes.keys).forEach {
            cambios += "Se agregó '${deAhora.getValue(it).nombre}' a $donde"
        }
        deAntes.keys.filter { it in deAhora }.forEach { lineaId ->
            val viejo = deAntes.getValue(lineaId).gramos
            val nuevo = deAhora.getValue(lineaId)
            if (!sonElMismoGramaje(viejo, nuevo.gramos)) {
                cambios += "'${nuevo.nombre}' pasó de ${formatearNumero(viejo)} a " +
                    "${formatearNumero(nuevo.gramos)} g"
            }
        }
    }

    val titulosAntes = antes.titulos.associateBy { it.seccionId }
    val titulosAhora = ahora.titulos.associateBy { it.seccionId }

    (titulosAntes.keys - titulosAhora.keys).forEach {
        cambios += "Se eliminaron los pasos de '${titulosAntes.getValue(it).nombre}'"
    }
    (titulosAhora.keys - titulosAntes.keys).forEach {
        cambios += "Se agregaron pasos en '${titulosAhora.getValue(it).nombre}'"
    }
    titulosAntes.keys.filter { it in titulosAhora }.forEach { id ->
        val ahoraTitulo = titulosAhora.getValue(id)
        val diferencia = ahoraTitulo.cuantosPasos - titulosAntes.getValue(id).cuantosPasos
        val donde = "en '${ahoraTitulo.nombre}'"
        if (diferencia != 0) cambios += frasePasos(diferencia, donde, donde)
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
