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

## Herramienta Excel de rendimiento (entregables/pruebas_excel)

Generador del archivo `entregables/Registro_Rendimiento_Auto_Delivery.xlsx` y sus pruebas automáticas. Python + openpyxl; las pruebas recalculan con LibreOffice.

### OUT
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: ruta del .xlsx que se va a generar.
- Cómo funciona: se lee de `sys.argv[1]`. Sin efectos secundarios.

### EMPTY
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: indica si el archivo se genera con el Modo aprendizaje apagado.
- Cómo funciona: es True cuando el segundo argumento es `empty`; el interruptor de Inicio queda en "No". Las filas reales siempre parten vacías.

### PRI, ACC, INPUT, CALC, LIGHT, BORDER, MUTED, G_DK, G_MD, G_LT, G_TX, OR_F, OR_T, RD_F, RD_T
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: paleta única de colores (hex) usada en todas las hojas.
- Cómo funciona: PRI/ACC azules de marca; INPUT crema (celdas para escribir); CALC gris (calculadas); G_* verdes, OR_* naranjas y RD_* rojos solo para cumplimiento de meta y alertas.

### FONT
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: nombre de la fuente de todo el libro ("Arial").
- Cómo funciona: la usan `font()` y el estilo Normal del libro.

### fill
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: crea un relleno sólido de un color.
- Cómo funciona: recibe un hex y retorna `PatternFill`. Sin efectos secundarios.

### font
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: crea una fuente Arial con tamaño, negrita, color, cursiva y subrayado.
- Cómo funciona: retorna `Font`. Valores por defecto: tamaño 10, gris oscuro.

### thin, BOX, BOT
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: bordes finos grises reutilizables (lado, caja completa, solo abajo).
- Cómo funciona: objetos `Side`/`Border` de openpyxl.

### CENTER, LEFT
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: alineaciones estándar (centrada, e izquierda con sangría), ambas con ajuste de texto.
- Cómo funciona: objetos `Alignment`.

### TIME, DATE
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: formatos de número para tiempos (`[m]:ss`) y fechas (`dd-mmm-yy`).
- Cómo funciona: se asignan a `number_format`.

### wb
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: libro de Excel en construcción.
- Cómo funciona: `Workbook()` de openpyxl. Se guarda en OUT al final. Su estructura queda protegida (`lockStructure`).

### sheet
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: crea (o renombra la primera) hoja sin cuadrícula y con margen a la izquierda.
- Cómo funciona: recibe nombre y `first`. Retorna la hoja. Modifica `wb`.

### put
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: escribe un valor en una celda y le aplica formato opcional.
- Cómo funciona: recibe hoja, referencia, valor, fuente, relleno, alineación, formato numérico y borde. Retorna la celda.

### merge
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: combina un rango y escribe el valor en su celda superior izquierda.
- Cómo funciona: llama a `merge_cells` y luego a `put`. Retorna la celda ancla.

### link
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: convierte una celda en enlace interno a otra hoja.
- Cómo funciona: crea `Hyperlink(location="'Hoja'!A1")`. `white=True` lo deja con letra blanca para usarlo como botón.

### title
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: pone el título, el subtítulo y los enlaces "Inicio" y "Panel" de una hoja.
- Cómo funciona: combina celdas de la fila 2 y 3. No pone el enlace "Panel" en la propia hoja Panel.

### dv
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: crea una validación de datos con cartel de ayuda opcional.
- Cómo funciona: `prompt=(título, texto)` activa el mensaje al seleccionar la celda. El resto de argumentos va a `DataValidation`. Por defecto muestra el mensaje de error.

### UNLOCK
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: protección "desbloqueada" para las celdas donde el usuario escribe.
- Cómo funciona: `Protection(locked=False)`. Solo surte efecto cuando la hoja está protegida.

### unlock
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: desbloquea una o varias celdas o rangos.
- Cómo funciona: recibe hoja y referencias. Aplica UNLOCK a cada celda.

