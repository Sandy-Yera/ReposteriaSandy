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
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    /** El catálogo está vacío de verdad, no es que la búsqueda no encontró nada. */
    val catalogoVacio: Boolean get() = !cargando && !hayIngredientes

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
    private val mensaje = MutableStateFlow<String?>(null)

    /**
     * Lo que se ve, armado a partir de cuatro fuentes que cambian por su cuenta.
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
        mensaje
    ) { todos, textoBuscado, dialogoActual, mensajeActual ->
        EstadoIngredientes(
            visibles = filtrarPor(todos, textoBuscado) { it.nombre },
            hayIngredientes = todos.isNotEmpty(),
            busqueda = textoBuscado,
            dialogo = dialogoActual,
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
        it.copy(valorPorGramo = texto, tocadoValor = true)
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
