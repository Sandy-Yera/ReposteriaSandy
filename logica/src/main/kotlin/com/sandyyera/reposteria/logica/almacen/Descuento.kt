package com.sandyyera.reposteria.logica.almacen

import com.sandyyera.reposteria.logica.formato.cantidadConUnidad
import com.sandyyera.reposteria.logica.formato.redondearParaGuardar

/**
 * Descontar del almacén lo que se gastó haciendo recetas (14.9).
 *
 * Es lo que pidió Sandy para no restar a mano frasco por frasco: *"en almacén, poder disminuir
 * los ingredientes eligiendo qué recetas hice y la cantidad"*. La app ya sabe cuánto lleva cada
 * receta —es de donde sale el costo— así que el dato ya está: lo que faltaba era usarlo para
 * mover el inventario.
 *
 * **Todo este archivo es una cuenta y no una escritura.** Arma la vista previa, la pantalla la
 * muestra completa, y recién al confirmar el repositorio escribe. Esa separación no es un
 * capricho de arquitectura: descontar toca muchas filas de una vez, es lo más destructivo que
 * hace el almacén, y lo que evita el desastre es poder mirar antes qué va a pasar con cada una.
 */

/** Una receta que se hizo, y cuántas tandas. */
data class RecetaHecha(
    val recetaId: Long,
    val titulo: String,
    /**
     * Cuántas veces se hizo la receta completa.
     *
     * Es `Double` y no `Int` a propósito: media tanda es normal en repostería, y obligar a un
     * entero empujaría a anotar una tanda entera y corregir el frasco después — o sea, a
     * deshacer justo el trabajo que esto ahorra.
     */
    val tandas: Double
)

/** Lo que **una tanda** de una receta gasta de un ingrediente. */
data class GastoDeIngrediente(val ingredienteId: Long, val cantidad: Double)

/**
 * Lo que le va a pasar a una fila del almacén.
 *
 * Lleva [habia] y [quedara] juntos y ya calculados porque la pantalla tiene que mostrar los dos:
 * "quedará 300 g" sin decir de cuánto se parte no se puede comprobar de un vistazo, que es
 * justamente lo que uno viene a hacer antes de confirmar algo que toca veinte filas.
 */
data class DescuentoDeUnaFila(
    val ingredienteId: Long,
    val nombre: String,
    val esObjeto: Boolean,
    val habia: Double,
    val seUsa: Double,
    val quedara: Double
) {
    val quedaNegativo: Boolean get() = quedara < 0

    /** "500 g" / "3 unidades", para la frase de la pantalla. */
    val comoSeLeeLoQueSeUsa: String get() = cantidadConUnidad(seUsa, esObjeto)

    val comoSeLeeLoQueQueda: String get() = cantidadConUnidad(quedara, esObjeto)

    /** El aviso del negativo, o `null`. Es el mismo de cualquier movimiento (14.8). */
    val aviso: String? get() = avisoDeCantidadNegativa(quedara, esObjeto)
}

/**
 * Un ingrediente que la receta gasta pero del que nadie lleva la cuenta.
 *
 * **No es un error y por eso no bloquea**: hay cosas que se compran y se usan sin anotarse en el
 * almacén, y esa es una decisión válida. Pero tampoco puede pasar callado — si el descuento
 * dijera solo lo que sí movió, quedaría la impresión de que se descontó todo, y la mitad del
 * valor del descuento es saber que la cuenta está completa.
 */
data class IngredienteSinAnotar(val ingredienteId: Long, val nombre: String, val seUsa: Double)

/**
 * Todo lo que va a pasar al confirmar, listo para mirarlo.
 *
 * [hayNegativos] existe para que la pantalla pueda destacar el caso antes de confirmar y no
 * después: descubrir que tres frascos quedaron bajo cero **al volver a la lista** obliga a
 * reconstruir de memoria qué se acaba de descontar.
 */
data class VistaPreviaDelDescuento(
    val filas: List<DescuentoDeUnaFila>,
    val sinAnotar: List<IngredienteSinAnotar>
) {
    val hayNegativos: Boolean get() = filas.any { it.quedaNegativo }

    val hayAlgoQueDescontar: Boolean get() = filas.isNotEmpty()

    /** Cuántas filas del almacén se van a mover. Es lo que se dice en el botón de confirmar. */
    val cuantasFilas: Int get() = filas.size
}

