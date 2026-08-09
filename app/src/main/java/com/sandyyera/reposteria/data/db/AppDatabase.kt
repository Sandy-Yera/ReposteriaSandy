package com.sandyyera.reposteria.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sandyyera.reposteria.data.db.dao.AlmacenDao
import com.sandyyera.reposteria.data.db.dao.EmpleadoDao
import com.sandyyera.reposteria.data.db.dao.HistorialDao
import com.sandyyera.reposteria.data.db.dao.IngredienteDao
import com.sandyyera.reposteria.data.db.dao.MoldeDao
import com.sandyyera.reposteria.data.db.dao.RecetaDao
import com.sandyyera.reposteria.data.db.entidades.Empleado
import com.sandyyera.reposteria.data.db.entidades.EmpleadoRecetaSueldo
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultiple
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultipleDetalle
import com.sandyyera.reposteria.data.db.entidades.ArticuloDeAlmacen
import com.sandyyera.reposteria.data.db.entidades.EventoCambio
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Molde
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.RecetaDuracion
import com.sandyyera.reposteria.data.db.entidades.RecetaIngrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaPaso
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.db.entidades.RecetaRendimiento
import com.sandyyera.reposteria.data.db.entidades.RecetaSeccion
import com.sandyyera.reposteria.data.db.entidades.RecetaSimulacionVenta

/**
 * La base de datos de la app.
 *
 * `exportSchema = true` deja en `app/schemas/` un archivo por cada versión del esquema.
 * Esos archivos se versionan en el repositorio: son el registro de las migraciones, y
 * perderlos arriesga los datos ya guardados en el celular.
 *
 * Al subir la versión hay que declarar la migración correspondiente. Room obliga a
 * hacerlo, que es exactamente la salvaguarda que se quiere una vez que hay datos reales.
 */
