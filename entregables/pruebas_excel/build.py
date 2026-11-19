import sys, datetime as dt
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.worksheet.table import Table, TableStyleInfo
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.worksheet.hyperlink import Hyperlink
from openpyxl.formatting.rule import FormulaRule
from openpyxl.workbook.defined_name import DefinedName
from openpyxl.comments import Comment
from openpyxl.utils import get_column_letter as L

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
    link(ws, f"{L(nav_col+1)}2", "Panel ›", "Panel").alignment = CENTER
    if sub:
        ws.row_dimensions[3].height = 20
        merge(ws, f"B3:{L(last_col)}3", sub, f=font(10, c=MUTED), al=Alignment(vertical="center"))

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
    ("X", "aux_kpi", 6, "aux"), ("Y", "aux_prodReal", 6, "aux"), ("Z", "aux_trioPrimero", 6, "aux"),
    ("AA", "aux_trioN", 6, "aux"), ("AB", "aux_diaScore", 6, "aux"), ("AC", "aux_repetida", 6, "aux"),
    ("AD", "aux_validoPersona", 6, "aux"), ("AE", "aux_autos", 6, "aux"), ("AF", "aux_turno", 6, "aux"),
    ("AG", "aux_fecha", 6, "aux"),
]
title(au, "Registro Auto", None, 17, 15)
au.row_dimensions[3].height = 38
merge(au, "B3:Q3",
      "Cómo se calcula el tiempo real de PM: el sistema acumula todo el día, no parte de cero a las 19:00. "
      "Por eso:  Tiempo real PM = (tiempo del día × autos del día − tiempo AM × autos AM) ÷ autos PM.  "
      "Así se ve quién falla y quién lo logra en su turno. El resultado final del día es siempre el que muestra el sistema al cierre.",
      f=font(9, c="3E4C59"), fl=fill(LIGHT), al=LEFT)
au.row_dimensions[4].height = 20
merge(au, "B4:Q4", "Fila AM: anota lo que marca el sistema a las 19:00.   Fila PM: anota lo que marca al cierre (total del día).   "
      "Tiempo en dos casillas: minutos y segundos (ej. 3 y 25).", f=font(9, True, ACC), al=Alignment(vertical="center"))
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

rng = lambda c: f"${c}${R0}:${c}${R1}"
for r in range(R0, R1 + 1):
    amA = f'SUMIFS({rng("AE")},{rng("AG")},AG{r},{rng("AF")},"AM")'
    amP = f'SUMIFS({rng("R")},{rng("AG")},AG{r},{rng("AF")},"AM")'
    s = f"ROUND(L{r}*86400,0)"
    hN = f'IF(TRIM(H{r}&"")="",0,--H{r})'
    iN = f'IF(TRIM(I{r}&"")="",0,--I{r})'
    hasT = f'TRIM(H{r}&"")&TRIM(I{r}&"")<>""'
    f = {
        "J": f'=IF(NOT({hasT}),"",IFERROR(IF(AND(ISNUMBER(H{r}),H{r}>0,H{r}<1),ROUND(H{r}*1440,0),IF(OR({hN}<0,{iN}<0,{hN}<>INT({hN})),NA(),{hN}*60+{iN}))/86400,""))',
        "K": f'=IF(OR(AG{r}="",AF{r}="",AF{r}="?",AE{r}=""),"",IF(AF{r}="AM",AE{r},AE{r}-{amA}))',
        "L": f'=IF(OR(K{r}="",J{r}="",AC{r}>1),"",IF(AF{r}="AM",J{r},IF(K{r}<=0,"",IF({amA}=0,J{r},(R{r}-{amP})/K{r}))))',
        "M": (f'=IF(COUNTA(B{r}:I{r})=0,"",IF(TRIM(B{r}&"")="","Revisar: falta fecha",IF(AG{r}="","Revisar: fecha no válida",'
              f'IF(AF{r}="","Revisar: falta turno",IF(AF{r}="?","Revisar: turno no válido (AM o PM)",IF(AC{r}>1,"Revisar: fila repetida",'
              f'IF(AND(TRIM(G{r}&"")<>"",AE{r}=""),"Revisar: autos no válidos",IF(AND({hasT},J{r}=""),"Revisar: tiempo no válido",'
              f'IF(OR(AE{r}="",J{r}=""),"Falta autos o tiempo",IF(AND(AF{r}="PM",K{r}<=0),"Revisar: autos del día menores que AM",'
              f'IF(L{r}="","Revisar datos",{lvl("L"+str(r))})))))))))))'),
        "N": f'=IF(L{r}="","",IF({s}<=0,"",IF({s}<240,"Sí","No")))',
        "O": f'=IF(OR(AF{r}<>"PM",J{r}="",AC{r}>1,AG{r}=""),"",J{r})',
        "P": f'=IF(O{r}="","",IF(ROUND(O{r}*86400,0)<240,"Cumplió","Falló")&IF(COUNTIFS({rng("AG")},AG{r},{rng("AF")},"AM")=0," (solo PM)",""))',
        "R": f'=IF(OR(J{r}="",AE{r}=""),0,J{r}*AE{r})',
        "S": f'=IF(OR(D{r}="",E{r}="",F{r}=""),"",D{r}&" · "&E{r}&" · "&F{r})',
        "T": f'=IF(fPersona="Todas",1,IF(OR(TRIM(D{r})=fPersona,TRIM(E{r})=fPersona,TRIM(F{r})=fPersona),1,0))',
        "U": f'=IF(AG{r}="",0,IF(AND(AG{r}>=fDesde,AG{r}<=fHasta),1,0))',
        "V": f'=IF(AND(U{r}=1,N{r}<>""),1,0)',
        "W": f'=IF(AND(V{r}=1,OR(fTurno="Total",AF{r}=fTurno)),1,0)',
        "X": f'=W{r}*T{r}',
        "Y": f'=IF(V{r}=1,K{r}*L{r},0)',
        "Z": f'=IF(AND(W{r}=1,S{r}<>""),IF(COUNTIFS(S${R0}:S{r},S{r},W${R0}:W{r},1)=1,1,0),0)',
        "AA": f'=IF(Z{r}=1,COUNTIF(Z${R0}:Z{r},1),"")',
        "AB": f'=IF(AND(U{r}=1,O{r}<>""),O{r}+ROW()/10000000000,"")',
        "AC": f'=IF(OR(AG{r}="",AF{r}="",AF{r}="?"),0,COUNTIFS({rng("AG")},AG{r},{rng("AF")},AF{r}))',
        "AD": f'=V{r}*T{r}',
        "AE": f'=IF(TRIM(G{r}&"")="","",IFERROR(IF(OR(--G{r}<0,--G{r}<>INT(--G{r})),"",--G{r}),""))',
        "AF": f'=IF(TRIM(C{r}&"")="","",IF(OR(TRIM(C{r})="AM",TRIM(C{r})="PM"),UPPER(TRIM(C{r})),"?"))',
        "AG": f'=IF(ISNUMBER(B{r}),INT(B{r}),"")',
    }
    au.row_dimensions[r].height = 22
    for letter, _, _, kind in cols:
        c = au[f"{letter}{r}"]
        if letter in f:
            c.value = f[letter]
        c.border = BOT
        c.font = font(10, c="1F2933" if kind != "calc" else "3E4C59")
        c.alignment = Alignment(horizontal="left" if letter in "DEFMPQ" else "center", vertical="center", indent=1 if letter in "DEFMPQ" else 0)
        if kind == "in": c.fill = fill(INPUT)
        elif kind == "calc": c.fill = fill(CALC)
    au[f"B{r}"].number_format = DATE
    for c_ in "JLO": au[f"{c_}{r}"].number_format = TIME
    au[f"Q{r}"].alignment = Alignment(horizontal="left", vertical="center", wrap_text=False, indent=1)
