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
 *
 * **[ingredienteId] es otra cosa que [lineaId] y las dos hacen falta.** [lineaId] identifica la
 * *fila de la original* y es lo que permite comparar dos fotos de ella. [ingredienteId] es el
 * ingrediente del catálogo, y es lo único que permite emparejar esa fila con la de **la copia**:
 * la copia tiene ids de fila propios, así que sin esto no hay forma de saber a qué cantidad de
 * acá le corresponde qué cantidad de allá — que es exactamente lo que necesita la adaptación en
 * proporción (8.11.3). Y es lo que permite saber, cuando la original borra un ingrediente, cuál
 * de las filas de la copia era esa; sin el dato, la única alternativa sería borrar de la copia
 * todo lo que la original no tenga, y eso se llevaría por delante lo que se agregó a mano.
 */
data class LineaDeFirma(
    val lineaId: Long,
    val ingredienteId: Long,
    val nombre: String,
    /**
     * Cuánto lleva, **en su unidad**: gramos, o unidades si [esObjeto] (14.1.1).
     *
     * Se llamaba `gramos` y el nombre mentía en un caso: una bolsa se cuenta por unidad y sus
     * gramos son 0 a propósito, así que la firma guardaba 0 para todas y **un cambio de 2 a 3
     * bolsas no se notaba**. Lo pidió Sandy — *"los materiales hechos por unidad también
     * deberían ser avisados"*— y tenía razón: esos materiales cuestan, así que cambiarlos
     * cambia la receta.
     */
    val cantidad: Double,
    /** Si se cuenta por unidad. Solo cambia **cómo se lee** la frase: "3 unidades", no "3 g". */
    val esObjeto: Boolean = false
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
 * **Las cantidades pueden salir resumidas en una sola frase.** Cambiar de molde multiplica
 * todas a la vez por el mismo factor, y ahí una frase por ingrediente es cierta e ilegible: en
 * una receta de doce, doce renglones para una sola noticia. Cuándo se resume lo decide
 * `frasesDeCantidades`, y en cuanto el reescalado deja de ser la única explicación posible se
 * vuelve al detalle.
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
    //
    // Las cantidades se juntan aparte y se deciden al final, en vez de escribirse acá dentro:
    // hay un caso —reescalar la original— en que **todas** cambian a la vez y por el mismo
    // factor, y ahí una frase por ingrediente es cierta e ilegible. Verlo exige mirarlas todas
    // juntas, y dentro del bucle nunca están todas.
    val cantidades = mutableListOf<CambioDeCantidad>()

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
            val nuevo = deAhora.getValue(lineaId)
            cantidades += CambioDeCantidad(
                nombre = nuevo.nombre,
                antes = deAntes.getValue(lineaId).cantidad,
                ahora = nuevo.cantidad,
                esObjeto = nuevo.esObjeto
            )
        }
    }

    cambios += frasesDeCantidades(cantidades)

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

/** Una cantidad que puede haber cambiado, mientras se decide cómo contarla. */
private data class CambioDeCantidad(
    val nombre: String,
    val antes: Double,
    val ahora: Double,
    val esObjeto: Boolean
) {
    /** "3 unidades" o "500 g". Sin esto la frase diría "3 g" de algo que se cuenta por unidad. */
    fun comoSeLee(cuanto: Double): String {
        val numero = formatearNumero(cuanto)
        return if (esObjeto) {
            "$numero ${if (cuanto == 1.0) "unidad" else "unidades"}"
        } else {
            "$numero g"
        }
    }
}

/**
 * Desde cuántos ingredientes conviene resumir un reescalado en una sola frase.
 *
 * Con **dos**, las frases sueltas ya son la información completa y ocupan dos renglones: decir
 * "todas se multiplicaron por 1,5" ahorraría un renglón y escondería los números. Con dos
 * ingredientes, además, que los dos cambien en la misma proporción todavía puede ser
 * casualidad.
 *
 * Desde **tres** se da vuelta: la casualidad deja de ser creíble —el mismo factor exacto en
 * tres cantidades es un reescalado, no tres decisiones— y la lista una por una es justo lo que
 * esconde la noticia. Una receta de doce ingredientes daría doce frases ciertas e ilegibles.
 */
