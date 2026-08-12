package com.sandyyera.reposteria.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandyyera.reposteria.AppContainer
import com.sandyyera.reposteria.BuildConfig
import com.sandyyera.reposteria.logica.almacen.RecetaParaElAlmacen
import com.sandyyera.reposteria.ui.almacen.AlmacenViewModel
import com.sandyyera.reposteria.ui.almacen.ListaAlmacenScreen
import com.sandyyera.reposteria.ui.empleados.EmpleadosViewModel
import com.sandyyera.reposteria.ui.empleados.ListaEmpleadosScreen
import com.sandyyera.reposteria.ui.ingredientes.IngredientesViewModel
import com.sandyyera.reposteria.ui.ingredientes.ListaIngredientesScreen
import com.sandyyera.reposteria.ui.moldes.ListaMoldesScreen
import com.sandyyera.reposteria.ui.moldes.MoldesViewModel
import com.sandyyera.reposteria.ui.recetas.CantidadesViewModel
import com.sandyyera.reposteria.ui.recetas.GastosViewModel
import com.sandyyera.reposteria.ui.recetas.ListaRecetasScreen
import com.sandyyera.reposteria.ui.recetas.ModelosDeLaReceta
import com.sandyyera.reposteria.ui.recetas.DuracionViewModel
import com.sandyyera.reposteria.ui.recetas.MoldeDeRecetaViewModel
import com.sandyyera.reposteria.ui.recetas.PasoCantidadesScreen
import com.sandyyera.reposteria.ui.recetas.PasoDeReceta
import com.sandyyera.reposteria.ui.recetas.PasoResumenScreen
import com.sandyyera.reposteria.ui.recetas.ResumenViewModel
import com.sandyyera.reposteria.ui.recetas.PasoDuracionScreen
import com.sandyyera.reposteria.ui.recetas.PasoGastosScreen
import com.sandyyera.reposteria.ui.recetas.PasoMoldeScreen
import com.sandyyera.reposteria.ui.recetas.PasoRendimientoScreen
import com.sandyyera.reposteria.ui.recetas.PasoPasosScreen
import com.sandyyera.reposteria.ui.recetas.PasoSimulacionScreen
import com.sandyyera.reposteria.ui.recetas.RendimientoViewModel
import com.sandyyera.reposteria.ui.recetas.PasosViewModel
import com.sandyyera.reposteria.ui.recetas.SimulacionViewModel
import com.sandyyera.reposteria.ui.recetas.TituloDeRecetaViewModel
import com.sandyyera.reposteria.ui.recetas.RecetasViewModel
import com.sandyyera.reposteria.ui.theme.Medidas
import kotlinx.coroutines.launch

/**
 * Las secciones de la app (12.1).
 *
 * Lo que todavía no exista se agrega al llegar su fase, y queda **fuera del enum** a propósito en
 * vez de puesto en gris: una opción que no lleva a ninguna parte se toca igual, y da la impresión
 * de que algo se rompió. Ventas entra con la Fase 12.
 */
enum class Seccion(val titulo: String, val icono: ImageVector) {
    /**
     * El orden es el de **lo que hay que tener antes**, no el de lo que más se usa: una receta
     * no se puede costear sin ingredientes cargados, y no se le puede poner molde sin moldes en
     * el catálogo. Recetas queda al final por ser la que depende de las otras dos.
     *
     * **Almacén va primero**, y lo pidió Sandy así. Encaja con la misma regla mirada de más
     * lejos: el almacén es lo que hay de verdad, y el catálogo de ingredientes es lo que se sabe
     * de eso. Es además la que se abre a diario —anotar lo que queda— mientras las otras tres se
     * llenan una vez y se corrigen de a poco.
     */
    ALMACEN("Almacén", Icons.Default.Home),
    INGREDIENTES("Ingredientes", Icons.Default.ShoppingCart),
    MOLDES("Moldes", Icons.Default.Star),
    RECETAS("Recetas", Icons.Default.Favorite),

