# Registro de funciones y variables

Ver `CLAUDE.md` para las reglas de uso de este archivo.
Cada entrada nueva va al final de su sección, con el mismo formato.

> **Cómo leer el estado de cada entrada:**
>
> - **`✅ IMPLEMENTADA`** — el archivo existe. La ruta es real: ábrelo y léelo, tal como pide
>   el paso 2 de `CLAUDE.md`.
> - **Sin marca** — está solo ESPECIFICADA en `arquitectura.md`. La ruta indica dónde va a
>   vivir según la sección 4, pero ese archivo todavía no existe. Para el paso 2 de `CLAUDE.md`
>   significa "ya está diseñado, reutiliza este diseño", no "ábrelo".
>
> **Sobre "compila":** lo de `logica/` está compilado y cubierto por 178 pruebas. Lo de `app/`
> se compila en el equipo de Sandy, porque el entorno donde se escribe no tiene el Android
> SDK. Las entidades, los conversores y la base de datos ya pasaron esa compilación; lo que
> se agregue después queda sin verificar hasta la siguiente.
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

### formatearMientrasSeEscribe ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/formato/Formato.kt
- Qué hace: pone los puntos de mil **mientras se escribe**, sin tocar lo que todavía no está escrito.
- Cómo funciona: recibe `String` y devuelve `String`. **`formatearNumero` NO sirve para esto** y ya se comprobó: aplicada tecla por tecla convierte "1000," en "1.000" (se come la coma y deja imposible el decimal), "1000,5" en "1.000,50" (inventa un cero) y "1,555" en "1,56" (redondea antes de tiempo). Esta agrupa solo la parte entera y deja intacta la decimal; descarta los puntos que reciba —los pone ella—, corta en `MAXIMO_DECIMALES` y descarta el signo menos. Aplicarla sobre su propio resultado no cambia nada, que es lo que permite llamarla en cada tecla. Lo que deja escrito siempre lo entiende `textoANumero`. La usan los tres campos numéricos de Ingredientes, desde el ViewModel (`cambiarValor`, `cambiarPrecio`, `cambiarCantidad`), no desde el Composable.

### formatearMientrasSeEscribe (con cursor) ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/formato/Formato.kt
- Qué hace: lo mismo que la anterior, **y además dice dónde queda el cursor**. Es la que hay que usar desde un campo de texto.
- Cómo funciona: recibe `(texto: String, cursor: Int)` y devuelve `TextoConCursor`. La posición no se puede conservar como número porque el texto cambia de largo: al escribir "1234" el texto pasa a "1.234" y la posición 4, que era el final, cae entre el "3" y el "4" — **esto pasó de verdad** y lo que se escribía después entraba en medio del número. Lo que conserva es cuántos caracteres escritos por la persona (dígitos y coma, sin contar los puntos) hay antes del cursor, y busca esa misma cantidad en el texto formateado. Si el texto se acorta, el cursor queda al final. Hay un test que barre todas las posiciones de varios textos para que nunca devuelva un cursor fuera de rango, que cerraría la app.

### TextoConCursor ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/formato/Formato.kt
- Qué hace: un texto y dónde quedó el cursor dentro de él.
- Cómo funciona: `data class` con `texto: String` y `cursor: Int`. Es lo que devuelve `formatearMientrasSeEscribe` con cursor. Está en `logica/` y no usa nada de Compose, así que se puede probar con JUnit; el Composable lo convierte a `TextFieldValue`.

### MAXIMO_DECIMALES ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/formato/Formato.kt
- Qué hace: cuántos decimales se pueden escribir en un campo numérico.
- Cómo funciona: constante `2`, los mismos que guarda `redondearADosDecimales`. Dejar escribir un tercero mostraría una precisión que se va a perder igual al guardar.

### coincide ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/busqueda/Busqueda.kt
- Qué hace: dice si un texto buscado aparece en cualquier parte de un campo, ignorando mayúsculas y tildes.
- Cómo funciona: recibe `textoBusqueda` y `campo` (ambos `String`), devuelve `Boolean`. Pasa los dos por `sinTildes` antes de comparar, para que "limon" encuentre "Mousse de limón". Lo usan las 4 pantallas con buscador (12.2).

### sinTildes ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/busqueda/Busqueda.kt
- Qué hace: quita los acentos de un texto dejando la letra base ("plátano" → "platano").
- Cómo funciona: recibe `String` y devuelve `String`. Normaliza a NFD (separa la letra de su acento) y borra los caracteres de marca con `Regex("\\p{Mn}+")`. Es `internal`: solo se usa dentro del módulo, a través de `coincide`.

### sonElMismoTexto ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/busqueda/Busqueda.kt
- Qué hace: dice si dos textos son el mismo nombre, ignorando mayúsculas, tildes y espacios sobrantes.
- Cómo funciona: recibe dos `String` y devuelve `Boolean`. A diferencia de `coincide`, que busca una parte dentro de otra, acá tienen que ser el texto completo: "Azúcar" y "azucar " son iguales, pero "azúcar flor" no. Sirve para avisar de un ingrediente repetido antes de crearlo, algo que la base no puede hacer sola porque para ella "azucar" y "azúcar" son distintos.

### filtrarPor ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/busqueda/Busqueda.kt
- Qué hace: deja de una lista solo los elementos que coinciden con lo escrito en el buscador.
- Cómo funciona: genérica — recibe `List<T>`, el texto buscado y una función que dice de dónde sacar el texto de cada elemento (`{ it.nombre }`, `{ it.titulo }`…); devuelve `List<T>` conservando el orden original. Con el buscador en blanco devuelve la lista completa, porque no haber escrito nada no es lo mismo que no encontrar nada. Es genérica a propósito: las 4 secciones filtran tipos distintos pero la regla de coincidencia (`coincide`) tiene que ser una sola.

### errorEnNombreEscrito ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Validaciones.kt
- Qué hace: revisa cualquier nombre escrito a mano — un ingrediente, un molde, un empleado.
- Cómo funciona: recibe `String` y devuelve el motivo del problema, o `null` si está bien — ese formato encaja directo con los campos de Compose, que muestran el mensaje bajo el campo. Rechaza vacíos, solo espacios, y más de `LARGO_MAXIMO_NOMBRE` caracteres. **No comprueba repetidos**: eso necesita la base de datos y lo hace el repositorio. **Antes se llamaba `errorEnNombreIngrediente`**, y se renombró al llegar los moldes: su cuerpo nunca tuvo nada de ingredientes, pero el nombre invitaba a que cada sección escribiera su propia copia idéntica. Las excepciones son las que sí usan otra palabra en el mensaje —el título de una receta y el nombre de una sección—, que viven en `Recetas.kt`.

### errorEnNumeroPositivoTexto ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Validaciones.kt
- Qué hace: revisa un número que tiene que ser mayor que cero, tal como está escrito en el campo.
- Cómo funciona: recibe el texto y el aviso que corresponde si el campo está vacío; devuelve el motivo o `null`. **Es el cuerpo que ya estaba escrito tres veces** —la cantidad del paquete en la calculadora, los gramos de un ingrediente en una receta, y las medidas de un molde—, palabra por palabra salvo ese aviso, que es lo único que cambia según lo que se pida; por eso entra por parámetro. `errorEnCantidadTexto` y `errorEnCantidadEnGramosTexto` ahora delegan acá sin cambiar ni un mensaje. **Donde el cero es un dato válido no se usa esta función** (el valor por gramo de un ingrediente regalado, las unidades por día de una receta que no se vende): esa diferencia es deliberada.

### errorEnValorPorGramo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Validaciones.kt
- Qué hace: revisa si el valor por gramo escrito sirve.
- Cómo funciona: recibe `Double` y devuelve el motivo o `null`. Rechaza negativos y valores que no son número (NaN, infinito). **Acepta el 0 a propósito**: hay ingredientes que no se costean y ponerlos en cero es la forma de decirlo.

### textoANumero ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Validaciones.kt
- Qué hace: convierte a número lo que la persona escribió, con el formato de la app.
- Cómo funciona: recibe `String` y devuelve `Double?`. Acepta la coma como separador decimal y el punto como separador de miles ("1.234,56"), que es como se escribe en el teclado del celular y no como lo espera Kotlin. Devuelve `null` si el texto no es un número. Es la operación inversa de `formatearNumero`, y hay un test que comprueba que ir y volver da lo mismo.

### errorEnValorPorGramoTexto ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Validaciones.kt
- Qué hace: revisa el valor por gramo **tal como está escrito en el campo**, no ya convertido a número.
- Cómo funciona: recibe `String` y devuelve el motivo o `null`. Es el puente entre `textoANumero` y `errorEnValorPorGramo`: distingue los tres problemas posibles —vacío, no es número, número que no sirve— con un mensaje distinto para cada uno. **Rechaza el campo en blanco aunque `errorEnValorPorGramo` acepte el 0**: dejarlo vacío suele ser un olvido y escribir 0 es una decisión.

