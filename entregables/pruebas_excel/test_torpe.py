"""Usuario torpe (abuelo) + tester destructivo. Parte 1: datos absurdos escritos/pegados en las celdas."""
import sys, datetime as dt
from openpyxl import load_workbook
from harness import build_case, secs

TEMPLATE = sys.argv[1]
NAMES = ["Ana", "Bruno", "Carla", "Diego", "Elena", "Felipe"]
T = ("Ana", "Bruno", "Carla")
d = lambda n, m=10: dt.date(2026, m, n)
out = []
def check(n, c, det=""): out.append((n, bool(c), det))

ERRS = ("#DIV/0!", "#VALUE!", "#REF!", "#NAME?", "#N/A", "#NUM!", "#NULL!", "Err:")
def formula_errors(path_formulas, wb_vals, sheets=("Registro Auto", "Registro Delivery", "Panel", "Calculos", "Reportes")):
    """Errores solo en celdas con fórmula NUESTRA (si el usuario escribe =1/0 en su celda, ese error es suyo)."""
    wf = load_workbook(path_formulas)
    bad = []
    for s in sheets:
        for row in wf[s].iter_rows():
            for c in row:
                if isinstance(c.value, str) and c.value.startswith("=") and c.value not in USER_FORMULAS:
                    v = wb_vals[s][c.coordinate].value
                    if isinstance(v, str) and v.startswith(ERRS): bad.append(f"{s}!{c.coordinate}={v}")
    return bad
USER_FORMULAS = {"=1/0", "=A1/0", '="AM"&1/0'}

# ---------------------------------------------------------------- Registro Auto
casos = [  # (descripción, fila B..I, comprobación sobre (M, J, L))
    ("Escribe =1/0 en Fecha", ("=1/0", "AM", *T, 100, 3, 0), lambda m, j, l: m.startswith("Revisar")),
    ("Escribe =1/0 en Autos", (d(2), "AM", *T, "=1/0", 3, 0), lambda m, j, l: m.startswith("Revisar")),
    ("Escribe =1/0 en Min", (d(3), "AM", *T, 100, "=1/0", 0), lambda m, j, l: m.startswith("Revisar")),
    ("Escribe =1/0 en Turno", (d(4), "=1/0", *T, 100, 3, 0), lambda m, j, l: m.startswith("Revisar")),
    ("Escribe =1/0 en Cajero", (d(5), "AM", "=1/0", "Bruno", "Carla", 100, 3, 0), lambda m, j, l: m == "Cumple"),
    ("Fecha escrita como número 16 (Excel: 16-ene-1900)", (16, "AM", *T, 100, 3, 0), lambda m, j, l: m.startswith("Revisar")),
    ("Año mal escrito 2162", (dt.date(2162, 10, 6), "AM", *T, 100, 3, 0), lambda m, j, l: m.startswith("Revisar")),
    ("Escribe 325 en Min queriendo decir 3:25", (d(7), "AM", *T, 100, 325, None), lambda m, j, l: m.startswith("Revisar")),
    ("Escribe 3.25 en Min (Excel chileno lo vuelve fecha 3-mar)", (d(8), "AM", *T, 100, 46084, None), lambda m, j, l: m.startswith("Revisar")),
    ("Autos imposibles 99999", (d(9), "AM", *T, 99999, 3, 0), lambda m, j, l: m.startswith("Revisar")),
    ("Turno 'a.m.'", (d(10), "a.m.", *T, 100, 3, 0), lambda m, j, l: m == "Cumple"),
    ("Turno 'Mañana' (no se adivina, se pide AM/PM)", (d(11), "Mañana", *T, 100, 3, 0), lambda m, j, l: m.startswith("Revisar")),
    ("Todo escrito en la celda Fecha '16-11 AM 120 autos 3:25'", ("16-11 AM 120 autos 3:25", None, None, None, None, None, None, None), lambda m, j, l: m.startswith("Revisar")),
    ("Total de segundos en Seg (Min vacío, Seg 205)", (d(13), "AM", *T, 100, None, 205), lambda m, j, l: secs(j) is not None and abs(secs(j) - 205) < 1),
    ("Solo un espacio en Autos", (d(14), "AM", *T, " ", 3, 0), lambda m, j, l: m.startswith("Revisar") or m.startswith("Falta")),
    ("Nombre con emoji y tilde 'Ána 🍔'", (d(15), "AM", "Ána 🍔", "Bruno", "Carla", 100, 3, 0), lambda m, j, l: m == "Cumple"),
]
rows = [c[1] for c in casos]
k0 = len(rows)
rows += [
    # cierre pasada la medianoche: la fila PM del 20 se anotó con fecha 21
    (d(20), "AM", *T, 100, 3, 0), (d(21), "PM", *T, 200, 3, 10),
    (d(21), "AM", *T, 90, 3, 0), (d(21), "PM", *T, 190, 3, 5),
    # observaciones larguísimas
    (d(22), "AM", *T, 100, 3, 0, "x" * 5000),
]
deliv = [
    (d(1), "AM", "=1/0", "Sí"),                                   # 0 enlace con fórmula error
    (d(1), "PM", "https://drive.google.com/a", "Sí, pero faltó uno"),  # 1 respuesta inventada
    (d(2), "AM", "https://drive.google.com/b", "Sí"),             # 2
    (d(2), "AM", "https://drive.google.com/b", "Sí"),             # 3 fila repetida
    (d(3), "AM", "Sí", None),                                     # 4 columnas corridas
    (d(3), "PM", "https://drive.google.com/" + "x" * 300, "SÍ"),  # 5 enlace larguísimo + SÍ mayúscula
    (d(4), "AM", None, "No"),                                     # 6 sin enlace (cuenta igual)
    (16, "PM", "https://drive.google.com/c", "No"),               # 7 fecha 1900
]

