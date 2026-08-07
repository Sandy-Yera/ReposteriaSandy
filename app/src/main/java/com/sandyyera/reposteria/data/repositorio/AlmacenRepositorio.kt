package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.AlmacenDao
import com.sandyyera.reposteria.data.db.dao.ArticuloConValor
import com.sandyyera.reposteria.data.db.entidades.ArticuloDeAlmacen
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.logica.busqueda.sonElMismoTexto
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreEscrito
import kotlinx.coroutines.flow.Flow

/**
 * El inventario: qué hay guardado y cuánto queda (sección 14).
 *
 * Cada función que puede fallar **revisa antes de escribir**, como el resto de los repositorios,
 * así que cancelar no necesita deshacer nada.
 */
class AlmacenRepositorio(
    private val dao: AlmacenDao,
    private val historial: HistorialRepositorio
) {

    /** Todo el almacén con su valor, avisando cuando cambie. La que usa la pantalla. */
    fun observarTodo(): Flow<List<ArticuloConValor>> = dao.observarTodo()

    /**
     * Qué ingredientes del catálogo ya están en el almacén.
     *
     * La pantalla la usa para **no ofrecerlos otra vez** en vez de dejar elegirlos y rechazarlos
     * al confirmar: es la misma regla que `titulosDisponibles` y que el ingrediente ya puesto en
     * una sección — lo que se ofrece y lo que se acepta no pueden discrepar. El índice único de
     * la tabla lo hace cumplir de verdad; esto es para que no se llegue a chocar.
     */
    suspend fun ingredientesYaGuardados(): Set<Long> = dao.ingredientesYaEnElAlmacen().toSet()

    /**
     * Pone un ingrediente del catálogo en el almacén.
     *
     * La cantidad va **en gramos**, como todo lo demás en la app: de ahí sale el valor de lo que
     * queda, multiplicando por el `valorPorGramo` que ya se mantiene al día.
     */
    suspend fun agregarIngrediente(ingrediente: Ingrediente, cantidad: Double): Resultado {
        if (ingrediente.id in ingredientesYaGuardados()) {
            return Resultado.NoSePudo(
                "'${ingrediente.nombre}' ya está en el almacén. Tócalo para cambiar la cantidad."
            )
        }
        dao.insertar(ArticuloDeAlmacen(ingredienteId = ingrediente.id, cantidad = cantidad))
        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.INGREDIENTE,
            descripcion = "Se agregó '${ingrediente.nombre}' al almacén",
            detalleAdicional = "${formatearNumero(cantidad)} g"
        )
        return Resultado.Listo
    }

    /**
     * Crea un artículo suelto: la caja, la cinta, la vela.
     *
     * Se cuenta en **unidades** y no en gramos, y no lleva valor: no entra en ninguna receta, así
     * que no hay costo que calcular con él. El día que haga falta costear los envases eso será
     * otra cosa —un costo fijo por producto— y no un valor por gramo inventado acá.
     *
     * Rechaza un nombre repetido comparando con `sonElMismoTexto`, o sea ignorando mayúsculas y
     * tildes: la base no sabe hacerlo, y dos "Cinta" son un inventario partido en dos.
     */
    suspend fun agregarArticuloSuelto(nombre: String, cantidad: Double): Resultado {
        val limpio = nombre.trim()
        errorEnNombreEscrito(limpio)?.let { return Resultado.NoSePudo(it) }
        dao.articulosSueltos().firstOrNull { sonElMismoTexto(it.nombre, limpio) }?.let {
            return Resultado.NoSePudo(
                "Ya tienes '${it.nombre}' en el almacén. Tócalo para cambiar la cantidad."
            )
        }

        dao.insertar(ArticuloDeAlmacen(nombre = limpio, cantidad = cantidad))
        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.INGREDIENTE,
            descripcion = "Se agregó '$limpio' al almacén"
        )
        return Resultado.Listo
    }

    /**
     * Cambia cuánto queda de algo, que es lo que se hace todos los días.
     *
     * **Toca `actualizadoEn` siempre, aunque la cantidad sea la misma.** Confirmar que sigue
     * habiendo lo mismo *es* haber revisado, y esa es justamente la pregunta que responde la
     * fecha: no "cuándo cambió" sino "cuándo lo miré". Un inventario donde revisar y no cambiar
     * nada deja la fecha vieja empuja a inventar un cambio para que se note que se revisó.
     *
     * **No registra en el historial**, a diferencia de crear y borrar: esto se hace a diario y
     * llenaría el panel de cambios hasta tapar lo que sí importa. La fecha de la fila ya cuenta
     * esa historia, y la de seis meses la cuenta mejor que doscientos eventos.
     */
    suspend fun cambiarCantidad(articuloId: Long, cantidad: Double): Resultado {
        val articulo = dao.obtener(articuloId)
            ?: return Resultado.NoSePudo("Eso ya no está en el almacén")
        if (cantidad < 0) return Resultado.NoSePudo("No puede quedar una cantidad negativa")

        dao.actualizar(
            articulo.copy(cantidad = cantidad, actualizadoEn = System.currentTimeMillis())
        )
        return Resultado.Listo
    }

    /**
     * Saca algo del almacén.
     *
     * **No toca el catálogo de ingredientes**: dejar de llevarle la cuenta a la harina no es
     * dejar de usarla en las recetas. Son dos cosas distintas y confundirlas borraría un
     * ingrediente en uso desde una pantalla que no avisa a qué recetas afecta.
     */
    suspend fun eliminar(articuloId: Long, nombre: String): Resultado {
        val articulo = dao.obtener(articuloId)
            ?: return Resultado.NoSePudo("Eso ya no está en el almacén")
        dao.eliminar(articulo)
        historial.registrar(
            tipo = TipoEvento.ELIMINACION,
            entidad = EntidadEvento.INGREDIENTE,
            descripcion = "Se sacó '$nombre' del almacén",
            detalleAdicional = "El ingrediente sigue en el catálogo"
        )
        return Resultado.Listo
    }
}
