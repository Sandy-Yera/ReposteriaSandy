"""Genera excel/Figuras.xlsx: registro de compras de figuras (CLP) pensado para Excel móvil.

Uso:  python3 excel/generar_figuras.py [ruta_salida.xlsx]
Sin macros, sin Power Query, sin conexiones externas. Solo tablas, validación de datos,
formato condicional y fórmulas estándar (más TEXTJOIN y MAXIFS, disponibles en Excel móvil).
"""
import sys
from datetime import date

from openpyxl import Workbook
from openpyxl.comments import Comment
from openpyxl.formatting.rule import FormulaRule
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.workbook.defined_name import DefinedName
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.worksheet.table import Table, TableStyleInfo

# ---------------------------------------------------------------- constantes
FILAS_COMPRAS = 200   # filas de datos preparadas en cada hoja de compras
FILAS_PAGOS = 300
FILAS_GRUPOS = 50
FILA_ENC = 4          # fila de encabezados de las tablas
FILA_INI = 5          # primera fila de datos

FUENTE = "Arial"
FMT_CLP = '"$"#,##0;-"$"#,##0;"$"0'
FMT_TC = '#,##0.00'
FMT_FECHA = 'dd-mm-yyyy'
FMT_MES = 'mmm-yyyy'

AZUL_ENC = PatternFill("solid", fgColor="1F4E78")    # encabezado de campo que escribes tú
GRIS_ENC = PatternFill("solid", fgColor="595959")    # encabezado de campo calculado
AMARILLO = PatternFill("solid", fgColor="FFF2CC")    # celdas de ingreso
ROJO_FILL = PatternFill("solid", fgColor="FFC7CE")
VERDE_FILL = PatternFill("solid", fgColor="C6EFCE")
NARANJA_FILL = PatternFill("solid", fgColor="FCE4D6")
BLANCO_FILL = PatternFill("solid", fgColor="FFFFFF")
ROJO_TXT = "9C0006"
VERDE_TXT = "006100"

MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]

# Columnas de las hojas de compras: (clave, encabezado, ancho, es_ingreso, nota)
COLS_COMPRAS = [
    ("id", "ID", 6, False, "Identificador fijo del producto (C001, C002...). No lo cambies: los pagos se enlazan a este ID."),
    ("prod", "Producto", 22, True, "Nombre de la figura. Al escribirlo, la fila se activa."),
    ("cant", "Cantidad", 8, True, "Unidades compradas. Si lo dejas vacío se toma 1."),
    ("total", "Total figura CLP", 13, False, "Calculado: cantidad x precio unitario x tipo de cambio aplicado, redondeado a pesos. No incluye envío."),
    ("reglas", "Reglas", 30, False, "Calculado: OK, o el motivo en rojo (mes bloqueado o límite superado) para el mes de este producto. Solo cuentan figuras, nunca envíos."),
    ("estado", "Estado producto", 11, False, "Calculado: Pendiente (sin pagos), Parcial, Pagado (pagado >= total) o Cerrado (si marcas Cerrar = Sí)."),
    ("cerrar", "Cerrar", 8, True, "Sí = das el producto por terminado aunque lo pagado no calce exacto. La diferencia queda en 'Diferencia figura'."),
    ("fecha", "Fecha compra", 11, True, "Fecha en que compraste. Se usa para el mes (si no indicas mes asignado) y para buscar el tipo de cambio sugerido."),
    ("mesasig", "Mes asignado", 11, True, "Opcional. Cualquier fecha dentro del mes al que quieres asignar la compra. Vacío = mes de la fecha de compra."),
    ("tienda", "Tienda", 14, True, "Nombre de la tienda de origen."),
    ("link", "Link", 18, True, "Enlace al producto o a la orden."),
    ("moneda", "Moneda", 8, True, "Moneda del precio: CLP, USD o JPY."),
    ("precio", "Precio unitario", 11, True, "Precio de UNA unidad, en la moneda indicada."),
    ("tcusado", "TC usado", 9, True, "Pesos por 1 USD o 1 JPY el día de la compra. Escríbelo para fijarlo: así el historial no cambia si editas la tabla de Config. En CLP déjalo vacío."),
    ("tcsug", "TC sugerido", 9, False, "Calculado: último tipo de cambio de Config con fecha igual o anterior a la compra. Solo referencia para copiarlo a 'TC usado'."),
    ("tcapl", "TC aplicado", 9, False, "Calculado: 1 si es CLP; 'TC usado' si lo escribiste; si no, el sugerido (aparece aviso 'TC no fijado')."),
    ("envmonto", "Envío monto", 10, True, "Costo de envío de este producto en su moneda. 0 o vacío = sin envío."),
    ("envmoneda", "Envío moneda", 8, True, "Moneda del envío: CLP, USD o JPY. Vacío = misma moneda del producto."),
    ("envclp", "Envío CLP", 10, False, "Calculado: envío en pesos. Si es la misma moneda del producto usa su TC aplicado; si es otra, el TC sugerido de Config para esa fecha."),
    ("acum", "Envío acumulado", 9, True, "Sí = el envío se paga en conjunto con otros (grupo de Envíos acumulados). No = se paga solo."),
    ("grupo", "Grupo envío", 8, True, "Solo si Envío acumulado = Sí: el grupo (G001, G002...) de la hoja Envíos acumulados."),
    ("estenv", "Estado envío", 10, False, "Calculado: Sin envío, Pendiente, Acumulado (en grupo aún no pagado) o Pagado. Independiente del estado del producto."),
    ("pagfig", "Pagado figura CLP", 12, False, "Calculado: suma de pagos tipo Figura de la hoja Pagos asociados a este ID."),
    ("pagenv", "Pagado envío CLP", 12, False, "Calculado: pagos tipo Envío hechos directo a este ID (envío no acumulado). Los envíos acumulados se pagan al grupo."),
    ("falta", "Falta pagar figura", 12, False, "Calculado: total - pagado figura (mínimo 0). Si está Cerrado, 0."),
    ("dif", "Diferencia figura", 12, False, "Calculado solo si está Pagado o Cerrado: pagado - total. Positivo = pagaste de más; negativo = pagaste de menos."),
    ("envpend", "Envío por pagar", 11, False, "Calculado: envío en CLP que aún falta pagar (individual o dentro de un grupo sin pagar)."),
    ("avisos", "Avisos de datos", 24, False, "Calculado: datos faltantes o por revisar (no son reglas de compra)."),
    ("mesef", "Mes efectivo", 10, False, "Calculado: mes que usan las reglas (mes asignado o, si está vacío, el de la fecha de compra)."),
    ("clave", "Clave mes", 8, False, "Auxiliar: año x 12 + mes. Sirve para comparar meses contiguos, también diciembre/enero."),
    ("totmes", "Total figuras mes", 12, False, "Auxiliar: suma de 'Total figura CLP' de todos los productos del mismo mes."),
    ("prodmes", "Productos mes", 8, False, "Auxiliar: productos del mes (unidades o filas, según Config)."),
    ("limmes", "Límite mes", 11, False, "Auxiliar: límite que aplica al mes (normal o excepción)."),
    ("nlista", "N lista", 6, False, "Auxiliar para la lista desplegable de Pagos."),
    ("etiq", "Etiqueta lista", 18, False, "Auxiliar: texto 'ID · Producto' que aparece en la lista de Pagos."),
]
COLS_PAGOS = [
    ("fecha", "Fecha pago", 11, True, "Fecha en que hiciste el pago."),
    ("monto", "Monto CLP", 11, True, "Monto pagado, siempre en pesos chilenos."),
    ("tipo", "Tipo", 8, True, "Figura = pago del producto. Envío = pago de un envío (individual o de un grupo)."),
    ("asoc", "Asociado a", 24, True, "Elige de la lista el producto (C...) o el grupo de envío (G...). También puedes escribir solo el ID."),
    ("nota", "Nota", 20, True, "Texto libre (medio de pago, cuota, etc.)."),
    ("id", "ID", 6, False, "Calculado: los 4 primeros caracteres de 'Asociado a'."),
    ("clave", "Clave mes pago", 9, False, "Auxiliar: año x 12 + mes de la fecha de pago."),
    ("valid", "Validación", 30, False, "Calculado: OK o qué revisar en este pago."),
]
COLS_GRUPOS = [
    ("id", "Grupo", 7, False, "Identificador fijo del grupo (G001...). Úsalo en Compras > Grupo envío y en Pagos."),
    ("desc", "Descripción", 22, True, "Nombre del grupo, por ej. 'Caja casillero EE.UU. dic'. Al escribirlo, el grupo aparece en la lista de Pagos."),
    ("nenv", "N envíos", 7, False, "Calculado: cuántos productos tienen Envío acumulado = Sí con este grupo."),
    ("suma", "Suma individual CLP", 12, False, "Calculado: suma de los envíos individuales (en CLP) de esos productos."),
    ("pagado", "Pagado conjunto CLP", 12, False, "Calculado: suma de pagos tipo Envío asociados a este grupo en la hoja Pagos."),
    ("fpago", "Fecha pago", 11, False, "Calculado: fecha del último pago registrado para el grupo. No hay plazo obligatorio."),
    ("estado", "Estado grupo", 10, False, "Calculado: Pendiente (sin pagos) o Pagado."),
    ("ahorro", "Ahorro CLP", 11, False, "Calculado si está Pagado: suma individual - pagado conjunto. Negativo = pagaste de más (sobrecosto)."),
    ("clave", "Clave mes pago", 9, False, "Auxiliar: año x 12 + mes de la fecha de pago."),
    ("usado", "Usado", 6, False, "Auxiliar: 1 si el grupo tiene descripción o envíos."),
    ("nlista", "N lista", 6, False, "Auxiliar para la lista desplegable de Pagos."),
    ("etiq", "Etiqueta lista", 18, False, "Auxiliar: texto 'Grupo · Descripción' de la lista de Pagos."),
]


