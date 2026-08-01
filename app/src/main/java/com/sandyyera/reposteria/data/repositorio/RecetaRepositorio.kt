package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.RecetaDao
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.RecetaDuracion
import com.sandyyera.reposteria.data.db.entidades.RecetaIngrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaSeccion
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.data.db.entidades.aVigente
import com.sandyyera.reposteria.data.db.entidades.RecetaRendimiento
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.formato.redondearADosDecimales
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import com.sandyyera.reposteria.logica.duracion.UnidadDuracion
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.ModoReescalado
import com.sandyyera.reposteria.logica.moldes.factorEscala
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.busqueda.sonElMismoTexto
import com.sandyyera.reposteria.logica.precios.errorAlElegirReferencia
import com.sandyyera.reposteria.logica.validaciones.NOMBRE_SECCION_POR_DEFECTO
import com.sandyyera.reposteria.logica.validaciones.descripcionDePromocion
import com.sandyyera.reposteria.logica.validaciones.elBloqueDiceAlgo
import com.sandyyera.reposteria.logica.validaciones.errorEnCantidadDeDuracion
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreSeccion
import com.sandyyera.reposteria.logica.validaciones.errorEnNumeroPositivoTexto
import com.sandyyera.reposteria.logica.validaciones.promocionesQueNoCabenEn
import com.sandyyera.reposteria.logica.validaciones.revisarRendimiento
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import com.sandyyera.reposteria.logica.validaciones.esNombreAutomaticoDeSeccion
import com.sandyyera.reposteria.logica.validaciones.errorEnTituloReceta
import com.sandyyera.reposteria.logica.validaciones.nombreSugeridoParaPrimeraSeccion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
     * Busca una receta que se llame igual que [titulo], ignorando tildes y mayúsculas.
     *
     * [exceptoId] sirve al renombrar, para que una receta no choque consigo misma.
     *
     * Se compara en memoria y no con una consulta porque SQLite no sabe ignorar tildes:
     * para la base "limon" y "limón" son títulos distintos. Es el mismo motivo por el que
     * `IngredienteRepositorio.buscarParecido` hace lo mismo.
     *
     * **A diferencia de ingredientes, la tabla no lleva índice único.** Es deliberado: al
     * poner esta regla ya había recetas repetidas guardadas, y un índice único habría
     * obligado a renombrarlas o borrarlas durante la migración — datos reales cambiando
     * sin que nadie lo pida. En vez de eso conviven, y `marcarRepetidos` las señala para
     * que la pantalla no deje seguir trabajando sobre ellas.
     */
    suspend fun buscarParecida(titulo: String, exceptoId: Long? = null): Receta? =
        dao.observarTodas().first()
            .firstOrNull { it.id != exceptoId && sonElMismoTexto(it.titulo, titulo) }

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
        buscarParecida(limpio)?.let { return ResultadoCrearReceta.YaExiste(it) }

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
        buscarParecida(limpio, exceptoId = recetaId)?.let {
            return Resultado.NoSePudo("Ya tienes una receta que se llama '${it.titulo}'")
        }

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

    suspend fun obtenerSecciones(recetaId: Long): List<RecetaSeccion> =
        dao.obtenerSecciones(recetaId)

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
        val unica = dao.obtenerSecciones(recetaId).singleOrNull() ?: return null
        // Si ya la bautizaron, no hay nada que preguntar: volver a proponer el título de
        // la receta pisaría un nombre que la persona eligió.
        if (!esNombreAutomaticoDeSeccion(unica.nombreSeccion)) return null
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

        seccionRepetida(existentes, limpio)?.let { return it }

        // Si la única que había todavía tiene el nombre automático, hay que bautizarla
        // antes de que deje de ser invisible: si no, quedaría un encabezado que dice
        // "General" al lado de "Crema".
        //
        // Si ya tenía nombre propio no se pide nada y **no se toca**: ese nombre lo eligió
        // alguien, y pisarlo con el título de la receta sería perder lo que escribió.
        val unica = existentes.singleOrNull()
        if (unica != null && esNombreAutomaticoDeSeccion(unica.nombreSeccion)) {
            val bautizo = nombreDeLaPrimera?.trim()
                ?: return Resultado.NoSePudo(
                    "Antes de agregar otra sección hay que ponerle nombre a la que ya existe"
                )
            errorEnNombreSeccion(bautizo)?.let { return Resultado.NoSePudo(it) }
            // El bautizo también puede chocar: la sugerencia es el título de la receta, y
            // "Salsa de chocolate" como receta y como sección nueva es un caso corriente.
            if (sonElMismoTexto(bautizo, limpio)) {
                return Resultado.NoSePudo(
                    "Las dos secciones quedarían con el mismo nombre. Cámbiale una."
                )
            }
            dao.actualizarSeccion(unica.copy(nombreSeccion = bautizo))
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

        // Se excluye a sí misma: corregirle una tilde a "Salsa de chocolate" no puede
        // chocar consigo misma.
        val otras = dao.obtenerSecciones(seccion.recetaId).filter { it.id != seccion.id }
        seccionRepetida(otras, limpio)?.let { return it }

        dao.actualizarSeccion(seccion.copy(nombreSeccion = limpio))
        return Resultado.Listo
    }

    /**
     * Si ya hay una sección que se llama así dentro de la misma receta.
     *
     * Compara con `sonElMismoTexto`, o sea ignorando mayúsculas, tildes y espacios
     * sobrantes: dos "Salsa de chocolate" son la misma sección aunque una lleve el acento y
     * la otra no, y tenerlas por separado no significa nada — al leer la receta no hay forma
     * de saber qué va en cada una.
     *
     * **No hay índice único que lo respalde**, igual que con los títulos de receta y por la
     * misma razón: puede haber secciones repetidas guardadas de antes, y un índice obligaría
     * a renombrarlas durante la migración, cambiando datos reales sin que nadie lo pida. La
     * regla vive acá y está cubierta por pruebas. A diferencia de las recetas repetidas, una
     * sección repetida que ya exista **no bloquea nada**: se sigue pudiendo usar y
     * renombrar, solo que renombrarla ya no puede chocar con otra.
     */
    private fun seccionRepetida(existentes: List<RecetaSeccion>, nombre: String): Resultado? {
        val choque = existentes.firstOrNull { sonElMismoTexto(it.nombreSeccion, nombre) }
        return choque?.let {
            Resultado.NoSePudo("Esta receta ya tiene una sección '${it.nombreSeccion}'")
        }
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

    /**
     * El costo de todas las recetas, avisando solo cuando cambia (lo que use la lista).
     *
     * Es la versión que hay que usar para **mostrar** costos. [costosDe] sigue existiendo
     * para quien necesita una foto de un momento —`obtenerDatosCalculo`, la simulación—,
     * pero una pantalla que use la foto se queda mostrándola cuando el mundo ya cambió: eso
     * fue exactamente el bug de borrar ingredientes y ver los costos viejos.
     *
     * El mapa que devuelve **no tiene entrada para las recetas sin ingredientes**, porque el
     * `GROUP BY` no les da fila. Acá no se puede rellenar como en [costosDe] —no se sabe qué
     * recetas hay sin consultarlas—, así que quien lo lea toma lo que falte como 0.
     */
    fun observarCostos(): Flow<Map<Long, Double>> =
        dao.observarCostos().map { filas -> filas.associate { it.recetaId to it.costo } }

    // --- Molde de la receta ---

    /** El rendimiento de una receta: molde, peso final y trozos. */
    suspend fun obtenerRendimiento(recetaId: Long): RecetaRendimiento? =
        dao.obtenerRendimiento(recetaId)

    /**
     * Guarda los trozos y el peso final, sin tocar el molde.
     *
     * Valida con `revisarRendimiento`, que es la misma comprobación que hace la pantalla en
     * cada tecla; acá se repite porque es la que decide de verdad.
     *
     * **Avisa antes de romper una promoción**: bajar los trozos puede dejar imposible una
     * promo por trozo que pedía más de los que van a quedar (6.2, el tope del último trozo).
     * Si eso pasa **no escribe nada** y devuelve el motivo nombrando cuáles — decir "hay
     * promociones que no caben" obligaría a revisarlas todas a mano.
     */
    suspend fun guardarRendimiento(
        recetaId: Long,
        trozosTexto: String,
        pesoFinalTexto: String
    ): Resultado {
        val actual = dao.obtenerRendimiento(recetaId)
            ?: return Resultado.NoSePudo("Esa receta ya no existe")

        val errores = revisarRendimiento(trozosTexto, pesoFinalTexto, actual.usaMolde)
        if (!errores.sirve) {
            return Resultado.NoSePudo(errores.trozos ?: errores.pesoFinal ?: "Revisa los datos")
        }

        val trozos = textoANumero(trozosTexto)!!.toInt()
        val peso = pesoFinalTexto.takeIf { it.isNotBlank() }?.let { textoANumero(it) }

        val vigentes = dao.obtenerPrecios(recetaId).map { it.aVigente() }
        val apretadas = promocionesQueNoCabenEn(trozos, vigentes)
        if (apretadas.isNotEmpty()) {
            val cuales = apretadas.joinToString(", ") { descripcionDePromocion(it) }
            return Resultado.NoSePudo(
                "Con $trozos trozos no caben estas promociones: $cuales. " +
                    "Ajústalas o elimínalas primero."
            )
        }

        dao.actualizarRendimiento(actual.copy(trozos = trozos, pesoFinalG = peso))
        return Resultado.Listo
    }

    /**
     * Define el molde de una receta **por primera vez**, sin reescalar nada.
     *
     * Es la distinción de 9.3 que se paga cara si se confunde: la primera vez no hay molde
     * original contra el cual comparar, así que no hay factor, no se elige modo y **las
     * cantidades quedan tal como se escribieron**. Reescalar es del segundo molde en
     * adelante, y para eso está [reescalarPorMolde], que corta con error si no hay original.
     *
     * [moldeOrigenId] enlaza la receta al molde del catálogo, para que reciba sus
     * correcciones (5.2). En "modo prueba" viene `null` y la receta queda con las medidas
     * pero sin vínculo.
     */
    suspend fun definirMolde(
        recetaId: Long,
        dimensiones: DimensionesMolde,
        moldeOrigenId: Long?
    ): Resultado {
        val actual = dao.obtenerRendimiento(recetaId)
            ?: return Resultado.NoSePudo("Esa receta ya no existe")
        if (actual.usaMolde && actual.dimensiones != null) {
            return Resultado.NoSePudo("Esta receta ya tiene molde: usa el reescalado")
        }

        dao.actualizarRendimiento(
            actual.copy(usaMolde = true, moldeOrigenId = moldeOrigenId, dimensiones = dimensiones)
        )
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se le definió el molde a '${tituloDe(recetaId)}'"
        )
        return Resultado.Listo
    }

    /**
     * Saca el molde de una receta: pasa a ser una de las que se miden por peso.
     *
     * **No borra las medidas que tenía**, solo deja de usarlas: si fue un error y se vuelve
     * atrás, están donde estaban. Sí corta el vínculo, porque una receta sin molde no tiene
     * por qué recibir correcciones de uno.
     */
    suspend fun quitarMolde(recetaId: Long): Resultado {
        val actual = dao.obtenerRendimiento(recetaId)
            ?: return Resultado.NoSePudo("Esa receta ya no existe")
        if (actual.pesoFinalG == null) {
            return Resultado.NoSePudo(
                "Sin molde el peso final es obligatorio: anótalo antes de quitarlo"
            )
        }
        dao.actualizarRendimiento(actual.copy(usaMolde = false, moldeOrigenId = null))
        return Resultado.Listo
    }

    /**
     * Reescala una receta **con molde** al pasarla a otro molde (8.3.1).
     *
     * Devuelve `NoSePudo` en vez de lanzar excepción cuando `factorEscala` rechaza el
     * cambio: la pantalla tiene que poder mostrar el motivo —"Demasiado riesgo. Mejor
     * escale con el otro método"— y una excepción cerraría la app en vez de explicar.
     *
     * Al terminar guarda las medidas nuevas **y el vínculo**: enlazada si se eligió un molde
     * del catálogo, suelta si fue modo prueba, aunque antes estuviera enlazada a otro.
     *
     * **El peso final se reescala junto con los ingredientes** (8.4.1, #4). Antes no se
     * tocaba, y eso dejaba la receta diciéndose cosas contradictorias: el doble de masa y el
     * mismo peso de producto, con lo que el peso por trozo —que sale de dividir uno por
     * otro— quedaba a la mitad de lo que corresponde sin que nada lo avisara. Como la
     * proporción es una estimación y no una medición, queda marcado con
     * `pesoReescaladoSinRevisar` hasta que alguien mire el campo.
     */
    suspend fun reescalarPorMolde(
        recetaId: Long,
        nuevo: DimensionesMolde,
        modo: ModoReescalado,
        moldeOrigenId: Long?
    ): Resultado {
        val actual = dao.obtenerRendimiento(recetaId)
            ?: return Resultado.NoSePudo("Esa receta ya no existe")
        val original = actual.dimensiones?.takeIf { actual.usaMolde }
            ?: return Resultado.NoSePudo(
                "Esta receta todavía no tiene molde: defínelo primero, sin reescalar"
            )

        val factor = runCatching { factorEscala(original, nuevo, modo) }
            .getOrElse { return Resultado.NoSePudo(it.message ?: "No se pudo reescalar") }

        multiplicarIngredientes(recetaId, factor)
        // Sin peso anotado no hay nada que reescalar ni nada que comprobar: la marca se
        // queda apagada en vez de pedir revisar un campo vacío.
        val pesoReescalado = actual.pesoFinalG?.let { redondearADosDecimales(it * factor) }
        dao.actualizarRendimiento(
            actual.copy(
                dimensiones = nuevo,
                moldeOrigenId = moldeOrigenId,
                pesoFinalG = pesoReescalado,
                pesoReescaladoSinRevisar = pesoReescalado != null
            )
        )
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se reescaló '${tituloDe(recetaId)}' a otro molde",
            detalleAdicional = "Factor ${formatearNumero(factor)}"
        )
        return Resultado.Listo
    }

    /**
     * Apaga el aviso de "peso reescalado, compruébalo" (8.4.1, #4).
     *
     * Se llama al **tocar el campo del peso**, haya cambiado o no. Que el número siga siendo
     * el mismo no significa que nadie lo haya revisado: lo que confirma el dato es haberlo
     * mirado, y mirarlo es justamente tocar el campo. Pedir además que se edite obligaría a
     * borrar y reescribir el mismo número para callar un aviso, que es peor que el aviso.
     *
     * No registra evento en el historial: no cambió ningún dato de la receta, solo se leyó.
     */
    suspend fun marcarPesoRevisado(recetaId: Long) {
        val actual = dao.obtenerRendimiento(recetaId) ?: return
        if (!actual.pesoReescaladoSinRevisar) return
        dao.actualizarRendimiento(actual.copy(pesoReescaladoSinRevisar = false))
    }

    /**
     * Reescala una receta **sin molde** (una salsa) para que rinda otro peso.
     *
     * Rechaza las recetas con molde en vez de intentarlo igual: ahí el peso final es
     * opcional, así que el cálculo caería sobre un dato que puede no existir y daría un
     * factor que no significa nada.
     */
    suspend fun reescalarPorPeso(recetaId: Long, nuevoPesoTexto: String): Resultado {
        val actual = dao.obtenerRendimiento(recetaId)
            ?: return Resultado.NoSePudo("Esa receta ya no existe")
        if (actual.usaMolde) {
            return Resultado.NoSePudo("Esta receta usa molde: reescálala eligiendo otro molde")
        }

        errorEnNumeroPositivoTexto(nuevoPesoTexto, "Escribe el peso nuevo")
            ?.let { return Resultado.NoSePudo(it) }
        val nuevoPeso = textoANumero(nuevoPesoTexto)!!

        // El peso final es obligatorio sin molde (6.2); el respaldo a la suma de gramos solo
        // actúa sobre recetas anteriores a esa validación.
        val pesoActual = actual.pesoFinalG ?: dao.sumaGramosIngredientes(recetaId)
        if (pesoActual <= 0) {
            return Resultado.NoSePudo("La receta no tiene peso ni ingredientes: nada que reescalar")
        }

        multiplicarIngredientes(recetaId, nuevoPeso / pesoActual)
        // Acá el peso es lo que se escribió, no lo que salió de una cuenta, así que si
        // quedaba encendido el aviso de "compruébalo" ya no aplica: se acaba de comprobar.
        dao.actualizarRendimiento(
            actual.copy(pesoFinalG = nuevoPeso, pesoReescaladoSinRevisar = false)
        )
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.RECETA,
            descripcion = "Se reescaló '${tituloDe(recetaId)}' a ${formatearNumero(nuevoPeso)} g"
        )
        return Resultado.Listo
    }

    /**
     * Multiplica todas las cantidades por [factor], redondeando a 2 decimales.
     *
     * El redondeo es el mismo que usa el resto de la app (`redondearADosDecimales`): si se
     * guardara la cantidad sin redondear, el subtotal que muestra la pantalla no coincidiría
     * con el que suma la base.
     */
    private suspend fun multiplicarIngredientes(recetaId: Long, factor: Double) {
        dao.obtenerTodosLosIngredientes(recetaId).forEach { item ->
            dao.actualizarCantidad(item.id, redondearADosDecimales(item.cantidadG * factor))
        }
    }

    private suspend fun tituloDe(recetaId: Long): String =
        dao.obtener(recetaId)?.titulo ?: "una receta"

    /**
     * Cambia las medidas del molde guardadas en una receta, sin tocar nada más (5.2).
     *
     * **No toca `moldeOrigenId` ni las cantidades de ingredientes.** Es la que usa
     * `MoldeRepositorio.actualizar` cuando se corrige una medida en el catálogo: corregir
     * un dato mal medido no es cambiar de molde, así que la receta no se reescala. Para lo
     * otro existirá `actualizarDimensionesYVinculoMolde` en la Fase 5.
     *
     * Si la receta no tiene fila de rendimiento no hace nada, en vez de crear una a medias.
     */
    suspend fun actualizarDimensionesMolde(recetaId: Long, dimensiones: DimensionesMolde) {
        val rendimiento = dao.obtenerRendimiento(recetaId) ?: return
        dao.actualizarRendimiento(rendimiento.copy(dimensiones = dimensiones))
    }

    /** Qué recetas siguen enlazadas a un molde del catálogo. */
    suspend fun obtenerRecetasConMoldeOrigen(moldeId: Long): List<Receta> =
        dao.obtenerRecetasConMoldeOrigen(moldeId)

    // --- Duración (8.4) ---

    /**
     * Las duraciones anotadas de una receta, indexadas por tipo.
     *
     * Devuelve un mapa y **no una lista completa con huecos**: los tipos que no están son
     * exactamente los que nadie llenó, y ese es un estado normal —el paso es opcional—. Quien
     * lo lea pone el bloque en blanco.
     */
    suspend fun obtenerDuraciones(recetaId: Long): Map<TipoDuracion, RecetaDuracion> =
        dao.obtenerDuraciones(recetaId).associateBy { it.tipo }

    /**
     * Guarda un bloque de duración, o lo borra si quedó sin decir nada.
     *
     * "Sin decir nada" es un bloque apto y sin cantidad. **Un bloque marcado "no apto" sí
     * dice algo** y se guarda, aunque no tenga números: que algo no se pueda congelar es
     * justamente el dato que uno quiere encontrar después.
     *
     * Borrar en vez de guardar una fila vacía no es un detalle: una fila con `apto = true` y
     * `cantidad = null` es indistinguible de "todavía no lo sé", así que dejarla no aporta y
     * hace que la receta parezca tener el paso llenado.
     */
    suspend fun guardarDuracion(
        recetaId: Long,
        tipo: TipoDuracion,
        apto: Boolean,
        cantidadTexto: String,
        unidad: UnidadDuracion?
    ): Resultado {
        if (obtener(recetaId) == null) return Resultado.NoSePudo("Esa receta ya no existe")

        errorEnCantidadDeDuracion(cantidadTexto, apto)?.let { return Resultado.NoSePudo(it) }

        if (!elBloqueDiceAlgo(apto, cantidadTexto)) {
            dao.eliminarDuracion(recetaId, tipo)
            return Resultado.Listo
        }

        dao.guardarDuracion(
            RecetaDuracion(
                recetaId = recetaId,
                tipo = tipo,
                apto = apto,
                // Con "no apto" se guardan en null a propósito: dejar el número de antes
                // haría que volver a marcarlo apto reviviera un dato que ya nadie confirmó.
                cantidad = if (apto) textoANumero(cantidadTexto)?.toInt() else null,
                unidad = if (apto) unidad else null
            )
        )
        return Resultado.Listo
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

    /** Ya hay una receta que se llama igual. Se devuelve para poder nombrarla en el aviso. */
    data class YaExiste(val existente: Receta) : ResultadoCrearReceta
}
