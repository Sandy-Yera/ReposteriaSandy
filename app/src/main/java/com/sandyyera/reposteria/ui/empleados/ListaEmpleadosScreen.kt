package com.sandyyera.reposteria.ui.empleados

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandyyera.reposteria.data.repositorio.RecetaDeUnEmpleado
import com.sandyyera.reposteria.logica.formato.AVISO_MONTOS_REDONDEADOS
import com.sandyyera.reposteria.logica.formato.formatearMonto
import com.sandyyera.reposteria.logica.sueldos.SimulacionMultipleResultado
import com.sandyyera.reposteria.ui.componentes.BarraBusqueda
import com.sandyyera.reposteria.ui.componentes.CampoNumerico
import com.sandyyera.reposteria.ui.componentes.MensajeCentrado
import com.sandyyera.reposteria.ui.theme.Medidas

/** Todo lo que se puede pedir desde la sección de empleados. */
data class AccionesEmpleados(
    val buscar: (String) -> Unit = {},
    val abrir: (Long) -> Unit = {},
    val cerrarDetalle: () -> Unit = {},
    val abrirNuevo: () -> Unit = {},
    val abrirRenombre: (FilaDeEmpleado) -> Unit = {},
    val cambiarNombre: (String) -> Unit = {},
    val guardarNombre: () -> Unit = {},
    val crearAunqueSeRepita: () -> Unit = {},
    val pedirBorrado: (FilaDeEmpleado) -> Unit = {},
    val confirmarBorrado: () -> Unit = {},
    val abrirElegirReceta: () -> Unit = {},
    val buscarReceta: (String) -> Unit = {},
    val elegirReceta: (RecetaCandidata) -> Unit = {},
    val abrirSueldo: (RecetaDeUnEmpleado) -> Unit = {},
    val cambiarGanancia: (String) -> Unit = {},
    val guardarSueldo: () -> Unit = {},
    val quitarReceta: (RecetaDeUnEmpleado) -> Unit = {},
    val cambiarDias: (String) -> Unit = {},
    val cambiarUnidades: (RecetaDeUnEmpleado, String) -> Unit = { _, _ -> },
    val soltarUnidades: (RecetaDeUnEmpleado) -> Unit = {},
    val cerrarDialogo: () -> Unit = {},
    val abrirMenu: () -> Unit = {}
)

/** La sección de empleados conectada a su ViewModel. */
@Composable
fun ListaEmpleadosScreen(
    modelo: EmpleadosViewModel,
    alAbrirMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val delEmpleado by modelo.delEmpleado.collectAsStateWithLifecycle()
    val dialogo by modelo.dialogo.collectAsStateWithLifecycle()
    val aviso by modelo.aviso.collectAsStateWithLifecycle()

    val acciones = remember(modelo, alAbrirMenu) {
        AccionesEmpleados(
            buscar = modelo::buscar,
            abrir = modelo::abrir,
            cerrarDetalle = modelo::cerrarDetalle,
            abrirNuevo = modelo::abrirNuevo,
            abrirRenombre = modelo::abrirRenombre,
            cambiarNombre = modelo::cambiarNombre,
            guardarNombre = modelo::guardarNombre,
            crearAunqueSeRepita = modelo::crearAunqueSeRepita,
            pedirBorrado = modelo::pedirBorrado,
            confirmarBorrado = modelo::confirmarBorrado,
            abrirElegirReceta = modelo::abrirElegirReceta,
            buscarReceta = modelo::buscarReceta,
            elegirReceta = modelo::elegirReceta,
            abrirSueldo = modelo::abrirSueldo,
            cambiarGanancia = modelo::cambiarGanancia,
            guardarSueldo = modelo::guardarSueldo,
            quitarReceta = modelo::quitarReceta,
            cambiarDias = modelo::cambiarDias,
            cambiarUnidades = modelo::cambiarUnidades,
            soltarUnidades = modelo::soltarUnidades,
            cerrarDialogo = modelo::cerrarDialogo,
            abrirMenu = alAbrirMenu
        )
    }

    ListaEmpleados(estado, delEmpleado, dialogo, aviso, acciones, modelo::mensajeMostrado, modifier)
}

