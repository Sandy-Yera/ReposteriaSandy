package com.sandyyera.reposteria.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Las medidas de espaciado y tamaño de la app.
 *
 * Están acá para que ninguna pantalla invente sus propios números: si cada una elige
 * cuánto separar las cosas, la app termina desalineada sin que nadie lo decida.
 *
 * Todos son múltiplos de 8, salvo [Medidas.minimo] para ajustes finos.
 */
object Medidas {
    /** Separación mínima, para detalles: 4dp. */
    val minimo = 4.dp

    /** Entre elementos muy relacionados, como un título y su subtítulo: 8dp. */
    val chico = 8.dp

    /** Dentro de una tarjeta, entre sus partes: 16dp. */
    val medio = 16.dp

    /** Márgenes de pantalla y separación entre tarjetas: 24dp. */
    val grande = 24.dp

    /**
     * Alto mínimo de cualquier cosa que se toque: 48dp.
     *
     * Es la recomendación de accesibilidad de Android, y acá importa el doble: la app se
     * usa apurada y a veces con las manos sucias, donde los botones chicos se fallan.
     */
    val objetivoTactil = 48.dp
}
