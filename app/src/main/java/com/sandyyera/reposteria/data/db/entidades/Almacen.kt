package com.sandyyera.reposteria.data.db.entidades

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Algo que hay guardado y del que se lleva la cuenta: el inventario (sección 14).
 *
 * **Todo lo que se anota acá tiene su ingrediente en el catálogo** (14.5). Sandy lo pidió así:
 * *"cuando crees un producto en almacén, irá automáticamente a ingredientes"*. Antes había dos
 * mitades —lo enlazado y lo suelto— y lo suelto se quedaba fuera del catálogo para que no
 * apareciera en el buscador de "agregar ingrediente a la receta"; eso ahora lo resuelve
 * `Ingrediente.vaEnRecetas` sin necesidad de dos clases de fila.
 *
 * De ese enlace sale todo lo que esta tabla **no** guarda: el nombre, el precio y la unidad. Es a
 * propósito — si el valor se escribiera también acá habría dos números para lo mismo y uno
 * quedaría viejo, y el nombre copiado no se enteraría de los renombres. Es la misma lección que
 * costó rehacer la firma de una receta copiada (8.11.7).
 *
 * [ingredienteId] sigue siendo nulable **solo por las filas anteriores a 14.5** que la migración
 * 7 → 8 no haya podido enlazar. Nada nuevo se crea así.
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
     * El ingrediente del catálogo. Solo es `null` en filas anteriores a 14.5.
     *
     * El índice es **único**: un ingrediente no puede tener dos filas de almacén, porque
     * entonces "cuánta harina queda" tendría dos respuestas. SQLite permite varios `NULL` en
     * un índice único, así que esas filas viejas no se estorban entre sí mientras existan.
     */
    val ingredienteId: Long? = null,

    /**
     * Restos del diseño anterior: el nombre propio de un artículo suelto.
     *
     * **Queda vacío en todo lo que se crea desde 14.5** y el nombre se lee siempre del catálogo.
     * La columna no se borra porque quitarla en SQLite es recrear la tabla entera con sus datos,
     * y una columna vacía cuesta menos que esa operación.
     */
    val nombre: String = "",

    /** Cuánto queda, en la unidad de medida de su ingrediente: gramos o unidades (14.5). */
    val cantidad: Double = 0.0,

    /**
     * Lo que quieras acordarte de esta compra: dónde, cuándo, si estaba en oferta (14.6).
     *
     * **Texto libre y un solo campo**, y no tres columnas (lugar, fecha, oferta). Sandy lo pidió
     * como "dónde lo compré, cuándo, si era una oferta o cosas así", y ese "cosas así" es el
     * dato: lo que hay que anotar cambia con cada compra, y una columna por cosa obliga a
     * decidir hoy cuáles son todas — dejando fuera justo la que aparezca mañana. Nadie consulta
     * por estos datos, se leen; así que no hace falta que la base los entienda.
     *
     * Es opcional de verdad: `null` es lo normal.
     */
    val detalles: String? = null,

    /**
     * Cuándo se actualizó por última vez.
     *
     * Se muestra en la lista, y no es decoración: un inventario que se lleva "día a día" vale
     * lo que vale su última actualización, y un número de hace tres semanas hay que poder
     * distinguirlo de uno de esta mañana antes de salir a comprar.
     */
    val actualizadoEn: Long = System.currentTimeMillis()
)
