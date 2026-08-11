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
import com.sandyyera.reposteria.data.repositorio.QueHacerConElNombre
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoRenombrarEnAlmacen
import com.sandyyera.reposteria.logica.almacen.RecetaHecha
import com.sandyyera.reposteria.logica.almacen.SentidoDelMovimiento
import com.sandyyera.reposteria.logica.almacen.VistaPreviaDelDescuento
import com.sandyyera.reposteria.logica.almacen.avisoDeCantidadNegativa
import com.sandyyera.reposteria.logica.almacen.resultadoDelMovimiento
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
    val ingredienteId: Long? get() = articulo.ingredienteId
    val nombre: String get() = articulo.nombre

    /**
     * Si se ofrece al armar una receta (14.11).
     *
     * Una fila vieja sin ingrediente cuenta como que **no**: era exactamente lo que un artículo
     * suelto significaba antes de 14.5 — algo de lo que se lleva la cuenta y que no entra en
     * ninguna receta.
     */
    val vaEnRecetas: Boolean get() = articulo.vaEnRecetas ?: false
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
 * Una receta en el cuadro de descontar, con cuántas tandas se escribieron (14.9).
 *
 * [tandas] es texto y no un número porque **es un campo mientras se escribe**: guardarlo ya
 * convertido obligaría a decidir qué significa un "1," a medio teclear, y la respuesta correcta
 * es "todavía nada".
 */
data class RecetaParaDescontar(
    val recetaId: Long,
    val titulo: String,
    val tandas: String = ""
)

/**
 * Las dos formas de anotar cuánto hay (14.7 y 14.8).
 *
 * `MANUAL` es mirar el frasco y escribir lo que queda; `MOVIMIENTO` es decir qué pasó —entró o
 * salió tanto— y dejar que la app haga la cuenta. **La segunda pasó a tener dos sentidos** y
 * antes se llamaba `CALCULADORA`, cuando solo sabía restar: sumar obligaba a hacer la cuenta de
 * cabeza y anotar el total, que es exactamente el trabajo que este cuadro existe para ahorrar.
 */
