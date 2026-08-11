package com.sandyyera.reposteria.logica.almacen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Descontar del almacén lo que se gastó haciendo recetas (14.9).
 *
 * Todo acá es una cuenta, nunca una escritura: la pantalla muestra la vista previa completa y
 * recién al confirmar el repositorio escribe. Descontar toca muchas filas de una vez, y lo que
 * evita el desastre es poder mirar antes qué le va a pasar a cada una.
 */
class DescuentoTest {

    private val torta = 1L
    private val queque = 2L
    private val harina = 10L
    private val azucar = 11L
    private val cajas = 12L

    private val gastos = mapOf(
        torta to listOf(
            GastoDeIngrediente(harina, 500.0),
            GastoDeIngrediente(azucar, 200.0),
            GastoDeIngrediente(cajas, 1.0)
        ),
        queque to listOf(
            GastoDeIngrediente(harina, 300.0),
            GastoDeIngrediente(azucar, 100.0)
        )
    )

    private fun almacen(vararg filas: FilaParaDescontar) = filas.toList()

    // --- Lo que se gasta ---

    @Test
    fun `dos recetas distintas suman sobre el mismo ingrediente`() {
        val seGasta = loQueSeGasta(
            listOf(RecetaHecha(torta, "Torta", 1.0), RecetaHecha(queque, "Queque", 1.0)),
            gastos
        )

        assertEquals(800.0, seGasta[harina]!!, 0.001)
        assertEquals(300.0, seGasta[azucar]!!, 0.001)
        assertEquals(1.0, seGasta[cajas]!!, 0.001)
    }

    @Test
    fun `la misma receta dos veces se suma, no se reemplaza`() {
        // Escrita dos veces en la lista es "la hice dos veces". Reemplazar en vez de sumar
        // descontaría una sola tanda y el almacén quedaría alto sin que nada lo dijera.
        val seGasta = loQueSeGasta(
            listOf(RecetaHecha(torta, "Torta", 1.0), RecetaHecha(torta, "Torta", 1.0)),
            gastos
        )

        assertEquals(1000.0, seGasta[harina]!!, 0.001)
    }

    @Test
    fun `media tanda descuenta la mitad`() {
        // Media tanda es normal en repostería. Obligar a un entero empujaría a anotar una tanda
        // entera y corregir el frasco después, que es justo el trabajo que esto ahorra.
        val seGasta = loQueSeGasta(listOf(RecetaHecha(torta, "Torta", 0.5)), gastos)

        assertEquals(250.0, seGasta[harina]!!, 0.001)
        assertEquals(0.5, seGasta[cajas]!!, 0.001)
    }

    @Test
    fun `cero tandas no gasta nada, y una receta que ya no existe tampoco revienta`() {
        assertTrue(loQueSeGasta(listOf(RecetaHecha(torta, "Torta", 0.0)), gastos).isEmpty())
        // Se pudo borrar entre que se abrió el cuadro y se confirmó: lo correcto es descontar
        // el resto y no perder la operación entera.
        val seGasta = loQueSeGasta(
            listOf(RecetaHecha(99L, "Borrada", 1.0), RecetaHecha(queque, "Queque", 1.0)),
            gastos
        )
        assertEquals(300.0, seGasta[harina]!!, 0.001)
    }

    // --- La vista previa ---

    @Test
    fun `la vista previa dice de cuanto se parte y en cuanto queda`() {
        val previa = vistaPreviaDelDescuento(
            loQueSeGasta(listOf(RecetaHecha(torta, "Torta", 1.0)), gastos),
            almacen(
                FilaParaDescontar(harina, "Harina", esObjeto = false, cantidad = 2000.0),
                FilaParaDescontar(cajas, "Cajas", esObjeto = true, cantidad = 12.0)
            )
        )

        val laHarina = previa.filas.single { it.ingredienteId == harina }
        assertEquals(2000.0, laHarina.habia, 0.001)
        assertEquals(500.0, laHarina.seUsa, 0.001)
        assertEquals(1500.0, laHarina.quedara, 0.001)
        assertEquals("500 g", laHarina.comoSeLeeLoQueSeUsa)
        assertEquals("1.500 g", laHarina.comoSeLeeLoQueQueda)
        assertFalse(laHarina.quedaNegativo)

        // Y los objetos se leen en unidades, con su singular cuidado.
        val lasCajas = previa.filas.single { it.ingredienteId == cajas }
        assertEquals("1 unidad", lasCajas.comoSeLeeLoQueSeUsa)
        assertEquals("11 unidades", lasCajas.comoSeLeeLoQueQueda)
    }

    @Test
    fun `un ingrediente que queda bajo cero se muestra con su aviso`() {
        // El caso que Sandy quiere ver, no esconder: o entró algo sin anotar, o la receta pide
        // más de lo que de verdad usa.
        val previa = vistaPreviaDelDescuento(
            loQueSeGasta(listOf(RecetaHecha(torta, "Torta", 1.0)), gastos),
            almacen(FilaParaDescontar(harina, "Harina", esObjeto = false, cantidad = 300.0))
        )

        val laHarina = previa.filas.single()
        assertEquals(-200.0, laHarina.quedara, 0.001)
        assertTrue(laHarina.quedaNegativo)
        assertTrue(previa.hayNegativos)
        assertNotNull("Y lo dice", laHarina.aviso)
        assertTrue(laHarina.aviso!!.contains("200 g bajo cero"))
    }

