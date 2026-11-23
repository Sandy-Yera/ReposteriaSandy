"""Medidas de texto y ajuste automático de visibilidad (zoom 50 %–200 % con espacio de seguridad).
Lo usan build.py (para ajustar el archivo) y test_zoom.py (para comprobarlo), así ambos miden igual."""
import math, re
from openpyxl.styles import Alignment
from openpyxl.utils import get_column_letter
from PIL import ImageFont

ZOOMS = [z / 100 for z in range(50, 205, 5)]
MARGEN = 0.92            # el texto usa como máximo el 92 % del espacio libre (8 % de seguridad)
PAD = 6                  # relleno interno de la celda en píxeles (≈3 por lado)
ESCALA_MIN = 0.80        # "reducir hasta ajustar" solo si la letra queda al menos al 80 % (sigue legible)
ALTO_LINEA = 1.275       # alto natural de una línea de Arial en Excel: 12,75 pt para letra de 10
_F = {("Arial", False, False): "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
      ("Arial", True, False): "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
      ("Arial", False, True): "/usr/share/fonts/truetype/liberation/LiberationSans-Italic.ttf",
      ("Arial", True, True): "/usr/share/fonts/truetype/liberation/LiberationSans-BoldItalic.ttf",
      ("Calibri", False, False): "/usr/share/fonts/truetype/crosextra/Carlito-Regular.ttf",
      ("Calibri", True, False): "/usr/share/fonts/truetype/crosextra/Carlito-Bold.ttf",
      ("Calibri", False, True): "/usr/share/fonts/truetype/crosextra/Carlito-Italic.ttf",
      ("Calibri", True, True): "/usr/share/fonts/truetype/crosextra/Carlito-BoldItalic.ttf"}
_cache = {}


def ancho_px(texto, font, z):
    """Ancho en píxeles del texto con la fuente de la celda al zoom z (tamaño de letra redondeado hacia arriba: peor caso)."""
    px = math.ceil((font.sz or 10) * 96 / 72 * z)
    k = (font.name if font.name in ("Arial", "Calibri") else "Arial", bool(font.b), bool(font.i), px)
    if k not in _cache: _cache[k] = ImageFont.truetype(_F[k[:3]], px)
    return _cache[k].getlength(texto)


def ancho_col_px(ws, c):
    """Ancho de la columna en píxeles a zoom 100 % (0 si está oculta). Considera columnas agrupadas min..max."""
    d = ws.column_dimensions.get(get_column_letter(c))
    if d is None or (d.width is None and not d.hidden):
        for dim in ws.column_dimensions.values():
            if dim.min and dim.max and dim.min <= c <= dim.max: d = dim; break
    if d is not None and d.hidden: return 0
    w = d.width if d is not None and d.width else 8.43
    return int(w * 7 + 5)


def alto_fila_pt(ws, r):
    d = ws.row_dimensions.get(r)
    if d is not None and d.hidden: return 0
    return d.height if d is not None and d.height else 15.0


def lineas(texto, font, disp, z):
    """Cuántas líneas ocupa el texto con ajuste por palabras en `disp` píxeles, y el ancho de la palabra más larga."""
    n, palabra_max = 0, 0
    for par in str(texto).split("\n"):
        actual = ""
        for pal in par.split(" "):
            palabra_max = max(palabra_max, ancho_px(pal, font, z))
            prueba = (actual + " " + pal).strip()
            if not actual or ancho_px(prueba, font, z) <= disp * MARGEN: actual = prueba
            else: n += 1; actual = pal
        n += 1
    return n, palabra_max


_memo = {}
def necesidad(texto, font, ancho100, alto_pt, indent=0, wrap=False):
    k = (texto, font.name, font.sz, bool(font.b), bool(font.i), ancho100, alto_pt, indent, wrap)
    if k not in _memo: _memo[k] = _necesidad(texto, font, ancho100, alto_pt, indent, wrap)
    return dict(_memo[k])
