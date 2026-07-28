package com.sandyyera.reposteria.data.db.entidades

import androidx.room.Entity
import androidx.room.ForeignKey

/** Dónde se guarda el producto. Cada receta tiene una fila por cada uno. */
enum class TipoDuracion { AMBIENTE, REFRIGERADA, CONGELADA }

/** En qué unidad se mide cuánto dura. */
enum class UnidadDuracion { HORAS, DIAS, SEMANAS, MESES }

/**
 * Cuánto dura el producto guardado de cierta forma.
 *
 * Todo el paso es opcional y las duraciones son estimaciones, no datos exactos: la
 * pantalla lo advierte.
 *
 * Cuando `apto` es `false` (por ejemplo, algo que no debe congelarse) se ignoran
 * `cantidad` y `unidad`: ahí lo que importa es justamente que no corresponde guardarlo así.
 */
@Entity(
    tableName = "receta_duracion",
    primaryKeys = ["recetaId", "tipo"],
    foreignKeys = [ForeignKey(
        entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class RecetaDuracion(
    val recetaId: Long,
    val tipo: TipoDuracion,
    val apto: Boolean = true,
    val cantidad: Int? = null,
    val unidad: UnidadDuracion? = null
)
