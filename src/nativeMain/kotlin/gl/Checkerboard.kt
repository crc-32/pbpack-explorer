package gl

import com.kgl.opengl.GL_REPEAT
import com.kgl.opengl.GL_RGBA

@ThreadLocal
object Checkerboard: Texture(8, 8, GL_RGBA, wrap = GL_REPEAT) {
    private val cbArrN = ubyteArrayOf(0xa0u, 0xa0u, 0xa0u, 0xffu, 0xe0u, 0xe0u, 0xe0u, 0xffu)
    private val cbArrR = ubyteArrayOf(0xe0u, 0xe0u, 0xe0u, 0xffu, 0xa0u, 0xa0u, 0xa0u, 0xffu)
    private var cbArr = cbArrN
    private val cbData = buildList<Byte> {
        repeat(8*8) {
            if (it.toFloat() % super.getDimensions().x == 0f) {
                if (cbArr == cbArrN) {
                    cbArr = cbArrR
                }else {
                    cbArr = cbArrN
                }
            }
            addAll(cbArr.toByteArray().toList())
        }
    }.toByteArray()
    init {
        setImage(cbData.toUByteArray())
    }
}