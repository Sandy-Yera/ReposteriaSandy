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

```bash
./gradlew :logica:test
```

## Estructura

```
logica/   Kotlin puro: las fórmulas (costos, precios, moldes, sueldos, simulaciones).
          Es un módulo aparte a propósito -- sin Android ni base de datos, así se
          prueba con JUnit sin emulador ni celular.

app/      La app Android: Room, pantallas con Compose y respaldo a Drive.
          (se agrega en el siguiente paso del plan)
```

## Estado

| | |
|---|---|
| Lógica pura | implementada, 69 tests |
| Base de datos (Room) | entidades, DAOs y primeros repositorios |
| Pantallas | pendiente |
| Respaldo en Drive | pendiente |
