package ui

import com.imgui.ImGui
import com.imgui.*
import com.kgl.glfw.Glfw
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import platform.posix.getenv

class OpenDialog(initial: String? = null, val cb: (String) -> Unit): SubWindow {
    var path = initial?: if (Platform.osFamily == OsFamily.WINDOWS) {
        getenv("HOMEPATH")?.toKString()?: "C:\\"
    } else {
        getenv("HOME")?.toKString()?: "/"
    }

    val buf = ByteArray(1024, {idx -> path.getOrNull(idx)?.toByte()?: 0})

    override fun render(): Boolean = with(ImGui) {
        setNextWindowSize(Vec2(400f, 60f), ImGuiCond.Always)
        setNextWindowPos(Vec2(
                ((Glfw.currentContext?.size?.first!!/2)-(400/2)).toFloat(),
                ((Glfw.currentContext?.size?.second!!/2)-(50/2)).toFloat(),
        ))
        if (beginPopupModal(ID, flags = ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoMove)) {
            setNextItemWidth(getWindowWidth())
            if (inputTextWithHint("", "Path", buf)) {
                path = buf.toKString()
            }
            if (button("Cancel")) {
                closeCurrentPopup()
            }
            sameLine()
            if (button("Open")) {
                cb(path)
                closeCurrentPopup()
            }
            endPopup()
        }
        return true
    }

    companion object {
        const val ID = "Open"
    }
}