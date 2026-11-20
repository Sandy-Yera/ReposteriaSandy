"""Equivalencia: versión anterior vs optimizada con los mismos datos (año lleno, filas desordenadas y filas con errores).
Compara celda por celda el Panel completo y las columnas visibles calculadas de ambos registros."""
import sys, random, datetime as dt
from harness import build_case

OLD, NEW = sys.argv[1], sys.argv[2]
NAMES = [f"Persona {i:02d}" for i in range(1, 31)]

def dataset(seed, shuffle):
    rnd = random.Random(seed)
    auto, deliv = [], []
    for k in range(540):
        f = dt.date(2025, 3, 1) + dt.timedelta(days=k)
        a1, s1, a2, s2 = rnd.randint(80, 160), rnd.randint(120, 320), rnd.randint(50, 130), rnd.randint(120, 320)
        sd = round((a1 * s1 + a2 * s2) / (a1 + a2))
        pool = NAMES if rnd.random() < .5 else NAMES[:8]       # mitad equipos frecuentes, mitad rotación total
        caso = rnd.random()
        if caso > 0.05: auto.append((f, "AM", *rnd.sample(pool, 3), a1, s1 // 60, s1 % 60))
        if caso < 0.97: auto.append((f, "PM", *rnd.sample(pool, 3), a1 + a2 if caso > 0.05 else a2, sd // 60, sd % 60))
        for t in ("AM", "PM"):
            if rnd.random() < .95: deliv.append((f, t, f"https://drive.google.com/{k}{t}", "Sí" if rnd.random() < .8 else "No"))
    # filas con errores típicos y repetidas
    auto += [(dt.date(2026, 12, 1), "AM", *NAMES[:3], 100, 3, 0), ("16/11", "AM", *NAMES[:3], 100, 3, 0),
             (dt.date(2026, 12, 2), "pm", *NAMES[3:6], "120", 325, None), (dt.date(2026, 12, 3), "AM", "=1/0", *NAMES[1:3], 100, 2, 70),
             (dt.date(2026, 12, 4), "AM", *NAMES[:3], 100, 3, 0), (dt.date(2026, 12, 4), "AM", *NAMES[:3], 100, 3, 0)]  # repetida sin PM
    deliv += [(dt.date(2026, 12, 1), "AM", "https://x", "si"), (dt.date(2026, 12, 1), "AM", "Sí", None)]
    if shuffle: rnd.shuffle(auto); rnd.shuffle(deliv)
    return auto[:1030], deliv[:1030]

def snapshot(wb):
    out = {}
    p = wb["Panel"]
    for row in p.iter_rows(min_row=4, max_row=60, min_col=2, max_col=13):
        for c in row:
            if c.value not in (None, ""): out[f"Panel!{c.coordinate}"] = c.value
    a = wb["Registro Auto"]
    for r in range(8, 1038):
        for col in "JKLMNOP": out[f"Auto!{col}{r}"] = a[f"{col}{r}"].value
    d = wb["Registro Delivery"]
    for r in range(8, 1038):
        for col in "EG": out[f"Deliv!{col}{r}"] = d[f"{col}{r}"].value
    return out

def same(x, y):
    if isinstance(x, (int, float)) and isinstance(y, (int, float)): return abs(x - y) < 1e-9
    if hasattr(x, "total_seconds") and hasattr(y, "total_seconds"): return abs(x.total_seconds() - y.total_seconds()) < 1e-6
    return x == y

fails, checks = [], 0
scen = [({}, 1, False), ({"L7": 3}, 2, True), ({"F7": "PM", "L7": 2}, 3, True), ({"B7": dt.date(2025, 6, 1), "D7": dt.date(2025, 6, 30), "L7": 1}, 4, False),
        ({"J7": "Persona 03", "F7": "AM"}, 5, True), ({"H7": "Delivery"}, 6, False), ({"L7": 1}, 7, True)]
for flt, seed, sh in scen:
    auto, deliv = dataset(seed, sh)
    flt = {"L7": 3, **flt}
    _, w_old = build_case(OLD, "eq_old.xlsx", auto, deliv, NAMES, flt)
    _, w_new = build_case(NEW, "eq_new.xlsx", auto, deliv, NAMES, flt)
    so, sn = snapshot(w_old), snapshot(w_new)
    a = w_old["Registro Auto"]
    distintos = len({(a[f"D{r}"].value, a[f"E{r}"].value, a[f"F{r}"].value) for r in range(8, 1038) if a[f"W{r}"].value == 1 and a[f"S{r}"].value})
    if distintos > 400:   # la versión anterior tenía tope de 400 tríos: su ranking de tríos no es comparable
        for d in (so, sn):
            for k in [k for k in d if k.startswith("Panel!") and 43 <= int("".join(ch for ch in k.split("!")[1] if ch.isdigit())) <= 47]: d.pop(k)
        print(f"   ({distintos} tríos distintos > 400: ranking de tríos excluido, verificado aparte)")
    keys = set(so) | set(sn)
    diffs = [(k, so.get(k), sn.get(k)) for k in sorted(keys) if not same(so.get(k), sn.get(k))]
    checks += len(keys)
    tag = f"[filtros {flt} semilla {seed} desordenado={sh}]"
    print(f"{tag}: {len(keys)} celdas, {len(diffs)} diferencias")
    for d in diffs[:12]: print("    ", d)
    fails += diffs
print(f"EQUIVALENCIA: {checks} celdas comparadas, {len(fails)} diferencias")
