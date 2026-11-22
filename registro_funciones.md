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
- Qué hace: Hoja principal: resumen del mes elegido.
- Cómo funciona: Selector Mes (B4) y Año (B5). Muestra totales de figuras, reglas (OK/rojo), envíos del mes, envíos sin pagar de todos los meses, ahorro de envíos conjuntos y fecha de pago. Cada fórmula usa IF(cfgModo="Sí", hojas de práctica, hojas reales).

### Compras
- Ubicación: excel/Figuras.xlsx (hoja Compras, tabla tblCompras)
- Qué hace: Una fila por producto real (200 filas listas, IDs C001-C200).
- Cómo funciona: Columnas de ingreso en amarillo (encabezado azul) y calculadas (encabezado gris). Las fórmulas leen Pagos y Config; no escriben en otras hojas.

### Pagos
- Ubicación: excel/Figuras.xlsx (hoja Pagos, tabla tblPagos)
- Qué hace: Registro de cada pago real en CLP (300 filas).
- Cómo funciona: Ingreso: Fecha, Monto, Tipo, Asociado a, Nota. Calcula ID (4 primeros caracteres de Asociado), clave de mes y Validación.

### Envíos acumulados
- Ubicación: excel/Figuras.xlsx (hoja Envíos acumulados, tabla tblEnvios)
- Qué hace: Grupos de envíos pagados en conjunto (G001-G050).
- Cómo funciona: Ingreso: Descripción. Calcula N envíos, suma individual, pagado conjunto (desde Pagos), fecha de pago, estado y ahorro = suma individual - pagado.

### Config
- Ubicación: excel/Figuras.xlsx (hoja Config, tabla tblTC)
- Qué hace: Parámetros editables y tabla de tipos de cambio.
- Cómo funciona: B3 Modo práctica, B4 límite normal, B5 límite excepción, B6 máx. productos, B7 contar por Unidades/Filas, B8 día de pago. Tabla tblTC (A12:C212): Fecha, Moneda, CLP por unidad.

### Ayuda
- Ubicación: excel/Figuras.xlsx (hoja Ayuda)
- Qué hace: Explicación de cada hoja, regla, campo y supuesto.
- Cómo funciona: Texto fijo generado desde la lista AYUDA y las notas de las columnas.

### Práctica Compras / Práctica Pagos / Práctica Envíos
- Ubicación: excel/Figuras.xlsx (tablas tblPracticaCompras, tblPracticaPagos, tblPracticaEnvios)
- Qué hace: Copias de estructura con datos simulados y columna 'Explicación (práctica)'.
- Cómo funciona: Mismas fórmulas que las hojas reales pero enlazadas entre sí. Con Modo práctica = No, un formato condicional deja texto y fondo blancos (se ocultan). Los datos reales nunca se tocan.

### Listas
- Ubicación: excel/Figuras.xlsx (hoja oculta Listas)
- Qué hace: Opciones de las listas desplegables.
- Cómo funciona: Col. A/B: lista compacta 'ID · nombre' de productos y grupos usados (real/práctica) con INDEX/MATCH sobre 'N lista'. Col. E-I: Sí/No, monedas, tipos de pago, meses, años.

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
- Cómo funciona: Config!B7: Unidades o Filas para contar productos.

### cfgDiaPago
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Config!B8: día de pago del mes.

### cfgTCFecha / cfgTCMoneda / cfgTCValor
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Columnas A, B y C de tblTC (filas 13-212).

### lstSiNo / lstMonedas / lstTipoPago / lstMeses / lstAnios
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Rangos fijos de Listas usados por las validaciones.

### lstAsociado / lstAsociadoP
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: OFFSET sobre Listas!A2 o B2 con largo = productos con nombre + grupos usados; lista de 'Asociado a' (real/práctica).

### lstGrupos / lstGruposP
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: IDs G001-G050 de Envíos acumulados / Práctica Envíos; lista de 'Grupo envío'.

### pnlClave
- Ubicación: excel/Figuras.xlsx (nombre definido)
- Qué hace: Referencia con nombre usada por las fórmulas.
- Cómo funciona: Panel!C6 (columna oculta): clave año*12+mes del mes elegido.

## Columnas calculadas (fórmulas por fila)

### Compras: Mes efectivo / Clave mes
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Primer día del Mes asignado o, si está vacío, del mes de Fecha compra; clave = AÑO*12+MES (permite comparar dic/ene restando 1).

### Compras: TC sugerido
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: MAXIFS da la última fecha de tblTC <= fecha de compra para la moneda; AVERAGEIFS trae su valor. Vacío si es CLP o no hay tasa.

