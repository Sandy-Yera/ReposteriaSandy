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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.data.repositorio.BloqueDelResumen
import com.sandyyera.reposteria.data.repositorio.LineaDelResumen
import com.sandyyera.reposteria.data.repositorio.PrecioDelResumen
import com.sandyyera.reposteria.data.repositorio.RendimientoDelResumen
import com.sandyyera.reposteria.data.repositorio.ResumenDeReceta
import com.sandyyera.reposteria.data.repositorio.SimulacionDelResumen
import com.sandyyera.reposteria.data.repositorio.SeccionDelResumen
import com.sandyyera.reposteria.logica.almacen.RecetaParaElAlmacen
import com.sandyyera.reposteria.logica.formato.AVISO_MONTOS_REDONDEADOS
import com.sandyyera.reposteria.logica.formato.formatearMonto
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el resumen. */
data class AccionesResumen(
    val alternar: (PasoDeReceta) -> Unit = {},
    val irAlPaso: (PasoDeReceta) -> Unit = {},
    val cerrarReceta: () -> Unit = {},
    /** Cierra la receta y abre el almacén con el cuadro de agregar ya lleno (14.13). */
    val guardarComoIngrediente: (RecetaParaElAlmacen) -> Unit = {}
)

/** El resumen conectado a su ViewModel. */
@Composable
fun PasoResumenScreen(
    tituloReceta: String,
    desplazamientoDePasos: ScrollState,
    modelo: ResumenViewModel,
    pasoActual: PasoDeReceta,
    alElegirPaso: (PasoDeReceta) -> Unit,
    alCerrarReceta: () -> Unit,
    alGuardarComoIngrediente: (RecetaParaElAlmacen) -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    PasoResumen(
        tituloReceta = tituloReceta,
        estado = estado,
        acciones = AccionesResumen(
            alternar = modelo::alternar,
            irAlPaso = alElegirPaso,
            cerrarReceta = alCerrarReceta,
            guardarComoIngrediente = alGuardarComoIngrediente
        ),
        pasoActual = pasoActual,
        modifier = modifier,
        desplazamientoDePasos = desplazamientoDePasos
    )
}

