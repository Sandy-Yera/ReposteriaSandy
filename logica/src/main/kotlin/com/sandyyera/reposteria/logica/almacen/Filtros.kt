package com.sandyyera.reposteria.logica.almacen

import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlin.math.abs

/**
 * Los filtros del almacén: buscar por cuánto queda y por qué tipo de cosa es (14.14).
 *
 * Los pidió Sandy cuando el almacén dejó de caber en una pantalla: con cuarenta filas, "¿qué se
 * está por acabar?" no se contesta leyendo la lista entera. El buscador por nombre sirve cuando
 * uno sabe qué busca; esto sirve para lo contrario — cuando lo que se busca es *cuáles*.
 *
 * **Todo esto vive acá y no en el ViewModel** por lo de siempre: son reglas que se pueden probar
 * sin celular, y escritas entre medio del dibujo se equivocan solas. Además, escribir `=>300` y
 * `>=300` es la clase de detalle que solo se comprueba con pruebas.
 */

/** Cuánta diferencia se perdona al comparar por igual. La misma del precio en el almacén. */
private const val TOLERANCIA = 0.000005

/**
 * Cómo se compara la cantidad escrita con la guardada.
 *
 * [escrituras] tiene **todas** las formas aceptadas de cada una, y ahí está el punto: Sandy pidió
 * expresamente que `>=` y `=>` funcionaran las dos —*"ojo que debe permitir hacer >= como también
 * =>"*—. No es capricho: cuál de las dos sale primero depende de por dónde uno empiece a pensar la
 * frase ("mayor o igual" / "igual o mayor"), y rechazar una obligaría a recordar cuál eligió la
 * app en vez de escribir lo que uno quiso decir.
 */
enum class Comparacion(val escrituras: List<String>, val comoSeLee: String) {
    MAYOR_O_IGUAL(listOf(">=", "=>"), "o más"),
    MENOR_O_IGUAL(listOf("<=", "=<"), "o menos"),
    IGUAL(listOf("="), "exactamente"),
    MAYOR(listOf(">"), "más de"),
    MENOR(listOf("<"), "menos de")
}

/**
 * Un filtro de cantidad ya entendido: la comparación y el número contra el que se compara.
 *
 * No guarda unidad **a propósito**. Sandy pidió que funcionara *"para unidades e ingredientes"*, y
 * la forma de que funcione para los dos es no mezclarse en eso: `=3` deja pasar las 3 cajas y los
 * 3 gramos. Filtrar además por unidad es la otra mitad de esta pantalla ([MarcaDeAlmacen]), y son
 * dos preguntas distintas que conviene poder hacer por separado.
 */
data class FiltroDeCantidad(val comparacion: Comparacion, val cantidad: Double) {

    /** Si una fila con esta cantidad tiene que quedar a la vista. */
    fun deja(cuanto: Double): Boolean = when (comparacion) {
        // El igual se compara con tolerancia y no con `==`: las cantidades pasan por divisiones
        // al descontar recetas, y un 300 que quedó en 299,9999999 tiene que seguir siendo 300
        // para quien lo mira. Es la misma tolerancia con la que el almacén compara precios.
        Comparacion.IGUAL -> abs(cuanto - cantidad) < TOLERANCIA
        Comparacion.MAYOR -> cuanto > cantidad + TOLERANCIA
        Comparacion.MENOR -> cuanto < cantidad - TOLERANCIA
        Comparacion.MAYOR_O_IGUAL -> cuanto > cantidad - TOLERANCIA
        Comparacion.MENOR_O_IGUAL -> cuanto < cantidad + TOLERANCIA
    }

    /** "300 o más", para decir en pantalla qué se entendió (8.7.1). */
    val comoSeLee: String get() = when (comparacion) {
        Comparacion.MAYOR_O_IGUAL, Comparacion.MENOR_O_IGUAL ->
            "${sinDecimalesInutiles(cantidad)} ${comparacion.comoSeLee}"
        else -> "${comparacion.comoSeLee} ${sinDecimalesInutiles(cantidad)}"
    }
}

/** "300" y no "300,0". Solo para leerlo en una frase; el número guardado no se toca. */
private fun sinDecimalesInutiles(valor: Double): String =
    if (valor == valor.toLong().toDouble()) valor.toLong().toString() else valor.toString()

/**
 * Qué es lo que hay escrito en el buscador del almacén.
 *
 * Es un tipo cerrado y no un `FiltroDeCantidad?` porque **hay tres respuestas y no dos**: puede
 * ser un nombre, puede ser un filtro, y puede ser un filtro **mal escrito**. Ese tercer caso es el
 * que importa: escribir `>abc` con un `null` de vuelta se trataría como buscar el nombre "abc",
 * la lista quedaría vacía sin explicación y el error estaría en la pantalla, invisible.
 */
sealed interface LoQueSeBusca {

    /** No empieza con un signo de comparar: es un nombre. También lo es el buscador vacío. */
    data class PorNombre(val texto: String) : LoQueSeBusca

    data class PorCantidad(val filtro: FiltroDeCantidad) : LoQueSeBusca

    /** Empieza con un signo pero lo que sigue no es un número. Se dice, no se ignora. */
    data class MalEscrito(val motivo: String) : LoQueSeBusca
}

