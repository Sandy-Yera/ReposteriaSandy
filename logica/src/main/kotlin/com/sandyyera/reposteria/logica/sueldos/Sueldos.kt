package com.sandyyera.reposteria.logica.sueldos

import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ingresoBruto

/** Cómo se reparte el ingreso de una receta entre el dueño y quien la vende. */
data class Sueldo(
    val ingresoBruto: Double,
    /** Costo total más la parte de la ganancia que no se lleva el empleado. */
    val yoMeLlevo: Double,
    val gananciaEmpleado: Double
)

/**
 * Reparte lo que entra por vender una receta completa.
 *
 * El dueño siempre recupera el costo total; lo que se negocia es cuánto de la ganancia
 * se lleva quien vende. Con ingreso 10.000 y costo 3.000 hay 7.000 de ganancia: si al
 * empleado le tocan 3.000, el dueño se lleva 7.000 (los 3.000 de costo más 4.000).
 *
 * El tope de [gananciaEmpleado] es la ganancia total, que es otra forma de decir que el
 * dueño nunca baja del costo: `yoMeLlevo = costo + (ganancia - gananciaEmpleado)` queda
 * por encima del costo exactamente cuando `gananciaEmpleado <= gananciaTotal`.
 *
 * Lanza excepción si la receta no cubre su costo, o si se pide más de la ganancia total.
 */
fun calcularSueldo(d: DatosCalculoReceta, gananciaEmpleado: Double): Sueldo {
    val ingreso = ingresoBruto(d)
    val gananciaTotal = ingreso - d.costoTotal

    // Este chequeo va primero y aparte a propósito: si gananciaTotal fuera negativa, el
    // rango 0.0..gananciaTotal queda VACÍO en Kotlin y el `in` de abajo daría false
    // incluso para 0.0, fallando siempre con un mensaje que no explica el problema real.
    require(gananciaTotal >= 0) {
        "La receta '${d.titulo}' no cubre su costo con el precio actual: no hay ganancia que repartir"
    }
    require(gananciaEmpleado in 0.0..gananciaTotal) {
        "La ganancia del empleado ($gananciaEmpleado) excede la ganancia total de la receta ($gananciaTotal)"
    }

    return Sueldo(
        ingresoBruto = ingreso,
        yoMeLlevo = d.costoTotal + (gananciaTotal - gananciaEmpleado),
        gananciaEmpleado = gananciaEmpleado
    )
}
