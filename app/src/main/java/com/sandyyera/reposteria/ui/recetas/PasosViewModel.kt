package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.RecetaPaso
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.logica.partes.AtajoDePaso
import com.sandyyera.reposteria.logica.partes.BloqueDePasos
import com.sandyyera.reposteria.logica.partes.PasoParaMostrar
import com.sandyyera.reposteria.logica.partes.SeccionParaTitulo
import com.sandyyera.reposteria.logica.partes.TituloDePaso
import com.sandyyera.reposteria.logica.partes.atajoAntesDelCursor
import com.sandyyera.reposteria.logica.partes.bloquesDePasos
import com.sandyyera.reposteria.logica.partes.reemplazarAtajo
import com.sandyyera.reposteria.logica.partes.titulosDisponibles
import com.sandyyera.reposteria.data.repositorio.IngredientesDeSeccion
import com.sandyyera.reposteria.data.repositorio.ParteTraida
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreSeccion
import com.sandyyera.reposteria.logica.validaciones.errorEnTextoDePaso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Un atajo recién escrito, con dónde estaba (8.8).
 *
 * Lleva el **cursor con que se detectó** y no solo el paso, porque de eso depende cuál de las
 * apariciones se reemplaza: en un paso que ya usó `:ingredientes:` dos veces, buscar la primera
 * cambiaría la equivocada. Es el mismo cuidado que `reemplazarAtajo`, que por eso pide el cursor.
 */
data class AtajoEnCurso(val pasoId: Long, val cursor: Int, val atajo: AtajoDePaso)

/**
 * Dónde hay que dejar el cursor después de reemplazar un atajo.
 *
 * Viaja aparte del texto porque el campo lo maneja él: el largo cambia al reemplazar, así que
 * conservar la posición como número la dejaría donde no va — el mismo problema que resolvió
 * `formatearMientrasSeEscribe` con `TextoConCursor`.
 */
data class PosicionDelCursor(val pasoId: Long, val cursor: Int)

/**
 * Un título que se está creando desde los pasos (8.8).
 *
 * Un título **es una sección de la receta**, así que crearlo pasa por las mismas reglas que en el
 * paso de cantidades — incluido [nombreDeLaPrimera], que es lo que hay que contestar cuando la
 * receta tenía una sola sección con el nombre automático: partir en dos obliga a bautizar la que
 * ya estaba, o quedaría un encabezado "General" al lado de "Crema" (8.2).
 */
data class TituloNuevo(
    val nombre: String = "",
    val nombreDeLaPrimera: String? = null,
    val tocado: Boolean = false,
    val guardando: Boolean = false,
    /** Lo que contestó el repositorio. Va junto al campo y nunca en la franja de abajo (8.2). */
    val rechazo: String? = null
) {
    val error: String? get() = rechazo ?: errorEnNombreSeccion(nombre).takeIf { tocado }

    val errorDeLaPrimera: String?
        get() = nombreDeLaPrimera?.let { errorEnNombreSeccion(it) }.takeIf { tocado }

    val puedeGuardar: Boolean
        get() = !guardando &&
            errorEnNombreSeccion(nombre) == null &&
            (nombreDeLaPrimera == null || errorEnNombreSeccion(nombreDeLaPrimera) == null)
}

/** Qué hay abierto encima del paso de pasos. */
sealed interface DialogoPasos {

    data object Ninguno : DialogoPasos

    /**
     * Elegir bajo qué título va un paso (8.8).
     *
     * Lleva [disponibles] ya resueltos y no la lista de secciones cruda: qué títulos se pueden
     * usar depende de cuáles están tomados por **otros** bloques y de si los nombres de sección
     * se muestran siquiera, y esa regla vive en `titulosDisponibles`. Calculándola acá, el menú
     * y lo que el repositorio acepta no pueden discrepar.
     *
     * [atajo] tiene valor cuando se llegó escribiendo `:titulo:`, y entonces al elegir hay que
     * **sacar el atajo del texto**. Es `null` cuando se llegó tocando el número del paso, donde
     * no hay nada escrito que borrar.
     */
    data class ElegirTitulo(
        val pasoId: Long,
        val disponibles: List<TituloDePaso>,
        val nombrePorId: Map<Long, String>,
        val atajo: AtajoEnCurso? = null,
        /**
         * El título nuevo que se está escribiendo, o `null` si solo se está eligiendo.
         *
         * Existe porque una receta sin secciones **no tenía de dónde sacar títulos**: el menú
         * ofrecía solo el General y la única salida era irse al paso de cantidades a crear una
         * sección. Sandy lo pidió al revés y tiene razón — un título es una parte de la receta, y
         * decidir que hay partes es algo que pasa mientras se escriben los pasos.
         */
        val creando: TituloNuevo? = null
    ) : DialogoPasos

