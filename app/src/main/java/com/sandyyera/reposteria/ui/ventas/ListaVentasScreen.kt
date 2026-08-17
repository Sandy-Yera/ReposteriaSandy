package com.sandyyera.reposteria.ui.ventas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.logica.calendario.DatosDelDia
import com.sandyyera.reposteria.logica.formato.AVISO_MONTOS_REDONDEADOS
import com.sandyyera.reposteria.logica.formato.formatearMonto
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.ventas.CifrasDelDia
import com.sandyyera.reposteria.ui.componentes.BarraBusqueda
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.componentes.CuadroDeDialogo
import com.sandyyera.reposteria.ui.componentes.MensajeCentrado
import com.sandyyera.reposteria.ui.theme.Medidas

/** Todo lo que se puede pedir desde la sección de ventas. */
data class AccionesVentas(
    val abrirDia: (Long) -> Unit = {},
    val cerrarDia: () -> Unit = {},
    val abrirRegistrar: () -> Unit = {},
    val moverFecha: (Long) -> Unit = {},
    val volverAHoy: () -> Unit = {},
    val cambiarNotas: (String) -> Unit = {},
    val abrirElegirReceta: () -> Unit = {},
    val buscarReceta: (String) -> Unit = {},
    val elegirReceta: (DatosCalculoReceta) -> Unit = {},
    val volverAlRegistro: () -> Unit = {},
    val quitarLinea: (Int) -> Unit = {},
    val cambiarUnidades: (Int, String) -> Unit = { _, _ -> },
    val cambiarPrecio: (Int, String) -> Unit = { _, _ -> },
    val usarElPrecioEstimado: (Int) -> Unit = {},
    val guardarVenta: () -> Unit = {},
    val pedirDescuento: (VentaConSusLineas) -> Unit = {},
    val confirmarDescuento: () -> Unit = {},
    val pedirBorrado: (VentaConSusLineas) -> Unit = {},
    val confirmarBorrado: () -> Unit = {},
    val cerrarDialogo: () -> Unit = {},
    val abrirMenu: () -> Unit = {}
)

/** La sección de ventas conectada a su ViewModel. */
@Composable
fun ListaVentasScreen(
    modelo: VentasViewModel,
    alAbrirMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val delDia by modelo.delDia.collectAsStateWithLifecycle()
    val dialogo by modelo.dialogo.collectAsStateWithLifecycle()
    val aviso by modelo.aviso.collectAsStateWithLifecycle()

    val acciones = remember(modelo, alAbrirMenu) {
        AccionesVentas(
            abrirDia = modelo::abrirDia,
            cerrarDia = modelo::cerrarDia,
            abrirRegistrar = modelo::abrirRegistrar,
            moverFecha = modelo::moverFecha,
            volverAHoy = modelo::volverAHoy,
            cambiarNotas = modelo::cambiarNotas,
            abrirElegirReceta = modelo::abrirElegirReceta,
            buscarReceta = modelo::buscarReceta,
            elegirReceta = modelo::elegirReceta,
            volverAlRegistro = modelo::volverAlRegistro,
            quitarLinea = modelo::quitarLinea,
            cambiarUnidades = modelo::cambiarUnidades,
            cambiarPrecio = modelo::cambiarPrecio,
            usarElPrecioEstimado = modelo::usarElPrecioEstimado,
            guardarVenta = modelo::guardarVenta,
            pedirDescuento = modelo::pedirDescuento,
            confirmarDescuento = modelo::confirmarDescuento,
            pedirBorrado = modelo::pedirBorrado,
            confirmarBorrado = modelo::confirmarBorrado,
            cerrarDialogo = modelo::cerrarDialogo,
            abrirMenu = alAbrirMenu
        )
    }

    ListaVentas(estado, delDia, dialogo, aviso, acciones, modelo::mensajeMostrado, modifier)
}

