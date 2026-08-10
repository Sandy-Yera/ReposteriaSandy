package com.sandyyera.reposteria.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 *
 * **El orden en que se corre importa, y equivocarse cuesta una corrida entera:**
 *
 *     ./gradlew :app:assembleDebug          # Room escribe app/schemas/N.json al compilar
 *     ./gradlew :app:connectedAndroidTest   # recién ahora existe para empaquetarlo
 *     ./gradlew :app:installDebug           # porque lo anterior DESINSTALÓ la app
 *
 * **Esa tercera línea no es opcional.** `connectedAndroidTest` instala la app, corre las
 * pruebas y **la desinstala** — es lo que hace Gradle siempre, no un fallo. Y con la app se va
 * su base de datos: las recetas reales del celular. De ahí que el respaldo
 * (`herramientas/respaldo_bd.sh bajar`) vaya **antes** de correr esto, y que devolverlo con
 * `subir <carpeta>` sea parte del procedimiento y no un plan de emergencia. Pasó de verdad:
 * la app desapareció del celular después de una corrida y hubo que restaurar.
 *
 * Al revés falla con `Cannot find the schema file in the assets folder`, que suena a que el
 * archivo se perdió y en realidad significa que todavía no se generó: los assets del APK de
 * pruebas se juntan **antes** de que KSP escriba el esquema nuevo, así que en la misma
 * invocación no llega. Pasó de verdad al subir a la versión 5.
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
     * 3 → 4: los moldes aprenden **cómo se cortan** (9.4).
     *
     * Lo que más importa comprobar acá no es que las columnas existan sino **que no toquen
     * nada**: el corte solo dice de qué tamaño queda cada trozo, y si llegara a rozar el área
     * o el volumen cambiaría el reescalado, o sea las cantidades de todas las recetas
     * enlazadas. Por eso se revisa que las medidas del molde queden idénticas.
     *
     * Las columnas van en **las dos tablas**, porque `DimensionesMolde` se embebe suelta en
     * `moldes` y con prefijo `molde_` en `receta_rendimiento`. Olvidar la segunda deja la app
     * sin arrancar, y eso solo se ve acá.
     *
     * **Sin `DEFAULT`, a diferencia de las dos anteriores**: son columnas nulables y ahí
     * `null` significa algo — "el corte que corresponda a la forma" — que para el rectángulo,
     * el cuadrado y el círculo ya es la respuesta correcta sin que nadie los edite.
     */
    @Test
    fun la_migracion_3_a_4_agrega_el_corte_sin_tocar_las_medidas() {
        ayudante.createDatabase(nombreDeLaBase, 3).use { base ->
            base.execSQL(
                "INSERT INTO moldes (id, nombre, tipoForma, largoCm, anchoCm, alturaMoldeCm, " +
                    "creadoEn, actualizadoEn) " +
                    "VALUES (1, 'Rectangular', 'RECTANGULO', 30.0, 20.0, 6.0, 1000, 1000)"
            )
            base.execSQL(
                "INSERT INTO recetas (id, titulo, pasoPrevio, creadoEn, actualizadoEn) " +
                    "VALUES (1, 'Torta de manjar', 'No necesita', 1000, 1000)"
            )
            base.execSQL(
                "INSERT INTO receta_rendimiento " +
                    "(recetaId, usaMolde, moldeOrigenId, pesoFinalG, trozos, " +
                    "molde_tipoForma, molde_largoCm, molde_anchoCm, molde_alturaMoldeCm, " +
                    "pesoReescaladoSinRevisar) " +
                    "VALUES (1, 1, 1, 1200.0, 8, 'RECTANGULO', 30.0, 20.0, 6.0, 0)"
            )
        }

        val migrada = ayudante.runMigrationsAndValidate(
            nombreDeLaBase, 4, true, AppDatabase.MIGRACION_3_4
        )

        migrada
            .query(
                "SELECT largoCm, anchoCm, alturaMoldeCm, formaDelCorte, largoDeCorteCm " +
                    "FROM moldes WHERE id = 1"
            )
            .use { fila ->
                assertTrue("El molde que ya estaba sigue ahí", fila.moveToFirst())
                assertEquals("Y sus medidas intactas", 30.0, fila.getDouble(0), 0.001)
                assertEquals(20.0, fila.getDouble(1), 0.001)
                assertEquals(6.0, fila.getDouble(2), 0.001)
                assertTrue("Sin corte anotado: se deduce de la forma", fila.isNull(3))
                assertTrue(fila.isNull(4))
            }

        migrada
            .query(
                "SELECT molde_largoCm, molde_anchoCm, molde_formaDelCorte " +
                    "FROM receta_rendimiento WHERE recetaId = 1"
            )
            .use { fila ->
                assertTrue("Y la receta enlazada también", fila.moveToFirst())
                assertEquals(30.0, fila.getDouble(0), 0.001)
                assertEquals(20.0, fila.getDouble(1), 0.001)
                assertTrue(fila.isNull(2))
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
    /**
     * La 4 → 5 conserva lo escrito y deja las columnas nuevas donde Room las espera (8.8, 8.11).
     *
     * **Es la migración más delicada hasta ahora** y por eso tiene prueba propia: agrega dos
     * columnas **con clave foránea**, que SQLite solo acepta por `ALTER TABLE` si su valor por
     * defecto es `NULL`, y dos índices que hay que crear a mano — `ALTER TABLE` no los crea, y
     * Room compara el esquema entero al abrir, índices y claves foráneas incluidos. Si algo de
     * eso no calza, `runMigrationsAndValidate` falla acá en vez de cerrar la app en el celular.
     *
     * Se comprueban **las dos mitades**: que el esquema valide (lo hace `runMigrationsAndValidate`
     * con `validateDroppedTables = true`) y que las filas sigan ahí. Validar el esquema solo
     * dejaría pasar un `DROP TABLE` seguido de un `CREATE TABLE`, que aprueba y borra todo.
     */
    @Test
    fun migracion_4_a_5_conserva_los_pasos_y_las_secciones() {
        ayudante.createDatabase(nombreDeLaBase, 4).use { base ->
            base.execSQL(
                "INSERT INTO recetas (id, titulo, pasoPrevio, creadoEn, actualizadoEn) " +
                    "VALUES (1, 'Torta de manjar', 'No necesita', 1000, 1000)"
            )
            base.execSQL(
                "INSERT INTO receta_secciones (id, recetaId, nombreSeccion, orden) " +
                    "VALUES (10, 1, 'Bizcocho', 0)"
            )
            base.execSQL(
                "INSERT INTO receta_pasos (id, recetaId, orden, contenido) " +
                    "VALUES (100, 1, 0, 'Batir las claras a punto de nieve.')"
            )
        }

        ayudante.runMigrationsAndValidate(
            nombreDeLaBase, 5, true, AppDatabase.MIGRACION_4_5
        ).use { base ->
            // El texto del paso es lo que más importa: lo escribió alguien.
            base.query(
                "SELECT contenido, tituloSeccionId, esGeneralAnidado FROM receta_pasos WHERE id = 100"
            ).use { fila ->
                assertTrue(fila.moveToFirst())
                assertEquals("Batir las claras a punto de nieve.", fila.getString(0))
                // Los pasos que ya existían quedan en el General, que es lo correcto: nadie
                // eligió un título para ellos porque no se podía.
                assertTrue(fila.isNull(1))
                assertEquals(0, fila.getInt(2))
            }
            // Y la sección conserva su nombre, sin origen porque no se copió de ninguna parte.
            base.query(
                "SELECT nombreSeccion, recetaOrigenId, firmaDelOrigen FROM receta_secciones WHERE id = 10"
            ).use { fila ->
                assertTrue(fila.moveToFirst())
                assertEquals("Bizcocho", fila.getString(0))
                assertTrue(fila.isNull(1))
                assertTrue(fila.isNull(2))
            }
        }
    }

    /**
     * De la 5 a la 7 de un tirón: el reparto del corte (9.4.3) y la tabla del almacén (14).
     *
     * **Va encadenada y no paso por paso, y eso no fue una elección de diseño sino la única
     * salida.** `MigrationTestHelper` valida contra el `N.json` de la versión de destino, y
     * **el 6.json no existe ni va a existir**: Room exporta solo el esquema de la versión
     * *actual* al compilar, y acá la base subió de 5 a 6 y de 6 a 7 entre dos compilaciones.
     * La versión 6 nunca llegó a compilarse sola, así que su esquema no se escribió nunca.
     *
     * **La lección, que vale más que esta prueba:** no subir dos versiones de la base entre dos
     * compilaciones. El intermedio queda sin esquema propio para siempre, y con él se pierde la
     * posibilidad de probar ese salto por separado — que es justamente lo que dice cuál de las
     * dos migraciones rompió algo cuando algo se rompe.
     *
     * Lo que sí se comprueba sigue siendo lo importante y es lo mismo de siempre: que **lo que
     * ya estaba guardado siga estando**. Validar el esquema solo dejaría pasar un `DROP TABLE`
     * seguido de un `CREATE TABLE`, que aprueba y borra todo.
     */
    @Test
    fun migracion_5_a_7_conserva_el_rendimiento_y_crea_el_almacen() {
        ayudante.createDatabase(nombreDeLaBase, 5).use { base ->
            base.execSQL(
                "INSERT INTO recetas (id, titulo, pasoPrevio, creadoEn, actualizadoEn) " +
                    "VALUES (1, 'Torta de manjar', 'No necesita', 1000, 1000)"
            )
            base.execSQL(
                "INSERT INTO receta_rendimiento " +
                    "(recetaId, usaMolde, moldeOrigenId, pesoFinalG, trozos, " +
                    "molde_tipoForma, molde_largoCm, molde_anchoCm, molde_alturaMoldeCm, " +
                    "pesoReescaladoSinRevisar) " +
                    "VALUES (1, 1, NULL, 1200.0, 6, 'RECTANGULO', 26.0, 25.0, 10.0, 0)"
            )
        }

        ayudante.runMigrationsAndValidate(
            nombreDeLaBase, 7, true, AppDatabase.MIGRACION_5_6, AppDatabase.MIGRACION_6_7
        ).use { base ->
            base.query(
                "SELECT trozos, molde_largoCm, molde_anchoCm, trozosALoLargo " +
                    "FROM receta_rendimiento WHERE recetaId = 1"
            ).use { fila ->
                assertTrue(fila.moveToFirst())
                assertEquals(6, fila.getInt(0))
                assertEquals("Las medidas del molde no se tocan", 26.0, fila.getDouble(1), 0.001)
                assertEquals(25.0, fila.getDouble(2), 0.001)
                assertTrue("Nadie eligió reparto todavía", fila.isNull(3))
            }
            // El almacén nace vacío, que es lo correcto: la tabla es nueva y no hay nada que
            // rellenar. Lo que se comprueba es que **exista y se pueda consultar** — si el
            // `CREATE TABLE` no calzara letra por letra con el que genera Room, la validación
            // de arriba ya habría fallado.
            base.query("SELECT COUNT(*) FROM almacen").use { fila ->
                assertTrue(fila.moveToFirst())
                assertEquals(0, fila.getInt(0))
            }
        }
    }

    /**
     * De la 7 a la 8: el almacén se conecta con los ingredientes (14.5).
     *
     * Lo que se comprueba no es que las columnas existan —de eso ya se encarga la validación de
     * esquema— sino **que los artículos sueltos que había no se queden huérfanos**. Hasta la 7 un
     * artículo suelto vivía solo en `almacen`, sin ingrediente; desde la 8 todo lo del almacén
     * tiene el suyo, y una fila sin él no sabría ni su unidad ni su precio.
     *
     * Se siembra también un ingrediente enlazado, para comprobar lo contrario: que la migración
     * **no** le invente un ingrediente nuevo ni le pise el que ya tenía.
     */
    @Test
    fun migracion_7_a_8_le_da_ingrediente_a_los_articulos_sueltos() {
        ayudante.createDatabase(nombreDeLaBase, 7).use { base ->
            base.execSQL(
                "INSERT INTO ingredientes (id, nombre, valorPorGramo, creadoEn, actualizadoEn) " +
                    "VALUES (1, 'Harina', 1.2, 1000, 1000)"
            )
            base.execSQL(
                "INSERT INTO almacen (id, ingredienteId, nombre, cantidad, actualizadoEn) " +
                    "VALUES (1, 1, '', 2500.0, 1000)"
            )
            base.execSQL(
                "INSERT INTO almacen (id, ingredienteId, nombre, cantidad, actualizadoEn) " +
                    "VALUES (2, NULL, 'Cajas de torta', 12.0, 1000)"
            )
        }

        ayudante.runMigrationsAndValidate(
            nombreDeLaBase, 8, true, AppDatabase.MIGRACION_7_8
        ).use { base ->
            base.query(
                "SELECT a.ingredienteId, i.nombre, i.esObjeto, i.vaEnRecetas, a.cantidad " +
                    "FROM almacen a JOIN ingredientes i ON i.id = a.ingredienteId " +
                    "WHERE a.id = 2"
            ).use { fila ->
                assertTrue("La caja quedó enlazada a un ingrediente", fila.moveToFirst())
                assertEquals("Cajas de torta", fila.getString(1))
                assertEquals("Un artículo suelto se contaba por unidad", 1, fila.getInt(2))
                assertEquals("Y no iba en ninguna receta", 0, fila.getInt(3))
                assertEquals("La cantidad guardada no se toca", 12.0, fila.getDouble(4), 0.001)
            }
            // Lo enlazado se queda como estaba: ni ingrediente nuevo ni marcas cambiadas.
            base.query(
                "SELECT ingredienteId FROM almacen WHERE id = 1"
            ).use { fila ->
                assertTrue(fila.moveToFirst())
                assertEquals(1, fila.getInt(0))
            }
            base.query("SELECT COUNT(*) FROM ingredientes").use { fila ->
                assertTrue(fila.moveToFirst())
                assertEquals("Solo se agregó el de la caja", 2, fila.getInt(0))
            }
            base.query(
                "SELECT esObjeto, vaEnRecetas FROM ingredientes WHERE id = 1"
            ).use { fila ->
                assertTrue(fila.moveToFirst())
                assertEquals("La harina se sigue midiendo en gramos", 0, fila.getInt(0))
                assertEquals("Y sigue yendo en recetas", 1, fila.getInt(1))
            }
        }
    }

    @Test
    fun migrar_8_a_9_marca_el_precio_base_de_cada_modo() {
        // **Arranca en 7 y no en 8**, aunque lo que se prueba sea 8 → 9: el esquema `8.json`
        // nunca llegó al repositorio —Room exporta solo el de la versión actual— y sin él
        // `createDatabase(..., 8)` no tiene contra qué construir. Encadenar desde 7 da la misma
        // base, y `receta_precios` no la toca la 7 → 8, así que las filas se pueden sembrar acá.
        ayudante.createDatabase(nombreDeLaBase, 7).use { base ->
            base.execSQL(
                "INSERT INTO recetas (id, titulo, pasoPrevio, creadoEn, actualizadoEn) " +
                    "VALUES (1, 'Torta', 'No necesita', 1000, 1000)"
            )
            // Dos de un trozo (el más antiguo tiene que ganar), uno del producto y una promo.
            base.execSQL(
                "INSERT INTO receta_precios (id, recetaId, modo, cantidad, precioTotal) " +
                    "VALUES (10, 1, 'TROZO', 1, 500.0)"
            )
            base.execSQL(
                "INSERT INTO receta_precios (id, recetaId, modo, cantidad, precioTotal) " +
                    "VALUES (11, 1, 'TROZO', 1, 700.0)"
            )
            base.execSQL(
                "INSERT INTO receta_precios (id, recetaId, modo, cantidad, precioTotal) " +
                    "VALUES (12, 1, 'PRODUCTO', 1, 3000.0)"
            )
            base.execSQL(
                "INSERT INTO receta_precios (id, recetaId, modo, cantidad, precioTotal) " +
                    "VALUES (13, 1, 'TROZO', 2, 900.0)"
            )
        }

        ayudante.runMigrationsAndValidate(
            nombreDeLaBase, 9, true, AppDatabase.MIGRACION_7_8, AppDatabase.MIGRACION_8_9
        ).use { base ->
            base.query("SELECT id FROM receta_precios WHERE esBase = 1 ORDER BY id").use { fila ->
                assertTrue(fila.moveToFirst())
                assertEquals("El trozo más antiguo, que es el que la app ya usaba", 10, fila.getInt(0))
                assertTrue(fila.moveToNext())
                assertEquals("Y el del producto entero", 12, fila.getInt(0))
                assertFalse("Nadie más", fila.moveToNext())
            }
        }
    }

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
                nombreDeLaBase, 9, true,
                AppDatabase.MIGRACION_1_2, AppDatabase.MIGRACION_2_3, AppDatabase.MIGRACION_3_4,
                AppDatabase.MIGRACION_4_5, AppDatabase.MIGRACION_5_6, AppDatabase.MIGRACION_6_7,
                AppDatabase.MIGRACION_7_8, AppDatabase.MIGRACION_8_9
            )
            .close()

        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val base = Room.databaseBuilder(contexto, AppDatabase::class.java, nombreDeLaBase)
            .addMigrations(
                AppDatabase.MIGRACION_1_2, AppDatabase.MIGRACION_2_3, AppDatabase.MIGRACION_3_4,
                AppDatabase.MIGRACION_4_5, AppDatabase.MIGRACION_5_6, AppDatabase.MIGRACION_6_7,
                AppDatabase.MIGRACION_7_8, AppDatabase.MIGRACION_8_9
            )
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
