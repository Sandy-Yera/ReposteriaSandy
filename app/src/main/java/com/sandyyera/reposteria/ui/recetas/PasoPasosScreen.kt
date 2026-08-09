package com.sandyyera.reposteria.ui.recetas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.logica.partes.AtajoDePaso
import com.sandyyera.reposteria.logica.partes.BloqueDePasos
import com.sandyyera.reposteria.logica.partes.PasoNumerado
import com.sandyyera.reposteria.logica.partes.PasoParaMostrar
import com.sandyyera.reposteria.logica.partes.TITULO_GENERAL
import com.sandyyera.reposteria.logica.partes.TituloDePaso
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el paso "Pasos". */
data class AccionesPasos(
    val agregarPaso: () -> Unit = {},
    val cambiarTexto: (Long, String, Int) -> Unit = { _, _, _ -> },
    val guardarPaso: (Long) -> Unit = {},
    val guardarTodoLoPendiente: () -> Unit = {},
    val abrirElegirTitulo: (Long) -> Unit = {},
    val elegirTitulo: (TituloDePaso) -> Unit = {},
    val abrirAyuda: () -> Unit = {},
    val cerrarAyuda: () -> Unit = {},
    val elegirIngrediente: (String) -> Unit = {},
    val empezarTituloNuevo: () -> Unit = {},
    val cancelarTituloNuevo: () -> Unit = {},
    val cambiarNombreDelTitulo: (String) -> Unit = {},
    val cambiarNombreDeLaPrimera: (String) -> Unit = {},
    val guardarTituloNuevo: () -> Unit = {},
    val cursorAplicado: () -> Unit = {},
    val moverPaso: (Long, Boolean) -> Unit = { _, _ -> },
    val pedirBorrado: (PasoParaMostrar) -> Unit = {},
    val confirmarBorrado: () -> Unit = {},
    val cerrarDialogo: () -> Unit = {},
    val mensajeMostrado: () -> Unit = {},
    val irAlPaso: (PasoDeReceta) -> Unit = {},
    val cerrarReceta: () -> Unit = {}
)

/** El paso "Pasos" conectado a su ViewModel. */
@Composable
fun PasoPasosScreen(
    tituloReceta: String,
    desplazamientoDePasos: ScrollState,
    modelo: PasosViewModel,
    pasoActual: PasoDeReceta,
    alElegirPaso: (PasoDeReceta) -> Unit,
    alCerrarReceta: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val dialogo by modelo.dialogo.collectAsStateWithLifecycle()
    val cursorPedido by modelo.cursorPedido.collectAsStateWithLifecycle()

    val acciones = remember(modelo, alElegirPaso, alCerrarReceta) {
        AccionesPasos(
            agregarPaso = modelo::agregarPaso,
            cambiarTexto = modelo::cambiarTexto,
            guardarPaso = modelo::guardarPaso,
            guardarTodoLoPendiente = modelo::guardarTodoLoPendiente,
            abrirElegirTitulo = { modelo.abrirElegirTitulo(it) },
            elegirTitulo = modelo::elegirTitulo,
            abrirAyuda = modelo::abrirAyuda,
            cerrarAyuda = modelo::cerrarAyuda,
            elegirIngrediente = modelo::elegirIngrediente,
            empezarTituloNuevo = modelo::empezarTituloNuevo,
            cancelarTituloNuevo = modelo::cancelarTituloNuevo,
            cambiarNombreDelTitulo = modelo::cambiarNombreDelTitulo,
            cambiarNombreDeLaPrimera = modelo::cambiarNombreDeLaPrimera,
            guardarTituloNuevo = modelo::guardarTituloNuevo,
            cursorAplicado = modelo::cursorAplicado,
            moverPaso = modelo::moverPaso,
            pedirBorrado = modelo::pedirBorrado,
            confirmarBorrado = modelo::confirmarBorrado,
            cerrarDialogo = modelo::cerrarDialogo,
            mensajeMostrado = modelo::mensajeMostrado,
            irAlPaso = alElegirPaso,
            cerrarReceta = alCerrarReceta
        )
    }

    PasoPasos(
        tituloReceta = tituloReceta,
        estado = estado,
        dialogo = dialogo,
        cursorPedido = cursorPedido,
        acciones = acciones,
        pasoActual = pasoActual,
        modifier = modifier,
        desplazamientoDePasos = desplazamientoDePasos
    )
}