### revisarIngrediente ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Validaciones.kt
- Qué hace: revisa de una vez los dos campos del formulario de un ingrediente.
- Cómo funciona: recibe el nombre y el valor por gramo escritos, devuelve `ErroresIngrediente`. Se llama en cada tecla para habilitar o no el botón de guardar. **No reemplaza la validación del repositorio**, que es la que decide de verdad y además comprueba lo único que acá no se puede saber: si ya existe otro ingrediente con ese nombre.

### LARGO_MAXIMO_NOMBRE ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Validaciones.kt
- Qué hace: cuántos caracteres puede tener cualquier nombre escrito a mano.
- Cómo funciona: constante `60`. La usan `errorEnNombreEscrito`, `errorEnTituloReceta` y `errorEnNombreSeccion`, y también sirve para poner el tope en el campo de texto de la pantalla.

### redondearADosDecimales ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/formato/Formato.kt
- Qué hace: redondea a 2 decimales, la precisión con la que la app guarda y muestra números.
- Cómo funciona: recibe `Double` y devuelve `Double` (`(valor * 100).roundToLong() / 100.0`). Es la misma cuenta que hace `formatearNumero` antes de armar el texto, separada porque también hace falta **antes de guardar**: si se guardara 1,6666… y la pantalla mostrara "1,67", multiplicar por los gramos de una receta no daría el número que se vio. La usan `valorPorGramo` (7.2) y los reescalados de receta (8.3.1).

### valorPorGramo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/calculadora/ValorPorGramo.kt
- Qué hace: calcula cuánto cuesta un gramo a partir de lo que se pagó por un paquete y de cuánto trae.
- Cómo funciona: recibe `precioTotal`, `cantidad` y la `UnidadDeCompra`, devuelve `Double` **ya redondeado a 2 decimales** (lo mismo que se va a mostrar y a guardar). Lanza excepción si los gramos no son mayores que cero, en vez de devolver infinito. **No confundir con `costoPorTrozo`**, que reparte un costo ya conocido; esta saca el costo unitario de una compra.

### calcularValorPorGramo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/calculadora/ValorPorGramo.kt
- Qué hace: hace la cuenta a partir de lo escrito en los campos, o devuelve `null` si todavía no se puede.
- Cómo funciona: recibe los dos textos y la unidad, devuelve `Double?`. Es lo que la pantalla llama en cada tecla para mostrar el resultado en vivo. **No lanza excepción**: mientras se escribe, "todavía no alcanza" es lo normal y no un error — a diferencia de `valorPorGramo`, que sí lanza porque es la última red.

### errorEnPrecioTexto ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/calculadora/ValorPorGramo.kt
- Qué hace: revisa el precio pagado tal como está escrito en el campo de la calculadora.
- Cómo funciona: recibe `String` y devuelve el motivo o `null`. Rechaza vacíos, negativos y lo que no sea número. **Acepta el 0**, por lo mismo que `errorEnValorPorGramo`: un ingrediente regalado cuesta 0 y eso es un dato válido.

### errorEnCantidadTexto ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/calculadora/ValorPorGramo.kt
- Qué hace: revisa la cantidad que trae el paquete.
- Cómo funciona: recibe `String` y devuelve el motivo o `null`; el cuerpo está en `errorEnNumeroPositivoTexto` y acá solo se le pasa el aviso propio del campo. A diferencia de `errorEnPrecioTexto`, **el cero no se acepta**: no es un dato raro pero válido, es una división por cero.

### revisarCalculadora ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/calculadora/ValorPorGramo.kt
- Qué hace: revisa de una vez los dos campos de la calculadora, mientras se escribe.
- Cómo funciona: recibe los dos textos y devuelve `ErroresCalculadora`. Es el equivalente de `revisarIngrediente` para la otra pantalla; misma idea, campos distintos.

### GRAMOS_POR_KILO ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/calculadora/ValorPorGramo.kt
- Qué hace: cuántos gramos tiene un kilo.
- Cómo funciona: constante `1000.0`. La usa `UnidadDeCompra.aGramos`. Es constante y no un `1000` suelto porque es justo el número que se equivoca uno al convertir de cabeza, y así hay un solo lugar donde puede estar mal.

### errorEnTituloReceta, errorEnNombreSeccion y errorEnCantidadEnGramosTexto ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Recetas.kt
- Qué hacen: revisan el título de una receta, el nombre de una sección y los gramos de un ingrediente dentro de ella.
- Cómo funcionan: reciben `String` y devuelven el motivo o `null`, igual que el resto del paquete. Las dos de nombre usan `LARGO_MAXIMO_NOMBRE`, que es el mismo tope para todo lo que se escribe a mano. **La de gramos rechaza el cero** —delegando en `errorEnNumeroPositivoTexto`—, a diferencia de `errorEnValorPorGramo`: un ingrediente en cantidad cero simplemente no está en la receta, y dejarlo guardado es una fila que no suma y confunde al leer.

### debenMostrarseLosNombresDeSeccion y esNombreAutomaticoDeSeccion ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Recetas.kt
- Qué hacen: deciden si se muestran los encabezados con el nombre de cada sección.
- Cómo funcionan: la primera recibe **la lista de nombres** (no la cantidad) y devuelve `Boolean`. Se muestran con dos o más, y también con una sola **si tiene nombre puesto a mano**. Esa segunda parte salió de un caso real: con "Bizcocho" y "Salsa", borrar el bizcocho dejaba la salsa sola y su encabezado desaparecía — el nombre seguía guardado, pero parecía perdido, y lo había escrito alguien. **Lo que uno escribe no se esconde solo.** `esNombreAutomaticoDeSeccion` distingue la sección que todavía se llama `NOMBRE_SECCION_POR_DEFECTO`; se compara por texto y no por una columna aparte, con la contrapartida aceptada de que bautizar una sección exactamente "General" la hace comportarse como la automática.

### marcarRepetidos ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/busqueda/Busqueda.kt
- Qué hace: de una lista, marca cuáles repiten un nombre que ya apareció antes.
- Cómo funciona: genérica; recibe la lista y de dónde sacar el texto, y devuelve `List<Boolean>` en el mismo orden. **El primero de cada nombre nunca se marca**, así que hay que pasarla ordenada por antigüedad para que el que se conserve sea el original. Compara con `sonElMismoTexto`, o sea ignora mayúsculas y tildes. Existe para los datos anteriores a prohibir los repetidos: no se pueden borrar solos —serían datos reales desapareciendo sin aviso— pero sí señalar para que la pantalla los trate distinto.

### nombreSugeridoParaPrimeraSeccion ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Recetas.kt
- Qué hace: propone un nombre para la sección que hasta ahora era invisible, al agregar la segunda.
- Cómo funciona: recibe el título de la receta y devuelve ese mismo texto, recortado al tope y sin espacios sobrantes; si viniera vacío cae en `NOMBRE_SECCION_POR_DEFECTO`. Propone el título y no "General" porque en "Torta de manjar" la primera sección suele ser el bizcocho de esa torta. Hay un test que comprueba que lo propuesto **nunca sale ya inválido**: sería pedir que corrijan algo que la persona no escribió.

### NOMBRE_SECCION_POR_DEFECTO ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Recetas.kt
- Qué hace: el nombre que se le pone sola a la primera sección mientras la receta tenga una sola.
- Cómo funciona: constante `"General"`. La usa `RecetaRepositorio.crear` al sembrar la sección automática. Ese nombre **no se muestra** mientras sea la única sección.

### CampoDeMolde y camposDe ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Moldes.kt
- Qué hacen: enumeran las medidas que puede pedir el formulario de un molde, y dicen cuáles pide cada forma.
- Cómo funcionan: `CampoDeMolde` es un `enum` con las 8 medidas posibles y su etiqueta; `camposDe(forma)` devuelve la lista que corresponde a esa forma, en el orden en que conviene preguntarlas. Existen para que **la pantalla y la validación no puedan discrepar**: si el formulario decidiera por su cuenta qué dibujar y la validación por la suya qué exigir, agregar una forma y tocar solo uno de los dos dejaría un campo obligatorio que nadie puede llenar. `ALTURA_MOLDE` aparece en las cinco formas, **incluida la exótica** (6.2): ahí el volumen ya viene medido con agua, pero sin la altura no se despeja el área y el Modo Altura del reescalado se queda sin qué comparar. La etiqueta va en el enum y no en la pantalla porque "Altura" a secas es justo el texto que confunde las dos alturas del triángulo.

### errorEnMedidaDeMoldeTexto ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Moldes.kt
- Qué hace: revisa una medida de molde tal como está escrita en el campo.
- Cómo funciona: delega en `errorEnNumeroPositivoTexto` con el aviso "Escribe la medida". **Rechaza el cero igual que un campo vacío**, y no por prolijidad: una medida en 0 deja el área o el volumen en 0, y ahí `factorEscala` divide por cero al reescalar — un error que aparecería mucho después, en otra pantalla, sin ninguna pista de dónde venía.

