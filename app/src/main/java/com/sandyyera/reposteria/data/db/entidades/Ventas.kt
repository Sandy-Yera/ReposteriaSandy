package com.sandyyera.reposteria.data.db.entidades

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una venta: **un día y una lista de lo que se vendió** (18.1).
 *
 * No es una boleta ni tiene cliente. Lo que se quiere analizar es el día —*"¿los domingos vendo
 * más?"*, *"¿el 18 se dispara?"*— y meter clientes ahora sería construir un CRM para responder
 * una pregunta que no lo necesita.
 *
 * [fecha] se guarda como **día del calendario** (`LocalDate.toEpochDay`) y no como un instante en
 * milisegundos, y esa diferencia importa: dos ventas del mismo día tienen que agruparse juntas, y
 * con milisegundos habría que normalizarlas en cada consulta —una operación que se hace mal una
 * vez y desordena todos los informes—. Del día salen solos el nombre, el mes y si era feriado
 * (18.3), así que **nada de eso se guarda**: serían seis columnas diciendo lo mismo que una, y que
 * quedan mal si alguien corrige la fecha después.
 */
@Entity(
    tableName = "ventas",
    indices = [Index("fecha")]
)
data class Venta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** El día, como `LocalDate.toEpochDay()`. Ver arriba por qué no son milisegundos. */
    val fecha: Long,

    /** Lo que uno quiera recordar de ese día: "feria", "pedido grande", "llovió". */
    val notas: String? = null,

    /**
     * Si de esta venta se descontó del almacén (18.4).
     *
     * Se guarda **aunque el descuento ya haya quedado en los movimientos**, porque responde otra
     * pregunta: "¿esta venta ya descontó?" se contesta mirando acá, y sin la columna habría que
     * salir a buscar si existe algún movimiento que la nombre. Es la diferencia entre una venta
     * que no descontó y una que descontó cero.
     */
    @ColumnInfo(defaultValue = "0") val descontoDelAlmacen: Boolean = false,

    val creadoEn: Long = System.currentTimeMillis()
)

/**
 * Una línea de la venta: qué receta, cuántas y a cuánto.
 *
 * **El precio se escribe y no se deduce del catálogo**, y esa es la decisión que hace que el
 * módulo sirva (18.1): el precio guardado en la receta es el que se *piensa* cobrar, y el de la
 * venta es el que se cobró. Si se dedujera, lo real y lo estimado serían el mismo número y no
 * habría nada que comparar.
 *
 * **Las tres cifras estimadas se congelan acá, al contrario que en todo el resto de la app.** En
 * una receta el costo se calcula en vivo a propósito, para que subir un ingrediente mueva todo lo
 * que cuelga de él. En una venta pasa lo contrario: lo que se estimó **ese día** es un hecho, y
 * recalcularlo después haría que el informe de marzo cambiara al corregir un precio en agosto. Un
 * informe que se mueve hacia atrás no se puede usar para decidir nada.
 */
@Entity(
    tableName = "venta_lineas",
    foreignKeys = [
        ForeignKey(
            entity = Venta::class, parentColumns = ["id"], childColumns = ["ventaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaId"],
            // **SET_NULL y no CASCADE**: borrar una receta no puede borrar la historia de lo que
            // se vendió. La venta ocurrió; que la receta ya no exista no la deshace.
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("ventaId"), Index("recetaId")]
)
data class VentaLinea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: Long,

    /** `null` cuando la receta se borró después. La línea sigue valiendo: ver [tituloReceta]. */
    val recetaId: Long? = null,

    /**
     * Cómo se llamaba la receta al vender.
     *
     * Copiado a propósito, y es la única copia de un nombre que esta app acepta. En todo lo demás
     * el nombre se lee del catálogo para que un renombre llegue solo (14.5); acá se congela
     * porque si la receta se borra **no queda de dónde leerlo**, y un informe con una línea sin
     * nombre no se puede leer.
     */
    val tituloReceta: String,

    /** Cuántos productos completos se vendieron. */
    val unidades: Int,

    /** Lo que se cobró por **cada uno**. Escrito a mano: es el dato real (18.1). */
    val precioUnitario: Double,

    /** Lo que la app decía que costaba hacer uno, ese día. Congelado: ver arriba. */
    val costoEstimadoUnitario: Double,

    /** Lo que la app decía que se cobraría por uno, ese día. Congelado: ver arriba. */
    val precioEstimadoUnitario: Double
)

/**
 * Por qué se movió algo del almacén (18.2).
 *
 * Existe porque **el costo real necesita saber de dónde vino cada salida**: lo que se descontó por
 * una venta cuenta en el informe de ese día, y lo que se botó por vencido no. Sin el motivo, todas
 * las salidas se verían iguales y "cuánto costó de verdad lo que vendí" no tendría respuesta.
 */
enum class MotivoDeMovimiento {
    /** Llegó una compra, o apareció algo que no estaba anotado. */
    ENTRADA,

    /** Se descontó por haber hecho recetas (14.9). */
    PRODUCCION,

    /** Se descontó al registrar una venta (18.4). */
    VENTA,

    /** Se corrigió a mano: "miré el frasco y quedaba esto". No dice a dónde se fue. */
    AJUSTE
}

/**
 * Un movimiento del almacén: qué salió o entró, cuánto, y por qué (18.2).
 *
 * Es la tabla que 14.12 dejó anotada como deuda. Sin ella, cada fila del almacén sabe **cuánto
 * hay** pero no **cómo llegó ahí**, y entonces "lo real" del informe sería lo estimado con otro
 * nombre.
 *
 * [valorUnitario] se congela igual que las cifras de la venta, y por la misma razón llevada un
 * paso más: el costo real de lo vendido en marzo se calcula con lo que valía la harina en marzo.
 * Leyéndolo del catálogo, subir el precio hoy encarecería retroactivamente todo lo que se vendió
 * el año pasado.
 */
@Entity(
    tableName = "movimientos_almacen",
    foreignKeys = [
        ForeignKey(
            entity = Ingrediente::class, parentColumns = ["id"], childColumns = ["ingredienteId"],
            // Igual que la línea de venta: borrar un ingrediente no borra la historia de lo que
            // se gastó. Por eso [nombre] viaja copiado.
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Venta::class, parentColumns = ["id"], childColumns = ["ventaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("ingredienteId"), Index("ventaId"), Index("fecha")]
)
data class MovimientoDeAlmacen(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** `null` cuando el ingrediente se borró después. El movimiento sigue valiendo. */
    val ingredienteId: Long? = null,

    /** Cómo se llamaba al moverse. Copiado por lo mismo que `VentaLinea.tituloReceta`. */
    val nombre: String,

    /**
     * Cuánto se movió, **con signo**: negativo si salió, positivo si entró.
     *
     * Acá sí va el signo en el número, al revés que en el cuadro de editar (14.8), y no es una
     * contradicción: allá se **escribe** —y un número que cambia de significado según dónde esté
     * escrito se guarda al revés— y acá se **suma**. Una columna con signo se totaliza con un
     * `SUM`; partida en dos haría falta un `CASE` en cada consulta.
     */
    val cantidad: Double,

    /** Lo que valía cada gramo (o cada unidad) en ese momento. Congelado: ver arriba. */
    val valorUnitario: Double,

    val motivo: MotivoDeMovimiento,

    /** La venta que lo causó, si fue una. `null` en producción, entradas y ajustes. */
    val ventaId: Long? = null,

    /** El día, como `LocalDate.toEpochDay()`. Mismo criterio que [Venta.fecha]. */
    val fecha: Long,

    val creadoEn: Long = System.currentTimeMillis()
)
