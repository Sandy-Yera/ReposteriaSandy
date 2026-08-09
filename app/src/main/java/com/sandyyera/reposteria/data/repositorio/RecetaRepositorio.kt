package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.RecetaDao
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.RecetaDuracion
import com.sandyyera.reposteria.data.db.entidades.RecetaIngrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.db.entidades.RecetaSeccion
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.data.db.entidades.aVigente
import com.sandyyera.reposteria.data.db.entidades.RecetaRendimiento
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.formato.redondearParaGuardar
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import com.sandyyera.reposteria.logica.duracion.UnidadDuracion
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.ModoReescalado
import com.sandyyera.reposteria.logica.moldes.factorEscala
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.busqueda.sonElMismoTexto
import com.sandyyera.reposteria.logica.busqueda.marcarRepetidos
import com.sandyyera.reposteria.data.db.entidades.RecetaPaso
import com.sandyyera.reposteria.data.db.entidades.esTraida
import com.sandyyera.reposteria.logica.partes.EstadoDelVinculo
import com.sandyyera.reposteria.logica.partes.FirmaDeReceta
import com.sandyyera.reposteria.logica.partes.LineaDeFirma
import com.sandyyera.reposteria.logica.partes.MOTIVO_TITULO_REPETIDO
import com.sandyyera.reposteria.logica.partes.MOTIVO_UN_SOLO_NIVEL
import com.sandyyera.reposteria.logica.partes.SeccionDeFirma
import com.sandyyera.reposteria.logica.partes.TituloDeFirma
import com.sandyyera.reposteria.logica.partes.TituloDePaso
import com.sandyyera.reposteria.logica.partes.VinculoConLaOriginal
import com.sandyyera.reposteria.logica.partes.cantidadAdaptada
import com.sandyyera.reposteria.logica.partes.compararFirmas
import com.sandyyera.reposteria.logica.partes.emparejarPorIngrediente
import com.sandyyera.reposteria.logica.partes.nombreSinChocar
import com.sandyyera.reposteria.logica.partes.sePuedeUsarComoParte
import com.sandyyera.reposteria.logica.partes.textoDelVinculo
import com.sandyyera.reposteria.logica.partes.vinculoDesdeTexto
import com.sandyyera.reposteria.logica.partes.errorAlUsarTitulo
import com.sandyyera.reposteria.logica.validaciones.elPasoDiceAlgo
import com.sandyyera.reposteria.logica.validaciones.errorEnTextoDePaso
import com.sandyyera.reposteria.logica.precios.basesQueFaltanEn
import com.sandyyera.reposteria.logica.precios.errorAlElegirReferencia
import com.sandyyera.reposteria.logica.validaciones.NOMBRE_SECCION_POR_DEFECTO
import com.sandyyera.reposteria.logica.validaciones.descripcionDePromocion
import com.sandyyera.reposteria.logica.validaciones.elBloqueDiceAlgo
import com.sandyyera.reposteria.logica.validaciones.errorEnCantidadDeDuracion
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreSeccion
import com.sandyyera.reposteria.logica.validaciones.errorEnNumeroPositivoTexto
import com.sandyyera.reposteria.logica.validaciones.promocionesQueNoCabenEn
import com.sandyyera.reposteria.logica.validaciones.revisarPrecio
import com.sandyyera.reposteria.data.db.entidades.RecetaSimulacionVenta
import com.sandyyera.reposteria.logica.validaciones.revisarSimulacion
import com.sandyyera.reposteria.logica.validaciones.revisarRendimiento
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import com.sandyyera.reposteria.logica.validaciones.esNombreAutomaticoDeSeccion
import com.sandyyera.reposteria.logica.validaciones.errorEnTituloReceta
import com.sandyyera.reposteria.logica.validaciones.nombreSugeridoParaPrimeraSeccion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Cómo terminó una operación que podía no poder hacerse.
 *
 * Es un tipo cerrado y no un `Boolean` para que el motivo viaje junto con el fracaso: la
 * pantalla tiene que poder mostrar *por qué* no se pudo, y un `false` no lo dice.
 */
sealed interface Resultado {
    data object Listo : Resultado

    /** No se hizo nada. [motivo] es el texto a mostrar. */
    data class NoSePudo(val motivo: String) : Resultado
}

/**
 * Una receta ofrecida para traerla como parte de otra, con el motivo si no se puede (8.11.6).
 *
 * Lleva el motivo en vez de quedar fuera de la lista por el mismo criterio que el ingrediente ya
 * puesto en una sección: **desaparecer haría pensar que la app la perdió**, y un gris sin
 * explicación invita a tocarlo y a preguntarse qué pasa. Lo resuelve `ComboBuscable` con su
 * `motivoNoDisponible`.
 */
data class RecetaParaTraer(val receta: Receta, val motivoNoDisponible: String?)

/**
 * Un grupo de secciones traídas de la misma receta, con lo que haya que avisar (8.11.3).
 *
 * **El grupo y no la sección suelta es la unidad**, y sale de 8.11.2: traer una receta trae
 * *todas* sus secciones, "todas quedan marcadas «vienen de Bizcocho», se muestran seguidas y
 * bajo un encabezado que las agrupa". La firma guardada es la de la receta original **entera**,
 * así que el "¿Qué cambió?" de cualquiera de sus secciones diría lo mismo — repetido tantas
 * veces como partes tenga. Mantener, actualizar y desvincular se aplican al grupo por lo mismo:
 * son decisiones sobre la receta que se trajo, no sobre un pedazo de ella.
 *
 * **La excepción son las huérfanas.** Cuando la receta original se borra, SQLite pone
 * `recetaOrigenId` en `null` en todas sus copias a la vez, y ahí ya no hay con qué distinguir
 * las que venían de una receta de las que venían de otra. Cada una queda como su propio grupo, y
 * es además lo que 8.11.4 pide en singular: *"Borrar: se elimina la sección y también sus pasos"*.
 */
data class ParteTraida(
    /** La receta de la que salieron. `null` si fue eliminada (8.11.4). */
    val origenId: Long?,
    /** Cómo se llama esa receta hoy. `null` si fue eliminada. */
    val tituloDelOrigen: String?,
    val seccionIds: List<Long>,
    val estado: EstadoDelVinculo,
    /** Las frases de "¿Qué cambió?" (8.11.5). Vacía si no cambió nada que esto mire. */
    val cambios: List<String>
) {
    /**
     * Si hay que dibujar el símbolo de advertencia sobre estas secciones.
     *
     * Una original borrada **siempre** avisa aunque no haya ningún cambio que contar: lo que se
     * está diciendo es que hay una decisión pendiente, no que algo se movió.
     */
    val hayQueAvisar: Boolean
        get() = estado == EstadoDelVinculo.ORIGINAL_BORRADA || cambios.isNotEmpty()
}

/**
 * Todo lo que se hace con una receta y sus partes.
 *
 * Cada función que puede fallar **revisa antes de escribir**. Eso hace que "cancelar la
 * acción y dejar todo como estaba" no necesite deshacer nada: simplemente no se escribió.
 */
