package gl

import com.imgui.ImTextureID
import com.imgui.Vec2
import com.imgui.impl.ImGuiOpenGL3
import com.kgl.opengl.*
import copengl.GLuintVar
import copengl.glDrawPixels
import kotlinx.cinterop.*

class Texture(private var width: Int, private val height: Int, private val format: UInt, private val filter: UInt = GL_NEAREST, private val wrap: UInt = GL_CLAMP_TO_EDGE) {
    private var internalId: UInt? = null
    private var imGuiTexture: ImTextureID? = null

    init {
        internalId = glGenTexture()
        assert(internalId != null && internalId!! != 0u) {return@assert "Texture is NULL"}
        glBindTexture(GL_TEXTURE_2D, internalId!!)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter.toInt())
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter.toInt())
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap.toInt())
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap.toInt())

        memScoped {
            val ptr: COpaquePointerVar = alloc()
            ptr.value = internalId!!.toLong().toCPointer()
            imGuiTexture = ImTextureID(ptr.value!!)
        }
    }

    fun destroy() {
        if (internalId != null) glDeleteTexture(internalId!!)
        internalId = null
        imGuiTexture = null
    }

    fun setImage(data: UByteArray) {
        data.usePinned {
            glBindTexture(GL_TEXTURE_2D, internalId!!)
            glTexImage2D(GL_TEXTURE_2D, 0, format.toInt(), width, height, 0, format, GL_UNSIGNED_BYTE, it.addressOf(0))
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter.toInt())
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter.toInt())
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap.toInt())
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap.toInt())
        }
    }


    fun getID(): UInt {
        return internalId!!
    }

    fun getImTextureID(): ImTextureID {
        return imGuiTexture!!
    }

    fun getDimensions(): Vec2 = Vec2(width.toFloat(), height.toFloat())
}