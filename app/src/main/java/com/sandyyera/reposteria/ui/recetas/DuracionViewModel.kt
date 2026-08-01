package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import com.sandyyera.reposteria.logica.duracion.UnidadDuracion
import com.sandyyera.reposteria.logica.duracion.describirDuracion
import com.sandyyera.reposteria.logica.validaciones.ORDEN_DE_LOS_BLOQUES
import com.sandyyera.reposteria.logica.validaciones.elBloqueDiceAlgo
import com.sandyyera.reposteria.logica.validaciones.errorEnCantidadDeDuracion
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lo escrito en un bloque de duración.
 *
 * Vive en memoria mientras se edita: **el paso no guarda en cada tecla**, a diferencia de
 * los ingredientes. Acá un bloque a medio escribir se ve igual que uno vaciado a propósito,
 * así que guardar por tecla escribiría y borraría filas mientras la persona todavía decide.
 * Lo que dispara el guardado es **salir del campo** (8.4.1).
 */
data class BloqueDeDuracion(
    val tipo: TipoDuracion,
    val apto: Boolean = true,
    val cantidad: String = "",
    val unidad: UnidadDuracion = UnidadDuracion.DIAS,
    val tocado: Boolean = false
) {
    val error: String? get() = errorEnCantidadDeDuracion(cantidad, apto).takeIf { tocado }

    /** Si este bloque tiene algo que guardar. Un "no apto" **sí** lo tiene. */
    val diceAlgo: Boolean get() = elBloqueDiceAlgo(apto, cantidad)

    /** Cómo se va leyendo, en vivo, tal como quedará guardado. */
    val comoSeLee: String
        get() = describirDuracion(
            apto = apto,
            cantidad = cantidad.takeIf { error == null }?.let { textoANumero(it)?.toInt() },
            unidad = unidad
        )
}

/** Lo que el paso de duración necesita para dibujarse. */
data class EstadoDuracion(
    val receta: Receta? = null,
    val bloques: List<BloqueDeDuracion> = ORDEN_DE_LOS_BLOQUES.map { BloqueDeDuracion(it) },
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    /** Si hay algo escrito que no sirve. Vacío no cuenta: el paso puede quedar en blanco. */
    val puedeGuardar: Boolean
        get() = bloques.all { errorEnCantidadDeDuracion(it.cantidad, it.apto) == null }

    /** Si el paso quedó completamente sin llenar, que es un estado válido (8.4). */
    val todoVacio: Boolean get() = bloques.none { it.diceAlgo }
}

/**
 * El cerebro del paso "Duración" (8.4).
 *
 * Es el paso más liviano de la receta y el único que **puede quedar completamente vacío**:
 * no alimenta ninguna cuenta — ni el costo, ni los precios, ni los sueldos, ni las
 * simulaciones. Por eso acá no hay nada que "recalcular en vivo" y las reglas son blandas:
 * lo que se revisa es que lo escrito signifique algo, no que esté completo.
 *
 * **Guarda solo, bloque por bloque** (8.4.1). Se fue el botón de "Guardar duraciones", que
 * era el mismo espejismo que el de rendimiento: al volver los datos seguían ahí porque
 * sobrevivía el ViewModel, no porque estuvieran guardados.
 *
 * Lo que dispara el guardado es **salir del campo**, no cada tecla, y esa diferencia con el
 * paso de cantidades es deliberada: un bloque a medio escribir se ve igual que uno vaciado a
 * propósito, así que guardar por tecla escribiría y borraría filas mientras la persona
 * todavía decide. El switch de "no apto" y el selector de unidad sí guardan al instante:
 * esos no se escriben a medias, se eligen.
 *
 * Cada bloque se guarda **solo**, y no los tres juntos: son independientes, y escribir en
 * uno no tiene por qué tocar las filas de los otros dos.
 */
