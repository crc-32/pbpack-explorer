package ui

import com.imgui.ImGui
import com.imgui.*
import com.kgl.glfw.Glfw
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import platform.posix.err
import platform.posix.getenv
import ui.extensions.calcTextSize

class PathDialog(id: String? = null, initial: String? = null, private val actionBtnName: String, private val cb: (String) -> String?): SubWindow {
    var path = initial?: getHome()
    override val id = actionBtnName + "##" + (id?: "PathDialog${this.hashCode().toString(16)}")

    val buf = ByteArray(1024, {idx -> path.getOrNull(idx)?.toByte()?: 0})

    private fun getHome(): String {
        return if (Platform.osFamily == OsFamily.WINDOWS) {
            getenv("HOMEPATH")?.toKString()?: "C:\\"
        } else {
            getenv("HOME")?.toKString()?: "/"
        }
    }

    private var errorString = ""

    fun performAction() {
        val res = cb(path)
        if (res.isNullOrEmpty()) {
            ImGui.closeCurrentPopup()
        }else {
            errorString = res
        }
    }

    override fun render(): Boolean = with(ImGui) {
        setNextWindowSize(Vec2(400f, 60f+(calcTextSize(errorString).y*2)), ImGuiCond.Always)
        setNextWindowPos(Vec2(
                ((Glfw.currentContext?.size?.first!!/2)-(400/2)).toFloat(),
                ((Glfw.currentContext?.size?.second!!/2)-(50/2)).toFloat(),
        ))
        if (beginPopupModal(id, flags = ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoMove)) {
            setNextItemWidth(getWindowWidth())
            if (inputTextWithHint("", "Path", buf)) {
                path = buf.toKString().replace("~", getHome())
            }
            if (isKeyPressed(getKeyIndex(ImGuiKey.Enter)) || isKeyPressed(getKeyIndex(ImGuiKey.KeyPadEnter))) {
                performAction()
            }
            if (button("Cancel")) {
                closeCurrentPopup()
            }
            sameLine()
            if (button(actionBtnName)) {
                performAction()
            }
            textWrapped(errorString)
            endPopup()
        }
        return true
    }
}