package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.db.entidades.aVigente
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.TrozoGanador
import com.sandyyera.reposteria.logica.precios.AVISO_TROZO_SUELTO
import com.sandyyera.reposteria.logica.precios.RepartoDeVenta
import com.sandyyera.reposteria.logica.precios.basesQueFaltanEn
import com.sandyyera.reposteria.logica.precios.esPrecioBase
import com.sandyyera.reposteria.logica.precios.repartoDeUnProducto
import com.sandyyera.reposteria.logica.precios.costoPorTrozo
import com.sandyyera.reposteria.logica.precios.gananciaFinal
import com.sandyyera.reposteria.logica.precios.gananciaPorTrozo
import com.sandyyera.reposteria.logica.precios.gananciaPorTrozoDe
import com.sandyyera.reposteria.logica.precios.ingresoBruto
import com.sandyyera.reposteria.logica.precios.precioPorTrozoDe
import com.sandyyera.reposteria.logica.precios.trozoGanador
import com.sandyyera.reposteria.logica.precios.trozosCubiertosPor
import com.sandyyera.reposteria.logica.validaciones.MAXIMA_CANTIDAD_DE_PRECIO
import com.sandyyera.reposteria.logica.validaciones.descripcionDePromocion
import com.sandyyera.reposteria.logica.validaciones.revisarPrecio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Qué hay abierto encima del paso de gastos. */
sealed interface DialogoGastos {

    data object Ninguno : DialogoGastos

    /**
     * El formulario de un precio, para crearlo o para editarlo.
     *
     * Es uno solo para los dos casos porque piden exactamente los mismos cuatro datos; lo
     * que cambia es el título y qué hace el ViewModel al confirmar. [editando] en `null` es
     * un precio nuevo.
     *
     * [trozosDeLaReceta] entra al estado y no se consulta al validar porque de él depende el
     * **tope del último trozo** (6.2): una promo de 3 trozos no cabe en una receta que rinde
     * 2, y ese aviso tiene que aparecer mientras se escribe, no al confirmar.
     */
    data class Formulario(
        val editando: RecetaPrecio? = null,
        val modo: ModoPrecio = ModoPrecio.TROZO,
        val cantidad: String = "1",
        val precioTotal: String = "",
        val etiqueta: String = "",
        val trozosDeLaReceta: Int = 1,
        /**
         * Los precios base que la receta todavía no tiene, y que **impiden armar promociones**
         * (8.6.1). Entra al estado por lo mismo que [trozosDeLaReceta]: es una condición de
         * afuera de la que depende un aviso que tiene que aparecer **mientras se escribe**.
         *
         * Va vacía al **editar** un precio que ya existe, y eso es deliberado: la regla es para
         * el orden en que se arma una receta, no una traba para corregir lo que ya está
         * guardado. Bloquear la edición dejaría una promo mal escrita sin forma de arreglarse.
         */
        val basesQueFaltan: List<ModoPrecio> = emptyList(),
        val tocado: Boolean = false,
        val guardando: Boolean = false,
        /** Lo que contestó el repositorio. Va dentro del cuadro, nunca abajo (8.2). */
        val rechazo: String? = null
    ) : DialogoGastos {

        private val errores get() =
            revisarPrecio(precioTotal, cantidad, modo, trozosDeLaReceta, etiqueta, basesQueFaltan)

        /**
         * Si este formulario está poniendo uno de los dos precios base obligatorios.
         *
         * Lo usa la pantalla para explicar por qué la cantidad está fija en 1 en vez de dejar
         * que se descubra tecleando un 2 y chocando con un aviso.
         */
        val estaPoniendoUnaBase: Boolean get() = basesQueFaltan.isNotEmpty()

        val errorPrecioTotal: String? get() = rechazo ?: errores.precioTotal.takeIf { tocado }
        val errorCantidad: String? get() = errores.cantidad.takeIf { tocado }
        val errorEtiqueta: String? get() = errores.etiqueta.takeIf { tocado }

        val puedeGuardar: Boolean get() = errores.sirve && !guardando

        /**
         * Si este formulario está describiendo una promoción y no el precio suelto.
         *
         * Sirve para el texto de ayuda: con cantidad 1 no hay nada que explicar, y desde 2
         * conviene decir que el precio escrito es **el total del paquete** y no el de cada
         * uno — que es justo donde se equivoca uno al cargar un "2 por $1.500".
         */
        val esPromocion: Boolean get() = (cantidad.toIntOrNull() ?: 1) > 1
    }

