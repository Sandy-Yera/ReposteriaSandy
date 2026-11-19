import sys, datetime as dt
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side, Protection
from openpyxl.worksheet.protection import SheetProtection
from openpyxl.workbook.protection import WorkbookProtection
from openpyxl.worksheet.table import Table, TableStyleInfo
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.worksheet.hyperlink import Hyperlink
from openpyxl.formatting.rule import FormulaRule
from openpyxl.workbook.defined_name import DefinedName
from openpyxl.comments import Comment
from openpyxl.utils import get_column_letter as L, column_index_from_string

OUT = sys.argv[1]
EMPTY = len(sys.argv) > 2 and sys.argv[2] == "empty"

# ---------- paleta ----------
PRI, ACC = "1F3A5F", "3D7EA6"
INPUT, CALC, LIGHT, BORDER, MUTED = "FFF6E0", "F2F4F7", "F7F8FA", "D5DAE1", "6B7785"
G_DK, G_MD, G_LT, G_TX = "1E7B45", "CDEBD8", "E6F5EC", "1E7B45"
OR_F, OR_T, RD_F, RD_T = "FDEBD3", "B4610E", "F9DADA", "B42318"
FONT = "Arial"

def fill(c): return PatternFill("solid", start_color=c, end_color=c)
def font(sz=10, b=False, c="1F2933", i=False, u=None): return Font(name=FONT, size=sz, bold=b, color=c, italic=i, underline=u)
thin = Side(style="thin", color=BORDER)
BOX = Border(left=thin, right=thin, top=thin, bottom=thin)
BOT = Border(bottom=thin)
CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)
LEFT = Alignment(horizontal="left", vertical="center", wrap_text=True, indent=1)
TIME = "[m]:ss"
DATE = "dd-mmm-yy"

wb = Workbook()
wb._named_styles["Normal"].font = Font(name=FONT, size=10)

def sheet(name, first=False):
    ws = wb.active if first else wb.create_sheet()
    ws.title = name
    ws.sheet_view.showGridLines = False
    ws.column_dimensions["A"].width = 3
    ws.row_dimensions[1].height = 14
    return ws

def put(ws, ref, val, f=None, fl=None, al=None, nf=None, bd=None):
    c = ws[ref]
    c.value = val
    if f: c.font = f
    if fl: c.fill = fl
    if al: c.alignment = al
    if nf: c.number_format = nf
    if bd: c.border = bd
    return c

def merge(ws, rng, val, **kw):
    ws.merge_cells(rng)
    return put(ws, rng.split(":")[0], val, **kw)

def link(ws, ref, text, target, white=False):
    c = ws[ref]
    c.value = text
    c.hyperlink = Hyperlink(ref=ref, location=f"'{target}'!A1", display=text)
    c.font = font(10, True, "FFFFFF" if white else ACC, u=None if white else "single")
    return c

def title(ws, text, sub, last_col, nav_col):
    ws.row_dimensions[2].height = 34
    merge(ws, f"B2:{L(nav_col-1)}2", text, f=font(18, True, PRI), al=Alignment(vertical="center"))
    link(ws, f"{L(nav_col)}2", "⌂ Inicio", "Inicio").alignment = CENTER
    if ws.title != "Panel": link(ws, f"{L(nav_col+1)}2", "Panel ›", "Panel").alignment = CENTER
    if sub:
        ws.row_dimensions[3].height = 20
        merge(ws, f"B3:{L(last_col)}3", sub, f=font(10, c=MUTED), al=Alignment(vertical="center"))

def dv(prompt=None, **k):
    k.setdefault("showErrorMessage", True)
    v = DataValidation(**k)
    if prompt:
        v.showInputMessage = True; v.promptTitle = prompt[0]; v.prompt = prompt[1]
    return v

UNLOCK = Protection(locked=False)
def unlock(ws, *refs):
    for ref in refs:
        cells = ws[ref]
        if not isinstance(cells, tuple): cells = ((cells,),)
        elif not isinstance(cells[0], tuple): cells = (cells,)
        for row in cells:
            for c in row: c.protection = UNLOCK

def protect(ws):
    ws.protection = SheetProtection(sheet=True, objects=True, scenarios=True, formatCells=True, formatColumns=False,
                                    formatRows=False, insertColumns=True, insertRows=True, insertHyperlinks=True,
                                    deleteColumns=True, deleteRows=True, selectLockedCells=False,
                                    selectUnlockedCells=False, sort=True, autoFilter=False, pivotTables=True)

FECHA_MIN, FECHA_MAX = 43831, 73051   # 01-01-2020 .. 01-01-2100
MAX_AUTOS, MAX_SEG = 3000, 1200
Z95 = 1.96                       # margen del 95 %
K_SIN_DATOS, K_TOPE = 5, 20      # fuerza del ajuste bayesiano (en turnos) cuando no se puede estimar, y su tope
DUDOSO = " · dudoso (PM con pocos autos)"
DUDOSO_SEG = 5                   # incertidumbre por redondeo del sistema desde la que se avisa
FMT_AJ = '+0.0" s";-0.0" s";0.0" s"'
FMT_MG = '"±"0.0" s"'       # límites de sentido común: 3.000 autos por fila, 20 minutos de promedio

def add_table(ws, name, ref):
    t = Table(displayName=name, ref=ref)
    t.tableStyleInfo = TableStyleInfo(name="TableStyleLight1", showRowStripes=False)
    ws.add_table(t)

def header(ws, row, col, labels, height=34):
    ws.row_dimensions[row].height = height
    for i, h in enumerate(labels):
        put(ws, f"{L(col+i)}{row}", h, f=font(10, True, "FFFFFF"), fl=fill(PRI), al=CENTER)

def lvl(x):
    s = f"ROUND({x}*86400,0)"
    return (f'IF({s}<=0,"Revisar datos",IF({s}<60,"Excelencia máxima",IF({s}<120,"Excelencia",'
            f'IF({s}<180,"Muy bueno",IF({s}<240,"Cumple",IF({s}<300,"No cumple (alerta)","No cumple"))))))')

def lvl_s(sec):
    """Nivel a partir de segundos enteros ya calculados (columna aux_segReal)."""
    return (f'IF({sec}<=0,"Revisar datos",IF({sec}<60,"Excelencia máxima",IF({sec}<120,"Excelencia",'
            f'IF({sec}<180,"Muy bueno",IF({sec}<240,"Cumple",IF({sec}<300,"No cumple (alerta)","No cumple"))))))')

def eb_resumen(ws, col, row0, n_rng, s_rng, c_rng, q_rng, n2_rng):
    """Escribe en ws el ajuste bayesiano empírico (ANOVA de un factor, estimador de momentos) sobre los grupos con
    n >= fMin. Devuelve las direcciones absolutas de s2 (varianza dentro) y k (fuerza del ajuste, en turnos)."""
    crit = '">="&fMin'
    vc = L(column_index_from_string(col) + 1)
    items = [("N", f'=SUMIF({n_rng},{crit})'), ("m", f'=COUNTIF({n_rng},{crit})'), ("S", f'=SUMIF({n_rng},{crit},{s_rng})'),
             ("C", f'=SUMIF({n_rng},{crit},{c_rng})'), ("Q", f'=SUMIF({n_rng},{crit},{q_rng})'), ("N2", f'=SUMIF({n_rng},{crit},{n2_rng})')]
    a = {}
    for i, (lab, frm) in enumerate(items):
        ws[f"{col}{row0+i}"] = lab; ws[f"{vc}{row0+i}"] = frm; a[lab] = f"${vc}${row0+i}"
    r = row0 + len(items)
    ws[f"{col}{r}"] = "s2"; ws[f"{vc}{r}"] = f'=IF({a["N"]}-{a["m"]}>0,({a["Q"]}-{a["C"]})/({a["N"]}-{a["m"]}),"")'
    ws[f"{col}{r+1}"] = "tau2"
    ws[f"{vc}{r+1}"] = (f'=IF(AND({a["m"]}>=2,ISNUMBER({vc}{r})),MAX(0,(({a["C"]}-{a["S"]}^2/{a["N"]})/({a["m"]}-1)-{vc}{r})'
                        f'/(({a["N"]}-{a["N2"]}/{a["N"]})/({a["m"]}-1))),"")')
    ws[f"{col}{r+2}"] = "k"
    ws[f"{vc}{r+2}"] = f'=IF(ISNUMBER({vc}{r+1}),IF({vc}{r+1}>0,MIN({vc}{r}/{vc}{r+1},{K_TOPE}),{K_TOPE}),{K_SIN_DATOS})'
    return f"Calculos!${vc}${r}", f"Calculos!${vc}${r+2}"

def lectura_f(score, margen):
    """Fórmula de la lectura en palabras de un puntaje ajustado y su margen del 95 %."""
    return (f'IF({score}="","",IF({margen}="","Pocos datos",IF({score}+{margen}<0,"Más rápido que lo normal",'
            f'IF({score}-{margen}>0,"Más lento que lo normal","Dentro de lo normal"))))')

def cf_time(ws, rng, first):
    s = f"ROUND({first}*86400,0)"
    ws.conditional_formatting.add(rng, FormulaRule(formula=[f'AND(ISNUMBER({first}),{s}>0,{s}<240)'], fill=fill(G_LT), font=Font(name=FONT, color=G_TX, bold=True), stopIfTrue=True))
    ws.conditional_formatting.add(rng, FormulaRule(formula=[f'AND(ISNUMBER({first}),{s}>=240,{s}<300)'], fill=fill(OR_F), font=Font(name=FONT, color=OR_T, bold=True), stopIfTrue=True))
    ws.conditional_formatting.add(rng, FormulaRule(formula=[f'AND(ISNUMBER({first}),{s}>=300)'], fill=fill(RD_F), font=Font(name=FONT, color=RD_T, bold=True), stopIfTrue=True))

def cf_text(ws, rng, first, mapping):
    for pat, fl_, tx in mapping:
        ws.conditional_formatting.add(rng, FormulaRule(formula=[pat.format(c=first)], fill=fill(fl_), font=Font(name=FONT, color=tx, bold=True), stopIfTrue=True))

GOOD_BAD = [('LEFT({c},2)="Sí"', G_LT, G_TX), ('{c}="No"', RD_F, RD_T)]

N_ROWS = 1100
R0, R1 = 8, 8 + N_ROWS - 1
RA = "'Registro Auto'"
RD = "'Registro Delivery'"
def ra(col): return f"{RA}!${col}${R0}:${col}${R1}"
def rd(col): return f"{RD}!${col}${R0}:${col}${R1}"
def rng(col): return f"${col}${R0}:${col}${R1}"

def f_turno(c):
    """Fórmula que normaliza un turno escrito a mano: 'AM'/'PM' (acepta am, a.m., 'PM '), '?' si es inválido, '' si está vacío."""
    tx = f'UPPER(SUBSTITUTE(SUBSTITUTE(TRIM({c}),".","")," ",""))'
    return f'=IF(ISBLANK({c}),"",IFERROR(IF(OR({tx}="AM",{tx}="PM"),{tx},"?"),"?"))'

def f_fecha(c):
    """Fórmula que devuelve la fecha como número entero si es una fecha real entre 2020 y 2099; si no, ''."""
    return f'=IFERROR(IF(AND(ISNUMBER({c}),{c}>={FECHA_MIN},{c}<{FECHA_MAX}),INT({c}),""),"")'

def dv_fecha(ejemplo, extra="", error_extra=""):
    """Validación de fecha (2020-2099) con cartel de ayuda y mensaje de error con un ejemplo."""
    return dv(("Fecha", f"Escríbela así: {ejemplo}{extra}"), type="date", operator="between", formula1=str(FECHA_MIN),
              formula2=str(FECHA_MAX - 1), errorTitle="Fecha", error=f"Escribe la fecha así: {ejemplo}{error_extra}")

# =====================================================================
# INICIO
# =====================================================================
ini = sheet("Inicio", first=True)
for col, w in zip("BCDEFGHIJ", [4, 22, 4, 60, 4, 4, 4, 4, 4]):
    ini.column_dimensions[col].width = w
