package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.db.dao.CostoDeReceta
import com.sandyyera.reposteria.data.db.dao.EmpleadoDao
import com.sandyyera.reposteria.data.db.dao.HistorialDao
import com.sandyyera.reposteria.data.db.dao.IngredienteDao
import com.sandyyera.reposteria.data.db.dao.LineaConIngrediente
import com.sandyyera.reposteria.data.db.dao.MoldeDao
import com.sandyyera.reposteria.data.db.dao.NombreDeIngrediente
import com.sandyyera.reposteria.data.db.dao.RecetaDao
import com.sandyyera.reposteria.data.db.dao.TrozosDeReceta
import com.sandyyera.reposteria.data.db.entidades.Empleado
import com.sandyyera.reposteria.data.db.entidades.EmpleadoRecetaSueldo
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultiple
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultipleDetalle
import com.sandyyera.reposteria.data.db.entidades.EventoCambio
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Molde
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.RecetaDuracion
import com.sandyyera.reposteria.data.db.entidades.RecetaIngrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaPaso
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.db.entidades.RecetaRendimiento
import com.sandyyera.reposteria.data.db.entidades.RecetaSeccion
import com.sandyyera.reposteria.data.db.entidades.RecetaSimulacionVenta
import com.sandyyera.reposteria.data.db.entidades.esTraida
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Versiones de mentira de los DAO, con los datos en memoria.
 *
 * Sirven para probar los repositorios y los ViewModel **sin base de datos y sin celular**,
 * con `./gradlew :app:test`. Los DAO de Room son interfaces, así que se pueden reemplazar
 * por estas sin tocar una línea del código de la app.
 *
 * Lo importante es que no son cascarones vacíos: [IngredienteDaoFalso] **imita el índice
 * único** de la tabla y revienta igual que la base de verdad ante un nombre repetido. Sin
 * eso, la prueba de "no se puede crear un ingrediente duplicado" pasaría aunque la
 * comprobación no existiera, que es justo lo que cierra la app.
 *
 * Lo que un DAO falso todavía no implementa **falla ruidosamente** en vez de devolver algo
 * vacío. Un `emptyList()` de relleno haría pasar pruebas que en realidad no probaron nada.
 */

class IngredienteDaoFalso : IngredienteDao {

    private val filas = MutableStateFlow<List<Ingrediente>>(emptyList())
    private var siguienteId = 1L

    /**
     * Qué ingredientes tienen fila en el almacén, para el aviso de 14.4.
     *
     * Es un `Flow` que la prueba mueve a mano y no una consulta a un almacén falso, porque
     * todavía no hay ninguna prueba que necesite las dos tablas de verdad. **Arranca vacío, que
     * es la respuesta correcta y no un relleno**: sin almacén, ningún ingrediente está en él, y
     * el aviso tiene que estar encendido en todos. El día que exista un `AlmacenDaoFalso`, esto
     * pasa a colgar de él.
     */
    val enElAlmacen = MutableStateFlow<Set<Long>>(emptySet())

    /** Deja ingredientes puestos de entrada, sin pasar por las comprobaciones. */
    fun sembrar(vararg ingredientes: Ingrediente) {
        filas.value = filas.value + ingredientes.map {
            if (it.id == 0L) it.copy(id = siguienteId++) else it
        }
    }

    private fun ordenados(lista: List<Ingrediente>) = lista.sortedBy { it.nombre.lowercase() }

    override fun observarTodos(): Flow<List<Ingrediente>> = filas.map(::ordenados)

    override fun observarParaRecetas(): Flow<List<Ingrediente>> =
        filas.map { lista -> ordenados(lista.filter { it.vaEnRecetas }) }

    /**
     * Los que no están en el almacén, imitando el `NOT EXISTS` de la consulta real.
     *
     * **Cuelga de [enElAlmacen] además del catálogo**, que es la mitad del punto: agregar algo
     * al almacén tiene que apagar su aviso sin que nadie lo pida. Un falso que solo mirara el
     * catálogo dejaría pasar una versión de la app donde el aviso no se apaga nunca.
     */
    override fun observarSinAlmacen(): Flow<List<Long>> =
        combine(filas, enElAlmacen) { ingredientes, guardados ->
            ingredientes.filter { it.id !in guardados }.map { it.id }
        }

    override suspend fun obtenerTodosUnaVez(): List<Ingrediente> = ordenados(filas.value)

    override suspend fun obtener(ingredienteId: Long): Ingrediente? =
        filas.value.firstOrNull { it.id == ingredienteId }

    override suspend fun insertar(ingrediente: Ingrediente): Long {
        // La tabla tiene un índice único sobre `nombre` con COLLATE NOCASE. Insertar un
        // repetido lanza excepción en la base de verdad y cierra la app si nadie lo
        // comprobó antes, así que acá pasa exactamente lo mismo.
        require(filas.value.none { it.nombre.equals(ingrediente.nombre, ignoreCase = true) }) {
            "UNIQUE constraint failed: ingredientes.nombre"
        }
        val id = siguienteId++
        filas.value = filas.value + ingrediente.copy(id = id)
        return id
    }

    override suspend fun actualizar(ingrediente: Ingrediente) {
        require(
            filas.value.none {
                it.id != ingrediente.id && it.nombre.equals(ingrediente.nombre, ignoreCase = true)
            }
        ) { "UNIQUE constraint failed: ingredientes.nombre" }
        filas.value = filas.value.map { if (it.id == ingrediente.id) ingrediente else it }
    }

    override suspend fun eliminar(ingrediente: Ingrediente) = eliminarPorId(ingrediente.id)

