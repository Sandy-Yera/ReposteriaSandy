package com.sandyyera.reposteria.logica.formato

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Cuántos decimales se pueden escribir, que son los que la app después guarda.
 *
 * **Eran 2 y pasaron a 5**, y no es un capricho de precisión: el que se rompía era el valor
 * por gramo. Un saco de 25 kg a $1.700 sale a 0,068 por gramo; redondeado a 0,07, multiplicar
 * por los 500 g de una receta da $35 donde son $34 — un 3 % de error metido en el costo de
 * **cada** receta que use ese ingrediente, y creciendo hacia abajo: algo a 0,004 por gramo se
 * redondeaba a 0,00 y salía gratis.
 *
 * Con 5 el error queda por debajo del peso en cualquier receta de tamaño real.
 */
const val MAXIMO_DECIMALES = 5

/** 10 elevado a [MAXIMO_DECIMALES]. Los dos tienen que moverse juntos. */
private const val ESCALA_DECIMAL = 100_000L

/**
 * De dónde en adelante un `Double` ya no tiene decimales que valga la pena redondear.
 *
 * Multiplicar por [ESCALA_DECIMAL] un número más grande que esto desborda `Long`, y
 * `roundToLong` no avisa: se pega al tope y devuelve un número sin ninguna relación con el
 * original. Es la misma trampa que ya costó una vez con `toInt()` en `esNumeroEntero`. Ningún
 * precio de repostería llega acá; el que llega es un dedo pegado en el teclado, y ahí lo
 * correcto es devolver lo que se escribió y dejar que el tope del campo lo rechace.
 */
private const val TOPE_PARA_REDONDEAR = 1e13

/**
 * Redondea a [MAXIMO_DECIMALES], que es la precisión con la que la app guarda y muestra.
 *
 * Es la misma cuenta que hace [formatearNumero] antes de armar el texto, separada acá porque
 * también hace falta **antes de guardar**: un valor por gramo calculado como 1,666666… se
 * mostraría redondeado y se guardaría entero, y entonces multiplicarlo por los gramos de una
 * receta no daría lo que la pantalla dejó ver. Guardando lo mismo que se muestra, la cuenta
 * cierra y un error se puede pillar mirando.
 *
 * **Se llamaba `redondearADosDecimales`** y se renombró al pasar a 5: el nombre decía el
 * número, así que cambiarlo por dentro habría dejado a cada llamador diciendo una mentira.
 *
 * Lo usan la calculadora de valor por gramo (7.2), los reescalados de receta (8.3.1) y la
 * adaptación de una parte traída (8.11.3).
 */
fun redondearParaGuardar(valor: Double): Double {
    if (!valor.isFinite() || abs(valor) >= TOPE_PARA_REDONDEAR) return valor
    return (valor * ESCALA_DECIMAL).roundToLong() / ESCALA_DECIMAL.toDouble()
}

/**
 * Convierte un número al formato de la app: punto para los miles, coma para los decimales.
 *
 *     1000.0     -> "1.000"
 *     1.55       -> "1,55"
 *     1.5        -> "1,5"
 *     0.06667    -> "0,06667"
 *     250.0      -> "250"
 *     -0.56      -> "-0,56"
 *     -1234.56   -> "-1.234,56"
 *
 * **Muestra hasta [MAXIMO_DECIMALES] y no rellena con ceros.** Esa es la parte que hace
 * soportables los 5 decimales: un precio redondo se sigue leyendo "$4.520" y no
 * "$4.520,00000", y los decimales aparecen solo donde de verdad hay algo que decir — que es
 * justamente el valor por gramo. El costo aceptado es que donde antes decía "1,50" ahora dice
 * "1,5": rellenar hasta 5 sería ruido en toda la app para ganar un cero en un caso.
 *
 * Lo que **no** cambia es la regla: lo que se muestra es exactamente lo que se guarda, así que
 * multiplicar a mano lo que se ve tiene que dar el número que la app muestra debajo.
 *
 * Ver la sección 6.1 de arquitectura.md.
 */
