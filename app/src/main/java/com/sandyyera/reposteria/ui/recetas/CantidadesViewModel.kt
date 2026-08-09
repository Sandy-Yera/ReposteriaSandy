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
import com.sandyyera.reposteria.data.repositorio.ParteTraida
import com.sandyyera.reposteria.data.repositorio.RecetaParaTraer
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoGuardarIngrediente
import com.sandyyera.reposteria.logica.calculadora.UnidadDeCompra
import com.sandyyera.reposteria.logica.calculadora.calcularValorPorGramo
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.partes.EstadoDelVinculo
import com.sandyyera.reposteria.logica.validaciones.revisarIngrediente
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

    /** Si esto se cuenta por unidad: una caja, una cinta (14.5). */
    val esObjeto: Boolean get() = item.unidades != null

    /** El número que se escribió, sea en gramos o en unidades. Es el que se edita. */
    val cuanto: Double get() = item.unidades ?: item.cantidadG

    /** "2 unidades" o "500 g", que es como se lee la línea en la pantalla. */
    val cuantoDice: String
        get() = if (esObjeto) {
            "${formatearNumero(cuanto)} ${if (cuanto == 1.0) "unidad" else "unidades"}"
        } else {
            "${formatearNumero(cuanto)} g"
        }

    /**
     * Si esta línea suma algo al costo de la receta.
     *
     * Es `false` en los objetos, y **eso hay que decirlo en la línea**: entran con 0 gramos por
     * decisión de diseño (14.5), así que su precio no llega al total. Descubrirlo comparando
     * números sería encontrarse con un total que no cuadra y no saber por qué.
     */
    val sumaAlCosto: Boolean get() = !esObjeto
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
        val guardando: Boolean = false,
        /** Lo que contestó el repositorio al intentar guardar. Va junto al campo (8.2). */
        val rechazo: String? = null,
        /**
         * Qué ingredientes ya están puestos en esta sección. Lo rellena el `combine`.
         *
         * Con esto el cuadro **no ofrece** uno que ya está, en vez de dejar elegirlo y
         * rechazarlo al confirmar. Es la misma regla que `titulosDisponibles`: lo que se
         * ofrece y lo que se acepta no pueden discrepar. El repositorio comprueba igual,
         * porque él es el que decide de verdad.
         */
        val yaEnLaSeccion: Set<Long> = emptySet()
    ) : DialogoCantidades {

        val errorCantidad: String?
            get() = errorEnCantidadEnGramosTexto(cantidad).takeIf { tocado }

        /**
         * Por qué no se puede elegir este ingrediente, o `null` si se puede.
         *
         * **Al editar una fila no aplica**: ahí el ingrediente elegido es justamente el que ya
         * está, y marcarlo como ocupado impediría cambiarle los gramos.
         */
        fun motivoNoDisponible(ingrediente: Ingrediente): String? =
            if (editando == null && ingrediente.id in yaEnLaSeccion) "Ya está en esta sección"
            else null

        val puedeGuardar: Boolean
            get() = elegido != null &&
                errorEnCantidadEnGramosTexto(cantidad) == null &&
                !guardando
    }

    /**
     * Crear un ingrediente **con su valor**, sin salir de la receta (7 y 7.2).
     *
     * Antes esto no era un cuadro: tocar "Crear «Azúcar flor»" lo creaba en el acto con valor
     * **0** y avisaba "ponle su precio en Ingredientes". Lo reportó Sandy y tenía razón — el
     * sentido del alta rápida es no salirse, y así había que salirse igual, ahora además con
     * un ingrediente a medio hacer suelto en el catálogo y una receta que mientras tanto
     * costaba de menos sin decirlo.
     *
     * Lleva la calculadora adentro porque es de donde sale el número en la vida real: nadie
     * sabe cuánto vale un gramo, sabe lo que pagó por el paquete. Va **plegada** ([calculando]
     * arranca en `false`) para no pedir dos datos a quien ya tiene el valor a mano; al abrirla,
     * lo que se escribe ahí rellena el campo de arriba con `calcularValorPorGramo` — la misma
     * función que usa la pantalla completa de 7.2, no una copia.
     *
     * [seccionId] y [yaEnLaSeccion] viajan por acá para poder **volver** al cuadro de agregar
     * con el ingrediente recién creado ya elegido, que es donde se estaba.
     */
    data class CrearIngrediente(
        val seccionId: Long,
        val yaEnLaSeccion: Set<Long>,
        val nombre: String,
        val valorPorGramo: String = "",
        val calculando: Boolean = false,
        val precioDelPaquete: String = "",
        val cantidadDelPaquete: String = "",
        val unidad: UnidadDeCompra = UnidadDeCompra.KILO,
        val tocado: Boolean = false,
        val guardando: Boolean = false,
        /** Lo que contestó el repositorio. Va junto al campo del nombre (8.2). */
        val rechazo: String? = null
    ) : DialogoCantidades {

        private val errores get() = revisarIngrediente(nombre, valorPorGramo)

        val errorNombre: String? get() = rechazo ?: errores.nombre.takeIf { tocado }

        val errorValor: String? get() = errores.valorPorGramo.takeIf { tocado }

        /** Lo que da la cuenta del paquete, o `null` si todavía no alcanza. */
        val resultadoDeLaCuenta: Double?
            get() = calcularValorPorGramo(precioDelPaquete, cantidadDelPaquete, unidad)

        val puedeGuardar: Boolean get() = errores.sirve && !guardando
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

    /**
     * Elegir qué receta traer dentro de esta (8.11).
     *
     * [candidatas] llega `null` mientras se consulta y no como lista vacía, la misma distinción
     * que la advertencia de borrado: vacía significa "no tienes otra receta que traer", que es
     * un mensaje distinto de "todavía estoy mirando".
     *
     * [nombreDeLaPrimera] es lo mismo que en [Seccion]: si la receta tiene su única sección con
     * el nombre automático **y algo cargado**, hay que bautizarla antes de que aparezcan otras
     * al lado. Cuando esa sección está vacía viene en `null` y no se pregunta nada — se elimina
     * sola, porque es la que se siembra al crear la receta.
     */
    data class TraerReceta(
        val busqueda: String = "",
        val candidatas: List<RecetaParaTraer>? = null,
        val nombreDeLaPrimera: String? = null,
        val trayendo: Boolean = false,
        val rechazo: String? = null
    ) : DialogoCantidades {

        val errorDeLaPrimera: String?
            get() = nombreDeLaPrimera?.let { errorEnNombreSeccion(it) }

        val puedeTraer: Boolean
            get() = !trayendo && errorDeLaPrimera == null

        /** Ninguna otra receta que traer, ya consultado. Distinto de estar consultando. */
        val noHayNingunaOtra: Boolean get() = candidatas?.isEmpty() == true
    }

    /**
     * El aviso de que la receta original cambió, o de que fue eliminada (8.11.3 y 8.11.4).
     *
     * Es **un cuadro y no tres botones sueltos en la pantalla** porque las tres salidas no son
     * equivalentes: dos son sobre este cambio y la tercera —desvincular— no se deshace. Ponerlas
     * al mismo nivel invitaría a tocar la definitiva sin leer.
     *
     * [confirmandoDesvincular] es el segundo paso de esa tercera salida: se avisa qué se pierde
     * **antes** de confirmar, porque después no hay vuelta atrás (8.11.3).
     */
    data class AvisoDeParte(
        val parte: ParteTraida,
        val trabajando: Boolean = false,
        val confirmandoDesvincular: Boolean = false,
        val confirmandoBorrar: Boolean = false
    ) : DialogoCantidades {
        /** Cualquiera de sus secciones sirve para nombrar al grupo en las acciones. */
        val seccionId: Long get() = parte.seccionIds.first()

        val laOriginalSeBorro: Boolean
            get() = parte.estado == EstadoDelVinculo.ORIGINAL_BORRADA
    }
}