### Compras: TC aplicado
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: 1 si CLP; si no, TC usado (fijo, escrito a mano) o el sugerido.

### Compras: Total figura CLP
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: ROUND(cantidad (vacía=1) x precio unitario x TC aplicado, 0). No incluye envío.

### Compras: Envío CLP
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Monto de envío x TC: 1 si CLP; TC aplicado si es la moneda del producto; si no, TC sugerido de Config. Moneda vacía = la del producto.

### Compras: Pagado figura CLP / Pagado envío CLP
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: SUMIFS en Pagos por ID y Tipo (Figura o Envío).

### Compras: Estado producto
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Cerrado si Cerrar=Sí; Pendiente si no hay pagos; Pagado si pagado >= total; si no, Parcial.

### Compras: Falta pagar figura / Diferencia figura
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Falta = MAX(0, total - pagado), 0 si Cerrado. Diferencia = pagado - total solo si Pagado o Cerrado; si no, 0.

### Compras: Estado envío / Envío por pagar
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Sin envío si Envío CLP = 0; acumulado: Pagado si el grupo tiene pagos, si no Acumulado; individual: Pagado si pagos >= envío, si no Pendiente. Por pagar = lo que falta según ese estado.

### Compras: Total figuras mes / Productos mes / Límite mes
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: SUMIFS del total por clave; unidades (suma de Cantidad, vacía=1) o filas según cfgContar; límite = cfgLimExc si productos <= cfgMaxProd, si no cfgLimNormal.

### Compras: Reglas
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: TEXTJOIN de 'Mes bloqueado: el mes X ya tiene compras' (si la clave-1 tiene compras) y 'Límite superado en $N (regla...)' (si total mes > límite). Vacío = OK. Solo figuras.

### Compras: Avisos de datos
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: TEXTJOIN de faltantes: fecha, moneda, precio, TC (o TC no fijado), grupo de envío, TC del envío.

### Compras: N lista / Etiqueta lista
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Correlativo de filas con producto y texto 'ID · Producto' para la lista de Pagos.

### Pagos: ID / Clave mes pago / Validación
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: ID = MAYUSC(IZQUIERDA(Asociado,4)); clave del mes de pago; Validación revisa datos completos, ID existente, que un grupo solo reciba pagos de Envío y que un envío acumulado se pague al grupo.

### Envíos acumulados: N envíos / Suma individual / Pagado conjunto / Fecha pago / Estado / Ahorro
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: COUNTIFS y SUMIFS sobre Compras (Grupo envío = ID y Envío acumulado = Sí); SUMIFS y MAXIFS sobre Pagos (Tipo Envío, ID del grupo); Ahorro = suma - pagado solo si Pagado.

### Panel: Meses alternos / Límite mensual / Estado general
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: COUNTIF de la clave del mes, del anterior y del siguiente; compara total de figuras con el límite que aplica; ADVERTENCIA si alguna regla falla. Formato condicional rojo/verde.

### Panel: bloque Envíos y ahorro
- Ubicación: excel/Figuras.xlsx
- Qué hace: Fórmula por fila/celda (ver encabezado y su nota).
- Cómo funciona: Pendientes del mes = SUMIFS de Envío por pagar; pagados en el mes = pagos tipo Envío con fecha del mes; total sin pagar = SUMA de Envío por pagar; ahorro por mes de pago y total.

## Funciones del generador (excel/generar_figuras.py)

### letras
- Ubicación: excel/generar_figuras.py
- Qué hace: Mapa clave de columna → letra.
- Cómo funciona: Recibe la lista de columnas; retorna dict. Sin efectos.

### q
- Ubicación: excel/generar_figuras.py
- Qué hace: Pone comillas simples a un nombre de hoja.
- Cómo funciona: Recibe texto; retorna "'hoja'".

### rango
- Ubicación: excel/generar_figuras.py
- Qué hace: Rango absoluto de una columna de datos de otra hoja.
- Cómo funciona: Recibe hoja, letra y n° de filas; retorna texto tipo 'Compras'!$D$5:$D$204.

### etiqueta_mes
- Ubicación: excel/generar_figuras.py
- Qué hace: Fórmula que convierte una clave de mes en 'mmm-aaaa'.
- Cómo funciona: Usa CHOOSE/MOD/INT para no depender del idioma de Excel. Retorna texto de fórmula.

### pesos
- Ubicación: excel/generar_figuras.py
- Qué hace: Fórmula de texto '$1.234'.
- Cómo funciona: Usa FIXED(x,0) para respetar el separador de miles del teléfono.

### estilo_titulo
- Ubicación: excel/generar_figuras.py
- Qué hace: Da formato de título a una celda.
- Cómo funciona: Modifica la fuente de la celda.