/**
 * Cuánto se gasta de cada ingrediente, sumando todas las recetas hechas.
 *
 * [gastosPorReceta] es, para cada receta, lo que lleva **una tanda**. Multiplicar acá y no al
 * leer la base es lo que permite media tanda y, sobre todo, que la misma receta aparezca dos
 * veces sin que se pisen: las cantidades se **suman**, no se reemplazan.
 *
 * Una receta elegida que no esté en el mapa aporta cero, sin reventar: puede haberse borrado
 * entre que se abrió el cuadro y se confirmó, y ahí lo correcto es descontar el resto y no
 * perder la operación entera.
 */
fun loQueSeGasta(
    recetasHechas: List<RecetaHecha>,
    gastosPorReceta: Map<Long, List<GastoDeIngrediente>>
): Map<Long, Double> {
    val total = mutableMapOf<Long, Double>()
    for (hecha in recetasHechas) {
        if (hecha.tandas <= 0) continue
        for (gasto in gastosPorReceta[hecha.recetaId].orEmpty()) {
            total[gasto.ingredienteId] =
                (total[gasto.ingredienteId] ?: 0.0) + gasto.cantidad * hecha.tandas
        }
    }
    return total.mapValues { redondearParaGuardar(it.value) }
        // Un ingrediente que suma cero no se descuenta ni se muestra: aparecería como una fila
        // que dice "se usa 0 g", que es ruido en una lista que hay que revisar entera.
        .filterValues { it > 0 }
}

/**
 * Cruza lo que se gasta con lo que hay anotado, y devuelve la vista previa completa.
 *
 * [enElAlmacen] son las filas que existen, con su cantidad de ahora. Lo que se gasta y no está
 * ahí sale por [VistaPreviaDelDescuento.sinAnotar] en vez de desaparecer.
 *
 * El orden de las filas es el que trae [enElAlmacen] — o sea el de la pantalla del almacén, que
 * es alfabético— y **los negativos no se suben arriba**: la lista se revisa entera antes de
 * confirmar, y reordenarla según el resultado haría que las mismas cosas cambiaran de lugar
 * entre una vez y la siguiente.
 */
fun vistaPreviaDelDescuento(
    seGasta: Map<Long, Double>,
    enElAlmacen: List<FilaParaDescontar>
): VistaPreviaDelDescuento {
    val porIngrediente = enElAlmacen.associateBy { it.ingredienteId }
    val filas = enElAlmacen.mapNotNull { fila ->
        val cuanto = seGasta[fila.ingredienteId] ?: return@mapNotNull null
        DescuentoDeUnaFila(
            ingredienteId = fila.ingredienteId,
            nombre = fila.nombre,
            esObjeto = fila.esObjeto,
            habia = fila.cantidad,
            seUsa = cuanto,
            quedara = resultadoDelMovimiento(fila.cantidad, cuanto, SentidoDelMovimiento.SALE)
        )
    }
    val sinAnotar = seGasta
        .filterKeys { it !in porIngrediente }
        .map { (id, cuanto) -> IngredienteSinAnotar(id, nombreDesconocido(id), cuanto) }
    return VistaPreviaDelDescuento(filas, sinAnotar)
}

/**
 * Lo mínimo que hace falta saber de una fila del almacén para descontarle.
 *
 * Es un tipo propio y no la fila de Room porque `logica/` no depende de Android, y además deja
 * ver de un vistazo que el descuento **no necesita el precio**: mover el inventario y valorarlo
 * son dos preguntas distintas.
 */
data class FilaParaDescontar(
    val ingredienteId: Long,
    val nombre: String,
    val esObjeto: Boolean,
    val cantidad: Double
)

/**
 * El nombre de relleno de un ingrediente que se gasta y no está en el almacén.
 *
 * Lo reemplaza quien tenga el catálogo a mano —el repositorio— llamando a [conLosNombres]. Acá
 * no se inventa: esta capa no conoce nombres que no le hayan pasado.
 */
private fun nombreDesconocido(ingredienteId: Long): String = "#$ingredienteId"

/**
 * Le pone nombre a los ingredientes que se gastan y no están anotados.
 *
 * Va aparte de [vistaPreviaDelDescuento] porque los nombres los tiene el catálogo y no el
 * almacén, y hacer que la cuenta dependiera de una segunda tabla la volvería imposible de probar
 * sin base de datos — que es justo lo que este módulo evita.
 */
fun VistaPreviaDelDescuento.conLosNombres(
    nombres: Map<Long, String>
): VistaPreviaDelDescuento = copy(
    sinAnotar = sinAnotar.map { it.copy(nombre = nombres[it.ingredienteId] ?: it.nombre) }
)
