package com.sandyyera.reposteria.ui.almacen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.dao.ArticuloConValor
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.repositorio.AlmacenRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoAgregarAlAlmacen
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.logica.almacen.loQueQueda
import com.sandyyera.reposteria.logica.almacen.seUsoDeMas
import com.sandyyera.reposteria.logica.busqueda.filtrarPor
import com.sandyyera.reposteria.logica.calculadora.UnidadDeCompra
import com.sandyyera.reposteria.logica.calculadora.valorPorGramo
import com.sandyyera.reposteria.logica.formato.cantidadConUnidad
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreEscrito
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Una fila del almacén lista para dibujarse (sección 14).
 *
 * Envuelve lo que devuelve la consulta y le agrega **cómo se lee**, que es lo único que la
 * pantalla necesita decidir y no debería decidir ella: la unidad y el valor salen del ingrediente
 * enlazado, y esa regla tiene que estar en un solo lugar.
 */
data class FilaDeAlmacen(val articulo: ArticuloConValor) {

    val id: Long get() = articulo.id
    val nombre: String get() = articulo.nombre
    val cantidad: Double get() = articulo.cantidad
    val detalles: String? get() = articulo.detalles

    /**
     * Si se cuenta por unidad en vez de por gramo.
     *
     * Una fila vieja sin ingrediente (ver la migración 7 → 8) cuenta como objeto: eso era
     * exactamente lo que un artículo suelto significaba antes de 14.5.
     */
    val esObjeto: Boolean get() = articulo.esObjeto ?: true

    /** "g" o "unidad", para escribirlo al lado de una cantidad. */
    val unidad: String get() = if (esObjeto) "unidad" else "g"

    /** "2.500 g" o "3 unidades". La unidad sale del ingrediente, no se guarda acá. */
    val cuantoQueda: String get() = cantidadConUnidad(articulo.cantidad, esObjeto)

    /**
     * Lo que vale lo que queda, o `null` si no se puede saber.
     *
     * Es `null` en las filas viejas que quedaron sin ingrediente, y **eso no es un hueco**: sin
     * precio por unidad no hay nada que multiplicar, y poner un 0 diría que no vale nada, que es
     * otra cosa. La pantalla muestra la cantidad igual; lo único que falta es el peso.
     */
    val valor: Double?
        get() = articulo.valorPorGramo?.let { it * articulo.cantidad }
}

/**
 * Cómo se está editando la cantidad de algo (14.7).
 *
 * Las dos formas existen porque responden a dos situaciones distintas y ninguna reemplaza a la
 * otra: [MANUAL] es para cuando uno mira el frasco y estima cuánto queda; [CALCULADORA] para
 * cuando se sabe **cuánto se usó** y la resta la hace la app.
 */
enum class ModoDeEdicion { MANUAL, CALCULADORA }

/** Qué hay abierto encima del almacén. */
sealed interface DialogoAlmacen {

    data object Ninguno : DialogoAlmacen

