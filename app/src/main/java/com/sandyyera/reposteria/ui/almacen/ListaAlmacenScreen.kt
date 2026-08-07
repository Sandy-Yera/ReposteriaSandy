package com.sandyyera.reposteria.ui.almacen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.data.db.dao.ArticuloConValor
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.ui.componentes.BarraBusqueda
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.componentes.ComboBuscable
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el almacén. */
data class AccionesAlmacen(
    val buscar: (String) -> Unit = {},
    val abrirAgregar: () -> Unit = {},
    val cambiarTipoDeArticulo: (Boolean) -> Unit = {},
    val buscarIngrediente: (String) -> Unit = {},
    val elegirIngrediente: (Ingrediente) -> Unit = {},
    val cambiarNombreSuelto: (String) -> Unit = {},
    val cambiarCantidadEscrita: (String) -> Unit = {},
    val guardarNuevo: () -> Unit = {},
    val abrirCambiarCantidad: (FilaDeAlmacen) -> Unit = {},
    val cambiarCantidadEnEdicion: (String) -> Unit = {},
    val guardarCantidad: () -> Unit = {},
    val pedirBorrado: (FilaDeAlmacen) -> Unit = {},
    val confirmarBorrado: () -> Unit = {},
    val cerrarDialogo: () -> Unit = {},
    val mensajeMostrado: () -> Unit = {},
    val abrirMenu: () -> Unit = {}
)

/** El almacén conectado a su ViewModel. */
@Composable
fun ListaAlmacenScreen(
    modelo: AlmacenViewModel,
    alAbrirMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val dialogo by modelo.dialogo.collectAsStateWithLifecycle()

    ListaAlmacen(
        estado = estado,
        dialogo = dialogo,
        acciones = AccionesAlmacen(
            buscar = modelo::buscar,
            abrirAgregar = modelo::abrirAgregar,
            cambiarTipoDeArticulo = modelo::cambiarTipoDeArticulo,
            buscarIngrediente = modelo::buscarIngrediente,
            elegirIngrediente = modelo::elegirIngrediente,
            cambiarNombreSuelto = modelo::cambiarNombreSuelto,
            cambiarCantidadEscrita = modelo::cambiarCantidadEscrita,
            guardarNuevo = modelo::guardarNuevo,
            abrirCambiarCantidad = modelo::abrirCambiarCantidad,
            cambiarCantidadEnEdicion = modelo::cambiarCantidadEnEdicion,
            guardarCantidad = modelo::guardarCantidad,
            pedirBorrado = modelo::pedirBorrado,
            confirmarBorrado = modelo::confirmarBorrado,
            cerrarDialogo = modelo::cerrarDialogo,
            mensajeMostrado = modelo::mensajeMostrado,
            abrirMenu = alAbrirMenu
        ),
        modifier = modifier
    )
}

