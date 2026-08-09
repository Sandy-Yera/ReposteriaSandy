package com.sandyyera.reposteria.ui.ingredientes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoGuardarIngrediente
import com.sandyyera.reposteria.logica.busqueda.filtrarPor
import com.sandyyera.reposteria.logica.calculadora.ErroresCalculadora
import com.sandyyera.reposteria.logica.calculadora.UnidadDeCompra
import com.sandyyera.reposteria.logica.calculadora.calcularValorPorGramo
import com.sandyyera.reposteria.logica.calculadora.revisarCalculadora
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.validaciones.ErroresIngrediente
import com.sandyyera.reposteria.logica.validaciones.revisarIngrediente
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Qué hay abierto encima de la lista de ingredientes.
 *
 * Es un tipo cerrado y no varios booleanos sueltos (`mostrandoFormulario`,
 * `mostrandoBorrado`…) porque con booleanos nada impide que dos queden en `true` a la vez
 * y aparezcan dos cuadros superpuestos. Acá solo puede haber uno.
 */
sealed interface DialogoIngrediente {

    /** No hay nada abierto: se ve solo la lista. */
    data object Ninguno : DialogoIngrediente

    /**
     * El formulario de alta o de edición.
     *
     * [editando] es `null` cuando se está creando uno nuevo, y trae el ingrediente
     * original cuando se está editando. Es el mismo formulario en los dos casos porque
     * pide exactamente los mismos datos; solo cambia el título y qué se hace al guardar.
     *
     * [tocadoNombre] y [tocadoValor] existen para no retar a nadie antes de tiempo: un
     * formulario recién abierto tiene los dos campos vacíos, y eso técnicamente es un
     * error, pero mostrarlo de entrada se lee como un reto por algo que aún no se hizo.
     * El aviso aparece recién cuando ese campo se tocó.
     *
     * [nombreRepetido] no sale de la validación local sino de la base: es el aviso de que
     * ya existe otro ingrediente con ese nombre, y solo se puede saber al intentar guardar.
     */
    data class Formulario(
        val editando: Ingrediente? = null,
        val nombre: String = "",
        val valorPorGramo: String = "",
        val tocadoNombre: Boolean = false,
        val tocadoValor: Boolean = false,
        val nombreRepetido: String? = null,
        val guardando: Boolean = false
    ) : DialogoIngrediente {

        val errores: ErroresIngrediente get() = revisarIngrediente(nombre, valorPorGramo)

        /** El error a mostrar bajo el campo del nombre, o `null` si no hay nada que decir. */
        val errorNombreVisible: String?
            get() = nombreRepetido ?: errores.nombre.takeIf { tocadoNombre }

        /** El error a mostrar bajo el campo del valor. */
        val errorValorVisible: String? get() = errores.valorPorGramo.takeIf { tocadoValor }

        /** Si el botón de guardar debe estar habilitado. */
        val puedeGuardar: Boolean get() = errores.sirve && !guardando
    }

    /**
     * La advertencia previa a borrar, que exige la política de 7.1.
     *
     * [recetasAfectadas] arranca en `null` mientras se consulta la base. Esa distinción
     * importa: `null` es "todavía no sé" y lista vacía es "no lo usa ninguna receta", y
     * confundirlos dejaría borrar sin haber mostrado la advertencia.
     */
    data class ConfirmarBorrado(
        val ingrediente: Ingrediente,
        val recetasAfectadas: List<Receta>? = null,
        val borrando: Boolean = false
    ) : DialogoIngrediente

    /**
     * La confirmación antes de pisar el valor de un ingrediente desde la calculadora (7.2).
     *
     * Guarda los dos valores —el que tiene y el que va a quedar— porque el aviso tiene que
     * mostrarlos juntos. Reemplazar un valor no se puede deshacer y no avisa a nadie más:
     * el costo de todas las recetas que usan ese ingrediente cambia en el mismo momento.
     */
    data class ConfirmarReemplazo(
        val ingrediente: Ingrediente,
        val valorNuevo: Double,
        val guardando: Boolean = false
    ) : DialogoIngrediente
}

/**
 * Qué se va a hacer con el valor que salió de la calculadora.
 *
 * Arranca en `null` —nada elegido— a propósito: la calculadora no adivina qué ingrediente
 * quisiste. Tocar "Listo" sin haber elegido avisa en vez de hacer algo por su cuenta.
 */
sealed interface DestinoDelValor {

    /** Crear un ingrediente nuevo con ese valor ya puesto. Va fijo primero en la lista. */
    data object Crear : DestinoDelValor

