package com.sandyyera.reposteria.ui.ingredientes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.logica.calculadora.UnidadDeCompra
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.ui.componentes.BarraBusqueda
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/**
 * Todo lo que se puede pedir desde la pantalla de ingredientes.
 *
 * Van agrupadas en un solo objeto porque sueltas ya eran diecinueve, y una lista así de
 * larga se equivoca sola: basta cambiar dos de orden para conectar el botón de borrar con
 * el de editar, y el compilador no diría nada porque tienen la misma forma.
 *
 * Todas traen una implementación vacía por defecto para que las vistas previas solo
 * escriban las que les importan.
 */
data class AccionesIngredientes(
    val buscar: (String) -> Unit = {},
    val pedirAlta: () -> Unit = {},
    val editar: (Ingrediente) -> Unit = {},
    val pedirBorrado: (Ingrediente) -> Unit = {},

    val cambiarNombre: (String) -> Unit = {},
    val cambiarValor: (String) -> Unit = {},
    val guardar: () -> Unit = {},

    val confirmarBorrado: () -> Unit = {},
    val confirmarReemplazo: () -> Unit = {},
    val cerrarDialogo: () -> Unit = {},

    val abrirCalculadora: () -> Unit = {},
    val cambiarPrecio: (String) -> Unit = {},
    val cambiarCantidad: (String) -> Unit = {},
    val cambiarUnidad: (UnidadDeCompra) -> Unit = {},
    val buscarDestino: (String) -> Unit = {},
    val elegirDestino: (DestinoDelValor) -> Unit = {},
    val terminarCalculadora: () -> Unit = {},
    val cerrarCalculadora: () -> Unit = {},

    val mensajeMostrado: () -> Unit = {}
)

/**
 * La pantalla de ingredientes, conectada al [IngredientesViewModel].
 *
 * Está partida en dos: esta parte lee el estado y reparte las acciones, y [ListaIngredientes]
 * solo dibuja. La razón es práctica: lo que dibuja no sabe que existe una base de datos, así
 * que se puede ver en la vista previa de Android Studio con ingredientes inventados, sin
 * celular y sin esperar a compilar la app entera.
 */
@Composable
fun ListaIngredientesScreen(
    modelo: IngredientesViewModel,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    val acciones = remember(modelo) {
        AccionesIngredientes(
            buscar = modelo::buscar,
            pedirAlta = modelo::abrirAlta,
            editar = modelo::abrirEdicion,
            pedirBorrado = modelo::pedirBorrado,
            cambiarNombre = modelo::cambiarNombre,
            cambiarValor = modelo::cambiarValor,
            guardar = modelo::guardar,
            confirmarBorrado = modelo::confirmarBorrado,
            confirmarReemplazo = modelo::confirmarReemplazo,
            cerrarDialogo = modelo::cerrarDialogo,
            abrirCalculadora = modelo::abrirCalculadora,
            cambiarPrecio = modelo::cambiarPrecio,
            cambiarCantidad = modelo::cambiarCantidad,
            cambiarUnidad = modelo::cambiarUnidad,
            buscarDestino = modelo::buscarDestino,
            elegirDestino = modelo::elegirDestino,
            terminarCalculadora = modelo::terminarCalculadora,
            cerrarCalculadora = modelo::cerrarCalculadora,
            mensajeMostrado = modelo::mensajeMostrado
        )
    }

    ListaIngredientes(estado = estado, acciones = acciones, modifier = modifier)
}

/**
 * El dibujo de la sección de ingredientes: la lista, la calculadora y los avisos.
 *
 * No recibe el ViewModel ni el repositorio a propósito — solo datos y funciones —, así que
 * se puede dibujar en la vista previa y no puede tocar la base de datos por accidente.
 *
 * Cuando la calculadora está abierta ocupa la pantalla entera en vez de aparecer como un
 * cuadro encima de la lista: tiene dos partes (la cuenta y a quién aplicársela) y no cabe
 * en un cuadro flotante con el teclado abierto.
 */
