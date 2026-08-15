#!/usr/bin/env python3
"""Revisiones del código Kotlin que no necesitan compilador ni Android SDK.

Existe porque el módulo `:app` solo se puede compilar en el equipo de Sandy, y hasta que
eso pasa los errores viajan a ciegas. Esto no reemplaza al compilador —no verifica tipos
de verdad— pero atrapa las cosas que sí se han colado hasta ahora:

1. Llaves o paréntesis sin cerrar.
2. Un identificador usado sin importar (incluidos los que declara un `typealias`).
3. Una acción de pantalla declarada con un tipo y atada a un método del ViewModel que
   recibe otro.
4. Lo mismo, pero cuando la acción se ata a un parámetro que la pantalla recibe de más
   arriba (`abrir = alAbrirReceta`), que es por donde pasó el `(Receta) -> Unit` que en
   realidad recibía un `Long`.
5. Una constante en MAYÚSCULAS escrita a secas que en realidad vive dentro de un
   `companion object`, y por eso solo falla en el archivo que la usa desde afuera.
6. Un nombre entre acentos graves (los de las pruebas) con un carácter que la JVM no
   admite — dos puntos, punto, barra…
7. Que esté versionado el esquema exportado de cada versión de la base de datos.
8. Una función suelta de `:logica` llamada como si fuera método (`items.filtrarPor(...)`).
9. Un método del DAO que su DAO falso no implementa (solo rompe al compilar las pruebas).

Se corre solo, sin instalar nada:

    python3 herramientas/revisar_kotlin.py

Termina con código 1 si encuentra algo. **Que pase no significa que compile**: significa
que no tiene ninguno de estos siete problemas. Solo mira nombres y tipos escritos tal
cual; nada que dependa de inferencia (el tipo de un `val` local, por ejemplo) está a su
alcance. La prueba real sigue siendo compilar.
"""

import glob
import os
import re
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Nombres que no hace falta importar: vienen de Kotlin o del propio lenguaje.
CONOCIDOS = {
    "String", "Int", "Long", "Double", "Boolean", "Float", "List", "Map", "Set", "Unit",
    "Any", "Exception", "IllegalArgumentException", "IllegalStateException", "Pair",
    "Triple", "Char", "StringBuilder", "Nothing", "NotImplementedError",
    # Las colecciones mutables son de Kotlin igual que List y Map, y se escriben como tipo
    # (`val x = mutableListOf<Long>()` no las nombra, pero `val x: MutableList<Long>` sí).
    "MutableList", "MutableMap", "MutableSet",
    "Array", "System", "Locale", "Regex", "Volatile", "Suppress", "OptIn", "Callback",
    "RoomDatabase", "Migration", "T",
}


def archivos_kotlin():
    patrones = ["app/src/**/*.kt", "logica/src/**/*.kt"]
    return sorted(r for p in patrones for r in glob.glob(os.path.join(RAIZ, p), recursive=True))


def sin_comentarios_ni_textos(codigo):
    """Deja el código sin comentarios, literales ni nombres entre acentos graves.

    Los nombres entre acentos graves son los de los tests (`fun \\`sin peso muestra No
    especificado\\`()`): adentro va prosa en español, no código, y si se deja pasar sus
    palabras con mayúscula aparecen como tipos sin importar.
    """
    codigo = re.sub(r'"""(?:.|\n)*?"""', '""', codigo)
    codigo = re.sub(r"/\*.*?\*/", "", codigo, flags=re.S)
    codigo = re.sub(r"//[^\n]*", "", codigo)
    codigo = re.sub(r"\\.", "", codigo)
    codigo = re.sub(r"`[^`\n]*`", "nombre", codigo)
    return re.sub(r'"(?:[^"\n])*"', '""', codigo)


def revisar_simbolos(rutas):
    """1. Llaves, paréntesis y corchetes balanceados."""
    pares = {"(": ")", "[": "]", "{": "}"}
    cierres = {v: k for k, v in pares.items()}
    problemas = 0
    for ruta in rutas:
        codigo = sin_comentarios_ni_textos(open(ruta, encoding="utf-8").read())
        pila = []
        for i, ch in enumerate(codigo):
            if ch in pares:
                pila.append((ch, i))
            elif ch in cierres:
                if not pila or pila[-1][0] != cierres[ch]:
                    linea = codigo[:i].count("\n") + 1
                    print(f"  sin abrir: {os.path.relpath(ruta, RAIZ)}:{linea} '{ch}'")
                    problemas += 1
                    break
                pila.pop()
        if pila:
            linea = codigo[: pila[-1][1]].count("\n") + 1
            print(f"  sin cerrar: {os.path.relpath(ruta, RAIZ)}:{linea} '{pila[-1][0]}'")
            problemas += 1
    return problemas


