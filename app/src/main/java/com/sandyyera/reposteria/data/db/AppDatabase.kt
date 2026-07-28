package com.sandyyera.reposteria.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sandyyera.reposteria.data.db.dao.EmpleadoDao
import com.sandyyera.reposteria.data.db.dao.HistorialDao
import com.sandyyera.reposteria.data.db.dao.IngredienteDao
import com.sandyyera.reposteria.data.db.dao.MoldeDao
import com.sandyyera.reposteria.data.db.dao.RecetaDao
import com.sandyyera.reposteria.data.db.entidades.Empleado
import com.sandyyera.reposteria.data.db.entidades.EmpleadoRecetaSueldo
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultiple
import com.sandyyera.reposteria.data.db.entidades.EmpleadoSimulacionMultipleDetalle
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
        EventoCambio::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Convertidores::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ingredienteDao(): IngredienteDao
    abstract fun recetaDao(): RecetaDao
    abstract fun moldeDao(): MoldeDao
    abstract fun empleadoDao(): EmpleadoDao
    abstract fun historialDao(): HistorialDao

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
                .build()

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
