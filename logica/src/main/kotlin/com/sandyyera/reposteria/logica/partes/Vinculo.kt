package com.sandyyera.reposteria.logica.partes

/**
 * En qué situación está una sección respecto de la receta de la que se copió (8.11.3, 8.11.4).
 *
 * Son **tres estados y no dos**, y esa es toda la razón de que esto exista como función en vez
 * de un `if` escrito en cada pantalla. `recetaOrigenId` es `SET_NULL`, así que cuando la receta
 * original se borra el vínculo lo corta SQLite sola, **antes de que nadie elija nada**. Ahí una
 * sección con el id en `null` se ve exactamente igual que una que se desvinculó a mano — y no
 * son lo mismo: a la primera hay que preguntarle qué hacer (8.11.4) y a la segunda no hay que
 * volver a molestarla nunca (8.11.3).
 *
 * Lo que las distingue es la firma, que nadie borra al eliminar la receta:
 *
 * | `recetaOrigenId` | `firmaDelOrigen` | Estado |
 * |---|---|---|
 * | tiene id | tiene firma | [VIVO] |
 * | `null`   | tiene firma | [ORIGINAL_BORRADA] |
 * | `null`   | `null`      | [SIN_VINCULO] |
 *
 * De ahí sale la regla que hay que respetar al desvincular: **se limpian las dos columnas, no
 * solo el id**. Limpiando solo el id, la sección quedaría diciendo que su original desapareció
 * y volvería a preguntar para siempre.
 */
enum class EstadoDelVinculo {
    /**
     * No viene de ninguna receta, o vino y se desvinculó a mano.
     *
     * **Los dos casos son el mismo estado a propósito**: lo que significa este valor es "no
     * avisa nunca más", y una sección escrita a mano y una desvinculada se comportan igual en
     * todo lo que sigue. Distinguirlos obligaría a una columna más para no cambiar nada.
     */
    SIN_VINCULO,

    /** Viene de una receta que todavía existe: avisa cuando aquella cambie (8.11.3). */
    VIVO,

    /** Venía de una receta que ya no está. Hay que preguntar qué hacer con ella (8.11.4). */
    ORIGINAL_BORRADA
}

/**
 * El estado del vínculo de una sección, a partir de sus dos columnas.
 *
 * Recibe [hayFirma] y no el texto de la firma porque acá no importa si se puede leer: una firma
 * ilegible (`firmaDesdeTexto` devuelve `null`) sigue siendo prueba de que esta sección se copió
 * de algún lado. Lo que se pierde ahí es el "¿Qué cambió?", no el vínculo entero.
 */
fun estadoDelVinculo(recetaOrigenId: Long?, hayFirma: Boolean): EstadoDelVinculo = when {
    recetaOrigenId != null && hayFirma -> EstadoDelVinculo.VIVO
    hayFirma -> EstadoDelVinculo.ORIGINAL_BORRADA
    else -> EstadoDelVinculo.SIN_VINCULO
}

/**
 * Lo que hay que decir antes de confirmar "Desvincular" (8.11.3).
 *
 * **No se deshace**, y por eso se avisa antes y no después: volver a enlazar sería volver a
 * copiar, y eso ya existe como camino aparte —traer la receta de nuevo—, pero traería otra vez
 * las cantidades de la original y pisaría las ajustadas a mano.
 */
const val AVISO_AL_DESVINCULAR =
    "La sección se queda con lo que tiene. Deja de avisarte cuando cambie la receta original, " +
        "y no se puede volver a enlazar."

/** Lo que se explica cuando la receta original ya no existe (8.11.4). */
const val AVISO_ORIGINAL_BORRADA =
    "La receta de la que salió esta sección fue eliminada. Lo que está escrito acá sigue " +
        "estando: puedes dejarlo como quedó, o borrar la sección con sus pasos."

/**
 * Todo lo que una sección copiada guarda sobre la original: **de qué sección salió** y la foto
 * de la receta entera al momento de copiarla.
 *
 * Las dos mitades hacen falta y responden preguntas distintas:
 *
 * - [firma] es la foto de la original **completa**, y de ahí sale el "¿Qué cambió?" (8.11.5).
 *   Tiene que ser de la receta entera y no solo de esta sección, porque *"se agregó una
 *   sección"* y *"se eliminó una sección"* son dos de los avisos que 8.11.5 pide, y desde una
 *   sección sola no se pueden ver.
 * - [seccionDeOrigen] dice **cuál** de esas secciones es esta copia. Sin eso, con una receta de
 *   tres partes traída entera, nada dice qué copia corresponde a qué original: los ids de la
 *   copia son propios, y el nombre no sirve porque puede haberse renombrado al chocar (8.11.2)
 *   o a mano después. Emparejarlas por el orden en que quedaron aguanta hasta que alguien borre
 *   o mueva una, y ahí las cantidades se adaptarían contra la sección equivocada — un error que
 *   se ve mucho después y ya con las cantidades pisadas.
 *
 * Van juntas en una columna (`firmaDelOrigen`) y no en dos, porque agregar la segunda sería una
 * migración para guardar un número que solo tiene sentido junto al otro.
 */
data class VinculoConLaOriginal(
    val seccionDeOrigen: Long,
    val firma: FirmaDeReceta
)

/**
 * El vínculo tal como se guarda en la columna `firmaDelOrigen`.
 *
 * Es una línea `P|<id>` —de qué sección de la original salió esta copia— y debajo la firma en su
 * propio formato, con su propia versión. **Las dos capas se versionan por separado a propósito**:
 * la firma ya cambió una vez (v1 → v2) y va a volver a cambiar; el envoltorio, que lleva un solo
 * número, no tiene por qué arrastrarse con ella.
 */
fun textoDelVinculo(vinculo: VinculoConLaOriginal): String =
    "P|${vinculo.seccionDeOrigen}\n" + textoDeFirma(vinculo.firma)

/**
 * Lee el vínculo guardado, o `null` si el texto no se entiende.
 *
 * **Devuelve `null` en vez de lanzar**, por lo mismo que `firmaDesdeTexto`: un vínculo ilegible
 * no puede impedir abrir la receta. Lo que se pierde es el aviso, y la sección se comporta como
 * una propia. Un envoltorio que no empiece con `P|<número>` es un formato que esta versión no
 * conoce, y ahí también se descarta entero: leer a medias sería emparejar cantidades a ciegas.
 */
fun vinculoDesdeTexto(texto: String?): VinculoConLaOriginal? {
    val lineas = texto?.lines() ?: return null
    val cabecera = lineas.firstOrNull()?.split("|") ?: return null
    if (cabecera.size != 2 || cabecera[0] != "P") return null
    val seccion = cabecera[1].toLongOrNull() ?: return null
    val firma = firmaDesdeTexto(lineas.drop(1).joinToString("\n")) ?: return null
    return VinculoConLaOriginal(seccion, firma)
}
