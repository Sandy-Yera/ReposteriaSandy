package com.sandyyera.reposteria.ui.moldes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sandyyera.reposteria.data.db.entidades.Molde
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.repositorio.MoldeRepositorio
import com.sandyyera.reposteria.data.repositorio.ResultadoGuardarMolde
import com.sandyyera.reposteria.logica.busqueda.filtrarPor
import com.sandyyera.reposteria.logica.formato.formatearMientrasSeEscribe
import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.FormaDelCorte
import com.sandyyera.reposteria.logica.moldes.OpcionDeReparto
import com.sandyyera.reposteria.logica.moldes.avisoDeMedidasDeCorteAjenas
import com.sandyyera.reposteria.logica.moldes.corteSugerido
import com.sandyyera.reposteria.logica.moldes.medidaDelTrozo
import com.sandyyera.reposteria.logica.moldes.opcionesDeReparto
import com.sandyyera.reposteria.logica.moldes.queHacenLasMedidasDeCorte
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.validaciones.CampoDeMolde
import com.sandyyera.reposteria.logica.validaciones.ErroresMolde
import com.sandyyera.reposteria.logica.validaciones.camposDe
import com.sandyyera.reposteria.logica.validaciones.conElCorte
import com.sandyyera.reposteria.logica.validaciones.dimensionesDesde
import com.sandyyera.reposteria.logica.validaciones.revisarMolde
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cómo se lee cada forma en la pantalla. */
fun nombreDeLaForma(forma: TipoFormaMolde): String = when (forma) {
    TipoFormaMolde.RECTANGULO -> "Rectángulo"
    TipoFormaMolde.CUADRADO -> "Cuadrado"
    TipoFormaMolde.CIRCULO -> "Círculo"
    TipoFormaMolde.TRIANGULO -> "Triángulo"
    TipoFormaMolde.EXOTICO -> "Otra forma"
}

/**
 * El texto de ayuda de una forma, o `null` si no necesita.
 *
 * Solo la exótica lo lleva: es la única donde lo que se pide no se mide con una regla, y
 * sin explicación nadie adivina que el volumen se saca llenando el molde con agua.
 */
fun ayudaDeLaForma(forma: TipoFormaMolde): String? = when (forma) {
    TipoFormaMolde.EXOTICO ->
        "Para un molde con forma irregular (estrella, corazón): llénalo de agua, " +
            "viértela en una jarra medidora y anota los mililitros. Son los mismos cm³."
    else -> null
}

/** Qué hay abierto encima del catálogo de moldes. */
sealed interface DialogoMolde {

    data object Ninguno : DialogoMolde