def extra(wb):
    pe = wb["Personal"]
    pe["B14"] = "Ana"          # nombre repetido en la lista
    pe["B15"] = "=1/0"         # fórmula error en la lista
    pe["B16"] = "Ána 🍔"
    wb["Reportes"]["E7"] = "=1/0"

res, wb = build_case(TEMPLATE, "torpe_1.xlsx", rows, deliv, NAMES, {"L7": 1}, extra)
a, p, de = wb["Registro Auto"], wb["Panel"], wb["Registro Delivery"]
fe = formula_errors("torpe_1.xlsx", wb)
check("Ninguna fórmula del archivo muestra #ERROR", not fe, fe[:8])
for i, (desc, _, fn) in enumerate(casos):
    r = 8 + i
    m, j, l = a[f"M{r}"].value or "", a[f"J{r}"].value, a[f"L{r}"].value
    try: ok = fn(m, j, l)
    except Exception: ok = False
    check(desc, ok, f"Nivel={m!r} J={secs(j)}")
r = 8 + k0
check("PM anotado con la fecha del día siguiente: queda marcado (fila repetida)", (a[f"M{r+1}"].value or "").startswith("Revisar") and (a[f"M{r+3}"].value or "").startswith("Revisar"),
      (a[f"M{r+1}"].value, a[f"M{r+3}"].value))
check("Observaciones de 5.000 caracteres no molestan", a[f"M{r+4}"].value == "Cumple", a[f"M{r+4}"].value)
# Delivery: estado visible y conteo correcto
est = lambda rr: str(de[f"G{8+rr}"].value or "")
check("Delivery: hay una columna Estado que explica cada fila", de["G7"].value == "Estado", de["G7"].value)
check("Delivery: respuesta inventada se marca", est(1).startswith("Revisar"), est(1))
check("Delivery: fila repetida se marca", est(2).startswith("Revisar") and est(3).startswith("Revisar"), (est(2), est(3)))
check("Delivery: columnas corridas se marcan", est(4).startswith("Revisar") or est(4).startswith("Falta"), est(4))
check("Delivery: fecha 1900 se marca", est(7).startswith("Revisar"), est(7))
check("Delivery: sin enlace cuenta pero avisa", est(6).startswith("OK") and "enlace" in est(6), est(6))
# válidas: fila 0 (enlace error, Sí), 5 (SÍ), 6 (No) -> 3 turnos, 2 Sí
check("Delivery: cuenta solo las filas válidas (3) y 2 Sí", p["K16"].value == 3 and abs((p["K11"].value or 0) - 2 / 3) < 1e-9, (p["K16"].value, p["K11"].value))
# Personal
names_rank = [p[f"C{35+k}"].value for k in range(5)]
check("Personal: nombre repetido no aparece dos veces en el ranking", sum(1 for x in names_rank if x == "Ana") <= 1, names_rank)
check("Personal: celda con =1/0 no rompe el ranking", names_rank[0] not in (None, "", "Sin datos suficientes"), p["C35"].value)
check("Personal: nombre con emoji funciona en el ranking", any("Ána 🍔" in str(x) for x in names_rank), names_rank)
check("Panel avisa nombres repetidos en Personal", "repetid" in str(p["B4"].value or ""), p["B4"].value)

# ---------------------------------------------------------------- Panel con filtros absurdos
base = [(d(1), "AM", *T, 100, 3, 0), (d(1), "PM", "Diego", "Elena", "Felipe", 200, 3, 30)]
for desc, flt, fn in [
    ("Filtro Desde con =1/0", {"B7": "=1/0"}, lambda p: p["H16"].value == 2),
    ("Filtro Turno con =1/0", {"F7": "=1/0"}, lambda p: p["H16"].value == 2),
    ("Filtro Persona con =1/0", {"J7": "=1/0"}, lambda p: p["H16"].value == 2),
    ("Filtro Desde 'hoy'", {"B7": "hoy"}, lambda p: p["H16"].value == 2 and "fecha" in str(p["B4"].value or "")),
    ("Mín. turnos 1000", {"L7": 1000}, lambda p: str(p["C35"].value).startswith("Sin")),
    ("Desde en 1900 (número 5)", {"B7": 5}, lambda p: p["H16"].value == 2),
]:
    res, wb2 = build_case(TEMPLATE, "torpe_p.xlsx", base, [], NAMES, flt)
    fe = formula_errors("torpe_p.xlsx", wb2)
    try: ok = fn(wb2["Panel"]) and not fe
    except Exception: ok = False
    check(f"Panel: {desc}", ok, f"turnos={wb2['Panel']['H16'].value} aviso={wb2['Panel']['B4'].value!r} errs={fe[:3]}")

print(f"TORPE / DESTRUCTIVO (datos): {sum(o[1] for o in out)}/{len(out)}")
for n, ok, det in out: print(("  ✓ " if ok else "  ✗ ") + n + ("" if ok else f"  →  {det}"))
