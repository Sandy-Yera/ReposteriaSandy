package com.sandyyera.reposteria.ui.componentes

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/**
 * El cuadro que usan **todos** los diálogos de la app, en vez de `AlertDialog` a secas.
 *
 * Lo único que agrega es que **el teclado no lo tape**, y existe por eso. Sandy lo reportó en el
 * cuadro de agregar al almacén: *"de primeras no funciona, debo sacar teclado, volver a
 * interactuar y, a la segunda o tercera, puedo moverme"*.
 *
 * **Un diálogo es una ventana aparte de la de la app**, y ahí está la trampa: lo que se arregle en
 * la Activity —el borde a borde, el `imePadding` de la raíz— no le llega. Cada cuadro tiene que
 * resolver el teclado por su cuenta, y hacerlo cuadro por cuadro significa que el próximo que
 * alguien escriba va a nacer roto. Por eso es un envoltorio y no una instrucción escrita en la
 * documentación: la forma de acordarse es no tener que acordarse.
 *
 * Las dos mitades:
 *
 * - `decorFitsSystemWindows = false` hace que la ventana del cuadro **no se achique sola** y que
 *   el teclado llegue como una medida que se puede consultar. Sin esto, el sistema la achica por
 *   su cuenta y con retraso, que es exactamente el "a la segunda o tercera" del reporte.
 * - `imePadding()` deja ese hueco abajo. Como el cuadro va centrado, el relleno de abajo lo
 *   **empuja hacia arriba**, que es lo que hace que se vea entero encima del teclado.
 *
 * Los nombres de los parámetros son los mismos de `AlertDialog`, en inglés y no traducidos, a
 * propósito: así cambiar uno por otro es solo cambiar el nombre de la función, sin tocar ninguna
 * de las 42 llamadas por dentro. Traducirlos habría convertido un reemplazo mecánico en 42
 * ediciones a mano, cada una con su oportunidad de equivocarse.
 */
@Composable
fun CuadroDeDialogo(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.imePadding(),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        properties = DialogProperties(decorFitsSystemWindows = false)
    )
}
