package com.sandyyera.reposteria.logica.validaciones

import com.sandyyera.reposteria.logica.moldes.DimensionesMolde
import com.sandyyera.reposteria.logica.moldes.TipoFormaMolde

/**
 * Las reglas de un molde: qué medidas hacen falta según su forma y cuáles sirven.
 *
 * Mismo formato que el resto del paquete: devuelven el motivo o `null`, sin lanzar
 * excepción, porque describen datos que la persona todavía está escribiendo. Quien sí
 * lanza es `DimensionesMolde.areaCm2`, la última red: si un molde llega ahí sin sus
 * medidas es que nunca pasó por acá.
 *
 * Ver las secciones 6.2 y 9.1 de arquitectura.md.
 */

/**
 * Una medida que puede pedir el formulario de un molde.
 *
 * Existe para que **la pantalla y la validación no puedan discrepar** sobre qué campos
 * hacen falta. Si el formulario decidiera por su cuenta qué dibujar según la forma, y la
 * validación decidiera por la suya qué exigir, alcanzaría con agregar una forma nueva y
 * tocar solo uno de los dos para tener un campo obligatorio que nadie puede llenar, o uno
 * visible que a nadie le importa. Las dos preguntan a [camposDe].
 *
 * La [etiqueta] va acá y no en la pantalla por lo mismo: "Altura" a secas es justo el
 * texto que confunde las dos alturas del triángulo.
 */
enum class CampoDeMolde(val etiqueta: String) {
    LARGO("Largo (cm)"),
    ANCHO("Ancho (cm)"),
    LADO("Lado (cm)"),
    DIAMETRO("Diámetro (cm)"),
    BASE_TRIANGULO("Base del triángulo (cm)"),
    ALTURA_TRIANGULO("Altura del triángulo (cm)"),
    VOLUMEN_EXOTICO("Volumen medido con agua (cm³)"),
    ALTURA_MOLDE("Alto del molde (cm)")
}

/**
 * Qué medidas hay que pedir para una forma, en el orden en que conviene preguntarlas.
 *
 * [CampoDeMolde.ALTURA_MOLDE] aparece en las cinco, **incluida la exótica**. En un molde
 * exótico el volumen ya se midió con agua y la altura no hace falta para calcularlo, pero
 * sin ella no se puede despejar el área (`área = volumen / altura`) y el Modo Altura del
 * reescalado se queda sin la única cifra que compara. Pedirla ahora cuando el molde está
 * en la mano cuesta un campo; pedirla después es ir a buscar el molde de nuevo.
 *
 * Va siempre al final: es la única que se mide en otra dirección, y separarla de las del
 * plano ayuda a no anotar el largo dos veces.
 */
fun camposDe(forma: TipoFormaMolde): List<CampoDeMolde> = when (forma) {
    TipoFormaMolde.RECTANGULO -> listOf(CampoDeMolde.LARGO, CampoDeMolde.ANCHO)
    TipoFormaMolde.CUADRADO -> listOf(CampoDeMolde.LADO)
    TipoFormaMolde.CIRCULO -> listOf(CampoDeMolde.DIAMETRO)
    TipoFormaMolde.TRIANGULO -> listOf(CampoDeMolde.BASE_TRIANGULO, CampoDeMolde.ALTURA_TRIANGULO)
    TipoFormaMolde.EXOTICO -> listOf(CampoDeMolde.VOLUMEN_EXOTICO)
} + CampoDeMolde.ALTURA_MOLDE

/**
 * Revisa una medida de molde tal como está escrita en el campo.
 *
 * El cero se rechaza igual que un campo vacío, y no por prolijidad: una medida en 0 deja
 * el área o el volumen en 0, y ahí `factorEscala` divide por cero al reescalar. Ese error
 * aparecería mucho después, en otra pantalla, sin ninguna pista de que venía de acá.
 */
fun errorEnMedidaDeMoldeTexto(texto: String): String? =
    errorEnNumeroPositivoTexto(texto, "Escribe la medida")

/**
 * Los problemas de un molde: el nombre, la forma y una entrada por cada medida con error.
 *
 * Las medidas van en un mapa y no en un campo por cada una porque **cuáles existen depende
 * de la forma**: un `data class` con las ocho tendría siempre seis en `null` sin que eso
 * signifique "está bien", sino "acá no se pregunta". El mapa solo lleva las que fallaron,
 * así la pantalla pinta `errores.medidas[campo]` bajo cada campo que dibujó y no tiene que
 * saber cuál corresponde a cuál forma.
 */