    override suspend fun eliminarPorId(ingredienteId: Long) {
        filas.value = filas.value.filterNot { it.id == ingredienteId }
    }
}

class HistorialDaoFalso : HistorialDao {

    /** Lo anotado, en el orden en que se anotó. Las pruebas lo revisan directamente. */
    val eventos = mutableListOf<EventoCambio>()

    /** Hasta qué fecha se pidió limpiar, en cada llamada. */
    val limpiezasPedidas = mutableListOf<Long>()

    private var siguienteId = 1L

    override fun observarTodos(): Flow<List<EventoCambio>> =
        MutableStateFlow(eventos.sortedByDescending { it.creadoEn })

    override suspend fun insertar(evento: EventoCambio): Long {
        val id = siguienteId++
        eventos += evento.copy(id = id)
        return id
    }

    override suspend fun borrarAnterioresA(anteriorA: Long) {
        limpiezasPedidas += anteriorA
        eventos.removeAll { it.creadoEn < anteriorA }
    }
}

/**
 * El falso de `MoldeDao`: el catálogo de moldes en memoria.
 *
 * Es más simple que el de recetas porque la tabla no tiene nada colgando: lo que sí hay que
 * imitar es que **borrar un molde no borra las recetas**, solo deja sus `moldeOrigenId`
 * apuntando a nada. Eso lo hace [RecetaDaoFalso.desvincularMolde], que se llama desde acá
 * igual que lo haría la clave foránea `SET_NULL` de la base real: si el falso no lo hiciera,
 * una prueba podría afirmar que las recetas quedan desvinculadas sin que nada lo haga.
 */
class MoldeDaoFalso(
    private val recetas: RecetaDaoFalso? = null
) : MoldeDao {

    private val filas = MutableStateFlow<List<Molde>>(emptyList())
    private var siguienteId = 1L

    private fun ordenados(lista: List<Molde>) = lista.sortedBy { it.nombre.lowercase() }

    /** Atajo para las pruebas: deja moldes puestos sin pasar por el repositorio. */
    fun sembrar(vararg moldes: Molde) {
        moldes.forEach { molde ->
            val id = if (molde.id == 0L) siguienteId++ else molde.id
            siguienteId = maxOf(siguienteId, id + 1)
            filas.value = filas.value + molde.copy(id = id)
        }
    }

    override fun observarTodos(): Flow<List<Molde>> = filas.map(::ordenados)

    override suspend fun obtenerTodosUnaVez(): List<Molde> = ordenados(filas.value)

    override suspend fun obtener(moldeId: Long): Molde? =
        filas.value.firstOrNull { it.id == moldeId }

    override suspend fun insertar(molde: Molde): Long {
        val id = siguienteId++
        filas.value = filas.value + molde.copy(id = id)
        return id
    }

    override suspend fun actualizar(molde: Molde) {
        filas.value = filas.value.map { if (it.id == molde.id) molde else it }
    }

    override suspend fun eliminarPorId(moldeId: Long) {
        filas.value = filas.value.filterNot { it.id == moldeId }
        // La regla SET_NULL de la clave foránea, a mano.
        recetas?.desvincularMolde(moldeId)
    }
}

/**
 * El falso de `RecetaDao`: una base de recetas en memoria.
 *
 * Creció al llegar la Fase 3, que es cuando apareció el consumidor real. Ahora imita de
 * verdad lo que hace SQLite, incluidas las tres cosas que se olvidan al escribir un falso
 * de relleno y que después aparecen como bugs:
 *
 * - **Las cascadas.** Borrar una receta se lleva sus secciones, y borrar una sección se
 *   lleva sus ingredientes. Si el falso no las hiciera, una prueba de borrado pasaría
 *   dejando filas huérfanas que en la base real sí desaparecen (o al revés).
 * - **El `JOIN` del costo** lee el `valorPorGramo` del ingrediente **en el momento de la
 *   consulta**, que es la decisión #3. Por eso necesita el catálogo: recibe el mismo
 *   [IngredienteDaoFalso] que use la prueba.
 * - **`costoDeVariasRecetas` omite las recetas sin ingredientes**, igual que el `GROUP BY`
 *   real. Quien la llame tiene que tomarlas como 0, y esa trampa solo se puede probar si
 *   el falso la reproduce.
 * - **La cascada llega también a las duraciones.** Borrar una receta se lleva sus filas de
 *   `receta_duracion`, igual que la clave foránea real: si el falso las dejara, una prueba
 *   podría afirmar que la cascada funciona sobre una tabla que en realidad quedó con basura.
 * - **`observarCostos` vuelve a emitir cuando cambia cualquiera de las tablas que consulta**,
 *   que es lo que hace el `InvalidationTracker` de Room. Sin esto no se podría probar el bug
 *   que motivó esa consulta: un falso que devuelva un `Flow` de un solo valor pasa la prueba
 *   aunque la app se quede mostrando costos viejos, que es justo lo que pasaba.
 *
 * Lo que sigue sin implementarse falla ruidosamente, con la misma regla de antes.
 */