def revisar_importaciones(rutas):
    """2. Identificadores usados que no están importados ni definidos en su paquete."""
    por_paquete = {}
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        paquete = re.search(r"^package\s+([\w.]+)", texto, re.M).group(1)
        codigo = sin_comentarios_ni_textos(re.sub(r"^import .*$", "", texto, flags=re.M))
        nombres = set(re.findall(
            r"\b(?:class|interface|object|enum class|typealias)\s+([A-Z]\w*)", codigo
        ))
        nombres |= set(re.findall(r"\bfun\s+(?:<[^>]*>\s*)?([A-Z]\w*)\s*\(", codigo))
        nombres |= set(re.findall(r"\bval\s+([A-Z]\w*)\b", codigo))
        por_paquete.setdefault(paquete, set()).update(nombres)

    problemas = 0
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        paquete = re.search(r"^package\s+([\w.]+)", texto, re.M).group(1)
        importados = {i.split(".")[-1] for i in re.findall(r"^import\s+([\w.]+)", texto, re.M)}
        codigo = sin_comentarios_ni_textos(re.sub(r"^import .*$", "", texto, flags=re.M))
        propios = set(re.findall(
            r"\b(?:class|interface|object|enum class|typealias)\s+([A-Z]\w*)", codigo
        ))
        propios |= set(re.findall(r"\bfun\s+(?:<[^>]*>\s*)?([A-Z]\w*)\s*\(", codigo))
        propios |= set(re.findall(r"\bval\s+([A-Z]\w*)\b", codigo))
        # CamelCase usado como tipo o llamada, sin un punto delante.
        #
        # El `>` del final de la clase no es adorno: sin él, un tipo usado **solo** como
        # argumento genérico —`List<OpcionDeReparto>,` — no lo seguía ninguno de los otros
        # caracteres y quedaba invisible. Por ahí se coló `OpcionDeReparto` al bajarlo a
        # `:logica`: el ViewModel se llevó el import y la pantalla, que lo usaba justo así,
        # quedó sin él. Esta revisión existe para atrapar exactamente eso.
        usados = {
            u for u in re.findall(r"(?<![\w.])([A-Z][a-z][A-Za-z0-9]*)(?=\s*[.(<>,)\s:=])", codigo)
            if not u.isupper()
        }
        faltan = usados - importados - por_paquete.get(paquete, set()) - CONOCIDOS - propios
        if faltan:
            print(f"  ¿sin importar? {os.path.relpath(ruta, RAIZ)}: {', '.join(sorted(faltan))}")
            problemas += 1
    return problemas


def _funciones_de_nivel_superior(codigo):
    """Corta el archivo en funciones de nivel superior: [(nombre, cuerpo)].

    Solo las que empiezan al margen izquierdo, que son las que tienen su propio alcance de
    variables locales. Lo de adentro (lambdas, clases anidadas) queda dentro del cuerpo.
    """
    trozos = []
    for m in re.finditer(r"^(?:private\s+|internal\s+)?fun\s+(?:<[^>]*>\s*)?(\w+)", codigo, re.M):
        abre = codigo.find("{", m.end())
        if abre < 0:
            continue
        nivel, fin = 0, -1
        for i in range(abre, len(codigo)):
            if codigo[i] == "{":
                nivel += 1
            elif codigo[i] == "}":
                nivel -= 1
                if nivel == 0:
                    fin = i
                    break
        if fin > 0:
            trozos.append((m.group(1), codigo[m.start():fin]))
    return trozos