    /**
     * El formulario de alta o de edición.
     *
     * [editando] es `null` al crear. Las medidas van en un mapa por la misma razón que en
     * `ErroresMolde`: cuáles existen depende de la forma, y lo que se escribió para una
     * forma que después se cambió no se borra — si vuelve a elegirla, ahí está.
     */
    data class Formulario(
        val editando: Molde? = null,
        val nombre: String = "",
        val forma: TipoFormaMolde? = null,
        val medidas: Map<CampoDeMolde, String> = emptyMap(),
        val tocado: Boolean = false,
        val guardando: Boolean = false,
        val rechazo: String? = null,

        // --- Cómo se corta (9.4) ---
        /** `null` = el que corresponda a la forma. Solo hay que elegirlo en dos formas. */
        val corte: FormaDelCorte? = null,
        val largoDeCorte: String = "",
        val anchoDeCorte: String = "",
        /**
         * En cuántos trozos se está imaginando el corte, **solo para ver cómo quedaría**.
         *
         * No se guarda: en cuántos trozos rinde algo es de la receta y no del molde (el mismo
         * molde da 6 porciones de torta y 12 de brownie). Existe porque Sandy pidió poder ver
         * los cortes *"en el mismo acto que escribo, sin siquiera aceptarlo"*, y el tamaño del
         * trozo no se puede calcular sin saber cuántos son.
         */
        val trozosDePrueba: String = ""
    ) : DialogoMolde {

        /** Las medidas que hay que pedir ahora mismo. Vacía mientras no haya forma elegida. */
        val campos: List<CampoDeMolde> get() = forma?.let { camposDe(it) } ?: emptyList()

        /**
         * El corte que se va a guardar: el elegido, o el que sugiere la forma.
         *
         * Es la misma regla que `corteEfectivoDe` aplica sobre un molde **ya guardado**; acá
         * no se puede llamar a aquella porque todavía no hay `DimensionesMolde` que pasarle
         * —se está escribiendo—, así que lo único compartido posible es `corteSugerido`, que
         * es donde vive la decisión de verdad. Hay una prueba que compara las dos respuestas.
         */
        val corteEfectivo: FormaDelCorte? get() = corte ?: corteSugerido(forma)

        /**
         * Si se ofrece elegir cómo se corta.
         *
         * **Se ofrece siempre que haya forma elegida**, incluidas las tres donde `corteSugerido`
         * ya sabe la respuesta. Antes solo aparecía en el triángulo y el exótico, con el
         * argumento de que preguntar lo obvio es pedir que confirmen algo que nadie discute — y
         * eso era cierto salvo por un detalle: **la sugerencia no siempre acierta**. Un molde
         * rectangular que se corta en cuñas existe, y hasta ahora no había forma de decirlo.
         * Lo pidió Sandy: *"me gustaría elegirlo siempre, incluso en los automáticos"*.
         *
         * Sigue viniendo **pre-elegida** la sugerencia, así que quien no tenga nada que
         * corregir no toca nada: la diferencia es entre no poder y no tener que.
         */
        val hayQuePreguntarElCorte: Boolean get() = forma != null

        /**
         * Si se ofrecen las dos medidas del corte escritas a mano.
         *
         * **En cualquier forma que se corte en cuadrícula**, y no solo donde hacen falta. En el
         * triángulo y el exótico son la única manera de saber el tamaño del trozo; en el
         * rectángulo y el cuadrado son opcionales, pero tienen que estar igual: son la forma de
         * mandar sobre la suposición del lado más largo (ver [laFormaYaDaLosLados]).
         */
        val pideMedidasDeCorte: Boolean
            get() = corteEfectivo == FormaDelCorte.CUADRICULA && forma != null

        /**
         * Si esta forma **ya da** los lados por su cuenta, o sea si anotarlos es opcional.
         *
         * Cambia solo el texto de ayuda, y ese texto es toda la diferencia: en un triángulo,
         * sin anotarlos la app no puede decir nada; en un rectángulo son la forma de **mandar
         * sobre la suposición** — se corta el lado más largo salvo que alguien diga otra cosa,
         * y ese "otra cosa" se escribe acá. Lo preguntó Sandy con un molde de 8 × 4: quería
         * cortar el 4 y no tenía cómo decirlo.
         */
        val laFormaYaDaLosLados: Boolean
            get() = forma == TipoFormaMolde.RECTANGULO || forma == TipoFormaMolde.CUADRADO

        private val errores: ErroresMolde
            get() = revisarMolde(nombre, forma, medidas, largoDeCorte, anchoDeCorte)

        /** Lo que esté mal en las medidas del corte, que son opcionales. */
        val errorCorte: String? get() = errores.corte.takeIf { tocado }

        /**
         * [rechazo] va primero, igual que en las secciones: es lo que contestó el
         * repositorio (un nombre repetido) y el aviso tiene que verse junto al campo, no en
         * la franja de abajo que el teclado tapa.
         */
        val errorNombre: String? get() = rechazo ?: errores.nombre.takeIf { tocado }

        val errorForma: String? get() = errores.forma.takeIf { tocado }

        /** El error de una medida, o `null`. Antes de tocar nada no se reta por vacíos. */
        fun errorDe(campo: CampoDeMolde): String? = errores.medidas[campo].takeIf { tocado }

        val puedeGuardar: Boolean get() = errores.sirve && !guardando

        /**
         * El área y el volumen que van quedando, o `null` mientras falte algo.
         *
         * Se muestran **mientras se escribe** y no al guardar: son la única forma de darse
         * cuenta ahí mismo de que se anotó un 3 en vez de un 30. No dependen del nombre,
         * a propósito — ver `dimensionesDesde`.
         */
        val vistaPrevia: Pair<Double, Double>?
            get() = dimensionesDesde(forma, medidas)?.let { it.areaCm2 to it.volumenCm3 }

        /**
         * Las dimensiones que van quedando **con el corte ya pegado**, para la vista previa.
         *
         * Usa `conElCorte`, la misma función que el guardado, y no un `copy` escrito acá: ese
         * fue el hueco por el que el corte se perdía al medir un molde dentro de una receta
         * (9.4.1). Si la previa se armara distinto de lo que se guarda, mostraría un corte y
         * guardaría otro.
         */
        private val dimensionesConCorte: DimensionesMolde?
            get() = dimensionesDesde(forma, medidas)
                ?.let { conElCorte(it, corteEfectivo, largoDeCorte, anchoDeCorte) }

        /**
         * Cómo quedarían los trozos, **mientras se escribe** (9.4.3).
         *
         * Lo pidió Sandy: *"al momento de crear los moldes, debería decirme cómo quedarían los
         * cortes; puedo visualizarlos en el mismo acto que escribo, sin siquiera aceptarlo o
         * ponerlos en una receta"*. Es el mismo argumento que el área y el volumen en vivo — la
         * forma de darse cuenta ahí mismo de que el molde no se corta como uno creía.
         *
         * Viene vacía mientras falte algo: sin medidas completas, sin un número de trozos
         * escrito, o con un corte que no es cuadrícula. Con cuñas no hay nada que repartir y el
         * tamaño sale solo de los trozos, así que eso lo dice [medidaDeLaPrueba].
         */
        val repartosDeLaPrueba: List<OpcionDeReparto>
            get() {
                val d = dimensionesConCorte ?: return emptyList()
                val cuantos = trozosDePrueba.toIntOrNull() ?: return emptyList()
                // El molde no elige nada —el reparto se decide en la receta, que es donde viven
                // los trozos—, así que no hay reparto anotado que pasarle. El `elegido` que
                // vuelve marca el que la app usaría, que acá es información y no una decisión.
                return opcionesDeReparto(d, cuantos, trozosALoLargo = null, ::formatearNumero)
            }

        /**
         * Qué está haciendo el par de medidas de corte anotadas, o `null` si no hay ninguna.
         *
         * El campo **no es neutro** y hasta ahora no lo decía: escribir los dos lados apaga el
         * reparto más parejo y deja mandando el orden. Sandy los llenó con las medidas del
         * propio molde *"prácticamente porque no entiendo qué va ahí"*, sin saber que con eso
         * cambiaba el resultado.
         */
        val explicacionDelCorte: String?
            get() = dimensionesConCorte?.let { queHacenLasMedidasDeCorte(it, ::formatearNumero) }

        /**
         * El aviso de que lo anotado para cortar no son los lados de este molde (9.4.4).
         *
         * Responde "¿qué pasa si pongo un número menor?" donde sirve: al lado del campo. No
         * bloquea —hay bordes que no se cortan— pero tampoco deja que pase callado.
         */
        val avisoDelCorte: String?
            get() = dimensionesConCorte?.let { avisoDeMedidasDeCorteAjenas(it, ::formatearNumero) }

        /**
         * El tamaño del trozo con lo escrito, para los cortes que no son cuadrícula.
         *
         * En cuñas la respuesta es una sola —los grados— y no hay reparto que elegir, así que
         * mostrar una lista de una opción sería pedir que elijan lo único que hay.
         */
        val medidaDeLaPrueba: String?
            get() {
                val d = dimensionesConCorte ?: return null
                if (corteEfectivo == FormaDelCorte.CUADRICULA) return null
                val cuantos = trozosDePrueba.toIntOrNull()?.takeIf { it >= 1 } ?: return null
                return medidaDelTrozo(d, corteEfectivo, cuantos, formatear = ::formatearNumero)
            }
    }