    /** La confirmación antes de borrar un precio. [comoSeLlama] ya viene legible. */
    data class ConfirmarBorrado(val precio: RecetaPrecio, val comoSeLlama: String) : DialogoGastos
}

/**
 * Un precio guardado, con lo que rinde, listo para dibujar.
 *
 * Cada fila trae **su propia ganancia**, y esa es la razón de que la lista exista: sirve para
 * comparar promociones entre sí, no solo para verlas. La de referencia es la que alimenta las
 * cifras de arriba.
 */
data class FilaDePrecio(
    val precio: RecetaPrecio,
    val comoSeLlama: String,
    val trozosQueCubre: Int,
    val precioPorTrozo: Double,
    val gananciaPorTrozo: Double,
    val esReferencia: Boolean,
    /** Si es uno de los dos precios base (cantidad 1) y no una promoción. */
    val esBase: Boolean = false
) {
    /**
     * Si vender a este precio deja pérdida.
     *
     * **No impide guardarlo ni verlo** (8.6): una promo que pierde plata sigue en la lista,
     * con su ganancia en negativo, porque esa es justamente la información que hace falta
     * para descartarla. Lo único que no puede es ser la referencia.
     */
    val pierdePlata: Boolean get() = gananciaPorTrozo < 0
}

/**
 * Lo que el paso de gastos y ganancias necesita para dibujarse (8.5 y 8.6).
 *
 * Todas las cifras salen de **un solo** `DatosCalculoReceta`, que es el punto de 6.4: se
 * observa una vez y de ahí salen los siete campos automáticos sin volver a la base ni una vez.
 */
