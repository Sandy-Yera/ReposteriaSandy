"""Modelo independiente en Python: calcula lo que el Excel debería mostrar (datos limpios)."""
from collections import defaultdict

FAR = 10**9


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
            elif K > 0: L = J if amA == 0 else (J * r["autos"] - amP) / K
        cumple = None if L is None or round(L) <= 0 else ("Sí" if round(L) < 240 else "No")
        O = J if (r["turno"] == "PM" and not dup) else None
        P = None
        if O is not None:
            P = ("Cumplió" if round(O) < 240 else "Falló") + ("" if cnt[(r["fecha"], "AM")] else " (solo PM)")
        out.append(dict(r, i=i, J=J, K=K, L=L, cumple=cumple, O=O, P=P, nivel=nivel(L) if L is not None else None))
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
    W = [r for r in V if tm(r)]
    rk = {}
    for role in ("caj", "board", "corr"):
        lst = []
        for idx, name in enumerate(personal):
            rr = [r for r in W if r[role] == name]
            a_ = sum(r["K"] for r in rr)
            if len(rr) >= minimo and a_ > 0:
                lst.append((sum(r["K"] * r["L"] for r in rr) / a_, idx, name, len(rr), sum(r["cumple"] == "Sí" for r in rr) / len(rr)))
        rk[role] = sorted(lst)[:5]
    res["rank"] = rk
    trios, order = defaultdict(list), []
    for r in W:
        if r["caj"] and r["board"] and r["corr"]:
            k = (r["caj"], r["board"], r["corr"])
            if k not in trios: order.append(k)
            trios[k].append(r)
    tl = []
    for idx, k in enumerate(order):
        rr = trios[k]; a_ = sum(r["K"] for r in rr)
        if len(rr) >= minimo and a_ > 0:
            tl.append((sum(r["K"] * r["L"] for r in rr) / a_, idx, k, len(rr), sum(r["cumple"] == "Sí" for r in rr) / len(rr)))
    res["trios"] = sorted(tl)[:5]
    days = sorted([(r["O"], r["i"], r) for r in F if in_range(r["fecha"]) and r["O"] is not None])[:5]
    res["dias_top"] = days
    if canal == "Delivery":
        for k in ("autos", "t", "pct", "turnos", "dias"): res[k] = "—"
    if canal == "Auto":
        res["del"] = "—"; res["del_n"] = "—"
    return res
