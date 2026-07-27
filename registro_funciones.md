# Registro de funciones y variables

Ver `CLAUDE.md` para las reglas de uso de este archivo.
Cada entrada nueva va al final de su sección, con el mismo formato.

> **Cómo leer el estado de cada entrada:**
>
> - **`✅ IMPLEMENTADA`** — el archivo existe y compila. La ruta es real: ábrelo y léelo, tal
>   como pide el paso 2 de `CLAUDE.md`.
> - **Sin marca** — está solo ESPECIFICADA en `arquitectura.md`. La ruta indica dónde va a
>   vivir según la sección 4, pero ese archivo todavía no existe. Para el paso 2 de `CLAUDE.md`
>   significa "ya está diseñado, reutiliza este diseño", no "ábrelo".
>
> **Al implementar cada función hay que volver a su entrada, marcarla y confirmar que la firma
> real coincide con la documentada.** Si cambió, se actualiza acá en el mismo cambio, no después.

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

### formatearNumero ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/formato/Formato.kt
- Qué hace: convierte un número a texto con el formato de la app — punto para los miles, coma para los decimales, y sin coma cuando no hay decimales.
- Cómo funciona: recibe un `Double`, redondea a 2 decimales y devuelve `String`. Trabaja sobre el valor absoluto y pega el signo al final, porque `(-0.56).toLong()` da 0 y perdería el "-" (mostraría una pérdida como ganancia). Fija `Locale.US` para que el separador de miles sea predecible y no dependa del idioma del celular. Ej: `1000.0` → `"1.000"`, `-1234.56` → `"-1.234,56"`. Cubierta por `FormatoTest` (9 casos, incluidos negativos y redondeos que llegan a entero).

### coincide ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/busqueda/Busqueda.kt
- Qué hace: dice si un texto buscado aparece en cualquier parte de un campo, ignorando mayúsculas y tildes.
- Cómo funciona: recibe `textoBusqueda` y `campo` (ambos `String`), devuelve `Boolean`. Pasa los dos por `sinTildes` antes de comparar, para que "limon" encuentre "Mousse de limón". Lo usan las 4 pantallas con buscador (12.2).

### sinTildes ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/busqueda/Busqueda.kt
- Qué hace: quita los acentos de un texto dejando la letra base ("plátano" → "platano").
- Cómo funciona: recibe `String` y devuelve `String`. Normaliza a NFD (separa la letra de su acento) y borra los caracteres de marca con `Regex("\\p{Mn}+")`. Es `internal`: solo se usa dentro del módulo, a través de `coincide`.

### pesoPorTrozo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/rendimiento/Rendimiento.kt
- Qué hace: calcula cuánto pesa cada trozo dividiendo el peso final del producto entre la cantidad de trozos.
- Cómo funciona: recibe `pesoFinalG: Double?` y `trozos: Int`, devuelve `String` ya formateado. Si `pesoFinalG` es `null` devuelve la constante `PESO_NO_ESPECIFICADO` en vez de un número — por eso retorna `String` y no `Double`. Valida `trozos >= 1` y lanza excepción si no, en vez de dividir por cero.

### factorEscala ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/moldes/Moldes.kt
- Qué hace: calcula por cuánto hay que multiplicar cada ingrediente al pasar una receta de un molde a otro.
- Cómo funciona: recibe `original` y `nuevo` (ambos `DimensionesMolde`) más el `modo`, devuelve `Double`. En `ALTURA` divide áreas y **lanza excepción** si el molde nuevo es más bajo que el original; en `CAPACIDAD` divide volúmenes sin esa restricción. Lanza excepción si el molde original tiene área o volumen cero, en vez de devolver infinito en silencio, y si a alguno de los dos moldes le falta la altura.

### trozosCubiertosPor ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: dice cuántos trozos cubre un precio guardado, que no es lo mismo según sea precio por trozo o por producto completo.
- Cómo funciona: recibe un `PrecioVigente` y el snapshot `DatosCalculoReceta`, devuelve `Int`. Si el modo es `TROZO` devuelve la cantidad tal cual; si es `PRODUCTO` la multiplica por los trozos de la receta (2 productos completos de 8 trozos = 16 trozos).

