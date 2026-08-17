package com.sandyyera.reposteria.logica.partes

import com.sandyyera.reposteria.logica.busqueda.sonElMismoTexto
import com.sandyyera.reposteria.logica.formato.redondearCantidad
import com.sandyyera.reposteria.logica.validaciones.LARGO_MAXIMO_NOMBRE

/**
 * Cuánto pasa a llevar un ingrediente de la copia cuando la receta original cambió (8.11.3).
 *
 * **Actualizar no pisa la cantidad: la adapta en proporción.** Es la diferencia entre las dos
 * razones por las que una cantidad puede cambiar:
 *
 * - *Acá cambió porque usas menos.* La salsa original rinde para un frasco; en el pastel usas
 *   la mitad. Eso es tuyo y no se toca nunca.
 * - *En la original cambió porque cambió la receta.* Bajaste la harina de 550 a 500 g: la
 *   proporción del bizcocho es otra ahora, y tu copia debería seguirla.
 *
 * Las dos conviven aplicando **el factor de ese ingrediente**: si la original pasó de 550 a
 * 500 y acá se usaban 275, quedan **250** — la mitad de la nueva, igual que antes era la
 * mitad de la vieja. La decisión de usar la mitad se conserva y el cambio de la receta llega
 * igual.
 *
 * Es por ingrediente y no un factor global porque en la original puede haber cambiado uno solo.
 *
 * Si [enLaOriginalAntes] es 0 no hay proporción que conservar —dividir daría infinito—, así
 * que se devuelve la cantidad nueva tal cual: es el mismo criterio que para un ingrediente
 * que la original no tenía.
 */
fun cantidadAdaptada(
    enLaCopia: Double,
    enLaOriginalAntes: Double,
    enLaOriginalAhora: Double
): Double {
    if (enLaOriginalAntes <= 0.0) return redondearCantidad(enLaOriginalAhora)
    // Dos decimales y no cinco: esto es una cantidad, no un precio por gramo (8.3.1). Sin eso,
    // adaptar una parte traída dejaba los 0,50007 g que Sandy reportó al reescalar.
    return redondearCantidad(enLaCopia * (enLaOriginalAhora / enLaOriginalAntes))
}

/**
 * El nombre con que entra una sección traída, esquivando los que ya existen (8.11.2).
 *
 * "Crema" → **"Crema 2"** si ya hay una Crema, "Crema 3" si también hay una Crema 2. Los
 * nombres de sección no se pueden repetir dentro de una receta (8.2) y esa regla no se toca;
 * **renombrar es mejor que rechazar la copia entera** por una coincidencia de nombre.
 *
 * Compara con `sonElMismoTexto`, o sea ignorando mayúsculas y tildes, porque es la misma
 * comparación que hace la validación que rechazaría el nombre: si acá se usara `==`, se
 * propondría "Crema" existiendo "crema" y la copia fallaría igual.
 *
 * **El resultado nunca pasa de `LARGO_MAXIMO_NOMBRE`**, y eso tampoco es prolijidad: el
 * mismo `errorEnNombreSeccion` que exige nombres únicos exige también que quepan en 60
 * caracteres. Con un nombre ya al límite, pegarle " 2" lo dejaba en 62 y la copia se
 * rechazaba por el nombre que esta función acababa de proponer — un callejón sin salida, con
 * un mensaje que además culpaba a la persona de algo que no escribió. Se recorta la base, no
 * el sufijo: el número es lo que hace único al nombre.
 */
fun nombreSinChocar(deseado: String, yaUsados: List<String>): String {
    val limpio = deseado.trim()
    if (yaUsados.none { sonElMismoTexto(it, limpio) }) return limpio.take(LARGO_MAXIMO_NOMBRE)

    var numero = 2
    while (true) {
        val sufijo = " $numero"
        val base = limpio.take(LARGO_MAXIMO_NOMBRE - sufijo.length).trimEnd()
        val candidato = base + sufijo
        if (yaUsados.none { sonElMismoTexto(it, candidato) }) return candidato
        numero++
    }
}

/**
 * Si una receta se puede traer dentro de otra: el tope de un solo nivel (8.11.6).
 *
 * **Una receta que ya usa otra receta no se puede usar dentro de una tercera.** Si Torta usa
 * Bizcocho, Torta no aparece en la lista al armar una receta nueva.
 *
 * Es una limitación puesta a propósito, no una que falte resolver: sin ella, actualizar el
 * bizcocho tendría que propagarse en cadena por todo lo que lo usa indirectamente, los avisos
 * se multiplicarían y la copia dejaría de ser algo que uno pueda seguir con la cabeza.
 *
 * Recibe [tieneSeccionesTraidas] y no consulta nada: que una receta "use otra" es
 * exactamente que alguna de sus secciones tenga `recetaOrigenId`, y eso ya se puede saber sin
 * una columna aparte (5.5.1).
 *
 * También se excluye a sí misma: una receta no se puede traer dentro de sí misma.
 */
