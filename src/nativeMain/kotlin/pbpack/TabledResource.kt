package pbpack

data class TabledResource(
        val index: Int,
        val offset: Int,
        val size: Int,
        val crc: UInt
) {
    fun toBytes(): ByteArray {
        val out = ByteArray(SIZE_BYTES)
        var n = 0

        out.setIntAt(n, index)
        n += Int.SIZE_BYTES

        out.setIntAt(n, offset)
        n += Int.SIZE_BYTES

        out.setIntAt(n, size)
        n += Int.SIZE_BYTES

        out.setUIntAt(n, crc)
        n += UInt.SIZE_BYTES
        return out
    }

    companion object {
        const val SIZE_BYTES = Int.SIZE_BYTES*3 + UInt.SIZE_BYTES

        fun fromBytes(bytes: ByteArray): TabledResource {
            if (bytes.size != SIZE_BYTES) throw IllegalArgumentException("ByteArray of size ${bytes.size} incorrect for TabledResource (size $SIZE_BYTES)")
            var n = 0

            val index = bytes.getIntAt(n)
            n += Int.SIZE_BYTES

            val offset = bytes.getIntAt(n)
            n += Int.SIZE_BYTES

            val size = bytes.getIntAt(n)
            n += Int.SIZE_BYTES

            val crc = bytes.getUIntAt(n)
            n += UInt.SIZE_BYTES
            return TabledResource(index, offset, size, crc)
        }
    }
}