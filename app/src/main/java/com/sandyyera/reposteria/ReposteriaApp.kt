package com.sandyyera.reposteria

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Punto de entrada de la app.
 *
 * Su única tarea es crear el [AppContainer], que es donde viven la base de datos y los
 * repositorios. Las pantallas llegan a él desde el contexto de la aplicación.
 */
class ReposteriaApp : Application() {

    val contenedor: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // **Abrir la base se dispara acá y en un hilo de fondo**, para que no le toque al
        // principal justo antes del primer cuadro (ver `AppContainer.precalentar`). No se
        // espera a que termine: la pantalla arranca igual mostrando su estado de carga, que
        // es el que ya tenía.
        //
        // El alcance vive lo que vive la app y no se cancela nunca, que es correcto para
        // exactamente una tarea que dura milisegundos; no es un lugar para poner trabajo
        // nuevo. `SupervisorJob` para que un fallo acá no arrastre nada más.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            contenedor.precalentar()
        }
    }
}
