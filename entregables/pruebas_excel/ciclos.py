"""Detecta referencias circulares reales. Agrupa por columna las filas de datos (8..fin) de las hojas de registro."""
import re, sys
from openpyxl import load_workbook
from openpyxl.utils import column_index_from_string, get_column_letter as gl
wb = load_workbook(sys.argv[1])
names = {n: v.attr_text for n, v in wb.defined_names.items()}
GROUPED = {"Registro Auto": 8, "Registro Delivery": 8, "Calculos": 3}
FIN = {"Registro Auto": 10**6, "Registro Delivery": 10**6, "Calculos": 102}   # en Calculos solo se agrupan las filas de datos 3..102
def node(sheet, col, row):
    if sheet == "Calculos" and col in ("A", "B", "C"): return f"{sheet}!{col}{row}"   # filtros, interruptor y aviso: celdas sueltas
    if sheet in GROUPED and GROUPED[sheet] <= row <= FIN[sheet]: return f"{sheet}!{col}*"
    return f"{sheet}!{col}{row}"
tok = re.compile(r"(?:'[^']+'|[A-Za-z_]+)!\$?[A-Z]{1,3}\$?\d+(?::\$?[A-Z]{1,3}\$?\d+)?|\$?[A-Z]{1,3}\$?\d+(?::\$?[A-Z]{1,3}\$?\d+)?|[A-Za-z_][A-Za-z_0-9]*")
def refs(sheet, f):
    f = re.sub(r'"[^"]*"', '""', f)
    out = set()
    for t in tok.findall(f):
        if t in names: t = names[t]
        m = re.fullmatch(r"(?:'([^']+)'|([A-Za-z_]+))?!?\$?([A-Z]{1,3})\$?(\d+)(?::\$?([A-Z]{1,3})\$?(\d+))?", t)
        if not m: continue
        sh = m.group(1) or m.group(2) or sheet
        if sh not in wb.sheetnames: sh = sheet if not (m.group(1) or m.group(2)) else sh
        c1, r1 = m.group(3), int(m.group(4))
        c2, r2 = (m.group(5) or c1), int(m.group(6) or r1)
        for ci in range(column_index_from_string(c1), column_index_from_string(c2) + 1):
            col = gl(ci)
            for rr in range(r1, r2 + 1):
                out.add(node(sh, col, rr))
    return out
G = {}
for ws in wb.worksheets:
    for row in ws.iter_rows():
        for c in row:
            if isinstance(c.value, str) and c.value.startswith("="):
                n = node(ws.title, c.column_letter, c.row)
                G.setdefault(n, set()).update(refs(ws.title, c.value[1:]))
# quitar autorreferencia de columna agrupada causada por rangos que crecen hacia arriba (S$8:S{r} en Z es otra columna, ok)
sys.setrecursionlimit(100000)
color, cyc = {}, []
def dfs(u, path):
    color[u] = 1; path.append(u)
    for v in G.get(u, ()):
        if v == u: continue
        if color.get(v) == 1: cyc.append(path[path.index(v):] + [v]); continue
        if color.get(v) is None and v in G: dfs(v, path)
    path.pop(); color[u] = 2
for n in list(G):
    if color.get(n) is None: dfs(n, [])
self_loops = [n for n in G if n in G[n]]
print("nodos:", len(G), "ciclos:", len(cyc), "auto-referencias de columna:", self_loops[:10])
for c in cyc[:5]: print("  CICLO:", " → ".join(c))
