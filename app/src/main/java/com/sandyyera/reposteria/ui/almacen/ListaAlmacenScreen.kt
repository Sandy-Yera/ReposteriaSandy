package com.sandyyera.reposteria.ui.almacen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.sandyyera.reposteria.data.repositorio.QueHacerConElNombre
import com.sandyyera.reposteria.data.repositorio.ResultadoAgregarAlAlmacen
import com.sandyyera.reposteria.logica.almacen.COMO_FILTRAR_POR_CANTIDAD
import com.sandyyera.reposteria.logica.almacen.MarcaDeAlmacen
import com.sandyyera.reposteria.logica.almacen.SentidoDelMovimiento
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.ui.componentes.BarraBusqueda
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.componentes.CuadroDeDialogo
import com.sandyyera.reposteria.ui.componentes.MensajeCentrado
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el almacén. */
data class AccionesAlmacen(
    val buscar: (String) -> Unit = {},
    val cambiarMarca: (MarcaDeAlmacen) -> Unit = {},
    val abrirFiltros: () -> Unit = {},
    val limpiarFiltros: () -> Unit = {},
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
    val cambiarLoQueSeMovio: (String) -> Unit = {},
    val cambiarSentidoDelMovimiento: (SentidoDelMovimiento) -> Unit = {},
    val cambiarEsObjetoEnEdicion: (Boolean) -> Unit = {},
    val abrirRenombre: () -> Unit = {},
    val cambiarNombreDelRenombre: (String) -> Unit = {},
    val cancelarRenombre: () -> Unit = {},
    val guardarRenombre: () -> Unit = {},
    val cambiarVaEnRecetasEnEdicion: (Boolean) -> Unit = {},
    val cambiarDetallesEnEdicion: (String) -> Unit = {},
    val guardarEdicion: () -> Unit = {},
    val resolverElNombre: (QueHacerConElNombre) -> Unit = {},
    val confirmarCambiosEnRecetas: () -> Unit = {},
    val abrirDescuentoPorRecetas: () -> Unit = {},
    val buscarRecetaParaDescontar: (String) -> Unit = {},
    val cambiarTandas: (Long, String) -> Unit = { _, _ -> },
    val calcularElDescuento: () -> Unit = {},
    val volverAElegirRecetas: () -> Unit = {},
    val confirmarElDescuento: () -> Unit = {},
    val pedirBorrado: (FilaDeAlmacen) -> Unit = {},
    val cambiarTambienDelCatalogo: (Boolean) -> Unit = {},
    val confirmarBorrado: () -> Unit = {},
    val cancelarBorradoDelCatalogo: () -> Unit = {},
    val confirmarBorradoDelCatalogo: () -> Unit = {},
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
            cambiarMarca = modelo::cambiarMarca,
            abrirFiltros = modelo::abrirFiltros,
            limpiarFiltros = modelo::limpiarFiltros,
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
            cambiarLoQueSeMovio = modelo::cambiarLoQueSeMovio,
            cambiarSentidoDelMovimiento = modelo::cambiarSentidoDelMovimiento,
            cambiarEsObjetoEnEdicion = modelo::cambiarEsObjetoEnEdicion,
            abrirRenombre = modelo::abrirRenombre,
            cambiarNombreDelRenombre = modelo::cambiarNombreDelRenombre,
            cancelarRenombre = modelo::cancelarRenombre,
            guardarRenombre = modelo::guardarRenombre,
            cambiarVaEnRecetasEnEdicion = modelo::cambiarVaEnRecetasEnEdicion,
            resolverElNombre = modelo::resolverElNombre,
            confirmarCambiosEnRecetas = modelo::confirmarCambiosEnRecetas,
            abrirDescuentoPorRecetas = modelo::abrirDescuentoPorRecetas,
            buscarRecetaParaDescontar = modelo::buscarRecetaParaDescontar,
            cambiarTandas = modelo::cambiarTandas,
            calcularElDescuento = modelo::calcularElDescuento,
            volverAElegirRecetas = modelo::volverAElegirRecetas,
            confirmarElDescuento = modelo::confirmarElDescuento,
            cambiarDetallesEnEdicion = modelo::cambiarDetallesEnEdicion,
            guardarEdicion = modelo::guardarEdicion,
            pedirBorrado = modelo::pedirBorrado,
            cambiarTambienDelCatalogo = modelo::cambiarTambienDelCatalogo,
            confirmarBorrado = modelo::confirmarBorrado,
            cancelarBorradoDelCatalogo = modelo::cancelarBorradoDelCatalogo,
            confirmarBorradoDelCatalogo = modelo::confirmarBorradoDelCatalogo,
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
            // Descontar por recetas hechas (14.9). Va acá arriba y no escondido en un menú:
            // es lo que se hace después de cocinar, o sea tan seguido como anotar una compra.
            OutlinedButton(
                onClick = acciones.abrirDescuentoPorRecetas,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medidas.objetivoTactil)
            ) {
                Text("Descontar lo que hice hoy")
            }

            ValorDelAlmacen(estado)
            LineaDeBusqueda(estado, acciones)
        }

        when {
            estado.almacenVacio -> MensajeCentrado(
                titulo = "El almacén está vacío",
                detalle = "Agrega lo que tengas guardado: harina, azúcar, y también cajas, " +
                    "cintas o velas. Todo lo que anotes acá aparece además en Ingredientes."
            )
            estado.busquedaSinResultados -> MensajeCentrado(
                titulo = "No encontré eso",
                // El vacío **dice qué filtros están puestos**, y no "prueba con otra palabra" a
                // secas: con una casilla marcada de antes, la lista puede quedar vacía por algo
                // que no se está mirando, y buscar mejor no arreglaría nada.
                detalle = if (estado.hayFiltrosPuestos) {
                    "Ninguna fila pasa los filtros que tienes puestos. " +
                        "Puedes quitarlos con 'Limpiar filtros'."
                } else {
                    "Prueba con otra palabra."
                }
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
        is DialogoAlmacen.Filtros -> PanelDeFiltros(estado, acciones)
        is DialogoAlmacen.Agregar -> DialogoAgregarAlAlmacen(dialogo, acciones)
        is DialogoAlmacen.CambiarCantidad -> DialogoEditarArticulo(dialogo, acciones)
        is DialogoAlmacen.ElegirQueHacerConElNombre -> DialogoQueHacerConElNombre(dialogo, acciones)
        is DialogoAlmacen.Renombrar -> DialogoRenombrarEnAlmacen(dialogo, acciones)
        is DialogoAlmacen.ConfirmarCambiosEnRecetas -> DialogoCambiosEnRecetas(dialogo, acciones)
        is DialogoAlmacen.DescontarPorRecetas -> DialogoDescontarPorRecetas(dialogo, acciones)
        is DialogoAlmacen.ConfirmarBorrado -> ConfirmarSacarDelAlmacen(dialogo, acciones)
        is DialogoAlmacen.ConfirmarBorradoDelCatalogo ->
            ConfirmarSacarDelCatalogo(dialogo, acciones)
    }
    }
}

