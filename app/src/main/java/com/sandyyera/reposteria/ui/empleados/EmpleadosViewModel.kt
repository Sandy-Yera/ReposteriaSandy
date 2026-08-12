package com.sandyyera.reposteria.ui.empleados

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.Empleado
import com.sandyyera.reposteria.data.repositorio.EmpleadoRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaDeUnEmpleado
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearEmpleado
import com.sandyyera.reposteria.logica.busqueda.filtrarPor
import com.sandyyera.reposteria.logica.partes.nombreSinChocar
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.sueldos.SimulacionMultipleResultado
import com.sandyyera.reposteria.logica.validaciones.errorEnDiasPorSemanaTexto
import com.sandyyera.reposteria.logica.validaciones.errorEnGananciaDelEmpleado
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreEscrito
import com.sandyyera.reposteria.logica.validaciones.motivoParaNoTocarAlEmpleado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Un empleado listo para dibujarse en la lista (10.1).
 *
 * Lleva [noSePuedeTocar] ya resuelto y no el `esGenerico` crudo: la regla de qué se puede hacer
 * con el genérico vive en `logica/validaciones` y es la misma que aplica el repositorio. Con el
 * booleano suelto, la pantalla tendría que volver a decidir y las dos decisiones se separarían.
 */
data class FilaDeEmpleado(val empleado: Empleado) {
    val id: Long get() = empleado.id
    val nombre: String get() = empleado.nombre

    /** El motivo por el que no se puede renombrar ni borrar, o `null` si sí se puede. */
    val noSePuedeTocar: String? get() = motivoParaNoTocarAlEmpleado(empleado.esGenerico)
}

/**
 * Una receta ofrecida para asignarle a un empleado, con el motivo si no se puede.
 *
 * **El motivo viaja con ella y no se calcula al tocarla**, que es el arreglo del cierre de la
 * app: sin precio no hay ganancia que repartir y las fórmulas lanzan con razón, así que la
 * pantalla tiene que saberlo **antes** de ofrecerla.
 */
data class RecetaCandidata(val datos: DatosCalculoReceta, val porQueNo: String?) {
    val sePuede: Boolean get() = porQueNo == null
}

/** Qué hay abierto encima de la sección de empleados. */
sealed interface DialogoEmpleados {

    data object Ninguno : DialogoEmpleados

    /**
     * Crear uno nuevo, o renombrar el que se está viendo.
     *
     * Es **el mismo cuadro** para las dos cosas porque desde afuera es la misma acción —escribir
     * un nombre— y lo único que cambia es el título. [editando] en `null` es "uno nuevo".
     */
    data class Nombre(
        val editando: Empleado? = null,
        val nombre: String = "",
        val tocado: Boolean = false,
        val guardando: Boolean = false,
        val rechazo: String? = null
    ) : DialogoEmpleados {

        /** El rechazo del repositorio manda sobre el del formato: es el más específico (8.2). */
        val error: String? get() = rechazo ?: errorEnNombreEscrito(nombre).takeIf { tocado }

        val puedeGuardar: Boolean get() = error == null && nombre.isNotBlank() && !guardando
    }

    /**
     * Ya existe uno con ese nombre: se pregunta en vez de rechazar (10.1).
     *
     * **Dos empleados pueden llamarse igual y no es un error**: son dos personas. El nombre
     * repetido no rompe nada porque lo que identifica al empleado es su `id`, así que lo único
     * que corresponde es avisar por si fue un descuido.
     */
    data class ConfirmarNombreRepetido(
        val nombre: String,
        /**
         * Cómo va a quedar el nuevo: "Ana 2", "Ana 3"…
         *
         * **Se dice antes de aceptar y no se descubre después.** Dos filas con el mismo nombre
         * son imposibles de distinguir en la lista, así que el que entra se numera; que eso pase
         * sin avisar es lo que hacía riesgoso el "sí, son dos personas".
         *
         * Lo resuelve `nombreSinChocar`, la misma función que bautiza una sección traída (8.11.2):
         * es el mismo problema —un nombre que ya está ocupado— y escribirlo de nuevo acá serían
         * dos numeraciones que se separan.
         */
        val comoQuedaria: String,
        val guardando: Boolean = false
    ) : DialogoEmpleados