ini.row_dimensions[2].height = 40
merge(ini, "B2:E2", "Registro de rendimiento · Auto y Delivery", f=font(22, True, PRI), al=Alignment(vertical="center"))
merge(ini, "B3:E3", "Control diario por turno (AM / PM) para el concurso de rendimiento del local.", f=font(11, c=MUTED), al=Alignment(vertical="center"))
ini.row_dimensions[3].height = 22
put(ini, "B5", "IR A", f=font(9, True, MUTED))
botones = [
    ("Panel", "Panel", "Indicadores, comparación AM vs PM y rankings. Aquí se mira, no se escribe (salvo los filtros)."),
    ("Registro Auto", "Registro Auto", "Anotar cada día los autos y el tiempo que muestra el sistema, por turno."),
    ("Registro Delivery", "Registro Delivery", "Anotar el enlace a la foto de comandas + tableta y si concuerdan."),
    ("Horarios", "Horarios", "Pegar cada semana una copia del horario del personal."),
    ("Reportes", "Reportes", "Guardar el enlace a cada reporte mensual o quincenal."),
    ("Personal", "Personal", "Lista de nombres para los desplegables. Agrega o quita personas aquí."),
    ("Instrucciones", "Instrucciones", "Paso a paso para usar el archivo, pensado para quien no sabe Excel."),
]
r = 6
for txt, tgt, desc in botones:
    ini.row_dimensions[r].height = 30
    c = link(ini, f"C{r}", txt + "  ›", tgt, white=True)
    c.fill = fill(PRI); c.alignment = CENTER
    put(ini, f"E{r}", desc, f=font(10, c="3E4C59"), al=Alignment(vertical="center", wrap_text=True))
    ini.row_dimensions[r + 1].height = 8
    r += 2
r += 1
put(ini, f"B{r}", "CÓMO LEER LOS COLORES", f=font(9, True, MUTED)); r += 1
leyenda = [
    (INPUT, None, "Celda crema: aquí se escribe."),
    (CALC, None, "Celda gris: se calcula sola, no se toca."),
    (G_LT, G_TX, "Verde: cumple la meta (bajo 4:00) o concuerda."),
    (OR_F, OR_T, "Naranja: entre 4:00 y 4:59, no cumple (zona de alerta)."),
    (RD_F, RD_T, "Rojo: 5:00 o más, o el delivery no concuerda."),
]
for fl_, tx, desc in leyenda:
    ini.row_dimensions[r].height = 24
    put(ini, f"C{r}", "", fl=fill(fl_), bd=BOX)
    put(ini, f"E{r}", desc, f=font(10, c="3E4C59"), al=Alignment(vertical="center"))
    r += 1
r += 1
put(ini, f"B{r}", "SI ALGO NO FUNCIONA", f=font(9, True, MUTED)); r += 1
for tip in ["¿No te deja escribir? Estás en una celda gris: esas se calculan solas. Escribe en las crema.",
            "¿Una fila se puso naranja? Lee la columna Nivel (Auto) o Estado (Delivery): dice qué corregir.",
            "¿Borraste algo sin querer? Presiona Ctrl + Z enseguida.",
            "¿Aviso rojo ⚠ arriba del Panel? Léelo: dice qué revisar. Más ayuda en Instrucciones."]:
    ini.row_dimensions[r].height = 34
    put(ini, f"C{r}", "•", f=font(12, True, ACC), al=Alignment(horizontal="right", vertical="center"))
    put(ini, f"E{r}", tip, f=font(10, c="3E4C59"), al=Alignment(vertical="center", wrap_text=True))
    r += 1

# =====================================================================
# PANEL (se crea ahora para fijar el orden; se llena después)
# =====================================================================
pan = sheet("Panel")

# =====================================================================
# REGISTRO AUTO
# =====================================================================
au = sheet("Registro Auto")
cols = [  # (letra, encabezado, ancho, tipo)
    ("B", "Fecha", 13, "in"), ("C", "Turno", 9, "in"), ("D", "Cajero", 16, "in"), ("E", "Board", 16, "in"),
    ("F", "Corredor", 16, "in"), ("G", "Autos (sistema)", 11, "in"), ("H", "Min (sistema)", 9, "in"),
    ("I", "Seg (sistema)", 9, "in"), ("J", "Tiempo sistema", 11, "calc"), ("K", "Autos del turno", 11, "calc"),
    ("L", "Tiempo real del turno", 12, "calc"), ("M", "Nivel", 34, "calc"), ("N", "¿Cumple meta?", 10, "calc"),
    ("O", "Tiempo del día", 11, "calc"), ("P", "Resultado del día", 19, "calc"), ("Q", "Observaciones", 40, "in"),
    ("R", "aux_prodSistema", 6, "aux"), ("S", "aux_trio", 6, "aux"), ("T", "aux_persona", 6, "aux"),
    ("U", "aux_enRango", 6, "aux"), ("V", "aux_valido", 6, "aux"), ("W", "aux_validoTurno", 6, "aux"),
    ("X", "aux_kpi", 6, "aux"), ("Y", "aux_prodReal", 6, "aux"), ("Z", "aux_cumple", 6, "aux"),
    ("AA", "aux_trioFiltrado", 6, "aux"), ("AB", "aux_diaScore", 6, "aux"), ("AC", "aux_repetida", 6, "aux"),
    ("AD", "aux_validoPersona", 6, "aux"), ("AE", "aux_autos", 6, "aux"), ("AF", "aux_turno", 6, "aux"),
    ("AG", "aux_fecha", 6, "aux"), ("AH", "aux_nombresFuera", 6, "aux"), ("AI", "aux_clave", 6, "aux"),
    ("AJ", "aux_amAutos", 6, "aux"), ("AK", "aux_amProd", 6, "aux"), ("AL", "aux_amFilas", 6, "aux"),
    ("AM", "aux_segSistema", 6, "aux"), ("AN", "aux_segReal", 6, "aux"), ("AO", "aux_cajeroFiltrado", 6, "aux"),
    ("AP", "aux_boardFiltrado", 6, "aux"), ("AQ", "aux_corredorFiltrado", 6, "aux"), ("AR", "aux_trioVez", 6, "aux"),
    ("AS", "aux_trioTurnos", 6, "aux"), ("AT", "aux_trioAutos", 6, "aux"), ("AU", "aux_trioProd", 6, "aux"),
    ("AV", "aux_trioCumple", 6, "aux"), ("AW", "aux_trioScore", 6, "aux"), ("AX", "aux_revisar", 6, "aux"),
    ("AY", "aux_contexto", 6, "aux"), ("AZ", "aux_desvio", 6, "aux"), ("BA", "aux_idxCajero", 6, "aux"),
    ("BB", "aux_idxBoard", 6, "aux"), ("BC", "aux_idxCorredor", 6, "aux"), ("BD", "aux_kCajero", 6, "aux"),
    ("BE", "aux_kBoard", 6, "aux"), ("BF", "aux_kCorredor", 6, "aux"), ("BG", "aux_kTrio", 6, "aux"),
    ("BH", "aux_nCajero", 6, "aux"), ("BI", "aux_nBoard", 6, "aux"), ("BJ", "aux_nCorredor", 6, "aux"),
    ("BK", "aux_nTrio", 6, "aux"), ("BL", "aux_rCajero", 6, "aux"), ("BM", "aux_rBoard", 6, "aux"),
    ("BN", "aux_rCorredor", 6, "aux"), ("BO", "aux_rTrio", 6, "aux"), ("BP", "aux_r2Cajero", 6, "aux"),
    ("BQ", "aux_r2Board", 6, "aux"), ("BR", "aux_r2Corredor", 6, "aux"), ("BS", "aux_r2Trio", 6, "aux"),
    ("BT", "aux_segValido", 6, "aux"), ("BU", "aux_margenPM", 6, "aux"), ("BV", "aux_trioComparables", 6, "aux"),
    ("BW", "aux_trioSumaR", 6, "aux"), ("BX", "aux_trioSumaR2", 6, "aux"), ("BY", "aux_trioC", 6, "aux"),
    ("BZ", "aux_trioN2", 6, "aux"),
]
title(au, "Registro Auto", None, 17, 15)
au.row_dimensions[3].height = 38
merge(au, "B3:Q3",
      "Cómo se calcula el tiempo real de PM: el sistema acumula todo el día, no parte de cero a las 19:00. "
      "Por eso:  Tiempo real PM = (tiempo del día × autos del día − tiempo AM × autos AM) ÷ autos PM.  "
      "Así se ve quién falla y quién lo logra en su turno. El resultado final del día es siempre el que muestra el sistema al cierre.",
      f=font(9, c="3E4C59"), fl=fill(LIGHT), al=LEFT)
au.row_dimensions[4].height = 20
merge(au, "B4:Q4", "Fila AM: lo que marca el sistema a las 19:00.   Fila PM: lo que marca al cierre (total del día), con la fecha del día en que empezó el turno "
      "aunque cierres pasada la medianoche.   Tiempo en dos casillas: minutos y segundos (ej. 3 y 25).", f=font(9, True, ACC), al=Alignment(vertical="center"))
au.row_dimensions[5].height = 8
au.row_dimensions[6].height = 20
merge(au, "B6:I6", "✎  LLENAS TÚ", f=font(9, True, OR_T), fl=fill(INPUT), al=CENTER)
merge(au, "J6:P6", "⚙  SE CALCULA SOLO", f=font(9, True, MUTED), fl=fill(CALC), al=CENTER)
put(au, "Q6", "✎  LLENAS TÚ", f=font(9, True, OR_T), fl=fill(INPUT), al=CENTER)
header(au, 7, 2, [c[1] for c in cols])
for letter, _, w, kind in cols:
    au.column_dimensions[letter].width = w
    if kind == "aux":
        au.column_dimensions[letter].hidden = True
au["L7"].comment = Comment("AM = tiempo del sistema.\nPM = (tiempo día × autos día − tiempo AM × autos AM) ÷ autos PM.\n"
                           "Muestra el rendimiento real de cada turno.", "Registro")
au["O7"].comment = Comment("Resultado final del día: lo que marca el sistema al cierre (fila PM).", "Registro")

