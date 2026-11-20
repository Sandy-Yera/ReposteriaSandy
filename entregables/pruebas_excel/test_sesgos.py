"""Precisión y sesgos con VERDAD CONOCIDA, con el archivo lleno (1.030 filas ≈ 73 semanas). No modifica el Excel.
Uso: python test_sesgos.py archivo.xlsx semilla"""
import sys, random, statistics, datetime as dt
from harness import build_case, secs

TEMPLATE, SEED = sys.argv[1], int(sys.argv[2])
MODO = sys.argv[3] if len(sys.argv) > 3 else "completo"     # limpio: solo sesgos que el método promete corregir
LIMPIO = MODO == "limpio"
rnd = random.Random(SEED)
CAJ = [f"C{i:02d}" for i in range(1, 9)]; BOA = [f"B{i:02d}" for i in range(1, 9)]; COR = [f"R{i:02d}" for i in range(1, 9)]
CAJ[0], CAJ[1], CAJ[2], CAJ[3], CAJ[4] = "P01", "P02", "P03", "P04", "P05"
NAMES = CAJ + BOA + COR
tal = {n: rnd.gauss(0, 12) for n in NAMES}
tal.update({"P01": 12.0, "P02": -12.0, "P03": 0.0, "P04": 0.0, "P05": 0.0})
if not LIMPIO: tal["B01"] = -25.0
DIAS = 515
inicio = dt.date(2025, 1, 6)
planted_day = inicio + dt.timedelta(days=7 * 40 + 5)          # un sábado excepcional
p03_turnos = set(rnd.sample(range(DIAS), 4))
def base(turno, wd, k, n):
    return (200 if turno == "AM" else 165) + (35 if wd >= 5 else 0) + (0 if LIMPIO else -20 * k / DIAS + 0.2 * (n - 100))   # deriva y volumen

