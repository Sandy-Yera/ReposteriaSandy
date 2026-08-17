package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.HistorialDao
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.EventoCambio
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import kotlinx.coroutines.flow.Flow

/** Seis meses en milisegundos: cuánto se guardan los eventos antes de borrarse solos. */
const val RETENCION_HISTORIAL_MS = 180L * 24 * 60 * 60 * 1000

class HistorialRepositorio(private val dao: HistorialDao) {

    fun observarTodos(): Flow<List<EventoCambio>> = dao.observarTodos()

    /**
     * Anota un cambio en el historial.
     *
     * [descripcion] siempre debe nombrar lo afectado ("Se eliminó el ingrediente
     * 'Harina'"), nunca un texto genérico: si no, el historial no sirve para saber qué
     * cambió. Eso obliga a leer el nombre **antes** de borrar la fila.
     *
     * [detalleAdicional] es para los efectos en cadena, como a qué recetas afectó borrar
     * un ingrediente.
     *
     * Aprovecha la llamada para borrar lo más viejo que la retención, así la limpieza no
     * necesita un proceso aparte.
     */
    suspend fun registrar(
        tipo: TipoEvento,
        entidad: EntidadEvento,
        descripcion: String,
        detalleAdicional: String? = null
    ) {
        val ahora = System.currentTimeMillis()
        dao.insertar(
            EventoCambio(
                tipo = tipo,
                entidad = entidad,
                descripcion = descripcion,
                detalleAdicional = detalleAdicional,
                creadoEn = ahora
            )
        )
        dao.borrarAnterioresA(ahora - RETENCION_HISTORIAL_MS)
    }
}
