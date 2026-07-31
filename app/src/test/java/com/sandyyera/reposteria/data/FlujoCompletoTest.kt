package com.sandyyera.reposteria.data

import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.data.repositorio.MoldeRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio
import com.sandyyera.reposteria.data.repositorio.Resultado
import com.sandyyera.reposteria.data.repositorio.ResultadoCrearReceta
import com.sandyyera.reposteria.data.repositorio.ResultadoGuardarIngrediente
import com.sandyyera.reposteria.data.repositorio.ResultadoGuardarMolde
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import com.sandyyera.reposteria.logica.duracion.UnidadDuracion
import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.ModoReescalado
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.gananciaFinal
import com.sandyyera.reposteria.logica.precios.ingresoBruto
import com.sandyyera.reposteria.logica.precios.precioEfectivoPorTrozo
import com.sandyyera.reposteria.logica.sueldos.calcularSueldo
import com.sandyyera.reposteria.logica.validaciones.CampoDeMolde
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El recorrido completo de la app, de punta a punta, cruzando los tres repositorios.
 *
 * **Por qué existe aparte de las pruebas de cada repositorio.** Cada una de esas mira una
 * pieza sola y con datos que ella misma prepara. Los errores que llegaron al celular no
 * fueron de una pieza: fueron de dos que dejaron de estar de acuerdo — la lista de recetas
 * mostrando costos de antes de borrar un ingrediente es exactamente eso, y ninguna prueba
 * de `RecetaRepositorio` sola lo habría visto, porque el ingrediente lo borra otro
 * repositorio.
 *
 * Así que acá no se prueba una función: se recorre lo que haría Sandy en una tarde —cargar
 * ingredientes, armar una receta con secciones, medir un molde, ponerle precio, corregir
 * cosas y borrar otras— y **después de cada paso se comprueba que todos los caminos hacia
 * el mismo número sigan dando lo mismo**.
 *
 * Es de escritorio (`./gradlew :app:test`), sobre la base de mentira.
 */
class FlujoCompletoTest {

    private lateinit var catalogo: IngredienteDaoFalso
    private lateinit var recetaDao: RecetaDaoFalso
    private lateinit var moldeDao: MoldeDaoFalso
    private lateinit var historialDao: HistorialDaoFalso

    private lateinit var ingredientes: IngredienteRepositorio
    private lateinit var recetas: RecetaRepositorio
    private lateinit var moldes: MoldeRepositorio

    @Before
    fun prepararTodo() {
        catalogo = IngredienteDaoFalso()
        recetaDao = RecetaDaoFalso(catalogo)
        moldeDao = MoldeDaoFalso(recetaDao)
        historialDao = HistorialDaoFalso()
        val historial = HistorialRepositorio(historialDao)

        // El mismo armado que hace AppContainer: si esto se desalinea, la app real también.
        ingredientes = IngredienteRepositorio(catalogo, recetaDao, historial)
        recetas = RecetaRepositorio(recetaDao, historial)
        moldes = MoldeRepositorio(moldeDao, recetas, historial)
    }

    private suspend fun crearIngrediente(nombre: String, valor: Double): Long =
        (ingredientes.crear(nombre, valor) as ResultadoGuardarIngrediente.Guardado).id

    /**
     * Comprueba que **los tres caminos hacia el costo de una receta** den lo mismo.
     *
     * Son tres de verdad, y cada uno lo usa una parte distinta de la app: el paso de
     * cantidades pide `costoTotal`, la lista de recetas escucha `observarCostos`, y las
     * fórmulas de precios reciben el `DatosCalculoReceta`. Si se separaran, la misma receta
     * mostraría números distintos según desde dónde se la mire, y nadie sabría cuál creer.
     */
    private suspend fun costoCoherente(recetaId: Long): Double {
        val directo = recetas.costoTotal(recetaId)
        val enVivo = recetas.observarCostos().first()[recetaId] ?: 0.0
        val enLote = recetas.costosDe(listOf(recetaId)).getValue(recetaId)
        val snapshot = recetas.obtenerDatosCalculo(listOf(recetaId)).getValue(recetaId).costoTotal

        assertEquals("costoTotal contra observarCostos", directo, enVivo, 0.001)
        assertEquals("costoTotal contra costosDe", directo, enLote, 0.001)
        assertEquals("costoTotal contra el snapshot", directo, snapshot, 0.001)
        return directo
    }

