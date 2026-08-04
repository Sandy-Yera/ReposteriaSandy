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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.PrecioVigente
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el paso de gastos y ganancias. */
data class AccionesGastos(
    val abrirPrecioNuevo: () -> Unit = {},
    val abrirEditarPrecio: (RecetaPrecio) -> Unit = {},
    val elegirModo: (ModoPrecio) -> Unit = {},
    val cambiarCantidad: (String) -> Unit = {},
    val cambiarPrecioTotal: (String) -> Unit = {},
    val cambiarEtiqueta: (String) -> Unit = {},
    val guardarPrecio: () -> Unit = {},
    val elegirReferencia: (RecetaPrecio) -> Unit = {},
    val pedirBorrado: (FilaDePrecio) -> Unit = {},
    val confirmarBorrado: () -> Unit = {},
    val cerrarDialogo: () -> Unit = {},
    val mensajeMostrado: () -> Unit = {},
    val irAlPaso: (PasoDeReceta) -> Unit = {},
    val cerrarReceta: () -> Unit = {}
)

/** El paso de gastos y ganancias conectado a su ViewModel. */
@Composable
fun PasoGastosScreen(
    tituloReceta: String,
    desplazamientoDePasos: ScrollState,
    modelo: GastosViewModel,
    pasoActual: PasoDeReceta,
    alElegirPaso: (PasoDeReceta) -> Unit,
    alCerrarReceta: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val dialogo by modelo.dialogo.collectAsStateWithLifecycle()

    val acciones = remember(modelo, alElegirPaso, alCerrarReceta) {
        AccionesGastos(
            abrirPrecioNuevo = modelo::abrirPrecioNuevo,
            abrirEditarPrecio = modelo::abrirEditarPrecio,
            elegirModo = modelo::elegirModo,
            cambiarCantidad = modelo::cambiarCantidad,
            cambiarPrecioTotal = modelo::cambiarPrecioTotal,
            cambiarEtiqueta = modelo::cambiarEtiqueta,
            guardarPrecio = modelo::guardarPrecio,
            elegirReferencia = modelo::elegirReferencia,
            pedirBorrado = modelo::pedirBorrado,
            confirmarBorrado = modelo::confirmarBorrado,
            cerrarDialogo = modelo::cerrarDialogo,
            mensajeMostrado = modelo::mensajeMostrado,
            irAlPaso = alElegirPaso,
            cerrarReceta = alCerrarReceta
        )
    }

    PasoGastos(
        tituloReceta = tituloReceta,
        estado = estado,
        dialogo = dialogo,
        acciones = acciones,
        pasoActual = pasoActual,
        modifier = modifier,
        desplazamientoDePasos = desplazamientoDePasos
    )
}

