package util.extensions

import com.imgui.Vec4
import kotlinx.cinterop.*
import platform.posix.memset

fun ByteArray.isPNG(): Boolean {
    return copyOfRange(0, 8) contentEquals byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}

fun ByteArray.isPDCI(): Boolean {
    return copyOfRange(0, 4) contentEquals "PDCI".encodeToByteArray()
}

fun ByteArray.isPDCS(): Boolean {
    return copyOfRange(0, 4) contentEquals "PDCS".encodeToByteArray()
}

fun UByte.toHostColor(): Vec4 {
    val r = (this.toInt() ushr 4 and 3) / 3
    val g = (this.toInt() ushr 2 and 3) / 3
    val b = (this.toInt() ushr 0 and 3) / 3
    val a = (this.toInt() ushr 6 and 3) / 3
    return Vec4(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat())
}