"""Genera excel/Figuras.xlsx: registro de compras de figuras (CLP) pensado para Excel móvil.

Uso:  python3 excel/generar_figuras.py [ruta_salida.xlsx]
Sin macros, sin Power Query. Solo tablas, validación de datos, formato condicional y fórmulas
estándar (más TEXTJOIN, MAXIFS y MINIFS, disponibles en Excel móvil). Única excepción opcional:
la columna Foto usa IMAGE (IMAGEN), que descarga la foto desde el link que escribas.
Requiere Pillow y las fuentes Liberation Sans (para medir el espacio de cada texto con cualquier zoom).
"""
import math
import re
import sys
from datetime import date

from openpyxl import Workbook
from openpyxl.comments import Comment
from openpyxl.formatting.rule import FormulaRule
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter
from openpyxl.workbook.defined_name import DefinedName
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.worksheet.table import Table, TableStyleInfo

# ---------------------------------------------------------------- constantes
FILAS_COMPRAS = 200   # filas de datos preparadas en cada hoja de compras
FILAS_PAGOS = 300
FILAS_GRUPOS = 50
FILAS_ABONOS = 200
# Las hojas de práctica solo necesitan unas pocas filas (los datos simulados usan menos de 20)
FILAS_PRACTICA = {"compras": 40, "pagos": 40, "grupos": 10, "abonos": 20}
FILA_ENC = 4          # fila de encabezados de las tablas
FILA_INI = 5          # primera fila de datos
MESES_PLAN = 24       # meses que muestra el plan de ahorro (desde el mes actual)

FUENTE = "Arial"
FMT_CLP = '"$"#,##0;-"$"#,##0;"$"0'
FMT_CLP_VACIO = '"$"#,##0;-"$"#,##0;""'
FMT_TC = '#,##0.00'
FMT_FECHA = 'dd-mm-yyyy'
FMT_MES = 'mmm-yyyy'
FMT_PCT = '0.0%'

AZUL_ENC = PatternFill("solid", fgColor="1F4E78")    # encabezado de campo que escribes tú
GRIS_ENC = PatternFill("solid", fgColor="595959")    # encabezado de campo calculado
AMARILLO = PatternFill("solid", fgColor="FFF2CC")    # celdas de ingreso
ROJO_FILL = PatternFill("solid", fgColor="FFC7CE")
VERDE_FILL = PatternFill("solid", fgColor="C6EFCE")
NARANJA_FILL = PatternFill("solid", fgColor="FCE4D6")
GRIS_FILL = PatternFill("solid", fgColor="EDEDED")
BLANCO_FILL = PatternFill("solid", fgColor="FFFFFF")
ROJO_TXT = "9C0006"
VERDE_TXT = "006100"
NARANJA_TXT = "833C0B"

MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]

# Columnas de las hojas de compras: (clave, encabezado, ancho, es_ingreso, nota)
COLS_COMPRAS = [
    ("id", "ID", 6, False, "Identificador fijo del producto (C001, C002...). No lo cambies: los pagos se enlazan a este ID."),
    ("prod", "Producto", 22, True, "Nombre de la figura. Al escribirlo, la fila se activa."),
    ("foto", "Foto", 10, False, "Calculado: muestra la foto del 'Link imagen' con la función IMAGEN. Si tu Excel no la tiene, verás un error solo aquí: ignóralo."),
    ("salida", "Fecha salida original", 11, True, "Fecha de lanzamiento original de la preventa. Define el mes de la compra y el pago del día 13. Si después se atrasa, NO la cambies (usa 'Salida actual')."),
    ("moneda", "Moneda", 8, True, "Moneda del precio: CLP, USD o JPY."),
    ("precio", "Precio unitario", 11, True, "Precio de UNA unidad, en la moneda indicada."),
    ("cant", "Cantidad", 8, True, "Unidades compradas. Si lo dejas vacío se toma 1."),
    ("tcusado", "TC usado", 9, True, "Pesos por 1 USD o 1 JPY del día de pago. Escríbelo para fijarlo: así el historial no cambia si editas la tabla de Config. En CLP déjalo vacío."),
    ("total", "Total figura CLP", 13, False, "Calculado: cantidad x precio unitario x tipo de cambio aplicado, redondeado a pesos. No incluye envío ni impuestos."),
    ("reglas", "Reglas", 30, False, "Calculado: OK, o el motivo en rojo (mes bloqueado o límite superado) para el mes de este producto. Solo cuentan figuras, nunca envíos ni impuestos."),
    ("estado", "Estado producto", 11, False, "Calculado: Pendiente (sin pagos), Parcial, Pagado (pagado >= total), Cerrado o Cancelado (según 'Estado manual')."),
    ("manual", "Estado manual", 10, True, "Activa (o vacío) = normal. Cerrada = das el producto por terminado aunque lo pagado no calce. Cancelada = reserva cancelada: deja de contar en todo."),
    ("envmonto", "Envío monto", 10, True, "Costo de envío (solo flete, sin aduana ni IVA) de este producto enviado solo, en su moneda. 0 o vacío = sin envío."),
    ("envmoneda", "Envío moneda", 8, True, "Moneda del envío: CLP, USD o JPY. Vacío = misma moneda del producto."),
    ("acum", "Envío acumulado", 9, True, "Sí = la figura queda en bodega y su envío se paga junto con otros (grupo de Envíos acumulados). No = se paga sola."),
    ("grupo", "Grupo envío", 8, True, "Solo si Envío acumulado = Sí: el grupo (G001, G002...) de la hoja Envíos acumulados."),
    ("estenv", "Estado envío", 10, False, "Calculado: Sin envío, Pendiente, Acumulado (en grupo aún no pagado) o Pagado. Independiente del estado del producto."),
    ("falta", "Falta pagar figura", 12, False, "Calculado: total - pagado figura (mínimo 0). Si está Cerrado o Cancelado, 0."),
    ("pagfig", "Pagado figura CLP", 12, False, "Calculado: suma de pagos tipo Figura de la hoja Pagos asociados a este ID."),
    ("pagenv", "Pagado envío CLP", 12, False, "Calculado: pagos tipo Envío hechos directo a este ID (envío no acumulado). Los envíos acumulados se pagan al grupo."),
    ("fpagada", "Figura pagada el", 11, False, "Calculado: fecha del último pago de la figura cuando ya quedó pagada (o Cerrada con pagos). Vacío = aún no está pagada: su envío no se puede pagar."),
    ("dif", "Diferencia figura", 12, False, "Calculado solo si está Pagado o Cerrado: pagado - total. Positivo = pagaste de más; negativo = pagaste de menos."),
    ("envpend", "Envío por pagar", 11, False, "Calculado: envío en CLP que aún falta pagar (individual o dentro de un grupo sin pagar)."),
    ("fpago", "Fecha pago", 11, False, "Calculado: 'Pago especial' si lo escribiste; si no, el día de pago de Config (13) del mes de salida original."),
    ("reserva", "Fecha reserva", 11, True, "Informativo: cuándo reservaste."),
    ("salact", "Salida actual", 11, True, "Opcional e informativo: nueva fecha si la tienda atrasó la salida. No cambia el mes ni el pago."),
    ("pagoesp", "Pago especial", 11, True, "Opcional: fecha de pago si esta figura se paga otro día (ej. antes del 13). Sigue contando para su mes."),
    ("tienda", "Tienda", 14, True, "Nombre de la tienda de origen."),
    ("link", "Link", 18, True, "Enlace al producto o a la orden."),
    ("linkimg", "Link imagen", 18, True, "Opcional: link DIRECTO de la foto (en el celular: mantén presionada la foto en la tienda > 'Copiar dirección de imagen')."),
    ("tcsug", "TC sugerido", 9, False, "Calculado: último tipo de cambio de Config con fecha igual o anterior a la fecha de pago. Solo referencia para copiarlo a 'TC usado'."),
    ("tcapl", "TC aplicado", 9, False, "Calculado: 1 si es CLP; 'TC usado' si lo escribiste; si no, el sugerido (aparece aviso 'TC no fijado')."),
    ("envclp", "Envío CLP", 10, False, "Calculado: envío en pesos. Si es la misma moneda del producto usa su TC aplicado; si es otra, el TC sugerido de Config para la fecha de pago."),
    ("envimp", "Envío para impuesto", 11, False, "Calculado: envío usado para estimar impuestos. Grupo sin pagar o envío individual: su envío CLP. Grupo pagado: su parte del pago conjunto, repartida según los envíos individuales; la última figura del grupo absorbe el redondeo para que la suma calce exacto con el pago."),
    ("arancel", "Arancel est.", 10, False, "Estimación: arancel (6% en Config) x (total figura + envío para impuesto). Se paga al llegar a Chile. No cuenta para las reglas."),
    ("iva", "IVA est.", 10, False, "Estimación: IVA (19% en Config) x (total figura + envío para impuesto + arancel). Se paga al llegar a Chile. No cuenta para las reglas."),
    ("impuestos", "Impuestos est.", 11, False, "Estimación: arancel + IVA. Solo referencia para preparar el dinero."),
    ("avisos", "Avisos de datos", 24, False, "Calculado: datos faltantes o por revisar (no son reglas de compra)."),
    ("clave", "Clave mes", 8, False, "Auxiliar: año x 12 + mes. Sirve para comparar meses contiguos, también diciembre/enero."),
    ("claveenv", "Clave mes pago envío", 9, False, "Auxiliar: mes (año x 12 + mes) en que se pagó el envío de esta figura (individual o su grupo)."),
    ("totmes", "Total figuras mes", 12, False, "Auxiliar: suma de 'Total figura CLP' de todos los productos del mismo mes."),
    ("prodmes", "Productos mes", 8, False, "Auxiliar: productos del mes (unidades o filas, según Config)."),
    ("limmes", "Límite mes", 11, False, "Auxiliar: límite que aplica al mes (normal o excepción)."),
    ("salef", "Salida efectiva", 11, False, "Auxiliar: 'Salida actual' si la escribiste; si no, la fecha de salida original. El envío no se puede pagar antes de esta fecha."),
    ("pgrupo", "Pagado grupo", 10, False, "Auxiliar: pagos válidos (OK) de tipo Envío hechos al grupo de esta figura."),
    ("sgrupo", "Suma grupo", 10, False, "Auxiliar: suma de envíos individuales (CLP) de las figuras activas del mismo grupo."),
    ("msgbloq", "Aviso bloqueo", 14, False, "Auxiliar: texto de mes bloqueado (vacío si no aplica)."),
    ("msglim", "Aviso límite", 14, False, "Auxiliar: texto de límite superado (vacío si no aplica)."),
    ("nlista", "N lista", 6, False, "Auxiliar para la lista desplegable de Pagos."),
    ("etiq", "Etiqueta lista", 18, False, "Auxiliar: texto 'ID · Producto' que aparece en la lista de Pagos."),
    ("integ", "Integridad", 16, False, "Auxiliar: OK si las fórmulas de la fila están intactas. Si alguien escribió encima de una celda gris, avisa para copiar la fila de arriba."),
]
COLS_PAGOS = [
    ("fecha", "Fecha pago", 11, True, "Fecha en que hiciste el pago. No puede ser futura."),
    ("monto", "Monto CLP", 11, True, "Monto pagado, siempre en pesos chilenos."),
    ("tipo", "Tipo", 8, True, "Figura = pago del producto. Envío = pago de un envío (individual o de un grupo). El envío solo se puede pagar con la figura ya pagada."),
    ("asoc", "Asociado a", 24, True, "Elige de la lista el producto (C...) o el grupo de envío (G...). También puedes escribir solo el ID."),
    ("nota", "Nota", 20, True, "Texto libre (medio de pago, etc.)."),
    ("valid", "Validación", 30, False, "Calculado: OK o por qué el pago NO cuenta. Solo los pagos con OK cuentan en los totales."),
    ("aviso", "Avisos", 26, False, "Calculado: cosas raras que no bloquean el pago (posible duplicado, pago anterior a la reserva, se pasa del total...)."),
    ("id", "ID", 6, False, "Calculado: ID normalizado de 'Asociado a'."),
    ("clave", "Clave mes pago", 9, False, "Auxiliar: año x 12 + mes de la fecha de pago."),
    ("posc", "Fila compra", 7, False, "Auxiliar: posición del producto (C...) en Compras; 0 si no existe."),
    ("posg", "Fila grupo", 7, False, "Auxiliar: posición del grupo (G...) en Envíos acumulados; 0 si no existe."),
    ("vbase", "Validación base", 18, False, "Auxiliar: validación sin la regla de orden figura→envío (evita referencias circulares)."),
    ("integ", "Integridad", 16, False, "Auxiliar: OK si las fórmulas de la fila están intactas."),
]
COLS_GRUPOS = [
    ("id", "Grupo", 7, False, "Identificador fijo del grupo (G001...). Úsalo en Compras > Grupo envío y en Pagos."),
    ("desc", "Descripción", 22, True, "Nombre del grupo, por ej. 'Bodega Nin Nin dic-feb'. Al escribirlo, el grupo aparece en la lista de Pagos."),
    ("nenv", "N envíos", 7, False, "Calculado: cuántos productos (no cancelados) tienen Envío acumulado = Sí con este grupo."),
    ("suma", "Suma individual CLP", 12, False, "Calculado: suma de los envíos individuales (en CLP) de esos productos."),
    ("pagado", "Pagado conjunto CLP", 12, False, "Calculado: suma de pagos tipo Envío asociados a este grupo en la hoja Pagos."),
    ("fpago", "Fecha pago", 11, False, "Calculado: fecha del último pago registrado para el grupo. No hay plazo obligatorio."),
    ("estado", "Estado grupo", 10, False, "Calculado: Pendiente (sin pagos) o Pagado."),
    ("ahorro", "Ahorro CLP", 11, False, "Calculado si está Pagado: suma individual - pagado conjunto. Negativo = pagaste de más (sobrecosto)."),
    ("imp", "Impuestos est. grupo", 12, False, "Estimación sobre el paquete completo: arancel 6% x (figuras + envío del grupo) e IVA 19% x (figuras + envío + arancel). Envío = pago conjunto si ya se pagó; si no, la suma individual. Puede diferir en pocos pesos de la suma por figura (redondeo)."),
    ("clave", "Clave mes pago", 9, False, "Auxiliar: año x 12 + mes de la fecha de pago."),
    ("usado", "Usado", 6, False, "Auxiliar: 1 si el grupo tiene descripción, envíos o pagos."),
    ("nlista", "N lista", 6, False, "Auxiliar para la lista desplegable de Pagos."),
    ("etiq", "Etiqueta lista", 18, False, "Auxiliar: texto 'Grupo · Descripción' de la lista de Pagos."),
    ("integ", "Integridad", 16, False, "Auxiliar: OK si las fórmulas de la fila están intactas."),
]
COLS_MOV = [
    ("fecha", "Fecha", 11, True, "Fecha del movimiento."),
    ("monto", "Monto CLP", 11, True, "Monto en pesos, siempre positivo. Si sacas dinero, elige Quitar."),
    ("mov", "Agregar o Quitar", 11, True, "Agregar = sumas al ahorro. Quitar = sacas del ahorro (por ejemplo, al pagar una figura con lo ahorrado)."),
    ("nota", "Nota", 20, True, "Texto libre (de dónde salió o para qué se usó)."),
    ("revisar", "Revisar", 18, False, "Calculado: OK si el movimiento es válido. Solo los movimientos válidos cambian el total."),
    ("despues", "Total después", 12, False, "Calculado: cómo quedó el ahorro después de este movimiento (historial)."),
    ("valor", "Valor", 9, False, "Auxiliar: +monto si es Agregar, -monto si es Quitar, 0 si no es válido."),
]
# Columnas del plan de ahorro (bloque calculado de la hoja Ahorro)
COLS_PLAN = [
    ("mes", "Mes", 10, "Mes del pago (desde el mes actual)."),
    ("fecha", "Primer pago", 11, "Fecha del primer pago de figuras de ese mes (13 o pago especial). Vacío si no hay pagos pendientes."),
    ("necesita", "A pagar", 12, "Suma de 'Falta pagar figura' de las figuras de ese mes (no incluye envíos ni impuestos)."),
    ("asignado", "Ahorrado", 12, "Parte del saldo ahorrado que cubre este mes. El saldo llena primero el mes más cercano y lo que sobra pasa al siguiente."),
    ("falta", "Falta juntar", 12, "A pagar - ahorrado."),
    ("meses", "Meses para juntar", 8, "Meses desde el mes siguiente al actual hasta el mes del pago, inclusive. 0 = el pago es este mes: apartar todo ya."),
    ("cuota", "Cuota mensual", 12, "Falta juntar repartido en partes iguales entre los meses para juntar (redondeado hacia arriba)."),
    ("apartar", "Apartar en este mes", 12, "Total a apartar durante este mes, sumando las cuotas de todos los pagos que vienen."),
    ("clave", "Clave", 8, "Auxiliar: año x 12 + mes."),
    ("integ", "Integridad", 14, "Auxiliar: OK si las fórmulas de la fila del plan están intactas."),
]


def letras(cols):
    """Devuelve {clave: letra de columna} para una lista de columnas."""
    return {c[0]: get_column_letter(i + 1) for i, c in enumerate(cols)}


LC = letras(COLS_COMPRAS)
LP = letras(COLS_PAGOS)
LG = letras(COLS_GRUPOS)
LA = letras(COLS_MOV)
PLAN_COL0 = 9   # el plan de pagos va a la derecha (desde la columna I)
LPL = {c[0]: get_column_letter(PLAN_COL0 + i) for i, c in enumerate(COLS_PLAN)}

# Distribución de la hoja Ahorro: total arriba, movimientos (historial) debajo, plan y detalle a la derecha
AH_TOTAL = 3                            # fila del TOTAL AHORRADO (celda B3)
AH_MOV_ENC = 6                          # encabezado de la tabla de movimientos
AH_MOV_INI = AH_MOV_ENC + 1
AH_MOV_FIN = AH_MOV_INI + FILAS_ABONOS - 1
AH_RES_COL = "J"                        # columna de valores del detalle (etiquetas en I)
AH_CELDAS = {"hoy": 3, "agregado": 4, "quitado": 5, "usado": 6, "saldo": 7, "extra": 8, "atrasado": 9, "khoy": 10, "kprox": 11}
AH_PLAN_ENC = 14                        # encabezado del plan (columnas I en adelante)
AH_PLAN_INI = AH_PLAN_ENC + 1
AH_PLAN_FIN = AH_PLAN_INI + MESES_PLAN  # última fila = "Más adelante"


def q(nombre_hoja):
    """Nombre de hoja entre comillas simples para referencias entre hojas."""
    return "'" + nombre_hoja + "'"


