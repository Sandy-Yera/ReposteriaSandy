package com.sandyyera.reposteria.ui.recetas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.RecetaIngrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaSeccion
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.componentes.ComboBuscable
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el paso de cantidades. */
data class AccionesCantidades(
    val agregarIngrediente: (Long) -> Unit = {},
    val cambiarCantidadDe: (LineaDeIngrediente) -> Unit = {},
    val quitarIngrediente: (LineaDeIngrediente) -> Unit = {},
    val buscarIngrediente: (String) -> Unit = {},
    val elegirIngrediente: (Ingrediente) -> Unit = {},
    val crearIngredienteRapido: (String) -> Unit = {},
    val cambiarCantidadEscrita: (String) -> Unit = {},
    val guardarIngrediente: () -> Unit = {},

    val agregarSeccion: () -> Unit = {},
    val cambiarNombreDeSeccion: (String) -> Unit = {},
    val cambiarNombreDeLaPrimera: (String) -> Unit = {},
    val guardarSeccion: () -> Unit = {},
    val renombrarSeccion: (RecetaSeccion) -> Unit = {},
    val cambiarNombreEnRenombrado: (String) -> Unit = {},
    val guardarRenombrado: () -> Unit = {},
    val pedirBorrarSeccion: (SeccionConIngredientes) -> Unit = {},
    val confirmarBorrarSeccion: () -> Unit = {},

    val renombrarReceta: () -> Unit = {},
    val cambiarTituloDeLaReceta: (String) -> Unit = {},
    val guardarTituloDeLaReceta: () -> Unit = {},

    val cerrarDialogo: () -> Unit = {},
    val mensajeMostrado: () -> Unit = {},
    val irAlPaso: (PasoDeReceta) -> Unit = {},
    val cerrarReceta: () -> Unit = {}
)

/** El paso de cantidades conectado a su ViewModel. */
@Composable
fun PasoCantidadesScreen(
    modelo: CantidadesViewModel,
    pasoActual: PasoDeReceta,
    alElegirPaso: (PasoDeReceta) -> Unit,
    alCerrarReceta: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    // El diálogo viene por su propio canal, sin pasar por las consultas del estado: un
    // campo de texto que recibe su valor con retraso se rompe (ver CantidadesViewModel).
    val dialogo by modelo.dialogo.collectAsStateWithLifecycle()

    val acciones = remember(modelo, alElegirPaso, alCerrarReceta) {
        AccionesCantidades(
            agregarIngrediente = modelo::abrirAgregarIngrediente,
            cambiarCantidadDe = modelo::abrirCambiarCantidad,
            quitarIngrediente = modelo::quitarIngrediente,
            buscarIngrediente = modelo::buscarIngrediente,
            elegirIngrediente = modelo::elegirIngrediente,
            crearIngredienteRapido = modelo::crearIngredienteRapido,
            cambiarCantidadEscrita = modelo::cambiarCantidadEscrita,
            guardarIngrediente = modelo::guardarIngrediente,
            agregarSeccion = modelo::abrirAgregarSeccion,
            cambiarNombreDeSeccion = modelo::cambiarNombreDeSeccion,
            cambiarNombreDeLaPrimera = modelo::cambiarNombreDeLaPrimera,
            guardarSeccion = modelo::guardarSeccion,
            renombrarSeccion = modelo::abrirRenombrarSeccion,
            cambiarNombreEnRenombrado = modelo::cambiarNombreEnRenombrado,
            guardarRenombrado = modelo::guardarRenombrado,
            pedirBorrarSeccion = modelo::pedirBorrarSeccion,
            confirmarBorrarSeccion = modelo::confirmarBorrarSeccion,
            renombrarReceta = modelo::abrirRenombrarReceta,
            cambiarTituloDeLaReceta = modelo::cambiarTituloDeLaReceta,
            guardarTituloDeLaReceta = modelo::guardarTituloDeLaReceta,
            cerrarDialogo = modelo::cerrarDialogo,
            mensajeMostrado = modelo::mensajeMostrado,
            irAlPaso = alElegirPaso,
            cerrarReceta = alCerrarReceta
        )
    }

    PasoCantidades(
        estado = estado,
        dialogo = dialogo,
        acciones = acciones,
        pasoActual = pasoActual,
        modifier = modifier
    )
}