/**
 * Una cantidad con su unidad: "500 g", "3 unidades", "1 unidad" (14.1.1).
 *
 * **Existe porque esta frase estaba escrita seis veces** — en la línea de una receta, en la fila
 * del almacén, en el menú de `:ingredientes:`, en el resumen, en la firma y en el aviso de
 * "ya está en esta sección"—. Seis copias del mismo `if` es seis lugares donde alguien va a
 * escribir "1 unidades", y solo se nota leyéndolo.
 *
 * La concordancia del singular no es un detalle: "1 unidades" hace dudar de si el número está
 * bien, que es lo último que se quiere de una cifra que sale de una regla de negocio.
 *
 * [esObjeto] decide la unidad. En gramos no hay singular que cuidar: "1 g" se lee igual.
 */
fun cantidadConUnidad(cantidad: Double, esObjeto: Boolean): String {
    val numero = formatearNumero(cantidad)
    if (!esObjeto) return "$numero g"
    return "$numero ${if (cantidad == 1.0) "unidad" else "unidades"}"
}

/**
 * Cuántos decimales tiene una cantidad de ingrediente **después de reescalar** (8.3.1).
 *
 * Dos, y no los cinco de [MAXIMO_DECIMALES]. Lo pidió Sandy con un caso que se ve en cuanto uno
 * reescala: *"tendría de limón 0,50007 g, cuando debería ser 0,5"*. Cinco decimales son los que
 * necesita un **precio por gramo** —$0,06667 es un dato— pero en una cantidad son la basura que
 * deja una multiplicación por un factor con decimales, y nadie pesa 0,00007 g de nada.
 */
const val DECIMALES_DE_CANTIDAD = 2

private const val ESCALA_DE_CANTIDAD = 100L

/**
 * Redondea una cantidad de ingrediente a [DECIMALES_DE_CANTIDAD].
 *
 * Va **al guardar y no al mostrar**, al revés que [formatearMonto], y esa diferencia es a
 * propósito: un monto redondeado solo para la vista deja el número exacto por detrás para seguir
 * calculando, pero acá el 0,00007 no es precisión que valga la pena conservar — es el residuo de
 * una regla de tres. Guardándolo, la próxima multiplicación lo arrastra y lo agranda.
 *
 * **Nunca convierte en cero algo que no lo era.** Con 0,004 g redondear daría 0, o sea que el
 * ingrediente desaparecería de la receta por reescalar: ahí se conserva la precisión de siempre.
 * Perder un ingrediente entero es mucho peor que mostrar un decimal de más en un caso raro.
 */
fun redondearCantidad(valor: Double): Double {
    if (!valor.isFinite() || abs(valor) >= TOPE_PARA_REDONDEAR) return valor
    val redondeado = (valor * ESCALA_DE_CANTIDAD).roundToLong() / ESCALA_DE_CANTIDAD.toDouble()
    if (redondeado == 0.0 && valor != 0.0) return redondearParaGuardar(valor)
    return redondeado
}

/**
 * Lo que se aclara al pie de toda pantalla que muestre montos con [formatearMonto].
 *
 * Es la mitad obligatoria del redondeo. Un número redondeado sin avisar es un número falso; con
 * el aviso es una comodidad. Va escrito una sola vez porque son varias las pantallas que lo
 * muestran y tienen que decir exactamente lo mismo.
 */
const val AVISO_MONTOS_REDONDEADOS =
    "Los montos se muestran redondeados al peso más cercano. Las cuentas se hacen con el " +
        "valor exacto."

/**
 * Un monto de plata para mostrar: **redondeado al peso más cercano** (15.2).
 *
 * Lo pidió Sandy para las cifras de gastos y ganancias: *"visualmente debe redondearse para
 * verse como número entero… pero por detrás todo seguirá siendo calculado con exactitud"*. Y
 * eso es exactamente lo que hace — es una función de **presentación**, no toca lo guardado ni
 * lo calculado. Lo que se guarda lo decide [redondearParaGuardar], que sigue en 5 decimales.
 *
 * **No sirve para un precio por gramo**, y esa es la línea que no hay que cruzar: un
 * ingrediente a $0,06667 el gramo se convertiría en "$0" y la receta parecería gratis. Se usa
 * para totales —lo que cuesta, lo que entra, lo que se gana— y ahí los centavos no son
 * información, son ruido: nadie cobra $4.520,33333.
 *
 * Al redondear, la suma de las partes puede no dar el total que se muestra. Por eso toda
 * pantalla que lo use tiene que mostrar además [AVISO_MONTOS_REDONDEADOS]: la aproximación se
 * declara, no se descubre.
 */
