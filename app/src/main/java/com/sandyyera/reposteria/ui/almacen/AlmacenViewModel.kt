package com.sandyyera.reposteria.ui.almacen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.dao.ArticuloConValor
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.repositorio.AlmacenRepositorio
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.logica.busqueda.filtrarPor
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreEscrito
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Una fila del almacén lista para dibujarse (sección 14).
 *
 * Envuelve lo que devuelve la consulta y le agrega **cómo se lee**, que es lo único que la
 * pantalla necesita decidir y no debería decidir ella: la unidad y el valor salen de si el
 * artículo está enlazado a un ingrediente o no, y esa regla tiene que estar en un solo lugar.
 */
data class FilaDeAlmacen(val articulo: ArticuloConValor) {

    val id: Long get() = articulo.id
    val nombre: String get() = articulo.nombre

    /** Si viene del catálogo de ingredientes. De eso dependen la unidad y el valor. */
    val esIngrediente: Boolean get() = articulo.ingredienteId != null

    /** "2.500 g" o "3 unidades". La unidad se deduce, no se guarda (ver `ArticuloDeAlmacen`). */
    val cuantoQueda: String
        get() = if (esIngrediente) {
            "${formatearNumero(articulo.cantidad)} g"
        } else {
            val cuantas = formatearNumero(articulo.cantidad)
            "$cuantas ${if (articulo.cantidad == 1.0) "unidad" else "unidades"}"
        }

    /**
     * Lo que vale lo que queda, o `null` si no se puede saber.
     *
     * Es `null` en los artículos sueltos y **eso no es un hueco**: una caja no tiene valor por
     * gramo, y poner un 0 diría que no vale nada, que es otra cosa. La pantalla muestra la
     * cantidad igual; lo único que falta es el peso.
     */
    val valor: Double?
        get() = articulo.valorPorGramo?.let { it * articulo.cantidad }
}

/** Qué hay abierto encima del almacén. */
sealed interface DialogoAlmacen {

    data object Ninguno : DialogoAlmacen

    /**
     * Agregar algo al almacén: un ingrediente del catálogo o un artículo suelto.
     *
     * Los dos caminos van en **un solo cuadro con dos pestañas** y no en dos botones distintos,
     * porque desde afuera son la misma acción —"anotar algo que tengo"— y cuál de los dos
     * corresponde se sabe recién al buscarlo: uno va a poner "Harina", no la encuentra entre los
     * ingredientes, y ahí se da cuenta de que era un artículo suelto. Con dos botones habría que
     * cerrar y volver a empezar.
     */
    data class Agregar(
        val suelto: Boolean = false,
        val busqueda: String = "",
        val candidatos: List<Ingrediente> = emptyList(),
        val yaGuardados: Set<Long> = emptySet(),
        val elegido: Ingrediente? = null,
        val nombreSuelto: String = "",
        val cantidad: String = "",
        val tocado: Boolean = false,
        val guardando: Boolean = false,
        val rechazo: String? = null
    ) : DialogoAlmacen {

        val visibles: List<Ingrediente>
            get() = filtrarPor(candidatos, busqueda) { it.nombre }

        /** Por qué no se puede elegir este ingrediente, o `null`. Lo pinta `ComboBuscable`. */
        fun motivoNoDisponible(ingrediente: Ingrediente): String? =
            if (ingrediente.id in yaGuardados) "Ya está en el almacén" else null

        val errorNombre: String?
            get() = rechazo ?: errorEnNombreEscrito(nombreSuelto).takeIf { suelto && tocado }

        /**
         * La cantidad **acepta el 0 a propósito**: "no queda nada" es justo el dato que uno
         * viene a anotar antes de salir a comprar, y rechazarlo obligaría a borrar la fila para
         * decirlo — perdiendo de paso que ese artículo existe.
         */
        val errorCantidad: String?
            get() = when {
                !tocado -> null
                cantidad.isBlank() -> "Escribe cuánto queda"
                textoANumero(cantidad) == null -> "Eso no es un número"
                else -> null
            }

        val puedeGuardar: Boolean
            get() = !guardando &&
                textoANumero(cantidad) != null &&
                if (suelto) errorEnNombreEscrito(nombreSuelto) == null else elegido != null
    }