class RecetaRepositorio(
    private val dao: RecetaDao,
    private val historial: HistorialRepositorio
) {

    // --- La receta ---

    fun observarTodas(): Flow<List<Receta>> = dao.observarTodas()

    /**
     * Una receta, avisando cuando cambia. **La que hay que usar para mostrarla.**
     *
     * Sigue la regla que dejó `observarCostos`: *lo que se muestra se observa; la foto de un
     * momento es para calcular*. Los cuatro pasos de una receta muestran su título en el
     * encabezado y se renombra desde adentro (8.4.1, #3), así que sin esto los otros tres se
     * quedan con el nombre viejo hasta que algo los haga releer.
     */
    fun observarReceta(recetaId: Long): Flow<Receta?> = dao.observarReceta(recetaId)

    suspend fun obtener(recetaId: Long): Receta? = dao.obtener(recetaId)

    /**
     * Busca una receta que se llame igual que [titulo], ignorando tildes y mayúsculas.
     *
     * [exceptoId] sirve al renombrar, para que una receta no choque consigo misma.
     *
     * Se compara en memoria y no con una consulta porque SQLite no sabe ignorar tildes:
     * para la base "limon" y "limón" son títulos distintos. Es el mismo motivo por el que
     * `IngredienteRepositorio.buscarParecido` hace lo mismo.
     *
     * **A diferencia de ingredientes, la tabla no lleva índice único.** Es deliberado: al
     * poner esta regla ya había recetas repetidas guardadas, y un índice único habría
     * obligado a renombrarlas o borrarlas durante la migración — datos reales cambiando
     * sin que nadie lo pida. En vez de eso conviven, y `marcarRepetidos` las señala para
     * que la pantalla no deje seguir trabajando sobre ellas.
     */
    suspend fun buscarParecida(titulo: String, exceptoId: Long? = null): Receta? =
        dao.observarTodas().first()
            .firstOrNull { it.id != exceptoId && sonElMismoTexto(it.titulo, titulo) }

    /**
     * Crea una receta con todo lo que necesita para existir sin huecos.
     *
     * La primera sección se llama [NOMBRE_SECCION_POR_DEFECTO] y **no se muestra** mientras
     * sea la única (8.2): existe para que los ingredientes tengan dónde colgar, no para que
     * alguien le ponga nombre a "todo lo que lleva".
     */
    suspend fun crear(titulo: String): ResultadoCrearReceta {
        val limpio = titulo.trim()
        errorEnTituloReceta(limpio)?.let { return ResultadoCrearReceta.NoValido(it) }
        buscarParecida(limpio)?.let { return ResultadoCrearReceta.YaExiste(it) }

        val id = dao.crearReceta(limpio, NOMBRE_SECCION_POR_DEFECTO)
        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se creó la receta '$limpio'"
        )
        return ResultadoCrearReceta.Creada(id)
    }

    suspend fun renombrar(recetaId: Long, titulo: String): Resultado {
        val limpio = titulo.trim()
        errorEnTituloReceta(limpio)?.let { return Resultado.NoSePudo(it) }
        val receta = dao.obtener(recetaId) ?: return Resultado.NoSePudo("Esa receta ya no existe")
        buscarParecida(limpio, exceptoId = recetaId)?.let {
            return Resultado.NoSePudo("Ya tienes una receta que se llama '${it.titulo}'")
        }

        dao.actualizar(receta.copy(titulo = limpio, actualizadoEn = System.currentTimeMillis()))
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se renombró la receta '${receta.titulo}' a '$limpio'"
        )
        return Resultado.Listo
    }

    /**
     * Borra la receta de verdad, cuando ya se confirmó la advertencia (6.3).
     *
     * Lee el título **antes** de borrar, porque después ya no se puede consultar y el
     * historial lo necesita. Todo lo que cuelga de la receta —secciones, ingredientes,
     * rendimiento, duración, precios, pasos, simulación y los sueldos que algún empleado
     * tuviera asignados— se va con ella por las cascadas declaradas en las entidades.
     */
    suspend fun confirmarEliminacion(recetaId: Long) {
        val receta = dao.obtener(recetaId) ?: return

        dao.eliminarPorId(recetaId)
        historial.registrar(
            tipo = TipoEvento.ELIMINACION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se eliminó la receta '${receta.titulo}'",
            detalleAdicional = "Se fueron con ella sus ingredientes, precios y los sueldos " +
                "que tuviera asignados"
        )
    }

    // --- Secciones ---

    fun observarSecciones(recetaId: Long): Flow<List<RecetaSeccion>> =
        dao.observarSecciones(recetaId)

    fun observarIngredientes(recetaId: Long): Flow<List<RecetaIngrediente>> =
        dao.observarIngredientesDeReceta(recetaId)

    /**
     * El rendimiento de una receta, avisando cuando cambia.
     *
     * **Lo miran dos pantallas a la vez** desde que el molde es un paso propio: el paso del
     * molde escribe `usaMolde` y `dimensiones`, y el de rendimiento decide con `usaMolde` si
     * el peso final es obligatorio y si ofrece reescalar por peso. Cada uno con su lectura de
     * una sola vez, las dos pantallas se contradecían.
     */
    fun observarRendimiento(recetaId: Long): Flow<RecetaRendimiento?> =
        dao.observarRendimiento(recetaId)

    suspend fun obtenerSecciones(recetaId: Long): List<RecetaSeccion> =
        dao.obtenerSecciones(recetaId)

    /**
     * Qué nombre proponer para la sección que hasta ahora era invisible.
     *
     * Devuelve `null` cuando no hay nada que bautizar: la receta ya tiene dos o más
     * secciones, así que todas tienen nombre visible y se puede agregar la nueva directo.
     *
     * La pantalla llama esto **antes** de agregar la segunda sección. Si devuelve un texto,
     * muestra el campo con esa sugerencia; si devuelve `null`, va derecho a
     * [agregarSeccion].
     */
    suspend fun nombreQueFaltaBautizar(recetaId: Long): String? {
        val unica = dao.obtenerSecciones(recetaId).singleOrNull() ?: return null
        // Si ya la bautizaron, no hay nada que preguntar: volver a proponer el título de
        // la receta pisaría un nombre que la persona eligió.
        if (!esNombreAutomaticoDeSeccion(unica.nombreSeccion)) return null
        val receta = dao.obtener(recetaId) ?: return null
        return nombreSugeridoParaPrimeraSeccion(receta.titulo)
    }

    /**
     * Agrega una sección, bautizando de paso la que era invisible.
     *
     * [nombreDeLaPrimera] es el nombre para la sección que ya existía, y solo se usa cuando
     * la receta tenía exactamente una. **Los ingredientes ya cargados no se mueven de
     * lugar** (8.2): siguen en la misma sección, que ahora tiene nombre visible.
     */
    suspend fun agregarSeccion(
        recetaId: Long,
        nombre: String,
        nombreDeLaPrimera: String? = null
    ): Resultado {
        val limpio = nombre.trim()
        errorEnNombreSeccion(limpio)?.let { return Resultado.NoSePudo(it) }

        val existentes = dao.obtenerSecciones(recetaId)
        if (existentes.isEmpty()) return Resultado.NoSePudo("Esa receta ya no existe")

        seccionRepetida(existentes, limpio)?.let { return it }

        // Si la única que había todavía tiene el nombre automático, hay que bautizarla
        // antes de que deje de ser invisible: si no, quedaría un encabezado que dice
        // "General" al lado de "Crema".
        //
        // Si ya tenía nombre propio no se pide nada y **no se toca**: ese nombre lo eligió
        // alguien, y pisarlo con el título de la receta sería perder lo que escribió.
        val unica = existentes.singleOrNull()
        if (unica != null && esNombreAutomaticoDeSeccion(unica.nombreSeccion)) {
            val bautizo = nombreDeLaPrimera?.trim()
                ?: return Resultado.NoSePudo(
                    "Antes de agregar otra sección hay que ponerle nombre a la que ya existe"
                )
            errorEnNombreSeccion(bautizo)?.let { return Resultado.NoSePudo(it) }
            // El bautizo también puede chocar: la sugerencia es el título de la receta, y
            // "Salsa de chocolate" como receta y como sección nueva es un caso corriente.
            if (sonElMismoTexto(bautizo, limpio)) {
                return Resultado.NoSePudo(
                    "Las dos secciones quedarían con el mismo nombre. Cámbiale una."
                )
            }
            dao.actualizarSeccion(unica.copy(nombreSeccion = bautizo))
        }

        dao.insertarSeccion(
            RecetaSeccion(
                recetaId = recetaId,
                nombreSeccion = limpio,
                orden = existentes.size
            )
        )
        return Resultado.Listo
    }

    suspend fun renombrarSeccion(seccion: RecetaSeccion, nombre: String): Resultado {
        val limpio = nombre.trim()
        errorEnNombreSeccion(limpio)?.let { return Resultado.NoSePudo(it) }

        // Se excluye a sí misma: corregirle una tilde a "Salsa de chocolate" no puede
        // chocar consigo misma.
        val otras = dao.obtenerSecciones(seccion.recetaId).filter { it.id != seccion.id }
        seccionRepetida(otras, limpio)?.let { return it }

        dao.actualizarSeccion(seccion.copy(nombreSeccion = limpio))
        return Resultado.Listo
    }

    /**
     * Si ya hay una sección que se llama así dentro de la misma receta.
     *
     * Compara con `sonElMismoTexto`, o sea ignorando mayúsculas, tildes y espacios
     * sobrantes: dos "Salsa de chocolate" son la misma sección aunque una lleve el acento y
     * la otra no, y tenerlas por separado no significa nada — al leer la receta no hay forma
     * de saber qué va en cada una.
     *
     * **No hay índice único que lo respalde**, igual que con los títulos de receta y por la
     * misma razón: puede haber secciones repetidas guardadas de antes, y un índice obligaría
     * a renombrarlas durante la migración, cambiando datos reales sin que nadie lo pida. La
     * regla vive acá y está cubierta por pruebas. A diferencia de las recetas repetidas, una
     * sección repetida que ya exista **no bloquea nada**: se sigue pudiendo usar y
     * renombrar, solo que renombrarla ya no puede chocar con otra.
     */
    private fun seccionRepetida(existentes: List<RecetaSeccion>, nombre: String): Resultado? {
        val choque = existentes.firstOrNull { sonElMismoTexto(it.nombreSeccion, nombre) }
        return choque?.let {
            Resultado.NoSePudo("Esta receta ya tiene una sección '${it.nombreSeccion}'")
        }
    }

    /**
     * Borra una sección con todos sus ingredientes.
     *
     * No deja la receta sin ninguna: los ingredientes necesitan dónde colgar y una receta
     * sin secciones no podría recibir nada.
     *
     * Al volver a quedar una sola, su nombre se vuelve invisible por su cuenta —
     * `debeMostrarNombreDeSeccion` lo decide al dibujar—, así que no hace falta renombrarla
     * de vuelta a "General".
     */
    suspend fun eliminarSeccion(recetaId: Long, seccionId: Long): Resultado {
        if (dao.contarSecciones(recetaId) <= 1) {
            return Resultado.NoSePudo("La receta necesita al menos una sección")
        }
        dao.eliminarSeccion(seccionId)
        return Resultado.Listo
    }

    // --- Ingredientes de la receta ---

    suspend fun obtenerIngredientes(recetaId: Long): List<RecetaIngrediente> =
        dao.obtenerTodosLosIngredientes(recetaId)

    /**
     * Cómo se llama lo que lleva esta receta, ordenado y sin repetidos (8.8).
     *
     * Lo usa el atajo `:ingredientes:` de los pasos. Se pide **una sola vez al abrir el menú** y
     * no como observador: mientras el menú está abierto esa lista no cambia, y observarla sumaría
     * una fuente que reemite justo mientras se escribe en un campo de texto (12.2.1).
     *
     * **Sin repetidos** porque el mismo ingrediente en dos secciones es correcto y frecuente —el
     * azúcar del bizcocho y el del almíbar— pero en este menú serían dos filas idénticas.
     */
    suspend fun nombresDeIngredientesDe(recetaId: Long): List<String> {
        val ids = dao.obtenerTodosLosIngredientes(recetaId).map { it.ingredienteId }.distinct()
        if (ids.isEmpty()) return emptyList()
        return dao.nombresDeIngredientes(ids)
            .map { it.nombre }
            .sortedBy { it.lowercase() }
    }

    /**
     * Los ingredientes que ya están puestos en una sección.
     *
     * La pantalla la usa para no ofrecer dos veces el mismo (8.2). Es por sección y no por
     * receta: el mismo ingrediente en dos secciones distintas es correcto.
     */
    suspend fun obtenerIngredientesDeSeccion(seccionId: Long): List<RecetaIngrediente> =
        dao.obtenerIngredientesDeSeccion(seccionId)

    /**
     * Pone un ingrediente en una sección de la receta.
     *
     * **Rechaza el que ya esté en esa misma sección.** Dos filas del mismo ingrediente no son
     * un dato: son una cantidad partida en dos que se lee mal y se suma bien, así que el
     * costo cuadra mientras la lista miente. Lo correcto es cambiarle los gramos a la fila
     * que ya está, y el aviso lleva a eso.
     *
     * **En dos secciones distintas sí se puede**, y es corriente —almendra en el bizcocho y
     * almendra en la decoración—, así que la comprobación es por sección y nunca por receta.
     *
     * Esto ya estaba anotado como agujero conocido en `Firma.kt`: sin comprobación ni índice
     * único, la firma de una receta copiada aplastaba las dos filas en una y hacía
     * desaparecer una cantidad en silencio.
     *
     * **No hay índice único en la base que lo respalde**, por lo mismo que las secciones
     * repetidas: pueden existir filas repetidas guardadas de antes —de hecho existen— y un
     * índice obligaría a resolverlas dentro de una migración, que es el peor lugar para
     * decidir qué cantidad se conserva. La regla vive acá, y lo de antes se sigue pudiendo
     * ver y corregir a mano.
     */
    /**
     * Pone un ingrediente en una sección.
     *
     * [unidades] va con valor **solo cuando el ingrediente se cuenta por unidad** (14.5), y
     * entonces [cantidadG] llega en 0: un objeto no pesa nada para la cuenta del costo, que de
     * punta a punta parte de gramos. Los dos números se escriben juntos y no por separado.
     */
    suspend fun agregarIngrediente(
        seccionId: Long,
        ingredienteId: Long,
        cantidadG: Double,
        unidades: Double? = null,
        orden: Int = 0
    ): Resultado {
        val yaEsta = dao.obtenerIngredientesDeSeccion(seccionId)
            .firstOrNull { it.ingredienteId == ingredienteId }
        if (yaEsta != null) {
            // El nombre se lee de la base y no se recibe: quien llama tiene el ingrediente
            // elegido a mano, pero pedirlo dejaría que el aviso dijera un nombre y la fila
            // guardada fuera otra.
            val nombre = dao.nombreDeIngrediente(ingredienteId) ?: "Ese ingrediente"
            val cuanto = yaEsta.unidades
                ?.let { "${formatearNumero(it)} unidades" }
                ?: "${formatearNumero(yaEsta.cantidadG)} g"
            return Resultado.NoSePudo(
                "$nombre ya está en esta sección, con $cuanto. " +
                    "Toca esa fila para cambiarle la cantidad."
            )
        }
        dao.insertarIngrediente(
            RecetaIngrediente(
                seccionId = seccionId,
                ingredienteId = ingredienteId,
                cantidadG = cantidadG,
                unidades = unidades,
                orden = orden
            )
        )
        return Resultado.Listo
    }

    suspend fun cambiarCantidad(itemId: Long, cantidadG: Double, unidades: Double? = null) =
        dao.actualizarCantidad(itemId, cantidadG, unidades)

    suspend fun quitarIngrediente(itemId: Long) = dao.eliminarIngrediente(itemId)

    /** Lo que cuesta hacer la receta, con el precio **actual** de cada ingrediente (#3). */
    suspend fun costoTotal(recetaId: Long): Double = dao.costoTotalReceta(recetaId)

    /**
     * El costo de varias recetas de una sola consulta, para la lista.
     *
     * Existe para que la pantalla no pregunte una vez por receta: con veinte recetas eso
     * serían veinte consultas cada vez que cambia cualquier cosa.
     *
     * **Devuelve una entrada por cada id pedido**, incluidos los que la consulta no trae.
     * El `GROUP BY` no da fila para una receta sin ingredientes, y quien reciba un mapa
     * incompleto tarde o temprano hace `getValue` y se cae. Ese remiendo va acá una vez y
     * no en cada llamador.
     */
    suspend fun costosDe(recetaIds: List<Long>): Map<Long, Double> {
        if (recetaIds.isEmpty()) return emptyMap()
        val encontrados = dao.costoDeVariasRecetas(recetaIds).associate { it.recetaId to it.costo }
        return recetaIds.associateWith { encontrados[it] ?: 0.0 }
    }

    /**
     * El costo de todas las recetas, avisando solo cuando cambia (lo que use la lista).
     *
     * Es la versión que hay que usar para **mostrar** costos. [costosDe] sigue existiendo
     * para quien necesita una foto de un momento —`obtenerDatosCalculo`, la simulación—,
     * pero una pantalla que use la foto se queda mostrándola cuando el mundo ya cambió: eso
     * fue exactamente el bug de borrar ingredientes y ver los costos viejos.
     *
     * El mapa que devuelve **no tiene entrada para las recetas sin ingredientes**, porque el
     * `GROUP BY` no les da fila. Acá no se puede rellenar como en [costosDe] —no se sabe qué
     * recetas hay sin consultarlas—, así que quien lo lea toma lo que falte como 0.
     */
    fun observarCostos(): Flow<Map<Long, Double>> =
        dao.observarCostos().map { filas -> filas.associate { it.recetaId to it.costo } }

    /**
     * El costo de **una** receta, avisando cuando cambie.
     *
     * **Es la que hay que usar con una receta abierta**; `observarCostos` es para la lista,
     * que las necesita todas. Mirar una sola con aquella significa recorrer y agrupar la base
     * entera para leer un número, y con la receta abierta hay dos pantallas suscritas.
     *
     * A diferencia del mapa de `observarCostos`, esta **sí contesta 0** para una receta sin
     * ingredientes en vez de no traerla: no hay `GROUP BY` que le niegue la fila.
     */
    fun observarCosto(recetaId: Long): Flow<Double> = dao.observarCostoDeReceta(recetaId)

    // --- Molde de la receta ---

    /** El rendimiento de una receta: molde, peso final y trozos. */
    suspend fun obtenerRendimiento(recetaId: Long): RecetaRendimiento? =
        dao.obtenerRendimiento(recetaId)

    /**
     * Guarda los trozos y el peso final, sin tocar el molde.
     *
     * Valida con `revisarRendimiento`, que es la misma comprobación que hace la pantalla en
     * cada tecla; acá se repite porque es la que decide de verdad.
     *
     * **Avisa antes de romper una promoción**: bajar los trozos puede dejar imposible una
     * promo por trozo que pedía más de los que van a quedar (6.2, el tope del último trozo).
     * Si eso pasa **no escribe nada** y devuelve el motivo nombrando cuáles — decir "hay
     * promociones que no caben" obligaría a revisarlas todas a mano.
     */
    suspend fun guardarRendimiento(
        recetaId: Long,
        trozosTexto: String,
        pesoFinalTexto: String
    ): Resultado {
        val actual = dao.obtenerRendimiento(recetaId)
            ?: return Resultado.NoSePudo("Esa receta ya no existe")

        val errores = revisarRendimiento(trozosTexto, pesoFinalTexto, actual.usaMolde)
        if (!errores.sirve) {
            return Resultado.NoSePudo(errores.trozos ?: errores.pesoFinal ?: "Revisa los datos")
        }

        val trozos = textoANumero(trozosTexto)!!.toInt()
        val peso = pesoFinalTexto.takeIf { it.isNotBlank() }?.let { textoANumero(it) }

        val vigentes = dao.obtenerPrecios(recetaId).map { it.aVigente() }
        val apretadas = promocionesQueNoCabenEn(trozos, vigentes)
        if (apretadas.isNotEmpty()) {
            val cuales = apretadas.joinToString(", ") { descripcionDePromocion(it) }
            return Resultado.NoSePudo(
                "Con $trozos trozos no caben estas promociones: $cuales. " +
                    "Ajústalas o elimínalas primero."
            )
        }

        dao.actualizarRendimiento(actual.copy(trozos = trozos, pesoFinalG = peso))
        return Resultado.Listo
    }

    /**
     * Define el molde de una receta **por primera vez**, sin reescalar nada.
     *
     * Es la distinción de 9.3 que se paga cara si se confunde: la primera vez no hay molde
     * original contra el cual comparar, así que no hay factor, no se elige modo y **las
     * cantidades quedan tal como se escribieron**. Reescalar es del segundo molde en
     * adelante, y para eso está [reescalarPorMolde], que corta con error si no hay original.
     *
     * [moldeOrigenId] enlaza la receta al molde del catálogo, para que reciba sus
     * correcciones (5.2). En "modo prueba" viene `null` y la receta queda con las medidas
     * pero sin vínculo.
     *
     * **Volver a poner molde después de quitarlo entra por acá y tampoco reescala**, aunque
     * la receta haya conservado las medidas del molde viejo y técnicamente hubiera contra qué
     * comparar. Es una decisión tomada: el caso real es equivocarse de molde y querer
     * corregirlo, y ahí reescalar sería multiplicar las cantidades por un error. Para
     * reescalar de verdad se cambia de molde sin quitarlo antes.
     */
    suspend fun definirMolde(
        recetaId: Long,
        dimensiones: DimensionesMolde,
        moldeOrigenId: Long?
    ): Resultado {
        val actual = dao.obtenerRendimiento(recetaId)
            ?: return Resultado.NoSePudo("Esa receta ya no existe")
        if (actual.usaMolde && actual.dimensiones != null) {
            return Resultado.NoSePudo("Esta receta ya tiene molde: usa el reescalado")
        }

        dao.actualizarRendimiento(
            actual.copy(usaMolde = true, moldeOrigenId = moldeOrigenId, dimensiones = dimensiones)
        )
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se le definió el molde a '${tituloDe(recetaId)}'"
        )
        return Resultado.Listo
    }

    /**
     * Saca el molde de una receta: pasa a ser una de las que se miden por peso.
     *
     * **No borra las medidas que tenía**, solo deja de usarlas: si fue un error y se vuelve
     * atrás, están donde estaban. Sí corta el vínculo, porque una receta sin molde no tiene
     * por qué recibir correcciones de uno.
     */
    suspend fun quitarMolde(recetaId: Long): Resultado {
        val actual = dao.obtenerRendimiento(recetaId)
            ?: return Resultado.NoSePudo("Esa receta ya no existe")
        if (actual.pesoFinalG == null) {
            return Resultado.NoSePudo(
                "Sin molde el peso final es obligatorio: anótalo antes de quitarlo"
            )
        }
        dao.actualizarRendimiento(actual.copy(usaMolde = false, moldeOrigenId = null))
        return Resultado.Listo
    }

    /**
     * Reescala una receta **con molde** al pasarla a otro molde (8.3.1).
     *
     * Devuelve `NoSePudo` en vez de lanzar excepción cuando `factorEscala` rechaza el
     * cambio: la pantalla tiene que poder mostrar el motivo —"Demasiado riesgo. Mejor
     * escale con el otro método"— y una excepción cerraría la app en vez de explicar.
     *
     * Al terminar guarda las medidas nuevas **y el vínculo**: enlazada si se eligió un molde
     * del catálogo, suelta si fue modo prueba, aunque antes estuviera enlazada a otro.
     *
     * **El peso final se reescala junto con los ingredientes** (8.4.1, #4). Antes no se
     * tocaba, y eso dejaba la receta diciéndose cosas contradictorias: el doble de masa y el
     * mismo peso de producto, con lo que el peso por trozo —que sale de dividir uno por
     * otro— quedaba a la mitad de lo que corresponde sin que nada lo avisara. Como la
     * proporción es una estimación y no una medición, queda marcado con
     * `pesoReescaladoSinRevisar` hasta que alguien mire el campo.
     */
    suspend fun reescalarPorMolde(
        recetaId: Long,
        nuevo: DimensionesMolde,
        modo: ModoReescalado,
        moldeOrigenId: Long?
    ): Resultado {
        val actual = dao.obtenerRendimiento(recetaId)
            ?: return Resultado.NoSePudo("Esa receta ya no existe")
        val original = actual.dimensiones?.takeIf { actual.usaMolde }
            ?: return Resultado.NoSePudo(
                "Esta receta todavía no tiene molde: defínelo primero, sin reescalar"
            )

        val factor = runCatching { factorEscala(original, nuevo, modo) }
            .getOrElse { return Resultado.NoSePudo(it.message ?: "No se pudo reescalar") }

        multiplicarIngredientes(recetaId, factor)
        // Sin peso anotado no hay nada que reescalar ni nada que comprobar: la marca se
        // queda apagada en vez de pedir revisar un campo vacío.
        val pesoReescalado = actual.pesoFinalG?.let { redondearParaGuardar(it * factor) }
        dao.actualizarRendimiento(
            actual.copy(
                dimensiones = nuevo,
                moldeOrigenId = moldeOrigenId,
                pesoFinalG = pesoReescalado,
                pesoReescaladoSinRevisar = pesoReescalado != null
            )
        )
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se reescaló '${tituloDe(recetaId)}' a otro molde",
            detalleAdicional = "Factor ${formatearNumero(factor)}"
        )
        return Resultado.Listo
    }

    /**
     * Apaga el aviso de "peso reescalado, compruébalo" (8.4.1, #4).
     *
     * Se llama al **tocar el campo del peso**, haya cambiado o no. Que el número siga siendo
     * el mismo no significa que nadie lo haya revisado: lo que confirma el dato es haberlo
     * mirado, y mirarlo es justamente tocar el campo. Pedir además que se edite obligaría a
     * borrar y reescribir el mismo número para callar un aviso, que es peor que el aviso.
     *
     * No registra evento en el historial: no cambió ningún dato de la receta, solo se leyó.
     */
    /**
     * Elige en cuántas partes se corta el primer lado del molde (9.4.3).
     *
     * `null` vuelve a "no lo elegí", que no es lo mismo que elegir `1`: sin elección la app
     * reparte lo más parejo que puede, y con `1` se corta el otro lado entero. Los dos son
     * respuestas válidas y hay que poder dar cualquiera de las dos.
     *
     * **No comprueba que divida justo a los trozos**, a propósito: `repartoEfectivo` ya
     * descarta lo que no divida y vuelve al más parejo, y rechazarlo acá obligaría a borrar el
     * reparto cada vez que se toca el número de trozos — cuando lo normal es volver a un
     * número que sí divide y encontrarse la elección donde se dejó.
     */
    suspend fun elegirRepartoDelCorte(recetaId: Long, trozosALoLargo: Int?) {
        val actual = dao.obtenerRendimiento(recetaId) ?: return
        if (actual.trozosALoLargo == trozosALoLargo) return
        dao.actualizarRendimiento(actual.copy(trozosALoLargo = trozosALoLargo))
    }

    suspend fun marcarPesoRevisado(recetaId: Long) {
        val actual = dao.obtenerRendimiento(recetaId) ?: return
        if (!actual.pesoReescaladoSinRevisar) return
        dao.actualizarRendimiento(actual.copy(pesoReescaladoSinRevisar = false))
    }

    /**
     * Reescala una receta **sin molde** (una salsa) para que rinda otro peso.
     *
     * Rechaza las recetas con molde en vez de intentarlo igual: ahí el peso final es
     * opcional, así que el cálculo caería sobre un dato que puede no existir y daría un
     * factor que no significa nada.
     */
    suspend fun reescalarPorPeso(recetaId: Long, nuevoPesoTexto: String): Resultado {
        val actual = dao.obtenerRendimiento(recetaId)
            ?: return Resultado.NoSePudo("Esa receta ya no existe")
        if (actual.usaMolde) {
            return Resultado.NoSePudo("Esta receta usa molde: reescálala eligiendo otro molde")
        }

        errorEnNumeroPositivoTexto(nuevoPesoTexto, "Escribe el peso nuevo")
            ?.let { return Resultado.NoSePudo(it) }
        val nuevoPeso = textoANumero(nuevoPesoTexto)!!

        // El peso final es obligatorio sin molde (6.2); el respaldo a la suma de gramos solo
        // actúa sobre recetas anteriores a esa validación.
        val pesoActual = actual.pesoFinalG ?: dao.sumaGramosIngredientes(recetaId)
        if (pesoActual <= 0) {
            return Resultado.NoSePudo("La receta no tiene peso ni ingredientes: nada que reescalar")
        }

        multiplicarIngredientes(recetaId, nuevoPeso / pesoActual)
        // Acá el peso es lo que se escribió, no lo que salió de una cuenta, así que si
        // quedaba encendido el aviso de "compruébalo" ya no aplica: se acaba de comprobar.
        dao.actualizarRendimiento(
            actual.copy(pesoFinalG = nuevoPeso, pesoReescaladoSinRevisar = false)
        )
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se reescaló '${tituloDe(recetaId)}' a ${formatearNumero(nuevoPeso)} g"
        )
        return Resultado.Listo
    }

    /**
     * Multiplica todas las cantidades por [factor], con el redondeo con que se guarda todo.
     *
     * El redondeo es el mismo que usa el resto de la app (`redondearParaGuardar`): si se
     * guardara la cantidad sin redondear, el subtotal que muestra la pantalla no coincidiría
     * con el que suma la base.
     */
    private suspend fun multiplicarIngredientes(recetaId: Long, factor: Double) {
        dao.obtenerTodosLosIngredientes(recetaId).forEach { item ->
            dao.actualizarCantidad(item.id, redondearParaGuardar(item.cantidadG * factor))
        }
    }

    private suspend fun tituloDe(recetaId: Long): String =
        dao.obtener(recetaId)?.titulo ?: "una receta"

    /**
     * Cambia las medidas del molde guardadas en una receta, sin tocar nada más (5.2).
     *
     * **No toca `moldeOrigenId` ni las cantidades de ingredientes.** Es la que usa
     * `MoldeRepositorio.actualizar` cuando se corrige una medida en el catálogo: corregir
     * un dato mal medido no es cambiar de molde, así que la receta no se reescala. Para lo
     * otro existirá `actualizarDimensionesYVinculoMolde` en la Fase 5.
     *
     * Si la receta no tiene fila de rendimiento no hace nada, en vez de crear una a medias.
     */
    suspend fun actualizarDimensionesMolde(recetaId: Long, dimensiones: DimensionesMolde) {
        val rendimiento = dao.obtenerRendimiento(recetaId) ?: return
        dao.actualizarRendimiento(rendimiento.copy(dimensiones = dimensiones))
    }

    /** Qué recetas siguen enlazadas a un molde del catálogo. */
    suspend fun obtenerRecetasConMoldeOrigen(moldeId: Long): List<Receta> =
        dao.obtenerRecetasConMoldeOrigen(moldeId)

    // --- Duración (8.4) ---

    /**
     * Las duraciones anotadas de una receta, indexadas por tipo.
     *
     * Devuelve un mapa y **no una lista completa con huecos**: los tipos que no están son
     * exactamente los que nadie llenó, y ese es un estado normal —el paso es opcional—. Quien
     * lo lea pone el bloque en blanco.
     */
    suspend fun obtenerDuraciones(recetaId: Long): Map<TipoDuracion, RecetaDuracion> =
        dao.obtenerDuraciones(recetaId).associateBy { it.tipo }

    /**
     * Guarda un bloque de duración, o lo borra si quedó sin decir nada.
     *
     * "Sin decir nada" es un bloque apto y sin cantidad. **Un bloque marcado "no apto" sí
     * dice algo** y se guarda, aunque no tenga números: que algo no se pueda congelar es
     * justamente el dato que uno quiere encontrar después.
     *
     * Borrar en vez de guardar una fila vacía no es un detalle: una fila con `apto = true` y
     * `cantidad = null` es indistinguible de "todavía no lo sé", así que dejarla no aporta y
     * hace que la receta parezca tener el paso llenado.
     */
    suspend fun guardarDuracion(
        recetaId: Long,
        tipo: TipoDuracion,
        apto: Boolean,
        cantidadTexto: String,
        unidad: UnidadDuracion?
    ): Resultado {
        if (obtener(recetaId) == null) return Resultado.NoSePudo("Esa receta ya no existe")

        errorEnCantidadDeDuracion(cantidadTexto, apto)?.let { return Resultado.NoSePudo(it) }

        if (!elBloqueDiceAlgo(apto, cantidadTexto)) {
            dao.eliminarDuracion(recetaId, tipo)
            return Resultado.Listo
        }

        dao.guardarDuracion(
            RecetaDuracion(
                recetaId = recetaId,
                tipo = tipo,
                apto = apto,
                // Con "no apto" se guardan en null a propósito: dejar el número de antes
                // haría que volver a marcarlo apto reviviera un dato que ya nadie confirmó.
                cantidad = if (apto) textoANumero(cantidadTexto)?.toInt() else null,
                unidad = if (apto) unidad else null
            )
        )
        return Resultado.Listo
    }

    // --- Precios ---

    /**
     * Arma la foto de una o varias recetas que después usan todas las fórmulas (6.4).
     *
     * Recibe una lista y no un id suelto a propósito: la simulación múltiple pide todas sus
     * recetas juntas y así resuelve con tres consultas en vez de tres por receta. Para una
     * sola se llama con una lista de un elemento.
     *
     * Ojo con los que **no vienen** en el resultado de las consultas en lote: una receta sin
     * ingredientes no aparece en la de costos, y hay que tomarla como 0. Lo mismo con los
     * trozos, que caen a 1 —el valor con que se siembra la receta— para que ninguna división
     * pueda reventar.
     */
    suspend fun obtenerDatosCalculo(recetaIds: List<Long>): Map<Long, DatosCalculoReceta> {
        if (recetaIds.isEmpty()) return emptyMap()

        val costos = costosDe(recetaIds)
        val trozos = dao.trozosDeVariasRecetas(recetaIds).associate { it.recetaId to it.trozos }
        val precios = dao.preciosDeVariasRecetas(recetaIds).groupBy { it.recetaId }

        return recetaIds.mapNotNull { id ->
            val receta = dao.obtener(id) ?: return@mapNotNull null
            id to DatosCalculoReceta(
                recetaId = id,
                titulo = receta.titulo,
                costoTotal = costos[id] ?: 0.0,
                trozos = trozos[id] ?: 1,
                precios = precios[id].orEmpty().map { it.aVigente() }
            )
        }.toMap()
    }

    /** Los precios de una receta, avisando cuando cambian. La que usa la pantalla (8.6). */
    fun observarPrecios(recetaId: Long): Flow<List<RecetaPrecio>> = dao.observarPrecios(recetaId)

    /**
     * El snapshot de una receta, **avisando cuando cambia** (8.5).
     *
     * Es a `obtenerDatosCalculo` lo que `observarCostos` es a `costosDe`: la misma foto, pero
     * para mostrar en vez de para calcular. Hace falta porque de las cinco cosas que lleva el
     * snapshot, **tres las escriben otros pasos**: el costo sale de los ingredientes
     * (paso 1), los trozos del rendimiento (paso 3) y el título del primero. Con una lectura
     * de una sola vez, este paso mostraría ganancias calculadas contra un costo que ya cambió
     * — que es exactamente el error que costó la tanda 3.
     *
     * Devuelve `null` mientras la receta no exista o no tenga rendimiento: es un estado real
     * —la receta se está creando, o se acaba de borrar— y no un error. Los trozos caen a 1
     * cuando falta la fila, que es el valor con que se siembra la receta y el único que no
     * revienta las divisiones.
     */
    fun observarDatosCalculo(recetaId: Long): Flow<DatosCalculoReceta?> = combine(
        dao.observarReceta(recetaId),
        dao.observarRendimiento(recetaId),
        // **El costo de esta receta y no el de todas.** Antes acá iba `observarCostos()`, que
        // recorre y agrupa la base completa, para después quedarse con una sola entrada del
        // mapa. Con la receta abierta hay dos pantallas suscritas a este snapshot —gastos y
        // simulación—, así que cambiar un gramo disparaba dos recorridos de toda la base.
        // `observarCosto` filtra por receta y además contesta 0 cuando no tiene ingredientes,
        // en vez de no traer fila, así que se fue con ella la trampa del `GROUP BY`.
        observarCosto(recetaId),
        dao.observarPrecios(recetaId)
    ) { receta, rendimiento, costo, precios ->
        receta?.let {
            DatosCalculoReceta(
                recetaId = it.id,
                titulo = it.titulo,
                costoTotal = costo,
                trozos = rendimiento?.trozos ?: 1,
                precios = precios.map { precio -> precio.aVigente() }
            )
        }
    }

    /**
     * Crea un precio o promoción de la receta (8.6).
     *
     * Revisa lo escrito con `revisarPrecio` **antes** de tocar nada, y necesita los trozos de
     * la receta para el tope del último trozo — una promo de 3 trozos no cabe en una receta
     * que rinde 2 (6.2).
     *
     * **No lo deja como referencia**, ni siquiera al ser el primero. No hace falta:
     * `precioDeReferencia` cae solo en el de menor ganancia cuando nadie eligió, así que con
     * un precio único ese precio manda igual. Marcarlo diría que alguien lo decidió, y la
     * pantalla usa justamente esa diferencia para distinguir "lo elegiste tú" de "es el
     * respaldo".
     */
    suspend fun crearPrecio(
        recetaId: Long,
        modo: ModoPrecio,
        cantidadTexto: String,
        precioTotalTexto: String,
        etiqueta: String = ""
    ): Resultado {
        val trozos = dao.obtenerRendimiento(recetaId)?.trozos ?: 1
        val yaGuardados = dao.obtenerPrecios(recetaId).map { it.aVigente() }
        val faltan = basesQueFaltanEn(yaGuardados)
        revisarPrecio(precioTotalTexto, cantidadTexto, modo, trozos, etiqueta, faltan).let { errores ->
            if (!errores.sirve) {
                return Resultado.NoSePudo(
                    errores.precioTotal ?: errores.cantidad ?: errores.etiqueta.orEmpty()
                )
            }
        }
        val cantidad = textoANumero(cantidadTexto)?.toInt() ?: return Resultado.NoSePudo(
            "Escribe cuántos lleva"
        )
        // Un segundo precio base del mismo modo no es un dato, es el mismo dato escrito dos
        // veces: `precioBasePorTrozo` se queda con el primero que encuentra y el otro queda
        // guardado sin alimentar nada. Se vio venir en el celular, donde dos filas base se
        // dibujaban idénticas y solo se distinguían por el monto.
        if (cantidad == 1 && yaGuardados.any { it.modo == modo && it.cantidad == 1 }) {
            return Resultado.NoSePudo(
                if (modo == ModoPrecio.TROZO) {
                    "Ya tienes el precio de un trozo: cámbialo en vez de agregar otro"
                } else {
                    "Ya tienes el precio del producto entero: cámbialo en vez de agregar otro"
                }
            )
        }
        val total = textoANumero(precioTotalTexto) ?: return Resultado.NoSePudo(
            "Escribe a cuánto lo vendes"
        )

        dao.insertarPrecio(
            RecetaPrecio(
                recetaId = recetaId,
                modo = modo,
                cantidad = cantidad,
                precioTotal = redondearParaGuardar(total),
                etiqueta = etiqueta.trim().ifBlank { null }
            )
        )
        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se agregó un precio a '${dao.obtener(recetaId)?.titulo}'",
            detalleAdicional = etiqueta.trim().ifBlank { null }
        )
        return Resultado.Listo
    }

    /**
     * Cambia un precio que ya existe.
     *
     * **Se puede dejar la referencia perdiendo plata, y es a propósito.** `errorAlElegirReferencia`
     * protege el acto de *elegir* una promo que pierde, que es una decisión; bajarle el precio
     * a la que ya manda es otra cosa, y bloquearlo sería además una regla que no se sostiene:
     * el mismo estado se alcanza sin tocar los precios, con que suba el costo de un
     * ingrediente en otra pantalla. Lo que corresponde no es impedirlo sino **decirlo**, y de
     * eso se encarga el aviso de la pantalla.
     */
    suspend fun editarPrecio(
        precioId: Long,
        modo: ModoPrecio,
        cantidadTexto: String,
        precioTotalTexto: String,
        etiqueta: String = ""
    ): Resultado {
        val actual = dao.obtenerPrecioPorId(precioId)
            ?: return Resultado.NoSePudo("Ese precio ya no existe")
        val trozos = dao.obtenerRendimiento(actual.recetaId)?.trozos ?: 1
        revisarPrecio(precioTotalTexto, cantidadTexto, modo, trozos, etiqueta).let { errores ->
            if (!errores.sirve) {
                return Resultado.NoSePudo(
                    errores.precioTotal ?: errores.cantidad ?: errores.etiqueta.orEmpty()
                )
            }
        }
        val cantidad = textoANumero(cantidadTexto)?.toInt() ?: return Resultado.NoSePudo(
            "Escribe cuántos lleva"
        )
        val total = textoANumero(precioTotalTexto) ?: return Resultado.NoSePudo(
            "Escribe a cuánto lo vendes"
        )

        // Se conservan `id`, `recetaId` y **`esReferencia`**: editar un precio no cambia cuál
        // manda. Escribir el objeto entero sin ese cuidado apagaría la referencia en silencio.
        dao.actualizarPrecio(
            actual.copy(
                modo = modo,
                cantidad = cantidad,
                precioTotal = redondearParaGuardar(total),
                etiqueta = etiqueta.trim().ifBlank { null }
            )
        )
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se editó un precio de '${dao.obtener(actual.recetaId)?.titulo}'",
            detalleAdicional = etiqueta.trim().ifBlank { null }
        )
        return Resultado.Listo
    }

    /**
     * Borra un precio.
     *
     * **Borrar el de referencia no deja la receta rota**: `precioDeReferencia` vuelve a caer
     * en el de menor ganancia, que es el mismo respaldo de una receta que nunca eligió. Y
     * borrar el último tampoco: una receta sin precios es exactamente una receta que todavía
     * no pasó por este paso, y las cifras automáticas se esconden solas (`tienePrecio`).
     *
     * No pide confirmación: eso es de la pantalla (6.3).
     */
    suspend fun eliminarPrecio(precioId: Long): Resultado {
        val precio = dao.obtenerPrecioPorId(precioId)
            ?: return Resultado.NoSePudo("Ese precio ya no existe")
        // El nombre se lee **antes** de borrar, porque después no habría cómo nombrarlo.
        val comoSeLlama = descripcionDePromocion(precio.aVigente())
        val titulo = dao.obtener(precio.recetaId)?.titulo

        dao.eliminarPrecio(precioId)
        historial.registrar(
            tipo = TipoEvento.ELIMINACION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se quitó el precio '$comoSeLlama' de '$titulo'"
        )
        return Resultado.Listo
    }

    // --- Simulación de ventas (8.7) ---

    /** Cuántos días y cuántas unidades, avisando cuando cambian. */
    fun observarSimulacion(recetaId: Long): Flow<RecetaSimulacionVenta?> =
        dao.observarSimulacionVenta(recetaId)

    /**
     * Guarda los dos campos de la simulación.
     *
     * Revisa antes de escribir, con las reglas de `logica/validaciones/Simulacion.kt`. Lo
     * particular de este paso es que **nada explota** con un número absurdo —los dos campos se
     * multiplican y ya— así que sin esa revisión un 200 escrito en vez de un 20 se guardaría
     * tan campante y saldría como una proyección mensual creíble y diez veces falsa.
     *
     * **No registra evento en el historial**: esto no es un dato de la receta sino una
     * pregunta de "qué pasaría si", que se cambia muchas veces seguidas justamente para
     * comparar. Anotar cada tanteo llenaría el historial de ruido.
     */
    suspend fun guardarSimulacion(
        recetaId: Long,
        diasTexto: String,
        unidadesTexto: String
    ): Resultado {
        val errores = revisarSimulacion(diasTexto, unidadesTexto)
        if (!errores.sirve) {
            return Resultado.NoSePudo(errores.diasPorSemana ?: errores.unidadesPorDia.orEmpty())
        }
        val dias = textoANumero(diasTexto)?.toInt() ?: return Resultado.NoSePudo(
            "Escribe cuántos días la vendes"
        )
        val unidades = textoANumero(unidadesTexto)?.toInt() ?: return Resultado.NoSePudo(
            "Escribe cuántas vendes por día"
        )
        // La fila se siembra al crear la receta, así que existe siempre; si no existiera, la
        // receta se borró mientras se escribía.
        dao.obtenerSimulacionVenta(recetaId) ?: return Resultado.NoSePudo("Esa receta ya no existe")
        dao.actualizarSimulacionVenta(
            RecetaSimulacionVenta(
                recetaId = recetaId,
                diasPorSemana = dias,
                unidadesPorDia = unidades
            )
        )
        return Resultado.Listo
    }

    // --- Pasos (8.8) ---

    /** Los pasos de una receta, en su orden y avisando cuando cambien. */
    fun observarPasos(recetaId: Long): Flow<List<RecetaPaso>> = dao.observarPasos(recetaId)

    /**
     * Agrega un paso al final, bajo el título que se le indique.
     *
     * Nace **vacío y eso está bien**: el paso se crea al tocar "Agregar", y el texto se escribe
     * después en el campo que aparece. Exigir texto para crearlo obligaría a escribirlo en un
     * cuadro aparte antes de verlo en su lugar, que es más pasos para lo que se hace más veces.
     * `guardarTextoDePaso` se encarga de que un paso que quedó en blanco no sobreviva.
     *
     * El orden sale de `ultimoOrdenDePaso + 1` y no de contar los pasos: contar da mal apenas
     * dos filas comparten un `orden`, y ahí dos pasos nuevos seguidos se pisarían.
     */
    suspend fun agregarPaso(
        recetaId: Long,
        titulo: TituloDePaso = null,
        esGeneralAnidado: Boolean = false
    ): Long = dao.insertarPaso(
        RecetaPaso(
            recetaId = recetaId,
            orden = (dao.ultimoOrdenDePaso(recetaId) ?: -1) + 1,
            contenido = "",
            tituloSeccionId = titulo,
            esGeneralAnidado = esGeneralAnidado
        )
    )

    /**
     * Guarda el texto de un paso, **o lo borra si quedó en blanco** (8.8).
     *
     * Esa segunda mitad no es un atajo: vaciar el campo es cómo se dice "este paso ya no va"
     * (`elPasoDiceAlgo`), y guardarlo como una fila vacía dejaría un número en la lista que no
     * dice nada — con la numeración corrida, además, correría todos los de abajo por un paso
     * fantasma. Es la misma decisión que `guardarDuracion`.
     */
    suspend fun guardarTextoDePaso(pasoId: Long, texto: String): Resultado {
        val paso = dao.obtenerPaso(pasoId) ?: return Resultado.NoSePudo("Ese paso ya no existe")
        errorEnTextoDePaso(texto)?.let { return Resultado.NoSePudo(it) }
        if (!elPasoDiceAlgo(texto)) {
            dao.eliminarPaso(pasoId)
            return Resultado.Listo
        }
        dao.actualizarPaso(paso.copy(contenido = texto.trim()))
        return Resultado.Listo
    }

    /**
     * Cambia bajo qué título va un paso.
     *
     * **Revisa antes de escribir**, como todo lo que puede fallar acá: un título que nombra una
     * sección no se puede usar dos veces (`errorAlUsarTitulo`), y saberlo exige mirar qué
     * títulos tienen los **demás** bloques. Se calculan acá y no en la pantalla porque el
     * repositorio es el que decide, y porque la pantalla podría estar mirando una foto vieja.
     */
    suspend fun cambiarTituloDePaso(pasoId: Long, titulo: TituloDePaso): Resultado {
        val paso = dao.obtenerPaso(pasoId) ?: return Resultado.NoSePudo("Ese paso ya no existe")
        if (titulo != null) {
            val secciones = dao.obtenerSecciones(paso.recetaId)
            val laSeccion = secciones.firstOrNull { it.id == titulo }
                ?: return Resultado.NoSePudo("Esa parte ya no existe en la receta")
            val usadosPorOtros = dao.obtenerPasos(paso.recetaId)
                .filter { it.id != pasoId }
                .map { it.tituloSeccionId }
                .distinct()
            errorAlUsarTitulo(titulo, usadosPorOtros, laSeccion.nombreSeccion)
                ?.let { return Resultado.NoSePudo(it) }
        }
        // Poner un título propio deja de ser un general anidado: son estados excluyentes, y
        // dejar el `true` puesto dibujaría con sangría un paso que ya no es de otra receta.
        dao.actualizarPaso(
            paso.copy(
                tituloSeccionId = titulo,
                esGeneralAnidado = if (titulo == null) paso.esGeneralAnidado else false
            )
        )
        return Resultado.Listo
    }

    suspend fun eliminarPaso(pasoId: Long) = dao.eliminarPaso(pasoId)

    /**
     * Mueve un paso una posición arriba o abajo.
     *
     * **Renumera la lista entera de 0 a n-1 con el movimiento ya aplicado**, en vez de
     * intercambiar los dos `orden` involucrados. Renumerar parece exagerado y es lo único
     * correcto: los `orden` guardados **no son necesariamente 0, 1, 2…** — borrar un paso deja
     * un hueco, así que una receta puede tener perfectamente [0, 5, 9]. Intercambiando índices
     * contra esos valores, mover el último hacia arriba lo mandaba al principio de la lista, no
     * una posición. E intercambiando los `orden` tal cual, dos filas que compartieran valor se
     * quedarían quietas para siempre.
     *
     * El costo es una escritura por paso en vez de dos, dentro de una transacción. Una receta
     * tiene decenas de pasos, no miles.
     *
     * Devuelve `false` si no había con quién intercambiar —el primero hacia arriba, el último
     * hacia abajo—, para que la pantalla pueda apagar el botón en vez de ofrecer algo que no
     * hace nada.
     */
    suspend fun moverPaso(pasoId: Long, haciaArriba: Boolean): Boolean {
        val todos = dao.obtenerPasos(dao.obtenerPaso(pasoId)?.recetaId ?: return false)
        val donde = todos.indexOfFirst { it.id == pasoId }
        val destino = if (haciaArriba) donde - 1 else donde + 1
        if (donde < 0 || destino !in todos.indices) return false

        val reordenados = todos.toMutableList()
        reordenados[donde] = todos[destino]
        reordenados[destino] = todos[donde]
        // **Una sola escritura de la lista completa**, y no un bucle: `@Transaction` es una
        // anotación de DAO y acá no significaría nada, así que un bucle podría cortarse a
        // medias y dejar dos pasos en la misma posición. Room sí envuelve en una transacción
        // un `@Update` de una colección.
        dao.actualizarPasos(reordenados.mapIndexed { posicion, paso -> paso.copy(orden = posicion) })
        return true
    }

    // --- Partes: recetas que usan otras recetas (8.11) ---

    /**
     * Las recetas que se pueden traer dentro de [recetaId], y las que no con su motivo.
     *
     * Deja fuera de la lista **solo a sí misma**; las demás vienen todas, las que se pueden usar
     * y las que no. Quién decide es `sePuedeUsarComoParte`, en `logica/`, y no un `if` escrito
     * acá: es la misma regla que se prueba sin base de datos.
     *
     * Los dos motivos de rechazo son distintos y por eso se distinguen: una receta **hecha de
     * partes** no se puede usar por el tope de un solo nivel (8.11.6), y una de **título
     * repetido** porque esas no se pueden ni abrir (8.11.7) — copiarla dejaría una parte cuya
     * original no se puede revisar.
     */
    suspend fun recetasParaTraer(recetaId: Long): List<RecetaParaTraer> {
        val todas = dao.observarTodas().first()
        val hechasDePartes = dao.idsDeRecetasHechasDePartes().toSet()
        // Se marcan las repetidas sobre la lista ordenada por antigüedad, igual que la lista de
        // recetas: así la que queda utilizable es la original y no una cualquiera.
        val porAntiguedad = todas.sortedBy { it.id }
        val repetidas = marcarRepetidos(porAntiguedad) { it.titulo }
            .withIndex().filter { it.value }.map { porAntiguedad[it.index].id }.toSet()

        return todas.filter { it.id != recetaId }.map { receta ->
            RecetaParaTraer(
                receta = receta,
                motivoNoDisponible = when {
                    !sePuedeUsarComoParte(receta.id, recetaId, receta.id in hechasDePartes) ->
                        MOTIVO_UN_SOLO_NIVEL
                    receta.id in repetidas -> MOTIVO_TITULO_REPETIDO
                    else -> null
                }
            )
        }
    }

    /**
     * Copia una receta entera dentro de otra, dejando el vínculo de referencia (8.11.1).
     *
     * **La copia es independiente**: cambiar una cantidad acá no toca la original y cambiar la
     * original no cambia esto. Lo único que queda es el vínculo, que sirve para decir de dónde
     * vino y para avisar cuando aquella cambie.
     *
     * Llegan **todas** las secciones de la original, no una sola con todo adentro (8.11.2):
     * aplastarlas perdería la división que la receta original tenía por algo. Un nombre que
     * choque se renombra con `nombreSinChocar` en vez de rechazar la copia entera.
     *
     * Los pasos vienen con ellas: los que iban bajo un título quedan bajo la sección copiada, y
     * **los que eran generales de la original quedan como generales anidados** (8.8) — son de
     * la parte que se trajo, no de la receta que se está armando, y mezclarlos haría imposible
     * distinguir "batir hasta que doble" del bizcocho de uno propio.
     *
     * [nombreDeLaPrimera] es lo mismo que en [agregarSeccion] y por el mismo motivo: si la
     * receta todavía tiene su única sección con el nombre automático **y algo cargado**, hay
     * que bautizarla antes de que deje de ser invisible, o quedaría un encabezado "General" al
     * lado de "Bizcocho". Si esa sección está **vacía** no se pregunta nada y se elimina: es la
     * que se siembra al crear la receta (8.10) y no tiene nada que perder.
     */
    suspend fun traerReceta(
        destinoId: Long,
        origenId: Long,
        nombreDeLaPrimera: String? = null
    ): Resultado {
        if (destinoId == origenId) {
            return Resultado.NoSePudo("Una receta no se puede traer dentro de sí misma")
        }
        val origen = dao.obtener(origenId) ?: return Resultado.NoSePudo("Esa receta ya no existe")
        val destino = dao.obtener(destinoId) ?: return Resultado.NoSePudo("Esa receta ya no existe")

        val seccionesDelOrigen = dao.obtenerSecciones(origenId)
        if (seccionesDelOrigen.isEmpty()) {
            return Resultado.NoSePudo("'${origen.titulo}' no tiene nada que traer")
        }
        // El tope de un solo nivel se comprueba **acá y no solo en la pantalla**, por lo mismo
        // que todas las reglas de este repositorio: la pantalla ofrece, este decide.
        val hechaDePartes = seccionesDelOrigen.any {
            it.recetaOrigenId != null || it.firmaDelOrigen != null
        }
        if (!sePuedeUsarComoParte(origenId, destinoId, hechaDePartes)) {
            return Resultado.NoSePudo(MOTIVO_UN_SOLO_NIVEL)
        }

        val existentes = dao.obtenerSecciones(destinoId)
        val sembradaVacia = laSeccionSembradaQueSePuedeTirar(destinoId, existentes)
        if (sembradaVacia == null) {
            // Mismo trato que agregar una sección a mano: la que era invisible tiene que
            // quedar bautizada antes de que aparezca otra al lado.
            val unica = existentes.singleOrNull()
            if (unica != null && esNombreAutomaticoDeSeccion(unica.nombreSeccion)) {
                val bautizo = nombreDeLaPrimera?.trim()
                    ?: return Resultado.NoSePudo(
                        "Antes de traer otra receta hay que ponerle nombre a la sección que ya existe"
                    )
                errorEnNombreSeccion(bautizo)?.let { return Resultado.NoSePudo(it) }
                dao.actualizarSeccion(unica.copy(nombreSeccion = bautizo))
            }
        }

        val firma = firmasDe(listOf(origenId)).getValue(origenId)
        val yaUsados = dao.obtenerSecciones(destinoId)
            .filter { it.id != sembradaVacia?.id }
            .map { it.nombreSeccion }
            .toMutableList()
        var orden = (existentes.maxOfOrNull { it.orden } ?: -1) + 1
        // De qué sección de la copia salió cada sección de la original, para que los pasos
        // copiados apunten a la sección de acá y no a la de allá.
        val equivalencia = mutableMapOf<Long, Long>()

        seccionesDelOrigen.forEach { seccion ->
            val nombre = nombreSinChocar(nombreConQueLlega(seccion, origen, seccionesDelOrigen), yaUsados)
            yaUsados += nombre
            val nuevaId = dao.insertarSeccion(
                RecetaSeccion(
                    recetaId = destinoId,
                    nombreSeccion = nombre,
                    orden = orden++,
                    recetaOrigenId = origenId,
                    firmaDelOrigen = textoDelVinculo(VinculoConLaOriginal(seccion.id, firma))
                )
            )
            equivalencia[seccion.id] = nuevaId
            // Se insertan directo y no por `agregarIngrediente`: aquella rechaza el repetido en
            // una sección, y acá la sección es nueva y está vacía — no hay con qué chocar. Y si
            // la original trae dos filas del mismo ingrediente (datos de antes de la regla), se
            // copian las dos: perder una sería perder una cantidad sin avisar.
            dao.obtenerIngredientesDeSeccion(seccion.id).forEach { fila ->
                dao.insertarIngrediente(
                    RecetaIngrediente(
                        seccionId = nuevaId,
                        ingredienteId = fila.ingredienteId,
                        cantidadG = fila.cantidadG,
                        orden = fila.orden
                    )
                )
            }
        }

        var ordenDePaso = (dao.ultimoOrdenDePaso(destinoId) ?: -1) + 1
        dao.obtenerPasos(origenId).forEach { paso ->
            dao.insertarPaso(
                RecetaPaso(
                    recetaId = destinoId,
                    orden = ordenDePaso++,
                    contenido = paso.contenido,
                    tituloSeccionId = paso.tituloSeccionId?.let { equivalencia[it] },
                    // Un paso general de la original pasa a ser general **anidado** acá. Y uno
                    // cuyo título apuntaba a una sección que ya no existe llega sin título, o
                    // sea también anidado: sigue siendo de la parte que se trajo.
                    esGeneralAnidado = paso.tituloSeccionId?.let { equivalencia[it] } == null
                )
            )
        }

        // Al final y no antes: `eliminarSeccion` no deja la receta sin ninguna, y esa red tiene
        // que seguir puesta. Con las traídas ya adentro, sacar la vacía no puede vaciar nada.
        sembradaVacia?.let { eliminarSeccion(destinoId, it.id) }

        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se trajo '${origen.titulo}' dentro de '${destino.titulo}'",
            detalleAdicional = "Llegaron ${seccionesDelOrigen.size} " +
                if (seccionesDelOrigen.size == 1) "sección" else "secciones"
        )
        return Resultado.Listo
    }

    /**
     * Con qué nombre entra una sección traída, antes de esquivar los que ya existen.
     *
     * Casi siempre es el suyo. La excepción es la sección **invisible**: una receta de una sola
     * parte la tiene todavía llamada "General" (8.2), y ese nombre nunca se muestra allá porque
     * es la única. Copiado tal cual, aparecería un encabezado que dice "General" al lado de
     * "Crema" — un nombre que nadie escribió y que no dice de qué parte se trata.
     *
     * Entra con **el título de la receta de la que salió**, que es lo que uno diría en voz alta:
     * el bizcocho de la torta es "Bizcocho". Es la misma sugerencia que la app propone al
     * bautizar la primera sección a mano (`nombreSugeridoParaPrimeraSeccion`), así que ya viene
     * recortada al tope y nunca sale inválida.
     *
     * **Solo cuando es la única de allá.** Con dos o más, los nombres ya se ven y los eligió
     * alguien; una que se llame "General" entre ellas es una decisión, no un nombre pendiente.
     */
    private fun nombreConQueLlega(
        seccion: RecetaSeccion,
        origen: Receta,
        todasLasDelOrigen: List<RecetaSeccion>
    ): String =
        if (todasLasDelOrigen.size == 1 && esNombreAutomaticoDeSeccion(seccion.nombreSeccion)) {
            nombreSugeridoParaPrimeraSeccion(origen.titulo)
        } else {
            seccion.nombreSeccion
        }

    /**
     * La sección sembrada al crear la receta, si se puede tirar sin perder nada.
     *
     * Es la decisión que 8.11.7 dejó abierta: una receta nueva nace con una sección vacía
     * (8.10), y al traerle una receta entera quedaría esa "General" al lado de las importadas.
     * **Se elimina, y solo cuando se puede demostrar que está vacía**: es la única, todavía
     * tiene el nombre automático, no tiene ingredientes y ningún paso la usa de título.
     *
     * Las cuatro condiciones son necesarias. Con nombre propio la escribió alguien; con
     * ingredientes o con pasos hay trabajo adentro; y si no es la única, ya dejó de ser
     * invisible hace rato. En cualquiera de esos casos se conserva y se la bautiza como al
     * agregar una sección a mano.
     */
    private suspend fun laSeccionSembradaQueSePuedeTirar(
        recetaId: Long,
        existentes: List<RecetaSeccion>
    ): RecetaSeccion? {
        val unica = existentes.singleOrNull() ?: return null
        if (!esNombreAutomaticoDeSeccion(unica.nombreSeccion)) return null
        if (dao.obtenerIngredientesDeSeccion(unica.id).isNotEmpty()) return null
        if (dao.obtenerPasos(recetaId).any { it.tituloSeccionId == unica.id }) return null
        return unica
    }

    /**
     * La foto actual de varias recetas, para comparar contra la guardada (8.11.5).
     *
     * Recibe una lista y no un id suelto por lo mismo que `obtenerDatosCalculo`: los avisos de
     * la lista de recetas necesitan las firmas de **todas** las originales que alguien usó, y
     * pedirlas de a una serían cuatro consultas por receta. Así son cuatro en total.
     */
    private suspend fun firmasDe(recetaIds: List<Long>): Map<Long, FirmaDeReceta> {
        if (recetaIds.isEmpty()) return emptyMap()

        val secciones = dao.seccionesDeVariasRecetas(recetaIds)
        val ingredientes = dao.ingredientesDeVariasSecciones(secciones.map { it.id })
        val nombres = dao.nombresDeIngredientes(ingredientes.map { it.ingredienteId }.distinct())
            .associate { it.id to it.nombre }
        val pasos = dao.pasosDeVariasRecetas(recetaIds)

        val porSeccion = ingredientes.groupBy { it.seccionId }
        return recetaIds.associateWith { recetaId ->
            val suyas = secciones.filter { it.recetaId == recetaId }
            val susPasos = pasos.filter { it.recetaId == recetaId }
            FirmaDeReceta(
                secciones = suyas.map { seccion ->
                    SeccionDeFirma(
                        seccionId = seccion.id,
                        nombre = seccion.nombreSeccion,
                        lineas = porSeccion[seccion.id].orEmpty().map { fila ->
                            LineaDeFirma(
                                lineaId = fila.id,
                                ingredienteId = fila.ingredienteId,
                                // Un ingrediente borrado del catálogo deja su fila sin nombre;
                                // la firma se arma igual, porque lo que compara son cantidades.
                                nombre = nombres[fila.ingredienteId] ?: "Ese ingrediente",
                                gramos = fila.cantidadG
                            )
                        }
                    )
                },
                // Solo los títulos que de verdad tienen pasos: una sección sin ninguno no es un
                // título de la receta, y contarla como uno vacío haría aparecer y desaparecer
                // avisos de "se agregaron los pasos de X" al escribir el primero y borrarlo.
                titulos = suyas.mapNotNull { seccion ->
                    val cuantos = susPasos.count { it.tituloSeccionId == seccion.id }
                    if (cuantos == 0) null
                    else TituloDeFirma(seccion.id, seccion.nombreSeccion, cuantos)
                },
                pasosGenerales = susPasos.count { it.tituloSeccionId == null }
            )
        }
    }

    /**
     * Las partes traídas de una receta, agrupadas y con lo que haya que avisar.
     *
     * Devuelve un grupo por receta original; las huérfanas quedan de a una (ver [ParteTraida]).
     * Una sección cuyo vínculo no se puede leer **no aparece**: se comporta como propia, que es
     * la degradación que ya eligió `firmaDesdeTexto` — perder el aviso, nunca la receta.
     */
    suspend fun partesDe(recetaId: Long): List<ParteTraida> =
        armarPartes(dao.obtenerSecciones(recetaId).filter { it.esTraida })

    /**
     * Lo mismo, avisando cuando cambie **la receta original**.
     *
     * Es la que hay que usar para mostrar los avisos, y lo que la hace distinta de las otras
     * `observar…` es de qué depende: un aviso de acá se enciende porque alguien tocó *otra*
     * receta. Por eso se cuelga de [RecetaDao.latidoDePartes] además de las secciones — sin él,
     * cambiar los gramos del bizcocho no movería nada en la torta hasta reabrirla.
     */
    fun observarPartesDe(recetaId: Long): Flow<List<ParteTraida>> =
        combine(dao.observarSecciones(recetaId), dao.latidoDePartes()) { secciones, _ ->
            armarPartes(secciones.filter { it.esTraida })
        }

    /**
     * Qué recetas tienen algún aviso pendiente, para marcarlas en la lista (8.11.3).
     *
     * Devuelve solo los ids porque es lo único que la lista necesita: el detalle se ve entrando,
     * que es justo lo que el aviso de afuera pide hacer.
     */
    fun observarRecetasConAviso(): Flow<Set<Long>> =
        dao.latidoDePartes().map {
            val traidas = dao.todasLasSeccionesTraidas()
            val deQueReceta = traidas.associate { seccion -> seccion.id to seccion.recetaId }
            armarPartes(traidas)
                .filter { parte -> parte.hayQueAvisar }
                .flatMap { parte -> parte.seccionIds }
                .mapNotNull { seccionId -> deQueReceta[seccionId] }
                .toSet()
        }

    /** Agrupa las secciones traídas y calcula el "¿Qué cambió?" de cada grupo. */
    private suspend fun armarPartes(traidas: List<RecetaSeccion>): List<ParteTraida> {
        if (traidas.isEmpty()) return emptyList()

        val conVinculo = traidas.mapNotNull { seccion ->
            vinculoDesdeTexto(seccion.firmaDelOrigen)?.let { seccion to it }
        }
        val origenes = conVinculo.mapNotNull { it.first.recetaOrigenId }.distinct()
        val ahora = firmasDe(origenes)
        val titulos = origenes.associateWith { dao.obtener(it)?.titulo }

        // Las vivas se juntan por receta original; las huérfanas quedan de a una, porque al
        // borrarse la original SQLite les puso el id en null a todas a la vez y ya no hay con
        // qué distinguir de cuál venía cada una.
        val vivas = conVinculo.filter { it.first.recetaOrigenId != null }
        val huerfanas = conVinculo.filter { it.first.recetaOrigenId == null }

        val deLasVivas = vivas.groupBy { it.first.recetaOrigenId!! }.map { (origenId, delGrupo) ->
            val firmaGuardada = delGrupo.first().second.firma
            ParteTraida(
                origenId = origenId,
                tituloDelOrigen = titulos[origenId],
                seccionIds = delGrupo.map { it.first.id },
                estado = EstadoDelVinculo.VIVO,
                cambios = ahora[origenId]
                    ?.let { compararFirmas(firmaGuardada, it).map { cambio -> cambio.frase } }
                    .orEmpty()
            )
        }

        val deLasHuerfanas = huerfanas.map { (seccion, _) ->
            ParteTraida(
                origenId = null,
                tituloDelOrigen = null,
                seccionIds = listOf(seccion.id),
                estado = EstadoDelVinculo.ORIGINAL_BORRADA,
                cambios = emptyList()
            )
        }

        return deLasVivas + deLasHuerfanas
    }

    /**
     * Trae a la copia lo que cambió en la original, **sin pisar las cantidades ajustadas a mano**.
     *
     * Es la promesa de 8.11.3 y toda la dificultad está ahí: una cantidad de la copia puede ser
     * distinta *a propósito* —la crema de la torta lleva la mitad que la que se vende sola— y
     * eso no se toca nunca. Lo que llega es el cambio de la original, aplicado **como factor de
     * ese ingrediente** (`cantidadAdaptada`): de 550 a 500 allá, con 275 acá, quedan 250.
     *
     * Es por ingrediente y no un factor global porque en la original puede haber cambiado uno
     * solo. Y la fila de acá se empareja con la de allá por el **ingrediente del catálogo**
     * (`emparejarPorIngrediente`), que es lo único estable entre dos recetas con ids propios.
     *
     * Tres casos más, cada uno con su regla:
     *
     * - **Un ingrediente nuevo en la original** llega con la cantidad de allá: no hay una "tuya"
     *   que conservar.
     * - **Uno que la original eliminó** se va — pero solo si la firma dice que había venido de
     *   ella. Lo que se agregó a mano acá se queda: la original nunca lo tuvo y borrarlo sería
     *   perder trabajo por un cambio de otra receta.
     * - **Uno que está en los dos lados pero no en la firma** no se toca. Nadie puede decir de
     *   qué proporción venía, y adivinar es exactamente lo que esto no hace.
     *
     * Al terminar vuelve a tomar la foto, así el aviso se apaga. Actúa sobre **todo el grupo**
     * traído junto (ver [ParteTraida]).
     */
    suspend fun actualizarParte(seccionId: Long): Resultado {
        val tocada = dao.obtenerSeccion(seccionId)
            ?: return Resultado.NoSePudo("Esa sección ya no existe")
        val origenId = tocada.recetaOrigenId
            ?: return Resultado.NoSePudo("La receta original fue eliminada, no hay de dónde traer")
        // Existe seguro: la clave foránea no deja apuntar a una receta borrada, la pone en null
        // — que es el caso de arriba. Se lee igual porque el historial necesita nombrarla.
        val origen = dao.obtener(origenId)
            ?: return Resultado.NoSePudo("La receta original fue eliminada, no hay de dónde traer")
        val nueva = firmasDe(listOf(origenId)).getValue(origenId)

        val delGrupo = seccionesDelGrupo(tocada)
        val actualizadas = mutableListOf<RecetaSeccion>()

        delGrupo.forEach { seccion ->
            val vinculo = vinculoDesdeTexto(seccion.firmaDelOrigen) ?: return@forEach
            val laDeAlla = vinculo.firma.secciones
                .firstOrNull { it.seccionId == vinculo.seccionDeOrigen }
            val filasDeAlla = dao.obtenerIngredientesDeSeccion(vinculo.seccionDeOrigen)
            val filasDeAca = dao.obtenerIngredientesDeSeccion(seccion.id)

            val emparejadas = emparejarPorIngrediente(
                copia = filasDeAca,
                original = filasDeAlla,
                ingredienteDeLaCopia = { it.ingredienteId },
                ingredienteDeLaOriginal = { it.ingredienteId }
            )

            emparejadas.juntos.forEach { (aca, alla) ->
                val antes = vinculo.firma.linea(alla.id)?.gramos ?: return@forEach
                dao.actualizarCantidad(
                    aca.id,
                    cantidadAdaptada(aca.cantidadG, antes, alla.cantidadG)
                )
            }
            emparejadas.soloEnLaCopia.forEach { aca ->
                val vinoDeLaOriginal = laDeAlla?.lineas?.any { it.ingredienteId == aca.ingredienteId }
                if (vinoDeLaOriginal == true) dao.eliminarIngrediente(aca.id)
            }
            emparejadas.soloEnLaOriginal.forEach { alla ->
                dao.insertarIngrediente(
                    RecetaIngrediente(
                        seccionId = seccion.id,
                        ingredienteId = alla.ingredienteId,
                        cantidadG = alla.cantidadG,
                        orden = alla.orden
                    )
                )
            }

            actualizadas += seccion.copy(
                firmaDelOrigen = textoDelVinculo(
                    VinculoConLaOriginal(vinculo.seccionDeOrigen, nueva)
                )
            )
        }

        refirmar(actualizadas)
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se actualizó una parte traída de '${origen.titulo}'",
            detalleAdicional = "Las cantidades se adaptaron en proporción"
        )
        return Resultado.Listo
    }

    /**
     * Deja la parte como está, pero **apaga este aviso** (8.11.3).
     *
     * "Mantener" y "actualizar" son decisiones sobre *este* cambio, y las dos dejan el vínculo
     * vivo: el próximo cambio de la original vuelve a preguntar. Para que eso se cumpla, mantener
     * no puede ser no hacer nada — sin volver a tomar la foto, el mismo aviso seguiría encendido
     * para siempre y no habría forma de distinguir "todavía no lo miré" de "lo miré y lo dejo
     * así". Lo único que se escribe es la firma nueva; **ningún ingrediente se toca**.
     *
     * No es lo mismo que [desvincularParte], que corta el vínculo y no vuelve a avisar nunca.
     */
    suspend fun mantenerParte(seccionId: Long): Resultado {
        val tocada = dao.obtenerSeccion(seccionId)
            ?: return Resultado.NoSePudo("Esa sección ya no existe")
        val origenId = tocada.recetaOrigenId
            ?: return Resultado.NoSePudo("La receta original fue eliminada")
        val nueva = firmasDe(listOf(origenId)).getValue(origenId)

        refirmar(
            seccionesDelGrupo(tocada).mapNotNull { seccion ->
                vinculoDesdeTexto(seccion.firmaDelOrigen)?.let { vinculo ->
                    seccion.copy(
                        firmaDelOrigen = textoDelVinculo(
                            VinculoConLaOriginal(vinculo.seccionDeOrigen, nueva)
                        )
                    )
                }
            }
        )
        return Resultado.Listo
    }

    /**
     * Guarda la foto nueva de todo un grupo **en una sola escritura**.
     *
     * Va junto y no sección por sección por lo mismo que la renumeración de los pasos: Room
     * envuelve en una transacción los `@Update` de una colección y un bucle desde acá no. Media
     * firma vieja dejaría unas secciones avisando y otras no, con la misma receta original
     * detrás — y el aviso que sobrevive no tendría ninguna explicación.
     */
    private suspend fun refirmar(secciones: List<RecetaSeccion>) {
        if (secciones.isNotEmpty()) dao.actualizarSecciones(secciones)
    }

    /**
     * Corta el vínculo para siempre: la sección se queda con lo que tiene y deja de avisar.
     *
     * **Limpia las dos columnas y no solo el id**, que es la regla de 8.11.7: con el id en
     * `null` y la firma puesta, la sección quedaría diciendo que su original desapareció y
     * volvería a preguntar para siempre — lo contrario de lo que se pidió.
     *
     * **No toca los ingredientes.** Lo que está escrito se queda escrito; lo único que se pierde
     * es el "vino de Bizcocho" y el "¿Qué cambió?", que es exactamente lo que se está pidiendo.
     *
     * Es también lo que hace "Mantener" cuando la original fue borrada (8.11.4): ahí no hay nada
     * que seguir mirando, así que dejar el vínculo puesto sería guardar un aviso que no se apaga.
     */
    suspend fun desvincularParte(seccionId: Long): Resultado {
        val tocada = dao.obtenerSeccion(seccionId)
            ?: return Resultado.NoSePudo("Esa sección ya no existe")
        val delGrupo = seccionesDelGrupo(tocada)
        dao.actualizarSecciones(
            delGrupo.map { it.copy(recetaOrigenId = null, firmaDelOrigen = null) }
        )
        return Resultado.Listo
    }

    /**
     * Borra las secciones traídas **y sus pasos** (8.11.4).
     *
     * Los pasos hay que borrarlos a mano: la clave foránea es `SET_NULL`, así que borrar la
     * sección sola los dejaría como generales de esta receta — que es lo correcto al
     * reorganizarla, y lo incorrecto acá, donde lo que se está diciendo es que esa parte no va.
     *
     * **No deja la receta sin ninguna sección**, la misma red de [eliminarSeccion]: los
     * ingredientes necesitan dónde colgar. Si el grupo es todo lo que hay, se rechaza en vez de
     * borrar a medias.
     */
    suspend fun borrarParte(seccionId: Long): Resultado {
        val tocada = dao.obtenerSeccion(seccionId)
            ?: return Resultado.NoSePudo("Esa sección ya no existe")
        val delGrupo = seccionesDelGrupo(tocada)
        if (dao.contarSecciones(tocada.recetaId) <= delGrupo.size) {
            return Resultado.NoSePudo(
                "La receta quedaría sin ninguna sección. Crea una antes de borrar esta."
            )
        }

        val ids = delGrupo.map { it.id }
        dao.eliminarPasosDeSecciones(ids)
        ids.forEach { dao.eliminarSeccion(it) }
        return Resultado.Listo
    }

    /**
     * Las demás secciones que llegaron junto con esta.
     *
     * Una huérfana es **su propio grupo**: sin `recetaOrigenId` no hay con qué juntarla con las
     * que venían de la misma receta borrada, y agruparlas por lo único que queda —la firma—
     * mezclaría las de dos originales distintas si las dos se borraron.
     */
    private suspend fun seccionesDelGrupo(seccion: RecetaSeccion): List<RecetaSeccion> {
        val origenId = seccion.recetaOrigenId ?: return listOf(seccion)
        return dao.obtenerSecciones(seccion.recetaId).filter { it.recetaOrigenId == origenId }
    }

    /**
     * Qué otras recetas usan esta como parte. Alimenta la advertencia de 8.11.4.
     *
     * Borrar una receta usada por otras **no rompe nada de inmediato** —las copias siguen ahí,
     * son independientes— pero deja un aviso pendiente en cada una. Por eso se enumera antes:
     * descubrirlo después no tendría explicación.
     */
    suspend fun recetasQueUsanEstaReceta(recetaId: Long): List<Receta> =
        dao.recetasQueUsanLaReceta(recetaId)

    /**
     * Cambia cuál de los precios de la receta alimenta las cifras automáticas (8.6).
     *
     * Devuelve el motivo del rechazo, o `null` si se pudo.
     *
     * **Revisa antes de escribir.** Si el precio elegido pierde plata no se toca nada, así
     * que "que la acción se cancele y vuelva al valor que tenía" es simplemente no haber
     * escrito: la referencia anterior sigue siendo la que era, sin ningún paso de deshacer.
     */
    suspend fun elegirPrecioDeReferencia(recetaId: Long, precioId: Long): String? {
        val datos = obtenerDatosCalculo(listOf(recetaId))[recetaId]
            ?: return "Esa receta ya no existe"
        val elegido = dao.obtenerPrecios(recetaId).firstOrNull { it.id == precioId }
            ?: return "Ese precio ya no existe"

        errorAlElegirReferencia(elegido.aVigente(), datos)?.let { return it }

        dao.fijarPrecioDeReferencia(recetaId, precioId)
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se cambió el precio de referencia de '${datos.titulo}'",
            detalleAdicional = elegido.etiqueta
        )
        return null
    }
}

/** Cómo terminó el intento de crear una receta. */
sealed interface ResultadoCrearReceta {
    data class Creada(val recetaId: Long) : ResultadoCrearReceta
    data class NoValido(val motivo: String) : ResultadoCrearReceta

    /** Ya hay una receta que se llama igual. Se devuelve para poder nombrarla en el aviso. */
    data class YaExiste(val existente: Receta) : ResultadoCrearReceta
}
