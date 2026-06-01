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
    val underline: TextUnderline? = null,
    val fontWeight: Int? = null,
    val italic: Boolean? = null,
    val lineHeight: Float? = null,
    val letterSpacing: Float? = null,
    val scaleX: Float? = null
) {
    fun merge(other: TextStyle): TextStyle = TextStyle(
        fontSize = other.fontSize ?: fontSize,
        textColor = other.textColor ?: textColor,
        fontFamily = other.fontFamily ?: fontFamily,
        underline = other.underline ?: underline,
        fontWeight = other.fontWeight ?: fontWeight,
        italic = other.italic ?: italic,
        lineHeight = other.lineHeight ?: lineHeight,
        letterSpacing = other.letterSpacing ?: letterSpacing,
        scaleX = other.scaleX ?: scaleX
    )
}

data class TextSpanStyle(
    val fontSize: Float? = null,
    val textColor: Int? = null,
    val fontFamily: String? = null,
    val backgroundColor: Int? = null,
    val backgroundBorderColor: Int? = null,
    val backgroundBorderWidth: Float? = null,
    val backgroundRadius: Float? = null,
    val backgroundPaddingHorizontal: Float? = null,
    val backgroundPaddingVertical: Float? = null,
    val fontWeight: Int? = null,
    val italic: Boolean? = null,
    val letterSpacing: Float? = null
) {
    fun merge(other: TextSpanStyle): TextSpanStyle = TextSpanStyle(
        fontSize = other.fontSize ?: fontSize,
        textColor = other.textColor ?: textColor,
        fontFamily = other.fontFamily ?: fontFamily,
        backgroundColor = other.backgroundColor ?: backgroundColor,
        backgroundBorderColor = other.backgroundBorderColor ?: backgroundBorderColor,
        backgroundBorderWidth = other.backgroundBorderWidth ?: backgroundBorderWidth,
        backgroundRadius = other.backgroundRadius ?: backgroundRadius,
        backgroundPaddingHorizontal = other.backgroundPaddingHorizontal ?: backgroundPaddingHorizontal,
        backgroundPaddingVertical = other.backgroundPaddingVertical ?: backgroundPaddingVertical,
        fontWeight = other.fontWeight ?: fontWeight,
        italic = other.italic ?: italic,
        letterSpacing = other.letterSpacing ?: letterSpacing
    )
}

data class TextRange<T>(
    val item: T,
    val start: Int,
    val end: Int
) {
    init {
        require(start >= 0) { "文本样式范围 start 不能小于 0" }
        require(end >= start) { "文本样式范围 end 不能小于 start" }
    }
}

data class AnnotatedText(
    val text: String,
    val spanStyles: List<TextRange<TextSpanStyle>> = emptyList()
) {
    init {
        spanStyles.forEach { range ->
            require(range.end <= text.length) { "文本样式范围不能超过文本长度" }
        }
    }
}

class AnnotatedTextBuilder {
    private data class MutableSpan(
        val style: TextSpanStyle,
        val start: Int,
        var end: Int
    )

    private val content = StringBuilder()
    private val spanStyles = mutableListOf<MutableSpan>()

    val length: Int get() = content.length

    fun append(text: String): AnnotatedTextBuilder = apply {
        content.append(text)
    }

    fun append(char: Char): AnnotatedTextBuilder = apply {
        content.append(char)
    }

    fun append(text: AnnotatedText): AnnotatedTextBuilder = apply {
        val offset = content.length
        content.append(text.text)
        text.spanStyles.forEach { range ->
            spanStyles += MutableSpan(
                style = range.item,
                start = offset + range.start,
                end = offset + range.end
            )
        }
    }

    fun withStyle(style: TextSpanStyle, block: AnnotatedTextBuilder.() -> Unit): AnnotatedTextBuilder = apply {
        val span = MutableSpan(style, content.length, content.length)
        spanStyles += span
        block()
        span.end = content.length
    }

    fun inlineCode(text: String, style: TextSpanStyle): AnnotatedTextBuilder =
        withStyle(style) { append(text) }

    fun build(): AnnotatedText = AnnotatedText(
        text = content.toString(),
        spanStyles = spanStyles
            .filter { it.start < it.end }
            .map { TextRange(it.style, it.start, it.end) }
    )
}

fun buildAnnotatedText(block: AnnotatedTextBuilder.() -> Unit): AnnotatedText =
    AnnotatedTextBuilder().apply(block).build()

data class TextStyleModifier(
    val style: TextStyle
) : TextElementModifier

interface TextModifier {
    fun then(other: TextModifier): TextModifier = if (other === TextModifier) this else CombinedTextModifier(this, other)
    fun <R> fold(initial: R, operation: (R, TextModifier) -> R): R
    fun toList() = fold(mutableListOf<TextModifier>()) { acc, mod ->
        acc.add(mod)
        acc
    }

    companion object : TextModifier {
        override fun <R> fold(initial: R, operation: (R, TextModifier) -> R): R = initial
        override fun then(other: TextModifier): TextModifier = other
    }
}

private class CombinedTextModifier(private val outer: TextModifier, private val inner: TextModifier) : TextModifier {
    override fun <R> fold(initial: R, operation: (R, TextModifier) -> R): R {
        return inner.fold(outer.fold(initial, operation), operation)
    }
}

interface TextElementModifier : TextModifier {
    override fun <R> fold(initial: R, operation: (R, TextModifier) -> R): R {
        return operation(initial, this)
    }
}

fun TextModifier.textStyle(style: TextStyle): TextModifier = this.then(TextStyleModifier(style))

fun TextModifier.font(
    fontSize: Float? = null,
    textColor: Int? = null,
    fontFamily: String? = null,
    underline: TextUnderline? = null,
    fontWeight: Int? = null,
    italic: Boolean? = null,
    lineHeight: Float? = null,
    letterSpacing: Float? = null,
    scaleX: Float? = null
): TextModifier = textStyle(
    TextStyle(
        fontSize = fontSize,
        textColor = textColor,
        fontFamily = fontFamily,
        underline = underline,
        fontWeight = fontWeight,
        italic = italic,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        scaleX = scaleX
    )
)

fun TextModifier.underline(underline: TextUnderline): TextModifier =
    textStyle(TextStyle(underline = underline))

fun TextModifier.textUnderline(underline: TextUnderline): TextModifier =
    underline(underline)

fun TextModifier.fontWeight(weight: Int): TextModifier =
    textStyle(TextStyle(fontWeight = weight))

fun TextModifier.bold(enabled: Boolean = true): TextModifier =
    fontWeight(if (enabled) 700 else 400)

fun TextModifier.italic(enabled: Boolean = true): TextModifier =
    textStyle(TextStyle(italic = enabled))

fun TextModifier.lineHeight(lineHeight: Float): TextModifier =
    textStyle(TextStyle(lineHeight = lineHeight))

fun TextModifier.letterSpacing(letterSpacing: Float): TextModifier =
    textStyle(TextStyle(letterSpacing = letterSpacing))

fun TextModifier.scaleX(scaleX: Float): TextModifier =
    textStyle(TextStyle(scaleX = scaleX))
