package ui

import com.imgui.*
import com.imgui.impl.ImGuiGlfw
import com.imgui.impl.ImGuiOpenGL3
import com.kgl.glfw.Glfw
import com.kgl.glfw.Window
import com.kgl.opengl.*
import formats.PDCData
import formats.PNGData
import gl.Checkerboard
import gl.PDCPainter
import gl.Texture
import gl.Textures
import gl.extensions.toRGBATexture
import kotlinx.coroutines.*
import pbpack.ResourcePack
import ui.extensions.calcTextSize
import util.extensions.*
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

class ExplorerWindow(var pbpack: ResourcePack? = null) {

    private val glfw: ImGuiGlfw
    private val gl: ImGuiOpenGL3

    private val window: Window
    private var stateDirty = true
    private var pdcSupersample = true
    private val pdcsData: MutableMap<Int, PDCData> = mutableMapOf()
    private val pdciData: MutableMap<Int, PDCData> = mutableMapOf()
    private val pngData: MutableMap<Int, PNGData> = mutableMapOf()

    private val pdcPainter: PDCPainter

    private val subWindows = mutableMapOf<String, SubWindow>()

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
        pdcPainter = PDCPainter()

        subWindows["open"] = PathDialog(actionBtnName = "Open") {
            try {
                pbpack = ResourcePack(it)
                stateDirty = true
            } catch (e: Exception) {
                return@PathDialog e.message?: "Failed"
            }
            return@PathDialog null
        }
    }

    private fun getPNG() = pbpack?.resources?.filter { it -> it.data.isPNG() }
    private fun getPDCI() = pbpack?.resources?.filter { it -> it.data.isPDCI() }
    private fun getPDCS() = pbpack?.resources?.filter { it -> it.data.isPDCS() }

    private fun resourceGridItem(index: Int, texture: Texture, onClick: () -> Unit) = with(ImGui) {
        var clicked = false
        val scale = texture.getDimensions()
        val targetMult = (200/scale.y).coerceAtMost(getColumnWidth()/scale.x)
        val size = Vec2(scale.x * targetMult, scale.y * targetMult)

        val pos = Vec2((getCursorPosX()+(getColumnWidth()/2))-(size.x/2), getCursorPosY())
        image(Checkerboard.getImTextureID(), Vec2(getColumnWidth(),200f))
        if (isItemClicked()) clicked = true
        val txtY = getCursorPosY()
        setCursorPos(pos)
        image(texture.getImTextureID(), size)
        if (isItemClicked()) clicked = true
        val xWidth = calcTextSize(index.toString()).x
        setCursorPosX(getCursorPosX() + ((getColumnWidth() / 2) - (xWidth / 2)))
        setCursorPosY(txtY+1)
        text(index.toString())
        if (isItemClicked()) clicked = true

        if (clicked) onClick()
    }

    fun run(): Boolean {
        if (!window.shouldClose) {
            Glfw.pollEvents()
            gl.newFrame()
            glfw.newFrame()
            glClearColor(0.2f, 0.2f, 0.2f, 1.0f)
            glClear(GL_COLOR_BUFFER_BIT)
            with(ImGui) {
                newFrame()
                if (stateDirty) {
                    setNextWindowSize(Vec2(200f, 50f), ImGuiCond.Always)
                    setNextWindowPos(Vec2(
                            ((Glfw.currentContext?.size?.first!!/2)-(200/2)).toFloat(),
                            ((Glfw.currentContext?.size?.second!!/2)-(50/2)).toFloat(),
                    ))
                    begin("Loading", null, ImGuiWindowFlags.NoSavedSettings or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize)
                    text("Loading...")
                    end()
                    render()
                    gl.renderDrawData(getDrawData())
                    window.swapBuffers()

                    pngData.clear()
                    val tmptex = Textures.copyAndClear()

                    getPNG()?.forEach {
                        pngData[it.meta.index] = PNGData(it.data)
                        val png = pngData[it.meta.index]!!

                        if (tmptex[it.meta.index] != null) {
                            tmptex[it.meta.index]!!.destroy()
                        }
                        Textures[it.meta.index] = png.getData().toRGBATexture(png.width.toInt(), png.height.toInt())
                    }

                    pdciData.clear()
                    pdcsData.clear()

                    getPDCI()?.forEach {
                        val pdci = PDCData.fromBytes(it.data)
                        pdciData[it.meta.index] = pdci
                        if (Textures[it.meta.index] == null) Textures[it.meta.index] = Texture(pdci.viewBox[0].toInt(), pdci.viewBox[1].toInt(), GL_RGBA, GL_LINEAR)
                        pdcPainter.paint(pdci, Textures[it.meta.index]!!)
                    }

                    getPDCS()?.forEach {
                        pdcsData[it.meta.index] = PDCData.fromBytes(it.data)
                    }
                    stateDirty = false
                    return true
                } else {

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
                        menu("View") {
                            if (menuItem("PDC Supersampling", "", pSelected = this@ExplorerWindow::pdcSupersample)) {
                                if (pdcSupersample) {
                                    pdcPainter.enhanceScale = 16
                                    stateDirty = true
                                }else {
                                    pdcPainter.enhanceScale = 1
                                    stateDirty = true
                                }
                            }
                        }
                    }
                    if (open) {
                        openPopup(subWindows["open"]!!.id)
                    }

                    begin("Pack Browser", null, ImGuiWindowFlags.MenuBar)
                    tabBar("typeTabs") {
                        tabItem("PNG") {
                            columns(5)
                            var i = 0
                            getPNG()?.forEach {
                                if (i % 5 == 0) separator()
                                resourceGridItem(it.meta.index, Textures[it.meta.index]!!) {
                                    subWindows["res${it.meta.index}"] = PNGViewer("Resource ${it.meta.index}", it.meta.index, pngData) {
                                        TODO()
                                    }
                                }
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
                                resourceGridItem(it.meta.index, Textures[it.meta.index]!!) {
                                    subWindows["res${it.meta.index}"] = PDCViewer(pdcPainter, "Resource ${it.meta.index}", it.meta.index, pdciData) {
                                        TODO()
                                    }
                                }
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
                                if (Textures[it.meta.index] == null) Textures[it.meta.index] = Texture(pdcs.viewBox[0].toInt(), pdcs.viewBox[1].toInt(), GL_RGBA, GL_LINEAR)
                                pdcPainter.paint(pdcs, Textures[it.meta.index]!!)
                                if (i % 5 == 0) separator()
                                resourceGridItem(it.meta.index, Textures[it.meta.index]!!) {
                                    subWindows["res${it.meta.index}"] = PDCViewer(pdcPainter, "Resource ${it.meta.index}", it.meta.index, pdcsData) {
                                        TODO()
                                    }
                                }
                                nextColumn()
                                i++
                            }
                            columns(1)
                        }
                    }
                    end()
                    subWindows.entries.removeAll { it -> it.value.render() == false }
                }
                getIO().displayFramebufferScale = Vec2(window.contentScale.first, window.contentScale.second)
                render()
                gl.renderDrawData(getDrawData())

                window.swapBuffers()
            }
        }else {
            return false
        }

        return true
    }

    fun cleanup() {
        Textures.destroyAll()
        Checkerboard.destroy()
        pdcPainter.destroy()
        gl.close()
        glfw.close()
        ImGui.destroyContext()
        window.close()
    }
}