def rango(hoja, col, filas, ini=FILA_INI):
    """Rango absoluto de una columna de datos, ej. 'Compras'!$D$5:$D$204."""
    return f"{q(hoja)}!${col}${ini}:${col}${ini + filas - 1}"


def etiqueta_mes(expr_clave):
    """Fórmula que convierte una clave de mes (año*12+mes) en texto 'mmm-aaaa' sin depender del idioma."""
    lista = ",".join(f'"{m}"' for m in MESES)
    return f'CHOOSE(MOD(({expr_clave})-1,12)+1,{lista})&"-"&INT((({expr_clave})-1)/12)'


def pesos(expr):
    """Fórmula de texto '$1.234' usando el separador de miles del idioma del teléfono."""
    return f'"$"&FIXED({expr},0)'


def fecha_dia_pago(expr_anio, expr_mes):
    """Fórmula de la fecha de pago (día de Config, o el último día si el mes es más corto)."""
    return (f"DATE({expr_anio},{expr_mes},MIN(cfgDiaPagoV,DAY(DATE({expr_anio},({expr_mes})+1,0))))")


# ---------------------------------------------------------------- estilos
def estilo_titulo(c, tam=14):
    c.font = Font(name=FUENTE, size=tam, bold=True, color="1F4E78")


def nota(celda, texto, ancho=260, alto=110):
    """Agrega un comentario (nota) a una celda."""
    com = Comment(texto, "Figuras")
    com.width, com.height = ancho, alto
    celda.comment = com


def encabezado(ws, fila, cols, col_ini=1):
    """Escribe encabezados con color (azul = ingreso, gris = calculado), nota y ancho de columna."""
    for i, (clave, texto, ancho, ingreso, txt_nota) in enumerate(cols, start=col_ini):
        c = ws.cell(row=fila, column=i, value=texto)
        c.font = Font(name=FUENTE, size=11, bold=True, color="FFFFFF")
        c.fill = AZUL_ENC if ingreso else GRIS_ENC
        c.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
        nota(c, txt_nota)
        ws.column_dimensions[get_column_letter(i)].width = ancho
    ws.row_dimensions[fila].height = 32


def formato_columna(ws, col, filas, fmt=None, ingreso=False, ajustar=False, ini=FILA_INI):
    """Aplica fuente, formato numérico y relleno de ingreso a las filas de datos de una columna."""
    for r in range(ini, ini + filas):
        c = ws[f"{col}{r}"]
        c.font = Font(name=FUENTE, size=11)
        if fmt:
            c.number_format = fmt
        if ingreso:
            c.fill = AMARILLO
        if ajustar:
            c.alignment = Alignment(wrap_text=True, vertical="top")


def fuente_auxiliar(ws, col, filas, ini=FILA_INI):
    """Fuente gris pequeña para columnas auxiliares."""
    for r in range(ini, ini + filas):
        ws[f"{col}{r}"].font = Font(name=FUENTE, size=9, color="808080")


def agregar_tabla(ws, nombre, ultima_col, filas, estilo="TableStyleLight1", fila_enc=FILA_ENC, col_ini="A"):
    """Crea una tabla de Excel (con filtros) sobre encabezado + filas de datos."""
    ref = f"{col_ini}{fila_enc}:{ultima_col}{fila_enc + filas}"
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


def cf_texto(ws, rango_cf, formula, fill, color, tachado=False):
    """Formato condicional por fórmula: relleno + color de texto en negrita (o tachado)."""
    fuente = Font(color=color, strike=True) if tachado else Font(color=color, bold=True)
    ws.conditional_formatting.add(rango_cf, FormulaRule(formula=[formula], fill=fill, font=fuente))


def banner(ws, ultima_col, practica):
    """Fila 2: aviso que cambia según el Modo práctica (visible solo cuando corresponde)."""
    ws.merge_cells(f"A2:{ultima_col}2")
    c = ws["A2"]
    if practica:
        c.value = ('=IF(cfgPractica,"DATOS DE PRÁCTICA (simulados). El Panel los está mostrando. '
                   'Tus datos reales siguen intactos en sus hojas.",'
                   '"Modo práctica desactivado: estos datos simulados están ocultos. Actívalo en Config.")')
    else:
        c.value = ('=IF(cfgPractica,"MODO PRÁCTICA ACTIVO: el Panel muestra datos simulados. '
                   'Lo que escribas aquí se guarda igual. Desactívalo en Config.","")')
    c.font = Font(name=FUENTE, size=11, bold=True, color=NARANJA_TXT)
    c.alignment = Alignment(wrap_text=True, vertical="center")
    ws.row_dimensions[2].height = 30
    cf_texto(ws, "A2", 'cfgPractica', NARANJA_FILL, NARANJA_TXT)


def ocultar_si_no_practica(ws, ultima_col, ultima_fila):
    """En hojas de práctica: con Modo práctica = No, texto y relleno blancos (desaparecen)."""
    ws.conditional_formatting.add(
        f"A3:{ultima_col}{ultima_fila}",
        FormulaRule(formula=['NOT(cfgPractica)'], fill=BLANCO_FILL,
                    font=Font(color="FFFFFF"), stopIfTrue=True))


def columna_explicacion(ws, col, ini, fin):
    """Formato de la columna 'Explicación (práctica)'."""
    ws.column_dimensions[col].width = 40
    for r in range(ini, fin + 1):
        ws[f"{col}{r}"].alignment = Alignment(wrap_text=True, vertical="top")
        ws[f"{col}{r}"].font = Font(name=FUENTE, size=10, italic=True, color=NARANJA_TXT)


def cargar_filas(ws, letras_cols, filas, extra_col=None, ini=FILA_INI):
    """Escribe filas de ejemplo (dict clave→valor) desde la primera fila de datos; 'expl' va a extra_col."""
    for i, fila in enumerate(filas):
        r = ini + i
        for k, v in fila.items():
            col = extra_col if k == "expl" else letras_cols[k]
            ws[f"{col}{r}"] = v