    /**
     * Elegir un ingrediente de la receta para escribirlo dentro del paso (8.8).
     *
     * Los nombres son **los de esta receta** y no los del catálogo entero: un paso habla de lo
     * que la receta lleva, y ofrecer las cincuenta cosas del catálogo obligaría a buscar entre
     * ingredientes que no vienen al caso.
     */
    data class ElegirIngrediente(
        val atajo: AtajoEnCurso,
        val grupos: List<IngredientesDeSeccion> = emptyList()
    ) : DialogoPasos {
        val vacio: Boolean get() = grupos.all { it.lineas.isEmpty() }
    }

    /**
     * La lista de atajos que existen.
     *
     * Se abre de dos formas y por eso [atajo] es nulable: desde el botón de arriba (`null`, no
     * hay nada escrito) o escribiendo `:info:` (con valor, y al cerrar hay que sacar ese `:info:`
     * del texto — si no, se quedaría escrito en la receta).
     */
    data class Ayuda(val atajo: AtajoEnCurso? = null) : DialogoPasos

    /** La confirmación antes de borrar un paso que tiene texto escrito. */
    data class ConfirmarBorrado(val pasoId: Long, val texto: String) : DialogoPasos
}

/** Lo que el paso "Pasos" necesita para dibujarse (8.8). */
data class EstadoPasos(
    val bloques: List<BloqueDePasos> = emptyList(),
    val secciones: List<SeccionParaTitulo> = emptyList(),
    /**
     * Lo que se está escribiendo, por paso.
     *
     * Va **aparte de los bloques** y no dentro de ellos: los bloques vienen de la base, y un
     * campo de texto que espera a que la base conteste se rompe — se vio con el nombre de la
     * receta, donde escribir "Torta" dejaba "ortaT" porque el campo reponía su valor anterior
     * antes de que llegara la letra nueva (12.2.1). Acá el texto cambia en el momento y la
     * base se entera después.
     */
    val escribiendo: Map<Long, String> = emptyMap(),
    /** Los grupos de secciones traídas de otra receta (8.11), para señalar sus bloques. */
    val partes: List<ParteTraida> = emptyList(),
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    /** El texto que hay que dibujar en un paso: lo que se está escribiendo, o lo guardado. */
    fun textoDe(paso: PasoParaMostrar): String = escribiendo[paso.id] ?: paso.texto

    /**
     * De qué receta vinieron los pasos de este bloque, o `null` si son propios (8.11.2).
     *
     * Lo pidió Sandy con el mismo argumento que las secciones del paso de cantidades: sin esto,
     * un bloque traído se ve igual que uno escrito acá, y con dos recetas traídas seguidas no hay
     * forma de saber dónde termina una. Un paso general anidado ya se dibuja con sangría, pero
     * eso dice "vino de algo", no **de qué**.
     */
    fun deDondeViene(titulo: TituloDePaso): String? {
        val seccionId = titulo ?: return null
        return partes.firstOrNull { seccionId in it.seccionIds }?.comoSeNombraElOrigen
    }

    /** Lo que esté mal en ese paso, o `null`. El vacío no es un error: es un paso que se borra. */
    fun errorDe(paso: PasoParaMostrar): String? = errorEnTextoDePaso(textoDe(paso))

    /** Si la receta todavía no tiene ningún paso. */
    val vacio: Boolean get() = bloques.isEmpty()
}

