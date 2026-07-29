package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.RecetaDao
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.RecetaIngrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaSeccion
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.data.db.entidades.aVigente
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.errorAlElegirReferencia
import com.sandyyera.reposteria.logica.validaciones.NOMBRE_SECCION_POR_DEFECTO
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreSeccion
import com.sandyyera.reposteria.logica.validaciones.errorEnTituloReceta
import com.sandyyera.reposteria.logica.validaciones.nombreSugeridoParaPrimeraSeccion
import kotlinx.coroutines.flow.Flow

/**
 * Cómo terminó una operación que podía no poder hacerse.
 *
 * Es un tipo cerrado y no un `Boolean` para que el motivo viaje junto con el fracaso: la
 * pantalla tiene que poder mostrar *por qué* no se pudo, y un `false` no lo dice.
 */
sealed interface Resultado {
    data object Listo : Resultado

    /** No se hizo nada. [motivo] es el texto a mostrar. */
    data class NoSePudo(val motivo: String) : Resultado
}

/**
 * Todo lo que se hace con una receta y sus partes.
 *
 * Cada función que puede fallar **revisa antes de escribir**. Eso hace que "cancelar la
 * acción y dejar todo como estaba" no necesite deshacer nada: simplemente no se escribió.
 */
class RecetaRepositorio(
    private val dao: RecetaDao,
    private val historial: HistorialRepositorio
) {

    // --- La receta ---

    fun observarTodas(): Flow<List<Receta>> = dao.observarTodas()

    suspend fun obtener(recetaId: Long): Receta? = dao.obtener(recetaId)

    /**
     * Crea una receta con todo lo que necesita para existir sin huecos.
     *
     * La primera sección se llama [NOMBRE_SECCION_POR_DEFECTO] y **no se muestra** mientras
     * sea la única (8.2): existe para que los ingredientes tengan dónde colgar, no para que
     * alguien le ponga nombre a "todo lo que lleva".
     */
    suspend fun crear(titulo: String): ResultadoCrearReceta {
        val limpio = titulo.trim()
        errorEnTituloReceta(limpio)?.let { return ResultadoCrearReceta.NoValido(it) }

        val id = dao.crearReceta(limpio, NOMBRE_SECCION_POR_DEFECTO)
        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se creó la receta '$limpio'"
        )
        return ResultadoCrearReceta.Creada(id)
    }

    suspend fun renombrar(recetaId: Long, titulo: String): Resultado {
        val limpio = titulo.trim()
        errorEnTituloReceta(limpio)?.let { return Resultado.NoSePudo(it) }
        val receta = dao.obtener(recetaId) ?: return Resultado.NoSePudo("Esa receta ya no existe")

        dao.actualizar(receta.copy(titulo = limpio, actualizadoEn = System.currentTimeMillis()))
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se renombró la receta '${receta.titulo}' a '$limpio'"
        )
        return Resultado.Listo
    }

    /**
     * Borra la receta de verdad, cuando ya se confirmó la advertencia (6.3).
     *
     * Lee el título **antes** de borrar, porque después ya no se puede consultar y el
     * historial lo necesita. Todo lo que cuelga de la receta —secciones, ingredientes,
     * rendimiento, duración, precios, pasos, simulación y los sueldos que algún empleado
     * tuviera asignados— se va con ella por las cascadas declaradas en las entidades.
     */
    suspend fun confirmarEliminacion(recetaId: Long) {
        val receta = dao.obtener(recetaId) ?: return

        dao.eliminarPorId(recetaId)
        historial.registrar(
            tipo = TipoEvento.ELIMINACION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se eliminó la receta '${receta.titulo}'",
            detalleAdicional = "Se fueron con ella sus ingredientes, precios y los sueldos " +
                "que tuviera asignados"
        )
    }

    // --- Secciones ---

    suspend fun obtenerSecciones(recetaId: Long): List<RecetaSeccion> = dao.obtenerSecciones(recetaId)

    /**
     * Qué nombre proponer para la sección que hasta ahora era invisible.
     *
     * Devuelve `null` cuando no hay nada que bautizar: la receta ya tiene dos o más
     * secciones, así que todas tienen nombre visible y se puede agregar la nueva directo.
     *
     * La pantalla llama esto **antes** de agregar la segunda sección. Si devuelve un texto,
     * muestra el campo con esa sugerencia; si devuelve `null`, va derecho a
     * [agregarSeccion].
     */
    suspend fun nombreQueFaltaBautizar(recetaId: Long): String? {
        if (dao.contarSecciones(recetaId) != 1) return null
        val receta = dao.obtener(recetaId) ?: return null
        return nombreSugeridoParaPrimeraSeccion(receta.titulo)
    }

    /**
     * Agrega una sección, bautizando de paso la que era invisible.
     *
     * [nombreDeLaPrimera] es el nombre para la sección que ya existía, y solo se usa cuando
     * la receta tenía exactamente una. **Los ingredientes ya cargados no se mueven de
     * lugar** (8.2): siguen en la misma sección, que ahora tiene nombre visible.
     */
    suspend fun agregarSeccion(
        recetaId: Long,
        nombre: String,
        nombreDeLaPrimera: String? = null
    ): Resultado {
        val limpio = nombre.trim()
        errorEnNombreSeccion(limpio)?.let { return Resultado.NoSePudo(it) }

        val existentes = dao.obtenerSecciones(recetaId)
        if (existentes.isEmpty()) return Resultado.NoSePudo("Esa receta ya no existe")

        // Si la que había era la única, hay que ponerle nombre antes de que deje de ser
        // invisible. Sin esto quedaría un encabezado que dice "General" al lado de "Crema".
        if (existentes.size == 1) {
            val bautizo = nombreDeLaPrimera?.trim()
                ?: return Resultado.NoSePudo(
                    "Antes de agregar otra sección hay que ponerle nombre a la que ya existe"
                )
            errorEnNombreSeccion(bautizo)?.let { return Resultado.NoSePudo(it) }
            dao.actualizarSeccion(existentes.single().copy(nombreSeccion = bautizo))
        }

        dao.insertarSeccion(
            RecetaSeccion(
                recetaId = recetaId,
                nombreSeccion = limpio,
                orden = existentes.size
            )
        )
        return Resultado.Listo
    }

    suspend fun renombrarSeccion(seccion: RecetaSeccion, nombre: String): Resultado {
        val limpio = nombre.trim()
        errorEnNombreSeccion(limpio)?.let { return Resultado.NoSePudo(it) }
        dao.actualizarSeccion(seccion.copy(nombreSeccion = limpio))
        return Resultado.Listo
    }

    /**
     * Borra una sección con todos sus ingredientes.
     *
     * No deja la receta sin ninguna: los ingredientes necesitan dónde colgar y una receta
     * sin secciones no podría recibir nada.
     *
     * Al volver a quedar una sola, su nombre se vuelve invisible por su cuenta —
     * `debeMostrarNombreDeSeccion` lo decide al dibujar—, así que no hace falta renombrarla
     * de vuelta a "General".
     */
    suspend fun eliminarSeccion(recetaId: Long, seccionId: Long): Resultado {
        if (dao.contarSecciones(recetaId) <= 1) {
            return Resultado.NoSePudo("La receta necesita al menos una sección")
        }
        dao.eliminarSeccion(seccionId)
        return Resultado.Listo
    }

    // --- Ingredientes de la receta ---

    suspend fun obtenerIngredientes(recetaId: Long): List<RecetaIngrediente> =
        dao.obtenerTodosLosIngredientes(recetaId)

    suspend fun agregarIngrediente(
        seccionId: Long,
        ingredienteId: Long,
        cantidadG: Double,
        orden: Int = 0
    ): Long = dao.insertarIngrediente(
        RecetaIngrediente(
            seccionId = seccionId,
            ingredienteId = ingredienteId,
            cantidadG = cantidadG,
            orden = orden
        )
    )

    suspend fun cambiarCantidad(itemId: Long, cantidadG: Double) =
        dao.actualizarCantidad(itemId, cantidadG)

    suspend fun quitarIngrediente(itemId: Long) = dao.eliminarIngrediente(itemId)

    /** Lo que cuesta hacer la receta, con el precio **actual** de cada ingrediente (#3). */
    suspend fun costoTotal(recetaId: Long): Double = dao.costoTotalReceta(recetaId)

    /**
     * El costo de varias recetas de una sola consulta, para la lista.
     *
     * Existe para que la pantalla no pregunte una vez por receta: con veinte recetas eso
     * serían veinte consultas cada vez que cambia cualquier cosa.
     *
     * **Devuelve una entrada por cada id pedido**, incluidos los que la consulta no trae.
     * El `GROUP BY` no da fila para una receta sin ingredientes, y quien reciba un mapa
     * incompleto tarde o temprano hace `getValue` y se cae. Ese remiendo va acá una vez y
     * no en cada llamador.
     */
    suspend fun costosDe(recetaIds: List<Long>): Map<Long, Double> {
        if (recetaIds.isEmpty()) return emptyMap()
        val encontrados = dao.costoDeVariasRecetas(recetaIds).associate { it.recetaId to it.costo }
        return recetaIds.associateWith { encontrados[it] ?: 0.0 }
    }

    // --- Precios ---

    /**
     * Arma la foto de una o varias recetas que después usan todas las fórmulas (6.4).
     *
     * Recibe una lista y no un id suelto a propósito: la simulación múltiple pide todas sus
     * recetas juntas y así resuelve con tres consultas en vez de tres por receta. Para una
     * sola se llama con una lista de un elemento.
     *
     * Ojo con los que **no vienen** en el resultado de las consultas en lote: una receta sin
     * ingredientes no aparece en la de costos, y hay que tomarla como 0. Lo mismo con los
     * trozos, que caen a 1 —el valor con que se siembra la receta— para que ninguna división
     * pueda reventar.
     */
    suspend fun obtenerDatosCalculo(recetaIds: List<Long>): Map<Long, DatosCalculoReceta> {
        if (recetaIds.isEmpty()) return emptyMap()

        val costos = costosDe(recetaIds)
        val trozos = dao.trozosDeVariasRecetas(recetaIds).associate { it.recetaId to it.trozos }
        val precios = dao.preciosDeVariasRecetas(recetaIds).groupBy { it.recetaId }

        return recetaIds.mapNotNull { id ->
            val receta = dao.obtener(id) ?: return@mapNotNull null
            id to DatosCalculoReceta(
                recetaId = id,
                titulo = receta.titulo,
                costoTotal = costos[id] ?: 0.0,
                trozos = trozos[id] ?: 1,
                precios = precios[id].orEmpty().map { it.aVigente() }
            )
        }.toMap()
    }

    /**
     * Cambia cuál de los precios de la receta alimenta las cifras automáticas (8.6).
     *
     * Devuelve el motivo del rechazo, o `null` si se pudo.
     *
     * **Revisa antes de escribir.** Si el precio elegido pierde plata no se toca nada, así
     * que "que la acción se cancele y vuelva al valor que tenía" es simplemente no haber
     * escrito: la referencia anterior sigue siendo la que era, sin ningún paso de deshacer.
     */
    suspend fun elegirPrecioDeReferencia(recetaId: Long, precioId: Long): String? {
        val datos = obtenerDatosCalculo(listOf(recetaId))[recetaId]
            ?: return "Esa receta ya no existe"
        val elegido = dao.obtenerPrecios(recetaId).firstOrNull { it.id == precioId }
            ?: return "Ese precio ya no existe"

        errorAlElegirReferencia(elegido.aVigente(), datos)?.let { return it }

        dao.fijarPrecioDeReferencia(recetaId, precioId)
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se cambió el precio de referencia de '${datos.titulo}'",
            detalleAdicional = elegido.etiqueta
        )
        return null
    }
}

/** Cómo terminó el intento de crear una receta. */
sealed interface ResultadoCrearReceta {
    data class Creada(val recetaId: Long) : ResultadoCrearReceta
    data class NoValido(val motivo: String) : ResultadoCrearReceta
}
