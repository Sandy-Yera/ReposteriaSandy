package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.IngredienteDao
import com.sandyyera.reposteria.data.db.dao.RecetaDao
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.logica.busqueda.sonElMismoTexto
import kotlinx.coroutines.flow.Flow

class IngredienteRepositorio(
    private val dao: IngredienteDao,
    private val recetaDao: RecetaDao,
    private val historial: HistorialRepositorio
) {

    fun observarTodos(): Flow<List<Ingrediente>> = dao.observarTodos()

    suspend fun obtener(ingredienteId: Long): Ingrediente? = dao.obtener(ingredienteId)

    /**
     * Busca un ingrediente que se llame parecido a [nombre].
     *
     * La base ya impide repetir el nombre exacto sin distinguir mayúsculas, pero no sabe
     * de tildes: para ella "azucar" y "azúcar" son distintos. Acá se compara ignorando
     * también las tildes, para poder avisar antes de crear un duplicado disfrazado.
     *
     * Devuelve `null` si no hay ninguno parecido.
     */
    suspend fun buscarParecido(nombre: String, exceptoId: Long? = null): Ingrediente? =
        // La comparación sin tildes no la puede hacer SQLite, así que se trae la lista y
        // se compara en memoria. Con un catálogo de ingredientes personales son pocas
        // filas y no pesa. No se consulta antes por nombre exacto porque sería redundante:
        // cualquier cosa que encontrara esa consulta la encuentra también esta.
        dao.obtenerTodosUnaVez()
            .firstOrNull { it.id != exceptoId && sonElMismoTexto(it.nombre, nombre) }

    suspend fun crear(nombre: String, valorPorGramo: Double): Long {
        val id = dao.insertar(Ingrediente(nombre = nombre.trim(), valorPorGramo = valorPorGramo))
        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.INGREDIENTE,
            descripcion = "Se creó el ingrediente '${nombre.trim()}'"
        )
        return id
    }

    suspend fun actualizar(ingrediente: Ingrediente) {
        dao.actualizar(ingrediente.copy(actualizadoEn = System.currentTimeMillis()))
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.INGREDIENTE,
            descripcion = "Se editó el ingrediente '${ingrediente.nombre}'"
        )
    }

    /**
     * Qué recetas usan este ingrediente.
     *
     * La pantalla **debe** llamar esto antes de ofrecer el borrado definitivo, y mostrar
     * la lista en la advertencia. Si viene vacía, igual se pide confirmación, pero sin
     * listado.
     */
    suspend fun recetasAfectadasPorBorrar(ingredienteId: Long): List<Receta> =
        recetaDao.obtenerRecetasQueUsan(ingredienteId)

    /**
     * Borra el ingrediente de verdad, cuando el usuario ya confirmó la advertencia.
     *
     * Lee el nombre y las recetas afectadas **antes** de borrar nada, porque después ya
     * no se pueden consultar y el historial los necesita. Luego quita las filas que lo
     * referencian —no hay clave foránea que lo haga solo— y recién ahí borra la fila.
     *
     * El costo de las recetas afectadas se reajusta por su cuenta: se calcula en vivo,
     * así que al desaparecer el ingrediente simplemente deja de sumar.
     *
     * No vuelve a preguntar: la confirmación es responsabilidad de la pantalla.
     */
    suspend fun confirmarEliminacion(ingredienteId: Long) {
        val ingrediente = dao.obtener(ingredienteId) ?: return
        val afectadas = recetaDao.obtenerRecetasQueUsan(ingredienteId)

        recetaDao.quitarIngredienteDeTodasLasSecciones(ingredienteId)
        dao.eliminarPorId(ingredienteId)

        historial.registrar(
            tipo = TipoEvento.ELIMINACION,
            entidad = EntidadEvento.INGREDIENTE,
            descripcion = "Se eliminó el ingrediente '${ingrediente.nombre}'",
            detalleAdicional = if (afectadas.isEmpty()) null
            else "Afectó a: ${afectadas.joinToString { it.titulo }}"
        )
    }
}
