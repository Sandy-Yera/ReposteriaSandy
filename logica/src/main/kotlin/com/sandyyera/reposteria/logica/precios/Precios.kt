package com.sandyyera.reposteria.logica.precios

/** Si un precio guardado es por trozo o por producto completo. */
enum class ModoPrecio { TROZO, PRODUCTO }

/**
 * Un precio o promoción tal como lo ven las fórmulas.
 *
 * Es el equivalente puro de la tabla `receta_precios`: la entidad de Room vive en el
 * módulo `:app` y se convierte a este tipo al armar el snapshot. Así las fórmulas no
 * dependen de Android y se pueden probar sin base de datos.
 *
 * Significa "vender [cantidad] trozos (o [cantidad] productos completos, según [modo])
 * por [precioTotal] en total".
 */
data class PrecioVigente(
    val modo: ModoPrecio,
    val cantidad: Int,
    val precioTotal: Double,
    val etiqueta: String? = null
)

/**
 * La foto de una receta que reciben todas las fórmulas de este archivo.
 *
 * Se arma una sola vez leyendo la base y se pasa hacia abajo, en vez de que cada fórmula
 * salga a buscar sus datos: si no, el costo total se recalcularía siete veces seguidas
 * con el mismo resultado (sección 6.4 de arquitectura.md).
 */
data class DatosCalculoReceta(
    val recetaId: Long,
    val titulo: String,
    val costoTotal: Double,
    val trozos: Int,
    val precios: List<PrecioVigente>
) {
    init {
        require(trozos >= 1) { "Una receta siempre tiene al menos 1 trozo (llegó $trozos)" }
    }

    /**
     * Si la receta ya tiene precio definido.
     *
     * Hay que consultarlo **antes** de pedir cualquier cifra automática: una receta a
     * medio crear en el wizard todavía no pasó por el paso de precios, y la pantalla
     * debe mostrar un guion en vez de reventar.
     */
    val tienePrecio: Boolean get() = precios.isNotEmpty()
}

/** El resultado de [trozoGanador]. */
data class TrozoGanador(
    val numero: Int,
    val ganancia: Double,
    /**
     * Si ese trozo existe de verdad en la receta. Cuando es `false`, la receta se vende
     * bajo su costo: haría falta vender más trozos de los que rinde para recuperarlo.
     */
    val alcanzable: Boolean
)

/** Cuántos trozos cubre un precio: en modo producto, cada unidad son todos los trozos. */
fun trozosCubiertosPor(precio: PrecioVigente, d: DatosCalculoReceta): Int =
    if (precio.modo == ModoPrecio.TROZO) precio.cantidad else precio.cantidad * d.trozos

/** Lleva cualquier precio a su equivalente por trozo, para poder compararlos entre sí. */
fun precioPorTrozoDe(precio: PrecioVigente, d: DatosCalculoReceta): Double {
    val cubiertos = trozosCubiertosPor(precio, d)
    require(cubiertos > 0) { "Un precio debe cubrir al menos un trozo (llegó ${precio.cantidad})" }
    return precio.precioTotal / cubiertos
}

/** Lo que cuesta producir cada trozo. Igual para todos los precios de la misma receta. */
fun costoPorTrozo(d: DatosCalculoReceta): Double = d.costoTotal / d.trozos

/** Ganancia por trozo con un precio concreto. Puede ser negativa si no cubre el costo. */
fun gananciaPorTrozoDe(precio: PrecioVigente, d: DatosCalculoReceta): Double =
    precioPorTrozoDe(precio, d) - costoPorTrozo(d)

/**
 * De todos los precios guardados, el que deja menos ganancia: el peor caso, que es con
 * el que se juega.
 *
 * Lanza excepción si la receta no tiene ningún precio. Quien la use en un total de varias
 * recetas debe filtrar antes con [DatosCalculoReceta.tienePrecio], para que una receta a
 * medio configurar no voltee la suma completa.
 */
fun precioDeMenorGanancia(d: DatosCalculoReceta): PrecioVigente =
    d.precios.minByOrNull { gananciaPorTrozoDe(it, d) }
        ?: error("La receta '${d.titulo}' no tiene ningún precio guardado todavía")

/** El precio por trozo que alimenta todas las cifras automáticas de la app. */
fun precioEfectivoPorTrozo(d: DatosCalculoReceta): Double =
    precioPorTrozoDe(precioDeMenorGanancia(d), d)

/** Lo que entra al vender el producto completo, sin descontar nada. */
fun ingresoBruto(d: DatosCalculoReceta): Double = precioEfectivoPorTrozo(d) * d.trozos

/** Ganancia por trozo al precio vigente. Puede ser negativa. */
fun gananciaPorTrozo(d: DatosCalculoReceta): Double =
    precioEfectivoPorTrozo(d) - costoPorTrozo(d)

/** Ganancia del producto completo. Puede ser negativa. */
fun gananciaFinal(d: DatosCalculoReceta): Double = ingresoBruto(d) - d.costoTotal

/**
 * A partir de qué trozo vendido la receta deja de perder plata.
 *
 * Con costo 1.400 y trozos a 500, el tercero es el primero que supera el costo y deja
 * 100 de ganancia. Si el costo se cubriera justo (1.500 con trozos a 500), el ganador es
 * el cuarto: empatar no es ganar.
 *
 * Devuelve además si ese trozo existe: con costo 5.000, trozos a 500 y solo 8 trozos,
 * el número da 11 y no hay forma de llegar. La pantalla debe mostrar la advertencia en
 * vez del número.
 */
fun trozoGanador(d: DatosCalculoReceta): TrozoGanador {
    val precioTrozo = precioEfectivoPorTrozo(d)
    val n = (d.costoTotal / precioTrozo).toInt() + 1
    return TrozoGanador(
        numero = n,
        ganancia = n * precioTrozo - d.costoTotal,
        alcanzable = n <= d.trozos
    )
}