add_table(au, "TablaAuto", f"B7:AG{R1}")
au.freeze_panes = "D8"

ejemplos_auto = [
    (dt.date(2026, 11, 16), "AM", "Ana", "Bruno", "Carla", 120, 3, 20, "EJEMPLO – borrar"),
    (dt.date(2026, 11, 16), "PM", "Diego", "Elena", "Felipe", 210, 3, 30, "EJEMPLO – borrar"),
    (dt.date(2026, 11, 17), "AM", "Diego", "Bruno", "Felipe", 110, 5, 0, "EJEMPLO – borrar"),
    (dt.date(2026, 11, 17), "PM", "Ana", "Elena", "Carla", 200, 4, 0, "EJEMPLO – borrar · AM falló, PM lo logró, pero el día falló"),
]

dv = lambda **k: DataValidation(showErrorMessage=True, **k)
v_turno = dv(type="list", formula1='"AM,PM"', errorTitle="Turno", error="Elige AM o PM.")
v_pers = dv(type="list", formula1="ListaPersonal", errorTitle="Nombre", error="Elige un nombre de la lista (hoja Personal).")
v_autos = dv(type="whole", operator="between", formula1="0", formula2="5000", errorTitle="Autos", error="Escribe un número entero de autos.")
v_min = dv(type="whole", operator="between", formula1="0", formula2="59", errorTitle="Minutos", error="Escribe solo los minutos (0 a 59).")
v_seg = dv(type="whole", operator="between", formula1="0", formula2="59", errorTitle="Segundos", error="Escribe solo los segundos (0 a 59).")
v_fecha = dv(type="date", operator="greaterThan", formula1="43831", errorTitle="Fecha", error="Escribe una fecha, por ejemplo 16-11-2026.")
for v, c_ in [(v_turno, "C"), (v_pers, "D"), (v_pers, "E"), (v_pers, "F"), (v_autos, "G"), (v_min, "H"), (v_seg, "I"), (v_fecha, "B")]:
    v.add(f"{c_}{R0}:{c_}{R1}")
for v in {id(x): x for x in [v_turno, v_pers, v_autos, v_min, v_seg, v_fecha]}.values():
    au.add_data_validation(v)

cf_time(au, f"J{R0}:J{R1}", f"J{R0}")
cf_time(au, f"L{R0}:L{R1}", f"L{R0}")
cf_time(au, f"O{R0}:O{R1}", f"O{R0}")
cf_text(au, f"M{R0}:M{R1}", f"M{R0}", [
    ('{c}="Excelencia máxima"', G_DK, "FFFFFF"), ('OR({c}="Excelencia",{c}="Muy bueno")', G_MD, G_TX),
    ('{c}="Cumple"', G_LT, G_TX), ('{c}="No cumple (alerta)"', OR_F, OR_T), ('{c}="No cumple"', RD_F, RD_T),
    ('LEFT({c},7)="Revisar"', OR_F, OR_T)])
