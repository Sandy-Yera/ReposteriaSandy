package com.sandyyera.reposteria.ui.ventas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.dao.ResumenDeUnDia
import com.sandyyera.reposteria.data.db.entidades.Venta
import com.sandyyera.reposteria.data.db.entidades.VentaLinea
import com.sandyyera.reposteria.data.repositorio.LineaParaRegistrar
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoRegistrarVenta
import com.sandyyera.reposteria.data.repositorio.VentaRepositorio
import com.sandyyera.reposteria.logica.almacen.VistaPreviaDelDescuento
import com.sandyyera.reposteria.logica.busqueda.filtrarPor
import com.sandyyera.reposteria.logica.calendario.DatosDelDia
import com.sandyyera.reposteria.logica.calendario.datosDelDia
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearMonto
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ingresoBruto
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import com.sandyyera.reposteria.logica.ventas.CifrasDelDia
import com.sandyyera.reposteria.logica.ventas.errorEnPrecioDeVentaTexto
import com.sandyyera.reposteria.logica.ventas.errorEnUnidadesVendidasTexto
import com.sandyyera.reposteria.logica.ventas.loQueDiceElDia
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Un día del informe, listo para dibujarse (18.2).
 *
 * Las tres cosas derivadas se calculan **en el cuerpo y no en un `get()`**: la lista las pide
 * varias veces por fila y por redibujado, y `datosDelDia` resuelve los feriados del año entero.
 * Con
 * getters, desplazar la lista los volvería a calcular en cada cuadro.
 */
data class FilaDeUnDia(val resumen: ResumenDeUnDia) {

    val fecha: Long = resumen.fecha

    /** El día en sí: número, texto, si fue feriado o fecha comercial (18.3). */
    val dia: DatosDelDia = datosDelDia(LocalDate.ofEpochDay(resumen.fecha))

    val cifras: CifrasDelDia = CifrasDelDia(
        ingresoReal = resumen.ingresoReal,
        ingresoEstimado = resumen.ingresoEstimado,
        costoEstimado = resumen.costoEstimado,
        costoReal = resumen.costoReal
    )

    /**
     * Lo que hay que leer de ese día, en frases.
     *
     * Sale de `logica/ventas` y no se arma acá: **la diferencia entre las dos columnas no es un
     * error de la app sino el hallazgo** (18.2), y decidir cómo se dice es justo la parte que se
     * puede probar sin celular.
     */
    val lecturas: List<String> = loQueDiceElDia(cifras)
}

/**
 * Una línea que se está escribiendo en el cuadro de registrar (18.1).
 *
 * [numero] es identidad **local del cuadro** y no de la receta, a propósito: la misma receta puede
 * ir dos veces en un día y no es un error — vender dos tortas a $5.000 y una a $4.000 por un
 * descuento es exactamente el dato que este módulo existe para capturar. Con la receta como clave,
 * la segunda línea pisaría la primera.
 */
data class LineaEnEdicion(
    val numero: Int,
    val recetaId: Long,
    val titulo: String,
    /** Lo que la app dice que se cobraría por uno. Se ofrece a la vista, pero no se rellena. */
    val precioEstimado: Double,
    val tienePrecio: Boolean,
    val unidades: String = "1",
    val precio: String = ""
) {

    val errorDeUnidades: String? get() = errorEnUnidadesVendidasTexto(unidades)

    val errorDePrecio: String? get() = errorEnPrecioDeVentaTexto(precio)

    val sirve: Boolean get() = errorDeUnidades == null && errorDePrecio == null

    /** Lo estimado escrito como se lee, o `null` si esa receta no tiene precio puesto. */
    val comoSeLeeLoEstimado: String? get() =
        if (tienePrecio) formatearMonto(precioEstimado) else null

    val total: Double get() = (textoANumero(unidades) ?: 0.0) * (textoANumero(precio) ?: 0.0)

    val comoSeLeeElTotal: String get() = formatearMonto(total)
}

/** Qué hay abierto encima de la sección de ventas. */
sealed interface DialogoVentas {

    data object Ninguno : DialogoVentas

