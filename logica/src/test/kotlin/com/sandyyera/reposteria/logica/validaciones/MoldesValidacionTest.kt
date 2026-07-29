package com.sandyyera.reposteria.logica.validaciones

import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** Las reglas del formulario de moldes (6.2 y 9.1). */
class MoldesValidacionTest {

    // ---- Qué campos pide cada forma ----

    @Test
    fun `cada forma pide sus medidas y ninguna de las otras`() {
        assertEquals(
            listOf(CampoDeMolde.LARGO, CampoDeMolde.ANCHO, CampoDeMolde.ALTURA_MOLDE),
            camposDe(TipoFormaMolde.RECTANGULO)
        )
        assertEquals(
            listOf(CampoDeMolde.LADO, CampoDeMolde.ALTURA_MOLDE),
            camposDe(TipoFormaMolde.CUADRADO)
        )
        assertEquals(
            listOf(CampoDeMolde.DIAMETRO, CampoDeMolde.ALTURA_MOLDE),
            camposDe(TipoFormaMolde.CIRCULO)
        )
        assertEquals(
            listOf(
                CampoDeMolde.BASE_TRIANGULO,
                CampoDeMolde.ALTURA_TRIANGULO,
                CampoDeMolde.ALTURA_MOLDE
            ),
            camposDe(TipoFormaMolde.TRIANGULO)
        )
        assertEquals(
            listOf(CampoDeMolde.VOLUMEN_EXOTICO, CampoDeMolde.ALTURA_MOLDE),
            camposDe(TipoFormaMolde.EXOTICO)
        )
    }

    @Test
    fun `el alto del molde se pide en las cinco formas, tambien en la exotica`() {
        // En un molde exótico el volumen ya viene medido con agua, pero sin la altura no se
        // puede despejar el área y el Modo Altura del reescalado se queda sin qué comparar.
        TipoFormaMolde.entries.forEach { forma ->
            assertTrue(
                "La forma $forma no pide el alto del molde",
                CampoDeMolde.ALTURA_MOLDE in camposDe(forma)
            )
        }
    }

    @Test
    fun `todas las formas piden por lo menos una medida ademas del alto`() {
        TipoFormaMolde.entries.forEach { forma ->
            assertTrue("La forma $forma no pide nada más que el alto", camposDe(forma).size >= 2)
        }
    }

    // ---- Las medidas ----

    @Test
    fun `una medida vacia, no numerica o negativa no sirve`() {
        assertNotNull(errorEnMedidaDeMoldeTexto(""))
        assertNotNull(errorEnMedidaDeMoldeTexto("   "))
        assertNotNull(errorEnMedidaDeMoldeTexto("veinte"))
        assertNotNull(errorEnMedidaDeMoldeTexto("-20"))
    }

    @Test
    fun `una medida en cero no sirve, porque dejaria el area en cero`() {
        // No es prolijidad: con área 0, `factorEscala` divide por cero al reescalar, y ese
        // error aparecería en otra pantalla sin ninguna pista de que venía de acá.
        assertNotNull(errorEnMedidaDeMoldeTexto("0"))
        assertNotNull(errorEnMedidaDeMoldeTexto("0,0"))
    }

    @Test
    fun `una medida sirve escrita como se escribe en el celular`() {
        assertNull(errorEnMedidaDeMoldeTexto("24"))
        assertNull(errorEnMedidaDeMoldeTexto("7,5"))
        assertNull(errorEnMedidaDeMoldeTexto("1.250"))
    }

    // ---- El formulario completo ----

    private fun medidas(vararg pares: Pair<CampoDeMolde, String>) = pares.toMap()

    @Test
    fun `un molde completo sirve`() {
        val errores = revisarMolde(
            nombre = "Molde redondo grande",
            forma = TipoFormaMolde.CIRCULO,
            medidas = medidas(CampoDeMolde.DIAMETRO to "24", CampoDeMolde.ALTURA_MOLDE to "7")
        )
        assertTrue(errores.sirve)
        assertNull(errores.nombre)
        assertNull(errores.forma)
        assertTrue(errores.medidas.isEmpty())
    }