const val MINIMO_PARA_RESUMIR_REESCALADO = 3

/**
 * Cómo se cuentan las cantidades que cambiaron: una por una, o resumidas en un reescalado.
 *
 * Se resume **solo cuando el reescalado es la única explicación posible**: todas las líneas que
 * siguen existiendo cambiaron, todas por el mismo factor, y son al menos
 * [MINIMO_PARA_RESUMIR_REESCALADO]. Basta con que una haya quedado igual para que ya no sea un
 * reescalado sino un cambio de ingredientes que casualmente comparten proporción, y ahí lo que
 * hay que ver es cuáles.
 *
 * Las líneas que antes estaban en 0 **descartan el resumen**: de un 0 no sale ninguna
 * proporción —es el mismo caso que `cantidadAdaptada` resuelve devolviendo la cantidad nueva—,
 * así que no se puede afirmar que sigan el factor de las demás. Es un caso raro y ahí se
 * prefiere el detalle, que nunca miente.
 *
 * **Lo que se cuenta por unidad también lo descarta**, y por una razón distinta: reescalar no lo
 * toca (14.1.1), así que si una bolsa cambió de 2 a 3 lo que pasó no fue un reescalado. Decir
 * "todas las cantidades se multiplicaron por 1,5" con un objeto en el medio sería contar mal la
 * noticia, que es justo lo que este resumen existe para evitar.
 */
private fun frasesDeCantidades(cambios: List<CambioDeCantidad>): List<String> {
    val detalle = cambios.filter { !sonElMismoGramaje(it.antes, it.ahora) }.map {
        // La unidad va **solo en el segundo número**: "de 550 a 500 g" se lee como se habla,
        // y repetirla en los dos suena a formulario. El plural sale del segundo, que es el que
        // la lleva: "de 3 a 1 unidad".
        "'${it.nombre}' pasó de ${formatearNumero(it.antes)} a ${it.comoSeLee(it.ahora)}"
    }
    if (cambios.size < MINIMO_PARA_RESUMIR_REESCALADO) return detalle
    if (detalle.size != cambios.size) return detalle
    if (cambios.any { it.antes <= 0.0 }) return detalle
    // Un reescalado **no toca lo que se cuenta por unidad** (14.1.1): reescalar por molde
    // cambia cuánta masa hay, no cuántas bolsas se usan. Si entre las que cambiaron hay un
    // objeto, lo que pasó no fue un reescalado, y resumirlo como tal sería contarlo mal.
    if (cambios.any { it.esObjeto }) return detalle

    val factor = cambios.first().let { it.ahora / it.antes }
    if (!cambios.all { sigueElFactor(it.antes, it.ahora, factor) }) return detalle

    return listOf(
        "La receta se reescaló: todas las cantidades quedaron multiplicadas por " +
            formatearNumero(factor)
    )
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
 * Si una cantidad cambió siguiendo el mismo [factor] que las demás.
 *
 * **La tolerancia es relativa y no la absoluta de `sonElMismoGramaje`**, y no es un capricho: el
 * factor se deduce de una línea cuyos dos gramajes ya vienen redondeados a 2 decimales, así que
 * trae un error propio de hasta 0,005 ÷ esa cantidad — y ese error se **multiplica** al
 * aplicarlo a una cantidad grande. Con 100 g de referencia y una línea de 5 kg, el desvío
 * legítimo pasa de medio gramo, muy por encima de los 0,005 de allá.
 *
 * Con tolerancia absoluta el resumen se caía justo en las recetas grandes, que son las que más
 * lo necesitan: doce frases donde había una sola noticia.
 *
 * El 0,1 % es holgado a propósito. Lo que se está decidiendo no es un número que se muestre
 * sino **cómo contar la noticia**, y tres cantidades que se movieron dentro del 0,1 % del mismo
 * factor son un reescalado; leerlas como tres decisiones separadas sería lo falso.
 */
private fun sigueElFactor(antes: Double, ahora: Double, factor: Double): Boolean {
    val esperado = antes * factor
    val margen = kotlin.math.max(0.01, kotlin.math.abs(esperado) * 0.001)
    return kotlin.math.abs(esperado - ahora) <= margen
}

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
