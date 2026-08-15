package com.sandyyera.reposteria.logica.ventas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo estimado contra lo real de un día (18.2).
 *
 * Lo que se cuida acá es **la diferencia entre "no sé" y "cero"**: un día sin descuento del
 * almacén no tiene costo real, y tratarlo como 0 haría que la ganancia de ese día saliera igual
 * al ingreso entero. Es la clase de error que se ve bien en la pantalla —números grandes y
 * alegres— y que solo una prueba distingue.
 */
class VentasTest {

    /** Un día que salió exactamente como se esperaba, para partir de algo neutro. */
    private fun comoSeEsperaba() = CifrasDelDia(
        ingresoReal = 10_000.0,
        ingresoEstimado = 10_000.0,
        costoEstimado = 3_000.0,
        costoReal = 3_000.0
    )

    // --- El costo que no se sabe ---

    @Test
    fun `sin costo real se usa el estimado, diciendo que lo es`() {
        val cifras = comoSeEsperaba().copy(costoReal = null)

        assertFalse(cifras.costoEsDeVerdad)
        assertEquals(3_000.0, cifras.costoQueVale, 0.001)
    }

    @Test
    fun `sin costo real no hay diferencia de costo que mostrar`() {
        // `null` y no 0: con un 0 la pantalla diría "gastaste lo mismo que lo estimado", que es
        // una afirmación, y lo cierto es que no se sabe.
        assertNull(comoSeEsperaba().copy(costoReal = null).diferenciaDeCosto)
    }

    @Test
    fun `un costo real de cero no es lo mismo que no tenerlo`() {
        // Puede pasar de verdad: una venta cuyas recetas son todas de ingredientes que valen 0.
        val gratis = comoSeEsperaba().copy(costoReal = 0.0)

        assertTrue("Se descontó, y dio 0", gratis.costoEsDeVerdad)
        assertEquals(0.0, gratis.costoQueVale, 0.001)
        assertEquals(-3_000.0, gratis.diferenciaDeCosto!!, 0.001)
    }

    // --- Las cuentas ---

    @Test
    fun `la ganancia estimada es el ingreso estimado menos el costo estimado`() {
        assertEquals(7_000.0, comoSeEsperaba().gananciaEstimada, 0.001)
    }

    @Test
    fun `la ganancia que vale usa el ingreso real y el costo que haya`() {
        val cifras = comoSeEsperaba().copy(ingresoReal = 12_000.0, costoReal = 4_000.0)

        assertEquals(8_000.0, cifras.gananciaQueVale, 0.001)
        assertEquals(1_000.0, cifras.diferenciaDeGanancia, 0.001)
    }

    @Test
    fun `cobrar de menos y gastar de mas se suman en contra`() {
        val malDia = CifrasDelDia(
            ingresoReal = 9_000.0,
            ingresoEstimado = 10_000.0,
            costoEstimado = 3_000.0,
            costoReal = 3_500.0
        )

        assertEquals(-1_000.0, malDia.diferenciaDeIngreso, 0.001)
        assertEquals(500.0, malDia.diferenciaDeCosto!!, 0.001)
        assertEquals(5_500.0, malDia.gananciaQueVale, 0.001)
        assertEquals(-1_500.0, malDia.diferenciaDeGanancia, 0.001)
    }

    // --- Lo que se lee del día ---

    @Test
    fun `un dia que salio como se esperaba no dice nada`() {
        // Que no haya nada que señalar también es una respuesta, y llenarla de frases obvias
        // enseñaría a no leer las que sí importan.
        assertTrue(loQueDiceElDia(comoSeEsperaba()).isEmpty())
    }

    @Test
    fun `las milesimas de las divisiones no generan avisos`() {
        // Las cifras vienen de multiplicar y dividir: siempre hay una diferencia mínima. Decir
        // "cobraste $0 más de lo estimado" enseña a ignorar estos avisos.
        val conRuido = comoSeEsperaba().copy(ingresoReal = 10_000.3, costoReal = 3_000.2)

        assertTrue(loQueDiceElDia(conRuido).isEmpty())
    }