def letras(cols):
    """Devuelve {clave: letra de columna} para una lista de columnas."""
    return {c[0]: get_column_letter(i + 1) for i, c in enumerate(cols)}


LC = letras(COLS_COMPRAS)
LP = letras(COLS_PAGOS)
LG = letras(COLS_GRUPOS)


def q(nombre_hoja):
    """Nombre de hoja entre comillas simples para referencias entre hojas."""
    return "'" + nombre_hoja + "'"


def rango(hoja, col, filas):
    """Rango absoluto de una columna de datos, ej. 'Compras'!$D$5:$D$204."""
    return f"{q(hoja)}!${col}${FILA_INI}:${col}${FILA_INI + filas - 1}"


def etiqueta_mes(expr_clave):
    """Fórmula que convierte una clave de mes (año*12+mes) en texto 'mmm-aaaa' sin depender del idioma."""
    lista = ",".join(f'"{m}"' for m in MESES)
    return f'CHOOSE(MOD(({expr_clave})-1,12)+1,{lista})&"-"&INT((({expr_clave})-1)/12)'


def pesos(expr):
    """Fórmula de texto '$1.234' usando el separador de miles del idioma del teléfono."""
    return f'"$"&FIXED({expr},0)'


# ---------------------------------------------------------------- estilos
def estilo_titulo(c, tam=14):
    c.font = Font(name=FUENTE, size=tam, bold=True, color="1F4E78")


def encabezado(ws, fila, cols):
    """Escribe encabezados con color (azul = ingreso, gris = calculado), nota y ancho de columna."""
    for i, (clave, texto, ancho, ingreso, nota) in enumerate(cols, start=1):
        c = ws.cell(row=fila, column=i, value=texto)
        c.font = Font(name=FUENTE, size=11, bold=True, color="FFFFFF")
        c.fill = AZUL_ENC if ingreso else GRIS_ENC
        c.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
        com = Comment(nota, "Figuras")
        com.width, com.height = 260, 110
        c.comment = com
        ws.column_dimensions[get_column_letter(i)].width = ancho
    ws.row_dimensions[fila].height = 32


def formato_columna(ws, col, filas, fmt=None, ingreso=False, ajustar=False):
    """Aplica fuente, formato numérico y relleno de ingreso a las filas de datos de una columna."""
    for r in range(FILA_INI, FILA_INI + filas):
        c = ws[f"{col}{r}"]
        c.font = Font(name=FUENTE, size=11)
        if fmt:
            c.number_format = fmt
        if ingreso:
            c.fill = AMARILLO
        if ajustar:
            c.alignment = Alignment(wrap_text=True, vertical="top")


def agregar_tabla(ws, nombre, ultima_col, filas, estilo="TableStyleLight1"):
    """Crea una tabla de Excel (con filtros) sobre encabezado + filas de datos."""
    ref = f"A{FILA_ENC}:{ultima_col}{FILA_INI + filas - 1}"
    t = Table(displayName=nombre, ref=ref)
    t.tableStyleInfo = TableStyleInfo(name=estilo, showRowStripes=True)
    ws.add_table(t)


def lista_dv(ws, formula, celdas, titulo, msg, permitir_vacio=True, estricto=True):
    """Agrega una validación de lista desplegable con mensaje de ayuda."""
    dv = DataValidation(type="list", formula1=formula, allow_blank=permitir_vacio,
                        showErrorMessage=estricto, showInputMessage=True,
                        promptTitle=titulo[:32], prompt=msg[:255],
                        errorTitle="Valor no válido", error="Elige un valor de la lista.")
    ws.add_data_validation(dv)
    dv.add(celdas)


def num_dv(ws, celdas, titulo, msg, tipo="decimal", minimo=0):
    """Validación numérica (>= mínimo) con mensaje de ayuda."""
    dv = DataValidation(type=tipo, operator="greaterThanOrEqual", formula1=str(minimo),
                        allow_blank=True, showErrorMessage=True, showInputMessage=True,
                        promptTitle=titulo[:32], prompt=msg[:255],
                        errorTitle="Número no válido", error=f"Escribe un número mayor o igual a {minimo}.")
    ws.add_data_validation(dv)
    dv.add(celdas)


def fecha_dv(ws, celdas, titulo, msg):
    """Validación de fecha con mensaje de ayuda."""
    dv = DataValidation(type="date", operator="greaterThan", formula1="36526",
                        allow_blank=True, showErrorMessage=True, showInputMessage=True,
                        promptTitle=titulo[:32], prompt=msg[:255],
                        errorTitle="Fecha no válida", error="Escribe una fecha (ej. 15-11-2026).")
    ws.add_data_validation(dv)
    dv.add(celdas)


def cf_texto(ws, rango_cf, formula, fill, color):
    """Formato condicional por fórmula: relleno + color de texto en negrita."""
    ws.conditional_formatting.add(rango_cf, FormulaRule(
        formula=[formula], fill=fill, font=Font(color=color, bold=True)))


def banner(ws, ultima_col, practica):
    """Fila 2: aviso que cambia según el Modo práctica (visible solo cuando corresponde)."""
    ws.merge_cells(f"A2:{ultima_col}2")
    c = ws["A2"]
    if practica:
        c.value = ('=IF(cfgModo="Sí","DATOS DE PRÁCTICA (simulados). El Panel los está mostrando. '
                   'Tus datos reales siguen intactos en sus hojas.",'
                   '"Modo práctica desactivado: estos datos simulados están ocultos. Actívalo en Config.")')
    else:
        c.value = ('=IF(cfgModo="Sí","MODO PRÁCTICA ACTIVO: el Panel muestra datos simulados. '
                   'Lo que escribas aquí se guarda igual. Desactívalo en Config.","")')
    c.font = Font(name=FUENTE, size=11, bold=True, color="833C0B")
    c.alignment = Alignment(wrap_text=True, vertical="center")
    ws.row_dimensions[2].height = 30
    cf_texto(ws, "A2", 'cfgModo="Sí"', NARANJA_FILL, "833C0B")


def ocultar_si_no_practica(ws, ultima_col, ultima_fila):
    """En hojas de práctica: con Modo práctica = No, texto y relleno blancos (desaparecen)."""
    ws.conditional_formatting.add(
        f"A3:{ultima_col}{ultima_fila}",
        FormulaRule(formula=['cfgModo<>"Sí"'], fill=BLANCO_FILL,
                    font=Font(color="FFFFFF"), stopIfTrue=True))


