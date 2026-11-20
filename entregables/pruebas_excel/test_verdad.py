"""Prueba con VERDAD CONOCIDA: se simula auto por auto con el talento real de cada persona fijado de antemano.
Se mide si el Excel (1) recupera el tiempo real de PM dentro del error de redondeo del sistema, (2) ordena a las
personas según su talento real, (3) no premia a quien solo trabaja turnos fáciles, (4) da márgenes honestos.
Uso: python test_verdad.py plantilla.xlsx"""
import sys, random, math, statistics, datetime as dt
from harness import build_case, secs

TEMPLATE = sys.argv[1]
SEED = int(sys.argv[2]) if len(sys.argv) > 2 else 5
rnd = random.Random(SEED)
NAMES = [f"P{i:02d}" for i in range(1, 31)]
CAJ, BOA, COR = NAMES[:10], NAMES[10:20], NAMES[20:30]
talento = {n: rnd.gauss(0, 12) for n in NAMES}          # segundos por auto: negativo = más rápido
talento["P01"] = +12.0   # cajero LENTO que solo trabaja PM de lunes a jueves (turnos fáciles)
talento["P02"] = -12.0   # cajero RÁPIDO que solo trabaja AM de fin de semana (turnos difíciles)
def base(turno, wd):     # dificultad del contexto (segundos)
    return (200 if turno == "AM" else 165) + (35 if wd >= 5 else 0)

auto_rows, verdad_pm, cuenta = [], [], {}
start = dt.date(2025, 1, 6)
for k in range(540):
    f = start + dt.timedelta(days=k); wd = f.weekday()
    fila = {}
    for turno in ("AM", "PM"):
        caj_pool = [c for c in CAJ if c not in ("P01", "P02")]
        if turno == "PM" and wd <= 3 and rnd.random() < .6: caj_pool = ["P01"]
        if turno == "AM" and wd >= 5 and rnd.random() < .6: caj_pool = ["P02"]
        c, b, co = rnd.choice(caj_pool), rnd.choice(BOA), rnd.choice(COR)
        n = rnd.randint(80, 160) if turno == "AM" else (rnd.randint(8, 20) if rnd.random() < .08 else rnd.randint(40, 140))
        mu = base(turno, wd) + talento[c] + talento[b] + talento[co] + rnd.gauss(0, 15)
        cars = [max(20, rnd.gauss(mu, 60)) for _ in range(n)]
        fila[turno] = (c, b, co, cars)
    am, pm = fila["AM"][3], fila["PM"][3]
    am_disp = round(sum(am) / len(am))                       # lo que muestra el sistema a las 19:00
    day_disp = round((sum(am) + sum(pm)) / (len(am) + len(pm)))  # lo que muestra al cierre
    auto_rows.append((f, "AM", *fila["AM"][:3], len(am), am_disp // 60, am_disp % 60))
    auto_rows.append((f, "PM", *fila["PM"][:3], len(am) + len(pm), day_disp // 60, day_disp % 60))
    verdad_pm.append((len(auto_rows) - 1 + 8, sum(pm) / len(pm), len(am), len(pm)))
    for x in fila["AM"][:3] + fila["PM"][:3]: cuenta[x] = cuenta.get(x, 0) + 1

res, wb = build_case(TEMPLATE, "verdad.xlsx", auto_rows[:1030], [], NAMES, {"L7": 3})
a, ca = wb["Registro Auto"], wb["Calculos"]
out = []
def check(n, c, det=""): out.append((n, bool(c), det))

# 1) tiempo real de PM vs verdad, dentro de la cota de redondeo 0,5 s·(autos día + autos AM)/autos PM
peor, fuera = 0, 0
for row, real, n_am, n_pm in verdad_pm:
    if row > 1037: break
    L = secs(a[f"L{row}"].value)
    cota = 0.5 * (n_am + n_pm + n_am) / n_pm + 0.51
    if L is None or abs(L - real) > cota: fuera += 1
    peor = max(peor, abs((L or 0) - real) / cota)
check("Tiempo real PM dentro del error de redondeo del sistema (todas las filas)", fuera == 0, f"fuera={fuera}, peor relación error/cota={peor:.2f}")
pocos = [(row, real, n_am, n_pm) for row, real, n_am, n_pm in verdad_pm if n_pm <= 20 and row <= 1037]
errs = [abs(secs(a[f"L{r}"].value) - real) for r, real, _, _ in pocos]
print(f"   PM con pocos autos ({len(pocos)} filas): error máx {max(errs):.1f} s, medio {statistics.mean(errs):.1f} s")

# 2) ranking de cajeros: correlación de Spearman con el talento real
hdr = {ca.cell(1, c).value: c for c in range(1, ca.max_column + 1) if ca.cell(1, c).value}
col_s = hdr.get("Cajero_ajustado") or hdr.get("Cajero_tiempo")
col_n = hdr.get("Cajero_nombre")
print("   criterio de orden usado:", ca.cell(1, col_s).value)
pares = []
for r in range(3, 103):
    nm, sc = ca.cell(r, col_n).value, ca.cell(r, col_s).value
    if nm in CAJ and isinstance(sc, (int, float)):
        pares.append((nm, sc * (86400 if ca.cell(1, col_s).value.endswith("tiempo") else 1)))
def rank(v): o = sorted(range(len(v)), key=lambda i: v[i]); r = [0] * len(v); [r.__setitem__(i, k) for k, i in enumerate(o)]; return r
ra_, rb_ = rank([p[1] for p in pares]), rank([talento[p[0]] for p in pares])
nn = len(pares); rho = 1 - 6 * sum((x - y) ** 2 for x, y in zip(ra_, rb_)) / (nn * (nn * nn - 1))
orden = [p[0] for p in sorted(pares, key=lambda p: p[1])]
verdad = sorted(CAJ, key=lambda n: talento[n])
print("   orden Excel:", orden)
print("   orden real: ", verdad)
check("Ranking de cajeros se parece al talento real (Spearman ≥ 0,8)", rho >= 0.8, f"rho={rho:.2f}")
check("P01 (lento, solo turnos fáciles) NO queda en el top 3", "P01" not in orden[:3], f"posición P01 = {orden.index('P01') + 1}")
check("P02 (rápido, solo turnos difíciles) queda en el top 3", "P02" in orden[:3], f"posición P02 = {orden.index('P02') + 1}")
print(f"TOTAL VERDAD: {sum(o[1] for o in out)}/{len(out)}")
for n, ok, det in out: print(("  ✓ " if ok else "  ✗ ") + n + ("" if ok else f"  →  {det}"))
