package com.sandyyera.reposteria.ui.recetas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.data.db.entidades.Molde
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.logica.moldes.MAX_DIFERENCIA_ALTURA_CM
import com.sandyyera.reposteria.logica.moldes.ModoReescalado
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.rendimiento.SIN_MOLDE
import com.sandyyera.reposteria.logica.validaciones.CampoDeMolde
import com.sandyyera.reposteria.ui.componentes.BarraBusqueda
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.moldes.nombreDeLaForma
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el paso de rendimiento. */
data class AccionesRendimiento(
    val cambiarTrozos: (String) -> Unit = {},
    val cambiarPesoFinal: (String) -> Unit = {},
    val guardar: () -> Unit = {},
    val abrirElegirMolde: () -> Unit = {},
    val cambiarOrigenDelMolde: (OrigenDelMolde) -> Unit = {},
    val buscarMolde: (String) -> Unit = {},
    val elegirMoldeGuardado: (Molde) -> Unit = {},
    val elegirFormaDePrueba: (TipoFormaMolde) -> Unit = {},
    val cambiarMedidaDePrueba: (CampoDeMolde, String) -> Unit = { _, _ -> },
    val elegirModoDeReescalado: (ModoReescalado) -> Unit = {},
    val confirmarMolde: () -> Unit = {},
    val pedirQuitarMolde: () -> Unit = {},
    val confirmarQuitarMolde: () -> Unit = {},
    val abrirReescalarPorPeso: () -> Unit = {},
    val cambiarPesoNuevo: (String) -> Unit = {},
    val confirmarReescaladoPorPeso: () -> Unit = {},
    val cerrarDialogo: () -> Unit = {},
    val mensajeMostrado: () -> Unit = {}
)

/** El paso de rendimiento conectado a su ViewModel. */
@Composable
fun PasoRendimientoScreen(
    modelo: RendimientoViewModel,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val dialogo by modelo.dialogo.collectAsStateWithLifecycle()

    val acciones = remember(modelo) {
        AccionesRendimiento(
            cambiarTrozos = modelo::cambiarTrozos,
            cambiarPesoFinal = modelo::cambiarPesoFinal,
            guardar = modelo::guardar,
            abrirElegirMolde = modelo::abrirElegirMolde,
            cambiarOrigenDelMolde = modelo::cambiarOrigenDelMolde,
            buscarMolde = modelo::buscarMolde,
            elegirMoldeGuardado = modelo::elegirMoldeGuardado,
            elegirFormaDePrueba = modelo::elegirFormaDePrueba,
            cambiarMedidaDePrueba = modelo::cambiarMedidaDePrueba,
            elegirModoDeReescalado = modelo::elegirModoDeReescalado,
            confirmarMolde = modelo::confirmarMolde,
            pedirQuitarMolde = modelo::pedirQuitarMolde,
            confirmarQuitarMolde = modelo::confirmarQuitarMolde,
            abrirReescalarPorPeso = modelo::abrirReescalarPorPeso,
            cambiarPesoNuevo = modelo::cambiarPesoNuevo,
            confirmarReescaladoPorPeso = modelo::confirmarReescaladoPorPeso,
            cerrarDialogo = modelo::cerrarDialogo,
            mensajeMostrado = modelo::mensajeMostrado
        )
    }

    PasoRendimiento(estado, dialogo, acciones, alVolver, modifier)
}

