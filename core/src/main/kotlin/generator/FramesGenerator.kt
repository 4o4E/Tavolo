package top.e404.tavolo.generator

import top.e404.tavolo.frame.Frame

fun interface FramesGenerator {
    suspend fun generate(args: MutableMap<String, String>): MutableList<Frame>
}
