package gl.extensions

import com.kgl.opengl.*
import gl.Texture
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

fun UByteArray.toRGBATexture(width: Int, height: Int): Texture = Texture(width, height, GL_RGBA).also {
    it.setImage(this)
}