@Composable
fun ListaIngredientes(
    estado: EstadoIngredientes,
    acciones: AccionesIngredientes,
    modifier: Modifier = Modifier
) {
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    // Muestra el aviso de "se agregó / se eliminó" y avisa de vuelta cuando terminó, para
    // que no reaparezca al girar el teléfono.
    LaunchedEffect(estado.mensaje) {
        val texto = estado.mensaje ?: return@LaunchedEffect
        anfitrionDeMensajes.showSnackbar(texto)
        acciones.mensajeMostrado()
    }

    val calculadora = estado.calculadora
    if (calculadora != null) {
        CalculadoraValorPorGramo(
            estado = calculadora,
            alCambiarPrecio = acciones.cambiarPrecio,
            alCambiarCantidad = acciones.cambiarCantidad,
            alCambiarUnidad = acciones.cambiarUnidad,
            alBuscarDestino = acciones.buscarDestino,
            alElegirDestino = acciones.elegirDestino,
            alTerminar = acciones.terminarCalculadora,
            alCerrar = acciones.cerrarCalculadora,
            modifier = modifier
        )
    } else {
        Catalogo(
            estado = estado,
            acciones = acciones,
            anfitrionDeMensajes = anfitrionDeMensajes,
            modifier = modifier
        )
    }

    // Los avisos van fuera del `if`: la confirmación de reemplazo aparece **encima de la
    // calculadora**, sin cerrarla, para que cancelar devuelva a donde se estaba.
    when (val dialogo = estado.dialogo) {
        is DialogoIngrediente.Ninguno -> Unit

        is DialogoIngrediente.Formulario -> FormularioIngrediente(
            estado = dialogo,
            alCambiarNombre = acciones.cambiarNombre,
            alCambiarValor = acciones.cambiarValor,
            alGuardar = acciones.guardar,
            alCerrar = acciones.cerrarDialogo
        )

        is DialogoIngrediente.ConfirmarBorrado -> ConfirmarBorradoIngrediente(
            estado = dialogo,
            alConfirmar = acciones.confirmarBorrado,
            alCerrar = acciones.cerrarDialogo
        )

        is DialogoIngrediente.ConfirmarReemplazo -> ConfirmarReemplazoValor(
            estado = dialogo,
            alConfirmar = acciones.confirmarReemplazo,
            alCerrar = acciones.cerrarDialogo
        )
    }
}

/**
 * El catálogo propiamente tal: los dos botones fijos arriba, el buscador, y la lista.
 *
 * Los botones van **fuera** del área que se desplaza (patrón de 8.1, el mismo de recetas y
 * moldes): con cuarenta ingredientes, un botón dentro del scroll obligaría a subir hasta
 * arriba cada vez que se quiere agregar uno.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Catalogo(
    estado: EstadoIngredientes,
    acciones: AccionesIngredientes,
    anfitrionDeMensajes: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Ingredientes") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
                // El botón del menú de 3 líneas se agrega junto con la navegación (12.1),
                // cuando exista una segunda sección a la que ir.
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
                Spacer(Modifier.width(Medidas.chico))
                Text("Nuevo ingrediente")
            }

            // Va con borde y no relleno para que se lea como lo que es: una herramienta de
            // apoyo, no la acción principal de la pantalla.
            OutlinedButton(
                onClick = acciones.abrirCalculadora,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medidas.objetivoTactil)
            ) {
                Text("Calculadora de valor por gramo")
            }

            BarraBusqueda(
                texto = estado.busqueda,
                alCambiar = acciones.buscar,
                marcador = "Buscar ingrediente"
            )

            when {
                // Mientras carga no se dice nada: la primera lectura de la base es
                // instantánea y un "cargando…" que parpadea molesta más de lo que informa.
                estado.cargando -> Spacer(Modifier.weight(1f))

                estado.catalogoVacio -> MensajeCentrado(
                    titulo = "Todavía no hay ingredientes",
                    detalle = "Toca «Nuevo ingrediente» para agregar el primero.",
                    modifier = Modifier.weight(1f)
                )

                estado.busquedaSinResultados -> MensajeCentrado(
                    titulo = "Nada coincide con «${estado.busqueda}»",
                    detalle = "Puedes buscar por cualquier parte del nombre, y no hace " +
                        "falta escribir las tildes.",
                    modifier = Modifier.weight(1f)
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Medidas.chico),
                    // Aire al final para que la última tarjeta no quede pegada al borde.
                    contentPadding = PaddingValues(top = Medidas.chico, bottom = Medidas.grande)
                ) {
                    items(estado.visibles, key = { it.id }) { ingrediente ->
                        TarjetaIngrediente(
                            ingrediente = ingrediente,
                            alEditar = { acciones.editar(ingrediente) },
                            alBorrar = { acciones.pedirBorrado(ingrediente) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Un ingrediente de la lista: su nombre, lo que vale el gramo, y qué se puede hacer con él.
 *
 * Los dos botones son explícitos en vez de esconder el editar detrás de "tocar la tarjeta".
 * Esta app se usa apurada y a veces con las manos sucias: un gesto que no se ve es un gesto
 * que no se encuentra, y cada botón ocupa sus 48dp completos.
 */