/** Lo que el paso de cantidades necesita para dibujarse. */
data class EstadoCantidades(
    val receta: Receta? = null,
    val secciones: List<SeccionConIngredientes> = emptyList(),
    val costoTotal: Double = 0.0,
    val catalogo: List<Ingrediente> = emptyList(),
    /** Los grupos de secciones traídas de otra receta, con sus avisos (8.11). */
    val partes: List<ParteTraida> = emptyList(),
    val mensaje: String? = null,
    val cargando: Boolean = true
) {

    /**
     * De qué grupo traído es una sección, o `null` si es propia.
     *
     * Se resuelve por el id y no guardando el grupo dentro de [SeccionConIngredientes] para que
     * ese tipo siga siendo lo que es —una sección con sus líneas— y no dependa de una consulta
     * que puede llegar después que las demás.
     */
    fun parteDe(seccionId: Long): ParteTraida? =
        partes.firstOrNull { seccionId in it.seccionIds }

    /**
     * Si esta sección abre el encabezado de su grupo ("Vienen de Bizcocho").
     *
     * Solo la **primera** de cada grupo lo dibuja: las secciones traídas juntas se muestran
     * seguidas (8.11.2), así que repetir el encabezado en cada una diría tres veces lo mismo.
     */
    fun abreElGrupo(seccionId: Long): Boolean =
        parteDe(seccionId)?.seccionIds?.firstOrNull() == seccionId

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
 * **Todo lo que muestra lo observa.** Antes las consultas de secciones e ingredientes eran
 * de una sola vez y se refrescaban con un contador `recargar` que esta misma clase
 * incrementaba al terminar cada operación. Eso funcionaba mientras la receta cabía en una
 * pantalla; con cuatro pasos dejó de funcionar, porque **quien cambia los ingredientes puede
 * ser otro**: reescalar por molde multiplica todas las cantidades desde el paso del molde, y
 * acá no se enteraba nadie — se veían las cantidades de antes hasta que algo disparara una
 * relectura. Es exactamente el bug que ya había pasado con los costos de la lista de recetas,
 * y la regla que salió de ahí vale igual acá: *lo que se muestra se observa; la foto de un
 * momento es para calcular*.
 *
 * El costo total **se relee de la base** en vez de sumarse acá. Podría sumarse en memoria
 * —cada línea sabe su subtotal— pero entonces habría dos verdades sobre el mismo número, y
 * la que manda cuando se calculan precios y sueldos es la de la base. Se pide dentro del
 * `combine`, que ahora se dispara solo cuando cambia cualquiera de las tablas que lo forman.
 */
class CantidadesViewModel(
    private val recetaId: Long,
    private val recetas: RecetaRepositorio,
    private val ingredientes: IngredienteRepositorio
) : ViewModel() {

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
        recetas.observarReceta(recetaId),
        recetas.observarSecciones(recetaId),
        recetas.observarIngredientes(recetaId),
        // El catálogo, el costo y las partes van juntos en un `combine` anidado porque `combine`
        // llega hasta cinco flujos y acá hacen falta siete. No cambia cuándo emite nada.
        combine(
            // Solo los que van en recetas: el almacén también guarda cosas que no entran en
            // ninguna —velas, bolsas— y ofrecerlas acá llenaría el buscador de ruido (14.5).
            ingredientes.observarParaRecetas(),
            recetas.observarCosto(recetaId),
            recetas.observarPartesDe(recetaId)
        ) { c, k, p -> Triple(c, k, p) },
        mensaje
    ) { receta, secciones, items, loDemas, mensajeActual ->
        val (catalogo, costoDeLaReceta, partesTraidas) = loDemas
        val porId = catalogo.associateBy { it.id }

        EstadoCantidades(
            receta = receta,
            secciones = secciones.map { seccion ->
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
            // **Observado y no consultado acá adentro.** Antes era `recetas.costoTotal(...)`,
            // una consulta `suspend` **dentro de la transformación**: cada emisión de
            // cualquiera de los otros flujos —una tecla, un ingrediente, un renombre— se
            // quedaba esperando un viaje más a SQLite antes de poder dibujar. Sigue viniendo
            // de la base y no de sumar las líneas en memoria, que era el punto (una segunda
            // verdad sobre el mismo número), pero ahora Room lo recalcula solo cuando cambia
            // alguna de las tres tablas de las que depende.
            costoTotal = costoDeLaReceta,
            catalogo = catalogo,
            partes = partesTraidas,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoCantidades()
    )

    // --- Ingredientes de la receta ---

    /**
     * Abre el cuadro de agregar un ingrediente a una sección.
     *
     * Lee de una vez qué hay puesto en esa sección, por lo mismo que `abrirElegirMolde` lee
     * el rendimiento: mientras el cuadro está abierto esa lista no cambia —agregar uno lo
     * cierra— así que observarla solo agregaría una fuente que reemite justo mientras se
     * escribe en un campo de texto, que es lo que 12.2.1 pide evitar.
     */
    fun abrirAgregarIngrediente(seccionId: Long) {
        _dialogo.value = DialogoCantidades.PonerIngrediente(seccionId = seccionId)
        viewModelScope.launch {
            val puestos = recetas.obtenerIngredientesDeSeccion(seccionId)
                .map { it.ingredienteId }
                .toSet()
            enDialogoIngrediente {
                // Se comprueba la sección porque entre abrir y responder la consulta pudo
                // abrirse otro cuadro, y marcar como ocupados los de la sección equivocada
                // escondería ingredientes que sí se pueden poner.
                if (it.seccionId == seccionId) it.copy(yaEnLaSeccion = puestos) else it
            }
        }
    }

    fun abrirCambiarCantidad(linea: LineaDeIngrediente) {
        _dialogo.value = DialogoCantidades.PonerIngrediente(
            seccionId = linea.item.seccionId,
            editando = linea.item,
            elegido = linea.ingrediente,
            // Se muestra con el formato de la app, que es el mismo que `textoANumero`
            // sabe leer de vuelta al guardar. En un objeto lo que se edita son sus unidades,
            // no los gramos —que son 0 a propósito—, y de eso se encarga `cuanto`.
            cantidad = formatearNumero(linea.cuanto),
            tocado = true
        )
    }

    fun buscarIngrediente(texto: String) = enDialogoIngrediente { it.copy(busqueda = texto) }

    // El rechazo era sobre el ingrediente anterior: elegir otro lo deja sin sentido.
    fun elegirIngrediente(ingrediente: Ingrediente) =
        enDialogoIngrediente { it.copy(elegido = ingrediente, rechazo = null) }

    fun cambiarCantidadEscrita(texto: String) = enDialogoIngrediente {
        it.copy(cantidad = formatearMientrasSeEscribe(texto), tocado = true)
    }

    /**
     * Abre el cuadro de crear un ingrediente sin salir de la receta (7).
     *
     * Es el momento que el `ComboBuscable` existe para resolver: darse cuenta a mitad de
     * carga de que falta un ingrediente y no tener que abandonar lo escrito para crearlo.
     *
     * **Antes creaba en el acto con valor 0** y mandaba a ponerle el precio en Ingredientes,
     * que es exactamente salirse — lo mismo que el alta rápida existe para evitar. Ahora
     * pregunta el valor acá, con la calculadora a mano.
     */
    fun crearIngredienteRapido(nombre: String) {
        val actual = _dialogo.value as? DialogoCantidades.PonerIngrediente ?: return
        _dialogo.value = DialogoCantidades.CrearIngrediente(
            seccionId = actual.seccionId,
            yaEnLaSeccion = actual.yaEnLaSeccion,
            nombre = nombre.trim()
        )
    }

    // Al escribir el nombre, el rechazo anterior deja de aplicar: era sobre el de antes.
    fun cambiarNombreDelIngredienteNuevo(texto: String) = enCrearIngrediente {
        it.copy(nombre = texto, tocado = true, rechazo = null)
    }

    fun cambiarValorDelIngredienteNuevo(texto: String) = enCrearIngrediente {
        it.copy(valorPorGramo = formatearMientrasSeEscribe(texto), tocado = true)
    }

    /**
     * Abre o cierra la calculadora del paquete.
     *
     * Al cerrarla **no se borra lo escrito**, por lo mismo que `elegirForma` no borra las
     * medidas de las otras formas: quien la abrió para comprobar una cuenta y la cierra no
     * está diciendo que se equivocó. Y el valor ya calculado se queda arriba, que es el
     * único dato que se guarda.
     */
    fun alternarCalculadoraDelIngrediente() = enCrearIngrediente {
        it.copy(calculando = !it.calculando)
    }

    fun cambiarPrecioDelPaquete(texto: String) = enCrearIngrediente {
        conLaCuentaRehecha(it.copy(precioDelPaquete = formatearMientrasSeEscribe(texto)))
    }

    fun cambiarCantidadDelPaquete(texto: String) = enCrearIngrediente {
        conLaCuentaRehecha(it.copy(cantidadDelPaquete = formatearMientrasSeEscribe(texto)))
    }

    fun cambiarUnidadDelPaquete(unidad: UnidadDeCompra) = enCrearIngrediente {
        conLaCuentaRehecha(it.copy(unidad = unidad))
    }

    /**
     * Baja el resultado de la cuenta al campo del valor, si ya se puede calcular.
     *
     * Se escribe en el campo de arriba en vez de guardarse aparte para que **haya un solo
     * valor**: el que se ve es el que se guarda, y se puede corregir a mano encima. Mientras
     * la cuenta no alcance no se toca nada, porque borrar lo que alguien escribió a mano por
     * empezar a teclear un precio sería peor que no ayudar.
     */
    private fun conLaCuentaRehecha(
        estado: DialogoCantidades.CrearIngrediente
    ): DialogoCantidades.CrearIngrediente {
        val calculado = estado.resultadoDeLaCuenta ?: return estado
        return estado.copy(valorPorGramo = formatearNumero(calculado), tocado = true)
    }

    fun guardarIngredienteNuevo() {
        val actual = _dialogo.value as? DialogoCantidades.CrearIngrediente ?: return
        if (!actual.puedeGuardar) return
        val valor = textoANumero(actual.valorPorGramo) ?: return

        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            // Se vuelve al cuadro de agregar con el ingrediente ya elegido, que es donde se
            // estaba: crear uno es un desvío, no un destino.
            fun volverCon(elegido: Ingrediente?) {
                _dialogo.value = DialogoCantidades.PonerIngrediente(
                    seccionId = actual.seccionId,
                    elegido = elegido,
                    yaEnLaSeccion = actual.yaEnLaSeccion
                )
            }

            when (val resultado = ingredientes.crear(actual.nombre.trim(), valor)) {
                is ResultadoGuardarIngrediente.Guardado -> {
                    volverCon(ingredientes.obtener(resultado.id))
                    mensaje.value = "Se creó '${actual.nombre.trim()}'"
                }
                // Ya existía: se elige el que hay en vez de crear un repetido, y se dice —
                // callarlo dejaría pensando que el valor escrito acá se guardó en algún lado.
                is ResultadoGuardarIngrediente.YaExiste -> {
                    volverCon(resultado.existente)
                    mensaje.value = "'${resultado.existente.nombre}' ya existía y se eligió ese"
                }
                is ResultadoGuardarIngrediente.NoValido ->
                    enCrearIngrediente { it.copy(guardando = false, rechazo = resultado.motivo) }
            }
        }
    }

    fun guardarIngrediente() {
        val actual = _dialogo.value as? DialogoCantidades.PonerIngrediente ?: return
        if (!actual.puedeGuardar) return
        val elegido = actual.elegido ?: return
        val escrito = textoANumero(actual.cantidad) ?: return

        // Un objeto se guarda con **0 gramos y sus unidades aparte** (14.5): así la línea puede
        // decir "2 cajas" sin que el motor de cálculo —que de punta a punta parte de gramos—
        // tenga que aprender otra unidad. El precio del objeto no llega al costo, y la pantalla
        // lo dice en la línea en vez de dejar que se descubra comparando totales.
        val gramos = if (elegido.esObjeto) 0.0 else escrito
        val unidades = escrito.takeIf { elegido.esObjeto }

        _dialogo.value = actual.copy(guardando = true)

        viewModelScope.launch {
            val enEdicion = actual.editando
            if (enEdicion == null) {
                when (val r = recetas.agregarIngrediente(
                    seccionId = actual.seccionId,
                    ingredienteId = elegido.id,
                    cantidadG = gramos,
                    unidades = unidades
                )) {
                    is Resultado.Listo -> _dialogo.value = DialogoCantidades.Ninguno
                    // El rechazo se queda **dentro del cuadro**, como el de las secciones: es
                    // sobre el ingrediente que se acaba de elegir, y con el teclado abierto un
                    // aviso en la franja de abajo no se ve (8.2).
                    is Resultado.NoSePudo -> _dialogo.value =
                        actual.copy(guardando = false, rechazo = r.motivo)
                }
            } else {
                recetas.cambiarCantidad(enEdicion.id, gramos, unidades)
                _dialogo.value = DialogoCantidades.Ninguno
            }
        }
    }

    fun quitarIngrediente(linea: LineaDeIngrediente) {
        viewModelScope.launch {
            recetas.quitarIngrediente(linea.item.id)
            mensaje.value = "Se quitó '${linea.ingrediente.nombre}'"
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
        }
    }

    // --- Partes: traer otra receta dentro de esta (8.11) ---

    /**
     * Abre el cuadro de traer una receta.
     *
     * Consulta dos cosas y las dos por el mismo motivo que [abrirAgregarSeccion]: dependen de lo
     * que hay en la base y no de lo que se esté viendo. La lista llega después porque cruza tres
     * consultas, así que el cuadro se abre vacío y se rellena — con `candidatas = null` mientras
     * tanto, para poder decir "buscando" en vez de "no tienes ninguna otra receta".
     */
    fun abrirTraerReceta() {
        _dialogo.value = DialogoCantidades.TraerReceta()
        viewModelScope.launch {
            val candidatas = recetas.recetasParaTraer(recetaId)
            val bautizo = recetas.nombreQueFaltaBautizar(recetaId)
            enDialogoTraer { it.copy(candidatas = candidatas, nombreDeLaPrimera = bautizo) }
        }
    }

    fun buscarRecetaParaTraer(texto: String) = enDialogoTraer { it.copy(busqueda = texto) }

    fun cambiarNombreDeLaPrimeraAlTraer(texto: String) = enDialogoTraer {
        it.copy(nombreDeLaPrimera = texto, rechazo = null)
    }

    fun traerReceta(origenId: Long) {
        val actual = _dialogo.value as? DialogoCantidades.TraerReceta ?: return
        if (!actual.puedeTraer) return

        _dialogo.value = actual.copy(trayendo = true)

        viewModelScope.launch {
            val elegida = actual.candidatas?.firstOrNull { it.receta.id == origenId }
            when (
                val resultado = recetas.traerReceta(
                    destinoId = recetaId,
                    origenId = origenId,
                    nombreDeLaPrimera = actual.nombreDeLaPrimera
                )
            ) {
                is Resultado.Listo -> {
                    _dialogo.value = DialogoCantidades.Ninguno
                    mensaje.value = "Se trajo '${elegida?.receta?.titulo ?: "la receta"}'"
                }
                // Dentro del cuadro: es sobre lo que se acaba de elegir, y el bautizo que
                // pudiera faltar se corrige ahí mismo sin volver a abrirlo (8.2).
                is Resultado.NoSePudo ->
                    enDialogoTraer { it.copy(trayendo = false, rechazo = resultado.motivo) }
            }
        }
    }

    /** Abre el aviso de una sección traída. La pantalla lo llama al tocar el símbolo. */
    fun abrirAvisoDeParte(seccionId: Long) {
        val parte = estado.value.parteDe(seccionId) ?: return
        _dialogo.value = DialogoCantidades.AvisoDeParte(parte)
    }

    /** Pide confirmar antes de desvincular, porque eso no se deshace (8.11.3). */
    fun pedirDesvincularParte() = enAvisoDeParte { it.copy(confirmandoDesvincular = true) }

    /** Pide confirmar antes de borrar la parte con sus pasos (8.11.4). */
    fun pedirBorrarParte() = enAvisoDeParte { it.copy(confirmandoBorrar = true) }

    fun volverDelAviso() = enAvisoDeParte {
        it.copy(confirmandoDesvincular = false, confirmandoBorrar = false)
    }

    /**
     * "Mantener": deja todo como está y apaga **este** aviso.
     *
     * Con la original viva se vuelve a tomar la foto, así el próximo cambio pregunta de nuevo
     * (8.11.3). Con la original **borrada** no hay foto que tomar ni nada que seguir mirando, y
     * mantener es literalmente desvincular (8.11.4) — dejar el vínculo puesto sería guardar un
     * aviso que ya no se puede apagar nunca.
     */
    fun mantenerParte() {
        val aviso = _dialogo.value as? DialogoCantidades.AvisoDeParte ?: return
        conLaParte(aviso) {
            if (aviso.laOriginalSeBorro) recetas.desvincularParte(aviso.seccionId)
            else recetas.mantenerParte(aviso.seccionId)
        }
    }

    fun actualizarParte() {
        val aviso = _dialogo.value as? DialogoCantidades.AvisoDeParte ?: return
        conLaParte(aviso, exito = "Se actualizó con lo nuevo de la receta original") {
            recetas.actualizarParte(aviso.seccionId)
        }
    }

    fun desvincularParte() {
        val aviso = _dialogo.value as? DialogoCantidades.AvisoDeParte ?: return
        conLaParte(aviso, exito = "Esta parte ya no está enlazada") {
            recetas.desvincularParte(aviso.seccionId)
        }
    }

    fun borrarParte() {
        val aviso = _dialogo.value as? DialogoCantidades.AvisoDeParte ?: return
        conLaParte(aviso, exito = "Se borró la parte con sus pasos") {
            recetas.borrarParte(aviso.seccionId)
        }
    }

    /**
     * El envoltorio común de las cuatro salidas del aviso.
     *
     * Las cuatro hacen lo mismo alrededor: marcar que se está trabajando, esperar, y cerrar o
     * mostrar el motivo. Escrito cuatro veces, la que se olvidara de apagar `trabajando` dejaría
     * el cuadro tomado para siempre sin que ninguna prueba lo notara.
     */
    private fun conLaParte(
        aviso: DialogoCantidades.AvisoDeParte,
        exito: String? = null,
        accion: suspend () -> Resultado
    ) {
        if (aviso.trabajando) return
        _dialogo.value = aviso.copy(trabajando = true)

        viewModelScope.launch {
            when (val resultado = accion()) {
                is Resultado.Listo -> {
                    _dialogo.value = DialogoCantidades.Ninguno
                    exito?.let { mensaje.value = it }
                }
                is Resultado.NoSePudo -> {
                    _dialogo.value = DialogoCantidades.Ninguno
                    mensaje.value = resultado.motivo
                }
            }
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

    private fun enCrearIngrediente(
        cambio: (DialogoCantidades.CrearIngrediente) -> DialogoCantidades.CrearIngrediente
    ) {
        _dialogo.update { actual ->
            if (actual is DialogoCantidades.CrearIngrediente) cambio(actual) else actual
        }
    }

    private fun enDialogoTraer(
        cambio: (DialogoCantidades.TraerReceta) -> DialogoCantidades.TraerReceta
    ) {
        _dialogo.update { actual ->
            if (actual is DialogoCantidades.TraerReceta) cambio(actual) else actual
        }
    }

    private fun enAvisoDeParte(
        cambio: (DialogoCantidades.AvisoDeParte) -> DialogoCantidades.AvisoDeParte
    ) {
        _dialogo.update { actual ->
            if (actual is DialogoCantidades.AvisoDeParte) cambio(actual) else actual
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
