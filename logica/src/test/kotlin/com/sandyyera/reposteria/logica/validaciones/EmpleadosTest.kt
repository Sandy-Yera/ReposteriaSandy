package com.sandyyera.reposteria.logica.validaciones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Las reglas de lo que se escribe en Empleados (sección 10). */
class EmpleadosTest {

    // --- La ganancia del empleado (10.1) ---

    @Test
    fun `un reparto dentro de la ganancia sirve`() {
        assertNull(errorEnGananciaDelEmpleado("3.000", gananciaTotal = 7000.0))
    }

    @Test
    fun `llevarse toda la ganancia se permite`() {
        // Es el tope de verdad: el dueño recupera el costo igual, que es lo único que la regla
        // protege. Rechazarlo sería inventar un margen que nadie pidió.
        assertNull(errorEnGananciaDelEmpleado("7.000", gananciaTotal = 7000.0))
    }

    @Test
    fun `el cero se acepta`() {
        // "Esta receta la vendo yo" es una respuesta válida. Obligar a borrar la asignación para
        // decirlo perdería de paso que la receta está asignada.
        assertNull(errorEnGananciaDelEmpleado("0", gananciaTotal = 7000.0))
    }

    @Test
    fun `pasarse de la ganancia dice cuanto hay`() {
        // Un "no se puede" a secas obliga a adivinar el tope probando números.
        val error = errorEnGananciaDelEmpleado("9.000", gananciaTotal = 7000.0)

        assertNotNull(error)
        assertEquals(
            "Esta receta gana \$7.000. No puedes repartir más que eso.",
            error
        )
    }

    @Test
    fun `una ganancia negativa se rechaza`() {
        assertNotNull(errorEnGananciaDelEmpleado("-100", gananciaTotal = 7000.0))
    }

    @Test
    fun `el campo vacio pide el dato en vez de suponer cero`() {
        // Suponer 0 escribiría un sueldo que nadie decidió.
        assertEquals("Escribe cuánto se lleva", errorEnGananciaDelEmpleado("", gananciaTotal = 7000.0))
        assertEquals("Escribe cuánto se lleva", errorEnGananciaDelEmpleado("   ", gananciaTotal = 7000.0))
    }

    @Test
    fun `lo que no es un numero se rechaza`() {
        assertNotNull(errorEnGananciaDelEmpleado("bastante", gananciaTotal = 7000.0))
    }

    @Test
    fun `sin ganancia que repartir se dice eso, no que el numero este mal`() {
        // Aceptar un 0 acá lo dejaría leyéndose como un sueldo asignado, cuando lo que pasa es
        // que la receta se vende bajo su costo y lo que hay que arreglar está en otra pantalla.
        val error = errorEnGananciaDelEmpleado("0", gananciaTotal = -500.0)

        assertEquals(
            "Esta receta se vende bajo su costo: no hay ganancia que repartir",
            error
        )
    }

    @Test
    fun `una receta que empata su costo solo admite cero`() {
        // El borde: ganancia total 0 sí se puede repartir, pero solo de una forma.
        assertNull(errorEnGananciaDelEmpleado("0", gananciaTotal = 0.0))
        assertNotNull(errorEnGananciaDelEmpleado("1", gananciaTotal = 0.0))
    }

    // --- El empleado estándar (10.2) ---

    @Test
    fun `al generico no se le puede hacer nada de eso`() {
        assertNotNull(motivoParaNoTocarAlEmpleado(esGenerico = true))
    }

    @Test
    fun `a los demas si`() {
        assertNull(motivoParaNoTocarAlEmpleado(esGenerico = false))
    }
}
