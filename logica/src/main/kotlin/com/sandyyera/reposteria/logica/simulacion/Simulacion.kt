package com.sandyyera.reposteria.logica.simulacion

import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.RepartoDeVenta
import com.sandyyera.reposteria.logica.precios.ingresoBruto
import com.sandyyera.reposteria.logica.precios.precioBaseDelProducto
import com.sandyyera.reposteria.logica.precios.precioBasePorTrozo
import com.sandyyera.reposteria.logica.precios.precioDeReferencia
import com.sandyyera.reposteria.logica.precios.repartir
import com.sandyyera.reposteria.logica.precios.repartoDeUnProducto

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
 * Lo que daría multiplicar el ingreso de **un** producto por los de la semana.
 *
 * No es lo que la app usa: existe para poder **contrastarla**, y nació de un caso real que Sandy
 * encontró probando. Una receta de 5 trozos con promo de 2 y trozo suelto a $6.000 deja $46.000
 * por producto (2 promos + 1 suelto); seis productos parecen $276.000, pero la semana son 30
 * trozos y la promo entra 15 veces justas: $300.000. Los $24.000 de diferencia son los seis
 * sueltos que, juntos, arman tres promociones más.
 *
 * Las dos cifras están bien y responden preguntas distintas — una es "qué pasa si vendo esta
 * torta sola", la otra "qué pasa si vendo seis". El problema es verlas en dos pantallas sin nada
 * que las una: ahí la segunda parece un error de la app. Por eso la comparación se calcula y se
 * explica en vez de esconderse.
 */
fun ingresoSiSeMultiplicaraElProducto(d: DatosCalculoReceta, dias: Int, unidades: Int): Double =
    ingresoBruto(d) * dias * unidades

/**
 * Cuántas promociones más se arman al juntar los sueltos de cada producto, o 0 si ninguna.
 *
 * Es la diferencia entre las dos cuentas de arriba, dicha en promociones en vez de en pesos:
 * "se arman 3 promociones más" se comprueba mirando la torta, y "entran $24.000 más" no.
 */
fun promocionesQueSeGananAlJuntar(d: DatosCalculoReceta, dias: Int, unidades: Int): Int {
    val productos = dias * unidades
    if (productos <= 0) return 0
    val enLaSemana = repartoSemanal(d, dias, unidades).cuantasVecesEntra
    val unoPorUno = repartoDeUnProducto(d).cuantasVecesEntra * productos
    return (enLaSemana - unoPorUno).coerceAtLeast(0)
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

/**
 * Lo que los empleados se llevan de una receta, todos juntos (10.1 y 8.7).
 *
 * **Una sola cifra y no una por empleado**, que es como Sandy lo pidió: la simulación de una
 * receta responde "cuánto me queda a mí", y para eso da igual entre cuántos se reparte lo que se
 * va. El detalle por persona ya está en la sección Empleados, que es donde se decide.
 *
 * [seLlevanPorProducto] es lo que sale **al vender un producto completo**, sumando el sueldo
 * asignado de cada empleado que tenga esta receta. Va por producto y no por semana porque de ahí
 * cuelgan las tres proyecciones sin volver a multiplicar.
 */
data class LoQueSeLlevanLosEmpleados(
    val cuantos: Int,
    val seLlevanPorProducto: Double
) {
    val hayEmpleados: Boolean get() = cuantos > 0

    /** "2 empleados" / "1 empleado", para escribirlo en una frase. */
    val comoSeLeeCuantos: String
        get() = if (cuantos == 1) "1 empleado" else "$cuantos empleados"
}

/**
 * La ganancia de una receta después de pagar a los empleados, por producto vendido.
 *
 * Sandy lo pidió al revés de como se planteó primero, y tiene razón: en vez de avisar "el precio
 * visible es sin descuentos" en las recetas que **sí** tienen empleados, lo que corresponde es
 * **restar de verdad** ahí y dejar el aviso para las que no tienen a nadie asignado. Un número que
 * hay que corregir de cabeza no es un número, es una tarea pendiente.
 *
 * Puede dar **negativo**, y eso es justamente lo que hay que ver: significa que con ese precio no
 * alcanza para pagar lo comprometido. Antes eso solo se descubría entrando a Empleados.
 */
fun gananciaDespuesDeLosEmpleados(
    gananciaPorProducto: Double,
    empleados: LoQueSeLlevanLosEmpleados
): Double = gananciaPorProducto - empleados.seLlevanPorProducto

/**
 * Qué hay que decir del precio de una receta respecto de sus empleados, o `null` si nada.
 *
 * Las tres respuestas posibles, y por qué son tres:
 *
 * - **Sin empleados**: se avisa que lo que se ve es sin descuentos, porque el día que se asigne
 *   uno estos números van a bajar y conviene saberlo antes y no después.
 * - **Con empleados y alcanza**: se dice cuánto se van, que es la resta que la pantalla ya aplicó.
 * - **Con empleados y no alcanza**: se dice que ese precio no da para pagarlos. Es el caso que
 *   Sandy quería ver acá — *"que tales precios se vean negados simplemente porque no se podría
 *   pagar a los empleados, en vez de que deba ir a empleados para verlo"*.
 */
fun loQueDicenLosEmpleados(
    gananciaPorProducto: Double,
    empleados: LoQueSeLlevanLosEmpleados
): String? = when {
    !empleados.hayEmpleados ->
        "Lo que se ve es sin descuentos por empleado: todavía no hay ninguno con esta receta."
    gananciaDespuesDeLosEmpleados(gananciaPorProducto, empleados) < 0 ->
        "Con este precio no alcanza para pagar a ${empleados.comoSeLeeCuantos} que tienen " +
            "esta receta asignada."
    else -> "Ya está descontado lo de ${empleados.comoSeLeeCuantos} que tienen esta receta."
}
