package com.sandyyera.reposteria.data.db.entidades

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.PrecioVigente

/**
 * Un precio o promoción de una receta: "vender N trozos (o N productos) por X en total".
 *
 * Todos los guardados se ven en la receta para poder compararlos, y **uno de ellos** es el
 * de referencia: el que alimenta las cifras automáticas (sueldos, simulaciones, ganancia
 * final, trozo ganador). Cuál es se elige a mano.
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
    val etiqueta: String? = null,

    /**
     * Si es este el precio con el que se calcula todo lo automático.
     *
     * Solo una fila por receta debe tenerlo en `1`, y eso lo garantiza
     * `RecetaDao.fijarPrecioDeReferencia`, que en una transacción apaga las demás antes de
     * encender esta. **No se escribe directo**: hacerlo a mano es la forma de terminar con
     * dos referencias y cifras que dependen de cuál fila salga primero.
     *
     * El `defaultValue` está declarado a propósito y tiene que calzar con el `DEFAULT 0`
     * de la migración 1→2: si uno lo declara y el otro no, Room detecta la diferencia al
     * abrir la base y la app no arranca.
     */
    @ColumnInfo(defaultValue = "0") val esReferencia: Boolean = false,

    /**
     * Si es **el precio de todos los días** de su modo: lo que se cobra por un trozo suelto o
     * por un producto entero, sin promoción de por medio.
     *
     * Una fila por receta **y por modo** debe tenerlo en `1`, y siempre con `cantidad = 1`. Lo
     * garantiza `RecetaDao.fijarPrecioBase`, que en una transacción apaga las demás del mismo
     * modo antes de encender esta. **No se escribe directo**, por lo mismo que `esReferencia`.
     *
     * Llegó en la versión 9. Antes la base se deducía de `cantidad == 1`, y por eso solo podía
     * haber una: un segundo precio de un trozo habría sido indistinguible del primero y la app
     * lo rechazaba. Con la marca puede haber varios y se sabe cuál es cuál.
     *
     * El `defaultValue` tiene que calzar con el `DEFAULT 0` de la migración 8 → 9, o Room se
     * niega a abrir la base.
     */
    @ColumnInfo(defaultValue = "0") val esBase: Boolean = false
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
    etiqueta = etiqueta,
    esReferencia = esReferencia,
    esBase = esBase
)