cf_text(au, f"N{R0}:N{R1}", f"N{R0}", GOOD_BAD)
cf_text(au, f"P{R0}:P{R1}", f"P{R0}", [('LEFT({c},7)="Cumplió"', G_LT, G_TX), ('LEFT({c},5)="Falló"', RD_F, RD_T)])
au.conditional_formatting.add(f"B{R0}:C{R1}", FormulaRule(formula=[f'OR($AC{R0}>1,AND(TRIM($B{R0}&"")<>"",$AG{R0}=""),$AF{R0}="?")'], fill=fill(OR_F), font=Font(name=FONT, color=OR_T, bold=True)))
au.conditional_formatting.add(f"G{R0}:I{R1}", FormulaRule(formula=[f'OR(AND(TRIM($G{R0}&"")<>"",$AE{R0}=""),LEFT($M{R0},22)="Revisar: tiempo no vál")'], fill=fill(OR_F), font=Font(name=FONT, color=OR_T, bold=True)))
au.conditional_formatting.add(f"D{R0}:F{R1}", FormulaRule(formula=[f'AND(TRIM(D{R0}&"")<>"",COUNTIF(Personal!$B$8:$B$107,D{R0})=0)'], fill=fill(OR_F), font=Font(name=FONT, color=OR_T, bold=True)))

# =====================================================================
# REGISTRO DELIVERY
# =====================================================================
de = sheet("Registro Delivery")
dcols = [("B", "Fecha", 13, "in"), ("C", "Turno", 9, "in"), ("D", "Enlace a la foto (pega la URL de Drive)", 46, "in"),
         ("E", "Abrir", 13, "calc"), ("F", "¿Concuerdan?", 13, "in"), ("G", "Observaciones", 46, "in"),
         ("H", "aux_enRango", 6, "aux"), ("I", "aux_enRangoTurno", 6, "aux"),
         ("J", "aux_concuerda", 6, "aux"), ("K", "aux_turno", 6, "aux"), ("L", "aux_fecha", 6, "aux")]
title(de, "Registro Delivery", None, 7, 6)
au_note = ("Una fila por turno. Sube la foto de las comandas junto a la tableta a Drive, copia su enlace y pégalo en la columna "
           "\"Enlace\". Revisa a mano si coinciden y marca Sí o No.")
de.row_dimensions[3].height = 34
merge(de, "B3:G3", au_note, f=font(9, c="3E4C59"), fl=fill(LIGHT), al=LEFT)
de.row_dimensions[4].height = 8; de.row_dimensions[5].height = 8
de.row_dimensions[6].height = 20
merge(de, "B6:D6", "✎  LLENAS TÚ", f=font(9, True, OR_T), fl=fill(INPUT), al=CENTER)
put(de, "E6", "⚙  SOLO", f=font(9, True, MUTED), fl=fill(CALC), al=CENTER)
merge(de, "F6:G6", "✎  LLENAS TÚ", f=font(9, True, OR_T), fl=fill(INPUT), al=CENTER)
header(de, 7, 2, [c[1] for c in dcols])
for letter, _, w, kind in dcols:
    de.column_dimensions[letter].width = w
    if kind == "aux": de.column_dimensions[letter].hidden = True
for r in range(R0, R1 + 1):
    f = {"E": f'=IF(D{r}="","",HYPERLINK(D{r},"Abrir foto ↗"))',
         "H": f'=IF(L{r}="",0,IF(AND(L{r}>=fDesde,L{r}<=fHasta),1,0))',
         "I": f'=IF(AND(H{r}=1,OR(fTurno="Total",K{r}=fTurno)),1,0)',
         "J": f'=IF(TRIM(F{r}&"")="","",IF(OR(TRIM(F{r})="Sí",TRIM(F{r})="Si"),"Sí",IF(TRIM(F{r})="No","No","?")))',
         "K": f'=IF(TRIM(C{r}&"")="","",IF(OR(TRIM(C{r})="AM",TRIM(C{r})="PM"),UPPER(TRIM(C{r})),"?"))',
         "L": f'=IF(ISNUMBER(B{r}),INT(B{r}),"")'}
    de.row_dimensions[r].height = 22
    for letter, _, _, kind in dcols:
        c = de[f"{letter}{r}"]
        if letter in f: c.value = f[letter]
        c.border = BOT
        c.font = font(10, True, ACC, u="single") if letter == "E" else font(10)
        c.alignment = Alignment(horizontal="left" if letter in "DG" else "center", vertical="center", indent=1 if letter in "DG" else 0)
        if kind == "in": c.fill = fill(INPUT)
        elif kind == "calc": c.fill = fill(CALC)
    de[f"B{r}"].number_format = DATE
add_table(de, "TablaDelivery", f"B7:L{R1}")
de.freeze_panes = "D8"
v_turno2 = dv(type="list", formula1='"AM,PM"', errorTitle="Turno", error="Elige AM o PM."); v_turno2.add(f"C{R0}:C{R1}")
v_sn = dv(type="list", formula1='"Sí,No"', errorTitle="¿Concuerdan?", error="Elige Sí o No."); v_sn.add(f"F{R0}:F{R1}")
v_fecha2 = dv(type="date", operator="greaterThan", formula1="43831", errorTitle="Fecha", error="Escribe una fecha."); v_fecha2.add(f"B{R0}:B{R1}")
for v in (v_turno2, v_sn, v_fecha2): de.add_data_validation(v)
cf_text(de, f"F{R0}:F{R1}", f"$J{R0}", [('{c}="Sí"', G_LT, G_TX), ('{c}="No"', RD_F, RD_T), ('{c}="?"', OR_F, OR_T)])
de.conditional_formatting.add(f"B{R0}:C{R1}", FormulaRule(formula=[f'OR(AND(TRIM($B{R0}&"")<>"",$L{R0}=""),$K{R0}="?")'], fill=fill(OR_F), font=Font(name=FONT, color=OR_T, bold=True)))
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
      "marcada con clic derecho → Pegar valores (ícono 123). Los marcos anteriores no se tocan.", f=font(9, c="3E4C59"), fl=fill(LIGHT), al=LEFT)