# ---------------------------------------------------------------- hojas de datos
def hoja_compras(wb, nombre, hoja_pagos, hoja_grupos, tabla, practica=False, demo=None):
    """Crea una hoja de compras (real o de práctica) con sus fórmulas, validaciones y formatos."""
    ws = wb.create_sheet(nombre)
    cols = list(COLS_COMPRAS)
    if practica:
        cols.append(("expl", "Explicación (práctica)", 40, False, "Qué caso demuestra esta fila."))
    ult = get_column_letter(len(cols))
    ws["A1"] = "Compras de figuras" + (" — PRÁCTICA" if practica else "")
    estilo_titulo(ws["A1"])
    banner(ws, ult, practica)
    ws["A3"] = ("Encabezado azul = lo escribes tú (celda amarilla) · gris = se calcula solo. "
                "Toca un encabezado para ver su nota.")
    ws["A3"].font = Font(name=FUENTE, size=9, italic=True, color="595959")
    encabezado(ws, FILA_ENC, cols)

    n = FILAS_PRACTICA["compras"] if practica else FILAS_COMPRAS
    fin = FILA_INI + n - 1
    R = lambda k: f"${LC[k]}${FILA_INI}:${LC[k]}${fin}"          # rango propio absoluto
    PA, PB, PC, PF, PV, PVB = (rango(hoja_pagos, LP[k], FILAS_PAGOS) for k in ("fecha", "monto", "tipo", "id", "valid", "vbase"))
    GA = rango(hoja_grupos, LG["id"], FILAS_GRUPOS)

    for r in range(FILA_INI, fin + 1):
        c = {k: f"{LC[k]}{r}" for k in LC}
        x = {k: f"${LC[k]}{r}" for k in LC}       # celdas de ingreso de la fila
        cB, man, sal, mon, grp, acu, fp = x["prod"], x["manual"], x["salida"], x["moneda"], x["grupo"], x["acum"], c["fpago"]
        es_cancel = f'LEFT({man},3)="Can"'
        es_cerr = f'LEFT({man},3)="Cer"'
        es_acum = f'LEFT({acu},1)="S"'
        cant_ok = f'OR({x["cant"]}="",AND(ISNUMBER({x["cant"]}),N({x["cant"]})>0))'
        cant = f'IF({x["cant"]}="",1,{x["cant"]})'
        precio_ok = f'AND(ISNUMBER({x["precio"]}),N({x["precio"]})>=0)'
        tcu_ok = f'AND(ISNUMBER({x["tcusado"]}),N({x["tcusado"]})>0)'
        ws[c["id"]] = f"C{r - FILA_INI + 1:03d}"
        ws[c["foto"]] = f'=IF({x["linkimg"]}="","",_xlfn.IMAGE({x["linkimg"]}))'
        ws[c["clave"]] = f'=IF(OR({cB}="",{es_cancel},NOT(ISNUMBER({sal}))),"",YEAR({sal})*12+MONTH({sal}))'
        ws[fp] = (f'=IF(OR({cB}="",NOT(ISNUMBER({sal}))),"",IF(ISNUMBER({x["pagoesp"]}),{x["pagoesp"]},'
                  f'{fecha_dia_pago(f"YEAR({sal})", f"MONTH({sal})")}))')
        maxd = f'_xlfn.MAXIFS(cfgTCFecha,cfgTCMoneda,{mon},cfgTCFecha,"<="&{fp},cfgTCValor,">0")'
        ws[c["tcsug"]] = (f'=IF(OR({cB}="",{mon}="",{mon}="CLP",{fp}=""),"",'
                          f'IF({maxd}=0,"",AVERAGEIFS(cfgTCValor,cfgTCMoneda,{mon},cfgTCFecha,{maxd},cfgTCValor,">0")))')
        ws[c["tcapl"]] = (f'=IF(OR({cB}="",{mon}=""),"",IF({mon}="CLP",1,IF(OR({mon}="USD",{mon}="JPY"),'
                          f'IF({tcu_ok},{x["tcusado"]},{c["tcsug"]}),"")))')
        ws[c["total"]] = (f'=IF(OR({cB}="",NOT(ISNUMBER({c["tcapl"]})),NOT({precio_ok}),NOT({cant_ok})),"",'
                          f'ROUND({cant}*{x["precio"]}*{c["tcapl"]},0))')
        me = f'IF({x["envmoneda"]}="",{mon},{x["envmoneda"]})'
        maxe = f'_xlfn.MAXIFS(cfgTCFecha,cfgTCMoneda,{me},cfgTCFecha,"<="&N({fp}),cfgTCValor,">0")'
        tce = (f'IF({me}="CLP",1,IF({me}={mon},N({c["tcapl"]}),'
               f'IFERROR(AVERAGEIFS(cfgTCValor,cfgTCMoneda,{me},cfgTCFecha,{maxe},cfgTCValor,">0"),0)))')
        ws[c["envclp"]] = (f'=IF(OR({cB}="",{es_cancel},NOT(ISNUMBER({x["envmonto"]})),N({x["envmonto"]})<=0),0,'
                           f'ROUND({x["envmonto"]}*{tce},0))')
        id_c = f'LEFT({x["id"]},1)="C"'
        ws[c["pagfig"]] = f'=IF({cB}="","",IF({id_c},SUMIFS({PB},{PC},"Fig*",{PF},{x["id"]},{PV},"OK"),0))'
        ws[c["pagenv"]] = f'=IF({cB}="","",IF({id_c},SUMIFS({PB},{PC},"Env*",{PF},{x["id"]},{PV},"OK"),0))'
        s_base = f'SUMIFS({PB},{PC},"Fig*",{PF},{x["id"]},{PVB},"OK")'
        ws[c["salef"]] = f'=IF({cB}="","",IF(ISNUMBER({x["salact"]}),{x["salact"]},IF(ISNUMBER({sal}),{sal},"")))'
        ws[c["fpagada"]] = (f'=IF(OR({cB}="",{es_cancel},NOT({id_c})),"",IF(OR(AND({es_cerr},{s_base}>0),'
                            f'AND(ISNUMBER({c["total"]}),{s_base}>0,{s_base}>={c["total"]})),'
                            f'_xlfn.MAXIFS({PA},{PC},"Fig*",{PF},{x["id"]},{PVB},"OK"),""))')
        ws[c["estado"]] = (f'=IF({cB}="","",IF({es_cancel},"Cancelado",IF({es_cerr},"Cerrado",'
                           f'IF(N({c["pagfig"]})=0,"Pendiente",IF({c["total"]}="","Parcial",'
                           f'IF({c["pagfig"]}>={c["total"]},"Pagado","Parcial"))))))')
        ws[c["falta"]] = (f'=IF({cB}="","",IF(OR({c["estado"]}="Cerrado",{c["estado"]}="Cancelado"),0,'
                          f'MAX(0,N({c["total"]})-{c["pagfig"]})))')
        ws[c["dif"]] = (f'=IF({cB}="","",IF(OR({c["estado"]}="Cerrado",{c["estado"]}="Pagado"),'
                        f'{c["pagfig"]}-N({c["total"]}),0))')
        ws[c["pgrupo"]] = f'=IF(OR({cB}="",{grp}=""),0,SUMIFS({PB},{PC},"Env*",{PF},{grp},{PV},"OK"))'
        pag_grupo = c["pgrupo"]
        ws[c["estenv"]] = (f'=IF({cB}="","",IF({c["envclp"]}=0,"Sin envío",IF({es_acum},'
                           f'IF(AND({grp}<>"",{pag_grupo}>0),"Pagado","Acumulado"),'
                           f'IF({c["pagenv"]}>={c["envclp"]},"Pagado","Pendiente"))))')
        ws[c["envpend"]] = (f'=IF({cB}="","",IF(OR({c["estenv"]}="Pagado",{c["estenv"]}="Sin envío"),0,'
                            f'IF({es_acum},{c["envclp"]},MAX(0,{c["envclp"]}-{c["pagenv"]}))))')
        ws[c["sgrupo"]] = f'=IF(OR({cB}="",{grp}=""),0,SUMIFS({R("envclp")},{R("grupo")},{grp},{R("acum")},"S*",{R("manual")},"<>Can*"))'
        suma_grupo = c["sgrupo"]
        abajo = lambda k: f"${LC[k]}${r + 1}:${LC[k]}${fin + 1}"
        arriba = lambda k: f"${LC[k]}${FILA_ENC}:${LC[k]}${r - 1}"
        es_ultima = (f'COUNTIFS({abajo("grupo")},{grp},{abajo("acum")},"S*",{abajo("manual")},"<>Can*",'
                     f'{abajo("envclp")},">0")=0')
        resto = (f'{pag_grupo}-SUMIFS({arriba("envimp")},{arriba("grupo")},{grp},{arriba("acum")},"S*",'
                 f'{arriba("manual")},"<>Can*")')
        ws[c["envimp"]] = (f'=IF({cB}="","",IF({c["envclp"]}=0,0,IF(AND({es_acum},{grp}<>"",{pag_grupo}>0,'
                           f'{suma_grupo}>0),IF({es_ultima},{resto},ROUND({c["envclp"]}*{pag_grupo}/{suma_grupo},0)),'
                           f'{c["envclp"]})))')
        base = f'N({c["total"]})+N({c["envimp"]})'
        sin_imp = f'OR({cB}="",{c["estado"]}="Cancelado",{c["total"]}="")'
        ws[c["arancel"]] = f'=IF({sin_imp},"",ROUND(({base})*cfgArancelV,0))'
        ws[c["iva"]] = f'=IF({sin_imp},"",ROUND(({base}+{c["arancel"]})*cfgIVAV,0))'
        ws[c["impuestos"]] = f'=IF({sin_imp},"",{c["arancel"]}+{c["iva"]})'
        d_env = (f'IF({es_acum},_xlfn.MAXIFS({PA},{PC},"Env*",{PF},{grp},{PV},"OK"),'
                 f'_xlfn.MAXIFS({PA},{PC},"Env*",{PF},{x["id"]},{PV},"OK"))')
        ws[c["claveenv"]] = (f'=IF({cB}="","",IF({c["estenv"]}="Sin envío",{c["clave"]},IF({c["estenv"]}<>"Pagado","",'
                             f'IF({d_env}=0,"",YEAR({d_env})*12+MONTH({d_env})))))')
        k = c["clave"]
        ws[c["totmes"]] = f'=IF({k}="","",SUMIFS({R("total")},{R("clave")},{k}))'
        ws[c["prodmes"]] = (f'=IF({k}="","",IF(cfgContar="Filas",COUNTIF({R("clave")},{k}),'
                            f'SUMIFS({R("cant")},{R("clave")},{k})+COUNTIFS({R("clave")},{k},{R("cant")},"")))')
        ws[c["limmes"]] = f'=IF({k}="","",IF({c["prodmes"]}<=cfgMaxProdV,cfgLimExcV,cfgLimNormalV))'
        m_bloq = f'IF(COUNTIF({R("clave")},{k}-1)>0,"Mes bloqueado: el mes "&{etiqueta_mes(k + "-1")}&" ya tiene compras","")'
        m_lim = (f'IF({c["totmes"]}>{c["limmes"]},"Límite superado en "&{pesos(c["totmes"] + "-" + c["limmes"])}&'
                 f'IF({c["prodmes"]}<=cfgMaxProdV," (excepción hasta "&cfgMaxProdV&" productos: tope "&{pesos("cfgLimExcV")}&")",'
                 f'" (regla normal, "&{c["prodmes"]}&" productos: tope "&{pesos("cfgLimNormalV")}&")"),"")')
        cancel_aviso = (f'IF(OR(COUNTIF({PF},{x["id"]})>0,{grp}<>""),'
                        f'"Cancelada con pagos o grupo asociado: revisa (esos pagos no cuentan)","")')
        ws[c["msgbloq"]] = f'=IF({k}="","",{m_bloq})'
        ws[c["msglim"]] = f'=IF({k}="","",{m_lim})'
        mb, ml = c["msgbloq"], c["msglim"]
        ws[c["reglas"]] = (f'=IF({cB}="","",IF({es_cancel},{cancel_aviso},IF({k}="","",'
                           f'IF(AND({mb}="",{ml}=""),"OK",_xlfn.TEXTJOIN(" | ",TRUE,{mb},{ml})))))')
        ws[c["avisos"]] = (f'=IF(OR({cB}="",{es_cancel}),"",_xlfn.TEXTJOIN(" | ",TRUE,'
                           f'IF({x["id"]}="","Falta ID",IF(COUNTIF({R("id")},{x["id"]})>1,"ID repetido: corrígelo",IF({id_c},"","El ID debe empezar con C"))),'
                           f'IF({sal}="","Falta fecha salida original",IF(ISNUMBER({sal}),"","Fecha salida no es fecha")),'
                           f'IF({cant_ok},"","Cantidad no válida"),'
                           f'IF({mon}="","Falta moneda",IF(OR({mon}="CLP",{mon}="USD",{mon}="JPY"),"","Moneda no válida")),'
                           f'IF({x["precio"]}="","Falta precio",IF({precio_ok},"","Precio no válido")),'
                           f'IF(AND({x["tcusado"]}<>"",NOT({tcu_ok})),"TC usado no válido",""),'
                           f'IF(AND({mon}="CLP",{x["tcusado"]}<>""),"TC usado se ignora en CLP",""),'
                           f'IF(AND(OR({mon}="USD",{mon}="JPY"),NOT({tcu_ok}),{c["tcsug"]}<>""),"TC no fijado (usa sugerido)",""),'
                           f'IF(AND(OR({mon}="USD",{mon}="JPY"),NOT({tcu_ok}),{c["tcsug"]}=""),"Falta TC",""),'
                           f'IF(AND({mon}="USD",ISNUMBER({c["tcapl"]}),OR(N({c["tcapl"]})<300,N({c["tcapl"]})>3000)),"TC USD raro (fuera de 300-3.000)",""),'
                           f'IF(AND({mon}="JPY",ISNUMBER({c["tcapl"]}),OR(N({c["tcapl"]})<1,N({c["tcapl"]})>30)),"TC JPY raro (fuera de 1-30)",""),'
                           f'IF({x["pagoesp"]}="","",IF(NOT(ISNUMBER({x["pagoesp"]})),"Pago especial no es fecha",'
                           f'IF(AND(ISNUMBER({sal}),OR(YEAR({x["pagoesp"]})<>YEAR(N({sal})),MONTH({x["pagoesp"]})<>MONTH(N({sal})))),"Pago especial fuera del mes de salida",""))),'
                           f'IF(AND({x["envmonto"]}<>"",OR(NOT(ISNUMBER({x["envmonto"]})),N({x["envmonto"]})<0)),"Envío monto no válido",""),'
                           f'IF(AND({es_acum},{grp}=""),"Falta grupo de envío",""),'
                           f'IF(AND({es_acum},{grp}<>"",COUNTIF({GA},{grp})=0),"Grupo no existe",""),'
                           f'IF(AND(NOT({es_acum}),{grp}<>""),"Grupo sin Envío acumulado = Sí",""),'
                           f'IF(AND(N({x["envmonto"]})>0,{me}<>"CLP",{me}<>{mon},{maxe}=0),"Falta TC del envío",""),'
                           f'IF(AND({es_cerr},N({c["pagfig"]})=0),"Cerrada sin pagos: ¿querías Cancelada?",""),'
                           f'IF(AND(ISNUMBER({x["reserva"]}),ISNUMBER({sal}),N({x["reserva"]})>N({sal})),"Reserva después de la salida: si compraste en stock, revisa el mes",""),'
                           f'IF(AND(ISNUMBER({x["salact"]}),ISNUMBER({sal}),N({x["salact"]})<N({sal})),"Salida actual antes que la original",""),'
                           f'IF(AND({es_acum},N({x["envmonto"]})=0),"Envío acumulado sin monto de envío",""),'
                           f'IF(AND(N({c["envclp"]})>0,N({c["pagenv"]})>1.2*N({c["envclp"]})),"Envío pagado de más (¿dos veces?)",""),'
                           f'IF(AND({es_cerr},ISNUMBER({c["total"]}),N({c["total"]})>0,N({c["pagfig"]})>0,N({c["pagfig"]})<0.9*N({c["total"]})),"Cerrada pagando menos del 90%",""),'
                           f'IF(AND(ISNUMBER({c["total"]}),N({c["total"]})>0,N({c["pagfig"]})>1.5*N({c["total"]})),"Pagado mucho más que el total (¿error al escribir?)","")))')
        ws[c["nlista"]] = f'=IF({cB}="","",COUNTA(${LC["prod"]}${FILA_INI}:{cB}))'
        ws[c["etiq"]] = f'=IF({cB}="","",{x["id"]}&" · "&{cB})'
        claves_formula = tuple(k for k, _, _, ing, _ in COLS_COMPRAS if not ing and k not in ("id", "integ"))
        ws[c["integ"]] = (f'=IF(AND({",".join("_xlfn.ISFORMULA(" + c[k] + ")" for k in claves_formula)}),'
                          f'IF({cB}="","","OK"),"Fórmulas borradas: copia la fila de arriba")')
        ws.row_dimensions[r].height = 30   # alto para que la foto se vea

    # formatos
    fmts = {"total": FMT_CLP, "envclp": FMT_CLP, "pagfig": FMT_CLP, "pagenv": FMT_CLP, "falta": FMT_CLP,
            "dif": FMT_CLP, "envpend": FMT_CLP, "envimp": FMT_CLP, "arancel": FMT_CLP, "iva": FMT_CLP,
            "impuestos": FMT_CLP, "totmes": FMT_CLP, "limmes": FMT_CLP,
            "salida": FMT_FECHA, "fpago": FMT_FECHA, "reserva": FMT_FECHA, "salact": FMT_FECHA,
            "pagoesp": FMT_FECHA, "fpagada": FMT_FECHA, "salef": FMT_FECHA, "pgrupo": FMT_CLP, "sgrupo": FMT_CLP,
            "precio": '#,##0.##', "envmonto": '#,##0.##', "tcusado": FMT_TC, "tcsug": FMT_TC, "tcapl": FMT_TC,
            "cant": "0"}
    for k, _, _, ing, _ in COLS_COMPRAS:
        formato_columna(ws, LC[k], n, fmts.get(k), ingreso=ing, ajustar=k in ("reglas", "avisos", "prod"))
    for k in ("clave", "claveenv", "totmes", "prodmes", "limmes", "salef", "pgrupo", "sgrupo", "msgbloq", "msglim", "nlista", "etiq", "integ"):
        fuente_auxiliar(ws, LC[k], n)

    # validaciones
    rng = lambda k: f"{LC[k]}{FILA_INI}:{LC[k]}{fin}"
    lista_dv(ws, "=lstManual", rng("manual"), "Estado manual",
             "Activa = normal. Cerrada = terminar aunque haya diferencia. Cancelada = reserva cancelada (deja de contar).")
    lista_dv(ws, "=lstMonedas", rng("moneda"), "Moneda", "CLP, USD o JPY.")
    lista_dv(ws, "=lstMonedas", rng("envmoneda"), "Moneda del envío", "Vacío = misma moneda del producto.")
    lista_dv(ws, "=lstSiNo", rng("acum"), "Envío acumulado", "Sí = queda en bodega y se paga junto con otros (elige un grupo).")
    lista_dv(ws, f"={'lstGruposP' if practica else 'lstGrupos'}", rng("grupo"), "Grupo de envío",
             "Grupo de la hoja Envíos acumulados. Solo si Envío acumulado = Sí.")
    num_dv(ws, rng("cant"), "Cantidad", "Unidades (entero, mínimo 1).", tipo="whole", minimo=1)
    num_dv(ws, rng("precio"), "Precio unitario", "Precio de una unidad en la moneda elegida.")
    num_dv(ws, rng("tcusado"), "TC usado", "Pesos por 1 USD/JPY del día de pago. Vacío en CLP.")
    num_dv(ws, rng("envmonto"), "Envío monto", "Solo flete, sin aduana ni IVA. 0 = sin envío.")
    fecha_dv(ws, rng("salida"), "Fecha salida original", "Lanzamiento original. Define el mes y el pago del 13. No la cambies si se atrasa.")
    fecha_dv(ws, rng("reserva"), "Fecha reserva", "Informativo.")
    fecha_dv(ws, rng("salact"), "Salida actual", "Opcional: nueva fecha si se atrasó. Solo informativo.")
    fecha_dv(ws, rng("pagoesp"), "Pago especial", "Opcional: si se paga otro día del mismo mes.")

    # formato condicional (rojo = advertencia, verde = OK, gris tachado = cancelado)
    E, S = LC["reglas"], LC["estado"]
    cf_texto(ws, f"A{FILA_INI}:{LC['total']}{fin}", f'${S}{FILA_INI}="Cancelado"', GRIS_FILL, "808080", tachado=True)
    cf_texto(ws, f"{E}{FILA_INI}:{E}{fin}", f'AND(${E}{FILA_INI}<>"",${E}{FILA_INI}<>"OK")', ROJO_FILL, ROJO_TXT)
    cf_texto(ws, f"{E}{FILA_INI}:{E}{fin}", f'${E}{FILA_INI}="OK"', VERDE_FILL, VERDE_TXT)
    cf_texto(ws, f"A{FILA_INI}:{LC['total']}{fin}", f'AND(${E}{FILA_INI}<>"",${E}{FILA_INI}<>"OK")', ROJO_FILL, ROJO_TXT)
    A = LC["avisos"]
    cf_texto(ws, f"{A}{FILA_INI}:{A}{fin}", f'${A}{FILA_INI}<>""', NARANJA_FILL, NARANJA_TXT)
    D = LC["dif"]
    cf_texto(ws, f"{D}{FILA_INI}:{D}{fin}", f'AND(ISNUMBER(${D}{FILA_INI}),${D}{FILA_INI}<>0)', NARANJA_FILL, NARANJA_TXT)
    cf_texto(ws, f"{S}{FILA_INI}:{S}{fin}", f'OR(${S}{FILA_INI}="Pagado",${S}{FILA_INI}="Cerrado")', VERDE_FILL, VERDE_TXT)
    V = LC["estenv"]
    cf_texto(ws, f"{V}{FILA_INI}:{V}{fin}", f'${V}{FILA_INI}="Pagado"', VERDE_FILL, VERDE_TXT)

    I = LC["integ"]
    cf_texto(ws, f"{I}{FILA_INI}:{I}{fin}", f'LEFT(${I}{FILA_INI},4)="Fórm"', ROJO_FILL, ROJO_TXT)
    if demo:
        cargar_filas(ws, LC, demo, extra_col=ult if practica else None)
    agregar_tabla(ws, tabla, ult, n)
    ws.freeze_panes = f"C{FILA_INI}"
    if practica:
        columna_explicacion(ws, ult, FILA_INI, fin)
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
    n = FILAS_PRACTICA["pagos"] if practica else FILAS_PAGOS
    fin = FILA_INI + n - 1
    CA = rango(hoja_compras, LC["id"], FILAS_COMPRAS)
    CT = rango(hoja_compras, LC["acum"], FILAS_COMPRAS)
    CB = rango(hoja_compras, LC["prod"], FILAS_COMPRAS)
    CM = rango(hoja_compras, LC["manual"], FILAS_COMPRAS)
    GA = rango(hoja_grupos, LG["id"], FILAS_GRUPOS)
    GN = rango(hoja_grupos, LG["nenv"], FILAS_GRUPOS)
    GS = rango(hoja_grupos, LG["suma"], FILAS_GRUPOS)
    CE, CFP, CU, CR, CTOT, CSE = (rango(hoja_compras, LC[k], FILAS_COMPRAS) for k in ("envclp", "fpagada", "grupo", "reserva", "total", "salef"))
    for r in range(FILA_INI, fin + 1):
        f, m, t, a, i = (f"${LP[k]}{r}" for k in ("fecha", "monto", "tipo", "asoc", "id"))
        # ID tolerante: "C003 · nombre", "c3", "C 3", "3" (= C003) o "g1"
        parte = f'SUBSTITUTE(IFERROR(LEFT({a},FIND("·",{a})-1),{a})," ","")'
        ws[f"{LP['id']}{r}"] = (f'=IF({a}="","",IF(ISNUMBER({a}),"C"&TEXT({a},"000"),'
                                f'IFERROR(UPPER(LEFT({parte},1))&TEXT(VALUE(MID({parte},2,9)),"000"),UPPER({parte}))))')
        ws[f"{LP['clave']}{r}"] = f'=IF(ISNUMBER({f}),YEAR({f})*12+MONTH({f}),"")'
        pc, pg = f"${LP['posc']}{r}", f"${LP['posg']}{r}"
        ws[pc] = f'=IF(LEFT({i},1)="C",IFERROR(MATCH({i},{CA},0),0),0)'
        ws[pg] = f'=IF(LEFT({i},1)="G",IFERROR(MATCH({i},{GA},0),0),0)'
        es_env = f'LEFT({t},3)="Env"'
        es_fig = f'LEFT({t},3)="Fig"'
        hoy_p = "cfgHoyPractica" if practica else "cfgHoyReal"
        vb = f"${LP['vbase']}{r}"
        ws[vb] = (
            f'=IF(AND({f}="",{m}="",{a}="",{t}=""),"",IF(OR({f}="",{m}="",{t}="",{a}=""),"Faltan datos",'
            f'IF(NOT(ISNUMBER({f})),"Fecha no es fecha",IF({f}>{hoy_p},"Fecha futura: contará desde ese día",'
            f'IF(OR(NOT(ISNUMBER({m})),N({m})<=0),"Monto no válido (número mayor a 0)",'
            f'IF(NOT(OR({es_env},{es_fig})),"Tipo debe ser Figura o Envío",'
            f'IF({pc}>0,IF(COUNTIF({CA},{i})>1,"ID repetido en Compras: corrígelo",'
            f'IF(INDEX({CB},{pc})="","Ese producto está vacío",'
            f'IF(LEFT(INDEX({CM},{pc}),3)="Can","Ese producto está cancelado: no cuenta",'
            f'IF({es_env},IF(LEFT(INDEX({CT},{pc}),1)="S","Ese envío es acumulado: paga al grupo",'
            f'IF(N(INDEX({CE},{pc}))=0,"Esa figura no tiene envío","OK")),"OK")))),'
            f'IF({pg}>0,IF(COUNTIF({GA},{i})>1,"ID de grupo repetido: corrígelo",'
            f'IF({es_fig},"Un grupo solo recibe pagos de Envío",'
            f'IF(N(INDEX({GN},{pg}))=0,"Grupo sin envíos asignados","OK"))),'
            f'"ID no existe"))))))))')
        fpag_c = f'INDEX({CFP},{pc})'
        sal_c = f'INDEX({CSE},{pc})'
        miembros = f'{CU},{i},{CT},"S*",{CM},"<>Can*"'
        ws[f"{LP['valid']}{r}"] = (
            f'=IF({vb}<>"OK",{vb},IF(AND({es_env},{pc}>0),IF({fpag_c}="","Primero paga la figura: aún no está pagada",'
            f'IF({f}<{fpag_c},"El envío no puede ser antes del pago de la figura",'
            f'IF(AND(ISNUMBER({sal_c}),{f}<N({sal_c})),"El envío no puede ser antes de la salida de la figura","OK"))),'
            f'IF(AND({es_env},{pg}>0),IF(COUNTIFS({miembros},{CFP},"")>0,"Hay figuras del grupo sin pagar",'
            f'IF({f}<_xlfn.MAXIFS({CFP},{miembros}),"El envío no puede ser antes del pago de sus figuras",'
            f'IF({f}<_xlfn.MAXIFS({CSE},{miembros}),"El envío no puede ser antes de la salida de sus figuras","OK"))),"OK")))')
        PAr, PBr, PCr, PFr, PVr = (f"${LP[k]}${FILA_INI}:${LP[k]}${fin}" for k in ("fecha", "monto", "tipo", "id", "valid"))
        ws[f"{LP['aviso']}{r}"] = (
            f'=IF(OR({vb}="",{vb}="Faltan datos"),"",_xlfn.TEXTJOIN(" | ",TRUE,'
            f'IF(COUNTIFS({PAr},{f},{PBr},{m},{PCr},{t},{PFr},{i})>1,"Posible pago duplicado",""),'
            f'IF(AND({pc}>0,ISNUMBER(INDEX({CR},MAX(1,{pc})))),IF(N({f})<N(INDEX({CR},MAX(1,{pc}))),"Pago anterior a la fecha de reserva",""),""),'
            f'IF(AND({es_fig},{pc}>0,ISNUMBER(INDEX({CTOT},MAX(1,{pc}))),LEFT(INDEX({CM},MAX(1,{pc})),3)<>"Cer"),IF(SUMIFS({PBr},{PCr},"Fig*",{PFr},{i},{PVr},"OK",{PAr},"<="&N({f}))>INDEX({CTOT},MAX(1,{pc})),"Con este pago se pasa del total de la figura",""),""),'
            f'IF(AND({es_env},{pc}>0,N(INDEX({CE},MAX(1,{pc})))>0),IF(ABS(N({m})-INDEX({CE},MAX(1,{pc})))>0.2*INDEX({CE},MAX(1,{pc})),"Monto distinto al envío calculado",""),""),'
            f'IF(AND({es_env},{pg}>0),IF(N({m})>2*N(INDEX({GS},MAX(1,{pg}))),"Pago del grupo mayor al doble de sus envíos",""),"")))')
        ws[f"{LP['integ']}{r}"] = (f'=IF(AND(_xlfn.ISFORMULA({i}),_xlfn.ISFORMULA(${LP["clave"]}{r}),'
                                   f'_xlfn.ISFORMULA({pc}),_xlfn.ISFORMULA({pg}),_xlfn.ISFORMULA({vb}),_xlfn.ISFORMULA(${LP["aviso"]}{r}),_xlfn.ISFORMULA(${LP["valid"]}{r})),IF({a}="","","OK"),"Fórmulas borradas: copia la fila de arriba")')
    for k, _, _, ing, _ in COLS_PAGOS:
        formato_columna(ws, LP[k], n, {"fecha": FMT_FECHA, "monto": FMT_CLP}.get(k), ingreso=ing, ajustar=k == "valid")
    rng = lambda k: f"{LP[k]}{FILA_INI}:{LP[k]}{fin}"
    fecha_dv(ws, rng("fecha"), "Fecha pago", "Ej. 13-11-2026.")
    num_dv(ws, rng("monto"), "Monto CLP", "Monto en pesos, sin decimales.", tipo="whole", minimo=0)
    lista_dv(ws, "=lstTipoPago", rng("tipo"), "Tipo", "Figura o Envío.")
    lista_dv(ws, f"={'lstAsociadoP' if practica else 'lstAsociado'}", rng("asoc"), "Asociado a",
             "Producto (C...) o grupo de envío (G...). Puedes escribir solo el ID.", estricto=False)
    H = LP["valid"]
    cf_texto(ws, f"{H}{FILA_INI}:{H}{fin}", f'AND(${H}{FILA_INI}<>"",${H}{FILA_INI}<>"OK")', ROJO_FILL, ROJO_TXT)
    cf_texto(ws, f"{H}{FILA_INI}:{H}{fin}", f'${H}{FILA_INI}="OK"', VERDE_FILL, VERDE_TXT)
    for k in ("id", "clave", "posc", "posg", "vbase", "integ"):
        fuente_auxiliar(ws, LP[k], n)
    AV = LP["aviso"]
    cf_texto(ws, f"{AV}{FILA_INI}:{AV}{fin}", f'${AV}{FILA_INI}<>""', NARANJA_FILL, NARANJA_TXT)
    I = LP["integ"]
    cf_texto(ws, f"{I}{FILA_INI}:{I}{fin}", f'LEFT(${I}{FILA_INI},4)="Fórm"', ROJO_FILL, ROJO_TXT)
    if demo:
        cargar_filas(ws, LP, demo, extra_col=ult if practica else None)
    agregar_tabla(ws, tabla, ult, n)
    ws.freeze_panes = f"B{FILA_INI}"
    if practica:
        columna_explicacion(ws, ult, FILA_INI, fin)
        ocultar_si_no_practica(ws, ult, fin)
    return ws


