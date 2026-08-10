package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.AlmacenDao
import com.sandyyera.reposteria.data.db.dao.ArticuloConValor
import com.sandyyera.reposteria.data.db.entidades.ArticuloDeAlmacen
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
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
    private val ingredientes: IngredienteRepositorio,
    private val historial: HistorialRepositorio
) {

    /** Todo el almacén con su valor, avisando cuando cambie. La que usa la pantalla. */
    fun observarTodo(): Flow<List<ArticuloConValor>> = dao.observarTodo()

    /**
     * Qué ingredientes del catálogo ya están en el almacén.
     *
     * **Es privada**: desde 14.5 el cuadro de agregar no elige de una lista sino que escribe un
     * nombre, así que no hay nada que "dejar de ofrecer" — la comprobación pasó a ser un rechazo
     * al guardar. El índice único de la tabla lo hace cumplir de verdad; esto es para poder
     * explicarlo con una frase en vez de reventar.
     */
    private suspend fun ingredientesYaGuardados(): Set<Long> =
        dao.ingredientesYaEnElAlmacen().toSet()

    /**
     * Agrega algo al almacén, **creando su ingrediente si no existía** (14.5).
     *
     * Es una sola función y no dos porque desde afuera es una sola acción — "anotar algo que
     * tengo" — y cuál de los dos casos es se sabe recién al buscar el nombre. Sandy lo pidió
     * así: *"cuando crees un producto en almacén, irá automáticamente a ingredientes"*.
     *
     * [esObjeto] dice si se cuenta por unidad (una caja) o por gramo, y [vaEnRecetas] si se
     * ofrece al armar una receta. **Son dos preguntas distintas**: una caja de torta es un objeto
     * y sí se anota en la receta; una vela decorativa es un objeto y no.
     *
     * **Si el ingrediente ya existía y esto lo cambiaría, no lo pisa: devuelve
     * [ResultadoAgregarAlAlmacen.YaExisteConCambios] para que se pregunte primero.** Son tres
     * cosas que pueden cambiar —el precio, la unidad y si va en recetas— y las tres se aplican
     * juntas al confirmar: antes solo se escribía el precio, así que marcar "se cuenta por
     * unidad" en algo que ya existía no hacía nada. Cambiar el precio o la unidad mueve el costo
     * de las recetas que lo usan y no se deshace, que es la misma confirmación de 7.2. Con
     * [reemplazarElPrecio] en `true` se vuelve a llamar y ahí sí se escribe.
     *
     * **Todo lo que puede fallar se revisa antes de escribir nada**, que es la regla de todos los
     * repositorios de esta app. El orden importa y ya se había roto acá: comprobar "ya está en el
     * almacén" *después* de resolver el precio hacía dos cosas mal — preguntaba por un precio para
     * una operación que iba a fallar igual, y con [reemplazarElPrecio] llegaba a **escribir el
     * precio nuevo** en el catálogo antes de devolver el rechazo, moviendo el costo de todas las
     * recetas por una acción que la app decía que no se hizo.
     */
    suspend fun agregar(
        nombre: String,
        esObjeto: Boolean,
        vaEnRecetas: Boolean,
        cantidad: Double,
        valor: Double,
        detalles: String?,
        reemplazarElPrecio: Boolean = false
    ): ResultadoAgregarAlAlmacen {
        val limpio = nombre.trim()
        errorEnNombreEscrito(limpio)?.let { return ResultadoAgregarAlAlmacen.NoSePudo(it) }
        if (cantidad < 0) {
            return ResultadoAgregarAlAlmacen.NoSePudo("No puede quedar una cantidad negativa")
        }

        // Antes que nada: si ya está en el almacén no hay nada que hacer, y preguntar por el
        // precio primero sería preguntar por una operación que va a fallar igual.
        val existente = ingredientes.buscarParecido(limpio)
        if (existente != null && existente.id in ingredientesYaGuardados()) {
            return ResultadoAgregarAlAlmacen.NoSePudo(
                "'${existente.nombre}' ya está en el almacén. Tócalo para cambiar la cantidad."
            )
        }

        val ingredienteId = when {
            existente == null -> {
                when (val creado = ingredientes.crear(limpio, valor, esObjeto, vaEnRecetas)) {
                    is ResultadoGuardarIngrediente.Guardado -> creado.id
                    is ResultadoGuardarIngrediente.NoValido ->
                        return ResultadoAgregarAlAlmacen.NoSePudo(creado.motivo)
                    // No debería pasar: se acaba de comprobar que no existe. Si pasara, seguir
                    // con el que hay es mejor que crear un repetido.
                    is ResultadoGuardarIngrediente.YaExiste -> creado.existente.id
                }
            }
            // Algo del ingrediente cambiaría y nadie confirmó todavía: no se escribe nada.
            !reemplazarElPrecio && hayQueConfirmar(existente, esObjeto, vaEnRecetas, valor) ->
                return ResultadoAgregarAlAlmacen.YaExisteConCambios(
                    existente = existente,
                    valorNuevo = valor,
                    esObjetoNuevo = esObjeto,
                    vaEnRecetasNuevo = vaEnRecetas,
                    usadoEnRecetas = ingredientes.recetasAfectadasPorBorrar(existente.id).size
                )

            else -> {
                // **Se aplican las tres cosas y no solo el precio.** Antes solo se escribía el
                // valor, así que marcar "se cuenta por unidad" en algo que ya existía no hacía
                // nada — el ingrediente seguía en gramos y no había forma de arreglarlo desde
                // acá. Lo reportó Sandy.
                if (hayQueConfirmar(existente, esObjeto, vaEnRecetas, valor)) {
                    ingredientes.actualizar(
                        existente.copy(
                            valorPorGramo = valor,
                            esObjeto = esObjeto,
                            vaEnRecetas = vaEnRecetas
                        )
                    )
                }
                existente.id
            }
        }

        dao.insertar(
            ArticuloDeAlmacen(
                ingredienteId = ingredienteId,
                cantidad = cantidad,
                detalles = detalles?.trim()?.takeIf { it.isNotBlank() }
            )
        )
        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.INGREDIENTE,
            descripcion = "Se agregó '$limpio' al almacén"
        )
        return ResultadoAgregarAlAlmacen.Listo
    }

    /**
     * Si anotar esto cambiaría el ingrediente que ya existe, y por lo tanto hay que preguntar.
     *
     * Las tres cosas pesan y no solo el precio: **cambiar la unidad cambia lo que el precio
     * significa**. Un ingrediente que pasa de gramos a unidades tiene el mismo número guardado y
     * de golpe quiere decir otra cosa, y las líneas de receta que lo usan en gramos empiezan a
     * multiplicar por un precio por unidad. Eso no puede pasar en silencio.
     */
    private fun hayQueConfirmar(
        existente: Ingrediente,
        esObjeto: Boolean,
        vaEnRecetas: Boolean,
        valor: Double
    ): Boolean =
        !mismoValor(existente.valorPorGramo, valor) ||
            existente.esObjeto != esObjeto ||
            existente.vaEnRecetas != vaEnRecetas

    /**
     * Si dos precios son el mismo para esta app.
     *
     * Con tolerancia y no con `==` por lo mismo que los gramajes de una firma: los valores pasan
     * por redondeos a 5 decimales, y preguntar "¿reemplazo el precio?" por una diferencia en el
     * sexto decimal sería enseñar a decir que sí sin leer.
     */
    private fun mismoValor(uno: Double, otro: Double): Boolean =
        kotlin.math.abs(uno - otro) < 0.000005

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
     * Cambia las notas de una compra: dónde, cuándo, si estaba en oferta (14.6).
     *
     * **No toca `actualizadoEn`**, al revés que la cantidad: corregir dónde se compró algo no es
     * haber revisado cuánto queda, y mover la fecha por eso haría creer que el stock está al día
     * cuando lo único que se editó fue una nota.
     */
    suspend fun guardarDetalles(articuloId: Long, detalles: String?): Resultado {
        val articulo = dao.obtener(articuloId)
            ?: return Resultado.NoSePudo("Eso ya no está en el almacén")
        dao.actualizar(
            articulo.copy(detalles = detalles?.trim()?.takeIf { it.isNotBlank() })
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

/**
 * Cómo terminó un intento de agregar algo al almacén (14.5).
 *
 * Es un tipo cerrado y no un `Resultado` porque tiene un caso que no es ni éxito ni fracaso:
 * **el ingrediente ya existe con otro precio**. Ahí no hay nada que corregir — los dos números
 * son válidos— sino una decisión que tomar, y quien la toma no es el repositorio.
 */
sealed interface ResultadoAgregarAlAlmacen {
    data object Listo : ResultadoAgregarAlAlmacen

    data class NoSePudo(val motivo: String) : ResultadoAgregarAlAlmacen

    /**
     * Ya existe y anotarlo así lo cambiaría. Hay que mostrar qué y preguntar (7.2, 14.5.1).
     *
     * Lleva el ingrediente entero y lo que quedaría, para poder mostrar **los dos lados juntos**:
     * esta es la única pantalla donde se pueden comparar antes de que el viejo desaparezca.
     *
     * Son tres cambios posibles y no solo el precio. El de la **unidad** es el que más hay que
     * mirar: el número guardado no se mueve pero pasa a significar otra cosa, y las líneas de
     * receta que ya usan ese ingrediente en gramos empiezan a multiplicar por un precio por
     * unidad. Por eso viaja [usadoEnRecetas]: sin ese número el aviso no puede decir a cuánto
     * afecta.
     */
    data class YaExisteConCambios(
        val existente: Ingrediente,
        val valorNuevo: Double,
        val esObjetoNuevo: Boolean,
        val vaEnRecetasNuevo: Boolean,
        val usadoEnRecetas: Int
    ) : ResultadoAgregarAlAlmacen {

        val cambiaElPrecio: Boolean
            get() = kotlin.math.abs(existente.valorPorGramo - valorNuevo) >= 0.000005

        val cambiaLaUnidad: Boolean get() = existente.esObjeto != esObjetoNuevo

        val cambiaSiVaEnRecetas: Boolean get() = existente.vaEnRecetas != vaEnRecetasNuevo

        /**
         * Si el cambio puede mover el costo de recetas que ya existen.
         *
         * Es lo que decide si el aviso lleva el color de advertencia: cambiar la unidad de algo
         * que nadie usa todavía no tiene consecuencias, y pintarlo igual enseñaría a ignorar el
         * aviso cuando sí las tenga.
         */
        val afectaRecetas: Boolean
            get() = usadoEnRecetas > 0 && (cambiaElPrecio || cambiaLaUnidad)
    }
}
