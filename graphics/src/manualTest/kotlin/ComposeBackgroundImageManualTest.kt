package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.Test
import top.e404.tavolo.draw.compose.HorizontalAlignment
import top.e404.tavolo.draw.compose.ImageOverflow
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.StrokeStyle
import top.e404.tavolo.draw.compose.TableRow
import top.e404.tavolo.draw.compose.TextModifier
import top.e404.tavolo.draw.compose.UiDsl
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.VerticalAlignment
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.backgroundImage
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.cell
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.font
import top.e404.tavolo.draw.compose.image
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.row
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.sizeIn
import top.e404.tavolo.draw.compose.table
import top.e404.tavolo.draw.compose.tableRow
import top.e404.tavolo.draw.compose.text

class ComposeBackgroundImageManualTest {
    private val uiFont = ManualTestSupport.uiFont
    private val titleText = TextModifier.font(fontSize = 30f, textColor = Color.WHITE, fontFamily = uiFont)
    private val bodyText = TextModifier.font(fontSize = 17f, textColor = ink, fontFamily = uiFont)
    private val captionText = TextModifier.font(fontSize = 14f, textColor = muted, fontFamily = uiFont)

    @Test
    fun test_compose_background_image() {
        val background = createBackgroundProbeImage()

        ManualTestSupport.saveCompose("背景图片-01-颜色叠加裁切填充容器") {
            box(
                modifier = Modifier
                    .size(1540f, 1330f)
                    .background(pageBg)
                    .padding(30f)
            ) {
                column {
                    text("背景图片 Modifier", textModifier = titleText)
                    text(
                        "覆盖背景色叠加、Scale 完整显示、Crop 居中裁切、Stretch 拉伸、padding 顺序、clip 形状，以及 Box / Row / Column / TableCell 容器。",
                        modifier = Modifier.padding(top = 8f, bottom = 24f),
                        textModifier = TextModifier.font(18f, Color.makeRGB(201, 210, 224), uiFont)
                    )

                    sourceImageCard(background)

                    row {
                        overviewCard(
                            "Box: 背景色 + Scale",
                            "完整渐变图居中显示，上下留白会透出底色。",
                            Modifier
                                .background(accentBlue)
                                .backgroundImage(background, ImageOverflow.Scale)
                        )
                        gap()
                        overviewCard(
                            "Box: 背景色 + Crop",
                            "渐变图铺满容器，左右或上下会被居中裁切。",
                            Modifier
                                .background(accentGreen)
                                .backgroundImage(background, ImageOverflow.Crop)
                        )
                        gap()
                        overviewCard(
                            "Box: 背景色 + Stretch",
                            "整张图直接拉伸到容器，不保留原始比例。",
                            Modifier
                                .background(accentYellow)
                                .background(background, ImageOverflow.Stretch)
                        )
                        gap()
                        overviewCard(
                            "圆角 Clip + Crop",
                            "先裁剪圆角，再绘制底色和背景图，边缘不应溢出。",
                            Modifier
                                .clip(Shape.RoundedRect(28f))
                                .background(accentBlue)
                                .background(background, ImageOverflow.Crop)
                                .border(4f, Color.WHITE, StrokeStyle.Dashed(listOf(14f, 8f)), Shape.RoundedRect(28f))
                        )
                    }

                    row(modifier = Modifier.padding(top = 24f)) {
                        containerCard("Row 容器背景", 450f, 250f) {
                            row(
                                modifier = Modifier
                                    .padding(top = 16f)
                                    .size(390f, 150f)
                                    .clip(Shape.RoundedRect(20f))
                                    .background(Color.makeRGB(239, 76, 88))
                                    .background(background, ImageOverflow.Scale)
                                    .border(3f, Color.WHITE, shape = Shape.RoundedRect(20f))
                                    .padding(14f),
                                verticalAlignment = VerticalAlignment.Center
                            ) {
                                miniPanel("左", accentBlue)
                                miniPanel("中", accentGreen)
                                miniPanel("右", accentYellow)
                            }
                            text("Row 自身有图片背景，内部子元素继续正常布局。", modifier = Modifier.padding(top = 14f), textModifier = captionText)
                        }
                        gap()
                        containerCard("Column 容器背景", 450f, 250f) {
                            column(
                                modifier = Modifier
                                    .padding(top = 16f)
                                    .size(390f, 150f)
                                    .clip(Shape.RoundedRect(20f))
                                    .background(accentBlue)
                                    .backgroundImage(background, ImageOverflow.Crop)
                                    .border(3f, Color.WHITE, shape = Shape.RoundedRect(20f))
                                    .padding(14f),
                                horizontalAlignment = HorizontalAlignment.Center
                            ) {
                                strip("第一行", Color.makeARGB(220, 255, 255, 255))
                                strip("第二行", Color.makeARGB(210, 255, 235, 180))
                                strip("第三行", Color.makeARGB(210, 182, 236, 209))
                            }
                            text("Column 的背景应覆盖整个容器，不受子项高度影响。", modifier = Modifier.padding(top = 14f), textModifier = captionText)
                        }
                        gap()
                        containerCard("TableCell 背景", 450f, 250f) {
                            table(columnSpacing = 12f, rowSpacing = 12f, modifier = Modifier.padding(top = 16f)) {
                                tableRow {
                                    backgroundCell("Scale", background, ImageOverflow.Scale, accentYellow)
                                    backgroundCell("Crop", background, ImageOverflow.Crop, accentGreen)
                                    backgroundCell("圆形", background, ImageOverflow.Crop, accentBlue, circle = true)
                                }
                            }
                            text("表格单元格可独立使用背景图片和底色。", modifier = Modifier.padding(top = 14f), textModifier = captionText)
                        }
                    }

                    row(modifier = Modifier.padding(top = 24f)) {
                        orderCard(
                            "padding -> 背景图",
                            Modifier
                                .padding(20f)
                                .background(Color.makeRGB(226, 74, 68))
                                .backgroundImage(background, ImageOverflow.Crop)
                                .border(4f, Color.WHITE)
                                .padding(18f)
                        )
                        gap()
                        orderCard(
                            "背景图 -> padding",
                            Modifier
                                .background(Color.makeRGB(226, 74, 68))
                                .backgroundImage(background, ImageOverflow.Crop)
                                .padding(20f)
                                .border(4f, Color.WHITE)
                                .padding(18f)
                        )
                        gap()
                        orderCard(
                            "多层背景",
                            Modifier
                                .background(accentGreen)
                                .padding(16f)
                                .backgroundImage(background, ImageOverflow.Scale)
                                .padding(16f)
                                .background(Color.makeARGB(210, 255, 255, 255))
                                .border(4f, accentBlue)
                                .padding(18f)
                        )
                    }
                }
            }
        }
    }

