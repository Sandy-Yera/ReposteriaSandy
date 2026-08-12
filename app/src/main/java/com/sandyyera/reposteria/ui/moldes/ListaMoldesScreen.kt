package com.sandyyera.reposteria.ui.moldes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.data.db.entidades.Molde
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.FormaDelCorte
import com.sandyyera.reposteria.logica.moldes.nombreDelCorte
import com.sandyyera.reposteria.logica.moldes.medidasEnTexto
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.validaciones.CampoDeMolde
import com.sandyyera.reposteria.ui.componentes.BarraBusqueda
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el catálogo de moldes. */
data class AccionesMoldes(
    val buscar: (String) -> Unit = {},
    val pedirAlta: () -> Unit = {},
    val editar: (Molde) -> Unit = {},
    val cambiarNombre: (String) -> Unit = {},
    val elegirForma: (TipoFormaMolde) -> Unit = {},
    val cambiarMedida: (CampoDeMolde, String) -> Unit = { _, _ -> },
    val elegirCorte: (FormaDelCorte) -> Unit = {},
    val cambiarTrozosDeLaPrueba: (String) -> Unit = {},
    val cambiarLargoDeCorte: (String) -> Unit = {},
    val cambiarAnchoDeCorte: (String) -> Unit = {},
    val cambiarNotas: (String) -> Unit = {},
    val guardar: () -> Unit = {},
    val pedirBorrado: (Molde) -> Unit = {},
    val confirmarBorrado: () -> Unit = {},
    val cerrarDialogo: () -> Unit = {},
    val mensajeMostrado: () -> Unit = {},
    val abrirMenu: () -> Unit = {}
)

/** El catálogo de moldes conectado a su ViewModel. */
@Composable
fun ListaMoldesScreen(
    modelo: MoldesViewModel,
    alAbrirMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    // Por su propio canal, sin pasar por la consulta del catálogo (ver MoldesViewModel).
    val dialogo by modelo.dialogo.collectAsStateWithLifecycle()

    val acciones = remember(modelo, alAbrirMenu) {
        AccionesMoldes(
            buscar = modelo::buscar,
            pedirAlta = modelo::abrirAlta,
            editar = modelo::abrirEdicion,
            cambiarNombre = modelo::cambiarNombre,
            elegirForma = modelo::elegirForma,
            cambiarMedida = modelo::cambiarMedida,
            elegirCorte = modelo::elegirCorte,
            cambiarTrozosDeLaPrueba = modelo::cambiarTrozosDeLaPrueba,
            cambiarLargoDeCorte = modelo::cambiarLargoDeCorte,
            cambiarAnchoDeCorte = modelo::cambiarAnchoDeCorte,
            cambiarNotas = modelo::cambiarNotas,
            guardar = modelo::guardar,
            pedirBorrado = modelo::pedirBorrado,
            confirmarBorrado = modelo::confirmarBorrado,
            cerrarDialogo = modelo::cerrarDialogo,
            mensajeMostrado = modelo::mensajeMostrado,
            abrirMenu = alAbrirMenu
        )
    }

    ListaMoldes(estado = estado, dialogo = dialogo, acciones = acciones, modifier = modifier)
}

/**
 * El dibujo del catálogo: botón fijo arriba, buscador, y los moldes debajo.
 *
 * Mismo patrón que ingredientes y recetas (8.1): el botón va fuera del área que se
 * desplaza, porque si no habría que subir hasta arriba cada vez que se quiere crear uno.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaMoldes(
    estado: EstadoMoldes,
    dialogo: DialogoMolde,
    acciones: AccionesMoldes,
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
                title = { Text("Moldes") },
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
                Text("  Crear molde")
            }

            if (estado.hayMoldes) {
                BarraBusqueda(
                    texto = estado.busqueda,
                    alCambiar = acciones.buscar,
                    marcador = "Buscar un molde"
                )
            }

            when {
                estado.catalogoVacio -> Aviso(
                    "Todavía no tienes moldes.\nCrea uno para poder reescalar recetas."
                )
                estado.busquedaSinResultados -> Aviso("No encontré ningún molde con ese nombre.")
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Medidas.chico),
                    contentPadding = PaddingValues(bottom = Medidas.grande)
                ) {
                    items(estado.visibles, key = { it.id }) { molde ->
                        TarjetaMolde(
                            molde = molde,
                            alEditar = { acciones.editar(molde) },
                            alBorrar = { acciones.pedirBorrado(molde) }
                        )
                    }
                }
            }
        }
    }

    when (dialogo) {
        is DialogoMolde.Ninguno -> Unit
        is DialogoMolde.Formulario -> FormularioMolde(dialogo, acciones)
        is DialogoMolde.ConfirmarBorrado -> ConfirmarBorradoMolde(dialogo, acciones)
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(Medidas.grande)
    )
}

/**
 * Un molde de la lista.
 *
 * Muestra el área, el volumen y el alto **calculados**, nunca guardados (5.2): son
 * propiedades de `DimensionesMolde`, así que no pueden quedar desincronizadas de las
 * medidas. Tocar la tarjeta abre la edición; el ícono aparte es solo para borrar.
 */