    /**
     * Anotar algo que hay: un ingrediente de cocina o cualquier otra cosa (14.5 y 14.6).
     *
     * **Es un solo cuadro y no dos caminos**, porque desde afuera es una sola acción y cuál de
     * los dos casos es se sabe recién al escribir el nombre. Lo que antes eran dos pestañas
     * ("un ingrediente" / "otra cosa") ahora son dos casillas que se pueden marcar por separado:
     * una caja de torta es un objeto y **sí** va en recetas; una vela decorativa es un objeto y
     * no. Con pestañas esas dos cosas caían en el mismo cajón.
     *
     * [existente] es el ingrediente del catálogo que se llama igual, cuando lo hay. No bloquea:
     * anotar en el almacén algo que ya está en ingredientes es el caso normal — es la mitad del
     * punto de conectar las dos secciones.
     */
    data class Agregar(
        val nombre: String = "",
        val esObjeto: Boolean = false,
        val vaEnRecetas: Boolean = true,
        val cantidad: String = "",
        val precio: String = "",
        val detalles: String = "",
        val tocado: Boolean = false,
        val guardando: Boolean = false,
        val rechazo: String? = null,
        val precioEnDisputa: PrecioEnDisputa? = null
    ) : DialogoAlmacen {

        val unidad: String get() = if (esObjeto) "unidad" else "g"

        val errorNombre: String?
            get() = rechazo ?: errorEnNombreEscrito(nombre).takeIf { tocado }

        /**
         * La cantidad **acepta el 0 a propósito**: "no queda nada" es justo el dato que uno viene
         * a anotar antes de salir a comprar, y rechazarlo obligaría a borrar la fila para
         * decirlo — perdiendo de paso que ese artículo existe.
         */
        val errorCantidad: String?
            get() = when {
                !tocado -> null
                cantidad.isBlank() -> "Escribe cuánto tienes"
                textoANumero(cantidad) == null -> "Eso no es un número"
                else -> null
            }

        /**
         * El precio también acepta el 0, y por un motivo distinto al de la cantidad: hay cosas
         * que uno anota sin saber lo que costaron —un regalo, algo que ya estaba— y obligar a
         * inventar una cifra sería peor que dejarla en cero y arreglarla después.
         */
        val errorPrecio: String?
            get() = when {
                !tocado -> null
                precio.isBlank() -> "Escribe cuánto te costó"
                textoANumero(precio) == null -> "Eso no es un número"
                textoANumero(precio)!! < 0 -> "El precio no puede ser negativo"
                else -> null
            }

        /**
         * Lo que cuesta **una unidad de medida**, sacado de lo que costó todo (14.5.2).
         *
         * Sandy pidió escribir lo que costó el producto y no el precio por gramo: *"de esta
         * forma, se hará la conversión al gramo"*. Es la misma cuenta que ya hace la calculadora
         * de la sección Ingredientes (7.2), reutilizada — no una división escrita otra vez acá.
         *
         * Es `null` mientras no se pueda dividir: sin cantidad no hay por cuánto dividir, y con
         * cantidad 0 la división no existe. Eso **no impide guardar**: anotar algo de lo que no
         * queda nada es un caso válido, y el precio se completa cuando se reponga.
         */
        val valorPorUnidad: Double?
            get() {
                val cuanto = textoANumero(cantidad) ?: return null
                val pagado = textoANumero(precio) ?: return null
                if (cuanto <= 0 || pagado < 0) return null
                return valorPorGramo(pagado, cuanto, UnidadDeCompra.GRAMO)
            }

        /** La cuenta escrita, para verla antes de guardar (8.7.1). `null` si todavía no se puede. */
        val comoSeLeeLaCuenta: String?
            get() {
                val cada = valorPorUnidad ?: return null
                return "${formatearNumero(textoANumero(precio)!!)} entre " +
                    "${formatearNumero(textoANumero(cantidad)!!)} $unidad = " +
                    "$${formatearNumero(cada)} por $unidad"
            }

        /**
         * Por qué no se puede sacar el precio por unidad, cuando no se puede.
         *
         * Se dice en vez de dejar el hueco: guardar un valor 0 sin explicar por qué haría que la
         * receta costara de menos sin que nada lo indicara.
         */
        val porQueNoHayCuenta: String?
            get() {
                if (valorPorUnidad != null) return null
                val cuanto = textoANumero(cantidad) ?: return null
                if (cuanto > 0) return null
                return "Con 0 no puedo sacar el precio por $unidad. Se guarda en 0 y lo " +
                    "arreglas cuando repongas."
            }

        val puedeGuardar: Boolean
            get() = !guardando &&
                precioEnDisputa == null &&
                errorEnNombreEscrito(nombre) == null &&
                errorPrecio == null &&
                textoANumero(cantidad) != null &&
                textoANumero(precio) != null
    }

