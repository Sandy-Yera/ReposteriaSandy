package com.sandyyera.reposteria

import android.content.Context
import com.sandyyera.reposteria.data.db.AppDatabase
import com.sandyyera.reposteria.data.repositorio.HistorialRepositorio
import com.sandyyera.reposteria.data.repositorio.IngredienteRepositorio
import com.sandyyera.reposteria.data.repositorio.MoldeRepositorio
import com.sandyyera.reposteria.data.repositorio.RecetaRepositorio

/**
 * Arma y guarda las piezas compartidas de la app: la base de datos y los repositorios.
 *
 * Hace el trabajo de una librería de inyección de dependencias, pero escrito a mano.
 * Para un proyecto de una sola persona alcanza y tiene la ventaja de que se entiende
 * leyéndolo: acá está, en un solo lugar, qué depende de qué. Si el proyecto crece se
 * puede migrar a Hilt sin rehacer las pantallas.
 *
 * Todo es `by lazy`: nada se crea hasta que alguien lo pide, así abrir la app no cuesta
 * más de lo necesario.
 */
class AppContainer(context: Context) {

    private val base: AppDatabase = AppDatabase.obtener(context)

    val historial: HistorialRepositorio by lazy {
        HistorialRepositorio(base.historialDao())
    }

    val ingredientes: IngredienteRepositorio by lazy {
        IngredienteRepositorio(
            dao = base.ingredienteDao(),
            recetaDao = base.recetaDao(),
            historial = historial
        )
    }

    val recetas: RecetaRepositorio by lazy {
        RecetaRepositorio(dao = base.recetaDao(), historial = historial)
    }

    /**
     * Depende de [recetas] y no del DAO de recetas: corregir un molde escribe en las recetas
     * enlazadas, y quién puede escribir en el rendimiento de una receta es cosa de ese
     * repositorio. Acá se ve de un vistazo que moldes viene después de recetas.
     */
    val moldes: MoldeRepositorio by lazy {
        MoldeRepositorio(dao = base.moldeDao(), recetas = recetas, historial = historial)
    }

    // El repositorio de empleados se agrega al llegar su fase.
}
