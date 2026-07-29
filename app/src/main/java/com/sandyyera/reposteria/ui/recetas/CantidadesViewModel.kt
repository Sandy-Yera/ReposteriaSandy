package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.RecetaIngrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaSeccion
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoGuardarIngrediente
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.validaciones.debenMostrarseLosNombresDeSeccion
import com.sandyyera.reposteria.logica.validaciones.errorEnCantidadEnGramosTexto
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreSeccion
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Un ingrediente dentro de una receta, ya cruzado con su ficha del catálogo.
 *
 * El cruce se hace en memoria y no con un `JOIN` porque el catálogo de ingredientes es
 * personal —decenas de filas, no miles— y la pantalla ya lo tiene cargado para el buscador
 * de "agregar ingrediente". Una consulta más solo para traer el nombre sería trabajo
 * repetido.
 */
data class LineaDeIngrediente(
    val item: RecetaIngrediente,
    val ingrediente: Ingrediente
) {
    /** Lo que aporta esta línea al costo: los mismos gramos × valor que suma la base. */
    val subtotal: Double get() = item.cantidadG * ingrediente.valorPorGramo
}

/** Una sección de la receta con lo que lleva dentro. */
data class SeccionConIngredientes(
    val seccion: RecetaSeccion,
    val lineas: List<LineaDeIngrediente>
)

/** Qué hay abierto encima del paso de cantidades. */
sealed interface DialogoCantidades {

    data object Ninguno : DialogoCantidades

    /**
     * Agregar un ingrediente a una sección, o cambiarle los gramos a uno que ya está.
     *
     * [elegido] arranca en `null` al agregar: primero se busca el ingrediente y recién
     * cuando hay uno elegido aparece el campo de gramos. Al editar viene puesto y el
     * buscador no se muestra, porque cambiar de ingrediente es quitar uno y poner otro.
     */
    data class PonerIngrediente(
        val seccionId: Long,
        val editando: RecetaIngrediente? = null,
        val busqueda: String = "",
        val elegido: Ingrediente? = null,
        val cantidad: String = "",
        val tocado: Boolean = false,
        val guardando: Boolean = false
    ) : DialogoCantidades {

        val errorCantidad: String?
            get() = errorEnCantidadEnGramosTexto(cantidad).takeIf { tocado }

        val puedeGuardar: Boolean
            get() = elegido != null &&
                errorEnCantidadEnGramosTexto(cantidad) == null &&
                !guardando
    }

    /**
     * Agregar una sección nueva.
     *
     * [nombreDeLaPrimera] solo se pide cuando la receta tenía una sola sección: la que era
     * invisible tiene que dejar de serlo, y para eso necesita nombre (8.2). Cuando ya hay
     * dos o más viene en `null` y ese campo no se muestra.
     */
    data class Seccion(
        val nombre: String = "",
        val nombreDeLaPrimera: String? = null,
        val tocado: Boolean = false,
        val guardando: Boolean = false
    ) : DialogoCantidades {

        val error: String? get() = errorEnNombreSeccion(nombre).takeIf { tocado }

        val errorDeLaPrimera: String?
            get() = nombreDeLaPrimera?.let { errorEnNombreSeccion(it) }.takeIf { tocado }

        val puedeGuardar: Boolean
            get() = errorEnNombreSeccion(nombre) == null &&
                (nombreDeLaPrimera == null || errorEnNombreSeccion(nombreDeLaPrimera) == null) &&
                !guardando
    }

    /** Cambiarle el nombre a una sección que ya tiene. */
    data class RenombrarSeccion(
        val seccion: RecetaSeccion,
        val nombre: String,
        val guardando: Boolean = false
    ) : DialogoCantidades {
        val error: String? get() = errorEnNombreSeccion(nombre)
        val puedeGuardar: Boolean get() = error == null && !guardando
    }

    /** La advertencia antes de borrar una sección con todo lo que lleva. */
    data class ConfirmarBorrarSeccion(
        val seccion: SeccionConIngredientes,
        val borrando: Boolean = false
    ) : DialogoCantidades
}

/** Lo que el paso de cantidades necesita para dibujarse. */
data class EstadoCantidades(
    val receta: Receta? = null,
    val secciones: List<SeccionConIngredientes> = emptyList(),
    val costoTotal: Double = 0.0,
    val catalogo: List<Ingrediente> = emptyList(),
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    /**
     * Si se muestran los encabezados con el nombre de cada sección.
     *
     * La regla vive en `logica/` y se prueba sola; acá solo se consulta. Depende de los
     * nombres y no de cuántas hay: una sección sola pero bautizada a mano sí muestra el
     * suyo, porque lo que uno escribe no se esconde solo (8.2).
     */
    val mostrarNombresDeSeccion: Boolean
        get() = debenMostrarseLosNombresDeSeccion(secciones.map { it.seccion.nombreSeccion })

    /** Si la receta todavía no tiene ningún ingrediente en ninguna sección. */
    val sinIngredientes: Boolean get() = secciones.all { it.lineas.isEmpty() }
}

