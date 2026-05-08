package top.e404.tavolo.generator

import top.e404.tavolo.frame.Frame

fun interface ImageGenerator {
    suspend fun generate(args: MutableMap<String, String>): MutableList<Frame>
}