### revisarMolde y ErroresMolde ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Moldes.kt
- Qué hacen: revisan de una vez el formulario completo de un molde, mientras se escribe.
- Cómo funcionan: `revisarMolde(nombre, forma, medidas)` devuelve `ErroresMolde`, que lleva `nombre`, `forma` y un **mapa** `medidas: Map<CampoDeMolde, String>` con solo las que fallaron, más `sirve`. Es un mapa y no un campo por medida porque cuáles existen depende de la forma: un `data class` con las ocho tendría siempre seis en `null` sin que eso signifique "está bien", sino "acá no se pregunta". Solo mira lo que `camposDe` pide para esa forma, así lo que quedó escrito de una forma elegida antes no se arrastra como error. Sin forma elegida el único aviso es el de la forma: no se puede exigir una medida sin saber cuál.

### dimensionesDesde ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Moldes.kt
- Qué hace: arma las `DimensionesMolde` a partir de lo escrito, o devuelve `null` si todavía no se puede.
- Cómo funciona: recibe la forma y el mapa de medidas escritas, devuelve `DimensionesMolde?`. Es el equivalente de `calcularValorPorGramo` para este formulario: se llama en cada tecla para mostrar el área y el volumen en vivo y **no lanza excepción**. **No mira el nombre** a propósito: un molde sin bautizar igual tiene medidas, y esconder el volumen hasta que lo bauticen sería tapar justo el número que dice si se midió bien. Los campos que esa forma no usa quedan en `null` aunque haya algo escrito —quien probó "círculo" y cambió a "cuadrado" no debe guardar un cuadrado con diámetro—. Hay un test que comprueba que **todo lo que `revisarMolde` aprueba se puede convertir**: si discreparan, la pantalla habilitaría guardar sobre un molde que no se puede armar.

### pesoPorTrozo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/rendimiento/Rendimiento.kt
- Qué hace: calcula cuánto pesa cada trozo dividiendo el peso final del producto entre la cantidad de trozos.
- Cómo funciona: recibe `pesoFinalG: Double?` y `trozos: Int`, devuelve `String` ya formateado. Si `pesoFinalG` es `null` devuelve la constante `PESO_NO_ESPECIFICADO` en vez de un número — por eso retorna `String` y no `Double`. Valida `trozos >= 1` y lanza excepción si no, en vez de dividir por cero.

### SIN_MOLDE ✅ IMPLEMENTADA (sin usar todavía)
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/rendimiento/Rendimiento.kt
- Qué hace: el texto fijo del campo molde cuando la receta no usa ninguno (una salsa, por ejemplo).
- Cómo funciona: constante con el texto `"No utiliza molde"`, especificado en la tabla de la sección 8.3. **Todavía no la usa nadie**: su consumidor es el paso "Rendimiento" de la Fase 5. Queda registrada justamente para que ahí se reutilice en vez de escribir el texto suelto en el Composable.

### factorEscala ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/moldes/Moldes.kt
- Qué hace: calcula por cuánto hay que multiplicar cada ingrediente al pasar una receta de un molde a otro.
- Cómo funciona: recibe `original` y `nuevo` (ambos `DimensionesMolde`) más el `modo`, devuelve `Double`. En `ALTURA` divide áreas y solo acepta moldes nuevos **entre 0 y `MAX_DIFERENCIA_ALTURA_CM` (3 cm) más altos**: si es más bajo lanza excepción, y si se pasa del margen lanza excepción con el texto de `MENSAJE_ALTURA_RIESGOSA`. En `CAPACIDAD` divide volúmenes sin ninguna restricción de altura. En ambos modos lanza excepción si al molde le falta la altura, o si el original tiene área o volumen cero, en vez de devolver infinito en silencio.

### MAX_DIFERENCIA_ALTURA_CM ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/moldes/Moldes.kt
- Qué hace: cuánto más alto puede ser el molde nuevo que el original al reescalar en Modo Altura.
- Cómo funciona: constante `3.0`, en centímetros. Modo Altura no usa la altura para calcular el factor, así que en un molde bastante más alto la masa sube lo mismo de siempre y queda perdida al fondo. Se mide como diferencia absoluta, no como proporción. Solo aplica a `ModoReescalado.ALTURA`.

### MENSAJE_ALTURA_RIESGOSA ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/moldes/Moldes.kt
- Qué hace: el texto que se muestra cuando el molde nuevo supera el margen de altura.
- Cómo funciona: constante con el texto `"Demasiado riesgo. Mejor escale con el otro método"`. Es una constante y no un texto suelto para que la pantalla pueda mostrar exactamente el mismo mensaje que produce la validación, sin copiarlo a mano.

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
- Qué hace: de todos los precios y promociones guardados de una receta, elige el que deja menos ganancia — el peor caso.
- Cómo funciona: recibe el snapshot y devuelve el `PrecioVigente` ganador. **Ya no es lo que alimenta las cifras automáticas**: eso lo decide `precioDeReferencia`. Sigue existiendo porque es el respaldo más prudente mientras no se haya elegido ninguno a mano. **Lanza excepción si la receta no tiene ningún precio guardado**; quien la llame en un contexto agregado (simulación múltiple) debe filtrar antes con `DatosCalculoReceta.tienePrecio`, porque si no una receta a medio configurar voltea el total completo.

### precioDeReferencia ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: elige el precio con el que se calcula todo lo automático — sueldos, simulaciones, ganancia final, trozo ganador.
- Cómo funciona: recibe el snapshot y devuelve el `PrecioVigente` que tenga `esReferencia = true`. Si ninguno lo tiene —receta recién creada, o filas anteriores a la versión 2 de la base— cae en `precioDeMenorGanancia`, que era el comportamiento anterior. **Es la única entrada a los cálculos automáticos**: nadie debería volver a llamar a `precioDeMenorGanancia` directamente para eso.

### errorAlElegirReferencia ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: revisa si un precio puede ser la referencia de la receta.
- Cómo funciona: recibe el `PrecioVigente` y el snapshot, devuelve el motivo o `null` (mismo formato que las validaciones de 6.2, porque describe algo corregible eligiendo otra promo). **Rechaza los que pierden plata**: de la referencia sale el sueldo, y `calcularSueldo` no puede repartir una ganancia que no existe. **Cubrir el costo justo sí se acepta.** Que un precio no pueda ser referencia no impide guardarlo ni verlo.

### MENSAJE_PROMOCION_CON_PERDIDAS ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: el texto que se muestra al intentar poner como referencia un precio que pierde plata.
- Cómo funciona: constante con el texto `"Esta promoción genera pérdidas"`. Es constante y no un texto suelto por lo mismo que `MENSAJE_ALTURA_RIESGOSA`: la pantalla muestra exactamente el mismo mensaje que produce la validación, sin copiarlo a mano.

### precioEfectivoPorTrozo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: el precio por trozo que alimenta todos los campos automáticos de la app.
- Cómo funciona: recibe el snapshot, devuelve `Double`. Es `precioPorTrozoDe` aplicado al resultado de `precioDeReferencia`, así que hereda su excepción cuando no hay precios.

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

### elegirPrecioDeReferencia
- Ubicación: data/repositorio/RecetaRepositorio.kt
- Qué hace: cambia cuál de los precios de una receta es el que alimenta las cifras automáticas.
- Cómo funciona: `suspend`, recibe `recetaId` y `precioId`, devuelve el motivo del rechazo o `null` si se pudo. **Revisa antes de escribir** con `errorAlElegirReferencia`: si el precio pierde plata no toca nada, así que "cancelar y volver al valor que tenía" es simplemente no haber escrito. Si pasa, llama a `fijarPrecioDeReferencia` (una transacción) y registra un evento verde. Se implementa en la Fase 3, junto con la pantalla que la usa.

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

### aVigente ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/entidades/Precios.kt
- Qué hace: convierte una fila de precio de la base al tipo que entienden las fórmulas.
- Cómo funciona: función de extensión sobre `RecetaPrecio` que devuelve `PrecioVigente`, dejando fuera `id` y `recetaId`. Existe porque `logica/` no depende de Android y allá el precio no sabe nada de Room. Es el puente que se usa al armar el snapshot de una receta.

### Convertidores ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/Convertidores.kt
- Qué hace: le enseña a Room a guardar y leer los seis enums del proyecto.
- Cómo funciona: clase con un par de `@TypeConverter` por enum (`TipoFormaMolde`, `ModoPrecio`, `TipoEvento`, `EntidadEvento`, `TipoDuracion`, `UnidadDuracion`), registrada con `@TypeConverters` en `AppDatabase`. Convierte a texto con `.name` y de vuelta con `valueOf`. **Por nombre y no por posición a propósito**: si se agrega un valor en medio de un enum, las posiciones ya guardadas cambiarían de significado en silencio.

### AppDatabase ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/AppDatabase.kt
- Qué hace: la base de datos de la app; reúne las 15 tablas y da acceso a los DAO.
- Cómo funciona: clase `@Database` en **versión 2** con `exportSchema = true`, que deja el esquema en `app/schemas/` — esos archivos se versionan porque son el registro de las migraciones. Lleva `MIGRACION_1_2` declarada. `obtener(context)` devuelve una única instancia compartida (doble chequeo con `@Volatile`), ya que abrir varias sobre el mismo archivo puede corromper datos. Room activa por su cuenta las claves foráneas y el modo WAL.

