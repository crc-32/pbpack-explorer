package ui.extensions

import cimgui.internal.igCalcTextSize
import com.imgui.ImGui
import com.imgui.ImVec2
import com.imgui.Vec2
import kotlinx.cinterop.memScoped

fun ImGui.calcTextSize(text: String): Vec2 = memScoped {
    val pout = ImVec2()
    igCalcTextSize(pout.ptr, text, null, false, 0f)
    return Vec2(pout.x, pout.y)
}