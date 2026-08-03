package com.sandyyera.reposteria.logica.partes

import com.sandyyera.reposteria.logica.validaciones.NOMBRE_SECCION_POR_DEFECTO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los títulos bajo los que van los pasos (8.8).
 *
 * La regla que ordena todo: los títulos **son las secciones de la receta, más "General"**, y
 * solo el General se puede repetir. Está en `logica/` y no en la pantalla porque de ella
 * dependen dos cosas que tienen que coincidir: qué se ofrece en el menú y qué se acepta al
 * confirmar. Si cada una la aplicara por su cuenta, el menú podría ofrecer algo que después
 * se rechaza.
 */
class TitulosTest {

    private val bizcocho = SeccionParaTitulo(1L, "Bizcocho")
    private val crema = SeccionParaTitulo(2L, "Crema")
    private val decoracion = SeccionParaTitulo(3L, "Decoración")
    private val general: TituloDePaso = null

    // --- Qué se puede repetir ---

    @Test
    fun `solo el General se repite`() {
        assertTrue(elTituloSePuedeRepetir(general))
        assertFalse(elTituloSePuedeRepetir(bizcocho.id))
    }

    @Test
    fun `el General se puede usar todas las veces que haga falta`() {
        // Un paso general -precalentar el horno, dejar enfriar- aparece naturalmente entre
        // medio de las partes, así que el bloque tiene que poder repetirse.
        assertNull(errorAlUsarTitulo(general, listOf(general, general), TITULO_GENERAL))
    }

    @Test
    fun `una seccion usada dos veces se rechaza, y el aviso dice por que`() {
        val error = errorAlUsarTitulo(crema.id, listOf(bizcocho.id, crema.id), "Crema")
        assertNotNull(error)
        assertTrue("Nombra la sección, no un id", error!!.contains("Crema"))
        assertTrue("Y explica el problema, no solo la regla", error.contains("cada paso"))
    }

    @Test
    fun `una seccion que todavia no se uso pasa`() {
        assertNull(errorAlUsarTitulo(decoracion.id, listOf(bizcocho.id, general), "Decoración"))
    }

    // --- Editar un bloque que ya existe ---

    @Test
    fun `un bloque no se rechaza a si mismo al reconfirmar su titulo`() {
        // Al tocar un bloque que ya era "Crema", los usados que llegan son los de los OTROS
        // bloques. Si el propio se colara en la lista, confirmar lo mismo daría error sobre
        // algo que nadie cambió.
        val losDemas = listOf(bizcocho.id, general)
        assertNull(errorAlUsarTitulo(crema.id, losDemas, "Crema"))
    }

    @Test
    fun `el bloque que se esta editando ve su propio titulo en el menu`() {
        // Mismo caso desde el menú: "Crema" tiene que seguir apareciendo, o el bloque no
        // podría quedarse como estaba.
        val disponibles = titulosDisponibles(
            seccionesDeLaReceta = listOf(bizcocho, crema, decoracion),
            usadosPorOtrosBloques = listOf(bizcocho.id, general)
        )
        assertTrue(crema.id in disponibles)
    }

    // --- Lo que se ofrece en el menú ---

    @Test
    fun `el menu ofrece las secciones sin usar, y el General siempre`() {
        val disponibles = titulosDisponibles(
            seccionesDeLaReceta = listOf(bizcocho, crema, decoracion),
            usadosPorOtrosBloques = listOf(bizcocho.id, general)
        )

        assertEquals(listOf(crema.id, decoracion.id, general), disponibles)
    }

    @Test
    fun `el General sigue estando aunque ya se haya usado tres veces`() {
        val disponibles = titulosDisponibles(
            seccionesDeLaReceta = listOf(bizcocho, crema),
            usadosPorOtrosBloques = listOf(bizcocho.id, crema.id, general, general, general)
        )
        assertEquals(listOf(general), disponibles)
    }

    @Test
    fun `el menu nunca queda vacio`() {
        // Aunque no quede ninguna sección libre: un menú sin nada que elegir sería un
        // callejón sin salida.
        val todasUsadas = listOf(bizcocho.id, crema.id, decoracion.id)
        val disponibles = titulosDisponibles(listOf(bizcocho, crema, decoracion), todasUsadas)
        assertEquals(listOf(general), disponibles)
    }

    @Test
    fun `el menu nunca ofrece algo que despues se rechaza`() {
        // Es la razón de que las dos reglas vivan juntas: lo que se ofrece y lo que se
        // acepta no pueden discrepar.
        val secciones = listOf(bizcocho, crema, decoracion)
        val usados = listOf(bizcocho.id, crema.id, general)

        titulosDisponibles(secciones, usados).forEach { titulo ->
            assertNull(
                "Se ofreció $titulo pero se rechazaría",
                errorAlUsarTitulo(titulo, usados, "cualquiera")
            )
        }
    }

    // --- La sección automática, que es invisible ---

    @Test
    fun `una receta recien creada solo ofrece el General`() {
        // Toda receta nace con una sección llamada "General" que no se muestra hasta que la
        // renombran o llega una segunda (8.2). Ofrecerla dejaría DOS "General" en el menú de
        // cualquier receta nueva: la sección que nadie vio y el bloque sin sección.
        val reciénCreada = listOf(SeccionParaTitulo(1L, NOMBRE_SECCION_POR_DEFECTO))

        assertEquals(listOf(general), titulosDisponibles(reciénCreada, usadosPorOtrosBloques = emptyList()))
    }

    @Test
    fun `una seccion unica pero bautizada a mano si se ofrece`() {
        // Acá el nombre lo escribió alguien y el encabezado se muestra en la receta, así que
        // el paso sí puede decir que pertenece a esa parte.
        val salsa = SeccionParaTitulo(1L, "Salsa")

        assertEquals(listOf(salsa.id, general), titulosDisponibles(listOf(salsa), emptyList()))
    }

    @Test
    fun `al llegar la segunda seccion aparecen las dos`() {
        // Con dos secciones los encabezados se muestran aunque una siga llamándose "General",
        // y ahí el menú tiene que ofrecerlas: es la misma regla, no una excepción.
        val automatica = SeccionParaTitulo(1L, NOMBRE_SECCION_POR_DEFECTO)

        assertEquals(
            listOf(automatica.id, crema.id, general),
            titulosDisponibles(listOf(automatica, crema), emptyList())
        )
    }

    // --- Bloques que se juntan ---

    @Test
    fun `dos generales seguidos son el mismo bloque partido en dos`() {
        // Mostrarlos separados repite el encabezado sin que la separación signifique nada.
        assertTrue(seJuntanLosBloques(general, general))
    }

    @Test
    fun `dos generales separados por una seccion no se juntan`() {
        // Ahí la separación sí significa algo: uno va antes de la crema y el otro después.
        assertFalse(seJuntanLosBloques(general, crema.id))
        assertFalse(seJuntanLosBloques(crema.id, general))
    }

    @Test
    fun `dos secciones distintas nunca se juntan`() {
        assertFalse(seJuntanLosBloques(bizcocho.id, crema.id))
    }
}
