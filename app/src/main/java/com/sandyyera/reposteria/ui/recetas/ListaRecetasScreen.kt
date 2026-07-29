package com.sandyyera.reposteria.ui.recetas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.ui.componentes.BarraBusqueda
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde la sección de recetas. */
data class AccionesRecetas(
    val buscar: (String) -> Unit = {},
    val pedirAlta: () -> Unit = {},
    val cambiarTitulo: (String) -> Unit = {},
    val guardar: () -> Unit = {},
    val editar: (Receta) -> Unit = {},
    val pedirBorrado: (Receta) -> Unit = {},
    val confirmarBorrado: () -> Unit = {},
    val cerrarDialogo: () -> Unit = {},
    val mensajeMostrado: () -> Unit = {},
    val abrirMenu: () -> Unit = {},
    val abrir: (Receta) -> Unit = {},
    val avisarBloqueada: (Receta) -> Unit = {}
)

/** La pantalla de recetas conectada a su ViewModel. */
@Composable
fun ListaRecetasScreen(
    modelo: RecetasViewModel,
    alAbrirMenu: () -> Unit,
    alAbrirReceta: (Receta) -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    // Por su propio canal, sin pasar por las consultas de costo (ver RecetasViewModel).
    val dialogo by modelo.dialogo.collectAsStateWithLifecycle()
    val recienCreada by modelo.recienCreada.collectAsStateWithLifecycle()

    // Una receta recién creada se abre sola para empezar a llenarla de inmediato.
    LaunchedEffect(recienCreada) {
        val id = recienCreada ?: return@LaunchedEffect
        modelo.recetaAbierta()
        alAbrirReceta(id)
    }

    val acciones = remember(modelo, alAbrirMenu, alAbrirReceta) {
        AccionesRecetas(
            buscar = modelo::buscar,
            pedirAlta = modelo::abrirAlta,
            cambiarTitulo = modelo::cambiarTitulo,
            guardar = modelo::guardar,
            editar = modelo::abrirCambioDeTitulo,
            pedirBorrado = modelo::pedirBorrado,
            confirmarBorrado = modelo::confirmarBorrado,
            cerrarDialogo = modelo::cerrarDialogo,
            mensajeMostrado = modelo::mensajeMostrado,
            abrirMenu = alAbrirMenu,
            abrir = alAbrirReceta,
            avisarBloqueada = modelo::avisarBloqueada
        )
    }

    ListaRecetas(estado = estado, dialogo = dialogo, acciones = acciones, modifier = modifier)
}

