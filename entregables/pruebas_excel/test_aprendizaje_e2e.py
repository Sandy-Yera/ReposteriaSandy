"""Modo aprendizaje de extremo a extremo con acciones reales en la planilla (LibreOffice vía UNO):
tester, usuario torpe y destructor. Verifica que el interruptor funcione en ambos sentidos.
Uso: python test_aprendizaje_e2e.py plantilla.xlsx"""
import sys, os, glob, time, subprocess, tempfile, random, datetime as dt
import uno
from com.sun.star.beans import PropertyValue
from com.sun.star.table import TableSortField
sys.path.insert(0, glob.glob("/root/.claude/skills/synced/*/xlsx/scripts")[0])
from office.soffice import get_soffice_env
from harness import build_case

TEMPLATE = sys.argv[1]
out = []
def check(n, c, det=""): out.append((n, bool(c), det))
def PV(n, v): p = PropertyValue(); p.Name = n; p.Value = v; return p

# ---------- archivo con datos reales (20 días de 2026 + 2 días en las mismas fechas que los ejemplos) ----------
NAMES = ["Ana", "Bruno", "Carla", "Diego", "Elena", "Felipe"]
rnd = random.Random(4)
auto, deliv = [], []
for k in range(20):
    f = dt.date(2026, 10, 1) + dt.timedelta(days=k)
    a1, s1, a2, s2 = rnd.randint(80, 150), rnd.randint(150, 280), rnd.randint(60, 120), rnd.randint(150, 280)
    sd = round((a1 * s1 + a2 * s2) / (a1 + a2))
    auto += [(f, "AM", *rnd.sample(NAMES, 3), a1, s1 // 60, s1 % 60), (f, "PM", *rnd.sample(NAMES, 3), a1 + a2, sd // 60, sd % 60)]
    deliv += [(f, "AM", f"https://drive.google.com/r{k}a", "Sí"), (f, "PM", f"https://drive.google.com/r{k}p", "No" if k % 4 == 0 else "Sí")]
auto += [(dt.date(2021, 3, 1), "AM", "Ana", "Bruno", "Carla", 100, 3, 0), (dt.date(2021, 3, 1), "PM", "Diego", "Elena", "Felipe", 190, 3, 20)]
N_REAL = len(auto)
build_case(TEMPLATE, "apr_e2e.xlsx", auto, deliv, NAMES, {"L7": 1}, modo_aprendizaje="Sí")

# ---------- LibreOffice ----------
prof = tempfile.mkdtemp()
proc = subprocess.Popen(["soffice", f"-env:UserInstallation=file://{prof}", "--headless", "--invisible", "--norestore",
                         "--accept=pipe,name=apre2e;urp;"], env=get_soffice_env(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
local = uno.getComponentContext()
resolver = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
for _ in range(60):
    try: ctx = resolver.resolve("uno:pipe,name=apre2e;urp;StarOffice.ComponentContext"); break
    except Exception: time.sleep(1)
cp = ctx.ServiceManager.createInstanceWithContext("com.sun.star.configuration.ConfigurationProvider", ctx)
upd = cp.createInstanceWithArguments("com.sun.star.configuration.ConfigurationUpdateAccess", (PV("nodepath", "/org.openoffice.Office.Calc/Formula/Calculation"),))
upd.setPropertyValue("UseThreadedCalculationForFormulaGroups", False); upd.commitChanges()
desktop = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
disp = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.DispatchHelper", ctx)

def load():
    doc = desktop.loadComponentFromURL(uno.systemPathToFileUrl(os.path.abspath("apr_e2e.xlsx")), "_blank", 0, (PV("Hidden", True),))
    doc.calculateAll(); return doc
def sh(doc, n): return doc.Sheets.getByName(n)
def val(doc, s, ref):
    c = sh(doc, s).getCellRangeByName(ref); return c.getString()
def escribir_ui(doc, s, ref, texto):
    """Escribe como un usuario (respeta protección): selecciona la celda y escribe."""
    ws = sh(doc, s); ctrl = doc.CurrentController; ctrl.setActiveSheet(ws)
    ctrl.select(ws.getCellRangeByName(ref)); disp.executeDispatch(ctrl.Frame, ".uno:EnterString", "", 0, (PV("StringName", texto),))
    doc.calculate()
def pegar(doc, s, ref, texto):
    """Pega un valor (salta la lista desplegable, como Ctrl+V)."""
    c = sh(doc, s).getCellRangeByName(ref)
    if texto is None: c.setString("")
    elif isinstance(texto, (int, float)): c.setValue(texto)
    elif texto.startswith("="): c.setFormula(texto)
    else: c.setString(texto)
    doc.calculate()
def errores(doc):
    bad = []
    for n in ["Inicio", "Panel", "Registro Auto", "Registro Delivery", "Horarios", "Reportes", "Personal", "Calculos"]:
        ws = sh(doc, n); cur = ws.createCursor(); cur.gotoEndOfUsedArea(False)
        for row in ws.getCellRangeByPosition(0, 0, cur.RangeAddress.EndColumn, cur.RangeAddress.EndRow).getDataArray():
            for v in row:
                if isinstance(v, str) and v.startswith(("#DIV/0!", "#VALUE!", "#REF!", "#NAME?", "#N/A", "#NUM!", "#NULL!", "Err:")): bad.append((n, v))
    return bad[:5]
FOTO = [("Panel", r) for r in ["B4", "B8", "B11", "E11", "H11", "K11", "B13", "B16", "H16", "K16", "C35", "G35", "J35", "B40", "C60", "C69", "F69"]] + \
       [("Registro Auto", r) for r in ["B5", "I2", "M1039", "Q1039", "B1038", "M1058"]] + [("Registro Delivery", "G1039"), ("Registro Delivery", "H2"), ("Horarios", "V16"), ("Horarios", "B4"), ("Horarios", "V11"), ("Inicio", "C6")]
def foto(doc): return {f"{s}!{r}": val(doc, s, r) for s, r in FOTO}
def modo_si(f): return (f["Panel!H16"] == "56" and "MODO APRENDIZAJE" in f["Panel!B4"] and f["Registro Auto!Q1039"].startswith("EJEMPLO")
                        and f["Registro Auto!I2"] == "Ver ejemplos ↓" and f["Registro Delivery!H2"] == "Ver ejemplos ↓"
                        and f["Horarios!B4"].startswith("▶ MODO APRENDIZAJE") and f["Horarios!V11"] == "← Volver a la Semana 1")
def modo_no(f): return (f["Panel!H16"] == str(N_REAL) and "MODO APRENDIZAJE" not in f["Panel!B4"] and f["Registro Auto!Q1039"] == "" and f["Registro Auto!B5"] == ""
                        and f["Registro Auto!I2"] == "" and f["Registro Delivery!H2"] == "" and f["Horarios!B4"] == "" and f["Horarios!V11"] == "")

# ================= A. El interruptor funciona en ambos sentidos =================
doc = load()
f0 = foto(doc)
check("Estado inicial Sí: Panel con 56 turnos de ejemplo, aviso, ejemplos y guías visibles", modo_si(f0), {k: f0[k] for k in ("Panel!H16", "Registro Auto!Q1039")})
check("Inicial: sin errores en ninguna hoja", not errores(doc), errores(doc))
escribir_ui(doc, "Inicio", "C6", "No")
f1 = foto(doc)
check("Escribir No (como usuario): Panel con los datos reales, sin aviso, ejemplos y guías ocultos", modo_no(f1), {k: f1[k] for k in ("Panel!H16", "Panel!B4", "Registro Auto!Q1039")})
check("En No: sin errores en ninguna hoja", not errores(doc), errores(doc))
check("En No: el horario de ejemplo y el enlace 'Ver ejemplos' desaparecen", f1["Horarios!V16"] == "" and f1["Registro Auto!I2"] == "")
check("En No: el ranking usa nombres reales", f1["Panel!C35"] in NAMES, f1["Panel!C35"])
escribir_ui(doc, "Inicio", "C6", "Sí")
f2 = foto(doc)
check("Volver a Sí: todo vuelve exactamente igual que al principio", f2 == f0, {k: (f0[k], f2[k]) for k in f0 if f0[k] != f2[k]})
estable = True
for i in range(6):
    escribir_ui(doc, "Inicio", "C6", "No" if i % 2 == 0 else "Sí")
    fi = foto(doc)
    estable &= (fi == f1) if i % 2 == 0 else (fi == f0)
check("Cambiar Sí/No 6 veces seguidas: siempre el mismo resultado en cada estado", estable)
# variantes escritas o pegadas
for txt, esperado in [("no", "No"), (" NO ", "No"), ("No.", "No"), ("N", "No"), ("nop", "No"), (0, "No"), ("si", "Sí"), ("SÍ", "Sí"), ("s", "Sí"), (1, "Sí"),
                      ("Tal vez", "Sí"), ("=1/0", "Sí"), (None, "Sí")]:
    pegar(doc, "Inicio", "C6", txt)
    fx = foto(doc)
    ok = modo_no(fx) if esperado == "No" else modo_si(fx)
    check(f"Interruptor con {txt!r} → se comporta como {esperado}", ok, {k: fx[k] for k in ("Panel!H16", "Panel!B4")})
    check(f"Interruptor con {txt!r}: Inicio muestra en palabras el estado real", ("limpio" in val(doc, "Inicio", "E7").lower()) == (esperado == "No") and val(doc, "Inicio", "E7") != "",
          val(doc, "Inicio", "E7"))
pegar(doc, "Inicio", "C6", "Sí")

# ================= B. Tester: filtros que quedan puestos al cambiar de modo =================
pegar(doc, "Panel", "B7", 44263); pegar(doc, "Panel", "D7", 44269)     # 8 al 14-mar-2021 (semana de ejemplo)
check("Sí + semana de ejemplo: el Panel filtra esa semana", 0 < int(val(doc, "Panel", "H16")) < 56, val(doc, "Panel", "H16"))
escribir_ui(doc, "Inicio", "C6", "No")
check("Pasar a No con esas fechas puestas: el aviso explica que no hay datos en esas fechas", "fechas" in val(doc, "Panel", "B4"), val(doc, "Panel", "B4"))
pegar(doc, "Panel", "B7", None); pegar(doc, "Panel", "D7", None)
escribir_ui(doc, "Inicio", "C6", "Sí")
pegar(doc, "Panel", "J7", "Ana (ej.)")
check("Sí + Persona 'Ana (ej.)': filtra los turnos de Ana de ejemplo", 0 < int(val(doc, "Panel", "H16")) < 56, val(doc, "Panel", "H16"))
escribir_ui(doc, "Inicio", "C6", "No")
check("Pasar a No con Persona 'Ana (ej.)': el aviso lo explica", "persona" in val(doc, "Panel", "B4").lower(), val(doc, "Panel", "B4"))
pegar(doc, "Panel", "J7", "Ana")
escribir_ui(doc, "Inicio", "C6", "Sí")
check("Sí + Persona real 'Ana': el aviso dice que en aprendizaje hay que elegir una persona de ejemplo", "(ej.)" in val(doc, "Panel", "B4"), val(doc, "Panel", "B4"))
pegar(doc, "Panel", "J7", "Todas")
ra = sh(doc, "Registro Auto")
reales_si = [[ra.getCellRangeByName(f"{c}{r}").getString() for c in "JKLMNOP"] for r in range(8, 8 + N_REAL)]
escribir_ui(doc, "Inicio", "C6", "No")
reales_no = [[ra.getCellRangeByName(f"{c}{r}").getString() for c in "JKLMNOP"] for r in range(8, 8 + N_REAL)]
check("Las filas reales calculan idéntico en Sí y en No (registro completo)", reales_si == reales_no)
check("Fila real en la misma fecha que un ejemplo (01-03-2021) no queda marcada repetida", not ra.getCellRangeByName(f"M{8 + N_REAL - 2}").getString().startswith("Revisar"),
      ra.getCellRangeByName(f"M{8 + N_REAL - 2}").getString())

# ================= C. Usuario torpe =================
escribir_ui(doc, "Inicio", "C6", "Sí")
fila = 8 + N_REAL
for col, v in zip("BCDEFGHI", ["16-11-2026", "AM", "Ana", "Bruno", "Carla", "120", "3", "10"]):
    escribir_ui(doc, "Registro Auto", f"{col}{fila}", v)
check("Torpe anota un turno real con el modo encendido: se guarda y su Nivel se calcula", ra.getCellRangeByName(f"M{fila}").getString() == "Cumple", ra.getCellRangeByName(f"M{fila}").getString())
check("…el Panel sigue mostrando solo ejemplos (56) y el aviso le dice que tiene filas reales ocultas", val(doc, "Panel", "H16") == "56" and "reales" in val(doc, "Panel", "B4"), val(doc, "Panel", "B4"))
escribir_ui(doc, "Inicio", "C6", "No")
check("…al pasar a No, su turno aparece en el Panel", val(doc, "Panel", "H16") == str(N_REAL + 1), val(doc, "Panel", "H16"))
f_antes = ra.getCellRangeByName("B1040").getFormula()
escribir_ui(doc, "Inicio", "C6", "Sí")
escribir_ui(doc, "Registro Auto", "B1040", "17-11-2026")
check("Torpe intenta escribir encima de un ejemplo: la hoja no lo deja", ra.getCellRangeByName("B1040").getFormula() == f_antes)
pegar(doc, "Registro Auto", f"D{fila}", "Ana (ej.)")
check("Torpe pone 'Ana (ej.)' como cajero REAL: no se mezcla con los ejemplos (siguen 56)", val(doc, "Panel", "H16") == "56", val(doc, "Panel", "H16"))
escribir_ui(doc, "Inicio", "C6", "No")
check("…y en No el aviso marca ese nombre como fuera de la lista", "no están en Personal" in val(doc, "Panel", "B4"), val(doc, "Panel", "B4"))
pegar(doc, "Registro Auto", f"D{fila}", "Ana")
pegar(doc, "Inicio", "C6", None)      # el usuario deja la celda vacía (Supr)
check("Torpe borra el interruptor (Supr): se ven los ejemplos y el aviso le dice cómo volver", modo_si(foto(doc)), val(doc, "Panel", "B4"))
check("Sin errores después de todo lo del torpe", not errores(doc), errores(doc))
doc.close(True)

# ================= D. Destructor (quita la protección) =================
def unprot(doc):
    for n in doc.Sheets.ElementNames:
        try: sh(doc, n).unprotect(os.environ.get("CLAVE_HOJAS", ""))
        except Exception: pass
doc = load(); unprot(doc)
pegar(doc, "Inicio", "C6", "No")
ra = sh(doc, "Registro Auto")
for col, v in zip("BCDEFGHI", [46400, "AM", "Ana", "Bruno", "Carla", 100, 3, 0]):
    pegar(doc, "Registro Auto", f"{col}1045", v)
check("Destructor escribe datos reales en la zona de ejemplos: el aviso lo detecta", "zona de ejemplos" in val(doc, "Panel", "B4"), val(doc, "Panel", "B4"))
check("…y no hay errores", not errores(doc), errores(doc))
doc.close(True)
doc = load(); unprot(doc)
sh(doc, "Registro Auto").Rows.removeByIndex(1037, 70); sh(doc, "Registro Delivery").Rows.removeByIndex(1037, 70); doc.calculateAll()
check("Destructor borra todas las filas de ejemplo: sin errores en Sí", not errores(doc), errores(doc))
pegar(doc, "Inicio", "C6", "No")
check("…y en No el Panel sigue con los datos reales, sin errores", val(doc, "Panel", "H16") == str(N_REAL) and not errores(doc), val(doc, "Panel", "H16"))
doc.close(True)
doc = load(); unprot(doc)
rng = sh(doc, "Registro Auto").getCellRangeByName("B8:CD1107")
fld = TableSortField(); fld.Field = 0; fld.IsAscending = True
desc = rng.createSortDescriptor()
for pv in desc:
    if pv.Name == "SortFields": pv.Value = uno.Any("[]com.sun.star.table.TableSortField", (fld,))
uno.invoke(rng, "sort", (desc,)); doc.calculateAll()
check("Destructor ordena la tabla mezclando ejemplos y reales: en Sí siguen 56 turnos de ejemplo", val(doc, "Panel", "H16") == "56", val(doc, "Panel", "H16"))
pegar(doc, "Inicio", "C6", "No")
check("…en No siguen exactamente los turnos reales, sin errores", val(doc, "Panel", "H16") == str(N_REAL) and not errores(doc), (val(doc, "Panel", "H16"), errores(doc)))
doc.close(True)
doc = load(); unprot(doc)
sh(doc, "Calculos").getCellRangeByName("B8").setString(""); doc.calculateAll()
check("Destructor vacía la celda interna del interruptor: sin errores", not errores(doc), errores(doc))
doc.close(True)
doc = load(); unprot(doc)
sh(doc, "Inicio").setName("Portada"); doc.calculateAll()
pegar(doc, "Portada", "C6", "No")
check("Destructor renombra Inicio: el interruptor sigue funcionando", modo_no(foto2 := {**{f"{s}!{r}": val(doc, s, r) for s, r in FOTO if s != "Inicio"}}), foto2.get("Panel!H16"))
doc.close(True)
proc.terminate()
print(f"MODO APRENDIZAJE E2E: {sum(o[1] for o in out)}/{len(out)}")
for n, ok, det in out: print(("  ✓ " if ok else "  ✗ ") + n + ("" if ok else f"  →  {det}"))
