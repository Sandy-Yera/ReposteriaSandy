#!/usr/bin/env python3
"""Mide el contraste de la paleta contra el mínimo que exige la sección 12.6.

La regla dice "contraste mínimo 4,5:1 entre texto y su fondo, en los dos modos". A ojo
no se puede saber si un color la cumple: el caramelo original (#B0762F) con texto blanco
encima parecía perfectamente legible y daba 3,83:1.

Se corre solo, sin instalar nada:

    python3 herramientas/contraste.py

Al agregar un color a `ui/theme/Color.kt` hay que agregar acá el par correspondiente.
Si algún par queda por debajo del mínimo, termina con código 1.
"""

import sys

# Mínimos de la WCAG 2.1, que es la norma que sigue Android.
TEXTO_NORMAL = 4.5
ELEMENTO_GRAFICO = 3.0   # bordes, íconos, texto grande


def luminancia(hexadecimal: str) -> float:
    """Luminancia relativa de un color, según la fórmula de la WCAG."""
    h = hexadecimal.lstrip("#")
    canales = []
    for i in (0, 2, 4):
        c = int(h[i:i + 2], 16) / 255
        canales.append(c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4)
    r, g, b = canales
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contraste(frente: str, fondo: str) -> float:
    a, b = luminancia(frente), luminancia(fondo)
    claro, oscuro = max(a, b), min(a, b)
    return (claro + 0.05) / (oscuro + 0.05)


# --- Modo claro ---
CREMA_FONDO = "#FFF8F0"
CREMA_SUPERFICIE = "#FFFCF8"
CREMA_NIVEL3 = "#F6EDE2"
CREMA_NIVEL4 = "#F1E7DA"        # tarjetas
CARAMELO = "#996729"
CARAMELO_CONTENEDOR = "#F5DFC0"
CARAMELO_CONTENEDOR_TEXTO = "#3A2408"
CHOCOLATE = "#3E2A22"
CHOCOLATE_TENUE = "#6E5E58"
BORDE = "#8C7A6E"
BLANCO = "#FFFFFF"
ROSA_RECETA = "#F9DDE3"        # pastel: solo pinta fondos, nunca texto ni íconos

# --- Modo oscuro ---
CHOCO_FONDO = "#1C1512"
CHOCO_SUPERFICIE = "#2A211C"
CHOCO_NIVEL4 = "#40342C"        # tarjetas
CARAMELO_CLARO = "#E0A96D"
CARAMELO_CONT_OSCURO = "#5C4322"
CARAMELO_CONT_OSCURO_TEXTO = "#F5DFC0"
CREMA_TEXTO = "#F2E4D6"
CREMA_TENUE = "#ACA095"
BORDE_OSCURO = "#9A8A7E"
ROSA_RECETA_OSCURO = "#4A3038"

# --- Historial de cambios ---
AZUL, AZUL_OSCURO = "#2E6FA8", "#7FB6E3"
VERDE, VERDE_OSCURO = "#3E7D4F", "#87C99A"
FRAMBUESA, FRAMBUESA_OSCURO = "#B03A5B", "#E8899F"
FRAMBUESA_CONTENEDOR = "#FADCE2"
FRAMBUESA_CONTENEDOR_TEXTO = "#4A0C1C"
FRAMBUESA_CONT_OSCURO = "#6B1F33"
FRAMBUESA_CONT_OSCURO_TEXTO = "#FFD9E0"

