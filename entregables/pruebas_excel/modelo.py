"""Modelo independiente en Python: calcula lo que el Excel debería mostrar (datos limpios)."""
from collections import defaultdict

FAR = 10**9
DUDOSO = " · dudoso (PM con pocos autos)"
Z95 = 1.96


def lectura(score, margen):
    if margen is None: return "Pocos datos"
    if score + margen < 0: return "Más rápido que lo normal"
    if score - margen > 0: return "Más lento que lo normal"
    return "Dentro de lo normal"


def bayes(grupos, s2_comun=None):
    """grupos: lista de (orden, clave, filas) ya filtrados por mínimo de turnos. Cada fila trae res = segundos vs lo normal.
    Modelo de efectos aleatorios por turno (ANOVA de un factor, estimador de momentos):
    res_t = theta_i + e_t,  e_t ~ (0, s2),  theta_i ~ (0, tau2).  Puntaje = estimación bayesiana empírica S_i/(n_i+k), k = s2/tau2.
    Margen 95% = 1,96 * raíz(s2/(n_i+k))."""
    st = [(o, c, rr, len(rr), sum(r["res"] for r in rr), sum(r["res"] ** 2 for r in rr), todas) for o, c, rr, todas in grupos]
    N = sum(x[3] for x in st); m = len(st)
    St = sum(x[4] for x in st); Ct = sum(x[4] ** 2 / x[3] for x in st); Qt = sum(x[5] for x in st); N2 = sum(x[3] ** 2 for x in st)
    s2 = (Qt - Ct) / (N - m) if N - m > 0 else None
    if s2_comun is not None: s2 = s2_comun[0]
    tau2 = None
    if m >= 2 and s2 is not None:
        msb = (Ct - St ** 2 / N) / (m - 1); n0 = (N - N2 / N) / (m - 1)
        tau2 = max(0.0, (msb - s2) / n0)
    k = 5.0 if tau2 is None else (min(s2 / tau2, 20.0) if tau2 > 0 else 20.0)
    out = []
    for o, c, rr, n, S, Q, todas in st:
        W = sum(r["K"] for r in todas)
        score = S / (n + k)
        margen = Z95 * (s2 / (n + k)) ** 0.5 if s2 is not None else None
        out.append(dict(score=score, orden=o, clave=c, n=len(todas), W=W, t=sum(r["K"] * r["L"] for r in todas) / W,
                        pct=sum(r["cumple"] == "Sí" for r in todas) / len(todas), margen=margen, lectura=lectura(score, margen)))
    return sorted(out, key=lambda d: (d["score"], d["orden"]))


def nivel(s):
    s = round(s)
    if s <= 0: return "Revisar datos"
    for lim, n in [(60, "Excelencia máxima"), (120, "Excelencia"), (180, "Muy bueno"), (240, "Cumple"), (300, "No cumple (alerta)")]:
        if s < lim: return n
    return "No cumple"


def filas(auto):
    """auto: lista de dicts fecha,turno,caj,board,corr,autos,min,seg (en orden de hoja). Devuelve filas calculadas."""
    cnt = defaultdict(int)
    for r in auto: cnt[(r["fecha"], r["turno"])] += 1
    am = defaultdict(lambda: [0, 0.0])  # autos, prod
    for r in auto:
        if r["turno"] == "AM":
            am[r["fecha"]][0] += r["autos"]; am[r["fecha"]][1] += r["autos"] * (r["min"] * 60 + r["seg"])
    out = []
    for i, r in enumerate(auto):
        J = r["min"] * 60 + r["seg"]
        dup = cnt[(r["fecha"], r["turno"])] > 1
        amA, amP = am[r["fecha"]]
        K = r["autos"] if r["turno"] == "AM" else r["autos"] - amA
        L = None
        if not dup:
            if r["turno"] == "AM": L = J
            elif cnt[(r["fecha"], "AM")] > 1: L = None     # AM repetido: el PM no se puede calcular
            elif K > 0: L = J if amA == 0 else (J * r["autos"] - amP) / K
        cumple = None if L is None or round(L) <= 0 else ("Sí" if round(L) < 240 else "No")
        margen_pm = 0.5 * (r["autos"] + amA) / K if (r["turno"] == "PM" and amA > 0 and K > 0) else 0
        O = J if (r["turno"] == "PM" and not dup) else None
        P = None
        if O is not None:
            P = ("Cumplió" if round(O) < 240 else "Falló") + ("" if cnt[(r["fecha"], "AM")] else " (solo PM)")
        niv = None
        if L is not None:
            niv = nivel(L) + (DUDOSO if (margen_pm >= 5 and abs(round(L) - 240) < margen_pm) else "")
        out.append(dict(r, i=i, J=J, K=K, L=L, cumple=cumple, O=O, P=P, nivel=niv))
    return out