def hoja_grupos(wb, nombre, hoja_compras, hoja_pagos, tabla, practica=False, demo=None):
    """Crea la hoja de envíos acumulados (real o de práctica): un grupo por fila, su ahorro e impuestos."""
    ws = wb.create_sheet(nombre)
    cols = list(COLS_GRUPOS)
    if practica:
        cols.append(("expl", "Explicación (práctica)", 40, False, "Qué caso demuestra este grupo."))
    ult = get_column_letter(len(cols))
    ws["A1"] = "Envíos acumulados" + (" — PRÁCTICA" if practica else "")
    estilo_titulo(ws["A1"])
    banner(ws, ult, practica)
    ws["A3"] = "Pueden quedar en bodega sin plazo. Al pagar, registra el pago conjunto en Pagos (Tipo Envío, Asociado = grupo)."
    ws["A3"].font = Font(name=FUENTE, size=9, italic=True, color="595959")
    encabezado(ws, FILA_ENC, cols)
    n = FILAS_PRACTICA["grupos"] if practica else FILAS_GRUPOS
    fin = FILA_INI + n - 1
    CU, CT, CS, CM, CTOT = (rango(hoja_compras, LC[k], FILAS_COMPRAS)
                            for k in ("grupo", "acum", "envclp", "manual", "total"))
    PA, PB, PC, PF, PV = (rango(hoja_pagos, LP[k], FILAS_PAGOS) for k in ("fecha", "monto", "tipo", "id", "valid"))
    for r in range(FILA_INI, fin + 1):
        c = {k: f"{LG[k]}{r}" for k in LG}
        g = f"${LG['id']}{r}"
        filtro = f'{CU},{g},{CT},"S*",{CM},"<>Can*"'
        ws[c["id"]] = f"G{r - FILA_INI + 1:03d}"
        ws[c["nenv"]] = f'=COUNTIFS({filtro})'
        ws[c["suma"]] = f'=SUMIFS({CS},{filtro})'
        ws[c["pagado"]] = f'=SUMIFS({PB},{PC},"Env*",{PF},{g},{PV},"OK")'
        ws[c["fpago"]] = f'=IF({c["pagado"]}>0,_xlfn.MAXIFS({PA},{PC},"Env*",{PF},{g},{PV},"OK"),"")'
        ws[c["usado"]] = f'=IF(OR(${LG["desc"]}{r}<>"",{c["nenv"]}>0,{c["pagado"]}>0),1,0)'
        ws[c["estado"]] = f'=IF({c["usado"]}=0,"",IF({c["pagado"]}>0,"Pagado","Pendiente"))'
        ws[c["ahorro"]] = f'=IF({c["estado"]}="Pagado",{c["suma"]}-{c["pagado"]},"")'
        base_g = (f'(SUMIFS({CTOT},{filtro})+IF({c["pagado"]}>0,{c["pagado"]},{c["suma"]}))')
        ar_g = f'ROUND({base_g}*cfgArancelV,0)'
        ws[c["imp"]] = f'=IF({c["nenv"]}=0,0,{ar_g}+ROUND(({base_g}+{ar_g})*cfgIVAV,0))'
        ws[c["clave"]] = f'=IF({c["fpago"]}="","",YEAR({c["fpago"]})*12+MONTH({c["fpago"]}))'
        ws[c["nlista"]] = f'=IF({c["usado"]}=1,SUM(${LG["usado"]}${FILA_INI}:{c["usado"]}),"")'
        ws[c["etiq"]] = f'={g}&IF(${LG["desc"]}{r}<>""," · "&${LG["desc"]}{r},"")'
        ws[c["integ"]] = (f'=IF(AND({",".join("_xlfn.ISFORMULA(" + c[k] + ")" for k in ("nenv", "suma", "pagado", "fpago", "estado", "ahorro", "imp", "clave", "usado", "nlista", "etiq"))}),'
                          f'IF(COUNTIF(${LG["id"]}${FILA_INI}:${LG["id"]}${fin},{g})>1,"ID de grupo repetido: corrígelo",'
                          f'IF({c["usado"]}=1,"OK","")),"Fórmulas borradas: copia la fila de arriba")')
    fmts = {"suma": FMT_CLP_VACIO, "pagado": FMT_CLP_VACIO, "ahorro": FMT_CLP, "imp": FMT_CLP_VACIO,
            "fpago": FMT_FECHA, "nenv": '0;-0;""'}
    for k, _, _, ing, _ in COLS_GRUPOS:
        formato_columna(ws, LG[k], n, fmts.get(k), ingreso=ing)
    for k in ("clave", "usado", "nlista", "etiq", "integ"):
        fuente_auxiliar(ws, LG[k], n)
    H = LG["ahorro"]
    cf_texto(ws, f"{H}{FILA_INI}:{H}{fin}", f'AND(ISNUMBER(${H}{FILA_INI}),${H}{FILA_INI}<0)', ROJO_FILL, ROJO_TXT)
    cf_texto(ws, f"{H}{FILA_INI}:{H}{fin}", f'AND(ISNUMBER(${H}{FILA_INI}),${H}{FILA_INI}>=0)', VERDE_FILL, VERDE_TXT)
    S = LG["estado"]
    cf_texto(ws, f"{S}{FILA_INI}:{S}{fin}", f'${S}{FILA_INI}="Pagado"', VERDE_FILL, VERDE_TXT)
    I = LG["integ"]
    cf_texto(ws, f"{I}{FILA_INI}:{I}{fin}", f'OR(LEFT(${I}{FILA_INI},4)="Fórm",LEFT(${I}{FILA_INI},5)="ID de")', ROJO_FILL, ROJO_TXT)
    if demo:
        cargar_filas(ws, LG, demo, extra_col=ult if practica else None)
    agregar_tabla(ws, tabla, ult, n)
    ws.freeze_panes = f"B{FILA_INI}"
    if practica:
        columna_explicacion(ws, ult, FILA_INI, fin)
        ocultar_si_no_practica(ws, ult, fin)
    return ws


def hoja_ahorro(wb, nombre, hoja_compras, hoja_pagos, tabla, practica=False, demo=None):
    """Crea la hoja de ahorro (real o práctica): total arriba, movimientos Agregar/Quitar con historial y plan del 13."""
    ws = wb.create_sheet(nombre)
    cols = list(COLS_MOV)
    if practica:
        cols.append(("expl", "Explicación (práctica)", 30, False, "Qué caso demuestra este movimiento."))
    ult_mov = get_column_letter(len(cols))
    ult = LPL["integ"]
    ws["A1"] = "Ahorro" + (" — PRÁCTICA" if practica else "")
    estilo_titulo(ws["A1"])
    banner(ws, ult_mov, practica)
    CF, CC, CFP = (rango(hoja_compras, LC[k], FILAS_COMPRAS) for k in ("falta", "clave", "fpago"))
    PA, PB, PC, PV = (rango(hoja_pagos, LP[k], FILAS_PAGOS) for k in ("fecha", "monto", "tipo", "valid"))
    MF, MV = (f"${LA[k]}${AH_MOV_INI}:${LA[k]}${AH_MOV_FIN}" for k in ("fecha", "valor"))
    cel = {k: f"${AH_RES_COL}${v}" for k, v in AH_CELDAS.items()}
    hoy = "cfgHoyPractica" if practica else "cfgHoyReal"
    p_ini, p_fin = AH_PLAN_INI, AH_PLAN_FIN
    PL = lambda k: f"${LPL[k]}${p_ini}:${LPL[k]}${p_fin}"

    # 1) TOTAL AHORRADO (lo primero que se ve)
    ws["A3"] = "TOTAL AHORRADO"
    ws["A3"].font = Font(name=FUENTE, size=14, bold=True, color="1F4E78")
    ws.merge_cells("B3:C3")
    ws["B3"] = f"={cel['saldo']}"
    ws["B3"].font = Font(name=FUENTE, size=18, bold=True)
    ws["B3"].number_format = FMT_CLP
    ws["B3"].alignment = Alignment(horizontal="right", vertical="center")
    ws.row_dimensions[3].height = 30
    nota(ws["A3"], "Suma de lo que agregaste menos lo que quitaste (y menos los pagos de figuras si en Config activaste el descuento automático).")
    cf_texto(ws, "B3", "B3<0", ROJO_FILL, ROJO_TXT)
    cf_texto(ws, "B3", "B3>0", VERDE_FILL, VERDE_TXT)
    ws["A4"] = "Próximo pago"
    ws["A4"].font = Font(name=FUENTE, size=11, bold=True)
    ws.merge_cells("B4:" + ult_mov + "4")
    kp = cel["kprox"]
    fprox = f'_xlfn.MINIFS({PL("fecha")},{PL("clave")},{kp})'
    meses_txt = ",".join(f'"{m}"' for m in MESES)
    ws["B4"] = (f'=IF({kp}=0,"Sin pagos pendientes",DAY({fprox})&"-"&CHOOSE(MONTH({fprox}),{meses_txt})&": cubres "&'
                f'{pesos(f"SUMIFS({PL(chr(97) + chr(115) + chr(105) + chr(103) + chr(110) + chr(97) + chr(100) + chr(111))},{PL(chr(99) + chr(108) + chr(97) + chr(118) + chr(101))},{kp})")}'
                f'&" de "&{pesos(f"SUMIFS({PL(chr(110) + chr(101) + chr(99) + chr(101) + chr(115) + chr(105) + chr(116) + chr(97))},{PL(chr(99) + chr(108) + chr(97) + chr(118) + chr(101))},{kp})")})')
    ws["B4"].font = Font(name=FUENTE, size=11)
    ws["A5"] = ("Para agregar o quitar: en la primera fila vacía escribe Fecha y Monto, y elige Agregar o Quitar. "
                "Cada fila queda en el historial.")
    ws.merge_cells("A5:" + ult_mov + "5")
    ws["A5"].font = Font(name=FUENTE, size=9, italic=True, color="595959")
    ws["A5"].alignment = Alignment(wrap_text=True, vertical="top")
    ws.row_dimensions[5].height = 28

    # 2) Movimientos (historial)
    encabezado(ws, AH_MOV_ENC, cols)
    na = FILAS_PRACTICA["abonos"] if practica else FILAS_ABONOS
    mov_fin = AH_MOV_INI + na - 1
    for r in range(AH_MOV_INI, mov_fin + 1):
        f, m, t = (f"${LA[k]}{r}" for k in ("fecha", "monto", "mov"))
        es_a, es_q = f'LEFT({t},1)="A"', f'LEFT({t},1)="Q"'
        valido = f'AND(ISNUMBER({f}),N({f})<={hoy},ISNUMBER({m}),N({m})>0,OR({es_a},{es_q}))'
        ws[f"{LA['valor']}{r}"] = f'=IF({valido},IF({es_q},-{m},{m}),0)'
        ws[f"{LA['despues']}{r}"] = f'=IF(${LA["valor"]}{r}=0,"",SUM(${LA["valor"]}${AH_MOV_INI}:${LA["valor"]}{r}))'
        ws[f"{LA['revisar']}{r}"] = (f'=IF(AND({f}="",{m}="",{t}=""),"",IF(NOT(ISNUMBER({f})),"Fecha no válida",IF({f}>{hoy},"Fecha futura: contará desde ese día",'
                                     f'IF(OR(NOT(ISNUMBER({m})),N({m})<=0),"Monto no válido (escribe un número positivo)",'
                                     f'IF(NOT(OR({es_a},{es_q})),"Elige Agregar o Quitar",'
                                     f'IF(${LA["despues"]}{r}<0,"Ojo: el ahorro queda negativo","OK"))))))')
    for k, _, _, ing, _ in COLS_MOV:
        formato_columna(ws, LA[k], na, {"fecha": FMT_FECHA, "monto": FMT_CLP, "despues": FMT_CLP, "valor": FMT_CLP}.get(k),
                        ingreso=ing, ini=AH_MOV_INI)
    fuente_auxiliar(ws, LA["valor"], na, ini=AH_MOV_INI)
    rng = lambda k: f"{LA[k]}{AH_MOV_INI}:{LA[k]}{mov_fin}"
    fecha_dv(ws, rng("fecha"), "Fecha", "Fecha del movimiento.")
    num_dv(ws, rng("monto"), "Monto CLP", "Monto positivo en pesos. Para sacar dinero elige Quitar.", tipo="whole", minimo=1)
    lista_dv(ws, "=lstMovAhorro", rng("mov"), "Agregar o Quitar", "Agregar suma al ahorro; Quitar lo resta.")
    E = LA["revisar"]
    cf_texto(ws, rng("revisar"), f'AND(${E}{AH_MOV_INI}<>"",${E}{AH_MOV_INI}<>"OK")', ROJO_FILL, ROJO_TXT)
    T = LA["mov"]
    cf_texto(ws, rng("monto"), f'LEFT(${T}{AH_MOV_INI},1)="Q"', ROJO_FILL, ROJO_TXT)
    cf_texto(ws, rng("monto"), f'LEFT(${T}{AH_MOV_INI},1)="A"', VERDE_FILL, VERDE_TXT)
    expl_col = ult_mov if practica else None
    if demo:
        cargar_filas(ws, LA, demo, extra_col=expl_col, ini=AH_MOV_INI)
    agregar_tabla(ws, tabla, ult_mov, na, fila_enc=AH_MOV_ENC)

    # 3) Detalle (a la derecha)
    ws.cell(row=2, column=PLAN_COL0, value="Detalle del ahorro").font = Font(name=FUENTE, size=12, bold=True, color="1F4E78")
    primer = f'_xlfn.MINIFS({MF},{MV},"<>0")'
    detalle = [
        ("hoy", "Hoy", f"={hoy}", FMT_FECHA, "Fecha de hoy (en práctica es fija; en real puedes fijarla en Config para probar)."),
        ("agregado", "Total agregado", f'=SUMIF({MV},">0")', FMT_CLP, "Suma de los movimientos Agregar válidos."),
        ("quitado", "Total quitado", f'=-SUMIF({MV},"<0")', FMT_CLP, "Suma de los movimientos Quitar válidos."),
        ("usado", "Pagos descontados", f'=IF(AND(cfgDescontar,{primer}>0),SUMIFS({PB},{PC},"Fig*",{PA},">="&{primer},{PV},"OK"),0)',
         FMT_CLP, "Solo si en Config activaste 'Descontar pagos del ahorro': pagos de figuras desde tu primer movimiento."),
        ("saldo", "Total ahorrado", f"={cel['agregado']}-{cel['quitado']}-{cel['usado']}", FMT_CLP, "Agregado - quitado - pagos descontados."),
        ("extra", "Saldo extra (sin pago asignado)", f"=MAX(0,{cel['saldo']}-SUM({PL('necesita')}))", FMT_CLP,
         "Lo que sobra después de cubrir todos los pagos que vienen."),
        ("atrasado", "Atrasado (meses pasados sin pagar)", f'=SUMIFS({CF},{CC},"<"&{cel["khoy"]})', FMT_CLP,
         "Falta pagar de figuras de meses ya pasados. No entra en el plan: revísalo en Compras."),
        ("khoy", "Clave mes actual", f"=YEAR({cel['hoy']})*12+MONTH({cel['hoy']})", "0", "Auxiliar: año x 12 + mes de hoy."),
        ("kprox", "Clave próximo pago", f'=IF(COUNTIF({PL("necesita")},">0")=0,0,_xlfn.MINIFS({PL("clave")},{PL("necesita")},">0"))', "0",
         "Auxiliar: clave del primer mes del plan con pagos pendientes."),
    ]
    ws.column_dimensions[get_column_letter(PLAN_COL0)].width = 26
    for k, etiq, f, fmt, txt in detalle:
        r = AH_CELDAS[k]
        a = ws.cell(row=r, column=PLAN_COL0, value=etiq)
        a.font = Font(name=FUENTE, size=11, bold=k == "saldo")
        a.alignment = Alignment(wrap_text=True)
        nota(a, txt, alto=80)
        b = ws.cell(row=r, column=PLAN_COL0 + 1, value=f)
        b.font = Font(name=FUENTE, size=12, bold=True)
        b.number_format = fmt
    for k in ("khoy", "kprox"):
        ws.cell(row=AH_CELDAS[k], column=PLAN_COL0).font = Font(name=FUENTE, size=9, color="808080")
        ws.cell(row=AH_CELDAS[k], column=PLAN_COL0 + 1).font = Font(name=FUENTE, size=9, color="808080")
    cf_texto(ws, cel["atrasado"].replace("$", ""), f"{cel['atrasado']}>0", ROJO_FILL, ROJO_TXT)

    # 4) Plan de pagos del 13 (a la derecha, debajo del detalle)
    ws.cell(row=AH_PLAN_ENC - 1, column=PLAN_COL0, value="Plan de pagos del 13").font = Font(
        name=FUENTE, size=12, bold=True, color="1F4E78")
    for i, (k, t, ancho, txt) in enumerate(COLS_PLAN):
        c = ws.cell(row=AH_PLAN_ENC, column=PLAN_COL0 + i, value=t)
        c.font = Font(name=FUENTE, size=11, bold=True, color="FFFFFF")
        c.fill = GRIS_ENC
        c.alignment = Alignment(wrap_text=True, horizontal="center", vertical="center")
        nota(c, txt, alto=90)
        if i > 1:
            ws.column_dimensions[get_column_letter(PLAN_COL0 + i)].width = ancho
    ws.column_dimensions[LPL["fecha"]].width = 11
    ws.row_dimensions[AH_PLAN_ENC].height = 32
    saldo_pos = f"MAX(0,{cel['saldo']})"
    for j, r in enumerate(range(p_ini, p_fin + 1)):
        L = {k: f"{LPL[k]}{r}" for k in LPL}
        mas_adelante = r == p_fin
        if mas_adelante:
            ws[L["clave"]] = (f'=IF(COUNTIFS({CC},">"&({cel["khoy"]}+{MESES_PLAN - 1}),{CF},">0")=0,{cel["khoy"]}+{MESES_PLAN},'
                              f'_xlfn.MINIFS({CC},{CC},">"&({cel["khoy"]}+{MESES_PLAN - 1}),{CF},">0"))')
            ws[L["mes"]] = "Más adelante"
            ws[L["necesita"]] = f'=SUMIFS({CF},{CC},">"&({cel["khoy"]}+{MESES_PLAN - 1}))'
        else:
            ws[L["clave"]] = f"={cel['khoy']}+{j}"
            ws[L["mes"]] = "=" + etiqueta_mes(L["clave"])
            ws[L["necesita"]] = f"=SUMIFS({CF},{CC},{L['clave']})"
        ws[L["fecha"]] = f'=IF({L["necesita"]}=0,"",_xlfn.MINIFS({CFP},{CC},{L["clave"]},{CF},">0"))'
        ws[L["asignado"]] = (f"=MIN({L['necesita']},MAX(0,{saldo_pos}-SUM(${LPL['necesita']}${AH_PLAN_ENC}:"
                             f"{LPL['necesita']}{r - 1})))")
        ws[L["falta"]] = f"={L['necesita']}-{L['asignado']}"
        ws[L["meses"]] = f'=IF({L["falta"]}=0,"",{L["clave"]}-{cel["khoy"]})'
        ws[L["cuota"]] = (f'=IF({L["falta"]}=0,0,IF({L["clave"]}-{cel["khoy"]}<=0,{L["falta"]},'
                          f'ROUNDUP({L["falta"]}/({L["clave"]}-{cel["khoy"]}),0)))')
        if j == 0:
            ws[L["apartar"]] = f"={L['cuota']}"
        elif mas_adelante:
            ws[L["apartar"]] = ""
        else:
            ws[L["apartar"]] = f'=SUMIFS({PL("cuota")},{PL("clave")},">="&{L["clave"]})'
        revisar = [k for k in LPL if k != "integ" and not (mas_adelante and k in ("mes", "apartar"))]
        ws[L["integ"]] = (f'=IF(AND({",".join("_xlfn.ISFORMULA(" + L[k] + ")" for k in revisar)}),"OK",'
                          f'"Fórmulas borradas: regenera o copia la fila de arriba")')
        for k in LPL:
            aux = k in ("clave", "integ")
            ws[L[k]].font = Font(name=FUENTE, size=9 if aux else 11, color="808080" if aux else "000000")
        for k in ("necesita", "asignado", "falta", "cuota", "apartar"):
            ws[L[k]].number_format = FMT_CLP_VACIO
        ws[L["fecha"]].number_format = FMT_FECHA
        ws[L["meses"]].number_format = "0"
    c0, c7 = LPL["mes"], LPL["apartar"]
    cf_texto(ws, f"{LPL['integ']}{p_ini}:{LPL['integ']}{p_fin}", f'LEFT(${LPL["integ"]}{p_ini},4)="Fórm"', ROJO_FILL, ROJO_TXT)
    cf_texto(ws, f"{c0}{p_ini}:{c7}{p_fin}", f"${LPL['necesita']}{p_ini}=0", BLANCO_FILL, "BFBFBF")
    cf_texto(ws, f"{LPL['falta']}{p_ini}:{LPL['falta']}{p_fin}", f"${LPL['falta']}{p_ini}>0", ROJO_FILL, ROJO_TXT)
    cf_texto(ws, f"{LPL['asignado']}{p_ini}:{LPL['asignado']}{p_fin}",
             f"AND(${LPL['necesita']}{p_ini}>0,${LPL['falta']}{p_ini}=0)", VERDE_FILL, VERDE_TXT)
    cf_texto(ws, f"{c7}{p_ini}:{c7}{p_ini + 1}", f"{c7}{p_ini}>0", NARANJA_FILL, NARANJA_TXT)
    ws.freeze_panes = f"A{AH_MOV_INI}"
    if practica:
        columna_explicacion(ws, expl_col, AH_MOV_INI, mov_fin)
        ws.column_dimensions[expl_col].width = 30
        ws.merge_cells(f"{get_column_letter(PLAN_COL0 + 3)}3:{ult}11")
        c = ws.cell(row=3, column=PLAN_COL0 + 3, value=(
            "Hoy de práctica: 01-02-2027 (fijo en Config). Movimientos: +60.000, +40.000, -20.000 (se usó para pagar C013) "
            "y +50.000 → total $130.000. Cubre marzo ($120.000) completo y el sobrante ($10.000) pasa a mayo. Mayo y julio "
            "se reparten en cuotas iguales. Febrero no tiene pagos, por eso 'apartar este mes' es $0."))
        c.font = Font(name=FUENTE, size=10, italic=True, color=NARANJA_TXT)
        c.alignment = Alignment(wrap_text=True, vertical="top")
        ocultar_si_no_practica(ws, ult, max(mov_fin, p_fin))
    return ws


