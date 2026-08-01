package com.sandyyera.reposteria.logica.partes

import com.sandyyera.reposteria.logica.validaciones.LARGO_MAXIMO_NOMBRE
import com.sandyyera.reposteria.logica.validaciones.errorEnNombreSeccion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas de traer una receta dentro de otra que no necesitan base de datos (8.11).
 *
 * Son las tres que más caro salen si se equivocan: adaptar una cantidad, bautizar una sección
 * que choca, y el tope de un solo nivel.
 */
class AdaptacionTest {

    // --- Adaptar en proporción (8.11.3) ---

    @Test
    fun `el ejemplo de la salsa, que es el que motivó la regla`() {
        // La original pasó de 550 a 500 y acá se usaban 275 (la mitad). Queda 250: la mitad
        // de la nueva, igual que antes era la mitad de la vieja.
        assertEquals(250.0, cantidadAdaptada(275.0, 550.0, 500.0), 0.001)
    }

    @Test
    fun `la decision de usar menos se conserva, no se pisa`() {
        // Es la diferencia entre las dos razones por las que una cantidad puede cambiar: acá
        // se usa un cuarto a propósito, y eso no lo decide la receta original.
        assertEquals(125.0, cantidadAdaptada(137.5, 550.0, 500.0), 0.001)
    }

    @Test
    fun `si en la original no cambio nada, acá tampoco`() {
        assertEquals(275.0, cantidadAdaptada(275.0, 550.0, 550.0), 0.001)
    }

    @Test
    fun `el resultado viene redondeado a dos decimales`() {
        // El mismo redondeo que el resto de la app: si se guardara sin redondear, el subtotal
        // que muestra la pantalla no coincidiría con el que suma la base.
        assertEquals(33.33, cantidadAdaptada(100.0, 300.0, 100.0), 0.001)
    }

    @Test
    fun `un ingrediente que en la original estaba en cero llega con la cantidad nueva`() {
        // No hay proporción que conservar y dividir daría infinito. Es el mismo criterio que
        // para un ingrediente que la original no tenía.
        assertEquals(500.0, cantidadAdaptada(275.0, 0.0, 500.0), 0.001)
    }

    @Test
    fun `bajar a cero en la original deja el ingrediente en cero`() {
        assertEquals(0.0, cantidadAdaptada(275.0, 550.0, 0.0), 0.001)
    }

    // --- El nombre que no choca (8.11.2) ---

    @Test
    fun `si el nombre esta libre entra tal cual`() {
        assertEquals("Crema", nombreSinChocar("Crema", listOf("Bizcocho")))
    }

    @Test
    fun `si ya hay una Crema, la que llega es Crema 2`() {
        assertEquals("Crema 2", nombreSinChocar("Crema", listOf("Bizcocho", "Crema")))
    }

    @Test
    fun `y si tambien hay una Crema 2, sigue buscando`() {
        assertEquals("Crema 3", nombreSinChocar("Crema", listOf("Crema", "Crema 2")))
    }

    @Test
    fun `choca aunque se escriba con otras mayusculas o sin tilde`() {
        // Es la misma comparación que hace la validación que rechazaría el nombre: con `==`
        // se propondría "Limón" existiendo "limon" y la copia fallaría igual.
        assertEquals("Limón 2", nombreSinChocar("Limón", listOf("limon")))
        assertEquals("Crema 2", nombreSinChocar("Crema", listOf("CREMA")))
    }

    @Test
    fun `los espacios sobrantes no cuentan como un nombre distinto`() {
        assertEquals("Crema 2", nombreSinChocar("  Crema  ", listOf("Crema")))
    }

    @Test
    fun `el nombre propuesto nunca pasa del tope, aunque el original ya estuviera al limite`() {
        // El mismo `errorEnNombreSeccion` que exige nombres únicos exige que quepan en 60.
        // Pegarle " 2" a un nombre de 60 lo dejaba en 62, y la copia se rechazaba por el
        // nombre que esta misma función acababa de proponer.
        val alLimite = "C".repeat(LARGO_MAXIMO_NOMBRE)

        val propuesto = nombreSinChocar(alLimite, listOf(alLimite))
        assertTrue(propuesto.length <= LARGO_MAXIMO_NOMBRE)
        assertNull("Y la validación lo acepta", errorEnNombreSeccion(propuesto))
        assertTrue("Sigue distinguiéndose por el número", propuesto.endsWith(" 2"))
    }

    @Test
    fun `un nombre larguisimo se recorta aunque no choque con nada`() {
        val largo = "C".repeat(LARGO_MAXIMO_NOMBRE + 20)
        assertNull(errorEnNombreSeccion(nombreSinChocar(largo, emptyList())))
    }

    @Test
    fun `si el recortado tambien choca, sigue subiendo el numero`() {
        val alLimite = "C".repeat(LARGO_MAXIMO_NOMBRE)
        val yaConDos = nombreSinChocar(alLimite, listOf(alLimite))

        val tercero = nombreSinChocar(alLimite, listOf(alLimite, yaConDos))
        assertTrue(tercero.length <= LARGO_MAXIMO_NOMBRE)
        assertNotEquals(yaConDos, tercero)
        assertNull(errorEnNombreSeccion(tercero))
    }

    // --- Un solo nivel (8.11.6) ---

    @Test
    fun `una receta suelta se puede traer`() {
        assertTrue(sePuedeUsarComoParte(recetaId = 2, laQueSeEstaArmando = 1, tieneSeccionesTraidas = false))
    }

    @Test
    fun `una receta que ya usa otra no se puede traer`() {
        // Si Torta usa Bizcocho, Torta no aparece al armar una receta nueva. Sin esto,
        // actualizar el bizcocho tendría que propagarse en cadena.
        assertFalse(sePuedeUsarComoParte(recetaId = 2, laQueSeEstaArmando = 1, tieneSeccionesTraidas = true))
    }

    @Test
    fun `una receta no se puede traer dentro de si misma`() {
        assertFalse(sePuedeUsarComoParte(recetaId = 1, laQueSeEstaArmando = 1, tieneSeccionesTraidas = false))
    }
}