class DuracionViewModel(
    private val recetaId: Long,
    private val recetas: RecetaRepositorio
) : ViewModel() {

    private val bloques = MutableStateFlow(ORDEN_DE_LOS_BLOQUES.map { BloqueDeDuracion(it) })
    private val mensaje = MutableStateFlow<String?>(null)

    val estado: StateFlow<EstadoDuracion> = combine(
        bloques,
        mensaje,
        // El título **se observa** y no se lee una vez: se renombra desde el paso de
        // cantidades (8.4.1, #3) y este encabezado tiene que enterarse solo.
        recetas.observarReceta(recetaId)
    ) { losBloques, mensajeActual, laReceta ->
        EstadoDuracion(
            receta = laReceta,
            bloques = losBloques,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoDuracion()
    )

    init {
        viewModelScope.launch {
            val guardadas = recetas.obtenerDuraciones(recetaId)
            // Los tipos que no están son los que nadie llenó: su bloque queda en blanco.
            bloques.value = ORDEN_DE_LOS_BLOQUES.map { tipo ->
                val fila = guardadas[tipo] ?: return@map BloqueDeDuracion(tipo)
                BloqueDeDuracion(
                    tipo = tipo,
                    apto = fila.apto,
                    cantidad = fila.cantidad?.toString().orEmpty(),
                    unidad = fila.unidad ?: UnidadDuracion.DIAS,
                    tocado = true
                )
            }
        }
    }

    fun cambiarApto(tipo: TipoDuracion, apto: Boolean) {
        // Lo escrito **no se borra** al marcar "no apto": si fue un toque por error, volver
        // a marcarlo apto devuelve el número. Lo que no se guarda es otra cosa, y eso lo
        // decide el repositorio.
        enBloque(tipo) { it.copy(apto = apto, tocado = true) }
        // Al instante: un switch no se toca a medias, se elige.
        guardarBloque(tipo)
    }

    fun cambiarCantidad(tipo: TipoDuracion, texto: String) = enBloque(tipo) {
        // Solo dígitos: las duraciones son enteras y el punto de mil no aplica a "99 meses".
        // **No guarda acá**: eso pasa al salir del campo (ver [guardarBloque]).
        it.copy(cantidad = texto.filter { caracter -> caracter.isDigit() }, tocado = true)
    }

    fun cambiarUnidad(tipo: TipoDuracion, unidad: UnidadDuracion) {
        enBloque(tipo) { it.copy(unidad = unidad, tocado = true) }
        // Al instante, por lo mismo que el switch: es una elección, no algo que se teclea.
        guardarBloque(tipo)
    }

    /**
     * Guarda **un** bloque, si lo que tiene escrito sirve.
     *
     * La llama la pantalla al salir del campo, y el propio ViewModel al cambiar el switch o
     * la unidad. Con un número inválido no escribe nada y deja el error bajo el campo, que ya
     * lo calcula el bloque: no hace falta avisar dos veces de lo mismo.
     *
     * **No anuncia el éxito.** Sin botón que apretar, un "se guardó" cada vez que se sale de
     * un campo es ruido — y encima aparecería justo mientras se pasa al bloque siguiente.
     * Solo se dice lo que salió mal, que es lo único que pide una decisión.
     */
    fun guardarBloque(tipo: TipoDuracion) {
        val bloque = bloques.value.firstOrNull { it.tipo == tipo } ?: return
        if (errorEnCantidadDeDuracion(bloque.cantidad, bloque.apto) != null) return

        viewModelScope.launch {
            val r = recetas.guardarDuracion(
                recetaId = recetaId,
                tipo = bloque.tipo,
                apto = bloque.apto,
                cantidadTexto = bloque.cantidad,
                unidad = bloque.unidad
            )
            (r as? Resultado.NoSePudo)?.let { mensaje.value = it.motivo }
        }
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun enBloque(tipo: TipoDuracion, cambio: (BloqueDeDuracion) -> BloqueDeDuracion) {
        bloques.update { lista ->
            lista.map { if (it.tipo == tipo) cambio(it) else it }
        }
    }

    companion object {
        fun fabrica(recetaId: Long, recetas: RecetaRepositorio): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { DuracionViewModel(recetaId, recetas) }
            }
    }
}
