package gl

import com.imgui.Vec2
import com.imgui.Vec4
import com.kgl.glfw.Glfw
import com.kgl.opengl.*
import com.kgl.opengl.glBindTexture
import com.kgl.opengl.glClear
import com.kgl.opengl.glClearColor
import com.kgl.opengl.glDisable
import com.kgl.opengl.glDrawArrays
import com.kgl.opengl.glEnable
import com.kgl.opengl.glLineWidth
import com.kgl.opengl.glTexImage2D
import com.kgl.opengl.glTexParameteri
import com.kgl.opengl.glViewport
import formats.PDCData
import formats.pdc.DrawCommand
import kotlinx.cinterop.*
import com.kgl.opengl.GL_TEXTURE_WRAP_S
import com.kgl.opengl.GL_TEXTURE_WRAP_T
import com.kgl.opengl.GL_CLAMP_TO_EDGE
import com.soywiz.korma.geom.vector.VectorPath
import com.soywiz.korma.geom.vector.circle
import util.extensions.toHostColor
import kotlin.system.getTimeMillis

class PDCPainter {
    val glVersion = glGetInteger(GL_MAJOR_VERSION) * 100 + glGetInteger(GL_MINOR_VERSION) * 10

    private var program: UInt
    private var frameBuffer: UInt
    private var vertexArrayObj: UInt
    private var viewportLoc: Int
    private var posLocation: Int
    private var colorLoc: Int
    var enhanceScale = 16

    init {
        vertexArrayObj = glGenVertexArray()
        glBindVertexArray(vertexArrayObj)
        /*val glslVersionString = "#version 450"
        val vertexShader = """
            uniform vec2 Viewport;
            attribute vec2 Position;
            
            void main() {
                gl_Position = vec4(2*Position.xy / Viewport.xy - 1, 0, 1);
            }
        """.trimIndent()
        val fragmentShader = """
            uniform vec4 UserCol;
            out vec4 color;
            void main() {
                color = UserCol;
            }
        """.trimIndent()*/

        val glslVersionString = "#version 300 es"
        val vertexShader = """
            uniform vec2 Viewport;
            layout (location = 0) in vec2 Position;
            
            void main() {
                gl_Position = vec4(2.0*Position.xy / Viewport.xy - 1.0, 0.0, 1.0);
            }
        """.trimIndent()
        val fragmentShader = """
            uniform mediump vec4 UserCol;
            out mediump vec4 color;
            void main() {
                color = UserCol;
            }
        """.trimIndent()


        val vShaderId = glCreateShader(GL_VERTEX_SHADER)
        glShaderSource(vShaderId, """
            $glslVersionString
            $vertexShader
        """.trimIndent())
        glCompileShader(vShaderId)
        check(glGetShaderi(vShaderId, GL_COMPILE_STATUS) == GL_TRUE.toInt()) { "Vertex shader didn't compile:\n${glGetShaderInfoLog(vShaderId)}" }

        val fShaderId = glCreateShader(GL_FRAGMENT_SHADER)
        glShaderSource(fShaderId, """
            $glslVersionString
            $fragmentShader
        """.trimIndent())
        glCompileShader(fShaderId)
        check(glGetShaderi(fShaderId, GL_COMPILE_STATUS) == GL_TRUE.toInt()) { "Frag shader didn't compile:\n${glGetShaderInfoLog(fShaderId)}" }

        program = glCreateProgram()
        glAttachShader(program, vShaderId)
        glAttachShader(program, fShaderId)
        glLinkProgram(program)
        check(glGetProgrami(program, GL_LINK_STATUS) == GL_TRUE.toInt()) { "Program didn't link" }

        glDeleteShader(vShaderId)
        glDeleteShader(fShaderId)

        viewportLoc = glGetUniformLocation(program, "Viewport")
        posLocation = glGetAttribLocation(program, "Position")
        colorLoc = glGetUniformLocation(program, "UserCol")
        frameBuffer = glGenFramebuffer()
    }