for r in range(R0, R1 + 1):
    # Índice: clave numérica fecha*10+turno (1=AM, 2=PM). Atajo local: el AM del día suele estar en la fila de arriba.
    amKey = f'AG{r}*10+1'
    cond = f'AND(AF{r-1}="AM",AG{r-1}=AG{r})' if r > R0 else 'FALSE'
    hasT = f'OR(NOT(ISBLANK(H{r})),NOT(ISBLANK(I{r})))'
    hN = f'IF(ISBLANK(H{r}),0,--H{r})'
    iN = f'IF(ISBLANK(I{r}),0,--I{r})'
    X = f'IF(AND(ISNUMBER(H{r}),H{r}>0,H{r}<1),ROUND(H{r}*1440,0),IF(OR({hN}<0,{iN}<0,{hN}<>INT({hN})),NA(),{hN}*60+{iN}))'
    pmOnly = f'OR(AF{r}<>"PM",AG{r}="")'
    f = {
        "J": f'=IF(AM{r}="","",IF(AM{r}>{MAX_SEG},"",AM{r}/86400))',
        "K": f'=IF(OR(AG{r}="",AF{r}="",AF{r}="?",AE{r}=""),"",IF(AF{r}="AM",AE{r},AE{r}-AJ{r}))',
        "L": f'=IF(OR(K{r}="",J{r}="",AC{r}>1,N(AL{r})>1),"",IF(AF{r}="AM",J{r},IF(K{r}<=0,"",IF(AJ{r}=0,J{r},(R{r}-AK{r})/K{r}))))',
        "M": (f'=IF(COUNTA(B{r}:I{r})=0,"",IF(ISBLANK(B{r}),"Revisar: falta fecha",IF(AG{r}="","Revisar: fecha no válida",'
              f'IF(AF{r}="","Revisar: falta turno",IF(AF{r}="?","Revisar: turno no válido (AM o PM)",IF(AC{r}>1,"Revisar: fila repetida",IF(N(AL{r})>1,"Revisar: el AM de este día está repetido",'
              f'IF(AND(NOT(ISBLANK(G{r})),AE{r}=""),"Revisar: autos no válidos",IF(AND({hasT},J{r}=""),"Revisar: tiempo no válido",'
              f'IF(OR(AE{r}="",J{r}=""),"Falta autos o tiempo",IF(AND(AF{r}="PM",K{r}<=0),"Revisar: autos del día menores que AM",'
              f'IF(L{r}="","Revisar datos",{lvl_s("AN"+str(r))}&IF(AND(BU{r}>={DUDOSO_SEG},ABS(AN{r}-240)<BU{r}),"{DUDOSO}","")))))))))))))'),
        "N": f'=IF(AN{r}="","",IF(AN{r}<=0,"",IF(AN{r}<240,"Sí","No")))',
        "O": f'=IF(OR(AF{r}<>"PM",J{r}="",AC{r}>1,AG{r}=""),"",J{r})',
        "P": f'=IF(O{r}="","",IF(ROUND(O{r}*86400,0)<240,"Cumplió","Falló")&IF(AL{r}=0," (solo PM)",""))',
        "R": f'=IF(OR(J{r}="",AE{r}=""),0,J{r}*AE{r})',
        "S": f'=IFERROR(IF(OR(D{r}="",E{r}="",F{r}=""),"",D{r}&" · "&E{r}&" · "&F{r}),"")',
        "T": f'=IFERROR(IF(fPersona="Todas",1,IF(OR(TRIM(D{r})=fPersona,TRIM(E{r})=fPersona,TRIM(F{r})=fPersona),1,0)),0)',
        "U": f'=IF(AG{r}="",0,IF(AND(AG{r}>=fDesde,AG{r}<=fHasta),1,0))',
        "V": f'=IF(AND(U{r}=1,N{r}<>""),1,0)',
        "W": f'=IF(AND(V{r}=1,OR(fTurno="Total",AF{r}=fTurno)),1,0)',
        "X": f'=W{r}*T{r}',
        "Y": f'=IF(V{r}=1,K{r}*L{r},0)',
        "Z": f'=IF(N{r}="Sí",1,0)',
        "AA": f'=IF(AND(W{r}=1,S{r}<>""),S{r},"")',
        "AB": f'=IF(AND(U{r}=1,O{r}<>""),O{r}+ROW()/10000000000,"")',
        "AC": f'=IF(AI{r}="",0,COUNTIF({rng("AI")},AI{r}))',
        "AD": f'=V{r}*T{r}',
        "AE": f'=IF(ISBLANK(G{r}),"",IFERROR(IF(OR(--G{r}<0,--G{r}<>INT(--G{r}),--G{r}>{MAX_AUTOS}),"",--G{r}),""))',
        "AF": f_turno(f"C{r}"),
        "AG": f_fecha(f"B{r}"),
        "AH": "=" + "+".join(f'IFERROR(AND(ISTEXT({c}{r}),COUNTIF(Personal!$B$8:$B$107,{c}{r})=0)*1,0)' for c in "DEF"),
        "AI": f'=IF(OR(AG{r}="",AF{r}="",AF{r}="?"),"",AG{r}*10+IF(AF{r}="AM",1,2))',
        "AJ": f'=IF({pmOnly},"",IF(AL{r}=1,IF({cond},N(AE{r-1 if r > R0 else r}),SUMIF({rng("AI")},{amKey},{rng("AE")})),0))',
        "AK": f'=IF({pmOnly},"",IF(AL{r}=1,IF({cond},N(R{r-1 if r > R0 else r}),SUMIF({rng("AI")},{amKey},{rng("R")})),0))',
        "AL": f'=IF({pmOnly},"",COUNTIF({rng("AI")},{amKey}))',
        "AM": f'=IF(NOT({hasT}),"",IFERROR({X},""))',
        "AN": f'=IF(L{r}="","",ROUND(L{r}*86400,0))',
        "AO": f'=IF(W{r}=1,IFERROR(IF(ISTEXT(D{r}),D{r},""),""),"")',
        "AP": f'=IF(W{r}=1,IFERROR(IF(ISTEXT(E{r}),E{r},""),""),"")',
        "AQ": f'=IF(W{r}=1,IFERROR(IF(ISTEXT(F{r}),F{r},""),""),"")',
        "AR": f'=IF(AA{r}="","",COUNTIF(AA${R0}:AA{r},AA{r}))',
        "AS": f'=IF(AR{r}=fMin,COUNTIF({rng("AA")},AA{r}),"")',
        "AT": f'=IF(AS{r}="","",SUMIF({rng("AA")},AA{r},{rng("K")}))',
        "AU": f'=IF(AS{r}="","",SUMIF({rng("AA")},AA{r},{rng("Y")}))',
        "AV": f'=IF(AS{r}="","",SUMIF({rng("AA")},AA{r},{rng("Z")}))',
        "AW": f'=IF(OR(AS{r}="",N(BV{r})<fMin,N(AT{r})<=0),"",BW{r}/(BV{r}+kTrio)+MATCH(AA{r},{rng("AA")},0)/1000000000)',
        "AX": f'=IF(OR(LEFT(M{r},7)="Revisar",LEFT(M{r},5)="Falta"),1,0)',
        # ---- comparación justa: contexto (turno × día de semana) y "contra los demás" ----
        "AY": f'=IF(AND(U{r}=1,AI{r}<>""),IF(AF{r}="PM",7,0)+WEEKDAY(AG{r},2),"")',
        "AZ": f'=IF(OR(V{r}<>1,AY{r}=""),"",L{r}*86400-INDEX(ctxBase,AY{r}))',
        "BT": f'=IF(V{r}=1,L{r}*86400,"")',
        "BU": f'=IF(AND(AF{r}="PM",N(AJ{r})>0,N(K{r})>0),0.5*(AE{r}+AJ{r})/K{r},0)',
        "BG": f'=IF(OR(AY{r}="",S{r}=""),"",AY{r}*10000+MATCH(S{r},{rng("S")},0))',
        "BV": f'=IF(AS{r}="","",COUNTIFS({rng("AA")},AA{r},{rng("BO")},">-1E9"))',
        "BW": f'=IF(AS{r}="","",SUMIF({rng("AA")},AA{r},{rng("BO")}))',
        "BX": f'=IF(AS{r}="","",SUMIF({rng("AA")},AA{r},{rng("BS")}))',
        "BY": f'=IF(N(BV{r})>0,BW{r}^2/BV{r},"")',
        "BZ": f'=IF(N(BV{r})>0,BV{r}^2,"")',
    }
    for idx_c, name_c, k_c, n_c, r_c, r2_c in (("BA", "D", "BD", "BH", "BL", "BP"), ("BB", "E", "BE", "BI", "BM", "BQ"),
                                                ("BC", "F", "BF", "BJ", "BN", "BR"), (None, None, "BG", "BK", "BO", "BS")):
        if idx_c:
            f[idx_c] = f'=IF(ISTEXT({name_c}{r}),IFERROR(MATCH({name_c}{r},Personal!$B$8:$B$107,0),""),"")'
            f[k_c] = f'=IF(OR(AY{r}="",{idx_c}{r}=""),"",AY{r}*1000+{idx_c}{r})'
        f[n_c] = f'=IF({k_c}{r}="","",COUNTIF({rng(k_c)},{k_c}{r}))'
        f[r_c] = (f'=IF(OR(W{r}<>1,AZ{r}="",{k_c}{r}=""),"",IF(INDEX(ctxN,AY{r})-{n_c}{r}>0,'
                  f'AZ{r}*INDEX(ctxN,AY{r})/(INDEX(ctxN,AY{r})-{n_c}{r}),""))')
        f[r2_c] = f'=IF({r_c}{r}="","",{r_c}{r}^2)'

    au.row_dimensions[r].height = 22
    for letter, _, _, kind in cols:
        c = au[f"{letter}{r}"]
        if letter in f:
            c.value = f[letter]
        c.border = BOT
        c.font = font(10, c="1F2933" if kind != "calc" else "3E4C59")
        c.alignment = Alignment(horizontal="left" if letter in "DEFMPQ" else "center", vertical="center", indent=1 if letter in "DEFMPQ" else 0)
        if kind == "in": c.fill = fill(INPUT); c.protection = UNLOCK
        elif kind == "calc": c.fill = fill(CALC)
    au[f"B{r}"].number_format = DATE
    for c_ in "JLO": au[f"{c_}{r}"].number_format = TIME
    au[f"Q{r}"].alignment = Alignment(horizontal="left", vertical="center", wrap_text=False, indent=1)
add_table(au, "TablaAuto", f"B7:BZ{R1}")
au.freeze_panes = "D8"

ejemplos_auto = [
    (dt.date(2026, 11, 16), "AM", "Ana", "Bruno", "Carla", 120, 3, 20, "EJEMPLO – borrar"),
    (dt.date(2026, 11, 16), "PM", "Diego", "Elena", "Felipe", 210, 3, 30, "EJEMPLO – borrar"),
    (dt.date(2026, 11, 17), "AM", "Diego", "Bruno", "Felipe", 110, 5, 0, "EJEMPLO – borrar"),
    (dt.date(2026, 11, 17), "PM", "Ana", "Elena", "Carla", 200, 4, 0, "EJEMPLO – borrar · AM falló, PM lo logró, pero el día falló"),
]

v_turno = dv(("Turno", "Elige AM o PM con la flechita."), type="list", formula1='"AM,PM"', errorTitle="Turno", error="Elige AM o PM con la flechita de la celda.")
v_pers = dv(("Nombre", "Elige con la flechita. Si no aparece, agrégalo en la hoja Personal."), type="list", formula1="ListaPersonal", errorTitle="Nombre", error="Elige un nombre con la flechita. Si la persona no está, agrégala primero en la hoja Personal.")
v_autos = dv(("Autos", "El número de autos que marca el sistema. Solo números, ej: 120."), type="whole", operator="between", formula1="0", formula2=str(MAX_AUTOS), errorTitle="Autos", error="Escribe solo el número de autos, sin letras. Ejemplo: 120")
v_min = dv(("Minutos", "Solo los minutos. Para 3:25 escribe 3 aquí y 25 en Seg."), type="whole", operator="between", formula1="0", formula2="19", errorTitle="Minutos", error="Escribe solo los minutos (0 a 19). Para 3:25 escribe 3 aquí y 25 en la casilla de al lado.")
v_seg = dv(("Segundos", "Solo los segundos. Para 3:25 escribe 25 aquí."), type="whole", operator="between", formula1="0", formula2="59", errorTitle="Segundos", error="Escribe solo los segundos (0 a 59).")
v_fecha = dv_fecha("16-11-2026")
for v, c_ in [(v_turno, "C"), (v_pers, "D"), (v_pers, "E"), (v_pers, "F"), (v_autos, "G"), (v_min, "H"), (v_seg, "I"), (v_fecha, "B")]:
    v.add(f"{c_}{R0}:{c_}{R1}")
for v in {id(x): x for x in [v_turno, v_pers, v_autos, v_min, v_seg, v_fecha]}.values():
    au.add_data_validation(v)

cf_time(au, f"J{R0}:J{R1}", f"J{R0}")
cf_time(au, f"L{R0}:L{R1}", f"L{R0}")
cf_time(au, f"O{R0}:O{R1}", f"O{R0}")
cf_text(au, f"M{R0}:M{R1}", f"M{R0}", [
    ('LEFT({c},17)="Excelencia máxima"', G_DK, "FFFFFF"), ('OR(LEFT({c},10)="Excelencia",LEFT({c},9)="Muy bueno")', G_MD, G_TX),
    ('LEFT({c},6)="Cumple"', G_LT, G_TX), ('LEFT({c},18)="No cumple (alerta)"', OR_F, OR_T), ('LEFT({c},9)="No cumple"', RD_F, RD_T),
    ('LEFT({c},7)="Revisar"', OR_F, OR_T)])
cf_text(au, f"N{R0}:N{R1}", f"N{R0}", GOOD_BAD)
cf_text(au, f"P{R0}:P{R1}", f"P{R0}", [('LEFT({c},7)="Cumplió"', G_LT, G_TX), ('LEFT({c},5)="Falló"', RD_F, RD_T)])
au.conditional_formatting.add(f"B{R0}:C{R1}", FormulaRule(formula=[f'OR($AC{R0}>1,AND(NOT(ISBLANK($B{R0})),$AG{R0}=""),$AF{R0}="?")'], fill=fill(OR_F), font=Font(name=FONT, color=OR_T, bold=True)))
au.conditional_formatting.add(f"G{R0}:I{R1}", FormulaRule(formula=[f'OR(AND(NOT(ISBLANK($G{R0})),$AE{R0}=""),LEFT($M{R0},22)="Revisar: tiempo no vál")'], fill=fill(OR_F), font=Font(name=FONT, color=OR_T, bold=True)))
au.conditional_formatting.add(f"D{R0}:F{R1}", FormulaRule(formula=[f'AND(ISTEXT(D{R0}),COUNTIF(Personal!$B$8:$B$107,D{R0})=0)'], fill=fill(OR_F), font=Font(name=FONT, color=OR_T, bold=True)))
protect(au)

