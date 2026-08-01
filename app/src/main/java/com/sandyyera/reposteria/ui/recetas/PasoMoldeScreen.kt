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

/** Todo lo que se puede pedir desde el paso del molde. */
data class AccionesMoldeDeReceta(
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
    val cerrarDialogo: () -> Unit = {},
    val mensajeMostrado: () -> Unit = {},
    val irAlPaso: (PasoDeReceta) -> Unit = {},
    val cerrarReceta: () -> Unit = {}
)

/** El paso del molde conectado a su ViewModel. */
@Composable
fun PasoMoldeScreen(
    modelo: MoldeDeRecetaViewModel,
    pasoActual: PasoDeReceta,
    alElegirPaso: (PasoDeReceta) -> Unit,
    alCerrarReceta: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val dialogo by modelo.dialogo.collectAsStateWithLifecycle()

    val acciones = remember(modelo, alElegirPaso, alCerrarReceta) {
        AccionesMoldeDeReceta(
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
            cerrarDialogo = modelo::cerrarDialogo,
            mensajeMostrado = modelo::mensajeMostrado,
            irAlPaso = alElegirPaso,
            cerrarReceta = alCerrarReceta
        )
    }

    PasoMolde(estado, dialogo, acciones, pasoActual, modifier)
}

/**
 * El dibujo del paso "Molde" (8.3.1 y 9.3).
 *
 * **Es un paso propio y no la mitad de arriba de Rendimiento** (8.4.1, #2). Acá vive el
 * reescalado, que es la operación más delicada de la app: cambia todas las cantidades de la
 * receta de una vez. Compartiendo pantalla con los trozos y el peso quedaba a un toque de
 * quien solo venía a corregir un número, y además obligaba a leer dos preguntas distintas
 * antes de responder cualquiera de las dos.
 *
 * La pantalla dice en todo momento **si la receta está enlazada al catálogo o no**: es la
 * única diferencia invisible entre dos recetas con las mismas medidas, y de ella depende que
 * una corrección del molde les llegue o no.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasoMolde(
    estado: EstadoMoldeDeReceta,
    dialogo: DialogoMoldeDeReceta,
    acciones: AccionesMoldeDeReceta,
    pasoActual: PasoDeReceta = PasoDeReceta.MOLDE,
    modifier: Modifier = Modifier
) {
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    // El botón de atrás cierra el cuadro si hay uno, y si no sale de la receta. Moverse
    // entre pasos es la fila de arriba, no el botón de atrás (8.4.1, #1).
    BackHandler(enabled = true) {
        if (dialogo is DialogoMoldeDeReceta.Ninguno) acciones.cerrarReceta()
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
                    title = { Text(estado.receta?.titulo.orEmpty()) },
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
            TarjetaDelMolde(estado, acciones)

            Text(
                text = if (estado.usaMolde) {
                    "Cambiar de molde recalcula las cantidades de todos los ingredientes, y " +
                        "también el peso del producto."
                } else {
                    "Sin molde, la receta se reescala por peso desde el paso Rendimiento."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    when (dialogo) {
        is DialogoMoldeDeReceta.Ninguno -> Unit
        is DialogoMoldeDeReceta.Elegir -> CuadroDeMolde(dialogo, acciones)
        is DialogoMoldeDeReceta.ConfirmarQuitar -> AlertDialog(
            onDismissRequest = acciones.cerrarDialogo,
            title = { Text("¿Dejar de usar molde?") },
            text = {
                Text(
                    if (estado.tienePesoFinal) {
                        "Las medidas quedan guardadas por si vuelves atrás. La receta pasa " +
                            "a reescalarse por peso, así que el peso final se vuelve " +
                            "obligatorio."
                    } else {
                        // Se dice antes y no después: el repositorio lo rechaza igual, pero
                        // enterarse al confirmar es enterarse cuando ya se decidió.
                        "Todavía no tiene peso final anotado, y sin molde ese dato es " +
                            "obligatorio. Anótalo en Rendimiento y vuelve."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = acciones.confirmarQuitarMolde,
                    enabled = estado.tienePesoFinal
                ) { Text("Quitar el molde") }
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
    estado: EstadoMoldeDeReceta,
    acciones: AccionesMoldeDeReceta
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
        ) {
            Text("Molde", style = MaterialTheme.typography.bodySmall)
            Text(
                text = estado.moldeEnlazado?.nombre
                    ?: if (estado.usaMolde) "Molde de prueba" else SIN_MOLDE,
                style = MaterialTheme.typography.headlineMedium
            )
            estado.medidasDelMolde?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
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
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                OutlinedButton(
                    onClick = acciones.abrirElegirMolde,
                    modifier = Modifier.heightIn(min = Medidas.objetivoTactil)
                ) {
                    Text(if (estado.usaMolde) "Cambiar de molde" else "Usar un molde")
                }
                if (estado.usaMolde) {
                    TextButton(
                        onClick = acciones.pedirQuitarMolde,
                        modifier = Modifier.heightIn(min = Medidas.objetivoTactil)
                    ) { Text("Quitar") }
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
    estado: DialogoMoldeDeReceta.Elegir,
    acciones: AccionesMoldeDeReceta
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
                        "Las cantidades de los ingredientes y el peso del producto se van " +
                            "a recalcular."
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

// --- Vistas previas ---

private fun estadoDeEjemplo(usaMolde: Boolean) = EstadoMoldeDeReceta(
    receta = Receta(id = 1, titulo = "Torta de manjar"),
    usaMolde = usaMolde,
    tienePesoFinal = true,
    cargando = false
)

@Preview(showBackground = true, name = "Molde - todavía sin molde")
@Composable
private fun VistaPreviaSinMolde() {
    ReposteriaTheme {
        PasoMolde(
            estadoDeEjemplo(usaMolde = false),
            DialogoMoldeDeReceta.Ninguno,
            AccionesMoldeDeReceta()
        )
    }
}

@Preview(showBackground = true, name = "Molde - eligiendo uno nuevo")
@Composable
private fun VistaPreviaElegirMolde() {
    ReposteriaTheme {
        PasoMolde(
            estadoDeEjemplo(usaMolde = true),
            DialogoMoldeDeReceta.Elegir(esReescalado = true),
            AccionesMoldeDeReceta()
        )
    }
}
