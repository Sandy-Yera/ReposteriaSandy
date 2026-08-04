package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.MoldeDao
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.Molde
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.logica.busqueda.sonElMismoTexto
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.validaciones.CampoDeMolde
import com.sandyyera.reposteria.logica.validaciones.ErroresMolde
import com.sandyyera.reposteria.logica.validaciones.dimensionesDesde
import com.sandyyera.reposteria.logica.moldes.FormaDelCorte
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import com.sandyyera.reposteria.logica.validaciones.revisarMolde
import kotlinx.coroutines.flow.Flow

/**
 * Cómo terminó un intento de guardar un molde.
 *
 * Misma idea que `ResultadoGuardarIngrediente`: un tipo cerrado para que la pantalla no
 * pueda ignorar los casos que no son éxito. [NoValido] devuelve los errores **por campo**
 * y no un texto suelto, porque un molde tiene hasta cuatro campos que pueden fallar a la
 * vez y cada aviso va bajo el suyo.
 */
sealed interface ResultadoGuardarMolde {
    data class Guardado(val id: Long) : ResultadoGuardarMolde

    /** Ya hay uno que se llama igual. Se devuelve para poder ofrecer abrirlo. */
    data class YaExiste(val existente: Molde) : ResultadoGuardarMolde

    data class NoValido(val errores: ErroresMolde) : ResultadoGuardarMolde
}

/**
 * El catálogo de moldes (9.2).
 *
 * Depende de [RecetaRepositorio] y no del DAO de recetas, a diferencia de
 * `IngredienteRepositorio`. La razón es que acá hay que **escribir** en las recetas —
 * propagarles una corrección de medidas—, y quién puede escribir en el rendimiento de una
 * receta es cosa de ese repositorio, no de este. Leer prestado un DAO ajeno para escribir
 * es cómo terminan existiendo dos lugares que modifican la misma tabla con reglas
 * distintas.
 */
