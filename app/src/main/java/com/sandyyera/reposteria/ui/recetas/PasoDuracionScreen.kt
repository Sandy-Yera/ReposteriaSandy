package com.sandyyera.reposteria.ui.recetas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import com.sandyyera.reposteria.logica.validaciones.ORDEN_DE_LOS_BLOQUES
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.logica.duracion.AVISO_DURACIONES_ESTIMADAS
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import com.sandyyera.reposteria.logica.duracion.UnidadDuracion
import com.sandyyera.reposteria.logica.duracion.nombreDeLaUnidad
import com.sandyyera.reposteria.logica.duracion.nombreDelTipoDeDuracion
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el paso de duración. */
data class AccionesDuracion(
    val cambiarApto: (TipoDuracion, Boolean) -> Unit = { _, _ -> },
    val cambiarCantidad: (TipoDuracion, String) -> Unit = { _, _ -> },
    val cambiarUnidad: (TipoDuracion, UnidadDuracion) -> Unit = { _, _ -> },
    val guardarBloque: (TipoDuracion) -> Unit = {},
    val mensajeMostrado: () -> Unit = {},
    val irAlPaso: (PasoDeReceta) -> Unit = {},
    val cerrarReceta: () -> Unit = {}
)

/** El paso de duración conectado a su ViewModel. */
@Composable
fun PasoDuracionScreen(
    tituloReceta: String,
    modelo: DuracionViewModel,
    pasoActual: PasoDeReceta,
    alElegirPaso: (PasoDeReceta) -> Unit,
    alCerrarReceta: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    val acciones = remember(modelo, alElegirPaso, alCerrarReceta) {
        AccionesDuracion(
            cambiarApto = modelo::cambiarApto,
            cambiarCantidad = modelo::cambiarCantidad,
            cambiarUnidad = modelo::cambiarUnidad,
            guardarBloque = modelo::guardarBloque,
            mensajeMostrado = modelo::mensajeMostrado,
            irAlPaso = alElegirPaso,
            cerrarReceta = alCerrarReceta
        )
    }

    PasoDuracion(tituloReceta, estado, acciones, pasoActual, modifier)
}

