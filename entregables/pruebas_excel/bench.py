"""Benchmark de rendimiento con el archivo lleno (1.100 filas Auto + 1.100 Delivery + 30 personas).
Mide: tamaño, apertura, recálculo completo, y respuesta a acciones típicas (escribir un dato, cambiar filtros).
Uso: python bench.py plantilla.xlsx etiqueta"""
import sys, os, glob, time, random, subprocess, tempfile, statistics, datetime as dt, json
import uno
from com.sun.star.beans import PropertyValue
from openpyxl import load_workbook
sys.path.insert(0, glob.glob("/root/.claude/skills/synced/*/xlsx/scripts")[0])
from office.soffice import get_soffice_env

TEMPLATE, TAG = sys.argv[1], sys.argv[2]
def PV(n, v): p = PropertyValue(); p.Name = n; p.Value = v; return p

NAMES = [f"Persona {i:02d}" for i in range(1, 31)]
def fill_full(src, dst, seed=11, rows=1030):
    rnd = random.Random(seed)
    wb = load_workbook(src)
    a, d, pe = wb["Registro Auto"], wb["Registro Delivery"], wb["Personal"]
    for i, n in enumerate(NAMES): pe[f"B{8+i}"] = n
    r = 8
    for k in range(rows // 2):
        f = dt.date(2025, 3, 1) + dt.timedelta(days=k)
        a1, s1, a2, s2 = rnd.randint(80, 160), rnd.randint(120, 320), rnd.randint(50, 130), rnd.randint(120, 320)
        sd = round((a1 * s1 + a2 * s2) / (a1 + a2))
        for t, au, sec in (("AM", a1, s1), ("PM", a1 + a2, sd)):
            c, b, co = rnd.sample(NAMES if rnd.random() < .5 else NAMES[:12], 3)
            for col, v in zip("BCDEFGHI", (f, t, c, b, co, au, sec // 60, sec % 60)): a[f"{col}{r}"] = v
            for col, v in zip("BCDF", (f, t, f"https://drive.google.com/{r}", "Sí" if rnd.random() < .8 else "No")): d[f"{col}{r}"] = v
            r += 1
    wb["Inicio"]["C6"] = "No"
    wb.save(dst)

full = f"bench_{TAG}.xlsx"
fill_full(TEMPLATE, full)

prof = tempfile.mkdtemp()
proc = subprocess.Popen(["soffice", f"-env:UserInstallation=file://{prof}", "--headless", "--invisible", "--norestore",
                         f"--accept=pipe,name=bench{TAG};urp;"], env=get_soffice_env(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
local = uno.getComponentContext()
res = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
for _ in range(60):
    try: ctx = res.resolve(f"uno:pipe,name=bench{TAG};urp;StarOffice.ComponentContext"); break
    except Exception: time.sleep(1)
cp = ctx.ServiceManager.createInstanceWithContext("com.sun.star.configuration.ConfigurationProvider", ctx)
upd = cp.createInstanceWithArguments("com.sun.star.configuration.ConfigurationUpdateAccess", (PV("nodepath", "/org.openoffice.Office.Calc/Formula/Calculation"),))
upd.setPropertyValue("UseThreadedCalculationForFormulaGroups", False); upd.commitChanges()
desk = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)

t0 = time.perf_counter()
doc = desk.loadComponentFromURL(uno.systemPathToFileUrl(os.path.abspath(full)), "_blank", 0, (PV("Hidden", True),))
doc.calculateAll()
t_open = time.perf_counter() - t0
full_times = []
for _ in range(3):
    t0 = time.perf_counter(); doc.calculateAll(); full_times.append(time.perf_counter() - t0)

ra, pn, de = doc.Sheets.getByName("Registro Auto"), doc.Sheets.getByName("Panel"), doc.Sheets.getByName("Registro Delivery")
for n in doc.Sheets.ElementNames: doc.Sheets.getByName(n).unprotect("")
def timed(action, reps=5):
    ts = []
    for i in range(reps):
        action(i); t0 = time.perf_counter(); doc.calculate(); ts.append(time.perf_counter() - t0)
    return statistics.median(ts)

acts = {
    "escribir segundos en una fila PM": lambda i: ra.getCellRangeByName("I601").setValue(10 + i),
    "escribir autos en una fila AM": lambda i: ra.getCellRangeByName("G600").setValue(100 + i),
    "cambiar nombre del cajero": lambda i: ra.getCellRangeByName("D700").setString(NAMES[i]),
    "escribir Sí/No en Delivery": lambda i: de.getCellRangeByName("F500").setString("Sí" if i % 2 else "No"),
    "cambiar filtro Turno": lambda i: pn.getCellRangeByName("F7").setString(["AM", "PM", "Total", "AM", "PM"][i]),
    "cambiar filtro de fechas (una semana)": lambda i: (pn.getCellRangeByName("B7").setValue(46000 + 7 * i), pn.getCellRangeByName("D7").setValue(46006 + 7 * i)),
    "cambiar filtro Persona": lambda i: pn.getCellRangeByName("J7").setString(NAMES[i]),
}
out = {"etiqueta": TAG, "tamaño_KB": round(os.path.getsize(TEMPLATE) / 1024), "abrir_y_calcular_s": round(t_open, 2),
       "recalculo_completo_s": round(statistics.median(full_times), 2)}
for k, f in acts.items(): out[k + "_s"] = round(timed(f), 3)
# conteo de fórmulas
wbf = load_workbook(TEMPLATE)
out["formulas"] = sum(1 for ws in wbf.worksheets for row in ws.iter_rows() for c in row if isinstance(c.value, str) and c.value.startswith("="))
print(json.dumps(out, ensure_ascii=False, indent=1))
json.dump(out, open(f"bench_{TAG}.json", "w"), ensure_ascii=False)
doc.close(True); proc.terminate()
