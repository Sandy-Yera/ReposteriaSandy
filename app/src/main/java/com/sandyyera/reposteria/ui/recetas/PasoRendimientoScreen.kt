package com.sandyyera.reposteria.ui.recetas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el paso de rendimiento. */
data class AccionesRendimiento(
    val cambiarTrozos: (String) -> Unit = {},
    val cambiarPesoFinal: (String) -> Unit = {},
    val marcarPesoRevisado: () -> Unit = {},
    val abrirReescalarPorPeso: () -> Unit = {},
    val cambiarPesoNuevo: (String) -> Unit = {},
    val confirmarReescaladoPorPeso: () -> Unit = {},
    val cerrarDialogo: () -> Unit = {},
    val mensajeMostrado: () -> Unit = {},
    val irAlPaso: (PasoDeReceta) -> Unit = {},
    val cerrarReceta: () -> Unit = {}
)

/** El paso de rendimiento conectado a su ViewModel. */
@Composable
fun PasoRendimientoScreen(
    tituloReceta: String,
    modelo: RendimientoViewModel,
    pasoActual: PasoDeReceta,
    alElegirPaso: (PasoDeReceta) -> Unit,
    alCerrarReceta: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val dialogo by modelo.dialogo.collectAsStateWithLifecycle()

    val acciones = remember(modelo, alElegirPaso, alCerrarReceta) {
        AccionesRendimiento(
            cambiarTrozos = modelo::cambiarTrozos,
            cambiarPesoFinal = modelo::cambiarPesoFinal,
            marcarPesoRevisado = modelo::marcarPesoRevisado,
            abrirReescalarPorPeso = modelo::abrirReescalarPorPeso,
            cambiarPesoNuevo = modelo::cambiarPesoNuevo,
            confirmarReescaladoPorPeso = modelo::confirmarReescaladoPorPeso,
            cerrarDialogo = modelo::cerrarDialogo,
            mensajeMostrado = modelo::mensajeMostrado,
            irAlPaso = alElegirPaso,
            cerrarReceta = alCerrarReceta
        )
    }

    PasoRendimiento(tituloReceta, estado, dialogo, acciones, pasoActual, modifier)
}

/**
 * El dibujo del paso "Rendimiento" (8.3): en cuántos trozos rinde y cuánto pesa.
 *
 * **El molde se fue a su propio paso** (8.4.1, #2). Lo que queda son dos campos y el número
 * que sale de ellos, que es de lo que trata este paso; el molde solo sigue asomándose en una
 * cosa, y es la que decide todo acá: con molde el peso final es opcional, sin molde es
 * obligatorio.
 *
 * Si el peso viene de un reescalado por molde, arriba de todo aparece el aviso de
 * comprobarlo, y se va al tocar el campo (8.4.1, #4).
 *
 * **No hay botón de guardar** (8.4.1). Se guarda solo, medio segundo después de dejar de
 * escribir. El botón que había parecía innecesario porque al volver los datos seguían ahí,
 * pero no estaban guardados: sobrevivía el ViewModel, no la fila. Sin botón, lo único que
 * queda por decir es cuando el guardado se rechaza, y eso va **bajo el campo de los trozos**
 * —único rechazo posible, el de las promociones que no caben— y no en la franja de abajo,
 * que con el teclado abierto no se ve.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasoRendimiento(
    tituloReceta: String,
    estado: EstadoRendimiento,
    dialogo: DialogoRendimiento,
    acciones: AccionesRendimiento,
    pasoActual: PasoDeReceta = PasoDeReceta.RENDIMIENTO,
    modifier: Modifier = Modifier
) {
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    // El botón de atrás cierra el cuadro si hay uno, y si no sale de la receta. **Ya no
    // vuelve a cantidades**: eso hacía que el mismo gesto significara "un paso atrás" acá y
    // "salir" en el primer paso. Moverse entre pasos es la fila de arriba.
    BackHandler(enabled = true) {
        if (dialogo is DialogoRendimiento.Ninguno) acciones.cerrarReceta()
        else acciones.cerrarDialogo()
    }

    LaunchedEffect(estado.mensaje) {
        val texto = estado.mensaje ?: return@LaunchedEffect
        try {
            anfitrionDeMensajes.showSnackbar(texto)
        } finally {
            // En `finally` y no después, porque `showSnackbar` se queda esperando a que el
            // aviso se cierre solo: al cambiar de paso antes de eso, esta corrutina se
            // cancela y el mensaje quedaba pendiente en el ViewModel. Reaparecía cada vez
            // que se volvía al paso — "se guardó el rendimiento" una y otra vez.
            acciones.mensajeMostrado()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(tituloReceta) },
                    navigationIcon = {
                        IconButton(onClick = acciones.cerrarReceta) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Salir de la receta"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilaDePasos(pasoActual = pasoActual, alElegirPaso = acciones.irAlPaso)
            }
        },
        snackbarHost = { SnackbarHost(anfitrionDeMensajes) }
    ) { interior ->
        Column(
            modifier = Modifier
                .padding(interior)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Medidas.grande)
                .padding(top = Medidas.medio, bottom = Medidas.grande),
            verticalArrangement = Arrangement.spacedBy(Medidas.medio)
        ) {
            estado.avisoDelPeso?.let { AvisoDePesoReescalado(it) }

            CampoNumerico(
                valor = estado.trozos,
                alCambiar = acciones.cambiarTrozos,
                etiqueta = "¿En cuántos trozos rinde?",
                error = estado.errorTrozos,
                accionDelTeclado = ImeAction.Next
            )

            CampoNumerico(
                valor = estado.pesoFinal,
                alCambiar = acciones.cambiarPesoFinal,
                etiqueta = "Peso del producto terminado (g)",
                error = estado.errorPesoFinal,
                // El peso se **pesa**, no se calcula: el mismo molde da pesos distintos
                // según la receta, así que las medidas del molde no lo reemplazan.
                ayuda = if (estado.usaMolde) {
                    "Opcional. Pésalo cuando esté listo; el molde no lo dice."
                } else {
                    "Obligatorio sin molde: es contra lo que se reescala la receta."
                },
                // Tocarlo para mirarlo ya cuenta como comprobarlo: el aviso de arriba se va
                // sin obligar a reescribir un número que puede estar bien.
                alEnfocar = acciones.marcarPesoRevisado
            )

            // Las dos mitades de la misma pregunta: qué se entrega en cada trozo y qué
            // cuesta entregarlo. Salen las dos de los trozos que se escriben arriba.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(Medidas.medio),
                    verticalArrangement = Arrangement.spacedBy(Medidas.medio)
                ) {
                    Column {
                        Text("Cada trozo pesa", style = MaterialTheme.typography.bodySmall)
                        Text(
                            // La unidad va acá y no dentro de `pesoPorTrozo`, porque esa
                            // función también devuelve "No especificado", y "No especificado
                            // g" no se lee.
                            text = estado.pesoDeCadaTrozo + estado.unidadDelPeso,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Column {
                        Text("Cada trozo cuesta", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "$${formatearNumero(estado.costoDeCadaTrozo)}",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            // Se mira `tieneIngredientes` y no el costo: un ingrediente puede
                            // valer 0 a propósito, y decirle "sin ingredientes" a una receta
                            // que sí los tiene manda a buscar un problema que no existe.
                            text = if (estado.tieneIngredientes) {
                                "Es el piso de cualquier precio que le pongas."
                            } else {
                                "Todavía sin ingredientes: cárgalos en Cantidades."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (estado.sePuedeReescalarPorPeso) {
                // Solo sin molde: con molde, cambiar de tamaño es cambiar de molde, y eso
                // se hace en el paso anterior.
                TextButton(
                    onClick = acciones.abrirReescalarPorPeso,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Medidas.objetivoTactil)
                ) { Text("Reescalar la receta a otro peso") }
            }
        }
    }

    when (dialogo) {
        is DialogoRendimiento.Ninguno -> Unit
        is DialogoRendimiento.ReescalarPorPeso -> CuadroDeReescaladoPorPeso(dialogo, acciones)
    }
}

/**
 * El aviso de que el peso salió de una multiplicación y hay que comprobarlo (8.4.1, #4).
 *
 * Va **arriba de todo y no como texto de ayuda del campo**: el hueco de ayuda ya lo ocupa
 * la explicación de si el peso es obligatorio, y un aviso que aparece y desaparece ahí haría
 * saltar el formulario. Además esto no es una aclaración sobre cómo llenar el campo: es algo
 * que pasó y que hay que resolver.
 *
 * Usa el color de error del tema y no el pastel de las tarjetas de dato, siguiendo la regla
 * de 12.6: los pasteles pintan fondos, las señales pintan íconos y texto.
 */
