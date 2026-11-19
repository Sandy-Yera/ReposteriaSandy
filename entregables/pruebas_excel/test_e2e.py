"""Extremo a extremo: uso real de un mes completo, horario pegado, reportes, filtros, y archivo lleno al máximo."""
import sys, time, random, datetime as dt
from openpyxl import load_workbook
from harness import build_case, scan_errors, secs, fecha, recalc

TEMPLATE = sys.argv[1]
out = []
def check(n, c, det=""): out.append((n, bool(c), det))

NAMES = ["Ana", "Bruno", "Carla", "Diego", "Elena", "Felipe", "Gabriela", "Hugo", "Iris", "Javier"]
rnd = random.Random(7)
auto, deliv = [], []
for k in range(30):
    f = dt.date(2026, 11, 1) + dt.timedelta(days=k)
    t1, t2 = rnd.sample(NAMES, 3), rnd.sample(NAMES, 3)
    a1, s1 = rnd.randint(80, 150), rnd.randint(150, 300)
    a2, s2 = rnd.randint(60, 120), rnd.randint(150, 300)
    sd = round((a1 * s1 + a2 * s2) / (a1 + a2))
    auto += [(f, "AM", *t1, a1, s1 // 60, s1 % 60), (f, "PM", *t2, a1 + a2, sd // 60, sd % 60)]
    deliv += [(f, "AM", f"https://drive.google.com/am{k}", rnd.choice(["Sí", "Sí", "No"])), (f, "PM", f"https://drive.google.com/pm{k}", rnd.choice(["Sí", "Sí", "No"]))]

horario = [[None, None] + [f"{d}-nov" for d in range(10, 17)], ["Nombre", "Horas", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"]]
horario += [[f"Trabajador {i}", 40] + [rnd.choice(["11:00-19:00", "19:00-02:00", "Libre"]) for _ in range(7)] for i in range(1, 33)]  # 32 personas (>30)


def extra(wb):
    ho, rp = wb["Horarios"], wb["Reportes"]
    s = 13 + 41  # marco de la semana 2
    ho[f"C{s+1}"] = dt.date(2026, 11, 10); ho[f"F{s+1}"] = dt.date(2026, 11, 16)
    for i, row in enumerate(horario):
        for j, v in enumerate(row): ho.cell(s + 3 + i, 2 + j, v)
    rp["B7"], rp["C7"], rp["D7"], rp["E7"] = dt.date(2026, 11, 1), dt.date(2026, 11, 15), "Quincenal", "https://drive.google.com/rep1"


res, wb = build_case(TEMPLATE, "e2e.xlsx", auto, deliv, NAMES, {"L7": 3}, extra)
check("Mes completo: recalcula sin errores", res.get("total_errors") == 0, res)
check("Mes completo: ninguna celda con error", not scan_errors(wb), scan_errors(wb)[:5])
p, ho = wb["Panel"], wb["Horarios"]
check("Panel cuenta los 60 turnos", p["H16"].value == 60, p["H16"].value)
check("Panel cuenta los 60 turnos de delivery", p["K16"].value == 60, p["K16"].value)
check("Ranking por estación con datos (mín. 3)", str(p["B34"].value).startswith("1. ") and str(p["F34"].value).startswith("1. "), (p["B34"].value, p["F34"].value))
check("Mejores días con 5 filas", all(p[f"C{52+k}"].value not in (None, "", "Sin datos") for k in range(5)), [p[f"C{52+k}"].value for k in range(5)])
tAM, tPM, tT = secs(p["F24"].value), secs(p["H24"].value), secs(p["J24"].value)
autosAM, autosPM = p["F23"].value, p["H23"].value
check("Total del día = promedio ponderado de AM y PM", abs(tT - (tAM * autosAM + tPM * autosPM) / (autosAM + autosPM)) < 0.01, (tAM, tPM, tT))
s = 13 + 41
check("Horario: 32 trabajadores pegados en el marco 2", ho.cell(s + 3 + 33, 2).value == "Trabajador 32", ho.cell(s + 36, 2).value)
check("Horario: el marco 1 sigue intacto", ho["B16"].value == "◤ Pega aquí" and ho["B14"].value == "Semana del", ho["B16"].value)
check("Horario: el marco 3 sigue intacto", ho[f"B{13+82}"].value == "SEMANA 3", ho[f"B{13+82}"].value)
check("Reportes: botón Abrir creado", wb["Reportes"]["F7"].value == "Abrir reporte ↗", wb["Reportes"]["F7"].value)
check("Delivery: botón Abrir creado", wb["Registro Delivery"]["E8"].value == "Abrir foto ↗", wb["Registro Delivery"]["E8"].value)

# filtros en el mismo mes
for desc, flt, fn in [
    ("Filtro un día (15-nov)", {"B7": dt.date(2026, 11, 15), "D7": dt.date(2026, 11, 15)}, lambda p: p["H16"].value == 2 and p["J27"].value == 2 and p["C52"].value is not None),
    ("Filtro semana (10 al 16-nov)", {"B7": dt.date(2026, 11, 10), "D7": dt.date(2026, 11, 16), "L7": 1}, lambda p: p["H16"].value == 14 and str(p["C43"].value) != "Sin datos suficientes"),
    ("Filtro AM", {"F7": "AM"}, lambda p: p["H16"].value == 30),
    ("Filtro persona Ana", {"J7": "Ana"}, lambda p: 0 < p["H16"].value < 60),
]:
    res, wb2 = build_case(TEMPLATE, "e2e_f.xlsx", auto, deliv, NAMES, flt, extra)
    check(desc, fn(wb2["Panel"]) and not scan_errors(wb2), f"turnos={wb2['Panel']['H16'].value}")

# archivo lleno: 1.100 filas (≈18 meses) y tiempo de recálculo
full = []
for k in range(550):
    f = dt.date(2025, 6, 1) + dt.timedelta(days=k)
    t1, t2 = rnd.sample(NAMES, 3), rnd.sample(NAMES, 3)
    full += [(f, "AM", *t1, 100, 3, rnd.randint(0, 59)), (f, "PM", *t2, 190, 3, rnd.randint(0, 59))]
t0 = time.time()
res, wb3 = build_case(TEMPLATE, "e2e_full.xlsx", full, [(r[0], r[1], "https://d/x", "Sí") for r in full], NAMES, {})
el = time.time() - t0
check(f"Archivo lleno (1.100 filas) recalcula sin errores ({el:.0f}s incluyendo guardar)", res.get("total_errors") == 0 and not scan_errors(wb3), res)
check("Archivo lleno: Panel cuenta 1.100 turnos", wb3["Panel"]["H16"].value == 1100, wb3["Panel"]["H16"].value)

print(f"EXTREMO A EXTREMO: {sum(o[1] for o in out)}/{len(out)} correctos")
for n, ok, det in out: print(("  ✓ " if ok else "  ✗ ") + n + ("" if ok else f"  →  {det}"))
