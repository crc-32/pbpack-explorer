package formats.pdc

import kotlin.native.concurrent.MutableData

data class CommandList(var commands: List<DrawCommand>) {
    fun toBytes(): ByteArray {
        return buildList<Byte> {
            val arr = ByteArray(2)
            arr.setUShortAt(0, commands.size.toUShort())
            this.addAll(arr.toList())
            commands.forEach {
                this.addAll(it.toBytes().toList())
            }
        }.toByteArray()
    }

    companion object {
        fun fromBytes(bytes: ByteArray): CommandList {
            val length = bytes.getUShortAt(0)
            var n = UShort.SIZE_BYTES
            return CommandList(buildList<DrawCommand> {
                for (i in 0 until length.toInt()) {
                    val cmd = DrawCommand.fromBytes(bytes.sliceArray(n..bytes.lastIndex))
                    add(cmd)
                    n += cmd.toBytes().size
                }
            })
        }
    }
}