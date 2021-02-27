package formats
import kotlinx.cinterop.*
import libpng.*
import platform.posix.fclose
import platform.posix.fmemopen
import util.extensions.isPNG

class PNGData(raw: ByteArray) {
    var width: UInt = 0u
    var height: UInt = 0u
    var colorType: PNGColorType = PNGColorType.Gray
    var bitDepth: UByte = 0u
    private var data: UByteArray = ubyteArrayOf()

    enum class PNGColorType(val value: Int, val stride: Int) {
        Gray(PNG_COLOR_TYPE_GRAY, 1),
        Palette(PNG_COLOR_TYPE_PALETTE, 1),
        RGB(PNG_COLOR_TYPE_RGB, 3),
        RGBA(PNG_COLOR_TYPE_RGB_ALPHA, 4),
        GrayAlpha(PNG_COLOR_TYPE_GRAY_ALPHA, 2);

        companion object {
            fun fromValue(value: Int) = values().first { it.value == value }
        }
    }

    init {
        require(raw.isPNG())

        memScoped {
            val readPtr = png_create_read_struct(PNG_LIBPNG_VER_STRING, null, null, null)
            assert(readPtr != null) {return@assert "readPtr null"}

            val infoPtr = png_create_info_struct(readPtr)
            assert(infoPtr != null) {return@assert "infoPtr null"}

            raw.usePinned {
                val fp = fmemopen(it.addressOf(0), it.get().size.toULong(), "r")
                png_init_io(readPtr, fp)
                png_set_sig_bytes(readPtr, 0)
                png_read_info(readPtr, infoPtr)

                width = png_get_image_width(readPtr, infoPtr)
                height = png_get_image_height(readPtr, infoPtr)
                val initColorType = PNGColorType.fromValue(png_get_color_type(readPtr, infoPtr).toInt())
                data = UByteArray(width.toInt()*height.toInt()*4)

                png_set_packing(readPtr)
                png_set_expand(readPtr)
                if (initColorType == PNGColorType.Palette) {
                    png_set_palette_to_rgb(readPtr)
                } else if (initColorType == PNGColorType.Gray) {
                    png_set_expand_gray_1_2_4_to_8(readPtr)
                }
                if (png_get_valid(readPtr, infoPtr, PNG_INFO_tRNS) != 0u) {
                    png_set_tRNS_to_alpha(readPtr)
                }

                png_set_interlace_handling(readPtr)
                png_read_update_info(readPtr, infoPtr)

                colorType = PNGColorType.fromValue(png_get_color_type(readPtr, infoPtr).toInt())
                bitDepth = png_get_bit_depth(readPtr, infoPtr)

                val rowPointers = allocArray<CArrayPointerVar<png_byteVar>>(height.toInt())
                for (y in 0 until height.toInt()) {
                    rowPointers[y] = allocArray<png_byteVar>(png_get_rowbytes(readPtr, infoPtr).toInt())
                }

                png_read_image(readPtr, rowPointers)

                png_destroy_read_struct(readPtr.toLong().toCPointer(), infoPtr.toLong().toCPointer(), null)
                fclose(fp)

                var i = 0
                for (y in 0 until height.toInt()) {
                    val row = rowPointers[y]
                    for (x in 0 until width.toInt()) {
                        val pixel = (row!! + x*colorType.stride)
                        when (colorType) {
                            PNGColorType.Gray -> {
                                data[i+0] = pixel!![0]
                                data[i+1] = pixel[0]
                                data[i+2] = pixel[0]
                                data[i+3] = 0xFFu
                            }
                            PNGColorType.GrayAlpha -> {
                                data[i+0] = pixel!![0]
                                data[i+1] = pixel[0]
                                data[i+2] = pixel[0]
                                data[i+3] = pixel[1]
                            }
                            PNGColorType.RGB -> {
                                data[i+0] = pixel!![0]
                                data[i+1] = pixel[1]
                                data[i+2] = pixel[2]
                                data[i+3] = 0xFFu
                            }
                            PNGColorType.RGBA -> {
                                data[i+0] = pixel!![0]
                                data[i+1] = pixel[1]
                                data[i+2] = pixel[2]
                                data[i+3] = pixel[3]
                            }
                            else -> {
                                throw UnsupportedOperationException("PNG color type of ${colorType} unsupported.")
                            }
                        }
                        i += 4
                    }
                }
            }
        }
    }

    fun getData(): UByteArray {
        return data
    }
}