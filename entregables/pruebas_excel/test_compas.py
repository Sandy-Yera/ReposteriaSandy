"""Casos límite de la corrección por compañero, de extremo a extremo (Excel recalculado vs modelo Python independiente).
Uso: python test_compas.py archivo.xlsx"""
import sys, random, datetime as dt
from harness import build_case, scan_errors, secs
import modelo

TEMPLATE = sys.argv[1]
out = []
def check(n, ok, det=""): out.append((n, bool(ok), det)); print(("  ✓ " if ok else "  ✗ ") + n + ("" if ok else f"  →  {det}"), flush=True)


def gen(seed, dias, elegir, tal, base_ruido=15):
    """elegir(rnd, k, turno) -> (caj, board, corr). Devuelve filas del registro (PM con acumulado del día)."""
    rnd = random.Random(seed); ini = dt.date(2026, 1, 5); filas = []
    for k in range(dias):
        f = ini + dt.timedelta(days=k); wd = f.weekday(); tr = {}
        for t in ("AM", "PM"):
            c, b, co = elegir(rnd, k, t)
            n = rnd.randint(60, 140)
            mu = (200 if t == "AM" else 170) + (30 if wd >= 5 else 0) + tal.get(c, 0) + tal.get(b, 0) + tal.get(co, 0) + rnd.gauss(0, base_ruido)
            tr[t] = (c, b, co, n, max(60, mu))
        am, pm = tr["AM"], tr["PM"]
        s_am = round(am[4]); s_dia = round((am[3] * am[4] + pm[3] * pm[4]) / (am[3] + pm[3]))
        filas.append((f, "AM", am[0], am[1], am[2], am[3], s_am // 60, s_am % 60))
        filas.append((f, "PM", pm[0], pm[1], pm[2], am[3] + pm[3], s_dia // 60, s_dia % 60))
    return filas


def a_dicts(filas):
    return [dict(fecha=r[0], turno=r[1], caj=r[2], board=r[3], corr=r[4], autos=r[5], min=r[6], seg=r[7]) for r in filas]


def leer_rank(p):
    res = {}
    for role, r0 in (("caj", 35), ("board", 43), ("corr", 51)):
        res[role] = [(p[f"C{r0+k}"].value, p[f"E{r0+k}"].value, p[f"G{r0+k}"].value, p[f"I{r0+k}"].value, p[f"J{r0+k}"].value) for k in range(5)]
    return res


def comparar(tag, filas, personal, panel=None, mod_personal=None, filas_mod=None):
    """Arma el caso, recalcula y compara los tres rankings del Panel con el modelo. Retorna los rankings leídos."""
    res, wb = build_case(TEMPLATE, f"compas_{tag}.xlsx", filas, [], personal, {"L7": 3, **(panel or {})})
    errs = scan_errors(wb)
    exp = modelo.panel(a_dicts(filas_mod or filas), [], mod_personal or personal, minimo=(panel or {}).get("L7", 3))
    p = wb["Panel"]; fallos = []
    for role, r0 in (("caj", 35), ("board", 43), ("corr", 51)):
        lst = exp["rank"][role]
        for k in range(5):
            rr = r0 + k
            if k < len(lst):
                e = lst[k]; g = p[f"G{rr}"].value; m = p[f"I{rr}"].value
                if p[f"C{rr}"].value != e["clave"]: fallos.append(f"{role}{k+1} nombre {p[f'C{rr}'].value}≠{e['clave']}")
                if p[f"E{rr}"].value != e["n"]: fallos.append(f"{role}{k+1} turnos")
                if not isinstance(g, (int, float)) or abs(g - e["score"]) > 0.002: fallos.append(f"{role}{k+1} ajustado {g}≠{round(e['score'], 3)}")
                if (e["margen"] is None) != (m in (None, "")) or (e["margen"] is not None and abs(m - e["margen"]) > 0.002): fallos.append(f"{role}{k+1} margen {m}≠{e['margen']}")
                if p[f"J{rr}"].value != e["lectura"]: fallos.append(f"{role}{k+1} lectura {p[f'J{rr}'].value}≠{e['lectura']}")
            elif p[f"C{rr}"].value not in (None, "", "Sin datos suficientes"): fallos.append(f"{role}{k+1} sobra {p[f'C{rr}'].value}")
    check(f"[{tag}] sin errores en ninguna celda", not errs and not res.get("total_errors"), (errs[:3], res.get("total_errors")))
    check(f"[{tag}] los 3 rankings del Panel coinciden con el cálculo independiente", not fallos, fallos[:5])
    return leer_rank(p), wb


C = ["Ana", "Bruno", "Carla", "Diego"]; B = ["Elena", "Felipe", "Gabi", "Hugo"]; R = ["Iris", "Javier", "Karen", "Luis"]
P = C + B + R

print("· 1. Trío siempre fijo (confusión total: imposible separar a cada uno)")
tal = {"Ana": -20, "Elena": 0, "Iris": 0}
fijo = [("Ana", "Elena", "Iris"), ("Bruno", "Felipe", "Javier"), ("Carla", "Gabi", "Karen"), ("Diego", "Hugo", "Luis")]
filas = gen(1, 80, lambda rnd, k, t: rnd.choice(fijo), tal)
rk, _ = comparar("trio_fijo", filas, P)
lect = [x[4] for role in rk for x in rk[role] if x[0]]
check("[trio_fijo] no inventa certezas: nadie del trío rápido aparece como 'más lento'",
      not any(x[0] in ("Elena", "Iris") and x[4] == "Más lento que lo normal" for role in rk for x in rk[role]), rk)

print("· 2. Una sola persona en Board y en Corredor (su aporte centrado debe ser 0)")
tal = {"Ana": -25, "Bruno": 0, "Carla": 0, "Diego": 25}
filas = gen(2, 80, lambda rnd, k, t: (rnd.choice(C), "Elena", "Iris"), tal)
rk, wb = comparar("un_board", filas, P)
ca = wb["Calculos"]; hdr = {ca.cell(1, c).value: c for c in range(1, ca.max_column + 1) if ca.cell(1, c).value}
aps = [ca.cell(r, hdr[f"{rol}_aporte_c"]).value for rol in ("Board", "Corredor") for r in range(3, 103)
       if ca.cell(r, hdr[f"{rol}_nombre"]).value in ("Elena", "Iris")]
check("[un_board] el aporte centrado del único board y del único corredor es 0", all(abs(v) < 1e-9 for v in aps), aps)
check("[un_board] el orden de cajeros sigue el talento (Ana 1.ª, Diego último)", rk["caj"][0][0] == "Ana" and [x[0] for x in rk["caj"] if x[0]][-1] == "Diego", rk["caj"])

print("· 3. Compañero estrella: cajero normal que SIEMPRE trabaja con el mejor board")
tal = {"Elena": -40, "Ana": 0, "Bruno": -15, "Carla": 0, "Diego": 15}
def eleg3(rnd, k, t):
    c = rnd.choice(C); return (c, "Elena" if c == "Ana" else rnd.choice(B[1:]), rnd.choice(R))
filas = gen(3, 120, eleg3, tal)
rk, _ = comparar("estrella", filas, P)
ana = next(x for x in rk["caj"] if x[0] == "Ana")
check("[estrella] Ana (normal) NO sale 'Más rápido que lo normal' por el board", ana[4] != "Más rápido que lo normal", ana)
check("[estrella] Bruno (realmente rápido) queda sobre Ana", [x[0] for x in rk["caj"]].index("Bruno") < [x[0] for x in rk["caj"]].index("Ana"), rk["caj"])

print("· 4. Mismos datos con las filas desordenadas (el resultado no puede cambiar)")
tal = {n: random.Random(4).gauss(0, 12) for n in P}
filas = gen(4, 90, lambda rnd, k, t: (rnd.choice(C), rnd.choice(B), rnd.choice(R)), tal)
rk1, _ = comparar("orden", filas, P)
mez = filas[:]; random.Random(9).shuffle(mez)
rk2, _ = comparar("orden_mezcla", mez, P)
iguales = all(a[0] == b[0] and a[4] == b[4] and (a[2] == b[2] or abs(a[2] - b[2]) < 1e-6 or True) for role in rk1 for a, b in zip(rk1[role], rk2[role]))
check("[orden] mismos nombres y lecturas con filas desordenadas", iguales, (rk1, rk2))
check("[orden] mismo puntaje (±0,01 s) con filas desordenadas",
      all((a[2] is None and b[2] is None) or (isinstance(a[2], float) and abs(a[2] - b[2]) < 0.01) for role in rk1 for a, b in zip(rk1[role], rk2[role]) if a[0] not in (None, "Sin datos suficientes")))

print("· 5. Nombres escritos con otra mayúscula en el registro (ANA / ana)")
filas5 = [(r[0], r[1], r[2].upper() if i % 3 == 0 else r[2].lower() if i % 3 == 1 else r[2], r[3], r[4], *r[5:]) for i, r in enumerate(filas)]
rk5, _ = comparar("mayus", filas5, P, filas_mod=filas)   # Excel no distingue mayúsculas: se compara contra los nombres bien escritos
check("[mayus] mismo ranking de cajeros que con los nombres bien escritos",
      [(x[0], x[4]) for x in rk5["caj"]] == [(x[0], x[4]) for x in rk1["caj"]], (rk5["caj"], rk1["caj"]))

print("· 6. Renombrar a una persona en Personal y en el registro (Ana → Ana María)")
filas6 = [tuple("Ana María" if v == "Ana" else v for v in r) for r in filas]
P6 = ["Ana María" if n == "Ana" else n for n in P]
rk6, _ = comparar("renombre", filas6, P6)
check("[renombre] mismos puntajes; solo cambia el nombre",
      all(abs((a[2] or 0) - (b[2] or 0)) < 1e-6 and (a[0] == b[0] or (a[0], b[0]) == ("Ana", "Ana María")) for a, b in zip(rk1["caj"], rk6["caj"])), (rk1["caj"], rk6["caj"]))

print("· 7. Muy pocos datos (4 días): no debe haber errores ni lecturas fuertes")
filas7 = gen(7, 4, lambda rnd, k, t: (rnd.choice(C), rnd.choice(B), rnd.choice(R)), {})
rk7, _ = comparar("pocos", filas7, P, {"L7": 1})
check("[pocos] ninguna lectura 'más rápido/lento' con 4 días", all(x[4] in (None, "", "Dentro de lo normal", "Pocos datos") for role in rk7 for x in rk7[role]), rk7)

print("· 8. Mínimo de turnos alto (nadie califica)")
rk8, _ = comparar("minimo_alto", filas7, P, {"L7": 50})
check("[minimo_alto] el Panel dice 'Sin datos suficientes' en las 3 estaciones", all(rk8[role][0][0] == "Sin datos suficientes" for role in rk8), [rk8[r][0] for r in rk8])

print("· 9. Archivo lleno (1.030 filas) con compañeros variados")
tal = {n: random.Random(10).gauss(0, 12) for n in P}
filas9 = gen(10, 515, lambda rnd, k, t: (rnd.choice(C), rnd.choice(B), rnd.choice(R)), tal)
comparar("lleno", filas9, P)

print(f"\nCOMPAÑEROS E2E: {sum(o[1] for o in out)}/{len(out)}")
