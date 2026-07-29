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
 * El falso de `RecetaDao`.
 *
 * Solo implementa lo que hoy usa `IngredienteRepositorio`: qué recetas usan un ingrediente
 * y cómo sacarlo de todas ellas. El resto falla con un mensaje que dice qué hacer.
 *
 * Es a propósito. Implementar las 36 consultas "por si acaso" sería escribir una segunda
 * base de datos que nadie ejecuta, y equivocarse ahí daría pruebas que aprueban código
 * roto. Cuando la Fase 3 necesite más, se agrega ahí, con una prueba que lo use.
 */
class RecetaDaoFalso : RecetaDao {

    private val usos = mutableMapOf<Long, MutableList<Receta>>()

    /** Declara que [receta] usa el ingrediente [ingredienteId]. */
    fun declararUso(ingredienteId: Long, receta: Receta) {
        usos.getOrPut(ingredienteId) { mutableListOf() } += receta
    }

    override suspend fun obtenerRecetasQueUsan(ingredienteId: Long): List<Receta> =
        usos[ingredienteId].orEmpty().sortedBy { it.titulo.lowercase() }

    override suspend fun quitarIngredienteDeTodasLasSecciones(ingredienteId: Long) {
        usos.remove(ingredienteId)
    }

    // --- Todo lo demás: sin implementar todavía ---

    private fun faltaImplementar(consulta: String): Nothing = throw NotImplementedError(
        "RecetaDaoFalso no implementa '$consulta' porque ninguna prueba lo necesita todavía. " +
            "Si llegaste hasta acá, impleméntalo con datos en memoria -- no lo hagas devolver " +
            "algo vacío para salir del paso, porque entonces la prueba no probaría nada."
    )

    override fun observarTodas(): Flow<List<Receta>> = faltaImplementar("observarTodas")
    override suspend fun obtener(recetaId: Long): Receta? = faltaImplementar("obtener")
    override suspend fun insertar(receta: Receta): Long = faltaImplementar("insertar")
    override suspend fun actualizar(receta: Receta) = faltaImplementar("actualizar")
    override suspend fun eliminarPorId(recetaId: Long) = faltaImplementar("eliminarPorId")
    override suspend fun costoTotalReceta(recetaId: Long): Double =
        faltaImplementar("costoTotalReceta")
    override suspend fun costoDeVariasRecetas(recetaIds: List<Long>): List<CostoDeReceta> =
        faltaImplementar("costoDeVariasRecetas")
    override suspend fun obtenerRecetasConMoldeOrigen(moldeId: Long): List<Receta> =
        faltaImplementar("obtenerRecetasConMoldeOrigen")
    override suspend fun obtenerSecciones(recetaId: Long): List<RecetaSeccion> =
        faltaImplementar("obtenerSecciones")
    override suspend fun contarSecciones(recetaId: Long): Int =
        faltaImplementar("contarSecciones")
    override suspend fun insertarSeccion(seccion: RecetaSeccion): Long =
        faltaImplementar("insertarSeccion")
    override suspend fun actualizarSeccion(seccion: RecetaSeccion) =
        faltaImplementar("actualizarSeccion")
    override suspend fun eliminarSeccion(seccionId: Long) = faltaImplementar("eliminarSeccion")
    override suspend fun obtenerTodosLosIngredientes(recetaId: Long): List<RecetaIngrediente> =
        faltaImplementar("obtenerTodosLosIngredientes")
    override suspend fun sumaGramosIngredientes(recetaId: Long): Double =
        faltaImplementar("sumaGramosIngredientes")
    override suspend fun insertarIngrediente(item: RecetaIngrediente): Long =
        faltaImplementar("insertarIngrediente")
    override suspend fun actualizarCantidad(itemId: Long, cantidad: Double) =
        faltaImplementar("actualizarCantidad")
    override suspend fun eliminarIngrediente(itemId: Long) =
        faltaImplementar("eliminarIngrediente")
    override suspend fun obtenerRendimiento(recetaId: Long): RecetaRendimiento? =
        faltaImplementar("obtenerRendimiento")
    override suspend fun obtenerTrozos(recetaId: Long): Int? = faltaImplementar("obtenerTrozos")
    override suspend fun usaMolde(recetaId: Long): Boolean? = faltaImplementar("usaMolde")
    override suspend fun obtenerPesoFinal(recetaId: Long): Double? =
        faltaImplementar("obtenerPesoFinal")
    override suspend fun trozosDeVariasRecetas(recetaIds: List<Long>): List<TrozosDeReceta> =
        faltaImplementar("trozosDeVariasRecetas")
    override suspend fun insertarRendimiento(rendimiento: RecetaRendimiento) =
        faltaImplementar("insertarRendimiento")
    override suspend fun actualizarRendimiento(rendimiento: RecetaRendimiento) =
        faltaImplementar("actualizarRendimiento")
    override suspend fun obtenerPrecios(recetaId: Long): List<RecetaPrecio> =
        faltaImplementar("obtenerPrecios")
    override suspend fun preciosDeVariasRecetas(recetaIds: List<Long>): List<RecetaPrecio> =
        faltaImplementar("preciosDeVariasRecetas")
    override suspend fun insertarPrecio(precio: RecetaPrecio): Long =
        faltaImplementar("insertarPrecio")
    override suspend fun actualizarPrecio(precio: RecetaPrecio) =
        faltaImplementar("actualizarPrecio")
    override suspend fun eliminarPrecio(precioId: Long) = faltaImplementar("eliminarPrecio")
    override suspend fun quitarReferenciaATodos(recetaId: Long) =
        faltaImplementar("quitarReferenciaATodos")
    override suspend fun marcarComoReferencia(precioId: Long) =
        faltaImplementar("marcarComoReferencia")
    override suspend fun insertarSimulacionVenta(simulacion: RecetaSimulacionVenta) =
        faltaImplementar("insertarSimulacionVenta")
    override suspend fun obtenerSimulacionVenta(recetaId: Long): RecetaSimulacionVenta? =
        faltaImplementar("obtenerSimulacionVenta")
    override suspend fun actualizarSimulacionVenta(simulacion: RecetaSimulacionVenta) =
        faltaImplementar("actualizarSimulacionVenta")
}