fun formatearMonto(valor: Double): String {
    if (!valor.isFinite()) return formatearNumero(valor)
    // `roundToLong` sobre el valor completo y no sobre las partes: redondear el entero y el
    // decimal por separado es lo que hace `formatearNumero`, y ahí un 0,6 se iría a "0,6" en
    // vez de subir al 1.
    return formatearNumero(valor.roundToLong().toDouble())
}

fun formatearNumero(valor: Double): String {
    val redondeado = redondearParaGuardar(valor)

    // Se trabaja en positivo y el signo se pega al final. Si se usara redondeado.toLong()
    // directamente, (-0,56) daría 0 y se perdería el "-": una pérdida se vería como ganancia.
    val negativo = redondeado < 0
    val absoluto = abs(redondeado)
    var entero = absoluto.toLong()
    var decimal = ((absoluto - entero) * ESCALA_DECIMAL).roundToLong()
    // La resta de arriba puede quedar un pelo por debajo del entero siguiente y redondear
    // hasta la escala completa. Sin esto saldría "0,100000", que no es un número.
    if (decimal >= ESCALA_DECIMAL) {
        entero++
        decimal -= ESCALA_DECIMAL
    }

    // Locale.US fija "," como separador de miles para poder cambiarlo por "." de forma
    // predecible. Sin fijarlo se usaría el idioma del celular, donde el separador puede
    // ser otro (un espacio, por ejemplo) y entonces el replace no encontraría nada.
    val enteroFmt = String.format(Locale.US, "%,d", entero).replace(",", ".")
    val signo = if (negativo) "-" else ""
    // Se rellena a la izquierda para no perder los ceros que van **entre** la coma y el primer
    // dígito (0,06667), y se recorta a la derecha para no inventar los que sobran.
    val decimales = decimal.toString().padStart(MAXIMO_DECIMALES, '0').trimEnd('0')

    return if (decimales.isEmpty()) "$signo$enteroFmt" else "$signo$enteroFmt,$decimales"
}

/**
 * Pone los puntos de mil **mientras se escribe**, sin tocar lo que todavía no está escrito.
 *
 * Es la hermana de [formatearNumero], y existe porque aquella no sirve para esto. Aquella
 * trabaja sobre un número ya terminado, y aplicada tecla por tecla arruina lo que se está
 * escribiendo:
 *
 * | Escrito      | Con `formatearNumero` | Con esta      |
 * |--------------|-----------------------|---------------|
 * | `1000,`      | `1.000` (se come la coma, no se pueden escribir decimales) | `1.000,` |
 * | `1000,50`    | `1.000,5` (se come el cero que se está escribiendo) | `1.000,50` |
 * | `1,555555`   | `1,55556` (redondea antes de tiempo) | `1,55555` |
 *
 * La segunda fila cambió al pasar a 5 decimales y muestra bien por qué son dos funciones:
 * `formatearNumero` ahora **saca** los ceros de la derecha, que es lo correcto para un número
 * terminado y lo peor posible mientras se escribe — borraría el cero apenas se teclea.
 *
 * Reglas, todas al servicio de lo mismo — que lo que se ve sea lo que se escribió:
 * - Agrupa **solo la parte entera**. Lo que va después de la coma queda tal cual, con sus
 *   ceros a la derecha y con la coma sola si todavía no viene nada.
 * - Descarta los puntos que vengan: son separadores de miles, los pone esta función. Por
 *   eso aplicarla sobre su propio resultado no lo cambia.
 * - Descarta cualquier otro carácter, incluido el signo menos. Los tres campos que la usan
 *   no aceptan negativos, así que es una tecla que no tiene nada que hacer ahí.
 * - Corta en [MAXIMO_DECIMALES] decimales, que es lo que la app guarda. Dejar escribir un
 *   tercero mostraría una precisión que se va a perder igual al guardar.
 * - Saca los ceros de más a la izquierda, pero deja el "0" de "0,5".
 *
 * El resultado siempre lo entiende
 * [com.sandyyera.reposteria.logica.validaciones.textoANumero], que es lo que después lo
 * convierte a número.
 */