data class EstadoGastos(
    val datos: DatosCalculoReceta? = null,
    val filas: List<FilaDePrecio> = emptyList(),
    /**
     * Si la receta tiene ingredientes cargados, **aparte del costo**.
     *
     * Repite la lección que ya costó un bug: un ingrediente puede valer 0 a propósito, así
     * que "costo 0" no significa "receta vacía". Acá pesa más que en ningún otro paso —
     * con costo 0 todas las ganancias que se muestran son el precio entero, y sin este
     * aviso parecen un negocio redondo.
     */
    val tieneIngredientes: Boolean = false,
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    val tienePrecio: Boolean get() = datos?.tienePrecio == true

    /** Si la referencia la eligió alguien, o es el respaldo del de menor ganancia (8.6). */
    val referenciaElegidaAMano: Boolean get() = datos?.tieneReferenciaElegida == true

    val costoTotal: Double get() = datos?.costoTotal ?: 0.0
    val trozos: Int get() = datos?.trozos ?: 1

    /** Lo que cuesta cada trozo. Se puede mostrar sin tener ningún precio guardado. */
    val costoDeCadaTrozo: Double get() = datos?.let { costoPorTrozo(it) } ?: 0.0

    // Las cifras automáticas. Todas dan `null` sin precios: una receta que todavía no pasó
    // por este paso no tiene ingreso ni ganancia, y mostrar 0 diría que se vende y no deja
    // nada, que es otra cosa.
    //
    // **No se llaman igual que las funciones que las calculan** —`ingresoDelProducto` y no
    // `ingresoBruto`— a propósito: una propiedad y una función homónimas en el mismo alcance
    // compilan, pero dejan un `ingresoBruto(it)` dentro de `val ingresoBruto` que hay que
    // leer dos veces para saber a cuál de las dos llama.
    val ingresoDelProducto: Double?
        get() = datos?.takeIf { it.tienePrecio }?.let { ingresoBruto(it) }
    val gananciaDeCadaTrozo: Double?
        get() = datos?.takeIf { it.tienePrecio }?.let { gananciaPorTrozo(it) }
    val gananciaDelProducto: Double?
        get() = datos?.takeIf { it.tienePrecio }?.let { gananciaFinal(it) }
    val elTrozoGanador: TrozoGanador?
        get() = datos?.takeIf { it.tienePrecio }?.let { trozoGanador(it) }

    /**
     * Si la receta se vende bajo su costo con el precio de referencia.
     *
     * Se puede llegar acá **sin haber elegido nada malo**: `errorAlElegirReferencia` impide
     * elegir a mano una promo que pierde, pero el mismo estado aparece solo si sube el costo
     * de un ingrediente en otra pantalla. Por eso esto se **muestra** en vez de prevenirse.
     */
    val laReferenciaPierdePlata: Boolean get() = (gananciaDeCadaTrozo ?: 0.0) < 0

    // --- Los dos precios base y el resto de las promociones (8.6.1) ---

    // `baseDelTrozo` y `baseDelProducto` vivían acá y se fueron al pasar `basesQueFaltan` a
    // delegar en `basesQueFaltanEn`: nadie los leía ya. Se eliminan en vez de dejarlos por si
    // acaso, que es la regla de siempre — una rama que nadie recorre es una rama que nadie
    // prueba. Las dos funciones de `logica/` siguen ahí para quien las necesite de verdad.

    /**
     * Cuáles de los dos precios base faltan por definir.
     *
     * Los dos son la base sobre la que se apoya todo lo demás: sin el del trozo, una
     * promoción que deja un trozo suelto no tiene con qué venderlo; sin el del producto, una
     * promoción de varios productos tampoco. La pantalla los pide primero por eso, y desde el
     * arreglo que pidió Sandy **no deja escribir una promoción hasta tenerlos**.
     *
     * Delega en `basesQueFaltanEn` en vez de repetir la condición: el repositorio aplica la
     * misma regla al guardar, y escritas por separado la pantalla terminaría habilitando el
     * botón para algo que allá se rechaza. Mientras no hayan llegado los datos devuelve la
     * lista vacía, para no pedir precios de una receta que todavía no se leyó.
     */
    val basesQueFaltan: List<ModoPrecio>
        get() = datos?.let { basesQueFaltanEn(it.precios) }.orEmpty()

    /** Cómo se reparte de verdad la venta de un producto al precio de referencia. */
    val reparto: RepartoDeVenta? get() = datos?.takeIf { it.tienePrecio }?.let { repartoDeUnProducto(it) }

    /**
     * El aviso de que la promoción no dividió exacto, o `null` si dividió.
     *
     * Es lo que pidió Sandy: que se avise **mientras se aplica la regla**, no en un manual.
     * Si además falta el precio individual, lo dice en vez de mostrar un total que da de
     * menos sin explicar por qué.
     */
    val avisoDelResto: String?
        get() {
            val r = reparto ?: return null
            if (!r.huboResto) return null
            val cuantos = if (r.sueltos == 1) "Quedó 1 trozo suelto" else "Quedaron ${r.sueltos} trozos sueltos"
            return if (r.faltaElPrecioSuelto) {
                "$cuantos y todavía no tienen precio individual: lo de abajo cuenta solo la promoción."
            } else {
                "$cuantos. $AVISO_TROZO_SUELTO."
            }
        }
}

/**
 * El cerebro del paso "Gastos y Ganancias" (8.5 y 8.6).
 *
 * **Es el primer paso que no guarda nada mientras se escribe**, y no es una excepción al
 * guardado automático de 8.4.1: acá no hay campos sueltos que llenar sino precios que se
 * crean, se editan y se borran de una lista. Lo que se escribe vive en un cuadro y se
 * confirma; el paso en sí no tiene nada propio que guardar.
 *
 * Todo lo que muestra sale de **un** `DatosCalculoReceta` observado (6.4). Tenía que ser
 * observado y no leído una vez porque de las cinco cosas que lleva, **tres las escriben otros
 * pasos**: el costo lo mueven los ingredientes, los trozos el rendimiento y el título el
 * primero. Con una foto vieja este paso mostraría ganancias calculadas contra un costo que ya
 * cambió, y eso no se ve — el número es creíble.
 */