### MIGRACION_1_2 ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/AppDatabase.kt
- Qué hace: agrega la columna `esReferencia` a `receta_precios` al pasar de la versión 1 a la 2.
- Cómo funciona: `Migration(1, 2)` con un `ALTER TABLE ... ADD COLUMN esReferencia INTEGER NOT NULL DEFAULT 0`. El `DEFAULT 0` es obligatorio —SQLite no deja agregar una columna `NOT NULL` sin él— y **tiene que calzar con el `@ColumnInfo(defaultValue = "0")` de la entidad**: Room compara los dos esquemas al abrir y, si difieren, la app no arranca. Las filas que ya existían quedan sin referencia elegida, que es lo correcto: `precioDeReferencia` usa entonces el de menor ganancia y todo sigue dando lo mismo que antes.

### AppDatabase.obtener ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/AppDatabase.kt
- Qué hace: entrega la base de datos, creándola la primera vez que se pide.
- Cómo funciona: recibe un `Context` y devuelve la instancia única de `AppDatabase`, guardándola para las siguientes llamadas. Usa el `applicationContext` para no retener una pantalla en memoria.

### ReposteriaTheme ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/theme/Theme.kt
- Qué hace: aplica la paleta de la app a todo lo que envuelve, en modo claro u oscuro.
- Cómo funciona: Composable que recibe si va en oscuro (por defecto, lo que tenga el celular) y el contenido. Elige entre `EsquemaClaro` y `EsquemaOscuro`, y además provee `LocalColoresHistorial` con la versión correspondiente de los tres colores del historial. Toda pantalla debe ir dentro de este Composable y usar `MaterialTheme.colorScheme.*`, nunca colores fijos. Los dos esquemas definen **todos** los roles de Material, no solo los principales: los que se dejan sin definir toman el gris violáceo de fábrica y aparecen sin aviso en el primer componente que los use (pasó con las tarjetas, que usan `surfaceContainerHighest`). Para texto secundario se usa `onSurfaceVariant`, que ya es el tono tenue correcto en los dos modos — no hay que aplicarle transparencia al color de texto normal.

### ColoresHistorial ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/theme/Theme.kt
- Qué hace: agrupa los tres colores del historial de cambios, que no caben en la paleta de Material.
- Cómo funciona: `data class` con `creacion` (azul), `edicion` (verde) y `eliminacion` (frambuesa). Se accede con `LocalColoresHistorial.current` y cambia solo entre claro y oscuro. El frambuesa es además el color de `error` del tema: si fuera un acento decorativo aparte, un rojo de adorno se confundiría con un aviso de borrado.

### IngredienteDao ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/dao/IngredienteDao.kt
- Qué hace: las consultas y escrituras de la tabla de ingredientes.
- Cómo funciona: `observarTodos()` devuelve un `Flow` ordenado alfabéticamente, así la pantalla se actualiza sola sin volver a preguntar; `buscarPorNombre` compara sin distinguir mayúsculas para avisar de repetidos; `eliminarPorId` es el borrado crudo, **sin** advertencia ni historial — eso lo hace el repositorio, que es lo que debe llamarse desde la pantalla.

### RecetaDao ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/dao/RecetaDao.kt
- Qué hace: todas las consultas de recetas, sus secciones, ingredientes, rendimiento, precios y simulación.
- Cómo funciona: incluye `costoTotalReceta` (suma en SQL con COALESCE, porque SUM sobre cero filas da NULL), su versión en lote `costoDeVariasRecetas` —**ojo: una receta sin ingredientes no aparece en el resultado y hay que tomarla como 0**—, `obtenerRecetasQueUsan` para la advertencia de borrado, `obtenerRecetasConMoldeOrigen` para propagar ediciones de molde, `crearReceta`, que en una transacción siembra rendimiento (trozos = 1), simulación y primera sección, `fijarPrecioDeReferencia`, que también va en transacción porque apagar las demás referencias y encender la elegida en dos pasos sueltos deja una ventana con dos referencias o ninguna, y **`observarCostos`, que devuelve `Flow` y es la que hay que usar para mostrar costos**: Room vigila las tres tablas de la consulta y reemite sola cuando cambia cualquiera.

### MoldeDao ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/dao/MoldeDao.kt
- Qué hace: el catálogo de moldes.
- Cómo funciona: CRUD simple con `observarTodos` como `Flow`. Borrar un molde no rompe las recetas enlazadas: su `moldeOrigenId` pasa a `null` por la clave foránea y conservan sus medidas.

### EmpleadoDao ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/dao/EmpleadoDao.kt
- Qué hace: empleados, sus sueldos por receta y su simulación múltiple.
- Cómo funciona: `observarTodos` deja al genérico primero (`esGenerico DESC`). `eliminarPorId` lleva `AND esGenerico = 0` como red de seguridad, para que el genérico no pueda borrarse ni por error. `guardarSueldo` usa REPLACE apoyándose en el índice único (empleadoId, recetaId), así nunca quedan dos sueldos para la misma receta.

### HistorialDao ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/dao/HistorialDao.kt
- Qué hace: guarda y lee los eventos del historial de cambios.
- Cómo funciona: `observarTodos` los devuelve del más reciente al más antiguo. `borrarAnterioresA` implementa la retención, y se llama al insertar cada evento para no necesitar un proceso aparte.

---

<a name="orquestacion"></a>
## Orquestación (`data/repositorio/`) — leen, calculan y escriben

### confirmarEliminacionIngrediente
- Ubicación: data/repositorio/IngredienteRepositorio.kt
- Qué hace: borra un ingrediente de verdad, después de que el usuario ya confirmó la advertencia.
- Cómo funciona: `suspend`, recibe `ingredienteId`. Lee el nombre **antes** de borrar (lo necesita el historial), quita las filas `RecetaIngrediente` que lo referencian, borra el ingrediente y registra un evento rojo indicando qué recetas se vieron afectadas. No vuelve a preguntar: la confirmación es responsabilidad de la UI (6.3). El costo de las recetas afectadas se reajusta solo, porque se calcula en vivo.

### RecetaRepositorio ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hace: todo lo que se hace con una receta y sus partes — crearla, renombrarla, borrarla, sus secciones, sus ingredientes, su costo, su snapshot y su precio de referencia.
- Cómo funciona: **cada función que puede fallar revisa antes de escribir**, así "cancelar y dejar todo como estaba" no necesita deshacer nada. Devuelve `Resultado` (`Listo` / `NoSePudo(motivo)`) en vez de `Boolean`, para que el motivo viaje junto al fracaso. Destacan: `nombreQueFaltaBautizar` (devuelve la sugerencia solo cuando hay exactamente una sección, o `null` si no hay nada que bautizar), `agregarSeccion` (renombra la primera **sin mover sus ingredientes de lugar**, 8.2), `eliminarSeccion` (nunca deja la receta sin ninguna) y `elegirPrecioDeReferencia`.

### RecetaRepositorio.elegirPrecioDeReferencia ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hace: cambia cuál de los precios de una receta alimenta las cifras automáticas (8.6).
- Cómo funciona: `suspend`, recibe `recetaId` y `precioId`, devuelve el motivo del rechazo o `null` si se pudo. Arma el snapshot, pregunta a `errorAlElegirReferencia` y **solo entonces** escribe con `fijarPrecioDeReferencia`. Si el precio pierde plata no toca nada: la referencia anterior sigue siendo la que era. Un rechazo tampoco deja evento en el historial, porque no pasó nada.

### RecetaRepositorio.obtenerDatosCalculo ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hace: arma de una sola vez la foto de una o varias recetas (costo, trozos y precios) que después usan todas las fórmulas.
- Cómo funciona: `suspend`, recibe una `List<Long>` y devuelve `Map<Long, DatosCalculoReceta>`. Recibe lista y no un id suelto a propósito: la simulación múltiple pide todas sus recetas juntas y resuelve con tres consultas en lote en vez de tres por receta. **Ojo con lo que no viene**: una receta sin ingredientes no aparece en el resultado de la consulta de costos (el `GROUP BY` no le da fila) y hay que tomarla como 0; los trozos que falten caen a 1, el valor con que se siembra la receta.

### Resultado y ResultadoCrearReceta ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hacen: dicen cómo terminó una operación que podía no poder hacerse.
- Cómo funcionan: `Resultado` es `Listo` / `NoSePudo(motivo)`; `ResultadoCrearReceta` es `Creada(recetaId)` / `NoValido(motivo)`. Son tipos cerrados y no `Boolean` para que el motivo viaje junto con el fracaso: la pantalla tiene que poder decir *por qué* no se pudo, y un `false` no lo dice. Misma idea que `ResultadoGuardarIngrediente`.

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