/**
 * La línea del buscador: el campo, el botón de filtros y el de limpiarlos (14.14).
 *
 * **Los filtros dejaron de vivir en la pantalla y se mudaron a un panel flotante.** Lo pidió
 * Sandy con el motivo a la vista: *"para ver lo que tengo en almacén queda muy corto, porque
 * tengo mucho arriba, y si escribo el teclado tapa por completo la zona"*. Entre los dos botones,
 * el valor del almacén, el buscador, las cuatro casillas y la chuleta, quedaba una franja para
 * ver lo que uno vino a ver. Filtrar se hace de vez en cuando; mirar la lista, siempre — así que
 * lo que se va arriba es lo primero.
 *
 * Lo único que se queda afuera es **lo que habla del texto recién escrito**: el aviso del filtro
 * mal escrito y la frase de cómo se entendió. Eso no es una opción que se configura, es la
 * respuesta a lo que se está tecleando, y escondida en un panel no serviría de nada.
 */
@Composable
private fun LineaDeBusqueda(estado: EstadoAlmacen, acciones: AccionesAlmacen) {
    Column(verticalArrangement = Arrangement.spacedBy(Medidas.minimo)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarraBusqueda(
                texto = estado.busqueda,
                alCambiar = acciones.buscar,
                marcador = "Buscar por nombre, o =300",
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = acciones.abrirFiltros) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Filtros",
                    // **Encendido cuando hay filtros puestos.** Con el panel cerrado, este ícono
                    // es lo único que puede avisar que la lista está recortada por algo que no se
                    // está viendo; apagado siempre, una lista corta parecería un almacén vacío.
                    tint = if (estado.marcas.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            // **Solo aparece si hay algo que limpiar.** Un botón que no hace nada la mayor parte
            // del tiempo enseña a no mirarlo, y este importa justo cuando la lista quedó vacía.
            if (estado.hayFiltrosPuestos) {
                TextButton(onClick = acciones.limpiarFiltros) {
                    Text("Limpiar", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // El aviso del filtro mal escrito va **pegado al campo** y no en la franja de abajo, que
        // el teclado tapa justo mientras se escribe el filtro (8.2).
        estado.errorDelFiltro?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        estado.comoSeEntendioElFiltro?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * El panel flotante de filtros (14.14).
 *
 * Trae **la chuleta arriba y las casillas debajo**, en ese orden y no al revés, porque es el orden
 * en que se usan: primero se lee cómo se escribe un filtro de cantidad, después se marcan las
 * categorías. Sandy lo pidió así — *"en la parte superior, el mensaje guía de cómo usar filtro por
 * cantidad"*—, y de paso la chuleta dejó de estar detrás de un botón: acá adentro no le quita
 * espacio a nada.
 *
 * **Las casillas se marcan con el panel abierto y la lista se filtra detrás**, sin botón de
 * aplicar. No hay nada que confirmar: marcar es el cambio, y un "Aceptar" solo agregaría un toque
 * a algo que ya se puede deshacer tocando otra vez.
 */
@Composable
private fun PanelDeFiltros(estado: EstadoAlmacen, acciones: AccionesAlmacen) {
    CuadroDeDialogo(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("Filtros") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                // La chuleta sale de la lista de `logica/` y no está escrita acá: una ayuda
                // escrita aparte de la regla que explica se queda mintiendo a la primera que
                // alguien cambia la regla.
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(Medidas.chico)) {
                        Text(
                            text = "Por cantidad, escribiendo en el buscador:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        COMO_FILTRAR_POR_CANTIDAD.forEach {
                            Text(text = it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Text(
                    text = "Por qué es cada cosa:",
                    style = MaterialTheme.typography.titleSmall
                )
                // Las cuatro casillas, en el orden de los dos pares. Marcar las dos de un par es
                // lo mismo que no marcar ninguna, y eso se dice abajo en vez de dejarlo adivinar.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Medidas.chico)
                ) {
                    MarcaDeAlmacen.entries.forEach { marca ->
                        FilterChip(
                            selected = marca in estado.marcas,
                            onClick = { acciones.cambiarMarca(marca) },
                            label = { Text(marca.etiqueta) }
                        )
                    }
                }
                Text(
                    text = "Sin marcar nada se muestra todo. Marcar las dos de un par es lo " +
                        "mismo que no marcar ninguna.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Listo") }
        },
        dismissButton = {
            if (estado.hayFiltrosPuestos) {
                TextButton(onClick = acciones.limpiarFiltros) { Text("Limpiar filtros") }
            }
        }
    )
}

/**
 * La primera advertencia: sacar algo del almacén (6.3).
 *
 * Trae la casilla que pidió Sandy — *"una opción que debo marcar, antes de apretar borrar, que sea
 * 'eliminar de ingredientes'"*—. Marcarla **no borra el ingrediente acá**: hace que al confirmar,
 * después de sacar la fila del almacén, salte la segunda advertencia con las recetas afectadas.
 *
 * Que sean dos preguntas y no una no es ceremonia de más: sacar del almacén no le hace nada a
 * ninguna receta, y borrar del catálogo se lleva el ingrediente de todas las que lo usan. Una sola
 * confirmación para las dos sería pedir permiso para lo chico y aprovechar para lo grande.
 *
 * El texto de arriba **cambia según la casilla**, en vez de quedarse diciendo "sigue en
 * Ingredientes" mientras la casilla marcada dice lo contrario.
 */
@Composable
private fun ConfirmarSacarDelAlmacen(
    dialogo: DialogoAlmacen.ConfirmarBorrado,
    acciones: AccionesAlmacen
) {
    CuadroDeDialogo(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("¿Sacar '${dialogo.fila.nombre}' del almacén?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                // Se dice con todas las letras: son dos cosas distintas y confundirlas haría
                // creer que esto borra un ingrediente en uso.
                Text(
                    if (dialogo.tambienDelCatalogo) {
                        "Deja de llevarle la cuenta acá. Como marcaste la casilla, después va " +
                            "a preguntar por el ingrediente, mostrando a qué recetas afecta."
                    } else {
                        "Deja de llevarle la cuenta acá. Sigue en Ingredientes y en las " +
                            "recetas que lo usan."
                    }
                )

                if (dialogo.sePuedeSacarDelCatalogo) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Medidas.objetivoTactil)
                            // Toda la fila cambia la casilla, no solo el cuadradito: es un
                            // blanco de 20 dp contra uno de ancho completo (8.4.1).
                            .clickable {
                                acciones.cambiarTambienDelCatalogo(!dialogo.tambienDelCatalogo)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dialogo.tambienDelCatalogo,
                            onCheckedChange = acciones.cambiarTambienDelCatalogo
                        )
                        Text(
                            text = "Eliminarlo también de Ingredientes",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
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

/**
 * La segunda advertencia: borrarlo también del catálogo de ingredientes (7.1).
 *
 * Es la misma advertencia que la de la pantalla de Ingredientes y por la misma regla: **nombra las
 * recetas afectadas**. Un "¿seguro?" sin la lista es un botón que se aprieta.
 *
 * Dice arriba que lo del almacén ya se hizo, porque es verdad y porque cancelar acá no lo
 * devuelve. Esconderlo dejaría creer que "Cancelar" deshace las dos cosas.
 *
 * El cuerpo entero se desplaza y los botones quedan fijos, igual que en el borrado de una receta:
 * con veinte recetas afectadas, un cuadro que crece se lleva el botón fuera de la pantalla.
 */
@Composable
private fun ConfirmarSacarDelCatalogo(
    dialogo: DialogoAlmacen.ConfirmarBorradoDelCatalogo,
    acciones: AccionesAlmacen
) {
    CuadroDeDialogo(
        onDismissRequest = acciones.cancelarBorradoDelCatalogo,
        title = { Text("¿Eliminar '${dialogo.nombre}' de Ingredientes?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                Text(
                    text = "Del almacén ya salió. Esto es lo otro: sacarlo del catálogo y de " +
                        "las recetas que lo usan.",
                    style = MaterialTheme.typography.bodyMedium
                )

                when {
                    // `null` es "todavía consultando" y no "no lo usa ninguna": mientras tanto el
                    // botón está apagado, porque confirmar sería confirmar media advertencia.
                    dialogo.recetasAfectadas == null -> Text(
                        text = "Revisando a qué recetas afecta…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    dialogo.recetasAfectadas.isNotEmpty() -> Column {
                        Text(
                            text = "Lo pierden ${dialogo.recetasAfectadas.size} " +
                                if (dialogo.recetasAfectadas.size == 1) "receta:" else "recetas:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        dialogo.recetasAfectadas.forEach {
                            Text(
                                text = "• ${it.titulo}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> Text(
                        text = "No lo usa ninguna receta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Esto no se puede deshacer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = acciones.confirmarBorradoDelCatalogo,
                enabled = dialogo.sePuedeBorrar
            ) {
                Text(text = "Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cancelarBorradoDelCatalogo) {
                Text("Dejarlo en Ingredientes")
            }
        }
    )
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

    CuadroDeDialogo(
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

                // Lo que hay que decir de un objeto en una receta es que **no pesa**: su
                // precio sí entra al costo, pero el peso del que sale el reescalado no lo
                // cuenta (14.5).
                if (estado.esObjeto && estado.vaEnRecetas) {
                    Text(
                        text = "En una receta cuesta como cualquier ingrediente, pero no suma " +
                            "al peso: reescalar por molde no lo multiplica.",
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
                    // Se pregunta lo que costó **todo** y no el precio por gramo: es el número
                    // que uno tiene delante al llegar de comprar, y la división la hace la app
                    // (14.5.2). Hacerla de cabeza es donde se cuela el error caro.
                    etiqueta = "¿Cuánto te costó en total?",
                    error = estado.errorPrecio,
                    accionDelTeclado = ImeAction.Next
                )
                // La cuenta se muestra antes de guardar, no después: es la misma regla de la
                // calculadora de valor por gramo (8.7.1).
                estado.comoSeLeeLaCuenta?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                estado.porQueNoHayCuenta?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
 * Este ingrediente ya existe y anotarlo así lo cambiaría (7.2 y 14.5.1).
 *
 * **Muestra los dos lados juntos y no pregunta en abstracto**, que fue lo que pidió Sandy: esta
 * es la única pantalla donde se pueden comparar antes de que el viejo desaparezca.
 *
 * Son tres cambios posibles y se listan **solo los que de verdad cambian**: un cuadro que enumera
 * lo que se queda igual obliga a leer tres renglones para encontrar el que importa.
 *
 * El de la **unidad** es el que más pesa, y por eso lleva su propio aviso cuando el ingrediente ya
 * está en recetas: el número del precio no se mueve pero pasa a significar otra cosa, y las líneas
 * que lo usan en gramos empiezan a multiplicar por un precio por unidad.
 */
@Composable
private fun DialogoPrecioDistinto(
    disputa: ResultadoAgregarAlAlmacen.YaExisteConCambios,
    acciones: AccionesAlmacen
) {
    val laUnidadDeAntes = if (disputa.existente.esObjeto) "unidad" else "gramo"
    val laUnidadNueva = if (disputa.esObjetoNuevo) "unidad" else "gramo"

    CuadroDeDialogo(
        onDismissRequest = acciones.cerrarLaDisputaDePrecio,
        title = { Text("'${disputa.existente.nombre}' ya existe") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                Text("Si lo anotas así, esto va a cambiar:")

                if (disputa.cambiaLaUnidad) {
                    Text("• Se cuenta por $laUnidadDeAntes → por $laUnidadNueva")
                }
                if (disputa.cambiaElPrecio) {
                    Text(
                        "• Precio: $${formatearNumero(disputa.existente.valorPorGramo)} → " +
                            "$${formatearNumero(disputa.valorNuevo)} por $laUnidadNueva"
                    )
                }
                if (disputa.cambiaSiVaEnRecetas) {
                    Text(
                        if (disputa.vaEnRecetasNuevo) "• Pasa a ofrecerse en las recetas"
                        else "• Deja de ofrecerse en las recetas"
                    )
                }

                if (disputa.afectaRecetas) {
                    Text(
                        text = "Lo usan ${disputa.usadoEnRecetas} " +
                            (if (disputa.usadoEnRecetas == 1) "receta" else "recetas") +
                            ", y su costo se mueve con esto. No se puede deshacer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (disputa.cambiaLaUnidad) {
                        // El caso que más confunde: lo ya escrito no se convierte solo.
                        Text(
                            text = "Ojo: lo que ya está puesto en una receta conserva su número. " +
                                "Revisa esas líneas después.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.conservarElPrecioGuardado) {
                Text("Dejarlo como está")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.reemplazarElPrecio) {
                Text(text = "Aplicar los cambios", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

/**
 * El nombre escrito no existe en el catálogo: qué significa eso (14.10).
 *
 * Las dos salidas se explican por su **consecuencia** y no por su nombre: "renombrar" y "separar"
 * no dicen nada solos, y lo que hay que saber para elegir es a cuántas recetas les cambia el
 * nombre y a cuántas no.
 */
@Composable
private fun DialogoQueHacerConElNombre(
    estado: DialogoAlmacen.ElegirQueHacerConElNombre,
    acciones: AccionesAlmacen
) {
    val cuantas = estado.usadoEnRecetas
    CuadroDeDialogo(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("'${estado.nombreNuevo}' no existe todavía") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                Text("¿Qué es lo que pasó con '${estado.nombreViejo}'?")
                Text(
                    text = "• Es el mismo y estaba mal escrito → Renombrar. " +
                        if (cuantas == 0) {
                            "No lo usa ninguna receta, así que no cambia nada más."
                        } else {
                            "Cambia también en ${if (cuantas == 1) "1 receta" else "$cuantas recetas"}."
                        },
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "• Resultó ser otra cosa → Separar. Se crea '${estado.nombreNuevo}' " +
                        "aparte y '${estado.nombreViejo}' queda igual en sus recetas.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { acciones.resolverElNombre(QueHacerConElNombre.RENOMBRAR) },
                enabled = !estado.guardando
            ) { Text("Renombrar") }
        },
        dismissButton = {
            TextButton(
                onClick = { acciones.resolverElNombre(QueHacerConElNombre.SEPARAR) },
                enabled = !estado.guardando
            ) { Text("Separar") }
        }
    )
}

/**
 * Cambiarle el nombre a algo del almacén (14.10).
 *
 * Cuadro propio y no un campo dentro del de editar: renombrar puede querer decir tres cosas
 * —renombrar, unir o separar— y como campo quedaba escondido debajo de la cantidad, que es la
 * decisión más chica.
 */
@Composable
private fun DialogoRenombrarEnAlmacen(
    estado: DialogoAlmacen.Renombrar,
    acciones: AccionesAlmacen
) {
    CuadroDeDialogo(
        onDismissRequest = acciones.cancelarRenombre,
        title = { Text("Cambiar el nombre") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                OutlinedTextField(
                    value = estado.nombre,
                    onValueChange = acciones.cambiarNombreDelRenombre,
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = estado.error != null,
                    // El aviso va **bajo el campo** y no en la franja de abajo, que con el
                    // teclado abierto queda tapada (8.2).
                    supportingText = {
                        Text(
                            estado.error
                                ?: "Si escribes uno que ya está en Ingredientes, se unen."
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.guardarRenombre, enabled = estado.puedeGuardar) {
                Text("Cambiar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cancelarRenombre) { Text("Cancelar") }
        }
    )
}

/**
 * La advertencia antes de cambiar **qué es** algo que las recetas usan (14.11).
 *
 * **Un solo cuadro para los dos cambios** —la unidad y si va en recetas—: los dos tocan las
 * mismas recetas, así que preguntar por separado sería pedir la misma autorización partida en
 * dos.
 *
 * **Nombra las recetas**, que es lo que hace que la confirmación signifique algo (7.1): un
 * "¿seguro?" sin la lista es un botón que se aprieta sin leer.
 */
@Composable
private fun DialogoCambiosEnRecetas(
    estado: DialogoAlmacen.ConfirmarCambiosEnRecetas,
    acciones: AccionesAlmacen
) {
    CuadroDeDialogo(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("'${estado.volverA.nombre}' cambia para las recetas") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                estado.cambios.forEach {
                    Text(text = "• $it", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Lo usan estas recetas:")
                estado.recetasAfectadas.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "No se puede deshacer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Seguirá en el almacén y en Ingredientes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = acciones.confirmarCambiosEnRecetas,
                enabled = !estado.guardando
            ) {
                Text("Cambiarlo", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

/**
 * Descontar del almacén lo que se gastó haciendo recetas (14.9).
 *
 * **Dos momentos en el mismo cuadro**: elegir qué se hizo, y mirar qué va a pasar. El segundo no
 * es una formalidad — esto toca muchas filas de una vez y es lo más destructivo del almacén, así
 * que poder revisarlo fila por fila antes de confirmar es lo que lo hace usable sin miedo.
 */
@Composable
private fun DialogoDescontarPorRecetas(
    estado: DialogoAlmacen.DescontarPorRecetas,
    acciones: AccionesAlmacen
) {
    val previa = estado.previa
    CuadroDeDialogo(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text(if (previa == null) "¿Qué hiciste?" else "Esto va a quedar") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                if (previa == null) {
                    Text(
                        text = "Escribe cuántas tandas hiciste de cada una. Media tanda se " +
                            "escribe 0,5.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BarraBusqueda(
                        texto = estado.busqueda,
                        alCambiar = acciones.buscarRecetaParaDescontar,
                        marcador = "Buscar receta"
                    )
                    estado.visibles.forEach { fila ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Medidas.chico),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fila.titulo,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            CampoNumerico(
                                valor = fila.tandas,
                                alCambiar = { acciones.cambiarTandas(fila.recetaId, it) },
                                etiqueta = "Tandas",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (estado.recetas.isEmpty() && !estado.calculando) {
                        Text("Todavía no hay recetas que hayas guardado.")
                    }
                } else {
                    if (!previa.hayAlgoQueDescontar) {
                        Text("Nada de lo que llevan esas recetas está anotado en el almacén.")
                    }
                    previa.filas.forEach { fila ->
                        Column {
                            Text(
                                text = "${fila.nombre}: ${fila.comoSeLeeLoQueSeUsa} → queda " +
                                    fila.comoSeLeeLoQueQueda,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (fila.quedaNegativo) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                            )
                            fila.aviso?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    if (previa.sinAnotar.isNotEmpty()) {
                        // No es un error —hay cosas que se usan sin llevarles la cuenta— pero
                        // callarlo dejaría la impresión de que se descontó todo.
                        Text(
                            text = "No están en el almacén, así que no se tocan: " +
                                previa.sinAnotar.joinToString { it.nombre },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (previa == null) {
                TextButton(
                    onClick = acciones.calcularElDescuento,
                    enabled = estado.puedeCalcular
                ) { Text("Ver qué queda") }
            } else {
                TextButton(
                    onClick = acciones.confirmarElDescuento,
                    enabled = previa.hayAlgoQueDescontar && !estado.guardando
                ) { Text("Descontar") }
            }
        },
        dismissButton = {
            if (previa == null) {
                TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
            } else {
                TextButton(onClick = acciones.volverAElegirRecetas) { Text("Volver") }
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
    CuadroDeDialogo(
        onDismissRequest = acciones.cerrarDialogo,
        title = {
            // **Tocar el nombre lo cambia**, que es el mismo gesto del título de una receta
            // (8.4.1 #3): está a la vista, así que tocarlo es lo que uno intenta. Antes era un
            // campo más abajo, y ahí quedaba escondida la decisión más grande del cuadro
            // —renombrar toca todas las recetas— debajo de la más chica.
            //
            // Toda la franja y no solo las letras, por lo mismo que allá: con el `clickable`
            // pegado al texto hay que apuntarle justo, y al lado no pasa nada.
            Text(
                text = estado.nombre,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medidas.objetivoTactil)
                    .clickable(onClick = acciones.abrirRenombre)
                    .wrapContentHeight()
            )
        },
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
                        selected = estado.modo == ModoDeEdicion.MOVIMIENTO,
                        onClick = { acciones.cambiarModoDeEdicion(ModoDeEdicion.MOVIMIENTO) },
                        label = { Text("Entró o salió") }
                    )
                }

                when (estado.modo) {
                    ModoDeEdicion.MANUAL -> CampoNumerico(
                        valor = estado.cantidad,
                        alCambiar = acciones.cambiarCantidadEnEdicion,
                        etiqueta = "¿Cuánto queda? (${estado.unidad})",
                        // El 0 es un dato y no un error: "no queda nada" es justo lo que uno
                        // viene a anotar antes de salir a comprar.
                        ayuda = "Puede ser 0 si se acabó",
                        accionDelTeclado = ImeAction.Next
                    )

                    ModoDeEdicion.MOVIMIENTO -> {
                        // **Los dos sentidos a la vista y no solo restar** (14.8). Antes esto era
                        // "calculadora" y solo sabía quitar: sumar obligaba a hacer la cuenta de
                        // cabeza y anotar el total, que es el trabajo que el cuadro ahorra.
                        Row(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                            FilterChip(
                                selected = estado.sentido == SentidoDelMovimiento.SALE,
                                onClick = {
                                    acciones.cambiarSentidoDelMovimiento(SentidoDelMovimiento.SALE)
                                },
                                label = { Text("Salió") }
                            )
                            FilterChip(
                                selected = estado.sentido == SentidoDelMovimiento.ENTRA,
                                onClick = {
                                    acciones.cambiarSentidoDelMovimiento(SentidoDelMovimiento.ENTRA)
                                },
                                label = { Text("Entró") }
                            )
                        }
                        CampoNumerico(
                            valor = estado.seMovio,
                            alCambiar = acciones.cambiarLoQueSeMovio,
                            etiqueta = if (estado.sentido == SentidoDelMovimiento.SALE) {
                                "¿Cuánto usaste? (${estado.unidad})"
                            } else {
                                "¿Cuánto entró? (${estado.unidad})"
                            },
                            accionDelTeclado = ImeAction.Next
                        )
                        estado.comoQuedaria?.let { queda ->
                            // La cuenta se muestra **antes** de guardar: una resta que no se ve
                            // hay que rehacerla de cabeza para confiar en ella (8.7.1).
                            Text(
                                text = "Queda $queda",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (estado.seFueDeRango) {
                            Text(
                                text = "Sacaste más de lo que había anotado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // El negativo **se muestra y no se impide** (14.8), en los dos modos: es el dato
                // que Sandy pidió ver, y el aviso trae las dos lecturas posibles porque solo ella
                // sabe cuál es la de ese frasco.
                estado.avisoDelResultado?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Toca el nombre de arriba para cambiarlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Los dos interruptores que dicen **qué es** esto, que hasta ahora solo se
                // elegían al crear y no se veían nunca más (14.11).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Se cuenta por unidad",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = estado.esObjeto,
                        onCheckedChange = acciones.cambiarEsObjetoEnEdicion
                    )
                }
                if (estado.esObjeto != estado.fila.esObjeto) {
                    // **El aviso de la unidad es el que más pesa** y no se nota mirándolo: el
                    // número del precio no se mueve pero pasa a significar otra cosa (14.5.1).
                    Text(
                        text = "El precio guardado no cambia de número, pero pasa a ser por " +
                            (if (estado.esObjeto) "unidad" else "gramo") +
                            ". Revisa el precio en Ingredientes después.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Se puede usar en recetas",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = estado.vaEnRecetas,
                        onCheckedChange = acciones.cambiarVaEnRecetasEnEdicion
                    )
                }
                if (!estado.vaEnRecetas && estado.fila.vaEnRecetas) {
                    Text(
                        text = "Al guardar se sacará de las recetas que lo usen. Te vamos a " +
                            "decir cuáles antes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
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
                modo = ModoDeEdicion.MOVIMIENTO,
                cantidad = "2.500",
                seMovio = "500",
                detalles = "",
                nombre = almacenDeEjemplo.visibles.first().nombre,
                esObjeto = false,
                vaEnRecetas = true
            ),
            AccionesAlmacen()
        )
    }
}
