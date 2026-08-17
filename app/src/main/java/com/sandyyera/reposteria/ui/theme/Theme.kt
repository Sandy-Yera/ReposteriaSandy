package com.sandyyera.reposteria.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Los dos esquemas definen **todos** los roles que Material puede pedir, no solo los
// principales. Los que se dejan sin definir no quedan vacíos: toman el valor de fábrica,
// que es gris violáceo, y aparecen sin aviso en el primer componente que los use. Así pasó
// con las tarjetas de la lista de ingredientes, que usan `surfaceContainerHighest`.
private val EsquemaClaro = lightColorScheme(
    primary = Caramelo,
    onPrimary = Color.White,
    primaryContainer = CarameloContenedor,
    onPrimaryContainer = CarameloContenedorTexto,
    inversePrimary = CarameloClaro,

    secondary = Caramelo,
    onSecondary = Color.White,
    secondaryContainer = CarameloContenedor,
    onSecondaryContainer = CarameloContenedorTexto,

    // El rol "terciario" de Material es justo para un acento que acompaña sin competir:
    // acá vive el rosa pastel de las recetas. `tertiary` a secas queda en caramelo a
    // propósito — ese rol se usa para textos e íconos, y ahí un rosa sí competiría con el
    // frambuesa de eliminar. El pastel solo pinta contenedores.
    tertiary = Caramelo,
    onTertiary = Color.White,
    tertiaryContainer = RosaReceta,
    onTertiaryContainer = Chocolate,

    background = CremaFondo,
    onBackground = Chocolate,
    surface = CremaSuperficie,
    onSurface = Chocolate,

    // Material usa este color para todo lo secundario: subtítulos, etiquetas de los campos
    // de texto, íconos de apoyo. Definirlo acá evita que cada pantalla invente su propio
    // "chocolate con transparencia" y que el contraste dependa de sobre qué fondo caiga.
    surfaceVariant = CremaNivel3,
    onSurfaceVariant = ChocolateTenue,

    surfaceContainerLowest = CremaNivel0,
    surfaceContainerLow = CremaNivel1,
    surfaceContainer = CremaNivel2,
    surfaceContainerHigh = CremaNivel3,
    surfaceContainerHighest = CremaNivel4,

    outline = Borde,
    outlineVariant = BordeSuave,

    // El fondo de los avisos emergentes (Snackbar), que Material dibuja invertido.
    inverseSurface = Chocolate,
    inverseOnSurface = CremaFondo,

    error = FrambuesaEliminacion,
    onError = Color.White,
    errorContainer = FrambuesaContenedor,
    onErrorContainer = FrambuesaContenedorTexto
)

private val EsquemaOscuro = darkColorScheme(
    primary = CarameloClaro,
    onPrimary = ChocolateOscuroFondo,
    primaryContainer = CarameloContenedorOscuro,
    onPrimaryContainer = CarameloContenedorOscuroTexto,
    inversePrimary = Caramelo,

    secondary = CarameloClaro,
    onSecondary = ChocolateOscuroFondo,
    secondaryContainer = CarameloContenedorOscuro,
    onSecondaryContainer = CarameloContenedorOscuroTexto,

    tertiary = CarameloClaro,
    onTertiary = ChocolateOscuroFondo,
    tertiaryContainer = RosaRecetaOscuro,
    onTertiaryContainer = CremaTexto,

    background = ChocolateOscuroFondo,
    onBackground = CremaTexto,
    surface = ChocolateOscuroSuperficie,
    onSurface = CremaTexto,

    surfaceVariant = ChocolateNivel3,
    onSurfaceVariant = CremaTenue,

    surfaceContainerLowest = ChocolateNivel0,
    surfaceContainerLow = ChocolateNivel1,
    surfaceContainer = ChocolateNivel2,
    surfaceContainerHigh = ChocolateNivel3,
    surfaceContainerHighest = ChocolateNivel4,

    outline = BordeOscuro,
    outlineVariant = BordeSuaveOscuro,

    inverseSurface = CremaTexto,
    inverseOnSurface = ChocolateOscuroFondo,

    error = FrambuesaEliminacionOscuro,
    onError = ChocolateOscuroFondo,
    errorContainer = FrambuesaContenedorOscuro,
    onErrorContainer = FrambuesaContenedorOscuroTexto
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