def revisar_alcance_de_locales(rutas):
    """11. Una variable local de una función usada dentro de **otra** función del mismo archivo.

    Es el error de mover un bloque de código de una función a otra y no llevarse lo que usaba.
    Pasó dos veces seguidas: `loQueVaAlAlmacen`, declarada en `NavegacionPrincipal` y usada en
    `MenuDeSecciones`, y `acciones`, parámetro de `PasoResumen` usado en
    `EncabezadoDelResumen`.

    Solo se miran nombres que en **alguna** función de ese archivo son local o parámetro: sin
    esa condición habría que resolver todos los imports y las herencias, y el resultado sería
    ruido. Con ella, el nombre existe seguro y lo único en duda es dónde.
    """
    problemas = 0
    for ruta in rutas:
        codigo = sin_comentarios_ni_textos(open(ruta, encoding="utf-8").read())
        funciones = _funciones_de_nivel_superior(codigo)
        if len(funciones) < 2:
            continue

        # Qué declara cada función: sus parámetros y sus `val`/`var` de adentro.
        #
        # **Una lista y no un diccionario por nombre**: dos funciones sobrecargadas comparten
        # nombre, y con un diccionario la segunda pisaba lo que declaraba la primera — que fue
        # exactamente el falso positivo que apareció al escribir esta revisión.
        declara = []
        for nombre, cuerpo in funciones:
            propios = set(re.findall(r"\b(?:val|var)\s+(\w+)", cuerpo))
            # Desestructuración: `val (uno, otro) = ...` no la ve el patrón de arriba.
            for grupo in re.findall(r"\b(?:val|var)\s*\(([\w,\s]+)\)", cuerpo):
                propios |= {t.strip() for t in grupo.split(",") if t.strip()}
            # **Todo lo que lleve dos puntos en el cuerpo**, y no solo la firma: eso cubre los
            # parámetros de las funciones anidadas, que son declaraciones tan locales como un
            # `val`. Sin esto, un `fun frase(medida: Double)` dentro de otra función daba un
            # falso positivo por `medida`.
            propios |= set(re.findall(r"(\w+)\s*:", cuerpo))
            # La variable de un `for (x in ...)`, que también es local y no lleva `val`.
            for grupo in re.findall(r"\bfor\s*\((.*?)\s+in\s", cuerpo):
                propios |= {t.strip(" ()") for t in grupo.split(",") if t.strip(" ()")}
            # Los de las lambdas (`{ fila ->`, `{ (a, b) ->`) y el `it` implícito.
            # Los parámetros de una lambda: `{ fila ->`, `{ a, b ->`, `{ (a, b) ->`.
            for grupo in re.findall(r"\{([^{}\n]*?)->", cuerpo):
                propios |= {t.strip(" ()") for t in grupo.split(",") if t.strip(" ()").isidentifier()}
            propios.add("it")
            declara.append(propios)

        # Un nombre es "local de alguien" si alguna función lo declara.
        deAlguien = set()
        for propios in declara:
            deAlguien |= propios

        for indice, (nombre, cuerpo) in enumerate(funciones):
            cuerpo_sin_firma = cuerpo[cuerpo.find("{"):] if "{" in cuerpo else cuerpo
            usados = set(re.findall(r"(?<![\w.])([a-z]\w*)\b", cuerpo_sin_firma))
            # Se descartan los nombres de argumento (`texto = ...`), que no son variables.
            argumentos = set(re.findall(r"(\w+)\s*=[^=]", cuerpo_sin_firma))
            faltan = (usados & deAlguien) - declara[indice] - argumentos - CONOCIDOS
            # Y lo que el archivo declara al margen (funciones, constantes) es de todos.
            faltan -= {f for f, _ in funciones}
            faltan -= set(re.findall(r"^(?:private\s+)?(?:val|const val)\s+(\w+)", codigo, re.M))
            if faltan:
                print(
                    f"  ¿fuera de alcance? {os.path.relpath(ruta, RAIZ)} "
                    f"en {nombre}(): {', '.join(sorted(faltan))}"
                )
                problemas += 1
    return problemas


def _cierre_de_parentesis(texto, desde):
    """Dónde termina la lista de parámetros que abre en [desde]. -1 si no cierra."""
    nivel = 0
    for i in range(desde, len(texto)):
        if texto[i] == "(":
            nivel += 1
        elif texto[i] == ")":
            nivel -= 1
            if nivel == 0:
                return i
    return -1


def revisar_daos_falsos(rutas):
    """10. Un método del DAO que el DAO falso no implementa.

    Solo revienta al compilar **las pruebas**, así que `installDebug` pasa y el error aparece
    recién en el `:app:test` siguiente — o sea en la máquina de Sandy y no acá. Pasó con
    `gastoDeVariasRecetas`, agregada al `RecetaDao` y olvidada en `RecetaDaoFalso`.

    Se cuentan solo los **abstractos**: un `@Transaction fun` con cuerpo no hay que
    implementarlo, y marcarlo daría un aviso por algo que compila.
    """
    interfaces, falsos = {}, {}
    for ruta in rutas:
        codigo = sin_comentarios_ni_textos(open(ruta, encoding="utf-8").read())
        for m in re.finditer(r"^interface\s+(\w*Dao)\b", codigo, re.M):
            cuerpo = codigo[m.end():]
            abstractos = set()
            for f in re.finditer(r"\bfun\s+(?:<[^>]*>\s*)?(\w+)\s*\(", cuerpo):
                cierre = _cierre_de_parentesis(cuerpo, f.end() - 1)
                if cierre < 0:
                    continue
                resto = cuerpo[cierre + 1:cierre + 200]
                # Después de los paréntesis puede venir `: Tipo` y después `{` (concreta),
                # `=` (concreta) o un salto a la siguiente declaración (abstracta).
                sin_tipo = re.sub(r"^\s*:[^{=\n]*", "", resto)
                if not sin_tipo.lstrip().startswith(("{", "=")):
                    abstractos.add(f.group(1))
            interfaces[m.group(1)] = abstractos
        for m in re.finditer(r"^class\s+(\w*DaoFalso)\b[^{]*?:\s*(\w*Dao)\b", codigo, re.M | re.S):
            cuerpo = codigo[m.end():]
            falsos[m.group(1)] = (
                m.group(2),
                set(re.findall(r"\boverride\s+(?:suspend\s+)?fun\s+(\w+)\s*\(", cuerpo))
            )

    problemas = 0
    for nombre, (interfaz, implementados) in sorted(falsos.items()):
        faltan = interfaces.get(interfaz, set()) - implementados
        if faltan:
            print(f"  {nombre} no implementa de {interfaz}: {', '.join(sorted(faltan))}")
            problemas += 1
    return problemas


