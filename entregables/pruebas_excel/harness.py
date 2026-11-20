"""Banco de pruebas del Excel: escribe datos con openpyxl, recalcula con LibreOffice y lee resultados."""
import glob, json, subprocess, datetime as dt, os
from openpyxl import load_workbook

SCRIPTS = glob.glob("/root/.claude/skills/synced/*/xlsx/scripts")[0]
R0 = 8
ERRS = ("#DIV/0!", "#VALUE!", "#REF!", "#NAME?", "#N/A", "#NUM!", "#NULL!", "Err:", "#¡")


def recalc(path):
    out = subprocess.run(["python", f"{SCRIPTS}/recalc.py", path, "600"], capture_output=True, text=True)
    return json.loads(out.stdout)


def secs(v):
    if isinstance(v, dt.timedelta): return v.total_seconds()
    if isinstance(v, dt.time): return v.hour * 3600 + v.minute * 60 + v.second + v.microsecond / 1e6
    if isinstance(v, (int, float)) and not isinstance(v, bool): return v * 86400
    return None


def fecha(v):
    if isinstance(v, dt.datetime): return v.date()
    return v


def build_case(template, out, auto_rows=(), deliv_rows=(), personal=None, panel=None, extra=None, modo_aprendizaje="No"):
    """auto_rows: tuplas (B..I [,Q]) ; deliv_rows: (B,C,D,F[,G]); panel: dict celda->valor."""
    wb = load_workbook(template)
    a, d, p, pe = wb["Registro Auto"], wb["Registro Delivery"], wb["Panel"], wb["Personal"]
    if personal is not None:
        for i in range(100):
            pe[f"B{8+i}"] = personal[i] if i < len(personal) else None
            pe[f"C{8+i}"] = None
    for i, row in enumerate(auto_rows):
        if row is None: continue
        for c, v in zip("BCDEFGHIQ", row): a[f"{c}{R0+i}"] = v
    for i, row in enumerate(deliv_rows):
        if row is None: continue
        for c, v in zip("BCDFH", row): d[f"{c}{R0+i}"] = v
    for k, v in (panel or {}).items(): p[k] = v
    wb["Inicio"]["C6"] = modo_aprendizaje          # las pruebas trabajan con datos reales salvo que se pida lo contrario
    assert len(auto_rows) <= 1030 and len(deliv_rows) <= 1030, "las filas 1038-1107 están reservadas para los ejemplos"
    if extra: extra(wb)
    wb.save(out)
    res = recalc(out)
    return res, load_workbook(out, data_only=True)


def scan_errors(wb, sheets=("Registro Auto", "Registro Delivery", "Panel", "Calculos", "Reportes", "Horarios", "Personal")):
    bad = []
    for s in sheets:
        for row in wb[s].iter_rows():
            for c in row:
                if isinstance(c.value, str) and c.value.startswith(ERRS):
                    bad.append(f"{s}!{c.coordinate}={c.value}")
    return bad
