package com.sandyyera.reposteria.logica.partes

import com.sandyyera.reposteria.logica.busqueda.sonElMismoTexto
import com.sandyyera.reposteria.logica.formato.redondearADosDecimales

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
    if (enLaOriginalAntes <= 0.0) return redondearADosDecimales(enLaOriginalAhora)
    return redondearADosDecimales(enLaCopia * (enLaOriginalAhora / enLaOriginalAntes))
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
 */
fun nombreSinChocar(deseado: String, yaUsados: List<String>): String {
    val limpio = deseado.trim()
    if (yaUsados.none { sonElMismoTexto(it, limpio) }) return limpio

    var numero = 2
    while (yaUsados.any { sonElMismoTexto(it, "$limpio $numero") }) numero++
    return "$limpio $numero"
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