### protect
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: protege una hoja sin contraseña.
- Cómo funciona: bloquea la edición de fórmulas y la inserción o el borrado de filas y columnas. Permite filtrar, seleccionar y cambiar el ancho o el alto de filas y columnas.

### FECHA_MIN, FECHA_MAX
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: rango de fechas válidas (serial Excel de 01-01-2020 a 01-01-2100).
- Cómo funciona: lo usan `f_fecha`, `dv_fecha` y las validaciones. Fuera de rango la fila se marca "Revisar".

### MAX_AUTOS, MAX_SEG
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: límites de sentido común: 3.000 autos por fila y 1.200 s (20 min) de tiempo promedio.
- Cómo funciona: valores mayores se consideran error de escritura (por ejemplo "325" en minutos) y la fila queda "Revisar".

### add_table
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: crea una tabla de Excel con filtros sobre un rango.
- Cómo funciona: estilo `TableStyleLight1` sin bandas. Los encabezados deben ser únicos.

### header
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: escribe una fila de encabezados con fondo azul y letra blanca.
- Cómo funciona: recibe fila, columna inicial y lista de textos.

### lvl
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: genera la fórmula del nivel (Excelencia máxima … No cumple) a partir de un tiempo en días.
- Cómo funciona: redondea a segundos (`ROUND(x*86400)`) y aplica los cortes 60/120/180/240/300 s. Se usa en el Panel.

### lvl_s
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: igual que `lvl`, pero a partir de segundos enteros ya calculados.
- Cómo funciona: lo usa la columna Nivel con `aux_segReal`, para no repetir el redondeo seis veces por fila. Nivel le agrega DUDOSO cuando corresponde.

### cf_time
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: formato condicional de tiempos: verde bajo 4:00, naranja de 4:00 a 4:59 y rojo desde 5:00.
- Cómo funciona: agrega tres reglas con fórmula sobre el rango. Solo colorea celdas numéricas.

### cf_text
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: formato condicional por texto (niveles, Sí/No, Estado).
- Cómo funciona: recibe pares (patrón de fórmula, relleno, color de letra). `{c}` se reemplaza por la celda de referencia.

### GOOD_BAD
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: mapeo de colores para Sí (verde) y No (rojo).
- Cómo funciona: lista que se pasa a `cf_text`.

### N_ROWS, R0, R1
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: capacidad de los registros: 1.100 filas, desde la fila 8 hasta la 1107.
- Cómo funciona: definen todos los rangos de fórmulas. El Panel avisa cuando quedan menos de 60 filas.

### RA, RD
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: nombres entre comillas de las hojas Registro Auto y Registro Delivery, para las referencias.
- Cómo funciona: los usan `ra()` y `rd()`.

### ra, rd
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: devuelven el rango absoluto completo de una columna en Registro Auto o en Delivery.
- Cómo funciona: `ra("K")` → `'Registro Auto'!$K$8:$K$1107`. Se usan desde otras hojas.

### rng
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: rango absoluto de una columna dentro de la misma hoja de registro.
- Cómo funciona: `rng("AI")` → `$AI$8:$AI$1107`. Reemplaza dos lambdas idénticas que existían antes.

### f_turno
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: fórmula que normaliza el turno escrito (am, a.m., "PM ") a "AM" o "PM".
- Cómo funciona: devuelve "?" si el texto es inválido y "" si está vacío. Es la misma fórmula en Auto (aux_turno) y en Delivery.

### f_fecha
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: fórmula que devuelve la fecha como entero si es real y está entre 2020 y 2099.
- Cómo funciona: devuelve "" si no lo es (texto, error, 1900, años absurdos) y quita la hora si la trae. Es la misma en Auto y Delivery.

### dv_fecha
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: validación de fecha estándar con cartel "Escríbela así: …".
- Cómo funciona: recibe un ejemplo y textos extra para el cartel y el error. Unifica cinco validaciones que estaban repetidas.

### ini, pan, au, de, ho, rp, pe, ca, ins
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: hojas Inicio, Panel, Registro Auto, Registro Delivery, Horarios, Reportes, Personal, Calculos (oculta) e Instrucciones.
- Cómo funciona: objetos `Worksheet`. Todas quedan protegidas con `protect()`.

