"""Errores comunes a propósito: el Excel no debe mostrar errores, debe marcar lo malo y no contaminar los indicadores."""
import sys, datetime as dt
from harness import build_case, scan_errors, secs, fecha

TEMPLATE = sys.argv[1]
NAMES = ["Ana", "Bruno", "Carla", "Diego", "Elena", "Felipe"]
d = lambda n: dt.date(2026, 11, n)
T = ("Ana", "Bruno", "Carla")
res_list = []


def check(name, cond, detail=""):
    res_list.append((name, bool(cond), detail))


# (descripción, fila B..I, comprobación sobre la fila calculada)
casos = [
    ("Fecha escrita como texto '01/11/2026'", ("01/11/2026", "AM", *T, 100, 3, 0), lambda m, l, j: m.startswith("Revisar")),
    ("Fecha con hora (02-nov 14:30) junto a su AM", (dt.datetime(2026, 11, 2, 14, 30), "PM", *T, 200, 3, 0), lambda m, l, j: secs(l) is not None and abs(secs(l) - 180) < 1),
    ("Turno en minúscula 'am'", (d(3), "am", *T, 100, 3, 0), lambda m, l, j: m == "Cumple"),
    ("Turno con espacio 'PM '", (d(4), "PM ", *T, 100, 3, 0), lambda m, l, j: m == "Cumple"),
    ("Turno inventado 'Tarde'", (d(5), "Tarde", *T, 100, 3, 0), lambda m, l, j: m.startswith("Revisar")),
    ("Autos como texto '120'", (d(6), "AM", *T, "120", 3, 0), lambda m, l, j: m == "Cumple"),
    ("Autos en palabras 'ciento veinte'", (d(7), "AM", *T, "ciento veinte", 3, 0), lambda m, l, j: m.startswith("Revisar")),
    ("Autos negativos -5", (d(8), "AM", *T, -5, 3, 0), lambda m, l, j: m.startswith("Revisar")),
    ("Escribe 3:25 en la casilla Min (Excel lo guarda como hora)", (d(9), "AM", *T, 100, 0.142361111111111, None), lambda m, l, j: secs(j) is not None and abs(secs(j) - 205) < 1),
    ("Min como texto '3'", (d(10), "AM", *T, 100, "3", 10), lambda m, l, j: secs(j) is not None and abs(secs(j) - 190) < 1),
    ("Segundos mayores a 59 (2 min 75 s)", (d(11), "AM", *T, 100, 2, 75), lambda m, l, j: secs(j) is not None and abs(secs(j) - 195) < 1),
    ("Minutos con decimal 3,5", (d(12), "AM", *T, 100, 3.5, None), lambda m, l, j: m.startswith("Revisar")),
    ("Tiempo 0:00", (d(13), "AM", *T, 100, 0, 0), lambda m, l, j: m.startswith("Revisar")),
    ("Segundos en texto 'treinta'", (d(14), "AM", *T, 100, 3, "treinta"), lambda m, l, j: m.startswith("Revisar")),
    ("Datos sin fecha", (None, "AM", *T, 100, 3, 0), lambda m, l, j: m.startswith("Revisar")),
    ("Solo se llenó el nombre del cajero", (d(15), None, "Ana", None, None, None, None, None), lambda m, l, j: m.startswith("Revisar") or m.startswith("Falta")),
    ("Falta el tiempo", (d(16), "AM", *T, 100, None, None), lambda m, l, j: m.startswith("Falta")),
]
rows = [c[1] for c in casos]
# casos de dos filas (al final)
extra_idx = len(rows)
rows += [
    (d(2), "AM", *T, 100, 3, 0),                       # AM del caso "fecha con hora"
    (d(20), "AM", *T, 150, 3, 0), (d(20), "PM", *T, 120, 3, 30),     # PM autos < AM
    (d(21), "AM", *T, 100, 3, 0), (d(21), "AM", *T, 100, 3, 0),      # fila repetida
    (d(22), "PM", "ana", "Bruno ", "Carla", 180, 3, 10),             # solo PM + nombres mal escritos
    None,                                                            # fila vacía en medio
    (d(23), "AM", "Ana", None, "Carla", 90, 2, 0),                   # trío incompleto
    (d(24), "AM", *T, 0, 3, 0),                                      # 0 autos
]
deliv = [(d(1), "AM", "https://drive.google.com/x", "si"), (d(1), "PM", "foto en el celular", "SI"),
         (d(2), "AM", "https://drive.google.com/y", "Si"), (d(2), "PM", None, "no"), (d(3), "AM", "x", "x"),
         ("02/11/2026", "PM", "https://d/z", "Sí"), (d(4), "am", "https://d/w", "No ")]