    @Test
    fun `una tarde completa, de los ingredientes a borrar la receta`() =
        runBlocking {
            // ---------- 1. El catálogo de ingredientes ----------
            val harina = crearIngrediente("Harina", 1.2)
            val azucar = crearIngrediente("Azúcar", 2.0)
            val chocolate = crearIngrediente("Chocolate", 8.0)

            // Repetido con otra tilde: la base no lo vería, la app sí.
            assertTrue(
                ingredientes.crear("azucar", 2.0) is ResultadoGuardarIngrediente.YaExiste
            )

            // ---------- 2. La receta y sus dos secciones ----------
            val receta = (recetas.crear("Torta de chocolate") as ResultadoCrearReceta.Creada)
                .recetaId
            assertEquals(0.0, costoCoherente(receta), 0.001)

            // La única sección todavía es la invisible, así que agregar otra pide bautizarla.
            assertNotNull(recetas.nombreQueFaltaBautizar(receta))
            assertTrue(
                recetas.agregarSeccion(receta, "Salsa", nombreDeLaPrimera = "Bizcocho")
                    is Resultado.Listo
            )
            val secciones = recetas.obtenerSecciones(receta)
            assertEquals(listOf("Bizcocho", "Salsa"), secciones.map { it.nombreSeccion })

            // Y ya no se puede repetir un nombre de sección dentro de esta receta.
            assertTrue(recetas.agregarSeccion(receta, "  bizcocho ") is Resultado.NoSePudo)

            // ---------- 3. Los ingredientes de la receta ----------
            recetas.agregarIngrediente(secciones[0].id, harina, 500.0)    // 600
            recetas.agregarIngrediente(secciones[0].id, azucar, 250.0)    // 500
            recetas.agregarIngrediente(secciones[1].id, chocolate, 200.0) // 1.600

            assertEquals(2700.0, costoCoherente(receta), 0.001)

            // La suma por sección tiene que dar el mismo total que la base (8.2).
            val items = recetas.obtenerIngredientes(receta)
            val porCatalogo = catalogo.obtenerTodosUnaVez().associateBy { it.id }
            val sumaPorSeccion = secciones.sumOf { seccion ->
                items.filter { it.seccionId == seccion.id }
                    .sumOf { it.cantidadG * porCatalogo.getValue(it.ingredienteId).valorPorGramo }
            }
            assertEquals(2700.0, sumaPorSeccion, 0.001)

            // ---------- 4. El molde ----------
            val molde = (moldes.crear(
                nombre = "Redondo grande",
                forma = TipoFormaMolde.CIRCULO,
                medidas = mapOf(
                    CampoDeMolde.DIAMETRO to "24",
                    CampoDeMolde.ALTURA_MOLDE to "7"
                )
            ) as ResultadoGuardarMolde.Guardado).id

            // Se enlaza la receta a ese molde, como hará el paso Rendimiento (Fase 5).
            val rendimiento = recetaDao.obtenerRendimiento(receta)!!
            recetaDao.actualizarRendimiento(
                rendimiento.copy(
                    usaMolde = true,
                    moldeOrigenId = molde,
                    dimensiones = moldes.obtener(molde)!!.dimensiones,
                    trozos = 8
                )
            )

            // ---------- 5. Los precios y la referencia ----------
            // Producir cada trozo cuesta 2.700 / 8 = 337,5.
            val porTrozo = recetaDao.insertarPrecio(
                RecetaPrecio(recetaId = receta, modo = ModoPrecio.TROZO, precioTotal = 1500.0)
            )
            val promoBuena = recetaDao.insertarPrecio(
                RecetaPrecio(
                    recetaId = receta, modo = ModoPrecio.TROZO,
                    cantidad = 3, precioTotal = 3600.0, etiqueta = "3 por 3.600"
                )
            )
            val promoMala = recetaDao.insertarPrecio(
                RecetaPrecio(
                    recetaId = receta, modo = ModoPrecio.TROZO,
                    cantidad = 4, precioTotal = 800.0, etiqueta = "Regalada"
                )
            )

            // Sin referencia elegida se usa el peor caso: la promo regalada, a 200 el trozo.
            var datos = recetas.obtenerDatosCalculo(listOf(receta)).getValue(receta)
            assertEquals(200.0, precioEfectivoPorTrozo(datos), 0.001)

            // Una promo que pierde plata no puede ser la referencia, y no escribe nada.
            assertNotNull(recetas.elegirPrecioDeReferencia(receta, promoMala))
            datos = recetas.obtenerDatosCalculo(listOf(receta)).getValue(receta)
            assertTrue("Sigue sin referencia elegida", !datos.tieneReferenciaElegida)

            // La de 3 por 3.600 sí: 1.200 el trozo.
            assertNull(recetas.elegirPrecioDeReferencia(receta, promoBuena))
            datos = recetas.obtenerDatosCalculo(listOf(receta)).getValue(receta)
            assertEquals(1200.0, precioEfectivoPorTrozo(datos), 0.001)
            assertEquals(9600.0, ingresoBruto(datos), 0.001)          // 1.200 × 8
            assertEquals(6900.0, gananciaFinal(datos), 0.001)         // 9.600 − 2.700

            // Y el sueldo sale de esa misma referencia, no de otro precio.
            val sueldo = calcularSueldo(datos, gananciaEmpleado = 2000.0)
            assertEquals(9600.0, sueldo.ingresoBruto, 0.001)
            assertEquals(7600.0, sueldo.yoMeLlevo, 0.001)

            // Cambiar de referencia mueve todo lo automático de una vez.
            assertNull(recetas.elegirPrecioDeReferencia(receta, porTrozo))
            datos = recetas.obtenerDatosCalculo(listOf(receta)).getValue(receta)
            assertEquals(1500.0, precioEfectivoPorTrozo(datos), 0.001)
            assertEquals(12000.0, ingresoBruto(datos), 0.001)

            // ---------- 6. Corregir el molde no toca la receta ----------
            assertTrue(
                moldes.actualizar(
                    molde, "Redondo grande", TipoFormaMolde.CIRCULO,
                    mapOf(
                        CampoDeMolde.DIAMETRO to "26",
                        CampoDeMolde.ALTURA_MOLDE to "7"
                    )
                ) is ResultadoGuardarMolde.Guardado
            )
            assertEquals(
                "Las medidas nuevas llegan a la receta",
                26.0,
                recetaDao.obtenerRendimiento(receta)!!.dimensiones!!.diametroCm!!,
                0.001
            )
            assertEquals(
                "Pero el costo no se mueve: corregir no es reescalar",
                2700.0, costoCoherente(receta), 0.001
            )

            // ---------- 7. Sube el precio de un ingrediente ----------
            ingredientes.actualizar(
                Ingrediente(id = harina, nombre = "Harina", valorPorGramo = 2.0)
            )
            // 500 g × 2 = 1.000, en vez de 600.
            assertEquals(3100.0, costoCoherente(receta), 0.001)

            // Y la ganancia baja sola, sin que nadie recalcule nada a mano.
            datos = recetas.obtenerDatosCalculo(listOf(receta)).getValue(receta)
            assertEquals(8900.0, gananciaFinal(datos), 0.001)          // 12.000 − 3.100

            // ---------- 8. Borrar un ingrediente que la receta usa ----------
            assertEquals(
                listOf("Torta de chocolate"),
                ingredientes.recetasAfectadasPorBorrar(chocolate).map { it.titulo }
            )
            ingredientes.confirmarEliminacion(chocolate)

            // El costo baja solo: es justo el bug que se vio en el celular.
            assertEquals(1500.0, costoCoherente(receta), 0.001)        // 1.000 + 500
            assertEquals(2, recetas.obtenerIngredientes(receta).size)
            assertNotNull("La receta sigue existiendo", recetas.obtener(receta))

            // ---------- 9. Borrar el molde ----------
            moldes.confirmarEliminacion(molde)
            val sinMolde = recetaDao.obtenerRendimiento(receta)!!
            assertNull("Pierde el vínculo", sinMolde.moldeOrigenId)
            assertEquals(
                "Pero conserva las medidas",
                26.0, sinMolde.dimensiones!!.diametroCm!!, 0.001
            )
            assertEquals("Y el costo tampoco cambia", 1500.0, costoCoherente(receta), 0.001)

            // ---------- 10. Borrar la receta se lleva todo lo suyo ----------
            recetas.confirmarEliminacion(receta)
            assertNull(recetas.obtener(receta))
            assertTrue(recetas.obtenerSecciones(receta).isEmpty())
            assertTrue(recetas.obtenerIngredientes(receta).isEmpty())
            // Los ingredientes del catálogo no se van con ella.
            assertEquals(2, catalogo.obtenerTodosUnaVez().size)

            // ---------- 11. El historial cuenta lo que pasó ----------
            // No se cuentan eventos exactos a propósito: eso se rompería al agregar
            // cualquier anotación nueva sin que nada esté mal. Lo que importa es que cada
            // cosa borrada quedó nombrada, que es lo que sirve para entender un cambio raro.
            val descripciones = historialDao.eventos.map { it.descripcion }
            assertTrue(descripciones.any { it.contains("Chocolate") })
            assertTrue(descripciones.any { it.contains("Redondo grande") })
            assertTrue(descripciones.any { it.contains("Torta de chocolate") })
        }