auto, verdad_pm, verdad_turno, verdad_dia = [], [], [], {}
for k in range(DIAS):
    f = inicio + dt.timedelta(days=k); wd = f.weekday()
    turnos = {}
    for t in ("AM", "PM"):
        pool = [c for c in CAJ if c not in ("P01", "P02", "P03", "P05")]
        if t == "PM" and wd <= 3 and rnd.random() < .6: pool = ["P01"]
        elif t == "AM" and wd >= 5 and rnd.random() < .6: pool = ["P02"]
        elif t == "AM" and k in p03_turnos: pool = ["P03"]
        elif not LIMPIO and k > DIAS - 90 and rnd.random() < .5: pool = ["P05"]
        c = rnd.choice(pool)
        b = "B01" if (c == "P04" and not LIMPIO) else rnd.choice(BOA)
        co = rnd.choice(COR)
        n = rnd.randint(80, 160) if t == "AM" else (rnd.randint(8, 20) if rnd.random() < .06 else rnd.randint(40, 140))
        ruido = -40 if c == "P03" else rnd.gauss(0, 15)
        mu = base(t, wd, k, n) + tal[c] + tal[b] + tal[co] + ruido
        if f == planted_day: mu -= 60
        cars = [max(20, rnd.gauss(mu, 60)) for _ in range(n)]
        turnos[t] = (c, b, co, cars)
    am, pm = turnos["AM"][3], turnos["PM"][3]
    am_d = round(sum(am) / len(am)); day_d = round((sum(am) + sum(pm)) / (len(am) + len(pm)))
    auto.append((f, "AM", *turnos["AM"][:3], len(am), am_d // 60, am_d % 60))
    auto.append((f, "PM", *turnos["PM"][:3], len(am) + len(pm), day_d // 60, day_d % 60))
    verdad_pm.append((8 + 2 * k + 1, sum(pm) / len(pm), len(am), len(pm)))
    verdad_dia[f] = (sum(am) + sum(pm)) / (len(am) + len(pm))
auto = auto[:1030]
res, wb = build_case(TEMPLATE, f"sesgos_{SEED}.xlsx", auto, [], NAMES, {"L7": 3})
a, ca, p = wb["Registro Auto"], wb["Calculos"], wb["Panel"]
out = []
def check(n, c, det=""): out.append((n, bool(c), det))
def spearman(x, y):
    def rk(v): o = sorted(range(len(v)), key=lambda i: v[i]); r = [0]*len(v); [r.__setitem__(i, j) for j, i in enumerate(o)]; return r
    rx, ry = rk(x), rk(y); n = len(x); return 1 - 6 * sum((u - w) ** 2 for u, w in zip(rx, ry)) / (n * (n * n - 1))

# 1) tiempo real de PM
fuera = sum(1 for row, real, na, npm in verdad_pm if row <= 1037 and (secs(a[f"L{row}"].value) is None or abs(secs(a[f"L{row}"].value) - real) > 0.5 * (2 * na + npm) / npm + 0.51))
check("Tiempo real de PM dentro del error de redondeo del sistema (≈515 filas PM)", fuera == 0, f"fuera={fuera}")
# 2) rankings por estación
hdr = {ca.cell(1, c).value: c for c in range(1, ca.max_column + 1) if ca.cell(1, c).value}
def bloque(rol):
    filas = []
    for r in range(3, 103):
        nm = ca.cell(r, hdr[f"{rol}_nombre"]).value
        aj = ca.cell(r, hdr[f"{rol}_ajustado"]).value
        if nm and isinstance(aj, (int, float)):
            filas.append(dict(n=nm, aj=aj, mg=ca.cell(r, hdr[f"{rol}_margen"]).value, lec=ca.cell(r, hdr[f"{rol}_lectura"]).value,
                              crudo=ca.cell(r, hdr[f"{rol}_tiempo"]).value * 86400, turnos=ca.cell(r, hdr[f"{rol}_turnos"]).value))
    return filas
res_rol = {}
for rol, lista in (("Cajero", CAJ), ("Board", BOA), ("Corredor", COR)):
    fl = [x for x in bloque(rol) if x["n"] in lista]
    rho_aj = spearman([x["aj"] for x in fl], [tal[x["n"]] for x in fl])
    rho_cr = spearman([x["crudo"] for x in fl], [tal[x["n"]] for x in fl])
    res_rol[rol] = fl
    print(f"   {rol}: Spearman ajustado {rho_aj:.2f} | crudo {rho_cr:.2f} | personas {len(fl)}")
    check(f"{rol}: el ranking justo sigue el talento real (Spearman ≥ 0,7)", rho_aj >= 0.7, f"{rho_aj:.2f} (crudo {rho_cr:.2f})")
caj = {x["n"]: x for x in res_rol["Cajero"]}
orden = [x["n"] for x in sorted(res_rol["Cajero"], key=lambda x: x["aj"])]
orden_crudo = [x["n"] for x in sorted(res_rol["Cajero"], key=lambda x: x["crudo"])]
real = sorted(caj, key=lambda n: tal[n])
print("   cajeros (justo):", orden); print("   cajeros (crudo):", orden_crudo); print("   cajeros (real): ", real)
check("Sesgo turnos fáciles: P01 (lento) no queda en el top 3", "P01" not in orden[:3], orden.index("P01") + 1)
check("Sesgo turnos difíciles: P02 (rápido) queda a ≤1 puesto de su lugar real", abs(orden.index("P02") - real.index("P02")) <= 1, (orden.index("P02") + 1, real.index("P02") + 1))
if "P03" in caj:
    check("Muestra chica con suerte: P03 (4 turnos) NO aparece como 'Más rápido que lo normal'", caj["P03"]["lec"] != "Más rápido que lo normal",
          (caj["P03"]["turnos"], round(caj["P03"]["aj"], 1), round(caj["P03"]["mg"], 1), caj["P03"]["lec"], round(caj["P03"]["crudo"] - statistics.mean(x["crudo"] for x in res_rol["Cajero"]), 1)))
else:
    check("Muestra chica: P03 con menos turnos que el mínimo no entra al ranking", True)
print(f"   P04 (normal, siempre con el mejor board): puesto justo {orden.index('P04')+1}, crudo {orden_crudo.index('P04')+1}, real {real.index('P04')+1}, lectura {caj['P04']['lec']}")
if "P05" in caj: print(f"   P05 (normal, entró al final cuando el local ya mejoró): puesto justo {orden.index('P05')+1}, crudo {orden_crudo.index('P05')+1}, real {real.index('P05')+1}, lectura {caj['P05']['lec']}")
# 3) márgenes y falsos positivos
cob, fp = [], []
for rol, lista in (("Cajero", CAJ), ("Board", BOA), ("Corredor", COR)):
    fl = res_rol[rol]
    w = {x["n"]: x["turnos"] for x in fl}; media = sum(tal[n] * w[n] for n in w) / sum(w.values())
    for x in fl:
        if not LIMPIO and x["n"] in ("P04", "P05"): continue          # sesgos no corregibles por diseño, se informan aparte
        if isinstance(x["mg"], (int, float)): cob.append(abs(x["aj"] - (tal[x["n"]] - media)) <= x["mg"])
        if abs(tal[x["n"]] - media) < 3: fp.append(x["lec"] != "Dentro de lo normal")
print(f"   cobertura del margen ±95%: {sum(cob)}/{len(cob)} ({sum(cob)/len(cob):.0%}) | falsos 'más rápido/lento': {sum(fp)}/{len(fp)}")
check("Margen ±95 % honesto (cobertura ≥ 85 %)", sum(cob) / len(cob) >= 0.85, f"{sum(cob)/len(cob):.0%}")
check("Falsos 'más rápido/lento' en personas normales ≤ 10 %", (sum(fp) / len(fp) if fp else 0) <= 0.10, f"{sum(fp)}/{len(fp)}")
# 4) tríos
tri = []
for r in range(8, 1038):
    v = a[f"AW{r}"].value
    if isinstance(v, (int, float)): tri.append((v, tal[a[f"D{r}"].value] + tal[a[f"E{r}"].value] + tal[a[f"F{r}"].value]))
if len(tri) >= 5:
    rt = spearman([x[0] for x in tri], [x[1] for x in tri])
    print(f"   tríos con ≥3 turnos: {len(tri)} | Spearman con el talento real del equipo {rt:.2f}")
    check("Ranking de tríos sigue el talento real del equipo (Spearman ≥ 0,6)", rt >= 0.6, f"{rt:.2f}")
# 5) mejores días: ¿el desvío que calcula el Excel coincide con el desvío REAL de cada día (auto por auto)?
dias = []
for r in range(8, 1038):
    cc = a[f"CC{r}"].value
    if isinstance(cc, (int, float)):
        f = a[f"B{r}"].value; f = f.date() if hasattr(f, "date") else f
        dias.append((cc, f, a[f"G{r}"].value))
porwd = {}
for f, m in verdad_dia.items():
    if f in {d[1] for d in dias}: porwd.setdefault(f.weekday(), []).append(m)
def dev_real(f):
    g = porwd[f.weekday()]; n = len(g); return (verdad_dia[f] - sum(g) / n) * n / (n - 1)
errs = [abs(cc - dev_real(f)) for cc, f, _ in dias]
rd = spearman([d[0] for d in dias], [dev_real(d[1]) for d in dias])
print(f"   días: error medio del desvío {statistics.mean(errs):.2f} s, máximo {max(errs):.2f} s | Spearman con el desvío real {rd:.3f}")
check("Mejores días: el desvío de cada día coincide con el real (error medio ≤ 1 s, orden ≥ 0,99)", statistics.mean(errs) <= 1 and rd >= 0.99, (round(statistics.mean(errs), 2), round(rd, 3)))
dias.sort()
pos = [d[1] for d in dias].index(planted_day) + 1
pos_real = sorted(dias, key=lambda d: dev_real(d[1])).index(next(d for d in dias if d[1] == planted_day)) + 1
check("El sábado excepcional queda en el puesto que le corresponde según su desvío real (±1)", abs(pos - pos_real) <= 1,
      f"puesto Excel {pos}, puesto real {pos_real} (desvío real {dev_real(planted_day):.0f} s)")
vol = spearman([d[0] for d in dias], [d[2] for d in dias])
print(f"   relación 'mejor día' ↔ volumen de autos (Spearman): {vol:+.2f} (positivo = los días con menos autos salen mejor)")
print(f"SESGOS [{MODO}] semilla {SEED}: {sum(o[1] for o in out)}/{len(out)}")
for n, ok, det in out: print(("  ✓ " if ok else "  ✗ ") + n + ("" if ok else f"  →  {det}"))