### HistorialRepositorio.registrar ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/HistorialRepositorio.kt
- Qué hace: anota un cambio en el historial y de paso limpia lo más viejo que seis meses.
- Cómo funciona: `suspend`, recibe tipo, entidad, descripción y un detalle opcional. **La descripción siempre debe nombrar lo afectado**, nunca un texto genérico, lo que obliga a leer el nombre antes de borrar nada. Tras insertar, borra los eventos anteriores a `RETENCION_HISTORIAL_MS`.

### ResultadoGuardarIngrediente ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/IngredienteRepositorio.kt
- Qué hace: dice cómo terminó un intento de guardar un ingrediente.
- Cómo funciona: tipo cerrado con `Guardado(id)`, `YaExiste(existente)` y `NoValido(motivo)`. Es un tipo cerrado y no un simple `Long` para que la pantalla **no pueda ignorar** los casos que no son éxito: si `crear` devolviera solo el id, olvidar comprobar el nombre repetido cerraría la app, porque el índice único de la base lanza excepción al insertar.

### IngredienteRepositorio.crear ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/IngredienteRepositorio.kt
- Qué hace: crea un ingrediente, comprobando antes que el dato sirva y que no esté repetido.
- Cómo funciona: `suspend`, recibe nombre y valor por gramo, devuelve `ResultadoGuardarIngrediente`. Valida nombre y valor con las funciones de `logica/validaciones`, busca repetidos con `buscarParecido`, y solo entonces inserta y registra en el historial. Las comprobaciones van acá y no en la pantalla porque hay dos formas de crear un ingrediente —el catálogo y el alta rápida desde una receta— y ambas deben comportarse igual.

### IngredienteRepositorio.actualizar ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/IngredienteRepositorio.kt
- Qué hace: guarda los cambios de un ingrediente que ya existe.
- Cómo funciona: `suspend`, recibe el `Ingrediente` completo y devuelve `ResultadoGuardarIngrediente`. Hace las mismas comprobaciones que `crear`, salvo que al buscar repetidos se excluye a sí mismo con `exceptoId`: cambiarle solo el precio a "Harina" no debe chocar con "Harina".

### IngredienteRepositorio.buscarParecido ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/IngredienteRepositorio.kt
- Qué hace: busca un ingrediente que se llame igual, para avisar antes de crear un repetido.
- Cómo funciona: `suspend`, recibe el nombre y opcionalmente un id a excluir (al editar). Trae la lista y compara en memoria con `sonElMismoTexto`, porque SQLite no sabe ignorar tildes y para ella "azucar" y "azúcar" son distintos. No consulta antes por nombre exacto: sería redundante, ya que todo lo que encontraría esa consulta lo encuentra también esta. Devuelve `null` si no hay parecido.

### IngredienteRepositorio.recetasAfectadasPorBorrar ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/IngredienteRepositorio.kt
- Qué hace: dice qué recetas usan un ingrediente, para la advertencia previa al borrado.
- Cómo funciona: `suspend`, recibe el id y devuelve `List<Receta>`. La pantalla **debe** llamarla antes de ofrecer el borrado definitivo. Si viene vacía igual se pide confirmación, pero sin listado.

### IngredienteRepositorio.confirmarEliminacion ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/IngredienteRepositorio.kt
- Qué hace: borra el ingrediente de verdad, una vez que el usuario ya confirmó.
- Cómo funciona: `suspend`, recibe el id. Lee el nombre y las recetas afectadas **antes** de borrar, porque después ya no se pueden consultar y el historial los necesita. Quita las filas que lo referencian (no hay clave foránea que lo haga solo) y recién ahí borra la fila. No vuelve a preguntar: la confirmación es de la pantalla.

### AppContainer ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/AppContainer.kt
- Qué hace: arma y guarda las piezas compartidas de la app — la base de datos y los repositorios.
- Cómo funciona: se crea una vez desde `ReposteriaApp` y expone `historial` e `ingredientes`, todos con `by lazy` para no construir nada hasta que se pida. Hace el trabajo de una librería de inyección de dependencias pero escrito a mano: en un solo archivo se ve qué depende de qué. **Acá se agregan los repositorios de recetas, moldes y empleados al llegar sus fases.**

### ReposteriaApp ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ReposteriaApp.kt
- Qué hace: el punto de entrada de la app.
- Cómo funciona: extiende `Application` y su única tarea es exponer el `contenedor` (`AppContainer`). Las pantallas llegan a los repositorios desde el contexto de la aplicación.

### AppDatabase.SembrarDatosIniciales ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/AppDatabase.kt
- Qué hace: crea el empleado genérico la primera vez que se arma la base de datos.
- Cómo funciona: `RoomDatabase.Callback` cuyo `onCreate` inserta con SQL directo la fila con `esGenerico = 1` (los DAO todavía no existen en ese momento). Sin esto, la garantía de que el empleado genérico "siempre está" sería falsa y la sección Empleados arrancaría vacía.

### TipografiaReposteria ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/theme/Type.kt
- Qué hace: define los tamaños de letra de la app.
- Cómo funciona: `Typography` de Material 3 que ajusta solo cuatro estilos (`headlineMedium`, `titleMedium`, `bodyMedium`, `bodySmall`) y hereda el resto. Todo en `sp` y no en `dp`, para que los textos crezcan si el celular tiene configurada una letra más grande por accesibilidad.

### Medidas ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/theme/Medidas.kt
- Qué hace: las separaciones y tamaños que usan todas las pantallas.
- Cómo funciona: objeto con `minimo` (4dp), `chico` (8dp), `medio` (16dp), `grande` (24dp), `objetivoTactil` (48dp) y `altoMaximoDeLista` (200dp). Existe para que ninguna pantalla invente sus propios números: si cada una elige cuánto separar, la app termina desalineada sin que nadie lo haya decidido. `objetivoTactil` es el alto mínimo de cualquier cosa que se toque; `altoMaximoDeLista` es el tope de una lista metida dentro de otra cosa (la advertencia de borrado, el `ComboBuscable`), que pasado ese alto se desplaza por dentro en vez de empujar los botones fuera de la pantalla.

### BarraBusqueda ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/componentes/BarraBusqueda.kt
- Qué hace: el campo de búsqueda que usan las cuatro secciones.
- Cómo funciona: Composable que recibe el texto actual y qué hacer al cambiar; la pantalla es dueña del texto, no él. Lleva lupa, y una X para limpiar que aparece **solo cuando hay algo escrito**. No filtra nada por su cuenta: la regla de qué cuenta como coincidencia vive en `logica/busqueda` para que las 4 pantallas busquen igual.

### CampoNumerico ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/componentes/CampoNumerico.kt
- Qué hace: el campo donde se escribe un monto o una cantidad. Pone el punto de mil solo y deja el cursor donde corresponde.
- Cómo funciona: Composable que recibe el valor, qué hacer al cambiar, la etiqueta y opcionalmente un error y un texto de ayuda. Guarda un `TextFieldValue` y no un `String` porque el `String` no lleva la posición del cursor: el campo la conserva como un número, y ese número deja de significar lo mismo cuando el texto se alarga con el punto de mil. Dónde va el cursor lo decide `formatearMientrasSeEscribe(texto, cursor)`, en `logica/`. **Es la única puerta de entrada de números de la app**: cualquier campo numérico nuevo va por acá y no con un `OutlinedTextField` suelto, porque si no hay que volver a resolver lo del cursor en cada pantalla.

### ComboBuscable ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/componentes/ComboBuscable.kt
- Qué hace: un buscador que además deja elegir de la lista y crear ahí mismo lo que no aparece.
- Cómo funciona: Composable **genérico** — recibe las opciones, cómo sacar el texto de cada una, el texto buscado y qué hacer al elegir; opcionalmente un `alCrear` que habilita el alta rápida. Filtra con `filtrarPor`. La fila de "Crear «x»" aparece solo si hay algo escrito que no coincide exactamente con una opción existente. Muestra la lista **debajo** y no en un menú flotante: en un celular un desplegable tapa justo el formulario que se está llenando y pelea con el teclado. Se usa para agregar un ingrediente a una receta (7) y para elegir un molde del catálogo (9.3).

### ListaIngredientesScreen ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/ListaIngredientesScreen.kt
- Qué hace: conecta la pantalla de ingredientes con su ViewModel.
- Cómo funciona: Composable que recibe el `IngredientesViewModel`, lee su estado con `collectAsStateWithLifecycle` (deja de leer la base cuando la pantalla no se ve) y reparte cada acción. **No dibuja nada**: eso lo hace `ListaIngredientes`.

### ListaIngredientes ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/ListaIngredientesScreen.kt
- Qué hace: dibuja la pantalla de ingredientes — botón fijo arriba, buscador, y la lista debajo.
- Cómo funciona: Composable que recibe **solo datos y funciones**, nunca el ViewModel ni el repositorio, así que se puede ver en la vista previa de Android Studio con ingredientes inventados y no puede tocar la base por accidente. El botón de agregar va fuera del área que se desplaza (patrón de 8.1, el mismo de recetas y moldes). Distingue "no hay ingredientes" de "la búsqueda no encontró nada", que son dos mensajes distintos. Cada monto pasa por `formatearNumero`.