def _necesidad(texto, font, ancho100, alto_pt, indent=0, wrap=False):
    """Revisa el texto en todos los zooms. Retorna dict con: cabe (bool), escala (mínima para caber en una línea),
    lineas (máx. con ajuste), alto_nec (pt), palabra_cabe (bool)."""
    sz = font.sz or 10
    peor = dict(cabe=True, escala=1.0, lineas=1, alto_nec=sz * ALTO_LINEA, palabra_cabe=True)
    for z in ZOOMS:
        disp = ancho100 * z - PAD - indent * 9 * z
        if wrap:
            n, pm = lineas(texto, font, disp, z)
            if pm > disp * MARGEN: peor["palabra_cabe"] = False; peor["cabe"] = False
            peor["lineas"] = max(peor["lineas"], n)
        else:
            larga = max(ancho_px(l, font, z) for l in str(texto).split("\n"))
            if larga > disp * MARGEN:
                peor["cabe"] = False; peor["escala"] = min(peor["escala"], disp * MARGEN / larga)
    peor["alto_nec"] = peor["lineas"] * sz * ALTO_LINEA
    if peor["alto_nec"] > alto_pt * MARGEN: peor["cabe"] = False
    return peor


import datetime as dt
MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic"]
DIAS = ["lun", "mar", "mié", "jue", "vie", "sáb", "dom"]
def mostrar(v, fmt):
    """Texto que Excel mostraría (aproximado, el peor caso razonable)."""
    if v is None: return ""
    if isinstance(v, bool): return "VERDADERO" if v else "FALSO"
    if isinstance(v, dt.timedelta): v = v.total_seconds() / 86400
    if isinstance(v, dt.time): v = (v.hour * 3600 + v.minute * 60 + v.second + v.microsecond / 1e6) / 86400
    if isinstance(v, (dt.datetime, dt.date)):
        d = v if isinstance(v, dt.date) else v.date()
        s = f"{d.day:02d}-{MESES[d.month-1]}-{d.year % 100:02d}"
        return (DIAS[d.weekday()] + " " + s) if fmt.startswith("ddd") else s
    if isinstance(v, (int, float)):
        if "[m]:ss" in fmt:
            t = round(v * 86400); return f"{t // 60}:{t % 60:02d}"
        if fmt.startswith("dd-") or fmt.startswith("ddd"):
            return mostrar(dt.date(1899, 12, 30) + dt.timedelta(days=int(v)), fmt)
        if fmt.endswith("%"): return f"{v * 100:.0f}%"
        if fmt.startswith("+0.0"): return f"{v:+.1f} s"
        if fmt.startswith('"±"'): return f"±{v:.1f} s"
        if fmt in ("0", "#,##0"): return f"{v:,.0f}".replace(",", ".")
        return f"{v:.10g}" if abs(v - round(v)) > 1e-9 else str(int(round(v)))
    return str(v)


LIT = re.compile(r'^=IF\(fAprende=1,"((?:[^"]|"")*)",""\)$')
def texto_fijo(v):
    """Texto conocido de la celda: el valor si no es fórmula, o el texto de un ejemplo IF(fAprende=1,"…","")."""
    if not isinstance(v, str): return None
    if not v.startswith("="): return v
    m = LIT.match(v)
    return m.group(1).replace('""', '"') if m else None


def con_alineacion(cell, **k):
    a = cell.alignment
    d = dict(horizontal=a.horizontal, vertical=a.vertical, wrap_text=a.wrap_text, shrink_to_fit=a.shrink_to_fit, indent=a.indent,
             text_rotation=a.text_rotation)
    d.update(k); cell.alignment = Alignment(**d)


