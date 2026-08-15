package com.sandyyera.reposteria.logica.simulacion

import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.PrecioVigente
import com.sandyyera.reposteria.logica.precios.ingresoBruto
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SimulacionTest {

    @Test
    fun `ejemplo exacto de la especificacion`() {
        // "digamos que espero vender 4 días y cada día vender 2. El ingreso bruto es
        //  5.000. Entonces (...) 5.000×4×2. O sea, 40.000."
        val r = simulacion(ingresoBase = 5000.0, costoBase = 0.0, dias = 4, unidades = 2)
        assertEquals(40000.0, r.ingresoSemanal, 0.001)
    }

    @Test
    fun `la ganancia semanal descuenta el costo`() {
        val r = simulacion(ingresoBase = 10000.0, costoBase = 3000.0, dias = 4, unidades = 2)
        assertEquals(80000.0, r.ingresoSemanal, 0.001)
        assertEquals(24000.0, r.costoSemanal, 0.001)
        assertEquals(56000.0, r.gananciaSemanal, 0.001)
    }

    @Test
    fun `lo mensual son las semanas por mes`() {
        val r = simulacion(ingresoBase = 5000.0, costoBase = 1000.0, dias = 4, unidades = 2)
        assertEquals(r.ingresoSemanal * SEMANAS_POR_MES, r.ingresoMensual, 0.001)
        assertEquals(r.costoSemanal * SEMANAS_POR_MES, r.costoMensual, 0.001)
        assertEquals(r.gananciaSemanal * SEMANAS_POR_MES, r.gananciaMensual, 0.001)
    }

    @Test
    fun `semanas por mes es 52 dividido 12 redondeado`() {
        assertEquals(4.33, SEMANAS_POR_MES, 0.001)
    }

    @Test
    fun `vender cero unidades da cero en todo`() {
        val r = simulacion(ingresoBase = 5000.0, costoBase = 1000.0, dias = 4, unidades = 0)
        assertEquals(0.0, r.ingresoSemanal, 0.001)
        assertEquals(0.0, r.gananciaMensual, 0.001)
    }

    @Test
    fun `una receta que pierde plata proyecta perdidas`() {
        val r = simulacion(ingresoBase = 1000.0, costoBase = 1500.0, dias = 5, unidades = 3)
        assertEquals(-7500.0, r.gananciaSemanal, 0.001)
        assert(r.gananciaMensual < 0) { "la pérdida debe mantenerse al proyectar al mes" }
    }

    // --- La simulación de una receta concreta, con su reparto real (8.6.1) ---

    private fun receta(trozos: Int, vararg precios: PrecioVigente) = DatosCalculoReceta(
        recetaId = 1, titulo = "Torta", costoTotal = 900.0, trozos = trozos,
        precios = precios.toList()
    )

    @Test
    fun `vender varios productos junta los restos, en vez de arrastrar uno por producto`() {
        // Es la promesa que quedó escrita al arreglar el ingreso de un producto. Una receta de
        // 3 trozos con promo de 2 deja siempre un suelto mirando producto por producto; en la
        // semana, 2 productos son 6 trozos y la promo entra tres veces justas.
        val d = receta(
            trozos = 3,
            PrecioVigente(ModoPrecio.TROZO, 1, 2000.0),
            PrecioVigente(ModoPrecio.TROZO, 2, 3000.0, esReferencia = true)
        )

        // Un día, dos productos: 6 trozos.
        val reparto = repartoSemanal(d, dias = 1, unidades = 2)

        assertEquals(3, reparto.cuantasVecesEntra)
        assertEquals(0, reparto.sueltos)
        assertEquals(9000.0, reparto.total, 0.001)
        // Multiplicar el ingreso de un producto (5.000) daría 10.000: mil pesos que no entran.
        assertNotEquals(10000.0, reparto.total, 0.001)
    }

    @Test
    fun `y si el total tampoco divide, el resto se cobra suelto una sola vez`() {
        val d = receta(
            trozos = 3,
            PrecioVigente(ModoPrecio.TROZO, 1, 2000.0),
            PrecioVigente(ModoPrecio.TROZO, 2, 3000.0, esReferencia = true)
        )

        // Un día, un producto: 3 trozos. Una promo y un suelto.
        val reparto = repartoSemanal(d, dias = 1, unidades = 1)

        assertEquals(1, reparto.cuantasVecesEntra)
        assertEquals(1, reparto.sueltos)
        assertEquals(5000.0, reparto.total, 0.001)
    }

    @Test
    fun `el costo si se multiplica, sin promociones que valgan`() {
        // Producir dos tortas cuesta el doble que producir una. Es la asimetría del paso: el
        // ingreso se reparte y el costo no.
        val d = receta(trozos = 3, PrecioVigente(ModoPrecio.TROZO, 1, 2000.0, esReferencia = true))

        val r = simulacionDeVenta(d, dias = 2, unidades = 3)

        assertEquals("6 productos x 900", 5400.0, r.costoSemanal, 0.001)
        assertEquals("6 productos x 3 trozos x 2.000", 36000.0, r.ingresoSemanal, 0.001)
        assertEquals(30600.0, r.gananciaSemanal, 0.001)
    }

    @Test
    fun `sin vender nada la semana da cero, y no revienta`() {
        // Las unidades aceptan el 0 a propósito: es como se dice "esta receta todavía no la
        // vendo" (ver arriba). La simulación tiene que contestar cero, no fallar.
        val d = receta(trozos = 3, PrecioVigente(ModoPrecio.TROZO, 2, 3000.0, esReferencia = true))

        val r = simulacionDeVenta(d, dias = 4, unidades = 0)

        assertEquals(0.0, r.ingresoSemanal, 0.001)
        assertEquals(0.0, r.costoSemanal, 0.001)
        assertEquals(0.0, r.gananciaMensual, 0.001)
    }

    @Test
    fun `lo mensual sale de multiplicar lo semanal por las semanas del mes`() {
        val d = receta(trozos = 2, PrecioVigente(ModoPrecio.TROZO, 1, 2500.0, esReferencia = true))

        val r = simulacionDeVenta(d, dias = 4, unidades = 2)

        // 8 productos x 2 trozos x 2.500 = 40.000, el ejemplo de la arquitectura.
        assertEquals(40000.0, r.ingresoSemanal, 0.001)
        assertEquals(40000.0 * SEMANAS_POR_MES, r.ingresoMensual, 0.001)
    }

    // --- El caso de las Mil hojas: por qué la semana no es el producto multiplicado ---

    /**
     * Los números tal como los tenía Sandy en el celular cuando reportó que "la cantidad que
     * entra es incorrecta". No lo era, pero no había forma de comprobarlo desde la pantalla.
     */
    private fun milHojas() = DatosCalculoReceta(
        recetaId = 1, titulo = "Mil hojas", costoTotal = 12167.45, trozos = 5,
        precios = listOf(
            PrecioVigente(ModoPrecio.TROZO, 1, 6000.0),
            PrecioVigente(ModoPrecio.PRODUCTO, 1, 40000.0),
            PrecioVigente(ModoPrecio.TROZO, 2, 20000.0, esReferencia = true)
        )
    )

    @Test
    fun `la semana da mas que el producto multiplicado, y la diferencia son los sueltos`() {
        val d = milHojas()

        // Un producto: 5 trozos = 2 promos + 1 suelto = 40.000 + 6.000.
        assertEquals(46000.0, ingresoBruto(d), 0.001)
        // Seis productos: 30 trozos, la promo entra 15 veces justas.
        assertEquals(300000.0, simulacionDeVenta(d, dias = 3, unidades = 2).ingresoSemanal, 0.001)
        assertEquals(276000.0, ingresoSiSeMultiplicaraElProducto(d, 3, 2), 0.001)
    }

    @Test
    fun `la diferencia se dice en promociones, que es lo que se puede comprobar mirando`() {
        // 24.000 de diferencia no se comprueban; "tres promociones más" sí: son los seis
        // trozos sueltos (uno por producto) armando tres pares.
        assertEquals(3, promocionesQueSeGananAlJuntar(milHojas(), dias = 3, unidades = 2))
    }

    @Test
    fun `cuando la promo divide exacto no hay nada que explicar`() {
        // 4 trozos con promo de 2: ningún producto deja suelto, así que juntar no gana nada y
        // las dos cuentas coinciden. Es cuando la pantalla no debe decir nada.
        val d = receta(
            trozos = 4,
            PrecioVigente(ModoPrecio.TROZO, 1, 2000.0),
            PrecioVigente(ModoPrecio.TROZO, 2, 3000.0, esReferencia = true)
        )

        assertEquals(0, promocionesQueSeGananAlJuntar(d, dias = 5, unidades = 3))
        assertEquals(
            ingresoSiSeMultiplicaraElProducto(d, 5, 3),
            simulacionDeVenta(d, 5, 3).ingresoSemanal,
            0.001
        )
    }

    @Test
    fun `sin vender nada no se gana ninguna promocion al juntar`() {
        // Con 0 productos, `repartoDeUnProducto` sí tiene respuesta pero multiplicarla por 0
        // no significa nada. Tiene que dar 0 y no un número negativo.
        val d = milHojas()
        assertEquals(0, promocionesQueSeGananAlJuntar(d, dias = 4, unidades = 0))
    }

    @Test
    fun `una promo por productos completos se reparte sobre los productos`() {
        val d = receta(
            trozos = 6,
            PrecioVigente(ModoPrecio.PRODUCTO, 1, 9000.0),
            PrecioVigente(ModoPrecio.PRODUCTO, 2, 16000.0, esReferencia = true)
        )

        // 5 productos en la semana: dos promos de dos, y uno suelto.
        val reparto = repartoSemanal(d, dias = 5, unidades = 1)

        assertEquals(2, reparto.cuantasVecesEntra)
        assertEquals(1, reparto.sueltos)
        assertEquals(41000.0, reparto.total, 0.001)   // 16.000 x 2 + 9.000
    }

    // --- Lo que se llevan los empleados (10.1 en la simulación de la receta) ---

    @Test
    fun `sin empleados no se descuenta nada, pero se avisa`() {
        // El aviso va justamente donde **no** hay descuento: el día que se asigne un empleado
        // estos números van a bajar, y conviene saberlo antes y no después.
        val nadie = LoQueSeLlevanLosEmpleados(cuantos = 0, seLlevanPorProducto = 0.0)

        assertEquals(7_000.0, gananciaDespuesDeLosEmpleados(7_000.0, nadie), 0.001)
        assertTrue(loQueDicenLosEmpleados(7_000.0, nadie)!!.contains("sin descuentos por empleado"))
    }

    @Test
    fun `con empleados se resta lo que se llevan todos juntos`() {
        // Una sola cifra y no una por empleado: la simulación de la receta responde "cuánto me
        // queda a mí", y para eso da igual entre cuántos se reparte lo que se va.
        val dos = LoQueSeLlevanLosEmpleados(cuantos = 2, seLlevanPorProducto = 2_500.0)

        assertEquals(4_500.0, gananciaDespuesDeLosEmpleados(7_000.0, dos), 0.001)
        assertTrue(loQueDicenLosEmpleados(7_000.0, dos)!!.contains("2 empleados"))
    }

    @Test
    fun `un precio que no alcanza para pagarlos se dice acá y no en Empleados`() {
        // Es el caso que Sandy quería ver en la simulación: antes había que entrar a Empleados
        // para descubrir que ese precio ya no daba.
        val caros = LoQueSeLlevanLosEmpleados(cuantos = 1, seLlevanPorProducto = 9_000.0)

        assertEquals(-2_000.0, gananciaDespuesDeLosEmpleados(7_000.0, caros), 0.001)
        assertTrue(loQueDicenLosEmpleados(7_000.0, caros)!!.contains("no alcanza para pagar"))
    }

    @Test
    fun `justo en el limite todavia alcanza`() {
        val alRas = LoQueSeLlevanLosEmpleados(cuantos = 1, seLlevanPorProducto = 7_000.0)

        assertEquals(0.0, gananciaDespuesDeLosEmpleados(7_000.0, alRas), 0.001)
        assertTrue(loQueDicenLosEmpleados(7_000.0, alRas)!!.contains("Ya está descontado"))
    }

    @Test
    fun `uno solo se lee en singular`() {
        val uno = LoQueSeLlevanLosEmpleados(cuantos = 1, seLlevanPorProducto = 100.0)

        assertEquals("1 empleado", uno.comoSeLeeCuantos)
    }
}