def panel(auto, deliv, personal, desde=None, hasta=None, turno="Total", persona="Todas", minimo=3, canal="Ambos"):
    F = filas(auto)
    d0 = desde or -FAR; d1 = hasta or FAR
    if desde and hasta and desde > hasta: d0, d1 = hasta, desde
    inr = lambda f: d0 <= f <= d1 if not isinstance(d0, int) else (f <= d1 if not isinstance(d1, int) else True)

    def in_range(f):
        if desde is None and hasta is None: return True
        lo = min(desde, hasta) if desde and hasta else desde
        hi = max(desde, hasta) if desde and hasta else hasta
        return (lo is None or f >= lo) and (hi is None or f <= hi)
    V = [r for r in F if in_range(r["fecha"]) and r["cumple"] is not None]
    tm = lambda r: turno == "Total" or r["turno"] == turno
    pm = lambda r: persona == "Todas" or persona in (r["caj"], r["board"], r["corr"])
    res = {}
    kp = [r for r in V if tm(r) and pm(r)]
    au = sum(r["K"] for r in kp)
    res["autos"] = au
    res["t"] = sum(r["K"] * r["L"] for r in kp) / au if au else None
    res["pct"] = sum(r["cumple"] == "Sí" for r in kp) / len(kp) if kp else None
    res["turnos"] = len(kp)
    D = [r for r in F if in_range(r["fecha"]) and r["P"]]
    res["dias"] = sum(r["P"].startswith("Cumplió") for r in D) / len(D) if D else None
    dv = [x for x in deliv if in_range(x["fecha"]) and (turno == "Total" or x["turno"] == turno) and x["conc"] in ("Sí", "No")]
    res["del"] = sum(x["conc"] == "Sí" for x in dv) / len(dv) if dv else None
    res["del_n"] = len(dv)
    tab = {}
    for t in ("AM", "PM", "Total"):
        rows = [r for r in V if pm(r) and (t == "Total" or r["turno"] == t)]
        a_ = sum(r["K"] for r in rows)
        dd = [x for x in deliv if in_range(x["fecha"]) and (t == "Total" or x["turno"] == t) and x["conc"] in ("Sí", "No")]
        tab[t] = dict(autos=a_, t=sum(r["K"] * r["L"] for r in rows) / a_ if a_ else None,
                      pct=sum(r["cumple"] == "Sí" for r in rows) / len(rows) if rows else None, n=len(rows),
                      dl=sum(x["conc"] == "Sí" for x in dd) / len(dd) if dd else None)
    res["tabla"] = tab
    # ---- Ranking ajustado (comparación justa) ----
    # Contexto g = (turno, día de la semana). "Lo normal" B_g = promedio por turno de los turnos válidos de TODO el historial en g.
    # Cada turno se compara contra los DEMÁS del mismo contexto (sin incluirse): r = (L - B_g) * N_g / (N_g - n_gi),
    # con N_g y n_gi contados sobre todos los turnos programados (fecha y turno válidos), sin depender del tiempo ni del filtro.
    U = F
    ctx = lambda r: (r["turno"], r["fecha"].weekday())
    Ng, cnt_role, Sv, Nv = {}, {}, {}, {}
    for r in U:
        g = ctx(r); Ng[g] = Ng.get(g, 0) + 1
        for role in ("caj", "board", "corr"):
            cnt_role[(role, g, r[role].lower())] = cnt_role.get((role, g, r[role].lower()), 0) + 1
        tk = (r["caj"] + " · " + r["board"] + " · " + r["corr"]).lower()
        cnt_role[("trio", g, tk)] = cnt_role.get(("trio", g, tk), 0) + 1
    for r in F:
        if r["cumple"] is None: continue
        g = ctx(r); Sv[g] = Sv.get(g, 0) + r["L"]; Nv[g] = Nv.get(g, 0) + 1
    def loo(r, role, key):
        g = ctx(r); n_ = Ng[g] - cnt_role[(role, g, key.lower())]
        return (r["L"] - Sv[g] / Nv[g]) * Ng[g] / n_ if n_ > 0 else None
    W = [r for r in V if tm(r)]
    rk = {}
    seen = set(); personas = []
    for idx, name in enumerate(personal):
        if name and name.strip() and name.lower() not in seen: seen.add(name.lower()); personas.append((idx, name))
    for role in ("caj", "board", "corr"):
        grupos = []
        for idx, name in personas:
            todas = [r for r in W if r[role] == name]
            comp = [dict(r, res=x) for r in todas for x in [loo(r, role, name)] if x is not None]
            if len(comp) >= minimo and sum(r["K"] for r in todas) > 0: grupos.append((idx, name, comp, todas))
        # varianza dentro común: todos los turnos comparables de la estación (no solo los que llegan al mínimo)
        todos = []
        for idx, name in personas:
            comp = [x for r in W if r[role] == name for x in [loo(r, role, name)] if x is not None]
            if comp: todos.append(comp)
        Na = sum(len(c) for c in todos); ma = len(todos)
        s2c = (sum(x * x for c in todos for x in c) - sum(sum(c) ** 2 / len(c) for c in todos)) / (Na - ma) if Na - ma > 0 else None
        rk[role] = bayes(grupos, (s2c,))[:5]
    res["rank"] = rk
    trios, order = defaultdict(list), []
    for r in W:
        if r["caj"] and r["board"] and r["corr"]:
            k = (r["caj"], r["board"], r["corr"])
            if k not in trios: order.append(k)
            trios[k].append(r)
    grupos = []
    for idx, k in enumerate(order):
        todas = trios[k]
        comp = [dict(r, res=x) for r in todas for x in [loo(r, "trio", " · ".join(k))] if x is not None]
        if len(todas) >= minimo and len(comp) >= minimo and sum(r["K"] for r in todas) > 0: grupos.append((idx, k, comp, todas))
    res["trios"] = bayes(grupos)[:5]
    # Mejores días: cada día se compara con lo normal de SU día de la semana (todo el historial, sin incluirse a sí mismo)
    dsum, dn = {}, {}
    for r in F:
        if r["O"] is not None:
            w = r["fecha"].weekday(); dsum[w] = dsum.get(w, 0) + r["O"]; dn[w] = dn.get(w, 0) + 1
    days = []
    for r in F:
        if r["O"] is None or not in_range(r["fecha"]): continue
        w = r["fecha"].weekday()
        if dn[w] - 1 <= 0: continue
        dev = (r["O"] - dsum[w] / dn[w]) * dn[w] / (dn[w] - 1)
        days.append((dev, r["i"], r))
    days = sorted(days)[:5]
    res["dias_top"] = days
    if canal == "Delivery":
        for k in ("autos", "t", "pct", "turnos", "dias"): res[k] = "—"
    if canal == "Auto":
        res["del"] = "—"; res["del_n"] = "—"
    return res