/**
 * El cerebro del paso "Pasos" (8.8).
 *
 * **Guarda solo, al salir del campo**, igual que el paso de duración y por el mismo motivo: un
 * paso a medio escribir se ve igual que uno que se está vaciando a propósito, así que escribir
 * en cada tecla borraría el paso apenas se seleccione todo el texto para reemplazarlo. Lo que
 * dispara la escritura es abandonar el campo.
 *
 * **Los bloques se arman en `logica/`** (`bloquesDePasos`) y no acá: agrupar por título, juntar
 * los generales pegados y numerar corrido son cuatro reglas que se pueden probar sin celular, y
 * escritas entre medio del dibujo se equivocan solas.
 */
class PasosViewModel(
    private val recetaId: Long,
    private val recetas: RecetaRepositorio
) : ViewModel() {

    private val mensaje = MutableStateFlow<String?>(null)
    private val escribiendo = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _dialogo = MutableStateFlow<DialogoPasos>(DialogoPasos.Ninguno)

    private val _cursorPedido = MutableStateFlow<PosicionDelCursor?>(null)

    /** El cuadro va por su propio canal, fuera del `combine` del estado (12.2.1). */
    val dialogo: StateFlow<DialogoPasos> = _dialogo

    /**
     * Dónde dejar el cursor tras reemplazar un atajo. La pantalla lo consume y avisa.
     *
     * Va por su propio canal y no dentro del estado por lo mismo que el diálogo: el estado pasa
     * por un `combine` que también escucha a la base, y un campo de texto que espera a que la
     * base conteste se rompe (12.2.1).
     */
    val cursorPedido: StateFlow<PosicionDelCursor?> = _cursorPedido

    val estado: StateFlow<EstadoPasos> = combine(
        recetas.observarPasos(recetaId),
        recetas.observarSecciones(recetaId),
        escribiendo,
        // Se **observa** y no se pide una vez: traer una receta desde el paso de cantidades
        // tiene que marcar sus bloques acá sin que nadie se acuerde de refrescar.
        recetas.observarPartesDe(recetaId),
        mensaje
    ) { pasos, secciones, enElCampo, partesTraidas, mensajeActual ->
        val paraTitulo = secciones.map { SeccionParaTitulo(it.id, it.nombreSeccion) }
        EstadoPasos(
            bloques = bloquesDePasos(pasos.map { it.aMostrar() }, paraTitulo),
            secciones = paraTitulo,
            escribiendo = enElCampo,
            partes = partesTraidas,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoPasos()
    )

    /** La fila de Room como la ven las funciones puras. */
    private fun RecetaPaso.aMostrar() = PasoParaMostrar(
        id = id,
        texto = contenido,
        titulo = tituloSeccionId,
        esGeneralAnidado = esGeneralAnidado,
        orden = orden
    )

    // --- Escribir ---

    /**
     * Agrega un paso vacío al final y **lo deja listo para escribir**.
     *
     * Nace bajo el mismo título que el último paso que haya, y no siempre en el General: quien
     * está escribiendo el bizcocho agrega el paso siguiente del bizcocho. Con la receta vacía
     * el primero va al General, que es lo único que existe entonces.
     */
    fun agregarPaso() {
        viewModelScope.launch {
            val ultimo = estado.value.bloques.lastOrNull()
            recetas.agregarPaso(
                recetaId = recetaId,
                titulo = ultimo?.titulo,
                esGeneralAnidado = false
            )
        }
    }

    /**
     * Lo que se teclea. No toca la base: eso pasa al salir del campo.
     *
     * De paso mira si acaba de escribirse un atajo **justo antes del cursor** (8.8). Se mira acá
     * y no al guardar porque el atajo tiene que responder en el momento: la gracia es que abra el
     * menú apenas se termina de escribir, ahí donde está la mano.
     */
    fun cambiarTexto(pasoId: Long, texto: String, cursor: Int) {
        escribiendo.value = escribiendo.value + (pasoId to texto)

        val atajo = atajoAntesDelCursor(texto, cursor) ?: return
        val enCurso = AtajoEnCurso(pasoId, cursor, atajo)
        when (atajo) {
            AtajoDePaso.INFO -> _dialogo.value = DialogoPasos.Ayuda(enCurso)
            AtajoDePaso.TITULO -> abrirElegirTitulo(pasoId, enCurso)
            AtajoDePaso.INGREDIENTES -> abrirElegirIngrediente(enCurso)
        }
    }

    // --- Los atajos (8.8) ---

    /** La lista de atajos, desde el botón de arriba. Sin nada escrito que sacar después. */
    fun abrirAyuda() {
        _dialogo.value = DialogoPasos.Ayuda()
    }

    /**
     * Cierra la ayuda. Es [cerrarDialogo] con otro nombre, para que la pantalla se lea.
     *
     * No hace nada distinto **a propósito**: cuando eran dos comportamientos, `:info:` se borraba
     * al cerrar y `:titulo:` no al cancelar. Dos funciones que hacen lo mismo son mejores que dos
     * que casi lo hacen.
     */
    fun cerrarAyuda() = cerrarDialogo()

    private fun abrirElegirIngrediente(enCurso: AtajoEnCurso) {
        _dialogo.value = DialogoPasos.ElegirIngrediente(enCurso)
        viewModelScope.launch {
            val grupos = recetas.ingredientesPorSeccionDe(recetaId)
            // Se comprueba que siga abierto el mismo: entre pedir la lista y que llegue pudo
            // cerrarse el cuadro o abrirse otro, y rellenar el equivocado mostraría una lista
            // que no corresponde al atajo que la pidió.
            val ahora = _dialogo.value
            if (ahora is DialogoPasos.ElegirIngrediente && ahora.atajo == enCurso) {
                _dialogo.value = ahora.copy(grupos = grupos)
            }
        }
    }

    /**
     * Escribe el ingrediente elegido —con su cantidad— en lugar del `:ingredientes:`.
     *
     * Recibe la frase ya armada y no el ingrediente, para que lo que se escribe sea exactamente
     * lo que se tocó: si la frase se rehiciera acá, cualquier diferencia con la del menú saldría
     * en el paso y solo se notaría leyéndolo después.
     */
    fun elegirIngrediente(comoSeEscribe: String) {
        val abierto = _dialogo.value as? DialogoPasos.ElegirIngrediente ?: return
        _dialogo.value = DialogoPasos.Ninguno
        reemplazar(abierto.atajo, comoSeEscribe)
    }

    // --- Crear un título sin salir de los pasos (8.8) ---

    /**
     * Abre el campo para escribir un título nuevo, dentro del mismo cuadro.
     *
     * Va a preguntar si hay que bautizar la sección que ya está: partir en dos una receta que
     * tenía una sola parte con el nombre automático obliga a nombrarla, o quedaría un encabezado
     * "General" al lado de "Crema" (8.2). La consulta va acá y no en la pantalla porque la
     * respuesta depende de la base.
     */
    fun empezarTituloNuevo() {
        val abierto = _dialogo.value as? DialogoPasos.ElegirTitulo ?: return
        _dialogo.value = abierto.copy(creando = TituloNuevo())
        viewModelScope.launch {
            val bautizo = recetas.nombreQueFaltaBautizar(recetaId)
            enTituloNuevo { it.copy(nombreDeLaPrimera = bautizo) }
        }
    }

    /** Vuelve del campo a la lista, sin crear nada. */
    fun cancelarTituloNuevo() = enElegirTitulo { it.copy(creando = null) }

    // Al escribir, el rechazo anterior deja de aplicar: era sobre lo que había antes.
    fun cambiarNombreDelTitulo(texto: String) = enTituloNuevo {
        it.copy(nombre = texto, tocado = true, rechazo = null)
    }

    fun cambiarNombreDeLaPrimera(texto: String) = enTituloNuevo {
        it.copy(nombreDeLaPrimera = texto, tocado = true, rechazo = null)
    }

    /**
     * Crea el título y **se lo pone al paso de una vez**.
     *
     * Las dos cosas juntas y no en dos toques: quien escribe `:titulo:` y crea "Crema" está
     * diciendo que *este* paso va bajo Crema. Dejarlo creado pero sin asignar obligaría a volver
     * a abrir el menú para elegir lo que se acaba de escribir.
     */
    fun guardarTituloNuevo() {
        val abierto = _dialogo.value as? DialogoPasos.ElegirTitulo ?: return
        val creando = abierto.creando ?: return
        if (!creando.puedeGuardar) return

        _dialogo.value = abierto.copy(creando = creando.copy(guardando = true))

        viewModelScope.launch {
            val resultado = recetas.agregarSeccion(
                recetaId = recetaId,
                nombre = creando.nombre,
                nombreDeLaPrimera = creando.nombreDeLaPrimera
            )
            if (resultado is Resultado.NoSePudo) {
                // Dentro del cuadro: es sobre lo que se acaba de escribir, y con el teclado
                // abierto la franja de abajo queda tapada (8.2).
                enTituloNuevo { it.copy(guardando = false, rechazo = resultado.motivo) }
                return@launch
            }

            // La sección recién creada es la última de la receta. Se lee de la base y no se
            // adivina: `agregarSeccion` no devuelve el id, y suponerlo pondría el paso bajo el
            // título equivocado justo cuando la receta ya tenía otras partes.
            val nueva = recetas.obtenerSecciones(recetaId).lastOrNull()
            _dialogo.value = DialogoPasos.Ninguno
            abierto.atajo?.let { reemplazar(it, "") }
            if (nueva == null) return@launch
            val r = recetas.cambiarTituloDePaso(abierto.pasoId, nueva.id)
            if (r is Resultado.NoSePudo) mensaje.value = r.motivo
        }
    }

    private fun enElegirTitulo(cambio: (DialogoPasos.ElegirTitulo) -> DialogoPasos.ElegirTitulo) {
        _dialogo.update { actual ->
            if (actual is DialogoPasos.ElegirTitulo) cambio(actual) else actual
        }
    }

    private fun enTituloNuevo(cambio: (TituloNuevo) -> TituloNuevo) = enElegirTitulo { abierto ->
        abierto.creando?.let { abierto.copy(creando = cambio(it)) } ?: abierto
    }

    /**
     * Saca el atajo del texto y deja en su lugar lo que se eligió.
     *
     * El texto sale de [escribiendo] y no de la base: el atajo se acaba de teclear, así que lo
     * guardado todavía no lo tiene. Si no hay nada ahí es que el paso ya se guardó o se cerró, y
     * entonces no hay nada que reemplazar.
     */
    private fun reemplazar(enCurso: AtajoEnCurso, porEsto: String) {
        val texto = escribiendo.value[enCurso.pasoId] ?: return
        val resultado = reemplazarAtajo(texto, enCurso.cursor, enCurso.atajo, porEsto)
        escribiendo.value = escribiendo.value + (enCurso.pasoId to resultado.texto)
        _cursorPedido.value = PosicionDelCursor(enCurso.pasoId, resultado.cursor)
    }

    /** La pantalla avisa que ya movió el cursor, para que no se vuelva a mover en cada dibujo. */
    fun cursorAplicado() {
        _cursorPedido.value = null
    }

    /**
     * Guarda lo escrito en un paso. Lo llama la pantalla **al salir del campo y al desmontarse**.
     *
     * Lo segundo no es de más: cambiar de paso de la receta puede llevarse el campo sin que
     * alcance a avisar que perdió el foco, y ahí lo recién escrito se perdía en silencio. Es la
     * misma lección que el paso de duración.
     */
    fun guardarPaso(pasoId: Long) {
        val texto = escribiendo.value[pasoId] ?: return
        viewModelScope.launch {
            when (val r = recetas.guardarTextoDePaso(pasoId, texto)) {
                is Resultado.Listo -> escribiendo.value = escribiendo.value - pasoId
                // El texto se conserva en el campo: si se descartara, el aviso diría qué está
                // mal sobre algo que ya no se puede ver ni corregir.
                is Resultado.NoSePudo -> mensaje.value = r.motivo
            }
        }
    }

    /** Guarda todo lo pendiente. La pantalla la llama al desmontarse. */
    fun guardarTodoLoPendiente() {
        escribiendo.value.keys.toList().forEach { guardarPaso(it) }
    }

    // --- El título ---

    fun abrirElegirTitulo(pasoId: Long, atajo: AtajoEnCurso? = null) {
        val secciones = estado.value.secciones
        // Los títulos que usan **los demás** bloques, no todos: al reabrir el de un bloque que
        // ya tiene título, incluirse a sí mismo lo dejaría fuera de su propia lista.
        val usadosPorOtros = estado.value.bloques
            .filterNot { bloque -> bloque.pasos.any { it.paso.id == pasoId } }
            .map { it.titulo }
        _dialogo.value = DialogoPasos.ElegirTitulo(
            pasoId = pasoId,
            disponibles = titulosDisponibles(secciones, usadosPorOtros),
            nombrePorId = secciones.associate { it.id to it.nombre },
            atajo = atajo
        )
    }

    fun elegirTitulo(titulo: TituloDePaso) {
        val abierto = _dialogo.value as? DialogoPasos.ElegirTitulo ?: return
        _dialogo.value = DialogoPasos.Ninguno
        // Si se llegó escribiendo `:titulo:`, ese texto sale del paso: el título es una marca
        // del paso, no algo que se lea dentro de él.
        abierto.atajo?.let { reemplazar(it, "") }
        viewModelScope.launch {
            val r = recetas.cambiarTituloDePaso(abierto.pasoId, titulo)
            if (r is Resultado.NoSePudo) mensaje.value = r.motivo
        }
    }

    // --- Mover y borrar ---

    fun moverPaso(pasoId: Long, haciaArriba: Boolean) {
        viewModelScope.launch { recetas.moverPaso(pasoId, haciaArriba) }
    }

    /**
     * Pide borrar un paso.
     *
     * **Un paso vacío se borra sin preguntar**: no hay nada que perder, y confirmarlo sería un
     * cuadro por cada "agregué uno de más". Con texto escrito sí se pregunta, que es la regla
     * de 6.3 — nada se borra de golpe.
     */
    fun pedirBorrado(paso: PasoParaMostrar) {
        val texto = estado.value.textoDe(paso)
        if (texto.isBlank()) {
            viewModelScope.launch { recetas.eliminarPaso(paso.id) }
            return
        }
        _dialogo.value = DialogoPasos.ConfirmarBorrado(paso.id, texto)
    }

    fun confirmarBorrado() {
        val abierto = _dialogo.value as? DialogoPasos.ConfirmarBorrado ?: return
        _dialogo.value = DialogoPasos.Ninguno
        viewModelScope.launch {
            recetas.eliminarPaso(abierto.pasoId)
            escribiendo.value = escribiendo.value - abierto.pasoId
            mensaje.value = "Se quitó el paso"
        }
    }

    /**
     * Cierra lo que esté abierto **y saca el atajo del texto si lo hubo**.
     *
     * Lo segundo no es de más: hasta acá `:info:` desaparecía al cerrar su ayuda pero `:titulo:`
     * y `:ingredientes:` se quedaban escritos al cancelar, así que el mismo gesto —salir sin
     * elegir— dejaba basura en unos casos y no en otros. Peor: ese `:titulo:` sobrante se guarda
     * dentro del paso y vuelve a abrir el menú apenas se borre una letra.
     *
     * Cancelar significa "no quiero esto", y lo que se escribió era la forma de pedirlo, no algo
     * que uno quiera leer después al seguir la receta.
     */
    fun cerrarDialogo() {
        val enCurso = atajoDelDialogoAbierto()
        _dialogo.value = DialogoPasos.Ninguno
        enCurso?.let { reemplazar(it, "") }
    }

    /** El atajo que abrió el cuadro que está abierto, si es que lo abrió uno. */
    private fun atajoDelDialogoAbierto(): AtajoEnCurso? = when (val abierto = _dialogo.value) {
        is DialogoPasos.Ayuda -> abierto.atajo
        is DialogoPasos.ElegirTitulo -> abierto.atajo
        is DialogoPasos.ElegirIngrediente -> abierto.atajo
        else -> null
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    companion object {
        fun fabrica(recetaId: Long, recetas: RecetaRepositorio): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { PasosViewModel(recetaId, recetas) }
            }
    }
}
