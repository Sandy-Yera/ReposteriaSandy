package com.sandyyera.reposteria.logica.validaciones

import kotlin.math.floor

/**
 * Las reglas que decide si un dato sirve **antes** de guardarlo.
 *
 * Todas devuelven el motivo por el que el dato no sirve, o `null` si está bien. Ese
 * formato encaja directo con los campos de texto de Compose, que muestran el mensaje
 * bajo el campo cuando hay algo que corregir.
 *
 * Viven acá y no en la pantalla para que la regla sea la misma sin importar desde dónde
 * se guarde: el catálogo de ingredientes y el alta rápida desde una receta son dos
 * pantallas distintas que crean lo mismo.
 *
 * Ojo con la diferencia entre estas y las comprobaciones que hacen las fórmulas
 * (`factorEscala`, `DatosCalculoReceta`…): aquellas lanzan excepción porque son la última
 * red de seguridad ante algo que nunca debió llegar hasta ahí. Estas no lanzan nada:
 * describen un dato que la persona todavía está escribiendo y puede corregir.
 */

/**
 * Cuántos caracteres puede tener cualquier nombre escrito a mano: ingredientes, títulos
 * de receta, nombres de sección, moldes, empleados.
 *
 * Es uno solo para todos a propósito. Un tope distinto por cada cosa no aporta nada —
 * ninguno de esos nombres tiene una razón para ser más largo que otro— y sí obliga a
 * recordar cuál era cuál.
 */
const val LARGO_MAXIMO_NOMBRE = 60

/**
 * Cuánto puede medir una nota corta, de las que se leen de un vistazo (9.6).
 *
 * 140 y no "lo que sea": lo que se pidió fue *"un mensaje corto que sea visible"*, y visible es lo
 * contrario de largo — una nota de tres párrafos hay que abrirla para leerla, que es exactamente
 * lo que este campo evita. Da para "es redondo, 22 cm de diámetro" y para un par de detalles más,
 * y no para una receta escondida.
 */
const val LARGO_MAXIMO_NOTA = 140

/**
 * Revisa cualquier nombre escrito a mano: un ingrediente, un molde, un empleado.
 *
 * Se llamaba `errorEnNombreIngrediente`, pero su cuerpo nunca tuvo nada de ingredientes y
 * el nombre hacía que cada sección nueva escribiera su propia copia idéntica. Los mensajes
 * ya hablan del "nombre" y no de qué cosa se está nombrando, así que sirven igual en las
 * cuatro secciones. Las excepciones son las que **sí** dicen otra palabra: el título de una
 * receta y el nombre de una sección viven en `Recetas.kt` con su propio texto.
 *
 * No comprueba si ya existe otro igual: eso necesita mirar la base de datos y lo resuelve
 * el repositorio, que compara ignorando tildes.
 */
fun errorEnNombreEscrito(nombre: String): String? {
    val limpio = nombre.trim()
    return when {
        limpio.isEmpty() -> "El nombre no puede quedar vacío"
        limpio.length > LARGO_MAXIMO_NOMBRE ->
            "El nombre no puede pasar de $LARGO_MAXIMO_NOMBRE caracteres"
        else -> null
    }
}

/**
 * Revisa un número que tiene que ser mayor que cero, tal como está escrito en el campo.
 *
 * Es la misma comprobación que ya estaba escrita tres veces —la cantidad del paquete en la
 * calculadora, los gramos de un ingrediente en una receta y ahora las medidas de un
 * molde—, palabra por palabra salvo el aviso de campo vacío, que es el único que sí cambia
 * según lo que se esté pidiendo. Por eso ese texto entra por parámetro y el resto vive acá
 * una sola vez: si mañana hay que aceptar otro separador, se arregla en un lugar.
 *
 * El cero se rechaza siempre. Donde el cero es un dato válido —el valor por gramo de un
 * ingrediente regalado, las unidades por día de una receta que no se vende— no se usa esta
 * función, y esa diferencia es deliberada, no un descuido.
 */
fun errorEnNumeroPositivoTexto(texto: String, siEstaVacio: String): String? {
    if (texto.isBlank()) return siEstaVacio
    val numero = textoANumero(texto) ?: return "Escribe un número válido"
    return when {
        numero.isNaN() || numero.isInfinite() -> "Escribe un número válido"
        numero <= 0 -> "La cantidad tiene que ser mayor que cero"
        else -> null
    }
}

