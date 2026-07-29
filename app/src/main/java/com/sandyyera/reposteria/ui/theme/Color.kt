package com.sandyyera.reposteria.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta cálida de repostería. Es fija y no toma los colores del sistema, para que la
// app se vea igual en cualquier celular y tenga identidad propia.
//
// Están **todos** los tonos que Material puede llegar a pedir, no solo los cuatro
// principales. Material tiene una paleta de unos 30 roles y usa los que uno no define con
// su valor de fábrica, que es gris violáceo: una tarjeta o un cuadro de diálogo se verían
// grises en medio de todo lo crema, sin que nada en el código lo explique.
//
// Los contrastes de esta paleta están medidos, no elegidos a ojo. El mínimo que exige la
// sección 12.6 es 4,5:1 entre un texto y el fondo sobre el que cae.

// --- Modo claro ---
val CremaFondo = Color(0xFFFFF8F0)
val CremaSuperficie = Color(0xFFFFFCF8)

// Los escalones de superficie, del más claro al más oscuro. Material los usa para separar
// visualmente lo que está "encima" de lo demás: tarjetas, cuadros de diálogo, menús.
val CremaNivel0 = Color(0xFFFFFFFF)
val CremaNivel1 = Color(0xFFFFFCF8)
val CremaNivel2 = Color(0xFFFBF3EA)
val CremaNivel3 = Color(0xFFF6EDE2)
val CremaNivel4 = Color(0xFFF1E7DA)   // el de las tarjetas

// El caramelo se oscureció de #B0762F a este tono. Con el original, un texto blanco encima
// —que es lo que lleva el botón principal— daba 3,83:1, por debajo del 4,5:1 que exige la
// propia sección 12.6. Es el mismo caramelo, un punto más tostado: ahora da 4,85:1.
val Caramelo = Color(0xFF996729)
val CarameloContenedor = Color(0xFFF5DFC0)
val CarameloContenedorTexto = Color(0xFF3A2408)

val Chocolate = Color(0xFF3E2A22)

// Texto tenue: subtítulos, "$1,55 por gramo", etiquetas de los campos.
// Es un color sólido y no chocolate con transparencia, así el contraste no cambia según
// sobre qué fondo caiga. Equivale a Chocolate al 75%; la tabla original decía 60%, pero
// al 60% daba 3,84:1 sobre la superficie y 4,30:1 sobre una tarjeta — las dos por debajo
// del mínimo. Al 75% da 6,03:1 y 5,05:1.
val ChocolateTenue = Color(0xFF6E5E58)

val Borde = Color(0xFF8C7A6E)
val BordeSuave = Color(0xFFE0D2C6)

// --- Modo oscuro ---
val ChocolateOscuroFondo = Color(0xFF1C1512)
val ChocolateOscuroSuperficie = Color(0xFF2A211C)

val ChocolateNivel0 = Color(0xFF171110)
val ChocolateNivel1 = Color(0xFF221A16)
val ChocolateNivel2 = Color(0xFF2A211C)
val ChocolateNivel3 = Color(0xFF352A24)
val ChocolateNivel4 = Color(0xFF40342C)   // el de las tarjetas

val CarameloClaro = Color(0xFFE0A96D)
val CarameloContenedorOscuro = Color(0xFF5C4322)
val CarameloContenedorOscuroTexto = Color(0xFFF5DFC0)

val CremaTexto = Color(0xFFF2E4D6)
val CremaTenue = Color(0xFFACA095)

val BordeOscuro = Color(0xFF9A8A7E)
val BordeSuaveOscuro = Color(0xFF4A3C34)

// --- Pasteles ---
// Rosa clásico de repostería, para las tarjetas de receta y los encabezados de sección.
//
// **Es superficie, nunca señal**, y esa distinción es la que evita el problema que
// advierte 12.6: el frambuesa de eliminar también es un rosa, y un rosa decorativo podría
// confundirse con un aviso de borrado. Acá no se confunden porque juegan en planos
// distintos — el pastel es un fondo grande y lavado, el frambuesa es texto o ícono
// saturado *encima* de él. Medido: el frambuesa mantiene 4,57:1 sobre este rosa, así que
// sigue saltando a la vista como lo que es.
//
// Regla para lo que venga: un pastel nuevo puede pintar un fondo; ningún pastel puede
// pintar un texto, un ícono ni un borde que signifique algo.
val RosaReceta = Color(0xFFF9DDE3)

// En oscuro un pastel no se puede aclarar -- brillaría. Se traduce al mismo tono, hundido:
// un rosa profundo de fondo con el texto crema de siempre encima.
val RosaRecetaOscuro = Color(0xFF4A3038)

// --- Los tres colores del historial de cambios ---
// La paleta de arriba es cálida a propósito: deja libres el azul, el verde y el rojo
// para que estos tres se distingan sin competir con nada.
val AzulCreacion = Color(0xFF2E6FA8)
val AzulCreacionOscuro = Color(0xFF7FB6E3)

val VerdeEdicion = Color(0xFF3E7D4F)
val VerdeEdicionOscuro = Color(0xFF87C99A)

// El frambuesa hace de color de eliminación y de error a la vez. Es deliberado: si fuera
// un acento decorativo aparte, un rojo de adorno se confundiría con un aviso de borrado.
val FrambuesaEliminacion = Color(0xFFB03A5B)
val FrambuesaEliminacionOscuro = Color(0xFFE8899F)

val FrambuesaContenedor = Color(0xFFFADCE2)
val FrambuesaContenedorTexto = Color(0xFF4A0C1C)
val FrambuesaContenedorOscuro = Color(0xFF6B1F33)
val FrambuesaContenedorOscuroTexto = Color(0xFFFFD9E0)
