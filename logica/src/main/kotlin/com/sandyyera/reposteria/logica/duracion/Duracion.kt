package com.sandyyera.reposteria.logica.duracion

/**
 * Los tipos y las unidades de duración de un producto (8.4).
 *
 * Viven en `:logica` y no junto a la tabla, por lo mismo que `ModoPrecio` y
 * `TipoFormaMolde`: las validaciones y el texto que se muestra son lógica pura y se prueban
 * sin base de datos. La entidad de Room los importa desde acá.
 */

/** Dónde se guarda el producto. Cada receta tiene una fila por cada uno. */
enum class TipoDuracion { AMBIENTE, REFRIGERADA, CONGELADA }

/**
 * En qué unidad se mide cuánto dura.
 *
 * Cuatro escalas y no una sola en horas: "3 meses" y "2.160 horas" son el mismo número pero
 * solo uno se lee. Van de menor a mayor a propósito — el orden del enum es el orden en que
 * se muestran.
 */
enum class UnidadDuracion { HORAS, DIAS, SEMANAS, MESES }

/** Cómo se lee un tipo de guardado en la pantalla. */
fun nombreDelTipoDeDuracion(tipo: TipoDuracion): String = when (tipo) {
    TipoDuracion.AMBIENTE -> "A temperatura ambiente"
    TipoDuracion.REFRIGERADA -> "Refrigerada"
    TipoDuracion.CONGELADA -> "Congelada"
}

/**
 * Cómo se lee una unidad, en singular o plural según la cantidad.
 *
 * "1 días" es el detalle que hace que una app se sienta descuidada, y arreglarlo en cada
 * pantalla que lo muestre es cómo se termina con tres reglas distintas.
 */
fun nombreDeLaUnidad(unidad: UnidadDuracion, cantidad: Int): String {
    val singular = cantidad == 1
    return when (unidad) {
        UnidadDuracion.HORAS -> if (singular) "hora" else "horas"
        UnidadDuracion.DIAS -> if (singular) "día" else "días"
        UnidadDuracion.SEMANAS -> if (singular) "semana" else "semanas"
        UnidadDuracion.MESES -> if (singular) "mes" else "meses"
    }
}

/**
 * El aviso fijo del paso, que va siempre a la vista (8.4).
 *
 * Vive acá junto al resto del texto que se muestra, y no entre las validaciones: no valida
 * nada, es lo que hay que tener en la cabeza mientras se escriben los números.
 */
const val AVISO_DURACIONES_ESTIMADAS = "Las duraciones son estimaciones no precisas"

/** Lo que se muestra cuando un bloque quedó sin llenar. */
const val DURACION_SIN_DATO = "Sin anotar"

/** Lo que se muestra cuando el producto no se puede guardar así. */
const val DURACION_NO_APTA = "No apto"

/**
 * El texto de un bloque de duración, listo para mostrar.
 *
 * Los tres estados posibles son distintos y **ninguno es un número**: no apto, sin anotar, o
 * una cantidad con su unidad. Devolver `Int?` obligaría a cada pantalla a decidir cómo se
 * lee cada caso, y ahí es donde aparecen los "0 días" y los "1 días".
 */
fun describirDuracion(apto: Boolean, cantidad: Int?, unidad: UnidadDuracion?): String = when {
    !apto -> DURACION_NO_APTA
    cantidad == null || unidad == null -> DURACION_SIN_DATO
    else -> "$cantidad ${nombreDeLaUnidad(unidad, cantidad)}"
}