    private fun paintVtxDataFill(vtxData: ByteArray, vtxCount: Int, color: Vec4) {
        vtxData.usePinned { data ->
            glBufferData(GL_ARRAY_BUFFER, vtxData.size.toLong(), data.addressOf(0), GL_STATIC_DRAW)
            glUniform4f(colorLoc, color.x, color.y, color.z, color.w)
            glPolygonMode(GL_FRONT_AND_BACK,GL_FILL)
            glDrawArrays(GL_TRIANGLES, 0, vtxCount)
        }
    }

    private fun paintVtxDataLine(vtxData: ByteArray, vtxCount: Int, color: Vec4, thickness: Float, pathOpen: Boolean) {
        if (thickness > 0f) {
            vtxData.usePinned {data ->
                glBufferData(GL_ARRAY_BUFFER, vtxData.size.toLong(), data.addressOf(0), GL_STATIC_DRAW)
                glUniform4f(colorLoc, color.x, color.y, color.z, color.w)
                glPolygonMode(GL_FRONT_AND_BACK,GL_LINE)
                glLineWidth(thickness*enhanceScale)
                if (!pathOpen) {
                    glDrawArrays(GL_LINE_LOOP, 0, vtxCount)
                } else {
                    glDrawArrays(GL_LINE_STRIP, 0, vtxCount)
                }
            }
        }
    }

    private fun paintVtxDataPoints(vtxData: ByteArray, vtxCount: Int, color: Vec4) {
        vtxData.usePinned {data ->
            glBufferData(GL_ARRAY_BUFFER, vtxData.size.toLong(), data.addressOf(0), GL_STATIC_DRAW)
            glUniform4f(colorLoc, color.x, color.y, color.z, color.w)
            glPointSize(2f)
            glDrawArrays(GL_POINTS, 0, vtxCount)
        }
    }

    fun paint(pdc: PDCData, texture: Texture, uv0: Vec2 = Vec2.Zero, uv1: Vec2 = texture.getDimensions(), playing: Boolean = true, pointsOnly: Boolean = false) {
        if (pdc.type == PDCData.Type.Sequence && playing && pdc.lastFrameTime != -1L && getTimeMillis() - pdc.lastFrameTime < pdc.getLastFrame().duration.toLong()) {
            return
        }
        pdc.lastFrameTime = getTimeMillis()
        val frame = if (playing) {
            pdc.getFrame()
        }else {
            if (pdc.cFrame > pdc.frames.lastIndex) pdc.cFrame = 0
            pdc.frames[pdc.cFrame]
        }

        glUseProgram(program)

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_BLEND)

        val w = pdc.viewBox[0]
        val h = pdc.viewBox[1]
        glBindVertexArray(vertexArrayObj)

