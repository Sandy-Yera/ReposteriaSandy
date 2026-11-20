"""Prueba de extremo a extremo del ciclo de vida real (LibreOffice vía UNO), paso a paso.
Después de CADA paso: (1) ninguna hoja muestra errores, (2) todo el Panel coincide con el modelo independiente en Python
calculado desde lo que hay escrito en ese momento, (3) lo específico del paso.
Uso: python test_vida_real.py archivo_entregado.xlsx"""
import sys, os, glob, time, subprocess, tempfile, random, shutil, datetime as dt
import uno
from com.sun.star.beans import PropertyValue
sys.path.insert(0, glob.glob("/root/.claude/skills/synced/*/xlsx/scripts")[0])
from office.soffice import get_soffice_env
import modelo

SRC = sys.argv[1]
shutil.copy(SRC, "vida.xlsx")
out, pasos = [], []
def check(n, c, det=""): out.append((pasos[-1] if pasos else "", n, bool(c), det))
def PV(n, v): p = PropertyValue(); p.Name = n; p.Value = v; return p
BASE = dt.date(1899, 12, 30)
def serial(d): return (d - BASE).days
def desde_serial(x): return BASE + dt.timedelta(days=int(x))

# ---------------- LibreOffice ----------------
prof = tempfile.mkdtemp()
proc = subprocess.Popen(["soffice", f"-env:UserInstallation=file://{prof}", "--headless", "--invisible", "--norestore",
                         "--accept=pipe,name=vidareal;urp;"], env=get_soffice_env(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
local = uno.getComponentContext()
res = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
for _ in range(60):
    try: ctx = res.resolve("uno:pipe,name=vidareal;urp;StarOffice.ComponentContext"); break
    except Exception: time.sleep(1)
cp = ctx.ServiceManager.createInstanceWithContext("com.sun.star.configuration.ConfigurationProvider", ctx)
upd = cp.createInstanceWithArguments("com.sun.star.configuration.ConfigurationUpdateAccess", (PV("nodepath", "/org.openoffice.Office.Calc/Formula/Calculation"),))
upd.setPropertyValue("UseThreadedCalculationForFormulaGroups", False); upd.commitChanges()
desktop = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
disp = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.DispatchHelper", ctx)
def abrir(path):
    d = desktop.loadComponentFromURL(uno.systemPathToFileUrl(os.path.abspath(path)), "_blank", 0, (PV("Hidden", True),))
    d.calculateAll(); return d
doc = abrir("vida.xlsx")
S = lambda n: doc.Sheets.getByName(n)
def cel(s, ref): return S(s).getCellRangeByName(ref)
def txt(s, ref): return cel(s, ref).getString()
def num(s, ref):
    c = cel(s, ref)
    return c.getValue() if c.getString() not in ("", "—") and c.getType().value != "TEXT" and not c.getString().startswith(("Sin", "—")) else None
def escribir(s, ref, texto):
    """Como un usuario: clic en la celda y escribir (respeta protección y validación)."""
    ws = S(s); ctrl = doc.CurrentController; ctrl.setActiveSheet(ws)
    ctrl.select(ws.getCellRangeByName(ref)); disp.executeDispatch(ctrl.Frame, ".uno:EnterString", "", 0, (PV("StringName", str(texto)),))
def pegar(s, ref, v):
    """Como Ctrl+V (salta la lista desplegable)."""
    c = cel(s, ref)
    if v is None or v == "": c.setString("")
    elif isinstance(v, dt.date): c.setValue(serial(v))
    elif isinstance(v, (int, float)): c.setValue(v)
    else: c.setString(v)
def recalc(): doc.calculate()
def errores():
    bad = []
    for n in ["Inicio", "Panel", "Registro Auto", "Registro Delivery", "Horarios", "Reportes", "Personal", "Calculos"]:
        ws = S(n); cur = ws.createCursor(); cur.gotoEndOfUsedArea(False)
        for i, row in enumerate(ws.getCellRangeByPosition(0, 0, cur.RangeAddress.EndColumn, cur.RangeAddress.EndRow).getDataArray()):
            for v in row:
                if isinstance(v, str) and v.startswith(("#DIV/0!", "#VALUE!", "#REF!", "#NAME?", "#N/A", "#NUM!", "#NULL!", "Err:")): bad.append((n, i + 1, v))
    return bad[:5]

# ---------------- lectura del estado y modelo independiente ----------------
def leer_auto(desde_fila=8, hasta_fila=1037):
    """Filas con datos estructuralmente válidos (las inválidas el Excel las excluye y se comprueban aparte)."""
    ws = S("Registro Auto"); data = ws.getCellRangeByName(f"B{desde_fila}:I{hasta_fila}").getDataArray()
    filas = []
    for row in data:
        f, t, c, b, co, a, m, sg = row
        if not isinstance(f, float) or t not in ("AM", "PM") or not all(isinstance(x, str) and x for x in (c, b, co)): continue
        if not isinstance(a, float) or a != int(a) or a < 0: continue
        if not isinstance(m, float) and m != "": continue
        if not isinstance(sg, float) and sg != "": continue
        m = int(m) if m != "" else 0; sg = int(sg) if sg != "" else 0
        if m == 0 and sg == 0 or m > 19: continue
        filas.append(dict(fecha=desde_serial(f), turno=t, caj=c, board=b, corr=co, autos=int(a), min=m, seg=sg))
    return filas
def leer_deliv():
    data = S("Registro Delivery").getCellRangeByName("B8:F1037").getDataArray()
    vis, filas = {}, []
    for f, t, _l, _a, conc in data:
        if isinstance(f, float) and t in ("AM", "PM") and conc in ("Sí", "No"):
            filas.append(dict(fecha=desde_serial(f), turno=t, conc=conc)); vis[(f, t)] = vis.get((f, t), 0) + 1
    return [x for x in filas if vis[(serial(x["fecha"]), x["turno"])] == 1]
def personal(): return [r[0] for r in S("Personal").getCellRangeByName("B8:B107").getDataArray()]
def filtros():
    g = lambda r: txt("Panel", r)
    d0 = cel("Panel", "B7").getValue() if g("B7") else None; d1 = cel("Panel", "D7").getValue() if g("D7") else None
    return dict(desde=desde_serial(d0) if d0 else None, hasta=desde_serial(d1) if d1 else None, turno=g("F7") or "Total",
                persona=g("J7") or "Todas", minimo=int(cel("Panel", "L7").getValue()) if g("L7") else 3, canal=g("H7") or "Ambos")

def igual(a, b, tol):
    if b is None: return a in (None, "", "—")
    if isinstance(b, (int, float)) and not isinstance(b, bool): return isinstance(a, (int, float)) and abs(a - b) <= tol
    return a == b          # texto, fechas y tríos: igualdad exacta
def verificar_panel(etq):
    """Compara TODO el Panel con el modelo independiente."""
    recalc()
    e = errores()
    check(f"{etq}: ninguna hoja muestra errores", not e, e)
    F = filtros()
    exp = modelo.panel(leer_auto(), leer_deliv(), personal(), **F)
    fallos = []
    def cmp(lab, got, want, tol=1e-6):
        if not igual(got, want, tol): fallos.append((lab, got, want))
    v = lambda ref: num("Panel", ref)
    t = lambda ref: (v(ref) * 86400) if v(ref) is not None else None
    cmp("autos", v("B11"), exp["autos"] if exp["autos"] != "—" else None)
    cmp("tiempo", t("E11"), exp["t"] if exp["t"] != "—" else None, 0.6)
    cmp("% meta", v("H11"), exp["pct"] if exp["pct"] != "—" else None)
    cmp("% delivery", v("K11"), exp["del"] if exp["del"] != "—" else None)
    cmp("% días", v("B16"), exp["dias"] if exp["dias"] != "—" else None)
    cmp("turnos", v("H16"), exp["turnos"] if exp["turnos"] != "—" else None)
    cmp("turnos delivery", v("K16"), exp["del_n"] if exp["del_n"] != "—" else None)
    if F["canal"] == "Ambos":
        for col, tt in zip("FHJ", ("AM", "PM", "Total")):
            q = exp["tabla"][tt]
            cmp(f"tabla {tt} autos", v(f"{col}23"), q["autos"]); cmp(f"tabla {tt} tiempo", t(f"{col}24"), q["t"], 0.6)
            cmp(f"tabla {tt} %meta", v(f"{col}26"), q["pct"]); cmp(f"tabla {tt} n", v(f"{col}27"), q["n"]); cmp(f"tabla {tt} delivery", v(f"{col}28"), q["dl"])
    for role, r0 in (("caj", 35), ("board", 43), ("corr", 51)):
        lst = exp["rank"][role]
        for k in range(5):
            rr = r0 + k
            if k < len(lst):
                d = lst[k]
                cmp(f"{role}{k+1} nombre", txt("Panel", f"C{rr}"), d["clave"]); cmp(f"{role}{k+1} turnos", v(f"E{rr}"), d["n"])
                cmp(f"{role}{k+1} tiempo", t(f"F{rr}"), d["t"], 0.6); cmp(f"{role}{k+1} ajustado", v(f"G{rr}"), d["score"], 0.005)
                cmp(f"{role}{k+1} margen", v(f"I{rr}"), d["margen"], 0.005); cmp(f"{role}{k+1} lectura", txt("Panel", f"J{rr}"), d["lectura"])
            else:
                cmp(f"{role}{k+1} vacío", txt("Panel", f"C{rr}"), "Sin datos suficientes" if k == 0 else "")
    for k in range(5):
        rr = 60 + k
        if k < len(exp["trios"]):
            d = exp["trios"][k]
            cmp(f"trío{k+1}", (txt("Panel", f"C{rr}"), txt("Panel", f"D{rr}"), txt("Panel", f"E{rr}")), d["clave"])
            cmp(f"trío{k+1} ajustado", v(f"H{rr}"), d["score"], 0.005); cmp(f"trío{k+1} lectura", txt("Panel", f"J{rr}"), d["lectura"])
        else:
            cmp(f"trío{k+1} vacío", txt("Panel", f"F{rr}"), "")
    for k in range(7):
        rr = 69 + k
        if k < len(exp["dias_top"]):
            dev, _, r = exp["dias_top"][k]
            cmp(f"día{k+1} fecha", desde_serial(cel("Panel", f"C{rr}").getValue()), r["fecha"])
            cmp(f"día{k+1} desvío", v(f"F{rr}"), dev, 0.005); cmp(f"día{k+1} resultado", txt("Panel", f"J{rr}"), r["P"])
    check(f"{etq}: TODO el Panel coincide con el cálculo independiente (tarjetas, AM/PM, rankings, días)", not fallos, fallos[:6])

def paso(nombre): pasos.append(nombre); print("·", nombre, flush=True)

# ================= 1. Primera vez: Modo aprendizaje =================
paso("1. Primera vez (Modo aprendizaje)")
check("El archivo llega en Modo aprendizaje", txt("Inicio", "C6") == "Sí" and "ACTIVO" in txt("Inicio", "E7"), txt("Inicio", "E7"))
check("Panel con aviso, 56 turnos de ejemplo y explicaciones", "MODO APRENDIZAJE" in txt("Panel", "B4") and txt("Panel", "H16") == "56" and txt("Panel", "B13").startswith("Cuántos"))
check("Los registros reales están vacíos", all(txt("Registro Auto", f"B{r}") == "" for r in range(8, 1038, 37)))
def verificar_ejemplos(etq):
    recalc()
    check(f"{etq}: sin errores", not errores(), errores())
    F = filtros()
    exp = modelo.panel(leer_auto(1039, 1107), [], ["Ana (ej.)", "Beto (ej.)", "Carla (ej.)", "Dani (ej.)", "Eli (ej.)", "Fede (ej.)"], **{**F, "canal": "Auto"})
    check(f"{etq}: tarjetas y ranking de los ejemplos coinciden con el cálculo independiente",
          abs(num("Panel", "H16") - exp["turnos"]) < 1e-9 and txt("Panel", "C35") == exp["rank"]["caj"][0]["clave"] and txt("Panel", "J35") == exp["rank"]["caj"][0]["lectura"],
          (num("Panel", "H16"), exp["turnos"], txt("Panel", "C35"), exp["rank"]["caj"][0]["clave"]))
verificar_ejemplos("Ejemplos sin filtros")
escribir("Panel", "F7", "PM"); verificar_ejemplos("Ejemplos con Turno=PM")
escribir("Panel", "F7", "Total"); escribir("Panel", "J7", "Ana (ej.)"); recalc()
check("Ejemplos con Persona=Ana (ej.): menos turnos", 0 < num("Panel", "H16") < 56, num("Panel", "H16"))
escribir("Panel", "J7", "Todas")

# ================= 2. Desactivar =================
paso("2. Desactivar Modo aprendizaje")
escribir("Inicio", "C6", "No"); recalc()
check("Inicio dice 'archivo LIMPIO'", "LIMPIO" in txt("Inicio", "E7"))
check("Panel vacío y sin aviso", txt("Panel", "H16") == "0" and txt("Panel", "B4") == "", (txt("Panel", "H16"), txt("Panel", "B4")))
check("Zona de ejemplos, guías y horario de ejemplo ocultos", txt("Registro Auto", "B1039") == "" and txt("Registro Auto", "B5") == "" and txt("Horarios", "V16") == "")
verificar_panel("Archivo limpio")

# ================= 3. Agregar personal y la primera semana (escribiendo como usuario) =================
paso("3. Agregar personal y primera semana")
EQUIPO = ["Ana", "Bruno", "Carla", "Diego", "Elena", "Felipe", "Gabriela", "Hugo"]
for i, n in enumerate(EQUIPO): escribir("Personal", f"B{8+i}", n)
recalc()
check("Los nombres quedaron en Personal", personal()[:8] == EQUIPO)
rnd = random.Random(12)
def turno_datos(f, t, am_autos=None, am_seg=None):
    c, b, co = rnd.sample(EQUIPO, 3)
    if t == "AM":
        a, s = rnd.randint(80, 150), rnd.randint(140, 290); return (f, t, c, b, co, a, s), (a, s)
    a2, s2 = rnd.randint(50, 120), rnd.randint(140, 290)
    tot = am_autos + a2; sd = round((am_autos * am_seg + a2 * s2) / tot)
    return (f, t, c, b, co, tot, sd), None
fila = [8]; dfila = [8]
def anotar_dia(f, ui=True):
    am, (a, s) = turno_datos(f, "AM"); pm, _ = turno_datos(f, "PM", a, s)
    for reg in (am, pm):
        vals = [reg[0].isoformat(), reg[1], reg[2], reg[3], reg[4], reg[5], reg[6] // 60, reg[6] % 60]
        for col, val in zip("BCDEFGHI", vals):
            (escribir if ui else pegar)("Registro Auto", f"{col}{fila[0]}", val if ui else (reg[0] if col == "B" else val))
        fila[0] += 1
    for t in ("AM", "PM"):
        for col, val in zip("BCDF", [f.isoformat() if ui else f, t, f"https://drive.google.com/{f}{t}", "No" if rnd.random() < .2 else "Sí"]):
            (escribir if ui else pegar)("Registro Delivery", f"{col}{dfila[0]}", val)
        dfila[0] += 1
INICIO = dt.date(2026, 10, 5)   # lunes
for k in range(7): anotar_dia(INICIO + dt.timedelta(days=k), ui=True)
recalc()
check("Las 14 filas escritas a mano quedaron válidas (Nivel con nota)", all(not txt("Registro Auto", f"M{r}").startswith(("Revisar", "Falta")) and txt("Registro Auto", f"M{r}") for r in range(8, 22)),
      [txt("Registro Auto", f"M{r}") for r in range(8, 22) if txt("Registro Auto", f"M{r}").startswith(("Revisar", "Falta")) or not txt("Registro Auto", f"M{r}")][:3])
check("Delivery: 14 filas con Estado OK", all(txt("Registro Delivery", f"G{r}").startswith("OK") for r in range(8, 22)))
verificar_panel("Primera semana")

# ================= 4. Editar =================
paso("4. Editar datos ya escritos")
antes = txt("Registro Auto", "L10")
escribir("Registro Auto", "I10", "59"); recalc()
check("Editar segundos de un AM cambia su tiempo real", txt("Registro Auto", "L10") != antes, (antes, txt("Registro Auto", "L10")))
verificar_panel("Tras editar un tiempo")
escribir("Registro Auto", "D11", "Hugo"); verificar_panel("Tras cambiar un cajero")
escribir("Registro Delivery", "F9", "No" if txt("Registro Delivery", "F9") == "Sí" else "Sí"); verificar_panel("Tras cambiar un Sí/No de Delivery")

# ================= 5. Borrar y volver a agregar =================
paso("5. Borrar y volver a agregar")
guardado = [cel("Registro Auto", f"{c}13").getString() if c in "BCDEF" else cel("Registro Auto", f"{c}13").getValue() for c in "BCDEFGHI"]
fecha13 = cel("Registro Auto", "B13").getValue()
for c in "BCDEFGHI": pegar("Registro Auto", f"{c}13", "")
recalc()
check("Al vaciar un turno PM su fila queda en blanco", txt("Registro Auto", "M13") == "" and txt("Registro Auto", "P13") == "")
verificar_panel("Tras borrar un turno PM")
pegar("Registro Auto", "B13", fecha13)
for c, v in zip("CDEFGHI", guardado[1:]): pegar("Registro Auto", f"{c}13", v)
verificar_panel("Tras volver a escribirlo")
for c in "BCDF": pegar("Registro Delivery", f"{c}12", "")
verificar_panel("Tras borrar una fila de Delivery")

# ================= 6. Errores típicos y su corrección =================
paso("6. Errores y correcciones")
for c, v in zip("BCDEFGHI", [INICIO + dt.timedelta(days=2), "AM", "Ana", "Bruno", "Carla", 100, 3, 0]): pegar("Registro Auto", f"{c}{fila[0]}", v)
recalc()
dup_rows = [r for r in range(8, fila[0] + 1) if txt("Registro Auto", f"M{r}") == "Revisar: fila repetida"]
check("Fila repetida: se marcan las dos", len(dup_rows) == 2, dup_rows)
check("…y el aviso del Panel la cuenta", "por revisar" in txt("Panel", "B4"), txt("Panel", "B4"))
verificar_panel("Con una fila repetida")
for c in "BCDEFGHI": pegar("Registro Auto", f"{c}{fila[0]}", "")
verificar_panel("Tras borrar la repetida")
seg_ok = cel("Registro Auto", "H9").getValue()
pegar("Registro Auto", "H9", 325); recalc()
check("Escribir 325 en minutos: 'Revisar: tiempo no válido'", txt("Registro Auto", "M9") == "Revisar: tiempo no válido", txt("Registro Auto", "M9"))
verificar_panel("Con un tiempo mal escrito")
pegar("Registro Auto", "H9", seg_ok); recalc()
check("Al corregirlo vuelve a tener nota", not txt("Registro Auto", "M9").startswith("Revisar"), txt("Registro Auto", "M9"))
verificar_panel("Tras corregir el tiempo")

# ================= 7. Completar el mes =================
paso("7. Completar el mes (3 semanas más)")
for k in range(7, 28): anotar_dia(INICIO + dt.timedelta(days=k), ui=False)
verificar_panel("Mes completo")

# ================= 8. Filtros =================
paso("8. Filtros del Panel")
def filtro(**kw):
    for ref, v in kw.items(): pegar("Panel", ref, v)
filtro(B7=INICIO + dt.timedelta(days=9), D7=INICIO + dt.timedelta(days=9)); verificar_panel("Un solo día")
filtro(B7=INICIO + dt.timedelta(days=7), D7=INICIO + dt.timedelta(days=13)); verificar_panel("Una semana")
dias_sem = [desde_serial(cel("Panel", f"C{69+k}").getValue()) for k in range(7) if txt("Panel", f"C{69+k}")]
check("Con una semana elegida, Mejores días muestra exactamente sus 7 días", len(dias_sem) == 7 and len(set(dias_sem)) == 7 and
      all(INICIO + dt.timedelta(days=7) <= d <= INICIO + dt.timedelta(days=13) for d in dias_sem), dias_sem)
filtro(B7="", D7=""); escribir("Panel", "F7", "PM"); verificar_panel("Turno PM")
escribir("Panel", "F7", "AM"); verificar_panel("Turno AM")
escribir("Panel", "F7", "Total"); escribir("Panel", "J7", "Carla"); verificar_panel("Persona Carla")
escribir("Panel", "J7", "Todas"); escribir("Panel", "H7", "Auto"); verificar_panel("Canal Auto")
escribir("Panel", "H7", "Delivery"); verificar_panel("Canal Delivery")
escribir("Panel", "H7", "Ambos"); escribir("Panel", "L7", "1"); verificar_panel("Mínimo 1 turno")
escribir("Panel", "L7", "6"); verificar_panel("Mínimo 6 turnos")
escribir("Panel", "L7", "3"); verificar_panel("Filtros normales")

# ================= 9. Personal: alta, cambio de nombre, baja =================
paso("9. Cambios en Personal")
escribir("Personal", "B16", "Iván")
for c, v in zip("BCDEFGHI", [INICIO + dt.timedelta(days=28), "AM", "Iván", "Ana", "Bruno", 110, 3, 5]): pegar("Registro Auto", f"{c}{fila[0]}", v)
fila[0] += 1
verificar_panel("Persona nueva con su primer turno")
pegar("Personal", "B8", "Ana P."); recalc()
check("Renombrar 'Ana' en Personal: el aviso marca sus turnos como nombre fuera de la lista", "no están en Personal" in txt("Panel", "B4"), txt("Panel", "B4"))
verificar_panel("Con 'Ana' renombrada")
pegar("Personal", "B8", "Ana"); verificar_panel("Nombre restaurado")
pegar("Personal", "B9", ""); recalc()
check("Borrar a 'Bruno' de Personal: el aviso lo detecta", "no están en Personal" in txt("Panel", "B4"))
verificar_panel("Sin Bruno en Personal")
pegar("Personal", "B9", "Bruno"); verificar_panel("Bruno repuesto")

# ================= 10. Activar y desactivar en pleno uso =================
paso("10. Activar y desactivar con datos reales")
recalc(); FOTO_REAL = {r: txt("Panel", r) for r in ["B11", "E11", "H11", "K11", "B16", "H16", "C35", "G35", "J35", "C43", "C60", "C69", "F69"]}
escribir("Inicio", "C6", "Sí"); recalc()
check("Al activar: Panel con 56 ejemplos y aviso de filas reales ocultas", txt("Panel", "H16") == "56" and "reales guardadas" in txt("Panel", "B4"), txt("Panel", "B4"))
check("Los datos reales siguen intactos en el registro", txt("Registro Auto", "M8") != "" and txt("Registro Auto", "B8") != "")
escribir("Inicio", "C6", "No"); recalc()
check("Al desactivar: el Panel vuelve exactamente a lo que mostraba antes de activar", {r: txt("Panel", r) for r in FOTO_REAL} == FOTO_REAL,
      {r: (FOTO_REAL[r], txt("Panel", r)) for r in FOTO_REAL if txt("Panel", r) != FOTO_REAL[r]})
verificar_panel("Tras activar y desactivar")

# ================= 11. Horarios y Reportes =================
paso("11. Horarios y Reportes")
pegar("Horarios", "C14", INICIO); pegar("Horarios", "F14", INICIO + dt.timedelta(days=6))
grid = [["", ""] + [f"{(INICIO + dt.timedelta(days=d)).day}-oct" for d in range(7)], ["Nombre", "Horas", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"]]
grid += [[n, "40"] + ["11:00-19:00" if (i + d) % 2 else "19:00-02:00" for d in range(7)] for i, n in enumerate(EQUIPO)]
for i, row in enumerate(grid):
    for j, v in enumerate(row): pegar("Horarios", f"{chr(66 + j)}{16 + i}", v)
recalc()
check("Horario pegado en el marco 1", txt("Horarios", "B18") == "Ana" and txt("Horarios", "J25") != "")
check("El marco 2 sigue vacío y con su título", txt("Horarios", "B79") == "SEMANA 2" and txt("Horarios", "B82") == "◤ Pega aquí")
escribir("Reportes", "B7", "2026-10-01"); escribir("Reportes", "C7", "2026-10-31"); escribir("Reportes", "D7", "Mensual"); escribir("Reportes", "E7", "https://drive.google.com/reporte-oct"); recalc()
check("Reporte agregado: aparece el botón Abrir", txt("Reportes", "F7") == "Abrir reporte ↗", txt("Reportes", "F7"))
pegar("Reportes", "E7", "reporte en mi escritorio"); recalc()
check("Editar el enlace por un texto: dice 'Enlace no válido'", txt("Reportes", "F7") == "Enlace no válido", txt("Reportes", "F7"))
for c in "BCDE": pegar("Reportes", f"{c}7", "")
recalc(); check("Borrar el reporte: la fila queda vacía", txt("Reportes", "F7") == "")
verificar_panel("Tras Horarios y Reportes (el Panel no cambia)")

# ================= 12. Guardar, cerrar y volver a abrir =================
paso("12. Guardar y volver a abrir")
foto = {r: txt("Panel", r) for r in ["B11", "E11", "H11", "K11", "B16", "H16", "K16", "F23", "H24", "C35", "G35", "I35", "J35", "C43", "C60", "C69", "F69"]}
doc.storeToURL(uno.systemPathToFileUrl(os.path.abspath("vida_guardado.xlsx")), (PV("FilterName", "Calc MS Excel 2007 XML"),))
doc.close(True)
doc = abrir("vida_guardado.xlsx")
foto2 = {r: txt("Panel", r) for r in foto}
check("Al reabrir, el Panel muestra exactamente lo mismo", foto == foto2, {k: (foto[k], foto2[k]) for k in foto if foto[k] != foto2[k]})
check("Al reabrir, las hojas siguen protegidas", all(S(n).isProtected() for n in ["Registro Auto", "Panel", "Inicio"]))
verificar_panel("Después de reabrir")
escribir("Inicio", "C6", "Sí"); recalc()
check("Después de reabrir, el interruptor sigue funcionando (Sí)", txt("Panel", "H16") == "56")
escribir("Inicio", "C6", "No"); verificar_panel("Después de reabrir, interruptor en No")
doc.close(True); proc.terminate()

ok = sum(o[2] for o in out)
print(f"\nVIDA REAL: {ok}/{len(out)} comprobaciones correctas en {len(pasos)} pasos")
for p, n, good, det in out:
    if not good: print(f"  ✗ [{p}] {n}  →  {det}")
