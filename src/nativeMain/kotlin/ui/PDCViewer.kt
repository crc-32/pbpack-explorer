package ui

import com.imgui.*
import com.kgl.opengl.GL_RGBA
import formats.PDCData
import formats.PNGData
import formats.pdc.CommandList
import formats.pdc.DrawCommand
import formats.pdc.Frame
import gl.Checkerboard
import gl.PDCPainter
import gl.Texture
import gl.Textures
import ui.extensions.calcTextSize
import ui.extensions.getItemRectMax
import ui.extensions.getItemRectMin
import util.extensions.toHostColor
import kotlin.math.roundToInt

class PDCViewer(private val pdcPainter: PDCPainter, name: String, private val index: Int, private val pdcData: Map<Int, PDCData>, onOverwrite: (PNGData) -> Unit): SubWindow {
    override val id = "View PDC: $name"
    private var open = true
    private var stateDirty = true
    private var redraw = false
    private var texId: Int? = null
    private lateinit var texture: Texture
    private var pdc: PDCData? = null
    private var playing = true
    private var oldCframe = 0
    private var first = true
    private var pointsMode = false
    private var lastHover = -1

    override fun render(): Boolean = with(ImGui) {
        if (pdcData[index] == null) {
            return false
        }
        if (pdc == null || pdc != pdcData[index]) {
            stateDirty = true
        }

        if (stateDirty) {
            stateDirty = false
            if (texId != null) {
                Textures.destroy(texId!!)
            }
            pdc = pdcData[index]!!.copy()
            playing = pdc!!.frames.size > 1
            texture = Texture(pdc!!.viewBox[0].toInt(), pdc!!.viewBox[1].toInt(), GL_RGBA)
            texId = Textures.allocate(texture)
            redraw = true
        }

        if (playing || oldCframe != pdc!!.cFrame || redraw) {
            redraw = false
            pdcPainter.paint(pdc!!, texture, uv0 = Vec2(8f, 8f), uv1 = Vec2(texture.getDimensions().x-8f, texture.getDimensions().y-8f), playing = playing, pointsOnly = pointsMode)
            oldCframe = pdc!!.cFrame
        }

        setNextWindowSize(Vec2(getIO().displaySize.y / 1.25f, getIO().displaySize.y / 1.25f), ImGuiCond.Once)
        begin(id, ::open, ImGuiWindowFlags.NoSavedSettings or ImGuiWindowFlags.MenuBar)
        menuBar {
            menu("View") {
                if (menuItem("Points Display", "Ctrl+Alt+P", pSelected = this@PDCViewer::pointsMode)) {
                    redraw = true
                }
            }
        }
        columns(2)
        if (first) {
            first = false
            setColumnWidth(0, getWindowContentRegionWidth()/1.55f)
        }
        val centerPoint = getColumnWidth()/2

        val scale = Vec2(texture.getDimensions().x, texture.getDimensions().y)
        val targetMult = (400/scale.y).coerceAtMost(getColumnWidth() / scale.x)
        val size = Vec2(scale.x * targetMult, scale.y * targetMult)

        val pos = Vec2((getCursorPosX()+centerPoint)-(size.x/2), getCursorPosY())
        setCursorPos(pos)
        image(Checkerboard.getImTextureID(), size)
        setCursorPos(pos)
        image(texture.getImTextureID(), size)
        if (pdc!!.type == PDCData.Type.Sequence) {
            sliderInt("Frame", pdc!!::cFrame, 0, pdc!!.frames.size-1)
            sameLine()
            checkbox("Play", ::playing)
        }
        spacing()
        val colWidth = calcTextSize("Dimensions . 16 bits/sample").x
        val colStart = Vec2(centerPoint - (colWidth/2), getCursorPosY())
        beginChild("details")
        columns(2)
        setCursorPosX(colStart.x)
        text("Dimensions")
        nextColumn()
        text("${pdc!!.viewBox[0]}x${pdc!!.viewBox[1]}")
        nextColumn()
        setCursorPosX(colStart.x)
        text("Type")
        nextColumn()
        text(pdc!!.type.toString())
        if (pdc!!.type == PDCData.Type.Sequence) {
            nextColumn()
            setCursorPosX(colStart.x)
            text("Play count")
            nextColumn()
            text("${if (pdc!!.playCount == (0xFFFFu).toUShort()) "Infinite (0xFFFF)" else pdc!!.playCount}")
        }
        columns(1)
        endChild()
        nextColumn()
        beginChild("conf")
        pdc!!.frames.forEachIndexed { i, frame ->
            if (collapsingHeader("Frame ${i}", if (pdc!!.frames.size == 1) ImGuiTreeNodeFlags.DefaultOpen else null)) {
                for (cmdi in 0 until frame.commandList.commands.size) {
                    var hover = false
                    val cmd = frame.commandList.commands[cmdi]
                    pushID("${i}-${cmd.hashCode()}")

                    val split = ImDrawListSplitter()
                    split.split(getWindowDrawList(), 2)
                    split.setCurrentChannel(getWindowDrawList(), 1)
                    group {
                        dummy(Vec2(getColumnWidth()-getColumnOffset(), 0f))

                        text("${cmdi}: ${cmd.type}")
                        if (checkbox("Hidden", cmd::hidden)) {
                            redraw = true
                        }
                        if (cmd.type != DrawCommand.Type.Circle) {
                            if (checkbox("Open", cmd::pathOpen)) {
                                redraw = true
                            }
                        }

                        val fill = cmd.fillColor.toHostColor()
                        val stroke = cmd.strokeColor.toHostColor()
                        text("Fill: R${fill.x*255} G${(fill.y*255).roundToInt()} B${(fill.z*255).roundToInt()} A${(fill.w*255).roundToInt()}")
                        text("Stroke: R${stroke.x*255} G${(stroke.y*255).roundToInt()} B${(stroke.z*255).roundToInt()} A${(stroke.w*255).roundToInt()}")
                        text("Stroke Width: ${cmd.strokeWidth}")
                        separator()
                    }

                    val min = getItemRectMin()
                    val max = getItemRectMax()

                    if (isItemHovered()) hover = true

                    if (hover && !playing) {
                        split.setCurrentChannel(getWindowDrawList(), 0)
                        getWindowDrawList().addRectFilled(min, max, getColorU32(ImGuiCol.FrameBgHovered))

                        val pdcTmp = PDCData(PDCData.Type.Image, pdc!!.viewBox, listOf(Frame(0u, CommandList(listOf<DrawCommand>(cmd)))), 0u)
                        pdcPainter.paint(pdcTmp, texture, uv0 = Vec2(8f, 8f), uv1 = Vec2(texture.getDimensions().x-8f, texture.getDimensions().y-8f), playing = false, pointsOnly = pointsMode)
                        lastHover = cmd.hashCode()
                    }else {
                        if (lastHover == cmd.hashCode()) {
                            lastHover = -1
                            redraw = true
                        }
                    }

                    split.merge(getWindowDrawList())

                    popID()
                }
            }
        }
        endChild()
        columns(1)
        end()
        return open
    }
}