"""Modo aprendizaje: los ejemplos se muestran/ocultan con el interruptor y NUNCA se mezclan con los datos reales.
Uso: python test_aprendizaje.py plantilla.xlsx"""
import sys, datetime as dt
from openpyxl import load_workbook
from harness import build_case, scan_errors

TEMPLATE = sys.argv[1]
out = []
def check(n, c, det=""): out.append((n, bool(c), det))
NAMES = ["Ana", "Bruno", "Carla", "Diego"]
d = dt.date
# datos reales EN LAS MISMAS FECHAS que los ejemplos (marzo 2021) para provocar choques
reales = [(d(2021, 3, 1), "AM", "Ana", "Bruno", "Carla", 100, 3, 0), (d(2021, 3, 1), "PM", "Diego", "Bruno", "Carla", 180, 3, 30),
          (d(2021, 3, 2), "AM", "Ana", "Bruno", "Diego", 90, 2, 50), (d(2021, 3, 2), "PM", "Carla", "Ana", "Diego", 170, 3, 10),
          (d(2021, 3, 3), "AM", "Bruno", "Ana", "Carla", 95, 4, 10), (d(2021, 3, 3), "PM", "Ana", "Diego", "Bruno", 160, 3, 50)]
deliv = [(d(2021, 3, 1), "AM", "https://drive.google.com/r1", "Sí"), (d(2021, 3, 1), "PM", "https://drive.google.com/r2", "No")]

res_no, wno = build_case(TEMPLATE, "apr_no.xlsx", reales, deliv, NAMES, {"L7": 1}, modo_aprendizaje="No")
res_si, wsi = build_case(TEMPLATE, "apr_si.xlsx", reales, deliv, NAMES, {"L7": 1}, modo_aprendizaje="Sí")
pn, ps, an, as_ = wno["Panel"], wsi["Panel"], wno["Registro Auto"], wsi["Registro Auto"]
check("Sin errores con Modo aprendizaje en No y en Sí", not scan_errors(wno) and not scan_errors(wsi), (scan_errors(wno)[:3], scan_errors(wsi)[:3]))
check("No: el Panel cuenta solo los 6 turnos reales", pn["H16"].value == 6, pn["H16"].value)
check("Sí: el Panel cuenta solo los 56 turnos de ejemplo válidos", ps["H16"].value == 56, ps["H16"].value)
check("Sí: aviso de Modo aprendizaje arriba del Panel", "MODO APRENDIZAJE" in str(ps["B4"].value), ps["B4"].value)
check("No: sin aviso de aprendizaje", "MODO APRENDIZAJE" not in str(pn["B4"].value or ""), pn["B4"].value)
check("Filas reales con la misma fecha que los ejemplos NO se marcan repetidas", all(an[f"M{r}"].value in ("Cumple", "Muy bueno", "No cumple (alerta)", "No cumple") for r in range(8, 14)),
      [an[f"M{r}"].value for r in range(8, 14)])
check("Las filas reales calculan igual en Sí y en No", all(an[f"{c}{r}"].value == as_[f"{c}{r}"].value for r in range(8, 14) for c in "JKLMNOP"))
check("No: la zona de ejemplos queda vacía", all(an[f"{c}{r}"].value in (None, "") for r in range(1038, 1100) for c in "BCDGMQ"))
check("Sí: la zona de ejemplos muestra datos y explicaciones", str(as_["Q1039"].value).startswith("EJEMPLO") and as_["M1039"].value not in (None, ""), (as_["Q1039"].value, as_["M1039"].value))
check("Sí: ranking con nombres de ejemplo, no reales", str(ps["C35"].value).endswith("(ej.)"), ps["C35"].value)
check("No: ranking con nombres reales", ps["C35"].value != pn["C35"].value and not str(pn["C35"].value).endswith("(ej.)"), pn["C35"].value)
check("Sí: el ejemplo de Ana sale 'Más rápido que lo normal'", ps["C35"].value == "Ana (ej.)" and ps["J35"].value == "Más rápido que lo normal", (ps["C35"].value, ps["J35"].value))
check("Sí: hay un ejemplo de PM 'dudoso'", any("dudoso" in str(as_[f"M{r}"].value) for r in range(1039, 1107)))
check("Sí: hay ejemplos de error (tiempo no válido / fila a medias)", {"Revisar: tiempo no válido", "Falta autos o tiempo"} <= {as_[f"M{r}"].value for r in range(1039, 1107)})
check("Sí: los errores de ejemplo NO se cuentan como filas por revisar", "fila(s) de Registro Auto" not in str(ps["B4"].value), ps["B4"].value)
check("Sí: cada tarjeta explica qué significa", ps["B13"].value == "Cuántos autos se atendieron en esas fechas.", ps["B13"].value)
check("Sí: el Panel propone probar filtros", str(ps["B8"].value).startswith("PRUEBA ESTO"), ps["B8"].value)
check("Sí: el ranking explica cómo leerse", str(ps["B40"].value).startswith("Así se lee"), ps["B40"].value)
check("Sí: guía bajo los encabezados del registro", as_["B5"].value == "Fecha del turno. Ej: 16-11-2026" and an["B5"].value in (None, ""), (as_["B5"].value, an["B5"].value))
check("Horarios: en Sí hay un aviso con enlace al horario de ejemplo; en No desaparece",
      str(wsi["Horarios"]["B4"].value).startswith("▶ MODO APRENDIZAJE") and wno["Horarios"]["B4"].value in (None, "") and
      str(wsi["Horarios"]["V18"].value or "") == "Ana (ej.)" , (wsi["Horarios"]["B4"].value, wno["Horarios"]["B4"].value))
check("Delivery: No cuenta solo lo real (2) y Sí solo ejemplos", pn["K16"].value == 2 and ps["K16"].value >= 50, (pn["K16"].value, ps["K16"].value))
# filtro persona con nombre de ejemplo
_, wp = build_case(TEMPLATE, "apr_p.xlsx", reales, deliv, NAMES, {"L7": 1, "J7": "Ana (ej.)"}, modo_aprendizaje="Sí")
check("Sí: filtrar por Persona = Ana (ej.) funciona", 0 < wp["Panel"]["H16"].value < 56, wp["Panel"]["H16"].value)
# protección: zona de ejemplos bloqueada, filas reales libres
wf = load_workbook("apr_si.xlsx")
a = wf["Registro Auto"]
check("Las celdas de ejemplo están bloqueadas (no se pueden pisar)", all(a[f"{c}1040"].protection.locked for c in "BCDGHIQ"))
check("Las filas reales siguen libres para escribir", not any(a[f"{c}1037"].protection.locked for c in "BCDGHIQ"))
check("El interruptor de Inicio se puede cambiar", not wf["Inicio"]["C6"].protection.locked)
print(f"MODO APRENDIZAJE: {sum(o[1] for o in out)}/{len(out)}")
for n, ok, det in out: print(("  ✓ " if ok else "  ✗ ") + n + ("" if ok else f"  →  {det}"))