class GastosViewModel(
    private val recetaId: Long,
    private val recetas: RecetaRepositorio
) : ViewModel() {

    private val mensaje = MutableStateFlow<String?>(null)
    private val _dialogo = MutableStateFlow<DialogoGastos>(DialogoGastos.Ninguno)

    /** El cuadro va por su propio canal, fuera del `combine` del estado (12.2.1). */
    val dialogo: StateFlow<DialogoGastos> = _dialogo

    val estado: StateFlow<EstadoGastos> = combine(
        recetas.observarDatosCalculo(recetaId),
        recetas.observarPrecios(recetaId),
        recetas.observarIngredientes(recetaId),
        mensaje
    ) { datos, precios, ingredientes, mensajeActual ->
        EstadoGastos(
            datos = datos,
            filas = datos?.let { armarFilas(it, precios) }.orEmpty(),
            tieneIngredientes = ingredientes.isNotEmpty(),
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoGastos()
    )

    /**
     * Cruza cada fila guardada con lo que rinde.
     *
     * Se hace acá y no en `logica/` porque necesita las dos mitades: la fila de Room, que
     * lleva el `id` con el que se toca, y el `PrecioVigente`, que es lo que entienden las
     * fórmulas.
     */
    private fun armarFilas(
        datos: DatosCalculoReceta,
        precios: List<RecetaPrecio>
    ): List<FilaDePrecio> {
        if (precios.isEmpty()) return emptyList()
        val idDeLaQueManda = idDeLaReferencia(datos, precios)
        return precios.map { fila ->
            val vigente = fila.aVigente()
            FilaDePrecio(
                precio = fila,
                comoSeLlama = descripcionDePromocion(vigente),
                trozosQueCubre = trozosCubiertosPor(vigente, datos),
                precioPorTrozo = precioPorTrozoDe(vigente, datos),
                gananciaPorTrozo = gananciaPorTrozoDe(vigente, datos),
                esReferencia = fila.id == idDeLaQueManda,
                esBase = esPrecioBase(vigente)
            )
        }
    }

    /**
     * **Qué fila** está alimentando las cifras. Es la misma regla de `precioDeReferencia`,
     * resuelta por `id`.
     *
     * Hace falta repetirla y no es un descuido: `PrecioVigente` **no lleva `id` a propósito**
     * —las fórmulas no necesitan identificadores— así que lo que devuelve `precioDeReferencia`
     * no se puede volver a atar a su fila. Y comparar por valor no sirve: `PrecioVigente` es
     * un `data class`, de modo que dos promociones idénticas —un "2 por $1.500" cargado dos
     * veces, que es un error corriente— son iguales entre sí y la lista marcaría **las dos**
     * como referencia.
     *
     * Que sea la misma regla lo cuida una prueba que compara las dos respuestas.
     */
    private fun idDeLaReferencia(datos: DatosCalculoReceta, precios: List<RecetaPrecio>): Long? {
        precios.firstOrNull { it.esReferencia }?.let { return it.id }
        // Sin ninguna elegida manda la de menor ganancia, que es el respaldo de 8.6. Se
        // recorre en el mismo orden que `precioDeMenorGanancia`, así que ante un empate las
        // dos se quedan con la primera.
        return precios.minByOrNull { gananciaPorTrozoDe(it.aVigente(), datos) }?.id
    }

    // --- El cuadro del precio ---

    fun abrirPrecioNuevo() {
        val faltan = estado.value.basesQueFaltan
        _dialogo.value = DialogoGastos.Formulario(
            trozosDeLaReceta = estado.value.trozos,
            basesQueFaltan = faltan,
            // **El cuadro se abre en la base que falta**, no siempre en trozos. Mientras falte
            // alguna, es lo único que se puede guardar, así que empezar en el modo equivocado
            // obligaría a cambiarlo a mano para descubrir después que la cantidad tampoco se
            // puede mover. Puestas las dos, arranca en trozos, que es lo que más se carga.
            modo = faltan.firstOrNull() ?: ModoPrecio.TROZO,
            cantidad = "1"
        )
    }

    fun abrirEditarPrecio(precio: RecetaPrecio) {
        _dialogo.value = DialogoGastos.Formulario(
            editando = precio,
            modo = precio.modo,
            cantidad = precio.cantidad.toString(),
            // Con el formato de la app, que es el mismo que `textoANumero` lee de vuelta:
            // abrir y guardar sin cambiar nada no puede alterar el número.
            precioTotal = formatearNumero(precio.precioTotal),
            etiqueta = precio.etiqueta.orEmpty(),
            trozosDeLaReceta = estado.value.trozos,
            // Vacía a propósito: ver `basesQueFaltan` en el formulario. Corregir un precio ya
            // guardado no puede quedar bloqueado por el orden en que se armó la receta.
            basesQueFaltan = emptyList(),
            tocado = true
        )
    }

    fun elegirModo(modo: ModoPrecio) = enFormulario { it.copy(modo = modo, rechazo = null) }

    fun cambiarCantidad(texto: String) = enFormulario {
        // Solo dígitos: media promoción no existe, y el punto de mil no aplica a "3 trozos".
        it.copy(
            cantidad = texto.filter { c -> c.isDigit() }.take(MAXIMA_CANTIDAD_DE_PRECIO.toString().length),
            tocado = true,
            rechazo = null
        )
    }

    fun cambiarPrecioTotal(texto: String) = enFormulario {
        it.copy(precioTotal = formatearMientrasSeEscribe(texto), tocado = true, rechazo = null)
    }

    fun cambiarEtiqueta(texto: String) = enFormulario {
        it.copy(etiqueta = texto, tocado = true, rechazo = null)
    }

    fun guardarPrecio() {
        val actual = _dialogo.value as? DialogoGastos.Formulario ?: return
        if (!actual.puedeGuardar) return

        _dialogo.value = actual.copy(guardando = true)
        viewModelScope.launch {
            val enEdicion = actual.editando
            val resultado = if (enEdicion == null) {
                recetas.crearPrecio(
                    recetaId = recetaId,
                    modo = actual.modo,
                    cantidadTexto = actual.cantidad,
                    precioTotalTexto = actual.precioTotal,
                    etiqueta = actual.etiqueta
                )
            } else {
                recetas.editarPrecio(
                    precioId = enEdicion.id,
                    modo = actual.modo,
                    cantidadTexto = actual.cantidad,
                    precioTotalTexto = actual.precioTotal,
                    etiqueta = actual.etiqueta
                )
            }
            when (resultado) {
                is Resultado.Listo -> _dialogo.value = DialogoGastos.Ninguno
                is Resultado.NoSePudo -> _dialogo.value =
                    actual.copy(guardando = false, rechazo = resultado.motivo)
            }
        }
    }

    // --- Elegir la referencia ---

    /**
     * Cambia cuál precio alimenta las cifras automáticas.
     *
     * El rechazo va **al aviso de abajo y no dentro de un cuadro**, al revés que el resto: acá
     * no hay ningún cuadro abierto ni teclado tapando nada — se tocó una fila de la lista — y
     * lo que hay que decir es por qué esa fila no se puede usar.
     */
    fun elegirReferencia(precio: RecetaPrecio) {
        if (estado.value.filas.firstOrNull { it.precio.id == precio.id }?.esReferencia == true) {
            return
        }
        viewModelScope.launch {
            recetas.elegirPrecioDeReferencia(recetaId, precio.id)?.let { mensaje.value = it }
        }
    }

    // --- Borrar ---

    fun pedirBorrado(fila: FilaDePrecio) {
        _dialogo.value = DialogoGastos.ConfirmarBorrado(fila.precio, fila.comoSeLlama)
    }

    fun confirmarBorrado() {
        val actual = _dialogo.value as? DialogoGastos.ConfirmarBorrado ?: return
        _dialogo.value = DialogoGastos.Ninguno
        viewModelScope.launch {
            when (val r = recetas.eliminarPrecio(actual.precio.id)) {
                is Resultado.Listo -> mensaje.value = "Se quitó '${actual.comoSeLlama}'"
                is Resultado.NoSePudo -> mensaje.value = r.motivo
            }
        }
    }

    fun cerrarDialogo() {
        _dialogo.value = DialogoGastos.Ninguno
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun enFormulario(cambio: (DialogoGastos.Formulario) -> DialogoGastos.Formulario) {
        _dialogo.update { if (it is DialogoGastos.Formulario) cambio(it) else it }
    }

    companion object {
        fun fabrica(recetaId: Long, recetas: RecetaRepositorio): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { GastosViewModel(recetaId, recetas) }
            }
    }
}