    /**
     * La advertencia antes de borrar un empleado (6.3).
     *
     * Dice **cuántas recetas pierde**, que es lo que se está por perder: los sueldos asignados se
     * van con él por la cascada. Un "¿seguro?" sin ese número no permite decidir.
     */
    data class ConfirmarBorrado(
        val empleado: Empleado,
        val cuantasRecetas: Int,
        val borrando: Boolean = false
    ) : DialogoEmpleados

    /**
     * Asignarle una receta, o cambiarle cuánto se lleva (10.1).
     *
     * [gananciaTotal] es el tope y **viaja con el cuadro**: es lo que hace que el aviso de "no
     * puedes pasarte" pueda aparecer **mientras se escribe** en vez de al guardar. Sacarlo de la
     * receta en cada tecla sería una consulta por dígito.
     */
    data class Sueldo(
        val recetaId: Long,
        val titulo: String,
        val gananciaTotal: Double,
        val ganancia: String,
        val guardando: Boolean = false,
        val rechazo: String? = null
    ) : DialogoEmpleados {

        val error: String?
            get() = rechazo ?: errorEnGananciaDelEmpleado(ganancia, gananciaTotal)

        val puedeGuardar: Boolean get() = error == null && !guardando

        /** El tope escrito como se lee, para decirlo antes de que alguien se pase. */
        val comoSeLeeElTope: String get() = formatearNumero(gananciaTotal)
    }

    /**
     * Elegir a qué receta asignarle un sueldo.
     *
     * Se leen **una sola vez** y no observadas (12.2.1): mientras el cuadro está abierto, una
     * receta que aparece movería la lista bajo el dedo.
     */
    data class ElegirReceta(
        val candidatas: List<RecetaCandidata> = emptyList(),
        val busqueda: String = "",
        val cargando: Boolean = true
    ) : DialogoEmpleados {
        val visibles: List<RecetaCandidata>
            get() = filtrarPor(candidatas, busqueda) { it.datos.titulo }
    }
}

/** Lo que la lista de empleados necesita para dibujarse. */
data class EstadoEmpleados(
    val empleados: List<FilaDeEmpleado> = emptyList(),
    val busqueda: String = "",
    /** A cuál se le está mirando el detalle, o `null` si se ve la lista. */
    val abierto: Long? = null,
    val cargando: Boolean = true
) {
    val visibles: List<FilaDeEmpleado>
        get() = filtrarPor(empleados, busqueda) { it.nombre }

    val elAbierto: FilaDeEmpleado? get() = empleados.firstOrNull { it.id == abierto }

    val listaVacia: Boolean get() = !cargando && empleados.isEmpty()
}

/**
 * Lo que necesita el detalle de un empleado: sus recetas y la simulación de todas juntas (10.3).
 *
 * Va en su propio estado y no dentro de [EstadoEmpleados] porque **se observa a otra cosa**: la
 * lista cuelga de la tabla de empleados y esto de los sueldos de uno. Juntos, abrir un empleado
 * volvería a dibujar la lista entera, y cada tecla en el buscador recalcularía la simulación.
 */
data class EstadoDelEmpleado(
    val recetas: List<RecetaDeUnEmpleado> = emptyList(),
    val diasPorSemana: String = "1",
    val simulacion: SimulacionMultipleResultado? = null,
    val cargando: Boolean = true
) {
    val sinRecetas: Boolean get() = !cargando && recetas.isEmpty()

    /**
     * El aviso de los días, o `null`.
     *
     * Faltaba: se podían escribir 9 días a la semana, que no existen. La regla ya vivía en
     * `logica/validaciones` y solo no se estaba mirando.
     */
    val errorDias: String? get() = errorEnDiasPorSemanaTexto(diasPorSemana)

    /** Cuántas de sus recetas no se pueden repartir todavía. La pantalla lo dice arriba. */
    val cuantasSinReparto: Int get() = recetas.count { it.sinRepartoPosible }
}

