package top.e404.tavolo.draw.compose

/** 从外到内扣除边框和 padding，使固定宽度、maxWidth 与组件的阅读宽度使用同一约束。 */
internal fun UiElement.renderDimensionLimit(horizontal: Boolean): Float = modifier.fold(Float.POSITIVE_INFINITY) { limit, item ->
    when (item) {
        is Size -> {
            val size = if (horizontal) item.width else item.height
            if (size.isNaN()) limit else minOf(limit, size)
        }
        is SizeIn -> minOf(limit, if (horizontal) item.maxWidth else item.maxHeight)
        is Padding -> limit - if (horizontal) item.left + item.right else item.top + item.bottom
        is Border -> limit - if (horizontal) item.left + item.right else item.top + item.bottom
        else -> limit
    }
}

internal fun UiElement.requireRenderHeight(height: Float) {
    require(height <= renderDimensionLimit(horizontal = false)) {
        "内容高度超过容器限制，请移除固定高度或增大高度；不会裁掉文本或图表"
    }
}
