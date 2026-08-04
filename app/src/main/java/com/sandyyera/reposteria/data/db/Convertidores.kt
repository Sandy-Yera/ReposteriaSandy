package com.sandyyera.reposteria.data.db

import androidx.room.TypeConverter
import com.sandyyera.reposteria.data.db.entidades.EntidadEvento
import com.sandyyera.reposteria.logica.duracion.TipoDuracion
import com.sandyyera.reposteria.data.db.entidades.TipoEvento
import com.sandyyera.reposteria.logica.duracion.UnidadDuracion
import com.sandyyera.reposteria.logica.moldes.FormaDelCorte
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde
import com.sandyyera.reposteria.logica.precios.ModoPrecio

/**
 * Le enseña a Room a guardar los enums, que por sí solo no sabe manejar.
 *
 * Se guardan por **nombre** y no por posición: si algún día se agrega un valor en medio
 * de un enum, las posiciones ya guardadas cambiarían de significado en silencio y los
 * datos quedarían mal sin que nada avise. El nombre no corre ese riesgo.
 */
class Convertidores {

    @TypeConverter fun formaATexto(v: TipoFormaMolde?): String? = v?.name
    @TypeConverter fun corteATexto(v: FormaDelCorte?): String? = v?.name
    @TypeConverter fun textoACorte(v: String?): FormaDelCorte? = v?.let { FormaDelCorte.valueOf(it) }
    @TypeConverter fun textoAForma(v: String?): TipoFormaMolde? = v?.let { TipoFormaMolde.valueOf(it) }

    @TypeConverter fun modoPrecioATexto(v: ModoPrecio?): String? = v?.name
    @TypeConverter fun textoAModoPrecio(v: String?): ModoPrecio? = v?.let { ModoPrecio.valueOf(it) }

    @TypeConverter fun tipoEventoATexto(v: TipoEvento?): String? = v?.name
    @TypeConverter fun textoATipoEvento(v: String?): TipoEvento? = v?.let { TipoEvento.valueOf(it) }

    @TypeConverter fun entidadEventoATexto(v: EntidadEvento?): String? = v?.name
    @TypeConverter fun textoAEntidadEvento(v: String?): EntidadEvento? = v?.let { EntidadEvento.valueOf(it) }

    @TypeConverter fun tipoDuracionATexto(v: TipoDuracion?): String? = v?.name
    @TypeConverter fun textoATipoDuracion(v: String?): TipoDuracion? = v?.let { TipoDuracion.valueOf(it) }

    @TypeConverter fun unidadDuracionATexto(v: UnidadDuracion?): String? = v?.name
    @TypeConverter fun textoAUnidadDuracion(v: String?): UnidadDuracion? = v?.let { UnidadDuracion.valueOf(it) }
}