res, wb = build_case(TEMPLATE, "err_rows.xlsx", rows, deliv, NAMES, {"L7": 1})
a, p, de = wb["Registro Auto"], wb["Panel"], wb["Registro Delivery"]
check("Recalculo sin errores", res.get("total_errors") == 0, res)
errs = scan_errors(wb)
check("Ninguna celda muestra #ERROR", not errs, errs[:6])
for i, (desc, _, fn) in enumerate(casos):
    r = 8 + i
    m, l, j = a[f"M{r}"].value or "", a[f"L{r}"].value, a[f"J{r}"].value
    try: ok = fn(m, l, j)
    except Exception as e: ok = False
    check(desc, ok, f"Nivel={m!r} Tiempo={secs(j)} Real={secs(l)}")
r = 8 + extra_idx
m = lambda rr: a[f"M{rr}"].value or ""
check("PM con autos menores que AM se marca", m(r + 2).startswith("Revisar"), m(r + 2))
check("Fila repetida se marca (ambas)", m(r + 3).startswith("Revisar") and m(r + 4).startswith("Revisar"), (m(r + 3), m(r + 4)))
check("Solo PM se marca '(solo PM)'", "(solo PM)" in (a[f"P{r+5}"].value or ""), a[f"P{r+5}"].value)
check("Fila vacía en medio no molesta", (a[f"M{r+6}"].value or "") == "", a[f"M{r+6}"].value)
check("Trío incompleto: el turno cuenta igual", m(r + 7) == "Muy bueno", m(r + 7))
check("0 autos no rompe nada", not str(a[f"L{r+8}"].value).startswith("#"), a[f"L{r+8}"].value)
# indicadores: solo cuentan las filas buenas
buenas = [a[f"N{rr}"].value for rr in range(8, 8 + len(rows)) if a[f"N{rr}"].value in ("Sí", "No")]
check("Panel cuenta solo filas válidas", p["H16"].value == len(buenas), f"panel={p['H16'].value} válidas={len(buenas)}")
# delivery: si/SI/Si/no se aceptan; 'x' no cuenta; fecha texto no cuenta
check("Delivery acepta si / SI / Si / no / 'No '", p["K16"].value == 5, f"turnos delivery contados={p['K16'].value} (esperado 5)")
check("Delivery % correcto (3 Sí de 5)", isinstance(p["K11"].value, float) and abs(p["K11"].value - 0.6) < 1e-9, p["K11"].value)
check("Enlace que no es URL no da error", not str(de["E9"].value).startswith("#"), de["E9"].value)

# ---------- filtros del Panel mal llenados ----------
base_rows = [(d(1), "AM", *T, 100, 3, 0), (d(1), "PM", "Diego", "Elena", "Felipe", 200, 3, 30), (d(2), "AM", *T, 100, 5, 0)]
panel_cases = [
    ("Desde escrito como texto", {"B7": "01/11"}, lambda p: p["H16"].value == 3),
    ("Desde posterior a Hasta (se invierten)", {"B7": d(2), "D7": d(1)}, lambda p: p["H16"].value == 3),
    ("Persona que no existe", {"J7": "Zoe"}, lambda p: p["H16"].value == 0 and "lista" in str(p["B4"].value or "")),
    ("Mín. turnos = 0", {"L7": 0}, lambda p: str(p["B34"].value).startswith("Sin")),
    ("Mín. turnos en texto 'tres'", {"L7": "tres"}, lambda p: str(p["B34"].value).startswith("Sin")),
    ("Turno en minúscula 'pm'", {"F7": "pm"}, lambda p: p["H16"].value == 1),
    ("Canal en minúscula 'auto'", {"H7": "auto"}, lambda p: p["K11"].value == "—" and p["B11"].value == 300),
    ("Todos los filtros borrados", {"F7": None, "H7": None, "J7": None, "L7": None}, lambda p: p["H16"].value == 3),
    ("Persona con espacios '  Ana'", {"J7": "  Ana"}, lambda p: p["H16"].value == 2),
]
for desc, flt, fn in panel_cases:
    res, wb = build_case(TEMPLATE, "err_panel.xlsx", base_rows, [], NAMES, flt)
    errs = scan_errors(wb)
    try: ok = fn(wb["Panel"]) and not errs and res.get("total_errors") == 0
    except Exception as e: ok = False
    check(f"Panel: {desc}", ok, f"turnos={wb['Panel']['H16'].value} aviso={wb['Panel']['B4'].value!r} errs={errs[:3]}")

ok_n = sum(1 for _, ok, _ in res_list if ok)
print(f"ERRORES PROVOCADOS: {ok_n}/{len(res_list)} resistidos")
for name, ok, det in res_list:
    print(("  ✓ " if ok else "  ✗ ") + name + ("" if ok else f"  →  {det}"))
