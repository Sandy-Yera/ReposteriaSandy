# Cómo preparar el equipo (Arch Linux)

Guía para dejar el proyecto andando en tu PC. Está pensada para hacerse **por partes**:
no necesitas todo desde el día uno, y cada nivel sirve para algo concreto.

| Nivel | Qué instalas | Qué te permite | ¿Ya lo necesitas? |
|---|---|---|---|
| **1** | Java (JDK) | Correr los 178 tests de la lógica | **Sí, ahora** |
| **2** | Android SDK | Compilar la app de verdad | Cuando lleguemos a Room |
| **3** | Celular por USB, o emulador | Ver la app funcionando | Al tener las primeras pantallas |

**Android Studio no es obligatorio.** Es cómodo, sobre todo para las pantallas de Compose,
pero todo se puede hacer con VS Code + la terminal. Más abajo están las dos rutas.

---

## Nivel 1 — Java, para correr los tests (10 minutos)

### 1. Revisa si ya lo tienes

```bash
java -version
```

Si responde `openjdk version "17..."` o superior, ya está: salta al paso 3.

### 2. Instálalo

```bash
sudo pacman -S jdk17-openjdk
```

Si ya tenías varias versiones de Java, elige cuál se usa por defecto:

```bash
archlinux-java status      # muestra las instaladas
sudo archlinux-java set java-17-openjdk
```

### 3. Corre los tests

Desde la carpeta del proyecto:

```bash
./gradlew :logica:test
```

La primera vez se demora unos minutos porque descarga Gradle y las dependencias.
Al final tiene que decir:

```
BUILD SUCCESSFUL
```

Para ver los tests uno por uno:

```bash
./gradlew :logica:test --info | grep -E "PASSED|FAILED"
```

Y si prefieres leerlo con calma, Gradle deja un informe en HTML:

```bash
xdg-open logica/build/reports/tests/test/index.html
```

> **Si algo falla acá, avísame con el mensaje completo.** Que a mí me pasen y a ti no
> significa que hay una diferencia de entorno que conviene resolver antes de seguir.

---

## Nivel 2 — Android SDK, para compilar la app

Acá tienes que elegir una de dos rutas. **Puedes cambiar de opinión después**: la B se
puede convertir en la A instalando Android Studio encima, sin rehacer nada.

### Ruta A — Android Studio (recomendada para empezar)

Trae el SDK, el emulador y su propio Java, todo junto y configurado. Es lo más simple
cuando uno recién parte, aunque pese más.

**No uses AUR para esto si nunca lo has hecho** — el paquete `android-studio` de AUR
compila y puede fallar por razones que no tienen que ver contigo. El paquete oficial de
Google es un `.tar.gz` que solo se descomprime:

1. Descarga el `.tar.gz` de Linux desde <https://developer.android.com/studio>
2. Descomprímelo en tu carpeta personal:

   ```bash
   tar -xzf ~/Descargas/android-studio-*-linux.tar.gz -C ~/
   ```

3. Ábrelo:

   ```bash
   ~/android-studio/bin/studio.sh
   ```

4. La primera vez te va a preguntar por el tipo de instalación: elige **Standard**.
   Va a descargar el SDK solo (son varios GB, anda con paciencia).

5. Para tenerlo en el menú de aplicaciones: dentro de Android Studio,
   **Tools → Create Desktop Entry**.

Cuando abras el proyecto, Android Studio va a crear solo un archivo `local.properties`
apuntando al SDK. **Ese archivo no se sube al repositorio** (está en `.gitignore`) porque
la ruta es distinta en cada máquina.

### Ruta B — Solo el SDK, y sigues en VS Code

Más liviano, si prefieres quedarte con tu editor de siempre.

```bash
sudo pacman -S jdk17-openjdk unzip
```

1. Descarga las "Command line tools only" de <https://developer.android.com/studio>
   (están abajo de todo en la página).
2. Prepáralas en la ruta que el SDK espera:

   ```bash
   mkdir -p ~/Android/Sdk/cmdline-tools
   unzip ~/Descargas/commandlinetools-linux-*.zip -d ~/Android/Sdk/cmdline-tools
   mv ~/Android/Sdk/cmdline-tools/cmdline-tools ~/Android/Sdk/cmdline-tools/latest
   ```

3. Agrega esto al final de tu `~/.bashrc` (o `~/.zshrc` si usas zsh):

   ```bash
   export ANDROID_HOME="$HOME/Android/Sdk"
   export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
   ```

   Y recarga: `source ~/.bashrc`

4. Instala lo necesario y acepta las licencias:

   ```bash
   sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   sdkmanager --licenses
   ```

**Para VS Code**, estas extensiones ayudan:
- *Kotlin* (fwcd) — resaltado y autocompletado
- *Gradle for Java* (Microsoft) — correr tareas de Gradle desde el editor