def revisar_llamadas_con_punto(rutas):
    """9. Una función suelta de `:logica` llamada como si fuera método (`x.f()`).

    `filtrarPor(items, busqueda) { ... }` recibe la lista **por parámetro**; escrito
    `items.filtrarPor(busqueda) { ... }` no compila, y la revisión 2 no lo ve porque el nombre
    sí está importado. Se coló así en el cuadro de descontar por recetas.

    Para no inventar falsos positivos solo se miran los nombres que en `:logica` están
    declarados **al margen izquierdo y sin receptor**, y se descarta cualquiera que además
    exista como método o como extensión en algún lado: ahí el punto puede ser correcto.
    """
    sueltas, con_punto = set(), set()
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        codigo = sin_comentarios_ni_textos(texto)
        # Declaradas al margen: `fun nombre(` o `fun <T> nombre(`, sin nada antes en la línea.
        for m in re.finditer(r"^fun\s+(?:<[^>]*>\s*)?(\w+)\s*\(", codigo, re.M):
            if "/logica/" in ruta.replace(os.sep, "/"):
                sueltas.add(m.group(1))
        # Métodos (con sangría) y extensiones (`fun Algo.nombre(`): ahí el punto es correcto.
        #
        # **`[ \t]` y no `\s`**: `\s` incluye el salto de línea, así que con `re.M` el `^`
        # calzaba en una línea en blanco y los saltos hacían de "sangría" — o sea que *toda*
        # función suelta precedida de una línea vacía parecía un método, y la revisión no
        # marcaba nunca nada. Se pilló comprobando que atrapara el error que la motivó.
        con_punto |= set(re.findall(r"^[ \t]+fun\s+(?:<[^>]*>\s*)?(\w+)\s*\(", codigo, re.M))
        con_punto |= set(re.findall(r"\bfun\s+(?:<[^>]*>\s*)?[\w.<>]+\.(\w+)\s*\(", codigo))

    candidatas = sueltas - con_punto
    if not candidatas:
        return 0

    problemas = 0
    for ruta in rutas:
        codigo = sin_comentarios_ni_textos(open(ruta, encoding="utf-8").read())
        # Se borra el paquete escrito completo antes de buscar: en
        # `com.sandyyera.reposteria.logica.validaciones.textoANumero(x)` el punto es parte de
        # la ruta y no una llamada a método. Sin esto, escribir el nombre calificado —que es
        # lo correcto cuando dos paquetes traen el mismo nombre— se marcaba como error.
        codigo = re.sub(r"\bcom(?:\.\w+)+\.", "", codigo)
        malas = {n for n in candidatas if re.search(r"\.\s*" + n + r"\s*[({]", codigo)}
        if malas:
            print(f"  ¿llamada con punto? {os.path.relpath(ruta, RAIZ)}: {', '.join(sorted(malas))}")
            problemas += 1
    return problemas


def tipos_de_parametros(firma):
    """De 'a: Long, b: Receta' saca ['Long', 'Receta'], respetando los genéricos."""
    partes, nivel, actual = [], 0, ""
    for ch in firma.strip():
        if ch in "(<":
            nivel += 1
        elif ch in ")>":
            nivel -= 1
        if ch == "," and nivel == 0:
            partes.append(actual)
            actual = ""
        else:
            actual += ch
    if actual.strip():
        partes.append(actual)
    return [p.split(":", 1)[1].strip() if ":" in p else p.strip() for p in partes]


