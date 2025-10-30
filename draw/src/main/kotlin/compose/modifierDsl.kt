package top.e404.skiko.draw.compose

import org.jetbrains.skia.Typeface

fun Modifier.maxSize(maxWidth: Float = Float.POSITIVE_INFINITY, maxHeight: Float = Float.POSITIVE_INFINITY): Modifier =
    this.then(MaxSize(maxWidth, maxHeight))

fun Modifier.imageOverflow(strategy: ImageOverflow): Modifier = this.then(ImageOverflowStrategy(strategy))
fun Modifier.textOverflow(strategy: TextOverflow): Modifier = this.then(TextOverflowStrategy(strategy))

fun Modifier.background(color: Int): Modifier = this.then(Background(color))
fun Modifier.textColor(color: Int): Modifier = this.then(TextColor(color))
fun Modifier.fontSize(size: Float): Modifier = this.then(FontSize(size))
fun Modifier.fontFamily(typeface: Typeface): Modifier = this.then(FontTypeface(typeface))
fun Modifier.clip(shape: Shape): Modifier = this.then(Clip(shape))

fun Modifier.padding(all: Float): Modifier = this.then(Padding(all, all, all, all))
fun Modifier.padding(horizontal: Float = 0f, vertical: Float = 0f): Modifier =
    this.then(Padding(vertical, horizontal, vertical, horizontal))

fun Modifier.padding(top: Float = 0f, right: Float = 0f, bottom: Float = 0f, left: Float = 0f): Modifier =
    this.then(Padding(top, right, bottom, left))

fun Modifier.margin(all: Float): Modifier = this.then(Margin(all, all, all, all))
fun Modifier.margin(horizontal: Float = 0f, vertical: Float = 0f): Modifier =
    this.then(Margin(vertical, horizontal, vertical, horizontal))

fun Modifier.margin(top: Float = 0f, right: Float = 0f, bottom: Float = 0f, left: Float = 0f): Modifier =
    this.then(Margin(top, right, bottom, left))

fun Modifier.border(all: Float, color: Int): Modifier = this.then(Border(all, all, all, all, color))
fun Modifier.border(horizontal: Float = 0f, vertical: Float = 0f, color: Int): Modifier =
    this.then(Border(vertical, horizontal, vertical, horizontal, color))

fun Modifier.border(top: Float = 0f, right: Float = 0f, bottom: Float = 0f, left: Float = 0f, color: Int): Modifier =
    this.then(Border(top, right, bottom, left, color))

fun Modifier.antiAlias(enabled: Boolean = true) = this.then(AntiAlias(enabled))

