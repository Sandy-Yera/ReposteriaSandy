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
| Lógica pura | implementada, 140 tests |
| Base de datos (Room) | 15 tablas, DAOs y primeros repositorios; 43 tests sobre una base de mentira en memoria |
| Pantallas | Ingredientes lista (alta, edición, búsqueda, borrado con advertencia y calculadora de valor por gramo); el resto pendiente |
| Respaldo en Drive | pendiente |