# ---------------------------------------------------------------- datos de práctica
D = date
DEMO_COMPRAS = [
    dict(prod="Figura A (mar)", cant=1, salida=D(2026, 3, 20), reserva=D(2025, 9, 1), tienda="Nin Nin", moneda="CLP",
         precio=90000, envmonto=6000, envmoneda="CLP", acum="No",
         expl="Marzo: 3 productos suman exactamente $200.000 → OK (no supera). Su envío individual se pagó el 10-abr, mes bloqueado: no da advertencia."),
    dict(prod="Figura B USD (mar)", cant=1, salida=D(2026, 3, 25), tienda="Tienda EE.UU.", moneda="USD", precio=60,
         tcusado=950, envmonto=12, envmoneda="USD", acum="Sí", grupo="G001",
         expl="USD 60 x TC fijado 950 = $57.000. Se pagó en dos partes ($30.000 + $27.000) antes de enviar el grupo G001."),
    dict(prod="Figura C (mar)", cant=1, salida=D(2026, 3, 28), tienda="Nin Nin", moneda="CLP", precio=53000,
         envmonto=9000, envmoneda="CLP", acum="Sí", grupo="G001",
         expl="Con envíos, marzo sumaría $226.400 (> $200.000) pero los envíos no cuentan: sigue OK."),
    dict(prod="Figura D JPY (may)", cant=1, salida=D(2026, 5, 22), salact=D(2026, 7, 30), tienda="Nin Nin",
         moneda="JPY", precio=20000, tcusado=6.5, envmonto=2000, acum="Sí", grupo="G001", manual="Cerrada",
         expl="JPY 20.000 x 6,5 = $130.000. Cerrada pagando $128.000 → diferencia -$2.000. Se atrasó a julio, pero sigue contando para mayo."),
    dict(prod="Figura E (may)", cant=1, salida=D(2026, 5, 18), tienda="Nin Nin", moneda="CLP", precio=170000,
         expl="Mayo: 2 productos suman exactamente $300.000 → OK por la excepción (hasta 2 productos, tope $300.000)."),
    dict(prod="Figura F (jul)", cant=1, salida=D(2026, 7, 20), tienda="Nin Nin", moneda="CLP", precio=100000,
         envmonto=5000, envmoneda="CLP", acum="Sí", grupo="G003",
         expl="Julio: 3 productos suman $250.000 → límite superado en $50.000 (regla normal, tope $200.000)."),
    dict(prod="Figura G (jul)", cant=1, salida=D(2026, 7, 25), tienda="Nin Nin", moneda="CLP", precio=80000,
         envmonto=5000, envmoneda="CLP", acum="Sí", grupo="G003",
         expl="Mismo mes que la anterior: también muestra el exceso del mes."),
    dict(prod="Figura H (jul)", cant=1, salida=D(2026, 7, 30), tienda="Nin Nin", moneda="CLP", precio=70000,
         expl="Tercer producto de julio: con 3 productos ya no aplica la excepción."),
    dict(prod="Figura I (ago)", cant=1, salida=D(2026, 8, 20), tienda="Nin Nin", moneda="CLP", precio=45000,
         envmonto=4000, envmoneda="CLP", acum="No",
         expl="Agosto es contiguo a julio (ocupado) → Mes bloqueado. Su envío individual sigue pendiente."),
    dict(prod="Figura J (oct)", cant=1, salida=D(2026, 10, 20), tienda="Nin Nin", moneda="CLP", precio=150000,
         expl="Octubre: 2 productos suman $309.800 → supera la excepción en $9.800."),
    dict(prod="Figura K USD (oct)", cant=1, salida=D(2026, 10, 24), tienda="Tienda EE.UU.", moneda="USD", precio=170,
         tcusado=940, expl="USD 170 x 940 = $159.800."),
    dict(prod="Figura L USD (dic)", cant=1, salida=D(2026, 12, 30), tienda="Tienda EE.UU.", moneda="USD", precio=80,
         tcusado=1000, envmonto=8000, envmoneda="CLP", acum="Sí", grupo="G002", manual="Cerrada",
         expl="Sale el 30-dic y se paga el 13-dic. USD 80 x 1.000 = $80.000. Cerrada pagando $81.500 → diferencia +$1.500."),
    dict(prod="Figura M (ene)", cant=1, salida=D(2027, 1, 20), tienda="Nin Nin", moneda="CLP", precio=60000,
         envmonto=7000, envmoneda="CLP", acum="Sí", grupo="G002",
         expl="Enero 2027 es contiguo a diciembre 2026 → Mes bloqueado (cambio de año). Pago parcial de $20.000 (sale del ahorro); los $40.000 restantes quedan como Atrasado."),
    dict(prod="Figura N (mar 27)", cant=1, salida=D(2027, 3, 25), reserva=D(2026, 9, 10), tienda="Nin Nin",
         moneda="CLP", precio=120000, linkimg="https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png",
         expl="Reserva para marzo 2027: el ahorro la cubre completa. Tiene 'Link imagen' de ejemplo para probar la Foto."),
    dict(prod="Figura O JPY (may 27)", cant=1, salida=D(2027, 5, 28), pagoesp=D(2027, 5, 8), tienda="Nin Nin",
         moneda="JPY", precio=15000, tcusado=6.4,
         expl="JPY 15.000 x 6,4 = $96.000. Se paga antes del 13 (pago especial 08-may) y sigue contando para mayo. Recibe el sobrante del ahorro."),
    dict(prod="Figura P (jul 27)", cant=1, salida=D(2027, 7, 22), tienda="Nin Nin", moneda="CLP", precio=50000,
         expl="Reserva de julio 2027: se reparte en cuotas iguales de feb a jul."),
    dict(prod="Figura Q cancelada (sep 27)", cant=1, salida=D(2027, 9, 20), tienda="Nin Nin", moneda="CLP",
         precio=200000, manual="Cancelada",
         expl="Reserva cancelada: no cuenta en reglas, ahorro, impuestos ni grupos. Queda gris y tachada."),
]
DEMO_PAGOS = [
    dict(fecha=D(2026, 3, 13), monto=90000, tipo="Figura", asoc="C001", nota="Pago completo", expl="Paga completo C001 → Pagado."),
    dict(fecha=D(2026, 4, 10), monto=6000, tipo="Envío", asoc="C001", nota="Envío individual",
         expl="Envío pagado en abril (mes bloqueado por marzo): los pagos de envío no afectan las reglas."),
    dict(fecha=D(2026, 3, 13), monto=30000, tipo="Figura", asoc="C002", nota="Abono", expl="Primer pago parcial de C002."),
    dict(fecha=D(2026, 5, 13), monto=27000, tipo="Figura", asoc="C002", nota="Resto",
         expl="Completa C002 antes de pagar el envío del grupo G001: un envío no se puede pagar con la figura sin pagar."),
    dict(fecha=D(2026, 5, 13), monto=128000, tipo="Figura", asoc="C004", nota="Pago con tarjeta",
         expl="Pagó $2.000 menos que lo calculado; C004 se cerró con diferencia."),
    dict(fecha=D(2026, 5, 13), monto=170000, tipo="Figura", asoc="C005", nota="", expl="C005 Pagado exacto."),
    dict(fecha=D(2026, 8, 5), monto=26000, tipo="Envío", asoc="G001", nota="Bodega Nin Nin",
         expl="Pago conjunto de G001 en agosto: recién ahí salió C004 (se atrasó a julio). Individuales suman $33.400 → ahorro $7.400."),
    dict(fecha=D(2026, 7, 13), monto=100000, tipo="Figura", asoc="C006", nota="", expl="C006 Pagado."),
    dict(fecha=D(2026, 9, 1), monto=12500, tipo="Envío", asoc="G003", nota="Caja julio",
         expl="Pago conjunto de G003: individuales suman $10.000 → sobrecosto $2.500 (ahorro negativo)."),
    dict(fecha=D(2026, 12, 13), monto=81500, tipo="Figura", asoc="C012", nota="Cobro final tarjeta",
         expl="Pagó $1.500 más; C012 cerrado con diferencia positiva."),
    dict(fecha=D(2026, 3, 13), monto=53000, tipo="Figura", asoc="C003", nota="", expl="C003 Pagado."),
    dict(fecha=D(2026, 7, 13), monto=80000, tipo="Figura", asoc="C007", nota="", expl="C007 Pagado."),
    dict(fecha=D(2026, 7, 13), monto=70000, tipo="Figura", asoc="C008", nota="", expl="C008 Pagado."),
    dict(fecha=D(2026, 8, 13), monto=45000, tipo="Figura", asoc="C009", nota="", expl="C009 Pagado (aunque el mes estaba bloqueado)."),
    dict(fecha=D(2026, 10, 13), monto=150000, tipo="Figura", asoc="C010", nota="", expl="C010 Pagado."),
    dict(fecha=D(2026, 10, 13), monto=159800, tipo="Figura", asoc="C011", nota="", expl="C011 Pagado."),
    dict(fecha=D(2027, 1, 9), monto=20000, tipo="Figura", asoc="C013", nota="Abono",
         expl="Pago parcial de C013, posterior al primer abono: se descuenta del ahorro."),
]
DEMO_GRUPOS = [
    dict(desc="Bodega mar-may", expl="3 envíos acumulados pagados juntos con ahorro."),
    dict(desc="Bodega dic-ene", expl="Pendiente sin fecha: puede esperar en bodega todo lo necesario."),
    dict(desc="Caja julio", expl="Pagado en conjunto más caro que por separado: sobrecosto."),
]
DEMO_MOV = [
    dict(fecha=D(2026, 12, 20), monto=60000, mov="Agregar", nota="Sueldo", expl="Primer movimiento: agrega al ahorro."),
    dict(fecha=D(2027, 1, 5), monto=40000, mov="Agregar", nota="", expl="Se asigna solo al pago más cercano no cubierto."),
    dict(fecha=D(2027, 1, 9), monto=20000, mov="Quitar", nota="Pago parcial C013", expl="Se sacó del ahorro para pagar C013."),
    dict(fecha=D(2027, 1, 25), monto=50000, mov="Agregar", nota="Extra", expl="Completa marzo; lo que sobra pasa a mayo."),
]

CASOS_PRACTICA = [
    "Hoy de práctica = 01-02-2027 (Config). Próximo pago: 13-mar-2027, cubierto por el ahorro. Atrasado $40.000: C013 quedó con saldo.",
    "mar-2026: 3 productos = $200.000 exacto → OK. Con envíos sumaría $226.400 y sigue OK (envíos no cuentan).",
    "abr-2026: sin compras (bloqueado por marzo). Se pagó un envío ese mes → sin advertencia.",
    "may-2026: 2 productos = $300.000 exacto → OK por excepción. C004 cerrado con -$2.000 (se atrasó, sigue en mayo).",
    "jul-2026: 3 productos = $250.000 → límite superado en $50.000.",
    "ago-2026: Mes bloqueado (julio ya tiene compras). Pago conjunto de G001 → ahorro $7.400 (no antes: C004 salió en julio).",
    "sep-2026: pago del grupo G003 → sobrecosto $2.500.",
    "oct-2026: 2 productos = $309.800 → supera la excepción en $9.800.",
    "dic-2026: C012 sale el 30 y se paga el 13; cerrado con +$1.500. ene-2027: bloqueado por diciembre (cambio de año).",
    "may-2027: pago especial el 08-may (antes del 13), cuenta para mayo. sep-2027: reserva cancelada, no cuenta.",
]


