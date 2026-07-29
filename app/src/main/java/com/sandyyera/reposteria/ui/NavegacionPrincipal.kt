package com.sandyyera.reposteria.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandyyera.reposteria.AppContainer
import com.sandyyera.reposteria.ui.ingredientes.IngredientesViewModel
import com.sandyyera.reposteria.ui.ingredientes.ListaIngredientesScreen
import com.sandyyera.reposteria.ui.recetas.ListaRecetasScreen
import com.sandyyera.reposteria.ui.recetas.RecetasViewModel
import com.sandyyera.reposteria.ui.theme.Medidas
import kotlinx.coroutines.launch

/**
 * Las secciones de la app (12.1).
 *
 * Moldes y Empleados se agregan al llegar sus fases. Están fuera de este enum a propósito
 * y no puestas en gris: una opción que no lleva a ninguna parte se toca igual, y da la
 * impresión de que algo se rompió.
 */
enum class Seccion(val titulo: String, val icono: ImageVector) {
    INGREDIENTES("Ingredientes", Icons.Default.ShoppingCart),
    RECETAS("Recetas", Icons.Default.Favorite)
}

/**
 * El menú de 3 líneas y la sección que se esté viendo.
 *
 * Cada sección tiene su propio ViewModel, y los dos siguen vivos al cambiar de una a otra:
 * ir a Recetas y volver a Ingredientes no borra lo que había escrito en el buscador. Eso
 * sale gratis de que `viewModel()` los guarde en la Activity y no en el Composable.
 *
 * La sección elegida va en `rememberSaveable` para que girar el teléfono no devuelva a
 * Ingredientes.
 */
@Composable
fun NavegacionPrincipal(
    contenedor: AppContainer,
    modifier: Modifier = Modifier
) {
    val estadoDelMenu = rememberDrawerState(DrawerValue.Closed)
    val alcance = rememberCoroutineScope()
    var seccionActual by rememberSaveable { mutableStateOf(Seccion.INGREDIENTES) }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = estadoDelMenu,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Repostería",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(Medidas.grande)
                )
                HorizontalDivider()

                Seccion.entries.forEach { seccion ->
                    NavigationDrawerItem(
                        label = { Text(seccion.titulo) },
                        icon = { Icon(seccion.icono, contentDescription = null) },
                        selected = seccion == seccionActual,
                        onClick = {
                            seccionActual = seccion
                            alcance.launch { estadoDelMenu.close() }
                        },
                        modifier = Modifier.padding(
                            horizontal = Medidas.chico,
                            vertical = Medidas.minimo
                        )
                    )
                }
            }
        }
    ) {
        val abrirMenu: () -> Unit = { alcance.launch { estadoDelMenu.open() } }

        when (seccionActual) {
            Seccion.INGREDIENTES -> ListaIngredientesScreen(
                modelo = viewModel(
                    factory = IngredientesViewModel.fabrica(contenedor.ingredientes)
                ),
                alAbrirMenu = abrirMenu
            )

            Seccion.RECETAS -> ListaRecetasScreen(
                modelo = viewModel(factory = RecetasViewModel.fabrica(contenedor.recetas)),
                alAbrirMenu = abrirMenu
            )
        }
    }
}