/**
 * El dibujo de la sección Empleados (sección 10).
 *
 * **Lista y detalle en la misma pantalla y no en dos**, decidido por `abierto`: entrar a un
 * empleado no cambia de sección, cambia de qué se está mirando dentro de ella. El botón de atrás
 * cierra el detalle antes de salir, que es lo que uno espera del gesto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaEmpleados(
    estado: EstadoEmpleados,
    delEmpleado: EstadoDelEmpleado,
    dialogo: DialogoEmpleados,
    aviso: String?,
    acciones: AccionesEmpleados,
    avisoMostrado: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val abierto = estado.elAbierto
    val anfitrionDeMensajes = remember { SnackbarHostState() }

    // El botón de atrás cierra el detalle antes de salir de la sección, que es lo que uno espera
    // del gesto: entrar a un empleado no cambió de sección, cambió de qué se está mirando.
    BackHandler(enabled = abierto != null) { acciones.cerrarDetalle() }

    LaunchedEffect(aviso) {
        val texto = aviso ?: return@LaunchedEffect
        try {
            anfitrionDeMensajes.showSnackbar(texto)
        } finally {
            // En `finally` por lo mismo que en las otras secciones: `showSnackbar` espera a que
            // el aviso se cierre solo, y cambiando de sección antes esta corrutina se cancela y
            // el mensaje quedaría pendiente, reapareciendo al volver.
            avisoMostrado()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Empleados") },
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
        if (abierto == null) {
            OutlinedButton(
                onClick = acciones.abrirNuevo,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medidas.objetivoTactil)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Nuevo empleado")
            }
            if (estado.empleados.size > 1) {
                BarraBusqueda(
                    texto = estado.busqueda,
                    alCambiar = acciones.buscar,
                    marcador = "Buscar empleado"
                )
            }
            when {
                estado.listaVacia -> MensajeCentrado(
                    titulo = "Todavía no hay empleados",
                    detalle = "El genérico es el modelo estándar de reparto y siempre está."
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Medidas.chico)
                ) {
                    items(estado.visibles, key = { it.id }) { fila ->
                        FilaEmpleado(fila, acciones)
                    }
                }
            }
        } else {
            DetalleDelEmpleado(abierto, delEmpleado, acciones)
        }
    }
    }

    when (dialogo) {
        is DialogoEmpleados.Ninguno -> Unit
        is DialogoEmpleados.Nombre -> DialogoNombreDeEmpleado(dialogo, acciones)
        is DialogoEmpleados.ConfirmarNombreRepetido -> DialogoNombreRepetido(dialogo, acciones)
        is DialogoEmpleados.ConfirmarBorrado -> DialogoBorrarEmpleado(dialogo, acciones)
        is DialogoEmpleados.Sueldo -> DialogoDelSueldo(dialogo, acciones)
        is DialogoEmpleados.ElegirReceta -> DialogoElegirReceta(dialogo, acciones)
    }
}

/**
 * Una fila de la lista.
 *
 * El genérico **no lleva lápiz ni papelera**, y no se dibujan en gris: un botón apagado se toca
 * igual y da la impresión de que algo se rompió (12.1). Lo que sí lleva es la etiqueta que
 * explica por qué es distinto.
 */