# ---------------------------------------------------------------- Config, Listas, Panel, Ayuda
def hoja_config(wb):
    """Crea Config: modo práctica, límites, día de pago, impuestos, fechas de hoy y tabla de tipos de cambio."""
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
         "Tope de compras de figuras por mes (sin envíos ni impuestos). Superarlo marca advertencia. Exactamente igual al tope es OK."),
        (5, "Límite con excepción (CLP)", 300000, "cfgLimExc", FMT_CLP,
         "Tope que aplica cuando el mes tiene pocos productos (ver fila siguiente)."),
        (6, "Máx. productos para excepción", 2, "cfgMaxProd", "0",
         "Si el mes tiene esta cantidad de productos o menos, el tope sube al límite con excepción."),
        (7, "Contar productos por", "Unidades", "cfgContar", None,
         "Unidades = suma la columna Cantidad (2 unidades de la misma figura cuentan 2). Filas = cada fila de Compras cuenta 1."),
        (8, "Día de pago del mes", 13, "cfgDiaPago", "0",
         "Día del mes en que se pagan las figuras (del mes de salida original). Si el mes es más corto, se usa el último día."),
        (9, "Arancel estimado", 0.06, "cfgArancel", FMT_PCT,
         "Porcentaje de arancel sobre figura + envío. Solo para estimar; las reglas de Aduanas pueden cambiar."),
        (10, "IVA estimado", 0.19, "cfgIVA", FMT_PCT,
         "IVA sobre figura + envío + arancel. Solo para estimar."),
        (11, "Fecha de hoy (opcional)", None, "cfgHoy", FMT_FECHA,
         "Vacío = se usa la fecha real del teléfono. Escribe una fecha solo para probar el plan de ahorro."),
        (12, "Fecha de hoy en práctica", D(2027, 2, 1), "cfgHoyPractica", FMT_FECHA,
         "Fecha fija que usan los datos de práctica para el plan de ahorro."),
        (13, "Descontar pagos del ahorro", "No", "cfgDescontarTxt", None,
         "No (recomendado) = el ahorro solo cambia con tus movimientos Agregar/Quitar; cuando pagues una figura con lo ahorrado, registra un Quitar. Sí = los pagos de figuras se restan solos del ahorro (entonces NO registres Quitar al pagar)."),
    ]
    for fila, etiq, val, nombre, fmt, txt in filas:
        a = ws.cell(row=fila, column=1, value=etiq)
        a.font = Font(name=FUENTE, size=11, bold=True)
        nota(a, txt, alto=100)
        b = ws.cell(row=fila, column=2, value=val)
        b.font = Font(name=FUENTE, size=12, bold=True, color="0000FF")
        b.fill = AMARILLO
        if fmt:
            b.number_format = fmt
        wb.defined_names[nombre] = DefinedName(nombre, attr_text=f"Config!$B${fila}")
    # Valores "seguros": si la celda está vacía o mal escrita se usa el valor por defecto (y Revisar lo avisa)
    seguros = {
        "cfgPractica": 'LEFT(TRIM(Config!$B$3),1)="S"',
        "cfgLimNormalV": 'IF(AND(ISNUMBER(Config!$B$4),Config!$B$4>0),Config!$B$4,200000)',
        "cfgLimExcV": 'IF(AND(ISNUMBER(Config!$B$5),Config!$B$5>0),Config!$B$5,300000)',
        "cfgMaxProdV": 'IF(AND(ISNUMBER(Config!$B$6),Config!$B$6>=0),INT(Config!$B$6),2)',
        "cfgDiaPagoV": 'IF(AND(ISNUMBER(Config!$B$8),Config!$B$8>=1,Config!$B$8<=31),INT(Config!$B$8),13)',
        "cfgArancelV": 'IF(ISNUMBER(Config!$B$9),IF(Config!$B$9>1,Config!$B$9/100,MAX(0,Config!$B$9)),0.06)',
        "cfgIVAV": 'IF(ISNUMBER(Config!$B$10),IF(Config!$B$10>1,Config!$B$10/100,MAX(0,Config!$B$10)),0.19)',
        "cfgHoyReal": 'IF(ISNUMBER(Config!$B$11),Config!$B$11,TODAY())',
        "cfgDescontar": 'LEFT(TRIM(Config!$B$13),1)="S"',
    }
    for nombre, f in seguros.items():
        wb.defined_names[nombre] = DefinedName(nombre, attr_text=f)
    # Columna Revisar: avisa si un parámetro está vacío o mal escrito
    ws["C2"] = "Revisar"
    ws["C2"].font = Font(name=FUENTE, size=11, bold=True, color="595959")
    ws.column_dimensions["C"].width = 30
    revisar = {
        3: 'IF(OR(LEFT(TRIM(B3),1)="S",LEFT(TRIM(B3),1)="N"),"OK","Escribe Sí o No (se usa No)")',
        4: 'IF(AND(ISNUMBER(B4),B4>0),"OK","Valor no válido: se usa 200.000")',
        5: 'IF(AND(ISNUMBER(B5),B5>0),IF(B5<cfgLimNormalV,"Ojo: es menor que el límite normal","OK"),"Valor no válido: se usa 300.000")',
        6: 'IF(AND(ISNUMBER(B6),B6>=0),"OK","Valor no válido: se usa 2")',
        7: 'IF(OR(B7="Unidades",B7="Filas"),"OK","Escribe Unidades o Filas (se usa Unidades)")',
        8: 'IF(AND(ISNUMBER(B8),B8>=1,B8<=31),"OK","Valor no válido: se usa 13")',
        9: 'IF(ISNUMBER(B9),IF(B9>1,"Se interpreta como "&B9&"%","OK"),"Valor no válido: se usa 6%")',
        10: 'IF(ISNUMBER(B10),IF(B10>1,"Se interpreta como "&B10&"%","OK"),"Valor no válido: se usa 19%")',
        11: 'IF(B11="","OK (usa la fecha real)",IF(ISNUMBER(B11),"Fecha fija para pruebas: bórrala para uso normal","No es fecha: se usa la fecha real"))',
        12: 'IF(ISNUMBER(B12),"OK","No es fecha")',
        13: 'IF(OR(LEFT(TRIM(B13),1)="S",LEFT(TRIM(B13),1)="N"),"OK","Escribe Sí o No (se usa No)")',
    }
    for fila, f in revisar.items():
        c = ws.cell(row=fila, column=3, value="=" + f)
        c.font = Font(name=FUENTE, size=10, italic=True)
        c.alignment = Alignment(wrap_text=True, vertical="center")
    cf_texto(ws, "C3:C13", 'LEFT(C3,2)="OK"', VERDE_FILL, VERDE_TXT)
    cf_texto(ws, "C3:C13", 'AND(C3<>"",LEFT(C3,2)<>"OK")', NARANJA_FILL, NARANJA_TXT)
    lista_dv(ws, "=lstSiNo", "B13", "Descontar pagos", "No = el ahorro solo cambia con Agregar/Quitar.", permitir_vacio=False)
    lista_dv(ws, "=lstSiNo", "B3", "Modo práctica", "Sí = ver datos simulados. No = ver solo tus datos.", permitir_vacio=False)
    num_dv(ws, "B4:B5", "Límite", "Monto en pesos.", tipo="whole", minimo=0)
    num_dv(ws, "B6", "Máx. productos", "Entero, por ej. 2.", tipo="whole", minimo=0)
    lista_dv(ws, '"Unidades,Filas"', "B7", "Contar productos por", "Unidades (columna Cantidad) o Filas.", permitir_vacio=False)
    dv = DataValidation(type="whole", operator="between", formula1="1", formula2="31", showInputMessage=True,
                        showErrorMessage=True, promptTitle="Día de pago", prompt="Número entre 1 y 31.")
    ws.add_data_validation(dv)
    dv.add("B8")
    num_dv(ws, "B9:B10", "Porcentaje", "Escribe por ej. 6% o 0,06.")
    fecha_dv(ws, "B11:B12", "Fecha", "Fecha para el plan de ahorro.")
    cf_texto(ws, "B3", 'B3="Sí"', NARANJA_FILL, NARANJA_TXT)
    cf_texto(ws, "B11", 'B11<>""', NARANJA_FILL, NARANJA_TXT)

    ws["A14"] = "Tipos de cambio (se ingresan a mano)"
    estilo_titulo(ws["A14"], 12)
    ws["A15"] = "Pesos por 1 unidad de moneda. Compras usa el último valor con fecha igual o anterior a la fecha de pago como sugerencia."
    ws["A15"].font = Font(name=FUENTE, size=9, italic=True, color="595959")
    ws["A15"].alignment = Alignment(wrap_text=True)
    ws.merge_cells("A15:C15")
    ws.row_dimensions[15].height = 28
    enc = [("Fecha", "Fecha desde la que rige el valor."), ("Moneda", "USD o JPY."),
           ("CLP por unidad", "Pesos chilenos por 1 USD o 1 JPY (ej. 950 o 6,4).")]
    fila_enc_tc, n_tc = 16, 200
    for i, (t, txt) in enumerate(enc, start=1):
        c = ws.cell(row=fila_enc_tc, column=i, value=t)
        c.font = Font(name=FUENTE, bold=True, color="FFFFFF")
        c.fill = AZUL_ENC
        nota(c, txt, 220, 70)
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
    ws[f"D{ini}"] = "← valores de ejemplo: reemplázalos por los reales"
    ws.merge_cells(f"D{ini}:H{ini}")
    ws[f"D{ini}"].font = Font(name=FUENTE, size=9, italic=True, color=NARANJA_TXT)
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
             "I": ("Años", list(range(2024, 2041)), "lstAnios"),
             "J": ("Estado manual", ["Activa", "Cerrada", "Cancelada"], "lstManual"),
             "L": ("Movimiento ahorro", ["Agregar", "Quitar"], "lstMovAhorro")}
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
        col_n = "C" if col == "A" else "D"
        ws[f"{col_n}1"] = f'=COUNTA({rango(hc, LC["prod"], FILAS_COMPRAS)})'
        ws[f"{col_n}2"] = f'=SUM({rango(hg, LG["usado"], FILAS_GRUPOS)})'
        n_c, n_g = f"Listas!${col_n}$1", f"Listas!${col_n}$2"
        largo = (FILAS_COMPRAS + FILAS_GRUPOS) if col == "A" else (FILAS_PRACTICA["compras"] + FILAS_PRACTICA["grupos"])
        for k in range(1, largo + 1):
            r = k + 1
            ws[f"{col}{r}"] = (f'=IF({k}<={n_c},INDEX({et_c},MATCH({k},{nl_c},0)),'
                               f'IF({k}<={n_c}+{n_g},INDEX({et_g},MATCH({k}-{n_c},{nl_g},0)),""))')
        wb.defined_names[nombre] = DefinedName(
            nombre, attr_text=f'OFFSET(Listas!${col}$2,0,0,MAX(1,{n_c}+{n_g}),1)')
        wb.defined_names[nombre_g] = DefinedName(nombre_g, attr_text=rango(hg, LG["id"], FILAS_GRUPOS))
    ws["K1"] = "Conteos auxiliares: C1/C2 = productos/grupos reales; D1/D2 = práctica."
    ws.sheet_state = "hidden"
    return ws


