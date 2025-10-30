package top.e404.skiko.draw.compose

import org.jetbrains.skia.Image


typealias Composable = Element.() -> Unit

@UiBuilder
fun Element.column(
    modifier: Modifier = Modifier,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left,
    block: Column.() -> Unit
) {
    add(Column(horizontalAlignment).apply { this.modifier = modifier; block() })
}

@UiBuilder
fun Element.row(
    modifier: Modifier = Modifier,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
    block: Row.() -> Unit
) {
    add(Row(verticalAlignment).apply { this.modifier = modifier; block() })
}

@UiBuilder
fun Element.box(modifier: Modifier = Modifier, block: Box.() -> Unit = {}) {
    add(Box().apply { this.modifier = modifier; block() })
}

@UiBuilder
fun Element.text(text: String, modifier: Modifier = Modifier) {
    add(Text(text).apply { this.modifier = modifier })
}

@UiBuilder
fun Element.image(image: Image, modifier: Modifier = Modifier) {
    add(ImageElement(image).apply { this.modifier = modifier })
}