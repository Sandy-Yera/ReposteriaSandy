package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.IngredienteDao
import com.sandyyera.reposteria.data.db.dao.RecetaDao
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.logica.busqueda.sonElMismoTexto
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreEscrito
import com.sandyyera.reposteria.logica.validaciones.errorEnValorPorGramo
import kotlinx.coroutines.flow.Flow

/**
 * Cómo terminó un intento de guardar un ingrediente.
 *
 * Es un tipo cerrado y no un simple `Long` para que la pantalla **no pueda ignorar** los
 * dos casos que no son éxito. Si `crear` devolviera solo el id, olvidar comprobar el
 * nombre repetido haría que la app se cerrara: la base tiene un índice único y el intento
 * de insertar lanzaría una excepción. Así el compilador obliga a decidir qué mostrar.
 */
sealed interface ResultadoGuardarIngrediente {
    data class Guardado(val id: Long) : ResultadoGuardarIngrediente

    /** Ya hay uno que se llama igual. Se devuelve para poder ofrecer editarlo. */
    data class YaExiste(val existente: Ingrediente) : ResultadoGuardarIngrediente

    /** El dato no pasó la validación. [motivo] es el texto a mostrar bajo el campo. */
    data class NoValido(val motivo: String) : ResultadoGuardarIngrediente
}

class IngredienteRepositorio(
    private val dao: IngredienteDao,
    private val recetaDao: RecetaDao,
    private val historial: HistorialRepositorio
) {

    fun observarTodos(): Flow<List<Ingrediente>> = dao.observarTodos()

    suspend fun obtener(ingredienteId: Long): Ingrediente? = dao.obtener(ingredienteId)

    /**
     * Busca un ingrediente que se llame igual que [nombre], ignorando tildes y mayúsculas.
     *
     * [exceptoId] sirve al editar: para no decir que un ingrediente choca consigo mismo.
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

    /**
     * Crea un ingrediente, comprobando antes que el dato sirva y que no esté repetido.
     *
     * Las dos comprobaciones van acá y no en la pantalla porque hay dos formas de llegar
     * a crear un ingrediente —el catálogo y el alta rápida desde una receta— y ambas
     * tienen que comportarse igual.
     */
    suspend fun crear(nombre: String, valorPorGramo: Double): ResultadoGuardarIngrediente {
        val limpio = nombre.trim()

        errorEnNombreEscrito(limpio)?.let { return ResultadoGuardarIngrediente.NoValido(it) }
        errorEnValorPorGramo(valorPorGramo)?.let { return ResultadoGuardarIngrediente.NoValido(it) }
        buscarParecido(limpio)?.let { return ResultadoGuardarIngrediente.YaExiste(it) }

        val id = dao.insertar(Ingrediente(nombre = limpio, valorPorGramo = valorPorGramo))
        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.INGREDIENTE,
            descripcion = "Se creó el ingrediente '$limpio'"
        )
        return ResultadoGuardarIngrediente.Guardado(id)
    }

    /**
     * Guarda los cambios de un ingrediente que ya existe.
     *
     * Comprueba lo mismo que [crear], salvo que al buscar repetidos se excluye a sí mismo:
     * cambiarle solo el precio a "Harina" no debe chocar con "Harina".
     */
    suspend fun actualizar(ingrediente: Ingrediente): ResultadoGuardarIngrediente {
        val limpio = ingrediente.nombre.trim()

        errorEnNombreEscrito(limpio)?.let { return ResultadoGuardarIngrediente.NoValido(it) }
        errorEnValorPorGramo(ingrediente.valorPorGramo)
            ?.let { return ResultadoGuardarIngrediente.NoValido(it) }
        buscarParecido(limpio, exceptoId = ingrediente.id)
            ?.let { return ResultadoGuardarIngrediente.YaExiste(it) }

        dao.actualizar(
            ingrediente.copy(nombre = limpio, actualizadoEn = System.currentTimeMillis())
        )
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.INGREDIENTE,
            descripcion = "Se editó el ingrediente '$limpio'"
        )
        return ResultadoGuardarIngrediente.Guardado(ingrediente.id)
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