/**
 * El paso "Pasos" de una receta (8.8).
 *
 * **Los pasos van agrupados bajo títulos**, no en una lista plana, y esa agrupación no se decide
 * acá: viene armada de `bloquesDePasos` (8.8.1). La pantalla solo dibuja lo que le llega, que es
 * lo que permite probar las cuatro reglas del agrupamiento sin celular.
 *
 * **Cada paso es un campo de texto abierto, no un cuadro que hay que abrir.** Escribir pasos es
 * lo que más se hace en esta pantalla, y meter cada uno detrás de un diálogo convertiría
 * escribir una receta de diez pasos en veinte toques de más.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasoPasos(
    tituloReceta: String,
    estado: EstadoPasos,
    dialogo: DialogoPasos,
    acciones: AccionesPasos,
    cursorPedido: PosicionDelCursor? = null,
    pasoActual: PasoDeReceta = PasoDeReceta.PASOS,
    modifier: Modifier = Modifier,
    desplazamientoDePasos: ScrollState = rememberScrollState()
) {
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    BackHandler(enabled = true) {
        if (dialogo is DialogoPasos.Ninguno) acciones.cerrarReceta() else acciones.cerrarDialogo()
    }

    // Guardar al irse, **a nivel de pantalla y no de cada campo**: con una lista perezosa, un
    // campo que sale de la vista al desplazarse también se desmonta, y ahí el guardado se
    // dispararía por desplazar. Es la misma lección del paso de duración.
    val guardarPendiente by rememberUpdatedState(acciones.guardarTodoLoPendiente)
    DisposableEffect(Unit) {
        onDispose { guardarPendiente() }
    }

    LaunchedEffect(estado.mensaje) {
        val texto = estado.mensaje ?: return@LaunchedEffect
        try {
            anfitrionDeMensajes.showSnackbar(texto)
        } finally {
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
                            Icon(Icons.Default.Close, contentDescription = "Salir de la receta")
                        }
                    },
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
            item { BotonDeAtajos(acciones.abrirAyuda) }

            if (!estado.cargando && estado.vacio) {
                item { TodaviaSinPasos() }
            }

            estado.bloques.forEach { bloque ->
                bloque.encabezado?.let { texto ->
                    item(key = "titulo-${bloque.titulo}-${bloque.pasos.first().paso.id}") {
                        EncabezadoDeBloque(
                            texto = texto,
                            vieneDe = estado.deDondeViene(bloque.titulo),
                            esGeneralAnidado = bloque.esGeneralAnidado
                        )
                    }
                }
                items(
                    count = bloque.pasos.size,
                    key = { i -> bloque.pasos[i].paso.id }
                ) { i ->
                    FilaDeUnPaso(
                        numerado = bloque.pasos[i],
                        conSangria = bloque.esGeneralAnidado,
                        estado = estado,
                        cursorPedido = cursorPedido,
                        acciones = acciones
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = acciones.agregarPaso,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Medidas.objetivoTactil)
                        .padding(top = Medidas.chico)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Agregar un paso", modifier = Modifier.padding(start = Medidas.chico))
                }
            }
        }
    }

    when (dialogo) {
        is DialogoPasos.Ninguno -> Unit
        is DialogoPasos.ElegirTitulo -> CuadroDeTitulo(dialogo, acciones)
        is DialogoPasos.ElegirIngrediente -> CuadroDeIngredientes(dialogo, acciones)
        is DialogoPasos.Ayuda -> CuadroDeAtajos(acciones)
        is DialogoPasos.ConfirmarBorrado -> ConfirmarBorrarPaso(dialogo, acciones)
    }
}

/** Con la receta sin pasos se dice que es opcional, para que no parezca que falta llenarlo. */
@Composable
private fun TodaviaSinPasos() {
    Text(
        text = "Todavía no escribiste los pasos. Es opcional: hay recetas que se saben de " +
            "memoria y no necesitan que nadie las anote.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * El encabezado de un bloque.
 *
 * El **general anidado** —los pasos que vinieron con una receta traída— se dibuja con sangría y
 * más chico (8.8). Son cosas distintas: uno habla del bizcocho que se copió y el otro de la
 * torta entera, y aplanarlos los volvería indistinguibles.
 */
@Composable
private fun EncabezadoDeBloque(texto: String, vieneDe: String?, esGeneralAnidado: Boolean) {
    Column(
        modifier = Modifier.padding(
            top = Medidas.chico,
            start = if (esGeneralAnidado) Medidas.medio else 0.dp
        )
    ) {
        Text(
            text = texto,
            style = if (esGeneralAnidado) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.titleMedium,
            fontWeight = if (esGeneralAnidado) null else FontWeight.Bold,
            color = if (esGeneralAnidado) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
        // **Cada bloque ajeno lo dice** (8.11.2). La sangría del general anidado ya avisa que
        // vino de algo, pero no de qué; y con dos recetas traídas seguidas eso no alcanza para
        // ver dónde termina una y empieza la otra.
        vieneDe?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Una fila: su número, el campo de texto, y los botones de mover y quitar.
 *
 * **El número no se guarda**, sale de la posición (8.8.1): guardarlo obligaría a reescribir
 * todos los de abajo cada vez que se agrega uno en medio, y bastaría una escritura perdida para
 * dejar dos pasos con el mismo número.
 *
 * **Tocar el número abre el título.** Es la aplicación de "tocar la cosa hace lo principal"
 * (8.4.1, #3) al único lugar donde cabía: el campo de texto ya está tomado por escribir.
 */
@Composable
private fun FilaDeUnPaso(
    numerado: PasoNumerado,
    conSangria: Boolean,
    estado: EstadoPasos,
    cursorPedido: PosicionDelCursor?,
    acciones: AccionesPasos
) {
    val paso = numerado.paso
    // El foco anterior tiene que sobrevivir a la recomposición, de ahí el `mutableStateOf`:
    // `onFocusChanged` avisa de **cada** cambio, y lo que dispara el guardado es *perderlo*, no
    // tenerlo. Con un `remember { false }` a secas el valor se reponía en cada redibujado y el
    // guardado no se disparaba nunca.
    var teniaFoco by remember { mutableStateOf(false) }

    // El campo trabaja con `TextFieldValue` y no con `String` porque los atajos necesitan saber
    // **dónde está el cursor** (8.8): `:ingredientes:` se dispara al terminar de escribirlo ahí
    // donde está la mano, no porque la palabra aparezca en otro renglón. Es el mismo recurso que
    // usa `CampoNumerico` para no mandar el cursor al final en cada tecla.
    val texto = estado.textoDe(paso)
    var recordado by remember { mutableStateOf(TextFieldValue(texto, TextRange(texto.length))) }
    val campo = if (recordado.text == texto) recordado else TextFieldValue(texto, TextRange(texto.length))

    // Cuando un atajo se reemplaza, el largo del texto cambia y el cursor tiene que ir donde
    // quedó lo insertado. Se avisa de vuelta para que esto no se repita en cada dibujado.
    LaunchedEffect(cursorPedido) {
        val pedido = cursorPedido ?: return@LaunchedEffect
        if (pedido.pasoId != paso.id) return@LaunchedEffect
        recordado = TextFieldValue(
            text = estado.textoDe(paso),
            selection = TextRange(pedido.cursor.coerceIn(0, estado.textoDe(paso).length))
        )
        acciones.cursorAplicado()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (conSangria) Medidas.medio else 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "${numerado.numero}.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .heightIn(min = Medidas.objetivoTactil)
                .clickable { acciones.abrirElegirTitulo(paso.id) }
                .padding(top = Medidas.medio, end = Medidas.chico)
        )
        OutlinedTextField(
            value = campo,
            onValueChange = {
                recordado = it
                acciones.cambiarTexto(paso.id, it.text, it.selection.end)
            },
            placeholder = { Text("Qué se hace en este paso") },
            supportingText = estado.errorDe(paso)?.let { { Text(it) } },
            isError = estado.errorDe(paso) != null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { foco ->
                    if (teniaFoco && !foco.isFocused) acciones.guardarPaso(paso.id)
                    teniaFoco = foco.isFocused
                }
        )
        Column {
            IconButton(onClick = { acciones.moverPaso(paso.id, true) }) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir este paso")
            }
            IconButton(onClick = { acciones.moverPaso(paso.id, false) }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar este paso")
            }
            IconButton(onClick = { acciones.pedirBorrado(paso) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Quitar este paso",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * El botón que muestra los atajos, arriba del todo (8.8).
 *
 * Lo pidió Sandy: *"en pasos, quiero que arriba del todo haya un botón que al presionarlo muestre
 * todas esas opciones que existen"*. Va **dentro de la lista y no fijo en la barra** porque se usa
 * al empezar, cuando todavía no hay nada escrito; una vez abajo, lo que sirve es `:info:`, que
 * está justamente para no tener que volver a subir.
 */
@Composable
private fun BotonDeAtajos(alTocar: () -> Unit) {
    OutlinedButton(
        onClick = alTocar,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Medidas.objetivoTactil)
            .padding(bottom = Medidas.chico)
    ) {
        Icon(Icons.Default.Info, contentDescription = null)
        Text("Atajos que puedes escribir", modifier = Modifier.padding(start = Medidas.chico))
    }
}

/**
 * La lista de atajos que existen.
 *
 * **Sale del enum `AtajoDePaso` y no de una lista escrita acá**, con su `escritura` y su
 * `queHace`. Un atajo nuevo aparece en esta ayuda sin que nadie se acuerde de agregarlo — que es
 * exactamente la clase de olvido que deja una ayuda mintiendo.
 */
@Composable
private fun CuadroDeAtajos(acciones: AccionesPasos) {
    AlertDialog(
        onDismissRequest = acciones.cerrarAyuda,
        title = { Text("Atajos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                Text(
                    text = "Escríbelos dentro de un paso, con los dos puntos incluidos. " +
                        "Se borran solos al elegir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AtajoDePaso.entries.forEach { atajo ->
                    Column {
                        Text(
                            text = atajo.escritura,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = atajo.queHace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.cerrarAyuda) { Text("Entendido") }
        }
    )
}

/**
 * Los ingredientes de esta receta con su cantidad, agrupados por parte (8.8).
 *
 * **Van por sección y no en una lista sola**, que es lo que pidió Sandy: el mismo ingrediente
 * puede llevar 600 g en el bizcocho y 50 en el almíbar, y aplanarlo dejaría dos filas iguales sin
 * decir cuál es cuál — o peor, una sola con la cantidad equivocada.
 *
 * Lo que se escribe en el paso es **exactamente la frase que se tocó**, no una rehecha acá:
 * cualquier diferencia entre las dos saldría en el paso y solo se notaría leyéndolo después.
 *
 * Con la receta sin ingredientes se dice eso mismo en vez de mostrar una lista vacía: el cuadro
 * abierto y sin nada dejaría pensando que se rompió algo.
 */
@Composable
private fun CuadroDeIngredientes(
    estado: DialogoPasos.ElegirIngrediente,
    acciones: AccionesPasos
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("¿Cuál?") },
        text = {
            if (estado.vacio) {
                Text(
                    "Esta receta todavía no tiene ingredientes. Se agregan en el paso de " +
                        "Cantidades."
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
                ) {
                    estado.grupos.forEach { grupo ->
                        // El nombre de la sección viene en `null` cuando no hay que mostrarlo
                        // —una receta de una sola parte, sin nombre propio (8.2)—, y entonces
                        // la lista va directa, sin un encabezado que repita el título.
                        grupo.nombre?.let { nombre ->
                            Text(
                                text = nombre,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = Medidas.chico)
                            )
                        }
                        if (grupo.lineas.isEmpty()) {
                            // Se dice, en vez de esconder la sección: una parte que no aparece
                            // deja dudando entre "no tiene nada" y "se perdió".
                            Text(
                                text = "Todavía sin ingredientes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        grupo.lineas.forEach { linea ->
                            Text(
                                // Con la cantidad, que es lo que se quiere escribir en el paso.
                                // El mismo ingrediente puede llevar 600 g en una parte y 50 en
                                // otra, y sin el número habría que ir a buscarlo.
                                text = linea.comoSeLee,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Medidas.objetivoTactil)
                                    .clickable { acciones.elegirIngrediente(linea.comoSeEscribe) }
                                    .padding(vertical = Medidas.chico)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

/** El menú de títulos. Lo ofrecido sale de `titulosDisponibles`, nunca de una lista propia. */
@Composable
private fun CuadroDeTitulo(estado: DialogoPasos.ElegirTitulo, acciones: AccionesPasos) {
    val creando = estado.creando
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text(if (creando == null) "¿De qué parte es este paso?" else "Parte nueva") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                if (creando == null) {
                    Text(
                        text = "El General es para lo que no pertenece a ninguna parte, y se " +
                            "puede repetir. Las partes de la receta, una sola vez cada una.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    estado.disponibles.forEach { titulo ->
                        Text(
                            text = titulo?.let { estado.nombrePorId[it] } ?: TITULO_GENERAL,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Medidas.objetivoTactil)
                                .clickable { acciones.elegirTitulo(titulo) }
                                .padding(vertical = Medidas.chico)
                        )
                    }
                    // Crear una parte desde acá, sin irse al paso de cantidades: una receta que
                    // no tenía secciones no tenía de dónde sacar títulos, y decidir que hay
                    // partes es algo que pasa justamente mientras se escriben los pasos (8.8).
                    OutlinedButton(
                        onClick = acciones.empezarTituloNuevo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Medidas.objetivoTactil)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Crear una parte", modifier = Modifier.padding(start = Medidas.chico))
                    }
                } else {
                    // El bautizo aparece **solo cuando hace falta**: si la receta ya tenía
                    // partes con nombre propio, no hay nada que renombrar (8.2).
                    creando.nombreDeLaPrimera?.let { primera ->
                        Text(
                            text = "Esta receta tenía una sola parte sin nombre. Al partirla en " +
                                "dos hay que nombrarla, o quedarían dos encabezados iguales.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = primera,
                            onValueChange = acciones.cambiarNombreDeLaPrimera,
                            label = { Text("Cómo se llama la que ya está") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = creando.errorDeLaPrimera != null,
                            supportingText = { creando.errorDeLaPrimera?.let { Text(it) } },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            )
                        )
                    }
                    OutlinedTextField(
                        value = creando.nombre,
                        onValueChange = acciones.cambiarNombreDelTitulo,
                        label = { Text("Cómo se llama la parte nueva") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = creando.error != null,
                        supportingText = { creando.error?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        )
                    )
                    Text(
                        text = "Se crea y este paso queda debajo de ella.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (creando != null) {
                TextButton(
                    onClick = acciones.guardarTituloNuevo,
                    enabled = creando.puedeGuardar
                ) { Text("Crear") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = if (creando == null) acciones.cerrarDialogo
                else acciones.cancelarTituloNuevo
            ) { Text(if (creando == null) "Cancelar" else "Volver") }
        }
    )
}

@Composable
private fun ConfirmarBorrarPaso(estado: DialogoPasos.ConfirmarBorrado, acciones: AccionesPasos) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("¿Quitar este paso?") },
        // Se muestra lo que dice, porque es lo que se pierde y no se puede deshacer.
        text = { Text("Se va lo que escribiste: «${estado.texto.take(120)}»") },
        confirmButton = {
            TextButton(onClick = acciones.confirmarBorrado) { Text("Quitar") }
        },
        dismissButton = { TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") } }
    )
}

// --- Vistas previas ---

private fun pasoDeEjemplo(id: Long, texto: String, titulo: TituloDePaso, orden: Int) =
    PasoParaMostrar(id = id, texto = texto, titulo = titulo, orden = orden)

private fun estadoDeEjemplo() = EstadoPasos(
    bloques = listOf(
        BloqueDePasos(
            titulo = 1L, encabezado = "Bizcocho", esGeneralAnidado = false,
            pasos = listOf(
                PasoNumerado(pasoDeEjemplo(1, "Batir las claras a punto de nieve.", 1L, 0), 1),
                PasoNumerado(pasoDeEjemplo(2, "Incorporar la harina en tres tandas.", 1L, 1), 2)
            )
        ),
        BloqueDePasos(
            titulo = null, encabezado = TITULO_GENERAL, esGeneralAnidado = false,
            pasos = listOf(PasoNumerado(pasoDeEjemplo(3, "Dejar enfriar.", null, 2), 3))
        )
    ),
    cargando = false
)

@Preview(showBackground = true, name = "Pasos - con bloques")
@Composable
private fun PasosConBloques() {
    ReposteriaTheme {
        PasoPasos("Torta de manjar", estadoDeEjemplo(), DialogoPasos.Ninguno, AccionesPasos())
    }
}

@Preview(showBackground = true, name = "Pasos - todavía vacío")
@Composable
private fun PasosVacio() {
    ReposteriaTheme {
        PasoPasos(
            "Torta de manjar",
            EstadoPasos(cargando = false),
            DialogoPasos.Ninguno,
            AccionesPasos()
        )
    }
}