> Aviso honesto: en VS Code **no vas a tener la vista previa de las pantallas de Compose**.
> Se puede trabajar igual (compilas e instalas en el celular para ver el resultado), pero
> cuando lleguemos a la interfaz probablemente te convenga tener Android Studio al menos
> para eso. Lo vemos cuando toque.

---

## Nivel 3 — Ver la app funcionando

### Opción recomendada: tu propio celular por USB

Es más rápido que el emulador, no consume RAM, y es donde la app va a vivir de verdad.

1. Instala las herramientas y las reglas para que Linux reconozca el teléfono:

   ```bash
   sudo pacman -S android-tools android-udev
   ```

2. **Agrégate al grupo `adbusers`.** El paquete crea el grupo pero no mete a nadie en él,
   y sin esto `adb` no puede hablarle al teléfono aunque el sistema lo detecte:

   ```bash
   sudo usermod -aG adbusers $USER
   ```

   El cambio recién aplica al volver a iniciar sesión. Para probarlo de inmediato, sin
   reiniciar, se puede abrir una shell con el grupo ya activo:

   ```bash
   newgrp adbusers
   adb kill-server && adb devices
   ```

   Igual conviene reiniciar después, para que quede activo en todas las terminales.

3. En el celular, activa las opciones de desarrollador:
   - **Ajustes → Información del teléfono**
   - Toca **7 veces** seguidas sobre *Número de compilación*
   - Vuelve atrás: aparece **Opciones de desarrollador**
   - Actívalas y enciende **Depuración por USB**

4. Conecta el cable y verifica:

   ```bash
   adb devices
   ```

   Debe aparecer tu teléfono. La primera vez el celular pregunta si autorizas al
   computador: acepta y marca *Siempre permitir*.

   **Si la lista sale vacía**, estos tres comandos dicen dónde está el problema:

   ```bash
   lsusb                  # ¿lo ve el sistema?
   groups                 # ¿estás en adbusers?
   pacman -Q android-udev android-tools
   ```

   - El teléfono no aparece en `lsusb` → es el cable o el puerto. Muchos cables son solo
     de carga y no llevan datos.
   - Aparece en `lsusb` pero `groups` no incluye `adbusers` → falta el paso 2.
   - Todo lo anterior está bien → la depuración USB sigue apagada, o el modo del cable
     quedó en "Solo cargar" en vez de "Transferir archivos".
   - **Todo lo anterior está bien y aun así nada** → ver USBGuard, más abajo.

   Si aparece `unauthorized`, va bien: revisa la pantalla del celular, hay un cuadro
   esperando que autorices el computador.

5. Para instalar la app cuando la tengamos:

   ```bash
   ./gradlew :app:installDebug
   ```

### Si usas USBGuard

Este fue el caso real acá, y cuesta dar con él porque no aparece en ningún tutorial de
Android: **USBGuard bloquea el teléfono justamente al activar la depuración USB**.

El motivo es que autoriza dispositivos por su **conjunto de interfaces**. Al conectar el
celular por primera vez lo autorizaste en modo transferencia de archivos; al encender la
depuración, el teléfono empieza a ofrecer una interfaz más (`ff:42:01`, que es adb), y
para USBGuard eso ya no es el dispositivo que aprobó. Lo bloquea.

Lo confuso es que **todo lo demás parece correcto**: el teléfono aparece en `lsusb`, los
permisos del dispositivo están bien, el grupo está bien, y la interfaz de adb existe en
los descriptores. Solo que nadie puede usarla. Y como el celular únicamente pregunta
"¿autorizar este equipo?" cuando alguien logra hablarle, tampoco pregunta nunca — lo que
hace pensar que el problema está en el teléfono cuando está en el PC.

Para comprobarlo:

```bash
systemctl status usbguard          # ¿está corriendo?
sudo usbguard list-devices | grep -i android
```

Si la línea empieza con un número y dice `block`, es esto. Se desbloquea con ese número:

```bash
sudo usbguard allow-device <número>
adb kill-server && adb devices
```

Ahí sí aparece el cuadro de autorización en el celular.

Ese permiso dura hasta desconectar. Para dejarlo fijo:

```bash
sudo usbguard allow-device <número> -p
```

La regla que genera queda amarrada al número de serie del teléfono, no a "cualquier
Samsung", así que es específica. Aun así es bajar una protección puesta a propósito:
usar el comando sin `-p` cada vez que toque instalar son dos segundos y no cede nada.

### Opción alternativa: emulador

Solo si no quieres usar el celular. Necesita virtualización activa:

```bash
lsmod | grep kvm          # debería listar kvm_intel o kvm_amd
sudo usermod -aG kvm $USER
```

Después de agregarte al grupo hay que **cerrar sesión y volver a entrar**. Si `lsmod` no
muestra nada, la virtualización está desactivada en la BIOS de tu PC.

El emulador se crea desde Android Studio (**Tools → Device Manager**).

