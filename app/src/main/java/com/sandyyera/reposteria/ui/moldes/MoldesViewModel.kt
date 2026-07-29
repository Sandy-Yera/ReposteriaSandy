package com.sandyyera.reposteria.ui.moldes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.Molde
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.MoldeRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoGuardarMolde
import com.sandyyera.reposteria.logica.busqueda.filtrarPor
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.validaciones.CampoDeMolde
import com.sandyyera.reposteria.logica.validaciones.ErroresMolde
import com.sandyyera.reposteria.logica.validaciones.camposDe
import com.sandyyera.reposteria.logica.validaciones.dimensionesDesde
import com.sandyyera.reposteria.logica.validaciones.revisarMolde
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cómo se lee cada forma en la pantalla. */
fun nombreDeLaForma(forma: TipoFormaMolde): String = when (forma) {
    TipoFormaMolde.RECTANGULO -> "Rectángulo"
    TipoFormaMolde.CUADRADO -> "Cuadrado"
    TipoFormaMolde.CIRCULO -> "Círculo"
    TipoFormaMolde.TRIANGULO -> "Triángulo"
    TipoFormaMolde.EXOTICO -> "Otra forma"
}

/**
 * El texto de ayuda de una forma, o `null` si no necesita.
 *
 * Solo la exótica lo lleva: es la única donde lo que se pide no se mide con una regla, y
 * sin explicación nadie adivina que el volumen se saca llenando el molde con agua.
 */
fun ayudaDeLaForma(forma: TipoFormaMolde): String? = when (forma) {
    TipoFormaMolde.EXOTICO ->
        "Para un molde con forma irregular (estrella, corazón): llénalo de agua, " +
            "viértela en una jarra medidora y anota los mililitros. Son los mismos cm³."
    else -> null
}

/** Qué hay abierto encima del catálogo de moldes. */
sealed interface DialogoMolde {

    data object Ninguno : DialogoMolde

    /**
     * El formulario de alta o de edición.
     *
     * [editando] es `null` al crear. Las medidas van en un mapa por la misma razón que en
     * `ErroresMolde`: cuáles existen depende de la forma, y lo que se escribió para una
     * forma que después se cambió no se borra — si vuelve a elegirla, ahí está.
     */
    data class Formulario(
        val editando: Molde? = null,
        val nombre: String = "",
        val forma: TipoFormaMolde? = null,
        val medidas: Map<CampoDeMolde, String> = emptyMap(),
        val tocado: Boolean = false,
        val guardando: Boolean = false,
        val rechazo: String? = null
    ) : DialogoMolde {

        /** Las medidas que hay que pedir ahora mismo. Vacía mientras no haya forma elegida. */
        val campos: List<CampoDeMolde> get() = forma?.let { camposDe(it) } ?: emptyList()

        private val errores: ErroresMolde get() = revisarMolde(nombre, forma, medidas)

        /**
         * [rechazo] va primero, igual que en las secciones: es lo que contestó el
         * repositorio (un nombre repetido) y el aviso tiene que verse junto al campo, no en
         * la franja de abajo que el teclado tapa.
         */
        val errorNombre: String? get() = rechazo ?: errores.nombre.takeIf { tocado }

        val errorForma: String? get() = errores.forma.takeIf { tocado }

        /** El error de una medida, o `null`. Antes de tocar nada no se reta por vacíos. */
        fun errorDe(campo: CampoDeMolde): String? = errores.medidas[campo].takeIf { tocado }

        val puedeGuardar: Boolean get() = errores.sirve && !guardando

        /**
         * El área y el volumen que van quedando, o `null` mientras falte algo.
         *
         * Se muestran **mientras se escribe** y no al guardar: son la única forma de darse
         * cuenta ahí mismo de que se anotó un 3 en vez de un 30. No dependen del nombre,
         * a propósito — ver `dimensionesDesde`.
         */
        val vistaPrevia: Pair<Double, Double>?
            get() = dimensionesDesde(forma, medidas)?.let { it.areaCm2 to it.volumenCm3 }
    }

