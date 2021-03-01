package gl

import com.kgl.opengl.GL_REPEAT
import com.kgl.opengl.GL_RGBA

object Checkerboard: Texture(2, 2, GL_RGBA, wrap = GL_REPEAT) {
    private val cbData = ubyteArrayOf(0xa0u, 0xa0u, 0xa0u, 0xffu, 0xf0u, 0xf0u, 0xf0u, 0xffu,
            0xf0u, 0xf0u, 0xf0u, 0xffu, 0xa0u, 0xa0u, 0xa0u, 0xffu)
    init {
        setImage(cbData.toUByteArray())
    }
}