---

## Cuando el compilador dice que no existe algo que sí existe

Síntoma: al compilar `:app` salen varios `Unresolved reference` de funciones de `logica/`
que llevaban semanas funcionando, y en el mismo archivo **unos imports fallan y otros no**:

```
e: ...IngredientesViewModel.kt:12:49 Unresolved reference 'filtrarPor'
e: ...IngredientesViewModel.kt:21:53 Unresolved reference 'textoANumero'
```

pero `ErroresIngrediente` —que vive en el mismo archivo que `textoANumero`— sí resuelve.

**Eso no lo puede causar el código.** Si el paquete estuviera mal escrito o el archivo no
existiera, fallaría el archivo completo, no la mitad. Lo que pasa es que quedó a medias la
carpeta `logica/build/`.

El motivo es cómo compila Kotlin: las funciones sueltas de un archivo van a parar a una
clase con el nombre del archivo (`Busqueda.kt` → `BusquedaKt.class`), mientras que cada
`class`, `data class` o `enum` va a la suya. Si una compilación incremental deja el
directorio inconsistente, pueden quedar las clases y faltar esos `*Kt.class` — y entonces
falla exactamente la mitad de cada import. Peor: `:logica:compileKotlin` queda `UP-TO-DATE`,
así que Gradle está convencido de que no hay nada que rehacer.

El mismo problema aparece también al correr `./gradlew :logica:test`, y ahí se ve todavía
más claro: el test no encuentra funciones que están **en su mismo paquete**, sin ningún
import de por medio. Es la misma carpeta rota, mirada desde el otro lado.

> **Ojo con esto:** `./gradlew :logica:test` **no sirve** para comprobar si el código está
> bien mientras el problema esté presente. Los tests se compilan contra
> `logica/build/classes/kotlin/main`, que es justamente lo que está mal. Hay que borrar
> primero y correr los tests después.

Para ver qué falta:

```bash
ls logica/build/classes/kotlin/main/com/sandyyera/reposteria/logica/*/
```

Tienen que estar todos los `*Kt.class`: `BusquedaKt`, `FormatoKt`, `ValidacionesKt`,
`ValorPorGramoKt`, `MoldesKt`, `PreciosKt`, `RendimientoKt`, `SimulacionKt`, `SueldosKt`.

### El arreglo

El estado viejo puede estar guardado en **tres lugares distintos**, y hay que vaciar los
tres. Borrar solo `logica/build` no basta: como este proyecto tiene
`org.gradle.caching=true`, Gradle guarda el resultado de cada tarea en una caché aparte y
lo restaura tal cual, roto incluido.

```bash
./gradlew --stop                        # 1. demonios: limpia el estado en memoria de Kotlin
rm -rf logica/build app/build           # 2. las carpetas de compilación
rm -rf ~/.gradle/caches/build-cache-1   # 3. la caché de Gradle entre compilaciones
./gradlew --no-build-cache :logica:test # 4. compilar de cero, sin consultar ninguna caché
```

Si los 178 tests pasan, el código estaba bien y era esto. Después,
`./gradlew :app:installDebug` normal.

Borrar `build-cache-1` es seguro: es pura caché y se rehace sola. La compilación siguiente
tarda más y luego vuelve a la normalidad.

### Antes de todo eso, descartar lo simple

Dos comandos de tres segundos que separan "está mal la compilación" de "está mal el
archivo en el disco":

```bash
git status --short && git log --oneline -1   # ¿el árbol está limpio y en el commit que crees?
grep -c "^fun " logica/src/main/kotlin/com/sandyyera/reposteria/logica/validaciones/Validaciones.kt
```

Si el conteo de funciones no coincide con lo que el archivo debería tener, entonces sí es
el código —o una copia incompleta— y limpiar cachés no va a arreglar nada.

---

## Preguntas que suelen aparecer

**¿Tengo que abandonar VS Code?**
No. Puedes editar todo en VS Code y usar la terminal para compilar y correr tests. Android
Studio conviene sobre todo para la vista previa de las pantallas y el depurador visual;
muchos usan los dos.

**¿Arch me va a dar problemas?**
No, al contrario: el emulador anda mejor en Linux que en Windows porque KVM es nativo. Lo
único distinto de Arch es que Java se administra con `archlinux-java` en vez de
`update-alternatives`.

**Instalé Java pero `./gradlew` dice que no lo encuentra.**
Revisa `archlinux-java status`. Si hay varias versiones, fija una con
`sudo archlinux-java set java-17-openjdk`.

**¿Cuánto espacio necesito?**
Android Studio con el SDK ocupa entre 8 y 12 GB. Solo el SDK (ruta B), unos 3 GB.

**¿Y si algo se rompe?**
Copia el mensaje de error completo y me lo pasas. Es más fácil de lo que parece: casi
siempre es una ruta o una licencia sin aceptar.