    /** Cambiar cuánto queda de algo. Es lo que se hace todos los días. */
    data class CambiarCantidad(
        val fila: FilaDeAlmacen,
        val cantidad: String,
        val guardando: Boolean = false
    ) : DialogoAlmacen {
        val puedeGuardar: Boolean get() = textoANumero(cantidad) != null && !guardando
    }

    /** La advertencia antes de sacar algo del almacén (6.3). */
    data class ConfirmarBorrado(
        val fila: FilaDeAlmacen,
        val borrando: Boolean = false
    ) : DialogoAlmacen
}

/** Lo que la pantalla del almacén necesita para dibujarse. */
data class EstadoAlmacen(
    val visibles: List<FilaDeAlmacen> = emptyList(),
    val hayArticulos: Boolean = false,
    val busqueda: String = "",
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    val almacenVacio: Boolean get() = !cargando && !hayArticulos
    val busquedaSinResultados: Boolean get() = hayArticulos && visibles.isEmpty()

    /**
     * Lo que vale todo lo que hay guardado.
     *
     * Suma **solo lo que tiene valor**: los artículos sueltos no aportan, y eso se dice al lado
     * en vez de contarlos como 0. Un total que se presenta como "el valor del almacén" mientras
     * ignora en silencio la mitad de las filas es un número que se cree y está mal.
     */
    val valorTotal: Double get() = visibles.sumOf { it.valor ?: 0.0 }

    /** Cuántas filas quedaron fuera del total por no tener valor. */
    val sinValor: Int get() = visibles.count { it.valor == null }
}

/**
 * El cerebro del almacén (sección 14).
 *
 * Mismo patrón que las otras secciones: `combine` de tres fuentes, `WhileSubscribed(5s)` y el
 * diálogo por su propio canal (12.2.1), que acá pesa porque el cuadro de agregar tiene campos de
 * texto y un buscador.
 */