put(ho, "B5", "IR A LA SEMANA", f=font(9, True, MUTED))
N_FR, FR_H, FR0 = 52, 41, 13
for k in range(N_FR):
    rr, cc = 6 + k // 9, 2 + k % 9
    s = FR0 + k * FR_H
    ref = f"{L(cc)}{rr}"
    c = ho[ref]; c.value = f"Semana {k+1}"
    c.hyperlink = Hyperlink(ref=ref, location=f"'Horarios'!B{s}", display=c.value)
    c.font = font(9, c=ACC, u="single"); c.alignment = CENTER
for rr in range(6, 12): ho.row_dimensions[rr].height = 18
v_fecha3 = dv(type="date", operator="greaterThan", formula1="43831", errorTitle="Fecha", error="Escribe una fecha.")
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
    ho.row_dimensions[s + 2].height = 22
    merge(ho, f"B{s+2}:J{s+2}", f"▼  PEGA AQUÍ EL HORARIO: clic en la celda B{s+3} → clic derecho → Pegar valores (123)",
          f=font(9, True, OR_T), al=Alignment(vertical="center"))
    for rr in range(s + 3, s + 38):
        ho.row_dimensions[rr].height = 18
        for c_ in "BCDEFGHIJ":
            cell = ho[f"{c_}{rr}"]
            cell.fill = fill(INPUT); cell.border = BOX; cell.font = font(10)
            cell.alignment = Alignment(horizontal="left" if c_ == "B" else "center", vertical="center")
            if rr == s + 3 and c_ != "B": cell.number_format = "d-mmm"
    put(ho, f"B{s+3}", "◤ Pega aquí", f=font(9, i=True, c=MUTED))
ho.add_data_validation(v_fecha3)
ho.freeze_panes = "A4"

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
        c.alignment = Alignment(horizontal="left" if letter in "EG" else "center", vertical="center", indent=1 if letter in "EG" else 0)
    rp[f"F{r}"] = f'=IF(E{r}="","",HYPERLINK(E{r},"Abrir reporte ↗"))'
    rp[f"B{r}"].number_format = DATE; rp[f"C{r}"].number_format = DATE
add_table(rp, "TablaReportes", f"B6:G{RP1}")
rp.freeze_panes = "B7"
v_tipo = dv(type="list", formula1='"Mensual,Quincenal"', errorTitle="Tipo", error="Elige Mensual o Quincenal."); v_tipo.add(f"D7:D{RP1}")
rp.add_data_validation(v_tipo)
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
      "Escribe un nombre por fila, siempre igual (ej. \"Ana P.\"). Si alguien se va, puedes dejarlo: sus datos antiguos siguen contando.",
      f=font(9, c="3E4C59"), fl=fill(LIGHT), al=LEFT)
pe.row_dimensions[4].height = 8; pe.row_dimensions[5].height = 8; pe.row_dimensions[6].height = 8
header(pe, 7, 2, ["Nombre", "Notas (opcional)"])
for r in range(P0, P1 + 1):
    pe.row_dimensions[r].height = 22
    for c_ in "BC":
        c = pe[f"{c_}{r}"]; c.fill = fill(INPUT); c.border = BOT; c.font = font(10); c.alignment = Alignment(vertical="center", indent=1)
add_table(pe, "TablaPersonal", f"B7:C{P1}")
pe.freeze_panes = "B8"
nombres_ej = ["Ana", "Bruno", "Carla", "Diego", "Elena", "Felipe"]

# =====================================================================
# CÁLCULOS (oculta)
# =====================================================================
ca = sheet("Calculos")
ca.sheet_state = "hidden"
put(ca, "A1", "Hoja auxiliar del Panel. No editar.", f=font(10, True))
for i, (lab, frm) in enumerate([
    ("fDesde", '=IF(AND(ISNUMBER(Panel!$B$7),ISNUMBER(Panel!$D$7)),INT(MIN(Panel!$B$7,Panel!$D$7)),IF(ISNUMBER(Panel!$B$7),INT(Panel!$B$7),0))'),
    ("fHasta", '=IF(AND(ISNUMBER(Panel!$B$7),ISNUMBER(Panel!$D$7)),INT(MAX(Panel!$B$7,Panel!$D$7)),IF(ISNUMBER(Panel!$D$7),INT(Panel!$D$7),2958465))'),
    ("fTurno", '=IF(OR(TRIM(Panel!$F$7&"")="AM",TRIM(Panel!$F$7&"")="PM"),UPPER(TRIM(Panel!$F$7)),"Total")'),
    ("fCanal", '=IF(TRIM(Panel!$H$7&"")="Auto","Auto",IF(TRIM(Panel!$H$7&"")="Delivery","Delivery","Ambos"))'),
    ("fPersona", '=IF(OR(TRIM(Panel!$J$7&"")="",TRIM(Panel!$J$7&"")="Todas"),"Todas",TRIM(Panel!$J$7))'),
    ("fMin", '=IF(ISNUMBER(Panel!$L$7),IF(Panel!$L$7>=1,INT(Panel!$L$7),3),3)'),
]):
    r = 2 + i
    put(ca, f"A{r}", lab); put(ca, f"B{r}", frm)
    wb.defined_names[lab] = DefinedName(lab, attr_text=f"Calculos!$B${r}")
