package top.e404.tavolo.draw.compose

import org.jetbrains.skia.Image
import top.e404.tavolo.util.Colors


typealias Composable = UiElement.() -> Unit

@UiDsl
fun UiElement.column(
    modifier: Modifier = Modifier,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left,
    block: Column.() -> Unit
) {
    add(Column(horizontalAlignment).apply { this.modifier = modifier; block() })
}

@UiDsl
fun UiElement.row(
    modifier: Modifier = Modifier,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
    block: Row.() -> Unit
) {
    add(Row(verticalAlignment).apply { this.modifier = modifier; block() })
}

@UiDsl
fun UiElement.box(
    modifier: Modifier = Modifier,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
    block: Box.() -> Unit = {}
) {
    add(Box(horizontalAlignment, verticalAlignment).apply { this.modifier = modifier; block() })
}

@UiDsl
fun UiElement.table(
    modifier: Modifier = Modifier,
    columnSpacing: Float = 0f,
    rowSpacing: Float = 0f,
    block: Table.() -> Unit
) {
    add(Table(columnSpacing, rowSpacing).apply { this.modifier = modifier; block() })
}

@UiDsl
fun Table.tableRow(block: TableRow.() -> Unit) {
    add(TableRow().apply(block))
}

@UiDsl
fun TableRow.cell(
    modifier: Modifier = Modifier,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
    block: TableCell.() -> Unit
) {
    add(TableCell(horizontalAlignment, verticalAlignment).apply { this.modifier = modifier; block() })
}

@UiDsl
fun UiElement.text(
    text: String,
    modifier: Modifier = Modifier,
    textModifier: TextModifier = TextModifier,
    fontSize: Float? = null,
    textColor: Int? = null,
    fontFamily: String? = null,
    textOverflow: TextOverflow? = null,
    textOverflowPlaceholder: String? = null,
    style: TextStyle? = null,
    underline: TextUnderline? = null
) {
    add(
        Text(
            text = text,
            textModifier = textModifier,
            fontSize = fontSize,
            textColor = textColor,
            fontFamily = fontFamily,
            textOverflow = textOverflow,
            textOverflowPlaceholder = textOverflowPlaceholder,
            style = style,
            underline = underline
        ).apply { this.modifier = modifier }
    )
}

@UiDsl
fun UiElement.image(
    image: Image,
    modifier: Modifier = Modifier,
    imageOverflow: ImageOverflow = ImageOverflow.Scale
) {
    add(ImageElement(image, imageOverflow).apply { this.modifier = modifier })
}

@UiDsl
fun UiElement.iconText(
    text: String,
    fontSize: Float,
    modifier: Modifier = Modifier,
    textModifier: TextModifier = TextModifier,
    textColor: Int? = null,
    fontFamily: String? = null,
    textOverflow: TextOverflow? = null,
    textOverflowPlaceholder: String? = null,
    style: TextStyle? = null,
    underline: TextUnderline? = null,
    iconColor: Int = Colors.LIGHT_BLUE.argb
) = row(modifier = modifier, verticalAlignment = VerticalAlignment.Center) {
    box(Modifier
        .size(fontSize / 2, fontSize)
        .clip(Shape.RoundedRect(fontSize))
        .background(iconColor)
    )
    text(
        text = text,
        modifier = Modifier.padding(left = fontSize / 4),
        textModifier = textModifier,
        fontSize = fontSize,
        textColor = textColor,
        fontFamily = fontFamily,
        textOverflow = textOverflow,
        textOverflowPlaceholder = textOverflowPlaceholder,
        style = style,
        underline = underline
    )
}