### precioPorTrozoDe ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: lleva cualquier precio guardado a su equivalente por trozo, para poder compararlos entre sí.
- Cómo funciona: recibe un `PrecioVigente` y el snapshot, devuelve `Double`. Divide `precioTotal` por `trozosCubiertosPor` y **lanza excepción si eso diera cero**, en vez de devolver infinito.

### costoPorTrozo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: reparte el costo total de la receta entre sus trozos.
- Cómo funciona: recibe el snapshot y devuelve `Double` (`costoTotal / trozos`). Es constante para todos los precios de una misma receta, y esa es justamente la razón de que exista el snapshot: antes se recalculaba una vez por cada precio.

### gananciaPorTrozoDe ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: cuánto se gana por trozo con un precio guardado concreto.
- Cómo funciona: recibe un `PrecioVigente` y el snapshot, devuelve `Double` (`precioPorTrozoDe - costoPorTrozo`). Puede ser negativo si ese precio no cubre el costo, y así debe mostrarse.

### precioDeMenorGanancia ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: de todos los precios y promociones guardados de una receta, elige el que deja menos ganancia — el peor caso, que es con el que se juega (decisión #4).
- Cómo funciona: recibe el snapshot y devuelve el `PrecioVigente` ganador. **Lanza excepción si la receta no tiene ningún precio guardado**; quien la llame en un contexto agregado (simulación múltiple) debe filtrar antes con `DatosCalculoReceta.tienePrecio`, porque si no una receta a medio configurar voltea el total completo.

### precioEfectivoPorTrozo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: el precio por trozo que alimenta todos los campos automáticos de la app.
- Cómo funciona: recibe el snapshot, devuelve `Double`. Es `precioPorTrozoDe` aplicado al resultado de `precioDeMenorGanancia`, así que hereda su excepción cuando no hay precios.

### trozoGanador ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: dice a partir de qué trozo vendido la receta deja de perder plata y empieza a ganarla.
- Cómo funciona: recibe el snapshot y devuelve un `TrozoGanador` con el número, la ganancia en ese punto, y si es **alcanzable**. Lo último importa: si hacen falta 11 trozos en una receta que rinde 8, el número existe pero es imposible y la pantalla debe mostrar la advertencia en vez del dato.

### ingresoBruto ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: lo que entra al vender el producto completo, sin descontar nada.
- Cómo funciona: recibe el snapshot, devuelve `Double` (`precioEfectivoPorTrozo × trozos`). Es la base del cálculo de sueldos (10.1) y de la simulación (8.7).

### gananciaPorTrozo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: la ganancia por trozo al precio vigente de menor ganancia.
- Cómo funciona: recibe el snapshot, devuelve `Double`. Puede ser negativo. De solo lectura en la UI.

### gananciaFinal ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: la ganancia del producto completo.
- Cómo funciona: recibe el snapshot, devuelve `Double` (`ingresoBruto - costoTotal`). Puede ser negativo; se muestra con signo gracias a `formatearNumero`.

### simulacion ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/simulacion/Simulacion.kt
- Qué hace: proyecta ingreso, costo y ganancia a una semana y a un mes, según cuántos días se vende y cuántas unidades por día.
- Cómo funciona: recibe `ingresoBase`, `costoBase`, `dias` y `unidades`; devuelve `SimulacionResultado` con las 6 cifras (3 semanales y 3 mensuales). Lo semanal es `base × dias × unidades` y lo mensual multiplica por `SEMANAS_POR_MES`.

### calcularSueldo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/sueldos/Sueldos.kt
- Qué hace: reparte el ingreso bruto de una receta entre lo que se lleva el dueño (costo + su parte de la ganancia) y lo que se lleva el empleado.
- Cómo funciona: recibe el snapshot y la `gananciaEmpleado` acordada, devuelve un `Sueldo`. **Lanza excepción en dos casos**: si la receta no cubre su costo (no hay ganancia que repartir) y si la ganancia pedida supera la ganancia total. El primer chequeo va antes a propósito: con ganancia total negativa el rango `0.0..gananciaTotal` queda vacío en Kotlin y el segundo `require` fallaría siempre con un mensaje que no explica nada.