/**
 * El dibujo del paso "Duración" (8.4).
 *
 * El aviso de que son estimaciones va **fijo arriba y no como un texto de ayuda al pie**: es
 * lo que hay que tener en la cabeza mientras se escriben los números, no algo que se lee
 * después de haberlos escrito.
 *
 * El paso puede quedar completamente vacío y la pantalla lo dice, para que no parezca que
 * falta llenarlo.
 *
 * **No hay botón de guardar** (8.4.1): cada bloque se guarda al salir de su campo, y el
 * switch y la unidad al instante. Salir del campo y no cada tecla, porque acá un bloque a
 * medio escribir se ve igual que uno vaciado a propósito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasoDuracion(
    tituloReceta: String,
    estado: EstadoDuracion,
    acciones: AccionesDuracion,
    pasoActual: PasoDeReceta = PasoDeReceta.DURACION,
    modifier: Modifier = Modifier
) {
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    // Sale de la receta, no vuelve al rendimiento: moverse entre pasos es la fila de arriba.
    BackHandler(onBack = acciones.cerrarReceta)

    // Cambiar de paso o salir de la receta desmonta esta pantalla, y ahí el campo puede irse
    // **sin** llegar a avisar que perdió el foco; sin esto, la duración recién escrita se
    // perdía en silencio.
    //
    // Va **a nivel de pantalla y no dentro de cada tarjeta**, que es donde estaba: con la
    // lista perezosa, una tarjeta que sale de la vista al desplazarse también se desmonta, y
    // ahí el guardado se dispararía por desplazar. Acá se dispara una sola vez, al irse de
    // verdad, que es lo que siempre quiso decir.
    DisposableEffect(Unit) {
        onDispose { ORDEN_DE_LOS_BLOQUES.forEach { acciones.guardarBloque(it) } }
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
        LazyColumn(
            modifier = Modifier
                .padding(interior)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = Medidas.grande,
                end = Medidas.grande,
                top = Medidas.medio,
                bottom = Medidas.grande
            ),
            verticalArrangement = Arrangement.spacedBy(Medidas.medio)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Text(
                        text = AVISO_DURACIONES_ESTIMADAS,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Medidas.medio)
                    )
                }
            }

            item {
                Text(
                    text = if (estado.todoVacio) {
                        "Este paso es opcional: puedes dejarlo en blanco y seguir."
                    } else {
                        "Deja en blanco lo que no sepas."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // **Los bloques no se dibujan hasta que llegaron los guardados.** Antes se
            // dibujaban tres vacíos y después se rellenaban, así que un bloque guardado como
            // "no apto" componía su campo y sus cuatro chips para tirarlos al instante
            // siguiente. Ese trabajo desperdiciado, justo en el primer fotograma, era el
            // tirón que se sentía al entrar al paso.
            if (!estado.cargando) {
                items(estado.bloques, key = { it.tipo }) { bloque ->
                    BloqueDeDuracionCard(bloque, acciones)
                }
            }
        }
    }
}

/**
 * Un bloque: dónde se guarda, cuánto dura, y el switch de "no apto".
 *
 * Con "no apto" los campos **se ocultan en vez de deshabilitarse**. Un campo gris invita a
 * intentar tocarlo y a preguntarse por qué no responde; si no corresponde guardarlo así, no
 * hay ninguna duración que anotar y el campo no tiene nada que hacer ahí. Lo escrito no se
 * pierde: vuelve si se saca el switch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BloqueDeDuracionCard(
    bloque: BloqueDeDuracion,
    acciones: AccionesDuracion
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.chico)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nombreDelTipoDeDuracion(bloque.tipo),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        // Se lee en vivo tal como va a quedar guardado, con el singular y el
                        // plural ya resueltos: "1 día", no "1 días".
                        text = bloque.comoSeLee,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("No apto", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = !bloque.apto,
                    onCheckedChange = { acciones.cambiarApto(bloque.tipo, !it) }
                )
            }

            if (bloque.apto) {
                CampoNumerico(
                    valor = bloque.cantidad,
                    alCambiar = { acciones.cambiarCantidad(bloque.tipo, it) },
                    etiqueta = "Cuánto dura",
                    error = bloque.error,
                    // Al salir del campo, no en cada tecla: acá "3" a medio escribir de un
                    // "30" se ve igual que un bloque que alguien acaba de vaciar (8.4.1).
                    alSalirDelCampo = { acciones.guardarBloque(bloque.tipo) }
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                    UnidadDuracion.entries.forEach { unidad ->
                        FilterChip(
                            selected = bloque.unidad == unidad,
                            onClick = { acciones.cambiarUnidad(bloque.tipo, unidad) },
                            // En plural en los chips: es la etiqueta de la unidad, no una
                            // cantidad concreta.
                            label = { Text(nombreDeLaUnidad(unidad, cantidad = 2)) }
                        )
                    }
                }
            }
        }
    }
}

// --- Vistas previas ---

private fun estadoDeEjemplo(vacio: Boolean) = EstadoDuracion(
    bloques = listOf(
        BloqueDeDuracion(TipoDuracion.AMBIENTE, cantidad = if (vacio) "" else "2"),
        BloqueDeDuracion(
            TipoDuracion.REFRIGERADA,
            cantidad = if (vacio) "" else "1",
            unidad = UnidadDuracion.SEMANAS
        ),
        BloqueDeDuracion(TipoDuracion.CONGELADA, apto = vacio)
    ),
    cargando = false
)

@Preview(showBackground = true, name = "Duración - vacía")
@Composable
private fun VistaPreviaDuracionVacia() {
    ReposteriaTheme {
        PasoDuracion("Torta de manjar", estadoDeEjemplo(vacio = true), AccionesDuracion())
    }
}

@Preview(showBackground = true, name = "Duración - con datos")
@Composable
private fun VistaPreviaDuracionConDatos() {
    ReposteriaTheme {
        PasoDuracion("Torta de manjar", estadoDeEjemplo(vacio = false), AccionesDuracion())
    }
}