fun sePuedeUsarComoParte(
    recetaId: Long,
    laQueSeEstaArmando: Long,
    tieneSeccionesTraidas: Boolean
): Boolean = recetaId != laQueSeEstaArmando && !tieneSeccionesTraidas

/** Lo que se muestra al lado de una receta que quedó fuera de la lista por el tope de 8.11.6. */
const val MOTIVO_UN_SOLO_NIVEL =
    "Esta receta ya está hecha de partes, así que no se puede usar dentro de otra."

/**
 * Lo que se muestra al lado de una receta que no se puede traer por tener el título repetido.
 *
 * Esas recetas **no se pueden ni abrir** (`marcarRepetidos` las bloquea en la lista, porque son
 * datos anteriores a prohibir los repetidos), así que copiarlas dentro de otra sería peor:
 * quedaría una parte cuya original no se puede revisar y cuyos avisos no llevan a ninguna parte.
 */
const val MOTIVO_TITULO_REPETIDO =
    "Esta receta tiene el título repetido. Renómbrala en la lista y después podrás usarla."

/**
 * El resultado de emparejar dos listas de filas por el ingrediente que usan.
 *
 * Los tres grupos son tres noticias distintas y hay que poder distinguirlas: [juntos] son los
 * que hay que adaptar, [soloEnLaCopia] los candidatos a que la original los haya eliminado, y
 * [soloEnLaOriginal] los que llegaron después y hay que traer.
 */
data class Emparejamiento<C, O>(
    val juntos: List<Pair<C, O>>,
    val soloEnLaCopia: List<C>,
    val soloEnLaOriginal: List<O>
)

/**
 * Empareja las filas de una sección copiada con las de la sección original, por el **ingrediente
 * del catálogo** que usa cada una.
 *
 * Es por el ingrediente y no por el id de la fila porque **la copia tiene ids propios**: una fila
 * de la copia no tiene forma de decir de qué fila de la original salió (5.5.1 lo deja así a
 * propósito, para no repetir el vínculo en cada ingrediente). Tampoco sirve el orden: agregar o
 * quitar una fila a mano corre todas las de abajo, y ahí las cantidades se adaptarían contra el
 * ingrediente equivocado — cambiando datos reales sin que nada se vea raro.
 *
 * Por el nombre tampoco: renombrar un ingrediente del catálogo no toca ninguna receta, y
 * emparejar por nombre haría que un renombre pareciera un ingrediente eliminado más otro
 * agregado. Es el mismo agujero que ya obligó a rehacer la firma.
 *
 * **Si un ingrediente aparece dos veces en la misma sección** —cosa que `agregarIngrediente` ya
 * no deja hacer, pero que existe en datos guardados de antes— las repetidas se emparejan **entre
 * ellas en el orden en que vienen**. No hay forma de saber cuál era cuál, y este es el único
 * criterio que no pierde ninguna: cualquier otro dejaría una cantidad sin pareja y la borraría o
 * la duplicaría.
 */
fun <C, O> emparejarPorIngrediente(
    copia: List<C>,
    original: List<O>,
    ingredienteDeLaCopia: (C) -> Long,
    ingredienteDeLaOriginal: (O) -> Long
): Emparejamiento<C, O> {
    // Se trabaja con **posiciones** de la lista original y no con las filas mismas: dos filas
    // repetidas de la misma sección pueden ser iguales campo por campo, y un `Set` de `data
    // class` las tomaría por una sola — perdiendo una cantidad justo en el caso raro que este
    // emparejamiento existe para no perder.
    val posicionesPorIngrediente = original.indices.groupBy { ingredienteDeLaOriginal(original[it]) }
    val juntos = mutableListOf<Pair<C, O>>()
    val soloAcá = mutableListOf<C>()
    val emparejadas = mutableSetOf<Int>()
    // Cuántas del mismo ingrediente ya se usaron de allá, para que la segunda "Harina" de acá
    // se empareje con la segunda de allá y no otra vez con la primera.
    val yaUsadas = mutableMapOf<Long, Int>()

    copia.forEach { fila ->
        val ingrediente = ingredienteDeLaCopia(fila)
        val candidatas = posicionesPorIngrediente[ingrediente].orEmpty()
        val cual = yaUsadas.getOrDefault(ingrediente, 0)
        val posicion = candidatas.getOrNull(cual)
        if (posicion == null) {
            soloAcá += fila
        } else {
            juntos += fila to original[posicion]
            emparejadas += posicion
            yaUsadas[ingrediente] = cual + 1
        }
    }

    val soloAllá = original.filterIndexed { posicion, _ -> posicion !in emparejadas }

    return Emparejamiento(juntos, soloAcá, soloAllá)
}
