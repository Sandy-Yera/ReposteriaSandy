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
import com.sandyyera.reposteria.data.db.dao.VentaDao
import com.sandyyera.reposteria.data.db.entidades.Empleado
import com.sandyyera.reposteria.data.db.entidades.EmpleadoRecetaSueldo
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultiple
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultipleDetalle
import com.sandyyera.reposteria.data.db.entidades.ArticuloDeAlmacen
import com.sandyyera.reposteria.data.db.entidades.EventoCambio
import com.sandyyera.reposteria.data.db.entidades.Ingrediente
import com.sandyyera.reposteria.data.db.entidades.Molde
import com.sandyyera.reposteria.data.db.entidades.MovimientoDeAlmacen
import com.sandyyera.reposteria.data.db.entidades.Receta
import com.sandyyera.reposteria.data.db.entidades.RecetaDuracion
import com.sandyyera.reposteria.data.db.entidades.RecetaIngrediente
import com.sandyyera.reposteria.data.db.entidades.RecetaPaso
import com.sandyyera.reposteria.data.db.entidades.RecetaPrecio
import com.sandyyera.reposteria.data.db.entidades.RecetaRendimiento
import com.sandyyera.reposteria.data.db.entidades.RecetaSeccion
import com.sandyyera.reposteria.data.db.entidades.RecetaSimulacionVenta
import com.sandyyera.reposteria.data.db.entidades.Venta
import com.sandyyera.reposteria.data.db.entidades.VentaLinea

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
        ArticuloDeAlmacen::class,
        Venta::class,
        VentaLinea::class,
        MovimientoDeAlmacen::class
    ],
    version = 10,
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
    abstract fun ventaDao(): VentaDao

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
                    MIGRACION_6_7, MIGRACION_7_8, MIGRACION_8_9, MIGRACION_9_10
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
         * 8 → 9: cuál de los precios de un trozo es **el de todos los días**.
         *
         * Hasta acá la base se deducía de `cantidad = 1`, y por eso solo podía haber una por
         * modo: un segundo precio de un trozo habría sido indistinguible del primero y el
         * repositorio lo rechazaba. Sandy lo reportó queriendo tantear —*"si yo quisiera testear
         * el valor de un trozo, no se me permite"*—: para comparar dos precios había que pisar
         * el que ya estaba, y entonces el anterior se perdía.
         *
         * La columna es lo que permite que convivan. `DEFAULT 0` porque SQLite lo exige para
         * una columna `NOT NULL`, y tiene que calzar con el `@ColumnInfo(defaultValue = "0")`
         * de la entidad.
         *
         * **El `UPDATE` no es opcional**: sin él ninguna receta guardada tendría base, y de la
         * base sale el precio de los trozos que sobran cuando una promoción no divide exacto.
         * Se marca `MIN(id)` de cada `(receta, modo)` con `cantidad = 1`, que es exactamente la
         * fila que `precioBasePorTrozo` venía eligiendo hasta esta versión — el primero que
         * encontraba. O sea que nada cambia de valor al migrar: cambia de dónde se sabe.
         */
        val MIGRACION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE receta_precios ADD COLUMN esBase INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    UPDATE receta_precios
                    SET esBase = 1
                    WHERE id IN (
                        SELECT MIN(id) FROM receta_precios
                        WHERE cantidad = 1
                        GROUP BY recetaId, modo
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * 9 → 10: las ventas, los movimientos del almacén (Fase 12, sección 18) y la nota del
         * molde (9.6).
         *
         * **Todo en una sola versión, y eso es deliberado.** Ventas y sus líneas no sirven sin los
         * movimientos —de ellos sale el costo *real*, que es la mitad del informe— y subir dos
         * versiones entre dos compilaciones deja a la del medio sin esquema exportado **para
         * siempre** (5.5.2). Ya pasó dos veces, con la 6 y la 8.
         *
         * **La nota del molde estuvo un rato en una versión 11 y se plegó acá.** No fue un cambio
         * de opinión sino de información: mientras no se supiera si el celular ya había corrido la
         * 9 → 10, tocar esta migración era peligroso —una tabla sin la columna que Room cree que
         * existe deja la app sin abrir— y una versión aparte era la única opción segura. Al
         * confirmarse que la 10 nunca se compiló, plegarla dejó de tener riesgo y salvó su
         * esquema. La regla que deja: **antes de partir una migración en dos, preguntar si la
         * primera ya corrió**; la respuesta cambia cuál de las dos opciones es la correcta.
         *
         * **No toca ni una fila de lo que ya existe.** Son tablas nuevas y nada más: una base con
         * cinco años de recetas queda exactamente igual, y lo único que aparece es la sección
         * Ventas vacía, que es lo correcto — no hubo ventas antes de poder anotarlas.
         *
         * Los `SET_NULL` de las claves foráneas son la decisión que hay que leer dos veces:
         * borrar una receta **no borra la historia de lo que se vendió**, y borrar un ingrediente
         * no borra la de lo que se gastó. La venta ocurrió; que la receta ya no exista no la
         * deshace. Por eso el título y el nombre viajan copiados en sus tablas, que es la única
         * copia de un nombre que esta app acepta.
         *
         * Los índices salen de las consultas que el informe va a hacer: por `fecha` para agrupar
         * el día, y por las tres claves foráneas —que Room exige indexar igual, y que acá además
         * se usan de verdad al armar el informe de una venta.
         */
        val MIGRACION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ventas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fecha INTEGER NOT NULL,
                        notas TEXT,
                        descontoDelAlmacen INTEGER NOT NULL DEFAULT 0,
                        creadoEn INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ventas_fecha ON ventas(fecha)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS venta_lineas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ventaId INTEGER NOT NULL,
                        recetaId INTEGER,
                        tituloReceta TEXT NOT NULL,
                        unidades INTEGER NOT NULL,
                        precioUnitario REAL NOT NULL,
                        costoEstimadoUnitario REAL NOT NULL,
                        precioEstimadoUnitario REAL NOT NULL,
                        FOREIGN KEY(ventaId) REFERENCES ventas(id) ON DELETE CASCADE,
                        FOREIGN KEY(recetaId) REFERENCES recetas(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_venta_lineas_ventaId ON venta_lineas(ventaId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_venta_lineas_recetaId ON venta_lineas(recetaId)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS movimientos_almacen (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ingredienteId INTEGER,
                        nombre TEXT NOT NULL,
                        cantidad REAL NOT NULL,
                        valorUnitario REAL NOT NULL,
                        motivo TEXT NOT NULL,
                        ventaId INTEGER,
                        fecha INTEGER NOT NULL,
                        creadoEn INTEGER NOT NULL,
                        FOREIGN KEY(ingredienteId) REFERENCES ingredientes(id) ON DELETE SET NULL,
                        FOREIGN KEY(ventaId) REFERENCES ventas(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_movimientos_almacen_ingredienteId " +
                        "ON movimientos_almacen(ingredienteId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_movimientos_almacen_ventaId " +
                        "ON movimientos_almacen(ventaId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_movimientos_almacen_fecha " +
                        "ON movimientos_almacen(fecha)"
                )

                // La nota del molde (9.6). `TEXT` nullable **sin `DEFAULT`**, porque acá el `null`
                // significa algo —"este molde no tiene nada anotado"— y no es lo mismo que una
                // nota vacía; es el mismo criterio de `almacen.detalles` en la 7 → 8.
                db.execSQL("ALTER TABLE moldes ADD COLUMN notas TEXT")
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
