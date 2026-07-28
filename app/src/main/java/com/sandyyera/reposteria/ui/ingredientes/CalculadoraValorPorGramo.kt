package com.sandyyera.reposteria.ui.ingredientes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.logica.calculadora.UnidadDeCompra
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.ui.componentes.BarraBusqueda
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/**
 * La calculadora de valor por gramo (sección 7.2 de arquitectura.md).
 *
 * Resuelve la cuenta que hay que rehacer cada vez que sube un precio: se compró un paquete
 * por tanto y trae tanto, y lo que la app necesita es cuánto cuesta un gramo. Hacerla
 * aparte, en la calculadora del celular, es donde se cuela el error caro — un cero de más
 * o de menos al pasar de kilos a gramos deja un ingrediente mil veces más barato.
 *
 * Es una pantalla completa y no un cuadro de diálogo porque tiene dos partes: la cuenta y
 * la lista de a quién aplicársela. Todo eso dentro de un cuadro flotante, con el teclado
 * abierto en un celular, no cabe.
 *
 * Va todo dentro de un solo `LazyColumn` —los campos también, como elementos— para que no
 * queden dos zonas que se desplazan por separado, y para que el teclado empuje hacia
 * arriba el campo que se está llenando en vez de taparlo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculadoraValorPorGramo(
    estado: EstadoCalculadora,
    alCambiarPrecio: (String) -> Unit,
    alCambiarCantidad: (String) -> Unit,
    alCambiarUnidad: (UnidadDeCompra) -> Unit,
    alBuscarDestino: (String) -> Unit,
    alElegirDestino: (DestinoDelValor) -> Unit,
    alTerminar: () -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    // El botón de atrás del teléfono cierra la calculadora, que es lo que cualquiera
    // espera. Sin esto saldría de la app entera.
    BackHandler(onBack = alCerrar)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Valor por gramo") },
                navigationIcon = {
                    IconButton(onClick = alCerrar) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar la calculadora")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { interior ->
        LazyColumn(
            modifier = Modifier
                .padding(interior)
                .fillMaxSize()
                .padding(horizontal = Medidas.grande),
            verticalArrangement = Arrangement.spacedBy(Medidas.chico),
            contentPadding = PaddingValues(vertical = Medidas.medio)
        ) {
            item { CuentaDeLaCompra(estado, alCambiarPrecio, alCambiarCantidad, alCambiarUnidad) }

            item { Resultado(estado.resultado) }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = Medidas.chico))
                Text(
                    text = "Reemplazar o crear",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (estado.faltaElegirDestino) {
                item {
                    Text(
                        text = "No elegiste nada. Marca «Crear un ingrediente nuevo» o uno " +
                            "de la lista de abajo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // "Crear" va fijo primero y no lo toca el buscador: no es un ingrediente que
            // se pueda no encontrar, es la salida para cuando ninguno sirve.
            item {
                OpcionDeDestino(
                    titulo = "Crear un ingrediente nuevo",
                    detalle = "Se abre el formulario con este valor ya puesto",
                    seleccionada = estado.creandoNuevo,
                    alTocar = { alElegirDestino(DestinoDelValor.Crear) }
                )
            }

            item {
                BarraBusqueda(
                    texto = estado.busquedaDestino,
                    alCambiar = alBuscarDestino,
                    marcador = "Buscar el ingrediente a reemplazar"
                )
            }

            if (estado.candidatos.isEmpty()) {
                item {
                    Text(
                        text = if (estado.busquedaDestino.isBlank()) {
                            "Todavía no tienes ingredientes guardados."
                        } else {
                            "Ninguno coincide con «${estado.busquedaDestino}»."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Medidas.chico)
                    )
                }
            }

            items(estado.candidatos, key = { it.id }) { ingrediente ->
                val elegido = estado.elegido?.id == ingrediente.id
                OpcionDeDestino(
                    titulo = ingrediente.nombre,
                    // El valor actual se muestra solo en el que está elegido, para que la
                    // lista se siga leyendo de un vistazo.
                    detalle = if (elegido) {
                        "Ahora vale $${formatearNumero(ingrediente.valorPorGramo)} por gramo"
                    } else {
                        null
                    },
                    seleccionada = elegido,
                    alTocar = { alElegirDestino(DestinoDelValor.Reemplazar(ingrediente.id)) }
                )
            }

            item { ResumenDelCambio(estado) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Medidas.chico),
                    horizontalArrangement = Arrangement.spacedBy(Medidas.chico)
                ) {
                    OutlinedButton(
                        onClick = alCerrar,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Medidas.objetivoTactil)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = alTerminar,
                        // Sin la cuenta hecha no hay nada que aplicar. Que falte elegir a
                        // quién NO lo deshabilita: ahí el botón tiene que poder avisar.
                        enabled = estado.puedeTerminar,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Medidas.objetivoTactil)
                    ) {
                        Text("Listo")
                    }
                }
            }
        }
    }
}

/** Los dos datos de la compra: lo que se pagó y lo que trae el paquete. */
@Composable
private fun CuentaDeLaCompra(
    estado: EstadoCalculadora,
    alCambiarPrecio: (String) -> Unit,
    alCambiarCantidad: (String) -> Unit,
    alCambiarUnidad: (UnidadDeCompra) -> Unit
) {
    val errorPrecio = estado.errorPrecioVisible
    val errorCantidad = estado.errorCantidadVisible

    Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
        Text(
            text = "¿Cuánto pagaste y cuánto trae?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = estado.precio,
            onValueChange = alCambiarPrecio,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Lo que pagaste (en $)") },
            singleLine = true,
            isError = errorPrecio != null,
            supportingText = { if (errorPrecio != null) Text(errorPrecio) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = estado.cantidad,
            onValueChange = alCambiarCantidad,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Lo que trae el paquete") },
            singleLine = true,
            isError = errorCantidad != null,
            supportingText = { if (errorCantidad != null) Text(errorCantidad) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            )
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
            BotonDeUnidad(
                texto = "Kilos",
                seleccionada = estado.unidad == UnidadDeCompra.KILO,
                alTocar = { alCambiarUnidad(UnidadDeCompra.KILO) },
                modifier = Modifier.weight(1f)
            )
            BotonDeUnidad(
                texto = "Gramos",
                seleccionada = estado.unidad == UnidadDeCompra.GRAMO,
                alTocar = { alCambiarUnidad(UnidadDeCompra.GRAMO) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Una de las dos unidades. Relleno la elegida, con borde la otra.
 *
 * Son dos botones a la vista y no un desplegable a propósito: pasar de kilos a gramos
 * cambia el resultado por mil, así que cuál está puesta tiene que verse sin tocar nada.
 */
@Composable
private fun BotonDeUnidad(
    texto: String,
    seleccionada: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alto = modifier.heightIn(min = Medidas.objetivoTactil)
    if (seleccionada) {
        Button(onClick = alTocar, modifier = alto) { Text(texto) }
    } else {
        OutlinedButton(onClick = alTocar, modifier = alto) { Text(texto) }
    }
}

/** El valor por gramo que sale de la cuenta, o el aviso de que todavía falta un dato. */
@Composable
private fun Resultado(valor: Double?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(Medidas.medio)) {
            Text(text = "Valor por gramo", style = MaterialTheme.typography.bodySmall)
            if (valor == null) {
                Text(
                    text = "Completa los dos campos de arriba",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "$${formatearNumero(valor)}",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    }
}

/** Una opción de la lista "Reemplazar o crear". */
@Composable
private fun OpcionDeDestino(
    titulo: String,
    detalle: String?,
    seleccionada: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Medidas.chico))
            .background(
                if (seleccionada) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .clickable(onClick = alTocar)
            .heightIn(min = Medidas.objetivoTactil)
            .padding(end = Medidas.medio),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // El círculo no recibe el toque por su cuenta: toda la fila es el objetivo, que es
        // mucho más difícil de fallar que un punto de 20dp.
        RadioButton(selected = seleccionada, onClick = null)
        Column(modifier = Modifier.padding(vertical = Medidas.chico)) {
            Text(text = titulo, style = MaterialTheme.typography.bodyLarge)
            if (detalle != null) {
                Text(
                    text = detalle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * La línea que resume qué va a pasar al tocar "Listo".
 *
 * Repite el valor actual del elegido aunque su fila ya lo muestre: si la lista es larga,
 * esa fila puede haber quedado fuera de la pantalla justo cuando se decide.
 */
@Composable
private fun ResumenDelCambio(estado: EstadoCalculadora) {
    val valor = estado.resultado ?: return
    val elegido = estado.elegido

    val texto = when {
        estado.creandoNuevo ->
            "Se abrirá el formulario de un ingrediente nuevo con $${formatearNumero(valor)} " +
                "por gramo."
        elegido != null ->
            "«${elegido.nombre}» pasará de $${formatearNumero(elegido.valorPorGramo)} a " +
                "$${formatearNumero(valor)} por gramo."
        else -> return
    }

    Text(
        text = texto,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Medidas.chico)
    )
}

// --- Vistas previas ---

private val despensa = listOf(
    Ingrediente(id = 1, nombre = "Azúcar flor", valorPorGramo = 1.55),
    Ingrediente(id = 2, nombre = "Harina", valorPorGramo = 1.55),
    Ingrediente(id = 3, nombre = "Manjar", valorPorGramo = 4.8)
)

@Composable
private fun Previsualizar(estado: EstadoCalculadora) {
    CalculadoraValorPorGramo(
        estado = estado.copy(candidatos = despensa),
        alCambiarPrecio = {}, alCambiarCantidad = {}, alCambiarUnidad = {},
        alBuscarDestino = {}, alElegirDestino = {}, alTerminar = {}, alCerrar = {}
    )
}

@Preview(showBackground = true, name = "Calculadora - recién abierta")
@Composable
private fun CalculadoraVacia() {
    ReposteriaTheme { Previsualizar(EstadoCalculadora()) }
}

@Preview(showBackground = true, name = "Calculadora - con el resultado")
@Composable
private fun CalculadoraConResultado() {
    ReposteriaTheme {
        Previsualizar(
            EstadoCalculadora(precio = "1.000", cantidad = "1", tocadoPrecio = true, tocadoCantidad = true)
        )
    }
}

@Preview(showBackground = true, name = "Calculadora - reemplazando la harina")
@Composable
private fun CalculadoraReemplazando() {
    ReposteriaTheme {
        Previsualizar(
            EstadoCalculadora(
                precio = "1.000",
                cantidad = "1",
                tocadoPrecio = true,
                tocadoCantidad = true,
                destino = DestinoDelValor.Reemplazar(2),
                elegido = despensa[1]
            )
        )
    }
}

@Preview(showBackground = true, name = "Calculadora - sin elegir nada")
@Composable
private fun CalculadoraSinDestino() {
    ReposteriaTheme {
        Previsualizar(
            EstadoCalculadora(
                precio = "1.000",
                cantidad = "1",
                tocadoPrecio = true,
                tocadoCantidad = true,
                faltaElegirDestino = true
            )
        )
    }
}

@Preview(showBackground = true, name = "Calculadora - oscuro")
@Composable
private fun CalculadoraOscura() {
    ReposteriaTheme(oscuro = true) {
        Previsualizar(
            EstadoCalculadora(
                precio = "2.500",
                cantidad = "1,5",
                tocadoPrecio = true,
                tocadoCantidad = true,
                destino = DestinoDelValor.Crear
            )
        )
    }
}
