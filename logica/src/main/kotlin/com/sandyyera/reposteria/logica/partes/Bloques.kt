package com.sandyyera.reposteria.logica.partes

/**
 * Cómo se agrupan los pasos de una receta bajo sus títulos, para dibujarlos (8.8).
 *
 * Los pasos se guardan **planos**: una fila por paso, cada una con su orden y con el id de la
 * sección a la que pertenece (o `null`, que es el General). Lo que se ve, en cambio, son
 * bloques con encabezado. Traducir de una cosa a la otra tiene cuatro reglas que no son obvias
 * y que se equivocan solas si se escriben dentro de un Composable, entre medio del dibujo.
 *
 * Se escribe acá y no en la pantalla por lo mismo que `camposDe` o `basesQueFaltanEn`: es lógica
 * que se puede probar sin celular, y de las cuatro reglas hay tres que solo se notan mirando un
 * caso concreto —dos generales pegados, un general anidado, el bloque único—, o sea justo lo que
 * una prueba puede fijar y un ojo no.
 */

/**
 * Un paso tal como lo ven las funciones puras: sin Room y sin saber de tablas.
 *
 * [esGeneralAnidado] distingue los pasos generales **de una receta traída** de los generales de
 * la receta que se está armando (8.8). Son dos cosas distintas —uno habla del bizcocho, el otro
 * de la torta entera— y aplanarlos los volvería indistinguibles. Solo tiene sentido con
 * [titulo] en `null`: un paso bajo el título "Crema" ya dice de qué parte habla.
 */
data class PasoParaMostrar(
    val id: Long,
    val texto: String,
    val titulo: TituloDePaso,
    val esGeneralAnidado: Boolean = false,
    val orden: Int = 0
)

/** Un paso con el número que le toca en la receta. */
data class PasoNumerado(val paso: PasoParaMostrar, val numero: Int)

/**
 * Un bloque de pasos con su encabezado, listo para dibujar.
 *
 * [encabezado] en `null` significa **no dibujar ninguno**, y no "dibujar vacío": pasa cuando el
 * General es el único bloque de la receta, donde el encabezado repetiría lo que ya dice el
 * título de arriba. Es el mismo criterio que esconde el nombre de la sección automática (8.2).
 */
data class BloqueDePasos(
    val titulo: TituloDePaso,
    val encabezado: String?,
    val esGeneralAnidado: Boolean,
    val pasos: List<PasoNumerado>
)

/**
 * Arma los bloques que se dibujan, a partir de los pasos guardados y las secciones de la receta.
 *
 * Las cuatro reglas, en el orden en que importan:
 *
 * 1. **Un bloque por tanda seguida del mismo título.** Los pasos vienen en su orden y la sección
 *    puede repetirse más adelante en la receta; cada tanda es su propio bloque, porque "Crema" al
 *    principio y "Crema" al final son dos momentos distintos de la preparación.
 * 2. **Dos generales pegados se juntan** (`seJuntanLosBloques`): son el mismo bloque partido en
 *    dos, y repetir el encabezado no significaría nada. **Un general anidado no se junta con uno
 *    normal**, aunque los dos tengan el título en `null` — esa es justamente la distinción que
 *    8.8 pide conservar.
 * 3. **La numeración es corrida y no por bloque.** Los pasos se cuentan 1, 2, 3… a lo largo de
 *    toda la receta, como en el ejemplo de 8.8. Reiniciar en cada bloque daría tres "paso 1" y
 *    haría imposible decir "me quedé en el 7".
 * 4. **El encabezado del General no se dibuja si es el único bloque.** Con una sola tanda de
 *    pasos generales, decir "General" arriba repite el título de la receta.
 *
 * Los títulos que apuntan a una sección que ya no existe se dibujan **como General** en vez de
 * desaparecer: los pasos son texto que alguien escribió y perderlos de vista sería peor que
 * mostrarlos sin su encabezado. No debería pasar —borrar una sección se lleva sus pasos— pero
 * una fila puede quedar suelta y esto no puede hacer desaparecer trabajo.
 */
fun bloquesDePasos(
    pasos: List<PasoParaMostrar>,
    seccionesDeLaReceta: List<SeccionParaTitulo>
): List<BloqueDePasos> {
    if (pasos.isEmpty()) return emptyList()

    val nombrePorId = seccionesDeLaReceta.associate { it.id to it.nombre }
    val enOrden = pasos.sortedBy { it.orden }

    // Los pasos se agrupan primero y se numeran después, en dos pasadas, porque la numeración
    // es de la receta entera y no del bloque: contando dentro del agrupamiento habría que
    // arrastrar un contador entre iteraciones, que es donde se cuela el error de reiniciarlo.
    val tandas = mutableListOf<MutableList<PasoParaMostrar>>()
    enOrden.forEach { paso ->
        val ultima = tandas.lastOrNull()
        val anterior = ultima?.last()
        val sigueLaMisma = anterior != null &&
            anterior.esGeneralAnidado == paso.esGeneralAnidado &&
            (anterior.titulo == paso.titulo || seJuntanLosBloques(anterior.titulo, paso.titulo))
        if (sigueLaMisma) ultima.add(paso) else tandas.add(mutableListOf(paso))
    }

    var numero = 0
    val bloques = tandas.map { tanda ->
        val primero = tanda.first()
        BloqueDePasos(
            titulo = primero.titulo,
            // Una sección borrada cae en el General en vez de dejar el bloque sin nombre.
            encabezado = primero.titulo?.let { nombrePorId[it] } ?: TITULO_GENERAL,
            esGeneralAnidado = primero.esGeneralAnidado,
            pasos = tanda.map { PasoNumerado(it, ++numero) }
        )
    }

    // Regla 4, aplicada al final porque depende de cuántos bloques quedaron.
    val esGeneralSolo = bloques.size == 1 &&
        bloques.single().titulo == null &&
        !bloques.single().esGeneralAnidado
    return if (esGeneralSolo) bloques.map { it.copy(encabezado = null) } else bloques
}
