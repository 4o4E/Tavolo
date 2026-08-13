package top.e404.tavolo.draw.compose

import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.SamplingMode

enum class ImageOverflow {
    Scale,
    Crop,
    Stretch
}

/**
 * 图片缩放开启抗锯齿时同时使用双线性过滤和线性 mipmap，兼顾放大与缩小质量。
 */
internal fun imageSamplingMode(antiAlias: Boolean): SamplingMode = if (antiAlias) {
    FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
} else {
    SamplingMode.DEFAULT
}