    @UiDsl
    private fun UiElement.sourceImageCard(source: Image) {
        row(
            modifier = Modifier
                .padding(bottom = 24f)
                .size(1424f, 270f)
                .clip(Shape.RoundedRect(18f))
                .background(surface)
                .border(1.5f, border, shape = Shape.RoundedRect(18f))
                .padding(20f),
            verticalAlignment = VerticalAlignment.Center
        ) {
            column {
                text("原图", textModifier = TextModifier.font(24f, ink, uiFont))
                text(
                    "360 x 220 的纯渐变图，图像内部带黑白双层边框。后续示例可通过边框判断 Scale 是否完整显示、Crop 是否裁切边缘。",
                    textModifier = bodyText
                )
            }
            image(
                source,
                modifier = Modifier
                    .padding(left = 30f)
            )
        }
    }

    @UiDsl
    private fun UiElement.overviewCard(title: String, description: String, backgroundModifier: Modifier) {
        column(
            modifier = Modifier
                .size(342f, 270f)
                .clip(Shape.RoundedRect(22f))
                .then(backgroundModifier)
                .border(1.5f, Color.makeARGB(120, 255, 255, 255), shape = Shape.RoundedRect(22f))
                .padding(22f)
        ) {
            text(
                title,
                modifier = Modifier.sizeIn(maxWidth = 292f),
                textModifier = TextModifier.font(24f, Color.WHITE, uiFont)
            )
            text(
                description,
                modifier = Modifier
                    .padding(top = 12f)
                    .sizeIn(maxWidth = 292f),
                textModifier = TextModifier.font(17f, Color.WHITE, uiFont)
            )
            box(
                modifier = Modifier
                    .padding(top = 28f)
                    .size(160f, 44f)
                    .clip(Shape.RoundedRect(22f))
                    .background(Color.makeARGB(210, 255, 255, 255)),
                horizontalAlignment = HorizontalAlignment.Center,
                verticalAlignment = VerticalAlignment.Center
            ) {
                text("内容层", textModifier = TextModifier.font(20f, ink, uiFont))
            }
        }
    }

    @UiDsl
    private fun UiElement.containerCard(title: String, width: Float, height: Float, block: UiElement.() -> Unit) {
        column(
            modifier = Modifier
                .size(width, height)
                .clip(Shape.RoundedRect(18f))
                .background(surface)
                .border(1.5f, border, shape = Shape.RoundedRect(18f))
                .padding(20f)
        ) {
            text(title, textModifier = TextModifier.font(22f, ink, uiFont))
            block()
        }
    }