@Composable
private fun TarjetaIngrediente(
    ingrediente: Ingrediente,
    alEditar: () -> Unit,
    alBorrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
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
                Text(
                    text = ingrediente.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    // El dinero siempre pasa por formatearNumero, nunca se arma a mano (12.6).
                    text = "$${formatearNumero(ingrediente.valorPorGramo)} por gramo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = alEditar) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    // Nombra el ingrediente para que el lector de pantalla no diga
                    // "editar, editar, editar" en toda la lista.
                    contentDescription = "Editar ${ingrediente.nombre}"
                )
            }

            IconButton(onClick = alBorrar) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar ${ingrediente.nombre}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** El texto que ocupa el lugar de la lista cuando no hay nada que mostrar. */
@Composable
private fun MensajeCentrado(
    titulo: String,
    detalle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Medidas.chico, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// --- Vistas previas ---

private val ingredientesDeEjemplo = listOf(
    Ingrediente(id = 1, nombre = "Azúcar flor", valorPorGramo = 1.55),
    Ingrediente(id = 2, nombre = "Harina", valorPorGramo = 1.2),
    Ingrediente(id = 3, nombre = "Manjar", valorPorGramo = 4.8),
    Ingrediente(id = 4, nombre = "Plátano", valorPorGramo = 0.0),
    Ingrediente(id = 5, nombre = "Chocolate cobertura", valorPorGramo = 12.345)
)

private fun estadoDeEjemplo(
    visibles: List<Ingrediente> = ingredientesDeEjemplo,
    hayIngredientes: Boolean = true,
    busqueda: String = ""
) = EstadoIngredientes(
    visibles = visibles,
    hayIngredientes = hayIngredientes,
    busqueda = busqueda,
    cargando = false
)

@Composable
private fun PrevisualizarLista(estado: EstadoIngredientes) {
    ListaIngredientes(estado = estado, acciones = AccionesIngredientes())
}

@Preview(showBackground = true, name = "Lista - claro")
@Composable
private fun ListaClaro() {
    ReposteriaTheme(oscuro = false) { PrevisualizarLista(estadoDeEjemplo()) }
}

@Preview(showBackground = true, name = "Lista - oscuro")
@Composable
private fun ListaOscuro() {
    ReposteriaTheme(oscuro = true) { PrevisualizarLista(estadoDeEjemplo()) }
}

@Preview(showBackground = true, name = "Catálogo vacío")
@Composable
private fun ListaVacia() {
    ReposteriaTheme {
        PrevisualizarLista(estadoDeEjemplo(visibles = emptyList(), hayIngredientes = false))
    }
}

@Preview(showBackground = true, name = "Búsqueda sin resultados")
@Composable
private fun ListaSinResultados() {
    ReposteriaTheme {
        PrevisualizarLista(
            estadoDeEjemplo(visibles = emptyList(), busqueda = "chocolate blanco")
        )
    }
}

@Preview(showBackground = true, name = "Con el formulario abierto")
@Composable
private fun ListaConFormulario() {
    ReposteriaTheme {
        PrevisualizarLista(
            estadoDeEjemplo().copy(
                dialogo = DialogoIngrediente.Formulario(
                    nombre = "Nuez",
                    valorPorGramo = "8,5",
                    tocadoNombre = true,
                    tocadoValor = true
                )
            )
        )
    }
}