### botones, leyenda
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: contenido de la hoja Inicio: botones de navegación y leyenda de colores.
- Cómo funciona: listas de tuplas (texto, destino, descripción) y (relleno, color, texto).

### cols
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: definición de columnas de Registro Auto (letra, encabezado, ancho, tipo in/calc/aux).
- Cómo funciona: B–Q visibles; R–AX auxiliares ocultas. Las relevantes son: clave índice AI; AM del día AJ/AK/AL; segundos AM/AN; columnas prefiltradas AO–AQ y AA; estadísticas de tríos AR–AW; banderas Z y AX.

### generar_ejemplos, EJ_AUTO, EJ_DEL
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: genera los datos inventados del Modo aprendizaje: 4 semanas de marzo 2021 con nombres "(ej.)". Ana es claramente más rápida y Carla más lenta; incluye un PM "dudoso", dos filas con errores típicos y casos de Delivery (No, sin enlace, respuesta inválida).
- Cómo funciona: semilla fija (siempre los mismos datos). Retorna filas (Auto, Delivery) con su explicación en Observaciones. Se escriben en la zona reservada R_EJ0..R1 como fórmulas de `f_ej`.

### v_turno, v_pers, v_autos, v_min, v_seg, v_fecha, v_turno2, v_sn, v_fecha2, v_link, v_fecha3, v_tipo, v_rf, v_nom, pv
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: validaciones de datos de cada hoja (listas, números, fechas) con cartel de ayuda.
- Cómo funciona: creadas con `dv`/`dv_fecha` y asignadas a rangos de entrada. `pv` agrupa las del Panel.

### dcols, au_note
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: columnas de Registro Delivery y su nota explicativa.
- Cómo funciona: incluye la columna visible Estado (G) y las auxiliares I–P (incluida la clave índice P).

### N_FR, PASTE_H, FR0, FR_H
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: geometría de Horarios: 52 marcos, 45 filas pegables cada uno, primer marco en la fila 13 y altura total del marco.
- Cómo funciona: la zona de pegado (B–M) queda desbloqueada y los títulos bloqueados.

### RP1, rcols
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: última fila y columnas de la hoja Reportes.
- Cómo funciona: 300 filas. La columna Abrir crea el enlace solo si el texto empieza con http.

### P0, P1, PB
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: rango de la lista de Personal (filas 8 a 107) y su referencia absoluta.
- Cómo funciona: lo usan los desplegables, los avisos y la detección de nombres fuera de la lista.

### roles
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: estaciones del ranking (Cajero, Board, Corredor) con su columna prefiltrada (AO/AP/AQ), su columna de residuo justo (BL/BM/BN) y la columna inicial de su bloque en Calculos (6, 20, 34).
- Cómo funciona: las estadísticas crudas y las del ajuste usan COUNTIF/SUMIF de una condición sobre la columna prefiltrada.

### aviso
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: fórmula del aviso rojo del Panel (Calculos!B9).
- Cómo funciona: concatena mensajes de filtros inválidos, filas por revisar (suma de AX), nombres fuera de la lista (AH), nombres repetidos, filas sin fórmulas y filas libres. Cada parte va con IFERROR.

### filtros
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: posiciones y etiquetas de los filtros del Panel.
- Cómo funciona: celdas combinadas en la fila 7, desbloqueadas.

### AUTO_OK, DEL_OK
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: condiciones de fórmula para mostrar u ocultar indicadores según el filtro Canal.
- Cómo funciona: `fCanal<>"Delivery"` y `fCanal<>"Auto"`.

### X, K_, Y_, N_, auto_t, d_turn, VP, C_
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: rangos frecuentes del Panel (KPI, autos, producto, cumple, flag de delivery válido, flag con persona, turno) y la fórmula del tiempo ponderado.
- Cómo funciona: cadenas de referencia reutilizadas para no repetir texto.

