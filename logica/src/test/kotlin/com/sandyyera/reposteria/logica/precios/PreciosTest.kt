package com.sandyyera.reposteria.logica.precios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreciosTest {

    private fun receta(
        costoTotal: Double,
        trozos: Int,
        vararg precios: PrecioVigente
    ) = DatosCalculoReceta(
        recetaId = 1L,
        titulo = "Torta de prueba",
        costoTotal = costoTotal,
        trozos = trozos,
        precios = precios.toList()
    )

    private fun porTrozo(precio: Double, cantidad: Int = 1) =
        PrecioVigente(ModoPrecio.TROZO, cantidad, precio)

    private fun porProducto(precio: Double, cantidad: Int = 1) =
        PrecioVigente(ModoPrecio.PRODUCTO, cantidad, precio)

    // --- Los ejemplos exactos de la especificación original ---

    @Test
    fun `ejemplo base de la especificacion - costo 1400 y trozo a 500`() {
        // "imagina que el total sale 1.400, y vendo cada trozo a 500,
        //  por ende el 3 representa ganancia (...) 100 pesos"
        val d = receta(costoTotal = 1400.0, trozos = 6, porTrozo(500.0))
        val ganador = trozoGanador(d)

        assertEquals(3, ganador.numero)
        assertEquals(100.0, ganador.ganancia, 0.001)
        assertTrue(ganador.alcanzable)
    }

    @Test
    fun `ejemplo con promocion - 2 trozos por 1500`() {
        // La promo deja el trozo a 750, así que el ganador pasa a ser el segundo.
        val d = receta(costoTotal = 1400.0, trozos = 6, porTrozo(1500.0, cantidad = 2))

        assertEquals(750.0, precioEfectivoPorTrozo(d), 0.001)
        val ganador = trozoGanador(d)
        assertEquals(2, ganador.numero)
        assertEquals(100.0, ganador.ganancia, 0.001)
    }

    @Test
    fun `ingreso bruto es precio por trozo multiplicado por los trozos`() {
        // "tomar precio por trozo y multiplicarlo por cada trozo"
        val d = receta(costoTotal = 3000.0, trozos = 8, porTrozo(1250.0))
        assertEquals(10000.0, ingresoBruto(d), 0.001)
    }

    // --- Modo producto ---

    @Test
    fun `precio por producto se reparte entre todos los trozos`() {
        val d = receta(costoTotal = 3000.0, trozos = 8, porProducto(10000.0))
        assertEquals(1250.0, precioEfectivoPorTrozo(d), 0.001)
        assertEquals(10000.0, ingresoBruto(d), 0.001)
    }

    @Test
    fun `promocion de dos productos completos`() {
        // "si llevas 2 productos, te lo dejo a x"
        val d = receta(costoTotal = 3000.0, trozos = 8, porProducto(16000.0, cantidad = 2))
        // 2 productos = 16 trozos, así que cada trozo sale 1.000
        assertEquals(1000.0, precioEfectivoPorTrozo(d), 0.001)
    }

    // --- Decisión #4: siempre gana el precio de MENOR ganancia ---

    @Test
    fun `entre varios precios se usa el de menor ganancia`() {
        val d = receta(
            costoTotal = 1400.0,
            trozos = 6,
            porTrozo(1000.0),                 // el más caro por trozo
            porTrozo(1500.0, cantidad = 2),   // 750 por trozo
            porTrozo(2000.0, cantidad = 4)    // 500 por trozo <- el peor caso
        )
        assertEquals(500.0, precioEfectivoPorTrozo(d), 0.001)
    }

    @Test
    fun `sin referencia elegida manda el de menor ganancia, como antes`() {
        val soloBase = receta(costoTotal = 1400.0, trozos = 6, porTrozo(500.0))
        val conPromoCara = receta(
            costoTotal = 1400.0, trozos = 6,
            porTrozo(500.0),
            porTrozo(900.0)   // deja más ganancia: no debe influir mientras nadie elija
        )
        assertEquals(precioEfectivoPorTrozo(soloBase), precioEfectivoPorTrozo(conPromoCara), 0.001)
        assertEquals(trozoGanador(soloBase).numero, trozoGanador(conPromoCara).numero)
        assertFalse(conPromoCara.tieneReferenciaElegida)
    }

    // --- Precio de referencia elegido a mano ---

    @Test
    fun `el precio marcado manda sobre el de menor ganancia`() {
        // Con la promo cara elegida, las cifras automáticas responden "cuánto ganaría con
        // ESTA", que es justamente para lo que sirve tener varias guardadas.
        val d = receta(
            costoTotal = 1400.0, trozos = 6,
            porTrozo(500.0),
            porTrozo(900.0).copy(esReferencia = true)
        )

        assertTrue(d.tieneReferenciaElegida)
        assertEquals(900.0, precioEfectivoPorTrozo(d), 0.001)
        assertEquals(5400.0, ingresoBruto(d), 0.001)
        assertEquals(4000.0, gananciaFinal(d), 0.001)
    }

    @Test
    fun `elegir la mas barata da las mismas cifras que antes`() {
        // Elegir a mano el peor caso tiene que dar exactamente lo de siempre: si no,
        // el cambio habría movido algo que no debía.
        val automatico = receta(costoTotal = 1400.0, trozos = 6, porTrozo(500.0), porTrozo(900.0))
        val elegido = receta(
            costoTotal = 1400.0, trozos = 6,
            porTrozo(500.0).copy(esReferencia = true),
            porTrozo(900.0)
        )
        assertEquals(precioEfectivoPorTrozo(automatico), precioEfectivoPorTrozo(elegido), 0.001)
        assertEquals(trozoGanador(automatico).numero, trozoGanador(elegido).numero)
    }

    @Test
    fun `la referencia tambien manda el trozo ganador y el sueldo`() {
        val conBarata = receta(
            costoTotal = 1400.0, trozos = 6,
            porTrozo(500.0).copy(esReferencia = true), porTrozo(900.0)
        )
        val conCara = receta(
            costoTotal = 1400.0, trozos = 6,
            porTrozo(500.0), porTrozo(900.0).copy(esReferencia = true)
        )

        assertEquals(3, trozoGanador(conBarata).numero)
        assertEquals(2, trozoGanador(conCara).numero)
        // El ingreso bruto es la base del sueldo (10.1): cambiar la referencia lo cambia.
        assertEquals(3000.0, ingresoBruto(conBarata), 0.001)
        assertEquals(5400.0, ingresoBruto(conCara), 0.001)
    }

    @Test
    fun `una promocion por producto tambien puede ser la referencia`() {
        val d = receta(
            costoTotal = 3000.0, trozos = 8,
            porTrozo(1000.0),
            porProducto(12000.0).copy(esReferencia = true)
        )
        assertEquals(1500.0, precioEfectivoPorTrozo(d), 0.001)
    }

    // --- La referencia no puede perder plata ---

    @Test
    fun `una promocion que pierde plata no se acepta como referencia`() {
        // Costo 3.000 entre 6 trozos: cada trozo cuesta 500 producirlo.
        val d = receta(costoTotal = 3000.0, trozos = 6, porTrozo(600.0))
        val promoQuePierde = porTrozo(800.0, cantidad = 2)   // deja el trozo en 400: pierde 100

        val motivo = errorAlElegirReferencia(promoQuePierde, d)

        assertEquals(MENSAJE_PROMOCION_CON_PERDIDAS, motivo)
    }

    @Test
    fun `una promocion que gana si se acepta`() {
        val d = receta(costoTotal = 3000.0, trozos = 6, porTrozo(600.0))
        assertNull(errorAlElegirReferencia(porTrozo(700.0), d))
    }

    @Test
    fun `cubrir el costo justo se acepta como referencia`() {
        // No deja ganancia, pero tampoco pérdida, y hay recetas que se venden así.
        val d = receta(costoTotal = 3000.0, trozos = 6, porTrozo(500.0))
        assertEquals(0.0, gananciaPorTrozoDe(porTrozo(500.0), d), 0.001)
        assertNull(errorAlElegirReferencia(porTrozo(500.0), d))
    }

    @Test
    fun `que no pueda ser referencia no impide guardarla ni verla`() {
        // La promo que pierde plata sigue en la lista, con su ganancia en negativo: esa es
        // justamente la información que hace falta para descartarla.
        val promoQuePierde = porTrozo(800.0, cantidad = 2)
        val d = receta(costoTotal = 3000.0, trozos = 6, porTrozo(600.0), promoQuePierde)

        assertEquals(2, d.precios.size)
        assertEquals(-100.0, gananciaPorTrozoDe(promoQuePierde, d), 0.001)
        assertEquals(MENSAJE_PROMOCION_CON_PERDIDAS, errorAlElegirReferencia(promoQuePierde, d))
    }

    @Test
    fun `si la unica promocion pierde plata las cifras salen en negativo`() {
        // Sin referencia elegida y con un solo precio bajo el costo, el respaldo es el de
        // menor ganancia -- que es ese mismo. Las cifras se muestran negativas, que es el
        // aviso que corresponde, en vez de dejar la receta sin ninguna cifra.
        val d = receta(costoTotal = 3000.0, trozos = 6, porTrozo(400.0))

        assertTrue(gananciaFinal(d) < 0)
        assertFalse(trozoGanador(d).alcanzable)
    }

    @Test
    fun `si la referencia desaparece se vuelve al respaldo, sin quedar sin cifras`() {
        // Borrar el precio que era la referencia deja a la receta sin ninguno marcado.
        // Las cifras no pueden quedar en blanco: pasan al de menor ganancia, y
        // `tieneReferenciaElegida` avisa que ese valor no lo eligió nadie.
        val conReferencia = receta(
            costoTotal = 1400.0, trozos = 6,
            porTrozo(500.0), porTrozo(900.0).copy(esReferencia = true)
        )
        val despuesDeBorrarla = receta(costoTotal = 1400.0, trozos = 6, porTrozo(500.0))

        assertEquals(900.0, precioEfectivoPorTrozo(conReferencia), 0.001)
        assertEquals(500.0, precioEfectivoPorTrozo(despuesDeBorrarla), 0.001)
        assertFalse(despuesDeBorrarla.tieneReferenciaElegida)
    }

    @Test
    fun `si por algun error quedaran dos marcadas, se usa la primera y no revienta`() {
        // No debería pasar: `fijarPrecioDeReferencia` apaga las demás en la misma
        // transacción. Pero si pasara, la cuenta tiene que dar algo definido en vez de
        // depender de cuál fila devuelva primero la base.
        val d = receta(
            costoTotal = 1400.0, trozos = 6,
            porTrozo(500.0).copy(esReferencia = true),
            porTrozo(900.0).copy(esReferencia = true)
        )
        assertEquals(500.0, precioEfectivoPorTrozo(d), 0.001)
    }

    // --- Casos límite que podrían reventar ---

    @Test
    fun `receta que se vende bajo su costo avisa que el trozo ganador no se alcanza`() {
        // Costo 5.000, trozos a 500, solo 8 trozos: harían falta 11.
        val d = receta(costoTotal = 5000.0, trozos = 8, porTrozo(500.0))
        val ganador = trozoGanador(d)

        assertEquals(11, ganador.numero)
        assertFalse("11 trozos no existen en una receta de 8", ganador.alcanzable)
        assertTrue("la ganancia final debe ser negativa", gananciaFinal(d) < 0)
    }

    @Test
    fun `cubrir el costo justo no cuenta como ganar`() {
        // Costo 1.500 con trozos a 500: al tercero se empata, el ganador es el cuarto.
        val d = receta(costoTotal = 1500.0, trozos = 6, porTrozo(500.0))
        assertEquals(4, trozoGanador(d).numero)
    }

    @Test
    fun `receta sin costo gana desde el primer trozo`() {
        val d = receta(costoTotal = 0.0, trozos = 4, porTrozo(500.0))
        val ganador = trozoGanador(d)
        assertEquals(1, ganador.numero)
        assertEquals(500.0, ganador.ganancia, 0.001)
        assertTrue(ganador.alcanzable)
    }

    @Test
    fun `ganancia por trozo negativa cuando el precio no cubre el costo`() {
        val d = receta(costoTotal = 4000.0, trozos = 4, porTrozo(500.0))
        assertEquals(-500.0, gananciaPorTrozo(d), 0.001)
        assertEquals(-2000.0, gananciaFinal(d), 0.001)
    }

    @Test
    fun `una receta sin precios se puede consultar sin reventar`() {
        val d = receta(costoTotal = 1000.0, trozos = 4)
        assertFalse(d.tienePrecio)
        // costoPorTrozo no depende del precio: debe seguir funcionando
        assertEquals(250.0, costoPorTrozo(d), 0.001)
    }

    @Test(expected = IllegalStateException::class)
    fun `pedir el precio de una receta sin precios lanza excepcion`() {
        precioDeMenorGanancia(receta(costoTotal = 1000.0, trozos = 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una receta no puede tener cero trozos`() {
        receta(costoTotal = 1000.0, trozos = 0, porTrozo(500.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un precio que no cubre ningun trozo lanza excepcion en vez de dividir por cero`() {
        val d = receta(costoTotal = 1000.0, trozos = 4, porTrozo(500.0, cantidad = 0))
        precioEfectivoPorTrozo(d)
    }
}
