package com.sandyyera.reposteria.logica.partes

/**
 * Cómo se guarda una [FirmaDeReceta] en la columna `firmaDelOrigen` (5.5.1).
 *
 * Va como **texto y no como columnas sueltas**, y la razón está escrita en la arquitectura:
 * la lista de lo que se guarda cambió apenas se decidió adaptar las cantidades en proporción,
 * y con una columna por dato eso habría sido una migración. Lo que se compara es un puñado de
 * números contra otro y **nunca se consulta por ellos**, así que no hace falta que la base los
 * entienda.
 *
 * El formato es de líneas y de campos fijos, para que se pueda leer de un vistazo abriendo la
 * base:
 *
 * ```
 * v1
 * S|12|Bizcocho
 * I|12|34|Harina|550.0
 * I|12|35|Azúcar|200.0
 * T|12|Bizcocho|3
 * G|2
 * ```
 *
 * `S` es una sección (id y nombre), `I` un ingrediente dentro de ella (sección, id de la
 * fila, nombre y gramos), `T` cuántos pasos van bajo un título y `G` cuántos pasos generales
 * hay. **Las claves son números y los nombres van escapados**, así que un nombre raro puede
 * ensuciar lo que se lee pero nunca puede partir un campo en dos.
 *
 * La `v1` de la primera línea es lo que permite cambiar el formato más adelante sin romper lo
 * ya guardado: una firma que no se entienda se descarta y la sección deja de avisar, que es
 * mucho mejor que reventar al abrir una receta.
 */
private const val VERSION_DE_LA_FIRMA = "v1"

/**
 * Convierte una firma a texto, escapando lo que podría partir el formato.
 *
 * El escape no es prolijidad: los nombres de sección y de ingrediente los escribe una
 * persona, y nada le impide llamar a algo "Crema 50|50". Sin escapar, un nombre así partiría
 * la línea en pedazos y la firma se leería mal para siempre, en silencio.
 */
fun textoDeFirma(firma: FirmaDeReceta): String {
    val lineas = mutableListOf(VERSION_DE_LA_FIRMA)

    firma.secciones.forEach { seccion ->
        lineas += "S|${seccion.seccionId}|${escapar(seccion.nombre)}"
        seccion.lineas.forEach { linea ->
            lineas += "I|${seccion.seccionId}|${linea.lineaId}|" +
                "${escapar(linea.nombre)}|${linea.gramos}"
        }
    }
    firma.titulos.forEach {
        lineas += "T|${it.seccionId}|${escapar(it.nombre)}|${it.cuantosPasos}"
    }
    lineas += "G|${firma.pasosGenerales}"

    return lineas.joinToString("\n")
}

/**
 * Lee una firma guardada, o devuelve `null` si el texto no se entiende.
 *
 * **Devuelve `null` en vez de lanzar** a propósito: una firma ilegible —de una versión vieja
 * del formato, o de una fila a medio escribir— no puede impedir abrir la receta. Lo que se
 * pierde es el aviso de "la original cambió", y quien la lea decide qué hacer con eso.
 */
fun firmaDesdeTexto(texto: String?): FirmaDeReceta? {
    val lineas = texto?.lines()?.filter { it.isNotBlank() } ?: return null
    if (lineas.firstOrNull() != VERSION_DE_LA_FIRMA) return null

    // Se arma en orden y con listas mutables porque los ingredientes llegan después de su
    // sección: un mapa por id evita tener que buscarla recorriendo.
    val orden = mutableListOf<Long>()
    val nombres = mutableMapOf<Long, String>()
    val contenido = mutableMapOf<Long, MutableList<LineaDeFirma>>()
    val titulos = mutableListOf<TituloDeFirma>()
    var generales = 0

    lineas.drop(1).forEach { linea ->
        val campos = linea.split("|")
        when (campos.firstOrNull()) {
            "S" -> {
                if (campos.size != 3) return null
                val id = campos[1].toLongOrNull() ?: return null
                orden += id
                nombres[id] = desescapar(campos[2])
                contenido[id] = mutableListOf()
            }
            "I" -> {
                if (campos.size != 5) return null
                val seccionId = campos[1].toLongOrNull() ?: return null
                // Un ingrediente cuya sección no vino antes es una firma rota, no una a la
                // que le falte un dato: descartarla entera es más honesto que inventar.
                val destino = contenido[seccionId] ?: return null
                destino += LineaDeFirma(
                    lineaId = campos[2].toLongOrNull() ?: return null,
                    nombre = desescapar(campos[3]),
                    gramos = campos[4].toDoubleOrNull() ?: return null
                )
            }
            "T" -> {
                if (campos.size != 4) return null
                titulos += TituloDeFirma(
                    seccionId = campos[1].toLongOrNull() ?: return null,
                    nombre = desescapar(campos[2]),
                    cuantosPasos = campos[3].toIntOrNull() ?: return null
                )
            }
            "G" -> {
                if (campos.size != 2) return null
                generales = campos[1].toIntOrNull() ?: return null
            }
            else -> return null
        }
    }

    return FirmaDeReceta(
        secciones = orden.map {
            SeccionDeFirma(it, nombres.getValue(it), contenido.getValue(it))
        },
        titulos = titulos,
        pasosGenerales = generales
    )
}

// El orden importa: la barra invertida se escapa primero, o se volvería a escapar a sí misma.
private fun escapar(texto: String): String = texto
    .replace("\\", "\\\\")
    .replace("|", "\\p")
    .replace("\n", "\\n")

private fun desescapar(texto: String): String {
    val salida = StringBuilder()
    var i = 0
    while (i < texto.length) {
        val actual = texto[i]
        if (actual != '\\' || i == texto.length - 1) {
            salida.append(actual)
            i++
            continue
        }
        when (texto[i + 1]) {
            '\\' -> salida.append('\\')
            'p' -> salida.append('|')
            'n' -> salida.append('\n')
            else -> salida.append(texto[i + 1])
        }
        i += 2
    }
    return salida.toString()
}
