package com.sandyyera.reposteria.data.db.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Algo que hay guardado y del que se lleva la cuenta: el inventario (sección 14).
 *
 * **Un artículo puede ser un ingrediente del catálogo o algo suelto**, y esa es la decisión que
 * ordena toda la tabla. Sandy lo pidió como "inventario de todo", y las dos mitades hacen falta
 * por motivos distintos:
 *
 * - **Un ingrediente enlazado** ([ingredienteId] con valor) sabe cuánto vale lo que queda, sin
 *   que nadie lo escriba: sale de su `valorPorGramo`, que ya se mantiene al día porque de él
 *   dependen todos los costos. Si además se escribiera el valor acá, habría dos números para lo
 *   mismo y uno quedaría viejo.
 * - **Un artículo suelto** ([ingredienteId] en `null`) es la caja, la cinta, la vela: no entra
 *   en ninguna receta y no tiene valor por gramo. Meterlo igual en el catálogo de ingredientes
 *   lo dejaría apareciendo en el buscador de "agregar ingrediente a la receta", que es
 *   exactamente donde no va.
 *
 * **La unidad se deduce y no se guarda**: lo enlazado se cuenta en gramos —como todo el resto de
 * la app— y lo suelto en unidades. Guardar una columna de unidad abriría la puerta a "3" de algo
 * que en la receta se mide en gramos, y ahí la cuenta del valor daría cualquier cosa.
 *
 * La clave foránea es **CASCADE**: borrar un ingrediente del catálogo se lleva su fila de
 * almacén. Es lo correcto y no una pérdida — sin el ingrediente no hay valor por gramo, así que
 * lo que quedaría es un nombre y un número sin nada detrás. Borrar un ingrediente ya avisa a qué
 * recetas afecta (7.1), y ese aviso es el lugar donde esto se menciona.
 */
@Entity(
    tableName = "almacen",
    foreignKeys = [
        ForeignKey(
            entity = Ingrediente::class, parentColumns = ["id"], childColumns = ["ingredienteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["ingredienteId"], unique = true)]
)
data class ArticuloDeAlmacen(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /**
     * El ingrediente del catálogo, o `null` si es un artículo suelto.
     *
     * El índice es **único**: un ingrediente no puede tener dos filas de almacén, porque
     * entonces "cuánta harina queda" tendría dos respuestas. SQLite permite varios `NULL` en
     * un índice único, así que los artículos sueltos no se estorban entre sí — que es
     * justamente lo que hace falta.
     */
    val ingredienteId: Long? = null,

    /**
     * Cómo se llama, **solo para los artículos sueltos**.
     *
     * En los enlazados queda vacío a propósito y el nombre se lee del catálogo: copiarlo acá
     * dejaría un segundo nombre que no se entera de los renombres. Es la misma lección que ya
     * costó rehacer la firma de una receta copiada (8.11.7).
     */
    val nombre: String = "",

    /** Cuánto queda: gramos si está enlazado, unidades si es suelto. */
    val cantidad: Double = 0.0,

    /**
     * Cuándo se actualizó por última vez.
     *
     * Se muestra en la lista, y no es decoración: un inventario que se lleva "día a día" vale
     * lo que vale su última actualización, y un número de hace tres semanas hay que poder
     * distinguirlo de uno de esta mañana antes de salir a comprar.
     */
    val actualizadoEn: Long = System.currentTimeMillis()
)
