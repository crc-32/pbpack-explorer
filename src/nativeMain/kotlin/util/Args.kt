package util

class Args(argv: Array<String>) {
    var help: Boolean = false
    val pbpackPath: String?
    init {
        var i = 0
        while (i <= argv.lastIndex) {
            when (argv[i]) {
                "-h", "--help" -> help = true
            }
            i++
        }
        pbpackPath = argv.lastOrNull()
    }
}