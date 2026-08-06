#!/usr/bin/env bash
#
# Baja y sube el archivo de la base de datos del celular, para no perder los datos de
# prueba al reinstalar la app.
#
# Room no borra nada por su cuenta: no se usa `fallbackToDestructiveMigration()` en ningún
# lado, así que una migración rota **detiene la app** en vez de vaciar la base en silencio.
# Los datos se pierden por lo otro: desinstalar. Y desinstalar es justo lo que uno termina
# haciendo cuando la app no arranca. Este script existe para que esa salida no cueste nada.
#
#   herramientas/respaldo_bd.sh bajar              guarda una copia con la fecha en el nombre
#   herramientas/respaldo_bd.sh subir <archivo>    devuelve una copia al celular
#   herramientas/respaldo_bd.sh listar             muestra las copias guardadas
#
# Requisitos: `adb` instalado, depuración USB activada y **la app instalada en versión
# debug**. `run-as` solo funciona con apps depurables; con un APK de release firmado,
# Android no deja entrar a su carpeta privada ni con el celular desbloqueado.
#
# Ver docs/entorno.md para dejar `adb` funcionando en Arch.

set -euo pipefail

PAQUETE="com.sandyyera.reposteria"
BASE="reposteria.db"
DESTINO="${REPOSTERIA_RESPALDOS:-$HOME/respaldos-reposteria}"

# La base viaja en tres archivos por el modo WAL: si se copia solo el .db, lo escrito más
# recientemente se queda en el -wal y la copia queda vieja sin que nada lo avise.
ARCHIVOS=("$BASE" "$BASE-wal" "$BASE-shm")

fallar() {
    echo "Error: $*" >&2
    exit 1
}

comprobar_entorno() {
    command -v adb >/dev/null 2>&1 || fallar "no encuentro 'adb'. Ver docs/entorno.md."

    local conectados
    # Sin tabulador literal en el patrón: un editor que lo convierta en espacios rompería
    # la cuenta en silencio y el script diría que no hay celular conectado.
    conectados=$(adb devices | grep -cE '^[^[:space:]]+[[:space:]]+device$' || true)
    [ "$conectados" -eq 0 ] && fallar "no hay ningún celular conectado (revisa 'adb devices')."
    [ "$conectados" -gt 1 ] && fallar "hay más de un dispositivo conectado; desconecta los demás."

    adb shell "run-as $PAQUETE true" >/dev/null 2>&1 ||
        fallar "'run-as' no funciona. ¿La app instalada es la de debug (./gradlew :app:installDebug)?"
}

bajar() {
    comprobar_entorno
    local carpeta="$DESTINO/$(date +%Y-%m-%d_%H%M%S)"
    mkdir -p "$carpeta"

    local bajados=0
    for archivo in "${ARCHIVOS[@]}"; do
        # No se usa 'adb pull' directo: la carpeta databases/ es privada de la app y adb no
        # entra. Se saca por run-as y se recibe por la salida estándar.
        if adb shell "run-as $PAQUETE test -f databases/$archivo" 2>/dev/null; then
            adb exec-out "run-as $PAQUETE cat databases/$archivo" > "$carpeta/$archivo"
            echo "  bajado: $archivo ($(du -h "$carpeta/$archivo" | cut -f1))"
            bajados=$((bajados + 1))
        fi
    done

    [ "$bajados" -eq 0 ] && { rmdir "$carpeta"; fallar "no hay ninguna base en el celular todavía."; }
    echo "Copia guardada en $carpeta"
}

subir() {
    local carpeta="${1:-}"
    [ -z "$carpeta" ] && fallar "falta decir qué copia subir. Usa 'listar' para verlas."
    [ -d "$carpeta" ] || fallar "no existe la carpeta '$carpeta'."
    [ -f "$carpeta/$BASE" ] || fallar "en '$carpeta' no hay ningún '$BASE'."
    comprobar_entorno

    echo "Esto reemplaza la base que está hoy en el celular por la de '$carpeta'."
    read -r -p "¿Seguro? Escribe 'si' para continuar: " respuesta
    [ "$respuesta" = "si" ] || { echo "Cancelado, no se tocó nada."; exit 0; }

    # La app tiene que estar cerrada: con la base abierta, escribir el archivo por debajo
    # deja a Room leyendo una versión y el disco con otra.
    adb shell "am force-stop $PAQUETE"

    # La carpeta databases/ no viene con la app: la crea Room la primera vez que se abre.
    # O sea que después de una instalación limpia todavía no existe — que es exactamente
    # cuando uno viene a restaurar. Sin esto, el 'cp' falla con "No such file or directory"
    # y el respaldo parece roto cuando el que falta es el directorio.
    adb shell "run-as $PAQUETE mkdir -p databases" ||
        fallar "no pude crear la carpeta databases/ en el celular."

    for archivo in "${ARCHIVOS[@]}"; do
        if [ -f "$carpeta/$archivo" ]; then
            adb push "$carpeta/$archivo" "/data/local/tmp/$archivo" >/dev/null
            adb shell "run-as $PAQUETE cp /data/local/tmp/$archivo databases/$archivo"
            adb shell "rm /data/local/tmp/$archivo"
            echo "  subido: $archivo"
        else
            # Un -wal viejo sobre una base nueva la corrompe: si la copia no lo traía,
            # el que esté en el celular tiene que irse.
            adb shell "run-as $PAQUETE rm -f databases/$archivo"
        fi
    done

    # Comprobar que la base quedó de verdad allá. Decir "Listo" sin mirar es lo que
    # convierte un respaldo en una falsa tranquilidad.
    adb shell "run-as $PAQUETE test -f databases/$BASE" 2>/dev/null ||
        fallar "la copia no quedó en el celular: '$BASE' no está en databases/."

    echo "Listo. Abre la app y revisa que estén tus datos."
}

listar() {
    [ -d "$DESTINO" ] || fallar "todavía no hay ninguna copia en $DESTINO."
    find "$DESTINO" -mindepth 1 -maxdepth 1 -type d | sort
}

case "${1:-}" in
    bajar) bajar ;;
    subir) subir "${2:-}" ;;
    listar) listar ;;
    *)
        echo "Uso: $0 {bajar|subir <carpeta>|listar}"
        exit 1
        ;;
esac
