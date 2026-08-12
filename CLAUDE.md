# Ver arquitectura.md para el diseño completo del proyecto.

# Instrucción: revisar `.gitignore` y `.gitattributes` al sumar algo nuevo

Estos dos archivos no se escriben una vez y se olvidan: hay que volver a ellos **cada vez
que entra tecnología nueva al proyecto** (una librería, una herramienta, un tipo de archivo
que antes no existía) o que aparece cualquier dato que no debería publicarse. La revisión
va en el **mismo cambio** que introduce lo nuevo, no después.

## Qué preguntarse en cada uno

**`.gitignore` — ¿esto genera algo que no debe subirse?**
- ¿Crea carpetas de compilación, cachés o archivos temporales?
- ¿Trae credenciales, tokens, claves o certificados? (ojo con los nombres que impone la
  herramienta: Google Cloud Console, por ejemplo, descarga un
  `client_secret_XXXX.apps.googleusercontent.com.json` con ese nombre exacto)
- ¿Genera archivos con rutas absolutas de esta máquina, que a otra no le sirven?
- ¿Puede llegar a contener **datos reales del negocio** — recetas, costos, márgenes,
  sueldos? Esos no son código y no van al repositorio.

**`.gitattributes` — ¿trae tipos de archivo que Git podría arruinar?**
- Todo formato binario nuevo (imágenes, fuentes, librerías `.so`/`.aar`) va marcado como
  `binary`. Si no, Git puede creer que es texto y corromperlo al normalizar saltos de línea.
- Todo script nuevo necesita su salto de línea explícito: `eol=lf` para Linux/Mac,
  `eol=crlf` para Windows. Un `.sh` con CRLF no se ejecuta.

## Cómo verificarlo (no a ojo)

Leer el archivo no basta; hay que preguntarle a Git:

```
git check-ignore -v <archivo>          # ¿lo ignoraría?, ¿por qué regla?
git check-attr -a -- <archivo>         # ¿qué atributos le aplican?
```

Comprobar **las dos direcciones**: que lo sensible quede ignorado, y que lo que sí debe
versionarse (el wrapper de Gradle, los esquemas de Room en `app/schemas/`) no se ignore
por accidente.

**Detalle que ya causó un error acá:** en `.gitignore` el `#` solo abre un comentario **al
inicio de la línea**. Escrito al final de un patrón queda como parte del patrón y la regla
deja de funcionar en silencio.

# Instrucción: recordar el respaldo de la base antes de instalar

Cada vez que se le pida a Sandy compilar o instalar la app en el celular
(`./gradlew :app:installDebug`, `connectedAndroidTest`, o cualquier cosa que reinstale),
**sugerir primero hacer el respaldo**, sin que tenga que acordarse ella:

```bash
herramientas/respaldo_bd.sh bajar
```

Y al final del mensaje, una mención corta —no un párrafo— de que existe
`herramientas/respaldo_bd.sh listar` para ver las copias guardadas, y que si algo salió
mal y la base quedó borrada, se recupera con
`herramientas/respaldo_bd.sh subir <carpeta>`.

El orden importa: el `bajar` va **antes**, junto al comando de instalar, porque después
de perder los datos ya no sirve. Lo de `listar` y `subir` va al final y breve, porque es
para el día que haga falta y repetirlo largo cada vez es ruido.

# Instrucción: los comandos van listos para copiar, sobre todo los del esquema

Cuando algo tenga que hacerlo Sandy en su equipo —porque acá no hay Android SDK y el
esquema de Room solo lo escribe el compilador—, **el mensaje tiene que traer el comando
completo, con la ruta entera, listo para pegar**. Nada de "agrega el JSON que se generó" ni
de rutas a medias.

El caso que se repite es el esquema de la base. Después de cada compilación en que sube la
versión de la base, va así, con el número real de la versión y no un `<n>`:

```bash
./gradlew :app:assembleDebug
git add app/schemas/com.sandyyera.reposteria.data.db.AppDatabase/10.json
git commit -m "Esquema de la versión 10 de la base"
git push
```

**Por qué esto y no una explicación:** esa ruta tiene el nombre completo del paquete y no se
escribe de memoria; el autocompletado del terminal la corta en pedazos, y el archivo aparece
entre los sin seguimiento, perdido en el ruido de `build/`. Ya pasó dos veces —el `6.json` se
perdió para siempre y el `8.json` estuvo meses en el disco sin subir— y las dos veces el
motivo fue el mismo: no estaba a mano el comando exacto.

**Nunca proponer `git add -f` para esto.** El `-f` pasa por encima de `.gitignore`, o sea de
la protección que existe para que no se suban credenciales ni datos reales del negocio. Los
esquemas en `app/schemas/` no están ignorados, así que un `git add` normal alcanza: si alguna
vez hiciera falta forzar, eso es señal de que hay una regla mal escrita que **hay que
arreglar**, no saltar.

Después de que Sandy diga que subió algo, **verificarlo de verdad**: `git pull`, mirar que el
archivo esté en `git ls-files`, y abrirlo para confirmar que dice la versión que corresponde.
Que el commit exista no prueba que adentro esté lo que se esperaba.

# Instrucción: registro y reutilización obligatoria de funciones y variables

Aplica a todo el código de este proyecto, en cualquier archivo y lenguaje (Kotlin, Gradle scripts, etc.). Objetivo: nunca reinventar algo que ya existe por falta de contexto.

## Regla (seguir en este orden, sin saltarse pasos)

**1. Registro único.**
Toda función y toda variable de módulo/clase (no variables locales triviales de un solo uso dentro de un bloque) debe quedar anotada en `registro_funciones.md`, en la raíz del repo. Formato exacto de cada entrada:

```
### nombreDeLaFuncionOVariable
- Ubicación: ruta/al/archivo.kt
- Qué hace: 1-2 líneas, en lenguaje simple.
- Cómo funciona: parámetros, qué retorna, efectos secundarios relevantes (ej. "escribe en Room", "llama a la red", "lanza excepción si X"). 2-4 líneas.
```

**2. Antes de escribir una función o variable nueva:**
- Revisar completo `registro_funciones.md` primero, entero, no solo buscar por palabra clave.
- Si alguna entrada suena remotamente relacionada por nombre, descripción o propósito: NO confiar solo en esa descripción. Abrir el archivo real donde vive esa función, leer el código tal cual está implementado, y recién ahí confirmar si de verdad sirve para el caso actual.
- Recién después de esa verificación, decidir: reutilizar tal cual, adaptar, o descartar.

**3. Si ninguna existente sirve de verdad:**
Crear la función o variable nueva. En el mismo cambio — no como pendiente, no "después" — agregar su entrada a `registro_funciones.md` con el formato exacto del punto 1.

**4. Prohibido:**
- Declarar código como "función/variable nueva" sin haber completado el punto 2 primero.
- Dejar una función o variable nueva sin su entrada correspondiente en el registro, aunque sea temporal.

## Nota de uso

Esta instrucción no se aplica sola entre sesiones de chat nuevas. Para que se cumpla:
- Si seguimos escribiendo código acá mismo, pega este archivo (o su contenido) al inicio de la conversación donde empecemos a codear.
- Si en algún momento usas Claude Code para este repo, guarda este contenido como `CLAUDE.md` en la raíz — se lee automáticamente en cada sesión, sin que tengas que pegarlo a mano.
