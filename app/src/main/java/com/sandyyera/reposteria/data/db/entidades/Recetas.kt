package com.sandyyera.reposteria.data.db.entidades

import androidx.room.ColumnInfo
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
    foreignKeys = [
        ForeignKey(
            entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaId"],
            onDelete = ForeignKey.CASCADE
        ),
        // **SET_NULL y no CASCADE**: si la receta original se borra, esta sección conserva sus
        // ingredientes y solo pierde el vínculo. Qué hacer con ella —mantenerla o borrarla— es
        // una decisión que se le pregunta a la persona (8.11.4), no algo que la base resuelva
        // sola llevándose trabajo por delante.
        ForeignKey(
            entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaOrigenId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("recetaId"), Index("recetaOrigenId")]
)
data class RecetaSeccion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recetaId: Long,
    val nombreSeccion: String,
    val orden: Int = 0,
    /** De qué receta se copió esta sección. `null` en las propias (5.5.1). */
    val recetaOrigenId: Long? = null,
    /**
     * La foto de la original al momento de copiar, en texto (5.5.1).
     *
     * Va como **texto y no como columnas sueltas** porque lo que se guarda cambió apenas se
     * decidió adaptar las cantidades en proporción, y con una columna por dato eso habría sido
     * una migración. Nunca se consulta por estos números: solo se comparan contra los de ahora.
     */
    val firmaDelOrigen: String? = null
)

/**
 * Si esta sección se copió de otra receta, aunque esa receta ya no exista (8.11.7).
 *
 * Mira las **dos** columnas y no solo el id, y ahí está todo el punto: `recetaOrigenId` es
 * `SET_NULL`, así que borrar la original lo pone en `null` sin tocar la firma. Preguntando solo
 * por el id, una sección huérfana pasaría por propia y nadie le preguntaría nunca qué hacer con
 * ella — que es justo lo que 8.11.4 existe para no dejar pasar.
 *
 * Va como extensión y no como propiedad de la entidad para que Room no intente mapearla a una
 * columna: es una pregunta sobre la fila, no un dato guardado.
 */
val RecetaSeccion.esTraida: Boolean
    get() = recetaOrigenId != null || firmaDelOrigen != null

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
    foreignKeys = [
        ForeignKey(
            entity = Receta::class, parentColumns = ["id"], childColumns = ["recetaId"],
            onDelete = ForeignKey.CASCADE
        ),
        // **SET_NULL**: borrar una sección deja sus pasos como General en vez de borrarlos.
        // El texto de un paso lo escribió alguien, y hacerlo desaparecer porque se reorganizó
        // la receta sería perder trabajo sin avisar; `bloquesDePasos` ya sabe dibujar un paso
        // sin título. Irse con la sección es una decisión aparte y explícita (8.11.4).
        ForeignKey(
            entity = RecetaSeccion::class, parentColumns = ["id"],
            childColumns = ["tituloSeccionId"], onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("recetaId"), Index("tituloSeccionId")]
)
data class RecetaPaso(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recetaId: Long,
    val orden: Int,
    val contenido: String,
    /** Bajo qué título va el paso. `null` es el General de esta receta (8.8). */
    val tituloSeccionId: Long? = null,
    /**
     * Si es un General **traído de otra receta**, que se dibuja con sangría y distinto del
     * General propio (8.8). Solo tiene sentido con [tituloSeccionId] en `null`.
     */
    @ColumnInfo(defaultValue = "0")
    val esGeneralAnidado: Boolean = false
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