class RecetaDaoFalso(
    private val catalogo: IngredienteDaoFalso = IngredienteDaoFalso()
) : RecetaDao {

    private val recetas = MutableStateFlow<List<Receta>>(emptyList())
    private val secciones = mutableListOf<RecetaSeccion>()
    private val items = mutableListOf<RecetaIngrediente>()
    private val rendimientos = mutableListOf<RecetaRendimiento>()
    private val precios = mutableListOf<RecetaPrecio>()
    private val simulaciones = mutableListOf<RecetaSimulacionVenta>()
    private val duraciones = mutableListOf<RecetaDuracion>()
    private val pasos = mutableListOf<RecetaPaso>()

    private var siguienteId = 1L
    private fun nuevoId() = siguienteId++

    /**
     * El equivalente del `InvalidationTracker` de Room, a mano.
     *
     * `secciones` e `items` son listas normales y no avisan cuando cambian. Este contador se
     * incrementa en cada escritura que toca alguna de las dos, y de él cuelga
     * [observarCostos]. Si se agrega una escritura nueva y se olvida llamar a [cambio], el
     * falso deja de reemitir y la prueba de regresión de los costos viejos se cae — que es
     * exactamente lo que tiene que pasar.
     */
    private val cambios = MutableStateFlow(0)

    private fun cambio() {
        cambios.value++
    }

    /**
     * Atajo para las pruebas: deja [receta] usando el ingrediente [ingredienteId].
     *
     * Crea de verdad la receta, su sección y la fila que las une, en vez de anotarlo en un
     * mapa aparte: así lo que después consulten `obtenerRecetasQueUsan` o el costo sale de
     * los mismos datos y no de dos verdades distintas.
     */
    fun declararUso(ingredienteId: Long, receta: Receta, cantidadG: Double = 100.0) {
        if (recetas.value.none { it.id == receta.id }) {
            recetas.value = recetas.value + receta
            siguienteId = maxOf(siguienteId, receta.id + 1)
        }
        val seccion = secciones.firstOrNull { it.recetaId == receta.id }
            ?: RecetaSeccion(id = nuevoId(), recetaId = receta.id, nombreSeccion = "General")
                .also { secciones += it }
        items += RecetaIngrediente(
            id = nuevoId(),
            seccionId = seccion.id,
            ingredienteId = ingredienteId,
            cantidadG = cantidadG
        )
        cambio()
    }

    /**
     * Lo que hace la clave foránea `SET_NULL` al borrarse un molde del catálogo (5.2).
     *
     * Las recetas **conservan sus medidas** y solo pierden el vínculo. Lo llama
     * [MoldeDaoFalso.eliminarPorId], porque en la base real esto lo hace SQLite sola.
     */
    fun desvincularMolde(moldeId: Long) {
        rendimientos.indices.forEach { i ->
            if (rendimientos[i].moldeOrigenId == moldeId) {
                rendimientos[i] = rendimientos[i].copy(moldeOrigenId = null)
            }
        }
        cambio()
    }

    /** Las filas de ingredientes de una receta, resolviendo el join con las secciones. */
    private fun itemsDe(recetaId: Long): List<RecetaIngrediente> {
        val ids = secciones.filter { it.recetaId == recetaId }.map { it.id }.toSet()
        return items.filter { it.seccionId in ids }
    }

    // --- La receta ---

    override fun observarTodas(): Flow<List<Receta>> =
        recetas.map { lista -> lista.sortedBy { it.titulo.lowercase() } }

    override suspend fun obtener(recetaId: Long): Receta? =
        recetas.value.firstOrNull { it.id == recetaId }

    /**
     * Las cuatro consultas reactivas cuelgan de las mismas fuentes que en Room.
     *
     * `observarReceta` de la lista de recetas; las otras tres del contador [cambios], que es
     * la imitación del `InvalidationTracker`. Que sean `Flow` de verdad y no un valor suelto
     * es lo que permite probar el bug que las motivó: dos pantallas mirando el mismo
     * rendimiento y quedándose cada una con su foto vieja.
     */
    override fun observarReceta(recetaId: Long): Flow<Receta?> =
        recetas.map { lista -> lista.firstOrNull { it.id == recetaId } }

    override fun observarSecciones(recetaId: Long): Flow<List<RecetaSeccion>> =
        cambios.map { secciones.filter { it.recetaId == recetaId }.sortedBy { it.orden } }

    override fun observarIngredientesDeReceta(recetaId: Long): Flow<List<RecetaIngrediente>> =
        cambios.map { itemsDe(recetaId) }

    /**
     * Las líneas ya cruzadas con el catálogo, imitando el `JOIN` de la consulta real.
     *
     * **El `JOIN` es INNER y acá también**: una fila cuyo ingrediente se borró no aparece, igual
     * que no suma al costo. Un falso que la dejara pasar aprobaría una versión del resumen que
     * dibuja renglones sin nombre ni precio.
     *
     * Cuelga además de `catalogo.observarTodos()` por lo mismo que el costo: renombrar o cambiarle
     * el precio a un ingrediente tiene que llegar al resumen sin que nadie lo pida.
     */
    override fun observarLineasConIngrediente(recetaId: Long): Flow<List<LineaConIngrediente>> =
        combine(cambios, catalogo.observarTodos()) { _, fichas ->
            val porId = fichas.associateBy { it.id }
            itemsDe(recetaId).mapNotNull { fila ->
                val ficha = porId[fila.ingredienteId] ?: return@mapNotNull null
                LineaConIngrediente(
                    id = fila.id,
                    seccionId = fila.seccionId,
                    ingredienteId = fila.ingredienteId,
                    cantidadG = fila.cantidadG,
                    unidades = fila.unidades,
                    nombre = ficha.nombre,
                    valorPorGramo = ficha.valorPorGramo
                )
            }
        }

    override fun observarRendimiento(recetaId: Long): Flow<RecetaRendimiento?> =
        cambios.map { rendimientos.firstOrNull { it.recetaId == recetaId } }

    override suspend fun insertar(receta: Receta): Long {
        val id = nuevoId()
        recetas.value = recetas.value + receta.copy(id = id)
        return id
    }

    override suspend fun actualizar(receta: Receta) {
        recetas.value = recetas.value.map { if (it.id == receta.id) receta else it }
    }

    override suspend fun eliminarPorId(recetaId: Long) {
        // La cascada declarada en las entidades: todo lo que cuelga se va con la receta.
        val seccionesDeLaReceta = secciones.filter { it.recetaId == recetaId }.map { it.id }.toSet()
        items.removeAll { it.seccionId in seccionesDeLaReceta }
        secciones.removeAll { it.recetaId == recetaId }
        rendimientos.removeAll { it.recetaId == recetaId }
        precios.removeAll { it.recetaId == recetaId }
        simulaciones.removeAll { it.recetaId == recetaId }
        duraciones.removeAll { it.recetaId == recetaId }
        pasos.removeAll { it.recetaId == recetaId }
        // **SET_NULL sobre `recetaOrigenId`**, que es lo que hace la clave foránea de 5.5.1: las
        // secciones que se copiaron de esta receta **no se borran** —conservan sus ingredientes—
        // y solo pierden el id. La firma **no se toca**, y ahí está todo el punto: es lo único
        // que después distingue una sección huérfana de una desvinculada a mano (8.11.7). Sin
        // esto acá, una prueba podría afirmar que la copia sobrevive sin que nada lo hiciera.
        secciones.replaceAll {
            if (it.recetaOrigenId == recetaId) it.copy(recetaOrigenId = null) else it
        }
        recetas.value = recetas.value.filterNot { it.id == recetaId }
        cambio()
    }

    // --- Costo ---

    override suspend fun costoTotalReceta(recetaId: Long): Double =
        itemsDe(recetaId).sumOf { item ->
            // INNER JOIN: si el ingrediente ya no existe, esa fila simplemente no suma.
            val valor = catalogo.obtener(item.ingredienteId)?.valorPorGramo ?: return@sumOf 0.0
            // El mismo `COALESCE(unidades, cantidadG)` de la consulta real: lo que se cuenta por
            // unidad va con 0 gramos y aun así cuesta (14.5). Si el falso multiplicara por los
            // gramos, las pruebas dirían que una bolsa sale gratis y la app cobraría por ella.
            (item.unidades ?: item.cantidadG) * valor
        }

    override suspend fun costoDeVariasRecetas(recetaIds: List<Long>): List<CostoDeReceta> =
        recetaIds
            // El GROUP BY real no devuelve fila para una receta sin ingredientes **que
            // sumen**. Se mira contra el catálogo y no solo si hay filas, porque el JOIN es
            // INNER: una fila que apunta a un ingrediente ya borrado no agrupa nada. Quien
            // lea el resultado usa justamente eso para distinguir "no tiene ingredientes"
            // de "los tiene y valen 0", así que el falso tiene que dar la misma respuesta.
            .filter { receta ->
                itemsDe(receta).any { catalogo.obtener(it.ingredienteId) != null }
            }
            .map { CostoDeReceta(it, costoTotalReceta(it)) }

    /**
     * Las tres fuentes que vigila Room en esta consulta, imitadas a mano.
     *
     * `catalogo.observarTodos()` entra porque el costo lee el `valorPorGramo` del momento:
     * borrar un ingrediente del catálogo tiene que cambiar el costo de las recetas que lo
     * usaban, y ese es el caso que se rompió en la app de verdad.
     */
    override fun observarCostos(): Flow<List<CostoDeReceta>> =
        combine(recetas, cambios, catalogo.observarTodos()) { lista, _, _ -> lista }
            .map { lista -> costoDeVariasRecetas(lista.map { it.id }) }

    /**
     * El costo de una sola receta, colgado de **las mismas tres fuentes** que el de todas.
     *
     * Cuelga de `recetas` también, aunque filtre por id: si esa receta se borra, quien esté
     * mirando su costo tiene que enterarse.
     *
     * **Contesta 0 para una receta sin ingredientes en vez de omitirla**, igual que la
     * consulta real: esa no lleva `GROUP BY`, así que `SUM` sobre cero filas da una fila con
     * `NULL` que el `COALESCE` convierte en 0. Es justo la diferencia con `observarCostos`, y
     * el falso tiene que reproducirla o la prueba no vería el día que se confundan.
     */
    override fun observarCostoDeReceta(recetaId: Long): Flow<Double> =
        combine(recetas, cambios, catalogo.observarTodos()) { _, _, _ -> Unit }
            .map { costoTotalReceta(recetaId) }

    // --- Consultas que cruzan tablas ---

    override suspend fun obtenerRecetasQueUsan(ingredienteId: Long): List<Receta> {
        val seccionesUsadas = items.filter { it.ingredienteId == ingredienteId }
            .map { it.seccionId }.toSet()
        val ids = secciones.filter { it.id in seccionesUsadas }.map { it.recetaId }.toSet()
        return recetas.value.filter { it.id in ids }.sortedBy { it.titulo.lowercase() }
    }

    override suspend fun quitarIngredienteDeTodasLasSecciones(ingredienteId: Long) {
        items.removeAll { it.ingredienteId == ingredienteId }
        cambio()
    }

    // --- Secciones e ingredientes ---

    override suspend fun obtenerSecciones(recetaId: Long): List<RecetaSeccion> =
        secciones.filter { it.recetaId == recetaId }.sortedBy { it.orden }

    override suspend fun contarSecciones(recetaId: Long): Int =
        secciones.count { it.recetaId == recetaId }

    override suspend fun obtenerSeccion(seccionId: Long): RecetaSeccion? =
        secciones.firstOrNull { it.id == seccionId }

    override suspend fun seccionesDeVariasRecetas(recetaIds: List<Long>): List<RecetaSeccion> =
        secciones.filter { it.recetaId in recetaIds }
            .sortedWith(compareBy({ it.recetaId }, { it.orden }))

    override suspend fun actualizarSecciones(lasQueCambian: List<RecetaSeccion>) {
        lasQueCambian.forEach { actualizarSeccion(it) }
    }

    override suspend fun insertarSeccion(seccion: RecetaSeccion): Long {
        val id = nuevoId()
        secciones += seccion.copy(id = id)
        cambio()
        return id
    }

    override suspend fun actualizarSeccion(seccion: RecetaSeccion) {
        val posicion = secciones.indexOfFirst { it.id == seccion.id }
        if (posicion >= 0) secciones[posicion] = seccion
        cambio()
    }

    override suspend fun eliminarSeccion(seccionId: Long) {
        items.removeAll { it.seccionId == seccionId }   // cascada
        // **SET_NULL y no cascada**, imitando la clave foránea de `receta_pasos`: borrar una
        // sección deja sus pasos como General en vez de llevárselos. El texto lo escribió
        // alguien. Sin esto acá, una prueba podría afirmar que los pasos sobreviven sin que
        // nada lo hiciera.
        pasos.replaceAll { if (it.tituloSeccionId == seccionId) it.copy(tituloSeccionId = null) else it }
        secciones.removeAll { it.id == seccionId }
        cambio()
    }

    override suspend fun obtenerTodosLosIngredientes(recetaId: Long): List<RecetaIngrediente> {
        val orden = secciones.filter { it.recetaId == recetaId }.associate { it.id to it.orden }
        return itemsDe(recetaId).sortedWith(
            compareBy({ orden[it.seccionId] ?: 0 }, { it.orden })
        )
    }

    override suspend fun sumaGramosIngredientes(recetaId: Long): Double =
        itemsDe(recetaId).sumOf { it.cantidadG }

    override suspend fun obtenerIngredientesDeSeccion(seccionId: Long): List<RecetaIngrediente> =
        items.filter { it.seccionId == seccionId }.sortedBy { it.orden }

    // Cruza contra el catálogo que use la prueba, igual que el JOIN del costo: si devolviera
    // un nombre inventado, el aviso de "ya está en esta sección" pasaría la prueba diciendo
    // cualquier cosa.
    override suspend fun nombreDeIngrediente(ingredienteId: Long): String? =
        catalogo.obtener(ingredienteId)?.nombre

    override suspend fun nombresDeIngredientes(
        ingredienteIds: List<Long>
    ): List<NombreDeIngrediente> = ingredienteIds.mapNotNull { id ->
        catalogo.obtener(id)?.let { NombreDeIngrediente(id, it.nombre) }
    }

    override suspend fun ingredientesDeVariasSecciones(
        seccionIds: List<Long>
    ): List<RecetaIngrediente> = items.filter { it.seccionId in seccionIds }
        .sortedWith(compareBy({ it.seccionId }, { it.orden }, { it.id }))

    override suspend fun insertarIngrediente(item: RecetaIngrediente): Long {
        val id = nuevoId()
        items += item.copy(id = id)
        cambio()
        return id
    }

    override suspend fun actualizarCantidad(itemId: Long, cantidad: Double, unidades: Double?) {
        val posicion = items.indexOfFirst { it.id == itemId }
        if (posicion >= 0) {
            items[posicion] = items[posicion].copy(cantidadG = cantidad, unidades = unidades)
        }
        cambio()
    }

    override suspend fun eliminarIngrediente(itemId: Long) {
        items.removeAll { it.id == itemId }
        cambio()
    }

    // --- Rendimiento ---

    override suspend fun obtenerRendimiento(recetaId: Long): RecetaRendimiento? =
        rendimientos.firstOrNull { it.recetaId == recetaId }

    override suspend fun obtenerTrozos(recetaId: Long): Int? = obtenerRendimiento(recetaId)?.trozos

    override suspend fun usaMolde(recetaId: Long): Boolean? =
        obtenerRendimiento(recetaId)?.usaMolde

    override suspend fun obtenerPesoFinal(recetaId: Long): Double? =
        obtenerRendimiento(recetaId)?.pesoFinalG

    override suspend fun trozosDeVariasRecetas(recetaIds: List<Long>): List<TrozosDeReceta> =
        rendimientos.filter { it.recetaId in recetaIds }
            .map { TrozosDeReceta(it.recetaId, it.trozos) }

    override suspend fun insertarRendimiento(rendimiento: RecetaRendimiento) {
        rendimientos += rendimiento
        cambio()
    }

    override suspend fun actualizarRendimiento(rendimiento: RecetaRendimiento) {
        val posicion = rendimientos.indexOfFirst { it.recetaId == rendimiento.recetaId }
        if (posicion >= 0) rendimientos[posicion] = rendimiento
        cambio()
    }

    // --- Duración ---

    override suspend fun obtenerDuraciones(recetaId: Long): List<RecetaDuracion> =
        duraciones.filter { it.recetaId == recetaId }

    override fun observarDuraciones(recetaId: Long): Flow<List<RecetaDuracion>> =
        cambios.map { duraciones.filter { fila -> fila.recetaId == recetaId } }

    override suspend fun guardarDuracion(duracion: RecetaDuracion) {
        // El REPLACE real se apoya en la clave primaria (recetaId, tipo): volver a guardar
        // el mismo bloque lo pisa. Sin esto el falso acumularía filas y una prueba de
        // "cambiar la duración" pasaría con dos valores distintos guardados a la vez.
        duraciones.removeAll { it.recetaId == duracion.recetaId && it.tipo == duracion.tipo }
        duraciones += duracion
        // Y el aviso, que es lo que hace `observarDuraciones`. Faltaba, y el resumen se quedaba
        // mostrando la lista vacía del primer instante: Room reemite ante **cualquier** escritura
        // en la tabla, y un falso que no lo haga aprueba una pantalla que no se entera.
        cambio()
    }

    override suspend fun eliminarDuracion(recetaId: Long, tipo: TipoDuracion) {
        duraciones.removeAll { it.recetaId == recetaId && it.tipo == tipo }
        cambio()
    }

    // --- Precios ---

    override suspend fun obtenerPrecios(recetaId: Long): List<RecetaPrecio> =
        precios.filter { it.recetaId == recetaId }.sortedBy { it.id }

    override suspend fun preciosDeVariasRecetas(recetaIds: List<Long>): List<RecetaPrecio> =
        precios.filter { it.recetaId in recetaIds }.sortedWith(compareBy({ it.recetaId }, { it.id }))

    // Cuelga del contador `cambios`, la imitación del InvalidationTracker. Que sea un `Flow`
    // de verdad es lo que permite probar el paso de gastos: sus cifras dependen del costo y
    // de los trozos, que escriben otros dos pasos.
    override fun observarPrecios(recetaId: Long): Flow<List<RecetaPrecio>> =
        cambios.map { precios.filter { p -> p.recetaId == recetaId }.sortedBy { p -> p.id } }

    override suspend fun obtenerPrecioPorId(precioId: Long): RecetaPrecio? =
        precios.firstOrNull { it.id == precioId }

    override suspend fun insertarPrecio(precio: RecetaPrecio): Long {
        val id = nuevoId()
        precios += precio.copy(id = id)
        cambio()
        return id
    }

    override suspend fun actualizarPrecio(precio: RecetaPrecio) {
        val posicion = precios.indexOfFirst { it.id == precio.id }
        if (posicion >= 0) precios[posicion] = precio
        cambio()
    }

    override suspend fun eliminarPrecio(precioId: Long) {
        precios.removeAll { it.id == precioId }
        cambio()
    }

    override suspend fun quitarReferenciaATodos(recetaId: Long) {
        for (i in precios.indices) {
            if (precios[i].recetaId == recetaId) precios[i] = precios[i].copy(esReferencia = false)
        }
        cambio()
    }

    override suspend fun marcarComoReferencia(precioId: Long) {
        val posicion = precios.indexOfFirst { it.id == precioId }
        if (posicion >= 0) precios[posicion] = precios[posicion].copy(esReferencia = true)
        cambio()
    }

    // La base va por modo y no por receta entera, igual que en Room: apagarlas todas juntas
    // dejaría al otro modo sin la suya.
    override suspend fun quitarBaseAlModo(recetaId: Long, modo: ModoPrecio) {
        for (i in precios.indices) {
            if (precios[i].recetaId == recetaId && precios[i].modo == modo) {
                precios[i] = precios[i].copy(esBase = false)
            }
        }
        cambio()
    }

    override suspend fun marcarComoBase(precioId: Long) {
        val posicion = precios.indexOfFirst { it.id == precioId }
        if (posicion >= 0) precios[posicion] = precios[posicion].copy(esBase = true)
        cambio()
    }

    override suspend fun otroPrecioDeUnoEnElModo(
        recetaId: Long,
        modo: ModoPrecio,
        exceptoId: Long
    ): RecetaPrecio? = precios
        .filter {
            it.recetaId == recetaId && it.modo == modo && it.cantidad == 1 && it.id != exceptoId
        }
        // `ORDER BY id` como en la consulta real: el más antiguo, que es el que la receta
        // viene usando. Ordenar distinto acá aprobaría un ascenso que en el celular es otro.
        .minByOrNull { it.id }

    // --- Simulación de venta ---

    override suspend fun insertarSimulacionVenta(simulacion: RecetaSimulacionVenta) {
        simulaciones += simulacion
        // Avisa igual que `actualizarSimulacionVenta`: **insertar también es escribir**. Sin
        // esto, una receta que estrena su fila de simulación no aparecía en `observarSimulacionVenta`
        // hasta que otra escritura moviera el contador — un dato correcto que llegaba tarde y
        // por casualidad.
        cambio()
    }

    override suspend fun obtenerSimulacionVenta(recetaId: Long): RecetaSimulacionVenta? =
        simulaciones.firstOrNull { it.recetaId == recetaId }

    // Cuelga del contador `cambios`, como el resto de las consultas reactivas del falso.
    override fun observarSimulacionVenta(recetaId: Long): Flow<RecetaSimulacionVenta?> =
        cambios.map { simulaciones.firstOrNull { fila -> fila.recetaId == recetaId } }

    override suspend fun actualizarSimulacionVenta(simulacion: RecetaSimulacionVenta) {
        val posicion = simulaciones.indexOfFirst { it.recetaId == simulacion.recetaId }
        if (posicion >= 0) simulaciones[posicion] = simulacion
        // Después de escribir, no antes: avisar de un cambio que todavía no pasó haría que
        // quien reaccione lea la fila vieja.
        cambio()
    }

    // --- Pasos (8.8) ---

    private fun pasosDe(recetaId: Long) =
        pasos.filter { it.recetaId == recetaId }.sortedWith(compareBy({ it.orden }, { it.id }))

    override fun observarPasos(recetaId: Long): Flow<List<RecetaPaso>> =
        cambios.map { pasosDe(recetaId) }

    override suspend fun obtenerPasos(recetaId: Long): List<RecetaPaso> = pasosDe(recetaId)

    override suspend fun obtenerPaso(pasoId: Long): RecetaPaso? =
        pasos.firstOrNull { it.id == pasoId }

    override suspend fun insertarPaso(paso: RecetaPaso): Long {
        val id = nuevoId()
        pasos += paso.copy(id = id)
        cambio()
        return id
    }

    override suspend fun actualizarPaso(paso: RecetaPaso) {
        val posicion = pasos.indexOfFirst { it.id == paso.id }
        if (posicion >= 0) pasos[posicion] = paso
        cambio()
    }

    // El parámetro se llama distinto que el campo a propósito: con los dos como `pasos`, el
    // cuerpo lee el parámetro y funciona, pero basta mover una línea para que deje de hacerlo
    // en silencio.
    override suspend fun actualizarPasos(losQueCambian: List<RecetaPaso>) {
        losQueCambian.forEach { actualizarPaso(it) }
    }

    override suspend fun eliminarPaso(pasoId: Long) {
        pasos.removeAll { it.id == pasoId }
        cambio()
    }

    /**
     * El mayor `orden`, o `null` si no hay pasos.
     *
     * **`maxOfOrNull` y no `maxOf`**, y el `null` importa: con `0` no se distinguiría "no hay
     * pasos" de "hay uno en la posición 0", y quien lo llama suma 1 — el primer paso quedaría
     * en 1 en vez de 0. Es la misma trampa que tiene la consulta real, que devuelve `NULL`.
     */
    override suspend fun ultimoOrdenDePaso(recetaId: Long): Int? =
        pasos.filter { it.recetaId == recetaId }.maxOfOrNull { it.orden }

    override suspend fun pasosDeVariasRecetas(recetaIds: List<Long>): List<RecetaPaso> =
        pasos.filter { it.recetaId in recetaIds }
            .sortedWith(compareBy({ it.recetaId }, { it.orden }, { it.id }))

    override suspend fun eliminarPasosDeSecciones(seccionIds: List<Long>) {
        pasos.removeAll { it.tituloSeccionId in seccionIds }
        cambio()
    }

    // --- Partes: recetas que usan otras recetas (8.11) ---

    override suspend fun recetasQueUsanLaReceta(recetaId: Long): List<Receta> {
        val ids = secciones
            .filter { it.recetaOrigenId == recetaId && it.recetaId != recetaId }
            .map { it.recetaId }
            .toSet()
        return recetas.value.filter { it.id in ids }.sortedBy { it.titulo.lowercase() }
    }

    override suspend fun idsDeRecetasHechasDePartes(): List<Long> =
        secciones.filter { it.esTraida }.map { it.recetaId }.distinct()

    override suspend fun todasLasSeccionesTraidas(): List<RecetaSeccion> =
        secciones.filter { it.esTraida }.sortedWith(compareBy({ it.recetaId }, { it.orden }))

    /**
     * El latido, colgado del contador de cambios y no de un número que se pueda repetir.
     *
     * Es lo que hace la consulta real sin quererlo: Room reemite cada vez que se **invalida**
     * una tabla, aunque el `COUNT` dé lo mismo que antes. Acá `cambios` sube en cada escritura,
     * así que corregir un gramaje —que no mueve ningún contador— igual dispara el recálculo. Un
     * falso que devolviera el `COUNT` de verdad **no** avisaría en ese caso, y la prueba del
     * aviso pasaría sin probar nada.
     */
    override fun latidoDePartes(): Flow<Int> = cambios

    // --- Lo que todavía no hace falta ---

    private fun faltaImplementar(consulta: String): Nothing = throw NotImplementedError(
        "RecetaDaoFalso no implementa '$consulta' porque ninguna prueba lo necesita todavía. " +
            "Si llegaste hasta acá, impleméntalo con datos en memoria -- no lo hagas devolver " +
            "algo vacío para salir del paso, porque entonces la prueba no probaría nada."
    )

    override suspend fun obtenerTodasUnaVez(): List<Receta> =
        recetas.value.sortedBy { it.titulo.lowercase() }

    override suspend fun obtenerRecetasConMoldeOrigen(moldeId: Long): List<Receta> {
        val ids = rendimientos.filter { it.moldeOrigenId == moldeId }.map { it.recetaId }.toSet()
        return recetas.value.filter { it.id in ids }.sortedBy { it.titulo.lowercase() }
    }
}

