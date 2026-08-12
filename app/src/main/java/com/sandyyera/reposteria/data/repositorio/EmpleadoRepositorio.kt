package com.sandyyera.reposteria.data.repositorio

import com.sandyyera.reposteria.data.db.dao.EmpleadoDao
import com.sandyyera.reposteria.data.db.entidades.Empleado
import com.sandyyera.reposteria.data.db.entidades.EmpleadoRecetaSueldo
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultiple
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultipleDetalle
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.logica.busqueda.sonElMismoTexto
import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ingresoBruto
import com.sandyyera.reposteria.logica.sueldos.RecetaEnLaSimulacion
import com.sandyyera.reposteria.logica.sueldos.SimulacionMultipleResultado
import com.sandyyera.reposteria.logica.sueldos.Sueldo
import com.sandyyera.reposteria.logica.sueldos.calcularSueldo
import com.sandyyera.reposteria.logica.sueldos.simulacionMultiple
import com.sandyyera.reposteria.logica.validaciones.errorEnGananciaDelEmpleado
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreEscrito
import com.sandyyera.reposteria.logica.validaciones.motivoParaNoTocarAlEmpleado
import com.sandyyera.reposteria.logica.validaciones.textoANumero
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Una receta asignada a un empleado, con su sueldo y sus cifras (10.1).
 *
 * Junta la fila guardada con el snapshot de la receta, que es lo que hace falta para mostrar el
 * reparto **y** para saber si todavía se puede calcular. [sueldo] es `null` cuando la receta no
 * cubre su costo: ahí `calcularSueldo` lanza con razón, y la pantalla tiene que decirlo en vez de
 * quedarse sin dibujar.
 */
data class RecetaDeUnEmpleado(
    val sueldo: EmpleadoRecetaSueldo,
    val datos: DatosCalculoReceta,
    val reparto: Sueldo?
) {
    val recetaId: Long get() = sueldo.recetaId
    val titulo: String get() = datos.titulo

    /**
     * Lo que gana la receta entera, que es el tope de lo que el empleado puede llevarse.
     *
     * **Contesta 0 y no revienta cuando la receta se quedó sin precio.** `ingresoBruto` lanza con
     * razón ahí —no hay nada que repartir— pero esto se lee para *dibujar* una fila que ya
     * existe: una receta puede perder su precio después de asignada, y ahí lo que corresponde es
     * mostrar el aviso de `sinRepartoPosible`, no cerrar la app.
     */
    val gananciaTotal: Double
        get() = if (!datos.tienePrecio) 0.0 else ingresoBruto(datos) - datos.costoTotal

    /** Si esta receta no se puede repartir todavía, y la pantalla tiene que decir por qué. */
    val sinRepartoPosible: Boolean get() = reparto == null
}

/**
 * Todo lo que se hace con los empleados y sus sueldos (sección 10).
 *
 * Igual que los demás: **revisa antes de escribir**, así que cancelar no necesita deshacer nada.
 *
 * Lo que distingue a este repositorio es que **no calcula**. El reparto vive en `logica/sueldos`
 * y ya está probado sin base de datos; acá se hacen las consultas y se llama. Si el bucle de la
 * simulación se escribiera de nuevo, habría dos versiones de la misma cuenta y solo una con
 * pruebas — que es exactamente cómo se separan.
 */
