package com.sandyyera.reposteria.logica.partes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cómo se agrupan los pasos bajo sus títulos para dibujarlos (8.8).
 *
 * Los pasos se guardan planos y se ven en bloques. Las reglas de esa traducción son cuatro y
 * tres de ellas solo se notan mirando un caso concreto —dos generales pegados, un general
 * anidado, el bloque único—, que es justo lo que una prueba fija y un ojo deja pasar.
 */
class BloquesTest {

    private val bizcocho = SeccionParaTitulo(1, "Bizcocho")
    private val crema = SeccionParaTitulo(2, "Crema")
    private val secciones = listOf(bizcocho, crema)

    private fun paso(
        orden: Int,
        titulo: TituloDePaso,
        anidado: Boolean = false
    ) = PasoParaMostrar(
        id = orden.toLong(), texto = "paso $orden", titulo = titulo,
        esGeneralAnidado = anidado, orden = orden
    )

    // --- El ejemplo de la arquitectura ---

    @Test
    fun `el ejemplo de 8 punto 8, tal cual`() {
        // Bizcocho (1, 2) · General (3) · Crema (4) · y de nuevo Bizcocho (5).
        val bloques = bloquesDePasos(
            listOf(
                paso(1, bizcocho.id), paso(2, bizcocho.id),
                paso(3, null),
                paso(4, crema.id),
                paso(5, bizcocho.id)
            ),
            secciones
        )

        assertEquals(4, bloques.size)
        assertEquals(
            listOf("Bizcocho", "General", "Crema", "Bizcocho"),
            bloques.map { it.encabezado }
        )
        // La misma sección puede volver más adelante: "Crema" al principio y "Crema" al final
        // son dos momentos distintos de la preparación, no un error.
        assertEquals(listOf(1, 2), bloques[0].pasos.map { it.numero })
        assertEquals(listOf(5), bloques[3].pasos.map { it.numero })
    }

    @Test
    fun `la numeracion es corrida y no reinicia en cada bloque`() {
        // Reiniciar daría tres "paso 1" y haría imposible decir "me quedé en el 7".
        val bloques = bloquesDePasos(
            listOf(paso(1, bizcocho.id), paso(2, crema.id), paso(3, null)),
            secciones
        )

        assertEquals(listOf(1, 2, 3), bloques.flatMap { b -> b.pasos.map { it.numero } })
    }

    @Test
    fun `los pasos se ordenan por su orden y no por como llegaron`() {
        val bloques = bloquesDePasos(
            listOf(paso(3, crema.id), paso(1, bizcocho.id), paso(2, bizcocho.id)),
            secciones
        )

        assertEquals(listOf("Bizcocho", "Crema"), bloques.map { it.encabezado })
        assertEquals(listOf(1, 2, 3), bloques.flatMap { b -> b.pasos.map { it.numero } })
    }

    // --- Los generales pegados ---

    @Test
    fun `dos generales seguidos son un solo bloque`() {
        // Son el mismo bloque partido en dos: repetir el encabezado no significa nada.
        val bloques = bloquesDePasos(
            listOf(paso(1, null), paso(2, null), paso(3, crema.id)),
            secciones
        )

        assertEquals(2, bloques.size)
        assertEquals(2, bloques[0].pasos.size)
    }

    @Test
    fun `entre medio de otras secciones si puede haber varios generales`() {
        // No se juntan porque no están pegados, y ahí la separación sí significa algo.
        val bloques = bloquesDePasos(
            listOf(paso(1, null), paso(2, crema.id), paso(3, null)),
            secciones
        )

        assertEquals(3, bloques.size)
        assertEquals(listOf("General", "Crema", "General"), bloques.map { it.encabezado })
    }

    // --- El general anidado (8.8) ---

    @Test
    fun `un general anidado no se junta con uno normal, aunque los dos sean General`() {
        // Es la distinción que 8.8 pide conservar: uno habla del bizcocho que se trajo y el
        // otro de la torta entera. Aplanarlos los volvería indistinguibles.
        val bloques = bloquesDePasos(
            listOf(paso(1, null, anidado = true), paso(2, null)),
            secciones
        )

        assertEquals(2, bloques.size)
        assertTrue(bloques[0].esGeneralAnidado)
        assertTrue(!bloques[1].esGeneralAnidado)
    }

    @Test
    fun `dos anidados seguidos si se juntan entre si`() {
        val bloques = bloquesDePasos(
            listOf(paso(1, null, anidado = true), paso(2, null, anidado = true)),
            secciones
        )

        assertEquals(1, bloques.size)
        assertEquals(2, bloques.single().pasos.size)
    }

    @Test
    fun `un anidado solo si lleva encabezado`() {
        // La regla del bloque único es para el General de la receta: un anidado dice de qué
        // receta traída habla, y sin encabezado se confundiría con los pasos propios.
        val bloques = bloquesDePasos(listOf(paso(1, null, anidado = true)), secciones)

        assertEquals("General", bloques.single().encabezado)
    }

    // --- El bloque único ---

    @Test
    fun `el encabezado General no se dibuja si es el unico bloque`() {
        // Repetiría lo que ya dice el título de la receta.
        val bloques = bloquesDePasos(listOf(paso(1, null), paso(2, null)), secciones)

        assertEquals(1, bloques.size)
        assertNull(bloques.single().encabezado)
        assertEquals("Pero los pasos siguen ahí", 2, bloques.single().pasos.size)
    }

    @Test
    fun `una seccion sola si lleva su encabezado`() {
        // Que sea el único bloque no lo hace redundante: alguien la nombró a propósito, y sin
        // el encabezado no se sabría de qué parte se está hablando.
        val bloques = bloquesDePasos(listOf(paso(1, bizcocho.id)), secciones)

        assertEquals("Bizcocho", bloques.single().encabezado)
    }

    // --- Los bordes ---

    @Test
    fun `una receta sin pasos no tiene bloques`() {
        assertTrue(bloquesDePasos(emptyList(), secciones).isEmpty())
    }

    @Test
    fun `un paso de una seccion que ya no existe se dibuja como General`() {
        // No debería pasar —borrar una sección se lleva sus pasos— pero una fila puede quedar
        // suelta, y hacer desaparecer texto que alguien escribió es peor que mostrarlo sin su
        // encabezado.
        val bloques = bloquesDePasos(listOf(paso(1, 999L)), secciones)

        assertEquals("General", bloques.single().encabezado)
        assertEquals(1, bloques.single().pasos.size)
    }

    @Test
    fun `el titulo del bloque se conserva aunque el encabezado no se dibuje`() {
        // La pantalla necesita el título para saber qué bloque está editando, incluso cuando
        // decide no dibujar su encabezado.
        val bloques = bloquesDePasos(listOf(paso(1, null)), secciones)

        assertNull(bloques.single().encabezado)
        assertNull("Y el título sigue siendo el General", bloques.single().titulo)
    }
}