/**
 * El dibujo de la sección Ventas (sección 18).
 *
 * **Informe y detalle en la misma pantalla**, decidido por `abierto`, igual que Empleados: entrar a
 * un día no cambia de sección, cambia de qué se está mirando dentro de ella.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaVentas(
    estado: EstadoVentas,
    delDia: EstadoDelDia,
    dialogo: DialogoVentas,
    aviso: String?,
    acciones: AccionesVentas,
    avisoMostrado: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val diaAbierto = estado.diaAbierto
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    BackHandler(enabled = diaAbierto != null) { acciones.cerrarDia() }

    LaunchedEffect(aviso) {
        val texto = aviso ?: return@LaunchedEffect
        try {
            anfitrionDeMensajes.showSnackbar(texto)
        } finally {
            // En `finally` como en el resto de las secciones: `showSnackbar` espera a que el aviso
            // se cierre solo, y cambiando de sección antes la corrutina se cancela y el mensaje
            // quedaría pendiente, reapareciendo al volver.
            avisoMostrado()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Ventas") },
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
                onClick = acciones.abrirRegistrar,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medidas.objetivoTactil)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(if (diaAbierto == null) "  Anotar una venta" else "  Anotar en este día")
            }

            if (diaAbierto == null) {
                when {
                    estado.listaVacia -> MensajeCentrado(
                        titulo = "Todavía no hay ventas anotadas",
                        detalle = "Anota lo que vendiste un día y la app compara lo que " +
                            "estimaba con lo que pasó de verdad."
                    )

                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(Medidas.chico)
                    ) {
                        items(estado.dias, key = { it.fecha }) { fila ->
                            FilaDelDia(fila, acciones)
                        }
                    }
                }
            } else {
                DetalleDelDia(diaAbierto, estado.elAbierto, delDia, acciones)
            }
        }
    }

    when (dialogo) {
        is DialogoVentas.Ninguno -> Unit
        is DialogoVentas.Registrar -> DialogoRegistrarVenta(dialogo, acciones)
        is DialogoVentas.ElegirReceta -> DialogoElegirRecetaParaVender(dialogo, acciones)
        is DialogoVentas.Descontar -> DialogoDescontarVenta(dialogo, acciones)
        is DialogoVentas.ConfirmarBorrado -> DialogoBorrarVenta(dialogo, acciones)
    }
}

/** Un día del informe. Tocarlo lo abre, que es lo que uno viene a hacer con un día. */
@Composable
private fun FilaDelDia(fila: FilaDeUnDia, acciones: AccionesVentas) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { acciones.abrirDia(fila.fecha) }
    ) {
        Column(
            modifier = Modifier.padding(Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
        ) {
            EncabezadoDelDia(fila.dia)
            CifrasDelDiaEnTexto(fila.cifras)
        }
    }
}

/**
 * La fecha con sus etiquetas: fin de semana, feriado, fecha comercial (18.3).
 *
 * Las etiquetas salen de `logica/calendario` y **no se guardan** con la venta: con la fecha se
 * calculan todas, y anotarlas serían seis columnas diciendo lo mismo que una — que además quedan
 * mal si alguien corrige la fecha después.
 */
@Composable
private fun EncabezadoDelDia(dia: DatosDelDia) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = dia.comoTexto, style = MaterialTheme.typography.titleMedium)
        Text(
            text = dia.comoNumero,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Medidas.minimo)) {
        // El feriado le gana a la fecha comercial cuando caen juntos, y eso ya lo resolvió
        // `queDiaEs`: acá solo se dibuja lo que quedó.
        dia.comoSeLlamaElDia?.let {
            Etiqueta(texto = it, esFuerte = dia.esFeriado)
        }
        // El fin de semana va **aparte del feriado**: un sábado no es un festivo, y mezclarlos
        // perdería justo la diferencia que sirve para comparar.
        if (dia.esFinDeSemana) Etiqueta(texto = "Fin de semana", esFuerte = false)
    }
}

@Composable
private fun Etiqueta(texto: String, esFuerte: Boolean) {
    Surface(
        color = if (esFuerte) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelSmall,
            color = if (esFuerte) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Medidas.chico, vertical = Medidas.minimo)
        )
    }
}

/**
 * Las tres cifras del día, cada una con lo que la app había estimado al lado (18.2).
 *
 * **El costo dice de dónde salió.** Cuando ninguna venta del día descontó del almacén, lo que se
 * muestra es el estimado, y llamarlo "real" sería el mismo número con otro nombre — que es
 * exactamente lo que este módulo existe para no hacer (18.5).
 */
@Composable
private fun CifrasDelDiaEnTexto(cifras: CifrasDelDia) {
    Column(verticalArrangement = Arrangement.spacedBy(Medidas.minimo)) {
        LineaDeCifra(
            etiqueta = "Cobrado",
            valor = cifras.ingresoReal,
            estimado = cifras.ingresoEstimado
        )
        LineaDeCifra(
            etiqueta = if (cifras.costoEsDeVerdad) "Costó" else "Costó (estimado)",
            valor = cifras.costoQueVale,
            estimado = if (cifras.costoEsDeVerdad) cifras.costoEstimado else null
        )
        LineaDeCifra(
            etiqueta = "Quedó",
            valor = cifras.gananciaQueVale,
            estimado = cifras.gananciaEstimada,
            destacada = true
        )
    }
}