def hoja_panel(wb):
    """Crea el Panel: primero ahorro y próximo pago (hoy); luego reglas, figuras, envíos e impuestos del mes elegido."""
    ws = wb["Panel"]
    ws.column_dimensions["A"].width = 30
    ws.column_dimensions["B"].width = 20
    ws.column_dimensions["C"].width = 8
    ws.column_dimensions["C"].hidden = True
    ws["A1"] = "Panel de figuras"
    estilo_titulo(ws["A1"], 16)
    ws.merge_cells("A2:B2")
    ws["A2"] = '=IF(cfgPractica,"MODO PRÁCTICA: estás viendo datos simulados. Desactívalo en Config.","Datos reales")'
    ws["A2"].font = Font(name=FUENTE, size=11, bold=True, color="595959")
    ws["A2"].alignment = Alignment(wrap_text=True)
    ws.row_dimensions[2].height = 30
    cf_texto(ws, "A2", 'cfgPractica', NARANJA_FILL, NARANJA_TXT)

    def doble(hoja_real, hoja_prac, col, filas):
        return rango(hoja_real, col, filas), rango(hoja_prac, col, filas)

    comp = lambda k: doble("Compras", "Práctica Compras", LC[k], FILAS_COMPRAS)
    pag = lambda k: doble("Pagos", "Práctica Pagos", LP[k], FILAS_PAGOS)
    grp = lambda k: doble("Envíos acumulados", "Práctica Envíos", LG[k], FILAS_GRUPOS)
    plan = lambda k: (rango("Ahorro", LPL[k], MESES_PLAN + 1, AH_PLAN_INI),
                      rango("Práctica Ahorro", LPL[k], MESES_PLAN + 1, AH_PLAN_INI))
    ah = lambda k: (f"'Ahorro'!${AH_RES_COL}${AH_CELDAS[k]}", f"'Práctica Ahorro'!${AH_RES_COL}${AH_CELDAS[k]}")

    def fuente(plantilla, **rangos):
        """Arma IF(cfgPractica, fórmula con hojas de práctica, fórmula con hojas reales)."""
        real = plantilla.format(**{k: v[0] for k, v in rangos.items()})
        prac = plantilla.format(**{k: v[1] for k, v in rangos.items()})
        return f'IF(cfgPractica,{prac},{real})'

    fila = [4]

    def valor(texto, formula, fmt=None, txt=None, ingreso=False, destacado=False):
        r = fila[0]
        a = ws.cell(row=r, column=1, value=texto)
        a.font = Font(name=FUENTE, size=12 if destacado else 11, bold=destacado)
        a.alignment = Alignment(wrap_text=True, vertical="center")
        if txt:
            nota(a, txt, alto=100)
        b = ws.cell(row=r, column=2, value=formula)
        b.font = Font(name=FUENTE, size=14 if destacado else 12, bold=True, color="0000FF" if ingreso else "000000")
        b.alignment = Alignment(horizontal="right", vertical="center", wrap_text=True)
        if ingreso:
            b.fill = AMARILLO
        if fmt:
            b.number_format = fmt
        if destacado:
            ws.row_dimensions[r].height = 22
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
        c = ws.cell(row=r, column=1, value=f'=IF(cfgPractica,"{texto}","")')
        c.font = Font(name=FUENTE, size=10, italic=True, color=NARANJA_TXT)
        c.alignment = Alignment(wrap_text=True, vertical="top")
        ws.row_dimensions[r].height = 15 * (1 + len(texto) // 45)
        cf_texto(ws, f"A{r}", 'cfgPractica', NARANJA_FILL, NARANJA_TXT)
        fila[0] += 1

    ws.merge_cells("A3:B3")
    ws.row_dimensions[3].height = 60
    r_estado = 3   # se llena al final, cuando existe el bloque de revisión
    fila[0] = 4

    # --- 1. Lo urgente: próximo pago y ahorro (según la fecha de hoy)
    seccion("Próximo pago del 13 y ahorro")
    tot_ah = valor("Total ahorrado", "=" + fuente("{s}", s=ah("saldo")), FMT_CLP,
                   "Lo que llevas ahorrado: agregado - quitado (detalle e historial en la hoja Ahorro).", destacado=True)
    cf_texto(ws, tot_ah, f"{tot_ah}<0", ROJO_FILL, ROJO_TXT)
    hoy = valor("Hoy", "=" + fuente("{h}", h=ah("hoy")), FMT_FECHA, "Fecha real del teléfono (o la de Config si la fijaste).")
    r_kn = fila[0]
    ws[f"C{r_kn}"] = "=" + fuente('IF(COUNTIF({n},">0")=0,0,_xlfn.MINIFS({k},{n},">0"))', k=plan("clave"), n=plan("necesita"))
    kn = f"$C${r_kn}"
    prox = valor("Próximo pago", "=" + fuente(f'IF({kn}=0,"Sin pagos pendientes",_xlfn.MINIFS({{f}},{{k}},{kn}))',
                                              f=plan("fecha"), k=plan("clave")), FMT_FECHA,
                 "Fecha del próximo pago de figuras (13 del mes o pago especial). En rojo si ya pasó y falta pagar.", destacado=True)
    cf_texto(ws, prox, f"AND(ISNUMBER({prox}),{prox}<{hoy})", ROJO_FILL, ROJO_TXT)
    a_pagar = valor("A pagar", "=" + fuente(f"SUMIFS({{n}},{{k}},{kn})", n=plan("necesita"), k=plan("clave")), FMT_CLP,
                    "Lo que falta pagar de las figuras de ese mes.", destacado=True)
    valor("Ahorrado para ese pago", "=" + fuente(f"SUMIFS({{a}},{{k}},{kn})", a=plan("asignado"), k=plan("clave")), FMT_CLP,
          "Parte del saldo ahorrado que cubre ese pago.")
    falta_p = valor("Falta juntar", "=" + fuente(f"SUMIFS({{f}},{{k}},{kn})", f=plan("falta"), k=plan("clave")), FMT_CLP,
                    "A pagar - ahorrado.", destacado=True)
    apartar = valor("Apartar este mes", "=" + fuente("INDEX({a},1)", a=plan("apartar")), FMT_CLP,
                    "Solo si hay un pago este mismo mes (todo lo que falta, ya). Los pagos de meses siguientes se juntan desde el mes que viene.",
                    destacado=True)
    valor("Apartar el próximo mes", "=" + fuente("INDEX({a},2)", a=plan("apartar")), FMT_CLP,
          "Suma de las cuotas iguales de todos los pagos que vienen. Detalle en la hoja Ahorro.")
    valor("Saldo extra", "=" + fuente("{s}", s=ah("extra")), FMT_CLP, "Lo que sobra después de cubrir todos los pagos que vienen.")
    atr = valor("Atrasado", "=" + fuente("{s}", s=ah("atrasado")), FMT_CLP, "Figuras de meses pasados con saldo por pagar.")
    cf_texto(ws, falta_p, f"{falta_p}>0", ROJO_FILL, ROJO_TXT)
    cf_texto(ws, falta_p, f"AND({falta_p}=0,{a_pagar}>0)", VERDE_FILL, VERDE_TXT)
    cf_texto(ws, apartar, f"{apartar}>0", NARANJA_FILL, NARANJA_TXT)
    cf_texto(ws, atr, f"{atr}>0", ROJO_FILL, ROJO_TXT)
    explicacion("El ahorro solo cubre figuras. Se mueve con Agregar/Quitar en la hoja (Práctica) Ahorro.")

    # --- 2. Mes elegido: reglas primero
    seccion("Mes a revisar")
    sel_mes = valor("Mes", None, txt="Elige el mes a revisar. Vacío = mes actual.", ingreso=True)
    sel_anio = valor("Año", None, fmt="0", txt="Elige el año. Vacío = año actual.", ingreso=True)
    lista_dv(ws, "=lstMeses", sel_mes, "Mes", "Elige el mes. Vacío = mes actual.")
    lista_dv(ws, "=lstAnios", sel_anio, "Año", "Elige el año. Vacío = año actual.")
    r_clave = fila[0]
    h = "IF(cfgPractica,cfgHoyPractica,cfgHoyReal)"
    mes_num = (f'IF(ISNUMBER({sel_mes}),IF(AND({sel_mes}>=1,{sel_mes}<=12),INT({sel_mes}),MONTH({h})),'
               f'IFERROR(MATCH(LEFT(LOWER(TRIM({sel_mes})),3),lstMeses,0),MONTH({h})))')
    anio = f'IF(AND(ISNUMBER({sel_anio}),{sel_anio}>=2000,{sel_anio}<=2100),INT({sel_anio}),YEAR({h}))'
    ws[f"C{r_clave}"] = f"={anio}*12+{mes_num}"
    wb.defined_names["pnlClave"] = DefinedName("pnlClave", attr_text=f"Panel!$C${r_clave}")
    valor("Mes seleccionado", "=" + etiqueta_mes("pnlClave"))

    tot_f = fuente("SUMIFS({t},{k},pnlClave)", t=comp("total"), k=comp("clave"))
    n_prod_f = ("IF(cfgContar=\"Filas\"," + fuente("COUNTIF({k},pnlClave)", k=comp("clave")) + "," +
                fuente('SUMIFS({c},{k},pnlClave)+COUNTIFS({k},pnlClave,{c},"")', c=comp("cant"), k=comp("clave")) + ")")
    occ = lambda d: fuente("COUNTIF({k},pnlClave" + d + ")", k=comp("clave"))
    seccion("Reglas del mes")
    gen_r = fila[0]
    fila[0] += 1
    alt = valor("Meses alternos",
                f'=IF({occ("")}=0,IF({occ("-1")}>0,"Sin compras. No disponible: "&{etiqueta_mes("pnlClave-1")}&" tiene compras",'
                f'IF({occ("+1")}>0,"Sin compras. No disponible: "&{etiqueta_mes("pnlClave+1")}&" tiene compras",'
                f'"Sin compras. Mes disponible")),'
                f'IF({occ("-1")}>0,"Mes bloqueado: el mes "&{etiqueta_mes("pnlClave-1")}&" ya tiene compras","OK"))',
                txt="El mes anterior y el siguiente a un mes con compras deben quedar sin compras de figuras. "
                    "La advertencia se muestra en el mes posterior.")
    limr_r = fila[0]
    fila[0] += 1
    # (Límite mensual y Estado general se escriben después, cuando existen las celdas de total y límite)
    explicacion("Rojo = regla rota con su motivo. Verde = OK. Envíos e impuestos nunca cuentan para las reglas.")

    seccion("Figuras del mes (sin envíos)")
    tot = valor("Total figuras", "=" + tot_f, FMT_CLP, "Suma de 'Total figura CLP' de los productos de este mes (sin cancelados).")
    valor("Pagado", "=" + fuente("SUMIFS({t},{k},pnlClave)", t=comp("pagfig"), k=comp("clave")), FMT_CLP,
          "Pagos tipo Figura de los productos de este mes.")
    valor("Falta pagar", "=" + fuente("SUMIFS({t},{k},pnlClave)", t=comp("falta"), k=comp("clave")), FMT_CLP,
          "Lo que queda por pagar de las figuras de este mes. Cerrados y cancelados no suman.")
    valor("Diferencia por tipo de cambio", "=" + fuente("SUMIFS({t},{k},pnlClave)", t=comp("dif"), k=comp("clave")), FMT_CLP,
          "Suma de 'Diferencia figura' de productos Pagados o Cerrados. Positivo = pagaste de más.")
    n_prod = valor("Productos en el mes", "=" + n_prod_f, "0", "Unidades o filas, según Config > Contar productos por.")
    lim = valor("Límite que aplica", f"=IF({n_prod}<=cfgMaxProdV,cfgLimExcV,cfgLimNormalV)", FMT_CLP,
                "Límite normal, o el de excepción si hay pocos productos.")
    valor("Margen disponible", f"={lim}-{tot}", FMT_CLP, "Límite - total. Negativo = excedido.")

    # ahora sí: Límite mensual y Estado general (arriba, en el bloque de reglas)
    ws.cell(row=limr_r, column=1, value="Límite mensual").font = Font(name=FUENTE, size=11)
    nota(ws.cell(row=limr_r, column=1), "Compara el total de figuras con el límite que aplica. Igual al tope = OK.")
    limr = f"B{limr_r}"
    ws[limr] = (f'=IF({tot}>{lim},"Límite superado en "&{pesos(tot + "-" + lim)}&IF({n_prod}<=cfgMaxProdV,'
                f'" (excepción hasta "&cfgMaxProdV&" productos: tope "&{pesos("cfgLimExcV")}&")",'
                f'" (regla normal, "&{n_prod}&" productos: tope "&{pesos("cfgLimNormalV")}&")"),"OK")')
    ws.cell(row=gen_r, column=1, value="Estado general").font = Font(name=FUENTE, size=12, bold=True)
    nota(ws.cell(row=gen_r, column=1), "OK si se cumplen ambas reglas; si no, ADVERTENCIA (ver motivo abajo).")
    gen = f"B{gen_r}"
    ws[gen] = f'=IF(AND(LEFT({alt},13)<>"Mes bloqueado",{limr}="OK"),"OK","ADVERTENCIA")'
    for celda in (alt, limr, gen):
        ws[celda].font = Font(name=FUENTE, size=11, bold=True)
        ws[celda].alignment = Alignment(horizontal="right", vertical="center", wrap_text=True)
        ws.row_dimensions[int(celda[1:])].height = 45
        ws.cell(row=int(celda[1:]), column=1).alignment = Alignment(wrap_text=True, vertical="center")
    ws.row_dimensions[gen_r].height = 24
    ws[gen].font = Font(name=FUENTE, size=14, bold=True)

    seccion("Envíos (no cuentan para las reglas)")
    valor("Pendientes de productos del mes", "=" + fuente("SUMIFS({e},{k},pnlClave)", e=comp("envpend"), k=comp("clave")),
          FMT_CLP, "Envíos aún sin pagar de los productos de este mes (individuales y acumulados).")
    valor("Pagados en el mes", "=" + fuente('SUMIFS({m},{t},"Env*",{k},pnlClave,{v},"OK")', m=pag("monto"), t=pag("tipo"), k=pag("clave"), v=pag("valid")),
          FMT_CLP, "Pagos tipo Envío válidos (Validación OK) con fecha dentro de este mes (individuales y conjuntos).")
    valor("Total sin pagar (todos los meses)", "=" + fuente("SUM({e})", e=comp("envpend")), FMT_CLP,
          "Todos los envíos pendientes o acumulados sin pagar, de cualquier mes.")
    ah_mes = valor("Ahorro de grupos pagados en el mes",
                   "=" + fuente("SUMIFS({a},{k},pnlClave)", a=grp("ahorro"), k=grp("clave")), FMT_CLP,
                   "Suma individual - pagado conjunto, de los grupos pagados este mes. Negativo = sobrecosto.")
    ah_tot = valor("Ahorro total de envíos conjuntos", "=" + fuente("SUM({a})", a=grp("ahorro")), FMT_CLP,
                   "Ahorro (o sobrecosto si es negativo) de todos los grupos pagados.")
    valor("Grupos en bodega sin pagar", "=" + fuente('COUNTIF({e},"Pendiente")', e=grp("estado")), "0",
          "Grupos de envío acumulado aún sin pago.")
    for celda in (ah_mes, ah_tot):
        cf_texto(ws, celda, f"{celda}<0", ROJO_FILL, ROJO_TXT)
        cf_texto(ws, celda, f"{celda}>0", VERDE_FILL, VERDE_TXT)
    explicacion("Un envío puede quedar en bodega aunque el producto esté pagado. Ahorro negativo (rojo) = pagaste de más.")

    seccion("Impuestos estimados (al llegar a Chile)")
    valor("De las figuras del mes", "=" + fuente("SUMIFS({i},{k},pnlClave)", i=comp("impuestos"), k=comp("clave")), FMT_CLP,
          "Arancel + IVA estimados de las figuras de este mes.")
    valor("A preparar por envíos pagados en el mes", "=" + fuente("SUMIFS({i},{k},pnlClave)", i=comp("impuestos"), k=comp("claveenv")),
          FMT_CLP, "Impuestos estimados de las figuras cuyo envío (o grupo) se pagó este mes: llegarán pronto a Chile.")
    valor("Arancel estimado (6%)", "=" + fuente("SUMIFS({i},{k},pnlClave)", i=comp("arancel"), k=comp("claveenv")),
          FMT_CLP, "Parte de arancel de la fila anterior.")
    valor("IVA estimado (19%)", "=" + fuente("SUMIFS({i},{k},pnlClave)", i=comp("iva"), k=comp("claveenv")),
          FMT_CLP, "Parte de IVA de la fila anterior.")
    explicacion("Estimación: arancel sobre figura + envío; IVA sobre figura + envío + arancel. No cuentan para las reglas.")

    # revisión del libro (salud de los datos)
    seccion("Revisión del libro")
    fin_c, fin_p, fin_g = FILA_INI + FILAS_COMPRAS - 1, FILA_INI + FILAS_PAGOS - 1, FILA_INI + FILAS_GRUPOS - 1
    abo = lambda col: (f"'Ahorro'!${col}${AH_MOV_INI}:${col}${AH_MOV_FIN}", f"'Práctica Ahorro'!${col}${AH_MOV_INI}:${col}${AH_MOV_FIN}")
    chequeos = [
        ("Config", '=SUMPRODUCT(--(LEFT(Config!$C$3:$C$13,2)<>"OK"))', "Parámetros vacíos o mal escritos (ver columna Revisar en Config)."),
        ("Compras con avisos de datos", "=" + fuente('COUNTIF({a},"?*")', a=comp("avisos")), "Filas con algo por revisar en 'Avisos de datos'."),
        ("Canceladas con pagos o grupo", "=" + fuente('COUNTIF({a},"Cancelada*")', a=comp("reglas")), "Reservas canceladas que aún tienen pagos o grupo (esos pagos no cuentan)."),
        ("Pagos con avisos", "=" + fuente('COUNTIF({a},"?*")', a=pag("aviso")), "Pagos que cuentan pero tienen algo raro (posible duplicado, se pasa del total, etc.)."),
        ("Pagos por revisar", "=" + fuente('COUNTIF({v},"?*")-COUNTIF({v},"OK")', v=pag("valid")), "Pagos cuya Validación no es OK: no cuentan en ningún total."),
        ("Movimientos de ahorro por revisar", "=" + fuente('COUNTIF({v},"?*")-COUNTIF({v},"OK")', v=abo(LA["revisar"])),
         "Movimientos con fecha, monto o Agregar/Quitar no válidos (no cuentan) o que dejan el ahorro en negativo."),
        ("IDs de grupo repetidos", "=" + fuente('COUNTIF({c},"ID de*")', c=grp("integ")),
         "Grupos con el mismo ID en Envíos acumulados: sus pagos no cuentan hasta corregirlo."),
        ("Fórmulas borradas", "=" + fuente('COUNTIF({a},"Fórm*")+COUNTIF({b},"Fórm*")+COUNTIF({c},"Fórm*")+COUNTIF({d},"Fórm*")',
                                         a=comp("integ"), b=pag("integ"), c=grp("integ"), d=plan("integ")),
         "Filas donde alguien escribió encima de una celda gris (calculada). Copia la fila de arriba para repararla."),
        ("Datos fuera de las filas preparadas", "=" + fuente(
            "COUNTA({c})+COUNTA({p})+COUNTA({a})",
            c=(f"'Compras'!$B${fin_c + 1}:$B${fin_c + 800}", f"'Práctica Compras'!$B${fin_c + 1}:$B${fin_c + 800}"),
            p=(f"'Pagos'!$A${fin_p + 1}:$A${fin_p + 800}", f"'Práctica Pagos'!$A${fin_p + 1}:$A${fin_p + 800}"),
            a=(f"'Ahorro'!$A${AH_MOV_FIN + 1}:$A${AH_MOV_FIN + 800}", f"'Práctica Ahorro'!$A${AH_MOV_FIN + 1}:$A${AH_MOV_FIN + 800}")),
         "Celdas escritas debajo de las filas preparadas: el libro no las cuenta."),
    ]
    celdas_rev = []
    for etiq, f, txt in chequeos:
        cel = valor(etiq, f, "0", txt)
        celdas_rev.append(cel)
        cf_texto(ws, cel, f"{cel}>0", ROJO_FILL, ROJO_TXT)
        cf_texto(ws, cel, f"{cel}=0", VERDE_FILL, VERDE_TXT)
    valor("Filas de Compras usadas", "=" + fuente('COUNTA({p})', p=comp("prod")) + f'&" de {FILAS_COMPRAS}"',
          txt="Cuántas figuras llevas anotadas de las filas preparadas.")
    total_rev = "+".join(celdas_rev)
    guia = ('"EMPIEZA AQUÍ: pon Modo práctica = No en Config y anota tu primera figura en Compras. '
            'Más ayuda en la hoja Ayuda."')
    ws[f"A{r_estado}"] = (f'=IF(AND(NOT(cfgPractica),COUNTA({rango("Compras", LC["prod"], FILAS_COMPRAS)})=0),{guia},'
                          f'IF(({total_rev})=0,"Revisión del libro: todo OK",'
                          f'"Revisión del libro: "&({total_rev})&" cosa(s) por revisar (ver final del Panel)"))')
    ws[f"A{r_estado}"].font = Font(name=FUENTE, size=11, bold=True)
    ws[f"A{r_estado}"].alignment = Alignment(wrap_text=True, vertical="center")
    cf_texto(ws, f"A{r_estado}", f'RIGHT(A{r_estado},7)="todo OK"', VERDE_FILL, VERDE_TXT)
    cf_texto(ws, f"A{r_estado}", f'LEFT(A{r_estado},7)="EMPIEZA"', AMARILLO, "1F4E78")
    cf_texto(ws, f"A{r_estado}", f'AND(RIGHT(A{r_estado},7)<>"todo OK",LEFT(A{r_estado},7)<>"EMPIEZA")', ROJO_FILL, ROJO_TXT)

    # casos de práctica
    fila[0] += 1
    r = fila[0]
    ws.merge_cells(f"A{r}:B{r}")
    ws.cell(row=r, column=1, value='=IF(cfgPractica,"Casos de práctica (con los valores por defecto de Config)","")').font = Font(
        name=FUENTE, size=12, bold=True, color=NARANJA_TXT)
    fila[0] += 1
    for caso in CASOS_PRACTICA:
        explicacion(caso)

    # formato condicional de reglas (rojo = advertencia, verde = OK)
    for celda in (alt, limr, gen):
        cf_texto(ws, celda, f'OR(LEFT({celda},13)="Mes bloqueado",LEFT({celda},7)="Límite ",{celda}="ADVERTENCIA")',
                 ROJO_FILL, ROJO_TXT)
        cf_texto(ws, celda, f'OR({celda}="OK",{celda}="Sin compras. Mes disponible")', VERDE_FILL, VERDE_TXT)
    ws.freeze_panes = "A4"
    return ws


AYUDA = [
    ("Cómo usar el libro", None),
    ("Registrar una reserva", "Compras → primera fila vacía: Producto, Fecha salida original, Moneda, Precio unitario y (si es USD/JPY) TC usado. Opcional: Fecha reserva, Link, Link imagen. Si tiene envío: monto, moneda y si queda en bodega (acumulado, con su grupo)."),
    ("Registrar un pago", "Pagos → Fecha, Monto CLP, Tipo (Figura/Envío) y Asociado a (elige el producto o grupo). La columna Validación debe decir OK."),
    ("Agregar o quitar ahorro", "Ahorro → primera fila vacía de la tabla: Fecha, Monto (positivo) y elige Agregar o Quitar. Cada fila queda en el historial con el 'Total después'. Arriba se ve el TOTAL AHORRADO. Si pagas una figura con lo ahorrado, registra un Quitar (salvo que en Config actives el descuento automático)."),
    ("Si se atrasa la salida", "No cambies la 'Fecha salida original'. Si quieres, anota la nueva en 'Salida actual' (solo informativa)."),
    ("Si cancelas una reserva", "Estado manual = Cancelada. Deja de contar en todo. Si quieres borrarla del todo, mantén presionada la fila y elige Eliminar: los IDs son fijos y nada se rompe."),
    ("Envío antes que la figura", "NO se puede: el pago de envío queda en rojo ('Primero paga la figura') y no cuenta hasta que la figura esté pagada. Tampoco puede tener fecha anterior al pago de la figura ni a su salida (la actual si se atrasó). Un grupo solo se puede pagar cuando todas sus figuras están pagadas."),
    ("Fechas futuras", "Un pago o movimiento de ahorro con fecha futura no cuenta hasta ese día."),
    ("Avisos (naranja)", "No bloquean: posible pago duplicado, pago anterior a la reserva, se pasa del total, monto distinto al envío, reserva después de la salida, Cerrada pagando menos del 90%, etc."),
    ("Pagos que se rechazan", "Un pago no cuenta si: tiene fecha futura, es un envío de una figura aún no pagada (o con fecha anterior a su pago), el producto está cancelado o vacío, el ID no existe o está repetido, es un envío a una figura sin envío o acumulada (se paga al grupo), es un pago de figura a un grupo, o el grupo no tiene figuras asignadas."),
    ("Si algo sale en rojo", "Lee el mensaje: dice qué revisar. Arriba del Panel, 'Revisión del libro' cuenta todo lo pendiente y al final del Panel está el detalle."),
    ("Solo cuenta lo válido", "Un pago o abono cuenta solo si su columna Validación/Revisar dice OK. Un pago a una figura Cancelada, a un ID que no existe o con monto mal escrito queda en rojo y NO se suma en ningún total (ni en el ahorro)."),
    ("Si escribiste encima de una celda gris", "La columna Integridad dice 'Fórmulas borradas'. Copia la fila de arriba (o de abajo) y pégala sobre esa fila; después vuelve a escribir tus datos amarillos."),
    ("Colores", "Encabezado azul + celda amarilla = lo escribes tú. Encabezado gris = calculado, no lo edites. Rojo = advertencia. Verde = OK. Naranja = aviso o diferencia. Gris tachado = cancelado."),
    ("Hojas", None),
    ("Panel", "Arriba: próximo pago del 13, cuánto tienes ahorrado, cuánto falta y cuánto apartar. Abajo: eliges mes y año y ves reglas, figuras, envíos e impuestos estimados de ese mes."),
    ("Compras", "Una fila por figura. Las columnas de la izquierda son las importantes; las de la derecha (gris claro) son auxiliares."),
    ("Pagos", "Un pago por fila, siempre en CLP. Los pagos de figura van al producto; los de envío al producto (si no es acumulado) o al grupo."),
    ("Envíos acumulados", "Un grupo por fila (G001...): figuras que esperan en bodega para enviarse juntas. Al pagar, registra el pago en Pagos con Asociado = grupo. Ahorro = suma de envíos individuales - pago conjunto. También estima los impuestos del paquete."),
    ("Ahorro", "Arriba el TOTAL AHORRADO y cuánto cubre del próximo pago; debajo la tabla Agregar/Quitar (historial). A la derecha el detalle y el plan de pagos del 13 (24 meses y 'Más adelante')."),
    ("Config", "Modo práctica, límites, excepción, forma de contar productos, día de pago (13), arancel e IVA estimados, fecha de hoy opcional y tipos de cambio."),
    ("Hojas Práctica", "Datos simulados, separados de los reales. Solo se ven y solo los usa el Panel cuando Modo práctica = Sí."),
    ("Reglas", None),
    ("Mes de la figura", "Es el mes de la Fecha salida original. Se paga el día 13 de ese mes (o en 'Pago especial'), aunque salga después del 13 o se atrase."),
    ("Solo figuras", "Las reglas solo miran el 'Total figura CLP'. Envíos, impuestos y sus pagos nunca cuentan para límites ni ocupan un mes."),
    ("Meses alternos", "Un mes con compras de figuras queda ocupado. El mes anterior y el siguiente deben quedar sin compras. Si hay compras en dos meses seguidos, el posterior muestra 'Mes bloqueado: el mes X ya tiene compras' (también entre diciembre y enero)."),
    ("Límite mensual", "Total de figuras del mes ≤ límite normal (200.000 por defecto). Si el mes tiene 2 productos o menos (por defecto), el tope es el de excepción (300.000 por defecto). Igual al tope = OK; más = rojo con el monto excedido."),
    ("Cerrar con diferencia", "Estado manual = Cerrada: el producto queda Cerrado aunque lo pagado no calce. La diferencia se ve en el producto y se suma en el Panel."),
    ("Envíos independientes", "El envío tiene su propio estado (Sin envío / Pendiente / Acumulado / Pagado) y sus propios pagos."),
    ("Tipo de cambio fijo", "Escribe el TC del día en 'TC usado': queda guardado y no cambia si editas la tabla de Config. Si lo dejas vacío se usa el sugerido y aparece 'TC no fijado'."),
    ("Impuestos estimados", "Arancel = 6% x (figura + envío). IVA = 19% x (figura + envío + arancel). Envío: el individual, o la parte del pago conjunto si el grupo ya se pagó. Es una referencia: las reglas de Aduanas pueden cambiar."),
    ("Cálculo del ahorro", "Total = agregado - quitado (menos los pagos de figuras solo si activas 'Descontar pagos del ahorro' en Config). Llena primero el pago más cercano y lo que sobra pasa al siguiente. Lo que falta se reparte en partes iguales desde el mes siguiente al actual hasta el mes del pago; si el pago es este mes, se aparta todo ya."),
    ("Campos de Compras", None),
] + [(t, n) for _, t, _, _, n in COLS_COMPRAS] + [("Campos de Pagos", None)] + [
    (t, n) for _, t, _, _, n in COLS_PAGOS] + [("Campos de Envíos acumulados", None)] + [
    (t, n) for _, t, _, _, n in COLS_GRUPOS] + [("Campos de Ahorro", None)] + [
    (t, n) for _, t, _, _, n in COLS_MOV] + [(t, n) for _, t, _, n in COLS_PLAN] + [
    ("Supuestos", None),
    ("Productos para la excepción", "Por defecto se cuentan unidades (columna Cantidad). Cámbialo a Filas en Config si prefieres contar cada producto distinto como 1."),
    ("Mes bloqueado", "Como no se registra el orden en que anotaste las compras, se marca siempre el mes posterior de cada par contiguo."),
    ("Atrasados", "Figuras de meses pasados que no están pagadas aparecen como 'Atrasado' y no entran en el plan de ahorro."),
    ("Escritura tolerante", "Se aceptan 'si'/'Sí', 'envio'/'Envío', 'cancelado'/'Cancelada', 'c3' o '3' como C003, y el mes del Panel como 'jul', 'Julio' o 7. Arancel/IVA escritos como 6 o 19 se interpretan como porcentaje."),
    ("Config vacía", "Si un parámetro queda vacío o mal escrito se usa su valor por defecto (200.000, 300.000, 2, día 13, 6%, 19%) y la columna Revisar de Config lo avisa."),
    ("Foto", "Usa la función IMAGEN de Microsoft 365, que descarga la foto desde internet. Si tu Excel no la tiene, deja vacío 'Link imagen'."),
    ("Filas preparadas", f"Compras tiene {FILAS_COMPRAS} filas, Pagos {FILAS_PAGOS}, Envíos {FILAS_GRUPOS} y Abonos {FILAS_ABONOS}, con fórmulas listas (las hojas de práctica tienen menos). Escribe en la primera fila vacía; no insertes filas en medio."),
    ("Orden de Compras", "Primero lo que escribes al registrar: Producto, Foto, Fecha salida original, Moneda, Precio, Cantidad, TC usado. Luego el resultado (Total, Reglas, Estado), el envío, los pagos y al final los datos informativos y auxiliares."),
    ("Impuestos sin envío", "Si una figura no tiene costo de envío, sus impuestos estimados se muestran en 'A preparar' del mes de su salida."),
]


def hoja_ayuda(wb):
    """Crea la hoja Ayuda con la explicación de cada hoja, regla y campo."""
    ws = wb.create_sheet("Ayuda")
    ws.column_dimensions["A"].width = 22
    ws.column_dimensions["B"].width = 60
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


# ---------------------------------------------------------------- legibilidad (zoom de 50% a 400%)
RUTA_FUENTES = "/usr/share/fonts/truetype/liberation/"   # Liberation Sans = mismas medidas que Arial
ZOOMS_DISENO = (0.5, 0.6, 0.75, 0.85, 0.9, 1.0, 1.1, 1.15, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0, 4.0)
MARGEN_SEGURIDAD = 1.2      # 20% de espacio extra sobre lo que el texto necesita en el peor zoom
MARGEN_ANCHO_TEXTO = 1.15   # las líneas se calculan con 15% menos de ancho (por si el texto crece)
MAX_LINEAS_TABLA = 3        # una fila de tabla no debe pasar de 3 líneas de texto (se ensancha la columna)
_fuentes_cache = {}

# Texto más largo esperado en celdas calculadas (por encabezado de columna), para reservar su espacio.
MUESTRA_TEXTO = {
    "Reglas": "Mes bloqueado: el mes sep-2027 ya tiene compras | Límite superado en $999.999.999 (regla normal, 99 productos: tope $999.999.999)",
    "Avisos de datos": "TC no fijado (usa sugerido) | Envío pagado de más (¿dos veces?) | Reserva después de la salida",
    "Aviso bloqueo": "Mes bloqueado: el mes sep-2027 ya tiene compras",
    "Aviso límite": "Límite superado en $999.999.999 (regla normal, 99 productos: tope $999.999.999)",
    "Estado producto": "Cancelado", "Estado envío": "Acumulado", "Estado grupo": "Pendiente",
    "Etiqueta lista": "C200 · Figura Nendoroid Hatsune Miku 2.0",
    "Integridad": "Fórmulas borradas: copia la fila de arriba",
    "Validación": "El envío no puede ser antes de la salida de sus figuras",
    "Validación base": "Monto no válido (número mayor a 0)",
    "Avisos": "Con este pago se pasa del total de la figura | Posible pago duplicado",
    "Revisar": "Monto no válido (escribe un número positivo)",
    "Mes": "Más adelante", "ID": "C200", "Grupo": "G050",
    # entradas de texto: espacio para un valor típico largo
    "Producto": "Figura Nendoroid Hatsune Miku Ver. 2.0", "Tienda": "Tienda EE.UU. Online",
    "Link": "https://www.nin-nin-game.com/es/figuras/12345", "Link imagen": "https://imagen.tienda.com/12345.jpg",
    "Nota": "Pago con tarjeta en cuotas", "Descripción": "Bodega Nin Nin dic-feb",
    "Asociado a": "C200 · Figura Nendoroid Hatsune Miku", "Agregar o Quitar": "Agregar", "Moneda": "JPY",
    "Envío moneda": "JPY", "Envío acumulado": "No", "Estado manual": "Cancelada", "Tipo": "Figura", "Cerrar": "Sí",
}
# Texto más largo esperado en el Panel (columna B), por etiqueta de la columna A.
MUESTRA_PANEL = {
    "Mes seleccionado": "sep-2027", "Próximo pago": "Sin pagos pendientes",
    "Meses alternos": "Sin compras. No disponible: sep-2027 tiene compras",
    "Límite mensual": "Límite superado en $999.999.999 (excepción hasta 99 productos: tope $999.999.999)",
    "Estado general": "ADVERTENCIA", "Filas de Compras usadas": "200 de 200",
}
# Valor numérico más ancho esperado según el formato de la celda.
MUESTRA_NUMERO = [('"$"', "-$999.999.999"), ("%", "100,0%"), ("dd-mm", "00-00-0000"), ("mmm", "sep-2027"),
                  ("#,##0.00", "9.999,99"), ("#,##0.##", "99.999,99")]


def _fuente_px(px, negrita, cursiva):
    from PIL import ImageFont
    nombre = {(False, False): "Regular", (True, False): "Bold", (False, True): "Italic", (True, True): "BoldItalic"}[(negrita, cursiva)]
    k = (px, negrita, cursiva)
    if k not in _fuentes_cache:
        _fuentes_cache[k] = ImageFont.truetype(f"{RUTA_FUENTES}LiberationSans-{nombre}.ttf", px)
    return _fuentes_cache[k]


def medir_px(texto, tam=11, negrita=False, cursiva=False):
    """Ancho que necesita el texto a zoom 100% considerando el peor redondeo de todos los zooms (+ margen interno)."""
    peor = 0
    for z in ZOOMS_DISENO:
        px = max(1, round(tam * 96 / 72 * z))
        w = _fuente_px(px, negrita, cursiva).getlength(texto) + math.ceil(6 * z) + 1
        peor = max(peor, w / z)
    return peor


def alto_linea_pt(tam=11):
    """Alto de una línea de texto en puntos, en el peor zoom."""
    peor = 0
    for z in ZOOMS_DISENO:
        px = max(1, round(tam * 96 / 72 * z))
        peor = max(peor, (math.ceil(px * 1.17) + math.ceil(2 * z) + 1) / z)
    return peor * 72 / 96


def lineas_necesarias(texto, ancho_px, tam=11, negrita=False, cursiva=False):
    """Líneas que ocupa el texto ajustado al ancho (en el peor zoom)."""
    peor = 1
    for z in ZOOMS_DISENO:
        px = max(1, round(tam * 96 / 72 * z))
        f = _fuente_px(px, negrita, cursiva)
        disponible = math.floor(ancho_px * z) - math.ceil(6 * z)
        n = 0
        for parrafo in str(texto).split("\n"):
            n += 1; actual = ""
            for p in parrafo.split(" "):
                prueba = (actual + " " + p) if actual else p
                if f.getlength(prueba) <= disponible:
                    actual = prueba; continue
                if actual: n += 1
                while f.getlength(p) > disponible and len(p) > 1:
                    k = len(p)
                    while k > 1 and f.getlength(p[:k]) > disponible: k -= 1
                    p = p[k:]; n += 1
                actual = p
        peor = max(peor, n)
    return peor


def _px_de_ancho(w): return math.trunc(w * 7 + 5)
def _ancho_de_px(px): return (px - 5) / 7


def _muestra(ws, celda, encabezados, etiquetas_panel):
    """Texto más largo que puede mostrar una celda (literal, o muestra si es fórmula)."""
    v = celda.value
    if v is None:   # celda de ingreso aún vacía: reservar espacio según su formato o su columna
        fmt = celda.number_format or ""
        for clave, ejemplo in MUESTRA_NUMERO:
            if clave in fmt: return ejemplo, True
        enc = encabezados.get(celda.column)
        if enc in MUESTRA_TEXTO: return MUESTRA_TEXTO[enc], False
        if fmt not in ("General", ""): return "-999.999.999", True
        return None, False
    if isinstance(v, (int, float)) or hasattr(v, "year"):
        for clave, ejemplo in MUESTRA_NUMERO:
            if clave in (celda.number_format or ""): return ejemplo, True
        return "-999.999.999", True
    v = str(v)
    if not v.startswith("="): return v, False
    enc = encabezados.get(celda.column)
    if ws.title.endswith("Panel") and celda.column == 2:
        etiqueta = ws.cell(row=celda.row, column=1).value
        if etiqueta in MUESTRA_PANEL: return MUESTRA_PANEL[etiqueta], False
    if enc in MUESTRA_TEXTO: return MUESTRA_TEXTO[enc], False
    fmt = celda.number_format or ""
    for clave, ejemplo in MUESTRA_NUMERO:
        if clave in fmt: return ejemplo, True
    literales = [m.group(1).replace('""', '"') for m in re.finditer(r'"((?:[^"]|"")*)"', v)]
    literales = [x for x in literales if len(x) >= 3 and not x.startswith(("<", ">", "Fig*", "Env*", "S*", "?*"))]
    if literales: return max(literales, key=len), False
    return "-999.999.999", True


def ensanchar_titulos(ws, filas=(1, 3), hasta_col=8):
    """Combina títulos y notas sueltas de la columna A con las columnas vecinas vacías, para que tengan espacio."""
    ocupadas = {(mr.min_row, mr.min_col) for mr in ws.merged_cells.ranges} | \
               {(r, c) for mr in ws.merged_cells.ranges for r in range(mr.min_row, mr.max_row + 1) for c in range(mr.min_col, mr.max_col + 1)}
    for r in filas:
        c = ws.cell(row=r, column=1)
        if c.value is None or (r, 1) in ocupadas: continue
        fin = 1
        while fin < hasta_col and ws.cell(row=r, column=fin + 1).value is None and (r, fin + 1) not in ocupadas:
            fin += 1
        if fin > 1:
            ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=fin)


