package com.sandyyera.reposteria.data.db.entidades

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Qué clase de cambio fue. Define el color con que se muestra en el historial. */
enum class TipoEvento {
    /** Azul. */
    CREACION,

    /** Verde. */
    EDICION,

    /** Rojo. */
    ELIMINACION
}

/** Sobre qué se hizo el cambio. */
enum class EntidadEvento { INGREDIENTE, RECETA, MOLDE, EMPLEADO }

/**
 * Una anotación en el historial de cambios.
 *
 * `descripcion` siempre nombra lo afectado ("Se eliminó el ingrediente 'Harina'"), nunca
 * un texto genérico: si no, el historial no sirve para saber qué cambió. Eso obliga a
 * leer el nombre antes de borrar la fila.
 *
 * `detalleAdicional` guarda los efectos en cadena, como a qué recetas afectó borrar un
 * ingrediente.
 *
 * Las filas más viejas que seis meses se borran solas: es un registro de lo reciente, no
 * un archivo permanente, y todo esto viaja dentro del respaldo que sube a Drive.
 */
@Entity(tableName = "eventos_cambio", indices = [Index("creadoEn")])
data class EventoCambio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: TipoEvento,
    val entidad: EntidadEvento,
    val descripcion: String,
    val detalleAdicional: String? = null,
    val creadoEn: Long = System.currentTimeMillis()
)