/**
 * El inventario: qué hay guardado y cuánto queda (sección 14).
 *
 * Mismo patrón que las otras tres secciones —botón fijo arriba, buscador, lista debajo— y por el
 * mismo motivo: el botón fuera del área que se desplaza no se aleja al crecer la lista.
 *
 * **Tocar una fila cambia la cantidad**, que es lo que se hace todos los días; el único ícono
 * que queda es el de sacarla del almacén. Es la misma decisión de 8.4.1 #3 en ingredientes y
 * recetas: lo que se hace cien veces se toca, y lo que hay que mirar con cuidado lleva ícono.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaAlmacen(
    estado: EstadoAlmacen,
    dialogo: DialogoAlmacen,
    acciones: AccionesAlmacen,
    modifier: Modifier = Modifier
) {
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    LaunchedEffect(estado.mensaje) {
        val texto = estado.mensaje ?: return@LaunchedEffect
        try {
            anfitrionDeMensajes.showSnackbar(texto)
        } finally {
            // En `finally` por lo mismo que en las otras secciones: `showSnackbar` espera a que
            // el aviso se cierre solo, y si se cambia de sección antes esta corrutina se cancela
            // y el mensaje quedaría pendiente, reapareciendo al volver.
            acciones.mensajeMostrado()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Almacén") },
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
        verticalArrangement = Arrangement.spacedBy(Medidas.medio)
    ) {
        OutlinedButton(
            onClick = acciones.abrirAgregar,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Medidas.objetivoTactil)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("  Agregar al almacén")
        }

        if (estado.hayArticulos) {
            ValorDelAlmacen(estado)
            BarraBusqueda(
                texto = estado.busqueda,
                alCambiar = acciones.buscar,
                marcador = "Buscar en el almacén"
            )
        }

        when {
            estado.almacenVacio -> MensajeCentrado(
                titulo = "El almacén está vacío",
                detalle = "Agrega lo que tengas guardado: ingredientes del catálogo, y también " +
                    "cajas, cintas o velas que no entran en ninguna receta."
            )
            estado.busquedaSinResultados -> MensajeCentrado(
                titulo = "No encontré eso",
                detalle = "Prueba con otra palabra."
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Medidas.chico),
                contentPadding = PaddingValues(bottom = Medidas.grande)
            ) {
                items(estado.visibles, key = { it.id }) { fila ->
                    FilaDeArticulo(
                        fila = fila,
                        alTocar = { acciones.abrirCambiarCantidad(fila) },
                        alSacar = { acciones.pedirBorrado(fila) }
                    )
                }
            }
        }
    }

    when (dialogo) {
        is DialogoAlmacen.Ninguno -> Unit
        is DialogoAlmacen.Agregar -> DialogoAgregarAlAlmacen(dialogo, acciones)
        is DialogoAlmacen.CambiarCantidad -> AlertDialog(
            onDismissRequest = acciones.cerrarDialogo,
            title = { Text("¿Cuánto queda de '${dialogo.fila.nombre}'?") },
            text = {
                CampoNumerico(
                    valor = dialogo.cantidad,
                    alCambiar = acciones.cambiarCantidadEnEdicion,
                    etiqueta = if (dialogo.fila.esIngrediente) "Gramos" else "Unidades",
                    // El 0 es un dato y no un error: "no queda nada" es justo lo que uno viene
                    // a anotar antes de salir a comprar.
                    ayuda = "Puede ser 0 si se acabó"
                )
            },
            confirmButton = {
                TextButton(onClick = acciones.guardarCantidad, enabled = dialogo.puedeGuardar) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
            }
        )
        is DialogoAlmacen.ConfirmarBorrado -> AlertDialog(
            onDismissRequest = acciones.cerrarDialogo,
            title = { Text("¿Sacar '${dialogo.fila.nombre}' del almacén?") },
            text = {
                Text(
                    if (dialogo.fila.esIngrediente) {
                        // Se dice con todas las letras: son dos cosas distintas y confundirlas
                        // haría creer que esto borra un ingrediente en uso.
                        "Deja de llevarle la cuenta acá. El ingrediente sigue en el catálogo y " +
                            "en las recetas que lo usan."
                    } else {
                        "Se quita del inventario. Puedes volver a agregarlo cuando quieras."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = acciones.confirmarBorrado, enabled = !dialogo.borrando) {
                    Text(text = "Sacar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
            }
        )
    }
    }
}

/**
 * Lo que vale todo lo guardado, arriba y siempre a la vista.
 *
 * Dice **cuántas cosas quedaron fuera del total** en vez de contarlas como 0: una caja no tiene
 * valor por gramo, y sumarla como cero presentaría "el valor del almacén" ignorando en silencio
 * parte de las filas — un número que se cree y está mal.
 */
