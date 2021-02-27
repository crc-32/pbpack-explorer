package ui

import cimgui.internal.igCalcTextSize
import com.imgui.*
import com.imgui.impl.ImGuiGlfw
import com.imgui.impl.ImGuiOpenGL3
import com.kgl.glfw.Glfw
import com.kgl.glfw.Window
import com.kgl.opengl.*
import formats.PDCData
import formats.PNGData
import gl.PDCPainter
import gl.Texture
import gl.extensions.toRGBATexture
import kotlinx.cinterop.memScoped
import pbpack.ResourcePack
import util.extensions.*
import kotlin.math.round

class ExplorerWindow(var pbpack: ResourcePack? = null) {

    private val glfw: ImGuiGlfw
    private val gl: ImGuiOpenGL3

    private val window: Window
    private var stateDirty = true
    private var tex: MutableMap<Int, Texture> = mutableMapOf()
    private var pdcsData: MutableMap<Int, PDCData> = mutableMapOf()

    private val cbData = ubyteArrayOf(0xa0u, 0xa0u, 0xa0u, 0xffu, 0xf0u, 0xf0u, 0xf0u, 0xffu,
                                      0xf0u, 0xf0u, 0xf0u, 0xffu, 0xa0u, 0xa0u, 0xa0u, 0xffu)
    private val checkerBoard: Texture

    private val pdcPainter: PDCPainter

    private val subWindows = mutableListOf<SubWindow>()

    init {
        window = Window(1280, 720, "pbpack Explorer")
        window.size = Pair((window.monitor?.physicalSize?.first?.div(1.2)?: 1280).toInt(), (window.monitor?.physicalSize?.second?.div(1.2)?: 720).toInt())
        Glfw.currentContext = window
        Glfw.setSwapInterval(1)

        val glslVersion = if (Platform.osFamily == OsFamily.MACOSX) "#version 150" else "#version 130"

        ImGui.createContext()
        ImGui.styleColorsDark()
        glfw = ImGuiGlfw(window, true)
        gl = ImGuiOpenGL3(glslVersion)

        checkerBoard = Texture(2, 2, GL_RGBA, wrap = GL_REPEAT).also {
            it.setImage(cbData.toUByteArray())
        }
        pdcPainter = PDCPainter()

        subWindows.add(OpenDialog() {
            pbpack = ResourcePack(it)
        })
    }

    private fun getPNG() = pbpack?.resources?.filter { it -> it.data.isPNG() }
    private fun getPDCI() = pbpack?.resources?.filter { it -> it.data.isPDCI() }
    private fun getPDCS() = pbpack?.resources?.filter { it -> it.data.isPDCS() }

    private fun resourceGridItem(index: Int, texture: Texture) = with(ImGui) {
        val scale = texture.getDimensions()
        val targetMult = (200/scale.y).coerceAtMost(getColumnWidth()/scale.x)
        val size = Vec2(scale.x * targetMult, scale.y * targetMult)

        val pos = Vec2((getCursorPosX()+(getColumnWidth()/2))-(size.x/2), getCursorPosY())
        image(checkerBoard.getImTextureID(), Vec2(getColumnWidth(),200f))
        val txtY = getCursorPosY()
        setCursorPos(pos)
        image(texture.getImTextureID(), size)
        val xWidth = memScoped {
            val pout = ImVec2()
            igCalcTextSize(pout.ptr, index.toString(), null, false, 0f)
            return@memScoped pout.x
        }
        setCursorPosX(getCursorPosX() + ((getColumnWidth() / 2) - (xWidth / 2)))
        setCursorPosY(txtY+1)
        text(index.toString())
    }

    fun run(): Boolean {
        if (!window.shouldClose) {
            if (stateDirty) {
                stateDirty = false
                getPNG()?.forEach {
                    val png = PNGData(it.data)
                    if (tex[it.meta.index] != null) {
                        tex[it.meta.index]!!.destroy()
                    }
                    tex[it.meta.index] = png.getData().toRGBATexture(png.width.toInt(), png.height.toInt())
                }

                getPDCI()?.forEach {
                    val pdci = PDCData.fromBytes(it.data)
                    if (tex[it.meta.index] == null) tex[it.meta.index] = Texture(pdci.viewBox[0].toInt(), pdci.viewBox[1].toInt(), GL_RGBA, GL_LINEAR)
                    pdcPainter.paint(pdci, tex[it.meta.index]!!)
                }
            }
            Glfw.pollEvents()
            gl.newFrame()
            glfw.newFrame()
            with(ImGui) {
                newFrame()

                // https://github.com/ocornut/imgui/issues/331
                var open = false
                mainMenuBar {
                    menu("File") {
                        if (menuItem("Open..", "Ctrl+O")) {
                            open = true
                        }
                        if (menuItem("Save", "Ctrl+S", enabled = pbpack != null))   { TODO() }
                        if (menuItem("Close"))  { return false }
                    }
                }
                if (open) {
                    openPopup(OpenDialog.ID)
                }

                begin("Pack Browser", null, ImGuiWindowFlags.MenuBar)
                if(sliderInt("PDC Enhance Level", pdcPainter::enhanceScale, 1, 8)) {
                    stateDirty = true
                }
                tabBar("typeTabs") {
                    tabItem("PNG") {
                        columns(5)
                        var i = 0
                        getPNG()?.forEach {
                            if (i % 5 == 0) separator()
                            resourceGridItem(it.meta.index, tex[it.meta.index]!!)
                            nextColumn()
                            i++
                        }
                        columns(1)
                    }
                    tabItem("PDCI") {
                        columns(5)
                        var i = 0
                        getPDCI()?.forEach {
                            if (i % 5 == 0) separator()
                            resourceGridItem(it.meta.index, tex[it.meta.index]!!)
                            nextColumn()
                            i++
                        }
                        columns(1)
                    }
                    tabItem("PDCS") {
                        columns(5)
                        var i = 0
                        getPDCS()?.forEach {
                            if (pdcsData[it.meta.index] == null) pdcsData[it.meta.index] = PDCData.fromBytes(it.data)
                            val pdcs = pdcsData[it.meta.index]!!
                            if (tex[it.meta.index] == null) tex[it.meta.index.toInt()] = Texture(pdcs.viewBox[0].toInt(), pdcs.viewBox[1].toInt(), GL_RGBA, GL_LINEAR)
                            pdcPainter.paint(pdcs, tex[it.meta.index]!!)
                            if (i % 5 == 0) separator()
                            resourceGridItem(it.meta.index, tex[it.meta.index]!!)
                            nextColumn()
                            i++
                        }
                        columns(1)
                    }
                }
                end()
                subWindows.removeAll { it -> it.render() == false }
                render()
                val (display_w, display_h) = window.frameBufferSize
                glViewport(0, 0, display_w, display_h)
                glClearColor(0.2f, 0.2f, 0.2f, 1.0f)
                glClear(GL_COLOR_BUFFER_BIT)
                gl.renderDrawData(getDrawData())

                window.swapBuffers()
            }
        }else {
            return false
        }

        return true
    }

    fun cleanup() {
        tex.forEach {
            it.value.destroy()
        }
        checkerBoard.destroy()
        pdcPainter.destroy()
        gl.close()
        glfw.close()
        ImGui.destroyContext()
        window.close()
    }
}