package com.sandyyera.reposteria.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprueba que actualizar la app no se lleve por delante los datos que ya están guardados.
 *
 * Esta es **la única prueba del proyecto que no se puede correr sin un celular o emulador**,
 * y la razón es que no hay forma honesta de probar una migración de otra manera: lo que se
 * quiere verificar es SQLite de verdad ejecutando el `ALTER TABLE` de verdad sobre una base
 * escrita con el esquema viejo de verdad. Una base de mentira en memoria —la que usan las
 * otras 90 pruebas— no tiene esquemas ni migraciones, así que aprobaría cualquier cosa.
 *
 * Se corre con el celular conectado:
 *
 *     ./gradlew :app:connectedAndroidTest
 *
 * **Por qué importa:** si una migración está rota, la app se cierra al abrirse y la única
 * salida en el celular es desinstalar, que borra todo. Esto lo detecta antes, en el
 * computador, donde lo que se pierde es una prueba en rojo.
 *
 * `MigrationTestHelper` lee los esquemas de `app/schemas/`, que por eso van versionados y
 * se declaran como assets de esta carpeta en `build.gradle.kts`. **Cada versión nueva de
 * la base necesita su archivo ahí y su prueba acá.**
 */
@RunWith(AndroidJUnit4::class)
class MigracionTest {

    /** Nombre aparte: no se toca la base real del celular. */
    private val nombreDeLaBase = "prueba-migracion.db"

    @get:Rule
    val ayudante = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    /**
     * El caso que motivó la migración: una receta con dos precios guardados en la versión 1,
     * cuando `esReferencia` todavía no existía.
     *
     * Se comprueban las dos mitades, porque una sola no alcanza:
     * 1. Que la migración **corra sin romper el esquema** — eso lo hace
     *    `runMigrationsAndValidate`, que compara la base resultante contra `2.json` columna
     *    por columna. Si el `DEFAULT 0` de la migración no calzara con el
     *    `@ColumnInfo(defaultValue = "0")` de la entidad, acá se caería (y en el celular la
     *    app no arrancaría).
     * 2. Que **los datos sigan ahí**. Validar el esquema no dice nada sobre las filas: un
     *    `DROP TABLE` seguido de un `CREATE TABLE` pasaría la validación con la base vacía.
     */
    @Test
    fun la_migracion_1_a_2_conserva_los_precios_que_ya_estaban() {
        ayudante.createDatabase(nombreDeLaBase, 1).use { base ->
            base.execSQL(
                "INSERT INTO recetas (id, titulo, pasoPrevio, creadoEn, actualizadoEn) " +
                    "VALUES (1, 'Torta de manjar', 'No necesita', 1000, 1000)"
            )
            base.execSQL(
                "INSERT INTO receta_precios " +
                    "(id, recetaId, modo, cantidad, precioTotal, etiqueta) " +
                    "VALUES (1, 1, 'TROZO', 1, 2500.0, NULL)"
            )
            base.execSQL(
                "INSERT INTO receta_precios " +
                    "(id, recetaId, modo, cantidad, precioTotal, etiqueta) " +
                    "VALUES (2, 1, 'TROZO', 3, 6000.0, 'Promo 3 trozos')"
            )
        }

        val migrada = ayudante.runMigrationsAndValidate(
            nombreDeLaBase, 2, true, AppDatabase.MIGRACION_1_2
        )

        migrada
            .query("SELECT id, precioTotal, etiqueta, esReferencia FROM receta_precios ORDER BY id")
            .use { fila ->
                assertTrue("La migración se llevó las filas que ya estaban", fila.moveToFirst())
                assertEquals(2, fila.count)

                assertEquals(1L, fila.getLong(0))
                assertEquals(2500.0, fila.getDouble(1), 0.001)
                assertTrue("La etiqueta nula debe seguir nula", fila.isNull(2))
                assertEquals("Ninguna fila vieja queda marcada como referencia", 0, fila.getInt(3))

                fila.moveToNext()
                assertEquals(2L, fila.getLong(0))
                assertEquals(6000.0, fila.getDouble(1), 0.001)
                assertEquals("Promo 3 trozos", fila.getString(2))
                assertEquals(0, fila.getInt(3))
            }
    }

