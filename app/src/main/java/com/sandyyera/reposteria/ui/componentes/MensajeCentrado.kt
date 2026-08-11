package com.sandyyera.reposteria.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sandyyera.reposteria.ui.theme.Medidas

/**
 * El texto que ocupa el lugar de una lista cuando no hay nada que mostrar (12.3).
 *
 * **Estaba copiado en tres pantallas** —recetas, ingredientes y almacén— y ya se habían separado:
 * dos centraban el texto y la tercera lo alineaba a la izquierda con otro relleno, así que la
 * misma situación se veía distinta según en qué sección uno estuviera. Al llegar la cuarta
 * (empleados) correspondía sacarlo acá y no hacer una copia más.
 *
 * Se queda con la versión centrada, que es la que estaba en dos de las tres y la que corresponde:
 * esto ocupa el hueco de una lista entera, y un texto pegado arriba a la izquierda en medio de una
 * pantalla vacía parece un error de dibujo antes que un mensaje.
 *
 * [detalle] no es opcional a propósito. Un vacío que solo dice "no hay nada" no ayuda; lo que
 * sirve es decir **qué hacer** —"toca Agregar para poner el primero"— y dejarlo opcional invita a
 * saltárselo.
 */
@Composable
fun MensajeCentrado(
    titulo: String,
    detalle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Medidas.grande),
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