### card
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: dibuja una tarjeta de indicador en el Panel (etiqueta, valor grande, subtítulo).
- Cómo funciona: combina celdas de 3 columnas × 4 filas y retorna la celda del valor.

### c_autos, c_t, c_pct, c_del, c_dias, c_niv, c_ta, c_td
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: celdas de valor de las 8 tarjetas del Panel.
- Cómo funciona: devueltas por `card`. Se usan para el formato condicional.

### section
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: título de sección del Panel con línea inferior y nota opcional.
- Cómo funciona: combina B:M en la fila dada.

### crit
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: criterios de la tabla AM vs PM vs Total.
- Cómo funciona: devuelve `aux_validoPersona=1` más el turno (`"*"` para Total).

### rows_cmp, CMP_END, RK_END, heads, TS, TR_END, DS
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: estructura del Panel: filas de comparación, encabezados, columna de puntaje justo de tríos (AW), de días (AB) y filas finales de cada sección.
- Cómo funciona: los rankings toman el k-ésimo menor puntaje con SMALL y su fila con MATCH.

### bloques
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: texto de la hoja Instrucciones (secciones 0 a 11).
- Cómo funciona: lista de (número, título, viñetas).

### SCRIPTS
- Ubicación: entregables/pruebas_excel/harness.py
- Qué hace: carpeta de los scripts de recálculo con LibreOffice.
- Cómo funciona: se busca con glob en la instalación de skills.

### R0 (harness), ERRS
- Ubicación: entregables/pruebas_excel/harness.py
- Qué hace: primera fila de datos (8) y prefijos de valores de error de Excel/LibreOffice.
- Cómo funciona: los usan `build_case` y `scan_errors`.

### recalc
- Ubicación: entregables/pruebas_excel/harness.py
- Qué hace: recalcula un .xlsx con LibreOffice.
- Cómo funciona: ejecuta `recalc.py` y retorna su JSON. Escribe los valores calculados en el mismo archivo.

### secs
- Ubicación: entregables/pruebas_excel/harness.py
- Qué hace: convierte un tiempo leído (timedelta, time o fracción de día) a segundos.
- Cómo funciona: retorna None si no es un tiempo.

### fecha
- Ubicación: entregables/pruebas_excel/harness.py
- Qué hace: normaliza un datetime a date.
- Cómo funciona: deja igual cualquier otro tipo.

### build_case
- Ubicación: entregables/pruebas_excel/harness.py
- Qué hace: prepara un caso de prueba: escribe filas de Auto, Delivery, Personal y filtros, guarda, recalcula y relee.
- Cómo funciona: retorna (JSON de recalc, libro con valores). `extra(wb)` permite cambios adicionales; `modo_aprendizaje` ("No" por defecto) fija el interruptor de Inicio. Rechaza más de 1.030 filas (las demás están reservadas para los ejemplos). Escribe el archivo `out`.

### scan_errors
- Ubicación: entregables/pruebas_excel/harness.py
- Qué hace: lista las celdas que muestran un valor de error.
- Cómo funciona: recorre las hojas indicadas y retorna "Hoja!Celda=valor".

### FAR
- Ubicación: entregables/pruebas_excel/modelo.py
- Qué hace: valor centinela para rangos de fecha abiertos.
- Cómo funciona: un número muy grande.

### nivel
- Ubicación: entregables/pruebas_excel/modelo.py
- Qué hace: nivel de un tiempo en segundos según los mismos cortes del Excel.
- Cómo funciona: redondea y retorna el texto del nivel.

### filas
- Ubicación: entregables/pruebas_excel/modelo.py
- Qué hace: modelo independiente en Python del cálculo por fila (autos del turno, tiempo real de PM, nivel, resultado del día).
- Cómo funciona: aplica las reglas de fila repetida y de AM repetido. Retorna una lista de dicts.

### panel
- Ubicación: entregables/pruebas_excel/modelo.py
- Qué hace: calcula lo que el Panel debería mostrar (tarjetas, AM/PM, rankings sin tope, mejores días) para unos filtros.
- Cómo funciona: es la referencia contra la que se comparan las simulaciones.

