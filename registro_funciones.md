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
- Ubicación: excel/Figuras.xlsx
- Qué hace: Total ahorrado arriba, movimientos Agregar/Quitar con historial y, a la derecha, detalle y plan de pagos del 13 (tabla tblAbonos).
- Cómo funciona: B3 = TOTAL AHORRADO; B4 = cuánto cubre del próximo pago. Tabla desde la fila 6: Fecha, Monto, Agregar o Quitar, Nota, Revisar, Total después, Valor. Detalle en I3:J11 y plan desde I14.

### Config
- Ubicación: excel/Figuras.xlsx (hoja Config, tabla tblTC)
- Qué hace: Parámetros editables y tabla de tipos de cambio.
- Cómo funciona: B3 modo práctica, B4-B5 límites, B6 máx. productos, B7 contar por, B8 día de pago (13), B9 arancel, B10 IVA, B11 hoy opcional, B12 hoy de práctica, B13 descontar pagos del ahorro (Sí/No). Columna C 'Revisar' (C3:C13) avisa valores vacíos o mal escritos y qué valor por defecto se usa. tblTC en A16:C216.

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
- Cómo funciona: Fórmula: Config!B11 si es una fecha (ISNUMBER); si no, HOY().

### cfgHoyPractica
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B12: fecha fija de hoy para los datos de práctica (01-02-2027).

### cfgTCFecha / cfgTCMoneda / cfgTCValor
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Columnas A, B y C de tblTC (filas 17-216).

### cfgPractica
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Verdadero si el modo práctica está activo.
- Cómo funciona: LEFT(TRIM(Config!B3),1)="S": acepta Sí, si, SI. Reemplaza las comparaciones cfgModo="Sí".

### cfgLimNormalV / cfgLimExcV / cfgMaxProdV / cfgDiaPagoV
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Valores seguros de los parámetros de Config.
- Cómo funciona: Si la celda está vacía, no es número o está fuera de rango, usan 200.000 / 300.000 / 2 / 13. Las fórmulas usan estos nombres, no las celdas directas.

### cfgArancelV / cfgIVAV
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Porcentajes seguros de arancel e IVA.
- Cómo funciona: Si el valor es mayor a 1 se interpreta como porcentaje entero (6 → 6%); vacío o texto → 6% / 19%.

### cfgDescontar
- Ubicación: excel/Figuras.xlsx
- Qué hace: Verdadero si en Config!B13 se activó 'Descontar pagos del ahorro'.
- Cómo funciona: LEFT(TRIM(Config!B13),1)="S". Por defecto No: el ahorro solo cambia con Agregar/Quitar.

### lstMovAhorro
- Ubicación: excel/Figuras.xlsx
- Qué hace: Lista Agregar/Quitar de la tabla de movimientos.
- Cómo funciona: Rango fijo Listas!L2:L3.

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

### Compras: Clave mes
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: AÑO*12+MES de la Fecha salida original (vacío si Cancelada o si la fecha no es fecha). Se eliminó la columna 'Mes efectivo' (era redundante).