    /**
     * Anotar lo que se vendió un día (18.1).
     *
     * **El precio empieza vacío y no relleno con el estimado**, aunque el estimado esté ahí al
     * lado. Rellenarlo haría que lo normal fuera aceptarlo, y entonces lo real y lo estimado serían
     * el mismo número por defecto — que es justo lo que deja al módulo sin nada que comparar
     * (18.1). El botón "Cobré lo estimado" existe para que decirlo siga siendo un toque, pero que
     * sea **una afirmación** y no lo que pasa por no tocar nada.
     */
    data class Registrar(
        val fecha: LocalDate,
        val lineas: List<LineaEnEdicion> = emptyList(),
        val notas: String = "",
        val guardando: Boolean = false,
        val rechazo: String? = null
    ) : DialogoVentas {

        val dia: DatosDelDia get() = datosDelDia(fecha)

        val esHoy: Boolean get() = fecha == LocalDate.now()

        /** El siguiente [LineaEnEdicion.numero] libre. No se lleva aparte para no separarlo. */
        val siguienteNumero: Int get() = (lineas.maxOfOrNull { it.numero } ?: 0) + 1

        val total: Double get() = lineas.sumOf { it.total }

        val comoSeLeeElTotal: String get() = formatearMonto(total)

        val puedeGuardar: Boolean
            get() = lineas.isNotEmpty() && lineas.all { it.sirve } && !guardando
    }

    /**
     * Elegir qué receta agregar a la venta.
     *
     * Lleva [volverA] con el cuadro de registrar entero, igual que el renombre del almacén: es un
     * paso **dentro** de anotar una venta, y perder las líneas ya escritas por ir a buscar la
     * siguiente sería cobrar un precio absurdo por el desvío.
     *
     * Las recetas se leen **una sola vez** y no observadas (12.2.1): con el cuadro abierto, una
     * receta que aparece movería la lista bajo el dedo.
     */
    data class ElegirReceta(
        val volverA: Registrar,
        val candidatas: List<DatosCalculoReceta> = emptyList(),
        val busqueda: String = "",
        val cargando: Boolean = true
    ) : DialogoVentas {

        val visibles: List<DatosCalculoReceta>
            get() = filtrarPor(candidatas, busqueda) { it.titulo }

        val sinRecetas: Boolean get() = !cargando && candidatas.isEmpty()
    }

    /**
     * Descontar del almacén lo que llevó una venta (18.4).
     *
     * [previa] en `null` es "todavía calculando": el cuadro **no se abre con la cuenta hecha**
     * porque cruzar las recetas contra el almacén toca varias tablas, y abrirlo después dejaría
     * el toque sin respuesta durante ese rato.
     */
    data class Descontar(
        val ventaId: Long,
        val previa: VistaPreviaDelDescuento? = null,
        val aplicando: Boolean = false
    ) : DialogoVentas

    /**
     * La advertencia antes de borrar una venta (6.3).
     *
     * Dice **si esa venta ya había descontado del almacén**, porque eso no se deshace: lo que salió
     * del frasco salió, y devolverlo exigiría saber que nadie lo corrigió a mano entremedio. Un
     * "¿seguro?" que no lo mencione dejaría creer que borrar lo devuelve todo al estado anterior.
     */
    data class ConfirmarBorrado(
        val ventaId: Long,
        val cuantasLineas: Int,
        val yaDesconto: Boolean,
        val borrando: Boolean = false
    ) : DialogoVentas
}

/** Una venta con sus líneas ya juntas, que es como se mira un día. */
data class VentaConSusLineas(val venta: Venta, val lineas: List<VentaLinea>) {

    val id: Long get() = venta.id

    val total: Double get() = lineas.sumOf { it.unidades * it.precioUnitario }

    val comoSeLeeElTotal: String get() = formatearMonto(total)
}

