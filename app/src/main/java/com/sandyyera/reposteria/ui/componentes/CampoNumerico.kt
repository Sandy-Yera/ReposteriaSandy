package com.sandyyera.reposteria.ui.componentes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/**
 * El campo donde se escribe un monto o una cantidad.
 *
 * Pone el punto de mil solo, tecla por tecla, **y deja el cursor donde corresponde**. Lo
 * segundo no es un detalle: la versión anterior formateaba el texto pero dejaba el cursor
 * en el mismo número de posición, y como "1234" pasa a medir un carácter más al volverse
 * "1.234", el cursor terminaba entre el "3" y el "4". Lo que se escribiera después entraba
 * en medio del número y el monto quedaba mal sin que se notara.
 *
 * Por eso guarda un [TextFieldValue] y no un `String`: el `String` no lleva la posición del
 * cursor, así que el campo la conserva por su cuenta como un número, y ese número deja de
 * significar lo mismo cuando el texto cambia de largo. Dónde va el cursor lo decide
 * `formatearMientrasSeEscribe`, en `logica/`, que sí se puede probar.
 *
 * Este Composable es la única puerta de entrada de números en la app. Cualquier campo
 * numérico nuevo va por acá y no con un `OutlinedTextField` suelto: si no, hay que volver
 * a resolver lo del cursor en cada pantalla, y basta olvidarlo una vez.
 *
 * [alEnfocar] avisa cuando el campo **recibe el foco**, o sea cuando alguien lo toca para
 * mirarlo, aunque no escriba nada. Lo pidió el aviso de "peso reescalado, compruébalo"
 * (8.4.1, #4): ese aviso tiene que irse al mirar el campo, no al editarlo, porque el número
 * puede estar bien y exigir una edición sería obligar a borrar y reescribir lo mismo.
 *
 * [alSalirDelCampo] avisa cuando lo **pierde**, que es el momento en que lo escrito deja de
 * estar a medias. Lo pidió el guardado automático de las duraciones (8.4.1): ahí un bloque a
 * medio escribir se ve igual que uno vaciado a propósito, así que guardar en cada tecla
 * escribiría y borraría filas mientras la persona todavía decide.
 */
@Composable
fun CampoNumerico(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    ayuda: String? = null,
    accionDelTeclado: ImeAction = ImeAction.Done,
    alEnfocar: () -> Unit = {},
    alSalirDelCampo: () -> Unit = {}
) {
    var recordado by remember {
        mutableStateOf(TextFieldValue(valor, TextRange(valor.length)))
    }

    // `onFocusChanged` avisa de cada cambio, no de las transiciones: sin recordar el estado
    // anterior no se distingue "acaba de recibir el foco" de "sigue teniéndolo".
    var teniaElFoco by remember { mutableStateOf(false) }

    // Lo normal es que [valor] venga de lo último que se escribió acá, y entonces se usa
    // lo recordado, que además trae la posición del cursor. Si viene distinto es porque lo
    // cambió otra cosa —abrir el formulario ya con un valor puesto, por ejemplo—, y ahí se
    // muestra ese con el cursor al final.
    val campo = if (recordado.text == valor) {
        recordado
    } else {
        TextFieldValue(valor, TextRange(valor.length))
    }

    OutlinedTextField(
        value = campo,
        onValueChange = { escrito ->
            val resultado = formatearMientrasSeEscribe(escrito.text, escrito.selection.start)
            recordado = TextFieldValue(resultado.texto, TextRange(resultado.cursor))
            alCambiar(resultado.texto)
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { foco ->
                if (foco.isFocused && !teniaElFoco) alEnfocar()
                if (!foco.isFocused && teniaElFoco) alSalirDelCampo()
                teniaElFoco = foco.isFocused
            },
        label = { Text(etiqueta) },
        singleLine = true,
        isError = error != null,
        // El hueco del mensaje se reserva siempre, con error o sin él: si apareciera y
        // desapareciera, el cuadro daría un salto justo mientras se escribe.
        supportingText = { Text(error ?: ayuda ?: "") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = accionDelTeclado
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun CampoVacio() {
    ReposteriaTheme {
        CampoNumerico(
            valor = "",
            alCambiar = {},
            etiqueta = "Lo que pagaste (en $)",
            ayuda = "Se escribe con coma: 1,55"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CampoConMiles() {
    ReposteriaTheme {
        CampoNumerico(
            valor = "12.345,67",
            alCambiar = {},
            etiqueta = "Lo que pagaste (en $)"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CampoConError() {
    ReposteriaTheme {
        CampoNumerico(
            valor = "",
            alCambiar = {},
            etiqueta = "Lo que trae el paquete",
            error = "Escribe cuánto trae el paquete"
        )
    }
}
