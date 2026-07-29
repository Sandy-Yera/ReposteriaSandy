package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.db.dao.CostoDeReceta
import com.sandyyera.reposteria.data.db.dao.HistorialDao
import com.sandyyera.reposteria.data.db.dao.IngredienteDao
import com.sandyyera.reposteria.data.db.dao.RecetaDao
import com.sandyyera.reposteria.data.db.dao.TrozosDeReceta
import com.sandyyera.reposteria.data.db.entidades.EventoCambio
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.RecetaIngrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.db.entidades.RecetaRendimiento
import com.sandyyera.reposteria.data.db.entidades.RecetaSeccion
import com.sandyyera.reposteria.data.db.entidades.RecetaSimulacionVenta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** Deja ingredientes puestos de entrada, sin pasar por las comprobaciones. */
    fun sembrar(vararg ingredientes: Ingrediente) {
        filas.value = filas.value + ingredientes.map {
            if (it.id == 0L) it.copy(id = siguienteId++) else it
        }
    }

    private fun ordenados(lista: List<Ingrediente>) = lista.sortedBy { it.nombre.lowercase() }

    override fun observarTodos(): Flow<List<Ingrediente>> = filas.map(::ordenados)

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

    private var siguienteId = 1L
    private fun nuevoId() = siguienteId++

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
        recetas.value = recetas.value.filterNot { it.id == recetaId }
    }

    // --- Costo ---

    override suspend fun costoTotalReceta(recetaId: Long): Double =
        itemsDe(recetaId).sumOf { item ->
            // INNER JOIN: si el ingrediente ya no existe, esa fila simplemente no suma.
            val valor = catalogo.obtener(item.ingredienteId)?.valorPorGramo ?: return@sumOf 0.0
            item.cantidadG * valor
        }

    override suspend fun costoDeVariasRecetas(recetaIds: List<Long>): List<CostoDeReceta> =
        recetaIds
            // El GROUP BY real no devuelve fila para una receta sin ingredientes.
            .filter { itemsDe(it).isNotEmpty() }
            .map { CostoDeReceta(it, costoTotalReceta(it)) }

    // --- Consultas que cruzan tablas ---

    override suspend fun obtenerRecetasQueUsan(ingredienteId: Long): List<Receta> {
        val seccionesUsadas = items.filter { it.ingredienteId == ingredienteId }
            .map { it.seccionId }.toSet()
        val ids = secciones.filter { it.id in seccionesUsadas }.map { it.recetaId }.toSet()
        return recetas.value.filter { it.id in ids }.sortedBy { it.titulo.lowercase() }
    }

    override suspend fun quitarIngredienteDeTodasLasSecciones(ingredienteId: Long) {
        items.removeAll { it.ingredienteId == ingredienteId }
    }

    // --- Secciones e ingredientes ---

    override suspend fun obtenerSecciones(recetaId: Long): List<RecetaSeccion> =
        secciones.filter { it.recetaId == recetaId }.sortedBy { it.orden }

    override suspend fun contarSecciones(recetaId: Long): Int =
        secciones.count { it.recetaId == recetaId }

    override suspend fun insertarSeccion(seccion: RecetaSeccion): Long {
        val id = nuevoId()
        secciones += seccion.copy(id = id)
        return id
    }

    override suspend fun actualizarSeccion(seccion: RecetaSeccion) {
        val posicion = secciones.indexOfFirst { it.id == seccion.id }
        if (posicion >= 0) secciones[posicion] = seccion
    }

    override suspend fun eliminarSeccion(seccionId: Long) {
        items.removeAll { it.seccionId == seccionId }   // cascada
        secciones.removeAll { it.id == seccionId }
    }

    override suspend fun obtenerTodosLosIngredientes(recetaId: Long): List<RecetaIngrediente> {
        val orden = secciones.filter { it.recetaId == recetaId }.associate { it.id to it.orden }
        return itemsDe(recetaId).sortedWith(
            compareBy({ orden[it.seccionId] ?: 0 }, { it.orden })
        )
    }

    override suspend fun sumaGramosIngredientes(recetaId: Long): Double =
        itemsDe(recetaId).sumOf { it.cantidadG }

    override suspend fun insertarIngrediente(item: RecetaIngrediente): Long {
        val id = nuevoId()
        items += item.copy(id = id)
        return id
    }

    override suspend fun actualizarCantidad(itemId: Long, cantidad: Double) {
        val posicion = items.indexOfFirst { it.id == itemId }
        if (posicion >= 0) items[posicion] = items[posicion].copy(cantidadG = cantidad)
    }

    override suspend fun eliminarIngrediente(itemId: Long) {
        items.removeAll { it.id == itemId }
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
    }

    override suspend fun actualizarRendimiento(rendimiento: RecetaRendimiento) {
        val posicion = rendimientos.indexOfFirst { it.recetaId == rendimiento.recetaId }
        if (posicion >= 0) rendimientos[posicion] = rendimiento
    }

    // --- Precios ---

    override suspend fun obtenerPrecios(recetaId: Long): List<RecetaPrecio> =
        precios.filter { it.recetaId == recetaId }.sortedBy { it.id }

    override suspend fun preciosDeVariasRecetas(recetaIds: List<Long>): List<RecetaPrecio> =
        precios.filter { it.recetaId in recetaIds }.sortedWith(compareBy({ it.recetaId }, { it.id }))

    override suspend fun insertarPrecio(precio: RecetaPrecio): Long {
        val id = nuevoId()
        precios += precio.copy(id = id)
        return id
    }

    override suspend fun actualizarPrecio(precio: RecetaPrecio) {
        val posicion = precios.indexOfFirst { it.id == precio.id }
        if (posicion >= 0) precios[posicion] = precio
    }

    override suspend fun eliminarPrecio(precioId: Long) {
        precios.removeAll { it.id == precioId }
    }

    override suspend fun quitarReferenciaATodos(recetaId: Long) {
        for (i in precios.indices) {
            if (precios[i].recetaId == recetaId) precios[i] = precios[i].copy(esReferencia = false)
        }
    }

    override suspend fun marcarComoReferencia(precioId: Long) {
        val posicion = precios.indexOfFirst { it.id == precioId }
        if (posicion >= 0) precios[posicion] = precios[posicion].copy(esReferencia = true)
    }

    // --- Simulación de venta ---

    override suspend fun insertarSimulacionVenta(simulacion: RecetaSimulacionVenta) {
        simulaciones += simulacion
    }

    override suspend fun obtenerSimulacionVenta(recetaId: Long): RecetaSimulacionVenta? =
        simulaciones.firstOrNull { it.recetaId == recetaId }

    override suspend fun actualizarSimulacionVenta(simulacion: RecetaSimulacionVenta) {
        val posicion = simulaciones.indexOfFirst { it.recetaId == simulacion.recetaId }
        if (posicion >= 0) simulaciones[posicion] = simulacion
    }

    // --- Lo que todavía no hace falta ---

    private fun faltaImplementar(consulta: String): Nothing = throw NotImplementedError(
        "RecetaDaoFalso no implementa '$consulta' porque ninguna prueba lo necesita todavía. " +
            "Si llegaste hasta acá, impleméntalo con datos en memoria -- no lo hagas devolver " +
            "algo vacío para salir del paso, porque entonces la prueba no probaría nada."
    )

    override suspend fun obtenerRecetasConMoldeOrigen(moldeId: Long): List<Receta> =
        faltaImplementar("obtenerRecetasConMoldeOrigen")
}