### FormularioIngrediente ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/DialogosIngrediente.kt
- Qué hace: el cuadro para crear o editar un ingrediente.
- Cómo funciona: Composable único para los dos casos —piden los mismos dos datos—; lo que cambia es el título y qué hace el ViewModel al confirmar. No valida por su cuenta: muestra los errores que ya vienen calculados en `DialogoIngrediente.Formulario`. El hueco del mensaje de error se reserva siempre, para que el cuadro no dé un salto mientras se escribe.

### ConfirmarBorradoIngrediente ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/DialogosIngrediente.kt
- Qué hace: la advertencia obligatoria antes de borrar un ingrediente (política de 7.1).
- Cómo funciona: Composable que lista las recetas afectadas y solo entonces habilita el botón de eliminar. **Mientras la lista es `null` el botón está deshabilitado**: `null` significa "todavía se está consultando" y lista vacía significa "no lo usa ninguna receta"; confundirlos dejaría borrar sin haber mostrado la advertencia completa.

### ConfirmarReemplazoValor ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/DialogosIngrediente.kt
- Qué hace: la confirmación antes de pisar el valor de un ingrediente desde la calculadora (7.2).
- Cómo funciona: Composable que muestra los dos números juntos —el que tiene y el que va a quedar—, el nuevo en el verde de "edición" del historial, que es el mismo color con que va a quedar anotado el cambio. Es la única pantalla donde se pueden comparar antes de que el viejo desaparezca: un valor por gramo pisado no se puede deshacer y el costo de todas las recetas que lo usan cambia en el mismo momento.

### CalculadoraValorPorGramo ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/CalculadoraValorPorGramo.kt
- Qué hace: la pantalla de la calculadora de valor por gramo (7.2).
- Cómo funciona: Composable que recibe `EstadoCalculadora` y las funciones de la sección de la calculadora. Es una **pantalla completa** y no un cuadro de diálogo porque tiene dos partes (la cuenta y a quién aplicársela) y eso no cabe en un cuadro flotante con el teclado abierto. Todo va dentro de un solo `LazyColumn` —los campos también, como elementos— para que no queden dos zonas que se desplazan por separado y para que el teclado empuje el campo que se está llenando en vez de taparlo. Lleva `BackHandler`, así el botón de atrás del teléfono la cierra en vez de salir de la app.

### IngredientesViewModel ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/IngredientesViewModel.kt
- Qué hace: guarda lo que se ve en la pantalla de ingredientes y ejecuta lo que se pide desde ella.
- Cómo funciona: expone `estado: StateFlow<EstadoIngredientes>`, armado con `combine` de cuatro fuentes (la lista de la base, el texto buscado, el diálogo abierto y el mensaje pendiente) — así el filtro se calcula una vez por cambio real y no en cada redibujado. `WhileSubscribed(5s)` corta la consulta cuando la pantalla deja de mirarse, con margen para que girar el teléfono no la reinicie. Sus acciones: `buscar`, `abrirAlta`, `abrirEdicion`, `cambiarNombre`, `cambiarValor`, `guardar`, `pedirBorrado`, `confirmarBorrado`, `cerrarDialogo` y `mensajeMostrado`. `guardar` traduce el `ResultadoGuardarIngrediente` del repositorio a algo visible; `pedirBorrado` abre la advertencia al instante y completa la lista de recetas cuando la consulta vuelve, comprobando que el cuadro siga abierto y sea el mismo ingrediente.

### IngredientesViewModel.fabrica ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/IngredientesViewModel.kt
- Qué hace: dice cómo construir el `IngredientesViewModel`, que necesita un repositorio y no tiene constructor vacío.
- Cómo funciona: función del `companion object` que recibe el `IngredienteRepositorio` y devuelve un `ViewModelProvider.Factory` armado con `viewModelFactory { initializer { … } }`. Existe porque el proyecto no usa una librería de inyección de dependencias (ver `AppContainer`). **Cada ViewModel nuevo necesita la suya**, con este mismo patrón.