### encabezado
- Ubicación: excel/generar_figuras.py
- Qué hace: Escribe encabezados con color, nota y ancho.
- Cómo funciona: Azul = ingreso, gris = calculado; agrega comentario por encabezado.

### formato_columna
- Ubicación: excel/generar_figuras.py
- Qué hace: Formatea las filas de datos de una columna.
- Cómo funciona: Fuente, formato numérico, relleno amarillo de ingreso y ajuste de texto.

### agregar_tabla
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea una tabla de Excel con filtros.
- Cómo funciona: Recibe hoja, nombre, última columna y filas.

### lista_dv / num_dv / fecha_dv
- Ubicación: excel/generar_figuras.py
- Qué hace: Agregan validaciones (lista, número, fecha) con mensaje de ayuda.
- Cómo funciona: Crean DataValidation y la aplican al rango.

### cf_texto
- Ubicación: excel/generar_figuras.py
- Qué hace: Agrega formato condicional por fórmula.
- Cómo funciona: Relleno + color de texto en negrita.

### banner
- Ubicación: excel/generar_figuras.py
- Qué hace: Fila 2 con aviso del modo práctica.
- Cómo funciona: Fórmula que cambia según cfgModo; formato naranja cuando está activo.

### ocultar_si_no_practica
- Ubicación: excel/generar_figuras.py
- Qué hace: Oculta datos de práctica cuando el modo está en No.
- Cómo funciona: Formato condicional con texto y fondo blancos.

### hoja_compras
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea una hoja de compras (real o práctica).
- Cómo funciona: Escribe IDs, fórmulas por fila, formatos, validaciones, colores y tabla; carga filas de demo si se pasan.

### hoja_pagos
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea una hoja de pagos (real o práctica).
- Cómo funciona: Fórmulas de ID, clave y Validación; lista 'Asociado a'.

### hoja_grupos
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea una hoja de envíos acumulados (real o práctica).
- Cómo funciona: Fórmulas de conteo, suma, pago, estado, ahorro y lista.

### cargar_filas
- Ubicación: excel/generar_figuras.py
- Qué hace: Escribe filas de ejemplo.
- Cómo funciona: Recibe dicts clave→valor; 'expl' va a la columna de explicación.

### hoja_config
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea Config y sus nombres definidos.
- Cómo funciona: Parámetros, validaciones y tabla tblTC con 2 tasas de ejemplo.

### hoja_listas
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea la hoja oculta Listas y los nombres lst*.
- Cómo funciona: Listas fijas y listas dinámicas compactas de productos/grupos.

### hoja_panel
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea el Panel.
- Cómo funciona: Selector, bloques de figuras, reglas, envíos, ahorro, pago y casos de práctica.

### hoja_ayuda
- Ubicación: excel/generar_figuras.py
- Qué hace: Crea la hoja Ayuda.
- Cómo funciona: Recorre AYUDA: títulos de sección y filas título/texto.

### construir
- Ubicación: excel/generar_figuras.py
- Qué hace: Arma y guarda el libro completo.
- Cómo funciona: Crea todas las hojas en orden, colores de pestaña y guarda en la ruta dada.

## Variables del generador (excel/generar_figuras.py)

### FILAS_COMPRAS / FILAS_PAGOS / FILAS_GRUPOS
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Filas de datos preparadas: 200, 300 y 50.

### FILA_ENC / FILA_INI
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Fila de encabezados (4) y primera fila de datos (5).

### FUENTE, FMT_CLP, FMT_TC, FMT_FECHA, FMT_MES
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Fuente Arial y formatos numéricos (CLP sin decimales con separador de miles).

### AZUL_ENC, GRIS_ENC, AMARILLO, ROJO_FILL, VERDE_FILL, NARANJA_FILL, BLANCO_FILL, ROJO_TXT, VERDE_TXT
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Colores consistentes: rojo advertencia, verde OK, naranja aviso.

### MESES
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Abreviaturas ene..dic para selector y etiquetas.

### COLS_COMPRAS / COLS_PAGOS / COLS_GRUPOS
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Definición de columnas: clave, encabezado, ancho, si es ingreso y nota.

### LC / LP / LG
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Mapas clave→letra de cada hoja.

### DEMO_COMPRAS / DEMO_PAGOS / DEMO_GRUPOS
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Datos simulados de las hojas de práctica.

### CASOS_PRACTICA
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Textos de los casos de práctica que muestra el Panel.

### AYUDA
- Ubicación: excel/generar_figuras.py
- Qué hace: Constante del generador.
- Cómo funciona: Contenido de la hoja Ayuda.
