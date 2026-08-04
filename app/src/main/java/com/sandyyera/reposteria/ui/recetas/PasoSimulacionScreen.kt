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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.PrecioVigente
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/** Todo lo que se puede pedir desde el paso de ganancias simuladas. */
data class AccionesSimulacion(
    val cambiarDias: (String) -> Unit = {},
    val cambiarUnidades: (String) -> Unit = {},
    val mensajeMostrado: () -> Unit = {},
    val irAlPaso: (PasoDeReceta) -> Unit = {},
    val cerrarReceta: () -> Unit = {}
)

/** El paso de ganancias simuladas conectado a su ViewModel. */
@Composable
fun PasoSimulacionScreen(
    tituloReceta: String,
    desplazamientoDePasos: ScrollState,
    modelo: SimulacionViewModel,
    pasoActual: PasoDeReceta,
    alElegirPaso: (PasoDeReceta) -> Unit,
    alCerrarReceta: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    val acciones = remember(modelo, alElegirPaso, alCerrarReceta) {
        AccionesSimulacion(
            cambiarDias = modelo::cambiarDias,
            cambiarUnidades = modelo::cambiarUnidades,
            mensajeMostrado = modelo::mensajeMostrado,
            irAlPaso = alElegirPaso,
            cerrarReceta = alCerrarReceta
        )
    }

    PasoSimulacion(
        tituloReceta = tituloReceta,
        estado = estado,
        acciones = acciones,
        pasoActual = pasoActual,
        modifier = modifier,
        desplazamientoDePasos = desplazamientoDePasos
    )
}

/**
 * El paso 6: cuánto dejaría vendiendo tanto por semana (8.7).
 *
 * **Los dos campos van arriba y las cifras debajo.** Al revés que en gastos, acá lo que se
 * viene a hacer es *mover* los números y mirar qué pasa; tenerlos a mano importa más que ver
 * el resultado primero, porque el resultado cambia con cada tecla.
 *
 * **Es el único paso donde un número absurdo no rompe nada**, y por eso hay que cuidarlo: los
 * dos campos se multiplican contra el ingreso, así que un 200 escrito en vez de un 20 sale
 * como una proyección mensual perfectamente creíble y diez veces falsa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasoSimulacion(
    tituloReceta: String,
    estado: EstadoSimulacion,
    acciones: AccionesSimulacion,
    pasoActual: PasoDeReceta = PasoDeReceta.SIMULACION,
    modifier: Modifier = Modifier,
    desplazamientoDePasos: ScrollState = rememberScrollState()
) {
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    BackHandler(onBack = acciones.cerrarReceta)

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
            item {
                Text(
                    text = "Es una proyección, no una promesa: sale de multiplicar el precio " +
                        "que elegiste por lo que creas que vas a vender.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                CampoNumerico(
                    valor = estado.diasPorSemana,
                    alCambiar = acciones.cambiarDias,
                    etiqueta = "Días a la semana que la vendes",
                    error = estado.errorDias,
                    accionDelTeclado = ImeAction.Next
                )
            }

            item {
                CampoNumerico(
                    valor = estado.unidadesPorDia,
                    alCambiar = acciones.cambiarUnidades,
                    etiqueta = "Cuántas vendes por día",
                    error = estado.errorUnidades,
                    // Se dice acá porque es el camino correcto para "todavía no la vendo", y
                    // sin decirlo la gente pone 0 días, que deja todo en cero pareciendo un
                    // error de la app.
                    ayuda = "Si todavía no la vendes, deja 0",
                    accionDelTeclado = ImeAction.Done
                )
            }

            if (!estado.tienePrecio) {
                item { SinPrecioNoHayQueProyectar(acciones.irAlPaso) }
                return@LazyColumn
            }

            estado.avisoDelResto?.let { aviso ->
                item { AvisoDelRestoSemanal(aviso) }
            }

            item { TarjetaDeLaProyeccion(estado) }
        }
    }
}

/** Sin precio guardado no hay nada que multiplicar, y conviene decir dónde se pone. */
@Composable
private fun SinPrecioNoHayQueProyectar(irAlPaso: (PasoDeReceta) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { irAlPaso(PasoDeReceta.GASTOS) }
    ) {
        Column(Modifier.padding(Medidas.medio)) {
            Text(
                text = "Todavía no le pusiste precio",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Sin precio no hay nada que proyectar. Toca acá para ir a Gastos y " +
                    "ganancias.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * El aviso de cómo se reparte la venta de la semana (8.6.1).
 *
 * Es lo que hace honesto a este paso: **el resto se junta**. Una receta de 3 trozos con una
 * promo de 2 deja siempre un suelto mirando producto por producto, pero en la semana los
 * restos se suman y la promo entra más veces. Sin este aviso, el número de abajo sería
 * correcto y aun así incomprensible.
 */
@Composable
private fun AvisoDelRestoSemanal(aviso: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = aviso,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Medidas.medio)
        )
    }
}

