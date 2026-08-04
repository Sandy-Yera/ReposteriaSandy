package com.sandyyera.reposteria.ui.recetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.RepartoDeVenta
import com.sandyyera.reposteria.logica.simulacion.SimulacionResultado
import com.sandyyera.reposteria.logica.simulacion.repartoSemanal
import com.sandyyera.reposteria.logica.simulacion.simulacionDeVenta
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
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    private val errores get() = revisarSimulacion(diasPorSemana, unidadesPorDia)

    val errorDias: String? get() = errores.diasPorSemana
    val errorUnidades: String? get() = errores.unidadesPorDia

    /** Si lo escrito sirve para guardarse. Lo consulta el guardado automático. */
    val puedeGuardar: Boolean get() = errores.sirve

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
    private val recetas: RecetaRepositorio
) : ViewModel() {

    private val dias = MutableStateFlow("")
    private val unidades = MutableStateFlow("")
    private val mensaje = MutableStateFlow<String?>(null)

    private var guardadoPendiente: Job? = null

    val estado: StateFlow<EstadoSimulacion> = combine(
        recetas.observarDatosCalculo(recetaId),
        combine(dias, unidades) { d, u -> d to u },
        mensaje
    ) { datos, escrito, mensajeActual ->
        EstadoSimulacion(
            datos = datos,
            diasPorSemana = escrito.first,
            unidadesPorDia = escrito.second,
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
        fun fabrica(recetaId: Long, recetas: RecetaRepositorio): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SimulacionViewModel(recetaId, recetas) }
            }
    }
}
