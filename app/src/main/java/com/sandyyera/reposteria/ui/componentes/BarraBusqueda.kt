package com.sandyyera.reposteria.ui.componentes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/**
 * El campo de búsqueda que usan las cuatro secciones.
 *
 * Es un solo Composable reutilizado, y no uno por pantalla, para que buscar se sienta
 * igual en todas partes. La regla de qué cuenta como coincidencia vive aparte, en
 * `logica/busqueda`, y filtra ignorando mayúsculas y tildes.
 *
 * Lleva una X para limpiar que aparece solo cuando hay algo escrito: borrar letra por
 * letra en el celular es molesto, y dejar el botón siempre visible sería un objeto
 * inútil ocupando lugar la mayor parte del tiempo.
 */
@Composable
fun BarraBusqueda(
    texto: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    marcador: String = "Buscar"
) {
    OutlinedTextField(
        value = texto,
        onValueChange = alCambiar,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(marcador) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (texto.isNotEmpty()) {
                IconButton(onClick = { alCambiar("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Limpiar la búsqueda")
                }
            }
        },
        singleLine = true,
        // Buscar no cierra nada ni pasa a otro campo: el filtro se aplica mientras se
        // escribe, así que la tecla de acción solo baja el teclado.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    )
}

@Preview(showBackground = true)
@Composable
private fun BarraBusquedaVacia() {
    ReposteriaTheme { BarraBusqueda(texto = "", alCambiar = {}) }
}

@Preview(showBackground = true)
@Composable
private fun BarraBusquedaConTexto() {
    ReposteriaTheme { BarraBusqueda(texto = "azúcar", alCambiar = {}) }
}
