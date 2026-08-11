package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.AlmacenDao
import com.sandyyera.reposteria.data.db.dao.ArticuloConValor
import com.sandyyera.reposteria.data.db.entidades.ArticuloDeAlmacen
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.logica.almacen.FilaParaDescontar
import com.sandyyera.reposteria.logica.almacen.RecetaHecha
import com.sandyyera.reposteria.logica.almacen.SentidoDelMovimiento
import com.sandyyera.reposteria.logica.almacen.VistaPreviaDelDescuento
import com.sandyyera.reposteria.logica.almacen.conLosNombres
import com.sandyyera.reposteria.logica.almacen.loQueSeGasta
import com.sandyyera.reposteria.logica.almacen.vistaPreviaDelDescuento
import com.sandyyera.reposteria.logica.almacen.resultadoDelMovimiento
import com.sandyyera.reposteria.logica.busqueda.sonElMismoTexto
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreEscrito
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * El inventario: qué hay guardado y cuánto queda (sección 14).
 *
 * Cada función que puede fallar **revisa antes de escribir**, como el resto de los repositorios,
 * así que cancelar no necesita deshacer nada.
 */
class AlmacenRepositorio(
    private val dao: AlmacenDao,
    private val ingredientes: IngredienteRepositorio,
    // Para el descuento por recetas hechas (14.9): es el único que sabe cuánto lleva cada
    // receta. La flecha va en este sentido y no al revés — las recetas no saben del almacén.
    private val recetas: RecetaRepositorio,
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

        // **Ya no se rechaza el negativo** (14.8). Antes esto contestaba "no puede quedar una
        // cantidad negativa", y era la misma idea que recortaba la resta en cero: el número
        // cómodo en vez del verdadero. Un negativo dice cuánto entró sin anotarse, o cuánto pide
        // de más una receta, y las dos cosas son datos. Lo que corresponde es mostrarlo con su
        // aviso, y de eso se encarga la pantalla.
        dao.actualizar(
            articulo.copy(cantidad = cantidad, actualizadoEn = System.currentTimeMillis())
        )
        return Resultado.Listo
    }

    /**
     * Suma o resta sobre lo que ya había (14.8).
     *
     * Existe porque **hasta ahora solo se podía restar**: la calculadora resolvía "usé 300 g" y
     * "compré un kilo más" no tenía dónde escribirse. Lo pidió Sandy tal cual: *"debería poder
     * agregar como quitar ingredientes con claridad"*.
     *
     * La cuenta la hace `resultadoDelMovimiento`, la misma de la vista previa de la pantalla, así
     * que lo que se ve antes de confirmar es exactamente lo que se guarda. [cuanto] llega siempre
     * en positivo y el sentido va aparte, para que un número no cambie de significado según dónde
     * esté escrito.
     */
    suspend fun mover(
        articuloId: Long,
        cuanto: Double,
        sentido: SentidoDelMovimiento
    ): Resultado {
        val articulo = dao.obtener(articuloId)
            ?: return Resultado.NoSePudo("Eso ya no está en el almacén")
        if (cuanto < 0) return Resultado.NoSePudo("Escribe cuánto, en positivo")

        return cambiarCantidad(
            articuloId,
            resultadoDelMovimiento(articulo.cantidad, cuanto, sentido)
        )
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

    // --- Descontar lo que se gastó haciendo recetas (14.9) ---

    /**
     * Qué le pasaría al almacén si se hubieran hecho estas recetas. **No escribe nada.**
     *
     * Va aparte de [descontar] a propósito, y no es ceremonia: esto toca muchas filas de una vez
     * y es lo más destructivo que hace el almacén. Lo que evita el desastre es poder mirar antes,
     * fila por fila, de cuánto se parte y en cuánto queda — incluido lo que va a quedar bajo
     * cero, que es justo lo que Sandy quiere ver y no esconder.
     *
     * Los ingredientes que la receta gasta y **no están anotados** salen por su cuenta en la
     * vista previa, con su nombre traído del catálogo. No es un error —hay cosas que se usan sin
     * llevarles la cuenta— pero callarlo dejaría la impresión de que se descontó todo.
     */
    suspend fun vistaPreviaDeDescontar(hechas: List<RecetaHecha>): VistaPreviaDelDescuento {
        val ids = hechas.map { it.recetaId }.distinct()
        if (ids.isEmpty()) return VistaPreviaDelDescuento(emptyList(), emptyList())

        val gastos = recetas.gastoDeVariasRecetas(ids)
        val seGasta = loQueSeGasta(hechas, gastos)
        val enElAlmacen = dao.observarTodo().first()
            .mapNotNull { fila ->
                FilaParaDescontar(
                    ingredienteId = fila.ingredienteId ?: return@mapNotNull null,
                    nombre = fila.nombre,
                    // Una fila anterior a 14.5 no sabe su unidad. Se toma como gramo, que es lo
                    // que era todo antes de que los objetos existieran.
                    esObjeto = fila.esObjeto ?: false,
                    cantidad = fila.cantidad
                )
            }
        val previa = vistaPreviaDelDescuento(seGasta, enElAlmacen)
        if (previa.sinAnotar.isEmpty()) return previa

        // Los nombres de lo que no está anotado los tiene el catálogo, no el almacén. Se piden
        // solo si hacen falta: lo normal es que esta lista venga vacía.
        val nombres = previa.sinAnotar.associate { falta ->
            falta.ingredienteId to (ingredientes.obtener(falta.ingredienteId)?.nombre ?: falta.nombre)
        }
        return previa.conLosNombres(nombres)
    }

    /**
     * Descuenta de verdad lo que muestra la vista previa.
     *
     * Recibe la previa **ya calculada** y no las recetas otra vez, y eso es deliberado: volver a
     * calcular acá abriría la puerta a que se escriba algo distinto de lo que se mostró — basta
     * que alguien edite una receta en otra pantalla entremedio. Lo que se confirma es lo que se
     * vio.
     *
     * Escribe fila por fila con `cambiarCantidad`, que ya acepta negativos (14.8). Una fila que
     * desapareció entremedio se salta en vez de voltear el resto: el resto del descuento es
     * correcto y perderlo entero sería peor.
     *
     * **Registra un solo evento en el historial y no uno por frasco.** Descontar una tanda es un
     * acto, no veinte, y anotarlo veinte veces taparía el panel de cambios justo con lo que más
     * se repite.
     */
    suspend fun descontar(previa: VistaPreviaDelDescuento, queSeHizo: String): Resultado {
        if (!previa.hayAlgoQueDescontar) {
            return Resultado.NoSePudo("No hay nada anotado que descontar")
        }
        var movidas = 0
        for (fila in previa.filas) {
            val articulo = dao.obtenerPorIngrediente(fila.ingredienteId) ?: continue
            dao.actualizar(
                articulo.copy(
                    cantidad = fila.quedara,
                    actualizadoEn = System.currentTimeMillis()
                )
            )
            movidas++
        }
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.INGREDIENTE,
            descripcion = "Se descontó del almacén lo de $queSeHizo",
            detalleAdicional = "Movió $movidas " + if (movidas == 1) "cosa" else "cosas"
        )
        return Resultado.Listo
    }

    // --- Cambiarle el nombre a algo del almacén (14.10) ---

    /**
     * Intenta ponerle otro nombre a una fila del almacén.
     *
     * **Un nombre en el almacén no es un texto propio de la fila**: es el del ingrediente al que
     * apunta (14.5). Por eso cambiarlo no es una edición sino una de tres cosas, y cuál es
     * depende de si el nombre escrito ya existe en el catálogo:
     *
     * 1. **Existe y está libre** → se une: esta fila pasa a llevar la cuenta de ese ingrediente.
     *    Es lo que pidió Sandy —*"si pongo un nombre que ya está en ingredientes, hace unión"*—
     *    y no hay nada que preguntar, porque es lo único que ese nombre puede significar.
     * 2. **Existe y ya tiene su propia fila de almacén** → no se puede, y es la excepción que
     *    ella misma nombró (*"a excepción de que ya exista previa unión"*). Unir dejaría dos
     *    filas para el mismo ingrediente, y ahí "cuánta harina queda" tendría dos respuestas —
     *    el índice único de la tabla lo impide de todos modos, pero reventar no explica nada.
     * 3. **No existe** → hay dos caminos posibles y se devuelve [HayQueElegir] sin tocar nada.
     *
     * [confirmado] es la respuesta a ese tercer caso y por eso llega `null` la primera vez: sin
     * él, el repositorio elegiría por su cuenta entre renombrar seis recetas y no tocarlas.
     */
    suspend fun renombrar(
        articuloId: Long,
        nombreNuevo: String,
        confirmado: QueHacerConElNombre? = null
    ): ResultadoRenombrarEnAlmacen {
        val limpio = nombreNuevo.trim()
        errorEnNombreEscrito(limpio)?.let {
            return ResultadoRenombrarEnAlmacen.NoSePudo(it)
        }
        val articulo = dao.obtener(articuloId)
            ?: return ResultadoRenombrarEnAlmacen.NoSePudo("Eso ya no está en el almacén")
        val actual = articulo.ingredienteId?.let { ingredientes.obtener(it) }
            ?: return ResultadoRenombrarEnAlmacen.NoSePudo(
                "Esta fila es anterior a la versión 14.5 y no tiene ingrediente"
            )
        if (sonElMismoTexto(actual.nombre, limpio)) {
            return ResultadoRenombrarEnAlmacen.Listo("Se llama igual que antes")
        }

        // El del catálogo se busca **excluyendo al propio**, porque acá el propio ya se descartó
        // arriba y sin excluirlo un cambio de tildes se leería como "ya existe otro igual".
        val yaExiste = ingredientes.buscarParecido(limpio, exceptoId = actual.id)
        if (yaExiste != null) {
            if (yaExiste.id in ingredientesYaGuardados()) {
                return ResultadoRenombrarEnAlmacen.NoSePudo(
                    "'${yaExiste.nombre}' ya tiene su propia fila en el almacén"
                )
            }
            dao.actualizar(articulo.copy(ingredienteId = yaExiste.id))
            historial.registrar(
                tipo = TipoEvento.EDICION,
                entidad = EntidadEvento.INGREDIENTE,
                descripcion = "'${actual.nombre}' del almacén pasó a ser '${yaExiste.nombre}'",
                detalleAdicional = "Se unió con el ingrediente que ya existía"
            )
            return ResultadoRenombrarEnAlmacen.Listo(
                "Ahora lleva la cuenta de '${yaExiste.nombre}'"
            )
        }

        return when (confirmado) {
            null -> ResultadoRenombrarEnAlmacen.HayQueElegir(
                nombreViejo = actual.nombre,
                nombreNuevo = limpio,
                usadoEnRecetas = ingredientes.recetasAfectadasPorBorrar(actual.id).size
            )

            QueHacerConElNombre.RENOMBRAR -> {
                when (val r = ingredientes.actualizar(actual.copy(nombre = limpio))) {
                    is ResultadoGuardarIngrediente.NoValido ->
                        ResultadoRenombrarEnAlmacen.NoSePudo(r.motivo)
                    is ResultadoGuardarIngrediente.YaExiste ->
                        ResultadoRenombrarEnAlmacen.NoSePudo(
                            "'${r.existente.nombre}' ya existe en ingredientes"
                        )
                    is ResultadoGuardarIngrediente.Guardado ->
                        ResultadoRenombrarEnAlmacen.Listo("Se renombró en todas partes")
                }
            }

            QueHacerConElNombre.SEPARAR -> {
                // **Se crea un ingrediente nuevo en vez de dejar la fila sin ninguno.** Que todo
                // lo del almacén tenga su ingrediente es la regla de 14.5, y de ahí salen el
                // precio y la unidad: una fila suelta no sabría ni cuánto vale lo que guarda.
                // "Romper la conexión" es dejar de apuntar a *ese* ingrediente, no quedarse sin.
                val creado = ingredientes.crear(
                    nombre = limpio,
                    valorPorGramo = actual.valorPorGramo,
                    esObjeto = actual.esObjeto,
                    vaEnRecetas = actual.vaEnRecetas
                )
                val nuevoId = when (creado) {
                    is ResultadoGuardarIngrediente.Guardado -> creado.id
                    is ResultadoGuardarIngrediente.YaExiste -> creado.existente.id
                    is ResultadoGuardarIngrediente.NoValido ->
                        return ResultadoRenombrarEnAlmacen.NoSePudo(creado.motivo)
                }
                dao.actualizar(articulo.copy(ingredienteId = nuevoId))
                historial.registrar(
                    tipo = TipoEvento.CREACION,
                    entidad = EntidadEvento.INGREDIENTE,
                    descripcion = "'$limpio' se separó de '${actual.nombre}'",
                    detalleAdicional = "'${actual.nombre}' sigue igual en sus recetas"
                )
                ResultadoRenombrarEnAlmacen.Listo(
                    "Se creó '$limpio' aparte. '${actual.nombre}' no se tocó"
                )
            }
        }
    }

    // --- Si va en recetas o no, desde el almacén (14.11) ---

    /**
     * A qué recetas afectaría sacar este ingrediente del catálogo de recetas.
     *
     * Se pregunta **antes** de mostrar la confirmación, no después de aceptarla: el aviso de 7.1
     * sirve porque nombra las recetas, y un "¿seguro?" sin la lista es un botón que se aprieta.
     */
    suspend fun recetasQueUsan(ingredienteId: Long): List<Receta> =
        ingredientes.recetasAfectadasPorBorrar(ingredienteId)

    /**
     * Cambia las dos cosas que definen **qué es** algo del almacén: su unidad y si va en
     * recetas (14.11).
     *
     * Van juntas en una sola función y no en dos porque **se confirman juntas**: las dos tocan
     * las mismas recetas, y preguntar dos veces por la misma lista de recetas afectadas sería
     * pedir la misma autorización partida en dos.
     *
     * [esObjeto] es lo que Sandy pidió después: *"debería poder cambiar gramos a unidad, en
     * almacén, caso que me haya equivocado"*. Es el cambio **más peligroso de los dos**, y no se
     * nota mirándolo: el número del precio no se mueve pero pasa a significar otra cosa, y las
     * líneas de receta que lo usan en gramos empiezan a multiplicar por un precio por unidad. Lo
     * que ya está escrito en una receta **conserva su número**, así que esas líneas hay que
     * revisarlas; la app no puede convertirlas sola porque no sabe cuántos gramos pesa una
     * unidad. Es la misma advertencia que ya daba el cuadro de agregar (14.5.1).
     *
     * [vaEnRecetas] apagado **saca de verdad sus líneas de las recetas**, no solo lo esconde del
     * buscador: dejarlo dentro de tres recetas mientras la app dice que no es un ingrediente
     * sería sostener dos verdades a la vez.
     *
     * **No pregunta nada.** Quién decide es la pantalla, que tiene que haber mostrado antes
     * [recetasQueUsan] con los nombres a la vista (7.1). Acá solo se escribe.
     */
    suspend fun cambiarQueEs(
        ingredienteId: Long,
        esObjeto: Boolean,
        vaEnRecetas: Boolean
    ): Resultado {
        val ingrediente = ingredientes.obtener(ingredienteId)
            ?: return Resultado.NoSePudo("Ese ingrediente ya no existe")
        val cambiaUnidad = ingrediente.esObjeto != esObjeto
        val saleDeRecetas = ingrediente.vaEnRecetas && !vaEnRecetas
        if (!cambiaUnidad && ingrediente.vaEnRecetas == vaEnRecetas) return Resultado.Listo

        if (saleDeRecetas) ingredientes.quitarDeLasRecetas(ingredienteId)

        return when (
            val r = ingredientes.actualizar(
                ingrediente.copy(esObjeto = esObjeto, vaEnRecetas = vaEnRecetas)
            )
        ) {
            is ResultadoGuardarIngrediente.Guardado -> {
                // Un solo evento con las dos frases: es un acto —"corregí qué es esto"— y
                // partirlo en dos llenaría el historial con la mitad de una decisión.
                val frases = buildList {
                    if (cambiaUnidad) {
                        add(if (esObjeto) "ahora se cuenta por unidad" else "ahora se pesa en gramos")
                    }
                    if (saleDeRecetas) add("dejó de ir en recetas")
                    if (!ingrediente.vaEnRecetas && vaEnRecetas) add("vuelve a ir en recetas")
                }
                historial.registrar(
                    tipo = TipoEvento.EDICION,
                    entidad = EntidadEvento.INGREDIENTE,
                    descripcion = "'${ingrediente.nombre}': ${frases.joinToString(", ")}"
                )
                Resultado.Listo
            }
            is ResultadoGuardarIngrediente.NoValido -> Resultado.NoSePudo(r.motivo)
            is ResultadoGuardarIngrediente.YaExiste ->
                Resultado.NoSePudo("Ya existe otro con ese nombre")
        }
    }
}


