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