### SEMANAS_POR_MES ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/simulacion/Simulacion.kt
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

### obtener (IngredienteRepositorio)
- Ubicación: data/repositorio/IngredienteRepositorio.kt
- Qué hace: trae un ingrediente por su id.
- Cómo funciona: `suspend`, recibe `ingredienteId` y devuelve `Ingrediente`. La usan `costoTotalReceta` (indirectamente, vía el JOIN) y `confirmarEliminacionIngrediente`, que necesita el nombre **antes** de borrar la fila para poder registrarlo en el historial.

### eliminar (IngredienteRepositorio)
- Ubicación: data/repositorio/IngredienteRepositorio.kt
- Qué hace: borra la fila de un ingrediente.
- Cómo funciona: `suspend`, recibe `ingredienteId`. Es el borrado crudo: **no** avisa, no revisa si está en uso ni registra en el historial. Todo eso lo hace `confirmarEliminacionIngrediente`, que es la que debe llamarse desde la UI.

### obtener (MoldeRepositorio)
- Ubicación: data/repositorio/MoldeRepositorio.kt
- Qué hace: trae un molde del catálogo por su id.
- Cómo funciona: `suspend`, recibe `moldeId` y devuelve `Molde`. La usa `actualizarMolde` para leer el nombre antes de modificarlo, por el historial.

### actualizarDimensiones (MoldeRepositorio)
- Ubicación: data/repositorio/MoldeRepositorio.kt
- Qué hace: guarda las medidas nuevas de un molde del catálogo.
- Cómo funciona: `suspend`, recibe `moldeId` y las nuevas `DimensionesMolde`. Solo toca la fila del molde: la propagación a las recetas enlazadas la hace `actualizarMolde`, que es quien debe llamarse.

### obtenerDimensionesMolde
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: trae las medidas del molde que tiene guardada una receta.
- Cómo funciona: `suspend`, recibe `recetaId` y devuelve `DimensionesMolde?`. Devuelve `null` si la receta no usa molde, y de ese `null` se agarra `reescalarRecetaPorMolde` para cortar con un error en vez de calcular un factor sin sentido.

### usaMolde
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: dice si una receta usa molde o no.
- Cómo funciona: `suspend`, recibe `recetaId` y devuelve `Boolean` (el campo `usaMolde` de su rendimiento). La usa `reescalarRecetaPorPeso` para rechazar recetas con molde, que deben ir por `reescalarRecetaPorMolde`.

### obtenerPesoFinal
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: trae el peso final del producto de una receta.
- Cómo funciona: `suspend`, recibe `recetaId` y devuelve `Double?`. Es `null` cuando no se especificó, cosa que solo puede pasar en recetas con molde (sin molde es obligatorio, 6.2).

### sumaGramosIngredientes
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: suma los gramos de todos los ingredientes de una receta.
- Cómo funciona: `suspend`, recibe `recetaId` y devuelve `Double`. Es el plan B de `reescalarRecetaPorPeso` cuando no hay peso final guardado. **No confundir con `costoTotalReceta`**: esta suma gramos, la otra suma dinero.

### obtenerTodosLosIngredientes
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: trae los ingredientes de una receta, de todas sus secciones juntas.
- Cómo funciona: `suspend`, recibe `recetaId` y devuelve `List<RecetaIngrediente>`. La usan las dos funciones de reescalado para recorrer y multiplicar cada cantidad.

### actualizarCantidad
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: cambia los gramos de un ingrediente dentro de una receta.
- Cómo funciona: `suspend`, recibe el id de la fila `RecetaIngrediente` y la nueva cantidad. La llaman los reescalados una vez por ingrediente, siempre con el valor ya redondeado a 2 decimales.

### quitarIngredienteDeTodasLasSecciones
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: saca un ingrediente de todas las recetas donde aparezca.
- Cómo funciona: `suspend`, recibe `ingredienteId` y borra las filas `RecetaIngrediente` que lo referencian. Existe porque esa relación **no** tiene clave foránea declarada, así que no hay cascada automática que lo haga. La llama `confirmarEliminacionIngrediente` justo antes de borrar el ingrediente.

