package com.sandyyera.reposteria.logica.sueldos

import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ingresoBruto
import com.sandyyera.reposteria.logica.simulacion.SEMANAS_POR_MES

/**
 * Una receta dentro de la simulación de un empleado (10.3).
 *
 * Es **una foto y no una consulta**: trae el [DatosCalculoReceta] ya leído, la ganancia ya
 * configurada y cuántas se venden al día. Así el agregado de abajo es una función pura que se
 * prueba sin base de datos, que es la regla de 6.4 — toda la lectura ocurre afuera y el bucle no
 * vuelve a tocar la base.
 *
 * [unidadesPorDia] es de esta receta; los **días** no están acá a propósito: son uno solo,
 * compartido por todas las recetas del empleado ("venderé 4 días, y esos 4 días serán 2
 * bizcochos, 1 torta, 5 chocolates por día"). Poner los días en cada fila abriría la puerta a
 * cuatro respuestas distintas para una sola pregunta.
 */
data class RecetaEnLaSimulacion(
    val datos: DatosCalculoReceta,
    val gananciaEmpleado: Double,
    val unidadesPorDia: Int
)

/**
 * Por qué una receta quedó fuera del total (10.3).
 *
 * **Lleva el motivo y no solo el título**, que es una diferencia con el diseño original: ahí
 * `omitidas` era una lista de nombres y la pantalla los presentaba como *"sin precio definido"*.
 * Con dos motivos posibles, esa frase mentiría en la mitad de los casos — y una lista que dice
 * mal por qué algo no se contó es peor que no tenerla, porque manda a arreglar lo que no está roto.
 */
data class RecetaOmitida(val titulo: String, val motivo: MotivoDeOmision)

/**
 * Las dos formas en que una receta puede no entrar en el total.
 *
 * Las dos existen por la misma razón —**una receta a medio configurar no puede voltear la
 * simulación completa**— y las dos harían fallar el cálculo si no se revisaran antes:
 * `precioDeReferencia` lanza sin precios, y `calcularSueldo` lanza si no hay ganancia que
 * repartir. En la pantalla de esa receta eso está bien, ahí uno quiere enterarse; acá haría caer
 * el total de diez recetas por culpa de una.
 */
enum class MotivoDeOmision(val comoSeLee: String) {
    /** Nunca se le puso precio, así que no hay ingreso que proyectar. */
    SIN_PRECIO("sin precio definido"),

    /**
     * El precio de referencia no alcanza a cubrir el costo.
     *
     * **No es lo mismo que no tener precio** y por eso no comparte motivo: acá hay una decisión
     * tomada y el problema es cuál. Mezclarlos mandaría a poner un precio que ya existe.
     */
    SE_VENDE_BAJO_EL_COSTO("se vende bajo su costo")
}

/**
 * El total de una simulación con varias recetas, por día, semana y mes (10.3).
 *
 * **Guarda solo las cifras diarias** y deriva las otras seis. Guardar las tres versiones
 * permitiría que quedaran desincronizadas entre sí, que es justo lo que nadie revisa hasta que
 * un número no cuadra y no hay forma de saber cuál de los tres está mal.
 */