@Composable
private fun TarjetaMolde(
    molde: Molde,
    alEditar: () -> Unit,
    alBorrar: () -> Unit
) {
    Card(
        // Tocar la tarjeta abre la edición, igual que en recetas: es LA acción de un molde
        // -corregir una medida- y un botón más dejaría la fila apretada.
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alEditar)
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
                Text(molde.nombre, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = resumenDeMedidas(molde.dimensiones),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // La nota, **en la fila y no escondida en el detalle** (9.6). Lo que se pidió fue
                // "un mensaje corto que sea **visible**", y algo que hay que abrir para leer no
                // sirve para lo que motivó el campo: enterarse del diámetro de un molde exótico
                // antes de ir a medirlo otra vez.
                molde.notas?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = alBorrar) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar ${molde.nombre}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * La línea de abajo de cada tarjeta: forma, **medidas tomadas**, área y volumen.
 *
 * Las medidas tomadas se agregaron después de que faltaran: la tarjeta decía "Rectángulo · 6
 * cm de alto · 600 cm² · 3.600 cm³", y con eso no se sabe cuál molde es. Frente al mueble uno
 * busca el de 20 por 30, no el de 600 cm²; el área y el volumen sirven para otra cosa —
 * comparar dos moldes entre sí— y por eso se quedan, detrás.
 *
 * El alto ya viene dentro de `medidasEnTexto`, así que no se repite acá.
 */
private fun resumenDeMedidas(dimensiones: DimensionesMolde): String {
    val forma = dimensiones.tipoForma?.let { nombreDeLaForma(it) } ?: "Sin forma"
    val tomadas = medidasEnTexto(dimensiones, ::formatearNumero)
    // El área y el volumen se calculan, y calcular exige que las medidas estén completas.
    // Un molde a medio guardar no debería existir, pero si existiera la lista tiene que
    // dibujarse igual en vez de cerrar la app.
    val calculadas = runCatching {
        "${formatearNumero(dimensiones.areaCm2)} cm² · " +
            "${formatearNumero(dimensiones.volumenCm3)} cm³"
    }.getOrNull()

    return listOfNotNull(forma, tomadas, calculadas).joinToString(" · ")
}

/**
 * El formulario de un molde: nombre, forma, y **solo las medidas de esa forma**.
 *
 * Qué campos dibujar sale de `estado.campos`, que a su vez viene de `camposDe` en
 * `logica/`. Es la misma lista que usa la validación, así que no hay forma de que la
 * pantalla pida una medida que nadie exige, ni de que exija una que nadie pidió.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormularioMolde(
    estado: DialogoMolde.Formulario,
    acciones: AccionesMoldes
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text(if (estado.editando == null) "Crear molde" else "Editar molde") },
        text = {
            // `Column` con desplazamiento y no `LazyColumn`, igual que el resto de los
            // cuadros del proyecto: acá hay a lo más seis elementos y una lista perezosa
            // dentro de un cuadro flotante pelea por la altura disponible.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                OutlinedTextField(
                    value = estado.nombre,
                    onValueChange = acciones.cambiarNombre,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    singleLine = true,
                    isError = estado.errorNombre != null,
                    supportingText = { estado.errorNombre?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )

                // Chips y no un desplegable: son cinco opciones cortas, caben a la vista, y
                // elegir con un toque evita el menú flotante que en un celular tapa justo el
                // formulario que se está llenando.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                    TipoFormaMolde.entries.forEach { forma ->
                        FilterChip(
                            selected = estado.forma == forma,
                            onClick = { acciones.elegirForma(forma) },
                            label = { Text(nombreDeLaForma(forma)) }
                        )
                    }
                }

                estado.errorForma?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                estado.forma?.let { forma ->
                    ayudaDeLaForma(forma)?.let { ayuda ->
                        Text(
                            text = ayuda,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                estado.campos.forEach { campo ->
                    CampoNumerico(
                        valor = estado.medidas[campo].orEmpty(),
                        alCambiar = { acciones.cambiarMedida(campo, it) },
                        etiqueta = campo.etiqueta,
                        error = estado.errorDe(campo),
                        accionDelTeclado =
                            if (campo == estado.campos.last()) ImeAction.Done else ImeAction.Next
                    )
                }

                // --- Cómo se corta (9.4) ---
                //
                // **Se pregunta siempre**, no solo donde no hay sugerencia. Antes se ofrecía
                // únicamente en el triángulo y el exótico, con el argumento de que en las otras
                // tres la respuesta es obvia — cierto salvo por un detalle: la sugerencia puede
                // no acertar, y un molde rectangular cortado en cuñas no tenía cómo decirse.
                // Viene pre-elegida, así que quien no tenga nada que corregir no toca nada.
                if (estado.hayQuePreguntarElCorte) {
                    Text(
                        text = "¿Cómo se corta?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        // Se dice para qué sirve, porque es opcional y si no nadie entiende
                        // por qué se lo preguntan.
                        text = "Es solo para saber de qué tamaño queda cada trozo. No cambia " +
                            "el volumen ni las cantidades de las recetas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FormaDelCorte.entries.forEach { corte ->
                        FilterChip(
                            selected = estado.corteEfectivo == corte,
                            onClick = { acciones.elegirCorte(corte) },
                            label = { Text(nombreDelCorte(corte)) }
                        )
                    }
                }

                if (estado.pideMedidasDeCorte) {
                    Text(
                        // Dos textos porque son dos cosas distintas: en un triángulo sin esto
                        // la app no puede decir nada, y en un rectángulo es la manera de
                        // mandar sobre la suposición del lado más largo.
                        //
                        // **Empieza diciendo que se puede dejar vacío.** Sandy los llenó con
                        // las medidas del propio molde "prácticamente porque no entiendo qué va
                        // ahí": un campo opcional que no dice que es opcional se contesta igual,
                        // y acá contestarlo cambia el reparto.
                        text = if (estado.laFormaYaDaLosLados) {
                            "Opcional. Déjalos vacíos y la app corta el lado más largo. " +
                                "Son los dos lados de este mismo molde, escritos en el orden " +
                                "en que los cortas: primero el que se parte."
                        } else {
                            "De qué tamaño es la parte que se corta, si la sabes. Primero el " +
                                "lado que se parte."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CampoNumerico(
                        valor = estado.largoDeCorte,
                        alCambiar = acciones.cambiarLargoDeCorte,
                        etiqueta = "Lado que se parte (cm)",
                        error = estado.errorCorte,
                        accionDelTeclado = ImeAction.Next
                    )
                    CampoNumerico(
                        valor = estado.anchoDeCorte,
                        alCambiar = acciones.cambiarAnchoDeCorte,
                        etiqueta = "El otro lado (cm)",
                        accionDelTeclado = ImeAction.Done
                    )
                    // Qué hace lo que se acaba de escribir, en vivo. Es la mitad que faltaba:
                    // el texto de arriba dice qué escribir y este dice qué pasó al escribirlo.
                    estado.explicacionDelCorte?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    estado.avisoDelCorte?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Cómo quedarían los trozos, **mientras se escribe** (9.4.3). Lo pidió
                // Sandy: poder verlo "en el mismo acto que escribo, sin siquiera aceptarlo o
                // ponerlos en una receta". El número de trozos **no se guarda**: cuántos rinde
                // algo es de la receta y no del molde — el mismo molde da 6 porciones de torta
                // y 12 de brownie—, así que guardarlo acá sería un segundo lugar donde puede
                // quedar viejo. Es solo para mirar.
                if (estado.corteEfectivo != null && estado.corteEfectivo != FormaDelCorte.NO_SE_CORTA) {
                    HorizontalDivider()
                    CampoNumerico(
                        valor = estado.trozosDePrueba,
                        alCambiar = acciones.cambiarTrozosDeLaPrueba,
                        etiqueta = "¿En cuántos trozos?",
                        ayuda = "Solo para ver cómo quedaría. No se guarda con el molde."
                    )
                    estado.medidaDeLaPrueba?.let {
                        Text(
                            text = "Cada trozo: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    estado.repartosDeLaPrueba.forEach { opcion ->
                        Text(
                            // Todos los repartos y no solo el que la app elegiría: acá no se
                            // está decidiendo nada —el reparto se elige en la receta, que es
                            // donde viven los trozos—, se está mirando qué da este molde. Pero
                            // sí se marca cuál sale por defecto, que antes había que adivinar.
                            //
                            // **En palabras y no solo "1 × 5"**: en un molde de 26 × 20 partido
                            // en 5, la app ofrecía `1 × 5` y `5 × 1` — dos cortes distintos con
                            // rótulos que se leen igual dados vuelta. Lo reportó Sandy.
                            text = opcion.comoSeCorta + "  ·  " + opcion.medida +
                                if (opcion.elegido) "  (la que usa la app)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (opcion.elegido) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // La nota corta (9.6). Va **al final y siempre**, en cualquier forma: lo que se
                // anota acá no depende de la forma —"es redondo, 22 cm", "se desmolda del
                // revés"— y esconderla en algunas dejaría un campo que aparece y desaparece.
                OutlinedTextField(
                    value = estado.notas,
                    onValueChange = acciones.cambiarNotas,
                    label = { Text("Nota (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    supportingText = {
                        Text(
                            "Lo que quieras recordar de este molde: su diámetro si es exótico, " +
                                "por dónde se desmolda, un detalle de su forma."
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                estado.vistaPrevia?.let { (area, volumen) ->
                    // En vivo y no al guardar: es la única forma de darse cuenta ahí mismo
                    // de que se anotó un 3 donde iba un 30.
                    Text(
                        text = "Área ${formatearNumero(area)} cm² · " +
                            "Volumen ${formatearNumero(volumen)} cm³",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.guardar, enabled = estado.puedeGuardar) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

/**
 * La advertencia antes de borrar un molde (6.3).
 *
 * El tono es distinto al de un ingrediente a propósito: borrar un molde **no rompe** las
 * recetas que lo usaban — conservan sus medidas y solo pierden el vínculo. Igual se avisa,
 * porque desde ese momento dejan de recibir correcciones y descubrirlo meses después no
 * tendría explicación.
 */
@Composable
private fun ConfirmarBorradoMolde(
    estado: DialogoMolde.ConfirmarBorrado,
    acciones: AccionesMoldes
) {
    val afectadas = estado.recetasAfectadas

    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("¿Eliminar '${estado.molde.nombre}'?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                when {
                    afectadas == null -> Text("Revisando qué recetas lo usan…")
                    afectadas.isEmpty() -> Text("No lo usa ninguna receta.")
                    else -> {
                        Text(
                            "Estas recetas conservan sus medidas, pero dejan de " +
                                "actualizarse si más adelante corriges este molde:"
                        )
                        Column(
                            modifier = Modifier
                                .heightIn(max = Medidas.altoMaximoDeLista)
                                .verticalScroll(rememberScrollState())
                        ) {
                            afectadas.forEach { receta ->
                                Text(
                                    text = "• ${receta.titulo}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = acciones.confirmarBorrado,
                // Deshabilitado mientras la lista es null: eso es "todavía consultando", y
                // dejar borrar ahí sería borrar sin haber mostrado la advertencia completa.
                enabled = afectadas != null && !estado.borrando
            ) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

// --- Vistas previas ---

private fun moldeDeEjemplo(id: Long, nombre: String, dimensiones: DimensionesMolde) =
    Molde(id = id, nombre = nombre, dimensiones = dimensiones)

private fun estadoDeEjemplo() = EstadoMoldes(
    visibles = listOf(
        moldeDeEjemplo(
            1, "Redondo grande",
            DimensionesMolde(
                tipoForma = TipoFormaMolde.CIRCULO,
                diametroCm = 24.0,
                alturaMoldeCm = 7.0
            )
        ),
        moldeDeEjemplo(
            2, "Budinera",
            DimensionesMolde(
                tipoForma = TipoFormaMolde.RECTANGULO,
                largoCm = 30.0,
                anchoCm = 12.0,
                alturaMoldeCm = 8.0
            )
        ),
        moldeDeEjemplo(
            3, "Corazón",
            DimensionesMolde(
                tipoForma = TipoFormaMolde.EXOTICO,
                volumenExoticoCm3 = 1500.0,
                alturaMoldeCm = 6.0
            )
        )
    ),
    hayMoldes = true,
    cargando = false
)

@Preview(showBackground = true)
@Composable
private fun VistaPreviaMoldes() {
    ReposteriaTheme {
        ListaMoldes(estadoDeEjemplo(), DialogoMolde.Ninguno, AccionesMoldes())
    }
}

@Preview(showBackground = true)
@Composable
private fun VistaPreviaMoldesVacio() {
    ReposteriaTheme {
        ListaMoldes(
            EstadoMoldes(cargando = false),
            DialogoMolde.Ninguno,
            AccionesMoldes()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VistaPreviaFormularioMolde() {
    ReposteriaTheme {
        ListaMoldes(
            estadoDeEjemplo(),
            DialogoMolde.Formulario(
                nombre = "Redondo grande",
                forma = TipoFormaMolde.CIRCULO,
                medidas = mapOf(
                    CampoDeMolde.DIAMETRO to "24",
                    CampoDeMolde.ALTURA_MOLDE to "7"
                ),
                tocado = true
            ),
            AccionesMoldes()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VistaPreviaBorrarMolde() {
    ReposteriaTheme {
        ListaMoldes(
            estadoDeEjemplo(),
            DialogoMolde.ConfirmarBorrado(
                molde = estadoDeEjemplo().visibles.first(),
                recetasAfectadas = emptyList()
            ),
            AccionesMoldes()
        )
    }
}
