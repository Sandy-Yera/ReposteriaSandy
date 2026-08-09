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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.ui.componentes.BarraBusqueda
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el almacén. */
data class AccionesAlmacen(
    val buscar: (String) -> Unit = {},
    val abrirAgregar: () -> Unit = {},
    val cambiarNombre: (String) -> Unit = {},
    val cambiarEsObjeto: (Boolean) -> Unit = {},
    val cambiarVaEnRecetas: (Boolean) -> Unit = {},
    val cambiarCantidadNueva: (String) -> Unit = {},
    val cambiarPrecioNuevo: (String) -> Unit = {},
    val cambiarDetallesNuevos: (String) -> Unit = {},
    val guardarNuevo: () -> Unit = {},
    val conservarElPrecioGuardado: () -> Unit = {},
    val reemplazarElPrecio: () -> Unit = {},
    val cerrarLaDisputaDePrecio: () -> Unit = {},
    val abrirEdicion: (FilaDeAlmacen) -> Unit = {},
    val cambiarModoDeEdicion: (ModoDeEdicion) -> Unit = {},
    val cambiarCantidadEnEdicion: (String) -> Unit = {},
    val cambiarLoQueSeUso: (String) -> Unit = {},
    val cambiarDetallesEnEdicion: (String) -> Unit = {},
    val guardarEdicion: () -> Unit = {},
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
            cambiarNombre = modelo::cambiarNombre,
            cambiarEsObjeto = modelo::cambiarEsObjeto,
            cambiarVaEnRecetas = modelo::cambiarVaEnRecetas,
            cambiarCantidadNueva = modelo::cambiarCantidadNueva,
            cambiarPrecioNuevo = modelo::cambiarPrecioNuevo,
            cambiarDetallesNuevos = modelo::cambiarDetallesNuevos,
            guardarNuevo = { modelo.guardarNuevo() },
            conservarElPrecioGuardado = modelo::conservarElPrecioGuardado,
            reemplazarElPrecio = modelo::reemplazarElPrecio,
            cerrarLaDisputaDePrecio = modelo::cerrarLaDisputaDePrecio,
            abrirEdicion = modelo::abrirEdicion,
            cambiarModoDeEdicion = modelo::cambiarModoDeEdicion,
            cambiarCantidadEnEdicion = modelo::cambiarCantidadEnEdicion,
            cambiarLoQueSeUso = modelo::cambiarLoQueSeUso,
            cambiarDetallesEnEdicion = modelo::cambiarDetallesEnEdicion,
            guardarEdicion = modelo::guardarEdicion,
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
 * Mismo patrón que las otras secciones —botón fijo arriba, buscador, lista debajo— y por el mismo
 * motivo: el botón fuera del área que se desplaza no se aleja al crecer la lista.
 *
 * **Tocar una fila la edita**, que es lo que se hace todos los días; el único ícono que queda es
 * el de sacarla del almacén. Es la misma decisión de 8.4.1 #3 en ingredientes y recetas: lo que se
 * hace cien veces se toca, y lo que hay que mirar con cuidado lleva ícono.
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
                detalle = "Agrega lo que tengas guardado: harina, azúcar, y también cajas, " +
                    "cintas o velas. Todo lo que anotes acá aparece además en Ingredientes."
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
                        alTocar = { acciones.abrirEdicion(fila) },
                        alSacar = { acciones.pedirBorrado(fila) }
                    )
                }
            }
        }
    }

    when (dialogo) {
        is DialogoAlmacen.Ninguno -> Unit
        is DialogoAlmacen.Agregar -> DialogoAgregarAlAlmacen(dialogo, acciones)
        is DialogoAlmacen.CambiarCantidad -> DialogoEditarArticulo(dialogo, acciones)
        is DialogoAlmacen.ConfirmarBorrado -> AlertDialog(
            onDismissRequest = acciones.cerrarDialogo,
            title = { Text("¿Sacar '${dialogo.fila.nombre}' del almacén?") },
            text = {
                // Se dice con todas las letras: son dos cosas distintas y confundirlas haría
                // creer que esto borra un ingrediente en uso.
                Text(
                    "Deja de llevarle la cuenta acá. Sigue en Ingredientes y en las recetas " +
                        "que lo usan."
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
 * Dice **cuántas cosas quedaron fuera del total** en vez de contarlas como 0: sumar como cero algo
 * cuyo precio no se conoce presentaría "el valor del almacén" ignorando en silencio parte de las
 * filas — un número que se cree y está mal.
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
                        if (estado.sinValor == 1) "artículo sin precio"
                        else "artículos sin precio",
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
                    // La cuenta completa, igual que en las líneas de una receta: así un precio
                    // mal puesto salta a la vista acá también.
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
 * Anotar algo que hay: nombre, cuánto, cuánto cuesta y —si se quiere— dónde se compró (14.5, 14.6).
 *
 * **Un solo camino y no dos.** Lo que antes eran las pestañas "un ingrediente" / "otra cosa" ahora
 * son dos casillas independientes, porque son dos preguntas distintas y encadenarlas se equivocaba
 * justo en el caso interesante: una caja de torta se cuenta por unidad **y** va en la receta.
 *
 * El cuadro **se desplaza**: con el teclado abierto no cabe entero, y sin desplazamiento el botón
 * de guardar quedaría fuera de la pantalla — que es exactamente lo que Sandy pidió arreglar en la
 * calculadora de valor por gramo.
 */
@Composable
private fun DialogoAgregarAlAlmacen(
    estado: DialogoAlmacen.Agregar,
    acciones: AccionesAlmacen
) {
    estado.precioEnDisputa?.let { disputa ->
        DialogoPrecioDistinto(disputa, acciones)
        return
    }

    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("Agregar al almacén") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                OutlinedTextField(
                    value = estado.nombre,
                    onValueChange = acciones.cambiarNombre,
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

                CasillaConExplicacion(
                    marcada = estado.esObjeto,
                    alCambiar = acciones.cambiarEsObjeto,
                    titulo = "Se cuenta por unidad",
                    explicacion = "Cajas, cintas, velas. Sin marcar se cuenta en gramos."
                )
                CasillaConExplicacion(
                    marcada = estado.vaEnRecetas,
                    alCambiar = acciones.cambiarVaEnRecetas,
                    titulo = "Se puede usar en recetas",
                    explicacion = "Aparece al agregar ingredientes a una receta."
                )

                // El aviso va acá y no al descubrirlo comparando totales: un objeto entra a la
                // receta con 0 gramos, así que no suma al costo (14.5).
                if (estado.esObjeto && estado.vaEnRecetas) {
                    Text(
                        text = "Ojo: lo que se cuenta por unidad entra a la receta sin peso, " +
                            "así que no suma al costo de los ingredientes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                CampoNumerico(
                    valor = estado.cantidad,
                    alCambiar = acciones.cambiarCantidadNueva,
                    etiqueta = if (estado.esObjeto) "¿Cuántas tienes?" else "¿Cuántos gramos?",
                    error = estado.errorCantidad,
                    ayuda = "Puede ser 0 si se acabó",
                    accionDelTeclado = ImeAction.Next
                )
                CampoNumerico(
                    valor = estado.precio,
                    alCambiar = acciones.cambiarPrecioNuevo,
                    etiqueta = "¿Cuánto cuesta cada ${estado.unidad}?",
                    error = estado.errorPrecio,
                    accionDelTeclado = ImeAction.Next
                )

                OutlinedTextField(
                    value = estado.detalles,
                    onValueChange = acciones.cambiarDetallesNuevos,
                    label = { Text("Detalles (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    supportingText = { Text("Dónde lo compraste, cuándo, si estaba en oferta.") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

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

/**
 * El precio escrito no es el que ya tenía ese ingrediente (7.2 y 14.5).
 *
 * **Muestra los dos números juntos y no pregunta en abstracto**, que fue lo que pidió Sandy: esta
 * es la única pantalla donde se pueden comparar antes de que el viejo desaparezca. El botón de
 * reemplazar dice a qué afecta, porque el costo de todas las recetas que usan ese ingrediente se
 * mueve con él y no se deshace.
 */
@Composable
private fun DialogoPrecioDistinto(
    disputa: PrecioEnDisputa,
    acciones: AccionesAlmacen
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarLaDisputaDePrecio,
        title = { Text("'${disputa.existente.nombre}' ya tiene otro precio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                Text("Guardado: $${formatearNumero(disputa.valorGuardado)}")
                Text("Escribiste: $${formatearNumero(disputa.valorEscrito)}")
                Text(
                    text = "Cambiarlo mueve el costo de todas las recetas que lo usan, y no se " +
                        "puede deshacer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.conservarElPrecioGuardado) {
                Text("Dejar el guardado")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.reemplazarElPrecio) {
                Text(text = "Usar el nuevo", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

/**
 * Editar algo del almacén: cuánto queda y sus notas (14.7).
 *
 * **Dos formas de llegar al mismo número**, y las dos hacen falta:
 *
 * - *A mano* es para cuando uno mira el frasco y estima. Baja de a poco.
 * - *Calculadora* es para cuando se sabe cuánto se usó: se escribe eso y la resta la hace la app.
 *
 * El resultado de la calculadora **se muestra antes de guardar**, no después: una resta que uno no
 * ve es una resta que hay que rehacer de cabeza para confiar en ella (8.7.1).
 */
@Composable
private fun DialogoEditarArticulo(
    estado: DialogoAlmacen.CambiarCantidad,
    acciones: AccionesAlmacen
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text(estado.fila.nombre) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                Text(
                    text = "Ahora hay ${estado.fila.cuantoQueda}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                    FilterChip(
                        selected = estado.modo == ModoDeEdicion.MANUAL,
                        onClick = { acciones.cambiarModoDeEdicion(ModoDeEdicion.MANUAL) },
                        label = { Text("A mano") }
                    )
                    FilterChip(
                        selected = estado.modo == ModoDeEdicion.CALCULADORA,
                        onClick = { acciones.cambiarModoDeEdicion(ModoDeEdicion.CALCULADORA) },
                        label = { Text("Calculadora") }
                    )
                }

                when (estado.modo) {
                    ModoDeEdicion.MANUAL -> CampoNumerico(
                        valor = estado.cantidad,
                        alCambiar = acciones.cambiarCantidadEnEdicion,
                        etiqueta = "¿Cuánto queda? (${estado.fila.unidad})",
                        // El 0 es un dato y no un error: "no queda nada" es justo lo que uno
                        // viene a anotar antes de salir a comprar.
                        ayuda = "Puede ser 0 si se acabó",
                        accionDelTeclado = ImeAction.Next
                    )

                    ModoDeEdicion.CALCULADORA -> {
                        CampoNumerico(
                            valor = estado.seUso,
                            alCambiar = acciones.cambiarLoQueSeUso,
                            etiqueta = "¿Cuánto usaste? (${estado.fila.unidad})",
                            accionDelTeclado = ImeAction.Next
                        )
                        estado.resultado?.let { queda ->
                            Text(
                                text = "Quedan ${formatearNumero(queda)} ${estado.fila.unidad}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (estado.seFueDeRango) {
                            // No impide guardar: el estante queda en cero igual. Se dice para
                            // que no parezca un error de la app.
                            Text(
                                text = "Usaste más de lo anotado, así que queda en 0. Puede ser " +
                                    "que la cantidad de antes estuviera mal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Los detalles van abajo, que es donde Sandy pidió verlos al entrar a editar.
                OutlinedTextField(
                    value = estado.detalles,
                    onValueChange = acciones.cambiarDetallesEnEdicion,
                    label = { Text("Detalles") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    supportingText = { Text("Dónde lo compraste, cuándo, si estaba en oferta.") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.guardarEdicion, enabled = estado.puedeGuardar) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

/**
 * Una casilla con su explicación debajo.
 *
 * La explicación no es adorno: "se cuenta por unidad" y "se puede usar en recetas" se parecen lo
 * suficiente como para marcarlas al revés, y un ejemplo concreto —caja, cinta, vela— resuelve la
 * duda sin tener que probar.
 */
@Composable
private fun CasillaConExplicacion(
    marcada: Boolean,
    alCambiar: (Boolean) -> Unit,
    titulo: String,
    explicacion: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Medidas.objetivoTactil)
            .clickable { alCambiar(!marcada) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = marcada, onCheckedChange = alCambiar)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = titulo, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = explicacion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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

private fun articulo(
    id: Long,
    nombre: String,
    cantidad: Double,
    valor: Double?,
    esObjeto: Boolean = false,
    detalles: String? = null
) = FilaDeAlmacen(
    ArticuloConValor(
        id = id,
        ingredienteId = id,
        nombre = nombre,
        cantidad = cantidad,
        valorPorGramo = valor,
        esObjeto = esObjeto,
        vaEnRecetas = !esObjeto,
        detalles = detalles,
        actualizadoEn = 0
    )
)

private val almacenDeEjemplo = EstadoAlmacen(
    visibles = listOf(
        articulo(1, "Harina", 2500.0, 1.2),
        articulo(2, "Azúcar", 800.0, 1.5),
        articulo(3, "Cajas de torta", 12.0, 350.0, esObjeto = true, detalles = "Feria, en oferta")
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

@Preview(showBackground = true, name = "Almacén - agregando una caja")
@Composable
private fun AlmacenAgregando() {
    ReposteriaTheme {
        ListaAlmacen(
            almacenDeEjemplo,
            DialogoAlmacen.Agregar(
                nombre = "Cajas de torta",
                esObjeto = true,
                cantidad = "12",
                precio = "350",
                detalles = "Feria, en oferta"
            ),
            AccionesAlmacen()
        )
    }
}

@Preview(showBackground = true, name = "Almacén - restando lo que se usó")
@Composable
private fun AlmacenCalculadora() {
    ReposteriaTheme {
        ListaAlmacen(
            almacenDeEjemplo,
            DialogoAlmacen.CambiarCantidad(
                fila = almacenDeEjemplo.visibles.first(),
                modo = ModoDeEdicion.CALCULADORA,
                cantidad = "2.500",
                seUso = "500",
                detalles = ""
            ),
            AccionesAlmacen()
        )
    }
}