    /**
     * El otro recorrido que cruza módulos: dos recetas compartiendo ingredientes.
     *
     * Va aparte porque prueba algo distinto — que las recetas **no se pisen entre sí** — y
     * mezclarlo con el recorrido de arriba haría que un fallo no dijera cuál de las dos
     * cosas se rompió.
     */
    @Test
    fun `dos recetas que comparten ingredientes no se pisan`() = runBlocking {
        val harina = crearIngrediente("Harina", 1.0)
        val azucar = crearIngrediente("Azúcar", 2.0)

        val torta = (recetas.crear("Torta") as ResultadoCrearReceta.Creada).recetaId
        val queque = (recetas.crear("Queque") as ResultadoCrearReceta.Creada).recetaId
        recetas.agregarIngrediente(recetas.obtenerSecciones(torta).single().id, harina, 500.0)
        recetas.agregarIngrediente(recetas.obtenerSecciones(queque).single().id, harina, 200.0)
        recetas.agregarIngrediente(recetas.obtenerSecciones(queque).single().id, azucar, 100.0)

        assertEquals(500.0, costoCoherente(torta), 0.001)
        assertEquals(400.0, costoCoherente(queque), 0.001)

        // Las dos secciones pueden llamarse igual: la regla de nombres es por receta.
        assertTrue(recetas.agregarSeccion(torta, "Crema", "Bizcocho") is Resultado.Listo)
        assertTrue(recetas.agregarSeccion(queque, "Crema", "Bizcocho") is Resultado.Listo)

        // Subir la harina mueve las dos, cada una según cuánta lleva.
        ingredientes.actualizar(Ingrediente(id = harina, nombre = "Harina", valorPorGramo = 3.0))
        assertEquals(1500.0, costoCoherente(torta), 0.001)
        assertEquals(800.0, costoCoherente(queque), 0.001)

        // Borrar una no toca a la otra.
        recetas.confirmarEliminacion(torta)
        assertNull(recetas.obtener(torta))
        assertEquals(800.0, costoCoherente(queque), 0.001)
        assertEquals(2, recetas.obtenerIngredientes(queque).size)

        // Y el costo en lote sigue trayendo una entrada por cada receta pedida, incluidas
        // las que no tienen ingredientes.
        val vacia = (recetas.crear("Sin nada") as ResultadoCrearReceta.Creada).recetaId
        val enLote = recetas.costosDe(listOf(queque, vacia))
        assertEquals(2, enLote.size)
        assertEquals(0.0, enLote.getValue(vacia), 0.001)
    }

