package com.sandyyera.reposteria.ui.recetas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de los pasos de una receta, que son los que dibuja `FilaDePasos`.
 *
 * La fila en sí es un Composable y no se puede probar sin celular, pero lo que la fila
 * muestra sale entero de este `enum`: si un paso llega sin título o con el título de otro,
 * la fila queda con una ficha en blanco o con dos iguales y no hay forma de distinguirlas.
 * Eso sí se puede comprobar acá, y es justo lo que se olvida al agregar el cuarto paso.
 */
class PasoDeRecetaTest {

    @Test
    fun `todos los pasos tienen un titulo que se puede leer`() {
        PasoDeReceta.entries.forEach { paso ->
            assertTrue(
                "El paso ${paso.name} quedó sin título y su ficha saldría en blanco",
                paso.titulo.isNotBlank()
            )
        }
    }

    @Test
    fun `ningun titulo se repite`() {
        val titulos = PasoDeReceta.entries.map { it.titulo }
        assertEquals(
            "Dos pasos con el mismo título dejan la fila imposible de usar",
            titulos.size,
            titulos.distinct().size
        )
    }

    @Test
    fun `el primer paso es cantidades`() {
        // Abrir una receta siempre empieza por acá, y la fila se dibuja en el orden del
        // enum: el primero de la lista y el primero que se muestra tienen que ser el mismo.
        assertEquals(PasoDeReceta.CANTIDADES, PasoDeReceta.entries.first())
    }

    @Test
    fun `los cuatro pasos que dependen entre si van seguidos`() {
        // Lo pidió Sandy: duración enclavada entre rendimiento y gastos obligaba a saltarla
        // cada vez que se recorría la cadena de las cifras. Es el único paso que no alimenta
        // ninguna cuenta, así que sale del medio y los otros cuatro quedan en su orden de
        // dependencia: el molde decide el rendimiento, el rendimiento los gastos, y los
        // gastos la simulación.
        assertEquals(
            listOf(
                PasoDeReceta.MOLDE,
                PasoDeReceta.RENDIMIENTO,
                PasoDeReceta.GASTOS,
                PasoDeReceta.SIMULACION
            ),
            PasoDeReceta.entries.filterNot {
                it == PasoDeReceta.CANTIDADES ||
                    it == PasoDeReceta.DURACION ||
                    // Pasos tampoco alimenta ninguna cifra, pero va al final y no segundo:
                    // es lo más largo de escribir y se hace cuando lo demás ya está decidido.
                    it == PasoDeReceta.PASOS
            }
        )
        assertEquals("Y duración queda segunda", PasoDeReceta.DURACION, PasoDeReceta.entries[1])
        assertEquals("Y pasos, último", PasoDeReceta.PASOS, PasoDeReceta.entries.last())
    }
}
