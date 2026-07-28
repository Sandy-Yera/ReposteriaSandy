package com.sandyyera.reposteria.data.db.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.PrecioVigente

/**
 * Un precio o promoción de una receta: "vender N trozos (o N productos) por X en total".
 *
 * No hay un precio "activo" que se elija a mano. Todos los guardados se ven en la receta
 * para poder compararlos, pero las cifras automáticas siempre usan el de menor ganancia.
 */
@Entity(
    tableName = "receta_precios",
    foreignKeys = [ForeignKey(
        entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recetaId")]
)
data class RecetaPrecio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recetaId: Long,
    val modo: ModoPrecio,
    val cantidad: Int = 1,
    val precioTotal: Double,
    val etiqueta: String? = null
)

/**
 * Convierte la fila a lo que entienden las fórmulas.
 *
 * Existe porque `logica/` no depende de Android: allá el precio se llama [PrecioVigente]
 * y no sabe nada de Room ni de identificadores.
 */
fun RecetaPrecio.aVigente(): PrecioVigente = PrecioVigente(
    modo = modo,
    cantidad = cantidad,
    precioTotal = precioTotal,
    etiqueta = etiqueta
)