# =====================================================================
# REGISTRO DELIVERY
# =====================================================================
de = sheet("Registro Delivery")
dcols = [("B", "Fecha", 13, "in"), ("C", "Turno", 9, "in"), ("D", "Enlace a la foto (pega la URL de Drive)", 42, "in"),
         ("E", "Abrir", 13, "calc"), ("F", "¿Concuerdan?", 13, "in"), ("G", "Estado", 30, "calc"), ("H", "Observaciones", 40, "in"),
         ("I", "aux_valida", 6, "aux"), ("J", "aux_enRango", 6, "aux"), ("K", "aux_enRangoTurno", 6, "aux"),
         ("L", "aux_concuerda", 6, "aux"), ("M", "aux_turno", 6, "aux"), ("N", "aux_fecha", 6, "aux"), ("O", "aux_repetida", 6, "aux"),
         ("P", "aux_clave", 6, "aux")]
title(de, "Registro Delivery", None, 8, 7)
au_note = ("Una fila por turno. Sube la foto de las comandas junto a la tableta a Drive, copia su enlace y pégalo en la columna "
           "\"Enlace\". Revisa a mano si coinciden y marca Sí o No. La columna Estado te dice si la fila quedó bien.")
de.row_dimensions[3].height = 34
merge(de, "B3:H3", au_note, f=font(9, c="3E4C59"), fl=fill(LIGHT), al=LEFT)
de.row_dimensions[4].height = 8; de.row_dimensions[5].height = 8
de.row_dimensions[6].height = 20
merge(de, "B6:D6", "✎  LLENAS TÚ", f=font(9, True, OR_T), fl=fill(INPUT), al=CENTER)
put(de, "E6", "⚙  SOLO", f=font(9, True, MUTED), fl=fill(CALC), al=CENTER)
put(de, "F6", "✎  LLENAS TÚ", f=font(9, True, OR_T), fl=fill(INPUT), al=CENTER)
put(de, "G6", "⚙  SOLO", f=font(9, True, MUTED), fl=fill(CALC), al=CENTER)
put(de, "H6", "✎  LLENAS TÚ", f=font(9, True, OR_T), fl=fill(INPUT), al=CENTER)
header(de, 7, 2, [c[1] for c in dcols])
for letter, _, w, kind in dcols:
    de.column_dimensions[letter].width = w
    if kind == "aux": de.column_dimensions[letter].hidden = True
for r in range(R0, R1 + 1):
    f = {"E": f'=IF(ISBLANK(D{r}),"",IFERROR(IF(LEFT(TRIM(D{r}),4)="http",HYPERLINK(TRIM(D{r}),"Abrir foto ↗"),"Enlace no válido"),"Enlace no válido"))',
         "G": (f'=IF(COUNTA(B{r}:D{r},F{r})=0,"",IF(ISBLANK(B{r}),"Revisar: falta fecha",IF(N{r}="","Revisar: fecha no válida",'
               f'IF(M{r}="","Revisar: falta turno",IF(M{r}="?","Revisar: turno no válido (AM o PM)",IF(O{r}>1,"Revisar: fila repetida",'
               f'IF(L{r}="","Falta marcar Sí o No",IF(L{r}="?","Revisar: escribe solo Sí o No",IF(ISBLANK(D{r}),"OK · falta el enlace",'
               f'IF(IFERROR(LEFT(TRIM(D{r}),4)<>"http",TRUE),"OK · revisa el enlace","OK"))))))))))'),
         "I": f'=IF(LEFT(G{r},2)="OK",1,0)',
         "J": f'=IF(I{r}=1,IF(AND(N{r}>=fDesde,N{r}<=fHasta),1,0),0)',
         "K": f'=IF(AND(J{r}=1,OR(fTurno="Total",M{r}=fTurno)),1,0)',
         "L": f'=IF(ISBLANK(F{r}),"",IFERROR(IF(OR(TRIM(F{r})="Sí",TRIM(F{r})="Si"),"Sí",IF(TRIM(F{r})="No","No","?")),"?"))',
         "M": f_turno(f"C{r}"),
         "N": f_fecha(f"B{r}"),
         "O": f'=IF(P{r}="",0,COUNTIF({rng("P")},P{r}))',
         "P": f'=IF(OR(N{r}="",M{r}="",M{r}="?"),"",N{r}*10+IF(M{r}="AM",1,2))'}
    de.row_dimensions[r].height = 22
    for letter, _, _, kind in dcols:
        c = de[f"{letter}{r}"]
        if letter in f: c.value = f[letter]
        c.border = BOT
        c.font = font(10, True, ACC, u="single") if letter == "E" else font(10)
        c.alignment = Alignment(horizontal="left" if letter in "DGH" else "center", vertical="center", indent=1 if letter in "DGH" else 0)
        if kind == "in": c.fill = fill(INPUT); c.protection = UNLOCK
        elif kind == "calc": c.fill = fill(CALC)
    de[f"B{r}"].number_format = DATE
add_table(de, "TablaDelivery", f"B7:P{R1}")
de.freeze_panes = "D8"
v_turno2 = dv(("Turno", "Elige AM o PM con la flechita."), type="list", formula1='"AM,PM"', errorTitle="Turno", error="Elige AM o PM con la flechita de la celda."); v_turno2.add(f"C{R0}:C{R1}")
v_sn = dv(("¿Concuerdan?", "Elige Sí o No con la flechita."), type="list", formula1='"Sí,No"', errorTitle="¿Concuerdan?", error="Elige Sí o No con la flechita de la celda."); v_sn.add(f"F{R0}:F{R1}")
v_fecha2 = dv_fecha("16-11-2026"); v_fecha2.add(f"B{R0}:B{R1}")
v_link = dv(("Enlace", "Pega aquí el enlace de Drive (Ctrl + V) y presiona Enter."), type="textLength", operator="greaterThan", formula1="0", showErrorMessage=False); v_link.add(f"D{R0}:D{R1}")
for v in (v_turno2, v_sn, v_fecha2, v_link): de.add_data_validation(v)
cf_text(de, f"F{R0}:F{R1}", f"$L{R0}", [('{c}="Sí"', G_LT, G_TX), ('{c}="No"', RD_F, RD_T), ('{c}="?"', OR_F, OR_T)])
cf_text(de, f"G{R0}:G{R1}", f"$G{R0}", [('{c}="OK"', G_LT, G_TX), ('OR(LEFT({c},7)="Revisar",LEFT({c},5)="Falta")', OR_F, OR_T), ('LEFT({c},4)="OK ·"', CALC, OR_T)])
de.conditional_formatting.add(f"B{R0}:C{R1}", FormulaRule(formula=[f'OR($O{R0}>1,AND(NOT(ISBLANK($B{R0})),$N{R0}=""),$M{R0}="?")'], fill=fill(OR_F), font=Font(name=FONT, color=OR_T, bold=True)))
protect(de)
ejemplos_del = [
    (dt.date(2026, 11, 16), "AM", "https://drive.google.com/ejemplo-16nov-am", "Sí", "EJEMPLO – borrar"),
    (dt.date(2026, 11, 16), "PM", "https://drive.google.com/ejemplo-16nov-pm", "No", "EJEMPLO – borrar · faltó 1 pedido en la foto"),
    (dt.date(2026, 11, 17), "AM", "https://drive.google.com/ejemplo-17nov-am", "Sí", "EJEMPLO – borrar"),
    (dt.date(2026, 11, 17), "PM", "https://drive.google.com/ejemplo-17nov-pm", "Sí", "EJEMPLO – borrar"),
]

# =====================================================================
# HORARIOS
# =====================================================================
ho = sheet("Horarios")
ho.column_dimensions["B"].width = 26; ho.column_dimensions["C"].width = 9
for c_ in "DEFGHIJ": ho.column_dimensions[c_].width = 13
title(ho, "Horarios semanales", None, 10, 9)
ho.row_dimensions[3].height = 34
merge(ho, "B3:J3", "Cada semana usa el siguiente marco vacío. Escribe las fechas en \"Semana del __ al __\" y pega el horario en la celda "
      "marcada con clic derecho → Pegar valores (ícono 123). Cada marco tiene espacio para 43 personas. Los marcos anteriores no se tocan.", f=font(9, c="3E4C59"), fl=fill(LIGHT), al=LEFT)
put(ho, "B5", "IR A LA SEMANA", f=font(9, True, MUTED))
N_FR, PASTE_H, FR0 = 52, 45, 13
FR_H = PASTE_H + 6
for k in range(N_FR):
    rr, cc = 6 + k // 9, 2 + k % 9
    s = FR0 + k * FR_H
    ref = f"{L(cc)}{rr}"
    c = ho[ref]; c.value = f"Semana {k+1}"
    c.hyperlink = Hyperlink(ref=ref, location=f"'Horarios'!B{s}", display=c.value)
    c.font = font(9, c=ACC, u="single"); c.alignment = CENTER
for rr in range(6, 12): ho.row_dimensions[rr].height = 18
v_fecha3 = dv_fecha("10-11-2026")
for k in range(N_FR):
    s = FR0 + k * FR_H
    ho.row_dimensions[s].height = 26
    merge(ho, f"B{s}:J{s}", f"SEMANA {k+1}", f=font(11, True, "FFFFFF"), fl=fill(PRI), al=LEFT)
    ho.row_dimensions[s + 1].height = 24
    put(ho, f"B{s+1}", "Semana del", f=font(10, True, PRI), al=Alignment(horizontal="right", vertical="center", indent=1))
    ho.merge_cells(f"C{s+1}:D{s+1}")
    put(ho, f"C{s+1}", None, fl=fill(INPUT), al=CENTER, nf=DATE, bd=BOX, f=font(10, True))
    put(ho, f"E{s+1}", "al", f=font(10, True, PRI), al=CENTER)
    ho.merge_cells(f"F{s+1}:G{s+1}")
    put(ho, f"F{s+1}", None, fl=fill(INPUT), al=CENTER, nf=DATE, bd=BOX, f=font(10, True))
    v_fecha3.add(f"C{s+1}"); v_fecha3.add(f"F{s+1}")
    unlock(ho, f"C{s+1}", f"D{s+1}", f"F{s+1}", f"G{s+1}")
    ho.row_dimensions[s + 2].height = 22
    merge(ho, f"B{s+2}:J{s+2}", f"▼  PEGA AQUÍ EL HORARIO: clic en la celda B{s+3} → clic derecho → Pegar valores (123)",
          f=font(9, True, OR_T), al=Alignment(vertical="center"))
    for rr in range(s + 3, s + 3 + PASTE_H):
        ho.row_dimensions[rr].height = 18
        for c_ in "KLM": ho[f"{c_}{rr}"].protection = UNLOCK   # margen por si el horario trae columnas de más
        for c_ in "BCDEFGHIJ":
            cell = ho[f"{c_}{rr}"]
            cell.fill = fill(INPUT); cell.border = BOX; cell.font = font(10); cell.protection = UNLOCK
            cell.alignment = Alignment(horizontal="left" if c_ == "B" else "center", vertical="center")
            if rr == s + 3 and c_ != "B": cell.number_format = "d-mmm"
    put(ho, f"B{s+3}", "◤ Pega aquí", f=font(9, i=True, c=MUTED))
ho.add_data_validation(v_fecha3)
ho.freeze_panes = "A4"
protect(ho)

# =====================================================================
# REPORTES
# =====================================================================
rp = sheet("Reportes")
RP1 = 7 + 300
rcols = [("B", "Desde", 13, "in"), ("C", "Hasta", 13, "in"), ("D", "Tipo", 13, "in"),
         ("E", "Enlace al reporte (pega la URL de Drive)", 44, "in"), ("F", "Abrir", 15, "calc"), ("G", "Notas", 46, "in")]