@Database(
    entities = [
        Ingrediente::class,
        Receta::class,
        RecetaSeccion::class,
        RecetaIngrediente::class,
        RecetaRendimiento::class,
        RecetaDuracion::class,
        RecetaPrecio::class,
        RecetaPaso::class,
        RecetaSimulacionVenta::class,
        Molde::class,
        Empleado::class,
        EmpleadoRecetaSueldo::class,
        EmpleadoSimulacionMultiple::class,
        EmpleadoSimulacionMultipleDetalle::class,
        EventoCambio::class,
        ArticuloDeAlmacen::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Convertidores::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ingredienteDao(): IngredienteDao
    abstract fun recetaDao(): RecetaDao
    abstract fun moldeDao(): MoldeDao
    abstract fun empleadoDao(): EmpleadoDao
    abstract fun historialDao(): HistorialDao
    abstract fun almacenDao(): AlmacenDao

    companion object {
        private const val NOMBRE_ARCHIVO = "reposteria.db"

        @Volatile
        private var instancia: AppDatabase? = null

        /**
         * Devuelve la base, creándola la primera vez.
         *
         * Se usa una sola instancia para toda la app: abrir varias sobre el mismo archivo
         * es una forma conocida de corromper datos.
         */
        fun obtener(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: construir(context).also { instancia = it }
            }

        /** Nombre del empleado estándar, el que siempre existe y no se puede borrar. */
        const val NOMBRE_EMPLEADO_GENERICO = "Empleado genérico"

        // Room ya activa las claves foráneas y el modo WAL por su cuenta, así que las
        // cascadas de las entidades funcionan sin configurar nada extra acá.
        //
        // Tampoco se llama a fallbackToDestructiveMigration(), y es a propósito: sin
        // migración declarada, subir la versión hace que la app falle al abrir en vez de
        // borrar la base en silencio. Es preferible una falla ruidosa en desarrollo a
        // perder recetas y costos reales del celular. Cada cambio de esquema tiene que
        // traer su propia migración.
        private fun construir(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NOMBRE_ARCHIVO
            )
                .addCallback(SembrarDatosIniciales)
                .addMigrations(
                    MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5, MIGRACION_5_6,
                    MIGRACION_6_7, MIGRACION_7_8
                )
                .build()

        /**
         * 1 → 2: cada precio puede ser el de referencia de su receta.
         *
         * Antes las cifras automáticas usaban siempre el precio de menor ganancia, sin
         * poder elegir. Ahora se elige uno, así que la tabla necesita saber cuál.
         *
         * Las filas que ya existían quedan en `0`, o sea sin referencia elegida, y eso es
         * exactamente lo que corresponde: mientras nadie elija, `precioDeReferencia` usa
         * el de menor ganancia y todo sigue dando lo mismo que antes de esta versión.
         *
         * El `DEFAULT 0` es obligatorio —SQLite no deja agregar una columna `NOT NULL`
         * sin valor por defecto— y tiene que calzar con el `@ColumnInfo(defaultValue = "0")`
         * de la entidad. Si no calzan, Room compara los dos esquemas al abrir y la app no
         * arranca.
         */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE receta_precios " +
                        "ADD COLUMN esReferencia INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 2 → 3: el peso final puede venir de un reescalado y estar sin comprobar.
         *
         * Al cambiar de molde, el peso del producto se multiplica por el mismo factor que
         * los ingredientes (8.4.1, #4). Eso es una estimación —el peso real depende de la
         * masa que quede pegada al molde y del agua que se evapore—, así que queda marcado
         * hasta que alguien mire el campo.
         *
         * Tenía que ser una columna: quien reescala hoy pesa el producto mañana, cuando
         * salga del horno, y para entonces la app ya se cerró. Un aviso en memoria se
         * pierde justo antes de servir.
         *
         * Las filas que ya existían quedan en `0`, que es lo correcto: sus pesos los
         * escribió alguien a mano, no salieron de ninguna multiplicación.
         *
         * Mismo cuidado que en la 1 → 2: el `DEFAULT 0` es obligatorio —SQLite no deja
         * agregar una columna `NOT NULL` sin él— y tiene que calzar con el
         * `@ColumnInfo(defaultValue = "0")` de la entidad, o la app no arranca.
         */
        val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE receta_rendimiento " +
                        "ADD COLUMN pesoReescaladoSinRevisar INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 3 → 4: cómo se corta un molde (9.4).
         *
         * Tres columnas por tabla, y van en **las dos** porque `DimensionesMolde` se embebe
         * dos veces: suelta en `moldes` y con el prefijo `molde_` en `receta_rendimiento`.
         * Olvidar la segunda dejaría la app sin arrancar, porque Room compara el esquema
         * entero al abrir.
         *
         * **Sin `DEFAULT`, a diferencia de las dos migraciones anteriores**, y no es un
         * descuido: estas columnas son nulables, y ahí `null` significa algo — "el corte que
         * corresponda a la forma", que para el rectángulo, el cuadrado y el círculo es la
         * respuesta correcta sin que nadie los edite. Las dos migraciones de antes agregaban
         * columnas `NOT NULL`, que en SQLite sí exigen un valor por defecto.
         *
         * **Ninguna de estas columnas entra en el área ni en el volumen.** El corte solo dice
         * de qué tamaño queda cada trozo; el reescalado no las mira.
         */
        val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE moldes ADD COLUMN formaDelCorte TEXT")
                db.execSQL("ALTER TABLE moldes ADD COLUMN largoDeCorteCm REAL")
                db.execSQL("ALTER TABLE moldes ADD COLUMN anchoDeCorteCm REAL")
                db.execSQL("ALTER TABLE receta_rendimiento ADD COLUMN molde_formaDelCorte TEXT")
                db.execSQL("ALTER TABLE receta_rendimiento ADD COLUMN molde_largoDeCorteCm REAL")
                db.execSQL("ALTER TABLE receta_rendimiento ADD COLUMN molde_anchoDeCorteCm REAL")
            }
        }

        /**
         * 4 → 5: los pasos saben bajo qué título van, y las secciones de dónde se copiaron
         * (8.8 y 8.11).
         *
         * **Es la versión 5 y no la 4**, aunque el plan de la Fase 9 dijera 4: la 4 se la llevó
         * el corte de los moldes, que llegó antes. Es justo el número que se copia mal.
         *
         * **Las dos columnas nuevas con `REFERENCES` son lo delicado de esta migración.** SQLite
         * deja agregar una columna con clave foránea por `ALTER TABLE` **solo si su valor por
         * defecto es `NULL`**, que es exactamente el caso de las dos: una sección propia no
         * viene de ninguna receta, y un paso sin título es el General. Si alguna fuera `NOT
         * NULL` habría que recrear la tabla entera.
         *
         * `esGeneralAnidado` sí es `NOT NULL` y por eso lleva `DEFAULT 0`, con el mismo cuidado
         * de siempre: tiene que calzar con el `@ColumnInfo(defaultValue = "0")` de la entidad o
         * Room no abre la base. Las dos claves foráneas van con `ON DELETE SET NULL`, también
         * igual que en las entidades — Room compara el esquema entero al abrir, incluidas las
         * claves foráneas y los índices, así que **los índices hay que crearlos acá a mano**:
         * `ALTER TABLE` no los crea solo y su ausencia también hace fallar la validación.
         *
         * Los índices llevan el nombre con que Room los genera (`index_<tabla>_<columna>`); con
         * otro nombre la comparación falla aunque el índice exista y cubra lo mismo.
         */
        val MIGRACION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE receta_secciones ADD COLUMN recetaOrigenId INTEGER " +
                        "REFERENCES recetas(id) ON DELETE SET NULL"
                )
                db.execSQL("ALTER TABLE receta_secciones ADD COLUMN firmaDelOrigen TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_receta_secciones_recetaOrigenId " +
                        "ON receta_secciones(recetaOrigenId)"
                )

                db.execSQL(
                    "ALTER TABLE receta_pasos ADD COLUMN tituloSeccionId INTEGER " +
                        "REFERENCES receta_secciones(id) ON DELETE SET NULL"
                )
                db.execSQL(
                    "ALTER TABLE receta_pasos ADD COLUMN esGeneralAnidado INTEGER " +
                        "NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_receta_pasos_tituloSeccionId " +
                        "ON receta_pasos(tituloSeccionId)"
                )
            }
        }

        /**
         * Agrega el reparto del corte en cuadrícula (9.4.3).
         *
         * Una sola columna nullable, que es el caso fácil de SQLite: no necesita `DEFAULT` —el
         * `null` **significa algo**, "no lo elegí", y ahí la app reparte lo más parejo que
         * puede—. Es la misma decisión que las tres columnas del corte en la migración 3 → 4.
         *
         * **Todas las filas que ya existen quedan en `null`, y eso es lo correcto**: nadie
         * eligió reparto antes de que se pudiera elegir. Lo que sí cambia para ellas es la
         * suposición —antes se partía siempre el lado largo en tantas tiras como trozos, ahora
         * se reparte parejo—, y ese cambio no necesita migración porque el tamaño del trozo
         * nunca se guardó: se calcula al mostrarlo.
         */
        val MIGRACION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE receta_rendimiento ADD COLUMN trozosALoLargo INTEGER")
            }
        }

        /**
         * Crea la tabla del almacén (sección 14).
         *
         * **Es la primera migración que agrega una tabla y no una columna**, y eso cambia el
         * cuidado que hay que tener: el `CREATE TABLE` tiene que quedar **letra por letra** como
         * lo generaría Room —tipos, `NOT NULL`, `DEFAULT`, la clave foránea y el índice único—
         * o `runMigrationsAndValidate` falla al comparar contra `7.json`. Room compara el
         * esquema entero, no "que exista una tabla parecida".
         *
         * El índice es único sobre `ingredienteId` y eso es una regla del negocio, no una
         * optimización: un ingrediente con dos filas de almacén haría que "cuánta harina queda"
         * tuviera dos respuestas. SQLite permite varios `NULL` ahí, así que los artículos
         * sueltos —que no tienen ingrediente— no se estorban entre sí.
         */
        val MIGRACION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `almacen` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ingredienteId` INTEGER, " +
                        "`nombre` TEXT NOT NULL, " +
                        "`cantidad` REAL NOT NULL, " +
                        "`actualizadoEn` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`ingredienteId`) REFERENCES `ingredientes`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_almacen_ingredienteId` " +
                        "ON `almacen` (`ingredienteId`)"
                )
            }
        }

        /**
         * Conecta el almacén con los ingredientes (14.5 y 14.6).
         *
         * Tres columnas y ninguna toca lo que ya estaba guardado:
         *
         * - `ingredientes.esObjeto` — si se cuenta por unidad. `DEFAULT 0` porque todo lo que
         *   ya existe se mide en gramos: es lo único que se podía cargar hasta ahora.
         * - `ingredientes.vaEnRecetas` — `DEFAULT 1`, por lo mismo al revés: todo lo que hay en
         *   el catálogo se cargó justamente para usarlo en recetas.
         * - `receta_ingredientes.unidades` — nullable **sin `DEFAULT`**, porque ahí el `null`
         *   significa algo: "esto se mide en gramos", que es el caso normal.
         * - `almacen.detalles` — nullable, mismo criterio.
         *
         * Los dos `DEFAULT` no son adorno: SQLite exige uno para agregar una columna `NOT NULL`,
         * y tienen que calzar con el `@ColumnInfo(defaultValue = ...)` de la entidad o Room se
         * niega a abrir la base en el celular. Es el mismo cuidado de las migraciones 1 → 2,
         * 2 → 3 y 4 → 5.
         *
         * **Y una parte que mueve datos y no solo columnas**: los artículos sueltos que había en
         * el almacén pasan a tener su ingrediente. Desde 14.5 anotar algo en el almacén lo crea
         * también en el catálogo, y una fila sin ingrediente ya no tiene forma de mostrarse
         * completa: no sabría su unidad ni su precio. Se crean como objeto (`esObjeto = 1`) y
         * fuera de las recetas (`vaEnRecetas = 0`), que es exactamente lo que un artículo suelto
         * era hasta esta versión — la caja, la cinta, la vela.
         *
         * Las tres condiciones de los `WHERE` no son paranoia: el índice de `almacen` es único
         * por `ingredienteId`, así que enlazar dos filas al mismo ingrediente reventaría la
         * migración entera y dejaría la app sin abrir. **Si alguna fila no se puede enlazar se
         * queda como está** —sin ingrediente— y la lista la sigue mostrando con el valor en
         * blanco: es preferible una fila a medias a una migración que falla o a un borrado.
         */
        val MIGRACION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ingredientes ADD COLUMN esObjeto INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ingredientes ADD COLUMN vaEnRecetas INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE receta_ingredientes ADD COLUMN unidades REAL")
                db.execSQL("ALTER TABLE almacen ADD COLUMN detalles TEXT")

                val ahora = System.currentTimeMillis()
                db.execSQL(
                    """
                    INSERT INTO ingredientes
                        (nombre, valorPorGramo, esObjeto, vaEnRecetas, creadoEn, actualizadoEn)
                    SELECT a.nombre, 0, 1, 0, $ahora, $ahora
                    FROM almacen a
                    WHERE a.ingredienteId IS NULL
                      AND TRIM(a.nombre) <> ''
                      AND NOT EXISTS (SELECT 1 FROM ingredientes i WHERE i.nombre = a.nombre)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE almacen
                    SET ingredienteId =
                            (SELECT i.id FROM ingredientes i WHERE i.nombre = almacen.nombre),
                        nombre = ''
                    WHERE ingredienteId IS NULL
                      AND EXISTS (SELECT 1 FROM ingredientes i WHERE i.nombre = almacen.nombre)
                      AND NOT EXISTS (
                          SELECT 1 FROM almacen otra
                          WHERE otra.ingredienteId =
                              (SELECT i.id FROM ingredientes i WHERE i.nombre = almacen.nombre)
                      )
                    """.trimIndent()
                )
            }
        }

        /**
         * Corre una sola vez, cuando la base se crea por primera vez.
         *
         * Siembra el empleado genérico, que el diseño da por existente siempre: es el
         * modelo estándar de reparto, va fijo al principio de la lista y no se puede
         * eliminar ni renombrar. Si no se creara acá, la sección Empleados arrancaría
         * vacía y esa garantía sería falsa.
         *
         * Se hace con SQL directo porque en este punto los DAO todavía no están
         * disponibles: la base se está construyendo.
         */
        private object SembrarDatosIniciales : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val ahora = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO empleados (nombre, esGenerico, creadoEn, actualizadoEn) " +
                        "VALUES (?, 1, ?, ?)",
                    arrayOf(NOMBRE_EMPLEADO_GENERICO, ahora, ahora)
                )
            }
        }
    }
}
