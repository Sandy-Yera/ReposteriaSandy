package com.sandyyera.reposteria.logica.validaciones

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

/** Cuántos caracteres puede tener el nombre de un ingrediente. */
const val LARGO_MAXIMO_NOMBRE = 60

/**
 * Revisa el nombre de un ingrediente.
 *
 * No comprueba si ya existe otro igual: eso necesita mirar la base de datos y lo resuelve
 * el repositorio, que compara ignorando tildes.
 */
fun errorEnNombreIngrediente(nombre: String): String? {
    val limpio = nombre.trim()
    return when {
        limpio.isEmpty() -> "El nombre no puede quedar vacío"
        limpio.length > LARGO_MAXIMO_NOMBRE ->
            "El nombre no puede pasar de $LARGO_MAXIMO_NOMBRE caracteres"
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
        nombre = errorEnNombreIngrediente(nombre),
        valorPorGramo = errorEnValorPorGramoTexto(valorPorGramoTexto)
    )