title(rp, "Reportes de delivery", "Una fila por cada reporte mensual o quincenal que llega. Pega el enlace de Drive; la columna \"Abrir\" se crea sola.", 7, 6)
rp.row_dimensions[4].height = 8; rp.row_dimensions[5].height = 8
header(rp, 6, 2, [c[1] for c in rcols])
for letter, _, w, _ in rcols: rp.column_dimensions[letter].width = w
for r in range(7, RP1 + 1):
    rp.row_dimensions[r].height = 22
    for letter, _, _, kind in rcols:
        c = rp[f"{letter}{r}"]
        c.border = BOT
        c.font = font(10, True, ACC, u="single") if letter == "F" else font(10)
        c.fill = fill(INPUT if kind == "in" else CALC)
        if kind == "in": c.protection = UNLOCK
        c.alignment = Alignment(horizontal="left" if letter in "EG" else "center", vertical="center", indent=1 if letter in "EG" else 0)
    rp[f"F{r}"] = f'=IF(ISBLANK(E{r}),"",IFERROR(IF(LEFT(TRIM(E{r}),4)="http",HYPERLINK(TRIM(E{r}),"Abrir reporte ↗"),"Enlace no válido"),"Enlace no válido"))'
    rp[f"B{r}"].number_format = DATE; rp[f"C{r}"].number_format = DATE
add_table(rp, "TablaReportes", f"B6:G{RP1}")
rp.freeze_panes = "B7"
v_tipo = dv(("Tipo", "Elige Mensual o Quincenal con la flechita."), type="list", formula1='"Mensual,Quincenal"', errorTitle="Tipo", error="Elige Mensual o Quincenal con la flechita."); v_tipo.add(f"D7:D{RP1}")
v_rf = dv_fecha("01-11-2026"); v_rf.add(f"B7:C{RP1}")
rp.add_data_validation(v_tipo); rp.add_data_validation(v_rf)
protect(rp)
ejemplos_rep = [
    (dt.date(2026, 10, 1), dt.date(2026, 10, 31), "Mensual", "https://drive.google.com/ejemplo-reporte-oct", "EJEMPLO – borrar"),
    (dt.date(2026, 11, 1), dt.date(2026, 11, 15), "Quincenal", "https://drive.google.com/ejemplo-reporte-nov-q1", "EJEMPLO – borrar"),
]

# =====================================================================
# PERSONAL
# =====================================================================
pe = sheet("Personal")
P0, P1 = 8, 107
title(pe, "Personal", None, 4, 4)
pe.column_dimensions["B"].width = 28; pe.column_dimensions["C"].width = 44
pe.column_dimensions["D"].width = 12; pe.column_dimensions["E"].width = 12
pe.row_dimensions[3].height = 48
merge(pe, "B3:C3", "Esta lista alimenta los desplegables de Cajero, Board, Corredor y el filtro Persona del Panel. "
      "Escribe un nombre por fila, siempre igual (ej. \"Ana P.\"). Hay espacio para 100. Si alguien se va, déjalo: sus datos antiguos siguen contando. Un nombre repetido se pinta de naranja.",
      f=font(9, c="3E4C59"), fl=fill(LIGHT), al=LEFT)
pe.row_dimensions[4].height = 8; pe.row_dimensions[5].height = 8; pe.row_dimensions[6].height = 8
header(pe, 7, 2, ["Nombre", "Notas (opcional)"])
for r in range(P0, P1 + 1):
    pe.row_dimensions[r].height = 22
    for c_ in "BC":
        c = pe[f"{c_}{r}"]; c.fill = fill(INPUT); c.border = BOT; c.font = font(10); c.alignment = Alignment(vertical="center", indent=1); c.protection = UNLOCK
add_table(pe, "TablaPersonal", f"B7:C{P1}")
pe.freeze_panes = "B8"
pe.conditional_formatting.add(f"B{P0}:B{P1}", FormulaRule(formula=[f'AND(ISTEXT(B{P0}),COUNTIF($B${P0}:$B${P1},B{P0})>1)'], fill=fill(OR_F), font=Font(name=FONT, color=OR_T, bold=True)))
v_nom = dv(("Nombre", "Un nombre por fila, siempre escrito igual. Hay espacio para 100."), type="textLength", operator="lessThan", formula1="40", errorTitle="Nombre", error="El nombre es muy largo (máximo 40 letras)."); v_nom.add(f"B{P0}:B{P1}")
pe.add_data_validation(v_nom)
protect(pe)
nombres_ej = ["Ana", "Bruno", "Carla", "Diego", "Elena", "Felipe"]

# =====================================================================
# CÁLCULOS (oculta)
# =====================================================================
ca = sheet("Calculos")
ca.sheet_state = "hidden"
protect(ca)
put(ca, "A1", "Hoja auxiliar del Panel. No editar.", f=font(10, True))
for i, (lab, frm) in enumerate([
    ("fDesde", '=IFERROR(IF(AND(ISNUMBER(Panel!$B$7),ISNUMBER(Panel!$D$7)),INT(MIN(Panel!$B$7,Panel!$D$7)),IF(ISNUMBER(Panel!$B$7),INT(Panel!$B$7),0)),0)'),
    ("fHasta", '=IFERROR(IF(AND(ISNUMBER(Panel!$B$7),ISNUMBER(Panel!$D$7)),INT(MAX(Panel!$B$7,Panel!$D$7)),IF(ISNUMBER(Panel!$D$7),INT(Panel!$D$7),2958465)),2958465)'),
    ("fTurno", '=IFERROR(IF(OR(TRIM(Panel!$F$7&"")="AM",TRIM(Panel!$F$7&"")="PM"),UPPER(TRIM(Panel!$F$7)),"Total"),"Total")'),
    ("fCanal", '=IFERROR(IF(TRIM(Panel!$H$7&"")="Auto","Auto",IF(TRIM(Panel!$H$7&"")="Delivery","Delivery","Ambos")),"Ambos")'),
    ("fPersona", '=IFERROR(IF(OR(TRIM(Panel!$J$7&"")="",TRIM(Panel!$J$7&"")="Todas"),"Todas",TRIM(Panel!$J$7)),"Todas")'),
    ("fMin", '=IF(ISNUMBER(Panel!$L$7),IF(Panel!$L$7>=1,INT(Panel!$L$7),3),3)'),
]):
    r = 2 + i
    put(ca, f"A{r}", lab); put(ca, f"B{r}", frm)
    wb.defined_names[lab] = DefinedName(lab, attr_text=f"Calculos!$B${r}")
wb.defined_names["ListaPersonal"] = DefinedName("ListaPersonal", attr_text=f"Personal!$B${P0}:$B${P1}")
put(ca, "D1", "Lista filtro persona"); put(ca, "D2", "Todas")
for i in range(100):
    put(ca, f"D{3+i}", f'=IFERROR(IF(OR(TRIM(Personal!$B${P0+i}&"")="",COUNTIF(Personal!$B${P0}:$B${P0+i},Personal!$B${P0+i})>1),"",Personal!$B${P0+i}),"")')
put(ca, "E1", "nombre_repetido")
for i in range(100):
    p = P0 + i
    put(ca, f"E{3+i}", f'=IFERROR(IF(AND(ISTEXT(Personal!$B${p}),TRIM(Personal!$B${p}&"")<>"",COUNTIF(Personal!$B${P0}:$B${P1},Personal!$B${p})>1),1,0),0)')
wb.defined_names["ListaFiltroPersona"] = DefinedName("ListaFiltroPersona", attr_text="Calculos!$D$2:$D$102")

# tabla de contextos (1-7 = AM lunes..domingo, 8-14 = PM lunes..domingo)
CTX0 = 110
for j, h in enumerate(["contexto", "turnos_programados", "turnos_validos", "suma_seg", "lo_normal_seg"]):
    put(ca, f"{L(1 + j)}{CTX0 - 1}", h)
for g in range(1, 15):
    r = CTX0 + g - 1
    ca[f"A{r}"] = g
    ca[f"B{r}"] = f'=COUNTIF({ra("AY")},A{r})'
    ca[f"C{r}"] = f'=COUNTIFS({ra("AY")},A{r},{ra("BT")},">-1E9")'
    ca[f"D{r}"] = f'=SUMIF({ra("AY")},A{r},{ra("BT")})'
    ca[f"E{r}"] = f'=IF(C{r}>0,D{r}/C{r},0)'
wb.defined_names["ctxN"] = DefinedName("ctxN", attr_text=f"Calculos!$B${CTX0}:$B${CTX0+13}")
wb.defined_names["ctxBase"] = DefinedName("ctxBase", attr_text=f"Calculos!$E${CTX0}:$E${CTX0+13}")

# bloques por estación: datos crudos (para mostrar) + ajuste justo (para ordenar)
RCOLS = ["nombre", "turnos", "autos", "prod", "tiempo", "pct", "ajustado", "comparables", "sumaR", "sumaR2", "c", "n2", "margen", "lectura"]
roles = [("Cajero", "AO", "BL", "BP", 6), ("Board", "AP", "BM", "BQ", 21), ("Corredor", "AQ", "BN", "BR", 36)]
for role, rc, rr_c, r2_c, c0 in roles:
    cl = {h: L(c0 + j) for j, h in enumerate(RCOLS)}
    for h, x in cl.items(): put(ca, f"{x}1", f"{role}_{h}")
    colr = lambda h: f"${cl[h]}$3:${cl[h]}$102"
    s2, kk = eb_resumen(ca, cl["nombre"], 104, colr("comparables"), colr("sumaR"), colr("c"), colr("sumaR2"), colr("n2"))
    for i in range(100):
        r = 3 + i
        c = {h: f"{x}{r}" for h, x in cl.items()}
        ca[c["nombre"]] = f'=Calculos!$D${3+i}'
        ca[c["turnos"]] = f'=IF({c["nombre"]}="",0,COUNTIF({ra(rc)},{c["nombre"]}))'
        ca[c["autos"]] = f'=IF({c["turnos"]}=0,0,SUMIF({ra(rc)},{c["nombre"]},{ra("K")}))'
        ca[c["prod"]] = f'=IF({c["turnos"]}=0,0,SUMIF({ra(rc)},{c["nombre"]},{ra("Y")}))'
        ca[c["tiempo"]] = f'=IF({c["autos"]}>0,{c["prod"]}/{c["autos"]},"")'
        ca[c["pct"]] = f'=IF({c["turnos"]}=0,"",SUMIF({ra(rc)},{c["nombre"]},{ra("Z")})/{c["turnos"]})'
        ca[c["comparables"]] = f'=IF({c["turnos"]}=0,0,COUNTIFS({ra(rc)},{c["nombre"]},{ra(rr_c)},">-1E9"))'
        ca[c["sumaR"]] = f'=IF({c["comparables"]}=0,0,SUMIF({ra(rc)},{c["nombre"]},{ra(rr_c)}))'
        ca[c["sumaR2"]] = f'=IF({c["comparables"]}=0,0,SUMIF({ra(rc)},{c["nombre"]},{ra(r2_c)}))'
        ca[c["c"]] = f'=IF({c["comparables"]}>0,{c["sumaR"]}^2/{c["comparables"]},0)'
        ca[c["n2"]] = f'={c["comparables"]}^2'
        ca[c["ajustado"]] = (f'=IF(AND({c["comparables"]}>=fMin,N({c["autos"]})>0),'
                             f'{c["sumaR"]}/({c["comparables"]}+{kk})+ROW()/1000000000,"")')
        ca[c["margen"]] = f'=IF(OR({c["ajustado"]}="",NOT(ISNUMBER({s2}))),"",{Z95}*SQRT({s2}/({c["comparables"]}+{kk})))'
        ca[c["lectura"]] = "=" + lectura_f(c["ajustado"], c["margen"])
put(ca, "A126", "Tríos (filas representativas del registro)")
TRIO_S2, TRIO_K = eb_resumen(ca, "A", 127, ra("BV"), ra("BW"), ra("BY"), ra("BX"), ra("BZ"))
wb.defined_names["kTrio"] = DefinedName("kTrio", attr_text=TRIO_K)