wb.defined_names["ListaPersonal"] = DefinedName("ListaPersonal", attr_text=f"Personal!$B${P0}:$B${P1}")
put(ca, "D1", "Lista filtro persona"); put(ca, "D2", "Todas")
for i in range(100):
    put(ca, f"D{3+i}", f'=IF(Personal!$B${P0+i}="","",Personal!$B${P0+i})')
wb.defined_names["ListaFiltroPersona"] = DefinedName("ListaFiltroPersona", attr_text="Calculos!$D$2:$D$102")

# bloques de personas por estación: nombre, n, autos, prod, t, pct, score
roles = [("Cajero", "D", 6), ("Board", "E", 14), ("Corredor", "F", 22)]
for role, rc, c0 in roles:
    cl = [L(c0 + j) for j in range(7)]
    for j, h in enumerate(["nombre", "turnos", "autos", "prod", "tiempo", "pct", "score"]):
        put(ca, f"{cl[j]}1", f"{role}_{h}")
    for i in range(100):
        r = 3 + i
        nm, n, au_, pr, t, pc, sc = [f"{x}{r}" for x in cl]
        crit = f"{ra(rc)},{nm},{ra('W')},1"
        ca[nm] = f'=IF(Personal!$B${P0+i}="","",Personal!$B${P0+i})'
        ca[n] = f'=IF({nm}="",0,COUNTIFS({crit}))'
        ca[au_] = f'=IF({n}=0,0,SUMIFS({ra("K")},{crit}))'
        ca[pr] = f'=IF({n}=0,0,SUMIFS({ra("Y")},{crit}))'
        ca[t] = f'=IF({au_}>0,{pr}/{au_},"")'
        ca[pc] = f'=IF({n}=0,"",COUNTIFS({crit},{ra("N")},"Sí")/{n})'
        ca[sc] = f'=IF(AND({n}>=fMin,{t}<>""),{t}+ROW()/10000000000,"")'

# tríos: k, pos, key, caj, board, corr, n, autos, prod, t, pct, score  (AD..AO)
T0 = 30
tc = [L(T0 + j) for j in range(12)]
for j, h in enumerate(["k", "pos", "trio", "cajero", "board", "corredor", "turnos", "autos", "prod", "tiempo", "pct", "score"]):
    put(ca, f"{tc[j]}1", f"trio_{h}")
N_TRIO = 400
for i in range(N_TRIO):
    r = 3 + i
    k, pos, key, cj, bo, co, n, aut, pr, t, pc, sc = [f"{x}{r}" for x in tc]
    ca[k] = i + 1
    ca[pos] = f'=IFERROR(MATCH({k},{ra("AA")},0),"")'
    ca[key] = f'=IF({pos}="","",INDEX({ra("S")},{pos}))'
    ca[cj] = f'=IF({pos}="","",INDEX({ra("D")},{pos}))'
    ca[bo] = f'=IF({pos}="","",INDEX({ra("E")},{pos}))'
    ca[co] = f'=IF({pos}="","",INDEX({ra("F")},{pos}))'
    crit = f"{ra('S')},{key},{ra('W')},1"
    ca[n] = f'=IF({key}="",0,COUNTIFS({crit}))'
    ca[aut] = f'=IF({n}=0,0,SUMIFS({ra("K")},{crit}))'
    ca[pr] = f'=IF({n}=0,0,SUMIFS({ra("Y")},{crit}))'
    ca[t] = f'=IF({aut}>0,{pr}/{aut},"")'
    ca[pc] = f'=IF({n}=0,"",COUNTIFS({crit},{ra("N")},"Sí")/{n})'
    ca[sc] = f'=IF(AND({n}>=fMin,{t}<>""),{t}+ROW()/10000000000,"")'

# =====================================================================
# PANEL
# =====================================================================
for c_ in "BCDEFGHIJKLM": pan.column_dimensions[c_].width = 12.5
pan.column_dimensions["N"].width = 3
title(pan, "Panel de rendimiento", "Todo se actualiza solo. Solo se escriben los filtros (celdas crema).", 13, 12)

# filtros
pan.row_dimensions[4].height = 20
aviso = ('=IF(AND(TRIM(Panel!$B$7&"")<>"",NOT(ISNUMBER(Panel!$B$7))),"Desde no es una fecha (se ignora). ","")'
         '&IF(AND(TRIM(Panel!$D$7&"")<>"",NOT(ISNUMBER(Panel!$D$7))),"Hasta no es una fecha (se ignora). ","")'
         '&IF(AND(ISNUMBER(Panel!$B$7),ISNUMBER(Panel!$D$7)),IF(Panel!$B$7>Panel!$D$7,"Desde era posterior a Hasta: se invirtieron. ",""),"")'
         '&IF(AND(fPersona<>"Todas",COUNTIF(Personal!$B$8:$B$107,fPersona)=0),"La persona no está en la lista de Personal. ","")'
         '&IF(AND(TRIM(Panel!$L$7&"")<>"",NOT(AND(ISNUMBER(Panel!$L$7),Panel!$L$7>=1))),"Mín. turnos no válido: se usa 3. ","")'
         f'&IF(COUNTIF({ra("M")},"Revisar*")>0,COUNTIF({ra("M")},"Revisar*")&" fila(s) para revisar en Registro Auto. ","")'
         f'&IF(COUNTIF({rd("J")},"?")+SUMPRODUCT(({rd("B")}<>"")*({rd("L")}=""))+COUNTIF({rd("K")},"?")>0,"Hay filas mal llenadas en Registro Delivery (en naranja). ","")')
