package com.sandyyera.reposteria.logica.calendario

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Qué día es, dicho de todas las formas que sirven para analizar después (16.3).
 *
 * Sandy lo pidió junto con las ventas: *"cosas como el día-mes como número, como también día
 * escrito, y verificar en el calendario si ese día es algún festivo o es un día especial, para
 * poder recabar 'metadatos'"*. La palabra es exacta — no es información de la venta, es
 * información **del día** de la venta, y es lo que después responde "¿los domingos vendo más?"
 * o "¿el 18 se dispara?".
 *
 * **Se calcula y no se guarda.** Con la fecha de la venta guardada, todo esto sale solo; anotarlo
 * en la tabla serían seis columnas que dicen lo mismo que una, y que además pueden quedar mal si
 * alguien corrige la fecha después.
 *
 * Todo acá es Kotlin puro, así que se prueba entero sin celular — que es la razón de que exista
 * antes que la pantalla que lo va a usar.
 */

/** Cómo se lee cada día de la semana, en minúscula para poder meterlo en una frase. */
fun nombreDelDia(fecha: LocalDate): String = when (fecha.dayOfWeek) {
    DayOfWeek.MONDAY -> "lunes"
    DayOfWeek.TUESDAY -> "martes"
    DayOfWeek.WEDNESDAY -> "miércoles"
    DayOfWeek.THURSDAY -> "jueves"
    DayOfWeek.FRIDAY -> "viernes"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}

/** Cómo se lee cada mes. */
fun nombreDelMes(fecha: LocalDate): String = when (fecha.monthValue) {
    1 -> "enero"
    2 -> "febrero"
    3 -> "marzo"
    4 -> "abril"
    5 -> "mayo"
    6 -> "junio"
    7 -> "julio"
    8 -> "agosto"
    9 -> "septiembre"
    10 -> "octubre"
    11 -> "noviembre"
    else -> "diciembre"
}

/** Si cae en fin de semana. Se separa del feriado porque un sábado no es un festivo. */
fun esFinDeSemana(fecha: LocalDate): Boolean =
    fecha.dayOfWeek == DayOfWeek.SATURDAY || fecha.dayOfWeek == DayOfWeek.SUNDAY

/**
 * El domingo de Pascua de un año, por el algoritmo de Gauss (variante anónima de 1876).
 *
 * Hace falta porque **tres feriados chilenos cuelgan de él** —Viernes Santo, Sábado Santo y, de
 * rebote, Corpus Christi no es feriado acá pero sí lo son los dos primeros— y no hay forma de
 * ponerlos en una tabla fija: se mueven todos los años.
 *
 * Es la única cuenta de este archivo que no se puede leer de un vistazo, y por eso está probada
 * contra fechas conocidas en vez de contra sí misma.
 */
fun domingoDePascua(anio: Int): LocalDate {
    val a = anio % 19
    val b = anio / 100
    val c = anio % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val mes = (h + l - 7 * m + 114) / 31
    val dia = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(anio, mes, dia)
}

/**
 * Un día del calendario que no es uno cualquiera.
 *
 * [esFeriado] separa los feriados legales de las **fechas comerciales** —el día de la madre, San
 * Valentín—, y esa distinción es la que hace que el dato sirva: para una repostería el día de la
 * madre pesa más que un feriado cualquiera, y mezclarlos en una sola bolsa perdería justo eso.
 */
data class DiaEspecial(val nombre: String, val esFeriado: Boolean)

/**
 * Los feriados chilenos de un año, por fecha.
 *
 * **No están todos y está dicho a propósito.** Faltan los regionales y los que el Congreso
 * declara para un año suelto (un censo, una elección), que no se pueden calcular porque no
 * siguen ninguna regla. Lo que hay son los permanentes, que son los que se repiten y por lo tanto
 * los únicos que sirven para comparar un año con otro.
 *
 * Los tres de fecha móvil —San Pedro y San Pablo, Asunción… — que la ley 19.973 corre al lunes
 * más cercano **no se corren acá**: esa regla depende de en qué día caiga y de excepciones, y una
 * regla a medias que a veces acierta es peor que no tenerla. Se guardan en su fecha propia, que
 * es la que la gente asocia con el día.
 */
fun feriadosDe(anio: Int): Map<LocalDate, DiaEspecial> {
    val pascua = domingoDePascua(anio)
    val fijos = listOf(
        LocalDate.of(anio, 1, 1) to "Año Nuevo",
        pascua.minusDays(2) to "Viernes Santo",
        pascua.minusDays(1) to "Sábado Santo",
        LocalDate.of(anio, 5, 1) to "Día del Trabajo",
        LocalDate.of(anio, 5, 21) to "Glorias Navales",
        LocalDate.of(anio, 6, 29) to "San Pedro y San Pablo",
        LocalDate.of(anio, 7, 16) to "Virgen del Carmen",
        LocalDate.of(anio, 8, 15) to "Asunción de la Virgen",
        LocalDate.of(anio, 9, 18) to "Independencia Nacional",
        LocalDate.of(anio, 9, 19) to "Glorias del Ejército",
        LocalDate.of(anio, 10, 12) to "Encuentro de Dos Mundos",
        LocalDate.of(anio, 10, 31) to "Iglesias Evangélicas y Protestantes",
        LocalDate.of(anio, 11, 1) to "Día de Todos los Santos",
        LocalDate.of(anio, 12, 8) to "Inmaculada Concepción",
        LocalDate.of(anio, 12, 25) to "Navidad"
    )
    return fijos.associate { (fecha, nombre) -> fecha to DiaEspecial(nombre, esFeriado = true) }
}

