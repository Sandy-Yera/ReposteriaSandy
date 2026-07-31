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
}
