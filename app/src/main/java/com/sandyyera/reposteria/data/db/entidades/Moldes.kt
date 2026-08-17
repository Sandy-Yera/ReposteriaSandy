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

    /**
     * Una nota corta y libre sobre este molde (9.6).
     *
     * Lo pidió Sandy con el caso que la dejó a medias: *"en algunos moldes exóticos son redondos,
     * tienen un diámetro, pero no lo puedo dejar escrito y deberé medirlo para poder recalcular
     * ciertas cosas"*. Un molde exótico se mide con agua a propósito —no hay fórmula para su
     * volumen— pero eso no significa que no se sepa **nada** de él: su diámetro, su forma, un
     * detalle de por dónde se desmolda.
     *
     * **Es texto libre y no participa de ningún cálculo**, y esa separación es todo el punto. Si
     * el diámetro anotado acá entrara en el área, un molde de rosca volvería a calcularse como un
     * cilindro y las cantidades de sus recetas se irían al tacho — que es exactamente lo que 9.4
     * existe para evitar. Acá se anota para **leerlo**, no para que la app lo use.
     */
    val notas: String? = null,

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
    val pesoReescaladoSinRevisar: Boolean = false,

    /**
     * En cuántas partes se corta el primer lado del molde, al cortar en cuadrícula (9.4.3).
     *
     * `null` significa "no lo elegí": ahí la app reparte lo más parejo que puede. Con un
     * número, ese manda — es el mismo criterio que los lados de corte anotados a mano.
     *
     * **El otro lado no se guarda**: sale de dividir `trozos` por este, así que guardarlo sería
     * un segundo lugar donde el mismo dato puede quedar mal. Y por lo mismo, un valor que ya no
     * divida justo a `trozos` (la receta pasó de 6 a 8) se descarta en vez de aplicarse:
     * `repartoEfectivo` vuelve al más parejo antes que mostrar 2,67 filas.
     *
     * **Vive acá y no en `DimensionesMolde`** aunque sea "del corte": depende de `trozos`, que
     * es de la receta y no del molde. En el molde no habría contra qué dividirlo.
     */
    val trozosALoLargo: Int? = null
)