def revisar_acciones(rutas):
    """3. Cada acción de pantalla, contra el método del ViewModel al que se ata.

    Es el patrón de este proyecto: las pantallas reciben un `Acciones*` con una función por
    cada cosa que se puede hacer, y se rellena con referencias `modelo::metodo`. Si los
    tipos no calzan, el compilador lo diría — pero acá no hay compilador.

    Un método puede existir en varios ViewModel (`pedirBorrado` está en ingredientes y en
    recetas), así que solo se avisa cuando **ninguno** calza.
    """
    metodos = {}
    for ruta in glob.glob(os.path.join(RAIZ, "app/src/main/**/*ViewModel.kt"), recursive=True):
        texto = open(ruta, encoding="utf-8").read()
        for m in re.finditer(r"^    fun (\w+)\(([^)]*)\)", texto, re.M):
            metodos.setdefault(m.group(1), []).append(tipos_de_parametros(m.group(2)))

    problemas = 0
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        bloque = re.search(r"data class (Acciones\w+)\(((?:.|\n)*?)\n\)", texto)
        if not bloque:
            continue
        declarados = {
            d.group(1): tipos_de_parametros(d.group(2))
            for d in re.finditer(r"val (\w+): \(([^)]*)\) -> Unit", bloque.group(2))
        }
        for a in re.finditer(r"(\w+) = modelo::(\w+)", texto):
            accion, metodo = a.group(1), a.group(2)
            if accion not in declarados or metodo not in metodos:
                continue
            if declarados[accion] not in metodos[metodo]:
                esperado = ", ".join(declarados[accion])
                reales = " o ".join("(" + ", ".join(f) + ")" for f in metodos[metodo])
                print(
                    f"  desajuste: {bloque.group(1)}.{accion} declara ({esperado}) "
                    f"pero {metodo} recibe {reales}"
                )
                problemas += 1
    return problemas


def revisar_enchufes(rutas):
    """4. Cada acción atada a un parámetro de la pantalla, contra el tipo de ese parámetro.

    La otra mitad del cableado: además de `modelo::metodo`, un `Acciones*` se rellena con
    parámetros que la pantalla recibe de más arriba (`abrir = alAbrirReceta`). Si la
    pantalla declara `(Receta) -> Unit` y la acción `(Long) -> Unit`, esto lo dice.
    """
    problemas = 0
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        bloque = re.search(r"data class (Acciones\w+)\(((?:.|\n)*?)\n\)", texto)
        if not bloque:
            continue
        declarados = {
            d.group(1): tipos_de_parametros(d.group(2))
            for d in re.finditer(r"val (\w+): \(([^)]*)\) -> Unit", bloque.group(2))
        }
        # Parámetros de función que reciben las pantallas de este archivo.
        parametros = {
            p.group(1): tipos_de_parametros(p.group(2))
            for p in re.finditer(r"^\s{4}(\w+): \(([^)]*)\) -> Unit", texto, re.M)
        }
        for a in re.finditer(r"(\w+) = (\w+)(?=[,\n)])", texto):
            accion, origen = a.group(1), a.group(2)
            if accion not in declarados or origen not in parametros:
                continue
            if declarados[accion] != parametros[origen]:
                print(
                    f"  desajuste: {bloque.group(1)}.{accion} declara "
                    f"({', '.join(declarados[accion])}) pero {origen} es "
                    f"({', '.join(parametros[origen])})"
                )
                problemas += 1
    return problemas


# Caracteres que la JVM no admite dentro del nombre de un método, ni siquiera entre
# acentos graves. Kotlin deja escribirlos y falla recién al compilar.
PROHIBIDOS_EN_NOMBRES = set(".;[]/<>:\\")


def revisar_nombres_con_acentos(rutas):
    """7. Nombres entre acentos graves con caracteres que la JVM no admite.

    Los nombres de las pruebas se escriben como frases (`fun \\`quitar un ingrediente baja el
    costo\\`()`), y ahí es natural poner dos puntos para separar. La JVM no los acepta —ni
    tampoco `.`, `;`, `[`, `]`, `/`, `<`, `>`, `\\`— y Kotlin no lo dice hasta compilar, con
    un mensaje que nombra el carácter pero no explica por qué molesta.

    Pasó con `fun \\`una tarde completa: ingredientes, receta...\\``, y costó una compilación
    entera de `:app` para enterarse.
    """
    problemas = 0
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        # Solo declaraciones. Los acentos graves de la documentación —`logica/`,
        # `Map<String, Int>`— son texto para leer, no nombres, y mirarlos daba cuarenta
        # avisos falsos por cada uno de verdad.
        for m in re.finditer(r"\b(fun|class|object|val|var)\s+`([^`\n]*)`", texto):
            malos = sorted(set(m.group(2)) & PROHIBIDOS_EN_NOMBRES)
            if not malos:
                continue
            linea = texto[: m.start()].count("\n") + 1
            print(f"  {os.path.relpath(ruta, RAIZ)}:{linea} el nombre de {m.group(1)} lleva "
                  f"{' '.join(repr(c) for c in malos)}, que la JVM no admite")
            problemas += 1
    return problemas