/**
 * La receta entera, para leerla y llegar a cualquier parte (8.12).
 *
 * **Es lo primero que se ve al abrir una receta**, y ese es el cambio que trae: hasta acá una
 * receta solo se recorría paso a paso, que sirve para armarla y no para leerla. Para ver qué
 * lleva, cuánto rinde y qué cuesta había que ir tocando pestaña por pestaña y acordarse de la
 * anterior.
 *
 * **Un acordeón y no todo desplegado.** Con las siete partes abiertas esto sería la receta entera
 * en una tira larguísima, que es justo lo que ya se puede ver recorriendo los pasos. Cerrado, lo
 * que se ve es el índice: de qué está hecha la receta y sus cifras principales.
 *
 * **No edita: lleva a editar.** Cada parte tiene su botón que abre el paso correspondiente.
 * Copiar los formularios acá dejaría dos lugares donde arreglar cada error, y los pasos ya tienen
 * resueltos sus casos raros — el peso sin revisar, el bautizo de la primera sección, la promoción
 * que no cabe en los trozos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasoResumen(
    tituloReceta: String,
    estado: EstadoResumen,
    acciones: AccionesResumen,
    pasoActual: PasoDeReceta = PasoDeReceta.RESUMEN,
    modifier: Modifier = Modifier,
    desplazamientoDePasos: ScrollState = rememberScrollState()
) {
    BackHandler(enabled = true) { acciones.cerrarReceta() }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(tituloReceta) },
                    actions = {
                        IconButton(onClick = acciones.cerrarReceta) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar la receta")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilaDePasos(
                    pasoActual = pasoActual,
                    alElegirPaso = acciones.irAlPaso,
                    desplazamiento = desplazamientoDePasos
                )
            }
        }
    ) { relleno ->
        val resumen = estado.resumen
        if (resumen == null) {
            // Cargando o recién borrada. En los dos casos lo honesto es no dibujar cifras:
            // media receta a medio llegar se lee como una receta incompleta.
            Column(modifier = Modifier.padding(relleno).padding(Medidas.grande)) {
                if (estado.desaparecio) Text("Esta receta ya no existe.")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
            contentPadding = PaddingValues(Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.chico)
        ) {
            item { EncabezadoDelResumen(resumen, acciones) }

            item {
                ParteDelResumen(
                    parte = PasoDeReceta.CANTIDADES,
                    resumida = if (resumen.sinIngredientes) "Todavía sin ingredientes"
                    else "$${formatearMonto(resumen.costoTotal)} en ingredientes",
                    estado = estado,
                    acciones = acciones
                ) { Ingredientes(resumen) }
            }

            item {
                ParteDelResumen(
                    parte = PasoDeReceta.DURACION,
                    resumida = if (resumen.duraciones.isEmpty()) "Sin anotar"
                    else "${resumen.duraciones.size} anotadas",
                    estado = estado,
                    acciones = acciones
                ) {
                    if (resumen.duraciones.isEmpty()) {
                        Vacio("Es opcional: hay recetas que no la necesitan.")
                    } else {
                        resumen.duraciones.forEach { Renglon(it) }
                    }
                }
            }

            item {
                ParteDelResumen(
                    parte = PasoDeReceta.MOLDE,
                    resumida = resumen.molde ?: "Sin molde",
                    estado = estado,
                    acciones = acciones
                ) {
                    if (resumen.molde == null) {
                        Vacio("Sin molde, el peso final del rendimiento es obligatorio.")
                    } else {
                        Renglon(resumen.molde)
                    }
                }
            }

            item {
                ParteDelResumen(
                    parte = PasoDeReceta.RENDIMIENTO,
                    resumida = trozosEnTexto(resumen.rendimiento.trozos),
                    estado = estado,
                    acciones = acciones
                ) { Rendimiento(resumen.rendimiento) }
            }

            item {
                ParteDelResumen(
                    parte = PasoDeReceta.GASTOS,
                    resumida = if (resumen.sinPrecios) "Sin precio"
                    else "${resumen.precios.size} precios",
                    estado = estado,
                    acciones = acciones
                ) { Precios(resumen) }
            }

            item {
                ParteDelResumen(
                    parte = PasoDeReceta.SIMULACION,
                    resumida = resumen.simulacion
                        ?.let { "$${formatearMonto(it.gananciaMensual)} al mes" }
                        ?: "Sin simular",
                    estado = estado,
                    acciones = acciones
                ) {
                    // Sin precio no hay nada que proyectar, y se dice cuál es el paso que falta
                    // en vez de dejar la parte vacía sin explicación (8.7).
                    if (resumen.simulacion == null) {
                        Vacio(
                            if (resumen.sinPrecios) {
                                "Primero hace falta un precio, en Gastos y ganancias."
                            } else {
                                "Todavía no se anotó cuánto se vende."
                            }
                        )
                    } else {
                        // Lo configurado **y lo que deja**, que es lo que uno viene a mirar:
                        // "2 por día, 4 días" dice lo que se anotó, no lo que se gana.
                        Renglon(resumen.simulacion.cuanto)
                        Renglon(
                            "Ganancia semanal: " +
                                "$${formatearMonto(resumen.simulacion.gananciaSemanal)}"
                        )
                        Renglon(
                            "Ganancia mensual: " +
                                "$${formatearMonto(resumen.simulacion.gananciaMensual)}"
                        )
                    }
                }
            }

            item {
                ParteDelResumen(
                    parte = PasoDeReceta.PASOS,
                    resumida = if (resumen.sinPasos) "Sin escribir"
                    else pasosEnTexto(resumen.bloquesDePasos.sumOf { it.pasos.size }),
                    estado = estado,
                    acciones = acciones
                ) { Pasos(resumen) }
            }
        }
    }
}

/** El título, lo que hay que tener hecho antes, y el aviso de las partes traídas. */
@Composable
private fun EncabezadoDelResumen(resumen: ResumenDeReceta, acciones: AccionesResumen) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
        ) {
            Text("Costo de los ingredientes", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "$${formatearMonto(resumen.costoTotal)}",
                style = MaterialTheme.typography.headlineMedium
            )
            // "Paso previo" es lo que hay que tener hecho **antes** de empezar, así que va arriba
            // de todo y no entre los pasos (8.8).
            Text(
                text = "Antes de empezar: ${resumen.pasoPrevio}",
                style = MaterialTheme.typography.bodySmall
            )
            // El aviso del redondeo va en el encabezado y no repetido en cada panel: el resumen
            // muestra montos en casi todos, y decirlo cinco veces es ruido (15.2).
            Text(
                text = AVISO_MONTOS_REDONDEADOS,
                style = MaterialTheme.typography.bodySmall
            )

            // Convertirla en ingrediente (14.13): los siropes, almíbares y azúcares invertidos
            // no se venden, se usan dentro de otras recetas. Va en el encabezado y no en un paso
            // propio porque **no es un paso de armar la receta** sino algo que se hace con ella
            // ya terminada, y acá está justo debajo del costo del que sale su precio.
            val paraElAlmacen = resumen.paraElAlmacen
            if (paraElAlmacen != null) {
                // **Sin `contentPadding` en cero.** Lo tenía para alinear el texto con lo de
                // arriba, y el borde redondeado del botón se comía las primeras y últimas
                // letras: un botón ancho con texto pegado al canto no tiene dónde curvarse.
                // Se recupera la alineación con un relleno propio, que no toca el canto.
                TextButton(
                    onClick = { acciones.guardarComoIngrediente(paraElAlmacen) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = Medidas.medio,
                        vertical = Medidas.chico
                    )
                ) {
                    Text(
                        text = "Guardarla como ingrediente " +
                            "($${formatearNumero(resumen.valorPorGramoComoIngrediente ?: 0.0)} " +
                            "por gramo)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // **Se dice por qué no se puede**, en vez de esconder el botón: un botón que
                // aparece y desaparece sin explicación se lee como que la app se rompió.
                resumen.porQueNoSeGuardaComoIngrediente?.let {
                    Text(
                        text = "Para guardarla como ingrediente: $it",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            // **Se listan los cambios y no solo se avisa que los hay.** Decir "revísalo en
            // Cantidades" mandaba a buscar un cambio de ingredientes que podía no existir: lo
            // que se movió pudo ser un paso. Con la frase exacta se entiende sin salir de acá,
            // y lo que queda allá es solo decidir — por eso también se dice cómo se apaga.
            resumen.avisosDePartes.forEach { aviso ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = if (aviso.laOriginalSeBorro) {
                                "  '${aviso.deDonde}' ya no existe"
                            } else {
                                "  '${aviso.deDonde}' cambió"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    aviso.cambios.forEach {
                        Text(
                            text = "• $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = "El aviso se apaga en Cantidades, tocando \"Cambió\" sobre esa " +
                            "parte y eligiendo Mantener o Actualizar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Una parte del acordeón: su nombre, una línea de resumen, y el contenido cuando está abierta.
 *
 * **La línea de resumen se ve siempre, abierta o cerrada**, y es la mitad del valor de esta
 * pantalla: cerrado el acordeón entero, esas siete líneas ya dicen en qué estado está la receta —
 * qué falta, qué cuesta, cuánto rinde— sin abrir nada.
 *
 * El botón de editar lleva al paso que corresponde. Es un botón de verdad y no la fila entera
 * porque tocar la fila ya significa abrir o cerrar, y un mismo gesto no puede hacer las dos cosas.
 */
@Composable
private fun ParteDelResumen(
    parte: PasoDeReceta,
    resumida: String,
    estado: EstadoResumen,
    acciones: AccionesResumen,
    contenido: @Composable () -> Unit
) {
    val abierta = estado.estaAbierta(parte)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(bottom = if (abierta) Medidas.chico else 0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medidas.objetivoTactil)
                    .clickable { acciones.alternar(parte) }
                    .padding(start = Medidas.medio, end = Medidas.chico),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = Medidas.chico)) {
                    Text(
                        text = parte.titulo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = resumida,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (abierta) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (abierta) "Cerrar ${parte.titulo}"
                    else "Abrir ${parte.titulo}"
                )
            }
            if (abierta) {
                HorizontalDivider()
                Column(
                    modifier = Modifier.padding(
                        start = Medidas.medio,
                        end = Medidas.medio,
                        top = Medidas.chico
                    ),
                    verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
                ) {
                    contenido()
                }
                TextButton(
                    onClick = { acciones.irAlPaso(parte) },
                    modifier = Modifier.padding(start = Medidas.chico)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text("  Editar ${parte.titulo.lowercase()}")
                }
            }
        }
    }
}

@Composable
private fun Ingredientes(resumen: ResumenDeReceta) {
    if (resumen.sinIngredientes) {
        Vacio("Sin ingredientes no hay costo que calcular.")
        return
    }
    resumen.secciones.forEach { seccion ->
        NombreDeParte(seccion, resumen.deDondeViene(seccion.id), resumen.secciones.size > 1)
        seccion.lineas.forEach { linea ->
            Renglon("${linea.cuanto} de ${linea.nombre} · $${formatearMonto(linea.subtotal)}")
        }
        if (seccion.lineas.isEmpty()) Renglon("Todavía sin ingredientes")
    }
}

/** El nombre de una sección, con su marca de origen si vino de otra receta (8.11.2). */
@Composable
private fun NombreDeParte(
    seccion: SeccionDelResumen,
    vieneDe: String?,
    mostrarCosto: Boolean
) {
    val nombre = seccion.nombre ?: return
    Text(
        text = if (mostrarCosto) "$nombre · $${formatearMonto(seccion.costo)}" else nombre,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Medidas.chico)
    )
    vieneDe?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Rendimiento(rendimiento: RendimientoDelResumen) {
    Renglon(trozosEnTexto(rendimiento.trozos))
    rendimiento.medidaDelTrozo?.let { Renglon("Cada trozo: $it") }
    rendimiento.pesoFinalG?.let { Renglon("Peso final: ${formatearNumero(it)} g") }
    if (rendimiento.pesoSinRevisar) {
        // Se dice acá y no solo en su paso: el peso viene de un reescalado y nadie lo pesó
        // todavía, así que todo lo que cuelga de él está en duda (9.3).
        Text(
            text = "El peso viene de un reescalado y no se ha revisado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun Precios(resumen: ResumenDeReceta) {
    if (resumen.sinPrecios) {
        Vacio("Sin precio no hay ganancia que calcular.")
        return
    }
    resumen.precios.forEach { precio ->
        Renglon(
            texto = precio.comoSeLee + " · gana $${formatearMonto(precio.gananciaPorTrozo)}" +
                " por trozo" + if (precio.esReferencia) " (referencia)" else "",
            // La ganancia negativa se pinta: vender bajo el costo es lo que esta pantalla
            // tiene que dejar ver de un vistazo (8.5).
            enRojo = precio.gananciaPorTrozo < 0
        )
    }
}

@Composable
private fun Pasos(resumen: ResumenDeReceta) {
    if (resumen.sinPasos) {
        Vacio("Es opcional: hay recetas que se saben de memoria.")
        return
    }
    resumen.bloquesDePasos.forEach { bloque -> BloqueDePasosDelResumen(bloque) }
}

@Composable
private fun BloqueDePasosDelResumen(bloque: BloqueDelResumen) {
    bloque.encabezado?.let { texto ->
        Text(
            text = texto,
            style = if (bloque.esGeneralAnidado) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.titleSmall,
            color = if (bloque.esGeneralAnidado) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                top = Medidas.chico,
                start = if (bloque.esGeneralAnidado) Medidas.medio else 0.dp
            )
        )
        bloque.vieneDe?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = if (bloque.esGeneralAnidado) Medidas.medio else 0.dp
                )
            )
        }
    }
    bloque.pasos.forEach { Renglon(it) }
}

/** Un renglón cualquiera del contenido de una parte. */
@Composable
private fun Renglon(texto: String, enRojo: Boolean = false) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = if (enRojo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    )
}

/**
 * Lo que ocupa el lugar del contenido cuando una parte está vacía.
 *
 * Dice **por qué está bien o qué falta**, no solo que está vacía: "sin anotar" a secas deja
 * dudando entre "no lo llené" y "no hace falta", que en esta app son dos cosas distintas — la
 * duración es opcional y el precio no.
 */
@Composable
private fun Vacio(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** "1 trozo" / "6 trozos". Concuerda, que es lo que hace que el número se lea sin dudar. */
private fun trozosEnTexto(trozos: Int): String =
    if (trozos == 1) "1 trozo" else "$trozos trozos"

/** "1 paso" / "12 pasos", por lo mismo. */
private fun pasosEnTexto(cuantos: Int): String =
    if (cuantos == 1) "1 paso" else "$cuantos pasos"

// --- Vistas previas ---

private val recetaDeEjemplo = ResumenDeReceta(
    titulo = "Torta de manjar",
    pasoPrevio = "No necesita",
    secciones = listOf(
        SeccionDelResumen(
            id = 1,
            nombre = "Bizcocho",
            lineas = listOf(
                LineaDelResumen("500 g", "Harina", 600.0),
                LineaDelResumen("200 g", "Azúcar", 310.0)
            ),
            costo = 910.0
        ),
        SeccionDelResumen(
            id = 2,
            nombre = "Envoltorio",
            lineas = listOf(LineaDelResumen("6 unidades", "Bolsas", 240.0)),
            costo = 240.0
        )
    ),
    costoTotal = 1150.0,
    duraciones = listOf("Al ambiente: 3 días", "Refrigerada: 7 días"),
    molde = "26 × 25 × 10 cm",
    rendimiento = RendimientoDelResumen(
        trozos = 6,
        pesoFinalG = 1200.0,
        medidaDelTrozo = "8,67 × 12,5 × 10 cm",
        pesoSinRevisar = false
    ),
    precios = listOf(
        PrecioDelResumen("Trozo · $500", 308.33, esReferencia = true),
        PrecioDelResumen("Producto entero · $2.500", 225.0, esReferencia = false)
    ),
    simulacion = SimulacionDelResumen(
        cuanto = "2 por día, 4 días a la semana",
        gananciaSemanal = 10800.0,
        gananciaMensual = 46764.0
    ),
    bloquesDePasos = listOf(
        BloqueDelResumen("Bizcocho", "de 'Bizcocho básico'", false, listOf("1. Batir las claras.")),
        BloqueDelResumen("General", null, false, listOf("2. Armar y refrigerar."))
    ),
    partes = emptyList()
)

@Preview(showBackground = true, name = "Resumen - cerrado")
@Composable
private fun ResumenCerrado() {
    ReposteriaTheme {
        PasoResumen(
            tituloReceta = "Torta de manjar",
            estado = EstadoResumen(resumen = recetaDeEjemplo, cargando = false),
            acciones = AccionesResumen()
        )
    }
}

@Preview(showBackground = true, name = "Resumen - cantidades abierto")
@Composable
private fun ResumenAbierto() {
    ReposteriaTheme {
        PasoResumen(
            tituloReceta = "Torta de manjar",
            estado = EstadoResumen(
                resumen = recetaDeEjemplo,
                abierta = PasoDeReceta.CANTIDADES,
                cargando = false
            ),
            acciones = AccionesResumen()
        )
    }
}

@Preview(showBackground = true, name = "Resumen - oscuro")
@Composable
private fun ResumenOscuro() {
    ReposteriaTheme(oscuro = true) {
        PasoResumen(
            tituloReceta = "Torta de manjar",
            estado = EstadoResumen(
                resumen = recetaDeEjemplo,
                abierta = PasoDeReceta.PASOS,
                cargando = false
            ),
            acciones = AccionesResumen()
        )
    }
}