### IngredienteDaoFalso, HistorialDaoFalso y RecetaDaoFalso ✅ IMPLEMENTADAS
- Ubicación: app/src/test/java/com/sandyyera/reposteria/data/DaosFalsos.kt
- Qué hacen: reemplazan a los DAO de Room con datos en memoria, para probar repositorios y ViewModel sin base de datos ni celular (`./gradlew :app:test`).
- Cómo funcionan: los DAO de Room son interfaces, así que se sustituyen sin tocar el código de la app. **`IngredienteDaoFalso` imita el índice único de la tabla** y lanza excepción ante un nombre repetido, igual que la base de verdad: sin eso, la prueba de "no se puede crear un duplicado" pasaría aunque la comprobación no existiera. `HistorialDaoFalso` expone `eventos` y `limpiezasPedidas` para revisarlos. `RecetaDaoFalso` creció en la Fase 3 hasta ser una base de recetas en memoria de verdad: hace las **cascadas** (borrar una receta se lleva sus secciones; borrar una sección, sus ingredientes), resuelve el **JOIN del costo** contra el catálogo de ingredientes leyendo el `valorPorGramo` del momento (decisión #3) —por eso recibe el mismo `IngredienteDaoFalso` que use la prueba—, y **omite del resultado en lote las recetas sin ingredientes**, igual que el `GROUP BY` real. Esa última trampa solo se puede probar si el falso la reproduce. Lo que aún no hace falta falla ruidosamente: un `emptyList()` de relleno haría pasar pruebas que no probaron nada.

### MigracionTest ✅ IMPLEMENTADA
- Ubicación: app/src/androidTest/java/com/sandyyera/reposteria/data/db/MigracionTest.kt
- Qué hace: comprueba que actualizar la app no se lleve por delante lo que ya estaba guardado.
- Cómo funciona: **la única prueba que necesita celular o emulador** (`./gradlew :app:connectedAndroidTest`), y no hay forma honesta de evitarlo: lo que se verifica es SQLite de verdad corriendo el `ALTER TABLE` de verdad, y la base de mentira en memoria de las otras 90 pruebas no tiene esquemas ni migraciones, así que aprobaría cualquier cosa. Escribe filas con el esquema de la versión 1, corre `runMigrationsAndValidate` y revisa **las dos mitades**: que el esquema resultante calce con `2.json` —si el `DEFAULT 0` de la migración no calzara con el `@ColumnInfo(defaultValue = "0")` de la entidad, la app no arrancaría en el celular— y que las filas sigan ahí, porque validar el esquema por sí solo dejaría pasar un `DROP TABLE` seguido de un `CREATE TABLE`. La segunda prueba abre la base ya migrada con Room entero, que es lo único que ejercita el `identityHash`. `MigrationTestHelper` lee `app/schemas/` como assets, declarados en `build.gradle.kts`: **cada versión nueva necesita su JSON versionado y su prueba acá.**

### RecetasViewModel ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/RecetasViewModel.kt
- Qué hace: guarda lo que se ve en la lista de recetas y ejecuta lo que se pide desde ella.
- Cómo funciona: mismo patrón que `IngredientesViewModel` — `combine` de cuatro fuentes y `WhileSubscribed(5s)`. Lo propio de acá es que el costo de cada receta se pide **en lote** con `costosDe` y no una por una: con veinte recetas serían veinte consultas cada vez que cambia cualquier cosa. Sus acciones: `buscar`, `abrirAlta`, `abrirCambioDeTitulo`, `cambiarTitulo`, `guardar`, `pedirBorrado`, `confirmarBorrado`, `cerrarDialogo` y `mensajeMostrado`.

### RecetaConCosto, EstadoRecetas y DialogoReceta ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/RecetasViewModel.kt
- Qué hacen: los tipos que la lista de recetas necesita para dibujarse.
- Cómo funcionan: `RecetaConCosto` junta la receta con lo que cuesta hacerla ahora mismo. `EstadoRecetas` distingue `catalogoVacio` de `busquedaSinResultados`, igual que ingredientes. `DialogoReceta` es cerrado (`Ninguno` / `Formulario` / `ConfirmarBorrado`); su `ConfirmarBorrado` **no necesita consultar nada antes**, a diferencia del de ingredientes: lo que se pierde al borrar una receta está todo dentro de ella.

### ListaRecetasScreen, ListaRecetas y AccionesRecetas ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/ListaRecetasScreen.kt
- Qué hacen: la sección de recetas — botón fijo arriba, buscador, y las recetas debajo con su costo.
- Cómo funcionan: la misma división de siempre — `ListaRecetasScreen` conecta el ViewModel, `ListaRecetas` solo dibuja y se puede ver en la vista previa. `AccionesRecetas` agrupa las diez funciones. La tarjeta de receta usa `tertiaryContainer` (el rosa pastel) en vez del crema del resto: es el único lugar con color propio, para que la lista se reconozca de un vistazo.

### CantidadesViewModel ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/CantidadesViewModel.kt
- Qué hace: el cerebro del paso "Cantidades" de una receta (8.2) — secciones, ingredientes y costo.
- Cómo funciona: las consultas de secciones e ingredientes **no son reactivas** (los DAO devuelven listas, no `Flow`), así que lleva un contador `recargar` que entra al `combine` y se incrementa al terminar cada operación, disparando una relectura. Es más simple que volver reactivas siete consultas y cuesta una lectura por acción, no por segundo. **El costo total se relee de la base** en vez de sumarse en memoria: sumarlo acá crearía una segunda verdad sobre el mismo número, y la que manda al calcular precios y sueldos es la de la base. Hay un test que comprueba que los dos caminos den lo mismo.

### LineaDeIngrediente, SeccionConIngredientes, EstadoCantidades y DialogoCantidades ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/CantidadesViewModel.kt
- Qué hacen: los tipos del paso de cantidades.
- Cómo funcionan: `LineaDeIngrediente` cruza la fila de la receta con la ficha del catálogo —el cruce se hace en memoria y no con un `JOIN` porque la pantalla ya tiene el catálogo cargado para el buscador—, y expone `subtotal`. `SeccionConIngredientes` expone `costo`, la suma de sus líneas: **se suma en memoria y no se consulta**, al revés que el costo total, y no es una inconsistencia — el total manda porque de él salen precios y sueldos, así que viene de la base; el de la sección no alimenta ninguna cuenta y lo que sí tiene que hacer es cuadrar con las líneas que se ven justo encima, cosa que sumando esas mismas líneas pasa por construcción. `EstadoCantidades` consulta `debeMostrarNombreDeSeccion` para decidir si se ven los encabezados, y expone `mostrarCostoPorSeccion`, que es **desde dos secciones** y no desde una con nombre propio: con una sola, su costo es el total que ya está arriba en grande. `DialogoCantidades` es cerrado: `PonerIngrediente` (se llama así y no `Ingrediente` para no chocar con la entidad), `Seccion`, `RenombrarSeccion` y `ConfirmarBorrarSeccion`. Los dos de sección llevan `rechazo`, que es lo que contestó el repositorio y entra en su `error` **antes** que la validación del campo: **un aviso sobre lo que se acaba de escribir va junto al campo y nunca en la franja de abajo**, porque con el teclado abierto esa franja queda tapada y el cuadro parece no haber hecho nada — se vio así en el celular con un nombre de sección repetido. Se limpia al escribir, porque el rechazo era sobre lo anterior.

### PasoCantidadesScreen, PasoCantidades y AccionesCantidades ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/PasoCantidadesScreen.kt
- Qué hacen: la pantalla del paso 1 de una receta.
- Cómo funcionan: misma división de siempre — una parte conecta el ViewModel, la otra solo dibuja y tiene vistas previas. **El costo total va fijo arriba, fuera del desplazamiento**: es el número por el que existe la pantalla, y con una receta larga quedaría fuera de vista justo mientras se ajustan las cantidades. Cada línea muestra la cuenta completa (`500 g × $1,2 = $600`) y no solo el total, para que un valor por gramo mal puesto salte a la vista. En el cuadro de agregar, el campo de gramos aparece **después** de elegir el ingrediente: pedir los dos a la vez obliga a decidir cuánto antes de saber de qué.

### NavegacionPrincipal y Seccion ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/NavegacionPrincipal.kt
- Qué hacen: el menú de 3 líneas y la sección que se esté viendo (12.1).
- Cómo funcionan: `ModalNavigationDrawer` con un `enum Seccion` que hoy tiene Ingredientes y Recetas. Abrir una receta la muestra **a pantalla completa, sin el menú**: es un paso dentro de la receta, no una sección de la app. Se expresa como dos ramas de un `if` (`MenuDeSecciones` aparte) y no con un `return` temprano dentro del Composable, para que quede claro que son dos árboles distintos. El ViewModel de cada receta lleva `key = "cantidades-<id>"`: sin esa clave, abrir una segunda receta reutilizaría el de la primera y mostraría los ingredientes equivocados. **Moldes y Empleados no están puestos en gris**: una opción que no lleva a ninguna parte se toca igual y parece que algo se rompió; se agregan al enum cuando exista su pantalla. Cada sección conserva su ViewModel al cambiar de una a otra, porque `viewModel()` los guarda en la Activity — ir a Recetas y volver no borra lo escrito en el buscador. La sección elegida va en `rememberSaveable` para sobrevivir al giro del teléfono.

### RecetaRepositorio.observarCostos ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hace: el costo de todas las recetas, avisando solo cuando cambia. Es la que hay que usar para **mostrar** costos.
- Cómo funciona: devuelve `Flow<Map<Long, Double>>`. Nació de un bug visto en el celular: la lista pedía los costos con `costosDe` —una foto de un momento— colgada del `Flow` de la tabla `recetas`; borrar un ingrediente no toca esa tabla, así que nadie volvía a preguntar y la lista seguía mostrando costos que ya no existían. Con un `Flow`, Room vigila `receta_ingredientes`, `receta_secciones` e `ingredientes` y reemite en cuanto cambia cualquiera. **El mapa no trae entrada para las recetas sin ingredientes** (el `GROUP BY` no les da fila) y acá no se puede rellenar como en `costosDe`, porque no se sabe qué recetas hay sin consultarlas: quien lo lea toma lo que falte como 0. **Regla que deja establecida: lo que se muestra se observa; `costosDe` es para calcular.**

### RecetaRepositorio.seccionRepetida ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hace: dice si dentro de una receta ya hay una sección con ese nombre.
- Cómo funciona: privada; recibe las secciones existentes y el nombre, devuelve `Resultado.NoSePudo` con el motivo o `null`. La usan `agregarSeccion` y `renombrarSeccion` —esta última excluyendo la propia sección, para que corregirle una tilde no choque consigo misma— y también se comprueba el bautizo de la primera contra el nombre nuevo, porque la sugerencia es el título de la receta y "Salsa de chocolate" como receta y como sección es un caso corriente. Compara con `sonElMismoTexto`. **No hay índice único que lo respalde**, por lo mismo que los títulos de receta: podría haber repetidos guardados de antes y un índice obligaría a renombrarlos en la migración. A diferencia de una receta repetida, una sección repetida que ya exista **no bloquea nada**.

### RecetaRepositorio.costosDe ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hace: el costo de varias recetas de una sola consulta, para la lista.
- Cómo funciona: `suspend`, recibe `List<Long>` y devuelve `Map<Long, Double>` **con una entrada por cada id pedido**, incluidos los que la consulta no trae. El `GROUP BY` no da fila para una receta sin ingredientes, y quien reciba un mapa incompleto tarde o temprano hace `getValue` y se cae; ese remiendo va acá una vez y no en cada llamador. La usan la lista y `obtenerDatosCalculo`.

### RosaReceta y RosaRecetaOscuro ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/theme/Color.kt
- Qué hacen: el rosa pastel de las tarjetas de receta, en claro y en oscuro.
- Cómo funcionan: se exponen por el rol `tertiaryContainer` del tema. **Son superficie, nunca señal**, y esa distinción es la que evita el problema que advierte 12.6: el frambuesa de eliminar también es un rosa. No se confunden porque juegan en planos distintos — el pastel es un fondo grande y lavado, el frambuesa es texto o ícono saturado encima. Medido: el frambuesa mantiene 4,57:1 sobre el rosa claro y 4,81:1 sobre el oscuro. **Regla para los pasteles que vengan: pueden pintar un fondo; ninguno puede pintar un texto, un ícono ni un borde que signifique algo.**

### luminancia y contraste ✅ IMPLEMENTADAS
- Ubicación: herramientas/contraste.py
- Qué hacen: miden si un color de texto se lee sobre su fondo, según la fórmula de la WCAG que sigue Android.
- Cómo funcionan: `luminancia(hexadecimal)` devuelve la luminancia relativa de un color; `contraste(frente, fondo)` devuelve la razón entre los dos (4,5:1 es el mínimo para texto normal, 3:1 para bordes). La lista `PARES` enumera cada combinación real de la paleta y el script termina con código 1 si alguna queda por debajo. **Al agregar un color a `Color.kt` hay que agregar su par acá**: a ojo esto no se puede evaluar — el caramelo original parecía perfectamente legible con texto blanco y daba 3,83:1.

### revisar_simbolos, revisar_importaciones, revisar_acciones, revisar_enchufes, revisar_constantes y revisar_esquemas ✅ IMPLEMENTADAS
- Ubicación: herramientas/revisar_kotlin.py
- Qué hacen: las seis revisiones del código Kotlin que se pueden hacer sin compilador ni Android SDK, que es la situación de siempre acá — `:app` solo compila en el equipo de Sandy.
- Cómo funcionan: `revisar_simbolos` cuenta llaves, paréntesis y corchetes; `revisar_importaciones` marca un tipo en CamelCase usado sin `import` ni definición en su paquete; `revisar_acciones` compara cada campo de un `Acciones*` con la firma del `modelo::metodo` al que se ata, y solo avisa si **ninguna** firma con ese nombre calza (`pedirBorrado` existe en dos ViewModel); `revisar_enchufes` hace lo mismo contra el parámetro de la pantalla (`abrir = alAbrirReceta`), que es por donde se coló el `(Receta) -> Unit` que recibía un `Long`; `revisar_constantes` marca una constante en MAYÚSCULAS escrita a secas que en realidad vive dentro de un `companion object` —pasó con `MIGRACION_1_2`, que resuelve dentro de su propia clase y falla solo en el archivo de afuera que la usa, y ese archivo era la prueba instrumentada, que tarda casi cuatro minutos en compilar—; `revisar_esquemas` compara la versión declarada en `AppDatabase` con los `app/schemas/N.json` versionados, y así descubrió que faltaba el de la versión 2. Todas devuelven cuántos problemas encontraron y `main` termina con código 1 si hay alguno. **Solo mira nombres y tipos escritos tal cual**: nada que dependa de inferencia —el tipo de un `val` local, por ejemplo— está a su alcance, así que pasar limpio no significa que compile.

### respaldo_bd.sh (bajar, subir, listar) ✅ IMPLEMENTADO
- Ubicación: herramientas/respaldo_bd.sh
- Qué hace: baja el archivo de la base del celular al computador y lo devuelve, para no perder los datos de prueba al reinstalar la app.
- Cómo funciona: `bajar` guarda una copia fechada en `$HOME/respaldos-reposteria` (o donde diga `REPOSTERIA_RESPALDOS`), `subir <carpeta>` la devuelve pidiendo confirmación escrita, `listar` las muestra. **Copia los tres archivos —`.db`, `-wal` y `-shm`—**: Room activa el modo WAL, así que lo último escrito puede estar todavía en el `-wal` y una copia de solo el `.db` sale vieja sin avisar; al subir, un `-wal` que sobre se borra, porque uno viejo sobre una base nueva la corrompe. Usa `adb exec-out` con `run-as` y no `adb pull`, ya que `databases/` es carpeta privada de la app; por lo mismo **solo funciona con la app instalada en debug**. Antes de subir hace `am force-stop`: escribir el archivo por debajo de una base abierta deja a Room leyendo una cosa y al disco con otra.

### sin_comentarios_ni_textos y tipos_de_parametros ✅ IMPLEMENTADAS
- Ubicación: herramientas/revisar_kotlin.py
- Qué hacen: las dos ayudas que usan las revisiones de arriba para leer Kotlin con expresiones regulares sin equivocarse.
- Cómo funcionan: `sin_comentarios_ni_textos(codigo)` borra comentarios, literales de texto y **nombres entre acentos graves** — adentro de un `fun \`sin peso muestra No especificado\`()` va prosa en español, y sus palabras con mayúscula se leían como tipos sin importar. `tipos_de_parametros("a: Long, b: Receta")` devuelve `["Long", "Receta"]` cortando solo por las comas de nivel cero, para no partir un `Map<String, Int>` por la mitad.

---

<a name="tipos-de-datos"></a>
## Tipos de datos

### DatosCalculoReceta ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: la foto de una receta —id, título, costo total, trozos y precios— que se lee una vez y se le pasa a todas las fórmulas.
- Cómo funciona: `data class` que **no es una tabla**, se arma en memoria con `obtenerDatosCalculo`. Valida en su constructor que `trozos >= 1`, así ninguna fórmula que divida por trozos puede reventar. Expone `tienePrecio`, que hay que consultar antes de pedir cualquier cifra automática, y `tieneReferenciaElegida`, que distingue "la referencia la elegiste tú" de "se está usando el respaldo". Es lo que permite que las funciones de `logica/` sean puras y probables sin base de datos, y que todas las cifras de una pantalla salgan de la misma lectura.

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

### ErroresIngrediente ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Validaciones.kt
- Qué hace: los problemas de un formulario de ingrediente, uno por campo.
- Cómo funciona: `data class` con `nombre: String?` y `valorPorGramo: String?` (`null` = ese campo está bien), más `sirve` que dice si no hay nada que corregir. Va por campo y no como un solo mensaje porque la pantalla tiene que poder mostrar cada aviso **bajo el campo que lo causó**.

### UnidadDeCompra ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/calculadora/ValorPorGramo.kt
- Qué hace: en qué unidad viene escrito el peso del paquete que se está costeando.
- Cómo funciona: `enum` con `GRAMO` y `KILO`, más el método `aGramos(cantidad)` que hace la conversión. Existe porque en repostería casi todo se compra en kilos y se usa en gramos, y esa conversión de cabeza es el paso donde se equivoca uno. **No se guarda en la base**: es un dato de la pantalla mientras dura la cuenta, así que no necesita `TypeConverter`.

### ErroresCalculadora ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/calculadora/ValorPorGramo.kt
- Qué hace: los problemas de la calculadora, uno por campo.
- Cómo funciona: `data class` con `precio: String?` y `cantidad: String?` (`null` = ese campo está bien), más `sirve`. Es un tipo aparte de `ErroresIngrediente` aunque tenga la misma forma: los nombres de los campos son parte de lo que significa, y un tipo genérico de "dos textos o nulos" dejaría de decir cuál es cuál justo donde importa, que es al pintarlos bajo su campo.

### DestinoDelValor ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/IngredientesViewModel.kt
- Qué hace: dice qué se va a hacer con el valor que salió de la calculadora.
- Cómo funciona: tipo cerrado con `Crear` (abrir el formulario de alta con el valor puesto) y `Reemplazar(ingredienteId)`. Arranca en `null` —nada elegido— a propósito: tocar "Listo" sin elegir avisa en vez de suponer. Guarda el **id** y no el ingrediente entero para que el "valor actual" que se muestra salga siempre de la lista viva y no de una copia que quedó vieja; si ese ingrediente se borró mientras tanto, la elección deja de resolverse sola.

### EstadoCalculadora ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/IngredientesViewModel.kt
- Qué hace: lo que la calculadora de valor por gramo (7.2) necesita para dibujarse.
- Cómo funciona: `data class` con lo escrito (`precio`, `cantidad`, `unidad`, `busquedaDestino`, `destino`), los `tocado…` que evitan mostrar errores antes de tiempo, `faltaElegirDestino` para el aviso de "no elegiste nada", y dos campos que **no se escriben a mano** sino que rellena el `combine` del ViewModel: `candidatos` (la lista ya filtrada) y `elegido` (el ingrediente resuelto contra la base). Expone `resultado`, `puedeTerminar` y `creandoNuevo`.

### AccionesIngredientes ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/ListaIngredientesScreen.kt
- Qué hace: agrupa todo lo que se puede pedir desde la sección de ingredientes.
- Cómo funciona: `data class` de 19 funciones, todas con una implementación vacía por defecto para que las vistas previas escriban solo las que les importan. Van agrupadas porque sueltas eran diecinueve parámetros: basta cambiar dos de orden para conectar el botón de borrar con el de editar, y el compilador no diría nada porque tienen la misma forma. **Al agregar una acción nueva va acá**, no como parámetro suelto.

### EstadoIngredientes ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/IngredientesViewModel.kt
- Qué hace: todo lo que la pantalla de ingredientes necesita para dibujarse.
- Cómo funciona: `data class` con `visibles` (la lista **ya filtrada** por el buscador — la pantalla no vuelve a filtrar), `hayIngredientes`, `busqueda`, `dialogo`, `mensaje` y `cargando`. Expone `catalogoVacio` y `busquedaSinResultados`, que son dos situaciones distintas y necesitan mensajes distintos: "todavía no agregaste nada" no es lo mismo que "no encontré eso".

### DialogoIngrediente ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/IngredientesViewModel.kt
- Qué hace: dice qué hay abierto encima de la lista de ingredientes.
- Cómo funciona: tipo cerrado con `Ninguno`, `Formulario` y `ConfirmarBorrado`. Es cerrado y no varios booleanos sueltos porque con booleanos nada impide que dos queden en `true` y aparezcan dos cuadros superpuestos. `Formulario` guarda los campos escritos, si cada uno ya se tocó (para no mostrar errores antes de tiempo) y el aviso de nombre repetido que llega desde la base. `ConfirmarBorrado` guarda las recetas afectadas como `List<Receta>?`, donde `null` es "todavía consultando" y lista vacía es "no lo usa ninguna receta".

### PrecioVigente ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: un precio o promoción tal como lo ven las fórmulas: "vender N trozos (o N productos) por X en total".
- Cómo funciona: `data class` con `modo`, `cantidad`, `precioTotal`, `etiqueta` y `esReferencia`. Es el equivalente puro de la tabla `receta_precios`: la entidad de Room vive en `:app` y se convierte a este tipo al armar el snapshot. Existe porque `:logica` no puede depender de Android, y es lo que permite probar las fórmulas de precios sin base de datos. **No lleva el `id`** a propósito: las fórmulas no necesitan saber de identificadores, y el repositorio ya trabaja con la fila completa.

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

> `Convertidores` está registrado más arriba, en **Acceso a datos**, que es donde vive.