PARES = [
    # (modo, qué es, frente, fondo, mínimo)
    ("claro", "texto en el fondo", CHOCOLATE, CREMA_FONDO, TEXTO_NORMAL),
    ("claro", "texto en superficie", CHOCOLATE, CREMA_SUPERFICIE, TEXTO_NORMAL),
    ("claro", "texto en tarjeta", CHOCOLATE, CREMA_NIVEL4, TEXTO_NORMAL),
    ("claro", "texto tenue en el fondo", CHOCOLATE_TENUE, CREMA_FONDO, TEXTO_NORMAL),
    ("claro", "texto tenue en superficie", CHOCOLATE_TENUE, CREMA_SUPERFICIE, TEXTO_NORMAL),
    ("claro", "texto tenue en tarjeta", CHOCOLATE_TENUE, CREMA_NIVEL4, TEXTO_NORMAL),
    ("claro", "texto tenue en nivel 3", CHOCOLATE_TENUE, CREMA_NIVEL3, TEXTO_NORMAL),
    ("claro", "botón principal", BLANCO, CARAMELO, TEXTO_NORMAL),
    ("claro", "texto en contenedor caramelo", CARAMELO_CONTENEDOR_TEXTO, CARAMELO_CONTENEDOR, TEXTO_NORMAL),
    ("claro", "botón de eliminar", BLANCO, FRAMBUESA, TEXTO_NORMAL),
    ("claro", "aviso de error en superficie", FRAMBUESA, CREMA_SUPERFICIE, TEXTO_NORMAL),
    ("claro", "aviso de error en tarjeta", FRAMBUESA, CREMA_NIVEL4, TEXTO_NORMAL),
    ("claro", "texto en contenedor de error", FRAMBUESA_CONTENEDOR_TEXTO, FRAMBUESA_CONTENEDOR, TEXTO_NORMAL),
    ("claro", "aviso emergente (snackbar)", CREMA_FONDO, CHOCOLATE, TEXTO_NORMAL),
    ("claro", "historial: creación", AZUL, CREMA_SUPERFICIE, TEXTO_NORMAL),
    ("claro", "historial: edición", VERDE, CREMA_SUPERFICIE, TEXTO_NORMAL),
    ("claro", "historial: eliminación", FRAMBUESA, CREMA_SUPERFICIE, TEXTO_NORMAL),
    ("claro", "borde de los campos", BORDE, CREMA_SUPERFICIE, ELEMENTO_GRAFICO),
    # El pastel de las recetas. El tercero es el que importa: el ícono de eliminar cae
    # encima del rosa, y tiene que seguir leyéndose como aviso y no como adorno.
    ("claro", "texto en tarjeta de receta", CHOCOLATE, ROSA_RECETA, TEXTO_NORMAL),
    ("claro", "texto tenue en receta", CHOCOLATE_TENUE, ROSA_RECETA, TEXTO_NORMAL),
    ("claro", "eliminar sobre el rosa", FRAMBUESA, ROSA_RECETA, TEXTO_NORMAL),

    ("oscuro", "texto en el fondo", CREMA_TEXTO, CHOCO_FONDO, TEXTO_NORMAL),
    ("oscuro", "texto en superficie", CREMA_TEXTO, CHOCO_SUPERFICIE, TEXTO_NORMAL),
    ("oscuro", "texto en tarjeta", CREMA_TEXTO, CHOCO_NIVEL4, TEXTO_NORMAL),
    ("oscuro", "texto tenue en el fondo", CREMA_TENUE, CHOCO_FONDO, TEXTO_NORMAL),
    ("oscuro", "texto tenue en superficie", CREMA_TENUE, CHOCO_SUPERFICIE, TEXTO_NORMAL),
    ("oscuro", "texto tenue en tarjeta", CREMA_TENUE, CHOCO_NIVEL4, TEXTO_NORMAL),
    ("oscuro", "botón principal", CHOCO_FONDO, CARAMELO_CLARO, TEXTO_NORMAL),
    ("oscuro", "texto en contenedor caramelo", CARAMELO_CONT_OSCURO_TEXTO, CARAMELO_CONT_OSCURO, TEXTO_NORMAL),
    ("oscuro", "botón de eliminar", CHOCO_FONDO, FRAMBUESA_OSCURO, TEXTO_NORMAL),
    ("oscuro", "aviso de error en superficie", FRAMBUESA_OSCURO, CHOCO_SUPERFICIE, TEXTO_NORMAL),
    ("oscuro", "aviso de error en tarjeta", FRAMBUESA_OSCURO, CHOCO_NIVEL4, TEXTO_NORMAL),
    ("oscuro", "texto en contenedor de error", FRAMBUESA_CONT_OSCURO_TEXTO, FRAMBUESA_CONT_OSCURO, TEXTO_NORMAL),
    ("oscuro", "aviso emergente (snackbar)", CHOCO_FONDO, CREMA_TEXTO, TEXTO_NORMAL),
    ("oscuro", "historial: creación", AZUL_OSCURO, CHOCO_SUPERFICIE, TEXTO_NORMAL),
    ("oscuro", "historial: edición", VERDE_OSCURO, CHOCO_SUPERFICIE, TEXTO_NORMAL),
    ("oscuro", "historial: eliminación", FRAMBUESA_OSCURO, CHOCO_SUPERFICIE, TEXTO_NORMAL),
    ("oscuro", "borde de los campos", BORDE_OSCURO, CHOCO_SUPERFICIE, ELEMENTO_GRAFICO),
    ("oscuro", "texto en tarjeta de receta", CREMA_TEXTO, ROSA_RECETA_OSCURO, TEXTO_NORMAL),
    ("oscuro", "texto tenue en receta", CREMA_TENUE, ROSA_RECETA_OSCURO, TEXTO_NORMAL),
    ("oscuro", "eliminar sobre el rosa", FRAMBUESA_OSCURO, ROSA_RECETA_OSCURO, TEXTO_NORMAL),
]


def main() -> int:
    bajos = 0
    modo_anterior = None
    for modo, descripcion, frente, fondo, minimo in PARES:
        if modo != modo_anterior:
            print(f"\n--- Modo {modo} ---")
            modo_anterior = modo
        razon = contraste(frente, fondo)
        cumple = razon >= minimo
        if not cumple:
            bajos += 1
        marca = "  " if cumple else "<-"
        print(f"  {'ok ' if cumple else 'BAJO'} {descripcion:32} {razon:5.2f}:1  "
              f"(mínimo {minimo}) {marca}")

    print(f"\n{len(PARES)} pares medidos, {bajos} por debajo del mínimo.")
    return 1 if bajos else 0


if __name__ == "__main__":
    sys.exit(main())