class EmpleadoRepositorio(
    private val dao: EmpleadoDao,
    private val recetas: RecetaRepositorio,
    private val historial: HistorialRepositorio
) {

    // --- Los empleados ---

    /** Todos, con el genérico primero. La que usa la lista. */
    fun observarTodos(): Flow<List<Empleado>> = dao.observarTodos()

    suspend fun obtener(empleadoId: Long): Empleado? = dao.obtener(empleadoId)

    /**
     * Crea un empleado.
     *
     * **Un nombre repetido se avisa pero no se prohíbe.** A diferencia de los ingredientes, dos
     * personas pueden llamarse igual y eso no es un error de datos; lo que sí conviene es
     * preguntar, porque lo normal es que sea un descuido. Por eso devuelve el que ya existe en vez
     * de rechazar: quien decide es la pantalla.
     */
    suspend fun crear(nombre: String): ResultadoCrearEmpleado {
        val limpio = nombre.trim()
        errorEnNombreEscrito(limpio)?.let { return ResultadoCrearEmpleado.NoValido(it) }

        dao.buscarPorNombre(limpio)?.let { return ResultadoCrearEmpleado.YaExiste(it) }

        val id = dao.insertar(Empleado(nombre = limpio))
        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.EMPLEADO,
            descripcion = "Se creó el empleado '$limpio'"
        )
        return ResultadoCrearEmpleado.Creado(id)
    }

    /** Crea aunque el nombre se repita. Se llama solo después de que alguien lo confirme. */
    suspend fun crearAunqueSeRepita(nombre: String): ResultadoCrearEmpleado {
        val limpio = nombre.trim()
        errorEnNombreEscrito(limpio)?.let { return ResultadoCrearEmpleado.NoValido(it) }

        val id = dao.insertar(Empleado(nombre = limpio))
        historial.registrar(
            tipo = TipoEvento.CREACION,
            entidad = EntidadEvento.EMPLEADO,
            descripcion = "Se creó el empleado '$limpio'"
        )
        return ResultadoCrearEmpleado.Creado(id)
    }

    /**
     * Le cambia el nombre a un empleado.
     *
     * **Al genérico no.** Es el modelo estándar y el glosario lo define como "siempre presente";
     * renombrarlo lo volvería indistinguible de uno cualquiera. La pantalla tampoco ofrece la
     * acción, y esto es la segunda red.
     */
    suspend fun renombrar(empleadoId: Long, nombre: String): Resultado {
        val empleado = dao.obtener(empleadoId) ?: return Resultado.NoSePudo("Ese empleado ya no existe")
        motivoParaNoTocarAlEmpleado(empleado.esGenerico)?.let { return Resultado.NoSePudo(it) }

        val limpio = nombre.trim()
        errorEnNombreEscrito(limpio)?.let { return Resultado.NoSePudo(it) }
        if (sonElMismoTexto(limpio, empleado.nombre)) return Resultado.Listo

        dao.actualizar(empleado.copy(nombre = limpio, actualizadoEn = System.currentTimeMillis()))
        historial.registrar(
            tipo = TipoEvento.EDICION,
            entidad = EntidadEvento.EMPLEADO,
            descripcion = "Se renombró el empleado '${empleado.nombre}' a '$limpio'"
        )
        return Resultado.Listo
    }

    /**
     * Cuántas recetas tiene asignadas, para poder avisar antes de borrarlo (6.3).
     *
     * Se pregunta **antes** de confirmar y no después: borrar un empleado se lleva sus sueldos por
     * cascada, y eso es trabajo que alguien configuró receta por receta.
     */
    suspend fun cuantasRecetasTiene(empleadoId: Long): Int = dao.obtenerSueldos(empleadoId).size

    /**
     * Borra un empleado, cuando ya se confirmó.
     *
     * Sus sueldos y su simulación se van con él por las cascadas de las entidades. Al genérico no
     * se le puede hacer: acá se rechaza con un motivo, y el `DELETE` del DAO además lleva su
     * propia condición para que no pase ni por error.
     */
    suspend fun eliminar(empleadoId: Long): Resultado {
        val empleado = dao.obtener(empleadoId) ?: return Resultado.NoSePudo("Ese empleado ya no existe")
        motivoParaNoTocarAlEmpleado(empleado.esGenerico)?.let { return Resultado.NoSePudo(it) }

        val cuantas = cuantasRecetasTiene(empleadoId)
        dao.eliminarPorId(empleadoId)
        historial.registrar(
            tipo = TipoEvento.ELIMINACION,
            entidad = EntidadEvento.EMPLEADO,
            descripcion = "Se eliminó el empleado '${empleado.nombre}'",
            detalleAdicional = if (cuantas > 0) {
                "Se fueron con él ${if (cuantas == 1) "1 sueldo" else "$cuantas sueldos"} de receta"
            } else {
                null
            }
        )
        return Resultado.Listo
    }

    // --- Los sueldos por receta (10.1) ---

    /**
     * Las recetas de un empleado con su reparto, avisando cuando cambie.
     *
     * **Cuelga de tres cosas** —los sueldos del empleado, y a través de `observarCostos` los
     * ingredientes y sus precios— porque el reparto depende de todas: cambiarle el precio a la
     * harina mueve la ganancia de la receta y con ella el tope del sueldo. Un observador que solo
     * mirara la tabla de sueldos dejaría la pantalla mostrando un reparto que ya no cuadra.
     *
     * Las recetas que ya no existen **no aparecen**: sus filas se fueron por la cascada (5.4), y
     * si quedara alguna suelta, no está en el snapshot y se salta.
     */
    fun observarRecetasDe(empleadoId: Long): Flow<List<RecetaDeUnEmpleado>> =
        combine(
            dao.observarSueldos(empleadoId),
            recetas.observarCostos()
        ) { sueldos, _ ->
            armarRecetasDe(sueldos)
        }

    /** Cruza los sueldos con el snapshot de sus recetas. Lo comparten el observador y el cálculo. */
    private suspend fun armarRecetasDe(
        sueldos: List<EmpleadoRecetaSueldo>
    ): List<RecetaDeUnEmpleado> {
        if (sueldos.isEmpty()) return emptyList()
        val datos = recetas.obtenerDatosCalculo(sueldos.map { it.recetaId }.distinct())

        return sueldos.mapNotNull { fila ->
            val d = datos[fila.recetaId] ?: return@mapNotNull null
            RecetaDeUnEmpleado(
                sueldo = fila,
                datos = d,
                // `runCatching` y no un `if` con la misma condición: lo que se quiere es
                // exactamente "lo que `calcularSueldo` acepta", y escribir la condición al lado
                // dejaría dos versiones de la misma regla para separarse.
                reparto = runCatching { calcularSueldo(d, fila.gananciaEmpleado) }.getOrNull()
            )
        }
    }

    /**
     * Qué recetas se le pueden asignar todavía a este empleado.
     *
     * **No ofrece las que ya tiene**: es la misma regla que `titulosDisponibles` y que el
     * ingrediente ya puesto en una sección — lo que se ofrece y lo que se acepta no pueden
     * discrepar. El índice único de la tabla lo hace cumplir de verdad; esto es para no chocar.
     */
    /**
     * Por qué una receta no se le puede asignar a un empleado, o `null` si sí se puede.
     *
     * **Es el arreglo de un cierre de la app.** Sin precio no hay ganancia que repartir, y todo
     * lo que cuelga de eso —`ingresoBruto`, `precioDeReferencia`— lanza excepción con razón. La
     * pantalla llamaba a esas fórmulas para mostrar el tope antes de preguntar, así que elegir
     * una receta sin precio cerraba la app en vez de decir que falta el precio. Lo encontró
     * Sandy: *"al parecer falla cuando la receta no lleva precio"*.
     *
     * Se contesta **antes** de ofrecerla, no al tocarla: una opción que se puede elegir y siempre
     * falla enseña a no leer lo que contesta.
     */
    fun porQueNoSeLePuedeAsignar(datos: DatosCalculoReceta): String? = when {
        !datos.tienePrecio -> "Todavía no tiene precio: sin él no hay ganancia que repartir"
        ingresoBruto(datos) - datos.costoTotal < 0 ->
            "Se vende bajo su costo, así que no hay ganancia que repartir"
        else -> null
    }

    suspend fun recetasQueFaltanPor(empleadoId: Long): List<DatosCalculoReceta> {
        val yaTiene = dao.obtenerSueldos(empleadoId).map { it.recetaId }.toSet()
        val todas = recetas.obtenerTodasUnaVez().map { it.id }.filterNot { it in yaTiene }
        return recetas.obtenerDatosCalculo(todas).values.sortedBy { it.titulo.lowercase() }
    }

    /**
     * Asigna o cambia lo que un empleado se lleva por una receta (10.1).
     *
     * Revisa contra **la ganancia de esa receta ahora**, no contra un tope guardado: los precios y
     * los costos se mueven, y un tope congelado dejaría pasar un reparto que ya no cabe.
     *
     * Guarda también los días y unidades de la simulación individual de esa receta, que son
     * propios suyos y no tienen relación con los días compartidos de la simulación múltiple.
     */
    suspend fun guardarSueldo(
        empleadoId: Long,
        recetaId: Long,
        gananciaTexto: String,
        diasPorSemana: Int = 1,
        unidadesPorDia: Int = 1
    ): Resultado {
        if (dao.obtener(empleadoId) == null) return Resultado.NoSePudo("Ese empleado ya no existe")
        val datos = recetas.obtenerDatosCalculo(listOf(recetaId))[recetaId]
            ?: return Resultado.NoSePudo("Esa receta ya no existe")

        if (!datos.tienePrecio) {
            return Resultado.NoSePudo(
                "'${datos.titulo}' todavía no tiene precio, así que no hay ganancia que repartir"
            )
        }

        val gananciaTotal = ingresoBruto(datos) - datos.costoTotal
        errorEnGananciaDelEmpleado(gananciaTexto, gananciaTotal)?.let {
            return Resultado.NoSePudo(it)
        }
        val ganancia = textoANumero(gananciaTexto) ?: return Resultado.NoSePudo("Escribe un número")

        // El id de la fila que ya existía se conserva: con REPLACE, insertar sin él borraría la
        // vieja y crearía otra, cambiando el id de algo que desde afuera es lo mismo.
        val existente = dao.obtenerSueldo(empleadoId, recetaId)
        dao.guardarSueldo(
            EmpleadoRecetaSueldo(
                id = existente?.id ?: 0,
                empleadoId = empleadoId,
                recetaId = recetaId,
                gananciaEmpleado = ganancia,
                diasPorSemana = diasPorSemana.coerceAtLeast(1),
                unidadesPorDia = unidadesPorDia.coerceAtLeast(0)
            )
        )
        return Resultado.Listo
    }

    /**
     * Cambia cuántas de esta receta vende al día, **sin tocar lo que se lleva**.
     *
     * Va a la misma fila que muestra la pantalla (`EmpleadoRecetaSueldo`) y no a la tabla de
     * detalle de la simulación: escribir en una y leer de la otra era el bug de "el campo no
     * cambia nada y el total sale en 0".
     *
     * Se conserva `gananciaEmpleado` con un `copy` en vez de reescribir la fila entera, por lo
     * mismo que `editarPrecio` conserva `esReferencia`: escribirla completa sin ese cuidado
     * pondría el reparto en cero cada vez que se corrige una cantidad.
     */
    suspend fun guardarUnidadesPorDia(
        empleadoId: Long,
        recetaId: Long,
        unidadesPorDia: Int
    ): Resultado {
        val actual = dao.obtenerSueldo(empleadoId, recetaId)
            ?: return Resultado.NoSePudo("Esa receta no está asignada")
        dao.guardarSueldo(actual.copy(unidadesPorDia = unidadesPorDia.coerceAtLeast(0)))
        return Resultado.Listo
    }

    /** Le quita una receta a un empleado. No toca la receta, solo el sueldo. */
    suspend fun quitarSueldo(sueldoId: Long) = dao.eliminarSueldo(sueldoId)

    // --- La simulación múltiple (10.3) ---

    /** Los días compartidos de un empleado. Sin configurar, 1. */
    suspend fun diasCompartidos(empleadoId: Long): Int =
        dao.obtenerDiasCompartidos(empleadoId)?.coerceAtLeast(1) ?: 1

    suspend fun guardarDiasCompartidos(empleadoId: Long, dias: Int): Resultado {
        if (dao.obtener(empleadoId) == null) return Resultado.NoSePudo("Ese empleado ya no existe")
        dao.guardarSimulacionMultiple(
            EmpleadoSimulacionMultiple(empleadoId = empleadoId, diasPorSemana = dias.coerceAtLeast(1))
        )
        return Resultado.Listo
    }

    /**
     * Cuántas unidades de una receta entran en la simulación múltiple.
     *
     * **Acepta 0 a propósito**: significa "esta receta no se vende hoy", que es una decisión
     * tomada y no un dato faltante. Aporta 0 al total sin quedar listada como omitida.
     */
    suspend fun guardarUnidadesEnLaSimulacion(
        empleadoId: Long,
        recetaId: Long,
        unidadesPorDia: Int
    ): Resultado {
        if (dao.obtener(empleadoId) == null) return Resultado.NoSePudo("Ese empleado ya no existe")
        // La fila de días tiene que existir antes que el detalle: la clave foránea del detalle
        // apunta a ella, no al empleado. Sin esto, guardar unidades antes de tocar los días
        // fallaría con un error de integridad que no explica nada.
        if (dao.obtenerSimulacionMultiple(empleadoId) == null) {
            dao.guardarSimulacionMultiple(EmpleadoSimulacionMultiple(empleadoId = empleadoId))
        }

        val existente = dao.obtenerDetalle(empleadoId).firstOrNull { it.recetaId == recetaId }
        dao.guardarDetalle(
            EmpleadoSimulacionMultipleDetalle(
                id = existente?.id ?: 0,
                empleadoId = empleadoId,
                recetaId = recetaId,
                unidadesPorDia = unidadesPorDia.coerceAtLeast(0)
            )
        )
        return Resultado.Listo
    }

    /**
     * Lo que deja un empleado con **todas** sus recetas a la vez (10.3).
     *
     * **Toda la lectura ocurre en las cuatro primeras líneas** y después no se toca más la base:
     * es el caso donde más se nota la regla del snapshot (6.4). El cálculo lo hace la función
     * pura de `logica/sueldos`, que ya está probada con sus bordes.
     *
     * Las recetas del detalle que no tienen sueldo asignado entran con **0 para el empleado**: el
     * dueño se lleva todo. Es lo correcto y no un hueco — asignar una receta a la simulación y no
     * asignarle sueldo significa justamente eso.
     */
    suspend fun simulacionDeTodasSusRecetas(empleadoId: Long): SimulacionMultipleResultado {
        val dias = diasCompartidos(empleadoId)
        // **Se recorren los sueldos y no la tabla de detalle**, que es el arreglo del bug que
        // Sandy reportó como "veo todo en 0". El detalle arranca vacío: una receta recién
        // asignada no tiene fila ahí, así que la simulación no la contaba — y el campo de
        // "cuántas vende al día" mostraba `sueldo.unidadesPorDia`, o sea **otra tabla**.
        // Escribir en una y leer de la otra hacía que el campo pareciera muerto y el total
        // siempre cero. Un número, un lugar.
        val sueldos = dao.obtenerSueldos(empleadoId)
        val datos = recetas.obtenerDatosCalculo(sueldos.map { it.recetaId }.distinct())

        val filas = sueldos.mapNotNull { sueldo ->
            val d = datos[sueldo.recetaId] ?: return@mapNotNull null
            RecetaEnLaSimulacion(
                datos = d,
                gananciaEmpleado = sueldo.gananciaEmpleado,
                unidadesPorDia = sueldo.unidadesPorDia
            )
        }
        return simulacionMultiple(filas, dias)
    }
}

/**
 * Cómo terminó crear un empleado.
 *
 * `YaExiste` **no es un rechazo**: dos personas pueden llamarse igual. Lleva el que ya está para
 * poder preguntar "¿es este, o creo otro?", que es una decisión de la pantalla y no del
 * repositorio.
 */
sealed interface ResultadoCrearEmpleado {
    data class Creado(val id: Long) : ResultadoCrearEmpleado

    data class YaExiste(val existente: Empleado) : ResultadoCrearEmpleado

    data class NoValido(val motivo: String) : ResultadoCrearEmpleado
}
