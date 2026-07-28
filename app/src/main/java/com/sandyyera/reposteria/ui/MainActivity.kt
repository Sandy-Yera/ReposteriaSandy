package com.sandyyera.reposteria.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/**
 * Pantalla provisional, solo para confirmar que el proyecto compila, arranca y aplica
 * el tema. Se reemplaza por la navegación real en el siguiente paso.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReposteriaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PantallaProvisional()
                }
            }
        }
    }
}

@Composable
private fun PantallaProvisional() {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = "Repostería",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Base de datos lista. Las pantallas vienen en el siguiente paso.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PantallaProvisionalClaro() {
    ReposteriaTheme(oscuro = false) { PantallaProvisional() }
}

@Preview(showBackground = true)
@Composable
private fun PantallaProvisionalOscuro() {
    ReposteriaTheme(oscuro = true) { PantallaProvisional() }
}
