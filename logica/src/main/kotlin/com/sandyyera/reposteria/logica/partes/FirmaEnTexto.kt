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
 * El formato es de líneas, para que se pueda leer de un vistazo abriendo la base:
 *
 * ```
 * v1
 * S|Bizcocho|Harina=550|Azúcar=200
 * S|Crema|Crema de leche=300
 * T|Bizcocho=3
 * G|2
 * ```
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
 * persona, y nada le impide llamar a algo "Crema 50|50" o "Azúcar = flor". Sin escapar, un
 * nombre así partiría la línea en pedazos y la firma se leería mal para siempre, en silencio.
 */
fun textoDeFirma(firma: FirmaDeReceta): String {
    val lineas = mutableListOf(VERSION_DE_LA_FIRMA)

    firma.secciones.forEach { (seccion, ingredientes) ->
        val partes = ingredientes.map { (nombre, gramos) -> "${escapar(nombre)}=$gramos" }
        lineas += (listOf("S", escapar(seccion)) + partes).joinToString("|")
    }
    firma.pasosPorTitulo.forEach { (titulo, cuantos) ->
        lineas += "T|${escapar(titulo)}=$cuantos"
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

    val secciones = linkedMapOf<String, Map<String, Double>>()
    val pasosPorTitulo = linkedMapOf<String, Int>()
    var generales = 0

    lineas.drop(1).forEach { linea ->
        val campos = linea.split("|")
        when (campos.firstOrNull()) {
            "S" -> {
                val nombre = campos.getOrNull(1)?.let(::desescapar) ?: return null
                val ingredientes = linkedMapOf<String, Double>()
                campos.drop(2).forEach { campo ->
                    val (clave, valor) = partirEnDos(campo) ?: return null
                    ingredientes[desescapar(clave)] = valor.toDoubleOrNull() ?: return null
                }
                secciones[nombre] = ingredientes
            }
            "T" -> {
                val (clave, valor) = campos.getOrNull(1)?.let(::partirEnDos) ?: return null
                pasosPorTitulo[desescapar(clave)] = valor.toIntOrNull() ?: return null
            }
            "G" -> generales = campos.getOrNull(1)?.toIntOrNull() ?: return null
            else -> return null
        }
    }

    return FirmaDeReceta(secciones, pasosPorTitulo, generales)
}

/** Parte `"Harina=550"` por el **último** `=`, que es el separador real: el nombre va escapado. */
private fun partirEnDos(campo: String): Pair<String, String>? {
    val corte = campo.lastIndexOf('=')
    if (corte <= 0) return null
    return campo.take(corte) to campo.substring(corte + 1)
}

// El orden importa: la barra invertida se escapa primero, o se volvería a escapar a sí misma.
private fun escapar(texto: String): String = texto
    .replace("\\", "\\\\")
    .replace("|", "\\p")
    .replace("=", "\\e")
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
            'e' -> salida.append('=')
            'n' -> salida.append('\n')
            else -> salida.append(texto[i + 1])
        }
        i += 2
    }
    return salida.toString()
}
