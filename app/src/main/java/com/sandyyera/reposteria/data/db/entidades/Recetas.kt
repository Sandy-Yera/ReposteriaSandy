package com.sandyyera.reposteria.data.db.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una receta.
 *
 * Todo lo que le pertenece (secciones, ingredientes, rendimiento, duración, precios,
 * pasos, simulación) desaparece con ella en cascada, igual que los sueldos que algún
 * empleado tuviera asignados para esta receta.
 */
@Entity(tableName = "recetas")
data class Receta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val pasoPrevio: String = "No necesita",
    val creadoEn: Long = System.currentTimeMillis(),
    val actualizadoEn: Long = System.currentTimeMillis()
)

/**
 * Un conjunto de ingredientes dentro de una receta: el bizcocho, la crema, el almíbar.
 *
 * Una receta simple igual tiene una, creada automáticamente y sin mostrar su nombre.
 * Cuando se le agrega una segunda, recién ahí se le pide un nombre a la primera.
 */
@Entity(
    tableName = "receta_secciones",
    foreignKeys = [ForeignKey(
        entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recetaId")]
)
data class RecetaSeccion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recetaId: Long,
    val nombreSeccion: String,
    val orden: Int = 0
)

/**
 * Cuánto se usa de un ingrediente dentro de una sección.
 *
 * Cuelga de la sección, no de la receta: al borrar una sección se van sus ingredientes.
 * Hacia `ingredientes` no hay clave foránea a propósito (ver [Ingrediente]).
 */
@Entity(
    tableName = "receta_ingredientes",
    foreignKeys = [ForeignKey(
        entity = RecetaSeccion::class, parentColumns = ["id"], childColumns = ["seccionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("seccionId"), Index("ingredienteId")]
)
data class RecetaIngrediente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seccionId: Long,
    val ingredienteId: Long,
    val cantidadG: Double,
    val orden: Int = 0
)

/** Un paso de la preparación. El "paso previo" va aparte, en [Receta.pasoPrevio]. */
@Entity(
    tableName = "receta_pasos",
    foreignKeys = [ForeignKey(
        entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recetaId")]
)
data class RecetaPaso(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recetaId: Long,
    val orden: Int,
    val contenido: String
)

/**
 * Cuántos días a la semana se vende la receta y cuántas unidades por día.
 *
 * Se crea junto con la receta con valores por defecto, para que las cifras simuladas
 * existan desde el principio y no haya un hueco mientras la receta está a medio armar.
 */
@Entity(
    tableName = "receta_simulacion_venta",
    foreignKeys = [ForeignKey(
        entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class RecetaSimulacionVenta(
    @PrimaryKey val recetaId: Long,
    val diasPorSemana: Int = 1,
    val unidadesPorDia: Int = 1
)