    /**
     * Va **al final** y no junto a Ingredientes, aunque el orden sea el de "lo que hay que tener
     * antes": un empleado no se puede configurar sin recetas con precio, así que depende de todo
     * lo anterior. Es además la que menos se abre — los repartos se acuerdan una vez y se miran
     * de vez en cuando.
     */
    EMPLEADOS("Empleados", Icons.Default.Person)
}

/**
 * El menú de 3 líneas y la sección que se esté viendo.
 *
 * Cada sección tiene su propio ViewModel, y los dos siguen vivos al cambiar de una a otra:
 * ir a Recetas y volver a Ingredientes no borra lo que había escrito en el buscador. Eso
 * sale gratis de que `viewModel()` los guarde en la Activity y no en el Composable.
 *
 * La sección elegida va en `rememberSaveable` para que girar el teléfono no devuelva a
 * Ingredientes.
 */
@Composable
fun NavegacionPrincipal(
    contenedor: AppContainer,
    modifier: Modifier = Modifier
) {
    var seccionActual by rememberSaveable { mutableStateOf(Seccion.ALMACEN) }

    // Qué receta está abierta, o null si se está viendo la lista. Va en rememberSaveable
    // para que girar el teléfono no devuelva a la lista a mitad de carga.
    var recetaAbierta by rememberSaveable { mutableStateOf<Long?>(null) }

    // En qué paso de la receta se está. También en rememberSaveable, por lo mismo: girar
    // el teléfono en Rendimiento no puede devolver a Cantidades.
    var pasoActual by rememberSaveable { mutableStateOf(PasoDeReceta.RESUMEN) }

    // Lo que una receta manda al almacén al convertirse en ingrediente (14.13).
    //
    // Va acá y no dentro de una de las dos secciones porque **cruza de una a otra**: la receta lo
    // produce y el almacén lo consume, y este es el único punto que ve a las dos. No es
    // `rememberSaveable` a propósito: si el teléfono gira mientras el cuadro está abierto, el
    // cuadro sobrevive solo (vive en el ViewModel del almacén) y este dato ya cumplió su función
    // — guardarlo lo volvería a aplicar y reabriría el cuadro encima del que ya está.
    var loQueVaAlAlmacen by remember { mutableStateOf<RecetaParaElAlmacen?>(null) }

    // Una receta abierta se ve a pantalla completa, sin el menú de secciones: es un paso
    // dentro de la receta, no una sección de la app.
    //
    // **Salir y cambiar de paso son dos gestos distintos y ya no se pisan**: la X y el
    // botón de atrás cierran la receta desde cualquier paso, y moverse entre pasos es la
    // fila de arriba. Antes la X de rendimiento devolvía a cantidades, así que el mismo
    // ícono significaba una cosa en el primer paso y otra en el segundo.

    // Dónde viven los ViewModel de la receta abierta, para poder soltarlos al cerrarla.
    // Sin esto quedaban vivos hasta cerrar la app, y con ellos sus observadores de la base:
    // ver `ModelosDeLaReceta`. Es la causa de que la app se fuera poniendo lenta con el uso.
    val modelosDeReceta: ModelosDeLaReceta = viewModel()

    val idAbierta = recetaAbierta
    if (idAbierta != null) {
        // Las dos van en `remember` y no sueltas: sin eso se crean de nuevo en cada
        // redibujado, y como cada pantalla arma su `Acciones*` con `remember(...)` sobre
        // ellas, ese `remember` no serviría de nada y la pantalla entera se recompondría
        // por cada tecla que se escribe en un campo.
        //
        // `cerrarReceta` además **suelta los ViewModel de esa receta**. Va acá y no en un
        // `DisposableEffect` porque cerrar es un acto y no un efecto de dejar de dibujarse:
        // con un efecto, girar el teléfono también los soltaría, que es justo lo que este
        // arreglo evita.
        val cerrarReceta: () -> Unit = remember(idAbierta) {
            {
                recetaAbierta = null
                // Volver al primer paso no se hace acá: ya lo hace `alAbrirReceta`, y la
                // decisión vive en un solo lugar.
                modelosDeReceta.cerrar(idAbierta)
            }
        }
        val elegirPaso: (PasoDeReceta) -> Unit = remember { { pasoActual = it } }

        // El título se observa **una sola vez para los cuatro pasos**, y desde acá porque
        // este es el único punto que vive mientras la receta está abierta. Antes lo sacaba
        // cada paso de su propio estado: cuatro observaciones de la misma fila, y cada una
        // con su primer instante en blanco — eso era el parpadeo del encabezado al cambiar
        // de sección, y por eso pasaba una sola vez por paso.
        val tituloModelo: TituloDeRecetaViewModel = viewModel(
            viewModelStoreOwner = modelosDeReceta.de(idAbierta),
            factory = TituloDeRecetaViewModel.fabrica(idAbierta, contenedor.recetas)
        )
        val tituloReceta by tituloModelo.titulo.collectAsStateWithLifecycle()

        // Dónde está arrastrada la fila de pasos, **también una sola para los cinco**. Cada
        // paso dibuja su propia fila, así que con un estado por pantalla la fila volvía al
        // principio en cada toque: arrastrada hasta el final para llegar al último paso, el
        // toque la devolvía al inicio y se dejaba de ver justo lo recién elegido.
        val desplazamientoDePasos = rememberScrollState()

        when (pasoActual) {
            PasoDeReceta.RESUMEN -> PasoResumenScreen(
                tituloReceta = tituloReceta,
                desplazamientoDePasos = desplazamientoDePasos,
                modelo = viewModel(
                    viewModelStoreOwner = modelosDeReceta.de(idAbierta),
                    factory = ResumenViewModel.fabrica(
                        recetaId = idAbierta,
                        recetas = contenedor.recetas
                    )
                ),
                pasoActual = pasoActual,
                alElegirPaso = elegirPaso,
                alCerrarReceta = cerrarReceta,
                alGuardarComoIngrediente = { datos ->
                    // Cierra la receta y cambia de sección: llevar el dato sin cerrar dejaría el
                    // cuadro del almacén abierto detrás de una receta que sigue en pantalla.
                    loQueVaAlAlmacen = datos
                    cerrarReceta()
                    seccionActual = Seccion.ALMACEN
                },
                modifier = modifier
            )

            PasoDeReceta.CANTIDADES -> PasoCantidadesScreen(
                tituloReceta = tituloReceta,
                desplazamientoDePasos = desplazamientoDePasos,
                modelo = viewModel(
                    viewModelStoreOwner = modelosDeReceta.de(idAbierta),
                    factory = CantidadesViewModel.fabrica(
                        recetaId = idAbierta,
                        recetas = contenedor.recetas,
                        ingredientes = contenedor.ingredientes
                    )
                ),
                pasoActual = pasoActual,
                alElegirPaso = elegirPaso,
                alCerrarReceta = cerrarReceta,
                modifier = modifier
            )

            PasoDeReceta.MOLDE -> PasoMoldeScreen(
                tituloReceta = tituloReceta,
                desplazamientoDePasos = desplazamientoDePasos,
                modelo = viewModel(
                    viewModelStoreOwner = modelosDeReceta.de(idAbierta),
                    factory = MoldeDeRecetaViewModel.fabrica(
                        recetaId = idAbierta,
                        recetas = contenedor.recetas,
                        moldes = contenedor.moldes
                    )
                ),
                pasoActual = pasoActual,
                alElegirPaso = elegirPaso,
                alCerrarReceta = cerrarReceta,
                modifier = modifier
            )

            PasoDeReceta.RENDIMIENTO -> PasoRendimientoScreen(
                tituloReceta = tituloReceta,
                desplazamientoDePasos = desplazamientoDePasos,
                modelo = viewModel(
                    viewModelStoreOwner = modelosDeReceta.de(idAbierta),
                    factory = RendimientoViewModel.fabrica(
                        recetaId = idAbierta,
                        recetas = contenedor.recetas
                    )
                ),
                pasoActual = pasoActual,
                alElegirPaso = elegirPaso,
                alCerrarReceta = cerrarReceta,
                modifier = modifier
            )

            PasoDeReceta.DURACION -> PasoDuracionScreen(
                tituloReceta = tituloReceta,
                desplazamientoDePasos = desplazamientoDePasos,
                modelo = viewModel(
                    viewModelStoreOwner = modelosDeReceta.de(idAbierta),
                    factory = DuracionViewModel.fabrica(idAbierta, contenedor.recetas)
                ),
                pasoActual = pasoActual,
                alElegirPaso = elegirPaso,
                alCerrarReceta = cerrarReceta,
                modifier = modifier
            )

            PasoDeReceta.GASTOS -> PasoGastosScreen(
                tituloReceta = tituloReceta,
                desplazamientoDePasos = desplazamientoDePasos,
                modelo = viewModel(
                    viewModelStoreOwner = modelosDeReceta.de(idAbierta),
                    factory = GastosViewModel.fabrica(idAbierta, contenedor.recetas)
                ),
                pasoActual = pasoActual,
                alElegirPaso = elegirPaso,
                alCerrarReceta = cerrarReceta,
                modifier = modifier
            )

            PasoDeReceta.SIMULACION -> PasoSimulacionScreen(
                tituloReceta = tituloReceta,
                desplazamientoDePasos = desplazamientoDePasos,
                modelo = viewModel(
                    viewModelStoreOwner = modelosDeReceta.de(idAbierta),
                    factory = SimulacionViewModel.fabrica(idAbierta, contenedor.recetas)
                ),
                pasoActual = pasoActual,
                alElegirPaso = elegirPaso,
                alCerrarReceta = cerrarReceta,
                modifier = modifier
            )

            PasoDeReceta.PASOS -> PasoPasosScreen(
                tituloReceta = tituloReceta,
                desplazamientoDePasos = desplazamientoDePasos,
                modelo = viewModel(
                    viewModelStoreOwner = modelosDeReceta.de(idAbierta),
                    factory = PasosViewModel.fabrica(idAbierta, contenedor.recetas)
                ),
                pasoActual = pasoActual,
                alElegirPaso = elegirPaso,
                alCerrarReceta = cerrarReceta,
                modifier = modifier
            )
        }
    } else {
        MenuDeSecciones(
            contenedor = contenedor,
            seccionActual = seccionActual,
            alElegirSeccion = { seccionActual = it },
            loQueVaAlAlmacen = loQueVaAlAlmacen,
            alAplicarLoDelAlmacen = { loQueVaAlAlmacen = null },
            alAbrirReceta = {
                recetaAbierta = it
                // Cada receta que se abre empieza por el resumen: quedarse en el paso donde se
                // dejó la anterior confundiría más de lo que ahorra, y el resumen es lo que
                // contesta la pregunta con que uno abre una receta — qué lleva (8.12).
                pasoActual = PasoDeReceta.RESUMEN
            },
            modifier = modifier
        )
    }
}