/**
 * Revisa el valor por gramo de un ingrediente.
 *
 * Se permite 0 a propósito: hay ingredientes que no se costean, y ponerlos en cero es la
 * forma de decir "esto no suma al costo".
 */
fun errorEnValorPorGramo(valor: Double): String? = when {
    valor.isNaN() || valor.isInfinite() -> "Escribe un número válido"
    valor < 0 -> "El valor no puede ser negativo"
    else -> null
}

/**
 * Convierte a número lo que la persona escribió, aceptando la coma como separador decimal.
 *
 * En el teclado del celular se escribe "1,55" y no "1.55", que es lo que espera Kotlin.
 * Devuelve `null` si el texto no es un número.
 */
fun textoANumero(texto: String): Double? =
    texto.trim().replace(".", "").replace(',', '.').toDoubleOrNull()

/**
 * Si un número escrito no tiene decimales.
 *
 * Existe por un error concreto que estaba repetido en cuatro campos: la forma "obvia" de
 * preguntarlo es `numero != numero.toInt().toDouble()`, y **está mal para números grandes**.
 * `toInt()` no desborda sino que se pega al tope de `Int`, así que un `10000000000` —que es
 * entero— se convierte en `2147483647`, deja de coincidir consigo mismo y el campo responde
 * "tiene que ser un número entero" sobre un número entero. El aviso queda mintiendo, y el
 * tope de verdad (que sí tenía algo que decir) nunca llega a revisarse.
 *
 * Con `floor` no hay tope que cruzar: un `Double` grande es igual a su propia parte entera y
 * la revisión sigue de largo hasta el aviso que corresponde.
 */
fun esNumeroEntero(numero: Double): Boolean = numero == floor(numero)

/**
 * Revisa el valor por gramo **tal como está escrito en el campo**, no ya convertido.
 *
 * Es el puente entre [textoANumero] y [errorEnValorPorGramo]: un campo de texto puede
 * tener tres problemas distintos —estar vacío, no ser un número, o ser un número que no
 * sirve— y cada uno necesita su propio mensaje. Sin esta función, las dos pantallas que
 * dan de alta un ingrediente (el catálogo y el alta rápida desde una receta) tendrían que
 * repetir ese encadenado, y bastaría con que una escribiera el mensaje distinto para que
 * la app se sintiera inconsistente.
 *
 * El campo vacío se rechaza aunque [errorEnValorPorGramo] acepte el 0: dejarlo en blanco
 * casi siempre es un olvido, mientras que escribir 0 es una decisión. Tomarlos por lo
 * mismo guardaría en silencio un ingrediente sin costo que después descuadra una receta.
 */
fun errorEnValorPorGramoTexto(texto: String): String? {
    if (texto.isBlank()) return "Escribe cuánto vale el gramo"
    val numero = textoANumero(texto) ?: return "Escribe un número válido"
    return errorEnValorPorGramo(numero)
}

/**
 * Los problemas de un formulario de ingrediente, uno por campo.
 *
 * Va por campo y no como un solo mensaje porque la pantalla tiene que poder mostrar cada
 * aviso **bajo el campo que lo causó**. Un único texto de error obligaría a adivinar a
 * cuál de los dos se refiere.
 */
data class ErroresIngrediente(
    val nombre: String?,
    val valorPorGramo: String?
) {
    /** `true` cuando no hay nada que corregir y el formulario se puede guardar. */
    val sirve: Boolean get() = nombre == null && valorPorGramo == null
}

/**
 * Revisa de una vez los dos campos del formulario de un ingrediente.
 *
 * Se llama en cada tecla mientras se escribe, para poder habilitar o deshabilitar el botón
 * de guardar y mostrar el motivo al momento. **No reemplaza la validación del repositorio**:
 * aquella es la que decide de verdad si se guarda, y además comprueba lo único que acá no
 * se puede saber —si ya existe otro ingrediente con ese nombre—, porque eso necesita mirar
 * la base de datos.
 */
fun revisarIngrediente(nombre: String, valorPorGramoTexto: String): ErroresIngrediente =
    ErroresIngrediente(
        nombre = errorEnNombreEscrito(nombre),
        valorPorGramo = errorEnValorPorGramoTexto(valorPorGramoTexto)
    )