@Composable
private fun AvisoDePesoReescalado(texto: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(Medidas.medio),
            horizontalArrangement = Arrangement.spacedBy(Medidas.chico),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Text(text = texto, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CuadroDeReescaladoPorPeso(
    estado: DialogoRendimiento.ReescalarPorPeso,
    acciones: AccionesRendimiento
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("Reescalar a otro peso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                Text(
                    text = "Las cantidades de todos los ingredientes se van a multiplicar " +
                        "para que la receta rinda ese peso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CampoNumerico(
                    valor = estado.pesoNuevo,
                    alCambiar = acciones.cambiarPesoNuevo,
                    etiqueta = "Peso nuevo (g)",
                    error = estado.rechazo,
                    accionDelTeclado = ImeAction.Done
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = acciones.confirmarReescaladoPorPeso,
                enabled = estado.puedeGuardar
            ) { Text("Reescalar") }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

// --- Vistas previas ---

private fun estadoDeEjemplo(usaMolde: Boolean) = EstadoRendimiento(
    usaMolde = usaMolde,
    trozos = "8",
    pesoFinal = "1.200",
    cargando = false
)

@Preview(showBackground = true, name = "Rendimiento - sin molde")
@Composable
private fun VistaPreviaSinMolde() {
    ReposteriaTheme {
        PasoRendimiento(
            "Torta de manjar",
            estadoDeEjemplo(usaMolde = false),
            DialogoRendimiento.Ninguno,
            AccionesRendimiento()
        )
    }
}

@Preview(showBackground = true, name = "Rendimiento - peso recién reescalado")
@Composable
private fun VistaPreviaPesoSinRevisar() {
    ReposteriaTheme {
        PasoRendimiento(
            "Torta de manjar",
            estadoDeEjemplo(usaMolde = true).copy(pesoSinRevisar = true),
            DialogoRendimiento.Ninguno,
            AccionesRendimiento()
        )
    }
}

@Preview(showBackground = true, name = "Rendimiento - reescalando por peso")
@Composable
private fun VistaPreviaReescalar() {
    ReposteriaTheme {
        PasoRendimiento(
            "Torta de manjar",
            estadoDeEjemplo(usaMolde = false),
            DialogoRendimiento.ReescalarPorPeso(pesoNuevo = "1.500"),
            AccionesRendimiento()
        )
    }
}
