package com.sandyyera.reposteria.ui.recetas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/**
 * Los pasos de una receta abierta (8.1).
 *
 * Van en un `enum` y no en un booleano porque de acá salen los seis pasos del asistente:
 * cantidades, rendimiento, duración, gastos, simulación y pasos. Con un booleano el tercero
 * ya obligaría a rehacerlo.
 *
 * El [titulo] vive en el enum y no en la pantalla por lo mismo que la etiqueta de
 * `CampoDeMolde`: la fila de arriba y cualquier otro lugar que nombre un paso tienen que
 * decirle igual, y dos listas de textos paralelas se desincronizan sin que nadie lo note.
 */
enum class PasoDeReceta(val titulo: String) {
    CANTIDADES("Cantidades"),

    /**
     * El molde va **antes** que rendimiento y no después, porque decide lo de allá: con
     * molde el peso final es opcional y sin molde es obligatorio (8.3). Preguntando el peso
     * primero habría que cambiar la respuesta después.
     */
    MOLDE("Molde"),
    RENDIMIENTO("Rendimiento"),
    DURACION("Duración")
}

/**
 * La fila de pasos que se desplaza, debajo del título de la receta.
 *
 * **Reemplaza a los botones de "Siguiente"**, y no es solo un cambio de lugar. Con el botón
 * al final de cada paso, la receta se recorría en un solo sentido: para volver a cantidades
 * desde duración había que salir y entrar de nuevo, y no se sabía cuántos pasos había hasta
 * llegar al último. Acá se ven todos y se salta al que interesa.
 *
 * Resuelve además un choque que se notaba en el celular: la X de rendimiento cerraba el paso
 * y devolvía a cantidades en vez de salir de la receta, así que el mismo gesto significaba
 * dos cosas según dónde se estuviera. Con la fila, **la X siempre sale de la receta** y
 * moverse entre pasos es la fila; los dos gestos dejan de competir.
 *
 * Se desplaza en horizontal aunque hoy los tres quepan: al llegar gastos, simulación y pasos
 * serán seis, y una fila que se apretuja hasta dejar los títulos ilegibles es peor que una
 * que se arrastra.
 */
@Composable
fun FilaDePasos(
    pasoActual: PasoDeReceta,
    alElegirPaso: (PasoDeReceta) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Medidas.medio, vertical = Medidas.chico),
        horizontalArrangement = Arrangement.spacedBy(Medidas.chico),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PasoDeReceta.entries.forEach { paso ->
            FichaDePaso(
                paso = paso,
                esElActual = paso == pasoActual,
                alElegir = { alElegirPaso(paso) }
            )
        }
    }
}

/**
 * Un paso dentro de la fila.
 *
 * El actual va relleno y los demás en el gris del tema. La diferencia es de fondo y no de
 * grosor de letra a propósito: en un celular al sol, "negrita contra normal" no se distingue
 * de un vistazo y el color sí.
 *
 * El actual **no se puede tocar**: llevaría al mismo lugar, y un toque que no hace nada deja
 * dudando si la pantalla respondió.
 */
@Composable
private fun FichaDePaso(
    paso: PasoDeReceta,
    esElActual: Boolean,
    alElegir: () -> Unit
) {
    val fondo = if (esElActual) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val letra = if (esElActual) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Medidas.medio))
            .background(fondo)
            .clickable(enabled = !esElActual, onClick = alElegir)
            .heightIn(min = Medidas.objetivoTactil)
            .padding(horizontal = Medidas.medio),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = paso.titulo,
            style = MaterialTheme.typography.bodyMedium,
            color = letra
        )
    }
}

// --- Vistas previas ---

@Preview(showBackground = true, name = "Fila de pasos - en cantidades")
@Composable
private fun FilaEnCantidades() {
    ReposteriaTheme {
        FilaDePasos(pasoActual = PasoDeReceta.CANTIDADES, alElegirPaso = {})
    }
}

@Preview(showBackground = true, name = "Fila de pasos - en duración, oscuro")
@Composable
private fun FilaEnDuracionOscuro() {
    ReposteriaTheme(oscuro = true) {
        FilaDePasos(pasoActual = PasoDeReceta.DURACION, alElegirPaso = {})
    }
}
