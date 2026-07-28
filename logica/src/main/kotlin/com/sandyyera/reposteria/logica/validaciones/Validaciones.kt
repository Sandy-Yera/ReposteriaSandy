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
