package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.Color
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.TextModifier
import top.e404.tavolo.draw.compose.TextOverflow
import top.e404.tavolo.draw.compose.UiDsl
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.font
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.shadow
import top.e404.tavolo.draw.compose.sizeIn
import top.e404.tavolo.draw.compose.text
import top.e404.tavolo.draw.compose.width
import top.e404.tavolo.util.FontManager

data class ChartContainerShadow(
    val blurRadius: Float = 18f,
    val color: Int = Color.makeARGB(55, 29, 37, 56),
    val offsetX: Float = 0f,
    val offsetY: Float = 8f,
    val spread: Float = 0f
)

data class ChartContainerTheme(
    val width: Float? = null,
    val padding: Float = 22f,
    val cornerRadius: Float = 16f,
    val backgroundColor: Int = Color.WHITE,
    val borderColor: Int = Color.makeRGB(218, 225, 236),
    val borderWidth: Float = 1.5f,
    val shadow: ChartContainerShadow? = null,
    val titleTextStyle: ChartTextStyle = ChartTextStyle(
        fontSize = 21f,
        color = Color.makeRGB(35, 44, 58),
        fontFamily = FontManager.defaultFamily,
        fontWeight = 700
    ),
    val subtitleTextStyle: ChartTextStyle = ChartTextStyle(
        fontSize = 15f,
        color = Color.makeRGB(109, 122, 142),
        fontFamily = FontManager.defaultFamily
    ),
    val captionTextStyle: ChartTextStyle = ChartTextStyle(
        fontSize = 14f,
        color = Color.makeRGB(109, 122, 142),
        fontFamily = FontManager.defaultFamily
    ),
    val subtitleTopGap: Float = 6f,
    val contentTopGap: Float = 18f,
    val captionTopGap: Float = 14f,
    val textMaxWidth: Float? = null
)

@UiDsl
fun UiElement.chartContainer(
    title: String? = null,
    subtitle: String? = null,
    caption: String? = null,
    theme: ChartContainerTheme = ChartContainerTheme(),
    modifier: Modifier = Modifier,
    content: UiElement.() -> Unit
) {
    val textMaxWidth = theme.textMaxWidth ?: theme.width?.let { (it - theme.padding * 2f).coerceAtLeast(0f) }
    val titleText = title?.takeIf { it.isNotBlank() }
    val subtitleText = subtitle?.takeIf { it.isNotBlank() }
    val captionText = caption?.takeIf { it.isNotBlank() }
    val hasHeader = titleText != null || subtitleText != null

    column(modifier = chartContainerModifier(modifier, theme)) {
        titleText?.let {
            text(
                it,
                modifier = Modifier.chartContainerTextMaxWidth(textMaxWidth),
                textModifier = theme.titleTextStyle.toTextModifier(),
                textOverflow = TextOverflow.Wrap
            )
        }
        subtitleText?.let {
            text(
                it,
                modifier = Modifier
                    .padding(top = if (titleText != null) theme.subtitleTopGap else 0f)
                    .chartContainerTextMaxWidth(textMaxWidth),
                textModifier = theme.subtitleTextStyle.toTextModifier(),
                textOverflow = TextOverflow.Wrap
            )
        }
        column(modifier = Modifier.padding(top = if (hasHeader) theme.contentTopGap else 0f)) {
            content()
        }
        captionText?.let {
            text(
                it,
                modifier = Modifier
                    .padding(top = theme.captionTopGap)
                    .chartContainerTextMaxWidth(textMaxWidth),
                textModifier = theme.captionTextStyle.toTextModifier(),
                textOverflow = TextOverflow.Wrap
            )
        }
    }
}

private fun chartContainerModifier(modifier: Modifier, theme: ChartContainerTheme): Modifier {
    val shape = Shape.RoundedRect(theme.cornerRadius)
    var result = modifier
    theme.width?.let { result = result.width(it) }
    theme.shadow?.let { shadow ->
        result = result.shadow(
            blurRadius = shadow.blurRadius,
            color = shadow.color,
            offsetX = shadow.offsetX,
            offsetY = shadow.offsetY,
            spread = shadow.spread,
            shape = shape
        )
    }
    result = result
        .clip(shape)
        .background(theme.backgroundColor)
    if (theme.borderWidth > 0f) {
        result = result.border(theme.borderWidth, theme.borderColor, shape = shape)
    }
    return result.padding(theme.padding)
}

private fun Modifier.chartContainerTextMaxWidth(maxWidth: Float?): Modifier =
    if (maxWidth == null) this else sizeIn(maxWidth = maxWidth)

private fun ChartTextStyle.toTextModifier(): TextModifier =
    TextModifier.font(
        fontSize = fontSize,
        textColor = color,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        italic = italic,
        scaleX = scaleX
    )
