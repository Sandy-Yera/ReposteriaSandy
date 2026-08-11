package com.sandyyera.reposteria.logica.almacen

import com.sandyyera.reposteria.logica.formato.formatearNumero
import com.sandyyera.reposteria.logica.formato.redondearParaGuardar

/**
 * Hacia dónde va un movimiento del almacén (14.8).
 *
 * **Existe porque hasta ahora solo se podía restar**, y Sandy lo dijo tal cual: *"solo pensé en
 * restar… pero qué hay de sumar? Debería poder agregar como quitar ingredientes con claridad.
 * Hoy no tengo opción para agregar, solo para quitar"*. La calculadora resolvía "usé 300 g" y
 * "compré un kilo más" no tenía dónde escribirse — había que hacer la suma de cabeza y anotar el
 * total, que es exactamente el trabajo que la pantalla existe para ahorrar.
 *
 * Es un enum y no un signo en el número por lo mismo que los dos campos del cuadro de edición: un
 * "500" que cambia de significado según dónde esté escrito es un número que se guarda al revés
 * el día que alguien cambie de modo sin borrar. Acá el sentido se elige aparte y el número
 * siempre es cuánto, en positivo.
 */
enum class SentidoDelMovimiento {
    /** Llegó más: una compra, una devolución, algo que apareció. */
    ENTRA,

    /** Se fue: se usó, se echó a perder, se regaló. */
    SALE
}

/**
 * Cuánto queda después de un movimiento. **Puede quedar negativo, y es a propósito.**
 *
 * Antes esta cuenta se recortaba en cero, con este argumento: *"un stock negativo no existe en un
 * estante"*. Sandy dio dos razones mejores para no recortarlo, y las dos son sobre lo que el
 * número **enseña**:
 *
 * 1. *"Puede que haya comprado más y por eso logré ocuparla"* — el negativo dice cuánto se compró
 *    sin anotar. Recortado a cero, esa compra desaparece y el almacén miente hacia arriba.
 * 2. *"El aviso me ayudaría a decir 'oh, quizá le eche menos a una y realmente esto debería estar
 *    en 0'"* — el negativo es la señal de que una receta pide más de lo que de verdad se usa. Esa
 *    es información sobre la receta, y recortándola se pierde.
 *
 * O sea que el cero no era el dato verdadero: era el dato cómodo. Lo que corresponde no es
 * esconder el negativo sino **mostrarlo con su aviso** (ver [avisoDeCantidadNegativa]).
 *
 * Devuelve redondeado con el mismo criterio que todo lo que se guarda, para que lo que se ve
 * antes de confirmar sea exactamente lo que queda escrito.
 */
fun resultadoDelMovimiento(habia: Double, cuanto: Double, sentido: SentidoDelMovimiento): Double =
    redondearParaGuardar(
        when (sentido) {
            SentidoDelMovimiento.ENTRA -> habia + cuanto
            SentidoDelMovimiento.SALE -> habia - cuanto
        }
    )

/**
 * Lo que queda después de usar algo. Es [resultadoDelMovimiento] en el sentido de siempre.
 *
 * Se conserva con su nombre porque es como se lee en la calculadora de "cuánto usé" (14.7), que
 * sigue siendo el caso de todos los días. **Ya no recorta en cero**: ver [resultadoDelMovimiento].
 */
fun loQueQueda(habia: Double, seUso: Double): Double =
    resultadoDelMovimiento(habia, seUso, SentidoDelMovimiento.SALE)

/**
 * El aviso de que una cantidad quedó bajo cero, o `null` si no quedó.
 *
 * **No impide guardar**, y ese es todo el punto: el negativo es el dato. Lo que hace el aviso es
 * ofrecer las dos lecturas posibles, porque solo Sandy sabe cuál es la de ese frasco — o entró
 * algo que no se anotó, o la receta pide de más. Decir solo "quedó en -200 g" deja el número sin
 * la pregunta que lo vuelve útil.
 *
 * [esObjeto] cambia la unidad de la frase, con la misma función que el resto de la app para que
 * no aparezca un "1 unidades" por acá.
 */
fun avisoDeCantidadNegativa(cantidad: Double, esObjeto: Boolean): String? {
    if (cantidad >= 0) return null
    val falta = formatearNumero(-cantidad)
    val unidad = if (esObjeto) "unidades" else "g"
    return "Quedó en $falta $unidad bajo cero. O entró algo que no se anotó, o la receta pide " +
        "más de lo que de verdad usas."
}

/**
 * Si al restar se usó más de lo que había anotado.
 *
 * Sigue existiendo porque **la pregunta no es la misma que la del negativo**: acá se compara
 * contra lo que había, y en un almacén que ya venía bajo cero usar 10 g más no es "se usó de
 * más", es seguir hundiendo un número que ya estaba hundido. Lo usa el cuadro de la calculadora
 * para su aviso propio; el del resultado lo da [avisoDeCantidadNegativa].
 */
fun seUsoDeMas(habia: Double, seUso: Double): Boolean = seUso > habia