    /**
     * La advertencia previa a borrar (6.3).
     *
     * [recetasAfectadas] es `null` mientras se consulta y lista vacía cuando no lo usa
     * ninguna receta, misma distinción que en ingredientes. Acá el aviso es más suave a
     * propósito: borrar un molde **no rompe** esas recetas, solo las desenlaza.
     */
    data class ConfirmarBorrado(
        val molde: Molde,
        val recetasAfectadas: List<Receta>? = null,
        val borrando: Boolean = false
    ) : DialogoMolde
}

/** Lo que el catálogo de moldes necesita para dibujarse. */
data class EstadoMoldes(
    val visibles: List<Molde> = emptyList(),
    val hayMoldes: Boolean = false,
    val busqueda: String = "",
    val mensaje: String? = null,
    val cargando: Boolean = true
) {
    val catalogoVacio: Boolean get() = !cargando && !hayMoldes
    val busquedaSinResultados: Boolean get() = hayMoldes && visibles.isEmpty()
}

/**
 * El cerebro del catálogo de moldes (9.2).
 *
 * Mismo patrón que ingredientes y recetas. Lo propio de acá es que el formulario cambia de
 * forma según lo que se elija: qué campos pedir sale de `camposDe`, en `logica/`, y no de
 * un `when` escrito en el Composable — así la pantalla y la validación no pueden discrepar.
 */
