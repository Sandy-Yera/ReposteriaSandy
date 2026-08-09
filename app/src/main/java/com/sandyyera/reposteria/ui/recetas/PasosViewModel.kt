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
import com.sandyyera.reposteria.logica.validaciones.errorEnTextoDePaso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
        val atajo: AtajoEnCurso? = null
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
        val nombres: List<String> = emptyList()
    ) : DialogoPasos

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
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    /** El texto que hay que dibujar en un paso: lo que se está escribiendo, o lo guardado. */
    fun textoDe(paso: PasoParaMostrar): String = escribiendo[paso.id] ?: paso.texto

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
        mensaje
    ) { pasos, secciones, enElCampo, mensajeActual ->
        val paraTitulo = secciones.map { SeccionParaTitulo(it.id, it.nombreSeccion) }
        EstadoPasos(
            bloques = bloquesDePasos(pasos.map { it.aMostrar() }, paraTitulo),
            secciones = paraTitulo,
            escribiendo = enElCampo,
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
     * Cierra la ayuda y, si se llegó escribiendo `:info:`, lo saca del texto.
     *
     * Dejarlo escrito convertiría un atajo en basura dentro de la receta: `:info:` no es algo que
     * uno quiera leer al seguir los pasos.
     */
    fun cerrarAyuda() {
        val abierto = _dialogo.value as? DialogoPasos.Ayuda
        _dialogo.value = DialogoPasos.Ninguno
        abierto?.atajo?.let { reemplazar(it, "") }
    }

    private fun abrirElegirIngrediente(enCurso: AtajoEnCurso) {
        _dialogo.value = DialogoPasos.ElegirIngrediente(enCurso)
        viewModelScope.launch {
            val nombres = recetas.nombresDeIngredientesDe(recetaId)
            // Se comprueba que siga abierto el mismo: entre pedir la lista y que llegue pudo
            // cerrarse el cuadro o abrirse otro, y rellenar el equivocado mostraría una lista
            // que no corresponde al atajo que la pidió.
            val ahora = _dialogo.value
            if (ahora is DialogoPasos.ElegirIngrediente && ahora.atajo == enCurso) {
                _dialogo.value = ahora.copy(nombres = nombres)
            }
        }
    }

    /** Escribe el ingrediente elegido en lugar del `:ingredientes:`. */
    fun elegirIngrediente(nombre: String) {
        val abierto = _dialogo.value as? DialogoPasos.ElegirIngrediente ?: return
        _dialogo.value = DialogoPasos.Ninguno
        reemplazar(abierto.atajo, nombre)
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

    fun cerrarDialogo() {
        _dialogo.value = DialogoPasos.Ninguno
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
