package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.repositorio.EmpleadoRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.RepartoDeVenta
import com.sandyyera.reposteria.logica.precios.ingresoBruto
import com.sandyyera.reposteria.logica.precios.precioDeReferencia
import com.sandyyera.reposteria.logica.simulacion.LoQueSeLlevanLosEmpleados
import com.sandyyera.reposteria.logica.simulacion.SimulacionResultado
import com.sandyyera.reposteria.logica.simulacion.gananciaDespuesDeLosEmpleados
import com.sandyyera.reposteria.logica.simulacion.loQueDicenLosEmpleados
import com.sandyyera.reposteria.logica.simulacion.loQueSeVendeEnLaSemana
import com.sandyyera.reposteria.logica.simulacion.promocionesQueSeGananAlJuntar
import com.sandyyera.reposteria.logica.simulacion.repartoSemanal
import com.sandyyera.reposteria.logica.simulacion.simulacionDeVenta
import com.sandyyera.reposteria.logica.validaciones.descripcionDePromocion
import com.sandyyera.reposteria.logica.validaciones.nombreDeLaCantidad
import com.sandyyera.reposteria.logica.validaciones.revisarSimulacion
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Cuánto silencio se espera antes de guardar solo, en milisegundos.
 *
 * El mismo medio segundo que el rendimiento, y por el mismo motivo: escribiendo "12" se pasa
 * por "1", y guardar cada tecla escribiría cifras que nadie quiso.
 */
private const val ESPERA_ANTES_DE_GUARDAR_MS = 500L

