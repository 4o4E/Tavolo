package top.e404.skiko.draw.compose

import org.jetbrains.skia.Image


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
fun UiElement.box(modifier: Modifier = Modifier, block: Box.() -> Unit = {}) {
    add(Box().apply { this.modifier = modifier; block() })
}

@UiDsl
fun UiElement.text(text: String, modifier: Modifier = Modifier) {
    add(Text(text).apply { this.modifier = modifier })
}

@UiDsl
fun UiElement.image(image: Image, modifier: Modifier = Modifier) {
    add(ImageElement(image).apply { this.modifier = modifier })
}