/**
 * El dibujo de la lista de recetas: botón fijo arriba, buscador, y las recetas debajo.
 *
 * Mismo patrón que ingredientes (8.1): el botón va fuera del área que se desplaza, porque
 * si no habría que subir hasta arriba cada vez que se quiere crear una.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaRecetas(
    estado: EstadoRecetas,
    dialogo: DialogoReceta,
    acciones: AccionesRecetas,
    modifier: Modifier = Modifier
) {
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    LaunchedEffect(estado.mensaje) {
        val texto = estado.mensaje ?: return@LaunchedEffect
        anfitrionDeMensajes.showSnackbar(texto)
        acciones.mensajeMostrado()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Recetas") },
                navigationIcon = {
                    IconButton(onClick = acciones.abrirMenu) {
                        Icon(Icons.Default.Menu, contentDescription = "Abrir el menú")
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
                .padding(horizontal = Medidas.grande)
                .padding(top = Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.chico)
        ) {
            Button(
                onClick = acciones.pedirAlta,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medidas.objetivoTactil)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(Medidas.chico))
                Text("Nueva receta")
            }

            BarraBusqueda(
                texto = estado.busqueda,
                alCambiar = acciones.buscar,
                marcador = "Buscar receta"
            )

            when {
                estado.cargando -> Spacer(Modifier.weight(1f))

                estado.catalogoVacio -> MensajeCentrado(
                    titulo = "Todavía no hay recetas",
                    detalle = "Toca «Nueva receta» para crear la primera.",
                    modifier = Modifier.weight(1f)
                )

                estado.busquedaSinResultados -> MensajeCentrado(
                    titulo = "Nada coincide con «${estado.busqueda}»",
                    detalle = "Puedes buscar por cualquier parte del título, y no hace " +
                        "falta escribir las tildes.",
                    modifier = Modifier.weight(1f)
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Medidas.chico),
                    contentPadding = PaddingValues(top = Medidas.chico, bottom = Medidas.grande)
                ) {
                    items(estado.visibles, key = { it.receta.id }) { fila ->
                        TarjetaReceta(
                            fila = fila,
                            // Una receta repetida no se abre ni se renombra: los dos
                            // caminos llevan al aviso, y solo queda borrarla.
                            alAbrir = {
                                if (fila.repetida) acciones.avisarBloqueada(fila.receta)
                                else acciones.abrir(fila.receta)
                            },
                            alEditar = {
                                if (fila.repetida) acciones.avisarBloqueada(fila.receta)
                                else acciones.editar(fila.receta)
                            },
                            alBorrar = { acciones.pedirBorrado(fila.receta) }
                        )
                    }
                }
            }
        }

        when (dialogo) {
            is DialogoReceta.Ninguno -> Unit

            is DialogoReceta.Formulario -> FormularioReceta(
                estado = dialogo,
                alCambiar = acciones.cambiarTitulo,
                alGuardar = acciones.guardar,
                alCerrar = acciones.cerrarDialogo
            )

            is DialogoReceta.Bloqueada -> AlertDialog(
                onDismissRequest = acciones.cerrarDialogo,
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("'${dialogo.receta.titulo}' está repetida") },
                text = { Text(AVISO_RECETA_REPETIDA) },
                confirmButton = {
                    TextButton(onClick = acciones.cerrarDialogo) { Text("Entendido") }
                }
            )

            is DialogoReceta.ConfirmarBorrado -> ConfirmarBorradoReceta(
                estado = dialogo,
                alConfirmar = acciones.confirmarBorrado,
                alCerrar = acciones.cerrarDialogo
            )
        }
    }
}

/**
 * Una receta de la lista, con lo que cuesta hacerla ahora mismo.
 *
 * La tarjeta va en el rosa pastel del tema (`tertiaryContainer`) y no en el crema del
 * resto. Es el único lugar con color propio a propósito: las recetas son el corazón de la
 * app y conviene que la lista se reconozca de un vistazo. El ícono de eliminar sigue en
 * frambuesa encima del rosa, con contraste medido (4,57:1), así que no se pierde entre la
 * decoración.
 */
