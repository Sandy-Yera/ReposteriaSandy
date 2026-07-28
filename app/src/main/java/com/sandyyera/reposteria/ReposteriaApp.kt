package com.sandyyera.reposteria

import android.app.Application

/**
 * Punto de entrada de la app.
 *
 * Su única tarea es crear el [AppContainer], que es donde viven la base de datos y los
 * repositorios. Las pantallas llegan a él desde el contexto de la aplicación.
 */
class ReposteriaApp : Application() {

    val contenedor: AppContainer by lazy { AppContainer(this) }
}
