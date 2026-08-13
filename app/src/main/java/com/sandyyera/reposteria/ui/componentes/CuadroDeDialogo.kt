package com.sandyyera.reposteria.ui.componentes

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Cuánto oscurece la pantalla el velo que un cuadro pone detrás. 0 es invisible, 1 es negro.
 *
 * **Android usa 0,6 por defecto y acá va mucho más bajo.** Lo pidió Sandy después del arreglo del
 * teclado: con el borde a borde el velo pasó a llegar hasta el canto de la pantalla, así que
 * mientras el teclado sube se ve una franja gris donde va a aparecer. Es correcto —el velo cubre
 * lo que está detrás— pero se nota, y lo que se pidió fue *"lo más tenue posible, para que pase
 * desapercibido"*.
 *
 * **No se pone en 0.** El velo no es decoración: es lo que dice que la app de atrás está esperando
 * y que hay que contestar el cuadro antes de seguir. Sin **nada** de oscurecido, un cuadro sobre
 * una lista clara se lee como una tarjeta más de la lista. Con esto apenas se insinúa, que es
 * justo lo que se pidió.
 */
private const val VELO_DE_LOS_CUADROS = 0.1f

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
 * Y de paso baja el velo a [VELO_DE_LOS_CUADROS], que también quedó de ese arreglo.
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
        confirmButton = {
            BajarElVelo()
            confirmButton()
        },
        modifier = modifier.imePadding(),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        properties = DialogProperties(decorFitsSystemWindows = false)
    )
}

/**
 * Baja el oscurecido de la ventana del cuadro a [VELO_DE_LOS_CUADROS]. No dibuja nada.
 *
 * **Va adentro de un hueco del cuadro y no arriba, junto al `AlertDialog`.** Ahí afuera
 * `LocalView` es la vista de la app, no la del cuadro —el cuadro es otra ventana— y se estaría
 * bajando el velo de la ventana equivocada. Se elige el hueco del botón de confirmar porque es el
 * **único obligatorio**: los demás son opcionales, y colgarlo de uno que puede faltar dejaría
 * cuadros con el velo por defecto sin que se note por qué.
 *
 * `DialogProperties` no tiene dónde decir esto, así que se pide a la ventana de Android
 * directamente. Es la única parte de la app que baja a ese nivel, y por eso está encerrada acá:
 * escrita en cada cuadro serían 42 lugares donde acordarse.
 */
@Composable
private fun BajarElVelo() {
    val vista = LocalView.current
    // En `SideEffect` y no suelto: tocar la ventana es un efecto sobre algo de afuera de Compose,
    // y hacerlo en medio del dibujo es pedir que pase a destiempo.
    SideEffect {
        (vista.parent as? DialogWindowProvider)?.window?.setDimAmount(VELO_DE_LOS_CUADROS)
    }
}