def revisar_esquemas(_rutas):
    """5. Que exista el esquema exportado de cada versión de la base.

    No es Kotlin balanceado ni tipos, pero es lo mismo: algo que se detecta leyendo y que
    en el celular se paga caro. `app/schemas/N.json` es lo que le permite a Room y a la
    prueba de migración saber cómo era la base antes. Si falta el de la versión actual, la
    migración siguiente no se puede escribir ni probar, y eso solo se descubre el día que
    hay datos reales adentro.

    Room lo genera al compilar; lo que se olvida es versionarlo.

    **Distingue el de la versión actual de los intermedios, y esa diferencia se pagó una
    vez.** Room exporta **solo el esquema de la versión actual**. Si la base sube dos
    versiones entre dos compilaciones —pasó con la 5 → 6 → 7—, la del medio nunca llega a
    compilarse sola y su JSON **no existe ni va a existir**: no es algo que se pueda
    recuperar volviendo a compilar. Se pierde con él la posibilidad de probar ese salto por
    separado, que es lo que dice cuál de las dos migraciones rompió algo cuando algo se rompe.

    Por eso el que falta de verdad —el actual— es un error, y un intermedio es un aviso con
    su explicación. Marcarlo como error dejaría la revisión en rojo para siempre por algo que
    ya no tiene arreglo, y eso enseña a ignorarla.
    """
    fuente = os.path.join(RAIZ, "app/src/main/java/com/sandyyera/reposteria/data/db/AppDatabase.kt")
    if not os.path.exists(fuente):
        return 0
    version = re.search(r"version\s*=\s*(\d+)", open(fuente, encoding="utf-8").read())
    if not version:
        print("  no encuentro la versión declarada en AppDatabase.kt")
        return 1

    version = int(version.group(1))
    carpeta = os.path.join(RAIZ, "app/schemas/com.sandyyera.reposteria.data.db.AppDatabase")

    def hay(n):
        return os.path.exists(os.path.join(carpeta, f"{n}.json"))

    intermedios = [n for n in range(1, version) if not hay(n)]
    if intermedios:
        print(f"  aviso: no está el esquema de {', '.join(f'{n}.json' for n in intermedios)}")
        print("  Room exporta solo el de la versión actual, así que una versión que no se")
        print("  compiló sola no tiene JSON y no se puede recuperar. Ese salto solo se puede")
        print("  probar encadenado. Para que no vuelva a pasar: no subir dos versiones de la")
        print("  base entre dos compilaciones.")

    if not hay(version):
        print(f"  la base está en la versión {version} y falta su esquema: {version}.json")
        print("  se genera al compilar (./gradlew :app:assembleDebug) y hay que versionarlo")
        return 1
    return 0


def revisar_constantes(rutas):
    """6. Una constante en MAYÚSCULAS usada sin calificar, que en realidad vive dentro de un
    `companion object`.

    Esto pasó de verdad: `MIGRACION_1_2` está dentro del `companion object` de
    `AppDatabase`, no suelta en el archivo. Escrita a secas resuelve dentro de la propia
    clase y en ninguna otra parte, así que el error solo aparece en el archivo que la usa
    desde afuera — en este caso la prueba de migración, que además solo se compila al
    correrla con un celular conectado. Tres minutos de build para enterarse.

    La revisión 2 no la ve porque solo mira nombres en CamelCase.
    """
    top, dentro_de_clase, paquetes_de = {}, {}, {}
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        paquete = re.search(r"^package\s+([\w.]+)", texto, re.M).group(1)
        # Sin sangría = suelta en el archivo; con sangría = dentro de algo.
        for m in re.finditer(r"^(\s*)(?:const\s+)?val\s+([A-Z][A-Z0-9_]{2,})\b", texto, re.M):
            destino = top if not m.group(1) else dentro_de_clase
            destino.setdefault(paquete, {})[m.group(2)] = os.path.relpath(ruta, RAIZ)
            if not m.group(1):
                paquetes_de.setdefault(m.group(2), set()).add(paquete)

    problemas = 0
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        paquete = re.search(r"^package\s+([\w.]+)", texto, re.M).group(1)
        importados = {i.split(".")[-1] for i in re.findall(r"^import\s+([\w.]+)", texto, re.M)}
        codigo = sin_comentarios_ni_textos(re.sub(r"^import .*$", "", texto, flags=re.M))
        # Declaradas acá mismo (a cualquier nivel): siempre resuelven.
        propias = set(re.findall(r"(?:const\s+)?val\s+([A-Z][A-Z0-9_]{2,})\b", codigo))

        # Un import que apunta al paquete equivocado. El compilador lo llama "unresolved
        # reference" y señala el uso, no el import, así que cuesta ver que el nombre sí
        # existe y lo que está mal es de dónde se lo pidió. Pasó con
        # AVISO_DURACIONES_ESTIMADAS, declarada en `validaciones` e importada de `duracion`.
        for camino in re.findall(r"^import\s+([\w.]+)", texto, re.M):
            paquete_pedido, _, nombre = camino.rpartition(".")
            donde = paquetes_de.get(nombre)
            if donde and paquete_pedido not in donde:
                print(f"  {os.path.relpath(ruta, RAIZ)}: importa '{nombre}' de "
                      f"'{paquete_pedido}', pero está en {' o '.join(sorted(donde))}")
                problemas += 1

        for uso in set(re.findall(r"(?<![\w.])([A-Z][A-Z0-9_]{2,})\b", codigo)):
            if uso in propias or uso in importados or uso in top.get(paquete, {}):
                continue
            donde = dentro_de_clase.get(paquete, {}).get(uso)
            if donde:
                print(f"  {os.path.relpath(ruta, RAIZ)}: '{uso}' no está suelta en su "
                      f"archivo sino dentro de una clase ({donde}); hay que calificarla")
                problemas += 1
    return problemas


