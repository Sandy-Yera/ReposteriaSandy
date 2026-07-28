package com.sandyyera.reposteria.data.db.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Alguien que vende los productos.
 *
 * Siempre existe uno con `esGenerico = true`: es el modelo estándar, va fijo al principio
 * de la lista y no se puede borrar ni renombrar. Los demás se crean y se borran libremente.
 */
@Entity(tableName = "empleados")
data class Empleado(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val esGenerico: Boolean = false,
    val creadoEn: Long = System.currentTimeMillis(),
    val actualizadoEn: Long = System.currentTimeMillis()
)

/**
 * Cuánta ganancia se lleva un empleado por una receta, y su simulación individual.
 *
 * El índice único impide que quede más de una fila para el mismo par empleado-receta:
 * si no, habría dos sueldos distintos para lo mismo y las consultas devolverían
 * cualquiera de los dos sin avisar.
 *
 * Ojo: este `diasPorSemana` es propio de esta receta. El de [EmpleadoSimulacionMultiple]
 * es otro, compartido entre todas; cambiar uno no afecta al otro.
 */
@Entity(
    tableName = "empleado_receta_sueldo",
    foreignKeys = [
        ForeignKey(
            entity = Empleado::class, parentColumns = ["id"], childColumns = ["empleadoId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // El compuesto cubre además la clave foránea de empleadoId. El de recetaId va aparte
    // porque el compuesto no sirve para buscar solo por receta, y la cascada lo necesita.
    indices = [
        Index(value = ["empleadoId", "recetaId"], unique = true),
        Index("recetaId")
    ]
)
data class EmpleadoRecetaSueldo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val empleadoId: Long,
    val recetaId: Long,
    val gananciaEmpleado: Double,
    val diasPorSemana: Int = 1,
    val unidadesPorDia: Int = 1
)

/**
 * Los días por semana que se usan al simular todas las recetas de un empleado a la vez.
 *
 * Es uno solo y compartido: "vendo 4 días, y esos 4 días son 2 bizcochos, 1 torta y
 * 5 chocolates por día".
 */
@Entity(
    tableName = "empleado_simulacion_multiple",
    foreignKeys = [ForeignKey(
        entity = Empleado::class, parentColumns = ["id"], childColumns = ["empleadoId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class EmpleadoSimulacionMultiple(
    @PrimaryKey val empleadoId: Long,
    val diasPorSemana: Int = 1
)

/** Cuántas unidades de cada receta entran en la simulación múltiple de un empleado. */
@Entity(
    tableName = "empleado_simulacion_multiple_detalle",
    foreignKeys = [
        ForeignKey(
            entity = EmpleadoSimulacionMultiple::class,
            parentColumns = ["empleadoId"], childColumns = ["empleadoId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["empleadoId", "recetaId"], unique = true),
        Index("recetaId")
    ]
)
data class EmpleadoSimulacionMultipleDetalle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val empleadoId: Long,
    val recetaId: Long,
    // Se permite 0 a propósito: significa "esta receta no se vende" y aporta 0 al total.
    val unidadesPorDia: Int = 0
)