# ---------------------------------------------------------------- hojas de datos
def hoja_compras(wb, nombre, hoja_pagos, hoja_grupos, tabla, practica=False, demo=None, listas_col="A"):
    """Crea una hoja de compras (real o de práctica) con sus fórmulas, validaciones y formatos."""
    ws = wb.create_sheet(nombre)
    cols = list(COLS_COMPRAS)
    if practica:
        cols.append(("expl", "Explicación (práctica)", 40, False, "Qué caso demuestra esta fila."))
    ult = get_column_letter(len(cols))
    ws["A1"] = "Compras de figuras" + (" — PRÁCTICA" if practica else "")
    estilo_titulo(ws["A1"])
    banner(ws, ult, practica)
    ws["A3"] = "Encabezado azul = lo escribes tú (celda amarilla) · gris = se calcula solo. Toca un encabezado para ver su nota."
    ws["A3"].font = Font(name=FUENTE, size=9, italic=True, color="595959")
    encabezado(ws, FILA_ENC, cols)

    n = FILAS_COMPRAS
    fin = FILA_INI + n - 1
    R = lambda k: f"${LC[k]}${FILA_INI}:${LC[k]}${fin}"          # rango propio absoluto
    PB, PC, PF = (rango(hoja_pagos, LP[k], FILAS_PAGOS) for k in ("monto", "tipo", "id"))

    for r in range(FILA_INI, fin + 1):
        c = {k: f"{LC[k]}{r}" for k in LC}
        cB = f"${LC['prod']}{r}"
        ws[c["id"]] = f"C{r - FILA_INI + 1:03d}"
        ws[c["mesef"]] = (f'=IF({cB}="","",IF(${LC["mesasig"]}{r}<>"",DATE(YEAR(${LC["mesasig"]}{r}),MONTH(${LC["mesasig"]}{r}),1),'
                          f'IF(${LC["fecha"]}{r}<>"",DATE(YEAR(${LC["fecha"]}{r}),MONTH(${LC["fecha"]}{r}),1),"")))')
        ws[c["clave"]] = f'=IF({c["mesef"]}="","",YEAR({c["mesef"]})*12+MONTH({c["mesef"]}))'
        mon, fec = f"${LC['moneda']}{r}", f"${LC['fecha']}{r}"
        maxd = f'_xlfn.MAXIFS(cfgTCFecha,cfgTCMoneda,{mon},cfgTCFecha,"<="&{fec})'
        ws[c["tcsug"]] = (f'=IF(OR({cB}="",{mon}="",{mon}="CLP",{fec}=""),"",'
                          f'IF({maxd}=0,"",AVERAGEIFS(cfgTCValor,cfgTCMoneda,{mon},cfgTCFecha,{maxd})))')
        ws[c["tcapl"]] = (f'=IF(OR({cB}="",{mon}=""),"",IF({mon}="CLP",1,'
                          f'IF(${LC["tcusado"]}{r}<>"",${LC["tcusado"]}{r},{c["tcsug"]})))')
        cant = f'IF(${LC["cant"]}{r}="",1,${LC["cant"]}{r})'
        ws[c["total"]] = (f'=IF(OR({cB}="",{c["tcapl"]}="",${LC["precio"]}{r}=""),"",'
                          f'ROUND({cant}*${LC["precio"]}{r}*{c["tcapl"]},0))')
        me = f'IF(${LC["envmoneda"]}{r}="",{mon},${LC["envmoneda"]}{r})'
        maxe = f'_xlfn.MAXIFS(cfgTCFecha,cfgTCMoneda,{me},cfgTCFecha,"<="&{fec})'
        tce = (f'IF({me}="CLP",1,IF({me}={mon},N({c["tcapl"]}),'
               f'IFERROR(AVERAGEIFS(cfgTCValor,cfgTCMoneda,{me},cfgTCFecha,{maxe}),0)))')
        ws[c["envclp"]] = f'=IF(OR({cB}="",N(${LC["envmonto"]}{r})=0),0,ROUND(${LC["envmonto"]}{r}*{tce},0))'
        ws[c["pagfig"]] = f'=IF({cB}="","",SUMIFS({PB},{PC},"Figura",{PF},${LC["id"]}{r}))'
        ws[c["pagenv"]] = f'=IF({cB}="","",SUMIFS({PB},{PC},"Envío",{PF},${LC["id"]}{r}))'
        ws[c["estado"]] = (f'=IF({cB}="","",IF(${LC["cerrar"]}{r}="Sí","Cerrado",IF(N({c["pagfig"]})=0,"Pendiente",'
                           f'IF({c["total"]}="","Parcial",IF({c["pagfig"]}>={c["total"]},"Pagado","Parcial")))))')
        ws[c["falta"]] = f'=IF({cB}="","",IF({c["estado"]}="Cerrado",0,MAX(0,N({c["total"]})-{c["pagfig"]})))'
        ws[c["dif"]] = (f'=IF({cB}="","",IF(OR({c["estado"]}="Cerrado",{c["estado"]}="Pagado"),'
                        f'{c["pagfig"]}-N({c["total"]}),0))')
        grp = f"${LC['grupo']}{r}"
        pag_grupo = f'SUMIFS({PB},{PC},"Envío",{PF},{grp})'
        ws[c["estenv"]] = (f'=IF({cB}="","",IF({c["envclp"]}=0,"Sin envío",IF(${LC["acum"]}{r}="Sí",'
                           f'IF(AND({grp}<>"",{pag_grupo}>0),"Pagado","Acumulado"),'
                           f'IF({c["pagenv"]}>={c["envclp"]},"Pagado","Pendiente"))))')
        ws[c["envpend"]] = (f'=IF({cB}="","",IF(OR({c["estenv"]}="Pagado",{c["estenv"]}="Sin envío"),0,'
                            f'IF(${LC["acum"]}{r}="Sí",{c["envclp"]},MAX(0,{c["envclp"]}-{c["pagenv"]}))))')
        k = c["clave"]
        ws[c["totmes"]] = f'=IF({k}="","",SUMIFS({R("total")},{R("clave")},{k}))'
        ws[c["prodmes"]] = (f'=IF({k}="","",IF(cfgContar="Filas",COUNTIF({R("clave")},{k}),'
                            f'SUMIFS({R("cant")},{R("clave")},{k})+COUNTIFS({R("clave")},{k},{R("cant")},"")))')
        ws[c["limmes"]] = f'=IF({k}="","",IF({c["prodmes"]}<=cfgMaxProd,cfgLimExc,cfgLimNormal))'
        m_bloq = f'IF(COUNTIF({R("clave")},{k}-1)>0,"Mes bloqueado: el mes "&{etiqueta_mes(k + "-1")}&" ya tiene compras","")'
        m_lim = (f'IF({c["totmes"]}>{c["limmes"]},"Límite superado en "&{pesos(c["totmes"] + "-" + c["limmes"])}&'
                 f'IF({c["prodmes"]}<=cfgMaxProd," (excepción hasta "&cfgMaxProd&" productos: tope "&{pesos("cfgLimExc")}&")",'
                 f'" (regla normal, "&{c["prodmes"]}&" productos: tope "&{pesos("cfgLimNormal")}&")"),"")')
        ws[c["reglas"]] = (f'=IF(OR({cB}="",{k}=""),"",IF(_xlfn.TEXTJOIN(" | ",TRUE,{m_bloq},{m_lim})="","OK",'
                           f'_xlfn.TEXTJOIN(" | ",TRUE,{m_bloq},{m_lim})))')
        ws[c["avisos"]] = (f'=IF({cB}="","",_xlfn.TEXTJOIN(" | ",TRUE,'
                           f'IF({fec}="","Falta fecha",""),'
                           f'IF({mon}="","Falta moneda",""),'
                           f'IF(${LC["precio"]}{r}="","Falta precio",""),'
                           f'IF(AND({mon}<>"",{mon}<>"CLP",${LC["tcusado"]}{r}="",{c["tcsug"]}<>""),"TC no fijado (usa sugerido)",""),'
                           f'IF(AND({mon}<>"",{mon}<>"CLP",${LC["tcusado"]}{r}="",{c["tcsug"]}=""),"Falta TC",""),'
                           f'IF(AND(${LC["acum"]}{r}="Sí",{grp}=""),"Falta grupo de envío",""),'
                           f'IF(AND(${LC["acum"]}{r}<>"Sí",{grp}<>""),"Grupo sin Envío acumulado = Sí",""),'
                           f'IF(AND(N(${LC["envmonto"]}{r})>0,{me}<>"CLP",{me}<>{mon},{maxe}=0),"Falta TC del envío","")))')
        ws[c["nlista"]] = f'=IF({cB}="","",COUNTIF(${LC["prod"]}${FILA_INI}:{cB},"?*"))'
        ws[c["etiq"]] = f'=IF({cB}="","",${LC["id"]}{r}&" · "&{cB})'

    # formatos
    for k, _, _, ing, _ in cols:
        if k == "expl":
            continue
        fmt = {"total": FMT_CLP, "envclp": FMT_CLP, "pagfig": FMT_CLP, "pagenv": FMT_CLP, "falta": FMT_CLP,
               "dif": FMT_CLP, "envpend": FMT_CLP, "totmes": FMT_CLP, "limmes": FMT_CLP,
               "fecha": FMT_FECHA, "mesasig": FMT_FECHA, "mesef": FMT_MES,
               "precio": '#,##0.##', "envmonto": '#,##0.##', "tcusado": FMT_TC, "tcsug": FMT_TC, "tcapl": FMT_TC,
               "cant": "0"}.get(k)
        formato_columna(ws, LC[k], n, fmt, ingreso=ing, ajustar=k in ("reglas", "avisos"))
    for k in ("clave", "totmes", "prodmes", "limmes", "nlista", "etiq"):
        for r in range(FILA_INI, fin + 1):
            ws[f"{LC[k]}{r}"].font = Font(name=FUENTE, size=9, color="808080")

    # validaciones
    rng = lambda k: f"{LC[k]}{FILA_INI}:{LC[k]}{fin}"
    lista_dv(ws, "=lstSiNo", rng("cerrar"), "Cerrar", "Sí = cerrar el producto aunque haya diferencia con lo calculado.")
    lista_dv(ws, "=lstMonedas", rng("moneda"), "Moneda", "CLP, USD o JPY.")
    lista_dv(ws, "=lstMonedas", rng("envmoneda"), "Moneda del envío", "Vacío = misma moneda del producto.")
    lista_dv(ws, "=lstSiNo", rng("acum"), "Envío acumulado", "Sí = se paga junto con otros envíos (elige un grupo).")
    lista_dv(ws, f"={'lstGruposP' if practica else 'lstGrupos'}", rng("grupo"), "Grupo de envío",
             "Grupo de la hoja Envíos acumulados. Solo si Envío acumulado = Sí.")
    num_dv(ws, rng("cant"), "Cantidad", "Unidades (entero, mínimo 1).", tipo="whole", minimo=1)
    num_dv(ws, rng("precio"), "Precio unitario", "Precio de una unidad en la moneda elegida.")
    num_dv(ws, rng("tcusado"), "TC usado", "Pesos por 1 USD/JPY el día de la compra. Vacío en CLP.")
    num_dv(ws, rng("envmonto"), "Envío monto", "Costo del envío en su moneda. 0 = sin envío.")
    fecha_dv(ws, rng("fecha"), "Fecha compra", "Ej. 15-11-2026.")
    fecha_dv(ws, rng("mesasig"), "Mes asignado", "Opcional: cualquier día del mes al que asignas la compra.")

    # formato condicional (rojo = advertencia, verde = OK)
    E = LC["reglas"]
    cf_texto(ws, f"{E}{FILA_INI}:{E}{fin}", f'AND(${E}{FILA_INI}<>"",${E}{FILA_INI}<>"OK")', ROJO_FILL, ROJO_TXT)
    cf_texto(ws, f"{E}{FILA_INI}:{E}{fin}", f'${E}{FILA_INI}="OK"', VERDE_FILL, VERDE_TXT)
    cf_texto(ws, f"A{FILA_INI}:{LC['total']}{fin}", f'AND(${E}{FILA_INI}<>"",${E}{FILA_INI}<>"OK")', ROJO_FILL, ROJO_TXT)
    A = LC["avisos"]
    cf_texto(ws, f"{A}{FILA_INI}:{A}{fin}", f'${A}{FILA_INI}<>""', NARANJA_FILL, "833C0B")
    D = LC["dif"]
    cf_texto(ws, f"{D}{FILA_INI}:{D}{fin}", f'AND(ISNUMBER(${D}{FILA_INI}),${D}{FILA_INI}<>0)', NARANJA_FILL, "833C0B")
    S = LC["estado"]
    cf_texto(ws, f"{S}{FILA_INI}:{S}{fin}", f'OR(${S}{FILA_INI}="Pagado",${S}{FILA_INI}="Cerrado")', VERDE_FILL, VERDE_TXT)
    V = LC["estenv"]
    cf_texto(ws, f"{V}{FILA_INI}:{V}{fin}", f'${V}{FILA_INI}="Pagado"', VERDE_FILL, VERDE_TXT)

    if demo:
        cargar_filas(ws, LC, demo, extra_col=ult if practica else None)
    agregar_tabla(ws, tabla, ult, n)
    ws.freeze_panes = f"C{FILA_INI}"
    if practica:
        ws.column_dimensions[ult].width = 40
        for r in range(FILA_INI, fin + 1):
            ws[f"{ult}{r}"].alignment = Alignment(wrap_text=True, vertical="top")
            ws[f"{ult}{r}"].font = Font(name=FUENTE, size=10, italic=True, color="833C0B")
        ocultar_si_no_practica(ws, ult, fin)
    return ws