def revisar_aserciones(rutas):
    """8. Una aserción de JUnit usada sin su `import org.junit.Assert.assertX`.

    La revisión 2 no la ve **y no es un descuido de aquella**: mira nombres en CamelCase,
    porque así se llaman los tipos, y las aserciones empiezan en minúscula. El agujero se
    pagó con `assertNull` en `DuracionViewModelTest`, que además no se notaba al instalar la
    app: `installDebug` no compila las pruebas, así que el error esperó hasta el siguiente
    `:app:test` — dos minutos de build para enterarse de un import.

    Se limita a `org.junit.Assert` a propósito. Cualquier función suelta usada sin importar
    sería lo mismo en general, pero eso no se puede saber sin resolver el paquete de cada
    llamada; acá el conjunto es cerrado y conocido, así que no hay falsos positivos.
    """
    problemas = 0
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        # Con el import de estrella o de la clase entera, se califican de otra forma.
        if re.search(r"^import\s+org\.junit\.Assert(\.\*)?$", texto, re.M):
            continue
        importadas = set(re.findall(r"^import\s+org\.junit\.Assert\.(\w+)", texto, re.M))
        if not importadas:
            continue
        codigo = sin_comentarios_ni_textos(texto)
        for uso in sorted(set(re.findall(r"(?<![\w.])(assert\w+)\s*\(", codigo)) - importadas):
            print(f"  {os.path.relpath(ruta, RAIZ)}: usa '{uso}' sin importarla de "
                  f"org.junit.Assert")
            problemas += 1
    return problemas


# Los parámetros que **de verdad** tienen `OutlinedTextField` y `TextField` de Material3.
# La lista sale del propio error del compilador, que los enumera enteros al no encontrar
# candidato. Es un conjunto cerrado y conocido, como el de `org.junit.Assert`.
PARAMETROS_DE_CAMPO_DE_TEXTO = {
    "value", "onValueChange", "modifier", "enabled", "readOnly", "textStyle", "label",
    "placeholder", "leadingIcon", "trailingIcon", "prefix", "suffix", "supportingText",
    "isError", "visualTransformation", "keyboardOptions", "keyboardActions", "singleLine",
    "maxLines", "minLines", "interactionSource", "shape", "colors",
}


def revisar_campos_de_texto(rutas):
    """12. Un parámetro que `OutlinedTextField` o `TextField` de Material3 no tienen.

    Salió de `onTextLayout`, que existe en `BasicTextField` y **no** en los de Material3.
    Se escribió de memoria, pasó las once revisiones anteriores y reventó la compilación de
    Sandy con siete errores: el primero por el parámetro y seis más en cascada, porque una
    llamada que no resuelve deja sin tipo a todos los `it` de sus lambdas.

    Es el mismo caso que las aserciones de JUnit: **no se puede revisar cualquier llamada**
    sin resolver los tipos, pero acá el conjunto de parámetros es cerrado y está en la
    documentación. Estos dos campos se usan en toda la app, así que el error se paga caro y
    la revisión es barata.
    """
    problemas = 0
    for ruta in rutas:
        codigo = sin_comentarios_ni_textos(open(ruta, encoding="utf-8").read())
        for llamada in re.finditer(r"(?<![\w.])(OutlinedTextField|TextField)\s*\(", codigo):
            nombre = llamada.group(1)
            inicio = llamada.end()
            profundidad, i = 1, inicio
            while profundidad and i < len(codigo):
                if codigo[i] in "([{":
                    profundidad += 1
                elif codigo[i] in ")]}":
                    profundidad -= 1
                i += 1
            cuerpo = codigo[inicio:i - 1]
            # Solo los del primer nivel: los de adentro son de otras funciones.
            nivel = 0
            for linea in cuerpo.split("\n"):
                encontrado = re.match(r"\s*([a-zA-Z]\w*)\s*=(?!=)", linea)
                if nivel == 0 and encontrado:
                    param = encontrado.group(1)
                    if param not in PARAMETROS_DE_CAMPO_DE_TEXTO:
                        print(f"  {os.path.relpath(ruta, RAIZ)}: {nombre} no tiene el "
                              f"parámetro '{param}'")
                        problemas += 1
                nivel += sum(linea.count(c) for c in "([{") - sum(linea.count(c) for c in ")]}")
    return problemas