    /**
     * La advertencia previa a borrar (6.3).
     *
     * [recetasAfectadas] es `null` mientras se consulta y lista vacía cuando no lo usa
     * ninguna receta, misma distinción que en ingredientes. Acá el aviso es más suave a
     * propósito: borrar un molde **no rompe** esas recetas, solo las desenlaza.
     */
    data class ConfirmarBorrado(
        val molde: Molde,
        val recetasAfectadas: List<Receta>? = null,
        val borrando: Boolean = false
    ) : DialogoMolde
}

/** Lo que el catálogo de moldes necesita para dibujarse. */
data class EstadoMoldes(
    val visibles: List<Molde> = emptyList(),
    val hayMoldes: Boolean = false,
    val busqueda: String = "",
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    val catalogoVacio: Boolean get() = !cargando && !hayMoldes
    val busquedaSinResultados: Boolean get() = hayMoldes && visibles.isEmpty()
}

/**
 * El cerebro del catálogo de moldes (9.2).
 *
 * Mismo patrón que ingredientes y recetas. Lo propio de acá es que el formulario cambia de
 * forma según lo que se elija: qué campos pedir sale de `camposDe`, en `logica/`, y no de
 * un `when` escrito en el Composable — así la pantalla y la validación no pueden discrepar.
 */