ca["A9"] = "aviso"; ca["B9"] = aviso
merge(pan, "B4:M4", '=IF(Calculos!$B$9="","","⚠  "&Calculos!$B$9)', f=font(9, True, RD_T), al=Alignment(vertical="center"))
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
pv = [dv(type="date", operator="greaterThan", formula1="43831", errorTitle="Fecha", error="Escribe una fecha o deja vacío."),
      dv(type="list", formula1='"AM,PM,Total"', errorTitle="Turno", error="Elige AM, PM o Total."),
      dv(type="list", formula1='"Auto,Delivery,Ambos"', errorTitle="Canal", error="Elige Auto, Delivery o Ambos."),
      dv(type="list", formula1="ListaFiltroPersona", errorTitle="Persona", error="Elige un nombre de la lista o Todas."),
      dv(type="whole", operator="between", formula1="1", formula2="100", errorTitle="Mínimo", error="Escribe un número entre 1 y 100.")]
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

d_turn = rd("I")
c_autos = card(10, 2, "AUTOS ATENDIDOS", f'=IF({AUTO_OK},SUMIFS({K_},{X},1),"—")', "#,##0",
               f'=IF({AUTO_OK},"Turno: "&fTurno&" · Persona: "&fPersona,"Canal Auto oculto")')
c_t = card(10, 5, "TIEMPO PROMEDIO REAL", f'=IF({AUTO_OK},IFERROR({auto_t},"—"),"—")', TIME,
           '="Ponderado por autos · meta bajo 4:00"')
c_pct = card(10, 8, "TURNOS AUTO DENTRO DE LA META", f'=IF({AUTO_OK},IFERROR(COUNTIFS({X},1,{N_},"Sí")/COUNTIFS({X},1),"—"),"—")', "0%",
             f'=IF({AUTO_OK},COUNTIFS({X},1,{N_},"Sí")&" de "&COUNTIFS({X},1)&" turnos bajo 4:00","")')
c_del = card(10, 11, "DELIVERY QUE CONCUERDA", f'=IF({DEL_OK},IFERROR(COUNTIFS({d_turn},1,{rd("J")},"Sí")/(COUNTIFS({d_turn},1,{rd("J")},"Sí")+COUNTIFS({d_turn},1,{rd("J")},"No")),"—"),"—")', "0%",
             f'=IF({DEL_OK},COUNTIFS({d_turn},1,{rd("J")},"Sí")&" de "&(COUNTIFS({d_turn},1,{rd("J")},"Sí")+COUNTIFS({d_turn},1,{rd("J")},"No"))&" turnos revisados","Canal Delivery oculto")')
c_dias = card(15, 2, "DÍAS QUE CUMPLIERON (RESULTADO FINAL)", f'=IF({AUTO_OK},IFERROR(COUNTIFS({ra("U")},1,{ra("P")},"Cumplió*")/COUNTIFS({ra("U")},1,{ra("P")},"?*"),"—"),"—")', "0%",
              f'=IF({AUTO_OK},COUNTIFS({ra("U")},1,{ra("P")},"Cumplió*")&" de "&COUNTIFS({ra("U")},1,{ra("P")},"?*")&" días · según sistema al cierre","")')
c_niv = card(15, 5, "NIVEL DEL PERÍODO", f'=IF(ISNUMBER({c_t}),{lvl(c_t)},"—")', None, '="Según el tiempo promedio real"')
c_ta = card(15, 8, "TURNOS AUTO REGISTRADOS", f'=IF({AUTO_OK},COUNTIFS({X},1),"—")', "0", '="Con datos completos en el rango"')
c_td = card(15, 11, "TURNOS DELIVERY REGISTRADOS", f'=IF({DEL_OK},COUNTIFS({d_turn},1,{rd("J")},"Sí")+COUNTIFS({d_turn},1,{rd("J")},"No"),"—")', "0", '="Con Sí/No marcado"')
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
    ("Turnos dentro de la meta", lambda t: f'=IF({AUTO_OK},IFERROR(COUNTIFS({crit(t)},{N_},"Sí")/COUNTIFS({crit(t)}),"—"),"—")', "0%"),
    ("Turnos auto registrados", lambda t: f'=IF({AUTO_OK},COUNTIFS({crit(t)}),"—")', "0"),
    ("Delivery que concuerda", lambda t: f'=IF({DEL_OK},IFERROR(COUNTIFS({rd("H")},1,{rd("K")},"{t}",{rd("J")},"Sí")/(COUNTIFS({rd("H")},1,{rd("K")},"{t}",{rd("J")},"Sí")+COUNTIFS({rd("H")},1,{rd("K")},"{t}",{rd("J")},"No")),"—"),"—")', "0%"),
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

# ----- Rankings por estación -----
r = CMP_END + 2
pan.row_dimensions[r - 1].height = 14
section(r, "Ranking por estación (Auto)", "Ordenado por tiempo promedio real de los turnos donde estuvo (menor es mejor). Solo entra quien tenga el mínimo de turnos. Respeta fechas y turno: elige AM o PM para ver quién rinde mejor en cada uno.")
r += 2
pan.row_dimensions[r].height = 24
blocks = [("Cajero", 2, roles[0][2]), ("Board", 6, roles[1][2]), ("Corredor", 10, roles[2][2])]
for role, col0, c0 in blocks:
    merge(pan, f"{L(col0)}{r}:{L(col0+3)}{r}", role.upper(), f=font(10, True, "FFFFFF"), fl=fill(ACC), al=CENTER)
    for j, h in enumerate(["Nombre", "Turnos", "Tiempo", "En meta"]):
        put(pan, f"{L(col0+j)}{r+1}", h, f=font(8, True, MUTED), al=CENTER, bd=BOT)