@Composable
private fun TarjetaReceta(
    fila: RecetaConCosto,
    alAbrir: () -> Unit,
    alEditar: () -> Unit,
    alBorrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        // Tocar la tarjeta abre la receta. Acá sí se usa un gesto sin botón propio, al
        // revés que en ingredientes: abrir es LA acción de una receta, "tocar para abrir"
        // se entiende sin explicación, y un cuarto botón dejaría la fila apretada.
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = alAbrir),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Medidas.objetivoTactil)
                .padding(start = Medidas.medio, end = Medidas.chico),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Medidas.chico)
            ) {
                Text(
                    text = fila.receta.titulo,
                    style = MaterialTheme.typography.titleMedium
                )
                if (fila.repetida) {
                    Text(
                        text = "Título repetido: solo se puede eliminar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        // El costo se lee de la base con el precio actual de cada
                        // ingrediente (decisión #3), así que sube solo cuando sube uno.
                        text = if (fila.costoTotal > 0) {
                            "Cuesta $${formatearNumero(fila.costoTotal)} hacerla"
                        } else {
                            "Todavía sin ingredientes"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = alEditar) {
                Icon(
                    // Se deja tocable aunque esté repetida: un botón muerto no explica
                    // nada, y así el toque lleva al aviso que sí explica por qué no se
                    // puede. El color tenue avisa antes de tocarlo.
                    imageVector = if (fila.repetida) Icons.Default.Warning else Icons.Default.Edit,
                    contentDescription = if (fila.repetida) {
                        "Por qué no se puede editar ${fila.receta.titulo}"
                    } else {
                        "Cambiar el título de ${fila.receta.titulo}"
                    },
                    tint = if (fila.repetida) MaterialTheme.colorScheme.error
                    else LocalContentColor.current
                )
            }

            IconButton(onClick = alBorrar) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar ${fila.receta.titulo}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** El cuadro para crear una receta o cambiarle el título. */
@Composable
private fun FormularioReceta(
    estado: DialogoReceta.Formulario,
    alCambiar: (String) -> Unit,
    alGuardar: () -> Unit,
    alCerrar: () -> Unit
) {
    val error = estado.error

    AlertDialog(
        onDismissRequest = alCerrar,
        title = {
            Text(if (estado.editando != null) "Cambiar el título" else "Nueva receta")
        },
        text = {
            OutlinedTextField(
                value = estado.titulo,
                onValueChange = alCambiar,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título") },
                singleLine = true,
                isError = error != null,
                supportingText = { if (error != null) Text(error) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                )
            )
        },
        confirmButton = {
            TextButton(onClick = alGuardar, enabled = estado.puedeGuardar) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}

/**
 * La advertencia antes de borrar una receta (6.3).
 *
 * Enumera lo que se va con ella sin consultar nada: a diferencia de un ingrediente —que
 * puede estar usado en recetas que hay que ir a buscar— todo lo de una receta vive dentro
 * de ella. Lo que sí conviene nombrar son los sueldos, porque están en otra sección y es
 * fácil olvidar que dependen de esto.
 */
@Composable
private fun ConfirmarBorradoReceta(
    estado: DialogoReceta.ConfirmarBorrado,
    alConfirmar: () -> Unit,
    alCerrar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("¿Eliminar '${estado.receta.titulo}'?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                Text("Se van con ella sus ingredientes, su rendimiento, sus precios y sus pasos.")
                Text(
                    text = "También los sueldos que algún empleado tuviera asignados para " +
                        "esta receta.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Esto no se puede deshacer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = alConfirmar, enabled = !estado.borrando) {
                Text(text = "Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}

/** El texto que ocupa el lugar de la lista cuando no hay nada que mostrar. */
@Composable
private fun MensajeCentrado(
    titulo: String,
    detalle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Medidas.chico, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// --- Vistas previas ---

private val recetasDeEjemplo = listOf(
    RecetaConCosto(Receta(id = 1, titulo = "Torta de manjar"), 12400.0),
    RecetaConCosto(Receta(id = 2, titulo = "Bizcocho de vainilla"), 3250.5),
    RecetaConCosto(Receta(id = 3, titulo = "Kuchen de nuez"), 0.0)
)

private fun estadoDeEjemplo(
    visibles: List<RecetaConCosto> = recetasDeEjemplo,
    hayRecetas: Boolean = true,
    busqueda: String = ""
) = EstadoRecetas(
    visibles = visibles,
    hayRecetas = hayRecetas,
    busqueda = busqueda,
    cargando = false
)

@Preview(showBackground = true, name = "Recetas - claro")
@Composable
private fun RecetasClaro() {
    ReposteriaTheme(oscuro = false) {
        ListaRecetas(estadoDeEjemplo(), DialogoReceta.Ninguno, AccionesRecetas())
    }
}

@Preview(showBackground = true, name = "Recetas - oscuro")
@Composable
private fun RecetasOscuro() {
    ReposteriaTheme(oscuro = true) {
        ListaRecetas(estadoDeEjemplo(), DialogoReceta.Ninguno, AccionesRecetas())
    }
}

@Preview(showBackground = true, name = "Recetas - vacío")
@Composable
private fun RecetasVacio() {
    ReposteriaTheme {
        ListaRecetas(
            estadoDeEjemplo(visibles = emptyList(), hayRecetas = false),
            DialogoReceta.Ninguno,
            AccionesRecetas()
        )
    }
}

@Preview(showBackground = true, name = "Recetas - borrando")
@Composable
private fun RecetasBorrando() {
    ReposteriaTheme {
        ListaRecetas(
            estadoDeEjemplo(),
            DialogoReceta.ConfirmarBorrado(Receta(id = 1, titulo = "Torta de manjar")),
            AccionesRecetas()
        )
    }
}

@Preview(showBackground = true, name = "Recetas - una repetida bloqueada")
@Composable
private fun RecetasConRepetida() {
    ReposteriaTheme {
        ListaRecetas(
            estadoDeEjemplo(
                visibles = recetasDeEjemplo + RecetaConCosto(
                    receta = Receta(id = 4, titulo = "torta de manjar"),
                    costoTotal = 0.0,
                    repetida = true
                )
            ),
            DialogoReceta.Ninguno,
            AccionesRecetas()
        )
    }
}
