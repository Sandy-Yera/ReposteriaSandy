package com.sandyyera.reposteria.ui.almacen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.dao.ArticuloConValor
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.AlmacenRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoAgregarAlAlmacen
import com.sandyyera.reposteria.data.repositorio.QueHacerConElNombre
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoRenombrarEnAlmacen
import com.sandyyera.reposteria.logica.almacen.LoQueSeBusca
import com.sandyyera.reposteria.logica.almacen.MarcaDeAlmacen
import com.sandyyera.reposteria.logica.almacen.RecetaHecha
import com.sandyyera.reposteria.logica.almacen.dejanPasar
import com.sandyyera.reposteria.logica.almacen.loQueSeBusca
import com.sandyyera.reposteria.logica.almacen.RecetaParaElAlmacen
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
     * El panel de filtros, flotando encima de la lista (14.14).
     *
     * **Los filtros salieron de la pantalla y se metieron acá**, y no por gusto: entre los dos
     * botones de arriba, el valor del almacén, el buscador, las cuatro casillas y la chuleta,
     * quedaba una franja para ver lo que uno vino a ver. Lo dijo Sandy: *"para ver lo que tengo
     * en almacén queda muy corto, porque tengo mucho arriba, y si escribo el teclado tapa por
     * completo la zona"*. Filtrar se hace de vez en cuando; mirar la lista, siempre.
     *
     * **No lleva datos.** Lo que el panel dibuja —las casillas marcadas— vive en [EstadoAlmacen],
     * porque también lo necesita la lista para filtrarse. Duplicarlo acá dejaría dos verdades
     * sobre lo mismo, que es justo lo que ya costó caro en la simulación de los empleados.
     */
    data object Filtros : DialogoAlmacen

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
         * El nombre **solo para mostrarlo en el encabezado**, que es donde se toca para
         * cambiarlo (14.10).
         *
         * No es un campo de este cuadro: renombrar tiene el suyo, porque no es una edición
         * cualquiera —puede querer decir renombrar, unir o separar— y meterla como un campo más
         * escondía la decisión más grande debajo de la más chica. Se guarda acá y no se lee de
         * `fila` para que al volver de un renombre exitoso el encabezado diga el nombre nuevo:
         * la `fila` es la foto de antes.
         */
        val nombre: String,
        /**
         * Si se cuenta por unidad en vez de por gramo (14.11).
         *
         * Lo pidió Sandy para lo ya cargado: *"debería poder cambiar gramos a unidad, en almacén,
         * caso que me haya equivocado"*. Es el cambio **más peligroso del cuadro** y no se nota
         * mirándolo — el precio no se mueve pero pasa a significar otra cosa.
         */
        val esObjeto: Boolean,
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

        /**
         * "g" o "unidad", **según lo elegido en el cuadro** y no según lo guardado.
         *
         * Cambiar la unidad y seguir viendo "(g)" en el campo de al lado sería contradecirse en
         * la misma pantalla.
         */
        val unidad: String get() = if (esObjeto) "unidad" else "g"

        /** Cómo queda leído, con su unidad, para mostrarlo antes de confirmar (8.7.1). */
        val comoQuedaria: String?
            get() = resultado?.let { cantidadConUnidad(it, esObjeto) }

        /**
         * El aviso de que el resultado queda bajo cero, o `null`.
         *
         * **No impide guardar, y ese es todo el punto** (14.8). El negativo es el dato: o entró
         * algo que no se anotó, o la receta pide más de lo que de verdad se usa. Antes esta
         * cuenta se recortaba en cero y las dos lecturas se perdían.
         */
        val avisoDelResultado: String?
            get() = resultado?.let { avisoDeCantidadNegativa(it, esObjeto) }

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

        /** Si el cuadro cambia **qué es** la cosa: su unidad o si va en recetas (14.11). */
        val cambiaQueEs: Boolean
            get() = esObjeto != fila.esObjeto || vaEnRecetas != fila.vaEnRecetas

        val puedeGuardar: Boolean get() = resultado != null && !guardando
    }

    /**
     * Cambiarle el nombre, en su propio cuadro (14.10).
     *
     * **Se llega tocando el nombre en el encabezado del otro**, que es el mismo gesto con el que
     * se renombra una receta (8.4.1 #3): el nombre está a la vista, así que tocarlo es lo que
     * uno intenta. Antes era un campo más dentro del cuadro de editar, y ahí quedaba escondida
     * la decisión más grande —renombrar toca todas las recetas— debajo de la más chica.
     *
     * [volverA] es el cuadro de edición al que se regresa, con lo que hubiera escrito sin
     * guardar: renombrar no puede costar perder la cantidad que se estaba corrigiendo.
     */
    data class Renombrar(
        val volverA: CambiarCantidad,
        val nombre: String,
        val rechazo: String? = null,
        val guardando: Boolean = false
    ) : DialogoAlmacen {

        /** El rechazo del repositorio manda sobre el del formato: es el más específico (8.2). */
        val error: String? get() = rechazo ?: errorEnNombreEscrito(nombre)

        val puedeGuardar: Boolean
            get() = error == null && nombre.trim() != volverA.nombre.trim() && !guardando
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
     * La advertencia antes de cambiar **qué es** algo que las recetas usan (14.11).
     *
     * Es **un solo cuadro para los dos cambios** —la unidad y si va en recetas— y no dos: los
     * dos tocan las mismas recetas, así que preguntar por separado sería pedir la misma
     * autorización partida en dos.
     *
     * Lleva **las recetas afectadas por nombre** y no un "¿seguro?": es la regla de 7.1, y un
     * aviso sin la lista es un botón que se aprieta sin leer.
     *
     * [cambios] son las frases de lo que va a pasar, armadas por el ViewModel: la pantalla no
     * tiene que volver a comparar contra la fila para saber qué decir.
     */
    data class ConfirmarCambiosEnRecetas(
        val volverA: CambiarCantidad,
        val recetasAfectadas: List<String>,
        val cambios: List<String>,
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
            get() = filtrarPor(recetas, busqueda) { it.titulo }

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

    /**
     * La advertencia antes de sacar algo del almacén (6.3).
     *
     * [tambienDelCatalogo] es lo que Sandy pidió: una casilla que se marca **antes** de apretar
     * borrar. Marcarla no borra el ingrediente acá — borra la fila del almacén de inmediato y
     * **abre la segunda advertencia**, la de ingredientes, con las recetas afectadas a la vista.
     * Son dos borrados con consecuencias muy distintas, y una sola confirmación para los dos
     * sería pedir permiso para lo chico y aprovechar para lo grande.
     */
    data class ConfirmarBorrado(
        val fila: FilaDeAlmacen,
        val tambienDelCatalogo: Boolean = false,
        val borrando: Boolean = false
    ) : DialogoAlmacen {

        /**
         * Si la casilla tiene sentido para esta fila.
         *
         * Una fila vieja sin ingrediente (ver la migración 7 → 8) no tiene nada que borrar del
         * catálogo, y ofrecer una casilla que no hace nada es peor que no ofrecerla.
         */
        val sePuedeSacarDelCatalogo: Boolean get() = fila.ingredienteId != null
    }

    /**
     * La segunda advertencia: sacarlo también del catálogo de ingredientes (7.1).
     *
     * Llega **después** de haber sacado la fila del almacén, y por eso el cuadro lo dice: si se
     * cancela acá, lo del almacén ya se fue igual. Es lo que Sandy describió, y es lo honesto —
     * la primera confirmación ya se dio.
     *
     * [recetasAfectadas] arranca en `null` mientras se consulta, con la misma distinción de
     * siempre: `null` es "todavía no sé" y lista vacía es "no lo usa ninguna receta".
     */
    data class ConfirmarBorradoDelCatalogo(
        val ingredienteId: Long,
        val nombre: String,
        val recetasAfectadas: List<Receta>? = null,
        val borrando: Boolean = false
    ) : DialogoAlmacen {
        val sePuedeBorrar: Boolean get() = recetasAfectadas != null && !borrando
    }
}

/** Lo que la pantalla del almacén necesita para dibujarse. */
data class EstadoAlmacen(
    val visibles: List<FilaDeAlmacen> = emptyList(),
    val hayArticulos: Boolean = false,
    val busqueda: String = "",
    /**
     * Qué se entendió de lo escrito en el buscador (14.14).
     *
     * Va en el estado **ya resuelto** y no como texto crudo para que la pantalla no vuelva a
     * interpretarlo: el filtro se lee una vez, y lo que se muestra abajo del campo —"mostrando
     * 300 o más"— sale exactamente de lo que se usó para filtrar. Si se leyera dos veces, un día
     * dirían cosas distintas y el que estaría mal sería el que no se ve.
     */
    val loQueSeBusca: LoQueSeBusca = LoQueSeBusca.PorNombre(""),
    /** Las casillas de categoría marcadas. Vacío es "no filtra por eso". */
    val marcas: Set<MarcaDeAlmacen> = emptySet(),
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    val almacenVacio: Boolean get() = !cargando && !hayArticulos
    val busquedaSinResultados: Boolean get() = hayArticulos && visibles.isEmpty()

    /** Si hay algo filtrando ahora mismo. Lo mira el mensaje de "no encontré nada". */
    val hayFiltrosPuestos: Boolean get() = busqueda.isNotBlank() || marcas.isNotEmpty()

    /** El aviso del filtro mal escrito, o `null`. Va junto al campo, no en la franja (8.2). */
    val errorDelFiltro: String?
        get() = (loQueSeBusca as? LoQueSeBusca.MalEscrito)?.motivo

    /**
     * "Mostrando 300 o más", o `null` si no se está filtrando por cantidad.
     *
     * Existe por 8.7.1: una lista recortada por una regla tiene que decir de qué está hecha. Sin
     * esto, escribir `=>300` y ver tres filas obliga a confiar en que se entendió bien — y en un
     * filtro donde `=>` y `>=` son la misma cosa, esa confianza no está ganada.
     */
    val comoSeEntendioElFiltro: String?
        get() = (loQueSeBusca as? LoQueSeBusca.PorCantidad)?.let {
            "Mostrando lo que tiene ${it.filtro.comoSeLee}"
        }

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
    private val marcas = MutableStateFlow<Set<MarcaDeAlmacen>>(emptySet())
    private val _dialogo = MutableStateFlow<DialogoAlmacen>(DialogoAlmacen.Ninguno)
    private val mensaje = MutableStateFlow<String?>(null)

    val dialogo: StateFlow<DialogoAlmacen> = _dialogo

    /** Lo del buscador y lo de las casillas, juntos: `combine` acepta cinco flujos. */
    private val comoSeFiltra = combine(busqueda, marcas) { texto, marcadas -> texto to marcadas }

    val estado: StateFlow<EstadoAlmacen> = combine(
        almacen.observarTodo(),
        comoSeFiltra,
        mensaje
    ) { articulos, (textoBuscado, marcadas), mensajeActual ->
        val filas = articulos.map { FilaDeAlmacen(it) }
        // El texto se lee **una sola vez** y de acá sale tanto lo que se filtra como lo que la
        // pantalla dice haber entendido. Leerlo dos veces dejaría abierta la puerta a que un día
        // no coincidan, y el que estaría mal sería justo el que no se ve.
        val buscado = loQueSeBusca(textoBuscado)
        // Las casillas se aplican **antes** que el texto, y da igual el orden para el resultado;
        // se hace así porque es más barato: descarta filas antes de comparar nombres letra a letra.
        val porCategoria = filas.filter { dejanPasar(marcadas, it.esObjeto, it.vaEnRecetas) }
        EstadoAlmacen(
            visibles = when (buscado) {
                is LoQueSeBusca.PorNombre -> filtrarPor(porCategoria, buscado.texto) { it.nombre }
                is LoQueSeBusca.PorCantidad ->
                    porCategoria.filter { buscado.filtro.deja(it.cantidad) }
                // Un filtro a medio escribir **no vacía la lista**: mientras se teclea `>=300`,
                // el `>` solo es un paso obligatorio del camino, y ver la lista desaparecer en
                // cada tecla se lee como que no hay nada. El aviso ya dice qué falta.
                is LoQueSeBusca.MalEscrito -> porCategoria
            },
            hayArticulos = filas.isNotEmpty(),
            busqueda = textoBuscado,
            loQueSeBusca = buscado,
            marcas = marcadas,
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

    /** Marca o desmarca una casilla de categoría (14.14). */
    fun cambiarMarca(marca: MarcaDeAlmacen) {
        marcas.value = if (marca in marcas.value) marcas.value - marca else marcas.value + marca
    }

    /** Abre el panel de filtros (14.14). Va por el canal de los cuadros, como los demás. */
    fun abrirFiltros() {
        _dialogo.value = DialogoAlmacen.Filtros
    }

    /** Deja el almacén sin ningún filtro puesto: el texto y las casillas de una vez. */
    fun limpiarFiltros() {
        busqueda.value = ""
        marcas.value = emptySet()
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
            esObjeto = fila.esObjeto,
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

    /**
     * Marca o desmarca "se cuenta por unidad" (14.11).
     *
     * **Solo cambia lo que se ve; no escribe.** Es el cambio más peligroso del cuadro —el precio
     * no se mueve pero pasa a significar otra cosa— así que pasa por la advertencia al guardar,
     * igual que el otro interruptor.
     */
    fun cambiarEsObjetoEnEdicion(valor: Boolean) = enEdicion { it.copy(esObjeto = valor) }

    // --- Renombrar, en su propio cuadro (14.10) ---

    /** Se llega tocando el nombre en el encabezado, como el título de una receta (8.4.1 #3). */
    fun abrirRenombre() {
        val actual = _dialogo.value as? DialogoAlmacen.CambiarCantidad ?: return
        _dialogo.value = DialogoAlmacen.Renombrar(volverA = actual, nombre = actual.nombre)
    }

    fun cambiarNombreDelRenombre(texto: String) {
        _dialogo.update { actual ->
            // El rechazo se limpia al escribir: es sobre lo que estaba, no sobre lo que se
            // está escribiendo ahora.
            if (actual is DialogoAlmacen.Renombrar) actual.copy(nombre = texto, rechazo = null)
            else actual
        }
    }

    /** Cierra el renombre y **vuelve al cuadro de editar**, sin perder lo que hubiera escrito. */
    fun cancelarRenombre() {
        val actual = _dialogo.value as? DialogoAlmacen.Renombrar ?: return
        _dialogo.value = actual.volverA
    }

    fun guardarRenombre() {
        val actual = _dialogo.value as? DialogoAlmacen.Renombrar ?: return
        if (!actual.puedeGuardar) return
        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch { intentarRenombrar(actual.volverA, actual.nombre, null) }
    }

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
            // notas ya quedaron guardadas, así que si los interruptores obligan a preguntar, lo
            // que se pregunta es solo eso y no se pierde el resto de la edición.
            if (pedirConfirmacionDeRecetas(actual)) return@launch

            terminarEdicion(actual)
        }
    }

    /**
     * Intenta el renombre y deja el cuadro que corresponda. Devuelve `true` si hay que parar.
     *
     * Es `suspend` y no lanza su propia corrutina porque **va encadenado con el resto**: lanzando
     * una aparte, el cuadro se cerraría mientras la pregunta viaja y la respuesta llegaría a una
     * pantalla que ya se fue.
     */
    private suspend fun intentarRenombrar(
        volverA: DialogoAlmacen.CambiarCantidad,
        nombreNuevo: String,
        confirmado: QueHacerConElNombre?
    ): Boolean {
        return when (val r = almacen.renombrar(volverA.fila.id, nombreNuevo, confirmado)) {
            is ResultadoRenombrarEnAlmacen.Listo -> {
                mensaje.value = r.comoQuedo
                // Vuelve al cuadro de editar **con el nombre nuevo en el encabezado**: la `fila`
                // es la foto de antes y todavía dice el viejo.
                _dialogo.value = volverA.copy(nombre = nombreNuevo.trim())
                false
            }

            is ResultadoRenombrarEnAlmacen.NoSePudo -> {
                // El aviso vuelve **junto al campo** y no a la franja de abajo, que con el
                // teclado abierto queda tapada (8.2).
                _dialogo.value = DialogoAlmacen.Renombrar(
                    volverA = volverA,
                    nombre = nombreNuevo,
                    rechazo = r.motivo
                )
                true
            }

            is ResultadoRenombrarEnAlmacen.HayQueElegir -> {
                _dialogo.value = DialogoAlmacen.ElegirQueHacerConElNombre(
                    volverA = volverA,
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
            intentarRenombrar(cuadro.volverA, cuadro.nombreNuevo, confirmado = que)
        }
    }

    /**
     * Si cambiar **qué es** la cosa necesita confirmarse, abre el aviso y devuelve `true`.
     *
     * Los dos cambios se preguntan **juntos** porque tocan las mismas recetas: partirlo en dos
     * cuadros sería pedir la misma autorización dos veces.
     *
     * **Sin recetas que lo usen no se pregunta.** El aviso existe para nombrar lo que se rompe
     * (7.1); sin nada que nombrar sería un "¿seguro?" que se aprieta sin leer y que enseña a
     * apretar los siguientes igual.
     */
    private suspend fun pedirConfirmacionDeRecetas(
        actual: DialogoAlmacen.CambiarCantidad
    ): Boolean {
        val ingredienteId = actual.fila.ingredienteId ?: return false
        if (!actual.cambiaQueEs) return false

        val afectadas = almacen.recetasQueUsan(ingredienteId)
        if (afectadas.isEmpty()) return false

        val cambios = buildList {
            if (actual.esObjeto != actual.fila.esObjeto) {
                add(
                    if (actual.esObjeto) {
                        "Pasará a contarse por unidad. El precio guardado no se mueve, pero " +
                            "pasa a ser por unidad: lo que ya está puesto en esas recetas " +
                            "conserva su número y hay que revisarlo."
                    } else {
                        "Pasará a pesarse en gramos. El precio guardado no se mueve, pero pasa " +
                            "a ser por gramo: lo que ya está puesto en esas recetas conserva su " +
                            "número y hay que revisarlo."
                    }
                )
            }
            if (!actual.vaEnRecetas && actual.fila.vaEnRecetas) {
                add("Se sacará de esas recetas, y su costo va a bajar.")
            }
        }
        if (cambios.isEmpty()) return false

        _dialogo.value = DialogoAlmacen.ConfirmarCambiosEnRecetas(
            volverA = actual.copy(guardando = false),
            recetasAfectadas = afectadas.map { it.titulo },
            cambios = cambios
        )
        return true
    }

    /** Contesta el aviso de los cambios que tocan recetas. */
    fun confirmarCambiosEnRecetas() {
        val aviso = _dialogo.value as? DialogoAlmacen.ConfirmarCambiosEnRecetas ?: return
        if (aviso.guardando) return
        _dialogo.value = aviso.copy(guardando = true)

        viewModelScope.launch { terminarEdicion(aviso.volverA) }
    }

    /**
     * Escribe los dos interruptores si cambiaron, y cierra.
     *
     * Es el final común de los dos caminos —guardar directo y guardar después de la advertencia—
     * para que cerrar el cuadro y aplicarlos no queden escritos dos veces con dos criterios.
     */
    private suspend fun terminarEdicion(actual: DialogoAlmacen.CambiarCantidad) {
        val ingredienteId = actual.fila.ingredienteId
        if (ingredienteId != null && actual.cambiaQueEs) {
            val r = almacen.cambiarQueEs(ingredienteId, actual.esObjeto, actual.vaEnRecetas)
            if (r is Resultado.NoSePudo) mensaje.value = r.motivo
        }
        _dialogo.value = DialogoAlmacen.Ninguno
    }

    /**
     * Abre el cuadro de agregar **con los campos ya llenos**, viniendo de una receta (14.13).
     *
     * Es un atajo a una pantalla que ya existe y no una nueva, que es justo lo que Sandy pidió:
     * *"sería mejor que me redirigiera al almacenaje, con los campos rellenos"*.
     *
     * El precio llega como **costo total** porque es lo que este cuadro pregunta (14.5.2) y es lo
     * que la receta costó; la división a valor por gramo la hace el cuadro, igual que siempre.
     * Queda `vaEnRecetas = true` y `esObjeto = false` sin preguntarlo: un almíbar se pesa y se usa
     * dentro de otras recetas — es exactamente para eso que se convierte.
     *
     * **Todo queda editable.** No es un guardado automático sino un formulario contestado: si el
     * almíbar se guarda en dos frascos y solo uno queda en la despensa, la cantidad se corrige
     * antes de aceptar.
     */
    fun abrirAgregarDesdeReceta(datos: RecetaParaElAlmacen) {
        _dialogo.value = DialogoAlmacen.Agregar(
            nombre = datos.nombre,
            esObjeto = false,
            vaEnRecetas = true,
            cantidad = formatearNumero(datos.cantidad),
            precio = formatearNumero(datos.costoTotal),
            // `tocado` en true porque los campos **ya tienen algo escrito**: con false, un error
            // real —un nombre que choca— no se mostraría hasta tocar el formulario.
            tocado = true
        )
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

    /** Marca o desmarca la casilla de "sacarlo también de ingredientes". */
    fun cambiarTambienDelCatalogo(marcado: Boolean) {
        _dialogo.update { actual ->
            if (actual is DialogoAlmacen.ConfirmarBorrado) {
                actual.copy(tambienDelCatalogo = marcado)
            } else {
                actual
            }
        }
    }

    /**
     * Saca la fila del almacén, y si la casilla estaba marcada **abre la segunda advertencia**.
     *
     * El orden es el que pidió Sandy y es el correcto: lo del almacén se hace de inmediato porque
     * ya se confirmó y no le hace nada a ninguna receta. Lo del catálogo espera a su propio aviso,
     * con las recetas afectadas a la vista (7.1), porque eso sí saca el ingrediente de todas las
     * recetas que lo usan.
     */
    fun confirmarBorrado() {
        val aviso = _dialogo.value as? DialogoAlmacen.ConfirmarBorrado ?: return
        if (aviso.borrando) return

        _dialogo.value = aviso.copy(borrando = true)

        viewModelScope.launch {
            almacen.eliminar(aviso.fila.id, aviso.fila.nombre)
            val ingredienteId = aviso.fila.ingredienteId
            if (!aviso.tambienDelCatalogo || ingredienteId == null) {
                _dialogo.value = DialogoAlmacen.Ninguno
                mensaje.value = "Se sacó '${aviso.fila.nombre}' del almacén"
                return@launch
            }

            // El mensaje del primer borrado **no se muestra todavía**: encima va a haber otro
            // cuadro, y una franja que aparece debajo de un diálogo no se lee. Se dice al final,
            // contando las dos cosas que pasaron.
            _dialogo.value = DialogoAlmacen.ConfirmarBorradoDelCatalogo(
                ingredienteId = ingredienteId,
                nombre = aviso.fila.nombre
            )
            val afectadas = almacen.recetasQueUsan(ingredienteId)
            _dialogo.update { actual ->
                if (actual is DialogoAlmacen.ConfirmarBorradoDelCatalogo &&
                    actual.ingredienteId == ingredienteId
                ) {
                    actual.copy(recetasAfectadas = afectadas)
                } else {
                    actual
                }
            }
        }
    }

    /** Cierra la segunda advertencia sin borrar del catálogo. Lo del almacén ya se hizo. */
    fun cancelarBorradoDelCatalogo() {
        val aviso = _dialogo.value as? DialogoAlmacen.ConfirmarBorradoDelCatalogo ?: return
        _dialogo.value = DialogoAlmacen.Ninguno
        mensaje.value = "Se sacó '${aviso.nombre}' del almacén. Sigue en ingredientes"
    }

    /** Borra de verdad del catálogo, ya con la segunda advertencia aceptada (7.1). */
    fun confirmarBorradoDelCatalogo() {
        val aviso = _dialogo.value as? DialogoAlmacen.ConfirmarBorradoDelCatalogo ?: return
        if (!aviso.sePuedeBorrar) return

        _dialogo.value = aviso.copy(borrando = true)

        viewModelScope.launch {
            val r = almacen.eliminarDelCatalogo(aviso.ingredienteId)
            _dialogo.value = DialogoAlmacen.Ninguno
            mensaje.value = when (r) {
                is Resultado.NoSePudo -> r.motivo
                is Resultado.Listo ->
                    "Se sacó '${aviso.nombre}' del almacén y de ingredientes"
            }
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