/**
 * El falso de `EmpleadoDao`: los empleados y sus sueldos en memoria.
 *
 * Lo que sí hay que imitar de verdad son **las tres cosas que la base hace sola** y que una
 * prueba podría dar por buenas sin que nadie las escriba:
 *
 * 1. **El índice único sobre (empleadoId, recetaId)** con `REPLACE`: guardar dos veces el sueldo
 *    de la misma receta pisa el anterior en vez de dejar dos. Sin esto, una prueba de "cambiar el
 *    sueldo" pasaría aunque quedaran dos filas y las consultas devolvieran cualquiera.
 * 2. **Las cascadas**: borrar un empleado se lleva sus sueldos, su simulación y su detalle. Si el
 *    falso los dejara, la prueba de borrado aprobaría una versión de la app que deja huérfanos.
 * 3. **La condición `esGenerico = 0` del DELETE**, que es la última red del empleado estándar.
 *
 * El genérico se siembra en el constructor, igual que hace `SembrarDatosIniciales` al crear la
 * base: sin él, la garantía de "siempre presente" sería falsa desde la primera prueba.
 */
class EmpleadoDaoFalso : EmpleadoDao {

    private val filas = MutableStateFlow<List<Empleado>>(emptyList())
    private val sueldos = mutableListOf<EmpleadoRecetaSueldo>()
    private val simulaciones = mutableListOf<EmpleadoSimulacionMultiple>()
    private val detalles = mutableListOf<EmpleadoSimulacionMultipleDetalle>()
    private var siguienteId = 1L