/** Lo que la lista de días necesita para dibujarse. */
data class EstadoVentas(
    val dias: List<FilaDeUnDia> = emptyList(),
    /** Qué día está abierto, como `epochDay`, o `null` si se ve el informe completo. */
    val abierto: Long? = null,
    val cargando: Boolean = true
) {
    /**
     * El día abierto con sus cifras, **o `null` si el informe ya no lo tiene**.
     *
     * Pasa de verdad: borrada la última venta de un día, ese día desaparece del informe pero la
     * pantalla sigue adentro. Por eso el encabezado no cuelga de esto sino de [diaAbierto] — si
     * colgara, borrar la última venta dejaría un detalle sin título.
     */
    val elAbierto: FilaDeUnDia? get() = dias.firstOrNull { it.fecha == abierto }

    /** Qué día se está mirando, exista o no todavía en el informe. */
    val diaAbierto: DatosDelDia? get() = abierto?.let { datosDelDia(LocalDate.ofEpochDay(it)) }

    val listaVacia: Boolean get() = !cargando && dias.isEmpty()
}

/**
 * Las ventas del día abierto.
 *
 * Va en su propio estado y no dentro de [EstadoVentas] por lo mismo que en Empleados: **se observa
 * a otra cosa**. El informe cuelga de la suma por día y esto de las ventas de uno; juntos, abrir un
 * día volvería a dibujar el informe entero.
 */
data class EstadoDelDia(
    val ventas: List<VentaConSusLineas> = emptyList(),
    val cargando: Boolean = true
) {
    val sinVentas: Boolean get() = !cargando && ventas.isEmpty()

    /** Cuántas de las ventas del día todavía no descontaron del almacén. */
    val sinDescontar: Int get() = ventas.count { !it.venta.descontoDelAlmacen }
}

/**
 * La sección Ventas (18).
 *
 * **No calcula nada del informe.** Las cuatro cifras de cada día las suma la base y lo que se
 * concluye de ellas vive en `logica/ventas`, ya probado sin celular; los metadatos del día salen de
 * `logica/calendario`. Acá solo se decide qué se muestra y en qué orden pasan las cosas.
 */
