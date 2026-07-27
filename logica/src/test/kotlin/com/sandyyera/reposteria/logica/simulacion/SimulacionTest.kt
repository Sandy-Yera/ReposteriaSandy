package com.sandyyera.reposteria.logica.simulacion

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
}