/** Lo que el paso de ganancias simuladas necesita para dibujarse (8.7). */
data class EstadoSimulacion(
    val datos: DatosCalculoReceta? = null,
    val diasPorSemana: String = "",
    val unidadesPorDia: String = "",
    /**
     * Lo que se llevan **todos** los empleados de esta receta, por producto vendido (10.1).
     *
     * Está acá para que la simulación reste de verdad en vez de avisar que no lo hace. Lo pidió
     * Sandy al revés de como se planteó primero, y tiene razón: un número que hay que corregir
     * de cabeza no es un número, es una tarea pendiente.
     */
    val empleados: LoQueSeLlevanLosEmpleados = LoQueSeLlevanLosEmpleados(0, 0.0),
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    private val errores get() = revisarSimulacion(diasPorSemana, unidadesPorDia)

    val errorDias: String? get() = errores.diasPorSemana
    val errorUnidades: String? get() = errores.unidadesPorDia

    /** Si lo escrito sirve para guardarse. Lo consulta el guardado automático. */
    val puedeGuardar: Boolean get() = errores.sirve

    /**
     * Lo que queda por producto **después** de pagar a los empleados (10.1).
     *
     * `null` cuando todavía no hay cifras que restar. Puede dar **negativo**, y eso es justo lo
     * que hay que ver: con ese precio no alcanza para pagar lo comprometido. Antes eso solo se
     * descubría entrando a Empleados.
     */
    val gananciaLimpiaPorProducto: Double?
        get() = gananciaPorProducto?.let { gananciaDespuesDeLosEmpleados(it, empleados) }

    /**
     * Lo que queda en la semana **después** de pagar a los empleados.
     *
     * Se resta sobre la proyección ya hecha y no se vuelve a proyectar: lo que se llevan es por
     * producto, así que se multiplica por los mismos días y unidades que el resto de la fila.
     * Calcularlo aparte sería una segunda versión de la misma cuenta.
     */
    val gananciaLimpiaSemanal: Double?
        get() = resultado?.let {
            it.gananciaSemanal - empleados.seLlevanPorProducto * (dias ?: 0) * (unidades ?: 0)
        }

    /** Qué decir del precio respecto de los empleados, o `null` si no hay nada que decir. */
    val loQueDicenLosEmpleadosDeLaReceta: String?
        get() = gananciaPorProducto?.let { loQueDicenLosEmpleados(it, empleados) }

    /**
     * Lo que deja **un producto completo**, antes de los empleados.
     *
     * `null` sin precio, y ese guardia no es de adorno: `ingresoBruto` lanza cuando no hay ningún
     * precio, y llamarlo para dibujar fue exactamente lo que cerró la app en Empleados.
     */
    private val gananciaPorProducto: Double?
        get() = datos?.takeIf { it.tienePrecio }?.let { ingresoBruto(it) - it.costoTotal }

    /** Si la receta ya tiene precio: sin él no hay nada que proyectar. */
    val tienePrecio: Boolean get() = datos?.tienePrecio == true

    private val dias: Int? get() = textoANumero(diasPorSemana)?.toInt()?.takeIf { errores.sirve }
    private val unidades: Int? get() = textoANumero(unidadesPorDia)?.toInt()?.takeIf { errores.sirve }

    /**
     * Las seis cifras, o `null` mientras no se puedan calcular.
     *
     * Sin precio guardado no hay nada que proyectar, y con los campos a medio escribir
     * tampoco: mostrar una cifra a partir de un número inválido sería peor que no mostrar
     * nada, porque parecería un resultado.
     */
    val resultado: SimulacionResultado?
        get() {
            val d = datos?.takeIf { it.tienePrecio } ?: return null
            return simulacionDeVenta(d, dias ?: return null, unidades ?: return null)
        }

    /**
     * Cómo se reparte la venta de la semana entre la promoción y lo que sobra (8.6.1).
     *
     * Es lo que hace honesto a este paso: **el resto se junta en la semana**. Una receta de 3
     * trozos con una promo de 2 deja siempre un suelto mirando producto por producto, pero
     * vendiendo dos productos son 6 trozos y la promo entra tres veces justas. Multiplicar el
     * ingreso de un producto habría dado de más — plata que no entra.
     */
    val reparto: RepartoDeVenta?
        get() {
            val d = datos?.takeIf { it.tienePrecio } ?: return null
            return repartoSemanal(d, dias ?: return null, unidades ?: return null)
        }

    /** El aviso de que en la semana quedan trozos sueltos, o `null` si calza justo. */
    val avisoDelResto: String?
        get() {
            val r = reparto ?: return null
            if (!r.huboResto) return null
            val cuantos = if (r.sueltos == 1) "queda 1 suelto" else "quedan ${r.sueltos} sueltos"
            return if (r.faltaElPrecioSuelto) {
                "En la semana $cuantos y todavía no tienen precio individual: lo de abajo " +
                    "cuenta solo las promociones."
            } else {
                "En la semana $cuantos, cobrados al valor individual."
            }
        }

    /**
     * De qué está hecho el "Entra" de la semana: cuánto se vende y cuántas promociones entran.
     *
     * **Es lo que faltaba para que la cifra se pudiera comprobar.** Sandy reportó que la
     * cantidad que entra era incorrecta, y no lo era: la pantalla mostraba $300.000 sin decir
     * de dónde salían, así que no había forma de contrastarlo contra nada. Con esta línea el
     * número se verifica mirando la torta.
     */
    val deQueSeCompone: String?
        get() {
            val d = datos?.takeIf { it.tienePrecio } ?: return null
            val r = reparto ?: return null
            val cuanto = loQueSeVendeEnLaSemana(d, dias ?: return null, unidades ?: return null)
            val enQue = nombreDeLaCantidad(precioDeReferencia(d).modo, cuanto)
            if (r.cuantasVecesEntra == 0) return "En la semana vendes $enQue."
            val promo = descripcionDePromocion(precioDeReferencia(d))
            val veces = if (r.cuantasVecesEntra == 1) "1 vez" else "${r.cuantasVecesEntra} veces"
            return "En la semana vendes $enQue: «$promo» entra $veces."
        }

    /**
     * Por qué la semana no es el ingreso de un producto multiplicado, cuando no lo es (8.6.1).
     *
     * Este es **el aviso que le faltaba a la pantalla** cuando Sandy comparó las dos y no le
     * cuadraron. Con 5 trozos y una promo de 2, un producto deja siempre un trozo suelto que se
     * cobra individual; vendiendo seis, esos seis sueltos se juntan y arman tres promociones
     * más. Las dos cifras están bien y contestan preguntas distintas, pero vistas en dos
     * pantallas sin nada que las una, la segunda parece un error de la app.
     *
     * Se dice en **promociones y no en pesos** porque así se comprueba: "se arman 3 promociones
     * más" se cuenta mirando; "entran $24.000 más" hay que creerlo.
     */
    val porQueNoEsMultiplicar: String?
        get() {
            val d = datos?.takeIf { it.tienePrecio } ?: return null
            val cuantas = promocionesQueSeGananAlJuntar(
                d, dias ?: return null, unidades ?: return null
            )
            if (cuantas == 0) return null
            val cuantasSeDice = if (cuantas == 1) "1 promoción más" else "$cuantas promociones más"
            return "Juntando lo que sobra de cada producto se arman $cuantasSeDice, así que " +
                "entra más que multiplicar lo de una sola."
        }
}

