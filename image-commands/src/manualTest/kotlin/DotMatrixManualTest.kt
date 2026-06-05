package top.e404.tavolo

import org.jetbrains.skia.*
import top.e404.tavolo.dot.binary
import top.e404.tavolo.dot.generator
import top.e404.tavolo.util.Colors
import top.e404.tavolo.util.bytes
import top.e404.tavolo.util.withCanvas
import java.io.File
import kotlin.test.Test

class DotMatrixManualTest {
    private fun test(file: File) = binary(Image.makeFromEncoded(file.readBytes())).generator(1, 1)

    @Test
    fun t1() {
        File("in").listFiles()!!.forEach {
            if (it.name.endsWith(".gif")) return@forEach
            println(test(it))
        }
    }
}
