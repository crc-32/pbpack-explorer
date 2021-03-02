package gl

import io.ktor.utils.io.*
import kotlin.native.concurrent.ensureNeverFrozen

@ThreadLocal
object Textures {
    val all: MutableMap<Int, Texture> = mutableMapOf()

    /**
     * @return the texture index
     */
    fun allocate(tex: Texture): Int {
        all[tex.hashCode()] = tex
        return tex.hashCode()
    }

    operator fun get(index: Int): Texture? {
        return all[index]
    }

    operator fun set(index: Int, value: Texture) {
        all[index] = value
    }

    fun destroy(index: Int) {
        all[index]?.destroy()
        all.remove(index)
    }

    fun destroyAll() {
        all.entries.forEach {
            it.value.destroy()
        }
        all.clear()
    }

    fun copyAndClear(): Map<Int, Texture> {
        val copy = all.toMap()
        all.clear()
        return copy
    }
}