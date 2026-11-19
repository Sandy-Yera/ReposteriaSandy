"""Simulaciones con datos aleatorios: compara cada celda del Panel y del Registro contra el modelo Python."""
import sys, random, datetime as dt
from harness import build_case, scan_errors, secs, fecha
import modelo

TEMPLATE = sys.argv[1]
fails, checks = [], 0
NAMES = ["Ana", "Bruno", "Carla", "Diego", "Elena", "Felipe", "Gabriela", "Hugo", "Iris", "Javier", "Karen", "Luis"]


def eq(label, got, exp, tol_s=0.6):
    global checks
    checks += 1
    if exp is None or exp == "—":
        ok = got in (None, "", "—")
    elif isinstance(exp, float) and label.endswith("_t"):
        g = secs(got); ok = g is not None and abs(g - exp) <= tol_s
    elif isinstance(exp, float) and (label.endswith("ajustado") or label.endswith("margen")):
        ok = isinstance(got, (int, float)) and abs(got - exp) < 0.002
    elif isinstance(exp, float):
        ok = isinstance(got, (int, float)) and abs(got - exp) < 1e-6
    else:
        ok = got == exp
    if not ok: fails.append(f"{label}: esperado {exp!r}, obtenido {got!r}")


def gen(seed, days=75):
    rnd = random.Random(seed)
    start = dt.date(2026, 6, 1)
    auto, deliv = [], []
    for k in range(days):
        f = start + dt.timedelta(days=k)
        caso = rnd.random()
        trio = lambda: rnd.sample(NAMES[:8] if rnd.random() < .7 else NAMES, 3)
        amA = rnd.randint(60, 160); amS = rnd.randint(110, 330)
        pmA = rnd.randint(40, 140); pmS = rnd.randint(100, 330)
        dayA = amA + pmA; dayS = round((amA * amS + pmA * pmS) / dayA)
        if caso < 0.85 or caso >= 0.93:
            c, b, r = trio(); auto.append(dict(fecha=f, turno="AM", caj=c, board=b, corr=r, autos=amA, min=amS // 60, seg=amS % 60))
        if caso < 0.85:
            c, b, r = trio(); auto.append(dict(fecha=f, turno="PM", caj=c, board=b, corr=r, autos=dayA, min=dayS // 60, seg=dayS % 60))
        elif caso < 0.93:  # solo PM
            c, b, r = trio(); auto.append(dict(fecha=f, turno="PM", caj=c, board=b, corr=r, autos=pmA, min=pmS // 60, seg=pmS % 60))
        for t in ("AM", "PM"):
            if rnd.random() < 0.9:
                deliv.append(dict(fecha=f, turno=t, conc="Sí" if rnd.random() < 0.8 else "No"))
    rnd.shuffle(auto) if seed % 2 else None  # semilla impar: filas desordenadas (como si ordenaran la tabla)
    return auto, deliv


def run(seed, filtros_list):
    auto, deliv = gen(seed)
    rows = [(r["fecha"], r["turno"], r["caj"], r["board"], r["corr"], r["autos"], r["min"], r["seg"]) for r in auto]
    drows = [(x["fecha"], x["turno"], f"https://drive.google.com/f{i}", x["conc"]) for i, x in enumerate(deliv)]
    for nf, flt in enumerate(filtros_list):
        panel = {"B7": flt.get("desde"), "D7": flt.get("hasta"), "F7": flt.get("turno", "Total"), "H7": flt.get("canal", "Ambos"),
                 "J7": flt.get("persona", "Todas"), "L7": flt.get("minimo", 3)}
        res, wb = build_case(TEMPLATE, f"sim_{seed}_{nf}.xlsx", rows, drows, NAMES, panel)
        tag = f"[seed {seed} filtro {nf} {flt}]"
        if res.get("total_errors"): fails.append(f"{tag} recalc errores {res}")
        errs = scan_errors(wb)
        if errs: fails.append(f"{tag} celdas con error: {errs[:5]}")
        exp = modelo.panel(auto, deliv, NAMES, desde=flt.get("desde"), hasta=flt.get("hasta"), turno=flt.get("turno", "Total"),
                           persona=flt.get("persona", "Todas"), minimo=flt.get("minimo", 3), canal=flt.get("canal", "Ambos"))
        p, a = wb["Panel"], wb["Registro Auto"]
        if nf == 0:  # registro fila a fila
            for r in modelo.filas(auto):
                rr = 8 + r["i"]
                eq(f"{tag} fila{rr} K", a[f"K{rr}"].value, r["K"])
                eq(f"{tag} fila{rr} L_t", a[f"L{rr}"].value, float(r["L"]) if r["L"] is not None else None)
                eq(f"{tag} fila{rr} nivel", a[f"M{rr}"].value, r["nivel"])
                eq(f"{tag} fila{rr} cumple", a[f"N{rr}"].value, r["cumple"])
                eq(f"{tag} fila{rr} dia_t", a[f"O{rr}"].value, float(r["O"]) if r["O"] is not None else None)
                eq(f"{tag} fila{rr} resultado", a[f"P{rr}"].value, r["P"])
        eq(f"{tag} autos", p["B11"].value, exp["autos"])
        eq(f"{tag} tprom_t", p["E11"].value, exp["t"])
        eq(f"{tag} pct", p["H11"].value, exp["pct"])
        eq(f"{tag} del", p["K11"].value, exp["del"])
        eq(f"{tag} dias", p["B16"].value, exp["dias"])
        eq(f"{tag} turnos", p["H16"].value, exp["turnos"])
        eq(f"{tag} del_n", p["K16"].value, exp["del_n"])
        if flt.get("canal", "Ambos") == "Ambos":
            for col, t in zip("FHJ", ("AM", "PM", "Total")):
                e = exp["tabla"][t]
                eq(f"{tag} tabla {t} autos", p[f"{col}23"].value, e["autos"])
                eq(f"{tag} tabla {t} tiempo_t", p[f"{col}24"].value, e["t"])
                eq(f"{tag} tabla {t} pct", p[f"{col}26"].value, e["pct"])
                eq(f"{tag} tabla {t} n", p[f"{col}27"].value, e["n"])
                eq(f"{tag} tabla {t} del", p[f"{col}28"].value, e["dl"])
        for role, r0 in (("caj", 35), ("board", 43), ("corr", 51)):
            lst = exp["rank"][role]
            for k in range(5):
                rr = r0 + k
                if k < len(lst):
                    e = lst[k]
                    eq(f"{tag} rank {role} {k+1} nombre", p[f"C{rr}"].value, e["clave"])
                    eq(f"{tag} rank {role} {k+1} turnos", p[f"E{rr}"].value, e["n"])
                    eq(f"{tag} rank {role} {k+1} tiempo_t", p[f"F{rr}"].value, float(e["t"]))
                    eq(f"{tag} rank {role} {k+1} ajustado", round(p[f"G{rr}"].value, 3) if isinstance(p[f"G{rr}"].value, float) else p[f"G{rr}"].value, round(e["score"], 3) + 0.0)
                    eq(f"{tag} rank {role} {k+1} margen", p[f"I{rr}"].value, e["margen"] if e["margen"] is None else float(e["margen"]))
                    eq(f"{tag} rank {role} {k+1} lectura", p[f"J{rr}"].value, e["lectura"])
                    eq(f"{tag} rank {role} {k+1} pct", p[f"L{rr}"].value, float(e["pct"]))
                else:
                    eq(f"{tag} rank {role} {k+1} vacío", p[f"C{rr}"].value, "Sin datos suficientes" if k == 0 else None)
        for k in range(5):
            rr = 60 + k
            if k < len(exp["trios"]):
                e = exp["trios"][k]
                eq(f"{tag} trio {k+1}", (p[f"C{rr}"].value, p[f"D{rr}"].value, p[f"E{rr}"].value), e["clave"])
                eq(f"{tag} trio {k+1} n", p[f"F{rr}"].value, e["n"])
                eq(f"{tag} trio {k+1} tiempo_t", p[f"G{rr}"].value, float(e["t"]))
                eq(f"{tag} trio {k+1} ajustado", round(p[f"H{rr}"].value, 3), round(e["score"], 3) + 0.0)
                eq(f"{tag} trio {k+1} margen", p[f"I{rr}"].value, e["margen"] if e["margen"] is None else float(e["margen"]))
                eq(f"{tag} trio {k+1} lectura", p[f"J{rr}"].value, e["lectura"])
            else:
                eq(f"{tag} trio {k+1} vacío", p[f"F{rr}"].value, None)
        for k in range(5):
            if k < len(exp["dias_top"]):
                O, _, r = exp["dias_top"][k]
                eq(f"{tag} dia {k+1} fecha", fecha(p[f"C{69+k}"].value), r["fecha"])
                eq(f"{tag} dia {k+1} tiempo_t", p[f"E{69+k}"].value, float(O))
                eq(f"{tag} dia {k+1} resultado", p[f"L{69+k}"].value, r["P"])


d = dt.date
run(1, [{}, {"desde": d(2026, 6, 8), "hasta": d(2026, 6, 14)}, {"desde": d(2026, 6, 20), "hasta": d(2026, 6, 20)},
        {"turno": "AM", "minimo": 2}, {"turno": "PM", "persona": "Carla"}, {"canal": "Delivery"}])
run(2, [{}, {"desde": d(2026, 7, 1), "hasta": d(2026, 7, 31), "minimo": 5}, {"turno": "PM", "minimo": 1}, {"canal": "Auto", "persona": "Luis"}])
run(3, [{}, {"desde": d(2026, 6, 30), "hasta": d(2026, 6, 1)}])  # rango invertido
print(f"SIMULACIÓN: {checks} comprobaciones, {len(fails)} fallos")
for f in fails[:40]: print("  ✗", f)