/** Las seis cifras: ingreso, costo y ganancia, por semana y por mes. */
@Composable
private fun TarjetaDeLaProyeccion(estado: EstadoSimulacion) {
    val r = estado.resultado
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
        ) {
            if (r == null) {
                Text(
                    // Con los campos a medio escribir no se muestra una cifra a partir de un
                    // número inválido: parecería un resultado.
                    text = "Completa los dos campos para ver la proyección.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Text("En una semana", style = MaterialTheme.typography.titleMedium)
            Cifra("Entra", r.ingresoSemanal)
            Cifra("Cuesta hacerlo", r.costoSemanal)
            Cifra("Te queda", r.gananciaSemanal, destacada = true)

            HorizontalDivider(Modifier.padding(vertical = Medidas.chico))

            Text("En un mes", style = MaterialTheme.typography.titleMedium)
            Cifra("Entra", r.ingresoMensual)
            Cifra("Cuesta hacerlo", r.costoMensual)
            Cifra("Te queda", r.gananciaMensual, destacada = true)

            Text(
                // Se dice de dónde sale el mes, porque 4,33 no es un número obvio y sin
                // explicarlo parece que la app se lo inventó.
                text = "El mes son 4,33 semanas: 52 semanas repartidas en 12 meses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Medidas.chico)
            )
        }
    }
}

@Composable
private fun Cifra(nombre: String, valor: Double, destacada: Boolean = false) {
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
            text = "$${formatearNumero(valor)}",
            style = if (destacada) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyMedium,
            fontWeight = if (destacada) FontWeight.Bold else null,
            // Una ganancia negativa se pinta en el color de error: vender más de algo que
            // pierde plata pierde más, y eso hay que poder verlo de un vistazo.
            color = if (valor < 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

// --- Vistas previas ---

private fun estadoDeEjemplo(
    dias: String = "4",
    unidades: String = "2",
    conPrecio: Boolean = true
) = EstadoSimulacion(
    datos = DatosCalculoReceta(
        recetaId = 1,
        titulo = "Torta de manjar",
        costoTotal = 1400.0,
        trozos = 6,
        precios = if (!conPrecio) emptyList() else listOf(
            PrecioVigente(ModoPrecio.TROZO, 1, 500.0, esReferencia = true)
        )
    ),
    diasPorSemana = dias,
    unidadesPorDia = unidades,
    cargando = false
)

@Preview(showBackground = true, name = "Simulación - con precio")
@Composable
private fun SimulacionConPrecio() {
    ReposteriaTheme {
        PasoSimulacion("Torta de manjar", estadoDeEjemplo(), AccionesSimulacion())
    }
}

@Preview(showBackground = true, name = "Simulación - todavía no la vendo")
@Composable
private fun SimulacionSinVender() {
    ReposteriaTheme {
        PasoSimulacion("Torta de manjar", estadoDeEjemplo(unidades = "0"), AccionesSimulacion())
    }
}

@Preview(showBackground = true, name = "Simulación - sin precio")
@Composable
private fun SimulacionSinPrecio() {
    ReposteriaTheme {
        PasoSimulacion(
            "Torta de manjar",
            estadoDeEjemplo(conPrecio = false),
            AccionesSimulacion()
        )
    }
}

@Preview(showBackground = true, name = "Simulación - oscuro")
@Composable
private fun SimulacionOscuro() {
    ReposteriaTheme(oscuro = true) {
        PasoSimulacion("Torta de manjar", estadoDeEjemplo(), AccionesSimulacion())
    }
}