    /**
     * Que el paso de duración **no toque nada** del resto.
     *
     * Es la comprobación al revés de las otras: acá lo que se verifica es que un paso nuevo
     * no se haya enredado con los que ya estaban. La duración es el único paso puramente
     * descriptivo —no entra en el costo, ni en los precios, ni en los sueldos, ni en las
     * simulaciones—, y esa independencia es fácil de romper sin darse cuenta el día que
     * alguien la meta en el snapshot "por si acaso".
     */
    @Test
    fun `anotar duraciones no mueve ninguna de las cifras de la receta`() = runBlocking {
        val harina = crearIngrediente("Harina", 2.0)
        val receta = (recetas.crear("Torta") as ResultadoCrearReceta.Creada).recetaId
        recetas.agregarIngrediente(recetas.obtenerSecciones(receta).single().id, harina, 500.0)
        recetaDao.actualizarRendimiento(recetaDao.obtenerRendimiento(receta)!!.copy(trozos = 4))
        recetaDao.insertarPrecio(
            RecetaPrecio(recetaId = receta, modo = ModoPrecio.TROZO, precioTotal = 800.0)
        )

        val antesDelCosto = costoCoherente(receta)
        val antes = recetas.obtenerDatosCalculo(listOf(receta)).getValue(receta)
        val antesDelIngreso = ingresoBruto(antes)
        val antesDeLaGanancia = gananciaFinal(antes)

        recetas.guardarDuracion(receta, TipoDuracion.AMBIENTE, true, "3", UnidadDuracion.DIAS)
        recetas.guardarDuracion(receta, TipoDuracion.CONGELADA, apto = false, "", null)

        assertEquals(antesDelCosto, costoCoherente(receta), 0.001)
        val despues = recetas.obtenerDatosCalculo(listOf(receta)).getValue(receta)
        assertEquals(antesDelIngreso, ingresoBruto(despues), 0.001)
        assertEquals(antesDeLaGanancia, gananciaFinal(despues), 0.001)
        assertEquals("Ni los trozos", antes.trozos, despues.trozos)
        assertEquals("Ni los precios", antes.precios.size, despues.precios.size)

        // Y al revés: reescalar la receta no borra ni cambia lo anotado de duración.
        recetas.definirMolde(
            receta,
            DimensionesMolde(
                tipoForma = TipoFormaMolde.CUADRADO, ladoCm = 10.0, alturaMoldeCm = 5.0
            ),
            moldeOrigenId = null
        )
        recetas.reescalarPorMolde(
            receta,
            DimensionesMolde(
                tipoForma = TipoFormaMolde.CUADRADO, ladoCm = 10.0, alturaMoldeCm = 10.0
            ),
            ModoReescalado.CAPACIDAD,
            moldeOrigenId = null
        )

        val duraciones = recetas.obtenerDuraciones(receta)
        assertEquals(2, duraciones.size)
        assertEquals(3, duraciones.getValue(TipoDuracion.AMBIENTE).cantidad)
        assertEquals(1000.0, recetas.obtenerIngredientes(receta).single().cantidadG, 0.001)
    }
}
