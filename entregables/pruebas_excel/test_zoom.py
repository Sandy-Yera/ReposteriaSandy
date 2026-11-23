"""¿Se ve todo bien con cualquier zoom (50 %–200 %) y con espacio de seguridad?
Mide cada texto visible YA CALCULADO con la fuente real (Liberation Sans = medidas de Arial; Carlito = Calibri) y lo
compara con el espacio de su celda o celdas combinadas, en cada zoom. Usa las mismas medidas que ajuste.py.
Uso: python test_zoom.py archivo_recalculado.xlsx [etiqueta]"""
import sys, re, datetime as dt
from openpyxl import load_workbook
from ajuste import necesidad, ancho_col_px, alto_fila_pt, mostrar, MARGEN

ARCH = sys.argv[1]; ETQ = sys.argv[2] if len(sys.argv) > 2 else ARCH
ESCALA_TEST = 0.75          # con "reducir hasta ajustar", la letra no puede quedar bajo el 75 % (sigue legible)
wb = load_workbook(ARCH, data_only=True)
problemas, revisadas = [], 0
for ws in wb.worksheets:
    if ws.sheet_state == "hidden": continue
    merges = {(m.min_row, m.min_col): m for m in ws.merged_cells.ranges}
    cubiertas = {(r, c) for m in ws.merged_cells.ranges for r in range(m.min_row, m.max_row + 1)
                 for c in range(m.min_col, m.max_col + 1) if (r, c) != (m.min_row, m.min_col)}
    for row in ws.iter_rows():
        for cell in row:
            if (cell.row, cell.column) in cubiertas or cell.value is None: continue
            txt = mostrar(cell.value, cell.number_format)
            if txt.strip() == "" or re.match(r"https?://", txt): continue      # enlace pegado: se abre con "Abrir foto ↗"
            if alto_fila_pt(ws, cell.row) == 0 or ancho_col_px(ws, cell.column) == 0: continue
            revisadas += 1
            m = merges.get((cell.row, cell.column))
            c0, c1 = (m.min_col, m.max_col) if m else (cell.column, cell.column)
            r0, r1 = (m.min_row, m.max_row) if m else (cell.row, cell.row)
            w = sum(ancho_col_px(ws, c) for c in range(c0, c1 + 1)); h = sum(alto_fila_pt(ws, r) for r in range(r0, r1 + 1))
            al = cell.alignment; ind = al.indent or 0
            es_num = not isinstance(cell.value, str)
            nec = necesidad(txt, cell.font, w, h, ind, bool(al.wrap_text))
            if nec["cabe"]: continue
            motivo = None
            if al.wrap_text:
                motivo = ("una palabra no cabe ni sola en la línea" if not nec["palabra_cabe"]
                          else f"necesita {nec['lineas']} líneas ≈ {nec['alto_nec']:.0f} pt (+8 %) y la fila tiene {h:.0f} pt")
            else:
                alto_ok = nec["alto_nec"] <= h * MARGEN
                if not alto_ok: motivo = f"letra de {cell.font.sz:g} pt en fila de {h:.0f} pt"
                elif al.shrink_to_fit:
                    if nec["escala"] < ESCALA_TEST: motivo = f"se achicaría al {nec['escala']:.0%}: poco legible"
                elif not es_num and not m and al.horizontal in (None, "general", "left"):
                    extra, c = 0, cell.column + 1
                    while ws.cell(cell.row, c).value is None and (cell.row, c) not in cubiertas and c - cell.column < 40:
                        extra += ancho_col_px(ws, c); c += 1
                    if not (extra and necesidad(txt, cell.font, w + extra, h)["cabe"]): motivo = f"no cabe (cabría al {nec['escala']:.0%})"
                else:
                    motivo = f"no cabe (cabría al {nec['escala']:.0%})" + (" → Excel mostraría ###" if es_num else "")
            if motivo: problemas.append(f"{ws.title}!{cell.coordinate}: {motivo} → {txt[:60]!r}")
print(f"[{ETQ}] celdas con texto revisadas: {revisadas} · zooms 50–200 % · margen de seguridad {round((1-MARGEN)*100)} %")
print(f"[{ETQ}] PROBLEMAS: {len(problemas)}")
for p in problemas: print("  ✗", p)