/**
 * El paso 5 de una receta: a cuánto se vende y cuánto deja (8.5 y 8.6).
 *
 * **Las cifras van arriba y la lista de precios abajo**, y no al revés: lo que se viene a ver
 * acá es cuánto deja la receta; los precios son de dónde sale ese número. La lista igual está
 * completa a un desplazamiento, porque comparar promociones entre sí es la otra mitad del paso.
 *
 * **Tocar una promoción la elige como referencia**, no la edita. Es lo que se hace muchas más
 * veces —probar cuánto daría vendiendo así— y sigue la regla de "tocar la cosa hace lo
 * principal" (8.4.1, #3). Editarla y borrarla son los dos íconos de su fila.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasoGastos(
    tituloReceta: String,
    estado: EstadoGastos,
    dialogo: DialogoGastos,
    acciones: AccionesGastos,
    pasoActual: PasoDeReceta = PasoDeReceta.GASTOS,
    modifier: Modifier = Modifier,
    desplazamientoDePasos: ScrollState = rememberScrollState()
) {
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    BackHandler(enabled = true) {
        if (dialogo is DialogoGastos.Ninguno) acciones.cerrarReceta() else acciones.cerrarDialogo()
    }

    LaunchedEffect(estado.mensaje) {
        val texto = estado.mensaje ?: return@LaunchedEffect
        try {
            anfitrionDeMensajes.showSnackbar(texto)
        } finally {
            // En `finally`: `showSnackbar` espera a que el aviso se cierre, y al cambiar de
            // paso antes de eso la corrutina se cancela y el mensaje quedaba pendiente,
            // reapareciendo cada vez que se volvía.
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
                    // Los mismos tres colores que los otros cuatro pasos: la barra no puede
                    // cambiar de color al moverse entre pasos, o el cambio se lee como un
                    // salto de la pantalla.
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilaDePasos(
                    pasoActual = pasoActual,
                    alElegirPaso = acciones.irAlPaso,
                    desplazamiento = desplazamientoDePasos
                )
            }
        },
        snackbarHost = { SnackbarHost(anfitrionDeMensajes) }
    ) { relleno ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
            contentPadding = PaddingValues(Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.chico)
        ) {
            if (!estado.tieneIngredientes) {
                item { AvisoSinIngredientes(acciones.irAlPaso) }
            }

            if (estado.laReferenciaPierdePlata) {
                item { AvisoVendeAPerdida() }
            }

            item { TarjetaDeCifras(estado) }

            item {
                Text(
                    text = "Precios y promociones",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Medidas.chico)
                )
            }

            if (!estado.tienePrecio) {
                item { SinPreciosTodavia() }
            }

            items(estado.filas, key = { it.precio.id }) { fila ->
                FilaDeUnPrecio(fila, acciones)
            }

            item {
                OutlinedButton(
                    onClick = acciones.abrirPrecioNuevo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Medidas.objetivoTactil)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        text = if (estado.tienePrecio) "Agregar una promoción" else "Poner el precio",
                        modifier = Modifier.padding(start = Medidas.chico)
                    )
                }
            }
        }
    }

    when (dialogo) {
        is DialogoGastos.Ninguno -> Unit
        is DialogoGastos.Formulario -> CuadroDePrecio(dialogo, acciones)
        is DialogoGastos.ConfirmarBorrado -> ConfirmarBorrarPrecio(dialogo, acciones)
    }
}

/**
 * El aviso de que la receta todavía no tiene ingredientes.
 *
 * Va **arriba de las cifras** porque sin él todas ellas mienten hacia arriba: con costo 0 la
 * ganancia es el precio entero y el negocio parece redondo. Es la misma distinción que ya
 * costó un error: costo 0 no es lo mismo que receta vacía, porque un ingrediente puede valer
 * 0 a propósito.
 */