/**
 * El segundo domingo de mayo: el día de la madre en Chile.
 *
 * Tiene función propia porque **es la fecha que más mueve una repostería** y es móvil, así que no
 * puede vivir en una lista fija.
 */
fun diaDeLaMadre(anio: Int): LocalDate = segundoDomingoDe(anio, 5)

/** El tercer domingo de junio: el día del padre. */
fun diaDelPadre(anio: Int): LocalDate = domingoNumero(anio, 6, 3)

private fun segundoDomingoDe(anio: Int, mes: Int): LocalDate = domingoNumero(anio, mes, 2)

private fun domingoNumero(anio: Int, mes: Int, cual: Int): LocalDate {
    val primero = LocalDate.of(anio, mes, 1)
    // Cuántos días faltan del 1 al primer domingo. Con el 1 en domingo, cero.
    val hastaElPrimerDomingo = (7 - primero.dayOfWeek.value % 7) % 7
    return primero.plusDays((hastaElPrimerDomingo + 7L * (cual - 1)))
}

/**
 * Las fechas comerciales de un año: las que mueven la venta sin ser feriado.
 *
 * Son pocas y elegidas, no un almanaque: cada una está porque a una repostería le cambia el día.
 * Agregar las cincuenta que existen convertiría "día especial" en un dato que se cumple siempre,
 * y un dato que se cumple siempre no distingue nada.
 */
fun fechasComercialesDe(anio: Int): Map<LocalDate, DiaEspecial> = mapOf(
    LocalDate.of(anio, 2, 14) to DiaEspecial("San Valentín", esFeriado = false),
    diaDeLaMadre(anio) to DiaEspecial("Día de la Madre", esFeriado = false),
    diaDelPadre(anio) to DiaEspecial("Día del Padre", esFeriado = false),
    LocalDate.of(anio, 12, 24) to DiaEspecial("Nochebuena", esFeriado = false),
    LocalDate.of(anio, 12, 31) to DiaEspecial("Fin de Año", esFeriado = false)
)

/**
 * Qué tiene de especial una fecha, o `null` si es un día común.
 *
 * **El feriado le gana a la fecha comercial** cuando caen juntos, porque es el que cambia si el
 * local abre. Pasa de verdad: el día de la madre puede caer el 1 de mayo.
 */
fun queDiaEs(fecha: LocalDate): DiaEspecial? =
    feriadosDe(fecha.year)[fecha] ?: fechasComercialesDe(fecha.year)[fecha]

/**
 * Todo lo que se sabe de un día, junto, listo para guardar en un informe o mostrar.
 *
 * Es lo que devuelve [datosDelDia] y lo que la sección de ventas va a agrupar: cada campo existe
 * porque es una forma distinta de cortar los números después —por día de la semana, por mes, por
 * si era festivo— y tenerlos separados evita que cada informe los vuelva a derivar a su manera.
 */
data class DatosDelDia(
    val fecha: LocalDate,
    val dia: Int,
    val mes: Int,
    val anio: Int,
    val nombreDelDia: String,
    val nombreDelMes: String,
    val esFinDeSemana: Boolean,
    val especial: DiaEspecial?
) {
    /** "18/9/2026", que es como se escribe una fecha corta acá. */
    val comoNumero: String get() = "$dia/$mes/$anio"

    /** "viernes 18 de septiembre", para leerlo sin traducir del número. */
    val comoTexto: String get() = "$nombreDelDia $dia de $nombreDelMes"

    val esFeriado: Boolean get() = especial?.esFeriado == true

    /** El nombre del día especial, o `null`. Lo que va como etiqueta al lado de la fecha. */
    val comoSeLlamaElDia: String? get() = especial?.nombre
}

/** Todo lo que se sabe de un día. Es la única entrada de este archivo que usa la app. */
fun datosDelDia(fecha: LocalDate): DatosDelDia = DatosDelDia(
    fecha = fecha,
    dia = fecha.dayOfMonth,
    mes = fecha.monthValue,
    anio = fecha.year,
    nombreDelDia = nombreDelDia(fecha),
    nombreDelMes = nombreDelMes(fecha),
    esFinDeSemana = esFinDeSemana(fecha),
    especial = queDiaEs(fecha)
)