class MoldesViewModel(
    private val repositorio: MoldeRepositorio
) : ViewModel() {

    private val busqueda = MutableStateFlow("")
    private val mensaje = MutableStateFlow<String?>(null)
    private val _dialogo = MutableStateFlow<DialogoMolde>(DialogoMolde.Ninguno)

    /**
     * Lo que hay abierto encima, por su propio canal.
     *
     * Por la misma razón de siempre (12.2.1): el formulario tiene cinco campos de texto, y
     * un campo que recibe su valor con retraso termina con el cursor donde no va.
     */
    val dialogo: StateFlow<DialogoMolde> = _dialogo

    val estado: StateFlow<EstadoMoldes> = combine(
        repositorio.observarTodos(),
        busqueda,
        mensaje
    ) { todos, textoBuscado, mensajeActual ->
        EstadoMoldes(
            visibles = filtrarPor(todos, textoBuscado) { it.nombre },
            hayMoldes = todos.isNotEmpty(),
            busqueda = textoBuscado,
            mensaje = mensajeActual,
            cargando = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EstadoMoldes()
    )

    fun buscar(texto: String) {
        busqueda.value = texto
    }

    fun abrirAlta() {
        _dialogo.value = DialogoMolde.Formulario()
    }

    /**
     * Abre el formulario con lo que ya tiene el molde.
     *
     * Las medidas vuelven a texto con `formatearNumero`, que es el mismo formato que
     * `textoANumero` sabe leer de vuelta: así editar y guardar sin cambiar nada no altera
     * ningún número.
     */
    fun abrirEdicion(molde: Molde) {
        val d = molde.dimensiones
        val escritas = mapOf(
            CampoDeMolde.LARGO to d.largoCm,
            CampoDeMolde.ANCHO to d.anchoCm,
            CampoDeMolde.LADO to d.ladoCm,
            CampoDeMolde.DIAMETRO to d.diametroCm,
            CampoDeMolde.BASE_TRIANGULO to d.baseTrianguloCm,
            CampoDeMolde.ALTURA_TRIANGULO to d.alturaTrianguloCm,
            CampoDeMolde.VOLUMEN_EXOTICO to d.volumenExoticoCm3,
            CampoDeMolde.ALTURA_MOLDE to d.alturaMoldeCm
        ).mapNotNull { (campo, valor) -> valor?.let { campo to formatearNumero(it) } }.toMap()

        _dialogo.value = DialogoMolde.Formulario(
            editando = molde,
            nombre = molde.nombre,
            forma = d.tipoForma,
            medidas = escritas,
            tocado = true,
            corte = d.formaDelCorte,
            largoDeCorte = d.largoDeCorteCm?.let { formatearNumero(it) }.orEmpty(),
            anchoDeCorte = d.anchoDeCorteCm?.let { formatearNumero(it) }.orEmpty()
        )
    }

    fun cambiarNombre(texto: String) = enFormulario {
        it.copy(nombre = texto, tocado = true, rechazo = null)
    }

    /**
     * Cambia la forma **sin borrar lo ya escrito** para las otras.
     *
     * Quien probó "círculo", anotó el diámetro y pasa a "cuadrado" para comparar, al volver
     * encuentra su diámetro donde lo dejó. Lo que no se pide para la forma actual no se
     * valida ni se guarda —eso lo resuelven `revisarMolde` y `dimensionesDesde`—, así que
     * conservarlo no cuesta nada y ahorra volver a medir.
     */
    fun elegirForma(forma: TipoFormaMolde) = enFormulario {
        it.copy(forma = forma, tocado = true)
    }

    fun elegirCorte(corte: FormaDelCorte) = enFormulario { it.copy(corte = corte, tocado = true) }

    /**
     * Cambia en cuántos trozos se está imaginando el corte, solo para la vista previa (9.4.3).
     *
     * **No se guarda en ninguna parte y es a propósito.** Lo pidió Sandy así: *"puedo
     * visualizarlos en el mismo acto que escribo, sin siquiera aceptarlo o ponerlos en una
     * receta"*. En cuántos trozos rinde algo es de la **receta** y no del molde —el mismo
     * molde da 6 porciones de torta y 12 de brownie—, así que guardarlo acá sería inventar un
     * segundo lugar donde ese número puede quedar viejo.
     */
    fun cambiarTrozosDeLaPrueba(texto: String) = enFormulario {
        it.copy(trozosDePrueba = texto.filter(Char::isDigit).take(3))
    }

    fun cambiarLargoDeCorte(texto: String) = enFormulario {
        it.copy(largoDeCorte = formatearMientrasSeEscribe(texto), tocado = true)
    }

    fun cambiarAnchoDeCorte(texto: String) = enFormulario {
        it.copy(anchoDeCorte = formatearMientrasSeEscribe(texto), tocado = true)
    }

    fun cambiarMedida(campo: CampoDeMolde, texto: String) = enFormulario {
        // Por el mismo camino que el resto de los campos numéricos: el punto de mil lo pone
        // el ViewModel, no el Composable.
        it.copy(medidas = it.medidas + (campo to formatearMientrasSeEscribe(texto)), tocado = true)
    }

    fun guardar() {
        val formulario = _dialogo.value as? DialogoMolde.Formulario ?: return
        if (!formulario.puedeGuardar) return

        _dialogo.value = formulario.copy(guardando = true, tocado = true)

        viewModelScope.launch {
            val editando = formulario.editando
            val resultado = if (editando == null) {
                repositorio.crear(
                    nombre = formulario.nombre,
                    forma = formulario.forma,
                    medidas = formulario.medidas,
                    // Se guarda el efectivo y no el elegido: así un molde rectangular queda
                    // con su corte anotado sin que nadie lo haya tocado, y la medida del
                    // trozo aparece sola.
                    corte = formulario.corteEfectivo,
                    largoDeCorteTexto = formulario.largoDeCorte,
                    anchoDeCorteTexto = formulario.anchoDeCorte
                )
            } else {
                repositorio.actualizar(
                    moldeId = editando.id,
                    nombre = formulario.nombre,
                    forma = formulario.forma,
                    medidas = formulario.medidas,
                    corte = formulario.corteEfectivo,
                    largoDeCorteTexto = formulario.largoDeCorte,
                    anchoDeCorteTexto = formulario.anchoDeCorte
                )
            }

            when (resultado) {
                is ResultadoGuardarMolde.Guardado -> {
                    _dialogo.value = DialogoMolde.Ninguno
                    mensaje.value = "Se guardó '${formulario.nombre.trim()}'"
                }
                is ResultadoGuardarMolde.YaExiste -> enFormulario {
                    it.copy(
                        guardando = false,
                        rechazo = "Ya tienes un molde '${resultado.existente.nombre}'"
                    )
                }
                is ResultadoGuardarMolde.NoValido -> enFormulario {
                    // El formulario ya calcula sus propios errores por campo; lo único que
                    // hace falta es dejar de estar guardando y que se vean.
                    it.copy(guardando = false, tocado = true)
                }
            }
        }
    }

    /**
     * Abre la advertencia de inmediato y completa la lista cuando vuelve la consulta.
     *
     * Igual que en ingredientes: esperar la consulta para recién abrir el cuadro haría
     * parecer que el botón no responde.
     */
    fun pedirBorrado(molde: Molde) {
        _dialogo.value = DialogoMolde.ConfirmarBorrado(molde)
        viewModelScope.launch {
            val afectadas = repositorio.recetasAfectadasPorBorrar(molde.id)
            _dialogo.update { actual ->
                // Se comprueba que siga abierto y sea el mismo molde: entre la consulta y
                // la respuesta puede haberse cerrado o abierto otro.
                if (actual is DialogoMolde.ConfirmarBorrado && actual.molde.id == molde.id) {
                    actual.copy(recetasAfectadas = afectadas)
                } else {
                    actual
                }
            }
        }
    }

    fun confirmarBorrado() {
        val aviso = _dialogo.value as? DialogoMolde.ConfirmarBorrado ?: return
        if (aviso.borrando) return

        _dialogo.value = aviso.copy(borrando = true)

        viewModelScope.launch {
            repositorio.confirmarEliminacion(aviso.molde.id)
            _dialogo.value = DialogoMolde.Ninguno
            mensaje.value = "Se eliminó '${aviso.molde.nombre}'"
        }
    }

    fun cerrarDialogo() {
        _dialogo.value = DialogoMolde.Ninguno
    }

    fun mensajeMostrado() {
        mensaje.value = null
    }

    private fun enFormulario(cambio: (DialogoMolde.Formulario) -> DialogoMolde.Formulario) {
        _dialogo.update { actual ->
            if (actual is DialogoMolde.Formulario) cambio(actual) else actual
        }
    }

    companion object {
        fun fabrica(repositorio: MoldeRepositorio): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { MoldesViewModel(repositorio) }
            }
    }
}
