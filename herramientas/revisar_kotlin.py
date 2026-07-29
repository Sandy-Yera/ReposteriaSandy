#!/usr/bin/env python3
"""Revisiones del código Kotlin que no necesitan compilador ni Android SDK.

Existe porque el módulo `:app` solo se puede compilar en el equipo de Sandy, y hasta que
eso pasa los errores viajan a ciegas. Esto no reemplaza al compilador —no verifica tipos
de verdad— pero atrapa las cosas que sí se han colado hasta ahora:

1. Llaves o paréntesis sin cerrar.
2. Un identificador usado sin importar.
3. Una acción de pantalla declarada con un tipo y atada a un método del ViewModel que
   recibe otro.
4. Lo mismo, pero cuando la acción se ata a un parámetro que la pantalla recibe de más
   arriba (`abrir = alAbrirReceta`), que es por donde pasó el `(Receta) -> Unit` que en
   realidad recibía un `Long`.
5. Una constante en MAYÚSCULAS escrita a secas que en realidad vive dentro de un
   `companion object`, y por eso solo falla en el archivo que la usa desde afuera.
6. Que esté versionado el esquema exportado de cada versión de la base de datos.

Se corre solo, sin instalar nada:

    python3 herramientas/revisar_kotlin.py

Termina con código 1 si encuentra algo. **Que pase no significa que compile**: significa
que no tiene ninguno de estos seis problemas. Solo mira nombres y tipos escritos tal
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
        nombres = set(re.findall(r"\b(?:class|interface|object|enum class)\s+([A-Z]\w*)", codigo))
        nombres |= set(re.findall(r"\bfun\s+(?:<[^>]*>\s*)?([A-Z]\w*)\s*\(", codigo))
        nombres |= set(re.findall(r"\bval\s+([A-Z]\w*)\b", codigo))
        por_paquete.setdefault(paquete, set()).update(nombres)

    problemas = 0
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        paquete = re.search(r"^package\s+([\w.]+)", texto, re.M).group(1)
        importados = {i.split(".")[-1] for i in re.findall(r"^import\s+([\w.]+)", texto, re.M)}
        codigo = sin_comentarios_ni_textos(re.sub(r"^import .*$", "", texto, flags=re.M))
        propios = set(re.findall(r"\b(?:class|interface|object|enum class)\s+([A-Z]\w*)", codigo))
        propios |= set(re.findall(r"\bfun\s+(?:<[^>]*>\s*)?([A-Z]\w*)\s*\(", codigo))
        propios |= set(re.findall(r"\bval\s+([A-Z]\w*)\b", codigo))
        # CamelCase usado como tipo o llamada, sin un punto delante
        usados = {
            u for u in re.findall(r"(?<![\w.])([A-Z][a-z][A-Za-z0-9]*)(?=\s*[.(<,)\s:=])", codigo)
            if not u.isupper()
        }
        faltan = usados - importados - por_paquete.get(paquete, set()) - CONOCIDOS - propios
        if faltan:
            print(f"  ¿sin importar? {os.path.relpath(ruta, RAIZ)}: {', '.join(sorted(faltan))}")
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


def revisar_esquemas(_rutas):
    """5. Que exista el esquema exportado de cada versión de la base.

    No es Kotlin balanceado ni tipos, pero es lo mismo: algo que se detecta leyendo y que
    en el celular se paga caro. `app/schemas/N.json` es lo que le permite a Room y a la
    prueba de migración saber cómo era la base antes. Si falta el de la versión actual, la
    migración siguiente no se puede escribir ni probar, y eso solo se descubre el día que
    hay datos reales adentro.

    Room lo genera al compilar; lo que se olvida es versionarlo.
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
    faltan = [n for n in range(1, version + 1)
              if not os.path.exists(os.path.join(carpeta, f"{n}.json"))]
    if faltan:
        print(f"  la base está en la versión {version} y falta el esquema de: "
              f"{', '.join(f'{n}.json' for n in faltan)}")
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
    top, dentro_de_clase = {}, {}
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        paquete = re.search(r"^package\s+([\w.]+)", texto, re.M).group(1)
        # Sin sangría = suelta en el archivo; con sangría = dentro de algo.
        for m in re.finditer(r"^(\s*)(?:const\s+)?val\s+([A-Z][A-Z0-9_]{2,})\b", texto, re.M):
            destino = top if not m.group(1) else dentro_de_clase
            destino.setdefault(paquete, {})[m.group(2)] = os.path.relpath(ruta, RAIZ)

    problemas = 0
    for ruta in rutas:
        texto = open(ruta, encoding="utf-8").read()
        paquete = re.search(r"^package\s+([\w.]+)", texto, re.M).group(1)
        importados = {i.split(".")[-1] for i in re.findall(r"^import\s+([\w.]+)", texto, re.M)}
        codigo = sin_comentarios_ni_textos(re.sub(r"^import .*$", "", texto, flags=re.M))
        # Declaradas acá mismo (a cualquier nivel): siempre resuelven.
        propias = set(re.findall(r"(?:const\s+)?val\s+([A-Z][A-Z0-9_]{2,})\b", codigo))

        for uso in set(re.findall(r"(?<![\w.])([A-Z][A-Z0-9_]{2,})\b", codigo)):
            if uso in propias or uso in importados or uso in top.get(paquete, {}):
                continue
            donde = dentro_de_clase.get(paquete, {}).get(uso)
            if donde:
                print(f"  {os.path.relpath(ruta, RAIZ)}: '{uso}' no está suelta en su "
                      f"archivo sino dentro de una clase ({donde}); hay que calificarla")
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
        ("Esquemas de Room", revisar_esquemas),
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