@Composable
private fun FilaEmpleado(fila: FilaDeEmpleado, acciones: AccionesEmpleados) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { acciones.abrir(fila.id) }
    ) {
        Row(
            modifier = Modifier.padding(start = Medidas.medio),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Medidas.chico)
            ) {
                // **Tocar el nombre lo cambia**, como el título de una receta y el del almacén
                // (8.4.1 #3). El lápiz se fue: lo que se hace seguido se toca.
                Text(
                    text = fila.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Medidas.objetivoTactil)
                        .clickable(enabled = fila.noSePuedeTocar == null) {
                            acciones.abrirRenombre(fila)
                        }
                        .wrapContentHeight()
                )
                fila.noSePuedeTocar?.let {
                    Text(
                        text = "El modelo estándar de reparto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (fila.noSePuedeTocar == null) {
                IconButton(onClick = { acciones.pedirBorrado(fila) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar empleado",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Lo de un empleado: sus recetas, lo que se lleva por cada una, y todas juntas (10.1 y 10.3).
 *
 * La simulación va **abajo y no arriba**: primero se asignan recetas y recién con eso el total
 * significa algo. Arriba, un total en cero sería lo primero que se ve de un empleado recién
 * creado.
 */
@Composable
private fun DetalleDelEmpleado(
    fila: FilaDeEmpleado,
    estado: EstadoDelEmpleado,
    acciones: AccionesEmpleados
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Medidas.medio)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fila.nombre,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = acciones.cerrarDetalle) { Text("Volver") }
        }

        OutlinedButton(
            onClick = acciones.abrirElegirReceta,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Medidas.objetivoTactil)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("  Darle una receta")
        }

        if (estado.sinRecetas) {
            Text(
                text = "Todavía no vende nada. Dale una receta y escribe cuánto se lleva por " +
                    "cada una que venda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        estado.recetas.forEach { receta -> FilaDeSueldo(receta, estado, acciones) }

        HorizontalDivider()
        SimulacionDelEmpleado(estado, acciones)
    }
}

/**
 * Una receta del empleado, con el reparto y cuántas vende al día.
 *
 * **Muestra las tres cifras del reparto y no solo la del empleado** (8.7.1): lo que entra, lo que
 * se lleva el dueño y lo que se lleva él. Una sola de las tres obliga a hacer las otras dos de
 * cabeza para comprobar que la cuenta cierra, que es justo lo que la pantalla ahorra.
 */
@Composable
private fun FilaDeSueldo(
    receta: RecetaDeUnEmpleado,
    estado: EstadoDelEmpleado,
    acciones: AccionesEmpleados
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Medidas.medio),
            verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = receta.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { acciones.abrirSueldo(receta) }
                )
                IconButton(onClick = { acciones.quitarReceta(receta) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Quitarle esta receta",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            val reparto = receta.reparto
            if (reparto == null) {
                // **Se dice por qué y no se deja el hueco.** `calcularSueldo` lanza con razón
                // cuando la receta no cubre su costo: no hay ganancia que repartir, y eso es un
                // dato de la receta que hay que ir a arreglar allá.
                Text(
                    text = "No se puede repartir todavía: esta receta no cubre su costo con el " +
                        "precio que tiene.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Cifra("Entra al vender una", reparto.ingresoBruto)
                Cifra("Me llevo", reparto.yoMeLlevo)
                Cifra("Se lleva", reparto.gananciaEmpleado, destacada = true)
            }

            // **El valor sale del estado y no de `receta.sueldo` directo.** Leyéndolo del `Int`
            // guardado, el campo no se podía vaciar: borrar el 1 no cambiaba nada y el 1 volvía
            // a aparecer, así que solo se podía escribir *delante* y convertirlo en 10. Vaciarlo
            // es el paso obligado para poner otro número.
            CampoNumerico(
                valor = estado.unidadesDe(receta),
                alCambiar = { acciones.cambiarUnidades(receta, it) },
                // Al salir se suelta el campo, para que vuelva a mostrar lo guardado: irse
                // dejándolo vacío no puede quedar contradiciendo a la simulación de abajo.
                alSalirDelCampo = { acciones.soltarUnidades(receta) },
                etiqueta = "¿Cuántas vende al día?",
                error = estado.errorDeUnidades(receta),
                // Se dice que es de acá y no de la receta: la receta tiene su propia simulación
                // (8.7) y son dos preguntas distintas — cuánto vende **este** empleado no es
                // cuánto se vende en total.
                ayuda = "Solo para la simulación de este empleado. No cambia la de la receta."
            )
        }
    }
}