class MoldeRepositorio(
    private val dao: MoldeDao,
    private val recetas: RecetaRepositorio,
    private val historial: HistorialRepositorio
) {

    fun observarTodos(): Flow<List<Molde>> = dao.observarTodos()

    suspend fun obtener(moldeId: Long): Molde? = dao.obtener(moldeId)

    /**
     * Busca un molde que se llame igual, ignorando mayúsculas y tildes.
     *
     * [exceptoId] sirve al editar, para que un molde no choque consigo mismo. Igual que en
     * ingredientes, la comparación se hace en memoria porque SQLite no sabe ignorar tildes.
     *
     * **No hay índice único en la tabla**, a diferencia de ingredientes: el catálogo de
     * moldes nace en esta fase, pero la regla vive acá por coherencia con recetas y
     * secciones, y para no tener que migrar el día que aparezca un repetido de otro origen.
     */
    suspend fun buscarParecido(nombre: String, exceptoId: Long? = null): Molde? =
        dao.obtenerTodosUnaVez()
            .firstOrNull { it.id != exceptoId && sonElMismoTexto(it.nombre, nombre) }

    /**
     * Crea un molde, revisando antes que sirva y que no esté repetido.
     *
     * Las comprobaciones van acá y no en la pantalla por lo mismo que en ingredientes: al
     * llegar la Fase 5 habrá una segunda forma de definir un molde (el "modo prueba" del
     * reescalado, 9.3) y las dos tienen que comportarse igual.
     */
    suspend fun crear(
        nombre: String,
        forma: TipoFormaMolde?,
        medidas: Map<CampoDeMolde, String>,
        corte: FormaDelCorte? = null,
        largoDeCorteTexto: String = "",
        anchoDeCorteTexto: String = ""
    ): ResultadoGuardarMolde {
        val limpio = nombre.trim()
        val errores = revisarMolde(limpio, forma, medidas, largoDeCorteTexto, anchoDeCorteTexto)
        if (!errores.sirve) return ResultadoGuardarMolde.NoValido(errores)

        buscarParecido(limpio)?.let { return ResultadoGuardarMolde.YaExiste(it) }

        // No puede ser null: `revisarMolde` ya aprobó las mismas medidas, y hay una prueba
        // en `logica/` de que todo lo que aprueba se puede convertir.
        // El corte se pega **después** de armar las dimensiones y con un `copy`, no
        // dentro de `dimensionesDesde`: así queda a la vista que no participa del área ni del
        // volumen, que son los que mueven el reescalado (9.4).
        val dimensiones = dimensionesDesde(forma, medidas)?.copy(
            formaDelCorte = corte,
            largoDeCorteCm = textoANumero(largoDeCorteTexto),
            anchoDeCorteCm = textoANumero(anchoDeCorteTexto)
        )
            ?: return ResultadoGuardarMolde.NoValido(errores)

        val id = dao.insertar(Molde(nombre = limpio, dimensiones = dimensiones))
        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.MOLDE,
            descripcion = "Se creó el molde '$limpio'"
        )
        return ResultadoGuardarMolde.Guardado(id)
    }

    /**
     * Corrige un molde del catálogo **y propaga la corrección a las recetas enlazadas**.
     *
     * Esto es para arreglar una medida mal tomada, no para cambiar de molde: por eso las
     * cantidades de ingredientes de esas recetas **no se tocan**. Reescalar es otra cosa y
     * vive en la Fase 5 (9.3).
     *
     * Las recetas que ya no están enlazadas —porque se reescalaron a un molde de prueba, o
     * porque este molde se borró y se volvió a crear— no reciben nada: conservan las medidas
     * que tenían, que es el comportamiento de 5.2.
     */
    suspend fun actualizar(
        moldeId: Long,
        nombre: String,
        forma: TipoFormaMolde?,
        medidas: Map<CampoDeMolde, String>,
        corte: FormaDelCorte? = null,
        largoDeCorteTexto: String = "",
        anchoDeCorteTexto: String = ""
    ): ResultadoGuardarMolde {
        val limpio = nombre.trim()
        val errores = revisarMolde(limpio, forma, medidas, largoDeCorteTexto, anchoDeCorteTexto)
        if (!errores.sirve) return ResultadoGuardarMolde.NoValido(errores)

        buscarParecido(limpio, exceptoId = moldeId)?.let {
            return ResultadoGuardarMolde.YaExiste(it)
        }

        val actual = dao.obtener(moldeId)
            ?: return ResultadoGuardarMolde.NoValido(
                errores.copy(nombre = "Ese molde ya no existe")
            )
        // El corte se pega **después** de armar las dimensiones y con un `copy`, no
        // dentro de `dimensionesDesde`: así queda a la vista que no participa del área ni del
        // volumen, que son los que mueven el reescalado (9.4).
        val dimensiones = dimensionesDesde(forma, medidas)?.copy(
            formaDelCorte = corte,
            largoDeCorteCm = textoANumero(largoDeCorteTexto),
            anchoDeCorteCm = textoANumero(anchoDeCorteTexto)
        )
            ?: return ResultadoGuardarMolde.NoValido(errores)

        dao.actualizar(
            actual.copy(
                nombre = limpio,
                dimensiones = dimensiones,
                actualizadoEn = System.currentTimeMillis()
            )
        )

        val enlazadas = recetas.obtenerRecetasConMoldeOrigen(moldeId)
        enlazadas.forEach { recetas.actualizarDimensionesMolde(it.id, dimensiones) }

        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.MOLDE,
            descripcion = "Se editó el molde '$limpio'",
            detalleAdicional = detalleDeRecetas("Se actualizaron", enlazadas)
        )
        return ResultadoGuardarMolde.Guardado(moldeId)
    }

    /**
     * Qué recetas usan este molde, para la advertencia previa a borrarlo (6.3).
     *
     * A diferencia de un ingrediente, borrar un molde **no rompe** esas recetas: pierden el
     * vínculo pero conservan sus medidas. Aun así se avisa, porque desde ese momento dejan
     * de recibir correcciones y descubrirlo meses después no tendría explicación.
     */
    suspend fun recetasAfectadasPorBorrar(moldeId: Long): List<Receta> =
        recetas.obtenerRecetasConMoldeOrigen(moldeId)

    /**
     * Borra el molde, una vez que la pantalla ya confirmó.
     *
     * Lee el nombre y las recetas afectadas **antes** de borrar: después la clave foránea
     * ya puso sus `moldeOrigenId` en `null` y no habría forma de nombrarlas en el historial.
     * No vuelve a preguntar; la confirmación es de la pantalla (6.3).
     */
    suspend fun confirmarEliminacion(moldeId: Long) {
        val molde = dao.obtener(moldeId) ?: return
        val enlazadas = recetas.obtenerRecetasConMoldeOrigen(moldeId)

        dao.eliminarPorId(moldeId)

        historial.registrar(
            tipo = TipoEvento.ELIMINACION,
            entidad = EntidadEvento.MOLDE,
            descripcion = "Se eliminó el molde '${molde.nombre}'",
            detalleAdicional = detalleDeRecetas("Quedaron sin vínculo", enlazadas)
        )
    }

    private fun detalleDeRecetas(que: String, recetas: List<Receta>): String? =
        recetas.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.titulo }
            ?.let { "$que: $it" }
}
