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
import com.sandyyera.reposteria.logica.validaciones.errorEnTituloReceta
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
) {
    /**
     * Lo que cuesta esta sección sola.
     *
     * Se suma **en memoria** y no con una consulta aparte, al revés que el costo total de la
     * receta. No es una inconsistencia: es la misma regla mirada de cerca. El total manda
     * porque de él salen los precios y los sueldos, así que tiene que venir de la base. Este
     * número no alimenta ninguna cuenta — solo sirve para leer la receta — y lo que sí tiene
     * que hacer es **cuadrar con las líneas que se ven arriba de él**. Sumando esas mismas
     * líneas, cuadra por construcción; pidiéndolo por separado, podría no cuadrar y no
     * habría forma de explicar la diferencia mirando la pantalla.
     *
     * Que la suma de todas las secciones dé el total de la base está cubierto por una prueba.
     */
    val costo: Double get() = lineas.sumOf { it.subtotal }
}

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
        val guardando: Boolean = false,
        val rechazo: String? = null
    ) : DialogoCantidades {

        /**
         * [rechazo] es lo que contestó el repositorio, y por eso va **primero**.
         *
         * Vive acá y no en el mensaje de abajo por algo que se vio en el celular: con el
         * teclado abierto, el aviso de la parte inferior queda tapado y el cuadro parece no
         * haber hecho nada. **Un error sobre lo que se acaba de escribir se muestra al lado
         * del campo, nunca en la franja de abajo**, que es para lo que ya pasó y el teclado
         * no está estorbando.
         */
        val error: String? get() = rechazo ?: errorEnNombreSeccion(nombre).takeIf { tocado }

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
        val guardando: Boolean = false,
        val rechazo: String? = null
    ) : DialogoCantidades {
        /** Mismo criterio que en [Seccion]: el aviso va junto al campo, no abajo. */
        val error: String? get() = rechazo ?: errorEnNombreSeccion(nombre)

        val puedeGuardar: Boolean get() = errorEnNombreSeccion(nombre) == null && !guardando
    }

    /**
     * Cambiarle el título a la receta, sin salir de ella.
     *
     * Vive acá y no en la lista de recetas porque ahí ya no hay lápiz: **tocar la cosa la
     * edita**, y una receta de la lista se toca para abrirla. El título se cambia desde
     * adentro, que además es donde uno se da cuenta de que la receta terminó siendo otra
     * cosa de la que se llamó al crearla.
     *
     * Lleva [rechazo] por lo mismo que los cuadros de sección: un título repetido lo detecta
     * el repositorio, y ese aviso va **junto al campo** y no en la franja de abajo, que con
     * el teclado abierto queda tapada.
     */
    data class RenombrarReceta(
        val titulo: String,
        val guardando: Boolean = false,
        val rechazo: String? = null
    ) : DialogoCantidades {

        val error: String? get() = rechazo ?: errorEnTituloReceta(titulo)

        val puedeGuardar: Boolean get() = errorEnTituloReceta(titulo) == null && !guardando
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

    /**
     * Si cada sección muestra al lado lo que cuesta.
     *
     * **Desde dos secciones**, y no desde una con nombre propio como pasa con los
     * encabezados. Con una sola, su costo es el total que ya está arriba en grande: repetir
     * el mismo número dos veces en la misma pantalla no informa, hace dudar de si son dos
     * cosas distintas. El dato aparece justo cuando empieza a servir, que es cuando hay
     * partes que comparar entre sí.
     */
    val mostrarCostoPorSeccion: Boolean get() = secciones.size > 1
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

    // Al escribir, el rechazo anterior deja de aplicar: era sobre lo que había antes.
    fun cambiarNombreDeSeccion(texto: String) = enDialogoSeccion {
        it.copy(nombre = texto, tocado = true, rechazo = null)
    }

    fun cambiarNombreDeLaPrimera(texto: String) = enDialogoSeccion {
        it.copy(nombreDeLaPrimera = texto, tocado = true, rechazo = null)
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
                is Resultado.NoSePudo ->
                    // Al campo y no al mensaje de abajo: el teclado está abierto y lo taparía.
                    enDialogoSeccion { it.copy(guardando = false, rechazo = resultado.motivo) }
            }
        }
    }

    fun abrirRenombrarSeccion(seccion: RecetaSeccion) {
        _dialogo.value = DialogoCantidades.RenombrarSeccion(seccion, seccion.nombreSeccion)
    }

    fun cambiarNombreEnRenombrado(texto: String) {
        _dialogo.update { actual ->
            if (actual is DialogoCantidades.RenombrarSeccion) {
                actual.copy(nombre = texto, rechazo = null)
            } else {
                actual
            }
        }
    }

    fun guardarRenombrado() {
        val actual = _dialogo.value as? DialogoCantidades.RenombrarSeccion ?: return
        if (!actual.puedeGuardar) return

        viewModelScope.launch {
            // El resultado se mira: desde que los nombres de sección no se pueden repetir,
            // renombrar puede fallar. Descartándolo, el cuadro se cerraba como si hubiera
            // funcionado y el nombre seguía siendo el de antes, sin ninguna explicación.
            when (val resultado = recetas.renombrarSeccion(actual.seccion, actual.nombre)) {
                is Resultado.Listo -> _dialogo.value = DialogoCantidades.Ninguno
                is Resultado.NoSePudo ->
                    // El cuadro queda abierto, con lo escrito y con el motivo bajo el campo:
                    // hay que corregirlo, no volver a escribirlo entero, y con el teclado
                    // abierto un aviso en la franja de abajo no se ve.
                    _dialogo.update { actualDialogo ->
                        if (actualDialogo is DialogoCantidades.RenombrarSeccion) {
                            actualDialogo.copy(guardando = false, rechazo = resultado.motivo)
                        } else {
                            actualDialogo
                        }
                    }
            }
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

    // --- El título de la receta ---

    /**
     * Abre el cuadro para cambiarle el título a la receta.
     *
     * El título se lee **de la base** y no de `estado.value`: el `combine` de arriba deja de
     * emitir cinco segundos después de que la pantalla se oculta, así que su último valor
     * puede ser de antes del último cambio. Es la misma razón por la que
     * [abrirAgregarSeccion] consulta en vez de mirar el estado.
     */
    fun abrirRenombrarReceta() {
        viewModelScope.launch {
            val receta = recetas.obtener(recetaId) ?: return@launch
            _dialogo.value = DialogoCantidades.RenombrarReceta(titulo = receta.titulo)
        }
    }

    // Al escribir, el rechazo anterior deja de aplicar: era sobre el título de antes.
    fun cambiarTituloDeLaReceta(texto: String) {
        _dialogo.update { actual ->
            if (actual is DialogoCantidades.RenombrarReceta) {
                actual.copy(titulo = texto, rechazo = null)
            } else {
                actual
            }
        }
    }

    fun guardarTituloDeLaReceta() {
        val actual = _dialogo.value as? DialogoCantidades.RenombrarReceta ?: return
        if (!actual.puedeGuardar) return

        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            when (val resultado = recetas.renombrar(recetaId, actual.titulo.trim())) {
                is Resultado.Listo -> _dialogo.value = DialogoCantidades.Ninguno
                // El cuadro queda abierto con lo escrito y el motivo bajo el campo: hay que
                // corregirlo, no volver a escribirlo entero.
                is Resultado.NoSePudo -> _dialogo.update { actualDialogo ->
                    if (actualDialogo is DialogoCantidades.RenombrarReceta) {
                        actualDialogo.copy(guardando = false, rechazo = resultado.motivo)
                    } else {
                        actualDialogo
                    }
                }
            }
            // Se relee siempre: el título vive en el encabezado de esta misma pantalla.
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