### TEMPLATE, NAMES, out / res_list / fails, checks
- Ubicación: entregables/pruebas_excel/test_*.py, bench.py
- Qué hace: plantilla a probar, nombres de prueba y acumuladores de resultados de cada batería.
- Cómo funciona: TEMPLATE se lee de argv. Los acumuladores se imprimen al final como ✓/✗.

### check
- Ubicación: entregables/pruebas_excel/test_errores.py, test_e2e.py, test_torpe.py, test_uno.py
- Qué hace: registra una comprobación con nombre, si pasó, y su detalle.
- Cómo funciona: agrega una tupla al acumulador de la batería.

### eq, gen, run
- Ubicación: entregables/pruebas_excel/test_sim.py
- Qué hace: comparar un valor con tolerancia, generar un historial aleatorio y correr un escenario de filtros completo contra el modelo.
- Cómo funciona: `run` construye el caso y compara registro fila a fila, tarjetas, tabla AM/PM, rankings y mejores días.

### casos, panel_cases, deliv, base_rows
- Ubicación: entregables/pruebas_excel/test_errores.py, test_torpe.py
- Qué hace: catálogos de errores provocados (filas absurdas, fórmulas =1/0, filtros mal escritos) con su comprobación esperada.
- Cómo funciona: cada caso es (descripción, fila, función que valida Nivel/tiempo).

### formula_errors, USER_FORMULAS
- Ubicación: entregables/pruebas_excel/test_torpe.py
- Qué hace: detecta errores solo en fórmulas del archivo e ignora las que escribió el "usuario".
- Cómo funciona: compara el libro de fórmulas con el de valores.

### extra
- Ubicación: entregables/pruebas_excel/test_e2e.py, test_torpe.py
- Qué hace: modificaciones adicionales al libro antes de recalcular (pegar horario, reportes, nombres repetidos).
- Cómo funciona: recibe el libro y lo modifica en memoria.

### PV, load, kpis, has_errors, unprot
- Ubicación: entregables/pruebas_excel/test_uno.py
- Qué hace: utilidades UNO: propiedad, abrir y calcular un documento, leer indicadores, buscar errores y quitar la protección.
- Cómo funciona: controlan LibreOffice por tubería. Desactivan el cálculo en paralelo, que da un falso Err:522.

### wb (ciclos), names, GROUPED, G
- Ubicación: entregables/pruebas_excel/ciclos.py
- Qué hace: libro analizado, nombres definidos, hojas cuyas filas se agrupan por columna y grafo de dependencias.
- Cómo funciona: G mapea nodo → nodos referenciados.

### node, refs, dfs
- Ubicación: entregables/pruebas_excel/ciclos.py
- Qué hace: nombrar un nodo, extraer las referencias de una fórmula y buscar ciclos en profundidad.
- Cómo funciona: imprime la cantidad de ciclos reales. 0 = sin referencias circulares.

### fill_full, timed, acts
- Ubicación: entregables/pruebas_excel/bench.py
- Qué hace: llenar el archivo al máximo (1.100 filas por registro y 30 personas), medir una acción y definir las acciones típicas.
- Cómo funciona: mide con `calculate()` de LibreOffice (mediana de 5) y guarda `bench_<etiqueta>.json`.

### dataset, snapshot, same
- Ubicación: entregables/pruebas_excel/test_equivalencia.py
- Qué hace: generar el mismo año de datos para dos versiones, tomar todos los valores visibles y compararlos con tolerancia.
- Cómo funciona: reporta las diferencias celda por celda entre la versión anterior y la optimizada.

### Z95
- Ubicación: entregables/pruebas_excel/build.py, entregables/pruebas_excel/modelo.py
- Qué hace: factor 1,96 del margen del 95 %.
- Cómo funciona: margen = Z95 × raíz(s2/(n+k)).

### K_SIN_DATOS, K_TOPE
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: fuerza del ajuste bayesiano, en turnos: 5 cuando no se puede estimar (menos de 2 grupos) y 20 como tope.
- Cómo funciona: los usa `eb_resumen` para calcular k.

