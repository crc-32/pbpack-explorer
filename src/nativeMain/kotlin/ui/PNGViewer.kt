package ui

import com.imgui.*
import formats.PNGData
import gl.Checkerboard
import gl.Texture
import gl.Textures
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.posix.exp
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import ui.extensions.calcTextSize

class PNGViewer(name: String, private val index: Int, private val pngData: Map<Int, PNGData>, onOverwrite: (PNGData) -> Unit): SubWindow {
    var open = true
    var uv0 = Vec2(0f,0f)
    var uv1 = Vec2(1f,1f)
    var wheel = 0f
    override val id: String = "View PNG: $name"
    private val exportDialog = PathDialog(actionBtnName = "Export") {
        memScoped {
            val file = fopen(it, "w+") ?: return@PathDialog "Couldn't save here"
            try {
                pngData[index]!!.raw.usePinned {
                    fwrite(it.addressOf(0), it.get().size.toULong(), 1, file)
                }
            } finally {
                fclose(file)
            }
        }
        return@PathDialog null
    }
    override fun render(): Boolean = with(ImGui) {
        if (Textures[index] == null || pngData[index] == null) {
            return false
        }
        val texture = Textures[index]!!
        val png = pngData[index]!!

        val scale = texture.getDimensions()
        val targetMult = (400/scale.y).coerceAtMost(getWindowContentRegionWidth()/scale.x)
        val size = Vec2(scale.x * targetMult, scale.y * targetMult)

        setNextWindowSize(Vec2(getIO().displaySize.y / 2, size.y + 130), ImGuiCond.Once)
        begin(id, ::open, ImGuiWindowFlags.NoSavedSettings or ImGuiWindowFlags.MenuBar)
        var export = false
        menuBar {
            menu("File") {
                if (menuItem("Export PNG...", "Ctrl+E")) {
                    export = true
                }
            }
        }
        if (export) {
            openPopup(exportDialog.id)
        }
        exportDialog.render()

        val centerPoint = getWindowContentRegionWidth()/2

        val pos = Vec2((getCursorPosX()+centerPoint)-(size.x/2), getCursorPosY())
        setCursorPos(pos)
        image(Checkerboard.getImTextureID(), size)
        setCursorPos(pos)
        image(texture.getImTextureID(), size, uv0, uv1)
        if (isItemHovered()) {
            wheel += getIO().mouseWheel.coerceIn(-5f, 5f)
            uv0 = Vec2((0.1f*wheel).coerceIn(-0.5f, 0.5f), (0.1f*wheel).coerceIn(-0.5f, 0.5f))
            uv1 = Vec2((1-(0.1f*wheel)).coerceIn(0.5f, 1.5f), (1-(0.1f*wheel)).coerceIn(0.5f, 1.5f))
        }
        spacing()
        val colWidth = calcTextSize("Dimensions . 16 bits/sample").x
        val colStart = Vec2(centerPoint - (colWidth/2), getCursorPosY())
        columns(2)
        setCursorPos(colStart)
        text("Dimensions")
        nextColumn()
        text("${png.width}x${png.height}")
        nextColumn()
        setCursorPosX(colStart.x)
        text("Format")
        nextColumn()
        text("${png.colorType}")
        nextColumn()
        setCursorPosX(colStart.x)
        text("Bit depth")
        nextColumn()
        text("${png.bitDepth} bits/sample")
        nextColumn()
        setCursorPosX(colStart.x)
        text("Bits/pixel")
        nextColumn()
        text("${png.colorType.stride*8}")
        columns(1)
        end()
        return open
    }

}