    /**
     * Cambiar cuánto queda de algo. Es lo que se hace todos los días (14.7).
     *
     * Guarda **los dos campos por separado** —[cantidad] para el modo manual y [seUso] para la
     * calculadora— y no uno solo que cambia de significado. Compartirlo haría que cambiar de modo
     * reinterpretara lo ya escrito: un "500" puesto como "queda" se leería de golpe como "usé",
     * y el número que se guarda sería otro sin que nadie tocara nada.
     */
    data class CambiarCantidad(
        val fila: FilaDeAlmacen,
        val modo: ModoDeEdicion = ModoDeEdicion.MANUAL,
        val cantidad: String,
        val seUso: String = "",
        val detalles: String,
        val guardando: Boolean = false
    ) : DialogoAlmacen {

        /** Lo que se va a guardar, venga del modo que venga. `null` si todavía no es un número. */
        val resultado: Double?
            get() = when (modo) {
                ModoDeEdicion.MANUAL -> textoANumero(cantidad)
                ModoDeEdicion.CALCULADORA ->
                    textoANumero(seUso)?.let { loQueQueda(fila.cantidad, it) }
            }

        /**
         * Si en la calculadora se usó más de lo que había anotado.
         *
         * **No impide guardar**: la cuenta queda en cero igual, que es lo que de verdad hay en el
         * estante. Se avisa para que no parezca un error de la app — o se anotó mal antes, o se
         * usó de otro paquete, y las dos cosas son datos que conviene ver.
         */
        val seFueDeRango: Boolean
            get() = modo == ModoDeEdicion.CALCULADORA &&
                textoANumero(seUso)?.let { seUsoDeMas(fila.cantidad, it) } == true

        val puedeGuardar: Boolean get() = resultado != null && !guardando
    }

    /** La advertencia antes de sacar algo del almacén (6.3). */
    data class ConfirmarBorrado(
        val fila: FilaDeAlmacen,
        val borrando: Boolean = false
    ) : DialogoAlmacen
}

/**
 * El precio escrito no coincide con el que ya tenía el ingrediente (7.2 y 14.5).
 *
 * Se muestran **los dos** y se pregunta antes de reemplazar, como pidió Sandy, porque cambiarlo
 * mueve el costo de **todas** las recetas que usan ese ingrediente y no se deshace. Esta es la
 * única pantalla donde los dos números se pueden comparar antes de que el viejo desaparezca.
 */
data class PrecioEnDisputa(
    val existente: Ingrediente,
    val valorGuardado: Double,
    val valorEscrito: Double
)

/** Lo que la pantalla del almacén necesita para dibujarse. */
data class EstadoAlmacen(
    val visibles: List<FilaDeAlmacen> = emptyList(),
    /**
     * Todo lo guardado, **sin filtrar por el buscador**.
     *
     * Existe porque el valor del almacén se sacaba de [visibles], y eso lo hacía cambiar al
     * escribir en el buscador: la tarjeta dice "Valor de lo guardado" y mostraba el de lo que
     * quedó a la vista. Un número que se mueve al buscar es un número que no se puede creer, y es
     * el mismo error que el total ya evitaba por otro lado al decir cuántas filas no incluye.
     */
    val todo: List<FilaDeAlmacen> = emptyList(),
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
     * Suma **solo lo que tiene valor**, y eso se dice al lado en vez de contar el resto como 0.
     * Un total que se presenta como "el valor del almacén" mientras ignora en silencio algunas
     * filas es un número que se cree y está mal.
     */
    val valorTotal: Double get() = todo.sumOf { it.valor ?: 0.0 }

    /** Cuántas filas quedaron fuera del total por no tener valor. */
    val sinValor: Int get() = todo.count { it.valor == null }
}

/**
 * El cerebro del almacén (sección 14).
 *
 * Mismo patrón que las otras secciones: `combine` de tres fuentes, `WhileSubscribed(5s)` y el
 * diálogo por su propio canal (12.2.1), que acá pesa porque los dos cuadros son casi todos campos
 * de texto.
 */
