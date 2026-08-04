package com.sandyyera.reposteria.logica.simulacion

import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.RepartoDeVenta
import com.sandyyera.reposteria.logica.precios.precioBaseDelProducto
import com.sandyyera.reposteria.logica.precios.precioBasePorTrozo
import com.sandyyera.reposteria.logica.precios.precioDeReferencia
import com.sandyyera.reposteria.logica.precios.repartir

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

/**
 * Cuántas unidades se venden en una semana, contadas **como las cuenta la promoción**.
 *
 * Si el precio de referencia es por trozo, se cuentan trozos: los de cada producto por todos
 * los productos de la semana. Si es por producto completo, se cuentan productos.
 */
fun loQueSeVendeEnLaSemana(d: DatosCalculoReceta, dias: Int, unidades: Int): Int {
    val productos = dias * unidades
    return if (precioDeReferencia(d).modo == ModoPrecio.TROZO) productos * d.trozos else productos
}

/**
 * Cómo se reparte la venta de **toda la semana** entre la promoción y lo que sobra (8.6.1).
 *
 * Acá se paga la promesa que quedó escrita al arreglar el ingreso de un producto: la duda era
 * qué pasa al vender varios, y la respuesta es que **se reparte el total, no se multiplica el
 * resultado de uno**. Una receta de 3 trozos con una promo de 2 deja siempre un trozo suelto
 * si se mira producto por producto; mirando la semana entera, dos productos son 6 trozos y la
 * promo entra tres veces justas.
 *
 * Multiplicar habría dado más —cada producto arrastrando su resto— y ese "más" es plata que no
 * entra.
 */
fun repartoSemanal(d: DatosCalculoReceta, dias: Int, unidades: Int): RepartoDeVenta {
    val referencia = precioDeReferencia(d)
    val suelto = if (referencia.modo == ModoPrecio.TROZO) {
        precioBasePorTrozo(d)
    } else {
        precioBaseDelProducto(d)
    }
    return repartir(loQueSeVendeEnLaSemana(d, dias, unidades), referencia, suelto)
}

/**
 * La simulación de una receta concreta, con su reparto real (8.7).
 *
 * **Es la que hay que usar desde la pantalla.** [simulacion] sigue existiendo porque es la
 * aritmética pura —y la usará la simulación de varias recetas de un empleado (10.2), donde el
 * ingreso de cada una ya viene calculado— pero por sí sola no sabe de promociones y
 * multiplicaría el resto de cada producto.
 *
 * El costo sí se multiplica y no se reparte: producir dos tortas cuesta el doble que producir
 * una, sin promociones que valgan.
 */
fun simulacionDeVenta(d: DatosCalculoReceta, dias: Int, unidades: Int): SimulacionResultado {
    val productos = dias * unidades
    val ingresoSemanal = repartoSemanal(d, dias, unidades).total
    val costoSemanal = d.costoTotal * productos
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