    /** Lo que hace que las escrituras vuelvan a emitir, igual que en `RecetaDaoFalso`. */
    private val cambios = MutableStateFlow(0)

    private fun cambio() {
        cambios.value++
    }

    init {
        filas.value = listOf(Empleado(id = siguienteId++, nombre = "Estándar", esGenerico = true))
    }

    /** El genérico sembrado, que las pruebas necesitan a mano. */
    val generico: Empleado get() = filas.value.first { it.esGenerico }

    override fun observarTodos(): Flow<List<Empleado>> = filas.map { lista ->
        lista.sortedWith(compareByDescending<Empleado> { it.esGenerico }.thenBy { it.nombre.lowercase() })
    }

    override suspend fun obtener(empleadoId: Long): Empleado? =
        filas.value.firstOrNull { it.id == empleadoId }

    override suspend fun obtenerGenerico(): Empleado? = filas.value.firstOrNull { it.esGenerico }

    override suspend fun buscarPorNombre(nombre: String): Empleado? =
        filas.value.firstOrNull { it.nombre.equals(nombre, ignoreCase = true) }

    override suspend fun insertar(empleado: Empleado): Long {
        val id = siguienteId++
        filas.value = filas.value + empleado.copy(id = id)
        return id
    }

    override suspend fun actualizar(empleado: Empleado) {
        filas.value = filas.value.map { if (it.id == empleado.id) empleado else it }
    }