pan.row_dimensions[r + 1].height = 18
for k in range(1, 6):
    rr = r + 1 + k
    pan.row_dimensions[rr].height = 22
    for role, col0, c0 in blocks:
        nm, n, t, pc, sc = [f"Calculos!${L(c0+j)}$3:${L(c0+j)}$102" for j in (0, 1, 4, 5, 6)]
        m = f"MATCH(SMALL({sc},{k}),{sc},0)"
        put(pan, f"{L(col0)}{rr}", f'=IFERROR("{k}. "&INDEX({nm},{m}),IF({k}=1,"Sin datos suficientes","—"))', f=font(10, True, "1F2933"), al=Alignment(horizontal="left", vertical="center", indent=1, shrink_to_fit=True), bd=BOT)
        put(pan, f"{L(col0+1)}{rr}", f'=IFERROR(INDEX({n},{m}),"")', f=font(10), al=CENTER, bd=BOT)
        put(pan, f"{L(col0+2)}{rr}", f'=IFERROR(INDEX({t},{m}),"")', f=font(10, True), al=CENTER, nf=TIME, bd=BOT)
        put(pan, f"{L(col0+3)}{rr}", f'=IFERROR(INDEX({pc},{m}),"")', f=font(10), al=CENTER, nf="0%", bd=BOT)
        cf_time(pan, f"{L(col0+2)}{rr}", f"{L(col0+2)}{rr}")
RK_END = r + 6

# ----- Ranking de tríos -----
r = RK_END + 2
pan.row_dimensions[r - 1].height = 14
section(r, "Ranking de equipos (tríos Cajero · Board · Corredor)", "Cada combinación distinta de tres personas cuenta como un equipo. Mismo criterio y mínimo de turnos.")
r += 2
pan.row_dimensions[r].height = 24
heads = [("B", "B", "#"), ("C", "D", "Cajero"), ("E", "F", "Board"), ("G", "H", "Corredor"), ("I", "I", "Turnos"), ("J", "K", "Tiempo real"), ("L", "M", "En meta")]
for a, b, h in heads:
    if a != b: merge(pan, f"{a}{r}:{b}{r}", h, f=font(10, True, "FFFFFF"), fl=fill(ACC), al=CENTER)
    else: put(pan, f"{a}{r}", h, f=font(10, True, "FFFFFF"), fl=fill(ACC), al=CENTER)
tr = lambda j: f"Calculos!${tc[j]}$3:${tc[j]}${2+N_TRIO}"
for k in range(1, 6):
    rr = r + k
    pan.row_dimensions[rr].height = 22
    m = f"MATCH(SMALL({tr(11)},{k}),{tr(11)},0)"
    vals = [("B", "B", f'=IFERROR(INDEX({tr(0)},{m}),IF({k}=1,"—",""))', None),
            ("C", "D", f'=IFERROR(INDEX({tr(3)},{m}),IF({k}=1,"Sin datos suficientes",""))', None),
            ("E", "F", f'=IFERROR(INDEX({tr(4)},{m}),"")', None), ("G", "H", f'=IFERROR(INDEX({tr(5)},{m}),"")', None),
            ("I", "I", f'=IFERROR(INDEX({tr(6)},{m}),"")', "0"), ("J", "K", f'=IFERROR(INDEX({tr(9)},{m}),"")', TIME),
            ("L", "M", f'=IFERROR(INDEX({tr(10)},{m}),"")', "0%")]
    for a, b, v, nf in vals:
        if a == "B": v = f'=IF(ISNUMBER(SMALL({tr(11)},{k})),{k},"")'
        if a != b: pan.merge_cells(f"{a}{rr}:{b}{rr}")
        put(pan, f"{a}{rr}", v, f=font(10, a in "BJ"), al=CENTER, nf=nf, bd=BOT)
        pan[f"{b}{rr}"].border = BOT
    cf_time(pan, f"J{rr}", f"J{rr}")
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
            ("C", "D", f'=IFERROR({fecha},IF({k}=1,"Sin datos",""))', DATE),
            ("E", "F", f'=IFERROR(INDEX({ra("O")},{m}),"")', TIME),
            ("G", "H", f'=IFERROR(IF(COUNTIFS({ra("AG")},{fecha},{C_},"AM",{N_},"?*")=0,"—",SUMIFS({ra("L")},{ra("AG")},{fecha},{C_},"AM")),"")', TIME),
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

