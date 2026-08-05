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

### esNumeroEntero ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Validaciones.kt
- Qué hace: dice si un número escrito no tiene decimales. La usan los cinco campos que piden un entero (trozos, duración, cantidad de una promoción, días por semana, unidades por día).
- Cómo funciona: recibe `Double` y devuelve `Boolean`, comparando contra `floor`. Existe por un error que estaba repetido en cuatro de esos campos: la forma "obvia" —`numero != numero.toInt().toDouble()`— **está mal para números grandes**, porque `toInt()` no desborda sino que se pega al tope de `Int`, así que un `10000000000` (que es entero) dejaba de coincidir consigo mismo y el campo respondía "tiene que ser un número entero" sobre un número entero, sin llegar nunca al aviso del tope, que era el que sí explicaba qué hacer.

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

### errorEnTrozosTexto, errorEnPesoFinalTexto y revisarRendimiento ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Rendimiento.kt
- Qué hacen: revisan los dos campos del paso "Rendimiento" (8.3).
- Cómo funcionan: reciben el texto escrito y devuelven el motivo o `null`. **Los trozos son enteros y mínimo 1**: de ahí salen todas las divisiones de la app (`costoPorTrozo`, `precioPorTrozoDe`, `pesoPorTrozo`), así que un 0 no es un dato raro sino una división por cero esperando en otra pantalla; hay además un tope de `MAXIMO_TROZOS` que no es regla del negocio sino la red contra escribir 8.000 en vez de 8. **El peso final es obligatorio solo sin molde**: ahí es lo único contra lo que se puede reescalar; con molde es opcional y se lee como "No especificado". Vacío y cero no son lo mismo: vacío con molde está bien, cero nunca.

### promocionesQueNoCabenEn, descripcionDePromocion y nombreDeLaCantidad ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Rendimiento.kt
- Qué hacen: dicen qué promociones quedarían imposibles al bajar los trozos de la receta, y cómo se nombra un precio.
- Cómo funcionan: `promocionesQueNoCabenEn(trozos, precios)` devuelve **la lista** y no un `Boolean`, porque el aviso tiene que nombrar cuáles: decir "hay promociones que no caben" obliga a revisarlas todas a mano. Es el tope del último trozo de 6.2 y **solo aplica al modo trozo** — vender 3 productos completos es posible por más que cada uno rinda 2. `descripcionDePromocion` da el texto para el aviso: la etiqueta si tiene, y si no su forma. `nombreDeLaCantidad(modo, cantidad)` es esa forma, y **mira el modo**, que es toda la razón de que exista: antes se decía "trozo" pasara lo que pasara, y en el celular una receta de 5 trozos con el trozo a $6.000 y el producto entero a $40.000 mostraba **dos filas base tituladas "1 trozo"**, una encima de la otra, distinguibles solo por el monto — la segunda parecía un error de tipeo. No es redacción: es la confusión que hace vender una torta entera al precio de una porción (8.6.2). Resuelve además el singular ("1 producto" / "2 productos").

### MAXIMO_TROZOS ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Rendimiento.kt
- Qué hace: cuántos trozos como máximo se aceptan en el campo.
- Cómo funciona: constante `200`. No es una regla del negocio: es que escribir 8.000 en vez de 8 deja el costo por trozo casi en cero y todo lo que sale de ahí sin sentido, sin ningún aviso.

### TipoDuracion, UnidadDuracion, describirDuracion y nombreDeLaUnidad ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/duracion/Duracion.kt
- Qué hacen: los tipos del paso "Duración" (8.4) y cómo se leen en la pantalla.
- Cómo funcionan: los dos `enum` **se movieron desde `data/db/entidades/` a `:logica`**, por lo mismo que `ModoPrecio` y `TipoFormaMolde` ya vivían allá: las validaciones y el texto que se muestra son lógica pura y se prueban sin base de datos. La entidad de Room los importa. `describirDuracion(apto, cantidad, unidad)` devuelve **texto y no un número**, porque los tres estados posibles son distintos y ninguno es una cifra: no apto, sin anotar, o una cantidad con su unidad; devolver `Int?` obligaría a cada pantalla a decidir cómo se lee cada caso, y ahí aparecen los "0 días". `nombreDeLaUnidad` resuelve el singular: "1 día" y no "1 días".

### errorEnPrecioTotalTexto, errorEnCantidadDePrecio, errorEnEtiquetaDePrecio, revisarPrecio y ErroresPrecio ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Precios.kt
- Qué hacen: revisan el formulario de un precio o promoción (6.2 y 8.5).
- Cómo funcionan: reciben lo escrito y devuelven el motivo o `null`, como el resto del paquete; `revisarPrecio` los junta en un `ErroresPrecio` **por campo**, para que cada aviso se pinte bajo el suyo (con el teclado abierto, la franja de abajo no se ve). Era el hueco de la Fase 7: las fórmulas que usan precios ya estaban escritas y probadas, pero **nada revisaba lo que se escribe antes de crearlos**, y de ahí salen dos divisiones por cero que reventarían mucho después y en otra pantalla (`precioPorTrozoDe` divide por la cantidad, `trozoGanador` por el precio). **El precio en 0 se rechaza**, a diferencia del valor por gramo de un ingrediente: un ingrediente regalado cuesta 0 y eso es un dato; un precio de venta en 0 no. `errorEnCantidadDePrecio` aplica el **tope del último trozo** de 6.2 —una promo de 3 trozos no cabe en una receta que rinde 2— **solo en modo trozo**, porque vender 3 productos completos sí es posible; su aviso nombra cuántos rinde la receta y va después del tope general, porque es el que más explica; para saber si es entero usa `esNumeroEntero` y no `toInt()`, que con un número grande respondía "tiene que ser un número entero" sobre un entero. `errorEnCantidadDePrecio` recibe además `basesQueFaltan`, que **bloquea cualquier cantidad mayor que 1** mientras la receta no tenga sus dos precios base (8.6.1): una promoción es una regla que se apoya en ellos, y "2 trozos por $20.000" sin precio de un trozo no tiene con qué cobrar el suelto que ella misma genera. Llega vacía por defecto y el aviso lo arma `faltanLasBases`, nombrando solo el que falta; el tope de la receta se comprueba **antes**, porque ante los dos problemas a la vez el específico explica más. La etiqueta es opcional y **delega en `errorEnNombreEscrito`** en vez de repetir su tope y su texto —lo único propio es dejar pasar el vacío—, para que el día que ese aviso cambie no quede una pantalla diciendo la frase vieja. **No reemplaza al repositorio**, que además sabe algo que acá no se puede saber: si el precio elegido como referencia pierde plata.

### basesQueFaltanEn ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: dice cuáles de los dos precios base —el del trozo y el del producto entero— todavía no están puestos.
- Cómo funciona: recibe `List<PrecioVigente>` y devuelve `List<ModoPrecio>`, primero el del trozo. **Es la única definición de "falta una base" de la app**, y recibe la lista y no el snapshot justamente para que la usen los dos lados que tienen que estar de acuerdo: `EstadoGastos.basesQueFaltan` (que pide los precios y no deja teclear una cantidad mayor que 1) y `RecetaRepositorio.crearPrecio`, que es el que decide de verdad. Escrita dos veces serían dos reglas que se separan en cuanto una cambie, y la pantalla habilitaría un botón que el repositorio rechaza. El orden importa: el cuadro de precio nuevo se abre en el primer modo que devuelva esta lista. **No confundir con `precioBasePorTrozo` / `precioBaseDelProducto`**, que devuelven el precio si existe; esta contesta lo contrario y sobre la lista cruda.

### faltanLasBases (privada) ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Precios.kt
- Qué hace: el texto de "primero pon el precio de un trozo y del producto entero", nombrando solo el que falta.
- Cómo funciona: recibe `List<ModoPrecio>` y devuelve `String`. Es **privada a propósito**: el único que decide cuándo mostrarlo es `errorEnCantidadDePrecio`, porque el aviso pertenece al campo de la cantidad — que es lo que hay que bajar a 1 para poder seguir. Ponerla pública invitaría a repetir el aviso en otro campo, donde no diría qué hacer.

### MAXIMA_CANTIDAD_DE_PRECIO ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Precios.kt
- Qué hace: cuántos trozos o productos como máximo puede cubrir una promoción.
- Cómo funciona: constante `200`. No es una regla del negocio: "200 trozos por $1.500" no es una promoción, es un 2 escrito tres veces. El tope real en modo trozo lo pone la receta; este existe para el modo producto, que no tiene ninguno.

### errorEnDiasPorSemanaTexto, errorEnUnidadesPorDiaTexto, revisarSimulacion y ErroresSimulacion ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Simulacion.kt
- Qué hacen: revisan los dos campos de "Ganancias simuladas" (6.2 y 8.7).
- Cómo funcionan: reciben lo escrito y devuelven el motivo o `null`; `revisarSimulacion` los junta por campo. Era el hueco de la Fase 8: `simulacion()` estaba escrita y probada desde hace fases pero **nada revisaba lo que se escribe antes de llamarla**. Lo particular de este paso es que **nada explota** con un número absurdo —los dos campos se multiplican contra el ingreso y ya—, y eso es justamente el problema: un 200 escrito en vez de un 20 sale como una proyección mensual creíble y diez veces falsa. Los dos campos tienen reglas **distintas a propósito**: los días van de 1 a 7 y **rechazan el 0** —dejarlos en cero deja la simulación entera en cero y parece un error de la app, y el aviso enseña la salida correcta—, mientras que las unidades **sí aceptan el 0**, que es como se dice "esta receta todavía no la vendo" sin borrar el resto de lo configurado. Para saber si el número es entero usan `esNumeroEntero`, no `toInt()`.

### DIAS_MAXIMOS_POR_SEMANA y MAXIMAS_UNIDADES_POR_DIA ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Simulacion.kt
- Qué hacen: los topes de los dos campos de la simulación.
- Cómo funcionan: constantes `7` y `500`. La primera no es configurable — es un calendario. La segunda es la red contra el dedo pegado, con la diferencia de que acá **nada explota** al pasarse: por eso hace falta el tope.

### errorEnCantidadDeDuracion, elBloqueDiceAlgo y ORDEN_DE_LOS_BLOQUES ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Duracion.kt
- Qué hacen: las reglas del paso de duración, que son más blandas que las del resto a propósito.
- Cómo funcionan: `errorEnCantidadDeDuracion(texto, apto)` acepta el **vacío** —"no lo sé" es una respuesta legítima y la más común— pero rechaza el 0 (para eso está el switch de "no apto") y los decimales (las unidades ya bajan de escala: medio día son 12 horas). Con `apto = false` no revisa nada: ahí la cantidad se ignora por completo. `elBloqueDiceAlgo` es la que decide si hay algo que guardar, y **un bloque "no apto" sí lo tiene** aunque no tenga números — ese es el caso que se olvida al escribirlo como "tiene cantidad". `ORDEN_DE_LOS_BLOQUES` los pone de la forma más común a la menos.