@Composable
private fun LineaDeCifra(
    etiqueta: String,
    valor: Double,
    estimado: Double?,
    destacada: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
            estimado?.let {
                Text(
                    text = "estimaba $${formatearMonto(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$${formatearMonto(valor)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (destacada) FontWeight.Bold else FontWeight.Normal,
                // Solo la ganancia se pinta de rojo al ser negativa: un costo alto no es un error
                // y un ingreso no puede ser negativo, así que colorearlos sería ruido.
                color = if (destacada && valor < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Lo que se ve al abrir un día: sus cifras, lo que hay que leer de ellas, y sus ventas. */
@Composable
private fun DetalleDelDia(
    dia: DatosDelDia,
    enElInforme: FilaDeUnDia?,
    delDia: EstadoDelDia,
    acciones: AccionesVentas
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Medidas.medio)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Medidas.medio),
                verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
            ) {
                EncabezadoDelDia(dia)
                // `enElInforme` es null cuando se borró la última venta del día: el día sigue
                // abierto —es un lugar donde anotar— pero ya no tiene cifras que mostrar.
                enElInforme?.let {
                    HorizontalDivider(modifier = Modifier.padding(vertical = Medidas.chico))
                    CifrasDelDiaEnTexto(it.cifras)
                    it.lecturas.forEach { frase ->
                        Text(
                            text = frase,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Medidas.chico)
                        )
                    }
                }
            }
        }

        when {
            delDia.cargando -> CircularProgressIndicator()

            delDia.sinVentas -> MensajeCentrado(
                titulo = "Ese día no tiene ventas anotadas",
                detalle = "Toca «Anotar en este día» para agregar la primera."
            )

            else -> delDia.ventas.forEach { fila -> TarjetaDeVenta(fila, acciones) }
        }

        Text(
            text = AVISO_MONTOS_REDONDEADOS,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Una venta del día, con sus líneas y los dos botones que se le pueden pedir. */
@Composable
private fun TarjetaDeVenta(fila: VentaConSusLineas, acciones: AccionesVentas) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
        ) {
            fila.lineas.forEach { linea ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${linea.unidades} × ${linea.tituloReceta}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$${formatearMonto(linea.unidades * linea.precioUnitario)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            fila.venta.notas?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Medidas.minimo))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total $${fila.comoSeLeeElTotal}",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // La que ya descontó **no muestra un botón apagado** (12.1): un botón en gris
                    // se toca igual y da la impresión de que algo se rompió. Muestra el hecho.
                    if (fila.venta.descontoDelAlmacen) {
                        Text(
                            text = "Descontado del almacén",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        TextButton(onClick = { acciones.pedirDescuento(fila) }) {
                            Text("Descontar")
                        }
                    }
                    IconButton(onClick = { acciones.pedirBorrado(fila) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Borrar esta venta")
                    }
                }
            }
        }
    }
}