/**
 * El cerebro del paso "Ganancias simuladas" (8.7).
 *
 * **Lo particular de este paso es que nada explota.** Los dos campos se multiplican contra el
 * ingreso y ya: un 200 escrito en vez de un 20 no rompe nada, sale como una proyección mensual
 * perfectamente creíble y diez veces falsa. Por eso las reglas de `revisarSimulacion` no son
 * un adorno, y por eso los días **rechazan el 0** mientras las unidades lo aceptan — "no la
 * vendo" se dice con 0 unidades, no con 0 días, que dejaría toda la proyección en cero
 * pareciendo un error de la app.
 *
 * Guarda solo, como el resto de los pasos (8.4.1), y **observa el snapshot** porque de las
 * cinco cosas que lleva, tres las escriben otros pasos: sin eso, cambiar un precio dejaría
 * esta pantalla proyectando plata que ya no es.
 */
class SimulacionViewModel(
    private val recetaId: Long,
    private val recetas: RecetaRepositorio,
    // Solo para saber cuánto se llevan los empleados de esta receta. La receta no sabe quién la
    // tiene asignada —no es su tabla—, así que el dato lo trae quien sí lo sabe.
    private val empleados: EmpleadoRepositorio
) : ViewModel() {

    private val dias = MutableStateFlow("")
    private val unidades = MutableStateFlow("")
    private val mensaje = MutableStateFlow<String?>(null)

    private var guardadoPendiente: Job? = null

    val estado: StateFlow<EstadoSimulacion> = combine(
        recetas.observarDatosCalculo(recetaId),
        combine(dias, unidades) { d, u -> d to u },
        // Se **observa** y no se pide una vez: asignarle un empleado a esta receta desde la otra
        // sección tiene que mover estos números sin que nadie se acuerde de refrescar.
        empleados.observarLoQueSeLlevanPor(recetaId),
        mensaje
    ) { datos, escrito, loDeLosEmpleados, mensajeActual ->
        EstadoSimulacion(
            datos = datos,
            diasPorSemana = escrito.first,
            unidadesPorDia = escrito.second,
            empleados = loDeLosEmpleados,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoSimulacion()
    )

    init {
        // Los campos siguen a lo guardado, con las dos mismas condiciones que el rendimiento:
        // solo si lo guardado cambió de verdad, y solo si no hay un guardado esperando. Sin
        // eso, la re-siembra pisa lo que se está tecleando.
        var ultimoGuardado: Pair<Int, Int>? = null
        viewModelScope.launch {
            recetas.observarSimulacion(recetaId).collect { fila ->
                val ahora = (fila?.diasPorSemana ?: 1) to (fila?.unidadesPorDia ?: 1)
                val cambio = ultimoGuardado != ahora
                ultimoGuardado = ahora
                if (!cambio || guardadoPendiente?.isActive == true) return@collect
                dias.value = ahora.first.toString()
                unidades.value = ahora.second.toString()
            }
        }
    }

    fun cambiarDias(texto: String) {
        // Solo dígitos: los días de la semana son unidades y no llevan punto de mil.
        dias.value = texto.filter { it.isDigit() }
        programarGuardado()
    }

    fun cambiarUnidades(texto: String) {
        unidades.value = texto.filter { it.isDigit() }
        programarGuardado()
    }

    private fun programarGuardado() {
        guardadoPendiente?.cancel()
        guardadoPendiente = viewModelScope.launch {
            delay(ESPERA_ANTES_DE_GUARDAR_MS)
            guardar()
        }
    }

    /**
     * Guarda si lo escrito sirve.
     *
     * **No anuncia el éxito.** Sin botón que apretar, un "se guardó" por cada número tecleado
     * sería ruido — y este es el paso donde más se teclea, porque la gracia es probar
     * combinaciones.
     */
    private suspend fun guardar() {
        if (!estado.value.puedeGuardar) return
        when (val r = recetas.guardarSimulacion(recetaId, dias.value, unidades.value)) {
            is Resultado.Listo -> Unit
            is Resultado.NoSePudo -> mensaje.value = r.motivo
        }
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    companion object {
        fun fabrica(
            recetaId: Long,
            recetas: RecetaRepositorio,
            empleados: EmpleadoRepositorio
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SimulacionViewModel(recetaId, recetas, empleados) }
        }
    }
}
