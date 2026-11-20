"""Valida el MÉTODO de ranking (sin Excel) con verdad conocida en 12 semillas: compara el orden crudo con el orden justo
(contexto turno×día, contra los demás, bayesiano empírico). Mide correlación con el talento real, si los confusores
engañan, cobertura real del margen ±95 % y falsos 'más rápido/lento'. Uso: python valida_metodo.py"""
import sys, statistics
import modelo
SRC = open("test_verdad.py").read().split("res, wb = build_case")[0].replace("from harness import build_case, secs", "")

def spearman(a, b):
    def rk(v):
        o = sorted(range(len(v)), key=lambda i: v[i]); r = [0] * len(v)
        for k, i in enumerate(o): r[i] = k
        return r
    x, y = rk(a), rk(b); n = len(a)
    return 1 - 6 * sum((p - q) ** 2 for p, q in zip(x, y)) / (n * (n * n - 1))

def ranking_justo(F, nombres, minimo=3):
    ctx = lambda r: (r["turno"], r["fecha"].weekday())
    Ng, Ngi, Sv, Nv = {}, {}, {}, {}
    for r in F:
        g = ctx(r); Ng[g] = Ng.get(g, 0) + 1; Ngi[(g, r["caj"])] = Ngi.get((g, r["caj"]), 0) + 1
        Sv[g] = Sv.get(g, 0) + r["L"]; Nv[g] = Nv.get(g, 0) + 1
    grupos, todos = [], []
    for i, n in enumerate(nombres):
        todas = [r for r in F if r["caj"] == n]
        comp = []
        for r in todas:
            g = ctx(r); libres = Ng[g] - Ngi[(g, n)]
            if libres > 0: comp.append(dict(r, res=(r["L"] - Sv[g] / Nv[g]) * Ng[g] / libres))
        if comp: todos.append(comp)
        if len(comp) >= minimo: grupos.append((i, n, comp, todas))
    Na = sum(len(c) for c in todos); ma = len(todos)
    s2c = (sum(r["res"] ** 2 for c in todos for r in c) - sum(sum(r["res"] for r in c) ** 2 / len(c) for c in todos)) / (Na - ma)
    return modelo.bayes(grupos, (s2c,))

tot = {k: [] for k in ("rho_crudo", "rho_justo", "cob", "falsos")}; p01 = {"crudo": 0, "justo": 0}
for seed in range(1, 13):
    g = {"__name__": "x"}; sys.argv = ["x", "x", str(seed)]; exec(SRC, g)
    rows = [dict(fecha=r[0], turno=r[1], caj=r[2], board=r[3], corr=r[4], autos=r[5], min=r[6], seg=r[7]) for r in g["auto_rows"][:1100]]
    F = [r for r in modelo.filas(rows) if r["cumple"] is not None]
    CAJ, tal = g["CAJ"], g["talento"]
    justo = ranking_justo(F, CAJ)
    crudo = sorted((sum(r["K"] * r["L"] for r in F if r["caj"] == n) / sum(r["K"] for r in F if r["caj"] == n), n) for n in CAJ)
    oj, oc = [d["clave"] for d in justo], [n for _, n in crudo]
    tot["rho_justo"].append(spearman(list(range(len(oj))), [tal[n] for n in oj]))
    tot["rho_crudo"].append(spearman(list(range(len(oc))), [tal[n] for n in oc]))
    p01["crudo"] += "P01" in oc[:3]; p01["justo"] += "P01" in oj[:3]
    media = statistics.mean(tal[n] for n in CAJ)
    for d in justo:
        tot["cob"].append(abs(d["score"] - (tal[d["clave"]] - media)) <= d["margen"])
        if abs(tal[d["clave"]] - media) < 3: tot["falsos"].append(d["lectura"] != "Dentro de lo normal")
print(f"Spearman con el talento real — crudo: {statistics.mean(tot['rho_crudo']):.2f} | justo: {statistics.mean(tot['rho_justo']):.2f} (mín {min(tot['rho_justo']):.2f})")
print(f"Lento de turnos fáciles (P01) en top 3 — crudo: {p01['crudo']}/12 | justo: {p01['justo']}/12")
print(f"Cobertura real del margen ±95 %: {sum(tot['cob']) / len(tot['cob']):.0%} ({len(tot['cob'])} intervalos)")
print(f"Falsos 'más rápido/lento' en personas sin diferencia real: {sum(tot['falsos'])}/{len(tot['falsos'])}")