def hoja_pagos(wb, nombre, hoja_compras, hoja_grupos, tabla, practica=False, demo=None):
    """Crea una hoja de pagos (real o de práctica): un pago por fila, siempre en CLP."""
    ws = wb.create_sheet(nombre)
    cols = list(COLS_PAGOS)
    if practica:
        cols.append(("expl", "Explicación (práctica)", 40, False, "Qué caso demuestra este pago."))
    ult = get_column_letter(len(cols))
    ws["A1"] = "Pagos (siempre en CLP)" + (" — PRÁCTICA" if practica else "")
    estilo_titulo(ws["A1"])
    banner(ws, ult, practica)
    ws["A3"] = "Un pago por fila. Tipo Figura → producto (C...). Tipo Envío → producto con envío individual o grupo (G...)."
    ws["A3"].font = Font(name=FUENTE, size=9, italic=True, color="595959")
    encabezado(ws, FILA_ENC, cols)
    n = FILAS_PAGOS
    fin = FILA_INI + n - 1
    CA = rango(hoja_compras, LC["id"], FILAS_COMPRAS)
    CT = rango(hoja_compras, LC["acum"], FILAS_COMPRAS)
    CB = rango(hoja_compras, LC["prod"], FILAS_COMPRAS)
    GA = rango(hoja_grupos, LG["id"], FILAS_GRUPOS)
    for r in range(FILA_INI, fin + 1):
        f, m, t, a, i = (f"${LP[k]}{r}" for k in ("fecha", "monto", "tipo", "asoc", "id"))
        ws[f"{LP['id']}{r}"] = f'=IF({a}="","",UPPER(LEFT(TRIM({a}),4)))'
        ws[f"{LP['clave']}{r}"] = f'=IF({f}="","",YEAR({f})*12+MONTH({f}))'
        existe_c = f'ISNUMBER(MATCH({i},{CA},0))'
        existe_g = f'ISNUMBER(MATCH({i},{GA},0))'
        ws[f"{LP['valid']}{r}"] = (
            f'=IF(AND({f}="",{m}="",{a}=""),"",IF(OR({f}="",{m}="",{t}="",{a}=""),"Faltan datos",'
            f'IF(AND(LEFT({i},1)="C",{existe_c}),IF(INDEX({CB},MATCH({i},{CA},0))="","Ese producto está vacío",'
            f'IF(AND({t}="Envío",INDEX({CT},MATCH({i},{CA},0))="Sí"),"Ese envío es acumulado: paga al grupo","OK")),'
            f'IF(AND(LEFT({i},1)="G",{existe_g}),IF({t}="Figura","Un grupo solo recibe pagos de Envío","OK"),'
            f'"ID no existe"))))')
    for k, _, _, ing, _ in cols:
        if k == "expl":
            continue
        fmt = {"fecha": FMT_FECHA, "monto": FMT_CLP}.get(k)
        formato_columna(ws, LP[k], n, fmt, ingreso=ing, ajustar=k == "valid")
    rng = lambda k: f"{LP[k]}{FILA_INI}:{LP[k]}{fin}"
    fecha_dv(ws, rng("fecha"), "Fecha pago", "Ej. 15-11-2026.")
    num_dv(ws, rng("monto"), "Monto CLP", "Monto en pesos, sin decimales.", tipo="whole", minimo=0)
    lista_dv(ws, "=lstTipoPago", rng("tipo"), "Tipo", "Figura o Envío.")
    lista_dv(ws, f"={'lstAsociadoP' if practica else 'lstAsociado'}", rng("asoc"), "Asociado a",
             "Producto (C...) o grupo de envío (G...). Puedes escribir solo el ID.", estricto=False)
    H = LP["valid"]
    cf_texto(ws, f"{H}{FILA_INI}:{H}{fin}", f'AND(${H}{FILA_INI}<>"",${H}{FILA_INI}<>"OK")', ROJO_FILL, ROJO_TXT)
    cf_texto(ws, f"{H}{FILA_INI}:{H}{fin}", f'${H}{FILA_INI}="OK"', VERDE_FILL, VERDE_TXT)
    for k in ("id", "clave"):
        for r in range(FILA_INI, fin + 1):
            ws[f"{LP[k]}{r}"].font = Font(name=FUENTE, size=9, color="808080")
    if demo:
        cargar_filas(ws, LP, demo, extra_col=ult if practica else None)
    agregar_tabla(ws, tabla, ult, n)
    ws.freeze_panes = f"B{FILA_INI}"
    if practica:
        for r in range(FILA_INI, fin + 1):
            ws[f"{ult}{r}"].alignment = Alignment(wrap_text=True, vertical="top")
            ws[f"{ult}{r}"].font = Font(name=FUENTE, size=10, italic=True, color="833C0B")
        ocultar_si_no_practica(ws, ult, fin)
    return ws


def hoja_grupos(wb, nombre, hoja_compras, hoja_pagos, tabla, practica=False, demo=None):
    """Crea la hoja de envíos acumulados (real o de práctica): un grupo por fila y su ahorro."""
    ws = wb.create_sheet(nombre)
    cols = list(COLS_GRUPOS)
    if practica:
        cols.append(("expl", "Explicación (práctica)", 40, False, "Qué caso demuestra este grupo."))
    ult = get_column_letter(len(cols))
    ws["A1"] = "Envíos acumulados" + (" — PRÁCTICA" if practica else "")
    estilo_titulo(ws["A1"])
    banner(ws, ult, practica)
    ws["A3"] = "Pueden quedar pendientes sin plazo. Al pagar, registra el pago conjunto en Pagos (Tipo Envío, Asociado = grupo)."
    ws["A3"].font = Font(name=FUENTE, size=9, italic=True, color="595959")
    encabezado(ws, FILA_ENC, cols)
    n = FILAS_GRUPOS
    fin = FILA_INI + n - 1
    CU = rango(hoja_compras, LC["grupo"], FILAS_COMPRAS)
    CT = rango(hoja_compras, LC["acum"], FILAS_COMPRAS)
    CS = rango(hoja_compras, LC["envclp"], FILAS_COMPRAS)
    PA, PB, PC, PF = (rango(hoja_pagos, LP[k], FILAS_PAGOS) for k in ("fecha", "monto", "tipo", "id"))
    for r in range(FILA_INI, fin + 1):
        c = {k: f"{LG[k]}{r}" for k in LG}
        g = f"${LG['id']}{r}"
        ws[c["id"]] = f"G{r - FILA_INI + 1:03d}"
        ws[c["nenv"]] = f'=COUNTIFS({CU},{g},{CT},"Sí")'
        ws[c["suma"]] = f'=SUMIFS({CS},{CU},{g},{CT},"Sí")'
        ws[c["pagado"]] = f'=SUMIFS({PB},{PC},"Envío",{PF},{g})'
        ws[c["fpago"]] = f'=IF({c["pagado"]}>0,_xlfn.MAXIFS({PA},{PC},"Envío",{PF},{g}),"")'
        ws[c["usado"]] = f'=IF(OR(${LG["desc"]}{r}<>"",{c["nenv"]}>0,{c["pagado"]}>0),1,0)'
        ws[c["estado"]] = f'=IF({c["usado"]}=0,"",IF({c["pagado"]}>0,"Pagado","Pendiente"))'
        ws[c["ahorro"]] = f'=IF({c["estado"]}="Pagado",{c["suma"]}-{c["pagado"]},"")'
        ws[c["clave"]] = f'=IF({c["fpago"]}="","",YEAR({c["fpago"]})*12+MONTH({c["fpago"]}))'
        ws[c["nlista"]] = f'=IF({c["usado"]}=1,SUM(${LG["usado"]}${FILA_INI}:{c["usado"]}),"")'
        ws[c["etiq"]] = f'={g}&IF(${LG["desc"]}{r}<>""," · "&${LG["desc"]}{r},"")'
    for k, _, _, ing, _ in cols:
        if k == "expl":
            continue
        fmt = {"suma": FMT_CLP, "pagado": FMT_CLP, "ahorro": FMT_CLP, "fpago": FMT_FECHA, "nenv": '0;-0;""'}.get(k)
        formato_columna(ws, LG[k], n, fmt, ingreso=ing)
    for k in ("clave", "usado", "nlista", "etiq"):
        for r in range(FILA_INI, fin + 1):
            ws[f"{LG[k]}{r}"].font = Font(name=FUENTE, size=9, color="808080")
    for k in ("suma", "pagado"):
        for r in range(FILA_INI, fin + 1):
            ws[f"{LG[k]}{r}"].number_format = '"$"#,##0;-"$"#,##0;""'
    H = LG["ahorro"]
    cf_texto(ws, f"{H}{FILA_INI}:{H}{fin}", f'AND(ISNUMBER(${H}{FILA_INI}),${H}{FILA_INI}<0)', ROJO_FILL, ROJO_TXT)
    cf_texto(ws, f"{H}{FILA_INI}:{H}{fin}", f'AND(ISNUMBER(${H}{FILA_INI}),${H}{FILA_INI}>=0)', VERDE_FILL, VERDE_TXT)
    S = LG["estado"]
    cf_texto(ws, f"{S}{FILA_INI}:{S}{fin}", f'${S}{FILA_INI}="Pagado"', VERDE_FILL, VERDE_TXT)
    if demo:
        cargar_filas(ws, LG, demo, extra_col=ult if practica else None)
    agregar_tabla(ws, tabla, ult, n)
    ws.freeze_panes = f"B{FILA_INI}"
    if practica:
        for r in range(FILA_INI, fin + 1):
            ws[f"{ult}{r}"].alignment = Alignment(wrap_text=True, vertical="top")
            ws[f"{ult}{r}"].font = Font(name=FUENTE, size=10, italic=True, color="833C0B")
        ocultar_si_no_practica(ws, ult, fin)
    return ws


