package com.sandyyera.reposteria.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
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

        setContent {
            ReposteriaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavegacionPrincipal(contenedor = contenedor)
                }
            }
        }
    }
}