data class ErroresMolde(
    val nombre: String? = null,
    val forma: String? = null,
    val medidas: Map<CampoDeMolde, String> = emptyMap(),
    /** Lo que esté mal en las medidas **del corte**, que son opcionales (9.4). */
    val corte: String? = null
) {
    /** `true` cuando no queda nada por corregir y el molde se puede guardar. */
    val sirve: Boolean
        get() = nombre == null && forma == null && medidas.isEmpty() && corte == null
}

/**
 * Revisa las dos medidas **del corte**, que son opcionales y van juntas o no van (9.4).
 *
 * Son otra cosa que las medidas del molde y por eso se revisan aparte: aquellas deciden el
 * área y el volumen —o sea el reescalado— y son obligatorias; estas solo dicen de qué tamaño
 * queda cada trozo, y no anotarlas es una respuesta perfectamente válida. La app se limita a
 * no mostrar el tamaño del trozo.
 *
 * Lo único que se exige es que **estén las dos o ninguna**: con un solo lado no se puede
 * medir nada, y dejarlo pasar guardaría un dato a medias que después no sirve.
 */
fun errorEnMedidasDeCorte(largoTexto: String, anchoTexto: String): String? {
    val largoVacio = largoTexto.isBlank()
    val anchoVacio = anchoTexto.isBlank()
    if (largoVacio && anchoVacio) return null
    if (largoVacio || anchoVacio) return "Escribe los dos lados, o ninguno"
    return errorEnMedidaDeMoldeTexto(largoTexto) ?: errorEnMedidaDeMoldeTexto(anchoTexto)
}

/**
 * Revisa de una vez el formulario completo de un molde, mientras se escribe.
 *
 * Solo mira las medidas que [camposDe] pide para esa forma: lo que haya quedado escrito de
 * una forma elegida antes se ignora, no se arrastra como error. Sin forma elegida todavía
 * no hay medidas que revisar, y el único aviso es el de la forma.
 */
fun revisarMolde(
    nombre: String,
    forma: TipoFormaMolde?,
    medidas: Map<CampoDeMolde, String>,
    largoDeCorteTexto: String = "",
    anchoDeCorteTexto: String = ""
): ErroresMolde {
    // Las medidas del corte se revisan igual sin forma elegida: son independientes de ella.
    val corte = errorEnMedidasDeCorte(largoDeCorteTexto, anchoDeCorteTexto)
    if (forma == null) {
        return ErroresMolde(
            nombre = errorEnNombreEscrito(nombre),
            forma = "Elige la forma del molde",
            corte = corte
        )
    }
    val problemas = camposDe(forma).mapNotNull { campo ->
        errorEnMedidaDeMoldeTexto(medidas[campo].orEmpty())?.let { campo to it }
    }
    return ErroresMolde(
        nombre = errorEnNombreEscrito(nombre),
        medidas = problemas.toMap(),
        corte = corte
    )
}

/**
 * Arma las dimensiones a partir de lo escrito, o devuelve `null` si todavía no se puede.
 *
 * Es el equivalente de `calcularValorPorGramo` para este formulario: se llama en cada tecla
 * para ir mostrando el área y el volumen en vivo, y **no lanza excepción** — mientras se
 * escribe, "todavía no alcanza" es lo normal. El nombre no se mira acá: un molde sin
 * bautizar igual tiene medidas, y esperar a que lo bauticen para mostrar el volumen sería
 * esconder justo el número que dice si se midió bien.
 *
 * Los campos que esa forma no usa quedan en `null` **aunque haya algo escrito en ellos**:
 * si alguien probó "círculo", anotó el diámetro y después cambió a "cuadrado", ese diámetro
 * ya no significa nada y guardarlo dejaría un molde que se contradice a sí mismo.
 */
fun dimensionesDesde(
    forma: TipoFormaMolde?,
    medidas: Map<CampoDeMolde, String>
): DimensionesMolde? {
    if (forma == null) return null
    val valores = camposDe(forma).associateWith { campo ->
        val texto = medidas[campo].orEmpty()
        if (errorEnMedidaDeMoldeTexto(texto) != null) return null
        textoANumero(texto) ?: return null
    }
    return DimensionesMolde(
        tipoForma = forma,
        largoCm = valores[CampoDeMolde.LARGO],
        anchoCm = valores[CampoDeMolde.ANCHO],
        ladoCm = valores[CampoDeMolde.LADO],
        diametroCm = valores[CampoDeMolde.DIAMETRO],
        baseTrianguloCm = valores[CampoDeMolde.BASE_TRIANGULO],
        alturaTrianguloCm = valores[CampoDeMolde.ALTURA_TRIANGULO],
        volumenExoticoCm3 = valores[CampoDeMolde.VOLUMEN_EXOTICO],
        alturaMoldeCm = valores[CampoDeMolde.ALTURA_MOLDE]
    )
}