def ajustar(wb, saltar=("Calculos",), muestras=None):
    """Pasada final: deja cada texto visible en cualquier zoom con espacio de seguridad.
    - Fórmulas y números (texto desconocido): 'reducir hasta ajustar' (si el texto cabe, no cambia nada).
    - Textos conocidos que no caben: se achican un poco (≥80 %) o pasan a varias líneas, y la fila crece.
    - Si una palabra no cabe ni sola, se ensancha la columna. Retorna un resumen de cambios."""
    cambios = dict(reducir=0, varias_lineas=0, filas_altas=0, columnas_anchas=0)
    for ws in wb.worksheets:
        if ws.title in saltar or ws.sheet_state == "hidden": continue
        merges = {(m.min_row, m.min_col): m for m in ws.merged_cells.ranges}
        cubiertas = {(r, c) for m in ws.merged_cells.ranges for r in range(m.min_row, m.max_row + 1)
                     for c in range(m.min_col, m.max_col + 1) if (r, c) != (m.min_row, m.min_col)}
        celdas = [c for row in ws.iter_rows() for c in row if c.value is not None and (c.row, c.column) not in cubiertas]
        for _ in range(2):          # segunda vuelta: tras ensanchar columnas se recalculan las alturas
            for cell in celdas:
                if alto_fila_pt(ws, cell.row) == 0 or ancho_col_px(ws, cell.column) == 0: continue
                m = merges.get((cell.row, cell.column))
                c0, c1 = (m.min_col, m.max_col) if m else (cell.column, cell.column)
                r0, r1 = (m.min_row, m.max_row) if m else (cell.row, cell.row)
                w = sum(ancho_col_px(ws, c) for c in range(c0, c1 + 1)); h = sum(alto_fila_pt(ws, r) for r in range(r0, r1 + 1))
                al = cell.alignment; txt = texto_fijo(cell.value)
                es_formula = isinstance(cell.value, str) and cell.value.startswith("=") and txt is None
                if txt is None and muestras:      # fórmula: el texto más largo que mostró en los escenarios de prueba
                    col = muestras.get(f"{ws.title}!col:{cell.column_letter}") if ws.title.startswith("Registro") and cell.row >= 8 else None
                    txt = col or muestras.get(f"{ws.title}!{cell.coordinate}")      # en los registros manda el peor de la columna
                if txt is None or re.match(r"https?://", txt or ""):
                    if not al.wrap_text and not al.shrink_to_fit:
                        con_alineacion(cell, shrink_to_fit=True); cambios["reducir"] += 1
                    continue
                if txt.strip() == "": continue
                nec = necesidad(txt, cell.font, w, h, al.indent or 0, bool(al.wrap_text))
                if nec["cabe"]:
                    if es_formula and not al.wrap_text and not al.shrink_to_fit:      # el dato real podría ser más largo
                        con_alineacion(cell, shrink_to_fit=True); cambios["reducir"] += 1
                    continue
                if not al.wrap_text:
                    # ¿desborda a celdas vacías de verdad a la derecha? (solo texto alineado a la izquierda, sin combinar)
                    if not m and al.horizontal in (None, "general", "left"):
                        extra, c = 0, cell.column + 1
                        while ws.cell(cell.row, c).value is None and (cell.row, c) not in cubiertas and c - cell.column < 40:
                            extra += ancho_col_px(ws, c); c += 1
                        if extra and necesidad(txt, cell.font, w + extra, h)["cabe"]: continue
                    if nec["escala"] >= ESCALA_MIN and nec["alto_nec"] <= h * MARGEN:
                        con_alineacion(cell, shrink_to_fit=True); cambios["reducir"] += 1; continue
                    con_alineacion(cell, wrap_text=True, shrink_to_fit=False, vertical=al.vertical or "center"); cambios["varias_lineas"] += 1
                    nec = necesidad(txt, cell.font, w, h, al.indent or 0, True)
                if not nec["palabra_cabe"] and not m:
                    # ensanchar la columna hasta que la palabra más larga quepa en el peor zoom
                    letra = get_column_letter(cell.column)
                    while not necesidad(txt, cell.font, ancho_col_px(ws, cell.column), 9999, al.indent or 0, True)["palabra_cabe"]:
                        ws.column_dimensions[letra].width = (ws.column_dimensions[letra].width or 8.43) + 0.5
                    cambios["columnas_anchas"] += 1
                    w = ancho_col_px(ws, cell.column); nec = necesidad(txt, cell.font, w, h, al.indent or 0, True)
                falta = nec["alto_nec"] / MARGEN - h
                if falta > 0.01:
                    ws.row_dimensions[r1].height = round(alto_fila_pt(ws, r1) + falta + 0.5)
                    cambios["filas_altas"] += 1
    return cambios