    /**
     * Pisar el valor de un ingrediente que ya existe.
     *
     * Guarda el **id** y no el ingrediente entero para que el "valor actual" que se
     * muestra sea siempre el de la base y no una copia que quedó vieja. Si mientras tanto
     * ese ingrediente se borró, deja de resolverse y la elección queda sin efecto, que es
     * lo correcto.
     */
    data class Reemplazar(val ingredienteId: Long) : DestinoDelValor
}

/**
 * Lo que la calculadora de valor por gramo (7.2) necesita para dibujarse.
 *
 * [candidatos] y [elegido] no se escriben a mano: se rellenan al armar el estado, a partir
 * de la lista viva de ingredientes. Todo lo demás es lo que la persona escribió.
 */
data class EstadoCalculadora(
    val precio: String = "",
    val cantidad: String = "",
    val unidad: UnidadDeCompra = UnidadDeCompra.KILO,
    val tocadoPrecio: Boolean = false,
    val tocadoCantidad: Boolean = false,
    val busquedaDestino: String = "",
    val destino: DestinoDelValor? = null,
    val faltaElegirDestino: Boolean = false,
    val candidatos: List<Ingrediente> = emptyList(),
    val elegido: Ingrediente? = null
) {
    private val errores: ErroresCalculadora get() = revisarCalculadora(precio, cantidad)

    val errorPrecioVisible: String? get() = errores.precio.takeIf { tocadoPrecio }
    val errorCantidadVisible: String? get() = errores.cantidad.takeIf { tocadoCantidad }

    /** El valor por gramo calculado, o `null` mientras falte algo por escribir. */
    val resultado: Double? get() = calcularValorPorGramo(precio, cantidad, unidad)

    /** Si el botón "Listo" está habilitado. */
    val puedeTerminar: Boolean get() = resultado != null

    /** Si "Crear un ingrediente nuevo" es lo que está elegido. */
    val creandoNuevo: Boolean get() = destino is DestinoDelValor.Crear
}

/**
 * Todo lo que la pantalla de ingredientes necesita para dibujarse.
 *
 * [visibles] ya viene filtrada por el buscador: la pantalla no vuelve a filtrar. Así el
 * filtro se calcula una vez por cambio real (texto nuevo o lista nueva) y no en cada
 * redibujado.
 */
data class EstadoIngredientes(
    val visibles: List<Ingrediente> = emptyList(),
    val hayIngredientes: Boolean = false,
    val busqueda: String = "",
    val dialogo: DialogoIngrediente = DialogoIngrediente.Ninguno,
    /** Cuando no es `null`, en vez de la lista se muestra la calculadora (7.2). */
    val calculadora: EstadoCalculadora? = null,
    /**
     * Los que **no están en el almacén** todavía (14.4).
     *
     * El aviso **no deshabilita nada**: el ingrediente sirve igual, con o sin él. Está para que
     * crear uno "para más adelante" —planear algo que todavía no se hace, que es para lo que
     * sirve el alta— siga siendo útil sin que se pierda entre los que sí tienen existencia
     * anotada. Y **no se apaga solo con el tiempo**: se apaga cuando aparece su fila de almacén,
     * y no antes.
     */
    val sinAlmacen: Set<Long> = emptySet(),
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    /** El catálogo está vacío de verdad, no es que la búsqueda no encontró nada. */
    val catalogoVacio: Boolean get() = !cargando && !hayIngredientes

    /** Si este ingrediente todavía no tiene existencia anotada en el almacén (14.4). */
    fun faltaEnElAlmacen(ingrediente: Ingrediente): Boolean = ingrediente.id in sinAlmacen

    /**
     * Si el ingrediente que se está editando no tiene existencia anotada (14.4).
     *
     * Va acá y no dentro de `DialogoIngrediente.Formulario` porque el formulario guarda lo que se
     * escribe y esto viene de la base: es el mismo motivo por el que la lista de candidatos de la
     * calculadora se resuelve en el `combine` y no dentro del diálogo — así el aviso se apaga en
     * cuanto el ingrediente aparece en el almacén, incluso con el cuadro abierto.
     */
    val alEditarFaltaEnElAlmacen: Boolean
        get() = (dialogo as? DialogoIngrediente.Formulario)?.editando?.id?.let { it in sinAlmacen }
            ?: false

    /** Cuántos ingredientes no están en el almacén, para el resumen de arriba. */
    val cuantosFaltanEnElAlmacen: Int get() = visibles.count { it.id in sinAlmacen }

    /** Hay ingredientes, pero ninguno coincide con lo buscado. */
    val busquedaSinResultados: Boolean get() = hayIngredientes && visibles.isEmpty()
}

