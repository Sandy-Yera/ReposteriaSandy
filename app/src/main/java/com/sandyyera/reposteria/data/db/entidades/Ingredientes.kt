package com.sandyyera.reposteria.data.db.entidades

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un ingrediente del catálogo, con lo que cuesta cada gramo.
 *
 * No hay ninguna clave foránea que impida borrar uno que esté en uso: eso se controla
 * en la lógica de negocio, avisando primero a qué recetas afecta.
 */
@Entity(
    tableName = "ingredientes",
    indices = [Index(value = ["nombre"], unique = true)]
)
data class Ingrediente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // NOCASE evita que "Harina" y "harina" entren como dos ingredientes distintos.
    // No cubre tildes ("azucar" y "azúcar" siguen siendo distintos para la base), así que
    // esa comparación la hace la validación en logica/ antes de guardar.
    @ColumnInfo(collate = ColumnInfo.NOCASE) val nombre: String,

    val valorPorGramo: Double = 0.0,
    val creadoEn: Long = System.currentTimeMillis(),
    val actualizadoEn: Long = System.currentTimeMillis()
)
