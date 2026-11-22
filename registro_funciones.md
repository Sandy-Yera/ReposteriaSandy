# Registro de funciones y variables

Ver `INSTRUCCIONES_REGISTRO_FUNCIONES.md` para las reglas de uso de este archivo.
Cada entrada nueva va al final, con el mismo formato.

<!--
### nombreDeLaFuncionOVariable
- Ubicación: ruta/al/archivo.kt
- Qué hace: ...
- Cómo funciona: ...
-->

---

# excel-Figuras

Sección exclusiva de la rama `excel-Figuras` (libro `excel/Figuras.xlsx` y su generador `excel/generar_figuras.py`).
No mezcla ni modifica las entradas de arriba.

## Hojas

### Panel
- Ubicación: excel/Figuras.xlsx (hoja Panel)
- Qué hace: Hoja principal. Arriba: próximo pago del 13 y ahorro (según hoy). Abajo: reglas, figuras, envíos e impuestos del mes elegido.
- Cómo funciona: Selector Mes/Año. Lee las hojas Ahorro, Compras, Pagos y Envíos acumulados. Cada fórmula usa IF(cfgModo="Sí", hojas de práctica, hojas reales). Columna C oculta con claves auxiliares.

### Compras
- Ubicación: excel/Figuras.xlsx (hoja Compras, tabla tblCompras)
- Qué hace: Una fila por figura real (200 filas listas, IDs C001-C200).
- Cómo funciona: El mes lo define 'Fecha salida original'; se paga el día 13 (cfgDiaPago) o en 'Pago especial'. Estado manual Activa/Cerrada/Cancelada. Calcula total, reglas, estados, envíos, impuestos estimados y foto (IMAGE).

### Pagos
- Ubicación: excel/Figuras.xlsx (hoja Pagos, tabla tblPagos)
- Qué hace: Registro de cada pago real en CLP (300 filas).
- Cómo funciona: Ingreso: Fecha, Monto, Tipo, Asociado a, Nota. Calcula ID, clave de mes y Validación (incluye aviso si el producto está cancelado).

### Envíos acumulados
- Ubicación: excel/Figuras.xlsx (hoja Envíos acumulados, tabla tblEnvios)
- Qué hace: Grupos de figuras en bodega que se envían juntas (G001-G050).
- Cómo funciona: Calcula N envíos, suma individual, pagado conjunto, fecha, estado, ahorro e impuestos estimados del grupo. Excluye figuras canceladas.

### Ahorro
- Ubicación: excel/Figuras.xlsx (hoja Ahorro, tabla tblAbonos)
- Qué hace: Resumen del ahorro, plan de pagos del 13 (24 meses + 'Más adelante') y tabla de abonos.
- Cómo funciona: Saldo = abonos - pagos de figuras desde el primer abono. El saldo llena en orden los meses del plan; lo que falta se reparte en cuotas iguales desde el mes siguiente hasta el mes del pago.

### Config
- Ubicación: excel/Figuras.xlsx (hoja Config, tabla tblTC)
- Qué hace: Parámetros editables y tabla de tipos de cambio.
- Cómo funciona: B3 modo práctica, B4-B5 límites, B6 máx. productos, B7 contar por, B8 día de pago (13), B9 arancel, B10 IVA, B11 hoy opcional, B12 hoy de práctica. tblTC en A16:C216.

### Ayuda
- Ubicación: excel/Figuras.xlsx (hoja Ayuda)
- Qué hace: Explicación de cada hoja, regla, campo y supuesto.
- Cómo funciona: Texto fijo generado desde AYUDA y las notas de las columnas.

### Práctica Compras / Práctica Pagos / Práctica Envíos / Práctica Ahorro
- Ubicación: excel/Figuras.xlsx (tablas tblPracticaCompras, tblPracticaPagos, tblPracticaEnvios, tblPracticaAbonos)
- Qué hace: Copias de estructura con datos simulados y columna 'Explicación (práctica)'.
- Cómo funciona: Mismas fórmulas que las hojas reales, enlazadas entre sí; el plan de ahorro usa cfgHoyPractica. Con Modo práctica = No se ocultan con formato condicional.

### Listas
- Ubicación: excel/Figuras.xlsx (hoja oculta Listas)
- Qué hace: Opciones de las listas desplegables.
- Cómo funciona: Col. A/B: lista compacta 'ID · nombre' de productos y grupos (real/práctica). Col. E-J: Sí/No, monedas, tipos de pago, meses, años, estado manual.

