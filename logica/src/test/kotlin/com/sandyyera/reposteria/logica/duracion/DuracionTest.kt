package com.sandyyera.reposteria.logica.duracion

import com.sandyyera.reposteria.logica.validaciones.MAXIMA_CANTIDAD_DE_DURACION
import com.sandyyera.reposteria.logica.validaciones.ORDEN_DE_LOS_BLOQUES
import com.sandyyera.reposteria.logica.validaciones.elBloqueDiceAlgo
import com.sandyyera.reposteria.logica.validaciones.errorEnCantidadDeDuracion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El paso "Duración" (8.4): el más liviano, y el único que puede quedar vacío. */
class DuracionTest {

    // --- Cómo se lee ---

    @Test
    fun `una duracion se lee con su unidad`() {
        assertEquals("3 días", describirDuracion(true, 3, UnidadDuracion.DIAS))
        assertEquals("2 semanas", describirDuracion(true, 2, UnidadDuracion.SEMANAS))
        assertEquals("6 meses", describirDuracion(true, 6, UnidadDuracion.MESES))
        assertEquals("48 horas", describirDuracion(true, 48, UnidadDuracion.HORAS))
    }

    @Test
    fun `en singular la unidad tambien va en singular`() {
        // "1 días" es el detalle que hace que una app se sienta descuidada.
        assertEquals("1 día", describirDuracion(true, 1, UnidadDuracion.DIAS))
        assertEquals("1 hora", describirDuracion(true, 1, UnidadDuracion.HORAS))
        assertEquals("1 semana", describirDuracion(true, 1, UnidadDuracion.SEMANAS))
        assertEquals("1 mes", describirDuracion(true, 1, UnidadDuracion.MESES))
    }

    @Test
    fun `los tres estados de un bloque se leen distinto`() {
        // Ninguno es un número: no apto, sin anotar, o una cantidad. Devolver `Int?` obligaría
        // a cada pantalla a decidir cómo se lee cada caso.
        assertEquals(DURACION_NO_APTA, describirDuracion(false, null, null))
        assertEquals(DURACION_SIN_DATO, describirDuracion(true, null, null))
        assertEquals("3 días", describirDuracion(true, 3, UnidadDuracion.DIAS))
    }

    @Test
    fun `un bloque no apto se lee como no apto aunque tenga numeros guardados`() {
        // Puede quedar un número de antes de marcarlo: lo que importa es que no corresponde.
        assertEquals(DURACION_NO_APTA, describirDuracion(false, 3, UnidadDuracion.DIAS))
    }

    @Test
    fun `una cantidad sin unidad no se puede leer y cuenta como sin anotar`() {
        assertEquals(DURACION_SIN_DATO, describirDuracion(true, 3, null))
        assertEquals(DURACION_SIN_DATO, describirDuracion(true, null, UnidadDuracion.DIAS))
    }

    @Test
    fun `cada tipo de guardado tiene su nombre`() {
        assertEquals("A temperatura ambiente", nombreDelTipoDeDuracion(TipoDuracion.AMBIENTE))
        assertEquals("Refrigerada", nombreDelTipoDeDuracion(TipoDuracion.REFRIGERADA))
        assertEquals("Congelada", nombreDelTipoDeDuracion(TipoDuracion.CONGELADA))
    }

    @Test
    fun `los tres bloques se muestran de lo mas comun a lo menos`() {
        assertEquals(
            listOf(TipoDuracion.AMBIENTE, TipoDuracion.REFRIGERADA, TipoDuracion.CONGELADA),
            ORDEN_DE_LOS_BLOQUES
        )
        assertEquals("Y están los tres", TipoDuracion.entries.size, ORDEN_DE_LOS_BLOQUES.size)
    }

    // --- Qué se acepta escribir ---

    @Test
    fun `un bloque vacio esta bien, porque no saber es una respuesta`() {
        // Es el único paso de la receta que puede quedar completamente sin llenar.
        assertNull(errorEnCantidadDeDuracion("", apto = true))
        assertNull(errorEnCantidadDeDuracion("   ", apto = true))
    }

    @Test
    fun `cero no sirve, porque para eso esta el switch de no apto`() {
        assertNotNull(errorEnCantidadDeDuracion("0", apto = true))
        assertNotNull(errorEnCantidadDeDuracion("-3", apto = true))
    }

    @Test
    fun `los decimales no sirven, porque las unidades ya bajan de escala`() {
        // Medio día son 12 horas: cambiar de unidad dice lo mismo y se lee mejor.
        assertNotNull(errorEnCantidadDeDuracion("1,5", apto = true))
        assertNull(errorEnCantidadDeDuracion("12", apto = true))
    }

    @Test
    fun `un numero absurdo se rechaza y sugiere cambiar de unidad`() {
        assertNull(errorEnCantidadDeDuracion("$MAXIMA_CANTIDAD_DE_DURACION", apto = true))
        assertNotNull(errorEnCantidadDeDuracion("${MAXIMA_CANTIDAD_DE_DURACION + 1}", apto = true))
        assertNotNull(errorEnCantidadDeDuracion("300", apto = true))
    }

    @Test
    fun `lo que no es numero no sirve`() {
        assertNotNull(errorEnCantidadDeDuracion("tres", apto = true))
    }

    @Test
    fun `marcado no apto no se revisa nada de lo escrito`() {
        // Ahí la cantidad se ignora por completo: no tiene sentido retar por un campo que ya
        // no significa nada.
        assertNull(errorEnCantidadDeDuracion("0", apto = false))
        assertNull(errorEnCantidadDeDuracion("cualquier cosa", apto = false))
        assertNull(errorEnCantidadDeDuracion("", apto = false))
    }

    // --- Cuándo un bloque tiene algo que guardar ---

    @Test
    fun `un bloque no apto si tiene algo que guardar, aunque no tenga numeros`() {
        // El caso que se olvida al escribir esto como "tiene cantidad": que algo no se pueda
        // congelar es justamente el dato.
        assertTrue(elBloqueDiceAlgo(apto = false, cantidadTexto = ""))
    }

    @Test
    fun `un bloque apto y vacio no tiene nada que guardar`() {
        assertFalse(elBloqueDiceAlgo(apto = true, cantidadTexto = ""))
    }

    @Test
    fun `un bloque con una cantidad valida tiene algo que guardar`() {
        assertTrue(elBloqueDiceAlgo(apto = true, cantidadTexto = "3"))
    }

    @Test
    fun `un bloque con una cantidad que no sirve no cuenta como lleno`() {
        // Si contara, se guardaría una fila con un dato que la validación ya rechazó.
        assertFalse(elBloqueDiceAlgo(apto = true, cantidadTexto = "0"))
        assertFalse(elBloqueDiceAlgo(apto = true, cantidadTexto = "tres"))
    }
}