/** El cuadro de anotar una venta (18.1). */
@Composable
private fun DialogoRegistrarVenta(
    estado: DialogoVentas.Registrar,
    acciones: AccionesVentas
) {
    CuadroDeDialogo(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("¿Qué vendiste?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                SelectorDeFecha(estado, acciones)

                estado.lineas.forEach { linea -> LineaDeVentaEnEdicion(linea, acciones) }

                OutlinedButton(
                    onClick = acciones.abrirElegirReceta,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Agregar una receta")
                }

                OutlinedTextField(
                    value = estado.notas,
                    onValueChange = acciones.cambiarNotas,
                    label = { Text("Notas del día (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    )
                )

                if (estado.lineas.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Total del día: $${estado.comoSeLeeElTotal}",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                estado.rechazo?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.guardarVenta, enabled = estado.puedeGuardar) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

/**
 * Qué día se está anotando.
 *
 * Con flechas y no con un calendario: lo que se anota es lo de hoy o lo de ayer, y para eso un
 * calendario es más pasos, no menos. El día se dice **escrito** —"sábado 16 de agosto"— porque un
 * número suelto no permite darse cuenta de que uno se pasó de día.
 */
@Composable
private fun SelectorDeFecha(estado: DialogoVentas.Registrar, acciones: AccionesVentas) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { acciones.moverFecha(-1) }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "El día anterior")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (estado.esHoy) "Hoy" else estado.dia.comoTexto,
                style = MaterialTheme.typography.titleSmall
            )
            estado.dia.comoSeLlamaElDia?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Adelante no se puede pasar de hoy, y en vez de un botón apagado se ofrece volver: una
        // venta con fecha futura no es un dato que exista, y en el informe se ordenaría arriba de
        // todo, encima de lo que sí pasó.
        if (estado.esHoy) {
            // Un hueco del tamaño del botón y **no un botón apagado** (12.1): el apagado se toca
            // igual y da la impresión de que algo se rompió. El hueco además deja la fecha
            // centrada, así que no se mueve al pasar de ayer a hoy.
            Spacer(modifier = Modifier.size(Medidas.objetivoTactil))
        } else {
            IconButton(onClick = { acciones.moverFecha(1) }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "El día siguiente")
            }
        }
    }
    if (!estado.esHoy) {
        TextButton(onClick = acciones.volverAHoy) { Text("Volver a hoy") }
    }
}

/** Una línea que se está escribiendo: cuántas y a cuánto. */
@Composable
private fun LineaDeVentaEnEdicion(linea: LineaEnEdicion, acciones: AccionesVentas) {
    Column(verticalArrangement = Arrangement.spacedBy(Medidas.minimo)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = linea.titulo,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { acciones.quitarLinea(linea.numero) }) {
                Icon(Icons.Default.Delete, contentDescription = "Quitar '${linea.titulo}'")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Medidas.chico)) {
            CampoNumerico(
                valor = linea.unidades,
                alCambiar = { acciones.cambiarUnidades(linea.numero, it) },
                etiqueta = "Cuántas",
                modifier = Modifier.weight(1f),
                error = linea.errorDeUnidades,
                accionDelTeclado = ImeAction.Next
            )
            CampoNumerico(
                valor = linea.precio,
                alCambiar = { acciones.cambiarPrecio(linea.numero, it) },
                etiqueta = "A cuánto c/u",
                modifier = Modifier.weight(1f),
                error = linea.errorDePrecio
            )
        }
        // **El precio no viene relleno con el estimado, y esto es por qué se puede aceptar en un
        // toque igual.** Relleno, lo normal sería no tocarlo, y entonces lo real y lo estimado
        // serían el mismo número por defecto — o sea, nada que comparar (18.1).
        val estimado = linea.comoSeLeeLoEstimado
        if (estimado == null) {
            Text(
                text = "Esta receta no tiene precio puesto, así que no hay estimado que comparar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TextButton(onClick = { acciones.usarElPrecioEstimado(linea.numero) }) {
                Text("Cobré lo estimado: $$estimado")
            }
        }
        if (linea.sirve) {
            Text(
                text = "Son $${linea.comoSeLeeElTotal}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider()
    }
}

/** Elegir qué receta agregar a la venta que se está anotando. */
@Composable
private fun DialogoElegirRecetaParaVender(
    estado: DialogoVentas.ElegirReceta,
    acciones: AccionesVentas
) {
    CuadroDeDialogo(
        onDismissRequest = acciones.volverAlRegistro,
        title = { Text("¿Cuál vendiste?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                BarraBusqueda(
                    texto = estado.busqueda,
                    alCambiar = acciones.buscarReceta,
                    marcador = "Buscar receta"
                )
                if (estado.sinRecetas) {
                    Text("Todavía no hay recetas guardadas.")
                }
                estado.visibles.forEach { datos ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { acciones.elegirReceta(datos) }
                            .padding(vertical = Medidas.chico)
                    ) {
                        Text(text = datos.titulo, style = MaterialTheme.typography.bodyLarge)
                        // La que no tiene precio **se ofrece igual**, diciendo qué le falta: lo que
                        // falta es la estimación, no la venta, y negarse a anotarla perdería el
                        // dato real por no tener el estimado (18.1).
                        if (!datos.tienePrecio) {
                            Text(
                                text = "Sin precio puesto: entra sin estimado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.volverAlRegistro) { Text("Volver") }
        }
    )
}

/** La vista previa de lo que saldría del almacén por esta venta (18.4). */
@Composable
private fun DialogoDescontarVenta(
    estado: DialogoVentas.Descontar,
    acciones: AccionesVentas
) {
    val previa = estado.previa
    CuadroDeDialogo(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("Esto va a quedar") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                if (previa == null) {
                    Text("Calculando…")
                } else {
                    Text(
                        text = "Descontar es lo que hace que el costo del día sea el real y no " +
                            "el estimado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            TextButton(
                onClick = acciones.confirmarDescuento,
                enabled = previa?.hayAlgoQueDescontar == true && !estado.aplicando
            ) { Text("Descontar") }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

/** La advertencia antes de borrar una venta (6.3). */
@Composable
private fun DialogoBorrarVenta(
    estado: DialogoVentas.ConfirmarBorrado,
    acciones: AccionesVentas
) {
    CuadroDeDialogo(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("¿Borrar esta venta?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                Text(
                    if (estado.cuantasLineas == 1) {
                        "Se va con su línea, y el informe del día deja de contarla."
                    } else {
                        "Se van sus ${estado.cuantasLineas} líneas, y el informe del día deja " +
                            "de contarlas."
                    }
                )
                // **Lo que salió del frasco salió.** Devolverlo exigiría saber que nadie lo
                // corrigió a mano entremedio, y suponerlo dejaría un número inventado donde antes
                // había uno mirado. Callarlo dejaría creer que borrar deshace todo.
                if (estado.yaDesconto) {
                    Text(
                        text = "Ojo: lo que ya descontó del almacén no se devuelve. Si hace " +
                            "falta, corrígelo a mano en Almacén.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.confirmarBorrado, enabled = !estado.borrando) {
                Text("Borrar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}
