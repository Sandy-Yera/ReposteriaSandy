package com.sandyyera.reposteria.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sandyyera.reposteria.ReposteriaApp
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/**
 * El punto de entrada de la interfaz.
 *
 * Su única tarea es armar el tema y entregarle el contenedor a [NavegacionPrincipal], que
 * es quien decide qué sección se ve. La Activity no conoce ninguna pantalla en particular:
 * al sumar Moldes y Empleados no hay que tocarla.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contenedor = (application as ReposteriaApp).contenedor

        // **Se declara el borde a borde en vez de sufrirlo.** Desde Android 15, una app con
        // `targetSdk = 35` va borde a borde quiera o no, y ahí `adjustResize` deja de tener
        // efecto: la ventana **ya no se achica** al abrirse el teclado. Eso es lo que Sandy vio —
        // la lista de pasos se desplazaba hasta un fondo que estaba debajo del teclado, así que
        // el último paso no había forma de verlo.
        //
        // Declararlo acá lo vuelve igual en todas las versiones de Android en vez de depender de
        // cuál tenga el celular: la ventana nunca se achica, el teclado llega como un "inset", y
        // el espacio lo hace `imePadding` de abajo. Es una regla sola en lugar de dos que se
        // contradicen según el aparato.
        enableEdgeToEdge()

        setContent {
            ReposteriaTheme {
                Surface(
                    // El color pinta **toda** la ventana, también detrás de las barras del
                    // sistema. El hueco de las barras lo respetan las pantallas por su cuenta
                    // (`Scaffold` y `TopAppBar` ya lo hacen), así que acá solo hay que asegurarse
                    // de que el fondo llegue hasta el canto: sin esto quedaría una franja del
                    // color del tema de Android, que en modo oscuro se ve blanca.
                    modifier = Modifier
                        .fillMaxSize()
                        // **El único lugar donde se descuenta el teclado.** Puesto en la raíz, todas
                        // las secciones se achican igual y ninguna tiene que acordarse: es lo que
                        // hacía `adjustResize` antes de que Android 15 lo ignorara.
                        .imePadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavegacionPrincipal(contenedor = contenedor)
                }
            }
        }
    }
}