/**
 * El cerebro del paso "Cantidades" de una receta (8.2).
 *
 * Las consultas de secciones e ingredientes no son reactivas —los DAO devuelven listas, no
 * `Flow`—, así que después de cada cambio hay que volver a leer. Eso lo resuelve
 * [recargar]: un contador que se incrementa al terminar cada operación y que entra al
 * `combine`, lo que dispara una relectura. Es más simple que hacer reactivas siete
 * consultas, y el costo es una lectura por acción, no por segundo.
 *
 * El costo total **se relee de la base** en vez de sumarse acá. Podría sumarse en memoria
 * —cada línea sabe su subtotal— pero entonces habría dos verdades sobre el mismo número, y
 * la que manda cuando se calculan precios y sueldos es la de la base.
 */
class CantidadesViewModel(
    private val recetaId: Long,
    private val recetas: RecetaRepositorio,
    private val ingredientes: IngredienteRepositorio
) : ViewModel() {

    private val recargar = MutableStateFlow(0)
    private val _dialogo = MutableStateFlow<DialogoCantidades>(DialogoCantidades.Ninguno)
    private val mensaje = MutableStateFlow<String?>(null)

    /**
     * Lo que hay abierto encima, **por su propio canal y no dentro de [estado]**.
     *
     * Esto no es un detalle de organización: es lo que arregla un bug real. El `combine`
     * de abajo hace cuatro consultas a la base en cada emisión, así que lo que sale de él
     * llega con retraso. Un campo de texto que recibe su valor con retraso se rompe —
     * escribías "Torta" y quedaba "ortaT", porque el campo alcanzaba a reponer su estado
     * anterior antes de que llegara la letra nueva y el cursor volvía al principio.
     *
     * Acá el diálogo cambia en el momento, sin pasar por ninguna consulta.
     */
    val dialogo: StateFlow<DialogoCantidades> = _dialogo

    val estado: StateFlow<EstadoCantidades> = combine(
        recargar,
        ingredientes.observarTodos(),
        mensaje
    ) { _, catalogo, mensajeActual ->
        val porId = catalogo.associateBy { it.id }
        val items = recetas.obtenerIngredientes(recetaId)

        EstadoCantidades(
            receta = recetas.obtener(recetaId),
            secciones = recetas.obtenerSecciones(recetaId).map { seccion ->
                SeccionConIngredientes(
                    seccion = seccion,
                    lineas = items
                        .filter { it.seccionId == seccion.id }
                        // Si el ingrediente ya no está en el catálogo, la línea se omite en
                        // vez de dibujarse a medias. Es la misma red de seguridad que el
                        // INNER JOIN del costo (8.2): esa fila tampoco suma.
                        .mapNotNull { item ->
                            porId[item.ingredienteId]?.let { LineaDeIngrediente(item, it) }
                        }
                )
            },
            costoTotal = recetas.costoTotal(recetaId),
            catalogo = catalogo,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoCantidades()
    )

    private fun volverALeer() {
        recargar.update { it + 1 }
    }

    // --- Ingredientes de la receta ---

    fun abrirAgregarIngrediente(seccionId: Long) {
        _dialogo.value = DialogoCantidades.PonerIngrediente(seccionId = seccionId)
    }

    fun abrirCambiarCantidad(linea: LineaDeIngrediente) {
        _dialogo.value = DialogoCantidades.PonerIngrediente(
            seccionId = linea.item.seccionId,
            editando = linea.item,
            elegido = linea.ingrediente,
            // Se muestra con el formato de la app, que es el mismo que `textoANumero`
            // sabe leer de vuelta al guardar.
            cantidad = formatearNumero(linea.item.cantidadG),
            tocado = true
        )
    }

    fun buscarIngrediente(texto: String) = enDialogoIngrediente { it.copy(busqueda = texto) }

    fun elegirIngrediente(ingrediente: Ingrediente) =
        enDialogoIngrediente { it.copy(elegido = ingrediente) }

    fun cambiarCantidadEscrita(texto: String) = enDialogoIngrediente {
        it.copy(cantidad = formatearMientrasSeEscribe(texto), tocado = true)
    }

    /**
     * Crea un ingrediente sin salir de la receta y lo deja elegido (7).
     *
     * Es el momento que el `ComboBuscable` existe para resolver: darse cuenta a mitad de
     * carga de que falta un ingrediente y no tener que abandonar lo escrito para crearlo.
     */
    fun crearIngredienteRapido(nombre: String) {
        viewModelScope.launch {
            when (val resultado = ingredientes.crear(nombre, 0.0)) {
                is ResultadoGuardarIngrediente.Guardado -> {
                    val creado = ingredientes.obtener(resultado.id)
                    enDialogoIngrediente { it.copy(elegido = creado, busqueda = "") }
                    mensaje.value =
                        "Se creó '$nombre' con valor 0. Ponle su precio en Ingredientes."
                }
                is ResultadoGuardarIngrediente.YaExiste -> {
                    enDialogoIngrediente {
                        it.copy(elegido = resultado.existente, busqueda = "")
                    }
                }
                is ResultadoGuardarIngrediente.NoValido -> {
                    mensaje.value = resultado.motivo
                }
            }
        }
    }

    fun guardarIngrediente() {
        val actual = _dialogo.value as? DialogoCantidades.PonerIngrediente ?: return
        if (!actual.puedeGuardar) return
        val elegido = actual.elegido ?: return
        val gramos = textoANumero(actual.cantidad) ?: return

        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            val enEdicion = actual.editando
            if (enEdicion == null) {
                recetas.agregarIngrediente(
                    seccionId = actual.seccionId,
                    ingredienteId = elegido.id,
                    cantidadG = gramos
                )
            } else {
                recetas.cambiarCantidad(enEdicion.id, gramos)
            }
            _dialogo.value = DialogoCantidades.Ninguno
            volverALeer()
        }
    }

    fun quitarIngrediente(linea: LineaDeIngrediente) {
        viewModelScope.launch {
            recetas.quitarIngrediente(linea.item.id)
            mensaje.value = "Se quitó '${linea.ingrediente.nombre}'"
            volverALeer()
        }
    }

    // --- Secciones ---

    /**
     * Abre el cuadro de sección nueva, preguntando antes si hay que bautizar la primera.
     *
     * Esa consulta va acá y no en la pantalla porque la respuesta depende de cuántas
     * secciones hay en la base, no de lo que se esté viendo.
     */
    fun abrirAgregarSeccion() {
        viewModelScope.launch {
            _dialogo.value = DialogoCantidades.Seccion(
                nombreDeLaPrimera = recetas.nombreQueFaltaBautizar(recetaId)
            )
        }
    }

    fun cambiarNombreDeSeccion(texto: String) = enDialogoSeccion {
        it.copy(nombre = texto, tocado = true)
    }

    fun cambiarNombreDeLaPrimera(texto: String) = enDialogoSeccion {
        it.copy(nombreDeLaPrimera = texto, tocado = true)
    }

    fun guardarSeccion() {
        val actual = _dialogo.value as? DialogoCantidades.Seccion ?: return
        if (!actual.puedeGuardar) return

        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            when (
                val resultado = recetas.agregarSeccion(
                    recetaId = recetaId,
                    nombre = actual.nombre,
                    nombreDeLaPrimera = actual.nombreDeLaPrimera
                )
            ) {
                is Resultado.Listo -> {
                    _dialogo.value = DialogoCantidades.Ninguno
                    volverALeer()
                }
                is Resultado.NoSePudo -> {
                    enDialogoSeccion { it.copy(guardando = false) }
                    mensaje.value = resultado.motivo
                }
            }
        }
    }

    fun abrirRenombrarSeccion(seccion: RecetaSeccion) {
        _dialogo.value = DialogoCantidades.RenombrarSeccion(seccion, seccion.nombreSeccion)
    }

    fun cambiarNombreEnRenombrado(texto: String) {
        _dialogo.update { actual ->
            if (actual is DialogoCantidades.RenombrarSeccion) actual.copy(nombre = texto)
            else actual
        }
    }

    fun guardarRenombrado() {
        val actual = _dialogo.value as? DialogoCantidades.RenombrarSeccion ?: return
        if (!actual.puedeGuardar) return

        viewModelScope.launch {
            recetas.renombrarSeccion(actual.seccion, actual.nombre)
            _dialogo.value = DialogoCantidades.Ninguno
            volverALeer()
        }
    }

    fun pedirBorrarSeccion(seccion: SeccionConIngredientes) {
        _dialogo.value = DialogoCantidades.ConfirmarBorrarSeccion(seccion)
    }

    fun confirmarBorrarSeccion() {
        val aviso = _dialogo.value as? DialogoCantidades.ConfirmarBorrarSeccion ?: return
        if (aviso.borrando) return

        _dialogo.value = aviso.copy(borrando = true)

        viewModelScope.launch {
            when (val r = recetas.eliminarSeccion(recetaId, aviso.seccion.seccion.id)) {
                is Resultado.Listo ->
                    mensaje.value = "Se quitó '${aviso.seccion.seccion.nombreSeccion}'"
                is Resultado.NoSePudo -> mensaje.value = r.motivo
            }
            _dialogo.value = DialogoCantidades.Ninguno
            volverALeer()
        }
    }

    fun cerrarDialogo() {
        _dialogo.value = DialogoCantidades.Ninguno
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun enDialogoIngrediente(
        cambio: (DialogoCantidades.PonerIngrediente) -> DialogoCantidades.PonerIngrediente
    ) {
        _dialogo.update { actual ->
            if (actual is DialogoCantidades.PonerIngrediente) cambio(actual) else actual
        }
    }

    private fun enDialogoSeccion(
        cambio: (DialogoCantidades.Seccion) -> DialogoCantidades.Seccion
    ) {
        _dialogo.update { actual ->
            if (actual is DialogoCantidades.Seccion) cambio(actual) else actual
        }
    }

    companion object {
        fun fabrica(
            recetaId: Long,
            recetas: RecetaRepositorio,
            ingredientes: IngredienteRepositorio
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CantidadesViewModel(recetaId, recetas, ingredientes) }
        }
    }
}