### crearReceta
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: crea una receta nueva junto con sus filas obligatorias, ya listas con valores seguros.
- Cómo funciona: `@Transaction suspend`, recibe el título y devuelve el `recetaId`. Inserta en la misma transacción el rendimiento (`trozos = 1`), la simulación de venta y una sección "General". **El `trozos = 1` es deliberado**: garantiza que ninguna división por trozos pueda reventar mientras la receta está a medio crear en el wizard (8.10). Los precios no se crean, porque un precio en 0 sería falso.

### obtenerDiasCompartidos
- Ubicación: data/repositorio/EmpleadoRepositorio.kt
- Qué hace: trae los días por semana que un empleado vendería, el valor común a todas sus recetas.
- Cómo funciona: `suspend`, recibe `empleadoId` y devuelve `Int`. Es el `diasPorSemana` de `EmpleadoSimulacionMultiple`. **No confundir** con el `diasPorSemana` de `EmpleadoRecetaSueldo`, que es propio de cada receta por separado; cambiar uno no afecta al otro.

### obtenerDetalle
- Ubicación: data/repositorio/EmpleadoRepositorio.kt
- Qué hace: trae cuántas unidades por día vendería un empleado de cada una de sus recetas.
- Cómo funciona: `suspend`, recibe `empleadoId` y devuelve la lista de `EmpleadoSimulacionMultipleDetalle`. Es la lista sobre la que itera `simulacionMultiple`.

### obtenerSueldos
- Ubicación: data/repositorio/EmpleadoRepositorio.kt
- Qué hace: trae de una sola vez todos los sueldos asignados a un empleado, indexados por receta.
- Cómo funciona: `suspend`, recibe `empleadoId` y devuelve `Map<Long, EmpleadoRecetaSueldo>` con el `recetaId` como clave. Devuelve el mapa completo a propósito, para que `simulacionMultiple` no consulte uno por receta dentro del bucle. Una receta sin sueldo asignado simplemente no está en el mapa y cuenta como 0.

### registrar
- Ubicación: data/repositorio/HistorialRepositorio.kt
- Qué hace: deja anotado en el historial que algo se creó, se editó o se eliminó.
- Cómo funciona: `suspend`, recibe `tipo`, `entidad`, `descripcion` y un `detalleAdicional` opcional. **La descripción siempre debe nombrar la entidad afectada** (`"Se eliminó el ingrediente 'Harina'"`), nunca un texto genérico, lo que obliga a leer el nombre antes de borrar. Aprovecha la llamada para borrar los eventos más viejos que `RETENCION_HISTORIAL_MS`.

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

### DatosCalculoReceta ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: la foto de una receta —id, título, costo total, trozos y precios— que se lee una vez y se le pasa a todas las fórmulas.
- Cómo funciona: `data class` que **no es una tabla**, se arma en memoria con `obtenerDatosCalculo`. Valida en su constructor que `trozos >= 1`, así ninguna fórmula que divida por trozos puede reventar. Expone `tienePrecio`, que hay que consultar antes de pedir cualquier cifra automática. Es lo que permite que las funciones de `logica/` sean puras y probables sin base de datos, y que todas las cifras de una pantalla salgan de la misma lectura.

### DimensionesMolde ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/moldes/Moldes.kt
- Qué hace: guarda la geometría de un molde (forma y medidas) y calcula solo su área y su volumen.
- Cómo funciona: `data class` puro que vive en `:logica`; el módulo `:app` lo embebe con `@Embedded` en `Molde` y en `RecetaRendimiento`. **Todos sus campos son nulables por obligación técnica**, no de diseño: se embebe como nulable en las recetas sin molde y Room no admite subcampos no-nulos ahí. La obligatoriedad real la impone la validación de 6.2. Expone `areaCm2` y `volumenCm3` como propiedades calculadas; ambas usan `!!` y fallan ruidosamente si el molde es inválido.

### TrozoGanador ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: el resultado de `trozoGanador` — qué número de trozo cubre el costo, cuánto se gana ahí, y si ese trozo existe de verdad.
- Cómo funciona: `data class` con `numero: Int`, `ganancia: Double` y `alcanzable: Boolean`. El tercer campo evita mostrar "trozo ganador: 11" en una receta que solo rinde 8.

