"""Pegado real de horarios (copiar en otro libro → pegar en Horarios) con estructuras variables, en LibreOffice vía UNO.
Uso: python test_horarios.py plantilla.xlsx"""
import sys, os, glob, time, subprocess, tempfile, shutil, datetime as dt
import uno
from com.sun.star.beans import PropertyValue
sys.path.insert(0, glob.glob("/root/.claude/skills/synced/*/xlsx/scripts")[0])
from office.soffice import get_soffice_env

SRC = sys.argv[1]
shutil.copy(SRC, "hor.xlsx")
out = []
def check(n, c, det=""): out.append((n, bool(c), det))
def PV(n, v): p = PropertyValue(); p.Name = n; p.Value = v; return p

prof = tempfile.mkdtemp()
proc = subprocess.Popen(["soffice", f"-env:UserInstallation=file://{prof}", "--headless", "--invisible", "--norestore",
                         "--accept=pipe,name=horarios;urp;"], env=get_soffice_env(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
local = uno.getComponentContext()
res = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
for _ in range(60):
    try: ctx = res.resolve("uno:pipe,name=horarios;urp;StarOffice.ComponentContext"); break
    except Exception: time.sleep(1)
# Excel pega encima sin preguntar; LibreOffice pregunta "¿reemplazar contenido?" y sin pantalla responde No.
# Se desactiva esa pregunta para que la prueba se comporte como Excel.
cp = ctx.ServiceManager.createInstanceWithContext("com.sun.star.configuration.ConfigurationProvider", ctx)
upd = cp.createInstanceWithArguments("com.sun.star.configuration.ConfigurationUpdateAccess", (PV("nodepath", "/org.openoffice.Office.Calc/Input"),))
upd.setPropertyValue("ReplaceCellsWarning", False); upd.commitChanges()
desktop = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
disp = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.DispatchHelper", ctx)
doc = desktop.loadComponentFromURL(uno.systemPathToFileUrl(os.path.abspath("hor.xlsx")), "_blank", 0, (PV("Hidden", True),))
doc.calculateAll()
ho = doc.Sheets.getByName("Horarios")
def t(ref): return ho.getCellRangeByName(ref).getString()

# geometría del marco leída del propio archivo: filas de título "SEMANA k"
titulos = {}
for r in range(1, 4000):
    v = ho.getCellByPosition(1, r - 1).getString()
    if v.startswith("SEMANA "): titulos[int(v.split()[1])] = r
    if len(titulos) >= 52: break
FR0, FR_H = titulos[1], titulos[2] - titulos[1]
ancla = lambda k: titulos[k] + 3
# columnas desbloqueadas de la zona de pegado (hasta la primera bloqueada)
cols_libres = 0
while not ho.getCellByPosition(1 + cols_libres, ancla(1) - 1).CellProtection.IsLocked: cols_libres += 1
filas_libres = 0
while not ho.getCellByPosition(1, ancla(1) - 1 + filas_libres).CellProtection.IsLocked: filas_libres += 1
print(f"Marco: {filas_libres} filas × {cols_libres} columnas pegables, cada {FR_H} filas")

DIAS = ["LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"]
TURNOS = ["08:00 - 15:00(60 MINS)", "09:30 - 16:00(30 MINS)", "12:00 - 18:30(30 MINS)", "18:30 - 01:00(30 MINS)", "23:00 - 06:00(60 MINS)", "", "16:30 - 00:00(30 MINS)"]
COLORES = [0xC0C0C0, 0xFFFF00, 0x00B0F0, 0xFFC000, 0xFF0000, 0x000000, 0xF4B183]
def crear_origen(n_personas, titulo=True, numero=True, horas=True, firma=False, resumen=False, extra_cols=0, filas_vacias_arriba=2):
    """Construye un horario como el del usuario en un libro aparte. Devuelve (doc, rango a copiar, celdas esperadas relativas)."""
    sdoc = desktop.loadComponentFromURL("private:factory/scalc", "_blank", 0, (PV("Hidden", True),))
    ws = sdoc.Sheets.getByIndex(0)
    r = 0
    cols = (["#"] if numero else []) + ["Nombre"] + (["Horas"] if horas else []) + DIAS + (["FIRMA"] if firma else []) + [f"Extra {i+1}" for i in range(extra_cols)]
    ncol = len(cols)
    if titulo:
        ws.getCellByPosition(3, 0).setString("Horario de trabajo Crew"); ws.getCellRangeByPosition(3, 0, 7, 0).merge(True)
        r = 1 + filas_vacias_arriba
    fila_fechas = r
    d0 = cols.index("LUNES")
    for i in range(7):
        c = ws.getCellByPosition(d0 + i, r); c.setValue(46349 + i)        # 23-nov-2026…
        loc = uno.createUnoStruct("com.sun.star.lang.Locale")
        key = sdoc.NumberFormats.queryKey("D-MMM", loc, False)
        c.NumberFormat = key if key >= 0 else sdoc.NumberFormats.addNew("D-MMM", loc)
        c.CellBackColor = 0x843C0C
    r += 1
    for j, h in enumerate(cols): ws.getCellByPosition(j, r).setString(h); ws.getCellByPosition(j, r).CellBackColor = 0x843C0C
    r += 1
    for p in range(n_personas):
        vals = ([str(p + 1)] if numero else []) + [f"PERSONA {p+1:02d} APELLIDO LARGO (2{p:08d})"] + (["30" if p % 2 else "20"] if horas else []) + \
               [TURNOS[(p + i) % 7] for i in range(7)] + ([""] if firma else []) + [f"x{p}" for _ in range(extra_cols)]
        for j, v in enumerate(vals):
            c = ws.getCellByPosition(j, r); c.setString(v)
            if j >= d0 and j < d0 + 7: c.CellBackColor = COLORES[(p + j - d0) % 7]
        r += 1
    if resumen:
        for lab in ["APERTURA", "MEDIO", "HASTA LAS 00", "CIERRE O TRASNOCHE", "TOTAL"]:
            ws.getCellByPosition(1 if numero else 0, r).setString(lab); ws.getCellRangeByPosition(1 if numero else 0, r, d0 - 1, r).merge(True)
            for i in range(7): ws.getCellByPosition(d0 + i, r).setValue(3 + i)
            r += 1
    rango = ws.getCellRangeByPosition(0, 0, ncol - 1, r - 1)
    nombre_col = cols.index("Nombre")
    esperado = {"primer_nombre": (fila_fechas + 2, nombre_col, "PERSONA 01 APELLIDO LARGO (200000000)"),
                "ultimo_nombre": (fila_fechas + 1 + n_personas, nombre_col, f"PERSONA {n_personas:02d} APELLIDO LARGO (2{n_personas-1:08d})"),
                "lunes_1": (fila_fechas + 2, d0, TURNOS[0]), "ultima_col": (fila_fechas + 1, ncol - 1, cols[-1])}
    return sdoc, rango, esperado, r, ncol

def copiar(sdoc, rango):
    ctrl = sdoc.CurrentController; ctrl.select(rango); disp.executeDispatch(ctrl.Frame, ".uno:Copy", "", 0, ())
def pegar_en(ref, modo):
    ctrl = doc.CurrentController; ctrl.setActiveSheet(ho); ctrl.select(ho.getCellRangeByName(ref))
    if modo == "valores":
        disp.executeDispatch(ctrl.Frame, ".uno:InsertContents", "", 0,
                             (PV("Flags", "SVD"), PV("FormulaCommand", 0), PV("SkipEmptyCells", False), PV("Transpose", False), PV("AsLink", False), PV("MoveMode", 4)))
    else:
        disp.executeDispatch(ctrl.Frame, ".uno:Paste", "", 0, ())
    doc.calculate()
def col(c): return chr(66 + c) if c < 25 else "A" + chr(65 + c - 25)

def marco_intacto(k):
    s = titulos[k]
    return t(f"B{s}") == f"SEMANA {k}" and t(f"B{s+1}") == "Semana del" and t(f"B{s+2}").startswith("▼")
def zona_vacia(k):
    a = ancla(k)
    return all(ho.getCellByPosition(c, a - 1 + r).getString() in ("", "◤ Pega aquí") for r in range(filas_libres) for c in range(1, 1 + cols_libres))

casos = [
    ("Horario 1 (título, N.º, Nombre, Horas, 7 días, 26 personas)", dict(n_personas=26)),
    ("Horario 2 (con FIRMA y 5 filas de resumen, 27 personas)", dict(n_personas=27, firma=True, resumen=True)),
    ("Horario 3 (sin título, 23 personas)", dict(n_personas=23, titulo=False)),
    ("Sin columna Horas ni N.º", dict(n_personas=25, numero=False, horas=False)),
    ("Equipo grande: 40 personas + resumen + FIRMA", dict(n_personas=40, firma=True, resumen=True)),
    ("Columnas de más: FIRMA + 3 columnas extra (14 columnas)", dict(n_personas=27, firma=True, extra_cols=3)),
    ("Justo en el límite: 50 personas + título + resumen (60 filas)", dict(n_personas=50, resumen=True)),
]
k = 1
usados = {}
for modo in ("valores", "normal"):
    for desc, kw in casos:
        sdoc, rango, esp, nfil, ncol = crear_origen(**kw)
        cabe = nfil <= filas_libres and ncol <= cols_libres
        copiar(sdoc, rango)
        a = ancla(k)
        pegar_en(f"B{a}", modo)
        etq = f"[{'Pegar valores' if modo == 'valores' else 'Ctrl+V normal'}] {desc} ({nfil}×{ncol})"
        if cabe:
            ok = all(ho.getCellByPosition(1 + cc, a - 1 + rr).getString() == v for rr, cc, v in esp.values())
            if ok: usados[k] = (a, esp)
            check(f"{etq}: todo el horario quedó dentro del marco {k}", ok,
                  {n: (ho.getCellByPosition(1 + cc, a - 1 + rr).getString(), v) for n, (rr, cc, v) in esp.items()})
        else:
            check(f"{etq}: NO cabe en el marco ({filas_libres}×{cols_libres})", False, "el horario es más grande que la zona de pegado")
        check(f"{etq}: el título del marco {k} y el de la semana siguiente siguen intactos", marco_intacto(k) and marco_intacto(k + 1))
        check(f"{etq}: la semana siguiente sigue vacía", zona_vacia(k + 1))
        check(f"{etq}: la hoja sigue protegida", ho.isProtected())
        sdoc.close(True)
        k += 1
# demasiado grande: 60 personas + título + resumen (70 filas)
sdoc, rango, esp, nfil, ncol = crear_origen(60, resumen=True)
copiar(sdoc, rango); pegar_en(f"B{ancla(k)}", "valores")
check(f"Horario más grande que el marco ({nfil} filas): Excel no pega nada y la semana siguiente queda intacta",
      marco_intacto(k + 1) and zona_vacia(k + 1) and marco_intacto(k))
check("…y el marco donde se intentó pegar queda vacío (no a medias)", zona_vacia(k), "")
sdoc.close(True)
k += 1
# corregir un horario: pegar uno con menos personas encima deja filas viejas -> se borra el marco primero
sdoc, rango, esp, nfil, ncol = crear_origen(27); copiar(sdoc, rango); pegar_en(f"B{ancla(k)}", "valores"); sdoc.close(True)
sdoc, rango, esp2, nfil2, ncol2 = crear_origen(20); copiar(sdoc, rango); pegar_en(f"B{ancla(k)}", "valores"); sdoc.close(True)
sobra = ho.getCellByPosition(2, ancla(k) - 1 + esp["ultimo_nombre"][0]).getString()
check("Pegar un horario más corto encima: quedan filas del anterior (por eso se avisa que hay que borrar antes)", sobra.startswith("PERSONA 27"), sobra)
zona = ho.getCellRangeByPosition(1, ancla(k) - 1, len([0]) + 17, ancla(k) - 1 + filas_libres - 1)
zona.clearContents(1 | 2 | 4 | 16)   # Supr: valores, fechas, textos y fórmulas
doc.calculate()
check("Borrar el marco (seleccionar la zona crema y Supr) funciona con la hoja protegida", zona_vacia(k) and marco_intacto(k))
copiar(*crear_origen(20)[:2]); pegar_en(f"B{ancla(k)}", "valores")
check("…y luego el horario corregido queda limpio, sin restos del anterior", ho.getCellByPosition(2, ancla(k) - 1 + esp["ultimo_nombre"][0]).getString() == "")
k += 1
# pegar en el lugar equivocado: sobre el título del marco (bloqueado)
sdoc, rango, esp, nfil, ncol = crear_origen(26)
copiar(sdoc, rango)
pegar_en(f"B{titulos[k]}", "valores")
check("Pegar por error sobre el TÍTULO de un marco: la hoja no lo deja y el marco queda intacto", marco_intacto(k) and zona_vacia(k))
pegar_en(f"B{titulos[k] + 1}", "valores")
check("Pegar por error sobre la fila 'Semana del': no lo deja", marco_intacto(k) and zona_vacia(k))
sdoc.close(True)
# todos los marcos usados conservan su horario completo
check(f"Los {len(usados)} marcos usados conservan su horario completo al final",
      all(ho.getCellByPosition(1 + cc, a - 1 + rr).getString() == v for a, esp in usados.values() for rr, cc, v in esp.values()))
check("Todos los títulos de las 52 semanas siguen intactos", all(marco_intacto(j) for j in range(1, 52)))
doc.close(True); proc.terminate()
print(f"HORARIOS: {sum(o[1] for o in out)}/{len(out)}")
for n, ok, det in out: print(("  ✓ " if ok else "  ✗ ") + n + ("" if ok else f"  →  {det}"))
