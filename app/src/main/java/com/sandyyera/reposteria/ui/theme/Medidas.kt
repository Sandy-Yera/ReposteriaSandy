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

    /**
     * Hasta dónde puede crecer una lista que va **dentro** de otra cosa: 200dp.
     *
     * Pasado ese alto se desplaza sola por dentro, en vez de empujar lo que tiene debajo.
     * Sin un tope, la advertencia de borrar un ingrediente usado en veinte recetas
     * crecería hasta dejar los botones de Cancelar y Eliminar fuera de la pantalla.
     */
    val altoMaximoDeLista = 200.dp

    /**
     * Alto **mínimo** del cuerpo de un cuadro con algo debajo que hay que notar: 280dp.
     *
     * Lo pidió Sandy con el cuadro de ingrediente nuevo, donde el botón de *"no sé el valor por
     * gramo, sé lo que pagué"* quedaba tan al fondo que casi no lo había visto. La solución no
     * fue mover el aviso —arriba compite con los campos, que es lo que uno vino a llenar— sino
     * **alargar un poco el cuadro**: lo justo para que asome y se entienda que hay algo más
     * abajo. *"No debe crecer tanto, solo un poco para lograr notarlo más y poder bajar a
     * leerlo."*
     */
    val altoMinimoDeCuadroConAviso = 280.dp

    /**
     * Hasta dónde crece el cuerpo de un **formulario** dentro de un cuadro: 380dp.
     *
     * Es distinto de [altoMaximoDeLista] y por eso no se reutiliza, aunque tenga el mismo aire:
     * aquel tope existe para que una lista larga no empuje los botones fuera de la pantalla, y un
     * formulario tiene un alto conocido y corto. Usar los 200dp de la lista era justamente lo que
     * dejaba el cuadro del ingrediente nuevo demasiado bajo — un tope pensado para otra cosa,
     * aplicado por estar a mano.
     */
    val altoMaximoDeFormulario = 380.dp
}