def asegurar_legibilidad(wb):
    """Da a cada celda espacio fijo con 20% de margen para que se lea bien con cualquier zoom (50% a 400%)."""
    for ws in wb.worksheets:
        if ws.sheet_state != "visible":
            continue
        ensanchar_titulos(ws)
        fusion = {}
        dentro = set()
        for mr in ws.merged_cells.ranges:
            fusion[(mr.min_row, mr.min_col)] = mr
            for r in range(mr.min_row, mr.max_row + 1):
                for c in range(mr.min_col, mr.max_col + 1):
                    if (r, c) != (mr.min_row, mr.min_col): dentro.add((r, c))
        # encabezados de tablas (fila de cabecera de cada tabla)
        encabezados = {}
        for t in ws.tables.values():
            ini = t.ref.split(":")[0]
            fila = int(re.sub(r"[A-Z]", "", ini))
            for c in ws[fila]:
                if isinstance(c.value, str) and c.value: encabezados.setdefault(c.column, c.value)
        for c in ws[g_fila_plan(ws)] if g_fila_plan(ws) else []:
            if isinstance(c.value, str) and c.value: encabezados.setdefault(c.column, c.value)
        filas_enc = {int(re.sub(r"[A-Z]", "", t.ref.split(":")[0])) for t in ws.tables.values()}
        if g_fila_plan(ws): filas_enc.add(g_fila_plan(ws))
        en_tabla = set()
        for t in ws.tables.values():
            a, b = t.ref.split(":")
            for fila in ws[a:b][1:]:
                for c in fila: en_tabla.add(c.coordinate)
        celdas = [c for fila in ws.iter_rows() for c in fila
                  if (c.value is not None or c.coordinate in en_tabla) and (c.row, c.column) not in dentro]
        # 1) anchos: números y palabras largas deben caber en una línea; encabezados en máximo 3 líneas
        necesidad = {}
        for c in celdas:
            if (c.row, c.column) in fusion: continue
            texto, es_num = _muestra(ws, c, encabezados, None)
            if texto is None: continue
            f = c.font; tam = f.sz or 11
            if es_num:
                px = medir_px(texto, tam, bool(f.b), bool(f.i))
            else:
                palabra = max(str(texto).split(" "), key=len) if str(texto).strip() else ""
                if palabra.startswith("http") or len(palabra) > 30: palabra = palabra[:18]
                px = medir_px(palabra, tam, bool(f.b), bool(f.i))
                if c.row in filas_enc:   # encabezado: que quepa en 3 líneas como máximo
                    px = max(px, medir_px(str(texto), tam, bool(f.b), bool(f.i)) / 3)
            necesidad[c.column_letter] = max(necesidad.get(c.column_letter, 0), px * MARGEN_SEGURIDAD)
        for col, px in necesidad.items():
            d = ws.column_dimensions[col]
            if d.hidden: continue
            actual = d.width or 8.43
            d.width = round(max(actual, _ancho_de_px(px)), 1)
        # textos de tablas: ancho suficiente para quedar en MAX_LINEAS_TABLA líneas
        for c in celdas:
            if c.coordinate not in en_tabla or (c.row, c.column) in fusion: continue
            texto, es_num = _muestra(ws, c, encabezados, None)
            if texto is None or es_num: continue
            d = ws.column_dimensions[c.column_letter]
            if d.hidden: continue
            f = c.font; tam = f.sz or 11
            while lineas_necesarias(texto, _px_de_ancho(d.width or 8.43) / MARGEN_ANCHO_TEXTO, tam, bool(f.b), bool(f.i)) > MAX_LINEAS_TABLA:
                d.width = round((d.width or 8.43) + 2, 1)
        # 2) alto de cada fila según la celda que más líneas necesita (texto siempre ajustado)
        alto = {}
        ocultas = {k for k, d in ws.column_dimensions.items() if d.hidden}
        for c in celdas:
            if c.column_letter in ocultas: continue
            texto, es_num = _muestra(ws, c, encabezados, None)
            if texto is None: continue
            f = c.font; tam = f.sz or 11
            mr = fusion.get((c.row, c.column))
            cols = range(mr.min_col, mr.max_col + 1) if mr else [c.column]
            filas = list(range(mr.min_row, mr.max_row + 1)) if mr else [c.row]
            ancho = sum(_px_de_ancho(ws.column_dimensions[get_column_letter(k)].width or 8.43) for k in cols)
            if not es_num:
                al = c.alignment
                c.alignment = Alignment(horizontal=al.horizontal, vertical=al.vertical or "center", wrap_text=True,
                                        indent=al.indent)
                n = lineas_necesarias(texto, ancho / MARGEN_ANCHO_TEXTO, tam, bool(f.b), bool(f.i))
            else:
                n = 1
            pt = n * alto_linea_pt(tam) * MARGEN_SEGURIDAD / len(filas)
            for r in filas:
                alto[r] = max(alto.get(r, 0), pt)
        for r, pt in alto.items():
            d = ws.row_dimensions[r]
            d.height = round(max(d.height or 0, pt, 18), 1)


def g_fila_plan(ws):
    """Fila del encabezado del plan de ahorro (si la hoja es de Ahorro)."""
    return AH_PLAN_ENC if ws.title.endswith("Ahorro") else None


def construir(ruta):
    """Arma el libro completo y lo guarda en 'ruta'."""
    wb = Workbook()
    # fuente base del libro = la misma de las celdas (Arial 11): los anchos de columna se miden con ella
    from openpyxl.utils.indexed_list import IndexedList
    wb._fonts = IndexedList([Font(name=FUENTE, sz=11, family=2)])
    wb.active.title = "Panel"
    hoja_compras(wb, "Compras", "Pagos", "Envíos acumulados", "tblCompras")
    hoja_pagos(wb, "Pagos", "Compras", "Envíos acumulados", "tblPagos")
    hoja_grupos(wb, "Envíos acumulados", "Compras", "Pagos", "tblEnvios")
    hoja_ahorro(wb, "Ahorro", "Compras", "Pagos", "tblAbonos")
    hoja_config(wb)
    hoja_ayuda(wb)
    hoja_compras(wb, "Práctica Compras", "Práctica Pagos", "Práctica Envíos", "tblPracticaCompras", practica=True, demo=DEMO_COMPRAS)
    hoja_pagos(wb, "Práctica Pagos", "Práctica Compras", "Práctica Envíos", "tblPracticaPagos",
               practica=True, demo=DEMO_PAGOS)
    hoja_grupos(wb, "Práctica Envíos", "Práctica Compras", "Práctica Pagos", "tblPracticaEnvios",
                practica=True, demo=DEMO_GRUPOS)
    hoja_ahorro(wb, "Práctica Ahorro", "Práctica Compras", "Práctica Pagos", "tblPracticaAbonos",
                practica=True, demo=DEMO_MOV)
    hoja_listas(wb)
    hoja_panel(wb)
    colores = {"Panel": "1F4E78", "Compras": "2E75B6", "Pagos": "2E75B6", "Envíos acumulados": "2E75B6",
               "Ahorro": "2E75B6", "Config": "7F7F7F", "Ayuda": "7F7F7F", "Práctica Compras": "C55A11",
               "Práctica Pagos": "C55A11", "Práctica Envíos": "C55A11", "Práctica Ahorro": "C55A11"}
    for n, col in colores.items():
        wb[n].sheet_properties.tabColor = col
    asegurar_legibilidad(wb)
    wb.active = 0
    wb.save(ruta)


if __name__ == "__main__":
    construir(sys.argv[1] if len(sys.argv) > 1 else "excel/Figuras.xlsx")
