#!/usr/bin/env bash
# Mide cuánto tarda la app en abrirse **en frío**, que es el arranque que se siente.
#
# Existe por una medición que salió mal: `adb shell am start -W` a secas, con la app
# recién usada, contesta `LaunchState: WARM` y un número bajísimo (121 ms) porque el
# proceso seguía vivo — estaba midiendo volver a una app que nunca se cerró. El pegón
# que se nota al abrir es un arranque **frío**, y para que lo sea hay que matar el
# proceso antes de cada intento. Eso es todo lo que hace este script, más repetirlo,
# porque un solo número no distingue "lento" de "justo pasó algo en el teléfono".
#
# Uso:
#   herramientas/medir_arranque.sh            # 5 intentos
#   herramientas/medir_arranque.sh 10         # los que se quieran
#
# Cómo leerlo: lo que importa es la **mediana**, no el mejor ni el peor. Y sobre todo,
# que `LaunchState` diga COLD: si dice WARM el proceso no llegó a morir y el número no
# sirve. Ver la sección 6.7 de arquitectura.md.

set -euo pipefail

PAQUETE="com.sandyyera.reposteria"
ACTIVIDAD="$PAQUETE/.ui.MainActivity"
INTENTOS="${1:-5}"

if ! command -v adb >/dev/null 2>&1; then
    echo "No encuentro 'adb'. Es el que habla con el celular." >&2
    exit 1
fi

if [ -z "$(adb devices | sed '1d' | grep -w device || true)" ]; then
    echo "No hay ningún celular conectado. Revisa 'adb devices'." >&2
    echo "Si es por wifi, primero 'adb connect <ip>:<puerto>'." >&2
    exit 1
fi

if ! adb shell pm list packages | grep -q "^package:$PAQUETE$"; then
    echo "La app no está instalada. Corre './gradlew :app:installDebug' primero." >&2
    exit 1
fi

echo "Midiendo $INTENTOS arranques en frío de $PAQUETE."
echo

tiempos=()
for i in $(seq 1 "$INTENTOS"); do
    # Lo que faltaba en la medición original: sin esto el proceso sigue vivo y lo que
    # se mide es reabrir, no abrir.
    adb shell am force-stop "$PAQUETE"
    # Un respiro para que el sistema termine de soltar el proceso; sin él, el arranque
    # siguiente a veces sale WARM igual.
    sleep 1

    salida="$(adb shell am start -W -n "$ACTIVIDAD" 2>&1)"
    estado="$(echo "$salida" | grep -oP 'LaunchState:\s*\K\w+' || echo '?')"
    total="$(echo "$salida" | grep -oP 'TotalTime:\s*\K\d+' || echo '')"

    if [ -z "$total" ]; then
        echo "  intento $i: no pude leer TotalTime. Salida cruda:" >&2
        echo "$salida" >&2
        exit 1
    fi

    marca=""
    [ "$estado" != "COLD" ] && marca="   <-- ojo: no arrancó en frío, este no cuenta"
    printf '  intento %-2s  %5s ms   (%s)%s\n' "$i" "$total" "$estado" "$marca"
    [ "$estado" = "COLD" ] && tiempos+=("$total")
done

echo
if [ "${#tiempos[@]}" -eq 0 ]; then
    echo "Ningún intento arrancó en frío. El número que salga no dice nada." >&2
    exit 1
fi

mediana="$(printf '%s\n' "${tiempos[@]}" | sort -n | awk '{v[NR]=$1} END {print (NR%2) ? v[(NR+1)/2] : int((v[NR/2]+v[NR/2+1])/2)}')"
echo "Mediana de ${#tiempos[@]} arranques en frío: $mediana ms"
echo
# Los tramos salen de lo que se siente, no de una norma: por debajo de medio segundo
# nadie percibe espera; pasado el segundo la pantalla se ve congelada, que es la
# palabra que usó Sandy ("se quedó pegado").
if [ "$mediana" -lt 500 ]; then
    echo "Va bien. Debajo de medio segundo no se percibe espera."
    echo
    echo "Si esto fue con la compilación de release, acuérdate de volver a la de siempre:"
    echo "    ./gradlew :app:installDebug"
    echo "El respaldo (respaldo_bd.sh) usa run-as y necesita la app depurable."
elif [ "$mediana" -lt 1000 ]; then
    echo "Aceptable, pero se nota. Vale la pena comparar contra una compilación"
    echo "sin depuración: ./gradlew :app:installRelease"
else
    echo "Esto es el pegón. Antes de tocar código, compara contra una compilación"
    echo "sin depuración, que es la mitad de la explicación:"
    echo "    ./gradlew :app:installRelease && herramientas/medir_arranque.sh"
    echo "y después vuelve a instalar la de siempre con :app:installDebug."
fi
