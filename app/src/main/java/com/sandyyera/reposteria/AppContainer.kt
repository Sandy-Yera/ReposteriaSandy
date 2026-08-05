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
class AppContainer(private val context: Context) {

    /**
     * La base, **también perezosa**, y eso cambia dónde se paga su construcción.
     *
     * Antes se armaba en el constructor, y como `MainActivity.onCreate` pide el contenedor
     * para pasárselo a la navegación, Room terminaba construyéndose **en el hilo principal
     * antes del primer cuadro**: cargar la clase generada, sus cinco DAO y los adaptadores de
     * quince entidades. Nada de eso se ve en el perfilador como "consulta lenta" porque no es
     * una consulta, es carga de clases, y en una app recién instalada todavía no está
     * compilada de antemano — que es justo cuando Sandy vio el pegón.
     *
     * Perezosa, `AppContainer(this)` no cuesta nada y el trabajo lo dispara [precalentar]
     * desde un hilo de fondo, en paralelo con el primer dibujado en vez de antes de él.
     */
    private val base: AppDatabase by lazy { AppDatabase.obtener(context) }

    /**
     * Abre la base **desde donde se llame**, para que no le toque al hilo principal.
     *
     * Se llama desde `ReposteriaApp.onCreate` en un hilo de fondo. No devuelve nada y no hay
     * que esperarla: si la pantalla llega antes, `by lazy` la hace esperar lo que falte, y si
     * llega después se encuentra todo listo. En el peor caso no gana nada; nunca empeora.
     *
     * Toca `recetas` y no `base` a secas porque hay que atravesar los dos perezosos, y de paso
     * ese es el repositorio que más pantallas usan.
     */
    fun precalentar() {
        recetas
    }

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