/**
 * Lee lo que hay escrito en el buscador y decide qué se está pidiendo.
 *
 * **Un número solo no es un filtro**: escribir `300` busca el nombre "300". Exigir el signo es lo
 * que Sandy describió y además es lo que evita que un ingrediente llamado "Colorante 300" se
 * vuelva imposible de encontrar. El signo es la señal de "acá vengo a comparar".
 *
 * Las dos formas de cada signo se prueban **antes que las de un carácter**, y ese orden no es
 * decorativo: probando `=` primero, un `=>300` se leería como "igual a >300", que no es número, y
 * la mitad de lo que Sandy pidió no funcionaría.
 */
fun loQueSeBusca(texto: String): LoQueSeBusca {
    val limpio = texto.trim()
    if (limpio.isEmpty()) return LoQueSeBusca.PorNombre(limpio)

    // Las de dos caracteres primero. `Comparacion.entries` ya está en ese orden, pero ordenarlo
    // acá lo deja explícito: el día que alguien agregue una comparación al enum, el orden de la
    // lista no puede ser lo que decida si `=>` sigue funcionando.
    val escrituras = Comparacion.entries
        .flatMap { comparacion -> comparacion.escrituras.map { it to comparacion } }
        .sortedByDescending { it.first.length }

    val (signo, comparacion) = escrituras.firstOrNull { limpio.startsWith(it.first) }
        ?: return LoQueSeBusca.PorNombre(limpio)

    val resto = limpio.removePrefix(signo).trim()
    if (resto.isEmpty()) {
        return LoQueSeBusca.MalEscrito("Falta el número. Por ejemplo: ${signo}300")
    }
    val cuanto = textoANumero(resto)
        ?: return LoQueSeBusca.MalEscrito("'$resto' no es un número")
    if (cuanto < 0) return LoQueSeBusca.MalEscrito("No se puede filtrar por menos de nada")

    return LoQueSeBusca.PorCantidad(FiltroDeCantidad(comparacion, cuanto))
}

/**
 * La chuleta de cómo se escriben los filtros, para tenerla a mano.
 *
 * La pidió Sandy junto con el filtro y por adelantado: *"para poder recordar, tener un campo que
 * me diga cómo debo escribirlos, para evitar olvidar"*. Vive acá y no escrita en la pantalla por
 * el mismo motivo que la ayuda de los atajos de los pasos sale del enum: una ayuda escrita aparte
 * de lo que explica se queda mintiendo a la primera que alguien cambia una regla.
 */
val COMO_FILTRAR_POR_CANTIDAD: List<String> = listOf(
    "=300 · justo 300",
    ">300 · más de 300",
    "<300 · menos de 300",
    ">=300 o =>300 · 300 o más",
    "<=300 o =<300 · 300 o menos",
    "Sin signo se busca por nombre. Sirve igual para gramos y para unidades."
)

/**
 * Las casillas que filtran por **qué es** cada cosa (14.11).
 *
 * Son cuatro y no dos interruptores porque las preguntas tienen tres respuestas y no dos: "va en
 * recetas", "no va en recetas" y "me da igual". Con un interruptor de dos posiciones, la tercera
 * —que es la de todos los días— no se puede decir.
 */
enum class MarcaDeAlmacen(val etiqueta: String) {
    VA_EN_RECETAS("Va en recetas"),
    NO_VA_EN_RECETAS("No va en recetas"),
    POR_UNIDAD("Por unidad"),
    EN_GRAMOS("En gramos")
}

/**
 * Si una fila pasa las casillas marcadas.
 *
 * **Marcar las dos de un par es lo mismo que no marcar ninguna.** Podría tratarse como "no pasa
 * nada" —son condiciones opuestas— pero eso deja la lista vacía sin decir por qué, y con las dos
 * casillas encendidas nadie va a leer eso como un error. Lo que uno quiso decir marcando las dos
 * es "las dos me sirven", y eso es exactamente no filtrar.
 *
 * Los dos pares se aplican **uno sobre otro**: marcar "Va en recetas" y "Por unidad" deja lo que
 * cumple las dos, que es como se leen dos filtros puestos a la vez.
 */
fun dejanPasar(marcas: Set<MarcaDeAlmacen>, esObjeto: Boolean, vaEnRecetas: Boolean): Boolean {
    val porRecetas = paso(
        marcado = MarcaDeAlmacen.VA_EN_RECETAS in marcas,
        marcadoElContrario = MarcaDeAlmacen.NO_VA_EN_RECETAS in marcas,
        cumple = vaEnRecetas
    )
    val porUnidad = paso(
        marcado = MarcaDeAlmacen.POR_UNIDAD in marcas,
        marcadoElContrario = MarcaDeAlmacen.EN_GRAMOS in marcas,
        cumple = esObjeto
    )
    return porRecetas && porUnidad
}

/** La regla de un par de casillas opuestas, escrita una sola vez. */
private fun paso(marcado: Boolean, marcadoElContrario: Boolean, cumple: Boolean): Boolean = when {
    marcado == marcadoElContrario -> true
    marcado -> cumple
    else -> !cumple
}
