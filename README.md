# ReposteriaSandy

App Android para gestionar recetas de repostería: ingredientes y sus precios, costeo de
recetas, moldes y reescalado, precios y promociones, y cálculo de sueldos por venta.
Con respaldo en Google Drive.

## Empezar acá

- **[docs/entorno.md](docs/entorno.md)** — cómo dejar el equipo listo (Arch Linux), por
  niveles: primero solo Java para correr los tests, después el SDK, después el celular.
- **[arquitectura.md](arquitectura.md)** — el diseño completo: modelo de datos, fórmulas,
  reglas y el plan de 16 fases.
- **[registro_funciones.md](registro_funciones.md)** — qué hace cada función y dónde vive.
  Se revisa **antes** de escribir algo nuevo, para no reimplementar lo que ya existe.
- **[CLAUDE.md](CLAUDE.md)** — las reglas de trabajo del repositorio.

## Correr los tests

Solo necesitas Java 17 o superior. No hace falta Android Studio.

Las fórmulas y validaciones, sin nada de Android:

```bash
./gradlew :logica:test
```

Los repositorios y el ViewModel, con la base de datos reemplazada por una de mentira en
memoria. Tampoco necesita celular, pero sí el Android SDK:

```bash
./gradlew :app:test
```

Y para comprobar que la paleta cumple el contraste mínimo de la sección 12.6 (no necesita
nada instalado más que Python):

```bash
python3 herramientas/contraste.py
```

Y las revisiones del Kotlin que no necesitan compilador: símbolos sin cerrar, tipos usados
sin importar, y acciones de pantalla atadas a algo que recibe otro tipo. Sirve mientras el
`:app` no se pueda compilar; **pasar limpio no significa que compile**.

```bash
python3 herramientas/revisar_kotlin.py
```

La prueba de migración es la única que necesita un celular o emulador conectado. Corre la
migración de la base sobre SQLite de verdad, que es la única forma de saber que actualizar
la app no borra lo guardado:

```bash
./gradlew :app:connectedAndroidTest
```

## No perder los datos de prueba

Reinstalar la app borra su base. Para guardarla antes y devolverla después (necesita `adb`
y la app instalada en debug):

```bash
herramientas/respaldo_bd.sh bajar          # guarda una copia fechada
herramientas/respaldo_bd.sh listar         # muestra las copias
herramientas/respaldo_bd.sh subir <copia>  # la devuelve al celular
```

## Estructura

```
logica/         Kotlin puro: las fórmulas (costos, precios, moldes, sueldos, simulaciones)
                y las validaciones. Es un módulo aparte a propósito -- sin Android ni base
                de datos, así se prueba con JUnit sin emulador ni celular.

app/            La app Android: Room, pantallas con Compose y respaldo a Drive.

herramientas/   Scripts sueltos de apoyo, que no son parte de la app.
```

## Estado

| | |
|---|---|
| Lógica pura | implementada, 196 tests |
| Base de datos (Room) | 15 tablas, versión 2 con su migración probada sobre SQLite de verdad, DAOs y repositorios de ingredientes, recetas y moldes; 135 tests sobre una base de mentira en memoria |
| Pantallas | Menú de secciones, Ingredientes completo, Recetas con su paso de cantidades y costo en vivo, y el catálogo de Moldes; faltan precios, rendimiento y empleados |
| Respaldo en Drive | pendiente |
