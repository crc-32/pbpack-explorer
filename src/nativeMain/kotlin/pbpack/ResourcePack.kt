package pbpack

import kotlinx.cinterop.*
import kotlinx.cinterop.nativeHeap.alloc
import platform.posix.*

class ResourcePack(val path: String) {
    val size: Long
    val crc: UInt
    val resources: List<Resource>
    companion object {
        const val TABLE_OFFSET = 0x0C
        const val RESOURCES_OFFSET = 0x200C // 3.x
    }

    init {
        val canOpen = memScoped {
            val stat = alloc<stat>()
            stat(path, stat.ptr)
            return@memScoped stat.st_mode and S_IFMT.toUInt() == S_IFREG.toUInt()
        }

        if (!canOpen) {
            throw IllegalArgumentException("Couldn't open $path to get resources")
        }

        val fp = fopen(path, "r") ?:
                throw IllegalArgumentException("Couldn't open $path to get resources")

        val bytes: ByteArray
        try {
            fseek(fp, 0L, SEEK_END)
            size = ftell(fp)
            assert(size >= 0)
            rewind(fp)
            bytes = memScoped {
                val buf = allocArray<ByteVar>(size)
                fread(buf, size.toULong(), 1, fp)
                return@memScoped buf.readBytes(size.toInt())
            }
        } finally {
            fclose(fp)
        }
        var n = 0
        val count = bytes.getUIntAt(n)
        n += Int.SIZE_BYTES
        println("Resource pack has $count resources")

        crc = bytes.getUIntAt(n)
        n += Int.SIZE_BYTES

        resources = buildList<Resource> {
            n = TABLE_OFFSET
            for (i in 0 until count.toInt()) {
                val tabled = TabledResource.fromBytes(bytes.copyOfRange(n, n+TabledResource.SIZE_BYTES))
                val data = bytes.copyOfRange(RESOURCES_OFFSET+tabled.offset, RESOURCES_OFFSET+tabled.offset+tabled.size)
                add(Resource(tabled, data))
                n += TabledResource.SIZE_BYTES
            }
        }
        var pngs = 0
        for (i in resources) {
            if (i.data.copyOfRange(0, 8) contentEquals byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) pngs++
        }
        println("PNGS: $pngs")
    }
}