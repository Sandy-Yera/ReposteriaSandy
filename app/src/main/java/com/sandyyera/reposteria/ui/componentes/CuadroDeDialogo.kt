package com.sandyyera.reposteria.ui.componentes

import android.view.WindowManager
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
            AjustarLaVentanaDelCuadro()
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
 * Los tres ajustes que la ventana del cuadro necesita y `DialogProperties` no sabe decir.
 *
 * **Va adentro de un hueco del cuadro y no arriba, junto al `AlertDialog`.** Ahí afuera
 * `LocalView` es la vista de la app, no la del cuadro —el cuadro es otra ventana— y se estarían
 * ajustando las de la ventana equivocada. Se elige el hueco del botón de confirmar porque es el
 * **único obligatorio**: los demás son opcionales, y colgarlo de uno que puede faltar dejaría
 * cuadros sin ajustar sin que se note por qué.
 *
 * Es la única parte de la app que baja al nivel de la ventana de Android, y por eso está
 * encerrada acá: escrita en cada cuadro serían 42 lugares donde acordarse.
 */
@Composable
private fun AjustarLaVentanaDelCuadro() {
    val vista = LocalView.current
    // En `SideEffect` y no suelto: tocar la ventana es un efecto sobre algo de afuera de Compose,
    // y hacerlo en medio del dibujo es pedir que pase a destiempo.
    SideEffect {
        val ventana = (vista.parent as? DialogWindowProvider)?.window ?: return@SideEffect

        ventana.setDimAmount(VELO_DE_LOS_CUADROS)

        // **Que el sistema no mueva el cuadro: de eso ya se encarga `imePadding`.**
        //
        // Eran dos manos moviendo lo mismo, y por eso Sandy vio el cuadro *"subir, luego bajar un
        // poco, y después aparecer el teclado"*: Android achicaba la ventana por su cuenta —el
        // `adjustResize` del manifiesto vale también para los cuadros— y encima el relleno del
        // teclado la empujaba hacia arriba. Cada uno llegaba a su tiempo, y de ahí el tirón.
        //
        // Con `ADJUST_NOTHING` la ventana se queda quieta y el teclado sigue llegando como una
        // medida consultable, que es lo único que `imePadding` necesita.
        ventana.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // **Sin animación propia de aparecer.** Lo pidió Sandy después de ver el velo entrar y
        // salir. El cuadro aparece de una y lo único que se mueve en pantalla es el teclado
        // subiendo, que es un movimiento y no tres encimados.
        ventana.setWindowAnimations(0)
    }
}
