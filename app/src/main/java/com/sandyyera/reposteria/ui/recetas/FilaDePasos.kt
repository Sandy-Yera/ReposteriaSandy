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
import androidx.compose.foundation.ScrollState
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
 * Van en un `enum` y no en un booleano porque de acá salen los **siete** pasos del asistente
 * (8.1): cantidades, molde, rendimiento, duración, gastos, simulación y pasos. Con un
 * booleano el tercero ya obligaría a rehacerlo — y de hecho el molde llegó después, que es
 * justamente el caso que un booleano no habría aguantado.
 *
 * El [titulo] vive en el enum y no en la pantalla por lo mismo que la etiqueta de
 * `CampoDeMolde`: la fila de arriba y cualquier otro lugar que nombre un paso tienen que
 * decirle igual, y dos listas de textos paralelas se desincronizan sin que nadie lo note.
 */
enum class PasoDeReceta(val titulo: String) {
    /**
     * La receta entera de un vistazo (8.12). **Va primera y es donde se abre una receta.**
     *
     * Una receta se arma una vez y se lee muchas. Entrando por Cantidades, leerla obligaba a
     * recorrer los siete pasos acordándose del anterior; entrando por acá, lo primero que se ve
     * es de qué está hecha y desde ahí se llega a cualquier parte.
     *
     * **No edita nada**, y por eso puede ser un paso más de la fila sin romper la regla de que
     * cada paso es un formulario: este es el índice de los otros seis.
     */
    RESUMEN("Resumen"),

    CANTIDADES("Cantidades"),

    /**
     * Duración (8.4). Va **segundo, y no en medio del camino de las cifras**.
     *
     * Es el único paso que no alimenta ninguna cuenta: cuánto dura un producto no entra en
     * ningún costo, ningún precio ni ninguna proyección. Los otros cuatro sí forman una
     * cadena —molde → rendimiento → gastos → simulación—, y tenerlo enclavado entre
     * rendimiento y gastos obligaba a saltarlo cada vez que se recorría esa cadena. Puesto
     * acá queda pegado a cantidades, que es lo otro que se anota mirando la receta y no la
     * calculadora, y los cuatro que sí dependen entre sí quedan seguidos.
     */
    DURACION("Duración"),

    /**
     * El molde va **antes** que rendimiento y no después, porque decide lo de allá: con
     * molde el peso final es opcional y sin molde es obligatorio (8.3). Preguntando el peso
     * primero habría que cambiar la respuesta después.
     */
    MOLDE("Molde"),
    RENDIMIENTO("Rendimiento"),

    /**
     * Gastos y ganancias (8.5). Va **después** de rendimiento porque necesita las dos cosas
     * que aquel define junto con cantidades: el costo sale de los ingredientes y todo se
     * reparte entre los trozos. Preguntando el precio antes, cada cifra que se muestre acá es
     * una cuenta hecha contra datos que todavía no existen.
     */
    GASTOS("Gastos y ganancias"),

    /**
     * Ganancias simuladas (8.7). Va **después** de gastos porque proyecta lo que aquel
     * calcula: sin precio de referencia no hay nada que multiplicar por los días.
     */
    SIMULACION("Ganancias simuladas"),

    /**
     * Pasos (8.8). Va **al final y no junto a duración**, aunque tampoco alimente ninguna
     * cifra: es lo más largo de escribir de toda la receta, y lo que se hace una vez cuando
     * ya está todo lo demás decidido. Duración se anota de paso mirando el producto; los
     * pasos se sientan a escribirse.
     */
    PASOS("Pasos")
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
 * Se desplaza en horizontal aunque hoy los primeros quepan: al llegar simulación y pasos
 * serán siete, y una fila que se apretuja hasta dejar los títulos ilegibles es peor que una
 * que se arrastra.
 *
 * **Dónde está arrastrada [desplazamiento] no lo guarda esta función**, y ese es todo el
 * punto: cada paso dibuja su propia fila, así que al cambiar de paso la fila se construye de
 * nuevo y con un estado propio volvería al principio sola. Se veía feo justamente cuando más
 * molesta — arrastrada hasta el final para tocar el último paso, el toque devolvía la fila al
 * inicio y dejaba de verse lo que se acababa de elegir. El estado vive en
 * `NavegacionPrincipal`, que es lo único que sigue vivo mientras la receta está abierta; es
 * la misma lección del título, y aparece por lo mismo: **lo que es igual en las cinco
 * pantallas vive donde las cinco se juntan**.
 *
 * El valor por defecto es para las vistas previas, que dibujan una pantalla suelta.
 */
@Composable
fun FilaDePasos(
    pasoActual: PasoDeReceta,
    alElegirPaso: (PasoDeReceta) -> Unit,
    modifier: Modifier = Modifier,
    desplazamiento: ScrollState = rememberScrollState()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(desplazamiento)
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
