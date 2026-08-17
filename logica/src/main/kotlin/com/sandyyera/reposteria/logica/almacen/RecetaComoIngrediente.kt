package com.sandyyera.reposteria.logica.almacen

import com.sandyyera.reposteria.logica.formato.redondearParaGuardar

/**
 * Convertir una receta en un ingrediente del catálogo (14.13).
 *
 * Lo pidió Sandy con los tres casos que lo motivan: *"a veces hago siropes, almíbares o azúcares
 * invertidos"*. Son recetas que no se venden — se **usan dentro de otras recetas**, y hasta ahora
 * la única forma de costearlas era estimar su valor por gramo a ojo.
 *
 * **Y la app ya sabe el número exacto.** Una receta tiene su costo total y su peso final, así que
 * el valor por gramo sale de dividir uno por el otro: no es una estimación sino la cuenta que la
 * receta ya tenía hecha. Esa es la razón de que esto valga la pena existiendo "traer otra receta"
 * (8.11): traerla copia sus ingredientes dentro, y esto la convierte en **una cosa** que se pesa y
 * se guarda en un frasco.
 */

/**
 * El valor por gramo de una receta, o `null` si no se puede saber todavía.
 *
 * Devuelve `null` en los dos casos en que no hay cuenta posible, y las dos son situaciones reales
 * y no errores:
 *
 * - **Sin peso final anotado**: la receta rinde algo que nadie pesó. Sin gramos no hay por cuánto
 *   dividir, y suponer uno inventaría el costo de todo lo que la use.
 * - **Con peso 0**: lo mismo, con una división por cero de regalo.
 *
 * Un costo de 0 **sí devuelve 0** y no `null`: una receta hecha solo de cosas que no se costean
 * vale 0 el gramo, y eso es un dato — la misma distinción que ya costó un bug en la lista de
 * recetas (un ingrediente puede valer 0 a propósito).
 */
fun valorPorGramoDeLaReceta(costoTotal: Double, pesoFinalG: Double?): Double? {
    if (pesoFinalG == null || pesoFinalG <= 0.0) return null
    return redondearParaGuardar(costoTotal / pesoFinalG)
}

/**
 * Por qué una receta todavía no se puede guardar como ingrediente, o `null` si sí se puede.
 *
 * Se contesta **antes** de ofrecer el botón, no después de tocarlo: el motivo dice qué falta y
 * dónde arreglarlo, que es lo único útil cuando la respuesta es que no. Un botón que se puede
 * tocar y siempre falla enseña a no leer lo que contesta.
 */
fun porQueNoSePuedeGuardarComoIngrediente(
    pesoFinalG: Double?,
    tieneIngredientes: Boolean
): String? = when {
    !tieneIngredientes ->
        "Esta receta todavía no tiene ingredientes, así que no se sabe cuánto cuesta."
    pesoFinalG == null || pesoFinalG <= 0.0 ->
        "Falta el peso final de la receta. Anótalo en Rendimiento y vuelve: de ahí sale el " +
            "valor por gramo."
    else -> null
}

/**
 * Lo que se lleva al almacén al convertir una receta (14.13).
 *
 * Las tres cosas viajan juntas porque **es el cuadro de agregar ya contestado**: el nombre, el
 * valor por gramo exacto y cuántos gramos salieron. Sandy lo pidió así —*"sería mejor que me
 * redirigiera al almacenaje, con los campos rellenos"*— y el motivo es que la conversión no es
 * una pantalla nueva sino un atajo a una que ya existe.
 *
 * [cantidad] es el peso final y no un campo vacío, y eso también salió del pedido: *"además justo
 * da la cantidad"*. Recién hecha, lo que hay en el frasco es exactamente lo que rindió.
 *
 * **Se manda el costo total y no el valor por gramo**, y eso encaja exacto con lo que el cuadro
 * pregunta (14.5.2): ahí el campo es *"cuánto costó todo"*, y la receta **costó** eso. El cuadro
 * hace su propia división y le sale el mismo número que [valorPorGramoDeLaReceta], sin un redondeo
 * de ida y otro de vuelta. Mandar el valor por gramo habría obligado a multiplicarlo para llenar
 * el campo, y a que el cuadro lo dividiera de nuevo — dos operaciones para volver al punto de
 * partida, cada una con su pérdida.
 */
data class RecetaParaElAlmacen(
    val nombre: String,
    val costoTotal: Double,
    val cantidad: Double
)