# =====================================================================
# INSTRUCCIONES
# =====================================================================
ins = sheet("Instrucciones")
ins.column_dimensions["B"].width = 5; ins.column_dimensions["C"].width = 100
title(ins, "Instrucciones", "Guía rápida. No hace falta saber Excel: solo escribe en las celdas crema.", 3, 4)
ins.column_dimensions["D"].width = 12; ins.column_dimensions["E"].width = 12
bloques = [
    ("1", "Cada día · Auto", [
        "Abre la hoja Registro Auto y baja hasta la primera fila vacía (crema).",
        "Fila AM: fecha, turno AM, elige Cajero, Board y Corredor de la lista, y anota los autos y el tiempo que marca el sistema a las 19:00.",
        "Fila PM: lo mismo, pero con lo que marca el sistema al cierre (es el total del día, el archivo separa solo el tiempo de PM).",
        "El tiempo va en dos casillas: minutos y segundos. Ejemplo: 3 min 25 s → escribe 3 en \"Min\" y 25 en \"Seg\". Si por error escribes 3:25 en \"Min\", el archivo lo entiende igual como 3 min 25 s.",
        "Si el equipo cambió a mitad de turno, anota quién entró en Observaciones. No agregues otra fila.",
        "Las columnas grises se llenan solas: autos del turno, tiempo real, nivel, si cumple y el resultado final del día.",
    ]),
    ("2", "Cada día · Delivery", [
        "En Drive: sube la foto de las comandas junto a la tableta → clic derecho sobre la foto → Compartir → Copiar enlace.",
        "En Registro Delivery: escribe fecha y turno, haz clic en la celda \"Enlace\", pega con Ctrl + V y presiona Enter.",
        "La columna \"Abrir\" crea un botón para ver la foto. Marca ¿Concuerdan? con Sí o No después de revisar.",
    ]),
    ("3", "Cada semana · Horario", [
        "En el Excel del horario, selecciona desde la celda de arriba a la izquierda (A1) hasta la última persona y la columna Domingo. Copia con Ctrl + C.",
        "En este archivo, abre Horarios y usa el primer marco vacío (los enlaces \"Semana 1, 2, 3…\" arriba te llevan directo).",
        "Escribe las fechas en \"Semana del __ al __\".",
        "Haz clic en la celda que dice \"◤ Pega aquí\" → clic derecho → Opciones de pegado → Valores (ícono 123). Así el marco no pierde su formato.",
        "Si pegaste con Ctrl + V normal y cambió el formato, no pasa nada grave: los datos quedan guardados igual.",
        "No borres ni muevas los marcos de semanas anteriores.",
    ]),
    ("4", "Cuando llega un reporte", [
        "En Reportes: escribe desde, hasta, elige Mensual o Quincenal, pega el enlace de Drive (Ctrl + V) y agrega notas si quieres.",
    ]),
    ("5", "Lista de personal", [
        "En Personal escribe un nombre por fila. Siempre igual: \"Ana P.\" y \"ana\" serían dos personas distintas.",
        "Si llega alguien, agrégalo al final. Si alguien se va, puedes dejarlo: no molesta y sus datos antiguos siguen contando.",
    ]),
    ("6", "Usar el Panel", [
        "Arriba están los filtros. Un día: misma fecha en Desde y Hasta. Una semana: lunes en Desde y domingo en Hasta. Vacío = todo.",
        "Turno: AM, PM o Total. Canal: Auto, Delivery o Ambos. Persona: muestra solo los turnos donde estuvo esa persona.",
        "Mín. turnos ranking: cuántos turnos necesita alguien para entrar al ranking (normal: 3).",
        "Los rankings ordenan por tiempo real promedio: el primero es el más rápido.",
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
    ("9", "Antes de empezar de verdad", [
        "Borra las filas de ejemplo (dicen \"EJEMPLO – borrar\" en Observaciones/Notas): selecciona solo las celdas crema de esas filas y presiona Suprimir.",
        "No borres las columnas grises: tienen las fórmulas.",
        "Si alguna vez se acaban las filas (hay 1.100, unos 18 meses), arrastra la esquina inferior derecha de la tabla hacia abajo.",
        "Si una fila sale en naranja o la columna Nivel dice \"Revisar: …\", ahí mismo dice qué corregir (fecha, turno, autos, tiempo, fila repetida o autos PM menores que AM). Esa fila no cuenta en el Panel hasta corregirla.",
        "El Panel avisa arriba, en rojo, si hay filas por revisar o si un filtro está mal escrito.",
        "Un nombre en naranja en Cajero/Board/Corredor no está en la lista de Personal (revisa espacios o la forma de escribirlo).",
    ]),
]
r = 5
for num, head, items in bloques:
    ins.row_dimensions[r].height = 28
    put(ins, f"B{r}", num, f=font(12, True, "FFFFFF"), fl=fill(PRI), al=CENTER)
    put(ins, f"C{r}", head, f=font(12, True, PRI), al=Alignment(vertical="center", indent=1))
    r += 1
    for it in items:
        ins.row_dimensions[r].height = 20 if len(it) < 110 else 32
        put(ins, f"C{r}", "•  " + it, f=font(10, c="3E4C59"), al=Alignment(vertical="center", wrap_text=True, indent=1))
        r += 1
    ins.row_dimensions[r].height = 12
    r += 1

# ---------- datos de ejemplo ----------
if not EMPTY:
    for i, row in enumerate(ejemplos_auto):
        rr = R0 + i
        for c_, v in zip("BCDEFGHIQ", row): au[f"{c_}{rr}"] = v
    for i, row in enumerate(ejemplos_del):
        rr = R0 + i
        for c_, v in zip("BCDFG", row): de[f"{c_}{rr}"] = v
    for i, row in enumerate(ejemplos_rep):
        rr = 7 + i
        for c_, v in zip("BCDEG", row): rp[f"{c_}{rr}"] = v
    for i, nm in enumerate(nombres_ej):
        pe[f"B{P0+i}"] = nm; pe[f"C{P0+i}"] = "EJEMPLO – borrar o reemplazar"

wb.active = 0
wb.save(OUT)
print("ok", OUT)
