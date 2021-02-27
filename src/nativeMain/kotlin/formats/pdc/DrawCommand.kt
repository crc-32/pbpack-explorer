package formats.pdc

import com.soywiz.korma.geom.Point
import com.soywiz.korma.geom.shape.buildPath
import com.soywiz.korma.geom.shape.toShape2d
import com.soywiz.korma.geom.vector.VectorPath
import com.soywiz.korma.geom.vector.circle
import com.soywiz.korma.geom.vector.rect
import formats.pdc.extensions.fromBytes
import formats.pdc.extensions.toBytes
import io.ktor.utils.io.bits.*

data class DrawCommand(
        var type: Type,
        var hidden: Boolean,
        var strokeColor: UByte,
        var strokeWidth: UByte,
        var fillColor: UByte,
        var pathOpen: Boolean? = null,
        var radius: UShort? = null,
        var points: List<Point>
) {
    enum class Type(val value: UByte) {
        Invalid(0u),
        Path(1u),
        Circle(2u),
        PrecisePath(3u);

        companion object {
            fun fromValue(value: UByte) = values().firstOrNull { it.value == value }?: Invalid
        }
    }

    fun toBytes(): ByteArray {
        val bytes = ByteArray(HEAD_SIZE_BYTES)

        var n = 0
        bytes.setUByteAt(n, type.value)
        n += UByte.SIZE_BYTES

        bytes.setUByteAt(n, (if (hidden) FLAG_HIDDEN else 0u).toUByte())
        n += UByte.SIZE_BYTES

        bytes.setUByteAt(n, strokeColor)
        n += UByte.SIZE_BYTES

        bytes.setUByteAt(n, strokeWidth)
        n += UByte.SIZE_BYTES

        bytes.setUByteAt(n, fillColor)
        n += UByte.SIZE_BYTES

        if (radius != null) {
            bytes.setUShortAt(n, radius!!)
        }else if (pathOpen != null) {
            bytes.setUShortAt(n, (if (pathOpen!!) ATTR_PATH_OPEN else 0u).toUShort())
        }else {
            throw IllegalStateException("radius and pathOpen both null")
        }
        n += UShort.SIZE_BYTES
        bytes.setUShortAt(n, points.size.toUShort())
        n += UShort.SIZE_BYTES

        val pointsB = buildList<Byte> {
            points.forEach {
                addAll(it.toBytes(type == Type.PrecisePath).toList())
            }
        }

        return bytes + pointsB.toByteArray()
    }

    companion object {
        const val HEAD_SIZE_BYTES = (UByte.SIZE_BYTES*5)+(UShort.SIZE_BYTES*2)
        const val FLAG_HIDDEN = 0b1u
        const val ATTR_PATH_OPEN = 0b1u

        fun fromBytes(bytes: ByteArray): DrawCommand {
            var n = 0
            val type = Type.fromValue(bytes.getUByteAt(n))
            assert(type != Type.Invalid) { return@assert "Invalid type" }

            n += UByte.SIZE_BYTES

            val flags = bytes.getUByteAt(n)
            n += UByte.SIZE_BYTES

            val strokeColor = bytes.getUByteAt(n)
            n += UByte.SIZE_BYTES

            val strokeWidth = bytes.getUByteAt(n)
            n += UByte.SIZE_BYTES

            val fillColor = bytes.getUByteAt(n)
            n += UByte.SIZE_BYTES

            val attribute = bytes.getUShortAt(n)
            n += UShort.SIZE_BYTES

            val pointCount = bytes.getUShortAt(n)
            n += UShort.SIZE_BYTES
            val pathOpen = attribute and ATTR_PATH_OPEN.toUShort() > 0u

            val points = buildList<Point> {
                for (i in 0 until pointCount.toInt()) {
                    add(Point.fromBytes(bytes.sliceArray(n until n+(Short.SIZE_BYTES*2)), type == Type.PrecisePath))
                    n += Short.SIZE_BYTES*2
                }
            }

            val command = DrawCommand(type, flags and FLAG_HIDDEN.toUByte() > 0u, strokeColor, strokeWidth, fillColor, points = points)
            if (type == Type.Circle) {
                command.radius = attribute
            }else {
                command.pathOpen = pathOpen
            }
            return command
        }
    }

    fun getPath(): VectorPath {
        val pointsNRep: MutableList<Point> = mutableListOf()
        return VectorPath {
            if (type != Type.Circle) {
                points.forEachIndexed {i, point ->
                    if (!pointsNRep.contains(point)) {
                        pointsNRep.add(point)
                        if (i == 0)
                            moveTo(point.x, point.y)
                        else
                            lineTo(point.x, point.y)
                    }
                }
                if (!pathOpen!!) {
                    close()
                }
            } else {
                circle(points[0].x, points[0].y, radius!!.toDouble())
            }
        }
    }
}