def cargar_filas(ws, letras_cols, filas, extra_col=None):
    """Escribe filas de ejemplo (dict clave→valor) desde la primera fila de datos; 'expl' va a extra_col."""
    for i, fila in enumerate(filas):
        r = FILA_INI + i
        for k, v in fila.items():
            col = extra_col if k == "expl" else letras_cols[k]
            ws[f"{col}{r}"] = v


# ---------------------------------------------------------------- datos de práctica
D = date
DEMO_COMPRAS = [
    dict(prod="Figura A (mar)", cant=1, fecha=D(2026, 3, 5), tienda="Tienda local", moneda="CLP", precio=90000,
         envmonto=6000, envmoneda="CLP", acum="No",
         expl="Marzo: 3 productos suman exactamente $200.000 → OK (no supera). Su envío individual se pagó el 10-abr, mes bloqueado: no da advertencia."),
    dict(prod="Figura B USD (mar)", cant=1, fecha=D(2026, 3, 12), tienda="Tienda EE.UU.", moneda="USD", precio=60,
         tcusado=950, envmonto=12, envmoneda="USD", acum="Sí", grupo="G001",
         expl="USD 60 x TC fijado 950 = $57.000. Pago parcial de $30.000 → estado Parcial. Envío acumulado en G001."),
    dict(prod="Figura C (mar)", cant=1, fecha=D(2026, 3, 20), tienda="Tienda local", moneda="CLP", precio=53000,
         envmonto=9000, envmoneda="CLP", acum="Sí", grupo="G001",
         expl="Con envíos, marzo sumaría $226.400 (> $200.000) pero los envíos no cuentan: sigue OK."),
    dict(prod="Figura D JPY (may)", cant=1, fecha=D(2026, 5, 3), tienda="Tienda Japón", moneda="JPY", precio=20000,
         tcusado=6.5, envmonto=2000, acum="Sí", grupo="G001",
         expl="JPY 20.000 x 6,5 = $130.000. Cerrado pagando $128.000 → diferencia -$2.000 (pagaste de menos)."),
    dict(prod="Figura E (may)", cant=1, fecha=D(2026, 5, 18), tienda="Tienda local", moneda="CLP", precio=170000,
         expl="Mayo: 2 productos suman exactamente $300.000 → OK por la excepción (hasta 2 productos, tope $300.000)."),
    dict(prod="Figura F (jul)", cant=1, fecha=D(2026, 7, 2), tienda="Tienda local", moneda="CLP", precio=100000,
         envmonto=5000, envmoneda="CLP", acum="Sí", grupo="G003",
         expl="Julio: 3 productos suman $250.000 → límite superado en $50.000 (regla normal, tope $200.000)."),
    dict(prod="Figura G (jul)", cant=1, fecha=D(2026, 7, 9), tienda="Tienda local", moneda="CLP", precio=80000,
         envmonto=5000, envmoneda="CLP", acum="Sí", grupo="G003",
         expl="Mismo mes que la anterior: también muestra el exceso del mes."),
    dict(prod="Figura H (jul)", cant=1, fecha=D(2026, 7, 21), tienda="Tienda local", moneda="CLP", precio=70000,
         expl="Tercer producto de julio: con 3 productos ya no aplica la excepción."),
    dict(prod="Figura I (ago)", cant=1, fecha=D(2026, 8, 4), tienda="Tienda local", moneda="CLP", precio=45000,
         envmonto=4000, envmoneda="CLP", acum="No",
         expl="Agosto es contiguo a julio (ocupado) → Mes bloqueado. Su envío individual sigue pendiente."),
    dict(prod="Figura J (oct)", cant=1, fecha=D(2026, 10, 6), tienda="Tienda local", moneda="CLP", precio=150000,
         expl="Octubre: 2 productos suman $309.800 → supera la excepción en $9.800."),
    dict(prod="Figura K USD (oct)", cant=1, fecha=D(2026, 10, 14), tienda="Tienda EE.UU.", moneda="USD", precio=170,
         tcusado=940, expl="USD 170 x 940 = $159.800."),
    dict(prod="Figura L USD (dic)", cant=1, fecha=D(2026, 12, 3), tienda="Tienda EE.UU.", moneda="USD", precio=80,
         tcusado=1000, envmonto=8000, envmoneda="CLP", acum="Sí", grupo="G002",
         expl="USD 80 x 1.000 = $80.000. Cerrado pagando $81.500 → diferencia +$1.500 (pagaste de más)."),
    dict(prod="Figura M (ene)", cant=1, fecha=D(2027, 1, 8), tienda="Tienda local", moneda="CLP", precio=60000,
         envmonto=7000, envmoneda="CLP", acum="Sí", grupo="G002",
         expl="Enero 2027 es contiguo a diciembre 2026 → Mes bloqueado (cambio de año). Pago parcial de $20.000."),
]
for _f in DEMO_COMPRAS:
    if _f["prod"].startswith(("Figura D", "Figura L")):
        _f["cerrar"] = "Sí"

DEMO_PAGOS = [
    dict(fecha=D(2026, 3, 6), monto=90000, tipo="Figura", asoc="C001", nota="Pago completo", expl="Paga completo C001 → Pagado."),
    dict(fecha=D(2026, 4, 10), monto=6000, tipo="Envío", asoc="C001", nota="Envío individual",
         expl="Envío pagado en abril (mes bloqueado por marzo): los pagos de envío no afectan las reglas."),
    dict(fecha=D(2026, 3, 13), monto=30000, tipo="Figura", asoc="C002", nota="Abono", expl="Pago parcial → C002 queda Parcial."),
    dict(fecha=D(2026, 5, 4), monto=128000, tipo="Figura", asoc="C004", nota="Pago con tarjeta",
         expl="Pagó $2.000 menos que lo calculado; C004 se cerró con diferencia."),
    dict(fecha=D(2026, 5, 19), monto=170000, tipo="Figura", asoc="C005", nota="", expl="C005 Pagado exacto."),
    dict(fecha=D(2026, 6, 15), monto=26000, tipo="Envío", asoc="G001", nota="Caja casillero",
         expl="Pago conjunto de G001: individuales suman $33.400 → ahorro $7.400."),
    dict(fecha=D(2026, 7, 3), monto=100000, tipo="Figura", asoc="C006", nota="", expl="C006 Pagado."),
    dict(fecha=D(2026, 9, 1), monto=12500, tipo="Envío", asoc="G003", nota="Caja julio",
         expl="Pago conjunto de G003: individuales suman $10.000 → sobrecosto $2.500 (ahorro negativo)."),
    dict(fecha=D(2026, 12, 4), monto=81500, tipo="Figura", asoc="C012", nota="Cobro final tarjeta",
         expl="Pagó $1.500 más; C012 cerrado con diferencia positiva."),
    dict(fecha=D(2027, 1, 9), monto=20000, tipo="Figura", asoc="C013", nota="Abono", expl="Pago parcial de C013."),
]
DEMO_GRUPOS = [
    dict(desc="Casillero mar-may", expl="3 envíos acumulados pagados juntos con ahorro."),
    dict(desc="Casillero dic-ene", expl="Pendiente sin fecha: puede esperar todo lo necesario."),
    dict(desc="Caja julio", expl="Pagado en conjunto más caro que por separado: sobrecosto."),
]

CASOS_PRACTICA = [
    "mar-2026: 3 productos = $200.000 exacto → OK. Con envíos sumaría $226.400 y sigue OK (envíos no cuentan).",
    "abr-2026: sin compras (bloqueado por marzo). Se pagó un envío ese mes → sin advertencia.",
    "may-2026: 2 productos = $300.000 exacto → OK por excepción. C004 cerrado con -$2.000.",
    "jun-2026: pago conjunto del grupo G001 → ahorro $7.400.",
    "jul-2026: 3 productos = $250.000 → límite superado en $50.000.",
    "ago-2026: Mes bloqueado (julio ya tiene compras).",
    "sep-2026: pago del grupo G003 → sobrecosto $2.500.",
    "oct-2026: 2 productos = $309.800 → supera la excepción en $9.800.",
    "dic-2026: C012 cerrado con +$1.500. ene-2027: bloqueado por diciembre (cambio de año).",
]