fun formatearMientrasSeEscribe(texto: String): String {
    val enteros = StringBuilder()
    val decimales = StringBuilder()
    var hayComa = false

    for (caracter in texto) {
        when {
            caracter == ',' && !hayComa -> hayComa = true
            !caracter.isDigit() -> Unit
            !hayComa -> enteros.append(caracter)
            decimales.length < MAXIMO_DECIMALES -> decimales.append(caracter)
        }
    }

    val sinCerosSobrantes = enteros.toString().trimStart('0')
    val parteEntera = when {
        sinCerosSobrantes.isNotEmpty() -> sinCerosSobrantes
        // Se escribieron solo ceros, o se empezó por la coma: hace falta un 0 adelante.
        enteros.isNotEmpty() || hayComa -> "0"
        // No se escribió nada todavía; el campo queda vacío y no con un 0 puesto solo.
        else -> ""
    }

    val agrupada = agruparDeTresEnTres(parteEntera)
    return if (hayComa) "$agrupada,$decimales" else agrupada
}

/**
 * Un texto y dónde quedó el cursor dentro de él.
 *
 * Existe porque al agregar un punto de mil el texto se alarga, y si el cursor se queda en
 * el mismo número de posición deja de estar donde la persona lo dejó: escribiendo "1234",
 * el texto pasa a "1.234" —cinco caracteres— y la posición 4, que era el final, ahora cae
 * entre el "3" y el "4". Lo que se escriba después entra en el medio del número.
 */
data class TextoConCursor(val texto: String, val cursor: Int)

/**
 * Formatea lo escrito **y dice dónde queda el cursor**, que es lo que hay que usar desde
 * un campo de texto.
 *
 * La posición no se puede conservar como número, porque el texto cambia de largo. Lo que
 * se conserva es **cuántos caracteres que la persona escribió** hay antes del cursor —los
 * dígitos y la coma—, sin contar los puntos, que los pone la app. Después se busca esa
 * misma cantidad en el texto ya formateado.
 *
 * Escribiendo "1234" con el cursor al final: hay 4 caracteres escritos antes del cursor;
 * en "1.234" el cuarto dígito termina en la posición 5, y ahí va el cursor. Al final, como
 * corresponde.
 *
 * Si el texto se acorta (al sacar ceros de más), el cursor queda al final en vez de en una
 * posición que ya no existe.
 */
fun formatearMientrasSeEscribe(texto: String, cursor: Int): TextoConCursor {
    val formateado = formatearMientrasSeEscribe(texto)

    val hastaElCursor = texto.take(cursor.coerceIn(0, texto.length))
    val escritosAntes = hastaElCursor.count(::loEscribioLaPersona)
    if (escritosAntes == 0) return TextoConCursor(formateado, 0)

    var contados = 0
    for (posicion in formateado.indices) {
        if (!loEscribioLaPersona(formateado[posicion])) continue
        contados++
        // El cursor va justo después del último carácter escrito que quedaba antes.
        if (contados == escritosAntes) return TextoConCursor(formateado, posicion + 1)
    }
    return TextoConCursor(formateado, formateado.length)
}

/** Los caracteres que salen del teclado. El punto no: ese lo pone la app. */
private fun loEscribioLaPersona(caracter: Char): Boolean = caracter.isDigit() || caracter == ','

/** Mete un punto cada tres dígitos, contando desde la derecha: "1000" -> "1.000". */
private fun agruparDeTresEnTres(digitos: String): String {
    if (digitos.length <= 3) return digitos
    val resultado = StringBuilder()
    for ((posicion, digito) in digitos.withIndex()) {
        // Cuántos dígitos quedan a la derecha de este. Se separa cada vez que es múltiplo
        // de 3, salvo al final del todo.
        val faltan = digitos.length - posicion
        resultado.append(digito)
        if (faltan > 1 && (faltan - 1) % 3 == 0) resultado.append('.')
    }
    return resultado.toString()
}