data class SimulacionMultipleResultado(
    val ingresoDiario: Double,
    val yoMeLlevoDiario: Double,
    val empleadoDiario: Double,
    val diasPorSemana: Int,
    /** Las que quedaron fuera del total, con su motivo. La pantalla las lista aparte. */
    val omitidas: List<RecetaOmitida> = emptyList()
) {
    val ingresoSemanal: Double get() = ingresoDiario * diasPorSemana
    val yoMeLlevoSemanal: Double get() = yoMeLlevoDiario * diasPorSemana
    val empleadoSemanal: Double get() = empleadoDiario * diasPorSemana

    val ingresoMensual: Double get() = ingresoSemanal * SEMANAS_POR_MES
    val yoMeLlevoMensual: Double get() = yoMeLlevoSemanal * SEMANAS_POR_MES
    val empleadoMensual: Double get() = empleadoSemanal * SEMANAS_POR_MES

    /** Si alguna receta quedó fuera. La pantalla lo usa para mostrar la lista o esconderla. */
    val hayOmitidas: Boolean get() = omitidas.isNotEmpty()

    /**
     * Si no entró **ninguna** receta al total.
     *
     * Es distinto de "no hay recetas asignadas" y hay que distinguirlo: acá el empleado sí tiene
     * recetas, pero ninguna se puede proyectar todavía. Un cero a secas se leería como "no vende
     * nada", que es una conclusión y no un dato faltante.
     */
    val todasOmitidas: Boolean get() = hayOmitidas && ingresoDiario == 0.0
}

/**
 * Suma lo que dejan al día todas las recetas de un empleado, y lo proyecta (10.3).
 *
 * **Las recetas que no se pueden calcular se saltan y se devuelven en `omitidas`**, en vez de
 * hacer fallar el total. Es la decisión central de esta función: con diez recetas asignadas y una
 * a medio configurar, lanzar excepción dejaría al empleado sin ninguna cifra por culpa de algo
 * que se arregla en otra pantalla. Se revisa **antes** de calcular, y es barato justamente porque
 * los precios ya vienen dentro de la foto.
 *
 * No reasigna sueldos: solo lee lo que ya se configuró receta por receta (10.1) y lo agrega.
 *
 * [diasPorSemana] es uno solo para todas, y se acota a 1 como mínimo: proyectar con 0 días daría
 * cero en todo y se leería como "no gana nada", que es una conclusión y no lo que se preguntó.
 */
fun simulacionMultiple(
    recetas: List<RecetaEnLaSimulacion>,
    diasPorSemana: Int
): SimulacionMultipleResultado {
    var ingresoDia = 0.0
    var yoMeLlevoDia = 0.0
    var empleadoDia = 0.0
    val omitidas = mutableListOf<RecetaOmitida>()

    recetas.forEach { fila ->
        val motivo = porQueNoSePuedeCalcular(fila)
        if (motivo != null) {
            omitidas += RecetaOmitida(fila.datos.titulo, motivo)
            return@forEach
        }

        val porDia = fila.unidadesPorDia
        val sueldo = calcularSueldo(fila.datos, fila.gananciaEmpleado)
        ingresoDia += sueldo.ingresoBruto * porDia
        yoMeLlevoDia += sueldo.yoMeLlevo * porDia
        empleadoDia += sueldo.gananciaEmpleado * porDia
    }

    return SimulacionMultipleResultado(
        ingresoDiario = ingresoDia,
        yoMeLlevoDiario = yoMeLlevoDia,
        empleadoDiario = empleadoDia,
        diasPorSemana = diasPorSemana.coerceAtLeast(1),
        omitidas = omitidas
    )
}

/**
 * Por qué esta receta no entra en el total, o `null` si sí entra.
 *
 * **Comprueba exactamente lo que `calcularSueldo` exige**, y ese es todo el punto: si acá se
 * revisara de menos, la excepción caería igual y con ella el total entero; si se revisara de
 * más, quedarían fuera recetas que sí se podían calcular.
 *
 * La ganancia pedida por encima de la total **no se omite**: eso no es una receta a medio
 * configurar sino un sueldo mal asignado, y esconderlo en una lista de omitidas dejaría al
 * empleado con un total silenciosamente menor. Es la única que sigue lanzando.
 */
private fun porQueNoSePuedeCalcular(fila: RecetaEnLaSimulacion): MotivoDeOmision? {
    if (!fila.datos.tienePrecio) return MotivoDeOmision.SIN_PRECIO
    if (ingresoBruto(fila.datos) < fila.datos.costoTotal) {
        return MotivoDeOmision.SE_VENDE_BAJO_EL_COSTO
    }
    return null
}
