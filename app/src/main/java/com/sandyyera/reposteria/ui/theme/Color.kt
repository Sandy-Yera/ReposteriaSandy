package com.sandyyera.reposteria.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta cálida de repostería. Es fija y no toma los colores del sistema, para que la
// app se vea igual en cualquier celular y tenga identidad propia.

// --- Modo claro ---
val CremaFondo = Color(0xFFFFF8F0)
val CremaSuperficie = Color(0xFFFFFCF8)
val Caramelo = Color(0xFFB0762F)
val Chocolate = Color(0xFF3E2A22)

// --- Modo oscuro ---
val ChocolateOscuroFondo = Color(0xFF1C1512)
val ChocolateOscuroSuperficie = Color(0xFF2A211C)
val CarameloClaro = Color(0xFFE0A96D)
val CremaTexto = Color(0xFFF2E4D6)

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