class VentasViewModel(
    private val ventas: VentaRepositorio
) : ViewModel() {

    private val abierto = MutableStateFlow<Long?>(null)
    private val _dialogo = MutableStateFlow<DialogoVentas>(DialogoVentas.Ninguno)
    private val mensaje = MutableStateFlow<String?>(null)

    val dialogo: StateFlow<DialogoVentas> = _dialogo

    val aviso: StateFlow<String?> = mensaje

    val estado: StateFlow<EstadoVentas> = combine(
        ventas.observarResumenPorDia(),
        abierto
    ) { resumenes, cual ->
        EstadoVentas(
            dias = resumenes.map { FilaDeUnDia(it) },
            abierto = cual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoVentas()
    )

    /**
     * Las ventas del día abierto, observadas.
     *
     * `flatMapLatest` sobre el día y no una lectura al abrirlo: descontar del almacén cambia el
     * `descontoDelAlmacen` de una venta que está a la vista, y con una foto el botón seguiría
     * ofreciendo descontar algo que ya se descontó. Es la regla de siempre — *lo que se muestra se
     * observa*.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val delDia: StateFlow<EstadoDelDia> = abierto.flatMapLatest { cual ->
        if (cual == null) {
            flowOf(EstadoDelDia(cargando = false))
        } else {
            combine(
                ventas.observarDelDia(cual),
                ventas.observarLineasDelDia(cual)
            ) { lista, lineas ->
                // Las líneas llegan de un día entero y se agrupan acá: pedirlas por venta serían
                // tantas suscripciones como ventas tenga el día.
                val porVenta = lineas.groupBy { it.ventaId }
                EstadoDelDia(
                    ventas = lista.map { VentaConSusLineas(it, porVenta[it.id].orEmpty()) },
                    cargando = false
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoDelDia()
    )

    fun mensajeMostrado() { mensaje.value = null }

    fun cerrarDialogo() { _dialogo.value = DialogoVentas.Ninguno }

    // --- Abrir y cerrar un día ---

    fun abrirDia(fecha: Long) { abierto.value = fecha }

    fun cerrarDia() { abierto.value = null }

    // --- Anotar una venta (18.1) ---

    /**
     * Abre el cuadro de registrar.
     *
     * Empieza en **el día que se esté mirando** y no siempre en hoy: si se abrió el sábado para
     * revisarlo, lo que se va a anotar es del sábado. Sin un día abierto, hoy.
     */
    fun abrirRegistrar() {
        val cual = abierto.value
        _dialogo.value = DialogoVentas.Registrar(
            fecha = if (cual == null) LocalDate.now() else LocalDate.ofEpochDay(cual)
        )
    }

    /**
     * Mueve el día de la venta que se está anotando.
     *
     * **No deja pasar de hoy.** Una venta con fecha futura no es un dato que exista todavía, y en
     * el informe se ordena arriba de todo, encima de lo que sí pasó.
     */
    fun moverFecha(dias: Long) = enRegistrar { actual ->
        val nueva = actual.fecha.plusDays(dias)
        if (nueva.isAfter(LocalDate.now())) actual else actual.copy(fecha = nueva)
    }

    fun volverAHoy() = enRegistrar { it.copy(fecha = LocalDate.now()) }

    fun cambiarNotas(texto: String) = enRegistrar { it.copy(notas = texto) }

    fun abrirElegirReceta() {
        val actual = _dialogo.value as? DialogoVentas.Registrar ?: return
        _dialogo.value = DialogoVentas.ElegirReceta(volverA = actual)
        viewModelScope.launch {
            val candidatas = ventas.recetasParaVender()
            _dialogo.update { abierto ->
                if (abierto !is DialogoVentas.ElegirReceta) abierto
                else abierto.copy(candidatas = candidatas, cargando = false)
            }
        }
    }

    fun buscarReceta(texto: String) {
        _dialogo.update { actual ->
            if (actual is DialogoVentas.ElegirReceta) actual.copy(busqueda = texto) else actual
        }
    }

    /** Vuelve al cuadro de registrar sin agregar nada, conservando lo ya escrito. */
    fun volverAlRegistro() {
        val actual = _dialogo.value as? DialogoVentas.ElegirReceta ?: return
        _dialogo.value = actual.volverA
    }

    /**
     * Agrega la receta elegida como una línea más.
     *
     * **También las que no tienen precio**, al revés que al asignarle una receta a un empleado:
     * allá sin precio no hay ganancia que repartir y la receta ni se ofrece; acá lo que falta es la
     * estimación y no la venta. Entra con estimado 0 y el cuadro lo dice.
     */
    fun elegirReceta(datos: DatosCalculoReceta) {
        val actual = _dialogo.value as? DialogoVentas.ElegirReceta ?: return
        val registro = actual.volverA
        _dialogo.value = registro.copy(
            lineas = registro.lineas + LineaEnEdicion(
                numero = registro.siguienteNumero,
                recetaId = datos.recetaId,
                titulo = datos.titulo,
                // El guardia no es de adorno: sin ningún precio, `ingresoBruto` lanza.
                precioEstimado = if (datos.tienePrecio) ingresoBruto(datos) else 0.0,
                tienePrecio = datos.tienePrecio
            )
        )
    }

    fun quitarLinea(numero: Int) = enRegistrar { actual ->
        actual.copy(lineas = actual.lineas.filterNot { it.numero == numero })
    }

    fun cambiarUnidades(numero: Int, texto: String) = enLinea(numero) {
        it.copy(unidades = formatearMientrasSeEscribe(texto))
    }

    fun cambiarPrecio(numero: Int, texto: String) = enLinea(numero) {
        it.copy(precio = formatearMientrasSeEscribe(texto))
    }

    /**
     * Copia lo estimado al campo del precio.
     *
     * Es lo que hace que decir "cobré lo de la lista" siga costando un toque, sin que sea lo que
     * pasa por no tocar nada: el número queda escrito porque alguien lo afirmó (18.1).
     */
    fun usarElPrecioEstimado(numero: Int) = enLinea(numero) { linea ->
        if (!linea.tienePrecio) linea
        else linea.copy(precio = formatearMientrasSeEscribe(formatearMonto(linea.precioEstimado)))
    }

    fun guardarVenta() {
        val actual = _dialogo.value as? DialogoVentas.Registrar ?: return
        if (!actual.puedeGuardar) return
        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            val resultado = ventas.registrar(
                fecha = actual.fecha.toEpochDay(),
                lineas = actual.lineas.map {
                    LineaParaRegistrar(
                        recetaId = it.recetaId,
                        // Ya pasaron por `sirve`, así que el número existe; el `?: 0` es la red
                        // que no debería hacer falta y no una segunda validación.
                        unidades = textoANumero(it.unidades)?.toInt() ?: 0,
                        precioUnitario = textoANumero(it.precio) ?: 0.0
                    )
                },
                notas = actual.notas
            )
            when (resultado) {
                is ResultadoRegistrarVenta.Registrada -> {
                    _dialogo.value = DialogoVentas.Ninguno
                    // Se abre el día recién anotado: es lo que uno viene a mirar después de
                    // anotarlo, y dejarlo en el informe obligaría a buscarlo entre los demás.
                    abierto.value = actual.fecha.toEpochDay()
                }
                is ResultadoRegistrarVenta.NoSePudo ->
                    _dialogo.value = actual.copy(guardando = false, rechazo = resultado.motivo)
            }
        }
    }

    // --- Descontar del almacén (18.4) ---

    fun pedirDescuento(fila: VentaConSusLineas) {
        if (fila.venta.descontoDelAlmacen) {
            mensaje.value = "Esta venta ya descontó del almacén"
            return
        }
        _dialogo.value = DialogoVentas.Descontar(ventaId = fila.id)
        viewModelScope.launch {
            val previa = ventas.vistaPreviaDelDescuento(fila.id)
            _dialogo.update { actual ->
                if (actual !is DialogoVentas.Descontar) actual else actual.copy(previa = previa)
            }
        }
    }

    fun confirmarDescuento() {
        val actual = _dialogo.value as? DialogoVentas.Descontar ?: return
        if (actual.aplicando) return
        _dialogo.value = actual.copy(aplicando = true)

        viewModelScope.launch {
            when (val r = ventas.descontarDelAlmacen(actual.ventaId)) {
                is Resultado.Listo ->
                    mensaje.value = "Descontado del almacén. Ahora el costo del día es el real"
                is Resultado.NoSePudo -> mensaje.value = r.motivo
            }
            _dialogo.value = DialogoVentas.Ninguno
        }
    }

    // --- Borrar ---

    fun pedirBorrado(fila: VentaConSusLineas) {
        _dialogo.value = DialogoVentas.ConfirmarBorrado(
            ventaId = fila.id,
            cuantasLineas = fila.lineas.size,
            yaDesconto = fila.venta.descontoDelAlmacen
        )
    }

    fun confirmarBorrado() {
        val actual = _dialogo.value as? DialogoVentas.ConfirmarBorrado ?: return
        if (actual.borrando) return
        _dialogo.value = actual.copy(borrando = true)

        viewModelScope.launch {
            ventas.eliminar(actual.ventaId)
            _dialogo.value = DialogoVentas.Ninguno
            // **El día abierto se deja abierto**, aunque haya sido la última venta y el día
            // desaparezca del informe. Salir solo sería una segunda cosa pasando por un toque que
            // pedía una sola, y ese día sigue siendo un lugar donde anotar: el detalle queda
            // diciendo que no hay nada, que es la verdad.
        }
    }

    // --- Ayudas ---

    /** Aplica un cambio solo si lo abierto es el cuadro de registrar. */
    private fun enRegistrar(cambio: (DialogoVentas.Registrar) -> DialogoVentas.Registrar) {
        _dialogo.update { actual ->
            if (actual is DialogoVentas.Registrar) cambio(actual) else actual
        }
    }

    /** Lo mismo, sobre una línea suelta de ese cuadro. */
    private fun enLinea(numero: Int, cambio: (LineaEnEdicion) -> LineaEnEdicion) = enRegistrar {
        it.copy(
            lineas = it.lineas.map { linea ->
                if (linea.numero == numero) cambio(linea) else linea
            }
        )
    }

    companion object {
        fun fabrica(ventas: VentaRepositorio): ViewModelProvider.Factory = viewModelFactory {
            initializer { VentasViewModel(ventas) }
        }
    }
}
