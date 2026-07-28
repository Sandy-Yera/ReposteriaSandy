package com.sandyyera.reposteria.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandyyera.reposteria.ReposteriaApp
import com.sandyyera.reposteria.ui.ingredientes.IngredientesViewModel
import com.sandyyera.reposteria.ui.ingredientes.ListaIngredientesScreen
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/**
 * La única pantalla de la app por ahora: el catálogo de ingredientes.
 *
 * Todavía no hay navegación ni menú de 3 líneas (12.1), porque no hay una segunda sección
 * a la que ir. Se agregan al llegar la lista de recetas, y esta pantalla pasa a ser una de
 * las cuatro sin tener que reescribirla: ya recibe todo lo que necesita desde afuera.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // El repositorio se toma del contenedor de la aplicación, que es donde vive la
        // base de datos. La Activity no la abre ni la conoce: solo pasa la pieza que hace
        // falta, y por eso girar el teléfono no vuelve a abrir nada.
        val repositorioIngredientes =
            (application as ReposteriaApp).contenedor.ingredientes

        setContent {
            ReposteriaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val modelo: IngredientesViewModel = viewModel(
                        factory = IngredientesViewModel.fabrica(repositorioIngredientes)
                    )
                    ListaIngredientesScreen(modelo = modelo)
                }
            }
        }
    }
}
