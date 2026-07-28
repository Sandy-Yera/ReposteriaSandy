package com.sandyyera.reposteria.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val EsquemaClaro = lightColorScheme(
    primary = Caramelo,
    onPrimary = Color.White,
    secondary = Caramelo,
    onSecondary = Color.White,
    background = CremaFondo,
    onBackground = Chocolate,
    surface = CremaSuperficie,
    onSurface = Chocolate,
    error = FrambuesaEliminacion,
    onError = Color.White
)

private val EsquemaOscuro = darkColorScheme(
    primary = CarameloClaro,
    onPrimary = ChocolateOscuroFondo,
    secondary = CarameloClaro,
    onSecondary = ChocolateOscuroFondo,
    background = ChocolateOscuroFondo,
    onBackground = CremaTexto,
    surface = ChocolateOscuroSuperficie,
    onSurface = CremaTexto,
    error = FrambuesaEliminacionOscuro,
    onError = ChocolateOscuroFondo
)

/**
 * Los tres colores del historial, que no caben en el esquema de Material.
 *
 * Van acá y no como colores sueltos en cada pantalla para que cambien solos entre modo
 * claro y oscuro, igual que el resto.
 */
data class ColoresHistorial(
    val creacion: Color,
    val edicion: Color,
    val eliminacion: Color
)

val LocalColoresHistorial = staticCompositionLocalOf {
    ColoresHistorial(AzulCreacion, VerdeEdicion, FrambuesaEliminacion)
}

@Composable
fun ReposteriaTheme(
    oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit
) {
    val historial = if (oscuro) {
        ColoresHistorial(AzulCreacionOscuro, VerdeEdicionOscuro, FrambuesaEliminacionOscuro)
    } else {
        ColoresHistorial(AzulCreacion, VerdeEdicion, FrambuesaEliminacion)
    }

    CompositionLocalProvider(LocalColoresHistorial provides historial) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
            typography = TipografiaReposteria,
            content = contenido
        )
    }
}