/**
 * En qué versión va la app, abajo del menú (sección 15).
 *
 * **Va acá y no en una pantalla de configuración**, que todavía no existe: el menú es lo único
 * que se abre desde cualquier parte, y esto se mira justo cuando uno no sabe dónde está parado.
 * El día que haya configuración, este es el primer candidato a mudarse.
 *
 * El número **sale de `BuildConfig` y no está escrito acá**: la versión se declara una sola vez,
 * en `build.gradle.kts`, que es de donde también la lee Android para los ajustes del sistema.
 * Escribirla en el código sería una segunda copia que se separa a la primera actualización que
 * alguien apure.
 *
 * Se lee "0.10.01": todavía no terminada, fase 10, primera actualización de esa fase.
 */
@Composable
private fun VersionDeLaApp() {
    HorizontalDivider(modifier = Modifier.padding(top = Medidas.chico))
    Text(
        text = "Versión ${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Medidas.grande,
            end = Medidas.grande,
            top = Medidas.chico,
            bottom = Medidas.medio
        )
    )
}

/**
 * El menú de 3 líneas con las secciones, y la que esté elegida.
 *
 * Va aparte de [NavegacionPrincipal] para no depender de un `return` temprano dentro de un
 * Composable: al abrir una receta cambia la estructura de lo que se dibuja, y expresarlo
 * como dos ramas de un `if` deja claro que son dos árboles distintos y no un atajo.
 */
