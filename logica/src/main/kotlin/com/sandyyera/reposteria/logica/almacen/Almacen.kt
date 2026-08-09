package com.sandyyera.reposteria.logica.almacen

import com.sandyyera.reposteria.logica.formato.redondearParaGuardar

/**
 * Lo que queda después de usar algo del almacén (14.7).
 *
 * Es la calculadora que pidió Sandy para no tener que restar de cabeza: se escribe **cuánto se
 * usó** y la app dice cuánto queda. La otra forma —bajar el número a mano— sigue existiendo y no
 * se reemplaza: sirve cuando uno mira el frasco y estima, en vez de haber medido lo que sacó.
 *
 * **No baja de cero.** Usar más de lo que había anotado no significa que quede una cantidad
 * negativa: significa que lo anotado estaba mal, y lo que queda de verdad es nada. Un stock
 * negativo no existe en un estante, y dejarlo pasar convertiría el valor del almacén en un
 * número que resta.
 *
 * Devuelve redondeado con el mismo criterio que todo lo que se guarda, para que lo que se ve
 * antes de confirmar sea exactamente lo que queda escrito.
 */
fun loQueQueda(habia: Double, seUso: Double): Double =
    redondearParaGuardar((habia - seUso).coerceAtLeast(0.0))

/**
 * Si al restar se usó más de lo que había anotado.
 *
 * Existe para poder **decirlo** y no solo para recortar en silencio: que la cuenta no cierre es
 * un dato — o se anotó mal antes, o se usó de otro paquete. Callarlo dejaría el stock en cero
 * sin ninguna explicación de por qué el número no coincide con lo que se escribió.
 */
fun seUsoDeMas(habia: Double, seUso: Double): Boolean = seUso > habia