    /**
     * 2 → 3: el peso final de una receta pasa a poder estar "reescalado sin comprobar".
     *
     * Mismas dos mitades que la anterior: que el esquema quede como dice `3.json` —lo que
     * atrapa un `DEFAULT` que no calce con el `@ColumnInfo` de la entidad— y que la fila que
     * ya estaba siga ahí con su peso intacto.
     *
     * Lo que se comprueba además es **el valor con que quedan las filas viejas**: un peso
     * escrito a mano antes de esta versión no salió de ninguna multiplicación, así que
     * marcarlo como "por comprobar" sería pedirle a Sandy que revise pesos que ella misma
     * pesó. Por eso el `DEFAULT 0` y por eso se verifica.
     */
    @Test
    fun la_migracion_2_a_3_conserva_el_peso_y_no_marca_nada_por_comprobar() {
        ayudante.createDatabase(nombreDeLaBase, 2).use { base ->
            base.execSQL(
                "INSERT INTO recetas (id, titulo, pasoPrevio, creadoEn, actualizadoEn) " +
                    "VALUES (1, 'Torta de manjar', 'No necesita', 1000, 1000)"
            )
            base.execSQL(
                "INSERT INTO receta_rendimiento " +
                    "(recetaId, usaMolde, moldeOrigenId, pesoFinalG, trozos) " +
                    "VALUES (1, 0, NULL, 1200.0, 8)"
            )
        }

        val migrada = ayudante.runMigrationsAndValidate(
            nombreDeLaBase, 3, true, AppDatabase.MIGRACION_2_3
        )

        migrada
            .query(
                "SELECT pesoFinalG, trozos, pesoReescaladoSinRevisar " +
                    "FROM receta_rendimiento WHERE recetaId = 1"
            )
            .use { fila ->
                assertTrue("La migración se llevó la fila que ya estaba", fila.moveToFirst())
                assertEquals(1200.0, fila.getDouble(0), 0.001)
                assertEquals(8, fila.getInt(1))
                assertEquals(
                    "Un peso escrito a mano no salió de ninguna multiplicación",
                    0,
                    fila.getInt(2)
                )
            }
    }

    /**
     * Abrir la base con la app entera después de migrar.
     *
     * `runMigrationsAndValidate` compara esquemas, pero es Room quien al abrir revisa el
     * `identityHash` guardado y decide si la base coincide con el código. Esa comprobación
     * es la que falla en el celular con "Room cannot verify the data integrity", y solo se
     * ve construyendo la base de verdad.
     */
    @Test
    fun despues_de_migrar_la_app_puede_abrir_la_base() {
        ayudante.createDatabase(nombreDeLaBase, 1).use { base ->
            base.execSQL(
                "INSERT INTO recetas (id, titulo, pasoPrevio, creadoEn, actualizadoEn) " +
                    "VALUES (1, 'Torta de manjar', 'No necesita', 1000, 1000)"
            )
        }
        // Encadenadas hasta la versión actual: es el camino que recorre de verdad un
        // celular que venía con la primera instalación, y el que se rompe si una migración
        // nueva no calza con la anterior.
        ayudante
            .runMigrationsAndValidate(
                nombreDeLaBase, 3, true, AppDatabase.MIGRACION_1_2, AppDatabase.MIGRACION_2_3
            )
            .close()

        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val base = Room.databaseBuilder(contexto, AppDatabase::class.java, nombreDeLaBase)
            .addMigrations(AppDatabase.MIGRACION_1_2, AppDatabase.MIGRACION_2_3)
            .build()

        try {
            base.openHelper.readableDatabase.query("SELECT titulo FROM recetas").use { fila ->
                assertTrue(fila.moveToFirst())
                assertEquals("Torta de manjar", fila.getString(0))
            }
        } finally {
            // `RoomDatabase` no es `Closeable`, así que no sirve `use`.
            base.close()
        }
    }
}