### DUDOSO, DUDOSO_SEG
- Ubicación: entregables/pruebas_excel/build.py (DUDOSO también en modelo.py)
- Qué hace: sufijo " · dudoso (PM con pocos autos)" y umbral de 5 s de incertidumbre por redondeo.
- Cómo funciona: Nivel agrega el sufijo si aux_margenPM ≥ 5 y el tiempo real queda a menos de esa incertidumbre de 4:00.

### FMT_AJ, FMT_MG
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: formatos "+12.3 s" para el puntaje ajustado y "±4.5 s" para el margen.
- Cómo funciona: se asignan a `number_format` en los rankings.

### eb_resumen
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: escribe en Calculos el ajuste bayesiano empírico (ANOVA de un factor, estimador de momentos) de un conjunto de grupos.
- Cómo funciona: recibe columna, fila inicial y rangos de n, suma de residuos, suma²/n, suma de cuadrados (o None) y n². Usa solo grupos con n ≥ fMin. `s2_externo` permite pasar una varianza dentro común (las estaciones la calculan con SUMSQ sobre todos sus turnos comparables). Retorna las direcciones de s2 y de k = s2/tau2 (con tope).

### lectura_f
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: fórmula de la lectura en palabras de un puntaje y su margen.
- Cómo funciona: devuelve "Más rápido que lo normal" si puntaje+margen < 0, "Más lento…" si puntaje−margen > 0, "Dentro de lo normal" en otro caso y "Pocos datos" si no hay margen.

### CTX0, ctxN, ctxBase
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: tabla de los 14 contextos (AM/PM × día de la semana) en Calculos!A110:E123, con nombres definidos para los turnos programados y "lo normal".
- Cómo funciona: ctxN = COUNTIF de aux_contexto (no depende de los tiempos). ctxBase = promedio de segundos de los turnos válidos del contexto.

### RCOLS
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: columnas de cada bloque de estación en Calculos.
- Cómo funciona: nombre, turnos, autos, prod, tiempo, pct (datos crudos para mostrar); ajustado, comparables, sumaR, c, n2, margen, lectura (ajuste justo para ordenar).

### TRIO_S2, TRIO_K, kTrio
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: varianza dentro y fuerza del ajuste para los tríos (Calculos!A127…). kTrio es su nombre definido.
- Cómo funciona: los calcula `eb_resumen` sobre las filas representativas del registro (BV, BW, BX, BY, BZ).

### RANK_HEADS, CF_LECT
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: encabezados de las tablas de ranking por estación y colores de la columna Lectura (verde más rápido, rojo más lento).
- Cómo funciona: se aplican a las tres tablas apiladas (Cajero, Board, Corredor).

### columnas AY–BZ de Registro Auto (comparación justa)
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: aux_contexto (1–14), aux_desvio (seg − lo normal de todo el historial), claves contexto×persona (contexto·1000 + índice en Personal) y contexto×trío, conteos (las personas consultan TABLA_CTX y el trío usa COUNTIF), residuos "contra los demás" r = desvío·N/(N−n), cuadrado del residuo del trío, segundos válidos, margen de redondeo del PM y estadísticas por trío. BA–BC y BP–BR quedan reservadas sin fórmula.
- Cómo funciona: los conteos dependen solo de fechas, turnos, nombres y filtro de fechas, no de los tiempos, así que escribir minutos o segundos no los recalcula.

### lectura (modelo)
- Ubicación: entregables/pruebas_excel/modelo.py
- Qué hace: misma regla de lectura que `lectura_f`, en Python.
- Cómo funciona: retorna el texto según puntaje y margen.

### bayes
- Ubicación: entregables/pruebas_excel/modelo.py
- Qué hace: referencia independiente del ajuste bayesiano empírico por turno (efectos aleatorios, estimador de momentos).
- Cómo funciona: recibe (orden, clave, filas comparables con res, todas las filas) y opcionalmente `s2_comun=(s2,)`, la varianza dentro común de la estación. Retorna dicts con score, margen, lectura, n, tiempo y pct, ordenados por score y orden.

