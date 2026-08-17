package com.sandyyera.reposteria.logica.formato

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatoTest {

    // --- La cantidad con su unidad (14.1.1) ---

    @Test
    fun `lo que se pesa va en gramos`() {
        assertEquals("500 g", cantidadConUnidad(500.0, esObjeto = false))
        assertEquals("Un gramo se lee igual", "1 g", cantidadConUnidad(1.0, esObjeto = false))
        assertEquals("2.500 g", cantidadConUnidad(2500.0, esObjeto = false))
    }

    @Test
    fun `lo que se cuenta por unidad concuerda en singular`() {
        // "1 unidades" hace dudar de si el número está bien, que es lo último que se quiere de
        // una cifra que sale de una regla de negocio.
        assertEquals("1 unidad", cantidadConUnidad(1.0, esObjeto = true))
        assertEquals("3 unidades", cantidadConUnidad(3.0, esObjeto = true))
        assertEquals("0 unidades", cantidadConUnidad(0.0, esObjeto = true))
    }

    @Test
    fun `usa el mismo formato de numero que el resto de la app`() {
        // Si armara el número por su cuenta, la misma cantidad se leería distinto según qué
        // pantalla la muestre.
        assertEquals(
            "${formatearNumero(1234.5)} g",
            cantidadConUnidad(1234.5, esObjeto = false)
        )
    }


    // --- Los ejemplos exactos de la especificación original ---

    @Test
    fun `sin decimales omite la coma`() {
        assertEquals("1.000", formatearNumero(1000.0))
        assertEquals("250", formatearNumero(250.0))
    }

    @Test
    fun `redondea al quinto decimal mas cercano`() {
        // El ejemplo original de la especificación era "si es 1,546 sea 1,55", con 2
        // decimales. Con 5, el 1,546 ya cabe entero y no hay nada que redondear; lo que se
        // redondea ahora es el sexto.
        assertEquals("1,546", formatearNumero(1.546))
        assertEquals("1,55", formatearNumero(1.55))
        assertEquals("1,55556", formatearNumero(1.5555555))
    }

    @Test
    fun `usa punto para miles y coma para decimales`() {
        assertEquals("1.000,5", formatearNumero(1000.5))
        assertEquals("1.234.567", formatearNumero(1234567.0))
        assertEquals("1.234.567,89", formatearNumero(1234567.89))
    }

    // --- Negativos: el bug que se corrigió en la revisión ---

    @Test
    fun `conserva el signo en valores entre menos uno y cero`() {
        // Antes devolvía "0,56": mostraba una pérdida como si fuera ganancia.
        assertEquals("-0,56", formatearNumero(-0.56))
        assertEquals("-0,01", formatearNumero(-0.01))
    }

    @Test
    fun `conserva el signo en negativos grandes`() {
        assertEquals("-1.234,56", formatearNumero(-1234.56))
        assertEquals("-1.000", formatearNumero(-1000.0))
    }

    // --- Casos límite ---

    @Test
    fun `cero se muestra sin coma`() {
        assertEquals("0", formatearNumero(0.0))
    }

    @Test
    fun `no se rellena con ceros a la derecha`() {
        // **Lo contrario de lo que hacía con 2 decimales**, donde 1,5 salía "1,50". Es lo que
        // hace soportables los 5: un precio redondo se sigue leyendo "$4.520" y no
        // "$4.520,00000". El costo aceptado es este cero que se fue.
        assertEquals("1,5", formatearNumero(1.5))
        assertEquals("4.520", formatearNumero(4520.0))
        // Pero los ceros de la izquierda del decimal **sí** se conservan: sin ellos, 0,06667
        // se leería "0,6667" y sería diez veces más.
        assertEquals("0,05", formatearNumero(0.05))
        assertEquals("0,06667", formatearNumero(0.0666666))
        assertEquals("0,00123", formatearNumero(0.00123))
    }

    @Test
    fun `el redondeo que llega a entero no deja coma colgando`() {
        // Con 5 decimales hay que irse al sexto para que suba el entero. El caso importa
        // porque la resta que separa entero de decimal puede redondear hasta la escala
        // completa, y sin cuidado saldría "0,100000", que no es un número.
        assertEquals("1.001", formatearNumero(1000.9999999))
        assertEquals("1", formatearNumero(0.9999999))
    }

    @Test
    fun `montos tipicos de la app`() {
        assertEquals("12.400", formatearNumero(12400.0))    // costo de una torta
        assertEquals("1,5468", formatearNumero(1.5468))     // valor por gramo
        assertEquals("40.000", formatearNumero(40000.0))    // ingreso mensual simulado
        // El caso que motivó los 5 decimales: 25 kg a $1.700. Con 2 salía "0,07", y por 500 g
        // daba $35 donde son $34.
        assertEquals("0,068", formatearNumero(1700.0 / 25000.0))
    }

    // --- Redondeo compartido ---

    @Test
    fun `redondear para guardar da el mismo numero que se muestra`() {
        assertEquals(1.5468, redondearParaGuardar(1.5468), 0.0)
        assertEquals(1.66667, redondearParaGuardar(1.6666666), 0.0)
        assertEquals(1000.0, redondearParaGuardar(1000.0), 0.0)
        assertEquals(-0.5551, redondearParaGuardar(-0.5551), 0.0)
    }

    @Test
    fun `un numero enorme se devuelve tal cual en vez de desbordar`() {
        // Multiplicar por 100.000 un número muy grande desborda `Long`, y `roundToLong` no
        // avisa: se pega al tope y devuelve algo sin ninguna relación con el original. Es la
        // misma trampa que ya costó una vez con `toInt()`. Ahí no hay decimales que redondear,
        // así que lo correcto es devolver lo que llegó.
        assertEquals(1e15, redondearParaGuardar(1e15), 0.0)
        assertEquals(-1e15, redondearParaGuardar(-1e15), 0.0)
        assertTrue(redondearParaGuardar(Double.NaN).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, redondearParaGuardar(Double.POSITIVE_INFINITY), 0.0)
    }

    // --- Formato mientras se escribe ---
    // Cada caso de acá es una tecla que se presiona; lo que importa es lo que queda a la
    // vista justo después de presionarla.

    @Test
    fun `pone el punto de mil al llegar al cuarto digito`() {
        assertEquals("1", formatearMientrasSeEscribe("1"))
        assertEquals("10", formatearMientrasSeEscribe("10"))
        assertEquals("100", formatearMientrasSeEscribe("100"))
        assertEquals("1.000", formatearMientrasSeEscribe("1000"))
        assertEquals("10.000", formatearMientrasSeEscribe("10000"))
    }

    @Test
    fun `agrupa de tres en tres en numeros largos`() {
        assertEquals("1.234.567", formatearMientrasSeEscribe("1234567"))
        assertEquals("12.345.678", formatearMientrasSeEscribe("12345678"))
        assertEquals("123.456.789", formatearMientrasSeEscribe("123456789"))
    }

    @Test
    fun `no se come la coma recien escrita`() {
        // Este es el motivo de que esta función exista. formatearNumero devolvía "1.000"
        // y la coma desaparecía en el momento de tocarla, dejando imposible el decimal.
        assertEquals("1.000,", formatearMientrasSeEscribe("1000,"))
        assertEquals("0,", formatearMientrasSeEscribe("0,"))
    }

    @Test
    fun `no inventa decimales que no se escribieron`() {
        // formatearNumero devolvía "1.000,50" al escribir "1000,5".
        assertEquals("1.000,5", formatearMientrasSeEscribe("1000,5"))
        assertEquals("1.000,50", formatearMientrasSeEscribe("1000,50"))
        assertEquals("0,0", formatearMientrasSeEscribe("0,0"))
    }

    @Test
    fun `corta en cinco decimales, que es lo que se guarda`() {
        assertEquals("1,55555", formatearMientrasSeEscribe("1,555555"))
        assertEquals("1,55999", formatearMientrasSeEscribe("1,5599999"))
        // Y no toca lo que todavía cabe, aunque termine en cero: acá el cero se está
        // escribiendo, al revés que en `formatearNumero`, que lo saca.
        assertEquals("1,50", formatearMientrasSeEscribe("1,50"))
    }

    @Test
    fun `aplicarla sobre su propio resultado no cambia nada`() {
        // Importa porque se llama en cada tecla sobre el texto que ella misma dejó.
        for (escrito in listOf("1.000", "1.000,5", "1.000,55555", "0,00123", "123.456.789", "0,", "")) {
            assertEquals(
                "reformatear '$escrito' debe devolver lo mismo",
                escrito,
                formatearMientrasSeEscribe(escrito)
            )
        }
    }

    @Test
    fun `descarta lo que no es un digito ni la coma`() {
        // El signo menos también: los tres campos que la usan no aceptan negativos.
        assertEquals("15", formatearMientrasSeEscribe("1a5"))
        assertEquals("1.000", formatearMientrasSeEscribe("-1000"))
        assertEquals("", formatearMientrasSeEscribe("abc"))
        assertEquals("", formatearMientrasSeEscribe(""))
    }

    @Test
    fun `solo la primera coma cuenta`() {
        assertEquals("1,55", formatearMientrasSeEscribe("1,5,5"))
    }

    @Test
    fun `saca los ceros de mas pero deja el de cero coma algo`() {
        assertEquals("5", formatearMientrasSeEscribe("05"))
        assertEquals("1.000", formatearMientrasSeEscribe("0001000"))
        assertEquals("0", formatearMientrasSeEscribe("000"))
        assertEquals("0,5", formatearMientrasSeEscribe("0,5"))
        // Empezar por la coma pone el 0 adelante solo.
        assertEquals("0,5", formatearMientrasSeEscribe(",5"))
    }

    @Test
    fun `lo que deja escrito se puede convertir a numero`() {
        // La cadena completa: lo que se escribe, lo que se ve, y lo que se guarda.
        val aLaVista = formatearMientrasSeEscribe("1000,5")
        assertEquals("1.000,5", aLaVista)
        val numero = com.sandyyera.reposteria.logica.validaciones.textoANumero(aLaVista)
        assertEquals(1000.5, numero!!, 0.0)
        assertEquals("1.000,5", formatearNumero(numero))
    }

    @Test
    fun `un valor por gramo chico sobrevive el viaje completo`() {
        // El recorrido que se rompía con 2 decimales: escribir, guardar, y volver a mostrar.
        val aLaVista = formatearMientrasSeEscribe("0,00123")
        assertEquals("0,00123", aLaVista)
        val numero = com.sandyyera.reposteria.logica.validaciones.textoANumero(aLaVista)!!
        assertEquals(0.00123, numero, 0.0)
        assertEquals("0,00123", formatearNumero(numero))
    }

    // --- Dónde queda el cursor ---
    // El bug que hubo: escribiendo "1234", el texto pasaba a "1.234" pero el cursor se
    // quedaba en la posición 4, que en el texto nuevo cae entre el "3" y el "4". Lo que se
    // escribiera después entraba en el medio del número.

    /** Escribe [tecla] al final de [antes] y devuelve cómo queda el campo. */
    private fun teclear(antes: String, tecla: String): String {
        val escrito = antes + tecla
        val resultado = formatearMientrasSeEscribe(escrito, escrito.length)
        // Se marca el cursor con "|" para que el test se lea como se ve la pantalla.
        return resultado.texto.substring(0, resultado.cursor) + "|" +
            resultado.texto.substring(resultado.cursor)
    }

    @Test
    fun `al aparecer el punto de mil el cursor queda al final`() {
        // Este es el caso exacto que falló en el celular.
        assertEquals("1.234|", teclear("123", "4"))
        assertEquals("1.000|", teclear("100", "0"))
        assertEquals("12.345|", teclear("1.234", "5"))
    }

    @Test
    fun `escribir de corrido deja siempre el cursor al final`() {
        var campo = ""
        for (tecla in listOf("1", "2", "3", "4", "5", ",", "5")) {
            val conCursor = teclear(campo, tecla)
            assertTrue(
                "tras escribir '$tecla' el cursor quedó en el medio: $conCursor",
                conCursor.endsWith("|")
            )
            campo = conCursor.dropLast(1)
        }
        assertEquals("12.345,5", campo)
    }

    @Test
    fun `el cursor en el medio se queda donde estaba`() {
        // "12|34" -> se agrega el punto -> el cursor sigue después del "2".
        val resultado = formatearMientrasSeEscribe("1234", 2)
        assertEquals("1.234", resultado.texto)
        assertEquals(3, resultado.cursor)
        assertEquals("1.2", resultado.texto.substring(0, resultado.cursor))
    }

    @Test
    fun `el cursor al principio se queda al principio`() {
        val resultado = formatearMientrasSeEscribe("1234", 0)
        assertEquals("1.234", resultado.texto)
        assertEquals(0, resultado.cursor)
    }

    @Test
    fun `si el texto se acorta el cursor no se sale`() {
        // Los ceros de más desaparecen: el cursor no puede quedar en una posición que ya
        // no existe.
        val resultado = formatearMientrasSeEscribe("000123", 6)
        assertEquals("123", resultado.texto)
        assertTrue(resultado.cursor <= resultado.texto.length)
        assertEquals(3, resultado.cursor)
    }

    @Test
    fun `borrar el ultimo digito deja el cursor al final`() {
        // "1.234|" + borrar -> el campo entrega "1.23" con el cursor en 4.
        val resultado = formatearMientrasSeEscribe("1.23", 4)
        assertEquals("123", resultado.texto)
        assertEquals(3, resultado.cursor)
    }

    @Test
    fun `el cursor nunca queda fuera del texto`() {
        // Barrido: cualquier posición de cualquiera de estos textos tiene que dar un
        // cursor válido. Un cursor fuera de rango cierra la app.
        val textos = listOf("", "1", "1234", "1.234", "1.234,56", "0,5", ",", "000", "abc12")
        for (texto in textos) {
            for (posicion in -2..texto.length + 2) {
                val resultado = formatearMientrasSeEscribe(texto, posicion)
                assertTrue(
                    "texto='$texto' posicion=$posicion dio cursor=${resultado.cursor} " +
                        "sobre '${resultado.texto}'",
                    resultado.cursor in 0..resultado.texto.length
                )
            }
        }
    }

    @Test
    fun `borrar hacia atras deshace bien el agrupado`() {
        // Al borrar el último dígito de "1.000" el campo queda con "1.00" y hay que
        // devolver "100", no "1.00".
        assertEquals("100", formatearMientrasSeEscribe("1.00"))
        assertEquals("10", formatearMientrasSeEscribe("1.0"))
        // Y borrar el punto en sí no debe borrar un dígito.
        assertEquals("1.000", formatearMientrasSeEscribe("1000"))
    }

    // --- Montos redondeados para mostrar (15.2) ---

    @Test
    fun `un monto se muestra sin centavos, al peso mas cercano`() {
        assertEquals("$4.520", "$" + formatearMonto(4520.33333))
        assertEquals("Sube cuando pasa la mitad", "$4.521", "$" + formatearMonto(4520.5))
        assertEquals("$1.667", "$" + formatearMonto(1666.66667))
    }

    @Test
    fun `una perdida redondeada sigue siendo una perdida`() {
        // El signo es lo primero que se mira en una ganancia: perderlo al redondear
        // convertiria una perdida en un cero inofensivo.
        assertEquals("-1.235", formatearMonto(-1234.56))
        assertEquals("-1", formatearMonto(-0.7))
    }

    @Test
    fun `redondear es solo para mostrar y no toca lo que se guarda`() {
        // La regla que separa las dos funciones: `redondearParaGuardar` sigue en 5 decimales,
        // que es lo que multiplica cada receta. Si el redondeo visual entrara ahi, un
        // ingrediente por gramo se iria a cero y la receta pareceria gratis.
        assertEquals(0.06667, redondearParaGuardar(0.0666666), 0.0000001)
        assertEquals("0,06667", formatearNumero(0.0666666))
        assertEquals("Y por eso los montos no se usan para eso", "0", formatearMonto(0.0666666))
    }

    // --- Cantidades de ingrediente al reescalar (8.3.1) ---

    @Test
    fun `una cantidad reescalada se queda en dos decimales`() {
        // El caso de Sandy, tal cual: "tendría de limón 0,50007 g, cuando debería ser 0,5".
        assertEquals(0.5, redondearCantidad(0.50007), 0.000001)
        assertEquals(133.33, redondearCantidad(133.33333), 0.000001)
        assertEquals("Sube cuando pasa la mitad", 0.51, redondearCantidad(0.505), 0.000001)
    }

    @Test
    fun `los enteros no ganan decimales de la nada`() {
        assertEquals("500", formatearNumero(redondearCantidad(500.0)))
        assertEquals("0", formatearNumero(redondearCantidad(0.0)))
    }

    @Test
    fun `nunca convierte en cero algo que no lo era`() {
        // Redondear 0,004 a dos decimales daría 0, o sea que el ingrediente desaparecería de la
        // receta por reescalarla. Perder uno entero es mucho peor que un decimal de más.
        assertEquals(0.004, redondearCantidad(0.004), 0.000001)
        assertNotEquals(0.0, redondearCantidad(0.001))
    }

    @Test
    fun `es distinto de redondearParaGuardar, que sigue en cinco`() {
        // La separación que importa: cinco decimales son los que necesita un precio por gramo
        // —$0,06667 es un dato— y en una cantidad son la basura de una regla de tres.
        assertEquals(0.06667, redondearParaGuardar(0.0666666), 0.000001)
        assertEquals(0.07, redondearCantidad(0.0666666), 0.000001)
    }
}