/** Una cifra con su nombre. Los montos van redondeados, como en el resto de la app (15.2). */
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
            text = "$${formatearMonto(valor)}",
            style = if (destacada) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyMedium,
            fontWeight = if (destacada) FontWeight.Bold else null,
            color = if (valor < 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Lo que deja el empleado con todas sus recetas juntas (10.3).
 *
 * **Las omitidas se listan con su motivo**, no se descuentan en silencio: un total que ignora dos
 * recetas sin decirlo es un número que se cree y está mal, la misma regla que el valor del
 * almacén (14.3).
 */
@Composable
private fun SimulacionDelEmpleado(estado: EstadoDelEmpleado, acciones: AccionesEmpleados) {
    Text("Si vendiera eso todos los días", style = MaterialTheme.typography.titleMedium)
    Text(
        // **De quién es cada cifra**, que era la confusión: las tres columnas conviven en la
        // misma tarjeta y sin decirlo no se sabe cuál mirar.
        text = "Son las tres partes del mismo total: lo que entra, lo que te queda a ti y lo " +
            "que se lleva el empleado.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    CampoNumerico(
        valor = estado.diasPorSemana,
        alCambiar = acciones.cambiarDias,
        etiqueta = "¿Cuántos días a la semana?",
        error = estado.errorDias
    )

    val r: SimulacionMultipleResultado = estado.simulacion ?: return
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
            if (r.todasOmitidas) {
                Text("Ninguna de sus recetas se puede calcular todavía.")
            } else {
                Text("En un día", style = MaterialTheme.typography.titleSmall)
                Cifra("Entra", r.ingresoDiario)
                Cifra("Me llevo", r.yoMeLlevoDiario)
                Cifra("Se lleva", r.empleadoDiario, destacada = true)

                HorizontalDivider(Modifier.padding(vertical = Medidas.chico))

                Text("En una semana", style = MaterialTheme.typography.titleSmall)
                Cifra("Se lleva", r.empleadoSemanal)

                Text("En un mes", style = MaterialTheme.typography.titleSmall)
                Cifra("Se lleva", r.empleadoMensual, destacada = true)

                Text(
                    text = "El mes son 4,33 semanas: 52 repartidas en 12.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (r.hayOmitidas) {
                HorizontalDivider(Modifier.padding(vertical = Medidas.chico))
                Text(
                    text = "Quedaron fuera del total:",
                    style = MaterialTheme.typography.bodySmall
                )
                r.omitidas.forEach {
                    Text(
                        text = "• ${it.titulo}: ${it.motivo.comoSeLee}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = AVISO_MONTOS_REDONDEADOS,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// --- Los cuadros ---

@Composable
private fun DialogoNombreDeEmpleado(
    estado: DialogoEmpleados.Nombre,
    acciones: AccionesEmpleados
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text(if (estado.editando == null) "Nuevo empleado" else "Cambiar el nombre") },
        text = {
            OutlinedTextField(
                value = estado.nombre,
                onValueChange = acciones.cambiarNombre,
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = estado.error != null,
                supportingText = { estado.error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                )
            )
        },
        confirmButton = {
            TextButton(onClick = acciones.guardarNombre, enabled = estado.puedeGuardar) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

/**
 * Ya hay uno con ese nombre.
 *
 * **No es un rechazo**, y esa es la diferencia con los ingredientes: dos personas pueden llamarse
 * igual, y lo que identifica al empleado es su `id`. Se avisa por si fue un descuido, no para
 * impedirlo.
 */
@Composable
private fun DialogoNombreRepetido(
    estado: DialogoEmpleados.ConfirmarNombreRepetido,
    acciones: AccionesEmpleados
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("Ya tienes un '${estado.nombre}'") },
        text = {
            Text(
                // **Se dice cómo va a quedar**, no solo que se puede. Dos filas con el mismo
                // nombre son imposibles de distinguir en la lista, así que el que entra se
                // numera — y eso hay que saberlo antes de aceptar, no descubrirlo después.
                "Si son dos personas distintas está bien tener los dos: el nuevo va a quedar " +
                    "como '${estado.comoQuedaria}' para poder distinguirlos. Si te " +
                    "equivocaste, cancela y ponle otro nombre."
            )
        },
        confirmButton = {
            TextButton(onClick = acciones.crearAunqueSeRepita, enabled = !estado.guardando) {
                Text("Crear '${estado.comoQuedaria}'")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

@Composable
private fun DialogoBorrarEmpleado(
    estado: DialogoEmpleados.ConfirmarBorrado,
    acciones: AccionesEmpleados
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("¿Eliminar a ${estado.empleado.nombre}?") },
        text = {
            Text(
                if (estado.cuantasRecetas == 0) {
                    "No tiene recetas asignadas, así que no se pierde ningún reparto."
                } else {
                    "Se van con él los repartos de ${estado.cuantasRecetas} " +
                        (if (estado.cuantasRecetas == 1) "receta" else "recetas") +
                        ". Las recetas no se tocan. No se puede deshacer."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = acciones.confirmarBorrado, enabled = !estado.borrando) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

/**
 * Cuánto se lleva por vender una.
 *
 * **Dice el tope antes de que alguien se pase** en vez de solo rechazar después: el máximo es la
 * ganancia de la receta, y eso no se puede adivinar mirando el campo vacío.
 */
@Composable
private fun DialogoDelSueldo(estado: DialogoEmpleados.Sueldo, acciones: AccionesEmpleados) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text(estado.titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.chico)) {
                Text(
                    // **De dónde sale ese número**, que era la duda: es el precio marcado como
                    // referencia hoy, y ese se cambia en el paso de Gastos de la receta. Sin
                    // decirlo, el tope parece una regla fija de la app.
                    text = "Con el precio que la receta tiene marcado ahora, deja " +
                        "$${estado.comoSeLeeElTope} de ganancia. Eso es lo más que se puede " +
                        "llevar: el resto lo necesitas para cubrir el costo. Si cambias el " +
                        "precio de referencia en la receta, este tope cambia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CampoNumerico(
                    valor = estado.ganancia,
                    alCambiar = acciones.cambiarGanancia,
                    etiqueta = "¿Cuánto se lleva por cada una?",
                    error = estado.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.guardarSueldo, enabled = estado.puedeGuardar) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cancelar") }
        }
    )
}

/** Elegir a qué receta asignarle un sueldo. Solo aparecen las que todavía no tiene. */
@Composable
private fun DialogoElegirReceta(
    estado: DialogoEmpleados.ElegirReceta,
    acciones: AccionesEmpleados
) {
    AlertDialog(
        onDismissRequest = acciones.cerrarDialogo,
        title = { Text("¿Qué receta vende?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Medidas.chico)
            ) {
                if (estado.candidatas.isEmpty() && !estado.cargando) {
                    Text("Ya tiene todas las recetas que hay.")
                } else {
                    BarraBusqueda(
                        texto = estado.busqueda,
                        alCambiar = acciones.buscarReceta,
                        marcador = "Buscar receta"
                    )
                    estado.visibles.forEach { candidata ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Medidas.objetivoTactil)
                                .clickable(enabled = candidata.sePuede) {
                                    acciones.elegirReceta(candidata)
                                }
                                .padding(vertical = Medidas.chico)
                        ) {
                            Text(
                                text = candidata.datos.titulo,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (candidata.sePuede) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // **El motivo a la vista y no al tocarla.** Antes elegir una receta
                            // sin precio cerraba la app: las fórmulas del reparto lanzan con
                            // razón cuando no hay ganancia que repartir.
                            candidata.porQueNo?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.cerrarDialogo) { Text("Cerrar") }
        }
    )
}