# ---------------------------------------------------------------- Config, Listas, Panel, Ayuda
def hoja_config(wb):
    """Crea Config: modo práctica, límites, día de pago y tabla de tipos de cambio."""
    ws = wb.create_sheet("Config")
    ws["A1"] = "Configuración"
    estilo_titulo(ws["A1"])
    ws.column_dimensions["A"].width = 30
    ws.column_dimensions["B"].width = 14
    ws.column_dimensions["C"].width = 16
    filas = [
        (3, "Modo práctica", "Sí", "cfgModo", None,
         "Sí = el Panel usa los datos simulados de las hojas 'Práctica'. No = solo tus datos reales. Puedes cambiarlo cuando quieras: no se borra nada."),
        (4, "Límite mensual normal (CLP)", 200000, "cfgLimNormal", FMT_CLP,
         "Tope de compras de figuras por mes (sin envíos). Superarlo marca advertencia. Exactamente igual al tope es OK."),
        (5, "Límite con excepción (CLP)", 300000, "cfgLimExc", FMT_CLP,
         "Tope que aplica cuando el mes tiene pocos productos (ver fila siguiente)."),
        (6, "Máx. productos para excepción", 2, "cfgMaxProd", "0",
         "Si el mes tiene esta cantidad de productos o menos, el tope sube al límite con excepción."),
        (7, "Contar productos por", "Unidades", "cfgContar", None,
         "Unidades = suma la columna Cantidad (2 unidades de la misma figura cuentan 2). Filas = cada fila de Compras cuenta 1."),
        (8, "Día de pago del mes", 5, "cfgDiaPago", "0",
         "Día del mes en que pagas (1 a 31). Solo informativo en el Panel. Valor de ejemplo: cámbialo."),
    ]
    for fila, etiq, val, nombre, fmt, nota in filas:
        a = ws.cell(row=fila, column=1, value=etiq)
        a.font = Font(name=FUENTE, size=11, bold=True)
        com = Comment(nota, "Figuras")
        com.width, com.height = 260, 100
        a.comment = com
        b = ws.cell(row=fila, column=2, value=val)
        b.font = Font(name=FUENTE, size=12, bold=True, color="0000FF")
        b.fill = AMARILLO
        if fmt:
            b.number_format = fmt
        wb.defined_names[nombre] = DefinedName(nombre, attr_text=f"Config!$B${fila}")
    lista_dv(ws, "=lstSiNo", "B3", "Modo práctica", "Sí = ver datos simulados. No = ver solo tus datos.", permitir_vacio=False)
    num_dv(ws, "B4:B5", "Límite", "Monto en pesos.", tipo="whole", minimo=0)
    num_dv(ws, "B6", "Máx. productos", "Entero, por ej. 2.", tipo="whole", minimo=0)
    lista_dv(ws, '"Unidades,Filas"', "B7", "Contar productos por", "Unidades (columna Cantidad) o Filas.", permitir_vacio=False)
    dv = DataValidation(type="whole", operator="between", formula1="1", formula2="31", showInputMessage=True,
                        showErrorMessage=True, promptTitle="Día de pago", prompt="Número entre 1 y 31.")
    ws.add_data_validation(dv)
    dv.add("B8")
    cf_texto(ws, "B3", 'B3="Sí"', NARANJA_FILL, "833C0B")

    ws["A10"] = "Tipos de cambio (se ingresan a mano)"
    estilo_titulo(ws["A10"], 12)
    ws["A11"] = "Pesos por 1 unidad de moneda. Compras usa el último valor con fecha igual o anterior a la compra como sugerencia."
    ws["A11"].font = Font(name=FUENTE, size=9, italic=True, color="595959")
    ws["A11"].alignment = Alignment(wrap_text=True)
    ws.merge_cells("A11:C11")
    ws.row_dimensions[11].height = 28
    enc = [("Fecha", "Fecha desde la que rige el valor."), ("Moneda", "USD o JPY."),
           ("CLP por unidad", "Pesos chilenos por 1 USD o 1 JPY (ej. 950 o 6,4).")]
    fila_enc_tc, n_tc = 12, 200
    for i, (t, nota) in enumerate(enc, start=1):
        c = ws.cell(row=fila_enc_tc, column=i, value=t)
        c.font = Font(name=FUENTE, bold=True, color="FFFFFF")
        c.fill = AZUL_ENC
        com = Comment(nota, "Figuras")
        com.width, com.height = 220, 70
        c.comment = com
    ini, fin = fila_enc_tc + 1, fila_enc_tc + n_tc
    ejemplos = [(D(2026, 11, 1), "USD", 950), (D(2026, 11, 1), "JPY", 6.2)]
    for i, (f, m, v) in enumerate(ejemplos):
        ws.cell(row=ini + i, column=1, value=f)
        ws.cell(row=ini + i, column=2, value=m)
        ws.cell(row=ini + i, column=3, value=v)
    for r in range(ini, fin + 1):
        ws.cell(row=r, column=1).number_format = FMT_FECHA
        ws.cell(row=r, column=3).number_format = FMT_TC
        for col in (1, 2, 3):
            ws.cell(row=r, column=col).font = Font(name=FUENTE, size=11)
            ws.cell(row=r, column=col).fill = AMARILLO
    ws["D13"] = "← valores de ejemplo: reemplázalos por los reales"
    ws["D13"].font = Font(name=FUENTE, size=9, italic=True, color="833C0B")
    t = Table(displayName="tblTC", ref=f"A{fila_enc_tc}:C{fin}")
    t.tableStyleInfo = TableStyleInfo(name="TableStyleLight1", showRowStripes=True)
    ws.add_table(t)
    wb.defined_names["cfgTCFecha"] = DefinedName("cfgTCFecha", attr_text=f"Config!$A${ini}:$A${fin}")
    wb.defined_names["cfgTCMoneda"] = DefinedName("cfgTCMoneda", attr_text=f"Config!$B${ini}:$B${fin}")
    wb.defined_names["cfgTCValor"] = DefinedName("cfgTCValor", attr_text=f"Config!$C${ini}:$C${fin}")
    fecha_dv(ws, f"A{ini}:A{fin}", "Fecha", "Fecha desde la que rige el valor.")
    lista_dv(ws, '"USD,JPY"', f"B{ini}:B{fin}", "Moneda", "USD o JPY.")
    num_dv(ws, f"C{ini}:C{fin}", "CLP por unidad", "Pesos por 1 unidad.")
    ws.freeze_panes = "A3"
    return ws


def hoja_listas(wb):
    """Crea la hoja oculta Listas con las opciones de las listas desplegables."""
    ws = wb.create_sheet("Listas")
    fijas = {"E": ("Sí/No", ["Sí", "No"], "lstSiNo"), "F": ("Monedas", ["CLP", "USD", "JPY"], "lstMonedas"),
             "G": ("Tipo pago", ["Figura", "Envío"], "lstTipoPago"), "H": ("Meses", MESES, "lstMeses"),
             "I": ("Años", list(range(2024, 2041)), "lstAnios")}
    for col, (tit, vals, nombre) in fijas.items():
        ws[f"{col}1"] = tit
        for i, v in enumerate(vals, start=2):
            ws[f"{col}{i}"] = v
        wb.defined_names[nombre] = DefinedName(nombre, attr_text=f"Listas!${col}$2:${col}${len(vals) + 1}")
    # listas dinámicas compactas de 'Asociado a' (productos + grupos usados) y de grupos
    for col, hc, hg, nombre, nombre_g in (("A", "Compras", "Envíos acumulados", "lstAsociado", "lstGrupos"),
                                          ("B", "Práctica Compras", "Práctica Envíos", "lstAsociadoP", "lstGruposP")):
        ws[f"{col}1"] = "Asociado " + ("real" if col == "A" else "práctica")
        nl_c, et_c = rango(hc, LC["nlista"], FILAS_COMPRAS), rango(hc, LC["etiq"], FILAS_COMPRAS)
        nl_g, et_g = rango(hg, LG["nlista"], FILAS_GRUPOS), rango(hg, LG["etiq"], FILAS_GRUPOS)
        n_c = f'COUNTIF({rango(hc, LC["prod"], FILAS_COMPRAS)},"?*")'
        n_g = f'SUM({rango(hg, LG["usado"], FILAS_GRUPOS)})'
        for k in range(1, FILAS_COMPRAS + FILAS_GRUPOS + 1):
            r = k + 1
            ws[f"{col}{r}"] = (f'=IF({k}<={n_c},INDEX({et_c},MATCH({k},{nl_c},0)),'
                               f'IF({k}<={n_c}+{n_g},INDEX({et_g},MATCH({k}-{n_c},{nl_g},0)),""))')
        wb.defined_names[nombre] = DefinedName(
            nombre, attr_text=f'OFFSET(Listas!${col}$2,0,0,MAX(1,{n_c}+{n_g}),1)')
        wb.defined_names[nombre_g] = DefinedName(nombre_g, attr_text=rango(hg, LG["id"], FILAS_GRUPOS))
    ws.sheet_state = "hidden"
    return ws