/** Las dos salidas de un nombre nuevo que no existe en el catálogo (14.10). */
enum class QueHacerConElNombre {
    /** Era el mismo y estaba mal escrito: se renombra, y con él todas sus recetas. */
    RENOMBRAR,

    /** Resultó ser otra cosa: se crea un ingrediente nuevo y el de antes queda intacto. */
    SEPARAR
}

/**
 * Cómo terminó un intento de renombrar algo del almacén (14.10).
 *
 * Es un tipo cerrado y no un `Resultado` porque el caso normal **no es ni éxito ni fracaso**: un
 * nombre nuevo puede querer decir dos cosas muy distintas y solo Sandy sabe cuál. Lo pidió así:
 * *"editar el nombre a otro que no es el del ingrediente debería preguntarme si deseo renombrar
 * o romper la conexión"*.
 */
sealed interface ResultadoRenombrarEnAlmacen {
    /** El nombre quedó. [comoQuedo] dice qué se hizo, para poder contarlo en la franja de abajo. */
    data class Listo(val comoQuedo: String) : ResultadoRenombrarEnAlmacen

    data class NoSePudo(val motivo: String) : ResultadoRenombrarEnAlmacen

    /**
     * El nombre nuevo no existe en el catálogo, así que hay dos caminos y hay que preguntar.
     *
     * - **Renombrar** el ingrediente: era el mismo y estaba mal escrito. Toca todas las recetas
     *   que lo usan, porque el nombre es uno solo.
     * - **Separar**: este frasco resultó ser otra cosa. Se crea un ingrediente nuevo con el
     *   nombre escrito y la fila del almacén pasa a apuntar ahí; el de antes queda intacto con
     *   sus recetas.
     *
     * [usadoEnRecetas] es lo que hace que la pregunta se pueda contestar: renombrar algo que no
     * usa ninguna receta no tiene consecuencias, y renombrar lo que usan seis es otra decisión.
     */
    data class HayQueElegir(
        val nombreViejo: String,
        val nombreNuevo: String,
        val usadoEnRecetas: Int
    ) : ResultadoRenombrarEnAlmacen
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