## Nombres definidos

### cfgModo
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B3: Sí/No del modo práctica.

### cfgLimNormal
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B4: límite mensual normal (200.000).

### cfgLimExc
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B5: límite con excepción (300.000).

### cfgMaxProd
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B6: máximo de productos para la excepción (2).

### cfgContar
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B7: Unidades o Filas.

### cfgDiaPago
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B8: día de pago del mes (13).

### cfgArancel
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B9: arancel estimado (6%).

### cfgIVA
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B10: IVA estimado (19%).

### cfgHoy
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B11: fecha de hoy opcional (vacía = fecha real).

### cfgHoyReal
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Fórmula: Config!B11 si tiene fecha; si no, HOY().

### cfgHoyPractica
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B12: fecha fija de hoy para los datos de práctica (01-02-2027).

### cfgTCFecha / cfgTCMoneda / cfgTCValor
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Columnas A, B y C de tblTC (filas 17-216).

### lstSiNo / lstMonedas / lstTipoPago / lstMeses / lstAnios / lstManual
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Rangos fijos de Listas usados por las validaciones.

### lstAsociado / lstAsociadoP
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: OFFSET sobre Listas!A2 o B2 con largo = productos con nombre + grupos usados; lista de 'Asociado a'.

### lstGrupos / lstGruposP
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: IDs G001-G050; lista de 'Grupo envío'.

### pnlClave
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Panel (columna C oculta): clave año*12+mes del mes elegido.

## Columnas calculadas (fórmulas por fila)

### Compras: Foto
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: IMAGE(Link imagen) si hay link; vacío si no. Requiere Microsoft 365 y descarga la foto.

### Compras: Fecha pago
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Pago especial si existe; si no, día cfgDiaPago del mes de salida original (último día si el mes es más corto).

### Compras: Mes efectivo / Clave mes
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Primer día del mes de la Fecha salida original (vacío si Cancelada); clave = AÑO*12+MES.

### Compras: TC sugerido / TC aplicado
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: MAXIFS da la última fecha de tblTC <= Fecha pago; AVERAGEIFS trae su valor. Aplicado = 1 si CLP, TC usado si existe, si no el sugerido.

### Compras: Total figura CLP
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: ROUND(cantidad (vacía=1) x precio x TC aplicado, 0).

### Compras: Envío CLP
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Monto de envío x TC (1 si CLP; TC aplicado si es la moneda del producto; si no, sugerido a la Fecha pago). 0 si Cancelada.

### Compras: Pagado figura / Pagado envío
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: SUMIFS en Pagos por ID y Tipo.

### Compras: Estado producto
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Cancelado / Cerrado según Estado manual; si no, Pendiente, Parcial o Pagado según pagos.

### Compras: Falta pagar / Diferencia figura
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Falta = MAX(0, total - pagado), 0 si Cerrado o Cancelado. Diferencia = pagado - total solo si Pagado o Cerrado.

### Compras: Estado envío / Envío por pagar
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Sin envío / Pendiente / Acumulado / Pagado; por pagar según ese estado.

### Compras: Envío para impuesto
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Envío CLP; si el grupo está pagado, Envío CLP x pagado del grupo / suma individual del grupo (sin canceladas).

### Compras: Arancel est. / IVA est. / Impuestos est.
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Arancel = ROUND((total + envío p/ impuesto) x cfgArancel); IVA = ROUND((total + envío + arancel) x cfgIVA); suma. Vacío si Cancelado.

### Compras: Clave mes pago envío
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Mes del último pago tipo Envío al ID (individual) o al grupo (acumulado), solo si el envío está Pagado.

### Compras: Total figuras mes / Productos mes / Límite mes
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: SUMIFS por clave; unidades o filas según cfgContar; límite según cfgMaxProd.

### Compras: Reglas
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Cancelada: aviso si tiene pagos o grupo. Si no, TEXTJOIN de 'Mes bloqueado...' y 'Límite superado...'; vacío = OK.

### Compras: Avisos de datos
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: TEXTJOIN de faltantes: fecha salida, moneda, precio, TC, grupo, TC del envío.

### Compras: N lista / Etiqueta lista
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Correlativo y texto 'ID · Producto' para la lista de Pagos.

### Pagos: ID / Clave mes pago / Validación
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: ID = MAYUSC(IZQUIERDA(Asociado,4)); clave del mes de pago; Validación revisa datos, ID, producto vacío o cancelado, pagos de figura a grupos y envíos acumulados.

