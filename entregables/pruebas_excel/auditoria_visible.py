"""Auditoría de visibilidad: ¿hay algo importante fuera de la primera pantalla sin un enlace visible que lleve a eso?
Y: ¿hay hipervínculos fijos en celdas con fórmula (que podrían mostrar su destino cuando la fórmula da "")?
Uso: python auditoria_visible.py archivo.xlsx"""
import sys, re
from openpyxl import load_workbook
from openpyxl.utils import get_column_letter, column_index_from_string

ANCHO_PANTALLA = 1200      # píxeles útiles en una pantalla de portátil a 100 % (descontando encabezados de fila)
FILAS_PANTALLA = 30        # filas visibles al abrir (aprox.)
wb = load_workbook(sys.argv[1])
problemas, revisados = [], 0

ANCHOS = {}
def anchos(ws):
    """Ancho real de cada columna (el archivo agrupa columnas iguales en un solo rango min..max)."""
    if ws.title not in ANCHOS:
        d = {}
        for dim in list(ws.column_dimensions.values()):
            for c in range(dim.min or 1, (dim.max or dim.min or 1) + 1):
                d[c] = (dim.width or 8.43, bool(dim.hidden))
        ANCHOS[ws.title] = d
    return ANCHOS[ws.title]
def x_col(ws, col):
    """Píxel donde empieza la columna (aprox. Excel: 7 px por unidad de ancho + 5)."""
    d = anchos(ws); x = 0
    for c in range(1, column_index_from_string(col)):
        w, oculta = d.get(c, (8.43, False))
        if not oculta: x += 7 * w + 5
    return x

def visible(ws, ref):
    m = re.match(r"([A-Z]+)(\d+)", ref)
    return x_col(ws, m.group(1)) < ANCHO_PANTALLA and int(m.group(2)) <= FILAS_PANTALLA

# destinos a los que lleva algún enlace visible (fijo o HYPERLINK en fórmula)
destinos_visibles = {}
for ws in wb.worksheets:
    if ws.sheet_state == "hidden": continue
    for row in ws.iter_rows():
        for c in row:
            dest = None
            if c.hyperlink is not None and c.hyperlink.location: dest = c.hyperlink.location
            elif isinstance(c.value, str) and "HYPERLINK(\"#" in c.value:
                dest = re.search(r'HYPERLINK\("#([^"]+)"', c.value).group(1)
            if dest and visible(ws, c.coordinate):
                hoja, celda = dest.replace("'", "").split("!")
                destinos_visibles.setdefault(hoja, []).append((celda, f"{ws.title}!{c.coordinate}"))

def tiene_enlace_cerca(hoja, ref):
    """¿Algún enlace visible lleva a una pantalla donde este elemento se ve? (al saltar, se ve desde el destino hacia la
    derecha unos 1.200 px y hacia abajo unas 30 filas). Para la zona de ejemplos basta con llegar a su fila separadora."""
    ws = wb[hoja]
    m = re.match(r"([A-Z]+)(\d+)", ref); col, fila = m.group(1), int(m.group(2))
    for celda, origen in destinos_visibles.get(hoja, []):
        m2 = re.match(r"([A-Z]+)(\d+)", celda); c2, f2 = m2.group(1), int(m2.group(2))
        dx = x_col(ws, col) - x_col(ws, c2)
        zona_ejemplos = f2 >= 1000 and fila >= f2          # tabla de ejemplos: se llega a la banda y se sigue bajando
        if zona_ejemplos and dx >= 0: return origen       # dentro de la tabla de ejemplos se sigue a la derecha (la banda lo indica)
        if 0 <= dx < ANCHO_PANTALLA and 0 <= fila - f2 <= FILAS_PANTALLA: return origen
    return None

for ws in wb.worksheets:
    if ws.sheet_state == "hidden": continue
    # 1) enlaces fijos sobre fórmulas
    for row in ws.iter_rows():
        for c in row:
            if c.hyperlink is not None and isinstance(c.value, str) and c.value.startswith("="):
                problemas.append(f"{ws.title}!{c.coordinate}: hipervínculo fijo sobre una fórmula (puede mostrar su destino si la fórmula da vacío)")
    # 2) elementos importantes: enlaces de navegación, avisos y contenido del Modo aprendizaje
    importantes = []
    for row in ws.iter_rows():
        for c in row:
            v = c.value
            if c.hyperlink is not None or (isinstance(v, str) and ("fAprende=1" in v or "HYPERLINK(\"#" in v or "Calculos!$B$9" in v)):
                importantes.append(c)
    # agrupar el contenido de aprendizaje por bloques (basta con que el inicio de cada bloque sea alcanzable)
    vistos = set()
    for c in importantes:
        revisados += 1
        ref = c.coordinate
        if visible(ws, ref): continue
        clave = (ws.title, c.column // 8, c.row // 60)
        if clave in vistos: continue
        vistos.add(clave)
        es_semana = ws.title == "Horarios" and c.hyperlink is not None and str(c.value).startswith("Semana ")
        enlace = tiene_enlace_cerca(ws.title, ref)
        # el contenido que sigue al flujo natural (bajar por el Panel o las Instrucciones, o el índice de semanas) no necesita enlace
        flujo_natural = ws.title in ("Panel", "Instrucciones") and x_col(ws, c.column_letter) < ANCHO_PANTALLA
        # guía corta bajo un encabezado de tabla: viaja con su columna (filas de encabezado inmovilizadas)
        guia_de_columna = ws.title.startswith("Registro") and c.row == 5 and ws.cell(7, c.column).value not in (None, "")
        flujo_natural = flujo_natural or guia_de_columna
        if not enlace and not flujo_natural and not es_semana:
            tipo = "enlace de navegación" if c.hyperlink is not None and not str(c.value).startswith("=") else "contenido"
            problemas.append(f"{ws.title}!{ref}: {tipo} fuera de la pantalla inicial (x≈{int(x_col(ws, c.column_letter))} px, fila {c.row}) "
                             f"sin enlace visible que lleve ahí → {str(c.value)[:70]!r}")
print(f"Elementos revisados: {revisados}")
print(f"Problemas: {len(problemas)}")
for p in problemas: print("  ✗", p)