def revisar_propiedades_de_constructor(rutas):
    """13. Una propiedad del constructor de una clase usada **fuera** de esa clase.

    Es el error de pegar un método en el lugar equivocado del archivo. Pasó dos veces seguidas
    con el mismo movimiento: agregar una función "al final" y que el final resulte ser el de
    **otra** declaración —un `sealed interface` que venía después—, no el de la clase. El código
    queda impecable de leer y no compila, porque ahí adentro `dao` no existe.

    Es la hermana de la revisión 11: aquella mira variables locales entre funciones, y esta mira
    las propiedades del constructor entre clases. Las dos parten del mismo hecho — el nombre
    existe en el archivo, y lo único en duda es si existe **ahí**.

    Solo mira clases de nivel superior con paréntesis, que son las que tienen constructor, y solo
    nombres que no vuelvan a declararse afuera: sin esa condición, un parámetro de otra función
    llamado igual daría un aviso falso.
    """
    problemas = 0
    for ruta in rutas:
        lineas = sin_comentarios_ni_textos(open(ruta, encoding="utf-8").read()).split("\n")
        for i, linea in enumerate(lineas):
            encabezado = re.match(r"^(?:internal |private )?(?:abstract )?class (\w+)\(", linea)
            if not encabezado:
                continue
            # El constructor termina en la línea que cierra su paréntesis.
            profundidad, fin_ctor = 0, i
            for n in range(i, len(lineas)):
                profundidad += lineas[n].count("(") - lineas[n].count(")")
                if profundidad <= 0 and n > i or (profundidad == 0 and n == i):
                    fin_ctor = n
                    break
            propiedades = set()
            for n in range(i, fin_ctor + 1):
                for prop in re.findall(r"(?:private |internal )?va[lr] (\w+)\s*:", lineas[n]):
                    propiedades.add(prop)
            if not propiedades:
                continue
            # El cuerpo de la clase llega hasta la primera línea que sea "}" al margen.
            fin_clase = len(lineas)
            for n in range(fin_ctor + 1, len(lineas)):
                if lineas[n] == "}":
                    fin_clase = n
                    break
            afuera = lineas[:i] + lineas[fin_clase + 1:]
            texto_afuera = "\n".join(afuera)
            for prop in sorted(propiedades):
                # Si se vuelve a declarar afuera, el nombre existe ahí por su cuenta.
                # Cuenta como declarado afuera un `val`/`var` (con tipo o sin él), un parámetro
                # y una variable de lambda. Sin las tres formas, un nombre corriente como
                # `recetas` daba aviso por existir en cualquier otra función del archivo.
                declarado = (
                    re.search(rf"(?<![\w.])va[lr]\s+{prop}\b", texto_afuera)
                    or re.search(rf"[(,]\s*{prop}\s*:", texto_afuera)
                    or re.search(rf"(?<![\w.]){prop}\s*->", texto_afuera)
                    or re.search(rf"(?<![\w.]){prop}\s*=\s*\w", texto_afuera)
                    # `vararg nombre:` y `{ nombre, otro ->` (lambda de varios parámetros).
                    or re.search(rf"vararg\s+{prop}\b", texto_afuera)
                    or re.search(rf"(?<![\w.]){prop}\s*,[^)\n]*->", texto_afuera)
                )
                if declarado:
                    continue
                if re.search(rf"(?<![\w.]){prop}\.", texto_afuera):
                    print(f"  {os.path.relpath(ruta, RAIZ)}: '{prop}' es del constructor de "
                          f"{encabezado.group(1)} y se usa fuera de esa clase")
                    problemas += 1
    return problemas


def main():
    rutas = archivos_kotlin()
    print(f"Revisando {len(rutas)} archivos Kotlin.\n")

    total = 0
    for titulo, revision in [
        ("Símbolos balanceados", revisar_simbolos),
        ("Importaciones", revisar_importaciones),
        ("Acciones contra ViewModel", revisar_acciones),
        ("Acciones contra la pantalla", revisar_enchufes),
        ("Constantes calificadas", revisar_constantes),
        ("Nombres entre acentos graves", revisar_nombres_con_acentos),
        ("Esquemas de Room", revisar_esquemas),
        ("Aserciones de JUnit", revisar_aserciones),
        ("Llamadas con punto", revisar_llamadas_con_punto),
        ("DAO falsos completos", revisar_daos_falsos),
        ("Alcance de locales", revisar_alcance_de_locales),
        ("Campos de texto de Material3", revisar_campos_de_texto),
        ("Propiedades del constructor", revisar_propiedades_de_constructor),
    ]:
        encontrados = revision(rutas)
        estado = "ok" if encontrados == 0 else f"{encontrados} problema(s)"
        print(f"  {titulo:32} {estado}")
        total += encontrados

    print()
    if total:
        print(f"{total} problema(s). Que compile en el celular sigue siendo la prueba real.")
    else:
        print("Sin problemas de los que esto sabe buscar. NO garantiza que compile.")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