/**
 * El paso 1 de una receta: qué lleva y cuánto cuesta (8.2).
 *
 * El costo total va **fijo arriba**, fuera del desplazamiento. Es el número por el que
 * existe esta pantalla, y con una receta larga quedaría fuera de vista justo mientras se
 * ajustan las cantidades — que es cuando más importa verlo cambiar.
 *
 * Los encabezados de sección aparecen recién desde la segunda: una receta de un solo
 * conjunto no necesita que le pongan título a "todo lo que lleva". Esa decisión la toma
 * `debeMostrarNombreDeSeccion`, en `logica/`.
 *
 * **El título se toca para cambiarlo.** Antes se renombraba desde la lista de recetas, con
 * un lápiz al lado del nombre; ahora se hace acá, adentro, que es donde uno se da cuenta de
 * que la receta se llama distinto de lo que quedó siendo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasoCantidades(
    estado: EstadoCantidades,
    dialogo: DialogoCantidades,
    acciones: AccionesCantidades,
    pasoActual: PasoDeReceta = PasoDeReceta.CANTIDADES,
    modifier: Modifier = Modifier
) {
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    BackHandler(onBack = acciones.cerrarReceta)

    LaunchedEffect(estado.mensaje) {
        val texto = estado.mensaje ?: return@LaunchedEffect
        anfitrionDeMensajes.showSnackbar(texto)
        acciones.mensajeMostrado()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = estado.receta?.titulo ?: "Receta",
                            modifier = Modifier.clickable(onClick = acciones.renombrarReceta)
                        )
                    },
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
                .padding(horizontal = Medidas.grande)
                .padding(top = Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.medio)
        ) {
            CostoTotal(estado.costoTotal, estado.sinIngredientes)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico),
                contentPadding = PaddingValues(bottom = Medidas.grande)
            ) {
                estado.secciones.forEach { seccion ->
                    if (estado.mostrarNombresDeSeccion) {
                        item(key = "encabezado-${seccion.seccion.id}") {
                            EncabezadoDeSeccion(
                                seccion = seccion,
                                mostrarCosto = estado.mostrarCostoPorSeccion,
                                sePuedeBorrar = estado.secciones.size > 1,
                                alRenombrar = { acciones.renombrarSeccion(seccion.seccion) },
                                alBorrar = { acciones.pedirBorrarSeccion(seccion) }
                            )
                        }
                    }

                    items(seccion.lineas, key = { "linea-${it.item.id}" }) { linea ->
                        FilaDeIngrediente(
                            linea = linea,
                            alCambiar = { acciones.cambiarCantidadDe(linea) },
                            alQuitar = { acciones.quitarIngrediente(linea) }
                        )
                    }

                    item(key = "agregar-${seccion.seccion.id}") {
                        OutlinedButton(
                            onClick = { acciones.agregarIngrediente(seccion.seccion.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Medidas.objetivoTactil)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(
                                text = "  Agregar ingrediente",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                item(key = "agregar-seccion") {
                    TextButton(
                        onClick = acciones.agregarSeccion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Medidas.objetivoTactil)
                            .padding(top = Medidas.chico)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("  Agregar sección")
                    }
                }
            }
        }

        when (dialogo) {
            is DialogoCantidades.Ninguno -> Unit

            is DialogoCantidades.PonerIngrediente -> DialogoPonerIngrediente(
                estado = dialogo,
                catalogo = estado.catalogo,
                acciones = acciones
            )

            is DialogoCantidades.Seccion -> DialogoSeccionNueva(dialogo, acciones)

            is DialogoCantidades.RenombrarSeccion -> AlertDialog(
                onDismissRequest = acciones.cerrarDialogo,
                title = { Text("Nombre de la sección") },
                text = {
                    OutlinedTextField(
                        value = dialogo.nombre,
                        onValueChange = acciones.cambiarNombreEnRenombrado,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = dialogo.error != null,
                        supportingText = { dialogo.error?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = acciones.guardarRenombrado,
                        enabled = dialogo.puedeGuardar
                    ) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
                }
            )

            is DialogoCantidades.RenombrarReceta -> AlertDialog(
                onDismissRequest = acciones.cerrarDialogo,
                title = { Text("Título de la receta") },
                text = {
                    OutlinedTextField(
                        value = dialogo.titulo,
                        onValueChange = acciones.cambiarTituloDeLaReceta,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = dialogo.error != null,
                        supportingText = { dialogo.error?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = acciones.guardarTituloDeLaReceta,
                        enabled = dialogo.puedeGuardar
                    ) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
                }
            )

            is DialogoCantidades.ConfirmarBorrarSeccion -> AlertDialog(
                onDismissRequest = acciones.cerrarDialogo,
                title = { Text("¿Quitar '${dialogo.seccion.seccion.nombreSeccion}'?") },
                text = {
                    Text(
                        if (dialogo.seccion.lineas.isEmpty()) {
                            "Esta sección está vacía."
                        } else {
                            "Se van con ella sus ${dialogo.seccion.lineas.size} ingredientes, " +
                                "y el costo de la receta va a bajar."
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = acciones.confirmarBorrarSeccion,
                        enabled = !dialogo.borrando
                    ) {
                        Text(text = "Quitar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
                }
            )
        }
    }
}

/** El número por el que existe esta pantalla, siempre a la vista. */
@Composable
private fun CostoTotal(costo: Double, sinIngredientes: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(Medidas.medio)) {
            Text(text = "Cuesta hacerla", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "$${formatearNumero(costo)}",
                style = MaterialTheme.typography.headlineMedium
            )
            if (sinIngredientes) {
                Text(
                    text = "Agrega ingredientes y el costo se va armando solo.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * El encabezado de una sección, que solo existe desde la segunda en adelante.
 *
 * El costo de la sección va **al lado del nombre** y no en una fila propia debajo de sus
 * ingredientes. Dos razones: aprovecha espacio que ya estaba vacío en vez de agregar una
 * línea por sección —con cuatro secciones eso empuja el botón de agregar fuera de la
 * pantalla—, y lo deja donde se lee al comparar, que es recorriendo los encabezados de
 * arriba abajo. Puesto debajo habría que ir a buscarlo al final de cada bloque.
 *
 * Va en `onSurfaceVariant` y en `bodyMedium`: es un dato de apoyo, no el número principal
 * de la pantalla. Ese sigue siendo el total de arriba, que es el único que manda.
 *
 * **El nombre se toca para cambiarlo** y ya no hay lápiz al lado. Con el ícono, cada
 * encabezado gastaba dos botones de 48dp en una fila que además muestra el costo, y el
 * nombre quedaba apretado; sin él, el nombre ocupa lo que necesita y el único botón que
 * queda es el de borrar, que sí conviene que se vea y no se toque sin querer.
 */
@Composable
private fun EncabezadoDeSeccion(
    seccion: SeccionConIngredientes,
    mostrarCosto: Boolean,
    sePuedeBorrar: Boolean,
    alRenombrar: () -> Unit,
    alBorrar: () -> Unit
) {
    Column(modifier = Modifier.padding(top = Medidas.chico)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Medidas.objetivoTactil),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = seccion.seccion.nombreSeccion,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Medidas.objetivoTactil)
                    .clickable(onClick = alRenombrar)
                    .wrapContentHeight()
            )
            if (mostrarCosto) {
                Text(
                    text = "$${formatearNumero(seccion.costo)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Medidas.chico)
                )
            }
            if (sePuedeBorrar) {
                IconButton(onClick = alBorrar) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Quitar ${seccion.seccion.nombreSeccion}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

/**
 * Un ingrediente de la receta: cuánto lleva y cuánto aporta al costo.
 *
 * **Tocar la fila cambia los gramos**; el único botón que queda es el de quitarlo. Con el
 * lápiz al lado, una receta de doce ingredientes mostraba veinticuatro íconos y el que de
 * verdad hay que mirar con cuidado —la X roja— se perdía entre ellos.
 */
@Composable
private fun FilaDeIngrediente(
    linea: LineaDeIngrediente,
    alCambiar: () -> Unit,
    alQuitar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Medidas.objetivoTactil),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = alCambiar)
                .padding(vertical = Medidas.minimo)
        ) {
            Text(
                text = linea.ingrediente.nombre,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                // Se muestra la cuenta completa y no solo el total: así se entiende de
                // dónde sale el número, y un valor por gramo mal puesto salta a la vista.
                text = "${formatearNumero(linea.item.cantidadG)} g × " +
                    "$${formatearNumero(linea.ingrediente.valorPorGramo)} = " +
                    "$${formatearNumero(linea.subtotal)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = alQuitar) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Quitar ${linea.ingrediente.nombre} de la receta",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Elegir un ingrediente del catálogo y decir cuántos gramos lleva.
 *
 * El campo de gramos aparece **después** de elegir: pedir los dos a la vez obliga a decidir
 * cuánto antes de saber de qué. Al editar, el ingrediente ya viene puesto y el buscador no
 * se muestra — cambiar de ingrediente es quitar uno y poner otro, no editar este.
 */
@Composable
private fun DialogoPonerIngrediente(
    estado: DialogoCantidades.PonerIngrediente,
    catalogo: List<Ingrediente>,
    acciones: AccionesCantidades
) {
    val elegido = estado.elegido

    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = {
            Text(if (estado.editando != null) "Cambiar la cantidad" else "Agregar ingrediente")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                if (elegido == null) {
                    ComboBuscable(
                        opciones = catalogo,
                        textoDe = { it.nombre },
                        busqueda = estado.busqueda,
                        alBuscar = acciones.buscarIngrediente,
                        alElegir = acciones.elegirIngrediente,
                        marcador = "Buscar ingrediente",
                        alCrear = acciones.crearIngredienteRapido
                    )
                } else {
                    Text(text = elegido.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "$${formatearNumero(elegido.valorPorGramo)} por gramo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CampoNumerico(
                        valor = estado.cantidad,
                        alCambiar = acciones.cambiarCantidadEscrita,
                        etiqueta = "Cuántos gramos lleva",
                        error = estado.errorCantidad,
                        accionDelTeclado = ImeAction.Done
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.guardarIngrediente, enabled = estado.puedeGuardar) {
                Text(if (estado.editando != null) "Guardar" else "Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

/**
 * Agregar una sección, bautizando de paso la que era invisible.
 *
 * El campo de arriba solo aparece cuando la receta tenía una sola sección. Es el momento
 * de 8.2: la sección automática deja de ser invisible y necesita un nombre, y se propone
 * el título de la receta porque suele ser la parte principal.
 */
@Composable
private fun DialogoSeccionNueva(
    estado: DialogoCantidades.Seccion,
    acciones: AccionesCantidades
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("Agregar sección") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                val nombreDeLaPrimera = estado.nombreDeLaPrimera
                if (nombreDeLaPrimera != null) {
                    Text(
                        text = "Al haber dos partes hay que poder distinguirlas. ¿Cómo se " +
                            "llama lo que ya tenías cargado?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = nombreDeLaPrimera,
                        onValueChange = acciones.cambiarNombreDeLaPrimera,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Lo que ya estaba") },
                        singleLine = true,
                        isError = estado.errorDeLaPrimera != null,
                        supportingText = { estado.errorDeLaPrimera?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        )
                    )
                    HorizontalDivider()
                }

                OutlinedTextField(
                    value = estado.nombre,
                    onValueChange = acciones.cambiarNombreDeSeccion,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("La sección nueva") },
                    singleLine = true,
                    isError = estado.error != null,
                    supportingText = { estado.error?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.guardarSeccion, enabled = estado.puedeGuardar) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

// --- Vistas previas ---

private val harina = Ingrediente(id = 1, nombre = "Harina", valorPorGramo = 1.2)
private val manjar = Ingrediente(id = 2, nombre = "Manjar", valorPorGramo = 4.8)

private fun seccion(id: Long, nombre: String, vararg lineas: LineaDeIngrediente) =
    SeccionConIngredientes(
        seccion = RecetaSeccion(id = id, recetaId = 1, nombreSeccion = nombre),
        lineas = lineas.toList()
    )

private fun linea(id: Long, seccionId: Long, ingrediente: Ingrediente, gramos: Double) =
    LineaDeIngrediente(
        item = RecetaIngrediente(
            id = id, seccionId = seccionId, ingredienteId = ingrediente.id, cantidadG = gramos
        ),
        ingrediente = ingrediente
    )

private val recetaSimple = EstadoCantidades(
    receta = Receta(id = 1, titulo = "Torta de manjar"),
    secciones = listOf(
        seccion(1, "General", linea(1, 1, harina, 500.0), linea(2, 1, manjar, 250.0))
    ),
    costoTotal = 1800.0,
    catalogo = listOf(harina, manjar),
    cargando = false
)

private val recetaConDosSecciones = recetaSimple.copy(
    secciones = listOf(
        seccion(1, "Bizcocho", linea(1, 1, harina, 500.0)),
        seccion(2, "Relleno", linea(2, 2, manjar, 250.0))
    )
)

@Preview(showBackground = true, name = "Cantidades - receta simple")
@Composable
private fun CantidadesSimple() {
    ReposteriaTheme {
        PasoCantidades(recetaSimple, DialogoCantidades.Ninguno, AccionesCantidades())
    }
}

@Preview(showBackground = true, name = "Cantidades - dos secciones")
@Composable
private fun CantidadesDosSecciones() {
    ReposteriaTheme {
        PasoCantidades(recetaConDosSecciones, DialogoCantidades.Ninguno, AccionesCantidades())
    }
}

@Preview(showBackground = true, name = "Cantidades - oscuro")
@Composable
private fun CantidadesOscuro() {
    ReposteriaTheme(oscuro = true) {
        PasoCantidades(recetaConDosSecciones, DialogoCantidades.Ninguno, AccionesCantidades())
    }
}

@Preview(showBackground = true, name = "Cantidades - receta vacía")
@Composable
private fun CantidadesVacia() {
    ReposteriaTheme {
        PasoCantidades(
            recetaSimple.copy(
                secciones = listOf(seccion(1, "General")),
                costoTotal = 0.0
            ),
            DialogoCantidades.Ninguno,
            AccionesCantidades()
        )
    }
}

@Preview(showBackground = true, name = "Cantidades - bautizando la primera sección")
@Composable
private fun CantidadesBautizando() {
    ReposteriaTheme {
        PasoCantidades(
            recetaSimple,
            DialogoCantidades.Seccion(nombre = "", nombreDeLaPrimera = "Torta de manjar"),
            AccionesCantidades()
        )
    }
}
