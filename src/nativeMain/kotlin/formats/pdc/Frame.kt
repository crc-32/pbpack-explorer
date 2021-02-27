package formats.pdc

data class Frame(val duration: UShort, val commandList: CommandList) {

    fun toBytes(): ByteArray {
        val commandListB = commandList.toBytes()
        val dBytes = ByteArray(2)
        dBytes.setUShortAt(0, duration)
        return dBytes + commandListB
    }

    companion object {
        fun fromBytes(bytes: ByteArray, noHeader: Boolean = false): Frame {
            val duration = bytes.getUShortAt(0)
            val commandList = CommandList.fromBytes(bytes.sliceArray((if (noHeader) 0 else UShort.SIZE_BYTES)..bytes.lastIndex))
            return Frame(duration, commandList)
        }
    }
}