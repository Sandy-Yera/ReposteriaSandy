#!/usr/bin/env bash
#
# Corre de una vez todo lo que se puede comprobar sin celular, en el orden que conviene:
# primero lo rápido, después lo lento. Si algo falla, se detiene ahí — seguir corriendo
# veinte minutos de pruebas cuando ya hay un archivo con una llave sin cerrar no sirve.
#
#   herramientas/probar_todo.sh
#
# Lo que NO corre y por qué:
#
#   ./gradlew :app:connectedAndroidTest    necesita el celular conectado (prueba de migración)
#   ./gradlew :app:installDebug            necesita el celular conectado
#
# Antes de instalar en el celular conviene hacer el respaldo: `herramientas/respaldo_bd.sh bajar`.

set -uo pipefail

cd "$(dirname "$(dirname "$(readlink -f "$0")")")"

paso() {
    echo
    echo "=============================================================="
    echo "  $1"
    echo "=============================================================="
}

fallo() {
    echo
    echo "!! Falló: $1"
    echo "!! Se detiene acá. Lo de más abajo no llegó a correr."
    exit 1
}

# El único fallo esperable que NO es un error: al subir la versión de la base, el esquema
# `app/schemas/N.json` lo escribe Room al compilar, así que hasta el primer compilado no
# existe. No se le hace excepción a la revisión —es la que se asegura de que ese archivo
# quede versionado— pero sí se dice qué hacer, porque el mensaje solo no lo aclara.
recordar_esquema() {
    echo
    echo "Si lo que falló es 'falta el esquema de: N.json', no hay nada roto: la base"
    echo "cambió de versión y ese archivo lo genera Room al compilar. Corre una vez"
    echo
    echo "    ./gradlew :app:assembleDebug"
    echo
    echo "y vuelve a intentar; después hay que versionar el archivo que apareció."
}

paso "1/4  Revisiones del Kotlin (sin compilador, segundos)"
python3 herramientas/revisar_kotlin.py || { recordar_esquema; fallo "revisar_kotlin.py"; }

paso "2/4  Contraste de la paleta (WCAG, sección 12.6)"
python3 herramientas/contraste.py || fallo "contraste.py"

paso "3/4  Las fórmulas puras — :logica (solo necesita Java)"
./gradlew :logica:test || fallo ":logica:test"

paso "4/4  Repositorios y ViewModel — :app (necesita el Android SDK)"
./gradlew :app:test || fallo ":app:test"

echo
echo "=============================================================="
echo "  Todo en verde."
echo "=============================================================="
echo
echo "Falta lo que necesita el celular conectado:"
echo
echo "    herramientas/respaldo_bd.sh bajar        # el respaldo, primero"
echo "    ./gradlew :app:connectedAndroidTest      # la prueba de migración"
echo "    ./gradlew :app:installDebug              # instalar"
echo "    herramientas/medir_arranque.sh           # si se siente lenta al abrir"
echo
echo "Los informes quedan en logica/build/reports/tests/test/index.html"
echo "y app/build/reports/tests/testDebugUnitTest/index.html"
