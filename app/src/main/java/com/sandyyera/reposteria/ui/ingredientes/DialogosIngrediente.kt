package com.sandyyera.reposteria.ui.ingredientes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.logica.validaciones.LARGO_MAXIMO_NOMBRE
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/**
 * El formulario de alta y de edición de un ingrediente.
 *
 * Es **uno solo** para los dos casos porque pide exactamente los mismos dos datos; tener
 * dos pantallas casi idénticas es la forma más común de que una corrección se aplique a
 * una y no a la otra. Lo único que cambia es el título y qué se hace al confirmar, y eso
 * lo decide el ViewModel.
 *
 * No valida nada por su cuenta: recibe los errores ya calculados en
 * [DialogoIngrediente.Formulario], que a su vez los saca de `logica/validaciones`. Así el
 * aviso dice lo mismo sin importar desde dónde se cree un ingrediente.
 */
@Composable
fun FormularioIngrediente(
    estado: DialogoIngrediente.Formulario,
    alCambiarNombre: (String) -> Unit,
    alCambiarValor: (String) -> Unit,
    alGuardar: () -> Unit,
    alCerrar: () -> Unit
) {
    val editando = estado.editando != null
    val errorNombre = estado.errorNombreVisible
    val errorValor = estado.errorValorVisible

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(if (editando) "Editar ingrediente" else "Nuevo ingrediente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                OutlinedTextField(
                    value = estado.nombre,
                    onValueChange = alCambiarNombre,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    singleLine = true,
                    isError = errorNombre != null,
                    // El hueco del mensaje se reserva siempre, con error o sin él: si
                    // apareciera y desapareciera, el cuadro daría un salto en la pantalla
                    // justo mientras se escribe.
                    supportingText = { if (errorNombre != null) Text(errorNombre) },
                    keyboardOptions = KeyboardOptions(
                        // Los ingredientes se escriben con mayúscula inicial ("Azúcar flor"),
                        // y el teclado la pone solo en vez de obligar a la tecla de mayúscula.
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )

                OutlinedTextField(
                    value = estado.valorPorGramo,
                    onValueChange = alCambiarValor,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Valor por gramo (en $)") },
                    singleLine = true,
                    isError = errorValor != null,
                    supportingText = {
                        // Sin error se explica el formato: es la primera vez que alguien ve
                        // este campo y no tiene por qué adivinar si va con coma o con punto.
                        Text(errorValor ?: "Se escribe con coma: 1,55")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = alGuardar, enabled = estado.puedeGuardar) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) { Text("Cancelar") }
        }
    )
}

/**
 * La advertencia obligatoria antes de borrar un ingrediente (política de 7.1).
 *
 * No ofrece el botón de eliminar hasta saber a qué recetas afecta: mientras
 * [DialogoIngrediente.ConfirmarBorrado.recetasAfectadas] sea `null` la consulta sigue en
 * curso y confirmar no significaría nada, porque la advertencia todavía no se mostró
 * completa. Por eso el botón arranca deshabilitado en vez de asumir que no afecta a nadie.
 */
@Composable
fun ConfirmarBorradoIngrediente(
    estado: DialogoIngrediente.ConfirmarBorrado,
    alConfirmar: () -> Unit,
    alCerrar: () -> Unit
) {
    val afectadas = estado.recetasAfectadas

    AlertDialog(
        onDismissRequest = alCerrar,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("¿Eliminar '${estado.ingrediente.nombre}'?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                when {
                    afectadas == null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Medidas.chico)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(Medidas.grande))
                        Text("Revisando qué recetas lo usan…")
                    }

                    afectadas.isEmpty() -> Text("No lo usa ninguna receta.")

                    else -> {
                        Text(
                            if (afectadas.size == 1) {
                                "Lo usa 1 receta. Se va a quitar de ella y su costo va a cambiar:"
                            } else {
                                "Lo usan ${afectadas.size} recetas. Se va a quitar de todas " +
                                    "y sus costos van a cambiar:"
                            }
                        )
                        Column(
                            modifier = Modifier
                                .heightIn(max = Medidas.altoMaximoDeLista)
                                .verticalScroll(rememberScrollState())
                        ) {
                            afectadas.forEach { receta ->
                                Text(
                                    text = "• ${receta.titulo}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                if (afectadas != null) {
                    Text(
                        text = "Esto no se puede deshacer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = alConfirmar,
                // Sin la lista consultada no hay advertencia que confirmar.
                enabled = afectadas != null && !estado.borrando
            ) {
                Text(text = "Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) { Text("Cancelar") }
        }
    )
}

// --- Vistas previas ---
// Cubren lo que cuesta reproducir a mano en el celular: el nombre repetido, los campos con
// error, y la advertencia de borrado en sus tres estados.

private val harina = Ingrediente(id = 1, nombre = "Harina", valorPorGramo = 1.2)

@Preview(showBackground = true)
@Composable
private fun FormularioNuevoVacio() {
    ReposteriaTheme {
        FormularioIngrediente(
            estado = DialogoIngrediente.Formulario(),
            alCambiarNombre = {}, alCambiarValor = {}, alGuardar = {}, alCerrar = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FormularioConErrores() {
    ReposteriaTheme {
        FormularioIngrediente(
            estado = DialogoIngrediente.Formulario(
                nombre = "a".repeat(LARGO_MAXIMO_NOMBRE + 1),
                valorPorGramo = "abc",
                tocadoNombre = true,
                tocadoValor = true
            ),
            alCambiarNombre = {}, alCambiarValor = {}, alGuardar = {}, alCerrar = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FormularioConNombreRepetido() {
    ReposteriaTheme {
        FormularioIngrediente(
            estado = DialogoIngrediente.Formulario(
                nombre = "azucar",
                valorPorGramo = "1,55",
                tocadoNombre = true,
                tocadoValor = true,
                nombreRepetido = "Ya tienes un ingrediente que se llama 'Azúcar'"
            ),
            alCambiarNombre = {}, alCambiarValor = {}, alGuardar = {}, alCerrar = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FormularioEditando() {
    ReposteriaTheme {
        FormularioIngrediente(
            estado = DialogoIngrediente.Formulario(
                editando = harina,
                nombre = harina.nombre,
                valorPorGramo = "1,2",
                tocadoNombre = true,
                tocadoValor = true
            ),
            alCambiarNombre = {}, alCambiarValor = {}, alGuardar = {}, alCerrar = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BorradoConsultando() {
    ReposteriaTheme {
        ConfirmarBorradoIngrediente(
            estado = DialogoIngrediente.ConfirmarBorrado(ingrediente = harina),
            alConfirmar = {}, alCerrar = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BorradoSinRecetas() {
    ReposteriaTheme {
        ConfirmarBorradoIngrediente(
            estado = DialogoIngrediente.ConfirmarBorrado(
                ingrediente = harina,
                recetasAfectadas = emptyList()
            ),
            alConfirmar = {}, alCerrar = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BorradoConRecetas() {
    ReposteriaTheme {
        ConfirmarBorradoIngrediente(
            estado = DialogoIngrediente.ConfirmarBorrado(
                ingrediente = harina,
                recetasAfectadas = listOf(
                    Receta(id = 1, titulo = "Torta de manjar"),
                    Receta(id = 2, titulo = "Bizcocho de vainilla"),
                    Receta(id = 3, titulo = "Kuchen de nuez")
                )
            ),
            alConfirmar = {}, alCerrar = {}
        )
    }
}