### Compras: TC sugerido / TC aplicado
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: MAXIFS da la última fecha de tblTC <= Fecha pago con valor numérico > 0; AVERAGEIFS trae su valor (filas con texto o 0 se ignoran, sin #DIV/0!). Aplicado = 1 si CLP, TC usado si existe, si no el sugerido.

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
- Cómo funciona: Solo si el ID empieza con C: SUMIFS en Pagos por ID, Tipo ("Fig*"/"Env*") y Validación = "OK": un pago inválido o a una figura cancelada no cuenta.

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
- Cómo funciona: Envío CLP; si el grupo está pagado, ROUND(Envío CLP x pagado del grupo / suma individual del grupo). La última figura del grupo (COUNTIFS de filas de abajo = 0) recibe pagado - suma de las partes de arriba, para que el total calce exacto sin sesgo de redondeo.

### Compras: Arancel est. / IVA est. / Impuestos est.
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Arancel = ROUND((total + envío p/ impuesto) x cfgArancel); IVA = ROUND((total + envío + arancel) x cfgIVA); suma. Vacío si Cancelado.

### Compras: Clave mes pago envío
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Sin envío → clave del mes de salida (llega sin costo de envío). Pagado → mes del último pago válido tipo Envío al ID (individual) o al grupo (acumulado). Si no, vacío.

### Compras: Total figuras mes / Productos mes / Límite mes
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: SUMIFS por clave; unidades o filas según cfgContar; límite según cfgMaxProd.

### Compras: Reglas
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Cancelada: aviso si tiene pagos o grupo. Si no, 'OK' si Aviso bloqueo y Aviso límite están vacíos; si no, TEXTJOIN de ambos.

### Compras: Avisos de datos
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: TEXTJOIN de: falta/repetido ID, fecha salida no válida, cantidad/precio/moneda/TC no válidos, TC USD/JPY fuera de rango, TC usado en CLP, pago especial fuera de mes o no fecha, envío no válido, grupo faltante o inexistente, TC del envío, 'Cerrada sin pagos' y los avisos de contralógica (ver entrada propia).

### Compras: N lista / Etiqueta lista
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Correlativo y texto 'ID · Producto' para la lista de Pagos.

### Pagos: ID / Clave mes pago / Validación
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: ID tolerante (texto antes de '·', sin espacios, letra + número rellenado a 3 dígitos; un número solo = C###); clave del mes de pago; Validación revisa datos, fecha, monto > 0, tipo, ID existente y no repetido, producto vacío o cancelado, pagos de figura a grupos y envíos acumulados. Solo 'OK' cuenta en los totales.

### Envíos acumulados: N envíos / Suma / Pagado / Fecha / Estado / Ahorro / Impuestos est. grupo
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: COUNTIFS/SUMIFS sobre Compras (grupo, acumulado "S*", manual "<>Can*") y SUMIFS/MAXIFS sobre Pagos ("Env*", grupo, Validación OK). Ahorro = suma - pagado si Pagado. Impuestos est. grupo se calcula sobre el paquete completo: base = figuras + (pagado conjunto o suma individual); arancel = ROUND(base x cfgArancelV); IVA = ROUND((base + arancel) x cfgIVAV).

### Ahorro: detalle (I3:J11)
- Ubicación: excel/Figuras.xlsx
- Qué hace: Cifras del ahorro que usa el Panel.
- Cómo funciona: Hoy, total agregado (SUMIF Valor > 0), total quitado, pagos descontados (solo si cfgDescontar: pagos de figuras OK desde el primer movimiento), total ahorrado = agregado - quitado - descontado, saldo extra, atrasado, clave del mes actual y clave del próximo pago.

### Ahorro: plan (A pagar / Primer pago / Ahorrado / Falta juntar / Meses / Cuota / Apartar)
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: A pagar = SUMIFS de Falta pagar por clave; Ahorrado = MIN(a pagar, MAX(0, MAX(0, total ahorrado) - suma de meses anteriores)); Meses = clave - clave actual; Cuota = falta (si meses <= 0) o ROUNDUP(falta/meses); Apartar = cuota del mes actual o suma de cuotas de pagos con clave >= ese mes.

### Panel: próximo pago y ahorro
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Primera fila: Total ahorrado. Clave del próximo mes con pagos = MINIFS sobre el plan; trae fecha, a pagar, ahorrado, falta, apartar este mes y el próximo, saldo, saldo extra y atrasado.

### Panel: Estado general / Meses alternos / Límite mensual
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: COUNTIF de la clave del mes, anterior y siguiente; total vs. límite; ADVERTENCIA si falla alguna. Rojo/verde.

### Panel: envíos e impuestos
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Pendientes del mes, pagados en el mes, total sin pagar, ahorro de grupos; impuestos de figuras del mes y de envíos pagados en el mes (con arancel e IVA por separado).

### Compras: Integridad
- Ubicación: excel/Figuras.xlsx
- Qué hace: Detecta si alguien escribió encima de una celda calculada.
- Cómo funciona: AND de ISFORMULA sobre TODAS las columnas calculadas de la fila (se arma desde COLS_COMPRAS); 'OK' si están intactas (vacío si la fila no tiene producto), si no 'Fórmulas borradas: copia la fila de arriba'.

### Pagos: Integridad
- Ubicación: excel/Figuras.xlsx
- Qué hace: Detecta fórmulas borradas en el pago.
- Cómo funciona: ISFORMULA sobre ID, clave, Fila compra, Fila grupo y Validación.

### Envíos acumulados: Integridad
- Ubicación: excel/Figuras.xlsx
- Qué hace: Detecta fórmulas borradas e IDs de grupo repetidos.
- Cómo funciona: ISFORMULA sobre todas las columnas calculadas; si el ID del grupo aparece más de una vez, 'ID de grupo repetido: corrígelo'.

### Ahorro: Valor / Total después / Revisar
- Ubicación: excel/Figuras.xlsx
- Qué hace: Calculan cada movimiento del historial.
- Cómo funciona: Valor = +monto (Agregar) o -monto (Quitar) si la fecha es válida y no futura, monto > 0 y tipo válido; si no, 0. Total después = suma de Valor hasta esa fila. Revisar = OK, el motivo del rechazo o 'Ojo: el ahorro queda negativo'.

### Panel: Revisión del libro
- Ubicación: excel/Figuras.xlsx
- Qué hace: Cuenta lo que hay que revisar en el libro.
- Cómo funciona: Config (columna Revisar ≠ OK), Compras con avisos, canceladas con pagos, pagos con avisos, pagos y movimientos de ahorro no OK, fórmulas borradas (columnas Integridad) y datos escritos debajo de las filas preparadas. La fila 3 muestra 'todo OK', el total pendiente o la guía 'EMPIEZA AQUÍ' si no hay datos reales.

### Panel: selector tolerante
- Ubicación: excel/Figuras.xlsx
- Qué hace: Interpreta el mes y año elegidos.
- Cómo funciona: Mes: número 1-12 o las 3 primeras letras ('Julio' → jul); vacío o inválido = mes de hoy. Año: 2000-2100; vacío = año de hoy. Resultado en pnlClave.

### Compras: Pagado grupo / Suma grupo
- Ubicación: excel/Figuras.xlsx
- Qué hace: Columnas auxiliares que se calculan una vez por fila y reutilizan Estado envío, Envío para impuesto y la clave de pago.
- Cómo funciona: Pagado grupo = SUMIFS de pagos Envío válidos al grupo; Suma grupo = SUMIFS de Envío CLP de figuras activas del grupo. 0 si no hay grupo.

### Compras: Aviso bloqueo / Aviso límite
- Ubicación: excel/Figuras.xlsx
- Qué hace: Textos de cada regla, calculados una vez y usados por la columna Reglas.
- Cómo funciona: Aviso bloqueo: 'Mes bloqueado...' si la clave-1 tiene compras. Aviso límite: 'Límite superado...' si total del mes > límite.

### Pagos: Fila compra / Fila grupo
- Ubicación: excel/Figuras.xlsx
- Qué hace: Posición del ID en Compras o en Envíos acumulados (0 si no existe).
- Cómo funciona: IFERROR(MATCH(ID, IDs, 0), 0) según la letra C o G. La Validación las reutiliza en vez de repetir MATCH.

### Pagos: Validación base / Validación / Avisos
- Ubicación: excel/Figuras.xlsx
- Qué hace: Decide si un pago cuenta y marca lo raro sin bloquear.
- Cómo funciona: Validación base (auxiliar): datos, fecha (no futura), monto > 0, tipo, ID existente y no repetido, producto vacío o cancelado, envío a figura sin envío o acumulada, figura a grupo, grupo sin envíos. Validación (final) = la base y, para Envío, la regla de orden: la figura debe estar pagada ('Figura pagada el'), el envío no puede ser antes de ese pago ni de la 'Salida efectiva'; para un grupo, todas sus figuras pagadas y el envío después del último pago y de la última salida. Se separa en dos columnas para evitar referencias circulares. Avisos (no bloquean): posible duplicado (COUNTIFS fecha+monto+tipo+ID), pago anterior a la reserva, se pasa del total (no en Cerradas), monto distinto al envío (>20%), pago de grupo mayor al doble.

### Ahorro: Integridad del plan
- Ubicación: excel/Figuras.xlsx
- Qué hace: Detecta fórmulas borradas en el plan de pagos del 13.
- Cómo funciona: ISFORMULA sobre cada columna de la fila del plan; el Panel lo suma en 'Fórmulas borradas'.

### Listas: conteos C1/C2 y D1/D2
- Ubicación: excel/Figuras.xlsx
- Qué hace: Cantidad de productos y grupos usados (real y práctica), calculados una sola vez.
- Cómo funciona: COUNTA de Producto (cuenta también nombres numéricos) y SUM de 'Usado'; las listas dinámicas y los nombres lstAsociado los reutilizan.

### Panel: Próximo pago en rojo / IDs de grupo repetidos
- Ubicación: excel/Figuras.xlsx
- Qué hace: Avisos nuevos del Panel.
- Cómo funciona: Formato rojo si la fecha del próximo pago es anterior a hoy. Fila de revisión que cuenta grupos con 'ID de grupo repetido'.

### Compras: Figura pagada el
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fecha en que la figura quedó pagada.
- Cómo funciona: Con la Validación base: si (Cerrada y pagos > 0) o (pagos >= total), MAXIFS de la fecha de sus pagos de figura; si no, vacío. La usa Pagos para permitir o bloquear el envío.

### Compras: Salida efectiva
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fecha desde la que se puede pagar el envío.
- Cómo funciona: 'Salida actual' si es fecha; si no, 'Fecha salida original'.

### Compras: avisos de contralógica
- Ubicación: excel/Figuras.xlsx
- Qué hace: Marcan combinaciones raras sin bloquear.
- Cómo funciona: Reserva después de la salida, salida actual antes que la original, acumulado sin monto de envío, envío pagado de más (>120%), Cerrada pagando menos del 90%, pagado más del 150% del total.

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
- Cómo funciona: Recibe hoja, nombre, hoja de pagos, hoja de grupos y tabla; usa FILAS_PRACTICA si es práctica. Fórmulas por fila (tolerantes a texto y mayúsculas), formatos, validaciones, colores, tabla y alto de fila para la foto.

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
- Cómo funciona: Total arriba (B3), próximo pago (B4), tabla de movimientos Agregar/Quitar con historial, detalle y plan del 13 a la derecha; en práctica agrega una explicación.

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
- Cómo funciona: Fija la fuente base Arial 11, crea todas las hojas, colores de pestaña, aplica asegurar_legibilidad y guarda en la ruta dada.

### _fuente_px
- Ubicación: excel/generar_figuras.py
- Qué hace: Carga (con caché) la fuente Liberation Sans (mismas medidas que Arial) en un tamaño en píxeles.
- Cómo funciona: Recibe px, negrita, cursiva; retorna un ImageFont de PIL. Requiere las fuentes en RUTA_FUENTES.

### medir_px
- Ubicación: excel/generar_figuras.py
- Qué hace: Ancho que necesita un texto a zoom 100%, en el peor caso de todos los zooms.
- Cómo funciona: Para cada zoom de ZOOMS_DISENO redondea la letra a píxeles enteros (como Excel), mide el texto + margen interno y retorna el máximo normalizado a 100%.

### alto_linea_pt
- Ubicación: excel/generar_figuras.py
- Qué hace: Alto de una línea de texto en puntos, en el peor zoom.
- Cómo funciona: Recibe el tamaño de letra; considera el redondeo a píxeles de cada zoom.

### lineas_necesarias
- Ubicación: excel/generar_figuras.py
- Qué hace: Cuántas líneas ocupa un texto ajustado a un ancho, en el peor zoom.
- Cómo funciona: Ajuste por palabras; si una palabra no cabe, la corta por caracteres (como Excel). Retorna el máximo de líneas entre zooms.

### _px_de_ancho / _ancho_de_px
- Ubicación: excel/generar_figuras.py
- Qué hace: Convierten ancho de columna de Excel (caracteres) a píxeles y viceversa.
- Cómo funciona: px = trunc(ancho x 7 + 5) con la fuente base Arial 11.

### _muestra
- Ubicación: excel/generar_figuras.py
- Qué hace: Texto más largo que puede mostrar una celda.
- Cómo funciona: Literal si es texto; para fórmulas usa MUESTRA_PANEL / MUESTRA_TEXTO por encabezado, o el texto entre comillas más largo de la fórmula; para números y celdas de ingreso vacías usa MUESTRA_NUMERO según el formato.

### ensanchar_titulos
- Ubicación: excel/generar_figuras.py
- Qué hace: Da espacio a títulos y notas sueltas de la columna A.
- Cómo funciona: Combina la celda con las columnas vecinas vacías (hasta la H) en las filas 1 y 3.

### asegurar_legibilidad
- Ubicación: excel/generar_figuras.py
- Qué hace: Fija el espacio de cada celda con margen de seguridad para que se lea con cualquier zoom (50% a 400%).
- Cómo funciona: Por hoja visible: ensancha columnas para números y palabras largas (+20%), ensancha columnas de texto de tablas para no pasar de 3 líneas, ajusta todo texto en líneas y calcula el alto de cada fila (líneas con 15% menos de ancho y +20% de alto). Ignora columnas ocultas. Se ejecuta al final de construir().

### g_fila_plan
- Ubicación: excel/generar_figuras.py
- Qué hace: Fila del encabezado del plan de ahorro si la hoja es de Ahorro.
- Cómo funciona: Retorna AH_PLAN_ENC o None.

## Variables del generador (excel/generar_figuras.py)

### FILAS_PRACTICA
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Filas de las hojas de práctica: compras 40, pagos 40, grupos 10, movimientos de ahorro 20 (reduce fórmulas y tamaño).

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

### AH_TOTAL, AH_MOV_ENC, AH_MOV_INI, AH_MOV_FIN, AH_RES_COL, AH_CELDAS, AH_PLAN_ENC, AH_PLAN_INI, AH_PLAN_FIN, PLAN_COL0
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Distribución de la hoja Ahorro: total en fila 3, movimientos desde la fila 6, detalle en la columna J (filas 3-11) y plan desde I14.

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

### COLS_COMPRAS / COLS_PAGOS / COLS_GRUPOS / COLS_MOV / COLS_PLAN
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Definición de columnas: clave, encabezado, ancho, ingreso y nota.

### LC / LP / LG / LA / LPL
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Mapas clave→letra de cada tabla.

### DEMO_COMPRAS / DEMO_PAGOS / DEMO_GRUPOS / DEMO_MOV
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

### RUTA_FUENTES / ZOOMS_DISENO
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Carpeta de Liberation Sans y niveles de zoom simulados (50% a 400%).

### MARGEN_SEGURIDAD / MARGEN_ANCHO_TEXTO / MAX_LINEAS_TABLA
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: 20% de espacio extra en ancho y alto; las líneas se calculan con 15% menos de ancho; filas de tabla de máximo 3 líneas.

### MUESTRA_TEXTO / MUESTRA_PANEL / MUESTRA_NUMERO
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Textos y valores más largos esperados (mensajes, montos de hasta $999.999.999, fechas) para reservar su espacio.

### Fuente base del libro (wb._fonts)
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Se reemplaza la fuente Normal (Calibri) por Arial 11, la misma de las celdas: así el ancho de columna se mide con la fuente que realmente se muestra.