### AVISO_PESO_REESCALADO ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/rendimiento/Rendimiento.kt
- Qué hace: el texto que acompaña a un peso final que salió de un reescalado por molde (8.4.1, #4).
- Cómo funciona: constante con el texto `"El peso de este producto ha sido reescalado automáticamente. Por favor, compruebe el peso."`. **Pide comprobar y no da por bueno**, y esa palabra es todo el punto: la proporción es una estimación y no una medición — el peso real depende de cuánta masa quede pegada al molde y de cuánta agua se evapore, y ninguna de las dos escala con el área. Es constante y no un texto suelto en la pantalla por lo mismo que `MENSAJE_ALTURA_RIESGOSA`.

### repartirEntreTrozos ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/rendimiento/Rendimiento.kt
- Qué hace: divide un total entre los trozos de la receta. **Es la única división por trozos de la app.**
- Cómo funciona: recibe `total: Double` y `trozos: Int`, devuelve `Double`; lanza excepción con 0 o menos en vez de devolver infinito. Existe porque la misma cuenta hacía falta en dos lugares que no se hablan — el peso de cada trozo (que se muestra en Rendimiento) y el costo de cada trozo (del que salen la ganancia y el trozo ganador) — y escrita dos veces son dos verdades sobre el mismo número, con la segunda olvidándose de comprobar el 0. `pesoPorTrozo` y `precios.costoPorTrozo` **delegan acá**; ninguna vuelve a dividir por su cuenta.

### pesoPorTrozo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/rendimiento/Rendimiento.kt
- Qué hace: calcula cuánto pesa cada trozo dividiendo el peso final del producto entre la cantidad de trozos.
- Cómo funciona: recibe `pesoFinalG: Double?` y `trozos: Int`, devuelve `String` ya formateado. Si `pesoFinalG` es `null` devuelve la constante `PESO_NO_ESPECIFICADO` en vez de un número — por eso retorna `String` y no `Double`. Valida `trozos >= 1` y lanza excepción si no, en vez de dividir por cero.

### SIN_MOLDE ✅ IMPLEMENTADA (sin usar todavía)
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/rendimiento/Rendimiento.kt
- Qué hace: el texto fijo del campo molde cuando la receta no usa ninguno (una salsa, por ejemplo).
- Cómo funciona: constante con el texto `"No utiliza molde"`, especificado en la tabla de la sección 8.3. **Todavía no la usa nadie**: su consumidor es el paso "Rendimiento" de la Fase 5. Queda registrada justamente para que ahí se reutilice en vez de escribir el texto suelto en el Composable.

### FormaDelCorte, corteSugerido, medidaDelTrozo y nombreDelCorte ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/moldes/Corte.kt
- Qué hacen: de qué tamaño queda cada trozo según cómo se parta el molde (9.4).
- Cómo funcionan: `FormaDelCorte` es `CUNAS`, `CUADRICULA` o `NO_SE_CORTA`. **El corte no es la forma**, y esa separación es la razón de que exista: la forma decide el área y el volumen —o sea el reescalado— y el corte solo dice el tamaño del trozo. Nació de la pregunta "¿a qué figura se parece este molde?", que apunta a lo correcto con la palabra equivocada: un molde de rosca **no se parece** a un círculo —le falta el centro y su volumen es otro, medido con agua— pero **se corta** como uno. Con "parecido" quedaba abierta la puerta a recalcular ese volumen y llevarse por delante las cantidades. `corteSugerido` devuelve `null` justo en el triángulo y el exótico, que son los dos que hay que preguntar; las otras tres formas quedan resueltas sin que nadie las edite. `medidaDelTrozo` devuelve **grados en cuñas y centímetros en cuadrícula** —un trozo de torta redonda es una porción y sus lados no miden lo mismo cerca del centro que en el borde—, corta el lado largo conservando el corto y la altura, y **devuelve `null` cuando no se puede afirmar nada**: sin medidas de corte anotadas, o con un molde que no se corta. Mejor no decir nada que decir un número inventado.

### errorEnMedidasDeCorte ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Moldes.kt
- Qué hace: revisa las dos medidas **del corte**, que son opcionales (9.4).
- Cómo funciona: recibe los dos textos y devuelve el motivo o `null`. Son otra cosa que las medidas del molde y por eso se revisan aparte: aquellas deciden el área y el volumen y son obligatorias; estas solo dicen el tamaño del trozo, y no anotarlas es una respuesta válida — la app simplemente no muestra el tamaño. Lo único que se exige es que **estén las dos o ninguna**: con un solo lado no se mide nada y guardarlo dejaría un dato a medias. `revisarMolde` las recibe con valor por defecto, así que todo lo que ya la llamaba sigue igual.

### MIGRACION_3_4 ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/AppDatabase.kt
- Qué hace: agrega las tres columnas del corte al pasar de la versión 3 a la 4 (9.4).
- Cómo funciona: `Migration(3, 4)` con seis `ALTER TABLE` — tres en `moldes` y tres en `receta_rendimiento` con el prefijo `molde_`, porque `DimensionesMolde` se embebe en las dos y olvidar una deja la app sin arrancar. **Sin `DEFAULT`, al revés que las dos anteriores**, y no es descuido: estas columnas son nulables y ahí `null` significa algo — "el corte que corresponda a la forma", que para rectángulo, cuadrado y círculo ya es la respuesta correcta sin editarlos. Las anteriores agregaban columnas `NOT NULL`, que en SQLite sí exigen valor por defecto. La prueba de migración comprueba sobre todo **que las medidas del molde queden idénticas**: si el corte llegara a rozar el área o el volumen, cambiaría el reescalado de todas las recetas enlazadas. **`app/schemas/4.json` lo genera Room al compilar** y hay que versionarlo.

### medidasEnTexto ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/moldes/Moldes.kt
- Qué hace: las medidas de un molde **tal como se tomaron**, en una línea: "30 × 20 cm, 6 de alto".
- Cómo funciona: recibe las `DimensionesMolde` y la función que da formato a los números (para no depender de `formato/` desde acá); devuelve `String?`. Existe porque las pantallas solo mostraban **área y volumen**, que son calculados: sirven para comparar dos moldes pero no responden la pregunta con la que uno se para frente al mueble — *¿cuál era el de 20 por 30?*. **Devuelve `null` si falta una medida en vez de lanzar**, al revés que `areaCm2`: esto es para mostrar y un molde a medio guardar no puede voltear una pantalla. El cuadrado escribe sus dos lados aunque sean el mismo, porque "20 cm" a secas no dice si es el lado o el diámetro; el triángulo distingue su altura de la del molde, que es lo que se confunde; y el exótico dice su volumen, porque no tiene largo ni ancho que mostrar. La altura va al final separada por coma y no multiplicando: "20 × 30 × 6" invita a pensar que los tres son lo mismo, cuando la altura es la que decide si la masa cabe.

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

### precioBasePorTrozo, precioBaseDelProducto, esPrecioBase, RepartoDeVenta, repartir, repartoDeUnProducto y AVISO_TROZO_SUELTO ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hacen: el reparto real de una venta entre una promoción y lo que sobra (8.6.1).
- Cómo funcionan: los dos precios **base** son las filas de `cantidad = 1`, una por modo; no son tabla ni columna nueva porque "el precio de un trozo" ya es eso. Existen como concepto porque **sostienen a las promociones**: una promo de 2 en una receta de 3 deja un trozo suelto, y ese suelto tiene que venderse a algo. `repartir(aVender, promocion, precioIndividual)` mete tantas promociones como quepan y cobra el resto al individual; `repartoDeUnProducto` lo aplica a **un** producto —los trozos de la receta si la promo es por trozo, un producto si es por producto—. Salió de un error visto en el celular: con 3 trozos y "2 por $3.000" la app mostraba $4.500, que es la promo dividida por trozo y multiplicada por tres, cuando lo real son $3.000 + el trozo suelto. `faltaElPrecioSuelto` marca el único caso incalculable —sobró algo y no hay base—, y ahí el total cuenta solo las promociones para que la pantalla avise en vez de mostrar un número corto sin decirlo. **`repartir` está separado a propósito**: el total que se reparte cambia según quién pregunte (la simulación reparte lo que se vende en un día, no multiplica lo de un producto), pero la regla es la misma. Lleva la misma guarda que tenía `precioPorTrozoDe` contra `cantidad = 0`, que al escribirlo se había perdido y la recuperó una prueba que ya existía.

### precioEfectivoPorTrozo ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/precios/Precios.kt
- Qué hace: el precio por trozo que alimenta todos los campos automáticos de la app.
- Cómo funciona: recibe el snapshot, devuelve `Double`. **Sale del reparto real** (`repartoDeUnProducto`) repartido entre los trozos, y no del precio de la promoción dividido — que era lo que mentía: con 3 trozos y una promo de 2, dividir da 1.500 por trozo como si los tres se vendieran así. Con resto es una mezcla de los dos precios, y todo lo que cuelga de esto (trozo ganador, ganancia, sueldos, simulación) queda cuadrado sin tocarlo. Hereda la excepción de `precioDeReferencia` cuando no hay precios.

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

### FirmaDeReceta, SeccionDeFirma, LineaDeFirma, TituloDeFirma y compararFirmas ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/partes/Firma.kt
- Qué hacen: la foto de una receta al copiarla dentro de otra, y las frases de "¿Qué cambió?" (8.11.5).
- Cómo funcionan: **todo se identifica por id y no por nombre** — la sección por su `seccionId` y cada ingrediente por el id de su fila `RecetaIngrediente` —, y el nombre viaja solo para armar la frase. Esa decisión se tomó tras encontrar dos agujeros en la primera versión, que usaba un mapa `nombre → gramos`: renombrar un ingrediente del catálogo (algo que no toca ninguna receta) levantaba una falsa alarma en cada copia **y hacía que la adaptación pisara la cantidad ajustada a mano**; y como el mismo ingrediente puede estar dos veces en una sección —no hay índice único ni comprobación que lo impida—, las dos filas se aplastaban en una y una cantidad desaparecía en silencio. Los nombres de las frases se leen del estado **actual**, así un renombre se muestra con el nombre nuevo sin generar aviso. `FirmaDeReceta` guarda además cuántos pasos hay bajo cada título y cuántos generales; los contadores (`cuantasSecciones`, `cuantosIngredientes`, `cuantosTitulos`) y `linea(lineaId)` se derivan y no se anotan aparte. **Lleva las cantidades y no solo cuántos ingredientes hay**, y eso es lo que la convierte en algo más que contadores: de ahí sale el factor con que se adapta cada cantidad (8.11.3). `compararFirmas` devuelve **frases y no una estructura**, porque es lo único que se hace con esto —mostrarlo— y una estructura obligaría a cada pantalla a decidir cómo se lee cada caso, que es donde aparecen los "1 ingredientes". Los gramajes se comparan con tolerancia y no con `==`: las cantidades pasan por redondeos a 2 decimales al reescalarse, y avisar de esa diferencia enseñaría a ignorar el aviso. Una sección que se fue entera se nombra **una vez** y no ingrediente por ingrediente. **No detecta** reescribir un paso ni renombrar una sección: es el precio de no hacer un diff.

### textoDeFirma y firmaDesdeTexto ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/partes/FirmaEnTexto.kt
- Qué hacen: guardar una `FirmaDeReceta` en la columna `firmaDelOrigen` y volver a leerla (5.5.1).
- Cómo funcionan: formato de líneas con una `v1` adelante, legible abriendo la base. **Escapan `\`, `|`, `=` y el salto de línea**, y no es prolijidad: los nombres de sección e ingrediente los escribe una persona y nada le impide poner "Crema 50|50" o "Azúcar = flor"; sin escapar, un nombre así partiría la línea y la firma se leería mal para siempre, en silencio. `firmaDesdeTexto` **devuelve `null` en vez de lanzar** ante cualquier cosa que no entienda —otra versión del formato, una fila a medio escribir—: una firma ilegible no puede impedir abrir la receta, lo que se pierde es el aviso. Se parte por el **último** `=` de cada campo, porque el nombre va escapado y el separador real es el de más a la derecha.

### cantidadAdaptada ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/partes/Adaptacion.kt
- Qué hace: cuánto pasa a llevar un ingrediente de la copia cuando la receta original cambió (8.11.3).
- Cómo funciona: recibe la cantidad de acá y las dos de la original (antes y ahora), devuelve `Double` ya redondeado a 2 decimales. **Actualizar no pisa la cantidad: la adapta en proporción**, aplicando el factor **de ese ingrediente** y no uno global —en la original puede haber cambiado uno solo—. Así conviven las dos razones por las que una cantidad cambia: la de acá ("uso la mitad", que es una decisión y no se toca) y la de allá ("bajé la harina de 550 a 500", que sí debe llegar). Si en la original era 0 no hay proporción que conservar y devuelve la cantidad nueva tal cual, en vez de dividir por cero.

### nombreSinChocar ✅ IMPLEMENTADA
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/partes/Adaptacion.kt
- Qué hace: el nombre con que entra una sección traída, esquivando los que ya existen (8.11.2).
- Cómo funciona: "Crema" → "Crema 2" → "Crema 3". Los nombres de sección no se pueden repetir dentro de una receta (8.2) y esa regla no se toca: **renombrar es mejor que rechazar la copia entera** por una coincidencia. **El resultado nunca pasa de `LARGO_MAXIMO_NOMBRE`**: la misma validación que exige nombres únicos exige que quepan en 60, y a un nombre ya al límite pegarle " 2" lo dejaba en 62 — la copia se rechazaba por el nombre que esta función acababa de proponer. Se recorta la base y no el sufijo, porque el número es lo que lo hace único. Compara con `sonElMismoTexto` —ignorando mayúsculas y tildes— y no con `==`, porque es la misma comparación que hace la validación que rechazaría el nombre: con `==` propondría "Limón" existiendo "limon" y la copia fallaría igual.

### sePuedeUsarComoParte y MOTIVO_UN_SOLO_NIVEL ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/partes/Adaptacion.kt
- Qué hacen: el tope de un solo nivel de anidamiento (8.11.6), y su explicación.
- Cómo funcionan: `sePuedeUsarComoParte(recetaId, laQueSeEstaArmando, tieneSeccionesTraidas)` devuelve `Boolean`. **Recibe el dato y no consulta nada**: que una receta "use otra" es exactamente que alguna de sus secciones tenga `recetaOrigenId`, y eso ya se puede saber sin una columna aparte (5.5.1). Excluye además la receta de sí misma. El tope es deliberado: sin él, actualizar el bizcocho tendría que propagarse en cadena por todo lo que lo usa indirectamente.

### TituloDePaso, TITULO_GENERAL, SeccionParaTitulo, elTituloSePuedeRepetir, errorAlUsarTitulo, titulosDisponibles y seJuntanLosBloques ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/partes/Titulos.kt
- Qué hacen: las reglas de bajo qué título va cada paso (8.8).
- Cómo funcionan: `TituloDePaso` es un `Long?` — el id de la sección, y **`null` es el General**. Que el General sea la ausencia de sección y no un nombre reservado importa: bautizar una sección "General" se puede (8.2), y con un nombre los dos serían indistinguibles en los datos. `TITULO_GENERAL` **se toma de `NOMBRE_SECCION_POR_DEFECTO`** y no se escribe otra vez: son la misma palabra a propósito y escritas por separado una se quedaría atrás al cambiar la otra. **Solo el General se repite**, porque un paso que no pertenece a ninguna parte aparece naturalmente entre medio de las partes; una sección usada dos veces se rechaza, ya que dos bloques "Crema" no dicen en cuál va cada cosa. `titulosDisponibles` recibe `SeccionParaTitulo(id, nombre)` —el nombre hace falta, no basta el id— y **no ofrece la sección automática mientras siga invisible**, reutilizando `debenMostrarseLosNombresDeSeccion`: sin eso, el menú de cualquier receta recién creada mostraba dos "General", porque toda receta nace con una sección llamada así. `usadosPorOtrosBloques` son los títulos de **los demás** bloques y no de todos, para que al editar uno que ya existe no se rechace a sí mismo. `titulosDisponibles` y `errorAlUsarTitulo` viven juntas y se prueban juntas a propósito: **lo que el menú ofrece y lo que se acepta al confirmar no pueden discrepar**, y hay una prueba que recorre todo lo ofrecido comprobando que nada se rechazaría después; la visibilidad la decide solo `titulosDisponibles`, que es la única que ve los nombres. `errorAlUsarTitulo` recibe el nombre en vez de deducirlo, porque acá no hay forma de saber cómo se llama la sección `12` y un aviso que dijera eso no le sirve a nadie. `seJuntanLosBloques` dice cuándo dos bloques seguidos son el mismo partido en dos —solo dos generales pegados—, sin juntarlos: eso es de quien dibuja.

### AtajoDePaso, atajoAntesDelCursor y reemplazarAtajo ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/partes/Atajos.kt
- Qué hacen: los atajos `:titulo:` y `:ingredientes:` que se escriben dentro de un paso (8.8).
- Cómo funcionan: van con **dos puntos adelante y atrás** para que el atajo no se dispare al escribir la palabra en medio de una frase — "Ahora el titulo se decora" no abre ninguna lista. `atajoAntesDelCursor(texto, cursor)` mira **solo hasta el cursor**, porque el atajo se dispara donde está la mano y no porque la palabra aparezca en otro renglón ya resuelto; compara en minúsculas, ya que el teclado del celular pone mayúscula al empezar una oración. `reemplazarAtajo` cambia **la aparición del cursor** y no la primera que encuentre —`indexOf` cambiaría la equivocada en un paso que ya usó el atajo antes— y devuelve `TextoDePaso` con el cursor nuevo, por lo mismo que `formatearMientrasSeEscribe`: el largo cambia y conservar la posición como número la deja donde no va.

### loQueSeVendeEnLaSemana, repartoSemanal y simulacionDeVenta ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/simulacion/Simulacion.kt
- Qué hacen: la simulación de una receta concreta, con su reparto real (8.7).
- Cómo funcionan: **es la que hay que usar desde la pantalla**, no `simulacion`. Acá se paga la deuda que quedó escrita en 8.6.1: multiplicar `ingresoBruto(d)` por los días y las unidades arrastra el resto de cada producto, y una receta de 3 trozos con promo de 2 deja siempre uno suelto mirando producto por producto — pero vendiendo dos son 6 trozos y la promo entra tres veces justas. Multiplicando daría 10.000 donde entran 9.000. `repartoSemanal` aplica `repartir` sobre el total de la semana, contado como lo cuenta la promoción (trozos si es por trozo, productos si es por producto). **El costo sí se multiplica**: producir dos tortas cuesta el doble, sin promociones que valgan, y esa asimetría es real. `simulacion` se queda como la aritmética pura, que es lo que necesita la simulación de varias recetas de un empleado (10.2), donde el ingreso de cada una ya viene calculado.

### SimulacionViewModel, EstadoSimulacion, PasoSimulacionScreen y AccionesSimulacion ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/ (ViewModel y pantalla)
- Qué hacen: el paso 6, "Ganancias simuladas" (8.7).
- Cómo funcionan: guarda solo con la misma espera de medio segundo que el rendimiento, y **no anuncia el éxito** — es el paso donde más se teclea, porque la gracia es probar combinaciones, y un "se guardó" por número sería ruido. Observa el snapshot (`observarDatosCalculo`) porque de las cinco cosas que lleva, tres las escriben otros pasos: sin eso, cambiar un precio dejaría esta pantalla proyectando plata que ya no es. `resultado` y `reparto` dan `null` con los campos a medio escribir, porque mostrar una cifra salida de un número inválido parecería un resultado. `avisoDelResto` dice cómo se reparte la semana, y `deQueSeCompone` y `porQueNoEsMultiplicar` son lo que faltaba para que la cifra **se pudiera comprobar** (8.7.1): la primera va siempre ("En la semana vendes 30 trozos: «2 trozos» entra 15 veces") y la segunda solo cuando la semana difiere de multiplicar un producto, que es cuando el número parece un error. Salieron de un reporte de Sandy en que la app tenía razón y no había forma de verificarlo. **Los dos campos van arriba y las cifras debajo**, al revés que gastos: acá lo que se hace es mover los números y mirar qué pasa. La ayuda del campo de unidades dice "si todavía no la vendes, deja 0", que es el camino correcto — sin decirlo la gente pone 0 días y deja todo en cero pareciendo un error de la app.

### ingresoSiSeMultiplicaraElProducto y promocionesQueSeGananAlJuntar ✅ IMPLEMENTADAS
- Ubicación: logica/src/main/kotlin/com/sandyyera/reposteria/logica/simulacion/Simulacion.kt
- Qué hacen: comparan lo que la app calcula contra la cuenta que uno hace de cabeza, para poder explicar la diferencia (8.7.1).
- Cómo funcionan: las dos reciben el snapshot, los días y las unidades. `ingresoSiSeMultiplicaraElProducto` devuelve `ingresoBruto(d) × dias × unidades` — **no alimenta ninguna cifra**, existe solo para contrastar; `promocionesQueSeGananAlJuntar` devuelve `Int`, la diferencia entre las promociones que entran en la semana y las que entrarían mirando producto por producto, nunca negativa y 0 sin productos. Nacieron de un reporte de Sandy: la simulación decía $300.000 donde ella esperaba 6 × $46.000 = $276.000, y **la app tenía razón** — los seis trozos sueltos (uno por producto, receta de 5 trozos con promo de 2) se juntan y arman tres promociones más. El problema no era el número sino que no había con qué comprobarlo. La diferencia se dice **en promociones y no en pesos** porque así se verifica contando; "entran $24.000 más" hay que creerlo. La regla que dejan: *una cifra que sale de una regla de negocio tiene que mostrar de qué está hecha; no basta con que sea correcta.*

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

### MoldeRepositorio.obtener ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/MoldeRepositorio.kt
- Qué hace: trae un molde del catálogo por su id.
- Cómo funciona: `suspend`, recibe `moldeId` y devuelve `Molde?`. La usan `actualizar` (necesita la fila completa para conservar `creadoEn`) y `confirmarEliminacion`, que lee el nombre **antes** de borrar porque después el historial no tendría cómo nombrarlo.

### MoldeRepositorio.crear ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/MoldeRepositorio.kt
- Qué hace: crea un molde, revisando antes que sirva y que no esté repetido.
- Cómo funciona: `suspend`, recibe el nombre, la forma y el mapa de medidas **escritas**, y devuelve `ResultadoGuardarMolde`. Valida con `revisarMolde` y convierte con `dimensionesDesde`, las dos de `logica/`. Las comprobaciones van acá y no en la pantalla porque en la Fase 5 habrá una segunda forma de definir un molde —el "modo prueba" del reescalado (9.3)— y las dos tienen que comportarse igual.

### MoldeRepositorio.buscarParecido ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/MoldeRepositorio.kt
- Qué hace: busca un molde que se llame igual, para no crear repetidos.
- Cómo funciona: `suspend`, recibe el nombre y opcionalmente un `exceptoId` (al editar, para que un molde no choque consigo mismo). Compara en memoria con `sonElMismoTexto`, porque SQLite no sabe ignorar tildes. **La tabla no tiene índice único**, a diferencia de ingredientes: la regla vive acá por coherencia con recetas y secciones, y para no tener que migrar el día que aparezca un repetido de otro origen.

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

### RecetaRepositorio.agregarIngrediente, obtenerIngredientesDeSeccion y RecetaDao.nombreDeIngrediente ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt (y sus `@Query` en RecetaDao.kt)
- Qué hacen: poner un ingrediente en una sección **sin dejar repetirlo**, y las dos consultas que eso necesita.
- Cómo funcionan: `agregarIngrediente` devuelve `Resultado` y ya no el id — **rechaza el ingrediente que ya esté en esa misma sección**, nombrándolo y diciendo cuántos gramos lleva, para que el aviso lleve a la fila que hay que editar. Antes no comprobaba nada y se vio en el celular: la misma sección aceptaba "Harina" dos veces. Dos filas del mismo ingrediente no son un dato sino una cantidad partida en dos, que **se suma bien y se lee mal**, así que el costo cuadra mientras la lista miente; el agujero ya estaba anotado en la entrada de `Firma.kt`, que además aplastaba las dos filas en una al copiar la receta. **En dos secciones distintas sí se puede** —almendra en el bizcocho y almendra en la decoración—, por eso `obtenerIngredientesDeSeccion` pregunta por sección y nunca por receta. `nombreDeIngrediente` vive en `RecetaDao` y no se pide prestado el `IngredienteRepositorio`: ese DAO ya conoce la tabla `ingredientes` —el cálculo del costo la cruza— y sumar un repositorio entero por un nombre abriría un camino de ida y vuelta entre los dos. **No hay índice único que lo respalde**, por lo mismo que las secciones repetidas: ya existen filas repetidas guardadas de antes, y un índice obligaría a decidir cuál cantidad se conserva dentro de una migración, que es el peor lugar para eso.

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

### MIGRACION_2_3 ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/AppDatabase.kt
- Qué hace: agrega la columna `pesoReescaladoSinRevisar` a `receta_rendimiento` al pasar de la versión 2 a la 3.
- Cómo funciona: `Migration(2, 3)` con un `ALTER TABLE ... ADD COLUMN pesoReescaladoSinRevisar INTEGER NOT NULL DEFAULT 0`, con el mismo cuidado que la 1 → 2: el `DEFAULT 0` es obligatorio en SQLite y tiene que calzar con el `@ColumnInfo(defaultValue = "0")` de la entidad. **Tenía que ser una columna y no un dato de la pantalla**: quien reescala hoy pesa el producto mañana, cuando salga del horno, y para entonces la app ya se cerró. Las filas que ya existían quedan en `0`, que es lo correcto — sus pesos los escribió alguien a mano, no salieron de ninguna multiplicación. **`app/schemas/3.json` lo genera Room al compilar** y hay que versionarlo.

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

### MoldeRepositorio.actualizar ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/MoldeRepositorio.kt
- Qué hace: corrige un molde del catálogo **y propaga la corrección a las recetas enlazadas**.
- Cómo funciona: `suspend`, recibe `moldeId`, nombre, forma y medidas escritas; devuelve `ResultadoGuardarMolde`. Guarda el molde y después llama a `RecetaRepositorio.actualizarDimensionesMolde` en cada receta que siga apuntando a él. **No reescala ingredientes**: corregir una medida mal tomada no es cambiar de molde, y reescalar vive en la Fase 5. Las recetas ya desvinculadas no reciben nada y conservan sus medidas (5.2). Registra un evento verde listando a quién afectó. *Se llamaba `actualizarMolde` en el diseño y la parte "cruda" iba aparte (`actualizarDimensiones`); quedó una sola función porque nadie llamaría a la cruda: propagar no es opcional, y dejar la puerta abierta a guardar sin propagar es exactamente cómo se desincronizan las recetas.*

### MoldeRepositorio.recetasAfectadasPorBorrar y confirmarEliminacion ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/MoldeRepositorio.kt
- Qué hacen: la consulta previa a la advertencia de borrado y el borrado en sí (6.3).
- Cómo funcionan: `suspend`. La primera devuelve `List<Receta>` para que la pantalla pueda enumerarlas. La segunda lee nombre y recetas **antes** de borrar —después la clave foránea ya puso sus `moldeOrigenId` en `null` y no habría cómo nombrarlas— y no vuelve a preguntar: la confirmación es de la pantalla. **A diferencia de un ingrediente, borrar un molde no rompe esas recetas**: conservan sus medidas y solo pierden el vínculo, así que dejan de recibir correcciones. Se avisa igual, porque descubrirlo meses después no tendría explicación.

### ResultadoGuardarMolde ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/MoldeRepositorio.kt
- Qué hace: dice cómo terminó un intento de guardar un molde.
- Cómo funciona: tipo cerrado con `Guardado(id)`, `YaExiste(existente)` y `NoValido(errores)`. Misma idea que `ResultadoGuardarIngrediente`, con una diferencia: `NoValido` lleva un `ErroresMolde` **por campo** y no un texto suelto, porque un molde tiene hasta cuatro campos que pueden fallar a la vez y cada aviso va bajo el suyo.

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
- Cómo funciona: Composable que recibe el valor, qué hacer al cambiar, la etiqueta y opcionalmente un error, un texto de ayuda, un `alEnfocar` y un `alSalirDelCampo`. Los dos avisos de foco se distinguen recordando el estado anterior, porque `onFocusChanged` avisa de cada cambio y no de las transiciones. `alSalirDelCampo` lo pidió el guardado automático de las duraciones (8.4.1): ahí lo que dispara la escritura es abandonar el campo, no teclear. `alEnfocar` avisa cuando el campo **recibe el foco** —o sea cuando alguien lo toca para mirarlo, aunque no escriba nada—; lo pidió el aviso de "peso reescalado, compruébalo" (8.4.1, #4), que tiene que irse al mirar el campo y no al editarlo, porque el número puede estar bien y exigir una edición sería obligar a borrar y reescribir lo mismo. Guarda un `TextFieldValue` y no un `String` porque el `String` no lleva la posición del cursor: el campo la conserva como un número, y ese número deja de significar lo mismo cuando el texto se alarga con el punto de mil. Dónde va el cursor lo decide `formatearMientrasSeEscribe(texto, cursor)`, en `logica/`. **Es la única puerta de entrada de números de la app**: cualquier campo numérico nuevo va por acá y no con un `OutlinedTextField` suelto, porque si no hay que volver a resolver lo del cursor en cada pantalla.

### ComboBuscable ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/componentes/ComboBuscable.kt
- Qué hace: un buscador que además deja elegir de la lista y crear ahí mismo lo que no aparece.
- Cómo funciona: Composable **genérico** — recibe las opciones, cómo sacar el texto de cada una, el texto buscado y qué hacer al elegir; opcionalmente un `alCrear` que habilita el alta rápida y un `motivoNoDisponible` que, devolviendo un texto, **deja la opción a la vista pero sin poder tocarse, con el motivo al lado** — desaparecer de la lista haría pensar que la app la perdió, y un gris sin explicación invita a tocarlo y a preguntarse qué pasa. Lo pidió el ingrediente que ya está en la sección. Filtra con `filtrarPor`. La fila de "Crear «x»" aparece solo si hay algo escrito que no coincide exactamente con una opción existente. Muestra la lista **debajo** y no en un menú flotante: en un celular un desplegable tapa justo el formulario que se está llenando y pelea con el teclado. Se usa para agregar un ingrediente a una receta (7) y para elegir un molde del catálogo (9.3).

### ListaIngredientesScreen ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/ListaIngredientesScreen.kt
- Qué hace: conecta la pantalla de ingredientes con su ViewModel.
- Cómo funciona: Composable que recibe el `IngredientesViewModel`, lee su estado con `collectAsStateWithLifecycle` (deja de leer la base cuando la pantalla no se ve) y reparte cada acción. **No dibuja nada**: eso lo hace `ListaIngredientes`.

### ListaIngredientes ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/ingredientes/ListaIngredientesScreen.kt
- Qué hace: dibuja la pantalla de ingredientes — botón fijo arriba, buscador, y la lista debajo.
- Cómo funciona: Composable que recibe **solo datos y funciones**, nunca el ViewModel ni el repositorio, así que se puede ver en la vista previa de Android Studio con ingredientes inventados y no puede tocar la base por accidente. El botón de agregar va fuera del área que se desplaza (patrón de 8.1, el mismo de recetas y moldes). Distingue "no hay ingredientes" de "la búsqueda no encontró nada", que son dos mensajes distintos. Cada monto pasa por `formatearNumero`. **La tarjeta de un ingrediente se toca para editarlo** y el único ícono que queda es el de borrar: los dos íconos de antes eran casi iguales y quedaban pegados, así que el que hay que tocar con cuidado —el rojo— estaba a un dedo del inofensivo.

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

### FlujoCompletoTest ✅ IMPLEMENTADA
- Ubicación: app/src/test/java/com/sandyyera/reposteria/data/FlujoCompletoTest.kt
- Qué hace: recorre la app de punta a punta cruzando los tres repositorios, en vez de probar una función.
- Cómo funciona: arma los repositorios **igual que `AppContainer`** y recorre una tarde de uso —ingredientes, receta con secciones, molde, precios y referencia, correcciones, borrados—, comprobando después de cada paso que `costoTotal`, `observarCostos`, `costosDe` y el `DatosCalculoReceta` sigan dando el mismo número. Existe porque los errores que llegaron al celular no fueron de una pieza sola sino de dos que dejaron de estar de acuerdo: el costo viejo de la lista lo causaba borrar un ingrediente, cosa que hace **otro** repositorio, así que ninguna prueba de `RecetaRepositorio` podía verlo. **No cuenta eventos del historial**, solo comprueba que cada cosa borrada quedó nombrada: contarlos se rompería al agregar cualquier anotación nueva sin que nada esté mal.

### DaosFalsos: las consultas reactivas ✅ IMPLEMENTADAS
- Ubicación: app/src/test/java/com/sandyyera/reposteria/data/DaosFalsos.kt
- Qué hacen: `observarReceta`, `observarSecciones`, `observarIngredientesDeReceta` y `observarRendimiento` en el falso.
- Cómo funcionan: cuelgan de las mismas fuentes que en Room — la de la receta del `StateFlow` de recetas, las otras tres del contador `cambios`, que es la imitación del `InvalidationTracker`. **Que sean `Flow` de verdad es lo que permite probar el bug que las motivó**: dos pantallas mirando el mismo rendimiento y quedándose cada una con su foto vieja. Toda escritura sobre `rendimientos` llama ahora a `cambio()`; si se agrega una y se olvida, las pruebas de "poner el molde desde el otro paso" se caen, que es exactamente lo que tiene que pasar.

### IngredienteDaoFalso, HistorialDaoFalso, RecetaDaoFalso y MoldeDaoFalso ✅ IMPLEMENTADAS
- Ubicación: app/src/test/java/com/sandyyera/reposteria/data/DaosFalsos.kt
- Qué hacen: reemplazan a los DAO de Room con datos en memoria, para probar repositorios y ViewModel sin base de datos ni celular (`./gradlew :app:test`).
- Cómo funcionan: los DAO de Room son interfaces, así que se sustituyen sin tocar el código de la app. **`IngredienteDaoFalso` imita el índice único de la tabla** y lanza excepción ante un nombre repetido, igual que la base de verdad: sin eso, la prueba de "no se puede crear un duplicado" pasaría aunque la comprobación no existiera. `HistorialDaoFalso` expone `eventos` y `limpiezasPedidas` para revisarlos. `RecetaDaoFalso` creció en la Fase 3 hasta ser una base de recetas en memoria de verdad: hace las **cascadas** (borrar una receta se lleva sus secciones; borrar una sección, sus ingredientes), resuelve el **JOIN del costo** contra el catálogo de ingredientes leyendo el `valorPorGramo` del momento (decisión #3) —por eso recibe el mismo `IngredienteDaoFalso` que use la prueba—, y **omite del resultado en lote las recetas sin ingredientes**, igual que el `GROUP BY` real. Esa última trampa solo se puede probar si el falso la reproduce. Lo que aún no hace falta falla ruidosamente: un `emptyList()` de relleno haría pasar pruebas que no probaron nada. **`MoldeDaoFalso` imita la regla `SET_NULL`** de la clave foránea: al borrar un molde llama a `RecetaDaoFalso.desvincularMolde`, que deja los `moldeOrigenId` en `null` **sin tocar las medidas**. Sin eso, una prueba podría afirmar que las recetas quedan desvinculadas sin que nada lo hiciera.

### PasoDeRecetaTest ✅ IMPLEMENTADA
- Ubicación: app/src/test/java/com/sandyyera/reposteria/ui/recetas/PasoDeRecetaTest.kt
- Qué hace: comprueba que los pasos que dibuja `FilaDePasos` tengan título, que ninguno se repita y que el primero sea Cantidades.
- Cómo funciona: JUnit puro, sin corrutinas ni base. La fila en sí es un Composable y no se puede probar sin celular, pero **todo lo que la fila muestra sale del enum**: un paso sin título deja una ficha en blanco y dos títulos iguales dejan la fila imposible de usar. Es lo que se olvida al agregar el cuarto paso (Gastos, Fase 7).

### MoldeDeRecetaViewModelTest y RendimientoViewModelTest ✅ IMPLEMENTADAS
- Ubicación: app/src/test/java/com/sandyyera/reposteria/ui/recetas/
- Qué hacen: prueban los dos pasos que antes eran uno solo.
- Cómo funcionan: `MoldeDeRecetaViewModelTest` se quedó con todo lo del molde —que el cuadro **sepa solo** si es la primera vez o un reescalado, que un rechazo quede dentro del cuadro y no en la franja de abajo, y que reescalar arrastre el peso del producto además de los ingredientes—; `RendimientoViewModelTest` con los dos campos y el aviso del peso. Este último arma el ViewModel con un método aparte (`abrirElPaso`) para poder **crearlo dos veces sobre la misma base**, que es lo más parecido a cerrar la app y volver a entrar: es la única forma de comprobar que el aviso sobrevive, que es toda la razón de que sea una columna.

### MigracionTest ✅ IMPLEMENTADA
- Ubicación: app/src/androidTest/java/com/sandyyera/reposteria/data/db/MigracionTest.kt
- Qué hace: comprueba que actualizar la app no se lleve por delante lo que ya estaba guardado.
- Cómo funciona: **la única prueba que necesita celular o emulador** (`./gradlew :app:connectedAndroidTest`), y no hay forma honesta de evitarlo: lo que se verifica es SQLite de verdad corriendo el `ALTER TABLE` de verdad, y la base de mentira en memoria de las otras 90 pruebas no tiene esquemas ni migraciones, así que aprobaría cualquier cosa. Escribe filas con el esquema de la versión 1, corre `runMigrationsAndValidate` y revisa **las dos mitades**: que el esquema resultante calce con `2.json` —si el `DEFAULT 0` de la migración no calzara con el `@ColumnInfo(defaultValue = "0")` de la entidad, la app no arrancaría en el celular— y que las filas sigan ahí, porque validar el esquema por sí solo dejaría pasar un `DROP TABLE` seguido de un `CREATE TABLE`. La segunda prueba abre la base ya migrada con Room entero, que es lo único que ejercita el `identityHash`. `MigrationTestHelper` lee `app/schemas/` como assets, declarados en `build.gradle.kts`: **cada versión nueva necesita su JSON versionado y su prueba acá.**

### MoldesViewModel ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/moldes/MoldesViewModel.kt
- Qué hace: guarda lo que se ve en el catálogo de moldes y ejecuta lo que se pide desde él.
- Cómo funciona: mismo patrón que ingredientes y recetas — `combine` de tres fuentes, `WhileSubscribed(5s)`, y el diálogo por su propio canal (12.2.1), que acá pesa más porque el formulario tiene hasta cinco campos de texto. Sus acciones: `buscar`, `abrirAlta`, `abrirEdicion`, `cambiarNombre`, `elegirForma`, `cambiarMedida`, `guardar`, `pedirBorrado`, `confirmarBorrado`, `cerrarDialogo` y `mensajeMostrado`. **`elegirForma` no borra lo escrito para las otras formas**: quien probó "círculo", anotó el diámetro y pasa a "cuadrado" para comparar, al volver lo encuentra donde lo dejó — y lo que no se pide para la forma actual no se valida ni se guarda, así que conservarlo no cuesta nada. `abrirEdicion` devuelve las medidas a texto con `formatearNumero`, el mismo formato que `textoANumero` lee de vuelta, para que abrir y guardar sin cambiar nada no altere ningún número.

### DialogoMolde, EstadoMoldes y AccionesMoldes ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/moldes/ (ViewModel y pantalla)
- Qué hacen: los tipos del catálogo de moldes.
- Cómo funcionan: `DialogoMolde` es cerrado (`Ninguno` / `Formulario` / `ConfirmarBorrado`). `Formulario` calcula sus errores llamando a `revisarMolde` en cada tecla, expone `campos` (lo que hay que dibujar, de `camposDe`), `errorDe(campo)` para pintar cada aviso bajo el suyo, y `vistaPrevia` con el área y el volumen en vivo — que **no dependen del nombre**, porque esconder el volumen hasta que bauticen el molde sería tapar justo el número que dice si se midió bien. Lleva `rechazo` por lo mismo que los cuadros de sección: un nombre repetido se avisa junto al campo y no en la franja de abajo, que el teclado tapa. `ConfirmarBorrado` distingue `null` ("consultando") de lista vacía ("no lo usa ninguna receta"), igual que en ingredientes. `EstadoMoldes` distingue `catalogoVacio` de `busquedaSinResultados`.

### ListaMoldesScreen y ListaMoldes ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/moldes/ListaMoldesScreen.kt
- Qué hacen: el catálogo de moldes — botón fijo arriba, buscador, y los moldes debajo (9.2).
- Cómo funcionan: la división de siempre, una parte conecta el ViewModel y la otra solo dibuja. El formulario pide **solo los campos de la forma elegida**, y cuáles son sale de `estado.campos` (o sea de `camposDe`, en `logica/`) y no de un `when` escrito acá: es la misma lista que usa la validación, así que la pantalla no puede pedir una medida que nadie exige ni exigir una que nadie pidió. La forma se elige con chips y no con un desplegable — son cinco opciones cortas, caben a la vista, y un menú flotante en un celular tapa justo el formulario que se está llenando. Cada tarjeta muestra área y volumen **calculados**, nunca guardados (5.2), envueltos en `runCatching` para que un molde a medio guardar no cierre la app.

### nombreDeLaForma y ayudaDeLaForma ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/moldes/MoldesViewModel.kt
- Qué hacen: cómo se lee cada forma de molde en la pantalla, y su texto de ayuda.
- Cómo funcionan: reciben un `TipoFormaMolde`. La primera devuelve el nombre visible (`EXOTICO` se muestra como "Otra forma", que es lo que significa sin sonar técnico). La segunda devuelve `null` salvo para la exótica: es la única donde lo que se pide no se mide con una regla, y sin explicación nadie adivina que el volumen sale llenando el molde con agua.

### RecetasViewModel ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/RecetasViewModel.kt
- Qué hace: guarda lo que se ve en la lista de recetas y ejecuta lo que se pide desde ella.
- Cómo funciona: mismo patrón que `IngredientesViewModel` — `combine` de cuatro fuentes y `WhileSubscribed(5s)`. Lo propio de acá es que el costo de cada receta se pide **en lote** con `costosDe` y no una por una: con veinte recetas serían veinte consultas cada vez que cambia cualquier cosa. Sus acciones: `buscar`, `abrirAlta`, `cambiarTitulo`, `guardar`, `pedirBorrado`, `confirmarBorrado`, `cerrarDialogo`, `mensajeMostrado` y `avisarBloqueada`. **Ya no renombra**: `abrirCambioDeTitulo` se eliminó y `DialogoReceta.Formulario` perdió su campo `editando`, porque el título ahora se cambia desde adentro de la receta (`CantidadesViewModel.abrirRenombrarReceta`). Se eliminó en vez de dejarlo por si acaso: una rama que nadie recorre es una rama que nadie prueba.

### RecetaConCosto, EstadoRecetas y DialogoReceta ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/RecetasViewModel.kt
- Qué hacen: los tipos que la lista de recetas necesita para dibujarse.
- Cómo funcionan: `RecetaConCosto` junta la receta con lo que cuesta hacerla ahora mismo. `EstadoRecetas` distingue `catalogoVacio` de `busquedaSinResultados`, igual que ingredientes. `DialogoReceta` es cerrado (`Ninguno` / `Bloqueada` / `Formulario` / `ConfirmarBorrado`); su `Formulario` **solo crea** —renombrar se hace desde adentro de la receta— y su `ConfirmarBorrado` **no necesita consultar nada antes**, a diferencia del de ingredientes: lo que se pierde al borrar una receta está todo dentro de ella.

### ListaRecetasScreen, ListaRecetas y AccionesRecetas ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/ListaRecetasScreen.kt
- Qué hacen: la sección de recetas — botón fijo arriba, buscador, y las recetas debajo con su costo.
- Cómo funcionan: la misma división de siempre — `ListaRecetasScreen` conecta el ViewModel, `ListaRecetas` solo dibuja y se puede ver en la vista previa. `AccionesRecetas` agrupa las once funciones. La tarjeta de receta usa `tertiaryContainer` (el rosa pastel) en vez del crema del resto: es el único lugar con color propio, para que la lista se reconozca de un vistazo. **La tarjeta ya no tiene lápiz**: el único ícono que queda es el de borrar y el toque abre la receta, que es lo que se hace cien veces por cada renombrado. Una receta repetida lleva ahora su propio ícono de advertencia junto al texto, porque el lápiz en frambuesa era lo que avisaba antes de tocarla.

### CantidadesViewModel ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/CantidadesViewModel.kt
- Qué hace: el cerebro del paso "Cantidades" de una receta (8.2) — secciones, ingredientes y costo.
- Cómo funciona: **todo lo que muestra lo observa** — receta, secciones, ingredientes y catálogo entran al `combine` como `Flow`. Antes llevaba un contador `recargar` que esta misma clase incrementaba al terminar cada operación; eso alcanzaba mientras la receta cabía en una pantalla, pero con cuatro pasos dejó de alcanzar: reescalar por molde multiplica todas las cantidades **desde otro paso**, y acá no se enteraba nadie. Se veía como "al volver a un molde menor no reescala el ingrediente", y "a veces funciona si se insiste" era alguna acción de esta pantalla disparando la relectura. **El costo total se relee de la base** en vez de sumarse en memoria: sumarlo acá crearía una segunda verdad sobre el mismo número, y la que manda al calcular precios y sueldos es la de la base. Hay un test que comprueba que los dos caminos den lo mismo. **Pero se observa, no se consulta dentro del `combine`**: era `recetas.costoTotal(recetaId)` metido en la transformación, o sea una consulta más en serie en **cada** emisión de cualquiera de los otros cuatro flujos, y eso se sentía como que la pantalla iba un pasito atrás al escribir (6.7). Ahora entra como un flujo más —emparejado con el catálogo en un `combine` de a dos, porque `combine` llega hasta cinco y hacían falta seis— y Room decide solo cuándo recalcularlo. **También es dueño del título de la receta**: `abrirRenombrarReceta` (que lo lee de la base, no de `estado.value`, porque el `combine` deja de emitir cinco segundos después de ocultarse la pantalla), `cambiarTituloDeLaReceta` y `guardarTituloDeLaReceta`, que traduce el `Resultado` del repositorio a un rechazo dentro del cuadro.

### LineaDeIngrediente, SeccionConIngredientes, EstadoCantidades y DialogoCantidades ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/CantidadesViewModel.kt
- Qué hacen: los tipos del paso de cantidades.
- Cómo funcionan: `LineaDeIngrediente` cruza la fila de la receta con la ficha del catálogo —el cruce se hace en memoria y no con un `JOIN` porque la pantalla ya tiene el catálogo cargado para el buscador—, y expone `subtotal`. `SeccionConIngredientes` expone `costo`, la suma de sus líneas: **se suma en memoria y no se consulta**, al revés que el costo total, y no es una inconsistencia — el total manda porque de él salen precios y sueldos, así que viene de la base; el de la sección no alimenta ninguna cuenta y lo que sí tiene que hacer es cuadrar con las líneas que se ven justo encima, cosa que sumando esas mismas líneas pasa por construcción. `EstadoCantidades` consulta `debeMostrarNombreDeSeccion` para decidir si se ven los encabezados, y expone `mostrarCostoPorSeccion`, que es **desde dos secciones** y no desde una con nombre propio: con una sola, su costo es el total que ya está arriba en grande. `DialogoCantidades` es cerrado: `PonerIngrediente` (se llama así y no `Ingrediente` para no chocar con la entidad; lleva `yaEnLaSeccion` y `motivoNoDisponible`, que son los que impiden ofrecer un ingrediente ya puesto, y `rechazo` para lo que conteste el repositorio), `Seccion`, `RenombrarSeccion`, `RenombrarReceta` y `ConfirmarBorrarSeccion`. `RenombrarReceta` vive acá y no en la lista de recetas porque allá el toque ya está tomado por abrirla, y adentro es donde uno se da cuenta de que la receta terminó siendo otra cosa de la que se llamó al crearla. Los dos de sección llevan `rechazo`, que es lo que contestó el repositorio y entra en su `error` **antes** que la validación del campo: **un aviso sobre lo que se acaba de escribir va junto al campo y nunca en la franja de abajo**, porque con el teclado abierto esa franja queda tapada y el cuadro parece no haber hecho nada — se vio así en el celular con un nombre de sección repetido. Se limpia al escribir, porque el rechazo era sobre lo anterior.

### PasoCantidadesScreen, PasoCantidades y AccionesCantidades ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/PasoCantidadesScreen.kt
- Qué hacen: la pantalla del paso 1 de una receta.
- Cómo funcionan: misma división de siempre — una parte conecta el ViewModel, la otra solo dibuja y tiene vistas previas. **El costo total va fijo arriba, fuera del desplazamiento**: es el número por el que existe la pantalla, y con una receta larga quedaría fuera de vista justo mientras se ajustan las cantidades. Cada línea muestra la cuenta completa (`500 g × $1,2 = $600`) y no solo el total, para que un valor por gramo mal puesto salte a la vista. En el cuadro de agregar, el campo de gramos aparece **después** de elegir el ingrediente: pedir los dos a la vez obliga a decidir cuánto antes de saber de qué. Bajo el `TopAppBar` va `FilaDePasos`, y con ella se fue el botón de "Siguiente: rendimiento y molde" del final de la lista. **Tocar la cosa la edita**: el título del encabezado abre el cuadro de renombrar la receta, el nombre de una sección abre el suyo, y tocar la fila de un ingrediente cambia sus gramos; el único ícono que queda en cada fila es el de quitar.

### DuracionViewModel, BloqueDeDuracion y EstadoDuracion ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/DuracionViewModel.kt
- Qué hacen: el cerebro del paso "Duración" (8.4).
- Cómo funcionan: **guarda solo, bloque por bloque** (8.4.1), y se fue el botón de "Guardar duraciones"; con él se fue también `EstadoDuracion.puedeGuardar`, que ya no decidía nada. La pantalla llama a `guardarBloque` al perder el foco **y al desmontarse** (`DisposableEffect`): cambiar de paso puede llevarse el campo sin que alcance a avisar que perdió el foco, y ahí lo recién escrito se perdía en silencio. Lo que dispara `guardarBloque` es **salir del campo** y no cada tecla, a diferencia del paso de cantidades: acá un bloque a medio escribir se ve igual que uno vaciado a propósito. El switch de "no apto" y el selector de unidad guardan **al instante**, porque esos no se escriben a medias, se eligen. Cada bloque va solo y no los tres juntos: son independientes, y escribir en uno no tiene por qué tocar las filas de los otros dos. Con un número inválido no escribe nada y deja el error bajo el campo, y tampoco anuncia el éxito — sin botón, un "se guardó" por cada campo que se abandona aparecería justo mientras se pasa al bloque siguiente. `BloqueDeDuracion` expone `comoSeLee` —el texto en vivo, con el singular ya resuelto— y `diceAlgo`. `EstadoDuracion.puedeGuardar` es `true` **con todo vacío**: es el único paso de la receta que puede quedar en blanco. Marcar "no apto" **no borra lo escrito** en la pantalla, por si fue un toque por error; lo que no se guarda lo decide el repositorio.

### PasoDuracionScreen, PasoDuracion y AccionesDuracion ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/PasoDuracionScreen.kt
- Qué hacen: la pantalla del paso 3 de una receta.
- Cómo funcionan: va en una **lista perezosa**, como el resto de las pantallas de formulario, y no en una columna con desplazamiento. Es el paso más pesado de la receta —tres tarjetas con su switch, su campo y cuatro chips cada una, unos cuarenta componentes— y en una columna común se componen todos de golpe aunque no se vean; **eso se sentía como un tirón al entrar al paso**. Junto con eso, **los bloques no se dibujan hasta que llegaron los guardados**: antes se dibujaban tres vacíos y aptos y después se rehacían, así que un bloque guardado como "no apto" componía su campo y sus cuatro chips para tirarlos al fotograma siguiente. El guardado al salir pasó a un `DisposableEffect` **a nivel de pantalla**, y no dentro de cada tarjeta: con la lista perezosa, una tarjeta que sale de la vista al desplazarse también se desmonta, y ahí el guardado se dispararía por desplazar. El aviso de que las duraciones son estimaciones va **fijo arriba y no como texto de ayuda al pie**: es lo que hay que tener en la cabeza mientras se escriben los números, no algo que se lee después. Con "no apto" los campos **se ocultan en vez de deshabilitarse** — un campo gris invita a tocarlo y a preguntarse por qué no responde, y si no corresponde guardarlo así no hay ninguna duración que anotar. La pantalla dice que el paso es opcional cuando está vacío, para que no parezca que falta llenarlo. Lleva `FilaDePasos` bajo el `TopAppBar`, y su X sale de la receta en vez de volver al rendimiento.

### RecetaRepositorio.guardarDuracion y obtenerDuraciones ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hacen: guardar y leer los bloques de duración de una receta.
- Cómo funcionan: `obtenerDuraciones` devuelve un `Map<TipoDuracion, RecetaDuracion>`; los tipos que faltan son exactamente los que nadie llenó, que es un estado normal. `guardarDuracion` **borra el bloque si quedó sin decir nada** en vez de guardar una fila vacía: una fila con `apto = true` y `cantidad = null` es indistinguible de "todavía no lo sé", así que dejarla haría parecer que el paso está llenado. Con "no apto" guarda `cantidad` y `unidad` en `null`, para que volver a marcarlo apto no reviva un dato que ya nadie confirmó.

### RendimientoViewModel ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/RendimientoViewModel.kt
- Qué hace: el cerebro del paso "Rendimiento" (8.3): en cuántos trozos rinde y cuánto pesa.
- Cómo funciona: **guarda solo** (8.4.1): se fue el botón, y `programarGuardado` agenda una escritura para `ESPERA_ANTES_DE_GUARDAR_MS` después de la última tecla, cancelando la anterior. Se dispara desde los dos `cambiar…` y **no colgado del `Flow` de los campos**, porque esos también se re-siembran solos cuando el molde reescala el peso y eso no es alguien escribiendo. Espera un silencio en vez de escribir por tecla: tecleando "12" se pasa por "1", y con 1 trozo una promoción de 3 no cabe. Con lo escrito a medias no escribe nada —un campo vacío mientras se corrige no puede borrar lo guardado— y **no anuncia el éxito**, que sin botón sería ruido; el rechazo sí se dice, y va en `rechazoAlGuardar`, junto al campo de los trozos. Mantiene el diálogo fuera del `combine` (12.2.1) y **observa el rendimiento en vez de leerlo una vez**, porque `usaMolde` y el peso los escribe el paso anterior. Los dos campos de texto siguen a lo guardado con un `distinctUntilChanged` sobre `(trozos, pesoFinalG)`: así el peso reescalado desde el molde llega al campo —antes se quedaba con el número viejo y guardar lo escribía de vuelta encima del recalculado— sin que una escritura que no toca esos campos (`marcarPesoRevisado`) borre lo que se está tecleando. **Ya no se ocupa del molde**: eso se fue entero a `MoldeDeRecetaViewModel` al hacerse un paso propio (8.4.1, #2), y lo único que queda del molde acá es `usaMolde`, porque de eso depende que el peso final sea opcional u obligatorio. Sus acciones: `cambiarTrozos`, `cambiarPesoFinal`, `marcarPesoRevisado`, `guardar`, `abrirReescalarPorPeso`, `cambiarPesoNuevo`, `confirmarReescaladoPorPeso`, `cerrarDialogo` y `mensajeMostrado`. Lo nuevo es `pesoSinRevisar`, que **viene de la base y no de la sesión**: al cambiar de molde el peso se multiplica por el mismo factor que los ingredientes, y eso es una estimación que hay que ir a comprobar mañana, cuando el producto salga del horno. `marcarPesoRevisado` lo apaga, y la pantalla la llama **cuando el campo recibe el foco** — tocarlo para mirarlo es lo que el aviso pide, y exigir además una edición obligaría a borrar y reescribir el mismo número.

### MoldeDeRecetaViewModel ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/MoldeDeRecetaViewModel.kt
- Qué hace: el cerebro del paso "Molde" de una receta (8.3.1 y 9.3) — qué molde usa y el reescalado.
- Cómo funciona: **observa el rendimiento**, no lo lee una vez: el peso lo anota el paso siguiente y de él depende que se pueda quitar el molde, así que con una foto vieja la advertencia seguía diciendo que faltaba peso después de haberlo anotado. **Salió de `RendimientoViewModel`**, que hacía las dos cosas; se separó porque el reescalado es la operación más delicada de la app —multiplica todas las cantidades de una vez— y compartiendo pantalla con dos campos de texto quedaba a un toque de quien solo venía a corregir los trozos (8.4.1, #2). **No confundir con `MoldesViewModel`** (plural, en `ui/moldes/`), que es el catálogo de moldes de la app; este es el molde de **una** receta y del catálogo solo lee. Lo propio es que **`abrirElegirMolde` decide solo si es la primera vez o un reescalado**, leyendo si la receta ya tiene molde: esa distinción no la puede tomar la pantalla. `confirmarMolde` llama a `definirMolde` o a `reescalarPorMolde` según eso, y son dos funciones distintas a propósito — una sola que "haga lo que corresponda" reescalaría una receta que solo quería estrenar molde ante un error en la condición, y eso no se ve hasta que las cantidades ya están mal. Un rechazo queda **dentro del cuadro** y no en la franja de abajo, porque ahí mismo está el selector de modo que lo resuelve. El aviso de un reescalado **manda a Rendimiento**, que es donde quedó el peso multiplicado esperando que alguien lo mire.

### DialogoRendimiento y EstadoRendimiento ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/RendimientoViewModel.kt
- Qué hacen: los tipos del paso de rendimiento.
- Cómo funcionan: `EstadoRendimiento` expone `costoDeCadaTrozo`, que sale de `repartirEntreTrozos` —la misma división que da el peso por trozo y el costo con que se calculan las ganancias— y de un `costoTotal` **observado** con `observarCostos`, porque quien lo mueve son los ingredientes y esos se cargan en otro paso. Lleva `tieneIngredientes` **aparte del costo**, repitiendo la lección que ya costó un bug en la lista de recetas: un ingrediente puede valer 0 a propósito y una receta hecha solo de esos cuesta 0 sin estar vacía. `rechazoAlGuardar` lleva lo que contestó el repositorio al guardar solo, y entra en `errorTrozos` **antes** que la validación del campo: sin botón que apretar, un aviso en la franja de abajo llegaría en un momento que nadie asocia con lo que hizo, y con el teclado abierto ni se ve (8.2). El único rechazo posible es el de las promociones que no caben, que es exactamente sobre los trozos. `DialogoRendimiento` quedó con `Ninguno` y `ReescalarPorPeso` — los del molde se fueron a `DialogoMoldeDeReceta`. `EstadoRendimiento` expone `pesoDeCadaTrozo` en vivo —usando `pesoPorTrozo` y `textoANumero`, sin reglas propias sobre cómo se escribe un número—, `avisoDelPeso` (el texto de `AVISO_PESO_REESCALADO`, o `null` si no hay nada que comprobar) y `sePuedeReescalarPorPeso`, que es **solo sin molde**: con molde, cambiar de tamaño es cambiar de molde y eso vive en el paso anterior.

### DialogoMoldeDeReceta, EstadoMoldeDeReceta y OrigenDelMolde ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/MoldeDeRecetaViewModel.kt
- Qué hacen: los tipos del paso del molde.
- Cómo funcionan: `OrigenDelMolde` distingue `GUARDADO` (queda enlazada al catálogo y recibe correcciones) de `PRUEBA` (medidas propias, sin vínculo) — **no da lo mismo cuál**, y la pantalla lo dice con todas las letras porque es la única diferencia invisible entre dos recetas con las mismas medidas. `DialogoMoldeDeReceta.Elegir` calcula sus `dimensiones` y su `moldeOrigenId` según el origen, y solo muestra el selector de modo cuando `esReescalado`. Lleva además `moldeActualId` y `motivoNoDisponible`: **el molde que la receta usa se muestra marcado y no se puede tocar** — lo pidió Sandy al probar quitar y poner molde, porque al abrir la lista no había forma de saber en cuál estaba, y elegirlo tampoco haría nada (el mismo criterio de `FichaDePaso`). Sale de la lectura que `abrirElegirMolde` ya hacía; no se observa porque el molde de la receta no puede cambiar mientras ese cuadro está abierto: cambiarlo es lo que lo cierra. En modo prueba es `null`, porque ahí la receta no está enlazada a ninguno. `EstadoMoldeDeReceta` expone `medidasDelMolde` (las tomadas, vía `medidasEnTexto`) y `areaYVolumen` (las calculadas) — **las dos consultan `usaMolde` y no solo si hay dimensiones**, porque `quitarMolde` las conserva a propósito por si fue un error, y sin esa condición una receta que acababa de dejar el molde seguía mostrando sus centímetros debajo de "No utiliza molde"; el área va envuelta en `runCatching` para que un molde a medio guardar no cierre la pantalla, `enlazadaAlCatalogo` y `tienePesoFinal`, que apaga el botón de quitar el molde **antes** de confirmar: el repositorio lo rechazaría igual, pero enterarse al confirmar es enterarse cuando ya se decidió.

### PasoRendimientoScreen, PasoRendimiento y AccionesRendimiento ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/PasoRendimientoScreen.kt
- Qué hacen: la pantalla del paso "Rendimiento": trozos, peso y el peso por trozo.
- Cómo funcionan: la división de siempre. **El molde se fue a su propio paso** (8.4.1, #2) y acá quedaron los dos campos y el número que sale de ellos. Lleva `FilaDePasos` bajo el `TopAppBar`; la X y el botón de atrás **salen de la receta** en vez de volver a cantidades, que era el choque de gestos de 8.1. Si el peso viene de un reescalado por molde, arriba de todo aparece `AvisoDePesoReescalado` — **en el color de error y no en el pastel de las tarjetas de dato**, siguiendo la regla de 12.6 de que los pasteles pintan fondos y las señales pintan íconos y texto. Va arriba y no como texto de ayuda del campo porque ese hueco ya lo ocupa la explicación de si el peso es obligatorio, y un aviso que aparece y desaparece ahí haría saltar el formulario.

### PasoMoldeScreen, PasoMolde y AccionesMoldeDeReceta ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/PasoMoldeScreen.kt
- Qué hacen: la pantalla del paso "Molde" de una receta.
- Cómo funcionan: la división de siempre. El cuadro de molde dice en una línea qué va a pasar —"solo se guardan las medidas" o "las cantidades y el peso se van a recalcular"—, que es la confusión de 9.3 puesta a la vista. El selector de modo usa las palabras del resultado y no las de la fórmula: "Que rinda más" y "El mismo grosor", con la explicación debajo. La tarjeta del molde dice **siempre si la receta está enlazada al catálogo o no**, porque de eso depende que una corrección de medidas le llegue, y es lo único que distingue dos recetas con las mismas medidas.

### PasoDeReceta ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/FilaDePasos.kt
- Qué hace: en qué paso de una receta abierta se está, y cómo se llama ese paso en la pantalla.
- Cómo funciona: `enum` con `CANTIDADES`, `DURACION`, `MOLDE`, `RENDIMIENTO`, `GASTOS` y `SIMULACION`, **cada uno con su `titulo`**. **`DURACION` va segunda desde que Sandy la reportó como molesta en medio del camino**, y el motivo es el que la distingue: es el único paso que **no alimenta ninguna cuenta** —cuánto dura un producto no entra en el costo, ni en el precio, ni en la proyección— mientras que los otros cuatro forman una cadena (molde → rendimiento → gastos → simulación). Enclavada en medio obligaba a saltarla cada vez que se recorría esa cadena; al principio queda junto a cantidades, que es lo otro que se anota mirando la receta en vez de la calculadora. `GASTOS` va después de rendimiento porque necesita lo que definen los otros: el costo sale de los ingredientes y todo se reparte entre los trozos. `MOLDE` va **antes** que rendimiento y no después, porque decide lo de allá: con molde el peso final es opcional y sin molde es obligatorio (8.3), así que preguntar el peso primero obligaría a cambiar la respuesta después, en `rememberSaveable` para que girar el teléfono no devuelva al primero. Es enum y no un booleano porque de acá salen los **siete** pasos del asistente (8.1): con un booleano el tercero ya obligaría a rehacerlo — y el molde llegó después, que es justo el caso que un booleano no habría aguantado. **Se movió desde `NavegacionPrincipal.kt`** al agregarse la fila de pasos: el enum y el Composable que lo dibuja son la misma decisión y separarlos obligaba a que el paquete `ui` supiera de recetas. El `titulo` vive acá y no en la pantalla por lo mismo que la etiqueta de `CampoDeMolde`: dos listas de textos paralelas se desincronizan sin que nadie lo note. Abrir una receta siempre empieza por el primer paso — quedarse donde se dejó la anterior confundiría más de lo que ahorra.

### GastosViewModel, EstadoGastos, DialogoGastos y FilaDePrecio ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/GastosViewModel.kt
- Qué hacen: el cerebro del paso 5, "Gastos y Ganancias" (8.5 y 8.6).
- Cómo funcionan: todo sale de **un** `DatosCalculoReceta` **observado** con `observarDatosCalculo`, porque de las cinco cosas que lleva el snapshot **tres las escriben otros pasos** — el costo los ingredientes, los trozos el rendimiento y el título el primero — y con una foto vieja este paso mostraría ganancias calculadas contra un costo que ya cambió, que no se nota porque el número es creíble. Es el primer paso que **no guarda mientras se escribe**, y no es una excepción a 8.4.1: acá no hay campos sueltos sino precios que se crean, editan y borran desde un cuadro. `EstadoGastos` devuelve `null` en las cifras cuando no hay precios —mostrar 0 diría "se vende y no deja nada", que es otra cosa— y lleva `tieneIngredientes` **aparte del costo**, repitiendo la lección de siempre: un ingrediente puede valer 0 a propósito, y acá pesa más que en ningún lado porque con costo 0 la ganancia es el precio entero y el negocio parece redondo. `basesQueFaltan` **delega en `basesQueFaltanEn`** y ya no repite la condición: el repositorio aplica la misma regla al guardar, y escritas por separado la pantalla habilitaría un botón que allá se rechaza. Con eso `baseDelTrozo` y `baseDelProducto` se quedaron sin lector y **se eliminaron** —una rama que nadie recorre es una rama que nadie prueba—. `DialogoGastos.Formulario` lleva ahora `basesQueFaltan` y expone `estaPoniendoUnaBase`; va **vacía al editar** un precio que ya existe, porque la regla es para el orden en que se arma una receta y no una traba para corregir lo guardado. `laReferenciaPierdePlata` cuelga del **estado y no de la acción**, porque a ese estado se llega sin elegir nada malo (ver la grieta de 8.6). `FilaDePrecio` trae la ganancia de cada precio: para eso existe la lista, para comparar promociones. **La referencia se resuelve por `id` y no comparando `PrecioVigente`**, que no lleva id y es un `data class`: dos promociones idénticas —un "2 por $1.500" cargado dos veces— serían iguales entre sí y la lista marcaría las dos; hay una prueba que compara esa resolución con la de `precioDeReferencia` para que no se separen.

### PasoGastosScreen, PasoGastos y AccionesGastos ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/PasoGastosScreen.kt
- Qué hacen: la pantalla del paso 5.
- Cómo funcionan: la división de siempre. **Las cifras van arriba y los precios abajo**: lo que se viene a ver es cuánto deja la receta, y los precios son de dónde sale ese número. **Tocar una promoción la elige como referencia** —es lo que pide 8.6— y la que ya manda no responde al toque (8.4.1, #6). De ahí sale **el único lápiz que queda en la app**, y es una excepción con motivo: 8.4.1 #3 sacó los íconos de editar porque el toque ya hacía eso, pero acá el toque está tomado, y sin lápiz un precio mal escrito solo se arreglaría borrándolo. El trozo ganador **no se muestra como número cuando cae fuera de la receta**: ahí se dice cuántos harían falta y cuántos hay, que es el caso que más importa ver. Una ganancia negativa se pinta en el color de error, nunca en un pastel (12.6). **Los avisos esperan a que lleguen los datos** (`!estado.cargando`): mientras el estado es el inicial, `tieneIngredientes` es `false` porque nadie contestó y no porque la receta esté vacía — sin esa condición, entrar a cada receta encendía el aviso rojo de "no tiene ingredientes" durante un fotograma y lo apagaba, que es el parpadeo que Sandy reportó. **`AvisoDelResto` se movió adentro de la tarjeta de cifras**, justo encima del trozo ganador: como tarjeta suelta arriba de todo quedaba fuera de la pantalla apenas se desplazaba y parecía no salir nunca. Es una banda teñida (`tertiaryContainer`) y no otra `Card`, y **no un texto en color `tertiary`**: eso se intentó y `contraste.py` lo rechazó con 3,97:1 en modo claro.

### TituloDeRecetaViewModel ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/TituloDeRecetaViewModel.kt
- Qué hace: el título de la receta abierta, **uno solo para los cuatro pasos**.
- Cómo funciona: observa `observarReceta` y expone `titulo: StateFlow<String>`, con `SharingStarted.Eagerly` porque mientras la receta esté abierta siempre hay alguien mirándolo — los cuatro pasos dibujan esa barra— y cortar la consulta al cambiar de paso sería volver a empezar justo cuando no se debe. **Lo crea `NavegacionPrincipal`**, que es el único punto que vive mientras la receta está abierta; creado desde una pantalla volveríamos al problema con otro nombre. Nació de un parpadeo visto en el celular: al cambiar de paso el nombre de la receta desaparecía un instante y volvía, una vez por paso. La causa no era el dibujo sino de dónde salía el texto — **la misma fila se observaba cuatro veces**, y un `StateFlow` empieza por su valor inicial mientras Room contesta, así que cada observación nueva tiene su propio instante en blanco. El valor inicial es `""` y no un texto de relleno: "Cargando…" sería el mismo parpadeo con mejor letra. Los tres pasos que solo mostraban el título **dejaron de pedir la receta entera** (`EstadoRendimiento`, `EstadoDuracion` y `EstadoMoldeDeReceta` perdieron su campo `receta`): es una consulta menos en cada uno, no una más acá. El paso de cantidades sí la conserva, porque además la renombra.

### FilaDePasos ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/FilaDePasos.kt
- Qué hace: la fila de pasos que se desplaza en horizontal, debajo del título de la receta.
- Cómo funciona: Composable que recibe `pasoActual: PasoDeReceta`, `alElegirPaso: (PasoDeReceta) -> Unit` y un `modifier`; dibuja una ficha por cada `PasoDeReceta.entries` dentro de un `Row` con `horizontalScroll`. **Reemplaza a los botones de "Siguiente"** de cantidades y rendimiento: con el botón al final, la receta se recorría en un solo sentido y no se sabía cuántos pasos había hasta llegar al último. Resuelve además el choque de gestos que se veía en el celular — la X de rendimiento cerraba el paso en vez de la receta —: con la fila, **la X siempre sale de la receta**. Va dentro del `topBar` de las tres pantallas (en un `Column` junto al `TopAppBar`), así que queda fija y no se desplaza con el contenido. La ficha actual no se puede tocar: llevaría al mismo lugar, y un toque que no hace nada deja dudando.

### FichaDePaso ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/FilaDePasos.kt
- Qué hace: un paso dentro de [FilaDePasos], relleno si es el actual.
- Cómo funciona: Composable privado; recibe el paso, si es el actual y qué hacer al tocarlo. La diferencia entre actual y no actual es de **fondo y no de grosor de letra**: en un celular al sol, "negrita contra normal" no se distingue de un vistazo y el color sí. Se arma con `clip` + `background` + `clickable` en vez de con `FilterChip`, que es lo que usa el resto de la app para elegir opciones: un `FilterChip` mide 32dp de alto y esto es la navegación principal de la receta, así que respeta `Medidas.objetivoTactil` (48dp) como cualquier otra cosa que se toque.

### NavegacionPrincipal y Seccion ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/NavegacionPrincipal.kt
- Qué hacen: el menú de 3 líneas y la sección que se esté viendo (12.1).
- Cómo funcionan: `ModalNavigationDrawer` con un `enum Seccion` que hoy tiene Ingredientes y Recetas. Abrir una receta la muestra **a pantalla completa, sin el menú**: es un paso dentro de la receta, no una sección de la app. Se expresa como dos ramas de un `if` (`MenuDeSecciones` aparte) y no con un `return` temprano dentro del Composable, para que quede claro que son dos árboles distintos. **Salir de la receta y cambiar de paso son dos gestos que ya no se pisan**: `cerrarReceta` (la X y el botón de atrás, desde cualquier paso) y `elegirPaso` (la fila de arriba) van los dos en `remember`, porque cada pantalla arma su `Acciones*` con `remember(...)` sobre ellas y sin eso ese `remember` no serviría de nada. Los ViewModel de la receta abierta **viven en `ModelosDeLaReceta`** y no en el store de la Activity: así se sueltan al cerrarla, junto con sus observadores de la base, que es lo que hacía que la app se fuera poniendo lenta con el uso (6.7). De paso ese dueño reemplaza a las claves `"cantidades-<id>"`, que eran lo que antes impedía que una segunda receta reutilizara el ViewModel de la primera. **El orden del menú es el de lo que hay que tener antes** —Ingredientes, Moldes, Recetas—, y no el de lo que más se usa: una receta no se costea sin ingredientes ni lleva molde sin catálogo, así que recetas va última por depender de las dos. Es lo que pidió Sandy. **Moldes y Empleados no están puestos en gris**: una opción que no lleva a ninguna parte se toca igual y parece que algo se rompió; se agregan al enum cuando exista su pantalla. Cada sección conserva su ViewModel al cambiar de una a otra, porque `viewModel()` los guarda en la Activity — ir a Recetas y volver no borra lo escrito en el buscador. La sección elegida va en `rememberSaveable` para sobrevivir al giro del teléfono.

### RecetaRepositorio.actualizarDimensionesMolde y obtenerRecetasConMoldeOrigen ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hacen: cambiar las medidas del molde guardadas en una receta, y saber qué recetas siguen enlazadas a un molde del catálogo.
- Cómo funcionan: las dos `suspend`. `actualizarDimensionesMolde` **no toca `moldeOrigenId` ni las cantidades de ingredientes** (5.2): es la que usa `MoldeRepositorio.actualizar` al corregir una medida, y corregir no es reescalar. Si la receta no tiene fila de rendimiento no hace nada, en vez de crear una a medias. Viven acá y no en `MoldeRepositorio` a propósito: quién puede escribir en el rendimiento de una receta es cosa de este repositorio, y prestarle el DAO a otro es cómo terminan existiendo dos lugares que modifican la misma tabla con reglas distintas.

### MoldeDao.obtenerTodosUnaVez ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/db/dao/MoldeDao.kt
- Qué hace: el catálogo de moldes completo, una sola vez.
- Cómo funciona: `suspend`, devuelve `List<Molde>` ordenada por nombre. Existe por lo mismo que su gemela en `IngredienteDao`: comparar nombres ignorando tildes no lo puede hacer SQLite, así que `buscarParecido` trae la lista y compara en memoria.

### RecetaRepositorio.definirMolde ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hace: le pone molde a una receta **por primera vez**, sin reescalar nada.
- Cómo funciona: `suspend`, recibe `recetaId`, las `DimensionesMolde` y un `moldeOrigenId` (`null` en modo prueba). Es la distinción de 9.3 que se paga cara si se confunde: la primera vez **no hay original contra el cual comparar**, así que no hay factor, no se elige modo y las cantidades quedan tal como se escribieron. Rechaza la receta que ya tiene molde y la manda al reescalado. Reescalar es del segundo molde en adelante.

### RecetaRepositorio.reescalarPorMolde y reescalarPorPeso ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hacen: ajustan las cantidades de una receta al cambiarla de molde, o para que rinda otro peso (8.3.1).
- Cómo funcionan: `suspend`, devuelven `Resultado`. **`reescalarPorMolde` no deja escapar la excepción de `factorEscala`**: la convierte en `NoSePudo(motivo)` porque la pantalla tiene que poder mostrar el texto —"Demasiado riesgo. Mejor escale con el otro método"— y una excepción cerraría la app en vez de explicar. Al terminar guarda medidas **y vínculo**: enlazada si se eligió un molde del catálogo, suelta si fue modo prueba. `reescalarPorPeso` es para las recetas sin molde y **rechaza las que sí lo tienen**, porque ahí el peso final es opcional y el cálculo caería sobre un dato que puede no existir. Las dos redondean a 2 decimales, que es lo que hace que el subtotal de la pantalla coincida con lo que suma la base.

### RecetaRepositorio.guardarRendimiento y quitarMolde ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hacen: guardar trozos y peso final, y dejar de usar molde.
- Cómo funcionan: `suspend`, devuelven `Resultado`. `guardarRendimiento` **avisa antes de romper una promoción**: si bajar los trozos deja imposible alguna promo por trozo, no escribe nada y devuelve el motivo nombrándolas. `quitarMolde` exige tener peso final anotado —sin molde pasa a ser obligatorio— y **conserva las medidas** por si fue un error, cortando solo el vínculo.

### RecetaRepositorio.marcarPesoRevisado ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hace: apaga el aviso de "peso reescalado, compruébalo" de una receta (8.4.1, #4).
- Cómo funciona: `suspend`, recibe `recetaId` y no devuelve nada. Pone `pesoReescaladoSinRevisar` en `false` y **se corta sola si ya estaba apagado**, porque la llama la pantalla cada vez que el campo del peso recibe el foco y no tiene sentido escribir en la base por mirar. **No registra evento en el historial**: no cambió ningún dato de la receta, solo se leyó uno.

### RecetaRepositorio.observarReceta, observarSecciones, observarIngredientes y observarRendimiento ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt (y sus `@Query` en RecetaDao.kt)
- Qué hacen: las cuatro lecturas de una receta que **avisan cuando cambian**, en vez de leerse una vez.
- Cómo funcionan: devuelven `Flow`. Nacieron del mismo bug que `observarCostos`, pero visto entre pasos: desde que una receta se recorre en cuatro pantallas, **quien escribe un dato casi nunca es quien lo muestra**. El paso del molde reescala los ingredientes y el peso; el de rendimiento lee `usaMolde` para decidir si el peso es obligatorio; el de cantidades muestra las cantidades que el molde acaba de multiplicar; y el título se cambia en el primero y se muestra en los cuatro. Con el contador `recargar` que tenía cada ViewModel, **cada uno solo se enteraba de sus propios cambios**: se veía como "el peso no cambió", "la opción de reescalar no desaparece hasta que toco algo", "a veces funciona si insisto". Peor todavía, guardar desde una pantalla con la copia vieja **escribía el dato viejo encima del recalculado**. Con esto se cumple la regla que ya estaba escrita: *lo que se muestra se observa; la foto de un momento es para calcular*.

### RecetaRepositorio.crearPrecio, editarPrecio, eliminarPrecio, observarPrecios y observarDatosCalculo ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hacen: el lado de datos del paso de gastos (8.6).
- Cómo funcionan: `crearPrecio` revisa con `revisarPrecio` **antes** de escribir y necesita los trozos para el tope del último trozo. **No marca el primero como referencia**, y no hace falta: `precioDeReferencia` cae solo en el de menor ganancia cuando nadie eligió, así que con un precio único ese manda igual — marcarlo diría que alguien lo decidió, y la pantalla usa esa diferencia. `editarPrecio` **conserva `esReferencia`**: escribir la fila entera sin ese cuidado la apagaría en silencio. **Deja a propósito que la referencia quede en pérdida**, porque `errorAlElegirReferencia` protege el acto de *elegir* una promo mala, y bloquear la edición sería una regla que no se sostiene: al mismo estado se llega con que suba un ingrediente en otra pantalla. `eliminarPrecio` lee cómo se llama el precio **antes** de borrarlo, porque después no habría cómo nombrarlo en el historial; borrar el de referencia no rompe nada —vuelve a mandar el de menor ganancia— y borrar el último deja la receta como si nunca hubiera pasado por el paso. `observarDatosCalculo` es a `obtenerDatosCalculo` lo que `observarCostos` es a `costosDe`: la misma foto pero para mostrar, armada con `combine` de receta, rendimiento, costos y precios.

### RecetaRepositorio.crearPrecio — las dos reglas de los precios base ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hace: además de lo de siempre, **rechaza una promoción sin precios base y una base repetida** (8.6.1).
- Cómo funciona: lee los precios guardados una vez, arma `basesQueFaltanEn(...)` y se lo pasa a `revisarPrecio`, que devuelve el aviso por el campo de la cantidad. Después comprueba aparte que no exista ya otra fila `cantidad = 1` del mismo modo: `precioBasePorTrozo` se queda con la primera que encuentra, así que la segunda quedaría guardada sin alimentar nada — y en la lista las dos se dibujan casi iguales. **Las dos comprobaciones van acá y no solo en la pantalla** por la razón de siempre: la pantalla valida para habilitar el botón, y el repositorio es el que decide; además, `crearPrecio` la llama también el flujo de pruebas y cualquier camino futuro. Lo que **no** bloquea es editar un precio ya guardado, ni la propia `cantidad = 1`, que es por donde se empieza.

### ModelosDeLaReceta ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/ui/recetas/ModelosDeLaReceta.kt
- Qué hace: guarda los ViewModel de **la receta abierta** en un `ViewModelStore` propio, para poder soltarlos al cerrarla.
- Cómo funciona: `de(recetaId)` devuelve el `ViewModelStoreOwner` de esa receta (creándolo la primera vez), `cerrar(recetaId)` lo vacía, y `onCleared` limpia todos. Nació de una lentitud que crecía con el uso (6.7): `viewModel()` sin dueño propio guarda en el de la **Activity**, así que los seis pasos de cada receta más el del título quedaban vivos hasta cerrar la app — y no dormidos, porque cuatro observan la base con un `viewModelScope.launch { … .collect { } }` que **no se detiene** al dejar de mirarse. Guardar un precio despertaba a los de todas las recetas abiertas en la sesión. **Es un ViewModel y no un `remember`** a propósito: así sobrevive a girar el teléfono y aun así se puede vaciar a mano, cosa que el store de la Activity no permite (su `clear()` se lleva todo, incluidos los de las listas). `cerrar` se llama desde `cerrarReceta` y **no desde un `DisposableEffect`** — cerrar es un acto, no un efecto de dejar de dibujarse, y con un efecto girar el teléfono también los soltaría. Con esto las claves `"cantidades-<id>"` dejaron de hacer falta y se fueron: quien separa una receta de otra es el dueño.

### RecetaRepositorio.observarCosto y RecetaDao.observarCostoDeReceta ✅ IMPLEMENTADAS
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt (y su `@Query` en RecetaDao.kt)
- Qué hacen: el costo de **una** receta, avisando cuando cambie.
- Cómo funcionan: devuelven `Flow<Double>`. **Es la que hay que usar con una receta abierta**; `observarCostos` (plural) es para la lista, que sí las necesita todas. Existen por rendimiento (6.7): `observarDatosCalculo` usaba la plural, que recorre y agrupa la base completa, para quedarse con una sola entrada del mapa — y con la receta abierta hay dos pantallas suscritas a ese snapshot, así que mover un gramo disparaba dos recorridos de toda la base. El `WHERE` va antes del agrupamiento y se apoya en el índice de `receta_secciones(recetaId)`. **Contesta 0 para una receta sin ingredientes en vez de omitirla**, que es la diferencia con la plural: al no llevar `GROUP BY`, `SUM` sobre cero filas da una fila con `NULL` que el `COALESCE` convierte en 0, así que quien la lea no tiene que acordarse de rellenar el hueco. Hay una prueba que la compara contra `costoTotal` para que las dos no se separen.

### AppContainer.precalentar ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/AppContainer.kt
- Qué hace: abre la base **desde donde se la llame**, para que no le toque al hilo principal.
- Cómo funciona: no recibe ni devuelve nada; toca `recetas`, que atraviesa los dos `by lazy` y construye Room. La llama `ReposteriaApp.onCreate` en `Dispatchers.IO` y **no se espera**: si la pantalla llega antes, el `by lazy` la hace esperar lo que falte; si llega después, se encuentra todo listo. En el peor caso no gana nada y nunca empeora. Junto con esto, `AppContainer.base` pasó a ser `by lazy`: antes se construía en el constructor, y como `MainActivity.onCreate` pide el contenedor, Room se armaba en el hilo principal **antes del primer cuadro** — cargar la clase generada, cinco DAO y los adaptadores de quince entidades, que en una app recién instalada todavía no está compilada de antemano (6.7).

### RecetaRepositorio.observarCostos ✅ IMPLEMENTADA
- Ubicación: app/src/main/java/com/sandyyera/reposteria/data/repositorio/RecetaRepositorio.kt
- Qué hace: el costo de todas las recetas, avisando solo cuando cambia. Es la que hay que usar para **mostrar** costos.
- Cómo funciona: devuelve `Flow<Map<Long, Double>>`. **Ojo con la del DAO, que se llama igual y devuelve otra cosa**: `dao.observarCostos()` da `Flow<List<CostoDeReceta>>`, las filas crudas, y esta las convierte en el mapa. Confundirlas cuesta un build: pasó en `observarDatosCalculo`, que terminó indexando una lista por el id de la receta. Nació de un bug visto en el celular: la lista pedía los costos con `costosDe` —una foto de un momento— colgada del `Flow` de la tabla `recetas`; borrar un ingrediente no toca esa tabla, así que nadie volvía a preguntar y la lista seguía mostrando costos que ya no existían. Con un `Flow`, Room vigila `receta_ingredientes`, `receta_secciones` e `ingredientes` y reemite en cuanto cambia cualquiera. **El mapa no trae entrada para las recetas sin ingredientes** (el `GROUP BY` no les da fila) y acá no se puede rellenar como en `costosDe`, porque no se sabe qué recetas hay sin consultarlas: quien lo lea toma lo que falte como 0. **Regla que deja establecida: lo que se muestra se observa; `costosDe` es para calcular.**

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

### revisar_simbolos, revisar_importaciones, revisar_acciones, revisar_enchufes, revisar_constantes, revisar_nombres_con_acentos, revisar_esquemas y revisar_aserciones ✅ IMPLEMENTADAS
- Ubicación: herramientas/revisar_kotlin.py
- Qué hacen: las ocho revisiones del código Kotlin que se pueden hacer sin compilador ni Android SDK, que es la situación de siempre acá — `:app` solo compila en el equipo de Sandy.
- Cómo funcionan: `revisar_simbolos` cuenta llaves, paréntesis y corchetes; `revisar_importaciones` marca un tipo en CamelCase usado sin `import` ni definición en su paquete —contando como definición también la de un `typealias`, que faltaba y sacó un falso positivo con el primero que apareció—; `revisar_acciones` compara cada campo de un `Acciones*` con la firma del `modelo::metodo` al que se ata, y solo avisa si **ninguna** firma con ese nombre calza (`pedirBorrado` existe en dos ViewModel); `revisar_enchufes` hace lo mismo contra el parámetro de la pantalla (`abrir = alAbrirReceta`), que es por donde se coló el `(Receta) -> Unit` que recibía un `Long`; `revisar_constantes` marca una constante en MAYÚSCULAS escrita a secas que en realidad vive dentro de un `companion object` —pasó con `MIGRACION_1_2`, que resuelve dentro de su propia clase y falla solo en el archivo de afuera que la usa, y ese archivo era la prueba instrumentada, que tarda casi cuatro minutos en compilar—; `revisar_nombres_con_acentos` marca un nombre de declaración entre acentos graves con un carácter que la JVM no admite —los nombres de las pruebas se escriben como frases y ahí es natural poner dos puntos, que Kotlin deja escribir y rechaza recién al compilar—, mirando **solo declaraciones** y no los acentos graves de la documentación, que son texto para leer y daban cuarenta avisos falsos por cada uno de verdad; `revisar_esquemas` compara la versión declarada en `AppDatabase` con los `app/schemas/N.json` versionados, y así descubrió que faltaba el de la versión 2. `revisar_aserciones` marca una aserción de JUnit usada sin su `import org.junit.Assert.assertX` — la revisión 2 no las ve **y no es un descuido de aquella**: mira nombres en CamelCase porque así se llaman los tipos, y las aserciones empiezan en minúscula. Se pagó con `assertNull` en `DuracionViewModelTest`, que encima no se notaba al instalar: `installDebug` no compila las pruebas, así que el error esperó al siguiente `:app:test`. Se limita a `org.junit.Assert` a propósito, porque ahí el conjunto es cerrado y no hay falsos positivos. Todas devuelven cuántos problemas encontraron y `main` termina con código 1 si hay alguno. **Solo mira nombres y tipos escritos tal cual**: nada que dependa de inferencia —el tipo de un `val` local, por ejemplo— está a su alcance, así que pasar limpio no significa que compile.

### probar_todo.sh ✅ IMPLEMENTADO
- Ubicación: herramientas/probar_todo.sh
- Qué hace: corre de una vez todo lo que se puede comprobar sin celular.
- Cómo funciona: cuatro pasos en orden de rapidez —`revisar_kotlin.py`, `contraste.py`, `:logica:test`, `:app:test`— y **se detiene en el primero que falle**. Tiene una sola aclaración especial, `recordar_esquema`, para el único fallo esperable que no es un error: al subir la versión de la base, `app/schemas/N.json` lo escribe Room al compilar, así que hasta el primer `./gradlew :app:assembleDebug` no existe. No se le hace excepción a la revisión —es la que se asegura de que ese archivo quede versionado— pero sí se dice qué hacer: seguir veinte minutos de pruebas cuando ya hay un archivo con una llave sin cerrar no aporta nada. Al terminar recuerda lo que sí necesita el celular (`connectedAndroidTest` e `installDebug`) y que el respaldo va antes. No reemplaza a ninguno: es el orden, para no tener que acordarse de los cuatro.

### medir_arranque.sh ✅ IMPLEMENTADO
- Ubicación: herramientas/medir_arranque.sh
- Qué hace: mide cuánto tarda la app en abrirse **en frío**, varias veces, y da la mediana.
- Cómo funciona: por cada intento hace `am force-stop` y **después** `am start -W`, lee `TotalTime` y `LaunchState`, y descarta los que no salieron `COLD`. Existe por una medición que salió mal y que conviene no repetir: `adb shell am start -W` a secas, con la app recién usada, contestó `LaunchState: WARM` y 121 ms — estaba midiendo *volver* a una app que nunca se cerró, no abrirla. El pegón que se siente es el arranque frío. Repite cinco veces por defecto porque un solo número no distingue "lento" de "justo pasó algo en el teléfono", y reporta la **mediana** y no el promedio, que un intento malo desvía. Comprueba antes que haya `adb`, celular conectado y la app instalada, cada uno con su mensaje. Al final orienta según el resultado, y pasado el segundo manda a comparar contra `:app:installRelease`, que es la otra mitad de la explicación (6.7).

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
