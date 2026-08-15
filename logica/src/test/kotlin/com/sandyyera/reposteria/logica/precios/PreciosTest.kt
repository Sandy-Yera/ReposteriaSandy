package com.sandyyera.reposteria.logica.precios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        // "si llevas 2 productos, te lo dejo a x".
        //
        // **Esta prueba cambió de respuesta al arreglar el resto (8.6.1)**, y el cambio es el
        // arreglo: antes daba 1.000 por trozo, que es 16.000 repartido entre los 16 trozos de
        // los DOS productos. Pero estas cifras miden **un** producto, y una promoción de dos
        // no se aplica vendiendo uno. Lo que se cobra por ese uno es su precio individual.
        val d = receta(
            costoTotal = 3000.0,
            trozos = 8,
            porProducto(9000.0),                  // el precio del producto suelto
            porProducto(16000.0, cantidad = 2)
        )

        assertEquals(9000.0, ingresoBruto(d), 0.001)
        assertEquals(1125.0, precioEfectivoPorTrozo(d), 0.001)   // 9.000 / 8
    }

    @Test
    fun `y sin el precio del producto suelto se marca en vez de inventar`() {
        val d = receta(costoTotal = 3000.0, trozos = 8, porProducto(16000.0, cantidad = 2))

        assertTrue(repartoDeUnProducto(d).faltaElPrecioSuelto)
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

        // Sigue eligiendo la de 4, que es la que menos deja.
        assertEquals(4, precioDeReferencia(d).cantidad)

        // **Lo que cambió es cuánto vale eso de verdad** (8.6.1): en 6 trozos, esa promo de 4
        // entra UNA vez y sobran 2, que se venden a 1.000 cada uno. Son 2.000 + 2.000 = 4.000
        // por el producto, o sea 666,67 por trozo — y no los 500 de antes, que salían de
        // suponer que los seis se vendían a precio de promoción.
        assertEquals(4000.0, ingresoBruto(d), 0.001)
        assertEquals(666.67, precioEfectivoPorTrozo(d), 0.01)
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
    fun `un precio con el que no alcanza para pagar a los empleados no se acepta`() {
        // Lo pidió Sandy: la app dejaba elegir uno con el que el reparto quedaba imposible, y
        // eso solo se descubría entrando a Empleados. Es la misma regla del precio que pierde
        // plata, mirada un paso más allá — de la referencia salen los sueldos.
        val d = receta(costoTotal = 3000.0, trozos = 6, porTrozo(600.0))
        // 600 x 6 = 3.600 de ingreso, 3.000 de costo: quedan 600 de ganancia por producto.

        val motivo = errorAlElegirReferencia(porTrozo(600.0), d, seLlevanLosEmpleados = 900.0)

        assertEquals(MENSAJE_NO_ALCANZA_PARA_LOS_EMPLEADOS, motivo)
    }

    @Test
    fun `si alcanza justo para pagarles, el precio se acepta`() {
        // Igual que cubrir el costo justo: no sobra nada, pero tampoco falta.
        val d = receta(costoTotal = 3000.0, trozos = 6, porTrozo(600.0))

        assertNull(errorAlElegirReferencia(porTrozo(600.0), d, seLlevanLosEmpleados = 600.0))
    }

    @Test
    fun `sin empleados el precio se mide como siempre`() {
        // El parámetro llega en 0 desde todos los lados que no saben de empleados, y ahí la
        // regla tiene que ser exactamente la de antes.
        val d = receta(costoTotal = 3000.0, trozos = 6, porTrozo(600.0))

        assertNull(errorAlElegirReferencia(porTrozo(600.0), d, seLlevanLosEmpleados = 0.0))
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

    // --- El resto de una promoción que no divide exacto (8.6.1) ---

    @Test
    fun `una promo de 2 en una receta de 3 no vende los tres a precio de promo`() {
        // El error que se vio en el celular: la app decía 4.500 -1.500 por trozo, por tres-
        // cuando esa promoción solo existe si se llevan dos. El tercero se vende suelto.
        val d = DatosCalculoReceta(
            recetaId = 1, titulo = "Torta", costoTotal = 900.0, trozos = 3,
            precios = listOf(
                PrecioVigente(ModoPrecio.TROZO, 1, 2000.0),
                PrecioVigente(ModoPrecio.TROZO, 2, 3000.0, esReferencia = true)
            )
        )

        val reparto = repartoDeUnProducto(d)

        assertEquals("Una promo entera", 1, reparto.cuantasVecesEntra)
        assertEquals("Y un trozo suelto", 1, reparto.sueltos)
        assertTrue(reparto.huboResto)
        // 3.000 de la promo + 2.000 del suelto = 5.000. NO 4.500.
        assertEquals(5000.0, reparto.total, 0.001)
        assertEquals(5000.0, ingresoBruto(d), 0.001)
    }

    @Test
    fun `cuando divide exacto no sobra nada y no hay nada que avisar`() {
        val d = DatosCalculoReceta(
            recetaId = 1, titulo = "Torta", costoTotal = 1200.0, trozos = 4,
            precios = listOf(
                PrecioVigente(ModoPrecio.TROZO, 1, 2000.0),
                PrecioVigente(ModoPrecio.TROZO, 2, 3000.0, esReferencia = true)
            )
        )

        val reparto = repartoDeUnProducto(d)

        assertEquals(2, reparto.cuantasVecesEntra)
        assertEquals(0, reparto.sueltos)
        assertFalse(reparto.huboResto)
        assertEquals(6000.0, reparto.total, 0.001)
    }

    @Test
    fun `el precio base por trozo se comporta igual que antes`() {
        // Con la promo de cantidad 1, cada trozo es una "promo" y no sobra nada: la cuenta
        // vieja y la nueva tienen que dar lo mismo, o esto rompería todo lo que ya andaba.
        val d = DatosCalculoReceta(
            recetaId = 1, titulo = "Torta", costoTotal = 1400.0, trozos = 6,
            precios = listOf(PrecioVigente(ModoPrecio.TROZO, 1, 500.0, esReferencia = true))
        )

        assertEquals(3000.0, ingresoBruto(d), 0.001)
        assertEquals(500.0, precioEfectivoPorTrozo(d), 0.001)
        assertEquals(3, trozoGanador(d).numero)
    }

    @Test
    fun `sin precio individual no se inventa lo que sobra, se marca`() {
        // Es la red de la decisión de Sandy: si falta el base, la app avisa en vez de mostrar
        // un número que da de menos sin decirlo.
        val d = DatosCalculoReceta(
            recetaId = 1, titulo = "Torta", costoTotal = 900.0, trozos = 3,
            precios = listOf(PrecioVigente(ModoPrecio.TROZO, 2, 3000.0, esReferencia = true))
        )

        val reparto = repartoDeUnProducto(d)

        assertTrue(reparto.faltaElPrecioSuelto)
        assertEquals("Solo cuenta la promoción que sí cabe", 3000.0, reparto.total, 0.001)
    }

    @Test
    fun `una promo de dos productos completos no se aplica al vender uno`() {
        // El mismo razonamiento un piso más arriba: `ingresoBruto` mide UN producto, y una
        // promo de dos no existe todavía a esa altura.
        val d = DatosCalculoReceta(
            recetaId = 1, titulo = "Torta", costoTotal = 1400.0, trozos = 6,
            precios = listOf(
                PrecioVigente(ModoPrecio.PRODUCTO, 1, 9000.0),
                PrecioVigente(ModoPrecio.PRODUCTO, 2, 16000.0, esReferencia = true)
            )
        )

        val reparto = repartoDeUnProducto(d)

        assertEquals("La promo de 2 no entra en 1", 0, reparto.cuantasVecesEntra)
        assertEquals(1, reparto.sueltos)
        assertEquals("Se vende al precio del producto entero", 9000.0, reparto.total, 0.001)
    }

    @Test
    fun `un precio del producto completo sigue dando lo mismo que antes`() {
        val d = DatosCalculoReceta(
            recetaId = 1, titulo = "Torta", costoTotal = 1400.0, trozos = 6,
            precios = listOf(PrecioVigente(ModoPrecio.PRODUCTO, 1, 9000.0, esReferencia = true))
        )

        assertEquals(9000.0, ingresoBruto(d), 0.001)
        assertEquals(1500.0, precioEfectivoPorTrozo(d), 0.001)
    }

    @Test
    fun `repartir sirve para cualquier total, que es lo que necesita la simulacion`() {
        // Dos productos de 3 trozos son 6 trozos, y ahí la promo de 2 SÍ entra tres veces:
        // la duda de "¿y si vendo más de uno?" se resuelve repartiendo el total, no
        // multiplicando el resultado de un producto.
        val promo = PrecioVigente(ModoPrecio.TROZO, 2, 3000.0)
        val suelto = PrecioVigente(ModoPrecio.TROZO, 1, 2000.0)

        val dosProductos = repartir(aVender = 6, promocion = promo, precioIndividual = suelto)

        assertEquals(3, dosProductos.cuantasVecesEntra)
        assertEquals(0, dosProductos.sueltos)
        assertEquals(9000.0, dosProductos.total, 0.001)
        // Y no es lo mismo que multiplicar por dos lo de un producto (5.000 x 2 = 10.000).
        assertNotEquals(10000.0, dosProductos.total, 0.001)
    }

    @Test
    fun `los dos precios base se reconocen por su marca`() {
        val d = DatosCalculoReceta(
            recetaId = 1, titulo = "Torta", costoTotal = 900.0, trozos = 3,
            precios = listOf(
                PrecioVigente(ModoPrecio.TROZO, 1, 2000.0, esBase = true),
                PrecioVigente(ModoPrecio.PRODUCTO, 1, 5500.0, esBase = true),
                PrecioVigente(ModoPrecio.TROZO, 2, 3000.0)
            )
        )

        assertEquals(2000.0, precioBasePorTrozo(d)!!.precioTotal, 0.001)
        assertEquals(5500.0, precioBaseDelProducto(d)!!.precioTotal, 0.001)
        assertTrue(esPrecioBase(precioBasePorTrozo(d)!!))
        assertFalse(esPrecioBase(PrecioVigente(ModoPrecio.TROZO, 2, 3000.0)))
    }

    @Test
    fun `con varios precios de un trozo manda el marcado, no el primero`() {
        // El caso que motivó la marca: Sandy quería tantear "¿y si el trozo valiera 2.500?"
        // sin perder el precio que ya tenía. Con la base deducida de `cantidad == 1` eso era
        // imposible, porque el segundo habría sido indistinguible del primero.
        val d = DatosCalculoReceta(
            recetaId = 1, titulo = "Torta", costoTotal = 900.0, trozos = 3,
            precios = listOf(
                PrecioVigente(ModoPrecio.TROZO, 1, 2000.0, etiqueta = "tanteo"),
                PrecioVigente(ModoPrecio.TROZO, 1, 2500.0, esBase = true)
            )
        )

        assertEquals("Manda el marcado aunque no sea el primero", 2500.0, precioBasePorTrozo(d)!!.precioTotal, 0.001)
        assertFalse(esPrecioBase(d.precios[0]))
        assertTrue("Pero el otro podría serlo", puedeSerBase(d.precios[0]))
        assertFalse("Una promoción no", puedeSerBase(PrecioVigente(ModoPrecio.TROZO, 2, 3000.0)))
    }

    @Test
    fun `sin ninguna marcada se cae en la primera de cantidad uno`() {
        // La red para las recetas guardadas antes de la versión 9. Sin ella, una fila mal
        // migrada dejaría de poder cobrar sus trozos sueltos **sin avisar**.
        val d = DatosCalculoReceta(
            recetaId = 1, titulo = "Torta", costoTotal = 900.0, trozos = 3,
            precios = listOf(
                PrecioVigente(ModoPrecio.TROZO, 2, 3000.0),
                PrecioVigente(ModoPrecio.TROZO, 1, 2000.0)
            )
        )

        assertEquals(2000.0, precioBasePorTrozo(d)!!.precioTotal, 0.001)
        assertEquals("Y sigue faltando la del producto", listOf(ModoPrecio.PRODUCTO), basesQueFaltanEn(d.precios))
    }
}
