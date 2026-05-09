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
