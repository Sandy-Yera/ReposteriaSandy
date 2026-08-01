package com.sandyyera.reposteria.data.db.entidades

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde

/**
 * Un molde del catálogo.
 *
 * Borrarlo no rompe las recetas que lo usaron: pierden el vínculo pero conservan las
 * medidas. Editarlo sí se propaga a las recetas que sigan enlazadas a él.
 */
@Entity(tableName = "moldes")
data class Molde(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    @Embedded val dimensiones: DimensionesMolde,
    val creadoEn: Long = System.currentTimeMillis(),
    val actualizadoEn: Long = System.currentTimeMillis()
)

/**
 * Cuánto rinde una receta: el molde que usa, el peso del producto y en cuántos trozos sale.
 *
 * `dimensiones` es el molde base para el próximo reescalado, no una foto congelada:
 * mientras `moldeOrigenId` apunte a un molde que existe, se mantiene igual a él. Si ese
 * molde se borra, el vínculo pasa a `null` y las medidas quedan como estaban.
 *
 * `pesoFinalG` es el peso real del producto terminado, pesado y no calculado: el mismo
 * molde da pesos distintos según la receta. Es opcional con molde y obligatorio sin él.
 */
@Entity(
    tableName = "receta_rendimiento",
    foreignKeys = [
        ForeignKey(
            entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Molde::class, parentColumns = ["id"], childColumns = ["moldeOrigenId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("moldeOrigenId")]
)
data class RecetaRendimiento(
    @PrimaryKey val recetaId: Long,
    val usaMolde: Boolean = false,
    val moldeOrigenId: Long? = null,
    @Embedded(prefix = "molde_") val dimensiones: DimensionesMolde? = null,
    val pesoFinalG: Double? = null,

    // Arranca en 1 y nunca en 0: así ninguna división por trozos puede reventar mientras
    // la receta está a medio crear en el asistente.
    val trozos: Int = 1,

    /**
     * Si el peso guardado salió de una multiplicación y todavía nadie lo miró (8.4.1, #4).
     *
     * Al cambiar de molde, el peso final se reescala con el mismo factor que los
     * ingredientes. Eso es una **estimación, no una medición**: el peso real depende de
     * cuánta masa quede pegada al molde y de cuánta agua se evapore. Por eso queda marcado
     * hasta que alguien toque el campo.
     *
     * **Es una columna y no un dato de la pantalla**, y tiene que serlo: quien reescala hoy
     * pesa el producto mañana, cuando salga del horno, y para entonces la app ya se cerró
     * cien veces. Un aviso que vive en memoria se pierde justo antes de servir.
     */
    @ColumnInfo(defaultValue = "0")
    val pesoReescaladoSinRevisar: Boolean = false
)