# =====================================================================
# PANEL
# =====================================================================
for c_ in "BCDEFGHIJKLM": pan.column_dimensions[c_].width = 12.5
pan.column_dimensions["N"].width = 3
title(pan, "Panel de rendimiento", "Todo se actualiza solo. Solo se escriben los filtros (celdas crema).", 13, 12)

# filtros
pan.row_dimensions[4].height = 30
PB = f"Personal!$B${P0}:$B${P1}"
aviso = ('=IFERROR(IF(AND(NOT(ISBLANK(Panel!$B$7)),NOT(ISNUMBER(Panel!$B$7))),"Desde no es una fecha (se ignora). ",""),"Desde no es una fecha (se ignora). ")'
         '&IFERROR(IF(AND(NOT(ISBLANK(Panel!$D$7)),NOT(ISNUMBER(Panel!$D$7))),"Hasta no es una fecha (se ignora). ",""),"Hasta no es una fecha (se ignora). ")'
         '&IFERROR(IF(AND(ISNUMBER(Panel!$B$7),ISNUMBER(Panel!$D$7)),IF(Panel!$B$7>Panel!$D$7,"Desde era posterior a Hasta: se invirtieron. ",""),""),"")'
         '&IFERROR(IF(AND(NOT(ISBLANK(Panel!$F$7)),fTurno="Total",UPPER(TRIM(Panel!$F$7))<>"TOTAL"),"Turno no válido: se muestra Total. ",""),"Turno no válido: se muestra Total. ")'
         '&IFERROR(IF(AND(NOT(ISBLANK(Panel!$H$7)),fCanal="Ambos",UPPER(TRIM(Panel!$H$7))<>"AMBOS"),"Canal no válido: se muestra Ambos. ",""),"Canal no válido: se muestra Ambos. ")'
         f'&IFERROR(IF(AND(fPersona<>"Todas",COUNTIF({PB},fPersona)=0),"La persona elegida no está en Personal. ",""),"")'
         '&IFERROR(IF(AND(NOT(ISBLANK(Panel!$L$7)),NOT(AND(ISNUMBER(Panel!$L$7),Panel!$L$7>=1))),"Mín. turnos no válido: se usa 3. ",""),"Mín. turnos no válido: se usa 3. ")'
         f'&IF(SUM({ra("AX")})>0,SUM({ra("AX")})&" fila(s) de Registro Auto por revisar o completar (columna Nivel). ","")'
         f'&IF(COUNTIF({rd("G")},"Revisar*")+COUNTIF({rd("G")},"Falta*")>0,COUNTIF({rd("G")},"Revisar*")+COUNTIF({rd("G")},"Falta*")&" fila(s) de Registro Delivery por revisar (columna Estado). ","")'
         f'&IF(SUM({ra("AH")})>0,SUM({ra("AH")})&" nombre(s) en Registro Auto no están en Personal (en naranja). ","")'
         '&IF(SUM(Calculos!$E$3:$E$102)>0,"Hay nombres repetidos en Personal (en naranja). ","")'
         f'&IFERROR(IF(COUNTIFS({ra("B")},"<>",{ra("M")},"")+COUNTIFS({rd("B")},"<>",{rd("G")},"")>0,"Hay filas sin fórmulas (se borraron o se insertó una fila a mano): ver Instrucciones, punto 11. ",""),"")'
         f'&IF(COUNTA({ra("B")})>={N_ROWS}-60,"Quedan "&({N_ROWS}-COUNTA({ra("B")}))&" filas libres en Registro Auto (ver Instrucciones, punto 11). ","")'
         f'&IF(COUNTA({rd("B")})>={N_ROWS}-60,"Quedan "&({N_ROWS}-COUNTA({rd("B")}))&" filas libres en Registro Delivery. ","")')
ca["A9"] = "aviso"; ca["B9"] = aviso
merge(pan, "B4:M4", '=IF(Calculos!$B$9="","","⚠  "&Calculos!$B$9)', f=font(9, True, RD_T), al=Alignment(vertical="center", wrap_text=True))
pan.row_dimensions[5].height = 18
put(pan, "B5", "FILTROS", f=font(9, True, MUTED))
pan.row_dimensions[6].height = 18
pan.row_dimensions[7].height = 28
filtros = [("B", "C", "Desde"), ("D", "E", "Hasta"), ("F", "G", "Turno"), ("H", "I", "Canal"), ("J", "K", "Persona"), ("L", "M", "Mín. turnos ranking")]
for a, b, lab in filtros:
    merge(pan, f"{a}6:{b}6", lab, f=font(9, True, PRI), al=Alignment(horizontal="left", vertical="bottom", indent=1))
    pan.merge_cells(f"{a}7:{b}7")
    for c_ in (a, b):
        pan[f"{c_}7"].fill = fill(INPUT); pan[f"{c_}7"].border = BOX
    pan[f"{a}7"].font = font(11, True); pan[f"{a}7"].alignment = CENTER
pan["B7"].number_format = DATE; pan["D7"].number_format = DATE
pan["F7"] = "Total"; pan["H7"] = "Ambos"; pan["J7"] = "Todas"; pan["L7"] = 3
unlock(pan, "B7:M7")
pv = [dv_fecha("16-11-2026", ". Vacío = sin límite.", ", o déjala vacía."),
      dv(("Turno", "Elige AM, PM o Total con la flechita."), type="list", formula1='"AM,PM,Total"', errorTitle="Turno", error="Elige AM, PM o Total con la flechita."),
      dv(("Canal", "Elige Auto, Delivery o Ambos con la flechita."), type="list", formula1='"Auto,Delivery,Ambos"', errorTitle="Canal", error="Elige Auto, Delivery o Ambos con la flechita."),
      dv(("Persona", "Elige un nombre o Todas con la flechita."), type="list", formula1="ListaFiltroPersona", errorTitle="Persona", error="Elige un nombre de la lista o Todas."),
      dv(("Mínimo de turnos", "Cuántos turnos necesita alguien para entrar al ranking. Normal: 3."), type="whole", operator="between", formula1="1", formula2="100", errorTitle="Mínimo", error="Escribe un número entre 1 y 100.")]
pv[0].add("B7"); pv[0].add("D7"); pv[1].add("F7"); pv[2].add("H7"); pv[3].add("J7"); pv[4].add("L7")
for v in pv: pan.add_data_validation(v)
pan.row_dimensions[8].height = 18
merge(pan, "B8:M8", "Un solo día: pon la misma fecha en Desde y Hasta.  Una semana: lunes en Desde, domingo en Hasta.  Vacío = todo el historial.  "
      "Persona filtra solo Auto.", f=font(8, i=True, c=MUTED), al=Alignment(vertical="center"))

AUTO_OK = 'fCanal<>"Delivery"'
DEL_OK = 'fCanal<>"Auto"'
X, K_, Y_, N_ = ra("X"), ra("K"), ra("Y"), ra("N")
auto_t = f'SUMIFS({Y_},{X},1)/SUMIFS({K_},{X},1)'

def card(r0, col0, label, value_formula, nf, sub_formula):
    a, b = L(col0), L(col0 + 2)
    for rr in range(r0, r0 + 4):
        for cc in range(col0, col0 + 3):
            cell = pan[f"{L(cc)}{rr}"]
            cell.fill = fill(LIGHT)
    for cc in range(col0, col0 + 3):
        pan[f"{L(cc)}{r0}"].border = Border(top=Side(style="thick", color=ACC))
    merge(pan, f"{a}{r0}:{b}{r0}", label, f=font(9, True, MUTED), fl=fill(LIGHT), al=Alignment(horizontal="left", vertical="center", indent=1))
    merge(pan, f"{a}{r0+1}:{b}{r0+2}", value_formula, f=font(24, True, PRI), fl=fill(LIGHT), al=Alignment(horizontal="left", vertical="center", indent=1), nf=nf)
    merge(pan, f"{a}{r0+3}:{b}{r0+3}", sub_formula, f=font(8, c=MUTED), fl=fill(LIGHT), al=Alignment(horizontal="left", vertical="center", indent=1))
    return f"{a}{r0+1}"

pan.row_dimensions[9].height = 14
for rr, h in zip(range(10, 14), [22, 22, 22, 20]): pan.row_dimensions[rr].height = h
pan.row_dimensions[14].height = 12
for rr, h in zip(range(15, 19), [22, 22, 22, 20]): pan.row_dimensions[rr].height = h

d_turn = rd("K")
c_autos = card(10, 2, "AUTOS ATENDIDOS", f'=IF({AUTO_OK},SUMIFS({K_},{X},1),"—")', "#,##0",
               f'=IF({AUTO_OK},"Turno: "&fTurno&" · Persona: "&fPersona,"Canal Auto oculto")')
c_t = card(10, 5, "TIEMPO PROMEDIO REAL", f'=IF({AUTO_OK},IFERROR({auto_t},"—"),"—")', TIME,
           '="Ponderado por autos · meta bajo 4:00"')
c_pct = card(10, 8, "TURNOS AUTO DENTRO DE LA META", f'=IF({AUTO_OK},IFERROR(SUMIF({X},1,{ra("Z")})/COUNTIFS({X},1),"—"),"—")', "0%",
             f'=IF({AUTO_OK},SUMIF({X},1,{ra("Z")})&" de "&COUNTIFS({X},1)&" turnos bajo 4:00","")')
c_del = card(10, 11, "DELIVERY QUE CONCUERDA", f'=IF({DEL_OK},IFERROR(COUNTIFS({d_turn},1,{rd("L")},"Sí")/(COUNTIFS({d_turn},1,{rd("L")},"Sí")+COUNTIFS({d_turn},1,{rd("L")},"No")),"—"),"—")', "0%",
             f'=IF({DEL_OK},COUNTIFS({d_turn},1,{rd("L")},"Sí")&" de "&(COUNTIFS({d_turn},1,{rd("L")},"Sí")+COUNTIFS({d_turn},1,{rd("L")},"No"))&" turnos revisados","Canal Delivery oculto")')
c_dias = card(15, 2, "DÍAS QUE CUMPLIERON (RESULTADO FINAL)", f'=IF({AUTO_OK},IFERROR(COUNTIFS({ra("U")},1,{ra("P")},"Cumplió*")/COUNTIFS({ra("U")},1,{ra("P")},"?*"),"—"),"—")', "0%",
              f'=IF({AUTO_OK},COUNTIFS({ra("U")},1,{ra("P")},"Cumplió*")&" de "&COUNTIFS({ra("U")},1,{ra("P")},"?*")&" días · según sistema al cierre","")')
c_niv = card(15, 5, "NIVEL DEL PERÍODO", f'=IF(ISNUMBER({c_t}),{lvl(c_t)},"—")', None, '="Según el tiempo promedio real"')
c_ta = card(15, 8, "TURNOS AUTO REGISTRADOS", f'=IF({AUTO_OK},COUNTIFS({X},1),"—")', "0", '="Con datos completos en el rango"')
c_td = card(15, 11, "TURNOS DELIVERY REGISTRADOS", f'=IF({DEL_OK},COUNTIFS({d_turn},1,{rd("L")},"Sí")+COUNTIFS({d_turn},1,{rd("L")},"No"),"—")', "0", '="Con Sí/No marcado"')
cf_time(pan, c_t, c_t)
cf_text(pan, c_niv, c_niv, [('{c}="Excelencia máxima"', G_DK, "FFFFFF"), ('OR({c}="Excelencia",{c}="Muy bueno",{c}="Cumple")', G_LT, G_TX),
                            ('{c}="No cumple (alerta)"', OR_F, OR_T), ('{c}="No cumple"', RD_F, RD_T)])

# ----- AM vs PM vs Total -----
def section(r, text, note=None):
    pan.row_dimensions[r].height = 26
    merge(pan, f"B{r}:M{r}", text, f=font(12, True, PRI), al=Alignment(vertical="bottom"), bd=None)
    for cc in "BCDEFGHIJKLM":
        pan[f"{cc}{r}"].border = Border(bottom=Side(style="medium", color=PRI))
    if note:
        pan.row_dimensions[r + 1].height = 18
        merge(pan, f"B{r+1}:M{r+1}", note, f=font(8, i=True, c=MUTED), al=Alignment(vertical="center"))