### Envíos acumulados: N envíos / Suma / Pagado / Fecha / Estado / Ahorro / Impuestos est. grupo
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: COUNTIFS/SUMIFS sobre Compras (grupo, acumulado Sí, no cancelada) y SUMIFS/MAXIFS sobre Pagos (Envío, grupo). Ahorro = suma - pagado si Pagado.

### Ahorro: resumen
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Hoy, total abonado, pagos de figuras desde el primer abono (SUMIFS con fecha >= MIN de abonos), saldo, saldo extra, atrasado (falta de meses < mes actual) y clave del mes actual.

### Ahorro: plan (A pagar / Primer pago / Ahorrado / Falta juntar / Meses / Cuota / Apartar)
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: A pagar = SUMIFS de Falta pagar por clave; Ahorrado = MIN(a pagar, MAX(0, saldo - suma de meses anteriores)); Meses = clave - clave actual; Cuota = falta (si meses <= 0) o ROUNDUP(falta/meses); Apartar = cuota del mes actual o suma de cuotas de pagos con clave >= ese mes.

### Panel: próximo pago y ahorro
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Clave del próximo mes con pagos = MINIFS sobre el plan; trae fecha, a pagar, ahorrado, falta, apartar este mes y el próximo, saldo, saldo extra y atrasado.

### Panel: Estado general / Meses alternos / Límite mensual
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: COUNTIF de la clave del mes, anterior y siguiente; total vs. límite; ADVERTENCIA si falla alguna. Rojo/verde.

### Panel: envíos e impuestos
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Pendientes del mes, pagados en el mes, total sin pagar, ahorro de grupos; impuestos de figuras del mes y de envíos pagados en el mes (con arancel e IVA por separado).

## Funciones del generador (excel/generar_figuras.py)

### letras
- Ubicación: excel/generar_figuras.py
- Qué hace: Mapa clave de columna → letra.
- Cómo funciona: Recibe la lista de columnas; retorna dict.

### q
- Ubicación: excel/generar_figuras.py
- Qué hace: Pone comillas simples a un nombre de hoja.
- Cómo funciona: Recibe texto; retorna "'hoja'".

### rango
- Ubicación: excel/generar_figuras.py
- Qué hace: Rango absoluto de una columna de datos.
- Cómo funciona: Recibe hoja, letra, n° de filas y fila inicial; retorna texto tipo 'Compras'!$D$5:$D$204.

### etiqueta_mes
- Ubicación: excel/generar_figuras.py
- Qué hace: Fórmula que convierte una clave de mes en 'mmm-aaaa'.
- Cómo funciona: CHOOSE/MOD/INT, sin depender del idioma.

### pesos
- Ubicación: excel/generar_figuras.py
- Qué hace: Fórmula de texto '$1.234'.
- Cómo funciona: FIXED(x,0) respeta el separador de miles del teléfono.

### fecha_dia_pago
- Ubicación: excel/generar_figuras.py
- Qué hace: Fórmula de la fecha de pago de un mes.
- Cómo funciona: DATE(año, mes, MIN(cfgDiaPago, último día del mes)).

### estilo_titulo
- Ubicación: excel/generar_figuras.py
- Qué hace: Da formato de título a una celda.
- Cómo funciona: Modifica la fuente.

### nota
- Ubicación: excel/generar_figuras.py
- Qué hace: Agrega un comentario a una celda.
- Cómo funciona: Crea Comment con ancho y alto.

### encabezado
- Ubicación: excel/generar_figuras.py
- Qué hace: Escribe encabezados con color, nota y ancho.
- Cómo funciona: Azul = ingreso, gris = calculado.

### formato_columna
- Ubicación: excel/generar_figuras.py
- Qué hace: Formatea las filas de datos de una columna.
- Cómo funciona: Fuente, formato numérico, relleno amarillo y ajuste de texto; acepta fila inicial.

### fuente_auxiliar
- Ubicación: excel/generar_figuras.py
- Qué hace: Fuente gris pequeña para columnas auxiliares.
- Cómo funciona: Modifica la fuente de las filas.

### agregar_tabla
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea una tabla de Excel con filtros.
- Cómo funciona: Recibe hoja, nombre, última columna, filas y fila de encabezado.

### lista_dv / num_dv / fecha_dv
- Ubicación: excel/generar_figuras.py
- Qué hace: Validaciones (lista, número, fecha) con mensaje de ayuda.
- Cómo funciona: Crean DataValidation y la aplican al rango.

