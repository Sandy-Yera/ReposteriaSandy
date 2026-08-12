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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.logica.formato.formatearMonto
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.ui.componentes.BarraBusqueda
import com.sandyyera.reposteria.ui.componentes.MensajeCentrado
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde la sección de recetas. */
data class AccionesRecetas(
    val buscar: (String) -> Unit = {},
    val pedirAlta: () -> Unit = {},
    val cambiarTitulo: (String) -> Unit = {},
    val guardar: () -> Unit = {},
    val pedirBorrado: (Receta) -> Unit = {},
    val confirmarBorrado: () -> Unit = {},
    val cerrarDialogo: () -> Unit = {},
    val mensajeMostrado: () -> Unit = {},
    val abrirMenu: () -> Unit = {},
    // Solo el id: abrir una receta no necesita el resto de sus datos, y así la puede
    // abrir tanto un toque en la tarjeta como el aviso de "recién creada".
    val abrir: (Long) -> Unit = {},
    val avisarBloqueada: (Receta) -> Unit = {}
)

/** La pantalla de recetas conectada a su ViewModel. */
@Composable
fun ListaRecetasScreen(
    modelo: RecetasViewModel,
    alAbrirMenu: () -> Unit,
    alAbrirReceta: (Long) -> Unit,
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
                            // Una receta repetida no se abre: el toque lleva al aviso, y
                            // solo queda borrarla.
                            alAbrir = {
                                if (fila.repetida) acciones.avisarBloqueada(fila.receta)
                                else acciones.abrir(fila.receta.id)
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
 *
 * **Ya no hay lápiz para renombrar.** El título se cambia desde adentro de la receta,
 * tocándolo en el encabezado: acá el toque está tomado por abrirla, que es lo que se hace
 * cien veces por cada vez que se le cambia el nombre.
 */
@Composable
private fun TarjetaReceta(
    fila: RecetaConCosto,
    alAbrir: () -> Unit,
    alBorrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        // Tocar la tarjeta abre la receta: abrir es LA acción de una receta y "tocar para
        // abrir" se entiende sin explicación.
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = Medidas.minimo)
                        )
                        Text(
                            // Sin el lápiz, este texto y el ícono son lo único que avisa
                            // antes de tocarla; el aviso completo llega al tocar.
                            text = "Título repetido: solo se puede eliminar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        // El costo se lee de la base con el precio actual de cada
                        // ingrediente (decisión #3), así que sube solo cuando sube uno.
                        // Se mira `tieneIngredientes` y no el costo: un ingrediente puede
                        // valer 0 a propósito, y una receta hecha solo de esos costaba 0
                        // pero no estaba vacía. Decirle "todavía sin ingredientes" a algo
                        // que sí los tiene manda a buscar un problema que no existe.
                        text = if (fila.tieneIngredientes) {
                            "Cuesta $${formatearMonto(fila.costoTotal)} hacerla"
                        } else {
                            "Todavía sin ingredientes"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // El aviso de parte va **además** del costo y no en su lugar: son dos cosas
                // distintas y las dos importan. En una repetida no se dibuja porque esa no se
                // puede abrir, y mandarla a entrar sería mandarla a una puerta cerrada.
                if (fila.tieneAvisoDeParte && !fila.repetida) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = Medidas.minimo)
                        )
                        Text(
                            // Dice qué hacer y no solo qué pasó: el detalle está adentro, y
                            // desde acá lo único accionable es entrar (8.11.3).
                            text = "Una receta que usa cambió. Entra para decidir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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

/**
 * El cuadro para crear una receta.
 *
 * Solo crea: el título de una receta que ya existe se cambia desde adentro, tocándolo en el
 * encabezado del paso de cantidades.
 */
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
        title = { Text("Nueva receta") },
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
 * La advertencia antes de borrar una receta (6.3 y 8.11.4).
 *
 * Lo que se va **con** ella se enumera sin consultar nada: todo lo de una receta vive dentro de
 * ella. Lo que sí hay que ir a buscar es qué otras recetas la usan como parte, y por eso ese
 * pedazo espera a la consulta.
 *
 * **El tono de esas dos mitades es distinto a propósito.** Lo de adentro se pierde; lo de
 * afuera no: las copias son independientes y siguen ahí enteras. Lo que les queda es un aviso
 * pendiente preguntando qué hacer, y decirlo antes es la diferencia entre entenderlo y
 * encontrárselo meses después sin explicación.
 *
 * **Los empleados van con nombre y apellido** (7.1). El aviso ya decía que se iban los sueldos,
 * pero sin decir de quién eso es una frase y no una advertencia. Lo pidió Sandy, y de paso
 * previó el caso feo: *"si tuviera 100 empleados distintos, el mensaje sería largo"*. Por eso el
 * cuerpo entero se desplaza y los botones se quedan quietos — con cien nombres, un cuadro que
 * crece empuja "Eliminar" fuera de la pantalla o, peor, lo deja justo donde estaba "Cancelar".
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
            // **El desplazamiento es de todo el cuerpo y no de cada lista.** Antes solo se movía
            // la lista de recetas que la usan, así que con muchos empleados el cuadro se estiraba
            // igual. Un solo `verticalScroll` acá adentro deja los botones del `AlertDialog`
            // fijos abajo, que es lo que Sandy pidió expresamente.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                Text("Se van con ella sus ingredientes, su rendimiento, sus precios y sus pasos.")

                when {
                    // `null` es "todavía consultando" y no "no la tiene nadie": mientras tanto el
                    // botón está apagado, porque confirmar acá sería confirmar media advertencia.
                    estado.empleados == null -> Text(
                        text = "Revisando si algún empleado la tiene asignada…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    estado.empleados.isNotEmpty() -> Column {
                        Text(
                            text = "Pierden su sueldo por esta receta " +
                                "${estado.empleados.size} " +
                                if (estado.empleados.size == 1) "empleado:" else "empleados:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        estado.empleados.forEach {
                            Text(
                                text = "• $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "Sus otras recetas no se tocan; lo que se borra es cuánto " +
                                "se llevaban por esta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> Text(
                        text = "Ningún empleado la tiene asignada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when {
                    estado.usadaPor == null -> Text(
                        text = "Revisando si otras recetas la usan…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    estado.usadaPor.isNotEmpty() -> Column {
                        Text(
                            text = "La usan como parte ${estado.usadaPor.size} " +
                                if (estado.usadaPor.size == 1) "receta:" else "recetas:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        estado.usadaPor.forEach {
                            Text(
                                text = "• ${it.titulo}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "No pierden nada: sus copias siguen tal cual. Lo que les " +
                                "queda es un aviso preguntando qué hacer con esa parte.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Esto no se puede deshacer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = alConfirmar, enabled = estado.sePuedeBorrar) {
                Text(text = "Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
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
            DialogoReceta.ConfirmarBorrado(
                receta = Receta(id = 1, titulo = "Torta de manjar"),
                usadaPor = emptyList(),
                empleados = emptyList()
            ),
            AccionesRecetas()
        )
    }
}

@Preview(showBackground = true, name = "Recetas - borrando una que otras usan")
@Composable
private fun RecetasBorrandoUnaUsada() {
    ReposteriaTheme {
        ListaRecetas(
            estadoDeEjemplo(),
            DialogoReceta.ConfirmarBorrado(
                receta = Receta(id = 1, titulo = "Bizcocho"),
                usadaPor = listOf(
                    Receta(id = 2, titulo = "Torta de manjar"),
                    Receta(id = 3, titulo = "Mil hojas")
                ),
                empleados = emptyList()
            ),
            AccionesRecetas()
        )
    }
}

@Preview(showBackground = true, name = "Recetas - borrando una que tienen empleados")
@Composable
private fun RecetasBorrandoUnaDeEmpleados() {
    ReposteriaTheme {
        ListaRecetas(
            estadoDeEjemplo(),
            DialogoReceta.ConfirmarBorrado(
                receta = Receta(id = 1, titulo = "Torta de manjar"),
                usadaPor = emptyList(),
                empleados = listOf("Ana", "Carla", "Modelo estándar")
            ),
            AccionesRecetas()
        )
    }
}

@Preview(showBackground = true, name = "Recetas - con aviso de parte")
@Composable
private fun RecetasConAvisoDeParte() {
    ReposteriaTheme {
        ListaRecetas(
            estadoDeEjemplo(
                visibles = listOf(
                    RecetaConCosto(
                        receta = Receta(id = 1, titulo = "Torta de manjar"),
                        costoTotal = 4520.0,
                        tieneIngredientes = true,
                        tieneAvisoDeParte = true
                    )
                )
            ),
            DialogoReceta.Ninguno,
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