@Composable
private fun AvisoSinIngredientes(irAlPaso: (PasoDeReceta) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { irAlPaso(PasoDeReceta.CANTIDADES) }
    ) {
        Column(Modifier.padding(Medidas.medio)) {
            Text(
                text = "Esta receta todavía no tiene ingredientes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Su costo es 0, así que las ganancias de abajo son el precio entero. " +
                    "Toca acá para ir a Cantidades.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/** El aviso de que con el precio de referencia la receta se vende bajo su costo. */
@Composable
private fun AvisoVendeAPerdida() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Medidas.medio)) {
            Text(
                text = "Con este precio la receta no alcanza a cubrir su costo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                // Las dos salidas, porque las dos son reales: puede haber subido un
                // ingrediente sin que nadie tocara los precios.
                text = "Súbele el precio, o elige otra promoción como referencia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/** Las siete cifras automáticas de 8.5, todas de solo lectura. */
@Composable
private fun TarjetaDeCifras(estado: EstadoGastos) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
        ) {
            Cifra("Cuesta hacerla", estado.costoTotal, destacada = true)
            Cifra("Cuesta cada trozo", estado.costoDeCadaTrozo)

            HorizontalDivider(Modifier.padding(vertical = Medidas.chico))

            if (!estado.tienePrecio) {
                Text(
                    text = "Pon un precio abajo para ver cuánto deja.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Cifra("Entra al vender el producto", estado.ingresoDelProducto)
            Cifra("Ganas por cada trozo", estado.gananciaDeCadaTrozo)
            Cifra("Ganas por el producto", estado.gananciaDelProducto, destacada = true)

            val ganador = estado.elTrozoGanador
            if (ganador != null) {
                HorizontalDivider(Modifier.padding(vertical = Medidas.chico))
                if (ganador.alcanzable) {
                    Text(
                        text = "Desde el trozo ${ganador.numero} empiezas a ganar " +
                            "($${formatearNumero(ganador.ganancia)} ahí).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // El caso que más importa ver: el número existe pero cae fuera de la
                    // receta, así que mostrarlo como un dato normal sería mentir.
                    Text(
                        text = "Harían falta ${ganador.numero} trozos para cubrir el costo, " +
                            "y la receta rinde ${estado.trozos}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (estado.filas.size > 1 && !estado.referenciaElegidaAMano) {
                Text(
                    // Solo desde dos precios: con uno solo, decir "se está usando el de menor
                    // ganancia" no informa nada, es el único que hay.
                    text = "Estas cifras usan la promoción que menos deja. Toca otra para " +
                        "calcular con esa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Una cifra con su nombre. [valor] en `null` se lee como un guion, nunca como 0. */
@Composable
private fun Cifra(nombre: String, valor: Double?, destacada: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = nombre,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = valor?.let { "$${formatearNumero(it)}" } ?: "—",
            style = if (destacada) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyMedium,
            fontWeight = if (destacada) FontWeight.Bold else null,
            // Una ganancia negativa se pinta en el color de error: es un dato que hay que
            // poder distinguir de un vistazo, no un número más.
            color = if ((valor ?: 0.0) < 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SinPreciosTodavia() {
    Text(
        text = "Todavía no le pusiste precio. El primero es el precio suelto de un trozo o " +
            "del producto completo; las promociones se agregan después.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Medidas.chico)
    )
}

/**
 * Una fila de la lista de precios.
 *
 * **Se toca para elegirla como referencia**, y la que ya lo es no responde al toque — llevaría
 * al mismo lugar (8.4.1, #6). Editar y borrar son los dos íconos.
 */
@Composable
private fun FilaDeUnPrecio(fila: FilaDePrecio, acciones: AccionesGastos) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (fila.esReferencia) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !fila.esReferencia) { acciones.elegirReferencia(fila.precio) }
    ) {
        Row(
            modifier = Modifier.padding(
                start = Medidas.medio,
                top = Medidas.chico,
                bottom = Medidas.chico
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                    Text(
                        text = fila.comoSeLlama,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (fila.esReferencia) {
                        Text(
                            text = "· manda esta",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "$${formatearNumero(fila.precio.precioTotal)} · " +
                        "$${formatearNumero(fila.precioPorTrozo)} el trozo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (fila.pierdePlata) {
                        "Pierdes $${formatearNumero(-fila.gananciaPorTrozo)} por trozo"
                    } else {
                        "Ganas $${formatearNumero(fila.gananciaPorTrozo)} por trozo"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fila.pierdePlata) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            // Este es el único lápiz que queda en la app, y es una excepción con motivo:
            // 8.4.1 #3 sacó los íconos de editar porque el toque ya hacía eso, pero acá el
            // toque está tomado por elegir la referencia, que es lo que 8.6 pide que se haga
            // tocando. Sin lápiz, un precio mal escrito solo se arreglaría borrándolo.
            IconButton(onClick = { acciones.abrirEditarPrecio(fila.precio) }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Cambiar este precio"
                )
            }
            IconButton(onClick = { acciones.pedirBorrado(fila) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Quitar este precio",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** El formulario de un precio, para crearlo o para editarlo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CuadroDePrecio(estado: DialogoGastos.Formulario, acciones: AccionesGastos) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text(if (estado.editando != null) "Cambiar el precio" else "Nuevo precio") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                Text(
                    text = "¿Qué es lo que vendes a este precio?",
                    style = MaterialTheme.typography.bodyMedium
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                    FilterChip(
                        selected = estado.modo == ModoPrecio.TROZO,
                        onClick = { acciones.elegirModo(ModoPrecio.TROZO) },
                        label = { Text("Trozos") }
                    )
                    FilterChip(
                        selected = estado.modo == ModoPrecio.PRODUCTO,
                        onClick = { acciones.elegirModo(ModoPrecio.PRODUCTO) },
                        label = { Text("Productos completos") }
                    )
                }

                CampoNumerico(
                    valor = estado.cantidad,
                    alCambiar = acciones.cambiarCantidad,
                    etiqueta = if (estado.modo == ModoPrecio.TROZO) {
                        "Cuántos trozos lleva"
                    } else {
                        "Cuántos productos lleva"
                    },
                    error = estado.errorCantidad,
                    accionDelTeclado = ImeAction.Next
                )

                CampoNumerico(
                    valor = estado.precioTotal,
                    alCambiar = acciones.cambiarPrecioTotal,
                    etiqueta = "A cuánto lo vendes",
                    error = estado.errorPrecioTotal,
                    // Con más de uno, el error caro es escribir el precio de cada uno en vez
                    // del total del paquete. Con uno solo no hay nada que aclarar.
                    ayuda = if (estado.esPromocion) "El total de los ${estado.cantidad} juntos"
                    else null,
                    accionDelTeclado = ImeAction.Next
                )

                OutlinedTextField(
                    value = estado.etiqueta,
                    onValueChange = acciones.cambiarEtiqueta,
                    label = { Text("Nombre (opcional)") },
                    supportingText = {
                        Text(estado.errorEtiqueta ?: "Por ejemplo: promo del sábado")
                    },
                    isError = estado.errorEtiqueta != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.guardarPrecio, enabled = estado.puedeGuardar) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ConfirmarBorrarPrecio(
    estado: DialogoGastos.ConfirmarBorrado,
    acciones: AccionesGastos
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("¿Quitar '${estado.comoSeLlama}'?") },
        text = {
            Text(
                "Las cifras se recalculan con los precios que queden. Si era la que mandaba, " +
                    "pasa a mandar la que menos deje."
            )
        },
        confirmButton = {
            TextButton(onClick = acciones.confirmarBorrado) { Text("Quitar") }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

// --- Vistas previas ---

private fun precio(
    id: Long,
    cantidad: Int,
    total: Double,
    etiqueta: String? = null,
    referencia: Boolean = false
) = RecetaPrecio(
    id = id,
    recetaId = 1,
    modo = ModoPrecio.TROZO,
    cantidad = cantidad,
    precioTotal = total,
    etiqueta = etiqueta,
    esReferencia = referencia
)

private fun estadoDeEjemplo(
    conPrecios: Boolean = true,
    sinIngredientes: Boolean = false
): EstadoGastos {
    val filas = if (!conPrecios) emptyList() else listOf(
        FilaDePrecio(precio(1, 1, 500.0), "1 trozo", 1, 500.0, 266.67, esReferencia = true),
        FilaDePrecio(precio(2, 2, 1500.0, "2x1.500"), "2x1.500", 2, 750.0, 516.67, false)
    )
    return EstadoGastos(
        datos = DatosCalculoReceta(
            recetaId = 1,
            titulo = "Torta de manjar",
            costoTotal = if (sinIngredientes) 0.0 else 1400.0,
            trozos = 6,
            precios = if (!conPrecios) emptyList() else listOf(
                PrecioVigente(ModoPrecio.TROZO, 1, 500.0, null, esReferencia = true),
                PrecioVigente(ModoPrecio.TROZO, 2, 1500.0, "2x1.500", esReferencia = false)
            )
        ),
        filas = filas,
        tieneIngredientes = !sinIngredientes,
        cargando = false
    )
}

@Preview(showBackground = true, name = "Gastos - con precio base y una promo")
@Composable
private fun GastosConPrecios() {
    ReposteriaTheme {
        PasoGastos("Torta de manjar", estadoDeEjemplo(), DialogoGastos.Ninguno, AccionesGastos())
    }
}

@Preview(showBackground = true, name = "Gastos - todavía sin precio")
@Composable
private fun GastosSinPrecios() {
    ReposteriaTheme {
        PasoGastos(
            "Torta de manjar",
            estadoDeEjemplo(conPrecios = false),
            DialogoGastos.Ninguno,
            AccionesGastos()
        )
    }
}

@Preview(showBackground = true, name = "Gastos - receta sin ingredientes")
@Composable
private fun GastosSinIngredientes() {
    ReposteriaTheme {
        PasoGastos(
            "Torta de manjar",
            estadoDeEjemplo(sinIngredientes = true),
            DialogoGastos.Ninguno,
            AccionesGastos()
        )
    }
}

@Preview(showBackground = true, name = "Gastos - el cuadro de una promoción")
@Composable
private fun GastosCuadro() {
    ReposteriaTheme {
        PasoGastos(
            "Torta de manjar",
            estadoDeEjemplo(),
            DialogoGastos.Formulario(
                modo = ModoPrecio.TROZO,
                cantidad = "2",
                precioTotal = "1.500",
                trozosDeLaReceta = 6,
                tocado = true
            ),
            AccionesGastos()
        )
    }
}

@Preview(showBackground = true, name = "Gastos - oscuro")
@Composable
private fun GastosOscuro() {
    ReposteriaTheme(oscuro = true) {
        PasoGastos("Torta de manjar", estadoDeEjemplo(), DialogoGastos.Ninguno, AccionesGastos())
    }
}