def hoja_panel(wb):
    """Crea el Panel: selector de mes/año, totales del mes, reglas, envíos y ahorro."""
    ws = wb["Panel"]
    ws.column_dimensions["A"].width = 30
    ws.column_dimensions["B"].width = 20
    ws.column_dimensions["C"].width = 8
    ws.column_dimensions["C"].hidden = True
    ws["A1"] = "Panel de figuras"
    estilo_titulo(ws["A1"], 16)
    ws.merge_cells("A2:B2")
    ws["A2"] = '=IF(cfgModo="Sí","MODO PRÁCTICA: estás viendo datos simulados. Desactívalo en Config.","Datos reales")'
    ws["A2"].font = Font(name=FUENTE, size=11, bold=True, color="595959")
    ws["A2"].alignment = Alignment(wrap_text=True)
    ws.row_dimensions[2].height = 30
    cf_texto(ws, "A2", 'cfgModo="Sí"', NARANJA_FILL, "833C0B")

    def comp(k):
        """Rango de una columna de Compras según el modo (fórmula IF real/práctica se arma en fuente())."""
        return rango("Compras", LC[k], FILAS_COMPRAS), rango("Práctica Compras", LC[k], FILAS_COMPRAS)

    def pag(k):
        return rango("Pagos", LP[k], FILAS_PAGOS), rango("Práctica Pagos", LP[k], FILAS_PAGOS)

    def grp(k):
        return rango("Envíos acumulados", LG[k], FILAS_GRUPOS), rango("Práctica Envíos", LG[k], FILAS_GRUPOS)

    def fuente(plantilla, **rangos):
        """Arma IF(cfgModo="Sí", fórmula con hojas de práctica, fórmula con hojas reales)."""
        real = plantilla.format(**{k: v[0] for k, v in rangos.items()})
        prac = plantilla.format(**{k: v[1] for k, v in rangos.items()})
        return f'IF(cfgModo="Sí",{prac},{real})'

    fila = [4]

    def etiqueta(texto, nota=None, negrita=False):
        r = fila[0]
        a = ws.cell(row=r, column=1, value=texto)
        a.font = Font(name=FUENTE, size=11, bold=negrita)
        a.alignment = Alignment(wrap_text=True, vertical="center")
        if nota:
            com = Comment(nota, "Figuras")
            com.width, com.height = 260, 100
            a.comment = com
        return r

    def valor(texto, formula, fmt=None, nota=None, ingreso=False):
        r = etiqueta(texto, nota)
        b = ws.cell(row=r, column=2, value=formula)
        b.font = Font(name=FUENTE, size=12, bold=True, color="0000FF" if ingreso else "000000")
        b.alignment = Alignment(horizontal="right", vertical="center", wrap_text=True)
        if ingreso:
            b.fill = AMARILLO
        if fmt:
            b.number_format = fmt
        fila[0] += 1
        return f"B{r}"

    def seccion(texto):
        fila[0] += 1
        r = fila[0]
        ws.merge_cells(f"A{r}:B{r}")
        c = ws.cell(row=r, column=1, value=texto)
        c.font = Font(name=FUENTE, size=12, bold=True, color="FFFFFF")
        c.fill = AZUL_ENC
        fila[0] += 1

    def explicacion(texto):
        r = fila[0]
        ws.merge_cells(f"A{r}:B{r}")
        c = ws.cell(row=r, column=1, value=f'=IF(cfgModo="Sí","{texto}","")')
        c.font = Font(name=FUENTE, size=10, italic=True, color="833C0B")
        c.alignment = Alignment(wrap_text=True, vertical="top")
        ws.row_dimensions[r].height = 15 * (1 + len(texto) // 45)
        cf_texto(ws, f"A{r}", 'cfgModo="Sí"', NARANJA_FILL, "833C0B")
        fila[0] += 1

    # selector
    sel_mes = valor("Mes", "jul", nota="Elige el mes a revisar.", ingreso=True)
    sel_anio = valor("Año", 2026, fmt="0", nota="Elige el año.", ingreso=True)
    lista_dv(ws, "=lstMeses", sel_mes, "Mes", "Elige el mes.", permitir_vacio=False)
    lista_dv(ws, "=lstAnios", sel_anio, "Año", "Elige el año.", permitir_vacio=False)
    r_clave = fila[0]
    ws[f"C{r_clave}"] = f"={sel_anio}*12+MATCH({sel_mes},lstMeses,0)"
    wb.defined_names["pnlClave"] = DefinedName("pnlClave", attr_text=f"Panel!$C${r_clave}")
    valor("Mes seleccionado", "=" + etiqueta_mes("pnlClave"))
    explicacion("Prueba los meses de la lista 'Casos de práctica' al final del Panel.")

    seccion("Figuras del mes (sin envíos)")
    tot = valor("Total figuras", "=" + fuente("SUMIFS({t},{k},pnlClave)", t=comp("total"), k=comp("clave")), FMT_CLP,
                "Suma de 'Total figura CLP' de los productos asignados a este mes.")
    valor("Pagado", "=" + fuente("SUMIFS({t},{k},pnlClave)", t=comp("pagfig"), k=comp("clave")), FMT_CLP,
          "Pagos tipo Figura de los productos de este mes (sin importar la fecha del pago).")
    valor("Falta pagar", "=" + fuente("SUMIFS({t},{k},pnlClave)", t=comp("falta"), k=comp("clave")), FMT_CLP,
          "Lo que queda por pagar de las figuras de este mes. Los productos Cerrados no suman.")
    valor("Diferencia por tipo de cambio", "=" + fuente("SUMIFS({t},{k},pnlClave)", t=comp("dif"), k=comp("clave")), FMT_CLP,
          "Suma de 'Diferencia figura' de productos Pagados o Cerrados. Positivo = pagaste de más.")
    n_prod = valor("Productos en el mes",
                   "=IF(cfgContar=\"Filas\"," + fuente("COUNTIF({k},pnlClave)", k=comp("clave")) + "," +
                   fuente('SUMIFS({c},{k},pnlClave)+COUNTIFS({k},pnlClave,{c},"")', c=comp("cant"), k=comp("clave")) + ")",
                   "0", "Unidades o filas, según Config > Contar productos por.")
    lim = valor("Límite que aplica", f"=IF({n_prod}<=cfgMaxProd,cfgLimExc,cfgLimNormal)", FMT_CLP,
                "Límite normal, o el de excepción si hay pocos productos.")
    valor("Margen disponible", f"={lim}-{tot}", FMT_CLP, "Límite - total. Negativo = excedido.")
    explicacion("El total solo suma figuras. Los envíos nunca cuentan para el límite.")

    seccion("Reglas del mes")
    occ = lambda d: fuente("COUNTIF({k},pnlClave" + d + ")", k=comp("clave"))
    alt = valor("Meses alternos",
                f'=IF({occ("")}=0,IF({occ("-1")}>0,"Sin compras. No disponible: "&{etiqueta_mes("pnlClave-1")}&" tiene compras",'
                f'IF({occ("+1")}>0,"Sin compras. No disponible: "&{etiqueta_mes("pnlClave+1")}&" tiene compras",'
                f'"Sin compras. Mes disponible")),'
                f'IF({occ("-1")}>0,"Mes bloqueado: el mes "&{etiqueta_mes("pnlClave-1")}&" ya tiene compras","OK"))',
                nota="El mes anterior y el siguiente a un mes con compras deben quedar sin compras de figuras. "
                     "La advertencia se muestra en el mes posterior.")
    limr = valor("Límite mensual",
                 f'=IF({tot}>{lim},"Límite superado en "&{pesos(tot + "-" + lim)}&IF({n_prod}<=cfgMaxProd,'
                 f'" (excepción hasta "&cfgMaxProd&" productos: tope "&{pesos("cfgLimExc")}&")",'
                 f'" (regla normal, "&{n_prod}&" productos: tope "&{pesos("cfgLimNormal")}&")"),"OK")',
                 nota="Compara el total de figuras con el límite que aplica. Igual al tope = OK.")
    gen = valor("Estado general",
                f'=IF(AND(LEFT({alt},13)<>"Mes bloqueado",{limr}="OK"),"OK","ADVERTENCIA")',
                nota="OK si se cumplen ambas reglas; si no, ADVERTENCIA (ver motivo arriba).")
    for celda in (alt, limr, gen):
        ws[celda].font = Font(name=FUENTE, size=11, bold=True)
        ws.row_dimensions[int(celda[1:])].height = 45
    explicacion("Rojo = regla rota con su motivo. Verde = OK. Los pagos de envío hechos en un mes bloqueado no cuentan.")

    seccion("Envíos (no cuentan para las reglas)")
    valor("Pendientes de productos del mes", "=" + fuente("SUMIFS({e},{k},pnlClave)", e=comp("envpend"), k=comp("clave")),
          FMT_CLP, "Envíos aún sin pagar de los productos asignados a este mes (individuales y acumulados).")
    valor("Pagados en el mes", "=" + fuente('SUMIFS({m},{t},"Envío",{k},pnlClave)', m=pag("monto"), t=pag("tipo"), k=pag("clave")),
          FMT_CLP, "Pagos tipo Envío con fecha dentro de este mes (individuales y conjuntos).")
    valor("Total sin pagar (todos los meses)", "=" + fuente("SUM({e})", e=comp("envpend")), FMT_CLP,
          "Todos los envíos pendientes o acumulados sin pagar, de cualquier mes.")
    explicacion("Un envío puede quedar pendiente o acumulado aunque el producto esté Cerrado.")

    seccion("Envíos pagados en conjunto")
    ah_mes = valor("Ahorro de grupos pagados en el mes",
                   "=" + fuente("SUMIFS({a},{k},pnlClave)", a=grp("ahorro"), k=grp("clave")), FMT_CLP,
                   "Suma individual - pagado conjunto, de los grupos pagados este mes. Negativo = sobrecosto.")
    ah_tot = valor("Ahorro total histórico", "=" + fuente("SUM({a})", a=grp("ahorro")), FMT_CLP,
                   "Ahorro (o sobrecosto si es negativo) de todos los grupos pagados.")
    valor("Grupos pendientes", "=" + fuente('COUNTIF({e},"Pendiente")', e=grp("estado")), "0",
          "Grupos de envío acumulado aún sin pago.")
    valor("Monto de grupos pendientes", "=" + fuente('SUMIFS({s},{e},"Pendiente")', s=grp("suma"), e=grp("estado")), FMT_CLP,
          "Suma de envíos individuales de los grupos sin pagar.")
    for celda in (ah_mes, ah_tot):
        cf_texto(ws, celda, f"{celda}<0", ROJO_FILL, ROJO_TXT)
        cf_texto(ws, celda, f"{celda}>0", VERDE_FILL, VERDE_TXT)
    explicacion("Ahorro positivo = pagar juntos salió más barato. Negativo (rojo) = pagaste de más.")

    seccion("Pago del mes")
    valor("Fecha de pago", f"=DATE({sel_anio},MATCH({sel_mes},lstMeses,0),MIN(cfgDiaPago,"
                           f"DAY(DATE({sel_anio},MATCH({sel_mes},lstMeses,0)+1,0))))", FMT_FECHA,
          "Día de pago de Config dentro del mes elegido (si el mes es más corto, el último día).")

    # casos de práctica
    fila[0] += 1
    r = fila[0]
    ws.merge_cells(f"A{r}:B{r}")
    ws.cell(row=r, column=1, value='=IF(cfgModo="Sí","Casos de práctica (con los límites por defecto de Config)","")').font = Font(
        name=FUENTE, size=12, bold=True, color="833C0B")
    fila[0] += 1
    for caso in CASOS_PRACTICA:
        explicacion(caso)

    # formato condicional de reglas (rojo = advertencia)
    for celda in (alt, limr, gen):
        cf_texto(ws, celda, f'OR(LEFT({celda},13)="Mes bloqueado",LEFT({celda},7)="Límite ",{celda}="ADVERTENCIA")',
                 ROJO_FILL, ROJO_TXT)
        cf_texto(ws, celda, f'OR({celda}="OK",{celda}="Sin compras. Mes disponible")', VERDE_FILL, VERDE_TXT)
    ws.freeze_panes = "A4"
    return ws


AYUDA = [
    ("Cómo usar el libro", None),
    ("Registrar una compra", "Compras → primera fila vacía: Producto, Cantidad, Fecha compra, Moneda, Precio unitario y (si es USD/JPY) TC usado. Si tiene envío: monto, moneda y si va acumulado (con su grupo)."),
    ("Registrar un pago", "Pagos → Fecha, Monto CLP, Tipo (Figura/Envío) y Asociado a (elige el producto o grupo). La columna Validación debe decir OK."),
    ("Colores", "Encabezado azul + celda amarilla = lo escribes tú. Encabezado gris = calculado, no lo edites. Rojo = advertencia. Verde = OK. Naranjo = aviso o diferencia."),
    ("Hojas", None),
    ("Panel", "Elige mes y año. Muestra total de figuras, pagado, falta, diferencia por tipo de cambio, estado de las reglas, envíos del mes, envíos sin pagar de todos los meses y ahorro de envíos conjuntos."),
    ("Compras", "Una fila por producto. Las columnas de la izquierda son las importantes; las de la derecha (gris claro) son auxiliares."),
    ("Pagos", "Un pago por fila, siempre en CLP. Los pagos de figura van al producto; los de envío al producto (si no es acumulado) o al grupo."),
    ("Envíos acumulados", "Un grupo por fila (G001...). Escribe una descripción; en Compras marca Envío acumulado = Sí y elige el grupo. Al pagar, registra el pago en Pagos con Asociado = grupo. El ahorro = suma de envíos individuales - pago conjunto."),
    ("Config", "Modo práctica, límites, máximo de productos para la excepción, forma de contar productos, día de pago y tabla de tipos de cambio."),
    ("Práctica Compras / Pagos / Envíos", "Datos simulados, separados de los reales. Solo se ven y solo los usa el Panel cuando Modo práctica = Sí."),
    ("Reglas", None),
    ("Solo figuras", "Las reglas solo miran el 'Total figura CLP'. Los envíos y sus pagos nunca cuentan para límites ni ocupan un mes."),
    ("Meses alternos", "Un mes con compras de figuras queda ocupado. El mes anterior y el siguiente deben quedar sin compras. Si hay compras en dos meses seguidos, el posterior muestra 'Mes bloqueado: el mes X ya tiene compras' (también entre diciembre y enero)."),
    ("Límite mensual", "Total de figuras del mes ≤ límite normal (200.000 por defecto). Si el mes tiene 2 productos o menos (por defecto), el tope es el de excepción (300.000 por defecto). Todo se edita en Config. Igual al tope = OK; más = rojo con el monto excedido y la regla aplicada."),
    ("Cerrar con diferencia", "Marca Cerrar = Sí: el producto queda Cerrado aunque lo pagado no calce. La diferencia se ve en el producto y se suma en el Panel."),
    ("Envíos independientes", "El envío tiene su propio estado (Sin envío / Pendiente / Acumulado / Pagado) y sus propios pagos."),
    ("Tipo de cambio fijo", "Escribe el TC del día en 'TC usado': queda guardado en la compra y no cambia si editas la tabla de Config. Si lo dejas vacío se usa el sugerido y aparece el aviso 'TC no fijado'."),
    ("Campos de Compras", None),
] + [(t, n) for _, t, _, _, n in COLS_COMPRAS] + [("Campos de Pagos", None)] + [
    (t, n) for _, t, _, _, n in COLS_PAGOS] + [("Campos de Envíos acumulados", None)] + [
    (t, n) for _, t, _, _, n in COLS_GRUPOS] + [
    ("Supuestos", None),
    ("Productos para la excepción", "Por defecto se cuentan unidades (columna Cantidad). Cámbialo a Filas en Config si prefieres contar cada producto distinto como 1."),
    ("Mes bloqueado", "Como no se registra el orden en que anotaste las compras, se marca siempre el mes posterior de cada par contiguo."),
    ("Pagos de figura", "Cuentan para el mes asignado del producto, no para el mes en que pagaste."),
    ("Filas preparadas", f"Compras tiene {FILAS_COMPRAS} filas, Pagos {FILAS_PAGOS} y Envíos {FILAS_GRUPOS}, con fórmulas listas. Escribe en la primera fila vacía; no insertes filas en medio."),
]


def hoja_ayuda(wb):
    """Crea la hoja Ayuda con la explicación de cada hoja, regla y campo."""
    ws = wb.create_sheet("Ayuda")
    ws.column_dimensions["A"].width = 22
    ws.column_dimensions["B"].width = 48
    ws["A1"] = "Ayuda"
    estilo_titulo(ws["A1"], 16)
    r = 3
    for titulo, texto in AYUDA:
        if texto is None:
            r += 1
            ws.merge_cells(f"A{r}:B{r}")
            c = ws.cell(row=r, column=1, value=titulo)
            c.font = Font(name=FUENTE, size=12, bold=True, color="FFFFFF")
            c.fill = AZUL_ENC
        else:
            a = ws.cell(row=r, column=1, value=titulo)
            a.font = Font(name=FUENTE, size=11, bold=True)
            a.alignment = Alignment(wrap_text=True, vertical="top")
            b = ws.cell(row=r, column=2, value=texto)
            b.font = Font(name=FUENTE, size=11)
            b.alignment = Alignment(wrap_text=True, vertical="top")
            ws.row_dimensions[r].height = 15 * (1 + len(texto) // 45)
        r += 1
    return ws


def construir(ruta):
    """Arma el libro completo y lo guarda en 'ruta'."""
    wb = Workbook()
    wb.active.title = "Panel"
    hoja_compras(wb, "Compras", "Pagos", "Envíos acumulados", "tblCompras")
    hoja_pagos(wb, "Pagos", "Compras", "Envíos acumulados", "tblPagos")
    hoja_grupos(wb, "Envíos acumulados", "Compras", "Pagos", "tblEnvios")
    hoja_config(wb)
    hoja_ayuda(wb)
    hoja_compras(wb, "Práctica Compras", "Práctica Pagos", "Práctica Envíos", "tblPracticaCompras",
                 practica=True, demo=DEMO_COMPRAS)
    hoja_pagos(wb, "Práctica Pagos", "Práctica Compras", "Práctica Envíos", "tblPracticaPagos",
               practica=True, demo=DEMO_PAGOS)
    hoja_grupos(wb, "Práctica Envíos", "Práctica Compras", "Práctica Pagos", "tblPracticaEnvios",
                practica=True, demo=DEMO_GRUPOS)
    hoja_listas(wb)
    hoja_panel(wb)
    colores = {"Panel": "1F4E78", "Compras": "2E75B6", "Pagos": "2E75B6", "Envíos acumulados": "2E75B6",
               "Config": "7F7F7F", "Ayuda": "7F7F7F", "Práctica Compras": "C55A11",
               "Práctica Pagos": "C55A11", "Práctica Envíos": "C55A11"}
    for n, col in colores.items():
        wb[n].sheet_properties.tabColor = col
    wb.active = 0
    wb.save(ruta)


if __name__ == "__main__":
    construir(sys.argv[1] if len(sys.argv) > 1 else "excel/Figuras.xlsx")
