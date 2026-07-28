package com.sandyyera.reposteria

import android.app.Application
import com.sandyyera.reposteria.data.db.AppDatabase

/**
 * Punto de entrada de la app.
 *
 * Arma acá las piezas compartidas, en vez de sumar una librería de inyección de
 * dependencias: siendo un proyecto de una sola persona, esto alcanza y se entiende
 * leyéndolo. Si el proyecto crece, se puede migrar a Hilt sin rehacer nada.
 */
class ReposteriaApp : Application() {
    val base: AppDatabase by lazy { AppDatabase.obtener(this) }
}
