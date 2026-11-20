"""Tester destructivo, parte 2: acciones reales sobre la planilla (LibreOffice vía UNO) + revisión de protecciones."""
import sys, os, glob, time, subprocess, tempfile, shutil, zipfile, re, random, datetime as dt
import uno
from com.sun.star.beans import PropertyValue
from com.sun.star.table import CellRangeAddress
sys.path.insert(0, glob.glob("/root/.claude/skills/synced/*/xlsx/scripts")[0])
from office.soffice import get_soffice_env
from harness import build_case

TEMPLATE = sys.argv[1]
out = []
def check(n, c, det=""): out.append((n, bool(c), det))
def PV(n, v): p = PropertyValue(); p.Name = n; p.Value = v; return p

# ---------- datos de un mes ----------
NAMES = ["Ana", "Bruno", "Carla", "Diego", "Elena", "Felipe", "Gabriela", "Hugo"]
rnd = random.Random(3)
auto = []
for k in range(20):
    f = dt.date(2026, 9, 1) + dt.timedelta(days=k)
    a1, s1, a2, s2 = rnd.randint(80, 150), rnd.randint(150, 300), rnd.randint(60, 120), rnd.randint(150, 300)
    sd = round((a1 * s1 + a2 * s2) / (a1 + a2))
    auto += [(f, "AM", *rnd.sample(NAMES, 3), a1, s1 // 60, s1 % 60), (f, "PM", *rnd.sample(NAMES, 3), a1 + a2, sd // 60, sd % 60)]
build_case(TEMPLATE, "uno_base.xlsx", auto, [], NAMES, {"L7": 1})

# ---------- LibreOffice ----------
prof = tempfile.mkdtemp()
proc = subprocess.Popen(["soffice", f"-env:UserInstallation=file://{prof}", "--headless", "--invisible", "--norestore", "--nologo",
                         "--accept=pipe,name=lotest;urp;"], env=get_soffice_env(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
local = uno.getComponentContext()
resolver = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
for _ in range(60):
    try: ctx = resolver.resolve("uno:pipe,name=lotest;urp;StarOffice.ComponentContext"); break
    except Exception: time.sleep(1)
# LibreOffice tiene un fallo conocido con el cálculo en paralelo de grupos de fórmulas (da Err:522 falso).
# Excel no lo tiene; se desactiva para que la prueba mida el archivo y no el fallo del motor.
cp = ctx.ServiceManager.createInstanceWithContext("com.sun.star.configuration.ConfigurationProvider", ctx)
upd = cp.createInstanceWithArguments("com.sun.star.configuration.ConfigurationUpdateAccess", (PV("nodepath", "/org.openoffice.Office.Calc/Formula/Calculation"),))
upd.setPropertyValue("UseThreadedCalculationForFormulaGroups", False); upd.commitChanges()
desktop = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)

def load(path):
    doc = desktop.loadComponentFromURL(uno.systemPathToFileUrl(os.path.abspath(path)), "_blank", 0, (PV("Hidden", True),))
    doc.calculateAll()
    return doc

def kpis(doc):
    p = doc.Sheets.getByName("Panel")
    g = lambda r: p.getCellRangeByName(r)
    vals = {}
    for ref in ["B11", "E11", "H11", "H16", "F23", "H23", "J23", "F24", "H24", "J24", "C35", "G35", "C43", "C51", "C60", "H60", "C69", "E69"]:
        vals[ref] = g(ref).getString()
    return vals

def has_errors(doc, names=("Registro Auto", "Panel", "Calculos")):
    bad = []
    for n in names:
        sh = doc.Sheets.getByName(n)
        cur = sh.createCursor(); cur.gotoEndOfUsedArea(False)
        rng = sh.getCellRangeByPosition(0, 0, cur.RangeAddress.EndColumn, cur.RangeAddress.EndRow)
        for row in rng.getDataArray():
            for v in row:
                if isinstance(v, str) and (v.startswith("#") or v.startswith("Err:")) and v not in ("#",): bad.append((n, v))
        if len(bad) > 5: break
    return bad[:5]

base = load("uno_base.xlsx"); K0 = kpis(base); base.close(True)

# ---------- A. Protecciones (lo que Excel hará respetar) ----------
doc = load("uno_base.xlsx")
prot = {n: doc.Sheets.getByName(n).isProtected() for n in doc.Sheets.ElementNames}
for n in ["Registro Auto", "Registro Delivery", "Panel", "Reportes", "Personal", "Horarios", "Inicio", "Instrucciones"]:
    check(f"Hoja '{n}' protegida (no se pueden borrar fórmulas, filas ni columnas)", prot.get(n), prot.get(n))
ra = doc.Sheets.getByName("Registro Auto")
locked = lambda sh, ref: sh.getCellRangeByName(ref).CellProtection.IsLocked
check("Celdas grises (fórmulas) bloqueadas", all(locked(ra, f"{c}50") for c in "JKLMNOP"), [locked(ra, f"{c}50") for c in "JKLMNOP"])
check("Celdas crema (para escribir) libres", all(not locked(ra, f"{c}50") for c in "BCDEFGHIQ"), [locked(ra, f"{c}50") for c in "BCDEFGHIQ"])
de = doc.Sheets.getByName("Registro Delivery")
check("Delivery: crema libre, gris bloqueado", all(not locked(de, f"{c}50") for c in "BCDFH") and all(locked(de, f"{c}50") for c in "EG"),
      ([locked(de, f"{c}50") for c in "BCDFH"], [locked(de, f"{c}50") for c in "EG"]))
pn = doc.Sheets.getByName("Panel")
check("Panel: filtros libres, indicadores bloqueados", all(not locked(pn, f"{c}7") for c in "BDFHJL") and locked(pn, "B11") and locked(pn, "C35"))
ho = doc.Sheets.getByName("Horarios")
check("Horarios: zona de pegado libre y títulos bloqueados", not locked(ho, "B16") and not locked(ho, "J40") and not locked(ho, "S75") and locked(ho, "B13") and locked(ho, "B79"),
      (locked(ho, "B16"), locked(ho, "J40"), locked(ho, "S75"), locked(ho, "B13"), locked(ho, "B79")))
# intento real de borrar una fila protegida vía la interfaz (dispatcher)
ctrl = doc.CurrentController; ctrl.setActiveSheet(ra)
disp = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.DispatchHelper", ctx)
f0 = ra.getCellRangeByName("M10").getFormula()
ctrl.select(ra.getCellRangeByName("A10")); disp.executeDispatch(ctrl.Frame, ".uno:DeleteRows", "", 0, ())
ctrl.select(ra.getCellRangeByName("M10")); disp.executeDispatch(ctrl.Frame, ".uno:Delete", "", 0, ())
ctrl.select(ra.getCellRangeByName("K12")); disp.executeDispatch(ctrl.Frame, ".uno:EnterString", "", 0, (PV("StringName", "999"),))
doc.calculateAll()
check("Interfaz: borrar fila / suprimir fórmula / escribir encima → bloqueado", ra.getCellRangeByName("M10").getFormula() == f0 and ra.getCellRangeByName("K12").getFormula().startswith("="),
      (ra.getCellRangeByName("M10").getFormula()[:30], ra.getCellRangeByName("K12").getFormula()[:30]))
check("Interfaz: los indicadores siguen iguales", kpis(doc) == K0)
doc.close(True)
x = zipfile.ZipFile("uno_base.xlsx").read("xl/workbook.xml").decode()
check("Libro con estructura protegida (no se pueden borrar, renombrar ni mostrar hojas ocultas)", 'lockStructure="1"' in x or "lockStructure=\"true\"" in x, re.findall(r"<workbookProtection[^>]*>", x))
s3 = zipfile.ZipFile("uno_base.xlsx").read("xl/worksheets/sheet3.xml").decode()
m = re.search(r"<sheetProtection[^>]*>", s3)
check("Registro Auto: filtros de la tabla siguen permitidos con la protección", m and 'autoFilter="0"' in m.group(0), m.group(0) if m else None)

# ---------- B. Si alguien quita la protección y destruye a propósito ----------
def unprot(doc):
    for n in doc.Sheets.ElementNames:
        try: doc.Sheets.getByName(n).unprotect(os.environ.get("CLAVE_HOJAS", ""))
        except Exception: pass

# B1 ordenar la tabla por fecha descendente
doc = load("uno_base.xlsx"); unprot(doc); ra = doc.Sheets.getByName("Registro Auto")
rng = ra.getCellRangeByName("B8:AG47")
fld = uno.createUnoStruct("com.sun.star.table.TableSortField"); fld.Field = 0; fld.IsAscending = False
desc = rng.createSortDescriptor()
for pv in desc:
    if pv.Name == "SortFields": pv.Value = uno.Any("[]com.sun.star.table.TableSortField", (fld,))
uno.invoke(rng, "sort", (desc,)); doc.calculateAll()
check("Ordenar la tabla al revés no cambia los resultados", kpis(doc) == K0 and not has_errors(doc), (kpis(doc), has_errors(doc)))
doc.close(True)
# B2 renombrar la hoja
doc = load("uno_base.xlsx"); unprot(doc)
doc.Sheets.getByName("Registro Auto").setName("Auto 2026"); doc.calculateAll()
check("Renombrar 'Registro Auto' no rompe nada", kpis(doc) == K0 and not has_errors(doc, ("Auto 2026", "Panel", "Calculos")))
doc.close(True)
# B3 borrar una fila completa de datos (fila 20 = día 7 AM)
doc = load("uno_base.xlsx"); unprot(doc); ra = doc.Sheets.getByName("Registro Auto")
ra.Rows.removeByIndex(19, 1); doc.calculateAll()
k = kpis(doc)
check("Borrar una fila entera no genera errores", not has_errors(doc), has_errors(doc))
check("Borrar una fila: el Panel deja de contar ese turno", int(k["H16"]) == int(K0["H16"]) - 1, (K0["H16"], k["H16"]))
doc.close(True)
# B4 insertar una fila en medio y escribir datos ahí (sin fórmulas)
doc = load("uno_base.xlsx"); unprot(doc); ra = doc.Sheets.getByName("Registro Auto")
ra.Rows.insertByIndex(15, 1)
for col, v in zip("BCDEFGHI", [46300, "AM", "Ana", "Bruno", "Carla", 100, 3, 0]):
    c = ra.getCellRangeByName(f"{col}16"); (c.setString(v) if isinstance(v, str) else c.setValue(v))
doc.calculateAll()
aviso = doc.Sheets.getByName("Panel").getCellRangeByName("B4").getString()
check("Fila insertada a mano sin fórmulas: el Panel lo avisa", "fórmula" in aviso, aviso)
doc.close(True)
# B5 escribir un número encima de una fórmula gris (sin protección)
doc = load("uno_base.xlsx"); unprot(doc); ra = doc.Sheets.getByName("Registro Auto")
ra.getCellRangeByName("M12").setString(""); ra.getCellRangeByName("M12").setFormula("")
doc.calculateAll()
aviso = doc.Sheets.getByName("Panel").getCellRangeByName("B4").getString()
check("Fórmula borrada a mano: el Panel lo avisa", "fórmula" in aviso, aviso)
doc.close(True)

proc.terminate()
print(f"TESTER DESTRUCTIVO (acciones reales): {sum(o[1] for o in out)}/{len(out)}")
for n, ok, det in out: print(("  ✓ " if ok else "  ✗ ") + n + ("" if ok else f"  →  {det}"))
