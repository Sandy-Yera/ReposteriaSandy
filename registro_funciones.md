# Registro de funciones y variables

Ver `CLAUDE.md` para las reglas de uso de este archivo.
Cada entrada nueva va al final de su sección, con el mismo formato.

> **Estado actual: todo lo de abajo está ESPECIFICADO en `arquitectura.md`, pero todavía NO
> IMPLEMENTADO** — el repositorio aún no tiene código Kotlin (la Fase 0 no ha empezado).
> Las rutas indican dónde va a vivir cada cosa según la sección 4, no un archivo que ya exista.
>
> Esto importa para el paso 2 de `CLAUDE.md`: al revisar si algo ya existe, una entrada de acá
> significa "ya está diseñado, reutiliza este diseño", no "ya está escrito, ábrelo y léelo".
> **Cuando cada fase implemente estas funciones de verdad, hay que volver a esta entrada y
> confirmar que la firma real coincide con la documentada** — si cambió, se actualiza acá en el
> mismo cambio, no después.

Índice rápido: [Lógica pura](#logica-pura) · [Acceso a datos](#acceso-a-datos) · [Orquestación](#orquestacion) · [Tipos de datos](#tipos-de-datos)

La división en tres grupos es la de la sección 6.5 de `arquitectura.md`. La regla práctica para
saber en cuál va algo nuevo: **si tiene `suspend` en la firma, no es lógica pura.**

**Qué NO está acá, a propósito:** las entidades que son tablas de la base (`Ingrediente`, `Receta`,
`RecetaPrecio`, `Molde`, `RecetaRendimiento`, `RecetaSeccion`, `RecetaIngrediente`, `RecetaDuracion`,
`RecetaPaso`, `RecetaSimulacionVenta`, `Empleado`, `EmpleadoRecetaSueldo`, `EmpleadoSimulacionMultiple`,
`EmpleadoSimulacionMultipleDetalle`, `EventoCambio`). Están completas en la sección 5 de
`arquitectura.md` —con sus columnas, claves foráneas, cascadas e índices—, y copiarlas acá solo
lograría que las dos versiones se desincronicen. **Para cualquier cosa relacionada con tablas,
la sección 5 es la fuente.** Sí están registrados los tipos que *no* son tablas (`DatosCalculoReceta`,
`Sueldo`, los `enum`…), porque esos son los que alguien podría reinventar sin darse cuenta.

---

<a name="logica-pura"></a>
## Lógica pura (`logica/`) — sin Android, sin Room, sin `suspend`

### formatearNumero
- Ubicación: logica/Formato.kt
- Qué hace: convierte un número a texto con el formato de la app — punto para los miles, coma para los decimales, y sin coma cuando no hay decimales.
- Cómo funciona: recibe un `Double`, redondea a 2 decimales y devuelve `String`. Trabaja sobre el valor absoluto y pega el signo al final, porque `(-0.56).toInt()` da 0 y perdería el "-" (mostraría una pérdida como ganancia). Fija `Locale.US` para que el separador de miles sea predecible y no dependa del idioma del celular. Ej: `1000.0` → `"1.000"`, `-1234.56` → `"-1.234,56"`.

### coincide
- Ubicación: logica/Busqueda.kt
- Qué hace: dice si un texto buscado aparece en cualquier parte de un campo, ignorando mayúsculas y tildes.
- Cómo funciona: recibe `textoBusqueda` y `campo` (ambos `String`), devuelve `Boolean`. Pasa los dos por `sinTildes` antes de comparar, para que "limon" encuentre "Mousse de limón". Lo usan las 4 pantallas con buscador (12.2).

### sinTildes
- Ubicación: logica/Busqueda.kt
- Qué hace: quita los acentos de un texto dejando la letra base ("plátano" → "platano").
- Cómo funciona: recibe `String` y devuelve `String`. Normaliza a NFD (separa la letra de su acento) y borra los caracteres de marca con `Regex("\\p{Mn}+")`. Es `private`: solo la usa `coincide` dentro del mismo archivo.

### pesoPorTrozo
- Ubicación: logica/Rendimiento.kt
- Qué hace: calcula cuánto pesa cada trozo dividiendo el peso final del producto entre la cantidad de trozos.
- Cómo funciona: recibe `pesoFinalG: Double?` y `trozos: Int`, devuelve `String` ya formateado. Si `pesoFinalG` es `null` devuelve el texto `"No especificado"` en vez de un número — por eso retorna `String` y no `Double`. No valida `trozos`: la regla `trozos >= 1` la garantiza la validación de 6.2.

### factorEscala
- Ubicación: logica/Moldes.kt
- Qué hace: calcula por cuánto hay que multiplicar cada ingrediente al pasar una receta de un molde a otro.
- Cómo funciona: recibe `original` y `nuevo` (ambos `DimensionesMolde`) más el `modo`, devuelve `Double`. En `ALTURA` divide áreas y **lanza excepción** si el molde nuevo es más bajo que el original; en `CAPACIDAD` divide volúmenes sin esa restricción. Divide por los datos del molde original, así que depende de que la validación de 6.2 haya exigido medidas `> 0`.

### trozosCubiertosPor
- Ubicación: logica/Precios.kt
- Qué hace: dice cuántos trozos cubre un precio guardado, que no es lo mismo según sea precio por trozo o por producto completo.
- Cómo funciona: recibe un `RecetaPrecio` y el snapshot `DatosCalculoReceta`, devuelve `Int`. Si el modo es `TROZO` devuelve la cantidad tal cual; si es `PRODUCTO` la multiplica por los trozos de la receta (2 productos completos de 8 trozos = 16 trozos).

### precioPorTrozoDe
- Ubicación: logica/Precios.kt
- Qué hace: lleva cualquier precio guardado a su equivalente por trozo, para poder compararlos entre sí.
- Cómo funciona: recibe un `RecetaPrecio` y el snapshot, devuelve `Double`. Divide `precioTotal` por `trozosCubiertosPor`. Depende de la validación `cantidad >= 1` de 6.2 para no dividir por cero.

### costoPorTrozo
- Ubicación: logica/Precios.kt
- Qué hace: reparte el costo total de la receta entre sus trozos.
- Cómo funciona: recibe el snapshot y devuelve `Double` (`costoTotal / trozos`). Es constante para todos los precios de una misma receta, y esa es justamente la razón de que exista el snapshot: antes se recalculaba una vez por cada precio.

### gananciaPorTrozoDe
- Ubicación: logica/Precios.kt
- Qué hace: cuánto se gana por trozo con un precio guardado concreto.
- Cómo funciona: recibe un `RecetaPrecio` y el snapshot, devuelve `Double` (`precioPorTrozoDe - costoPorTrozo`). Puede ser negativo si ese precio no cubre el costo, y así debe mostrarse.

### precioDeMenorGanancia
- Ubicación: logica/Precios.kt
- Qué hace: de todos los precios y promociones guardados de una receta, elige el que deja menos ganancia — el peor caso, que es con el que se juega (decisión #4).
- Cómo funciona: recibe el snapshot y devuelve el `RecetaPrecio` ganador. **Lanza excepción si la receta no tiene ningún precio guardado**; quien la llame en un contexto agregado (simulación múltiple) debe filtrar antes con `precios.isEmpty()`, porque si no una receta a medio configurar voltea el total completo.

### precioEfectivoPorTrozo
- Ubicación: logica/Precios.kt
- Qué hace: el precio por trozo que alimenta todos los campos automáticos de la app.
- Cómo funciona: recibe el snapshot, devuelve `Double`. Es `precioPorTrozoDe` aplicado al resultado de `precioDeMenorGanancia`, así que hereda su excepción cuando no hay precios.

### trozoGanador
- Ubicación: logica/Precios.kt
- Qué hace: dice a partir de qué trozo vendido la receta deja de perder plata y empieza a ganarla.
- Cómo funciona: recibe el snapshot y devuelve un `TrozoGanador` con el número, la ganancia en ese punto, y si es **alcanzable**. Lo último importa: si hacen falta 11 trozos en una receta que rinde 8, el número existe pero es imposible y la pantalla debe mostrar la advertencia en vez del dato.

### ingresoBruto
- Ubicación: logica/Precios.kt
- Qué hace: lo que entra al vender el producto completo, sin descontar nada.
- Cómo funciona: recibe el snapshot, devuelve `Double` (`precioEfectivoPorTrozo × trozos`). Es la base del cálculo de sueldos (10.1) y de la simulación (8.7).

### gananciaPorTrozo
- Ubicación: logica/Precios.kt
- Qué hace: la ganancia por trozo al precio vigente de menor ganancia.
- Cómo funciona: recibe el snapshot, devuelve `Double`. Puede ser negativo. De solo lectura en la UI.

### gananciaFinal
- Ubicación: logica/Precios.kt
- Qué hace: la ganancia del producto completo.
- Cómo funciona: recibe el snapshot, devuelve `Double` (`ingresoBruto - costoTotal`). Puede ser negativo; se muestra con signo gracias a `formatearNumero`.

### simulacion
- Ubicación: logica/Simulacion.kt
- Qué hace: proyecta ingreso, costo y ganancia a una semana y a un mes, según cuántos días se vende y cuántas unidades por día.
- Cómo funciona: recibe `ingresoBase`, `costoBase`, `dias` y `unidades`; devuelve `SimulacionResultado` con las 6 cifras (3 semanales y 3 mensuales). Lo semanal es `base × dias × unidades` y lo mensual multiplica por `SEMANAS_POR_MES`.

### calcularSueldo
- Ubicación: logica/Sueldos.kt
- Qué hace: reparte el ingreso bruto de una receta entre lo que se lleva el dueño (costo + su parte de la ganancia) y lo que se lleva el empleado.
- Cómo funciona: recibe el snapshot y la `gananciaEmpleado` acordada, devuelve un `Sueldo`. **Lanza excepción en dos casos**: si la receta no cubre su costo (no hay ganancia que repartir) y si la ganancia pedida supera la ganancia total. El primer chequeo va antes a propósito: con ganancia total negativa el rango `0.0..gananciaTotal` queda vacío en Kotlin y el segundo `require` fallaría siempre con un mensaje que no explica nada.

### SEMANAS_POR_MES
- Ubicación: logica/Simulacion.kt
- Qué hace: cuántas semanas se cuentan por mes al proyectar cifras mensuales.
- Cómo funciona: constante `4.33`, que es 52 ÷ 12. La usan `simulacion` y las propiedades mensuales de `SimulacionMultipleResultado`.

---

<a name="acceso-a-datos"></a>
## Acceso a datos (`data/dao/`, `data/repositorio/`)

### costoTotalReceta
- Ubicación: data/db/dao/RecetaDao.kt
- Qué hace: suma cuánto cuesta hacer una receta, sumando cantidad × precio de cada ingrediente de todas sus secciones.
- Cómo funciona: `@Query` que devuelve `Double` para un `recetaId`. Hace la suma en SQL con un `JOIN` de tres tablas, no recorriendo desde Kotlin. Lleva `COALESCE(..., 0)` porque `SUM` sobre cero filas da `NULL` en SQLite y una receta nueva aún no tiene ingredientes. Lee `valorPorGramo` en el momento de la consulta, que es lo que exige la decisión #3 (nunca hay precios congelados).

### obtenerDatosCalculo
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: arma de una sola vez la foto de una o varias recetas (costo, trozos y precios) que después usan todas las fórmulas.
- Cómo funciona: `@Transaction suspend` que recibe una `List<Long>` de ids y devuelve `Map<Long, DatosCalculoReceta>`. Recibe lista y no un id suelto a propósito: la simulación múltiple pide todas sus recetas juntas y resuelve con 3 consultas (`WHERE recetaId IN`) en vez de 3 por receta. Para una sola receta se llama con una lista de un elemento.

### obtenerRecetasQueUsan
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: dice qué recetas usan un ingrediente dado.
- Cómo funciona: `suspend`, recibe `ingredienteId` y devuelve `List<Receta>`. Es la consulta que alimenta la advertencia antes de borrar un ingrediente (7.1). **No envolver en otra función con otro nombre** — ya existió un `recetasQueUsan` duplicado y se eliminó.

### obtenerRecetasConMoldeOrigen
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: dice qué recetas están enlazadas a un molde del catálogo.
- Cómo funciona: `suspend`, recibe `moldeId` y devuelve `List<Receta>`. La usa `actualizarMolde` para saber a quién propagarle una corrección de medidas. Se apoya en el índice de `moldeOrigenId`.

### actualizarDimensionesMolde
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: cambia las medidas del molde guardadas en una receta, sin tocar nada más.
- Cómo funciona: `suspend`, recibe `recetaId` y las nuevas `DimensionesMolde`. **No toca `moldeOrigenId` ni las cantidades de ingredientes**: es la que usa la sincronización cuando se corrige un molde del catálogo. Para reescalar de verdad existe `actualizarDimensionesYVinculoMolde`.

### actualizarDimensionesYVinculoMolde
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: cambia las medidas del molde de una receta **y además** a qué molde del catálogo queda enlazada.
- Cómo funciona: `suspend`, recibe `recetaId`, las nuevas `DimensionesMolde` y un `moldeOrigenId: Long?`. Si viene un id, la receta queda enlazada y empieza a recibir correcciones de ese molde; si viene `null` (modo prueba) queda desvinculada aunque antes tuviera vínculo. La usa `reescalarRecetaPorMolde`.

### RETENCION_HISTORIAL_MS
- Ubicación: data/repositorio/HistorialRepositorio.kt
- Qué hace: cuánto tiempo se guardan los eventos del historial de cambios antes de borrarse solos.
- Cómo funciona: constante de ~6 meses en milisegundos. Se aplica al registrar cada evento nuevo, borrando los más viejos que ese umbral. Evita que el archivo que se sube a Drive crezca para siempre por una tabla que casi no se consulta.

---

<a name="orquestacion"></a>
## Orquestación (`data/repositorio/`) — leen, calculan y escriben

### confirmarEliminacionIngrediente
- Ubicación: data/repositorio/IngredienteRepositorio.kt
- Qué hace: borra un ingrediente de verdad, después de que el usuario ya confirmó la advertencia.
- Cómo funciona: `suspend`, recibe `ingredienteId`. Lee el nombre **antes** de borrar (lo necesita el historial), quita las filas `RecetaIngrediente` que lo referencian, borra el ingrediente y registra un evento rojo indicando qué recetas se vieron afectadas. No vuelve a preguntar: la confirmación es responsabilidad de la UI (6.3). El costo de las recetas afectadas se reajusta solo, porque se calcula en vivo.

### actualizarMolde
- Ubicación: data/repositorio/MoldeRepositorio.kt
- Qué hace: corrige las medidas de un molde del catálogo y propaga esa corrección a todas las recetas enlazadas a él.
- Cómo funciona: `suspend`, recibe `moldeId` y las nuevas `DimensionesMolde`. Lee el nombre antes de actualizar (para el historial), guarda el molde, y llama a `actualizarDimensionesMolde` en cada receta vinculada. **No reescala ingredientes**: es una corrección del dato de referencia, no un cambio de molde. Registra un evento verde listando las recetas actualizadas.

### reescalarRecetaPorPeso
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: ajusta las cantidades de una receta **sin molde** (una salsa, por ejemplo) para que rinda un peso final distinto.
- Cómo funciona: `suspend`, recibe `recetaId` y el nuevo peso de referencia. Calcula el factor como peso nuevo ÷ peso actual y multiplica cada ingrediente, redondeando a 2 decimales. Depende de la validación `pesoFinalG > 0` de 6.2 para no dividir por cero. **No cambia `trozos`**: eso se ajusta aparte si se quiere.

### reescalarRecetaPorMolde
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: ajusta las cantidades de una receta **con molde** al pasarla a un molde distinto, conservando el grosor o la capacidad según el modo elegido.
- Cómo funciona: `suspend`, recibe `recetaId`, las `DimensionesMolde` nuevas, el `ModoReescalado` y el `moldeOrigenId` nuevo (o `null` si fue modo prueba). Aplica `factorEscala` a cada ingrediente y después guarda medidas y vínculo con `actualizarDimensionesYVinculoMolde`. **Lanza excepción si la receta todavía no tiene molde**: definir el molde por primera vez no es reescalar, ahí no hay original con qué comparar y solo se asigna.

### simulacionMultiple
- Ubicación: data/repositorio/EmpleadoRepositorio.kt
- Qué hace: suma lo que un empleado vendería y ganaría con **todas** sus recetas asignadas a la vez, no receta por receta.
- Cómo funciona: `suspend`, recibe `empleadoId` y devuelve `SimulacionMultipleResultado`. Hace toda la lectura al principio —días, detalle, sueldos y `obtenerDatosCalculo` en lote— y el bucle ya no toca la base. Las recetas sin precio guardado se saltan y se devuelven en `omitidas`, para que una receta a medio configurar no voltee el total. Ojo: usa el `diasPorSemana` **compartido** del empleado, que es distinto del que tiene cada `EmpleadoRecetaSueldo` por separado.

---

<a name="tipos-de-datos"></a>
## Tipos de datos

### DatosCalculoReceta
- Ubicación: data/db/entidades/DatosCalculoReceta.kt
- Qué hace: la foto de una receta —id, título, costo total, trozos y precios— que se lee una vez y se le pasa a todas las fórmulas.
- Cómo funciona: `data class` que **no es una tabla**, se arma en memoria con `obtenerDatosCalculo`. Es lo que permite que las funciones de `logica/` sean puras y probables sin base de datos, y que todas las cifras de una pantalla salgan de la misma lectura.

### DimensionesMolde
- Ubicación: data/db/entidades/DimensionesMolde.kt
- Qué hace: guarda la geometría de un molde (forma y medidas) y calcula solo su área y su volumen.
- Cómo funciona: `data class` compartido vía `@Embedded` entre `Molde` y `RecetaRendimiento`. **Todos sus campos son nulables por obligación técnica**, no de diseño: se embebe como nulable en las recetas sin molde y Room no admite subcampos no-nulos ahí. La obligatoriedad real la impone la validación de 6.2. Expone `areaCm2` y `volumenCm3` como propiedades calculadas; ambas usan `!!` y fallan ruidosamente si el molde es inválido.

### TrozoGanador
- Ubicación: logica/Precios.kt
- Qué hace: el resultado de `trozoGanador` — qué número de trozo cubre el costo, cuánto se gana ahí, y si ese trozo existe de verdad.
- Cómo funciona: `data class` con `numero: Int`, `ganancia: Double` y `alcanzable: Boolean`. El tercer campo evita mostrar "trozo ganador: 11" en una receta que solo rinde 8.

### Sueldo
- Ubicación: logica/Sueldos.kt
- Qué hace: el reparto del ingreso de una receta entre el dueño y el empleado.
- Cómo funciona: `data class` con `ingresoBruto`, `yoMeLlevo` y `gananciaEmpleado`. `yoMeLlevo` incluye el costo total más la parte de la ganancia que no se lleva el empleado.

### SimulacionResultado
- Ubicación: logica/Simulacion.kt
- Qué hace: las 6 cifras que devuelve una simulación de ventas: ingreso, costo y ganancia, en versión semanal y mensual.
- Cómo funciona: `data class` de 6 `Double`. Lo mensual ya viene multiplicado por `SEMANAS_POR_MES`, no hay que volver a hacerlo al mostrarlo.

### SimulacionMultipleResultado
- Ubicación: logica/Sueldos.kt
- Qué hace: el total de una simulación con varias recetas a la vez, en versión diaria, semanal y mensual, más la lista de las que quedaron fuera.
- Cómo funciona: `data class` que guarda solo las 3 cifras **diarias** más `diasPorSemana` y `omitidas: List<String>` (títulos de recetas sin precio). Las 6 cifras semanales y mensuales son propiedades calculadas: guardar las tres versiones permitiría que quedaran desincronizadas entre sí.

### ModoPrecio
- Ubicación: data/db/entidades/RecetaPrecio.kt
- Qué hace: distingue si un precio guardado es por trozo o por producto completo.
- Cómo funciona: `enum` con `TROZO` y `PRODUCTO`. Es enum y no `String` a propósito: un typo en un texto libre no lo detecta el compilador. Necesita `TypeConverter` y se guarda por nombre, no por ordinal.

### ModoReescalado
- Ubicación: logica/Moldes.kt
- Qué hace: distingue las dos formas de reescalar una receta a otro molde.
- Cómo funciona: `enum` con `ALTURA` (conserva el grosor comparando áreas; exige que el molde nuevo no sea más bajo) y `CAPACIDAD` (conserva el volumen, sin restricción de altura).

### TipoFormaMolde
- Ubicación: data/db/entidades/DimensionesMolde.kt
- Qué hace: la forma de un molde, que determina qué medidas se piden y con qué fórmula se saca el área.
- Cómo funciona: `enum` con `RECTANGULO`, `CIRCULO`, `CUADRADO`, `TRIANGULO` y `EXOTICO`. El último es para formas irregulares, donde el volumen se mide llenando el molde con agua en vez de calcularlo. Necesita `TypeConverter`.

### TipoEvento
- Ubicación: data/db/entidades/EventoCambio.kt
- Qué hace: si un evento del historial fue una creación, una edición o una eliminación.
- Cómo funciona: `enum` con `CREACION` (azul), `EDICION` (verde) y `ELIMINACION` (rojo). Necesita `TypeConverter`.

### Convertidores
- Ubicación: data/db/Convertidores.kt
- Qué hace: le enseña a Room a guardar y leer los enums, que por sí solo no sabe manejar.
- Cómo funciona: clase con pares de `@TypeConverter` por cada enum, registrada con `@TypeConverters` en `AppDatabase`. Convierte a texto con `.name` y de vuelta con `valueOf`. **Por nombre y no por ordinal a propósito**: si algún día se agrega un valor en medio del enum, los ordinales ya guardados cambiarían de significado en silencio.
