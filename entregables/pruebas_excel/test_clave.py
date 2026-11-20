"""Clave de protección y crédito del autor (LibreOffice vía UNO). Uso: CLAVE_HOJAS=... python test_clave.py archivo.xlsx"""
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

CLAVE = os.environ["CLAVE_HOJAS"]
desktop = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
url = uno.systemPathToFileUrl(os.path.abspath(TEMPLATE))
doc = desktop.loadComponentFromURL(url, "_blank", 0, (PV("Hidden", True),))
check("Se abre sin pedir ninguna clave (uso normal igual que antes)", doc is not None)
nombres = list(doc.Sheets.ElementNames)
def intenta(sh, clave):
    try: sh.unprotect(clave)
    except Exception: pass
    return not sh.isProtected()
for n in nombres:
    sh = doc.Sheets.getByName(n)
    check(f"{n}: protegida al abrir", sh.isProtected())
    check(f"{n}: NO se desprotege sin clave", not intenta(sh, ""))
    check(f"{n}: NO se desprotege con una clave equivocada", not intenta(sh, "1234"))
for hoja, celda in (("Inicio", "B4"), ("Panel", "B3"), ("Instrucciones", "C113")):
    c = doc.Sheets.getByName(hoja).getCellRangeByName(celda)
    check(f"Crédito en {hoja}!{celda} presente y bloqueado", "Gabriel Sandoval" in c.getString() and c.CellProtection.IsLocked, (c.getString(), c.CellProtection.IsLocked))
# LibreOffice no aplica el bloqueo de estructura de un .xlsx (ni el suyo propio al reabrir): se verifica el XML que lee Excel
x = zipfile.ZipFile(TEMPLATE).read("xl/workbook.xml").decode()
m = re.search(r"<workbookProtection[^>]*>", x)
check("Estructura del libro bloqueada CON clave (formato que lee Excel)", m and 'lockStructure="1"' in m.group(0) and re.search(r'workbookPassword="[0-9A-F]{4}"', m.group(0)), m.group(0) if m else None)
from openpyxl.utils.protection import hash_password
check("La clave del libro es la misma clave entregada", m and f'workbookPassword="{hash_password(CLAVE)}"' in m.group(0), (hash_password(CLAVE), m.group(0) if m else None))
for n in nombres:
    sh = doc.Sheets.getByName(n)
    check(f"{n}: con la clave correcta SÍ se desprotege", intenta(sh, CLAVE))
doc.close(True); proc.terminate(); shutil.rmtree(prof, ignore_errors=True)
print(f"CLAVE Y CRÉDITO: {sum(o[1] for o in out)}/{len(out)}")
for n, ok, det in out: print(("  ✓ " if ok else "  ✗ ") + n + ("" if ok else f"  →  {det}"))