    @Test
    fun `sin forma elegida se avisa de la forma y no de medidas que nadie pidio`() {
        val errores = revisarMolde("Molde nuevo", forma = null, medidas = emptyMap())
        assertFalse(errores.sirve)
        assertNotNull(errores.forma)
        assertTrue("No se puede exigir una medida sin saber la forma", errores.medidas.isEmpty())
    }

    @Test
    fun `el error de una medida queda apuntado a su propio campo`() {
        // La pantalla pinta `errores.medidas[campo]` bajo cada campo que dibujó: si el error
        // no viniera identificado, el aviso saldría bajo el campo equivocado.
        val errores = revisarMolde(
            nombre = "Molde de torta",
            forma = TipoFormaMolde.RECTANGULO,
            medidas = medidas(
                CampoDeMolde.LARGO to "30",
                CampoDeMolde.ANCHO to "",
                CampoDeMolde.ALTURA_MOLDE to "0"
            )
        )
        assertFalse(errores.sirve)
        assertEquals(setOf(CampoDeMolde.ANCHO, CampoDeMolde.ALTURA_MOLDE), errores.medidas.keys)
    }

    @Test
    fun `lo escrito para otra forma no se arrastra como error`() {
        // Se probó "círculo", se anotó el diámetro, y después se cambió a "cuadrado". Ese
        // diámetro ya no lo pide nadie y no puede impedir guardar.
        val errores = revisarMolde(
            nombre = "Molde cuadrado",
            forma = TipoFormaMolde.CUADRADO,
            medidas = medidas(
                CampoDeMolde.DIAMETRO to "no me hagan caso",
                CampoDeMolde.LADO to "20",
                CampoDeMolde.ALTURA_MOLDE to "6"
            )
        )
        assertTrue(errores.sirve)
    }

    @Test
    fun `un molde sin nombre no sirve aunque las medidas esten bien`() {
        val errores = revisarMolde(
            nombre = "   ",
            forma = TipoFormaMolde.CUADRADO,
            medidas = medidas(CampoDeMolde.LADO to "20", CampoDeMolde.ALTURA_MOLDE to "6")
        )
        assertFalse(errores.sirve)
        assertNotNull(errores.nombre)
        assertTrue(errores.medidas.isEmpty())
    }

    @Test
    fun `un nombre demasiado largo no sirve`() {
        val errores = revisarMolde(
            nombre = "a".repeat(LARGO_MAXIMO_NOMBRE + 1),
            forma = TipoFormaMolde.CUADRADO,
            medidas = medidas(CampoDeMolde.LADO to "20", CampoDeMolde.ALTURA_MOLDE to "6")
        )
        assertNotNull(errores.nombre)
    }

    // ---- De lo escrito a las dimensiones ----

    @Test
    fun `mientras falte algo no se arman dimensiones, y no revienta`() {
        assertNull(dimensionesDesde(null, emptyMap()))
        assertNull(dimensionesDesde(TipoFormaMolde.CIRCULO, emptyMap()))
        assertNull(
            dimensionesDesde(
                TipoFormaMolde.CIRCULO,
                medidas(CampoDeMolde.DIAMETRO to "24")   // falta el alto
            )
        )
        assertNull(
            dimensionesDesde(
                TipoFormaMolde.CIRCULO,
                medidas(CampoDeMolde.DIAMETRO to "24", CampoDeMolde.ALTURA_MOLDE to "0")
            )
        )
    }

    @Test
    fun `un formulario completo se convierte en dimensiones que calculan bien`() {
        val redondo = dimensionesDesde(
            TipoFormaMolde.CIRCULO,
            medidas(CampoDeMolde.DIAMETRO to "24", CampoDeMolde.ALTURA_MOLDE to "7")
        )
        assertNotNull(redondo)
        assertEquals(PI * 12 * 12, redondo!!.areaCm2, 0.001)
        assertEquals(PI * 12 * 12 * 7, redondo.volumenCm3, 0.001)
    }

