package formats

import copengl.GL_ARB_tessellation_shader
import copengl.Time
import formats.pdc.Frame
import gl.Texture
import util.extensions.isPDCI
import util.extensions.isPDCS
import kotlin.system.getTimeMillis
import kotlin.time.TimeSource

data class PDCData(
        val type: Type,
        val viewBox: ShortArray,
        var frames: List<Frame>,
        var playCount: UShort
) {
    var cFrame = 0
    var lastFrameTime = -1L

    enum class Type(val value: ByteArray) {
        Image("PDCI".encodeToByteArray()),
        Sequence("PDCS".encodeToByteArray());

        companion object {
            fun fromValue(value: ByteArray) = values().first { it.value.contentEquals(value) }
        }
    }

    companion object {
        fun fromBytes(raw: ByteArray): PDCData {
            require(raw.isPDCI() || raw.isPDCS())

            var n = 0
            val type = Type.fromValue(raw.copyOfRange(0, 4))
            n += 4

            val fileSize = raw.getUIntAt(n)
            n += UInt.SIZE_BYTES

            val version = raw.getUByteAt(n)
            n += UByte.SIZE_BYTES

            n += 1 // reserved

            val viewBox = ShortArray(2)

            viewBox[0] = raw.getShortAt(n)
            n += Short.SIZE_BYTES
            viewBox[1] = raw.getShortAt(n)
            n += Short.SIZE_BYTES

            val frames: List<Frame>
            val playCount: UShort
            when (type) {
                Type.Image -> {
                    frames = listOf(Frame.fromBytes(raw.sliceArray(n until raw.size), true))
                    playCount = 0u
                }
                Type.Sequence -> {
                    playCount = raw.getUShortAt(n)
                    n += UShort.SIZE_BYTES

                    val frameCount = raw.getUShortAt(n)
                    n += UShort.SIZE_BYTES
                    assert(frameCount > 0u)
                    frames = buildList {
                        for (i in 0 until frameCount.toInt()) {
                            val frame = Frame.fromBytes(raw.sliceArray(n until raw.size))
                            add(frame)
                            n += frame.toBytes().size
                        }
                    }
                }
            }

            return PDCData(type, viewBox, frames, playCount)
        }
    }

    fun getFrame(): Frame {
        if (cFrame > frames.lastIndex) cFrame = 0


        return frames[cFrame++]
    }

    fun getLastFrame(): Frame {
        return frames[(cFrame-1).coerceAtLeast(0)]
    }

    init {
        require(viewBox.size == 2)
    }

}