### cf_texto
- Ubicación: excel/generar_figuras.py
- Qué hace: Formato condicional por fórmula.
- Cómo funciona: Relleno + texto en negrita, o tachado.

### banner
- Ubicación: excel/generar_figuras.py
- Qué hace: Fila 2 con aviso del modo práctica.
- Cómo funciona: Fórmula según cfgModo; naranja cuando está activo.

### ocultar_si_no_practica
- Ubicación: excel/generar_figuras.py
- Qué hace: Oculta datos de práctica cuando el modo está en No.
- Cómo funciona: Formato condicional blanco sobre blanco.

### columna_explicacion
- Ubicación: excel/generar_figuras.py
- Qué hace: Formato de la columna de explicación de práctica.
- Cómo funciona: Ancho, ajuste y cursiva naranja.

### cargar_filas
- Ubicación: excel/generar_figuras.py
- Qué hace: Escribe filas de ejemplo.
- Cómo funciona: Dicts clave→valor desde la fila inicial; 'expl' va a la columna de explicación.

### hoja_compras
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea una hoja de compras (real o práctica).
- Cómo funciona: Fórmulas por fila, formatos, validaciones, colores, tabla y alto de fila para la foto.

### hoja_pagos
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea una hoja de pagos (real o práctica).
- Cómo funciona: Fórmulas de ID, clave y Validación; lista 'Asociado a'.

### hoja_grupos
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea una hoja de envíos acumulados (real o práctica).
- Cómo funciona: Conteo, suma, pago, estado, ahorro, impuestos y lista.

### hoja_ahorro
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea una hoja de ahorro (real o práctica).
- Cómo funciona: Resumen, plan de pagos del 13 y tabla de abonos; en práctica agrega una explicación.

### hoja_config
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea Config y sus nombres definidos.
- Cómo funciona: Parámetros, validaciones y tabla tblTC con 2 tasas de ejemplo.

### hoja_listas
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea la hoja oculta Listas y los nombres lst*.
- Cómo funciona: Listas fijas y listas dinámicas compactas.

### hoja_panel
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea el Panel.
- Cómo funciona: Bloques: próximo pago y ahorro, mes a revisar, reglas, figuras, envíos, impuestos y casos de práctica.

### hoja_ayuda
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea la hoja Ayuda.
- Cómo funciona: Recorre AYUDA.

### construir
- Ubicación: excel/generar_figuras.py
- Qué hace: Arma y guarda el libro completo.
- Cómo funciona: Crea todas las hojas, colores de pestaña y guarda en la ruta dada.

## Variables del generador (excel/generar_figuras.py)

### FILAS_COMPRAS / FILAS_PAGOS / FILAS_GRUPOS / FILAS_ABONOS
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Filas de datos preparadas: 200, 300, 50 y 200.

### FILA_ENC / FILA_INI
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Fila de encabezados (4) y primera fila de datos (5).

### MESES_PLAN
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Meses del plan de ahorro (24) desde el mes actual.

### AH_RES_INI, AH_PLAN_ENC, AH_PLAN_INI, AH_PLAN_FIN, AH_ABO_ENC, AH_ABO_INI, AH_ABO_FIN, AH_CELDAS
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Distribución de filas de la hoja Ahorro y celdas del resumen.

### FUENTE, FMT_CLP, FMT_CLP_VACIO, FMT_TC, FMT_FECHA, FMT_MES, FMT_PCT
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Fuente Arial y formatos numéricos.

### AZUL_ENC, GRIS_ENC, AMARILLO, ROJO_FILL, VERDE_FILL, NARANJA_FILL, GRIS_FILL, BLANCO_FILL, ROJO_TXT, VERDE_TXT, NARANJA_TXT
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Colores consistentes: rojo advertencia, verde OK, naranja aviso, gris cancelado.

### MESES
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Abreviaturas ene..dic.

### COLS_COMPRAS / COLS_PAGOS / COLS_GRUPOS / COLS_ABONOS / COLS_PLAN
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Definición de columnas: clave, encabezado, ancho, ingreso y nota.

### LC / LP / LG / LA / LPL
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Mapas clave→letra de cada tabla.

### DEMO_COMPRAS / DEMO_PAGOS / DEMO_GRUPOS / DEMO_ABONOS
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Datos simulados de las hojas de práctica.

### CASOS_PRACTICA
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Textos de casos de práctica del Panel.

### AYUDA
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Contenido de la hoja Ayuda.
