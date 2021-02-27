package formats.pdc.extensions

import com.soywiz.korma.geom.IPoint
import com.soywiz.korma.geom.Point
import kotlin.math.round

fun Point.toBytes(precise: Boolean): ByteArray {
    val scale = if (precise) 8f else 1f
    val arr = ByteArray(2*Short.SIZE_BYTES)
    arr.setShortAt(0, round(x*scale).toInt().toShort())
    arr.setShortAt(Short.SIZE_BYTES, round(y*scale).toInt().toShort())
    return arr
}

fun Point.Companion.fromBytes(bytes: ByteArray, precise: Boolean): Point {
    val scale = if (precise) 8f else 1f
    return Point(bytes.getShortAt(0)/scale, bytes.getShortAt(Short.SIZE_BYTES)/scale)
}

fun List<IPoint>.toDoubleArray(): DoubleArray {
    val doubles = DoubleArray(this.size*2)
    var i = 0
    this.forEach {
        doubles[i++] = it.x
        doubles[i++] = it.y
    }
    return doubles
}