/**
 * El dibujo del paso "Rendimiento" (8.3).
 *
 * Arriba el molde, porque es lo que decide todo lo demás: **con molde el peso final es
 * opcional y sin molde es obligatorio**, así que preguntar el peso antes obligaría a
 * cambiar la respuesta después.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasoRendimiento(
    estado: EstadoRendimiento,
    dialogo: DialogoRendimiento,
    acciones: AccionesRendimiento,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    // El botón de atrás del teléfono cierra el cuadro si hay uno, y si no vuelve al paso
    // anterior. Sin esto saldría de la app en medio de la receta.
    BackHandler(enabled = true) {
        if (dialogo is DialogoRendimiento.Ninguno) alVolver() else acciones.cerrarDialogo()
    }

    LaunchedEffect(estado.mensaje) {
        val texto = estado.mensaje ?: return@LaunchedEffect
        anfitrionDeMensajes.showSnackbar(texto)
        acciones.mensajeMostrado()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(estado.receta?.titulo ?: "Rendimiento") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.Default.Close, contentDescription = "Volver a cantidades")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
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
            TarjetaDelMolde(estado, acciones)

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
                }
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(Medidas.medio)) {
                    Text("Cada trozo pesa", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = estado.pesoDeCadaTrozo,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            OutlinedButton(
                onClick = acciones.guardar,
                enabled = estado.puedeGuardar,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medidas.objetivoTactil)
            ) { Text("Guardar rendimiento") }

            if (!estado.usaMolde) {
                // Solo sin molde: con molde, cambiar de tamaño es cambiar de molde, y eso
                // se hace arriba eligiendo otro.
                TextButton(
                    onClick = acciones.abrirReescalarPorPeso,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reescalar la receta a otro peso") }
            }
        }
    }

    when (dialogo) {
        is DialogoRendimiento.Ninguno -> Unit
        is DialogoRendimiento.ElegirMolde -> CuadroDeMolde(dialogo, acciones)
        is DialogoRendimiento.ReescalarPorPeso -> CuadroDeReescaladoPorPeso(dialogo, acciones)
        is DialogoRendimiento.ConfirmarQuitarMolde -> AlertDialog(
            onDismissRequest = acciones.cerrarDialogo,
            title = { Text("¿Dejar de usar molde?") },
            text = {
                Text(
                    "Las medidas quedan guardadas por si vuelves atrás. La receta pasa a " +
                        "reescalarse por peso, así que el peso final se vuelve obligatorio."
                )
            },
            confirmButton = {
                TextButton(onClick = acciones.confirmarQuitarMolde) { Text("Quitar el molde") }
            },
            dismissButton = {
                TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
            }
        )
    }
}

/** La tarjeta del molde: qué usa la receta ahora y cómo cambiarlo. */
@Composable
private fun TarjetaDelMolde(
    estado: EstadoRendimiento,
    acciones: AccionesRendimiento
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
        ) {
            Text("Molde", style = MaterialTheme.typography.bodySmall)
            Text(
                text = estado.moldeEnlazado?.nombre
                    ?: if (estado.usaMolde) "Molde de prueba" else SIN_MOLDE,
                style = MaterialTheme.typography.titleMedium
            )
            estado.medidasDelMolde?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (estado.usaMolde) {
                Text(
                    // La diferencia invisible entre los dos orígenes: dos recetas con las
                    // mismas medidas se comportan distinto según esto.
                    text = if (estado.enlazadaAlCatalogo) {
                        "Si corriges este molde en el catálogo, la receta se actualiza sola."
                    } else {
                        "Medidas propias de esta receta: no cambian si editas el catálogo."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                OutlinedButton(onClick = acciones.abrirElegirMolde) {
                    Text(if (estado.usaMolde) "Cambiar de molde" else "Usar un molde")
                }
                if (estado.usaMolde) {
                    TextButton(onClick = acciones.pedirQuitarMolde) { Text("Quitar") }
                }
            }
        }
    }
}

