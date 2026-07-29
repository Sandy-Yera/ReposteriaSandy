package com.sandyyera.reposteria.logica.sueldos

import com.sandyyera.reposteria.logica.precios.DatosCalculoReceta
import com.sandyyera.reposteria.logica.precios.ModoPrecio
import com.sandyyera.reposteria.logica.precios.PrecioVigente
import org.junit.Assert.assertEquals
import org.junit.Test

class SueldosTest {

    /** Receta con ingreso bruto 10.000 y costo 3.000: la del ejemplo de la especificación. */
    private fun recetaDelEjemplo() = DatosCalculoReceta(
        recetaId = 1L,
        titulo = "Torta de manjar",
        costoTotal = 3000.0,
        trozos = 8,
        precios = listOf(PrecioVigente(ModoPrecio.TROZO, 1, 1250.0))  // 1.250 x 8 = 10.000
    )

    @Test
    fun `ejemplo exacto de la especificacion`() {
        // "supongamos que el ingreso bruto es de 10 mil. 7 mil es ganancia total y 3 mil
        //  de costo total. Yo me llevaré esos 3 mil y además 4 mil (...) Y la otra
        //  persona se quedará con 3 mil."
        val sueldo = calcularSueldo(recetaDelEjemplo(), gananciaEmpleado = 3000.0)

        assertEquals(10000.0, sueldo.ingresoBruto, 0.001)
        assertEquals(7000.0, sueldo.yoMeLlevo, 0.001)
        assertEquals(3000.0, sueldo.gananciaEmpleado, 0.001)
    }

    @Test
    fun `lo que se lleva cada uno siempre suma el ingreso bruto`() {
        val d = recetaDelEjemplo()
        for (ganancia in listOf(0.0, 1500.0, 3000.0, 6999.0, 7000.0)) {
            val s = calcularSueldo(d, ganancia)
            assertEquals(
                "con gananciaEmpleado=$ganancia las partes no suman el ingreso",
                s.ingresoBruto, s.yoMeLlevo + s.gananciaEmpleado, 0.001
            )
        }
    }

    @Test
    fun `si el empleado no se lleva nada el dueno se lleva todo`() {
        val s = calcularSueldo(recetaDelEjemplo(), gananciaEmpleado = 0.0)
        assertEquals(10000.0, s.yoMeLlevo, 0.001)
    }

    @Test
    fun `el empleado puede llevarse toda la ganancia pero nunca el costo`() {
        // En el tope, al dueño le queda exactamente el costo total: no baja de ahí.
        val s = calcularSueldo(recetaDelEjemplo(), gananciaEmpleado = 7000.0)
        assertEquals(3000.0, s.yoMeLlevo, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pedir mas que la ganancia total lanza excepcion`() {
        calcularSueldo(recetaDelEjemplo(), gananciaEmpleado = 7000.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una ganancia negativa para el empleado lanza excepcion`() {
        calcularSueldo(recetaDelEjemplo(), gananciaEmpleado = -1.0)
    }

    @Test
    fun `receta que se vende bajo su costo avisa con un mensaje entendible`() {
        // Este es el caso que antes fallaba siempre con un mensaje sin sentido: con
        // ganancia total negativa, el rango 0.0..gananciaTotal queda vacío en Kotlin.
        val enPerdida = DatosCalculoReceta(
            recetaId = 2L,
            titulo = "Receta mal costeada",
            costoTotal = 10000.0,
            trozos = 4,
            precios = listOf(PrecioVigente(ModoPrecio.TROZO, 1, 500.0))  // ingreso 2.000
        )

        val error = runCatching { calcularSueldo(enPerdida, gananciaEmpleado = 0.0) }
            .exceptionOrNull()

        assertEquals(IllegalArgumentException::class.java, error!!::class.java)
        assert(error.message!!.contains("no cubre su costo")) {
            "el mensaje debe explicar el problema real, llegó: ${error.message}"
        }
    }

    // --- El sueldo sigue al precio de referencia (decisión #4) ---

    @Test
    fun `elegir otra promocion como referencia cambia el sueldo`() {
        // Es el motivo del cambio: poder responder "¿cuánto le tocaría con ESTA promo?".
        val conBase = DatosCalculoReceta(
            recetaId = 1L, titulo = "Torta de manjar", costoTotal = 3000.0, trozos = 8,
            precios = listOf(
                PrecioVigente(ModoPrecio.TROZO, 1, 1250.0, esReferencia = true),
                PrecioVigente(ModoPrecio.TROZO, 2, 3000.0)      // promo: 1.500 por trozo
            )
        )
        val conPromo = conBase.copy(
            precios = listOf(
                PrecioVigente(ModoPrecio.TROZO, 1, 1250.0),
                PrecioVigente(ModoPrecio.TROZO, 2, 3000.0, esReferencia = true)
            )
        )

        assertEquals(10000.0, calcularSueldo(conBase, 3000.0).ingresoBruto, 0.001)
        assertEquals(12000.0, calcularSueldo(conPromo, 3000.0).ingresoBruto, 0.001)
        // Lo que se lleva el dueño sube con el ingreso; lo acordado con el empleado no.
        assertEquals(9000.0, calcularSueldo(conPromo, 3000.0).yoMeLlevo, 0.001)
    }

    @Test
    fun `una referencia que empata el costo solo permite un sueldo de cero`() {
        // Empatar sí se acepta como referencia (8.6), pero no hay ganancia que repartir.
        val empatada = DatosCalculoReceta(
            recetaId = 3L, titulo = "Receta al costo", costoTotal = 4000.0, trozos = 8,
            precios = listOf(PrecioVigente(ModoPrecio.TROZO, 1, 500.0, esReferencia = true))
        )

        assertEquals(0.0, calcularSueldo(empatada, gananciaEmpleado = 0.0).gananciaEmpleado, 0.001)
        assertEquals(
            IllegalArgumentException::class.java,
            runCatching { calcularSueldo(empatada, gananciaEmpleado = 1.0) }
                .exceptionOrNull()!!::class.java
        )
    }
}