    @Test
    fun `los campos que esa forma no usa quedan vacios aunque haya algo escrito`() {
        val cuadrado = dimensionesDesde(
            TipoFormaMolde.CUADRADO,
            medidas(
                CampoDeMolde.DIAMETRO to "24",
                CampoDeMolde.LADO to "20",
                CampoDeMolde.ALTURA_MOLDE to "6"
            )
        )
        assertNotNull(cuadrado)
        assertNull("Un cuadrado con diámetro guardado se contradice a sí mismo", cuadrado!!.diametroCm)
        assertEquals(20.0, cuadrado.ladoCm!!, 0.001)
        assertEquals(400.0, cuadrado.areaCm2, 0.001)
    }

    @Test
    fun `en un molde exotico el volumen es el medido y el area se despeja`() {
        val exotico = dimensionesDesde(
            TipoFormaMolde.EXOTICO,
            medidas(CampoDeMolde.VOLUMEN_EXOTICO to "1.500", CampoDeMolde.ALTURA_MOLDE to "6")
        )
        assertNotNull(exotico)
        assertEquals(1500.0, exotico!!.volumenCm3, 0.001)
        assertEquals(250.0, exotico.areaCm2, 0.001)
    }

    @Test
    fun `las dos alturas del triangulo son distintas y no se confunden`() {
        // `alturaTrianguloCm` entra en el área; `alturaMoldeCm` es la profundidad real.
        val triangulo = dimensionesDesde(
            TipoFormaMolde.TRIANGULO,
            medidas(
                CampoDeMolde.BASE_TRIANGULO to "20",
                CampoDeMolde.ALTURA_TRIANGULO to "10",
                CampoDeMolde.ALTURA_MOLDE to "5"
            )
        )
        assertNotNull(triangulo)
        assertEquals(100.0, triangulo!!.areaCm2, 0.001)     // 20 × 10 / 2
        assertEquals(500.0, triangulo.volumenCm3, 0.001)    // × 5 de alto
    }

    @Test
    fun `todo lo que revisarMolde aprueba se puede convertir en dimensiones`() {
        // Las dos funciones se leen por separado, y si discreparan la pantalla habilitaría
        // el botón de guardar sobre un molde que no se puede armar.
        val casos = mapOf(
            TipoFormaMolde.RECTANGULO to medidas(
                CampoDeMolde.LARGO to "30", CampoDeMolde.ANCHO to "20",
                CampoDeMolde.ALTURA_MOLDE to "7"
            ),
            TipoFormaMolde.CUADRADO to medidas(
                CampoDeMolde.LADO to "20", CampoDeMolde.ALTURA_MOLDE to "6"
            ),
            TipoFormaMolde.CIRCULO to medidas(
                CampoDeMolde.DIAMETRO to "24", CampoDeMolde.ALTURA_MOLDE to "7"
            ),
            TipoFormaMolde.TRIANGULO to medidas(
                CampoDeMolde.BASE_TRIANGULO to "20", CampoDeMolde.ALTURA_TRIANGULO to "10",
                CampoDeMolde.ALTURA_MOLDE to "5"
            ),
            TipoFormaMolde.EXOTICO to medidas(
                CampoDeMolde.VOLUMEN_EXOTICO to "1.500", CampoDeMolde.ALTURA_MOLDE to "6"
            )
        )
        casos.forEach { (forma, medidas) ->
            assertTrue("$forma: revisarMolde la rechazó", revisarMolde("Molde", forma, medidas).sirve)
            val dimensiones = dimensionesDesde(forma, medidas)
            assertNotNull("$forma: revisarMolde aprobó pero no se pudo armar", dimensiones)
            // Y ninguna de las dos cifras que usa el reescalado sale en cero.
            assertTrue("$forma: área en cero", dimensiones!!.areaCm2 > 0)
            assertTrue("$forma: volumen en cero", dimensiones.volumenCm3 > 0)
        }
    }
}