### Sueldo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/sueldos/Sueldos.kt
- Qué hace: el reparto del ingreso de una receta entre el dueño y el empleado.
- Cómo funciona: `data class` con `ingresoBruto`, `yoMeLlevo` y `gananciaEmpleado`. `yoMeLlevo` incluye el costo total más la parte de la ganancia que no se lleva el empleado.

### SimulacionResultado ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/simulacion/Simulacion.kt
- Qué hace: las 6 cifras que devuelve una simulación de ventas: ingreso, costo y ganancia, en versión semanal y mensual.
- Cómo funciona: `data class` de 6 `Double`. Lo mensual ya viene multiplicado por `SEMANAS_POR_MES`, no hay que volver a hacerlo al mostrarlo.

### SimulacionMultipleResultado
- Ubicación: logica/Sueldos.kt
- Qué hace: el total de una simulación con varias recetas a la vez, en versión diaria, semanal y mensual, más la lista de las que quedaron fuera.
- Cómo funciona: `data class` que guarda solo las 3 cifras **diarias** más `diasPorSemana` y `omitidas: List<String>` (títulos de recetas sin precio). Las 6 cifras semanales y mensuales son propiedades calculadas: guardar las tres versiones permitiría que quedaran desincronizadas entre sí.

### PrecioVigente ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: un precio o promoción tal como lo ven las fórmulas: "vender N trozos (o N productos) por X en total".
- Cómo funciona: `data class` con `modo`, `cantidad`, `precioTotal` y `etiqueta`. Es el equivalente puro de la tabla `receta_precios`: la entidad de Room vive en `:app` y se convierte a este tipo al armar el snapshot. Existe porque `:logica` no puede depender de Android, y es lo que permite probar las fórmulas de precios sin base de datos.

### ModoPrecio ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: distingue si un precio guardado es por trozo o por producto completo.
- Cómo funciona: `enum` con `TROZO` y `PRODUCTO`. Es enum y no `String` a propósito: un typo en un texto libre no lo detecta el compilador. Necesita `TypeConverter` y se guarda por nombre, no por ordinal.

### ModoReescalado ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/moldes/Moldes.kt
- Qué hace: distingue las dos formas de reescalar una receta a otro molde.
- Cómo funciona: `enum` con `ALTURA` (conserva el grosor comparando áreas; exige que el molde nuevo no sea más bajo) y `CAPACIDAD` (conserva el volumen, sin restricción de altura).

### TipoFormaMolde ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/moldes/Moldes.kt
- Qué hace: la forma de un molde, que determina qué medidas se piden y con qué fórmula se saca el área.
- Cómo funciona: `enum` con `RECTANGULO`, `CIRCULO`, `CUADRADO`, `TRIANGULO` y `EXOTICO`. El último es para formas irregulares, donde el volumen se mide llenando el molde con agua en vez de calcularlo. Necesita `TypeConverter`.

### TipoEvento
- Ubicación: data/db/entidades/EventoCambio.kt
- Qué hace: si un evento del historial fue una creación, una edición o una eliminación.
- Cómo funciona: `enum` con `CREACION` (azul), `EDICION` (verde) y `ELIMINACION` (rojo). Necesita `TypeConverter`.

### EntidadEvento
- Ubicación: data/db/entidades/EventoCambio.kt
- Qué hace: sobre qué tipo de cosa fue un evento del historial.
- Cómo funciona: `enum` con `INGREDIENTE`, `RECETA`, `MOLDE` y `EMPLEADO`. Era un `String` libre y se pasó a enum por la misma razón que `ModoPrecio` (5.5). Necesita `TypeConverter`.

### Convertidores
- Ubicación: data/db/Convertidores.kt
- Qué hace: le enseña a Room a guardar y leer los enums, que por sí solo no sabe manejar.
- Cómo funciona: clase con pares de `@TypeConverter` por cada enum, registrada con `@TypeConverters` en `AppDatabase`. Convierte a texto con `.name` y de vuelta con `valueOf`. **Por nombre y no por ordinal a propósito**: si algún día se agrega un valor en medio del enum, los ordinales ya guardados cambiarían de significado en silencio.
