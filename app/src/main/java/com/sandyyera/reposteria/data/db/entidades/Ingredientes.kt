package com.sandyyera.reposteria.data.db.entidades

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un ingrediente del catálogo, con lo que cuesta cada gramo.
 *
 * No hay ninguna clave foránea que impida borrar uno que esté en uso: eso se controla
 * en la lógica de negocio, avisando primero a qué recetas afecta.
 */
@Entity(
    tableName = "ingredientes",
    indices = [Index(value = ["nombre"], unique = true)]
)
data class Ingrediente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // NOCASE evita que "Harina" y "harina" entren como dos ingredientes distintos.
    // No cubre tildes ("azucar" y "azúcar" siguen siendo distintos para la base), así que
    // esa comparación la hace la validación en logica/ antes de guardar.
    @ColumnInfo(collate = ColumnInfo.NOCASE) val nombre: String,

    /**
     * Lo que cuesta **una unidad de medida**: un gramo, o una unidad si [esObjeto] (14.5).
     *
     * La columna se llama `valorPorGramo` desde la versión 1 y se queda así: renombrarla en
     * SQLite es recrear la tabla entera con sus datos, y el nombre viejo mintiendo en un caso
     * cuesta menos que esa operación sobre datos reales. Lo que sí no puede quedar suelto es
     * cuál de las dos cosas significa, y eso lo dice [esObjeto] — nunca se lee una sin la otra.
     */
    val valorPorGramo: Double = 0.0,

    /**
     * Si esto se cuenta **por unidad** en vez de por gramo: una caja, una cinta, una vela.
     *
     * Cambia dos cosas y ninguna es el cálculo: cómo se cuenta en el almacén, y cómo se lee en
     * una receta ("2 cajas" en vez de "2 g"). **Un objeto entra a una receta con 0 gramos**, así
     * que no suma al costo — decisión de Sandy para no tocar el motor de cálculo, que de punta a
     * punta parte de gramos. La pantalla lo dice en la línea, para que no se descubra comparando
     * números (14.5).
     */
    @ColumnInfo(defaultValue = "0")
    val esObjeto: Boolean = false,

    /**
     * Si se ofrece al armar una receta.
     *
     * **Es otra pregunta que [esObjeto]**, y las dos hacen falta. Una caja de torta es un objeto
     * y sí se anota en la receta; una vela decorativa es un objeto y no. Un ingrediente normal es
     * las dos cosas: no es objeto y va en recetas.
     *
     * Con esto el almacén puede llevar la cuenta de todo sin que el buscador de "agregar
     * ingrediente a la receta" se llene de cosas que nunca van en una — que era el motivo de
     * tenerlos en tablas separadas, y ya no hace falta.
     */
    @ColumnInfo(defaultValue = "1")
    val vaEnRecetas: Boolean = true,

    val creadoEn: Long = System.currentTimeMillis(),
    val actualizadoEn: Long = System.currentTimeMillis()
)

/**
 * Cómo se cuenta este ingrediente: "g" o "unidad". Para escribirlo al lado de una cantidad.
 *
 * Va como extensión y no como propiedad de la entidad para que Room no intente mapearla a una
 * columna: es una lectura de [Ingrediente.esObjeto], no un dato guardado.
 */
val Ingrediente.unidadDeMedida: String
    get() = if (esObjeto) "unidad" else "g"