### talento, base (test_verdad)
- Ubicación: entregables/pruebas_excel/test_verdad.py
- Qué hace: talento real fijado por persona (segundos) y dificultad de cada contexto, para simular auto por auto con verdad conocida.
- Cómo funciona: P01 es lento y solo trabaja turnos fáciles; P02 es rápido y solo trabaja turnos difíciles. Se comprueba el tiempo real de PM contra la cota de redondeo y el ranking contra el talento real.

### spearman, ranking_justo, SRC
- Ubicación: entregables/pruebas_excel/valida_metodo.py
- Qué hace: correlación de rangos, ranking justo en Python y fuente del generador de datos de test_verdad.
- Cómo funciona: valida el método en 12 semillas (correlación, confusores, cobertura del margen y falsos positivos) sin usar Excel.

### DIA0, diaN, diaBase
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: tabla de "lo normal" del resultado final del día para cada día de la semana (Calculos!A140:D146), con nombres definidos para cantidad de días y promedio.
- Cómo funciona: usa todo el historial (columnas CA y CB del registro). La usa aux_diaVsNormal.

### columnas CA–CC de Registro Auto (mejores días justos)
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: aux_diaSemana (1–7), aux_diaSeg (resultado del día en segundos) y aux_diaVsNormal (cuánto mejor o peor fue el día frente a lo normal de su día de la semana, sin incluirse a sí mismo).
- Cómo funciona: aux_diaScore (AB) ordena "Mejores días" con este desvío, filtrado por el rango de fechas. Si ese día de la semana tiene un solo registro, queda vacío.

### TABLA_CTX_COL0, TABLA_CTX
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: tabla índice persona × contexto en Calculos (una por estación, 100 filas × 14 contextos, desde las columnas 50, 64 y 78) con los turnos programados de cada persona en cada contexto.
- Cómo funciona: cada casilla es un COUNTIF de la clave. Cada fila del registro consulta su casilla con INDEX(tabla, MOD(clave,1000), INT(clave/1000)), sin contar. Al cambiar un nombre se recalculan unas 420 casillas en lugar de 1.100 conteos.

### R_BANDA, R_EJ0, R1_REAL, N_REAL
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: geometría de la zona de ejemplos: fila separadora 1038, ejemplos 1039–1107, filas reales 8–1037 (1.030).
- Cómo funciona: solo las filas reales se desbloquean y tienen validaciones. Los avisos de filas libres y de filas sin fórmulas miran solo las reales.

### EJ_NAMES, EJ_OFF
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: nombres inventados de los ejemplos ("Ana (ej.)"…) y desplazamiento de 1.000.000 en las claves de las filas de ejemplo.
- Cómo funciona: con el desplazamiento, una fila real en la misma fecha y turno que un ejemplo nunca se considera repetida ni se usa como "su AM".

### rar, rdr
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: rango de una columna solo en las filas reales de Registro Auto / Delivery.
- Cómo funciona: lo usan los avisos (filas por revisar, sin fórmulas, filas libres).

### vacio
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: condición "celda vacía" robusta: vacía, solo espacios o fórmula que devuelve "" (ejemplos ocultos).
- Cómo funciona: IFERROR(LEN(TRIM(c&""))=0, FALSE). Un error escrito por el usuario no cuenta como vacío. Reemplaza a ISBLANK en las fórmulas por fila.

### si_aprende, f_ej
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: fórmulas que muestran un texto (o un valor de ejemplo) solo con Modo aprendizaje en Sí.
- Cómo funciona: =IF(fAprende=1, valor, ""). f_ej convierte fechas a DATE(), números y textos.

