import com.imgui.ImGui
import com.kgl.glfw.Glfw
import com.kgl.glfw.OpenGLProfile
import pbpack.ResourcePack
import ui.ExplorerWindow
import util.Args

var mainWindow: ExplorerWindow? = null

fun main(argv: Array<String>) {
    val args = Args(argv)
    if (args.help) {
        println("""
            pbpack-explorer commandline help
            Syntax: pbpack-explorer [options] [pbpack path]
        """.trimIndent())
        return
    }

    Glfw.setErrorCallback { error, description ->
        println("Glfw Error ${error}: $description")
    }

    check(Glfw.init())

    Glfw.windowHints.samples = 4
    with(Glfw.windowHints) {
        if (Platform.osFamily == OsFamily.MACOSX) {
            contextVersionMajor = 3
            contextVersionMinor = 2
            openGLProfile = OpenGLProfile.Core  // 3.2+ only
            openGLForwardCompat = true          // Required on Mac
        } else {
            contextVersionMajor = 3
            contextVersionMinor = 0
            // openGLProfile = OpenGLProfile.Core  // 3.2+ only
            // openGLForwardCompat = true          // 3.0+ only
        }
    }

    if (!args.pbpackPath.isNullOrEmpty()) {
        val respack = ResourcePack(args.pbpackPath)
        mainWindow = ExplorerWindow(respack)
    }else {
        mainWindow = ExplorerWindow()
    }
    try {
        while (true) {
            if (!(mainWindow?.run()?: false)) break
        }
    } finally {
        mainWindow?.cleanup()
        Glfw.terminate()
    }

}