class AlmacenViewModel(
    private val almacen: AlmacenRepositorio,
    private val ingredientes: IngredienteRepositorio
) : ViewModel() {

    private val busqueda = MutableStateFlow("")
    private val _dialogo = MutableStateFlow<DialogoAlmacen>(DialogoAlmacen.Ninguno)
    private val mensaje = MutableStateFlow<String?>(null)

    val dialogo: StateFlow<DialogoAlmacen> = _dialogo

    val estado: StateFlow<EstadoAlmacen> = combine(
        almacen.observarTodo(),
        busqueda,
        mensaje
    ) { articulos, textoBuscado, mensajeActual ->
        val filas = articulos.map { FilaDeAlmacen(it) }
        EstadoAlmacen(
            visibles = filtrarPor(filas, textoBuscado) { it.nombre },
            hayArticulos = filas.isNotEmpty(),
            busqueda = textoBuscado,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoAlmacen()
    )

    fun buscar(texto: String) {
        busqueda.value = texto
    }

    /**
     * Abre el cuadro de agregar y va a buscar el catálogo.
     *
     * Las dos consultas van acá y no en la pantalla porque dependen de lo que hay en la base,
     * no de lo que se esté viendo. El cuadro se abre al instante y se rellena cuando vuelven.
     */
    fun abrirAgregar() {
        _dialogo.value = DialogoAlmacen.Agregar()
        viewModelScope.launch {
            // `first()` sobre el flujo del catálogo y no un observador: mientras el cuadro
            // está abierto esa lista no cambia —crear un ingrediente desde acá no se puede— así
            // que observarla solo agregaría una fuente que reemite justo mientras se escribe en
            // un campo de texto, que es lo que 12.2.1 pide evitar.
            val catalogo = ingredientes.observarTodos().first()
            val guardados = almacen.ingredientesYaGuardados()
            enAgregar { it.copy(candidatos = catalogo, yaGuardados = guardados) }
        }
    }

    /** Cambia entre "un ingrediente" y "otra cosa" sin cerrar el cuadro. */
    fun cambiarTipoDeArticulo(suelto: Boolean) = enAgregar {
        it.copy(suelto = suelto, rechazo = null)
    }

    fun buscarIngrediente(texto: String) = enAgregar { it.copy(busqueda = texto) }

    fun elegirIngrediente(ingrediente: Ingrediente) = enAgregar {
        it.copy(elegido = ingrediente, rechazo = null)
    }

    fun cambiarNombreSuelto(texto: String) = enAgregar {
        it.copy(nombreSuelto = texto, tocado = true, rechazo = null)
    }

    fun cambiarCantidadEscrita(texto: String) = enAgregar {
        it.copy(cantidad = formatearMientrasSeEscribe(texto), tocado = true)
    }

    fun guardarNuevo() {
        val actual = _dialogo.value as? DialogoAlmacen.Agregar ?: return
        if (!actual.puedeGuardar) return
        val cuanto = textoANumero(actual.cantidad) ?: return

        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            val resultado = if (actual.suelto) {
                almacen.agregarArticuloSuelto(actual.nombreSuelto, cuanto)
            } else {
                almacen.agregarIngrediente(actual.elegido ?: return@launch, cuanto)
            }
            when (resultado) {
                is Resultado.Listo -> _dialogo.value = DialogoAlmacen.Ninguno
                // Dentro del cuadro y no en la franja de abajo: es sobre lo que se acaba de
                // elegir o escribir, y con el teclado abierto esa franja queda tapada (8.2).
                is Resultado.NoSePudo ->
                    enAgregar { it.copy(guardando = false, rechazo = resultado.motivo) }
            }
        }
    }

    /**
     * Abre el cuadro de cambiar la cantidad, con lo que hay puesto.
     *
     * Viene relleno y no vacío a propósito: lo normal es corregir —"quedaban 2 kilos, ahora
     * 1,5"— y no anotar desde cero. Con el campo vacío habría que recordar cuánto había.
     */
    fun abrirCambiarCantidad(fila: FilaDeAlmacen) {
        _dialogo.value = DialogoAlmacen.CambiarCantidad(
            fila = fila,
            cantidad = formatearNumero(fila.articulo.cantidad)
        )
    }

    fun cambiarCantidadEnEdicion(texto: String) {
        _dialogo.update { actual ->
            if (actual is DialogoAlmacen.CambiarCantidad) {
                actual.copy(cantidad = formatearMientrasSeEscribe(texto))
            } else {
                actual
            }
        }
    }

    fun guardarCantidad() {
        val actual = _dialogo.value as? DialogoAlmacen.CambiarCantidad ?: return
        if (!actual.puedeGuardar) return
        val cuanto = textoANumero(actual.cantidad) ?: return

        viewModelScope.launch {
            when (val r = almacen.cambiarCantidad(actual.fila.id, cuanto)) {
                is Resultado.Listo -> Unit
                is Resultado.NoSePudo -> mensaje.value = r.motivo
            }
            _dialogo.value = DialogoAlmacen.Ninguno
        }
    }

    fun pedirBorrado(fila: FilaDeAlmacen) {
        _dialogo.value = DialogoAlmacen.ConfirmarBorrado(fila)
    }

    fun confirmarBorrado() {
        val aviso = _dialogo.value as? DialogoAlmacen.ConfirmarBorrado ?: return
        if (aviso.borrando) return

        _dialogo.value = aviso.copy(borrando = true)

        viewModelScope.launch {
            almacen.eliminar(aviso.fila.id, aviso.fila.nombre)
            _dialogo.value = DialogoAlmacen.Ninguno
            mensaje.value = "Se sacó '${aviso.fila.nombre}' del almacén"
        }
    }

    fun cerrarDialogo() {
        _dialogo.value = DialogoAlmacen.Ninguno
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun enAgregar(cambio: (DialogoAlmacen.Agregar) -> DialogoAlmacen.Agregar) {
        _dialogo.update { actual ->
            if (actual is DialogoAlmacen.Agregar) cambio(actual) else actual
        }
    }

    companion object {
        fun fabrica(
            almacen: AlmacenRepositorio,
            ingredientes: IngredienteRepositorio
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AlmacenViewModel(almacen, ingredientes) }
        }
    }
}