### fAprende, v_apr
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: interruptor Modo aprendizaje (Inicio!C6, lista Sí/No) y su lectura en Calculos!B8 (1 = Sí, 0 = No).
- Cómo funciona: cuenta como No todo lo que empiece con N ("no", " NO ", "N", "nop", "No.") y el 0. Lo demás, incluso vacío, texto raro o error, cuenta como Sí. Con 1 se ven ejemplos y explicaciones y el Panel usa solo filas de ejemplo; con 0 todo eso se vuelve "" y el Panel usa solo filas reales. Inicio!E7 muestra el estado en palabras.

### aux_ejemplo (Registro Auto CD, Registro Delivery Q)
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: marca constante 1 en las filas de la zona de ejemplos.
- Cómo funciona: las fórmulas comparan N(marca)=fAprende para decidir si la fila entra al modo actual (U, contexto, día de semana, Delivery en rango) y para excluir ejemplos de los avisos.

### GUIA_AUTO, GUIA_DEL, EXPL_CARD, DIAS_R0
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: textos de guía bajo cada encabezado de los registros, explicación de cada tarjeta del Panel y fila inicial de "Mejores días" (para la línea "Así se lee").
- Cómo funciona: todos se muestran solo en Modo aprendizaje; las tarjetas vuelven a su subtítulo normal con No.

### check (test_aprendizaje), reales, deliv
- Ubicación: entregables/pruebas_excel/test_aprendizaje.py
- Qué hace: prueba del Modo aprendizaje con datos reales en las mismas fechas que los ejemplos.
- Cómo funciona: verifica que Sí muestre solo ejemplos y No solo datos reales, que no haya choques ni repetidas, que las filas reales calculen igual en ambos modos, las explicaciones, el filtro con nombre de ejemplo y la protección de la zona.

### estado del interruptor (Inicio!E7)
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: dice en palabras en qué modo quedó el archivo ("MODO APRENDIZAJE ACTIVO" o "archivo LIMPIO").
- Cómo funciona: fórmula sobre fAprende; sirve cuando alguien escribe o pega algo raro en el interruptor.

### avisos de Modo aprendizaje (parte de `aviso`)
- Ubicación: entregables/pruebas_excel/build.py
- Qué hace: avisa de filas reales guardadas que no se ven en Sí, fechas filtradas sin turnos, persona que no corresponde al modo y datos escritos en la zona de ejemplos con el modo en No.
- Cómo funciona: usan SUMPRODUCT(LEN(…)>0) sobre las filas reales o la zona de ejemplos, SUM(aux_enRango) y COUNT(aux_contexto).

### FOTO, foto, modo_si, modo_no, escribir_ui, pegar, errores (test_aprendizaje_e2e)
- Ubicación: entregables/pruebas_excel/test_aprendizaje_e2e.py
- Qué hace: prueba de extremo a extremo del Modo aprendizaje en LibreOffice como tester, usuario torpe y destructor.
- Cómo funciona: escribir_ui escribe como un usuario (respeta la protección) y pegar salta la lista desplegable como un Ctrl+V. foto toma las celdas clave para comparar estados; modo_si y modo_no verifican cada estado. Cambia el interruptor en ambos sentidos y con variantes escritas o pegadas.

### test_vida_real (abrir, escribir, pegar, leer_auto, leer_deliv, personal, filtros, igual, verificar_panel, verificar_ejemplos, anotar_dia, turno_datos, paso)
- Ubicación: entregables/pruebas_excel/test_vida_real.py
- Qué hace: prueba del ciclo de vida real del archivo en LibreOffice, en 12 pasos: Modo aprendizaje, desactivarlo, cargar personal y datos escribiendo como usuario, editar, borrar y reponer, errores y su corrección, completar un mes, filtros, cambios en Personal, activar y desactivar con datos reales, Horarios y Reportes, guardar y reabrir.
- Cómo funciona: después de cada paso, `verificar_panel` lee lo que hay escrito (leer_auto, leer_deliv, personal, filtros), calcula el Panel con `modelo.panel` y lo compara celda por celda (tarjetas, AM/PM, rankings con ajuste, margen y lectura, mejores días). También revisa que ninguna hoja muestre errores. `escribir` respeta protección y listas; `pegar` simula Ctrl+V.
