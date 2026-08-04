package com.sandyyera.reposteria.logica.simulacion

import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.PrecioVigente
import org.junit.Assert.assertNotEquals
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
}