/**
 * El cerebro de la pantalla de ingredientes: guarda lo que se ve y ejecuta lo que se pide.
 *
 * La pantalla no habla nunca con el repositorio ni con la base: solo lee [estado] y llama
 * a estas funciones. Eso es lo que permite que sobreviva a girar el teléfono —el ViewModel
 * no se recrea— y que la pantalla se pueda dibujar en la vista previa con datos inventados.
 */
class IngredientesViewModel(
    private val repositorio: IngredienteRepositorio
) : ViewModel() {

    private val busqueda = MutableStateFlow("")
    private val dialogo = MutableStateFlow<DialogoIngrediente>(DialogoIngrediente.Ninguno)
    private val calculadora = MutableStateFlow<EstadoCalculadora?>(null)
    private val mensaje = MutableStateFlow<String?>(null)

    /**
     * Lo que se ve, armado a partir de cinco fuentes que cambian por su cuenta.
     *
     * `combine` vuelve a calcular solo cuando alguna cambia de verdad, y el filtro queda
     * acá adentro en vez de dentro del dibujo de la pantalla. `WhileSubscribed` corta la
     * consulta a la base cuando la pantalla deja de mirarse, con cinco segundos de gracia
     * para que girar el teléfono no la reinicie.
     */
    val estado: StateFlow<EstadoIngredientes> = combine(
        repositorio.observarTodos(),
        busqueda,
        dialogo,
        calculadora,
        // El aviso del almacén y el mensaje van juntos en un `combine` de a dos porque `combine`
        // llega hasta cinco flujos y acá hacen falta seis. No cambia cuándo emite nada.
        combine(mensaje, repositorio.observarSinAlmacen()) { m, faltan -> m to faltan }
    ) { todos, textoBuscado, dialogoActual, calculadoraActual, mensajeYFaltantes ->
        val (mensajeActual, faltanEnAlmacen) = mensajeYFaltantes
        EstadoIngredientes(
            visibles = filtrarPor(todos, textoBuscado) { it.nombre },
            hayIngredientes = todos.isNotEmpty(),
            busqueda = textoBuscado,
            dialogo = dialogoActual,
            // La lista de candidatos y el ingrediente elegido se resuelven acá, contra la
            // lista viva: así el "valor actual" que muestra la calculadora es siempre el
            // de la base y no una copia que quedó vieja.
            calculadora = calculadoraActual?.let { estadoCalculadora ->
                val destino = estadoCalculadora.destino
                estadoCalculadora.copy(
                    candidatos = filtrarPor(todos, estadoCalculadora.busquedaDestino) { it.nombre },
                    elegido = if (destino is DestinoDelValor.Reemplazar) {
                        todos.firstOrNull { it.id == destino.ingredienteId }
                    } else {
                        null
                    }
                )
            },
            sinAlmacen = faltanEnAlmacen,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoIngredientes()
    )

    fun buscar(texto: String) {
        busqueda.value = texto
    }

    fun abrirAlta() {
        dialogo.value = DialogoIngrediente.Formulario()
    }

    fun abrirEdicion(ingrediente: Ingrediente) {
        dialogo.value = DialogoIngrediente.Formulario(
            editando = ingrediente,
            nombre = ingrediente.nombre,
            // Se muestra con el formato de la app (coma decimal), que es el mismo que
            // `textoANumero` sabe leer de vuelta al guardar.
            valorPorGramo = formatearNumero(ingrediente.valorPorGramo),
            // Ya tienen contenido válido: si algo se rompe al editar, el aviso es útil de
            // inmediato y no hay nada que "retar" antes de tiempo.
            tocadoNombre = true,
            tocadoValor = true
        )
    }

    fun cambiarNombre(texto: String) = enFormulario {
        // Al cambiar el nombre, el aviso de repetido deja de aplicar: era sobre el anterior.
        it.copy(nombre = texto, tocadoNombre = true, nombreRepetido = null)
    }

    fun cambiarValor(texto: String) = enFormulario {
        it.copy(valorPorGramo = formatearMientrasSeEscribe(texto), tocadoValor = true)
    }

    /**
     * Guarda lo que hay en el formulario, sea alta o edición.
     *
     * La decisión de qué mostrar la toma el `when` sobre [ResultadoGuardarIngrediente]:
     * el repositorio es el que sabe si el nombre está repetido, porque es el único que
     * puede mirar la base. Acá solo se traduce ese resultado a algo visible.
     */
    fun guardar() {
        val formulario = dialogo.value as? DialogoIngrediente.Formulario ?: return
        if (!formulario.puedeGuardar) return

        val nombre = formulario.nombre.trim()
        val valor = textoANumero(formulario.valorPorGramo) ?: return

        dialogo.value = formulario.copy(guardando = true)

        viewModelScope.launch {
            val original = formulario.editando
            val resultado = if (original == null) {
                repositorio.crear(nombre, valor)
            } else {
                repositorio.actualizar(original.copy(nombre = nombre, valorPorGramo = valor))
            }

            when (resultado) {
                is ResultadoGuardarIngrediente.Guardado -> {
                    dialogo.value = DialogoIngrediente.Ninguno
                    mensaje.value =
                        if (original == null) "Se agregó '$nombre'" else "Se guardó '$nombre'"
                }

                is ResultadoGuardarIngrediente.YaExiste -> enFormulario {
                    it.copy(
                        guardando = false,
                        nombreRepetido = "Ya tienes un ingrediente que se llama " +
                            "'${resultado.existente.nombre}'"
                    )
                }

                // No debería llegar acá, porque el botón está deshabilitado mientras hay
                // errores. Pero el repositorio valida igual —es el que decide de verdad—
                // y si algún día las dos reglas se separan, esto lo muestra en vez de
                // dejar el formulario colgado sin explicación.
                is ResultadoGuardarIngrediente.NoValido -> {
                    enFormulario { it.copy(guardando = false) }
                    mensaje.value = resultado.motivo
                }
            }
        }
    }

    /**
     * Abre la advertencia de borrado y sale a averiguar a qué recetas afecta.
     *
     * El cuadro se muestra de inmediato con la lista todavía en `null`, para que tocar el
     * botón responda al instante, y el listado llega cuando la consulta termina.
     */
    fun pedirBorrado(ingrediente: Ingrediente) {
        dialogo.value = DialogoIngrediente.ConfirmarBorrado(ingrediente)

        viewModelScope.launch {
            val afectadas = repositorio.recetasAfectadasPorBorrar(ingrediente.id)
            // Puede haber cerrado el cuadro mientras se consultaba: solo se completa si
            // sigue abierto y es el mismo ingrediente.
            dialogo.update { actual ->
                if (actual is DialogoIngrediente.ConfirmarBorrado &&
                    actual.ingrediente.id == ingrediente.id
                ) {
                    actual.copy(recetasAfectadas = afectadas)
                } else {
                    actual
                }
            }
        }
    }

    /**
     * Borra de verdad, ya con la advertencia aceptada.
     *
     * Exige que las recetas afectadas ya se hayan consultado: si todavía son `null`, la
     * advertencia no se llegó a mostrar completa y confirmar no significaría nada.
     */
    fun confirmarBorrado() {
        val aviso = dialogo.value as? DialogoIngrediente.ConfirmarBorrado ?: return
        if (aviso.recetasAfectadas == null || aviso.borrando) return

        dialogo.value = aviso.copy(borrando = true)

        viewModelScope.launch {
            repositorio.confirmarEliminacion(aviso.ingrediente.id)
            dialogo.value = DialogoIngrediente.Ninguno
            mensaje.value = "Se eliminó '${aviso.ingrediente.nombre}'"
        }
    }

    fun cerrarDialogo() {
        dialogo.value = DialogoIngrediente.Ninguno
    }

    // --- Calculadora de valor por gramo (7.2) ---

    fun abrirCalculadora() {
        calculadora.value = EstadoCalculadora()
    }

    fun cerrarCalculadora() {
        calculadora.value = null
    }

    fun cambiarPrecio(texto: String) = enCalculadora {
        it.copy(precio = formatearMientrasSeEscribe(texto), tocadoPrecio = true)
    }

    fun cambiarCantidad(texto: String) = enCalculadora {
        it.copy(cantidad = formatearMientrasSeEscribe(texto), tocadoCantidad = true)
    }

    fun cambiarUnidad(unidad: UnidadDeCompra) = enCalculadora { it.copy(unidad = unidad) }

    fun buscarDestino(texto: String) = enCalculadora { it.copy(busquedaDestino = texto) }

    /**
     * Elige qué hacer con el resultado, o lo desmarca si se vuelve a tocar lo ya elegido.
     *
     * Poder desmarcar importa: sin eso, un toque por error deja una opción puesta que ya
     * no se puede sacar salvo cerrando y volviendo a empezar.
     */
    fun elegirDestino(destino: DestinoDelValor) = enCalculadora {
        it.copy(
            destino = if (it.destino == destino) null else destino,
            // Al elegir algo, el reclamo de "no elegiste nada" deja de tener sentido.
            faltaElegirDestino = false
        )
    }

    /**
     * El botón "Listo": lleva el valor calculado a donde se haya elegido.
     *
     * Si no hay nada elegido **no hace nada y avisa**, en vez de suponer. Crear abre el
     * formulario con el valor ya puesto; reemplazar pasa antes por la confirmación, que es
     * donde se ven el valor viejo y el nuevo juntos.
     */
    fun terminarCalculadora() {
        val actual = calculadora.value ?: return
        val valor = actual.resultado ?: return

        when (val destino = actual.destino) {
            null -> enCalculadora { it.copy(faltaElegirDestino = true) }

            is DestinoDelValor.Crear -> {
                calculadora.value = null
                dialogo.value = DialogoIngrediente.Formulario(
                    valorPorGramo = formatearNumero(valor),
                    tocadoValor = true
                )
            }

            is DestinoDelValor.Reemplazar -> {
                // Se relee de la base en vez de usar el `elegido` del estado: ese lo
                // rellena el `combine` y podría venir de una lectura anterior.
                viewModelScope.launch {
                    val ingrediente = repositorio.obtener(destino.ingredienteId)
                    if (ingrediente == null) {
                        // Se borró mientras la calculadora estaba abierta.
                        enCalculadora { it.copy(destino = null, faltaElegirDestino = true) }
                    } else {
                        dialogo.value = DialogoIngrediente.ConfirmarReemplazo(
                            ingrediente = ingrediente,
                            valorNuevo = valor
                        )
                    }
                }
            }
        }
    }

    /**
     * Pisa el valor del ingrediente, ya con la confirmación aceptada.
     *
     * Cierra la calculadora además del aviso: el trabajo que se había empezado ahí ya se
     * terminó, y dejarla abierta invitaría a aplicar el mismo valor dos veces.
     */
    fun confirmarReemplazo() {
        val aviso = dialogo.value as? DialogoIngrediente.ConfirmarReemplazo ?: return
        if (aviso.guardando) return

        dialogo.value = aviso.copy(guardando = true)

        viewModelScope.launch {
            val resultado = repositorio.actualizar(
                aviso.ingrediente.copy(valorPorGramo = aviso.valorNuevo)
            )
            dialogo.value = DialogoIngrediente.Ninguno
            calculadora.value = null
            mensaje.value = when (resultado) {
                is ResultadoGuardarIngrediente.Guardado ->
                    "'${aviso.ingrediente.nombre}' quedó en $${formatearNumero(aviso.valorNuevo)} por gramo"
                // No debería pasar: es el mismo nombre de siempre y solo cambia el número.
                is ResultadoGuardarIngrediente.YaExiste ->
                    "No se pudo guardar: ya hay otro ingrediente con ese nombre"
                is ResultadoGuardarIngrediente.NoValido -> resultado.motivo
            }
        }
    }

    /** La pantalla avisa que ya mostró el mensaje, para que no reaparezca al girar. */
    fun mensajeMostrado() {
        mensaje.value = null
    }

    /** Cambia el formulario abierto, si es que hay uno. Si no hay, no hace nada. */
    private fun enFormulario(
        cambio: (DialogoIngrediente.Formulario) -> DialogoIngrediente.Formulario
    ) {
        dialogo.update { actual ->
            if (actual is DialogoIngrediente.Formulario) cambio(actual) else actual
        }
    }

    /** Cambia la calculadora abierta, si es que está abierta. */
    private fun enCalculadora(cambio: (EstadoCalculadora) -> EstadoCalculadora) {
        calculadora.update { actual -> actual?.let(cambio) }
    }

    companion object {
        /**
         * Cómo construir este ViewModel, ya que necesita un repositorio y no tiene
         * constructor vacío.
         *
         * El proyecto no usa una librería de inyección de dependencias (ver `AppContainer`),
         * así que la fábrica se escribe a mano. Son cinco líneas y evitan una dependencia
         * más en un proyecto de una persona.
         */
        fun fabrica(repositorio: IngredienteRepositorio): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { IngredientesViewModel(repositorio) }
            }
    }
}