    override suspend fun eliminarPorId(empleadoId: Long) {
        // La condición del DELETE real: al genérico no se le puede ni por error.
        val victima = filas.value.firstOrNull { it.id == empleadoId && !it.esGenerico } ?: return
        filas.value = filas.value - victima
        // Las cascadas de las tres tablas que cuelgan de él.
        sueldos.removeAll { it.empleadoId == empleadoId }
        detalles.removeAll { it.empleadoId == empleadoId }
        simulaciones.removeAll { it.empleadoId == empleadoId }
        cambio()
    }

    /** Imita la cascada de borrar una **receta**, que se lleva sus sueldos y sus detalles (5.4). */
    fun alBorrarLaReceta(recetaId: Long) {
        sueldos.removeAll { it.recetaId == recetaId }
        detalles.removeAll { it.recetaId == recetaId }
        cambio()
    }

    override suspend fun obtenerSueldos(empleadoId: Long): List<EmpleadoRecetaSueldo> =
        sueldos.filter { it.empleadoId == empleadoId }

    override fun observarSueldos(empleadoId: Long): Flow<List<EmpleadoRecetaSueldo>> =
        cambios.map { sueldos.filter { fila -> fila.empleadoId == empleadoId } }

    override suspend fun obtenerSueldo(empleadoId: Long, recetaId: Long): EmpleadoRecetaSueldo? =
        sueldos.firstOrNull { it.empleadoId == empleadoId && it.recetaId == recetaId }