@Composable
private fun MenuDeSecciones(
    contenedor: AppContainer,
    seccionActual: Seccion,
    alElegirSeccion: (Seccion) -> Unit,
    alAbrirReceta: (Long) -> Unit,
    /**
     * Lo que una receta manda al almacén al convertirse en ingrediente (14.13), o `null`.
     *
     * Viaja **por parámetro y no por una variable compartida**: quien lo produce es la receta,
     * que vive en `NavegacionPrincipal`, y quien lo consume es la sección Almacén, que se dibuja
     * acá dentro. Son dos funciones distintas y este es el único hilo entre las dos.
     */
    loQueVaAlAlmacen: RecetaParaElAlmacen? = null,
    /** Avisa que ya se aplicó, para que no se vuelva a abrir el mismo cuadro. */
    alAplicarLoDelAlmacen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val estadoDelMenu = rememberDrawerState(DrawerValue.Closed)
    val alcance = rememberCoroutineScope()

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = estadoDelMenu,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Repostería",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(Medidas.grande)
                )
                HorizontalDivider()

                Seccion.entries.forEach { seccion ->
                    NavigationDrawerItem(
                        label = { Text(seccion.titulo) },
                        icon = { Icon(seccion.icono, contentDescription = null) },
                        selected = seccion == seccionActual,
                        onClick = {
                            alElegirSeccion(seccion)
                            alcance.launch { estadoDelMenu.close() }
                        },
                        modifier = Modifier.padding(
                            horizontal = Medidas.chico,
                            vertical = Medidas.minimo
                        )
                    )
                }

                VersionDeLaApp()
            }
        }
    ) {
        val abrirMenu: () -> Unit = { alcance.launch { estadoDelMenu.open() } }

        when (seccionActual) {
            Seccion.ALMACEN -> {
                val modeloAlmacen: AlmacenViewModel = viewModel(
                    factory = AlmacenViewModel.fabrica(
                        almacen = contenedor.almacen,
                        recetas = contenedor.recetas
                    )
                )
                // Se aplica **una sola vez y se limpia**: sin el `null` de después, volver al
                // almacén desde cualquier otra sección reabriría el mismo cuadro con los datos
                // de una receta que se convirtió hace rato.
                LaunchedEffect(loQueVaAlAlmacen) {
                    loQueVaAlAlmacen?.let {
                        modeloAlmacen.abrirAgregarDesdeReceta(it)
                        alAplicarLoDelAlmacen()
                    }
                }
                ListaAlmacenScreen(modelo = modeloAlmacen, alAbrirMenu = abrirMenu)
            }

            Seccion.INGREDIENTES -> ListaIngredientesScreen(
                modelo = viewModel(
                    factory = IngredientesViewModel.fabrica(contenedor.ingredientes)
                ),
                alAbrirMenu = abrirMenu
            )

            Seccion.EMPLEADOS -> ListaEmpleadosScreen(
                modelo = viewModel(
                    factory = EmpleadosViewModel.fabrica(contenedor.empleados)
                ),
                alAbrirMenu = abrirMenu
            )

            Seccion.RECETAS -> ListaRecetasScreen(
                modelo = viewModel(factory = RecetasViewModel.fabrica(contenedor.recetas)),
                alAbrirMenu = abrirMenu,
                alAbrirReceta = alAbrirReceta
            )

            Seccion.MOLDES -> ListaMoldesScreen(
                modelo = viewModel(factory = MoldesViewModel.fabrica(contenedor.moldes)),
                alAbrirMenu = abrirMenu
            )
        }
    }
}
