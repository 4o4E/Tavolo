package top.e404.tavolo.draw.compose

enum class TextOverflow {
    /**
     * 换行显示，超出宽度时自动换行。
     */
    Wrap,

    /**
     * 省略号显示，超出宽度时在末尾添加省略号。
     */
    Ellipsis
}

object TextDefaults {
    const val OVERFLOW_PLACEHOLDER = "…"
}

enum class TextUnderlineMode {
    Line,
    Block
}

data class TextUnderline(
    val color: Int? = null,
    val thickness: Float? = null,
    val offset: Float? = null,
    val strokeStyle: StrokeStyle = StrokeStyle.Solid,
    val mode: TextUnderlineMode = TextUnderlineMode.Line,
    val startPadding: Float = 0f,
    val endPadding: Float = 0f
)

data class TextStyle(
    val fontSize: Float? = null,
    val textColor: Int? = null,
    val fontFamily: String? = null,
    val underline: TextUnderline? = null
)
