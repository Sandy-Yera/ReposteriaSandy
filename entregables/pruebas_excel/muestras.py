"""Guarda, para cada celda con fórmula, el texto más ancho que mostró en los escenarios recalculados (peor caso).
En los registros (fila ≥ 8) se guarda además el más ancho por columna. Uso: python muestras.py salida.json esc1.xlsx esc2.xlsx …"""
import sys, json
from openpyxl import load_workbook
from ajuste import mostrar, ancho_px
out = {}
def anota(k, txt, font):
    if k not in out or ancho_px(txt, font, 1) > ancho_px(out[k], font, 1): out[k] = txt
for arch in sys.argv[2:]:
    wf, wv = load_workbook(arch), load_workbook(arch, data_only=True)
    for ws in wf.worksheets:
        if ws.sheet_state == "hidden": continue
        wsv = wv[ws.title]
        for row in ws.iter_rows():
            for c in row:
                if not (isinstance(c.value, str) and c.value.startswith("=")): continue
                txt = mostrar(wsv[c.coordinate].value, c.number_format)
                if not txt.strip() or txt.startswith("http"): continue
                anota(f"{ws.title}!{c.coordinate}", txt, c.font)
                if ws.title.startswith("Registro") and c.row >= 8 and c.row != 1038: anota(f"{ws.title}!col:{c.column_letter}", txt, c.font)
json.dump(out, open(sys.argv[1], "w"), ensure_ascii=False, indent=0)
print("muestras:", len(out))
