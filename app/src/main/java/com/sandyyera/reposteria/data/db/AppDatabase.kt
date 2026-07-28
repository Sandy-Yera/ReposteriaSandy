package com.sandyyera.reposteria.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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

        // Room ya activa las claves foráneas y el modo WAL por su cuenta, así que las
        // cascadas de las entidades funcionan sin configurar nada extra acá.
        private fun construir(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NOMBRE_ARCHIVO
            ).build()
    }
}