class AlmacenViewModel(
    private val almacen: AlmacenRepositorio
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
            todo = filas,
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

    // --- Agregar (14.5 y 14.6) ---

    fun abrirAgregar() {
        _dialogo.value = DialogoAlmacen.Agregar()
    }

    fun cambiarNombre(texto: String) = enAgregar {
        it.copy(nombre = texto, tocado = true, rechazo = null)
    }

    /**
     * Marca que esto se cuenta por unidad.
     *
     * **Desmarcar "va en recetas" no viene de regalo**: son dos preguntas y el usuario contesta
     * las dos. Encadenarlas dejaría a la caja de torta —objeto que sí va en una receta— fuera del
     * buscador sin que nadie lo pidiera.
     */
    fun cambiarEsObjeto(esObjeto: Boolean) = enAgregar { it.copy(esObjeto = esObjeto) }

    fun cambiarVaEnRecetas(vaEnRecetas: Boolean) = enAgregar {
        it.copy(vaEnRecetas = vaEnRecetas)
    }

    fun cambiarCantidadNueva(texto: String) = enAgregar {
        it.copy(cantidad = formatearMientrasSeEscribe(texto), tocado = true)
    }

    fun cambiarPrecioNuevo(texto: String) = enAgregar {
        it.copy(precio = formatearMientrasSeEscribe(texto), tocado = true)
    }

    fun cambiarDetallesNuevos(texto: String) = enAgregar { it.copy(detalles = texto) }

    /**
     * Guarda lo nuevo, o se detiene a preguntar por el precio.
     *
     * [reemplazandoElPrecio] llega en `true` solo desde el aviso de precio distinto: es la
     * respuesta explícita a esa pregunta, y no un atajo para saltársela.
     */
    fun guardarNuevo(reemplazandoElPrecio: Boolean = false) {
        val actual = _dialogo.value as? DialogoAlmacen.Agregar ?: return
        if (actual.guardando) return
        if (!reemplazandoElPrecio && !actual.puedeGuardar) return
        val cuanto = textoANumero(actual.cantidad) ?: return
        // Lo que se guarda es el precio **por unidad de medida**, no lo que se pagó: es lo que
        // multiplica cada receta. Con cantidad 0 no hay división posible y va 0, que es lo que
        // dice el aviso del cuadro (14.5.2).
        val precio = actual.valorPorUnidad ?: 0.0

        _dialogo.value = actual.copy(guardando = true, precioEnDisputa = null)

        viewModelScope.launch {
            val resultado = almacen.agregar(
                nombre = actual.nombre,
                esObjeto = actual.esObjeto,
                vaEnRecetas = actual.vaEnRecetas,
                cantidad = cuanto,
                valor = precio,
                detalles = actual.detalles,
                reemplazarElPrecio = reemplazandoElPrecio
            )
            terminarDeAgregar(actual, resultado)
        }
    }

    /** Qué hacer con lo que contestó el repositorio. Lo comparten los dos caminos de guardado. */
    private fun terminarDeAgregar(
        actual: DialogoAlmacen.Agregar,
        resultado: ResultadoAgregarAlAlmacen
    ) {
        when (resultado) {
            is ResultadoAgregarAlAlmacen.Listo -> {
                _dialogo.value = DialogoAlmacen.Ninguno
                mensaje.value = "Se agregó '${actual.nombre.trim()}' al almacén"
            }
            // Dentro del cuadro y no en la franja de abajo: es sobre lo que se acaba de
            // escribir, y con el teclado abierto esa franja queda tapada (8.2).
            is ResultadoAgregarAlAlmacen.NoSePudo ->
                enAgregar { it.copy(guardando = false, rechazo = resultado.motivo) }

            is ResultadoAgregarAlAlmacen.PrecioDistinto -> enAgregar {
                it.copy(
                    guardando = false,
                    precioEnDisputa = PrecioEnDisputa(
                        existente = resultado.existente,
                        valorGuardado = resultado.existente.valorPorGramo,
                        valorEscrito = resultado.nuevoValor
                    )
                )
            }
        }
    }

    /**
     * Deja el precio que ya estaba y guarda igual lo demás.
     *
     * **No se puede resolver reescribiendo el campo del precio**, que es lo que hacía antes:
     * desde 14.5.2 ese campo dice lo que costó *todo*, no lo que cuesta cada unidad, así que
     * poner ahí el valor guardado escribiría un número que significa otra cosa. Va por su propia
     * llamada al repositorio, con el valor que ya estaba.
     */
    fun conservarElPrecioGuardado() {
        val actual = _dialogo.value as? DialogoAlmacen.Agregar ?: return
        val disputa = actual.precioEnDisputa ?: return
        val cuanto = textoANumero(actual.cantidad) ?: return

        _dialogo.value = actual.copy(guardando = true, precioEnDisputa = null)

        viewModelScope.launch {
            val resultado = almacen.agregar(
                nombre = actual.nombre,
                esObjeto = actual.esObjeto,
                vaEnRecetas = actual.vaEnRecetas,
                cantidad = cuanto,
                valor = disputa.valorGuardado,
                detalles = actual.detalles
            )
            terminarDeAgregar(actual, resultado)
        }
    }

    /** Cambia el precio del ingrediente por el escrito. Mueve el costo de todas sus recetas. */
    fun reemplazarElPrecio() {
        if (_dialogo.value !is DialogoAlmacen.Agregar) return
        guardarNuevo(reemplazandoElPrecio = true)
    }

    fun cerrarLaDisputaDePrecio() = enAgregar { it.copy(precioEnDisputa = null) }

    // --- Editar lo que ya está (14.7) ---

    /**
     * Abre el cuadro de editar, con lo que hay puesto.
     *
     * El campo manual viene relleno y no vacío a propósito: lo normal es corregir —"quedaban 2
     * kilos, ahora 1,5"— y no anotar desde cero. El de la calculadora, al revés, arranca vacío
     * porque lo que se escribe ahí es lo que se acaba de usar y eso no lo sabe nadie más.
     */
    fun abrirEdicion(fila: FilaDeAlmacen) {
        _dialogo.value = DialogoAlmacen.CambiarCantidad(
            fila = fila,
            cantidad = formatearNumero(fila.cantidad),
            detalles = fila.detalles.orEmpty()
        )
    }

    fun cambiarModoDeEdicion(modo: ModoDeEdicion) = enEdicion { it.copy(modo = modo) }

    fun cambiarCantidadEnEdicion(texto: String) = enEdicion {
        it.copy(cantidad = formatearMientrasSeEscribe(texto))
    }

    fun cambiarLoQueSeUso(texto: String) = enEdicion {
        it.copy(seUso = formatearMientrasSeEscribe(texto))
    }

    fun cambiarDetallesEnEdicion(texto: String) = enEdicion { it.copy(detalles = texto) }

    /**
     * Guarda la cantidad y los detalles de una vez.
     *
     * Son dos escrituras y no una porque tocan cosas distintas —una mueve la fecha de revisión y
     * la otra no—, pero desde afuera es un solo botón: quien corrige "eran 2 kilos" y de paso
     * anota "estaba en oferta" hizo una sola cosa.
     */
    fun guardarEdicion() {
        val actual = _dialogo.value as? DialogoAlmacen.CambiarCantidad ?: return
        if (!actual.puedeGuardar) return
        val cuanto = actual.resultado ?: return

        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            val cambio = almacen.cambiarCantidad(actual.fila.id, cuanto)
            if (cambio is Resultado.NoSePudo) {
                mensaje.value = cambio.motivo
                _dialogo.value = DialogoAlmacen.Ninguno
                return@launch
            }
            if (actual.detalles.trim() != actual.fila.detalles.orEmpty().trim()) {
                val notas = almacen.guardarDetalles(actual.fila.id, actual.detalles)
                if (notas is Resultado.NoSePudo) mensaje.value = notas.motivo
            }
            _dialogo.value = DialogoAlmacen.Ninguno
        }
    }

    // --- Sacar del almacén ---

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

    private fun enEdicion(
        cambio: (DialogoAlmacen.CambiarCantidad) -> DialogoAlmacen.CambiarCantidad
    ) {
        _dialogo.update { actual ->
            if (actual is DialogoAlmacen.CambiarCantidad) cambio(actual) else actual
        }
    }

    companion object {
        fun fabrica(almacen: AlmacenRepositorio): ViewModelProvider.Factory = viewModelFactory {
            initializer { AlmacenViewModel(almacen) }
        }
    }
}
