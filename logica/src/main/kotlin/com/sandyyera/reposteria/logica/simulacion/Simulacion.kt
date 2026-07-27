package com.sandyyera.reposteria.logica.simulacion

/** Semanas por mes: 52 ÷ 12. Se usa para pasar cualquier cifra semanal a mensual. */
const val SEMANAS_POR_MES = 4.33

/** Las 6 cifras de una simulación de ventas. Lo mensual ya viene multiplicado. */
data class SimulacionResultado(
    val ingresoSemanal: Double,
    val costoSemanal: Double,
    val gananciaSemanal: Double,
    val ingresoMensual: Double,
    val costoMensual: Double,
    val gananciaMensual: Double
)

/**
 * Proyecta a la semana y al mes lo que deja una receta.
 *
 * Vendiendo 4 días a la semana, 2 unidades por día, con un ingreso de 5.000 por unidad,
 * la semana da 40.000.
 *
 * [ingresoBase] y [costoBase] son por unidad vendida y salen del mismo snapshot que el
 * resto de la pantalla.
 */
fun simulacion(
    ingresoBase: Double,
    costoBase: Double,
    dias: Int,
    unidades: Int
): SimulacionResultado {
    val ingresoSemanal = ingresoBase * dias * unidades
    val costoSemanal = costoBase * dias * unidades
    val gananciaSemanal = ingresoSemanal - costoSemanal
    return SimulacionResultado(
        ingresoSemanal = ingresoSemanal,
        costoSemanal = costoSemanal,
        gananciaSemanal = gananciaSemanal,
        ingresoMensual = ingresoSemanal * SEMANAS_POR_MES,
        costoMensual = costoSemanal * SEMANAS_POR_MES,
        gananciaMensual = gananciaSemanal * SEMANAS_POR_MES
    )
}
