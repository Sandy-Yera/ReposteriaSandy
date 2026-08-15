package com.sandyyera.reposteria.logica.ventas

import com.sandyyera.reposteria.logica.formato.formatearMonto
import com.sandyyera.reposteria.logica.validaciones.errorEnNumeroPositivoTexto
import com.sandyyera.reposteria.logica.validaciones.esNumeroEntero
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlin.math.abs

/**
 * Lo estimado contra lo real de un día de ventas (18.2).
 *
 * Es la pregunta que le da sentido al resto de la app: hasta acá todo calcula lo que **debería**
 * pasar, y nada compara eso con lo que pasó. Lo pidió Sandy así — *"ver el estimado del costo y la
 * ganancia contra lo real del costo y la ganancia del día"*.
 *
 * **Acá no se consulta nada.** Las cuatro cifras llegan sumadas por la base y lo de este archivo
 * es lo que se concluye de ellas, que es justo lo que se puede probar sin celular.
 */

/**
 * Cuántas unidades caben en una línea de venta.
 *
 * Como el resto de los topes de la app (`MAXIMO_TROZOS`, `MAXIMAS_UNIDADES_POR_DIA`), **no es una
 * regla del negocio: es la red contra el dedo pegado**. Mil productos completos de una sola receta
 * en un día no es una repostería, es un cero de más al teclear — y un cero de más acá no se nota
 * mirando: infla el informe del día, del mes y del año de una vez.
 */
const val MAXIMAS_UNIDADES_VENDIDAS = 1000

/** Cuánto se perdona al comparar dos montos. Debajo de un peso, la diferencia no es información. */
private const val UN_PESO = 0.5

/**
 * Revisa cuántas unidades se vendieron.
 *
 * **Rechaza el 0**, al revés que las unidades por día de un empleado: allá el cero es cómo se dice
 * "esta receta todavía no la vendo" y la simulación da cero, que es la respuesta correcta. Acá una
 * línea de cero unidades no dice nada — es una línea que sobra, y lo que corresponde es quitarla.
 */
fun errorEnUnidadesVendidasTexto(texto: String): String? {
    if (texto.isBlank()) return "Escribe cuántas vendiste"
    val numero = textoANumero(texto) ?: return "Escribe un número válido"
    return when {
        numero.isNaN() || numero.isInfinite() -> "Escribe un número válido"
        !esNumeroEntero(numero) -> "Tiene que ser un número entero"
        numero < 1 -> "Tiene que ser al menos 1. Si no vendiste, quita la línea"
        numero > MAXIMAS_UNIDADES_VENDIDAS ->
            "Más de $MAXIMAS_UNIDADES_VENDIDAS parece un error"
        else -> null
    }
}

/**
 * Revisa a cuánto se vendió cada unidad.
 *
 * Reutiliza `errorEnNumeroPositivoTexto` con su propio texto para el vacío, igual que el precio de
 * una receta: la regla —número, no cero, no infinito— es la misma y escribirla otra vez sería
 * tener dos que se separan.
 */
fun errorEnPrecioDeVentaTexto(texto: String): String? =
    errorEnNumeroPositivoTexto(texto, "Escribe a cuánto lo vendiste")

/**
 * Las cuatro cifras de un día, y todo lo que se concluye de ellas (18.2).
 *
 * [costoReal] es `null` —y no 0— cuando **ninguna venta de ese día descontó del almacén**, y esa
 * distinción es la que sostiene el módulo entero: sin movimientos no se sabe lo que costó de
 * verdad, y un 0 diría que fue gratis. Un informe que confunde "no sé" con "nada" miente en la
 * dirección más cómoda.
 */
data class CifrasDelDia(
    val ingresoReal: Double,
    val ingresoEstimado: Double,
    val costoEstimado: Double,
    val costoReal: Double? = null
) {

    /** Si el costo que se muestra salió de movimientos de verdad y no de la estimación. */
    val costoEsDeVerdad: Boolean get() = costoReal != null

    /**
     * El costo con el que se hacen las cuentas: el real si lo hay, el estimado si no.
     *
     * **La pantalla tiene que decir cuál de los dos es** (18.5). Mostrar el estimado bajo el
     * título "real" sería el mismo número con otro nombre, que es exactamente lo que este módulo
     * existe para no hacer.
     */
    val costoQueVale: Double get() = costoReal ?: costoEstimado

    val gananciaEstimada: Double get() = ingresoEstimado - costoEstimado

    /** Lo que quedó de verdad, con la salvedad de [costoEsDeVerdad] sobre el costo. */
    val gananciaQueVale: Double get() = ingresoReal - costoQueVale

    /** Positivo si se cobró más de lo estimado. */
    val diferenciaDeIngreso: Double get() = ingresoReal - ingresoEstimado

    /** Positivo si se gastó más de lo estimado. `null` si no hay costo real que comparar. */
    val diferenciaDeCosto: Double? get() = costoReal?.let { it - costoEstimado }

    val diferenciaDeGanancia: Double get() = gananciaQueVale - gananciaEstimada
}

/**
 * Qué hay que leer de un día, en frases.
 *
 * **La diferencia entre las dos columnas no es un error de la app: es el hallazgo** (18.2), y por
 * eso se dice con palabras y no se deja como dos números uno al lado del otro. Un costo real más
 * alto significa que la receta gasta más de lo anotado; uno más bajo, que la receta pide de más —
 * la misma señal que ya da un stock en negativo (14.8), vista desde el otro lado.
 *
 * Devuelve una lista y no un texto suelto porque son lecturas independientes: el ingreso puede
 * calzar y el costo no. Vacía quiere decir que el día salió como se esperaba, que también es una
 * respuesta.
 *
 * **Nada por debajo de un peso.** Las cifras vienen de multiplicaciones y divisiones, así que
 * siempre hay unas milésimas de diferencia; decir "cobraste $0 más de lo estimado" enseña a
 * ignorar estos avisos.
 */
fun loQueDiceElDia(cifras: CifrasDelDia): List<String> = buildList {
    val porElIngreso = cifras.diferenciaDeIngreso
    if (abs(porElIngreso) >= UN_PESO) {
        add(
            if (porElIngreso > 0) {
                "Cobraste $${formatearMonto(porElIngreso)} más de lo que la app estimaba."
            } else {
                "Cobraste $${formatearMonto(-porElIngreso)} menos de lo que la app estimaba."
            }
        )
    }

    val porElCosto = cifras.diferenciaDeCosto
    if (porElCosto == null) {
        add(
            "Esta venta no descontó del almacén, así que el costo es el estimado: no hay con " +
                "qué compararlo."
        )
    } else if (abs(porElCosto) >= UN_PESO) {
        add(
            if (porElCosto > 0) {
                "Gastaste $${formatearMonto(porElCosto)} más de lo anotado: esas recetas " +
                    "llevan más de lo que dicen."
            } else {
                "Gastaste $${formatearMonto(-porElCosto)} menos de lo anotado: esas recetas " +
                    "piden de más."
            }
        )
    }
}