    override suspend fun guardarSueldo(sueldo: EmpleadoRecetaSueldo): Long {
        // El índice único con REPLACE: el par (empleado, receta) manda, no el id.
        sueldos.removeAll { it.empleadoId == sueldo.empleadoId && it.recetaId == sueldo.recetaId }
        val id = if (sueldo.id == 0L) siguienteId++ else sueldo.id
        sueldos += sueldo.copy(id = id)
        cambio()
        return id
    }

    override suspend fun eliminarSueldo(sueldoId: Long) {
        sueldos.removeAll { it.id == sueldoId }
        cambio()
    }

    override suspend fun obtenerSimulacionMultiple(empleadoId: Long): EmpleadoSimulacionMultiple? =
        simulaciones.firstOrNull { it.empleadoId == empleadoId }

    override suspend fun obtenerDiasCompartidos(empleadoId: Long): Int? =
        obtenerSimulacionMultiple(empleadoId)?.diasPorSemana

    override suspend fun guardarSimulacionMultiple(simulacion: EmpleadoSimulacionMultiple) {
        simulaciones.removeAll { it.empleadoId == simulacion.empleadoId }
        simulaciones += simulacion
        cambio()
    }

    override suspend fun obtenerDetalle(empleadoId: Long): List<EmpleadoSimulacionMultipleDetalle> =
        detalles.filter { it.empleadoId == empleadoId }

    override fun observarDetalle(empleadoId: Long): Flow<List<EmpleadoSimulacionMultipleDetalle>> =
        cambios.map { detalles.filter { fila -> fila.empleadoId == empleadoId } }

    override suspend fun guardarDetalle(detalle: EmpleadoSimulacionMultipleDetalle): Long {
        // La clave foránea real apunta a la fila de días, no al empleado: sin ella, la base
        // rechazaría el detalle. Acá se comprueba igual para que la prueba lo note.
        require(simulaciones.any { it.empleadoId == detalle.empleadoId }) {
            "FOREIGN KEY constraint failed: falta la fila de empleado_simulacion_multiple"
        }
        detalles.removeAll { it.empleadoId == detalle.empleadoId && it.recetaId == detalle.recetaId }
        val id = if (detalle.id == 0L) siguienteId++ else detalle.id
        detalles += detalle.copy(id = id)
        cambio()
        return id
    }

    override suspend fun eliminarDetalle(detalleId: Long) {
        detalles.removeAll { it.id == detalleId }
        cambio()
    }
}