enum class ModoDeEdicion { MANUAL, MOVIMIENTO }

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
    /**
         * Lo que cambiaría en un ingrediente que ya existe, mientras se pregunta (14.5.1).
         *
         * Es el resultado del repositorio tal cual y no una copia propia: lo que hay que mostrar
         * —qué cambia, y a cuántas recetas afecta— ya viene calculado ahí, y volver a deducirlo
         * acá sería una segunda versión de la misma regla.
         */
        val precioEnDisputa: ResultadoAgregarAlAlmacen.YaExisteConCambios? = null
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
        val seMovio: String = "",
        val sentido: SentidoDelMovimiento = SentidoDelMovimiento.SALE,
        val detalles: String,
        /**
         * El nombre, editable desde acá (14.10).
         *
         * Es el del ingrediente al que apunta la fila y no un texto propio, así que cambiarlo no
         * es una edición cualquiera: puede querer decir renombrar, unir o separar. Quién decide
         * cuál es `AlmacenRepositorio.renombrar`.
         */
        val nombre: String,
        val vaEnRecetas: Boolean,
        val guardando: Boolean = false
    ) : DialogoAlmacen {

        /** Lo que se va a guardar, venga del modo que venga. `null` si todavía no es un número. */
        val resultado: Double?
            get() = when (modo) {
                ModoDeEdicion.MANUAL -> textoANumero(cantidad)
                ModoDeEdicion.MOVIMIENTO -> textoANumero(seMovio)
                    ?.let { resultadoDelMovimiento(fila.cantidad, it, sentido) }
            }

        /** Cómo queda leído, con su unidad, para mostrarlo antes de confirmar (8.7.1). */
        val comoQuedaria: String?
            get() = resultado?.let { cantidadConUnidad(it, fila.esObjeto) }

        /**
         * El aviso de que el resultado queda bajo cero, o `null`.
         *
         * **No impide guardar, y ese es todo el punto** (14.8). El negativo es el dato: o entró
         * algo que no se anotó, o la receta pide más de lo que de verdad se usa. Antes esta
         * cuenta se recortaba en cero y las dos lecturas se perdían.
         */
        val avisoDelResultado: String?
            get() = resultado?.let { avisoDeCantidadNegativa(it, fila.esObjeto) }

        /**
         * Si se sacó más de lo que había anotado.
         *
         * **No es lo mismo que el aviso del negativo** y por eso convive con él: acá se compara
         * contra lo que había, y en un frasco que ya venía bajo cero sacar 10 g más no es
         * "se usó de más", es seguir hundiendo algo ya hundido.
         */
        val seFueDeRango: Boolean
            get() = modo == ModoDeEdicion.MOVIMIENTO &&
                sentido == SentidoDelMovimiento.SALE &&
                fila.cantidad >= 0 &&
                textoANumero(seMovio)?.let { seUsoDeMas(fila.cantidad, it) } == true

        /** Si el nombre escrito es distinto del que tiene. Decide si hay que intentar renombrar. */
        val nombreCambio: Boolean get() = nombre.trim() != fila.nombre.trim()

        val errorNombre: String? get() = errorEnNombreEscrito(nombre)

        val puedeGuardar: Boolean
            get() = resultado != null && errorNombre == null && !guardando
    }

    /**
     * El nombre escrito no existe en el catálogo: hay que elegir qué significa (14.10).
     *
     * Es un cuadro propio y no un aviso dentro del otro porque **son dos caminos que no se
     * deshacen igual**: renombrar toca todas las recetas que usan el ingrediente, y separar no
     * toca ninguna. Poner eso en una línea de ayuda sería esconder la decisión más grande del
     * cuadro debajo de la más chica.
     */
    data class ElegirQueHacerConElNombre(
        val volverA: CambiarCantidad,
        val nombreViejo: String,
        val nombreNuevo: String,
        val usadoEnRecetas: Int,
        val guardando: Boolean = false
    ) : DialogoAlmacen

    /**
     * La advertencia antes de que algo deje de ser un ingrediente de recetas (14.11).
     *
     * Lleva **las recetas afectadas por nombre** y no un "¿seguro?": es la misma regla de 7.1, y
     * un aviso sin la lista es un botón que se aprieta sin leer. Acá pesa más todavía, porque
     * apagarlo saca sus líneas de esas recetas y eso les mueve el costo.
     */
    data class ConfirmarSalidaDeRecetas(
        val volverA: CambiarCantidad,
        val recetasAfectadas: List<String>,
        val guardando: Boolean = false
    ) : DialogoAlmacen

    /**
     * Elegir qué recetas se hicieron, para descontar lo que llevaron (14.9).
     *
     * **Dos momentos en un mismo cuadro y no dos cuadros**: primero se eligen las recetas y las
     * tandas, después se mira la vista previa. Son dos pantallas de lo mismo, y separarlas
     * obligaría a volver atrás para corregir un número que se ve mal recién en la previa.
     *
     * [previa] en `null` es "todavía estoy eligiendo". Es lo que distingue los dos momentos, y
     * va acá y no en un booleano aparte porque no puede haber previa sin haberla calculado.
     */
    data class DescontarPorRecetas(
        val recetas: List<RecetaParaDescontar> = emptyList(),
        val busqueda: String = "",
        val previa: VistaPreviaDelDescuento? = null,
        val calculando: Boolean = false,
        val guardando: Boolean = false
    ) : DialogoAlmacen {

        /** Las que tienen un número escrito mayor que cero. Son las que se van a descontar. */
        val elegidas: List<RecetaParaDescontar>
            get() = recetas.filter { (textoANumero(it.tandas) ?: 0.0) > 0 }

        val visibles: List<RecetaParaDescontar>
            get() = recetas.filtrarPor(busqueda) { it.titulo }

        val puedeCalcular: Boolean get() = elegidas.isNotEmpty() && !calculando

        /** "Torta de manjar y 2 más", para el historial y para el título de la previa. */
        val comoSeLlamaLoQueSeHizo: String
            get() {
                val nombres = elegidas.map { it.titulo }
                return when (nombres.size) {
                    0 -> "nada"
                    1 -> "'${nombres.single()}'"
                    else -> "'${nombres.first()}' y ${nombres.size - 1} más"
                }
            }
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
    private val almacen: AlmacenRepositorio,
    // Solo para leer la lista de recetas del cuadro de descontar (14.9). El almacén podría
    // exponerla, pero eso sería hacerle de intermediario a una lista que no es suya.
    private val recetas: RecetaRepositorio
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

        // **La disputa NO se limpia acá.** Limpiarla dejaba ver el cuadro de agregar durante el
        // instante entre confirmar y que la base conteste — un parpadeo que se lee como si algo
        // hubiera vuelto atrás. Se limpia al llegar el resultado, en `terminarDeAgregar`.
        _dialogo.value = actual.copy(guardando = true)

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
                enAgregar {
                    it.copy(guardando = false, rechazo = resultado.motivo, precioEnDisputa = null)
                }

            is ResultadoAgregarAlAlmacen.YaExisteConCambios -> enAgregar {
                it.copy(guardando = false, precioEnDisputa = resultado)
            }
        }
    }

    /**
     * Deja el ingrediente tal como estaba y solo lo anota en el almacén.
     *
     * Manda **los tres datos que ya tenía el ingrediente** —precio, unidad y si va en recetas— y
     * no los escritos en el cuadro. Es lo que dice el botón: *dejarlo como está* es no tocarlo.
     * Mandando los del cuadro, el repositorio volvería a encontrar diferencias y preguntaría lo
     * mismo otra vez; mandando estos, no hay nada que confirmar y el artículo entra derecho.
     *
     * **No se puede resolver reescribiendo el campo del precio**, que es lo que hacía antes:
     * desde 14.5.2 ese campo dice lo que costó *todo*, no lo que cuesta cada unidad, así que
     * poner ahí el valor guardado escribiría un número que significa otra cosa. Va por su propia
     * llamada al repositorio.
     */
    fun conservarElPrecioGuardado() {
        val actual = _dialogo.value as? DialogoAlmacen.Agregar ?: return
        val disputa = actual.precioEnDisputa ?: return
        val cuanto = textoANumero(actual.cantidad) ?: return

        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            val resultado = almacen.agregar(
                nombre = actual.nombre,
                esObjeto = disputa.existente.esObjeto,
                vaEnRecetas = disputa.existente.vaEnRecetas,
                cantidad = cuanto,
                valor = disputa.existente.valorPorGramo,
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
            detalles = fila.detalles.orEmpty(),
            nombre = fila.nombre,
            vaEnRecetas = fila.vaEnRecetas
        )
    }

    fun cambiarModoDeEdicion(modo: ModoDeEdicion) = enEdicion { it.copy(modo = modo) }

    fun cambiarSentidoDelMovimiento(sentido: SentidoDelMovimiento) =
        enEdicion { it.copy(sentido = sentido) }

    fun cambiarCantidadEnEdicion(texto: String) = enEdicion {
        it.copy(cantidad = formatearMientrasSeEscribe(texto))
    }

    fun cambiarLoQueSeMovio(texto: String) = enEdicion {
        it.copy(seMovio = formatearMientrasSeEscribe(texto))
    }

    fun cambiarNombreEnEdicion(texto: String) = enEdicion { it.copy(nombre = texto) }

    /**
     * Marca o desmarca "se puede usar en recetas" (14.11).
     *
     * **Solo cambia lo que se ve; no escribe.** Apagarlo saca de verdad sus líneas de las
     * recetas, así que la decisión pasa por su advertencia al guardar y no por el interruptor —
     * un cambio destructivo no puede quedar hecho por el gesto de mirar una casilla.
     */
    fun cambiarVaEnRecetasEnEdicion(valor: Boolean) = enEdicion { it.copy(vaEnRecetas = valor) }

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

            // **El orden importa: primero lo que puede abrir otro cuadro.** La cantidad y las
            // notas ya quedaron guardadas, así que si el nombre o el interruptor obligan a
            // preguntar, lo que se pregunta es solo eso y no se pierde el resto de la edición.
            if (actual.nombreCambio && intentarRenombrar(actual, confirmado = null)) return@launch
            if (pedirConfirmacionDeRecetas(actual)) return@launch

            terminarEdicion(actual)
        }
    }

    /**
     * Intenta el renombre. Devuelve `true` si dejó un cuadro abierto y hay que parar acá.
     *
     * Es `suspend` y no lanza su propia corrutina porque **va encadenado con el resto del
     * guardado**: lanzando una aparte, el cuadro se cerraría mientras la pregunta viaja y la
     * respuesta llegaría a una pantalla que ya se fue.
     */
    private suspend fun intentarRenombrar(
        actual: DialogoAlmacen.CambiarCantidad,
        confirmado: QueHacerConElNombre?
    ): Boolean {
        return when (val r = almacen.renombrar(actual.fila.id, actual.nombre, confirmado)) {
            is ResultadoRenombrarEnAlmacen.Listo -> {
                mensaje.value = r.comoQuedo
                false
            }

            is ResultadoRenombrarEnAlmacen.NoSePudo -> {
                // Vuelve al cuadro con el aviso **junto al campo** y no en la franja de abajo,
                // que con el teclado abierto queda tapada (8.2).
                mensaje.value = r.motivo
                _dialogo.value = actual.copy(guardando = false)
                true
            }

            is ResultadoRenombrarEnAlmacen.HayQueElegir -> {
                _dialogo.value = DialogoAlmacen.ElegirQueHacerConElNombre(
                    volverA = actual.copy(guardando = false),
                    nombreViejo = r.nombreViejo,
                    nombreNuevo = r.nombreNuevo,
                    usadoEnRecetas = r.usadoEnRecetas
                )
                true
            }
        }
    }

    /** Contesta el cuadro de "renombrar o separar". */
    fun resolverElNombre(que: QueHacerConElNombre) {
        val cuadro = _dialogo.value as? DialogoAlmacen.ElegirQueHacerConElNombre ?: return
        if (cuadro.guardando) return
        _dialogo.value = cuadro.copy(guardando = true)

        viewModelScope.launch {
            if (intentarRenombrar(cuadro.volverA, confirmado = que)) return@launch
            if (pedirConfirmacionDeRecetas(cuadro.volverA)) return@launch
            terminarEdicion(cuadro.volverA)
        }
    }

    /**
     * Si apagar "va en recetas" necesita confirmarse, abre el aviso y devuelve `true`.
     *
     * **Solo apagar pregunta.** Encenderlo agrega algo a la lista de lo que se puede elegir y no
     * le quita nada a nadie; apagarlo saca sus líneas de las recetas que lo usan y les mueve el
     * costo, que es exactamente lo que 7.1 pide avisar con los nombres a la vista.
     */
    private suspend fun pedirConfirmacionDeRecetas(
        actual: DialogoAlmacen.CambiarCantidad
    ): Boolean {
        val ingredienteId = actual.fila.ingredienteId ?: return false
        if (actual.vaEnRecetas || actual.vaEnRecetas == actual.fila.vaEnRecetas) return false

        val afectadas = almacen.recetasQueUsan(ingredienteId)
        _dialogo.value = DialogoAlmacen.ConfirmarSalidaDeRecetas(
            volverA = actual.copy(guardando = false),
            recetasAfectadas = afectadas.map { it.titulo }
        )
        return true
    }

    /** Contesta el aviso de "deja de ser un ingrediente de recetas". */
    fun confirmarSalidaDeRecetas() {
        val aviso = _dialogo.value as? DialogoAlmacen.ConfirmarSalidaDeRecetas ?: return
        if (aviso.guardando) return
        _dialogo.value = aviso.copy(guardando = true)

        viewModelScope.launch { terminarEdicion(aviso.volverA) }
    }

    /**
     * Escribe el interruptor de recetas si cambió, y cierra.
     *
     * Es el final común de los tres caminos —guardar directo, después del nombre y después de la
     * advertencia— para que cerrar el cuadro y aplicar el interruptor no queden escritos tres
     * veces con tres criterios.
     */
    private suspend fun terminarEdicion(actual: DialogoAlmacen.CambiarCantidad) {
        val ingredienteId = actual.fila.ingredienteId
        if (ingredienteId != null && actual.vaEnRecetas != actual.fila.vaEnRecetas) {
            val r = almacen.cambiarVaEnRecetas(ingredienteId, actual.vaEnRecetas)
            if (r is Resultado.NoSePudo) mensaje.value = r.motivo
        }
        _dialogo.value = DialogoAlmacen.Ninguno
    }

    // --- Descontar lo que se gastó haciendo recetas (14.9) ---

    /**
     * Abre el cuadro con **todas** las recetas y ninguna elegida.
     *
     * Las recetas se leen **una sola vez** y no observadas, que es la regla de 12.2.1: mientras
     * el cuadro está abierto, una receta que aparece o desaparece movería la lista bajo el dedo.
     * La foto de un momento es justamente para esto.
     */
    fun abrirDescuentoPorRecetas() {
        _dialogo.value = DialogoAlmacen.DescontarPorRecetas(calculando = true)
        viewModelScope.launch {
            val todas = recetas.obtenerTodasUnaVez()
            _dialogo.update { actual ->
                if (actual !is DialogoAlmacen.DescontarPorRecetas) actual
                else actual.copy(
                    recetas = todas.map { RecetaParaDescontar(it.id, it.titulo) },
                    calculando = false
                )
            }
        }
    }

    fun buscarRecetaParaDescontar(texto: String) = enDescuento { it.copy(busqueda = texto) }

    fun cambiarTandas(recetaId: Long, texto: String) = enDescuento { actual ->
        actual.copy(
            recetas = actual.recetas.map { fila ->
                if (fila.recetaId == recetaId) {
                    fila.copy(tandas = formatearMientrasSeEscribe(texto))
                } else {
                    fila
                }
            }
        )
    }

    /**
     * Calcula qué pasaría, **sin escribir nada**.
     *
     * Es el paso que hace que esto se pueda usar sin miedo: descontar toca muchas filas de una
     * vez y es lo más destructivo del almacén. Mirar antes, fila por fila, es lo que separa una
     * herramienta útil de una que hay que deshacer a mano.
     */
    fun calcularElDescuento() {
        val actual = _dialogo.value as? DialogoAlmacen.DescontarPorRecetas ?: return
        if (!actual.puedeCalcular) return
        _dialogo.value = actual.copy(calculando = true)

        viewModelScope.launch {
            val hechas = actual.elegidas.map {
                RecetaHecha(it.recetaId, it.titulo, textoANumero(it.tandas) ?: 0.0)
            }
            val previa = almacen.vistaPreviaDeDescontar(hechas)
            _dialogo.update { abierto ->
                if (abierto !is DialogoAlmacen.DescontarPorRecetas) abierto
                else abierto.copy(previa = previa, calculando = false)
            }
        }
    }

    /** Vuelve de la vista previa a elegir recetas, sin perder los números escritos. */
    fun volverAElegirRecetas() = enDescuento { it.copy(previa = null) }

    /** Escribe el descuento. Solo se llega acá desde la vista previa. */
    fun confirmarElDescuento() {
        val actual = _dialogo.value as? DialogoAlmacen.DescontarPorRecetas ?: return
        val previa = actual.previa ?: return
        if (actual.guardando) return
        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            // Se manda la previa **ya calculada** y no las recetas otra vez: lo que se confirma
            // tiene que ser exactamente lo que se vio.
            val r = almacen.descontar(previa, actual.comoSeLlamaLoQueSeHizo)
            mensaje.value = when (r) {
                is Resultado.NoSePudo -> r.motivo
                is Resultado.Listo -> "Se descontó del almacén"
            }
            _dialogo.value = DialogoAlmacen.Ninguno
        }
    }

    private fun enDescuento(
        cambio: (DialogoAlmacen.DescontarPorRecetas) -> DialogoAlmacen.DescontarPorRecetas
    ) {
        _dialogo.update { actual ->
            if (actual is DialogoAlmacen.DescontarPorRecetas) cambio(actual) else actual
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
        fun fabrica(
            almacen: AlmacenRepositorio,
            recetas: RecetaRepositorio
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AlmacenViewModel(almacen, recetas) }
        }
    }
}
