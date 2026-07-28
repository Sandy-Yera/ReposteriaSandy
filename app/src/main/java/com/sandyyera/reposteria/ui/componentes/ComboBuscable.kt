package com.sandyyera.reposteria.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.sandyyera.reposteria.logica.busqueda.filtrarPor
import com.sandyyera.reposteria.ui.theme.Medidas
import com.sandyyera.reposteria.ui.theme.ReposteriaTheme

/**
 * Un buscador que además deja elegir de la lista y crear lo que no aparece.
 *
 * Es la pieza que se usa dentro de una receta para agregarle un ingrediente (7), y más
 * adelante para elegir un molde del catálogo (9.3). De ahí que sea genérico: la regla de
 * "qué coincide" tiene que ser la misma en los dos casos, y esa regla vive en
 * `logica/busqueda`, no acá.
 *
 * El alta rápida ([alCrear]) es lo que evita el peor momento de la app: estar cargando una
 * receta, darse cuenta de que falta un ingrediente, y tener que salir a crearlo perdiendo
 * lo escrito. Aparece solo cuando hay algo escrito que no coincide con nada — ofrecer
 * "crear" con el campo vacío no tendría qué crear.
 *
 * No abre un menú flotante sino que muestra la lista debajo, en el mismo flujo de la
 * pantalla. Es a propósito: un desplegable sobre un formulario largo tapa justo lo que se
 * está llenando, y en un celular deja el teclado peleando por el mismo espacio.
 */
@Composable
fun <T> ComboBuscable(
    opciones: List<T>,
    textoDe: (T) -> String,
    busqueda: String,
    alBuscar: (String) -> Unit,
    alElegir: (T) -> Unit,
    modifier: Modifier = Modifier,
    marcador: String = "Buscar",
    alCrear: ((String) -> Unit)? = null
) {
    val coincidencias = filtrarPor(opciones, busqueda) { textoDe(it) }
    val escrito = busqueda.trim()
    val puedeCrear = alCrear != null && escrito.isNotEmpty() &&
        coincidencias.none { textoDe(it).equals(escrito, ignoreCase = true) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Medidas.chico)
    ) {
        BarraBusqueda(texto = busqueda, alCambiar = alBuscar, marcador = marcador)

        LazyColumn(
            modifier = Modifier.heightIn(max = Medidas.altoMaximoDeLista),
            verticalArrangement = Arrangement.spacedBy(Medidas.minimo)
        ) {
            items(coincidencias) { opcion ->
                FilaDeOpcion(
                    texto = textoDe(opcion),
                    alTocar = { alElegir(opcion) }
                )
            }

            if (puedeCrear) {
                item {
                    FilaDeOpcion(
                        texto = "Crear «$escrito»",
                        alTocar = { alCrear?.invoke(escrito) },
                        icono = true
                    )
                }
            }

            if (coincidencias.isEmpty() && !puedeCrear) {
                item {
                    Text(
                        text = if (opciones.isEmpty()) "Todavía no hay nada en la lista"
                        else "Nada coincide con lo escrito",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Medidas.chico)
                    )
                }
            }
        }
    }
}

/** Una fila tocable de la lista, con su alto mínimo de 48dp para no fallarle. */
@Composable
private fun FilaDeOpcion(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    icono: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Medidas.chico))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = alTocar)
            .heightIn(min = Medidas.objetivoTactil)
            .padding(horizontal = Medidas.medio),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Medidas.chico)
    ) {
        if (icono) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = if (icono) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

// --- Vistas previas ---

private val ingredientes = listOf(
    "Azúcar flor", "Harina", "Limón", "Manjar", "Plátano", "Nuez"
)

@Preview(showBackground = true, name = "Combo - lista completa")
@Composable
private fun ComboCompleto() {
    ReposteriaTheme {
        ComboBuscable(
            opciones = ingredientes,
            textoDe = { it },
            busqueda = "",
            alBuscar = {},
            alElegir = {},
            marcador = "Buscar ingrediente",
            alCrear = {}
        )
    }
}

@Preview(showBackground = true, name = "Combo - filtrando sin tildes")
@Composable
private fun ComboFiltrando() {
    ReposteriaTheme {
        ComboBuscable(
            opciones = ingredientes,
            textoDe = { it },
            busqueda = "platano",
            alBuscar = {},
            alElegir = {},
            marcador = "Buscar ingrediente",
            alCrear = {}
        )
    }
}

@Preview(showBackground = true, name = "Combo - ofrece crear lo que falta")
@Composable
private fun ComboOfreciendoCrear() {
    ReposteriaTheme {
        ComboBuscable(
            opciones = ingredientes,
            textoDe = { it },
            busqueda = "Ralladura de naranja",
            alBuscar = {},
            alElegir = {},
            marcador = "Buscar ingrediente",
            alCrear = {}
        )
    }
}
