package com.sandyyera.reposteria.logica.validaciones

import com.sandyyera.reposteria.logica.formato.formatearNumero

/**
 * Las reglas de lo que se escribe en el módulo Empleados (sección 10).
 *
 * El nombre de un empleado usa `errorEnNombreEscrito`, el mismo de ingredientes y moldes: es la
 * misma pregunta —"¿esto se puede llamar así?"— y una segunda versión se separaría de la primera.
 * Lo que sí necesita reglas propias es **cuánto se lleva el empleado**, porque su tope no es un
 * número fijo sino la ganancia de esa receta.
 */

/**
 * Qué está mal en la ganancia escrita para un empleado, o `null` si sirve (10.1).
 *
 * **El tope es la ganancia total de la receta**, y no un porcentaje ni una cifra fija: es la
 * traducción de "el empleado puede llevarse toda la ganancia, pero yo nunca bajo del costo".
 * `calcularSueldo` lo exige con un `require`, y esta función existe para decirlo **antes** de
 * intentar guardar — una excepción es correcta en el motor de cálculo y no sirve como aviso bajo
 * un campo de texto.
 *
 * El **0 se acepta**: "esta receta la vendo yo" es una respuesta válida, y obligar a borrar la
 * asignación para decirlo perdería de paso que esa receta está asignada.
 *
 * Con [gananciaTotal] negativa no hay nada que repartir, y se dice así en vez de aceptar un 0
 * que se leería como un sueldo asignado: la receta se está vendiendo bajo su costo y lo que hay
 * que arreglar está en otra pantalla.
 */
fun errorEnGananciaDelEmpleado(texto: String, gananciaTotal: Double): String? {
    if (gananciaTotal < 0) {
        return "Esta receta se vende bajo su costo: no hay ganancia que repartir"
    }
    if (texto.isBlank()) return "Escribe cuánto se lleva"
    val numero = textoANumero(texto) ?: return "Escribe un número válido"
    return when {
        numero.isNaN() || numero.isInfinite() -> "Escribe un número válido"
        numero < 0 -> "No puede ser negativo"
        numero > gananciaTotal ->
            "Esta receta gana $${formatearNumero(gananciaTotal)}. No puedes repartir más que eso."
        else -> null
    }
}

/**
 * Si un empleado se puede borrar o renombrar.
 *
 * El genérico no: es el modelo estándar y el glosario lo define como "siempre presente". La
 * pantalla tampoco ofrece esas acciones, y el `DELETE` del DAO lleva su propia condición — son
 * tres redes para lo mismo a propósito, porque perderlo dejaría la sección arrancando vacía y
 * esa garantía convertida en mentira.
 */
fun motivoParaNoTocarAlEmpleado(esGenerico: Boolean): String? =
    if (esGenerico) "El empleado estándar no se puede borrar ni renombrar" else null