/**
 * La sección Empleados (10).
 *
 * **No calcula nada.** El reparto y la simulación viven en `logica/sueldos`, ya probados sin base
 * de datos, y el repositorio es el que consulta. Acá solo se decide qué se muestra y en qué orden
 * pasan las cosas — que es lo que un ViewModel tiene que hacer.
 */
class EmpleadosViewModel(
    private val empleados: EmpleadoRepositorio
) : ViewModel() {

    private val busqueda = MutableStateFlow("")
    private val abierto = MutableStateFlow<Long?>(null)
    private val _dialogo = MutableStateFlow<DialogoEmpleados>(DialogoEmpleados.Ninguno)
    private val mensaje = MutableStateFlow<String?>(null)

    // El número de días es del cuadro y no de la base mientras se escribe: guardarlo en cada
    // tecla escribiría "1", "12" y "123" al pasar por un 123.
    private val dias = MutableStateFlow("1")
    private val simulacion = MutableStateFlow<SimulacionMultipleResultado?>(null)

    val dialogo: StateFlow<DialogoEmpleados> = _dialogo

    val estado: StateFlow<EstadoEmpleados> = combine(
        empleados.observarTodos(),
        busqueda,
        abierto,
        mensaje
    ) { lista, texto, cual, _ ->
        EstadoEmpleados(
            empleados = lista.map { FilaDeEmpleado(it) },
            busqueda = texto,
            abierto = cual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoEmpleados()
    )

    /**
     * Las recetas del empleado abierto, observadas.
     *
     * `flatMapLatest` y no una lectura al abrir: los costos de esas recetas cambian desde otra
     * pantalla —basta que suba un ingrediente— y el reparto que se muestra tiene que moverse con
     * ellos. Es la misma regla de siempre: *lo que se muestra se observa*.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val delEmpleado: StateFlow<EstadoDelEmpleado> =
        combine(
            abierto.flatMapLatest { cual ->
                if (cual == null) flowOf(emptyList()) else empleados.observarRecetasDe(cual)
            },
            dias,
            simulacion
        ) { recetas, cuantosDias, resultado ->
            EstadoDelEmpleado(
                recetas = recetas,
                diasPorSemana = cuantosDias,
                simulacion = resultado,
                cargando = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EstadoDelEmpleado()
        )

    val aviso: StateFlow<String?> = mensaje

    fun mensajeMostrado() { mensaje.value = null }

    fun buscar(texto: String) { busqueda.value = texto }

    // --- Abrir y cerrar el detalle ---

    fun abrir(empleadoId: Long) {
        abierto.value = empleadoId
        simulacion.value = null
        viewModelScope.launch {
            dias.value = empleados.diasCompartidos(empleadoId).toString()
            recalcularSimulacion()
        }
    }

    fun cerrarDetalle() {
        abierto.value = null
        simulacion.value = null
    }

    // --- Crear y renombrar ---

    fun abrirNuevo() { _dialogo.value = DialogoEmpleados.Nombre() }

    fun abrirRenombre(fila: FilaDeEmpleado) {
        // El motivo se comprueba acá **y** en el repositorio: acá para no abrir un cuadro que va
        // a ser rechazado, y allá porque es quien decide de verdad.
        fila.noSePuedeTocar?.let {
            mensaje.value = it
            return
        }
        _dialogo.value = DialogoEmpleados.Nombre(editando = fila.empleado, nombre = fila.nombre)
    }

    fun cambiarNombre(texto: String) {
        _dialogo.update { actual ->
            if (actual is DialogoEmpleados.Nombre) actual.copy(nombre = texto, tocado = true, rechazo = null)
            else actual
        }
    }

    fun guardarNombre() {
        val actual = _dialogo.value as? DialogoEmpleados.Nombre ?: return
        if (!actual.puedeGuardar) return
        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            val editando = actual.editando
            if (editando != null) {
                when (val r = empleados.renombrar(editando.id, actual.nombre)) {
                    is Resultado.Listo -> _dialogo.value = DialogoEmpleados.Ninguno
                    is Resultado.NoSePudo ->
                        _dialogo.value = actual.copy(guardando = false, rechazo = r.motivo)
                }
                return@launch
            }
            when (val r = empleados.crear(actual.nombre)) {
                is ResultadoCrearEmpleado.Creado -> _dialogo.value = DialogoEmpleados.Ninguno
                is ResultadoCrearEmpleado.NoValido ->
                    _dialogo.value = actual.copy(guardando = false, rechazo = r.motivo)
                // **No es un rechazo**: dos personas pueden llamarse igual. Se pregunta.
                is ResultadoCrearEmpleado.YaExiste ->
                    _dialogo.value = DialogoEmpleados.ConfirmarNombreRepetido(
                        nombre = actual.nombre,
                        comoQuedaria = nombreSinChocar(
                            actual.nombre,
                            estado.value.empleados.map { it.nombre }
                        )
                    )
            }
        }
    }

    /** Contesta el aviso de nombre repetido: sí, son dos personas distintas. */
    fun crearAunqueSeRepita() {
        val aviso = _dialogo.value as? DialogoEmpleados.ConfirmarNombreRepetido ?: return
        if (aviso.guardando) return
        _dialogo.value = aviso.copy(guardando = true)

        viewModelScope.launch {
            // Se crea **con el nombre numerado** y no con el repetido: es lo que el aviso acaba
            // de prometer, y guardar el otro dejaría dos filas idénticas en la lista.
            empleados.crearAunqueSeRepita(aviso.comoQuedaria)
            _dialogo.value = DialogoEmpleados.Ninguno
        }
    }

    // --- Borrar ---

    fun pedirBorrado(fila: FilaDeEmpleado) {
        fila.noSePuedeTocar?.let {
            mensaje.value = it
            return
        }
        viewModelScope.launch {
            _dialogo.value = DialogoEmpleados.ConfirmarBorrado(
                empleado = fila.empleado,
                // Se cuenta **antes** de preguntar: el aviso sirve porque dice cuántas recetas
                // se van con él, y contarlas después sería preguntar en abstracto.
                cuantasRecetas = empleados.cuantasRecetasTiene(fila.id)
            )
        }
    }

    fun confirmarBorrado() {
        val aviso = _dialogo.value as? DialogoEmpleados.ConfirmarBorrado ?: return
        if (aviso.borrando) return
        _dialogo.value = aviso.copy(borrando = true)

        viewModelScope.launch {
            val r = empleados.eliminar(aviso.empleado.id)
            if (r is Resultado.NoSePudo) mensaje.value = r.motivo
            if (abierto.value == aviso.empleado.id) cerrarDetalle()
            _dialogo.value = DialogoEmpleados.Ninguno
        }
    }

    // --- Asignarle recetas ---

    fun abrirElegirReceta() {
        val cual = abierto.value ?: return
        _dialogo.value = DialogoEmpleados.ElegirReceta()
        viewModelScope.launch {
            val candidatas = empleados.recetasQueFaltanPor(cual).map {
                RecetaCandidata(it, empleados.porQueNoSeLePuedeAsignar(it))
            }
            _dialogo.update { actual ->
                if (actual is DialogoEmpleados.ElegirReceta) {
                    actual.copy(candidatas = candidatas, cargando = false)
                } else {
                    actual
                }
            }
        }
    }

    fun buscarReceta(texto: String) {
        _dialogo.update { actual ->
            if (actual is DialogoEmpleados.ElegirReceta) actual.copy(busqueda = texto) else actual
        }
    }

    /** Elegida la receta, se pasa a escribir cuánto se lleva. Son dos preguntas, no una. */
    fun elegirReceta(candidata: RecetaCandidata) {
        // La que no se puede **ni siquiera abre el cuadro**: el motivo ya está a la vista en la
        // lista, y calcular su tope sería justo lo que cerraba la app.
        candidata.porQueNo?.let {
            mensaje.value = it
            return
        }
        _dialogo.value = DialogoEmpleados.Sueldo(
            recetaId = candidata.datos.recetaId,
            titulo = candidata.datos.titulo,
            gananciaTotal = gananciaDe(candidata.datos),
            ganancia = ""
        )
    }

    /** Cambiar lo que se lleva por una receta que ya tenía asignada. */
    fun abrirSueldo(receta: RecetaDeUnEmpleado) {
        _dialogo.value = DialogoEmpleados.Sueldo(
            recetaId = receta.recetaId,
            titulo = receta.titulo,
            gananciaTotal = receta.gananciaTotal,
            ganancia = formatearNumero(receta.sueldo.gananciaEmpleado)
        )
    }

    fun cambiarGanancia(texto: String) {
        _dialogo.update { actual ->
            if (actual is DialogoEmpleados.Sueldo) {
                actual.copy(ganancia = formatearMientrasSeEscribe(texto), rechazo = null)
            } else {
                actual
            }
        }
    }

    fun guardarSueldo() {
        val actual = _dialogo.value as? DialogoEmpleados.Sueldo ?: return
        val cual = abierto.value ?: return
        if (!actual.puedeGuardar) return
        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            when (val r = empleados.guardarSueldo(cual, actual.recetaId, actual.ganancia)) {
                is Resultado.Listo -> {
                    _dialogo.value = DialogoEmpleados.Ninguno
                    recalcularSimulacion()
                }
                is Resultado.NoSePudo ->
                    _dialogo.value = actual.copy(guardando = false, rechazo = r.motivo)
            }
        }
    }

    fun quitarReceta(receta: RecetaDeUnEmpleado) {
        viewModelScope.launch {
            empleados.quitarSueldo(receta.sueldo.id)
            mensaje.value = "'${receta.titulo}' ya no es de este empleado"
            recalcularSimulacion()
        }
    }

    // --- La simulación de todas sus recetas (10.3) ---

    fun cambiarDias(texto: String) {
        dias.value = texto
        // No se guarda lo que no pasa la validación: el aviso ya lo dice el estado, y escribir
        // un 9 dejaría una semana de nueve días guardada en la base.
        if (errorEnDiasPorSemanaTexto(texto) != null) return
        val cuantos = texto.toIntOrNull() ?: return
        val cual = abierto.value ?: return
        viewModelScope.launch {
            val r = empleados.guardarDiasCompartidos(cual, cuantos)
            if (r is Resultado.NoSePudo) {
                mensaje.value = r.motivo
                return@launch
            }
            recalcularSimulacion()
        }
    }

    fun cambiarUnidades(receta: RecetaDeUnEmpleado, texto: String) {
        val cuantas = texto.toIntOrNull() ?: return
        val cual = abierto.value ?: return
        viewModelScope.launch {
            // A la **misma fila que muestra la pantalla**: escribir en la tabla de detalle y leer
            // de la del sueldo era el bug de "el campo no cambia nada".
            empleados.guardarUnidadesPorDia(cual, receta.recetaId, cuantas)
            recalcularSimulacion()
        }
    }

    /**
     * Vuelve a pedir la simulación completa.
     *
     * Se pide entera y no se ajusta a mano tras cada cambio: el total sale de una función ya
     * probada, y actualizarlo por partes sería una segunda versión de la misma cuenta — la que
     * no tiene pruebas.
     */
    private suspend fun recalcularSimulacion() {
        val cual = abierto.value ?: return
        simulacion.value = empleados.simulacionDeTodasSusRecetas(cual)
    }

    /** La ganancia de una receta, que es el tope de lo que el empleado puede llevarse. */
    private fun gananciaDe(datos: DatosCalculoReceta): Double =
        com.sandyyera.reposteria.logica.precios.ingresoBruto(datos) - datos.costoTotal

    fun cerrarDialogo() { _dialogo.value = DialogoEmpleados.Ninguno }

    companion object {
        fun fabrica(empleados: EmpleadoRepositorio): ViewModelProvider.Factory = viewModelFactory {
            initializer { EmpleadosViewModel(empleados) }
        }
    }
}