pan.row_dimensions[19].height = 14
section(20, "AM  vs  PM  vs  Total del día", "El tiempo de PM es el real del turno (descontando AM). La columna Total coincide con el resultado del sistema al cierre. Respeta fechas, canal y persona.")
r = 22
pan.row_dimensions[r].height = 26
merge(pan, f"B{r}:E{r}", "Indicador", f=font(10, True, "FFFFFF"), fl=fill(PRI), al=LEFT)
for (a, b), lab in zip([("F", "G"), ("H", "I"), ("J", "K")], ["AM", "PM", "Total día"]):
    merge(pan, f"{a}{r}:{b}{r}", lab, f=font(10, True, "FFFFFF"), fl=fill(PRI), al=CENTER)
VP, C_ = ra("AD"), ra("AF")
def crit(t): return f'{VP},1,{C_},"{t}"'
rows_cmp = [
    ("Autos atendidos", lambda t: f'=IF({AUTO_OK},SUMIFS({K_},{crit(t)}),"—")', "#,##0"),
    ("Tiempo promedio real", lambda t: f'=IF({AUTO_OK},IFERROR(SUMIFS({Y_},{crit(t)})/SUMIFS({K_},{crit(t)}),"—"),"—")', TIME),
    ("Nivel", None, None),
    ("Turnos dentro de la meta", lambda t: f'=IF({AUTO_OK},IFERROR(SUMIFS({ra("Z")},{crit(t)})/COUNTIFS({crit(t)}),"—"),"—")', "0%"),
    ("Turnos auto registrados", lambda t: f'=IF({AUTO_OK},COUNTIFS({crit(t)}),"—")', "0"),
    ("Delivery que concuerda", lambda t: f'=IF({DEL_OK},IFERROR(COUNTIFS({rd("J")},1,{rd("M")},"{t}",{rd("L")},"Sí")/(COUNTIFS({rd("J")},1,{rd("M")},"{t}",{rd("L")},"Sí")+COUNTIFS({rd("J")},1,{rd("M")},"{t}",{rd("L")},"No")),"—"),"—")', "0%"),
]
for i, (lab, fn, nf) in enumerate(rows_cmp):
    rr = r + 1 + i
    pan.row_dimensions[rr].height = 24
    merge(pan, f"B{rr}:E{rr}", lab, f=font(10, c="3E4C59"), al=LEFT, bd=BOT)
    for (a, b), t in zip([("F", "G"), ("H", "I"), ("J", "K")], ["AM", "PM", "*"]):
        if fn is None:
            tref = f"{a}{r+2}"
            val = f'=IF(ISNUMBER({tref}),{lvl(tref)},"—")'
        else:
            val = fn(t)
        merge(pan, f"{a}{rr}:{b}{rr}", val, f=font(11, True, PRI), al=CENTER, nf=nf)
        pan[f"{a}{rr}"].border = BOT; pan[f"{b}{rr}"].border = BOT
    for c_ in "CDE": pan[f"{c_}{rr}"].border = BOT
    if lab == "Tiempo promedio real":
        for a in "FHJ": cf_time(pan, f"{a}{rr}", f"{a}{rr}")
    if lab == "Nivel":
        for a in "FHJ":
            cf_text(pan, f"{a}{rr}", f"{a}{rr}", [('{c}="Excelencia máxima"', G_DK, "FFFFFF"), ('OR({c}="Excelencia",{c}="Muy bueno",{c}="Cumple")', G_LT, G_TX),
                                                 ('{c}="No cumple (alerta)"', OR_F, OR_T), ('{c}="No cumple"', RD_F, RD_T)])
CMP_END = r + len(rows_cmp)

# ----- Rankings por estación (ajuste justo) -----
r = CMP_END + 2
pan.row_dimensions[r - 1].height = 14
section(r, "Ranking por estación (Auto)",
        "Orden justo: cada turno se compara con los DEMÁS turnos del mismo tipo y día de la semana (así no gana quien solo trabaja turnos fáciles), "
        "y a quien tiene pocos turnos se le acerca a lo normal (método bayesiano). Negativo = más rápido que lo normal. ± = margen del 95 %: "
        "si dos márgenes se cruzan, la diferencia puede ser suerte. \"Tiempo real\" es el promedio sin ajustar.")
pan.row_dimensions[r + 1].height = 40
pan[f"B{r+1}"].alignment = Alignment(vertical="center", wrap_text=True)
r += 3
RANK_HEADS = [("B", "B", "#"), ("C", "D", "Nombre"), ("E", "E", "Turnos"), ("F", "F", "Tiempo real"), ("G", "H", "vs. lo normal"),
              ("I", "I", "± margen"), ("J", "K", "Lectura"), ("L", "M", "En meta")]
CF_LECT = [('LEFT({c},11)="Más rápido "', G_LT, G_TX), ('LEFT({c},10)="Más lento "', RD_F, RD_T)]
for role, rc, rr_c, r2_c, c0 in roles:
    cl = {h: L(c0 + j) for j, h in enumerate(RCOLS)}
    colr = lambda h: f"Calculos!${cl[h]}$3:${cl[h]}$102"
    pan.row_dimensions[r].height = 24
    merge(pan, f"B{r}:M{r}", role.upper(), f=font(10, True, "FFFFFF"), fl=fill(ACC), al=CENTER)
    pan.row_dimensions[r + 1].height = 18
    for a, b, h in RANK_HEADS:
        if a != b: pan.merge_cells(f"{a}{r+1}:{b}{r+1}")
        put(pan, f"{a}{r+1}", h, f=font(8, True, MUTED), al=CENTER, bd=BOT); pan[f"{b}{r+1}"].border = BOT
    sc = colr("ajustado")
    for k in range(1, 6):
        rr = r + 1 + k
        pan.row_dimensions[rr].height = 22
        m = f"MATCH(SMALL({sc},{k}),{sc},0)"
        vals = [("B", "B", f'=IF(ISNUMBER(SMALL({sc},{k})),{k},"")', None),
                ("C", "D", f'=IFERROR(INDEX({colr("nombre")},{m}),IF({k}=1,"Sin datos suficientes",""))', None),
                ("E", "E", f'=IFERROR(INDEX({colr("turnos")},{m}),"")', "0"),
                ("F", "F", f'=IFERROR(INDEX({colr("tiempo")},{m}),"")', TIME),
                ("G", "H", f'=IFERROR(INDEX({sc},{m}),"")', FMT_AJ),
                ("I", "I", f'=IFERROR(INDEX({colr("margen")},{m}),"")', FMT_MG),
                ("J", "K", f'=IFERROR(INDEX({colr("lectura")},{m}),"")', None),
                ("L", "M", f'=IFERROR(INDEX({colr("pct")},{m}),"")', "0%")]
        for a, b, v, nf in vals:
            if a != b: pan.merge_cells(f"{a}{rr}:{b}{rr}")
            put(pan, f"{a}{rr}", v, f=font(10, a in "BCG"), al=CENTER, nf=nf, bd=BOT); pan[f"{b}{rr}"].border = BOT
        cf_time(pan, f"F{rr}", f"F{rr}")
        cf_text(pan, f"J{rr}", f"J{rr}", CF_LECT)
    r += 8
RK_END = r - 2

# ----- Ranking de tríos (ajuste justo) -----
r = RK_END + 2
pan.row_dimensions[r - 1].height = 14
section(r, "Ranking de equipos (tríos Cajero · Board · Corredor)", "Cada combinación distinta de tres personas cuenta como un equipo. Mismo método justo y mínimo de turnos.")
r += 2
pan.row_dimensions[r].height = 24
heads = [("B", "B", "#"), ("C", "C", "Cajero"), ("D", "D", "Board"), ("E", "E", "Corredor"), ("F", "F", "Turnos"), ("G", "G", "Tiempo real"),
         ("H", "H", "vs. normal"), ("I", "I", "± margen"), ("J", "K", "Lectura"), ("L", "M", "En meta")]
for a, b, h in heads:
    if a != b: merge(pan, f"{a}{r}:{b}{r}", h, f=font(10, True, "FFFFFF"), fl=fill(ACC), al=CENTER)
    else: put(pan, f"{a}{r}", h, f=font(10, True, "FFFFFF"), fl=fill(ACC), al=CENTER)
TS = ra("AW")
for k in range(1, 6):
    rr = r + k
    pan.row_dimensions[rr].height = 22
    m = f"MATCH(SMALL({TS},{k}),{TS},0)"
    mg = f'IFERROR(IF(ISNUMBER({TRIO_S2}),{Z95}*SQRT({TRIO_S2}/(INDEX({ra("BV")},{m})+kTrio)),""),"")'
    vals = [("B", "B", f'=IF(ISNUMBER(SMALL({TS},{k})),{k},"")', None),
            ("C", "C", f'=IFERROR(INDEX({ra("D")},{m}),IF({k}=1,"Sin datos suficientes",""))', None),
            ("D", "D", f'=IFERROR(INDEX({ra("E")},{m}),"")', None), ("E", "E", f'=IFERROR(INDEX({ra("F")},{m}),"")', None),
            ("F", "F", f'=IFERROR(INDEX({ra("AS")},{m}),"")', "0"),
            ("G", "G", f'=IFERROR(INDEX({ra("AU")},{m})/INDEX({ra("AT")},{m}),"")', TIME),
            ("H", "H", f'=IFERROR(SMALL({TS},{k}),"")', FMT_AJ),
            ("I", "I", "=" + mg, FMT_MG),
            ("J", "K", "=IFERROR(" + lectura_f(f"H{rr}", f"I{rr}") + ',"")', None),
            ("L", "M", f'=IFERROR(INDEX({ra("AV")},{m})/INDEX({ra("AS")},{m}),"")', "0%")]
    for a, b, v, nf in vals:
        if a != b: pan.merge_cells(f"{a}{rr}:{b}{rr}")
        put(pan, f"{a}{rr}", v, f=font(10, a in "BH"), al=Alignment(horizontal="center", vertical="center", shrink_to_fit=True), nf=nf, bd=BOT)
        pan[f"{b}{rr}"].border = BOT
    cf_time(pan, f"G{rr}", f"G{rr}")
    cf_text(pan, f"J{rr}", f"J{rr}", CF_LECT)
TR_END = r + 5

# ----- Mejores días -----
r = TR_END + 2
pan.row_dimensions[r - 1].height = 14
section(r, "Mejores días", "Ordenados por el resultado final del día (lo que marca el sistema al cierre). Respeta el rango de fechas.")
r += 2
pan.row_dimensions[r].height = 24
heads = [("B", "B", "#"), ("C", "D", "Fecha"), ("E", "F", "Tiempo del día"), ("G", "H", "AM real"), ("I", "J", "PM real"), ("K", "K", "Autos"), ("L", "M", "Resultado")]
for a, b, h in heads:
    if a != b: merge(pan, f"{a}{r}:{b}{r}", h, f=font(10, True, "FFFFFF"), fl=fill(ACC), al=CENTER)
    else: put(pan, f"{a}{r}", h, f=font(10, True, "FFFFFF"), fl=fill(ACC), al=CENTER)
DS = ra("AB")
for k in range(1, 6):
    rr = r + k
    pan.row_dimensions[rr].height = 22
    m = f"MATCH(SMALL({DS},{k}),{DS},0)"
    fecha = f"INDEX({ra('AG')},{m})"
    vals = [("B", "B", f'=IF(ISNUMBER(SMALL({DS},{k})),{k},"")', None),
            ("C", "D", f'=IFERROR({fecha},IF({k}=1,"Sin datos",""))', "ddd dd-mmm-yy"),
            ("E", "F", f'=IFERROR(INDEX({ra("O")},{m}),"")', TIME),
            ("G", "H", f'=IFERROR(IF(COUNTIFS({ra("AI")},{fecha}*10+1,{N_},"?*")=0,"—",SUMIF({ra("AI")},{fecha}*10+1,{ra("L")})),"")', TIME),
            ("I", "J", f'=IFERROR(IF(INDEX({ra("L")},{m})="","—",INDEX({ra("L")},{m})),"")', TIME),
            ("K", "K", f'=IFERROR(INDEX({ra("G")},{m}),"")', "#,##0"),
            ("L", "M", f'=IFERROR(INDEX({ra("P")},{m}),"")', None)]
    for a, b, v, nf in vals:
        if a != b: pan.merge_cells(f"{a}{rr}:{b}{rr}")
        put(pan, f"{a}{rr}", v, f=font(10, a in "BE"), al=CENTER, nf=nf, bd=BOT)
        pan[f"{b}{rr}"].border = BOT
    for a in "EGI": cf_time(pan, f"{a}{rr}", f"{a}{rr}")
    cf_text(pan, f"L{rr}", f"L{rr}", [('LEFT({c},7)="Cumplió"', G_LT, G_TX), ('LEFT({c},5)="Falló"', RD_F, RD_T)])