    @UiDsl
    private fun UiElement.orderCard(title: String, modifier: Modifier) {
        column(
            modifier = Modifier
                .size(460f, 270f)
                .clip(Shape.RoundedRect(18f))
                .background(surface)
                .border(1.5f, border, shape = Shape.RoundedRect(18f))
                .padding(18f)
        ) {
            text(title, textModifier = TextModifier.font(22f, ink, uiFont))
            box(
                modifier = modifier
                    .size(260f, 60f),
                horizontalAlignment = HorizontalAlignment.Center,
                verticalAlignment = VerticalAlignment.Center
            ) {
                text("观察背景边界", textModifier = TextModifier.font(21f, ink, uiFont))
            }
        }
    }

    @UiDsl
    private fun UiElement.miniPanel(label: String, color: Int) {
        box(
            modifier = Modifier
                .padding(right = 12f)
                .size(104f, 88f)
                .clip(Shape.RoundedRect(14f))
                .background(Color.makeARGB(218, 255, 255, 255))
                .border(3f, color, shape = Shape.RoundedRect(14f)),
            horizontalAlignment = HorizontalAlignment.Center,
            verticalAlignment = VerticalAlignment.Center
        ) {
            text(label, textModifier = TextModifier.font(24f, color, uiFont))
        }
    }

    @UiDsl
    private fun UiElement.strip(label: String, color: Int) {
        box(
            modifier = Modifier
                .padding(bottom = 8f)
                .size(260f, 32f)
                .clip(Shape.RoundedRect(16f))
                .background(color),
            horizontalAlignment = HorizontalAlignment.Center,
            verticalAlignment = VerticalAlignment.Center
        ) {
            text(label, textModifier = TextModifier.font(16f, ink, uiFont))
        }
    }

    @UiDsl
    private fun TableRow.backgroundCell(
        label: String,
        image: Image,
        overflow: ImageOverflow,
        color: Int,
        circle: Boolean = false
    ) {
        val shape = if (circle) Shape.Circle else Shape.RoundedRect(14f)
        cell(
            modifier = Modifier
                .size(112f, 112f)
                .clip(shape)
                .background(color)
                .backgroundImage(image, overflow)
                .border(3f, Color.WHITE, shape = shape)
                .padding(10f),
            horizontalAlignment = HorizontalAlignment.Center,
            verticalAlignment = VerticalAlignment.Center
        ) {
            text(label, textModifier = TextModifier.font(17f, Color.WHITE, uiFont))
        }
    }

    @UiDsl
    private fun UiElement.gap(width: Float = 22f) {
        box(Modifier.size(width, 1f))
    }

    private fun createBackgroundProbeImage(): Image {
        val width = 360
        val height = 220
        return Surface.makeRasterN32Premul(width, height).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.TRANSPARENT)
            val paint = Paint().apply {
                isAntiAlias = false
                mode = PaintMode.FILL
            }
            val topLeft = intArrayOf(38, 110, 255)
            val topRight = intArrayOf(52, 198, 132)
            val bottomLeft = intArrayOf(255, 208, 68)
            val bottomRight = intArrayOf(234, 88, 82)

            for (y in 0 until height) {
                val v = y.toFloat() / (height - 1)
                for (x in 0 until width) {
                    val u = x.toFloat() / (width - 1)
                    val r = bilinear(topLeft[0], topRight[0], bottomLeft[0], bottomRight[0], u, v)
                    val g = bilinear(topLeft[1], topRight[1], bottomLeft[1], bottomRight[1], u, v)
                    val b = bilinear(topLeft[2], topRight[2], bottomLeft[2], bottomRight[2], u, v)
                    paint.color = Color.makeRGB(r, g, b)
                    canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), 1f, 1f), paint)
                }
            }
            paint.apply {
                isAntiAlias = true
                mode = PaintMode.STROKE
                strokeWidth = 2f
                color = Color.makeARGB(235, 35, 44, 58)
            }
            canvas.drawRect(Rect.makeXYWH(1f, 1f, width - 2f, height - 2f), paint)
            surface.makeImageSnapshot()
        }
    }

    private fun bilinear(topLeft: Int, topRight: Int, bottomLeft: Int, bottomRight: Int, u: Float, v: Float): Int {
        val top = topLeft * (1f - u) + topRight * u
        val bottom = bottomLeft * (1f - u) + bottomRight * u
        return (top * (1f - v) + bottom * v).toInt().coerceIn(0, 255)
    }

    private companion object {
        val pageBg: Int = Color.makeRGB(27, 34, 46)
        val surface: Int = Color.makeRGB(255, 255, 255)
        val border: Int = Color.makeRGB(216, 224, 236)
        val ink: Int = Color.makeRGB(39, 49, 66)
        val muted: Int = Color.makeRGB(105, 119, 140)
        val accentBlue: Int = Color.makeRGB(48, 104, 255)
        val accentGreen: Int = Color.makeRGB(41, 178, 123)
        val accentYellow: Int = Color.makeRGB(245, 177, 48)
    }
}
