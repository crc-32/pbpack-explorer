package gl

import com.imgui.Vec2

data class PDCPaintVert(val x: Float, val y: Float) {
    companion object {
        const val SIZE_BYTES = (2*Float.SIZE_BYTES)
    }
    fun toBytes(): ByteArray {
        val arr = ByteArray(SIZE_BYTES)
        arr.setFloatAt(0, x)
        arr.setFloatAt(Float.SIZE_BYTES, y)
        return arr
    }
}