    @Test
    fun `lo que se gasta y no esta anotado se dice en vez de desaparecer`() {
        // No es un error —hay cosas que se usan sin llevarles la cuenta— pero callarlo dejaría
        // la impresión de que se descontó todo.
        val previa = vistaPreviaDelDescuento(
            loQueSeGasta(listOf(RecetaHecha(torta, "Torta", 1.0)), gastos),
            almacen(FilaParaDescontar(harina, "Harina", esObjeto = false, cantidad = 2000.0))
        ).conLosNombres(mapOf(azucar to "Azúcar", cajas to "Cajas de torta"))

        assertEquals(1, previa.filas.size)
        assertEquals(2, previa.sinAnotar.size)
        assertEquals(
            listOf("Azúcar", "Cajas de torta"),
            previa.sinAnotar.map { it.nombre }.sorted()
        )
    }

    @Test
    fun `una fila del almacen que no se usa no aparece en la previa`() {
        // La lista se revisa entera antes de confirmar: meter las veinte filas que no se tocan
        // esconde las tres que sí.
        val previa = vistaPreviaDelDescuento(
            loQueSeGasta(listOf(RecetaHecha(queque, "Queque", 1.0)), gastos),
            almacen(
                FilaParaDescontar(harina, "Harina", esObjeto = false, cantidad = 2000.0),
                FilaParaDescontar(cajas, "Cajas", esObjeto = true, cantidad = 12.0)
            )
        )

        assertEquals(1, previa.cuantasFilas)
        assertEquals(harina, previa.filas.single().ingredienteId)
    }

    @Test
    fun `sin nada elegido no hay nada que descontar`() {
        val previa = vistaPreviaDelDescuento(
            emptyMap(),
            almacen(FilaParaDescontar(harina, "Harina", esObjeto = false, cantidad = 2000.0))
        )

        assertFalse(previa.hayAlgoQueDescontar)
        assertFalse(previa.hayNegativos)
        assertTrue(previa.sinAnotar.isEmpty())
    }

    @Test
    fun `el orden de la previa es el del almacen y no el del resultado`() {
        // Reordenar por "los negativos arriba" haría que las mismas cosas cambiaran de lugar
        // entre una vez y la siguiente, y eso obliga a releer la lista completa cada vez.
        val previa = vistaPreviaDelDescuento(
            loQueSeGasta(listOf(RecetaHecha(torta, "Torta", 1.0)), gastos),
            almacen(
                FilaParaDescontar(azucar, "Azúcar", esObjeto = false, cantidad = 50.0),
                FilaParaDescontar(cajas, "Cajas", esObjeto = true, cantidad = 12.0),
                FilaParaDescontar(harina, "Harina", esObjeto = false, cantidad = 2000.0)
            )
        )

        assertEquals(listOf(azucar, cajas, harina), previa.filas.map { it.ingredienteId })
        assertTrue("Y el negativo sigue en su lugar", previa.filas.first().quedaNegativo)
    }

    // --- Sumar, no solo restar (14.8) ---

    @Test
    fun `entra y sale son el mismo numero en dos sentidos`() {
        assertEquals(1500.0, resultadoDelMovimiento(1000.0, 500.0, SentidoDelMovimiento.ENTRA), 0.001)
        assertEquals(500.0, resultadoDelMovimiento(1000.0, 500.0, SentidoDelMovimiento.SALE), 0.001)
    }

    @Test
    fun `restar de mas ya no se recorta en cero`() {
        // Cambió a pedido de Sandy y con mejor razón que la que tenía: el cero era el dato
        // cómodo, no el verdadero. El negativo dice cuánto se compró sin anotar, o cuánto pide
        // de más una receta.
        assertEquals(-200.0, loQueQueda(300.0, 500.0), 0.001)
        assertNotNull(avisoDeCantidadNegativa(-200.0, esObjeto = false))
        assertNull("Y en cero no hay nada que avisar", avisoDeCantidadNegativa(0.0, esObjeto = false))
    }

    @Test
    fun `el aviso del negativo usa la unidad que corresponde`() {
        assertTrue(avisoDeCantidadNegativa(-3.0, esObjeto = true)!!.contains("3 unidades bajo cero"))
        assertTrue(avisoDeCantidadNegativa(-3.0, esObjeto = false)!!.contains("3 g bajo cero"))
    }

    @Test
    fun `sumar sobre un negativo lo saca del pozo`() {
        // Es el caso que el recorte en cero hacía imposible de arreglar bien: con el stock
        // hundido en -200, comprar un kilo tiene que dejar 800 y no 1.000.
        assertEquals(800.0, resultadoDelMovimiento(-200.0, 1000.0, SentidoDelMovimiento.ENTRA), 0.001)
    }
}