/**
 * El cuadro de elegir molde, que sirve para las dos cosas y **lo dice**.
 *
 * La primera vez solo guarda medidas; de la segunda en adelante reescala los ingredientes.
 * El selector de modo aparece solo en el segundo caso, porque en el primero no hay nada que
 * conservar: no existe un molde anterior con el que comparar (9.3).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CuadroDeMolde(
    estado: DialogoRendimiento.ElegirMolde,
    acciones: AccionesRendimiento
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text(if (estado.esReescalado) "Cambiar de molde" else "Definir el molde") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                Text(
                    text = if (estado.esReescalado) {
                        "Las cantidades de los ingredientes se van a recalcular."
                    } else {
                        "Solo se guardan las medidas: los ingredientes quedan como están."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                    FilterChip(
                        selected = estado.origen == OrigenDelMolde.GUARDADO,
                        onClick = { acciones.cambiarOrigenDelMolde(OrigenDelMolde.GUARDADO) },
                        label = { Text("De mis moldes") }
                    )
                    FilterChip(
                        selected = estado.origen == OrigenDelMolde.PRUEBA,
                        onClick = { acciones.cambiarOrigenDelMolde(OrigenDelMolde.PRUEBA) },
                        label = { Text("Medir uno prestado") }
                    )
                }

                when (estado.origen) {
                    OrigenDelMolde.GUARDADO -> {
                        BarraBusqueda(
                            texto = estado.busqueda,
                            alCambiar = acciones.buscarMolde,
                            marcador = "Buscar un molde"
                        )
                        if (estado.candidatos.isEmpty()) {
                            Text(
                                text = "No tienes moldes guardados que coincidan. " +
                                    "Créalos en la sección Moldes, o mide uno prestado acá.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.heightIn(max = Medidas.altoMaximoDeLista),
                            contentPadding = PaddingValues(vertical = Medidas.minimo)
                        ) {
                            items(estado.candidatos, key = { it.id }) { molde ->
                                FilaDeMolde(
                                    molde = molde,
                                    elegido = estado.elegido?.id == molde.id,
                                    alElegir = { acciones.elegirMoldeGuardado(molde) }
                                )
                            }
                        }
                    }

                    OrigenDelMolde.PRUEBA -> {
                        Text(
                            // Es lo que distingue este camino, y sin decirlo nadie sabría
                            // por qué elegiría uno u otro.
                            text = "No se guarda en tu catálogo: son medidas de esta receta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                            TipoFormaMolde.entries.forEach { forma ->
                                FilterChip(
                                    selected = estado.forma == forma,
                                    onClick = { acciones.elegirFormaDePrueba(forma) },
                                    label = { Text(nombreDeLaForma(forma)) }
                                )
                            }
                        }
                        estado.campos.forEach { campo ->
                            CampoNumerico(
                                valor = estado.medidas[campo].orEmpty(),
                                alCambiar = { acciones.cambiarMedidaDePrueba(campo, it) },
                                etiqueta = campo.etiqueta,
                                accionDelTeclado = if (campo == estado.campos.last()) {
                                    ImeAction.Done
                                } else {
                                    ImeAction.Next
                                }
                            )
                        }
                    }
                }

                if (estado.esReescalado) {
                    SelectorDeModo(estado.modo, acciones.elegirModoDeReescalado)
                }

                estado.rechazo?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.confirmarMolde, enabled = estado.puedeGuardar) {
                Text(if (estado.esReescalado) "Reescalar" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

@Composable
private fun FilaDeMolde(molde: Molde, elegido: Boolean, alElegir: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Medidas.objetivoTactil)
            .clickable(onClick = alElegir)
            .padding(Medidas.chico)
    ) {
        Text(
            text = molde.nombre,
            style = MaterialTheme.typography.bodyMedium,
            color = if (elegido) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Qué se conserva al pasar de un molde a otro (8.3.1). */
@Composable
private fun SelectorDeModo(modo: ModoReescalado, alElegir: (ModoReescalado) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Medidas.minimo)) {
        Text("¿Qué quieres conservar?", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
            FilterChip(
                selected = modo == ModoReescalado.CAPACIDAD,
                onClick = { alElegir(ModoReescalado.CAPACIDAD) },
                label = { Text("Que rinda más") }
            )
            FilterChip(
                selected = modo == ModoReescalado.ALTURA,
                onClick = { alElegir(ModoReescalado.ALTURA) },
                label = { Text("El mismo grosor") }
            )
        }
        Text(
            text = if (modo == ModoReescalado.CAPACIDAD) {
                "Compara volúmenes: la torta queda igual de alta y rinde más porciones."
            } else {
                "Compara áreas: la tajada queda del mismo grosor. El molde nuevo no puede " +
                    "ser más bajo, ni más de ${MAX_DIFERENCIA_ALTURA_CM.toInt()} cm más alto."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                    "Las cantidades de los ingredientes se recalculan para llegar a este peso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CampoNumerico(
                    valor = estado.pesoNuevo,
                    alCambiar = acciones.cambiarPesoNuevo,
                    etiqueta = "Peso nuevo (g)",
                    error = estado.rechazo
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
    receta = Receta(id = 1, titulo = "Torta de manjar"),
    usaMolde = usaMolde,
    trozos = "8",
    pesoFinal = "1.200",
    cargando = false
)

@Preview(showBackground = true)
@Composable
private fun VistaPreviaSinMolde() {
    ReposteriaTheme {
        PasoRendimiento(
            estadoDeEjemplo(usaMolde = false),
            DialogoRendimiento.Ninguno,
            AccionesRendimiento(),
            alVolver = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VistaPreviaElegirMolde() {
    ReposteriaTheme {
        PasoRendimiento(
            estadoDeEjemplo(usaMolde = true),
            DialogoRendimiento.ElegirMolde(esReescalado = true),
            AccionesRendimiento(),
            alVolver = {}
        )
    }
}