class MoldesViewModel(
    private val repositorio: MoldeRepositorio
) : ViewModel() {

    private val busqueda = MutableStateFlow("")
    private val mensaje = MutableStateFlow<String?>(null)
    private val _dialogo = MutableStateFlow<DialogoMolde>(DialogoMolde.Ninguno)

    /**
     * Lo que hay abierto encima, por su propio canal.
     *
     * Por la misma razón de siempre (12.2.1): el formulario tiene cinco campos de texto, y
     * un campo que recibe su valor con retraso termina con el cursor donde no va.
     */
    val dialogo: StateFlow<DialogoMolde> = _dialogo

    val estado: StateFlow<EstadoMoldes> = combine(
        repositorio.observarTodos(),
        busqueda,
        mensaje
    ) { todos, textoBuscado, mensajeActual ->
        EstadoMoldes(
            visibles = filtrarPor(todos, textoBuscado) { it.nombre },
            hayMoldes = todos.isNotEmpty(),
            busqueda = textoBuscado,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoMoldes()
    )

    fun buscar(texto: String) {
        busqueda.value = texto
    }

    fun abrirAlta() {
        _dialogo.value = DialogoMolde.Formulario()
    }

    /**
     * Abre el formulario con lo que ya tiene el molde.
     *
     * Las medidas vuelven a texto con `formatearNumero`, que es el mismo formato que
     * `textoANumero` sabe leer de vuelta: así editar y guardar sin cambiar nada no altera
     * ningún número.
     */
    fun abrirEdicion(molde: Molde) {
        val d = molde.dimensiones
        val escritas = mapOf(
            CampoDeMolde.LARGO to d.largoCm,
            CampoDeMolde.ANCHO to d.anchoCm,
            CampoDeMolde.LADO to d.ladoCm,
            CampoDeMolde.DIAMETRO to d.diametroCm,
            CampoDeMolde.BASE_TRIANGULO to d.baseTrianguloCm,
            CampoDeMolde.ALTURA_TRIANGULO to d.alturaTrianguloCm,
            CampoDeMolde.VOLUMEN_EXOTICO to d.volumenExoticoCm3,
            CampoDeMolde.ALTURA_MOLDE to d.alturaMoldeCm
        ).mapNotNull { (campo, valor) -> valor?.let { campo to formatearNumero(it) } }.toMap()

        _dialogo.value = DialogoMolde.Formulario(
            editando = molde,
            nombre = molde.nombre,
            forma = d.tipoForma,
            medidas = escritas,
            tocado = true
        )
    }

    fun cambiarNombre(texto: String) = enFormulario {
        it.copy(nombre = texto, tocado = true, rechazo = null)
    }

    /**
     * Cambia la forma **sin borrar lo ya escrito** para las otras.
     *
     * Quien probó "círculo", anotó el diámetro y pasa a "cuadrado" para comparar, al volver
     * encuentra su diámetro donde lo dejó. Lo que no se pide para la forma actual no se
     * valida ni se guarda —eso lo resuelven `revisarMolde` y `dimensionesDesde`—, así que
     * conservarlo no cuesta nada y ahorra volver a medir.
     */
    fun elegirForma(forma: TipoFormaMolde) = enFormulario {
        it.copy(forma = forma, tocado = true)
    }

    fun cambiarMedida(campo: CampoDeMolde, texto: String) = enFormulario {
        // Por el mismo camino que el resto de los campos numéricos: el punto de mil lo pone
        // el ViewModel, no el Composable.
        it.copy(medidas = it.medidas + (campo to formatearMientrasSeEscribe(texto)), tocado = true)
    }

    fun guardar() {
        val formulario = _dialogo.value as? DialogoMolde.Formulario ?: return
        if (!formulario.puedeGuardar) return

        _dialogo.value = formulario.copy(guardando = true, tocado = true)

        viewModelScope.launch {
            val editando = formulario.editando
            val resultado = if (editando == null) {
                repositorio.crear(formulario.nombre, formulario.forma, formulario.medidas)
            } else {
                repositorio.actualizar(
                    editando.id,
                    formulario.nombre,
                    formulario.forma,
                    formulario.medidas
                )
            }

            when (resultado) {
                is ResultadoGuardarMolde.Guardado -> {
                    _dialogo.value = DialogoMolde.Ninguno
                    mensaje.value = "Se guardó '${formulario.nombre.trim()}'"
                }
                is ResultadoGuardarMolde.YaExiste -> enFormulario {
                    it.copy(
                        guardando = false,
                        rechazo = "Ya tienes un molde '${resultado.existente.nombre}'"
                    )
                }
                is ResultadoGuardarMolde.NoValido -> enFormulario {
                    // El formulario ya calcula sus propios errores por campo; lo único que
                    // hace falta es dejar de estar guardando y que se vean.
                    it.copy(guardando = false, tocado = true)
                }
            }
        }
    }

    /**
     * Abre la advertencia de inmediato y completa la lista cuando vuelve la consulta.
     *
     * Igual que en ingredientes: esperar la consulta para recién abrir el cuadro haría
     * parecer que el botón no responde.
     */
    fun pedirBorrado(molde: Molde) {
        _dialogo.value = DialogoMolde.ConfirmarBorrado(molde)
        viewModelScope.launch {
            val afectadas = repositorio.recetasAfectadasPorBorrar(molde.id)
            _dialogo.update { actual ->
                // Se comprueba que siga abierto y sea el mismo molde: entre la consulta y
                // la respuesta puede haberse cerrado o abierto otro.
                if (actual is DialogoMolde.ConfirmarBorrado && actual.molde.id == molde.id) {
                    actual.copy(recetasAfectadas = afectadas)
                } else {
                    actual
                }
            }
        }
    }

    fun confirmarBorrado() {
        val aviso = _dialogo.value as? DialogoMolde.ConfirmarBorrado ?: return
        if (aviso.borrando) return

        _dialogo.value = aviso.copy(borrando = true)

        viewModelScope.launch {
            repositorio.confirmarEliminacion(aviso.molde.id)
            _dialogo.value = DialogoMolde.Ninguno
            mensaje.value = "Se eliminó '${aviso.molde.nombre}'"
        }
    }

    fun cerrarDialogo() {
        _dialogo.value = DialogoMolde.Ninguno
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun enFormulario(cambio: (DialogoMolde.Formulario) -> DialogoMolde.Formulario) {
        _dialogo.update { actual ->
            if (actual is DialogoMolde.Formulario) cambio(actual) else actual
        }
    }

    companion object {
        fun fabrica(repositorio: MoldeRepositorio): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { MoldesViewModel(repositorio) }
            }
    }
}