@Composable
private fun ValorDelAlmacen(estado: EstadoAlmacen) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(Medidas.medio)) {
            Text("Valor de lo guardado", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "$${formatearNumero(estado.valorTotal)}",
                style = MaterialTheme.typography.headlineMedium
            )
            if (estado.sinValor > 0) {
                Text(
                    text = "No incluye ${estado.sinValor} " +
                        if (estado.sinValor == 1) "artículo sin valor por gramo"
                        else "artículos sin valor por gramo",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** Una cosa guardada: cuánto queda, cuánto vale y desde cuándo no se revisa. */
@Composable
private fun FilaDeArticulo(
    fila: FilaDeAlmacen,
    alTocar: () -> Unit,
    alSacar: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                    .clickable(onClick = alTocar)
                    .padding(vertical = Medidas.chico)
            ) {
                Text(text = fila.nombre, style = MaterialTheme.typography.titleMedium)
                Text(
                    // La cuenta completa, igual que en las líneas de una receta: así un valor
                    // por gramo mal puesto salta a la vista acá también.
                    text = fila.valor
                        ?.let { "${fila.cuantoQueda} · $${formatearNumero(it)}" }
                        ?: fila.cuantoQueda,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = alSacar) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Sacar ${fila.nombre} del almacén",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Agregar algo al almacén: un ingrediente del catálogo, o algo suelto.
 *
 * Los dos caminos van en **un cuadro con dos chips** y no en dos botones separados: desde afuera
 * es la misma acción —anotar algo que tengo— y cuál de las dos corresponde se sabe recién al
 * buscarlo. Con dos botones, darse cuenta a mitad de camino obligaría a cerrar y volver a
 * empezar.
 */
@Composable
private fun DialogoAgregarAlAlmacen(
    estado: DialogoAlmacen.Agregar,
    acciones: AccionesAlmacen
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("Agregar al almacén") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                    FilterChip(
                        selected = !estado.suelto,
                        onClick = { acciones.cambiarTipoDeArticulo(false) },
                        label = { Text("Un ingrediente") }
                    )
                    FilterChip(
                        selected = estado.suelto,
                        onClick = { acciones.cambiarTipoDeArticulo(true) },
                        label = { Text("Otra cosa") }
                    )
                }

                if (estado.suelto) {
                    Text(
                        text = "Cajas, cintas, velas: cosas que tienes guardadas y que no " +
                            "entran en ninguna receta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = estado.nombreSuelto,
                        onValueChange = acciones.cambiarNombreSuelto,
                        label = { Text("¿Qué es?") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = estado.errorNombre != null,
                        supportingText = { estado.errorNombre?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        )
                    )
                } else if (estado.elegido == null) {
                    ComboBuscable(
                        opciones = estado.candidatos,
                        textoDe = { it.nombre },
                        busqueda = estado.busqueda,
                        alBuscar = acciones.buscarIngrediente,
                        alElegir = acciones.elegirIngrediente,
                        marcador = "Buscar ingrediente",
                        motivoNoDisponible = estado::motivoNoDisponible
                    )
                } else {
                    Text(text = estado.elegido.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "$${formatearNumero(estado.elegido.valorPorGramo)} por gramo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // El campo de cantidad aparece **después** de saber de qué, igual que en el
                // cuadro de agregar un ingrediente a una receta: pedir los dos a la vez obliga
                // a decidir cuánto antes de saber de qué.
                if (estado.suelto || estado.elegido != null) {
                    CampoNumerico(
                        valor = estado.cantidad,
                        alCambiar = acciones.cambiarCantidadEscrita,
                        etiqueta = if (estado.suelto) "¿Cuántas?" else "¿Cuántos gramos?",
                        error = estado.errorCantidad,
                        ayuda = "Puede ser 0 si se acabó"
                    )
                }

                estado.rechazo?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.guardarNuevo, enabled = estado.puedeGuardar) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

/** El texto que ocupa el lugar de la lista cuando no hay nada que mostrar. */
@Composable
private fun MensajeCentrado(titulo: String, detalle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Medidas.grande),
        verticalArrangement = Arrangement.spacedBy(Medidas.chico)
    ) {
        Text(text = titulo, style = MaterialTheme.typography.titleMedium)
        Text(
            text = detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- Vistas previas ---

private fun articulo(id: Long, nombre: String, cantidad: Double, valor: Double?) =
    FilaDeAlmacen(
        ArticuloConValor(
            id = id,
            ingredienteId = if (valor != null) id else null,
            nombre = nombre,
            cantidad = cantidad,
            valorPorGramo = valor,
            actualizadoEn = 0
        )
    )

private val almacenDeEjemplo = EstadoAlmacen(
    visibles = listOf(
        articulo(1, "Harina", 2500.0, 1.2),
        articulo(2, "Azúcar", 800.0, 1.5),
        articulo(3, "Cajas de torta", 12.0, null)
    ),
    hayArticulos = true,
    cargando = false
)

@Preview(showBackground = true, name = "Almacén - claro")
@Composable
private fun AlmacenClaro() {
    ReposteriaTheme(oscuro = false) {
        ListaAlmacen(almacenDeEjemplo, DialogoAlmacen.Ninguno, AccionesAlmacen())
    }
}

@Preview(showBackground = true, name = "Almacén - oscuro")
@Composable
private fun AlmacenOscuro() {
    ReposteriaTheme(oscuro = true) {
        ListaAlmacen(almacenDeEjemplo, DialogoAlmacen.Ninguno, AccionesAlmacen())
    }
}

@Preview(showBackground = true, name = "Almacén - vacío")
@Composable
private fun AlmacenVacio() {
    ReposteriaTheme {
        ListaAlmacen(
            EstadoAlmacen(cargando = false),
            DialogoAlmacen.Ninguno,
            AccionesAlmacen()
        )
    }
}

@Preview(showBackground = true, name = "Almacén - agregando otra cosa")
@Composable
private fun AlmacenAgregandoSuelto() {
    ReposteriaTheme {
        ListaAlmacen(
            almacenDeEjemplo,
            DialogoAlmacen.Agregar(suelto = true, nombreSuelto = "Cintas", cantidad = "20"),
            AccionesAlmacen()
        )
    }
}