    @Test
    fun `cobrar mas de lo estimado se dice`() {
        val cifras = comoSeEsperaba().copy(ingresoReal = 12_000.0)
        val frases = loQueDiceElDia(cifras)

        assertEquals(1, frases.size)
        assertTrue(frases.single().contains("más"))
        assertTrue("Y dice cuánto", frases.single().contains("2.000"))
    }

    @Test
    fun `cobrar menos de lo estimado se dice en positivo`() {
        // El monto se muestra sin signo: "cobraste $2.000 menos" y no "cobraste $-2.000 menos",
        // que obliga a leerlo dos veces.
        val frases = loQueDiceElDia(comoSeEsperaba().copy(ingresoReal = 8_000.0))

        assertTrue(frases.single().contains("menos"))
        assertTrue(frases.single().contains("2.000"))
        assertFalse(frases.single().contains("-"))
    }

    @Test
    fun `gastar mas de lo anotado dice que la receta lleva mas`() {
        // Es el hallazgo, no un error de la app (18.2).
        val frases = loQueDiceElDia(comoSeEsperaba().copy(costoReal = 3_800.0))

        assertEquals(1, frases.size)
        assertTrue(frases.single().contains("llevan más de lo que dicen"))
    }

    @Test
    fun `gastar menos de lo anotado dice que la receta pide de mas`() {
        val frases = loQueDiceElDia(comoSeEsperaba().copy(costoReal = 2_400.0))

        assertTrue(frases.single().contains("piden de más"))
    }

    @Test
    fun `sin descuento se avisa que el costo no se puede comparar`() {
        val frases = loQueDiceElDia(comoSeEsperaba().copy(costoReal = null))

        assertEquals(1, frases.size)
        assertTrue(frases.single().contains("no descontó del almacén"))
    }

    @Test
    fun `el ingreso y el costo se leen por separado`() {
        // Son lecturas independientes: el ingreso puede calzar y el costo no.
        val frases = loQueDiceElDia(
            comoSeEsperaba().copy(ingresoReal = 12_000.0, costoReal = 3_800.0)
        )

        assertEquals(2, frases.size)
    }

    // --- Lo que se escribe al registrar ---

    @Test
    fun `las unidades vendidas piden un entero de al menos uno`() {
        assertNotNull(errorEnUnidadesVendidasTexto(""))
        assertNotNull(errorEnUnidadesVendidasTexto("dos"))
        assertNotNull(errorEnUnidadesVendidasTexto("1,5"))
        assertNull(errorEnUnidadesVendidasTexto("3"))
    }

    @Test
    fun `cero unidades vendidas se rechaza y se dice qué hacer`() {
        // Al revés que las unidades por día de un empleado, donde el 0 es cómo se dice "esta
        // receta no la vendo". Acá una línea en cero no dice nada: es una línea que sobra.
        val aviso = errorEnUnidadesVendidasTexto("0")

        assertNotNull(aviso)
        assertTrue("Dice qué hacer", aviso!!.contains("quita la línea"))
    }

    @Test
    fun `pasarse del tope de unidades se rechaza`() {
        assertNull(errorEnUnidadesVendidasTexto("$MAXIMAS_UNIDADES_VENDIDAS"))
        assertNotNull(errorEnUnidadesVendidasTexto("${MAXIMAS_UNIDADES_VENDIDAS + 1}"))
    }

    @Test
    fun `el precio de venta rechaza el cero y el vacio`() {
        assertNotNull(errorEnPrecioDeVentaTexto(""))
        assertNotNull(errorEnPrecioDeVentaTexto("0"))
        assertNull(errorEnPrecioDeVentaTexto("1.250"))
    }
}