pan.freeze_panes = "A9"
protect(pan)

# =====================================================================
# INSTRUCCIONES
# =====================================================================
ins = sheet("Instrucciones")
ins.column_dimensions["B"].width = 5; ins.column_dimensions["C"].width = 100
title(ins, "Instrucciones", "Guía rápida. No hace falta saber Excel: solo escribe en las celdas crema.", 3, 4)
ins.column_dimensions["D"].width = 12; ins.column_dimensions["E"].width = 12
bloques = [
    ("0", "Lo primero, al abrir el archivo", [
        "Si arriba aparece una franja amarilla que dice \"Vista protegida\" o \"Habilitar edición\", haz clic en \"Habilitar edición\". Sin eso no se puede escribir.",
        "Solo se escribe en las celdas color crema. Las grises se calculan solas y están bloqueadas para que nada se rompa.",
        "Las celdas con flechita se llenan eligiendo de la lista (clic en la celda → clic en la flechita).",
        "Al hacer clic en una celda crema aparece un cartelito amarillo que explica qué escribir.",
    ]),
    ("1", "Cada día · Auto", [
        "Abre la hoja Registro Auto y baja hasta la primera fila vacía (crema).",
        "Fila AM: fecha, turno AM, elige Cajero, Board y Corredor de la lista, y anota los autos y el tiempo que marca el sistema a las 19:00.",
        "Fila PM: lo mismo, pero con lo que marca el sistema al cierre (es el total del día, el archivo separa solo el tiempo de PM).",
        "La fila PM lleva la fecha del día en que empezó el turno, aunque cierres pasada la medianoche. Ejemplo: turno del lunes 16 que cierra a la 01:00 del martes → fecha 16.",
        "El tiempo va en dos casillas: minutos y segundos. Ejemplo: 3 min 25 s → escribe 3 en \"Min\" y 25 en \"Seg\". Si por error escribes 3:25 en \"Min\", el archivo lo entiende igual.",
        "Si el equipo cambió a mitad de turno, anota quién entró en Observaciones. No agregues otra fila.",
        "Mira la columna Nivel: si dice el nivel (Cumple, Muy bueno…), quedó bien. Si dice \"Revisar: …\", te dice qué corregir.",
    ]),
    ("2", "Cada día · Delivery", [
        "En Drive: sube la foto de las comandas junto a la tableta → clic derecho sobre la foto → Compartir → Copiar enlace.",
        "En Registro Delivery: escribe fecha y turno, haz clic en la celda \"Enlace\", pega con Ctrl + V y presiona Enter.",
        "Marca ¿Concuerdan? con Sí o No después de revisar. La columna \"Abrir\" crea un botón para ver la foto.",
        "Mira la columna Estado: \"OK\" = quedó bien. \"OK · falta el enlace\" cuenta igual, pero conviene pegar el enlace. \"Revisar: …\" o \"Falta…\" = hay que corregir.",
    ]),
    ("3", "Cada semana · Horario", [
        "En el Excel del horario, selecciona desde la celda de arriba a la izquierda (A1) hasta la última persona y la columna Domingo. Copia con Ctrl + C.",
        "En este archivo, abre Horarios y usa el primer marco vacío (los enlaces \"Semana 1, 2, 3…\" de arriba te llevan directo).",
        "Escribe las fechas en \"Semana del __ al __\".",
        "Haz clic en la celda que dice \"◤ Pega aquí\" → clic derecho → Opciones de pegado → Valores (ícono 123). Así el marco no pierde su formato.",
        "Cada marco tiene espacio para 43 personas. Si Excel no te deja pegar, revisa que hiciste clic en \"◤ Pega aquí\" y no en el título del marco.",
        "Si pegaste en el marco equivocado, presiona Ctrl + Z de inmediato para deshacer.",
    ]),
    ("4", "Cuando llega un reporte", [
        "En Reportes: escribe desde, hasta, elige Mensual o Quincenal, pega el enlace de Drive (Ctrl + V) y agrega notas si quieres.",
    ]),
    ("5", "Lista de personal", [
        "En Personal escribe un nombre por fila (hay espacio para 100). Siempre igual: \"Ana P.\" y \"Ana\" serían dos personas distintas.",
        "Si llega alguien, agrégalo al final. Si alguien se va, déjalo en la lista: sus datos antiguos siguen contando.",
        "Un nombre repetido se pinta de naranja: borra uno de los dos.",
    ]),
    ("6", "Usar el Panel", [
        "Arriba están los filtros. Un día: misma fecha en Desde y Hasta. Una semana: lunes en Desde y domingo en Hasta. Vacío = todo.",
        "Turno: AM, PM o Total. Canal: Auto, Delivery o Ambos. Persona: muestra solo los turnos donde estuvo esa persona.",
        "Mín. turnos ranking: cuántos turnos necesita alguien para entrar al ranking (normal: 3).",
        "Si arriba del Panel aparece un aviso rojo con ⚠, léelo: dice qué filas o filtros hay que revisar.",
    ]),
    ("6b", "Cómo leer los rankings (para premiar con justicia)", [
        "El orden NO es el tiempo crudo. Cada turno se compara con los demás turnos del mismo tipo (AM o PM) y del mismo día de la semana. Así nadie gana solo por trabajar en turnos más tranquilos.",
        "\"vs. lo normal\": segundos más rápido (negativo, ej. −12 s) o más lento (positivo) que lo normal en sus turnos.",
        "\"± margen\": cuánto puede variar ese número por azar (95 % de seguridad). Si los márgenes de dos personas se cruzan, la diferencia entre ellas puede ser suerte.",
        "\"Lectura\": \"Más rápido que lo normal\" solo aparece cuando la ventaja es clara aun con el margen. \"Dentro de lo normal\" = no hay pruebas de que sea mejor ni peor.",
        "A quien tiene pocos turnos se le acerca a lo normal: así una racha corta de suerte no lo pone primero. Con más turnos, su número refleja más su rendimiento real.",
        "\"Tiempo real\" es el promedio sin ajustar, para referencia. Un cajero rinde con su equipo: el ranking por estación reparte el resultado del turno entre los tres.",
    ]),
    ("7", "Niveles de tiempo", [
        "0:01 a 0:59 Excelencia máxima · 1:00 a 1:59 Excelencia · 2:00 a 2:59 Muy bueno · 3:00 a 3:59 Cumple.",
        "4:00 a 4:59 No cumple (alerta, naranja) · 5:00 o más No cumple (rojo). La meta es estar por debajo de 4:00.",
    ]),
    ("8", "Por qué PM tiene \"tiempo real\"", [
        "El sistema no parte de cero a las 19:00: su promedio es de todo el día. Para ver el rendimiento propio de PM se usa:",
        "Tiempo real PM = (tiempo del día × autos del día − tiempo AM × autos AM) ÷ autos PM.",
        "Ejemplo: AM falló con 5:00, PM lo logró con 2:47, pero el resultado final del día fue 4:00 → el día falló.",
    ]),
    ("9", "Qué significa cada mensaje", [
        "Revisar: falta fecha / fecha no válida → escribe la fecha así: 16-11-2026 (año entre 2020 y 2099).",
        "Revisar: falta turno / turno no válido → elige AM o PM con la flechita.",
        "Revisar: autos no válidos → solo números enteros, sin letras ni espacios (máximo 3.000).",
        "Revisar: tiempo no válido → minutos y segundos por separado, solo números. Más de 20 minutos se considera un error de escritura (ej. 325 en vez de 3 y 25).",
        "Revisar: fila repetida → hay dos filas con la misma fecha y turno. Borra la que sobra. Pasa también si anotaste el PM con la fecha del día siguiente.",
        "Revisar: autos del día menores que AM → en la fila PM va el total del día que marca el sistema, no solo los autos de PM.",
        "Revisar: el AM de este día está repetido → la fila PM no puede calcular su tiempo real hasta que borres el AM que sobra.",
        "\"… · dudoso (PM con pocos autos)\" → el PM tuvo muy pocos autos y su tiempo real quedó tan cerca de 4:00 que el redondeo del sistema podría cambiar si cumple o no. No es un error: tómalo con cautela.",
        "Falta autos o tiempo → la fila está a medias: completa lo que falta.",
        "Una fila marcada con Revisar o Falta no cuenta en el Panel hasta que se corrige. Al corregirla, vuelve a contar sola.",
    ]),
    ("10", "Si algo sale raro", [
        "\"No me deja escribir\" o \"dice que la hoja está protegida\" → hiciste clic en una celda gris. Escribe solo en las crema.",
        "\"Sale un mensaje rojo al escribir\" → el dato no tiene el formato correcto. Lee el mensaje, presiona Reintentar y escríbelo como dice.",
        "\"Quiero borrar las filas de ejemplo\" → selecciona solo las celdas crema de esas filas (de Fecha a Seg) y presiona Suprimir. Luego haz lo mismo con Observaciones.",
        "\"Borré algo sin querer\" → presiona Ctrl + Z enseguida. Si ya guardaste, cierra sin guardar y vuelve a abrir.",
        "\"Una celda se puso naranja\" → hay algo mal escrito en esa fila. Lee la columna Nivel (Auto) o Estado (Delivery).",
        "\"El Panel muestra — o Sin datos\" → no hay datos para esos filtros. Prueba dejando Desde y Hasta vacíos.",
    ]),
    ("11", "Cuando se acaben las filas (después de unos 18 meses)", [
        "El Panel avisa cuando quedan menos de 60 filas.",
        "Guarda una copia de respaldo: Archivo → Guardar como → ponle un nombre como \"Registro hasta dic-2027\".",
        "En este archivo, borra las celdas crema de los registros (solo crema) y sigue anotando desde arriba. El respaldo queda con todo el historial.",
        "Si el Panel dice \"Hay filas sin fórmulas\", alguien quitó la protección y borró o insertó filas. Lo más seguro es volver a una copia anterior.",
        "Solo para quien sabe Excel: las hojas se desprotegen en Revisar → Desproteger hoja (no tienen contraseña). Vuelve a protegerlas al terminar.",
    ]),
]
r = 5
for num, head, items in bloques:
    ins.row_dimensions[r].height = 28
    put(ins, f"B{r}", num, f=font(12, True, "FFFFFF"), fl=fill(PRI), al=CENTER)
    ins.row_dimensions[r].height = 28
    put(ins, f"C{r}", head, f=font(12, True, PRI), al=Alignment(vertical="center", indent=1))
    r += 1
    for it in items:
        ins.row_dimensions[r].height = 20 if len(it) < 105 else (32 if len(it) < 200 else 44)
        put(ins, f"C{r}", "•  " + it, f=font(10, c="3E4C59"), al=Alignment(vertical="center", wrap_text=True, indent=1))
        r += 1
    ins.row_dimensions[r].height = 12
    r += 1

protect(ini); protect(ins)
wb.security = WorkbookProtection(lockStructure=True)

# ---------- datos de ejemplo ----------
if not EMPTY:
    for i, row in enumerate(ejemplos_auto):
        rr = R0 + i
        for c_, v in zip("BCDEFGHIQ", row): au[f"{c_}{rr}"] = v
    for i, row in enumerate(ejemplos_del):
        rr = R0 + i
        for c_, v in zip("BCDFH", row): de[f"{c_}{rr}"] = v
    for i, row in enumerate(ejemplos_rep):
        rr = 7 + i
        for c_, v in zip("BCDEG", row): rp[f"{c_}{rr}"] = v
    for i, nm in enumerate(nombres_ej):
        pe[f"B{P0+i}"] = nm; pe[f"C{P0+i}"] = "EJEMPLO – borrar o reemplazar"

wb.active = 0
wb.save(OUT)
print("ok", OUT)