        val oldBuf = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer)

        glUniform2f(viewportLoc, w.toFloat(), h.toFloat())

        glBindTexture(GL_TEXTURE_2D, texture.getID())
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA.toInt(),w.toInt()*enhanceScale, h.toInt()*enhanceScale, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0.toLong().toCPointer<ByteVar>())
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST.toInt())
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST.toInt())
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE.toInt())
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE.toInt())

        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture.getID(), 0)

        glDrawBuffers(1, cValuesOf(GL_COLOR_ATTACHMENT0))
        assert(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { return@assert "Failed FB check" }

        glDisable(GL_CULL_FACE)
        glDisable(GL_DEPTH_TEST)

        if (glVersion >= 320) glBindSampler(0u,0u)

        glClearColor(0f,0f,0f,0f)
        glClear(GL_COLOR_BUFFER_BIT)

        glViewport(0+uv0.x.toInt(), 0+uv0.y.toInt(), uv1.x.toInt()*enhanceScale, uv1.y.toInt()*enhanceScale)

        frame.commandList.commands.forEach {
            if (it.hidden) return@forEach
            val vBuffer = glGenBuffer()
            val scol = it.strokeColor.toHostColor()
            val fcol = it.fillColor.toHostColor()
            glBindBuffer(GL_ARRAY_BUFFER, vBuffer)

            glEnableVertexAttribArray(posLocation.toUInt())
            glVertexAttribPointer(posLocation.toUInt(), 2, GL_FLOAT, false, 0, 0L.toCPointer<ByteVar>())

            when (it.type){
                DrawCommand.Type.Path, DrawCommand.Type.PrecisePath -> {
                    var fillVtxData: ByteArray? = null
                    var fillVtxCount = 0

                    val path = it.getPath()
                    val points = path.getPoints()
                    if (it.pathOpen != true) {
                        val triangles = EarClipper(path).triangulate()
                        fillVtxData = buildList<Byte> {
                            triangles.forEach {
                                addAll(PDCPaintVert(it.x.toFloat(), it.y.toFloat()).toBytes().toList())
                                fillVtxCount++
                            }
                        }.toByteArray()
                    }

                    val outlineVtxData = buildList<Byte> {
                        points.forEach {
                            addAll(PDCPaintVert(it.x.toFloat(), it.y.toFloat()).toBytes().toList())
                        }
                    }.toByteArray()

                    if (!pointsOnly) {
                        if (!it.pathOpen && fillVtxData?.size?:0 > 0) {
                            paintVtxDataFill(fillVtxData!!, fillVtxCount, fcol)
                        }
                    }

                    if (pointsOnly) {
                        paintVtxDataPoints(outlineVtxData, path.totalPoints, scol)
                        paintVtxDataLine(outlineVtxData, path.totalPoints, scol.copy(w = 0.5f), 1f, it.pathOpen)
                    }else {
                        paintVtxDataLine(outlineVtxData, path.totalPoints, scol, it.strokeWidth.toFloat(), it.pathOpen)
                    }

                    glDisableVertexAttribArray(posLocation.toUInt())
                }

                DrawCommand.Type.Circle -> {
                    if (!pointsOnly) {
                        for (point in it.points) {
                            val path = VectorPath {
                                circle(point, it.radius.toDouble())
                                close()
                            }
                            val genPoints = path.getPoints()
                            var outlineVtxCount = 0
                            var fillVtxCount = 0

                            val outlineVtxData = buildList<Byte> {
                                genPoints.forEach {
                                    addAll(PDCPaintVert(it.x.toFloat(), it.y.toFloat()).toBytes().toList())
                                    outlineVtxCount++
                                }
                            }.toByteArray()

                            val triangles = EarClipper(path).triangulate()
                            val fillVtxData = buildList<Byte> {
                                triangles.forEach {
                                    addAll(PDCPaintVert(it.x.toFloat(), it.y.toFloat()).toBytes().toList())
                                    fillVtxCount++
                                }
                            }.toByteArray()

                            paintVtxDataFill(fillVtxData, fillVtxCount, fcol)
                            paintVtxDataLine(outlineVtxData, outlineVtxCount, scol, it.strokeWidth.toFloat(), false)
                        }
                    } else {
                        val outlineVtxData = buildList<Byte> {
                            it.points.forEach {
                                addAll(PDCPaintVert(it.x.toFloat(), it.y.toFloat()).toBytes().toList())
                            }
                        }.toByteArray()
                        paintVtxDataPoints(outlineVtxData, it.points.size, scol)
                    }
                }

                else -> {
                    println("Invalid command")
                }
            }
        }
        glBindVertexArray(0.toUInt())
        glBindFramebuffer(GL_FRAMEBUFFER, oldBuf.toUInt())
    }

    fun destroy() {
        glDeleteProgram(program)
        glDeleteFramebuffer(frameBuffer)
        glDeleteVertexArray(vertexArrayObj)
    }
}