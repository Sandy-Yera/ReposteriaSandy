package com.sandyyera.reposteria.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografía de la app.
 *
 * Todo en `sp` y no en `dp`: así los textos crecen si el celular tiene configurado un
 * tamaño de letra más grande por accesibilidad. La app se usa en la cocina, muchas veces
 * de reojo y con las manos ocupadas, así que respetar esa preferencia importa.
 *
 * Se parte de la tipografía por defecto de Material y solo se ajusta lo que hace falta,
 * en vez de redefinir los quince estilos: menos código y menos cosas que se desalineen.
 */
val TipografiaReposteria = Typography(
    // Títulos de pantalla ("Ingredientes", "Recetas")
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    // Nombre de una receta o un ingrediente dentro de su tarjeta
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    // Texto corriente
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // Datos secundarios: "$12.400 · 8 trozos"
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
)
