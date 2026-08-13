package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.Test
import top.e404.tavolo.draw.compose.HorizontalAlignment
import top.e404.tavolo.draw.compose.ImageOverflow
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.TextModifier
import top.e404.tavolo.draw.compose.UiDsl
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.VerticalAlignment
import top.e404.tavolo.draw.compose.antiAlias
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.font
import top.e404.tavolo.draw.compose.image
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.row
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.text

class ComposeImageSamplingManualTest {
    private val font = ManualTestSupport.uiFont

    @Test
    fun test_compose_image_sampling() {
        val smallSource = checkerImage(12, 1)
        val largeSource = checkerImage(240, 3)
        val avatar = ManualTestSupport.drawnAvatar(7, 180, 110)

        ManualTestSupport.saveCompose("图片缩放-01-抗锯齿开关") {
            column(
                Modifier
                    .size(940f, 700f)
                    .background(Color.makeRGB(239, 242, 247))
                    .padding(30f)
            ) {
                text(
                    "图片缩放与圆形裁剪抗锯齿",
                    textModifier = TextModifier.font(30f, Color.BLACK, this@ComposeImageSamplingManualTest.font)
                )
                text(
                    "左侧为默认开启，右侧使用 Modifier.antiAlias(false) 关闭。",
                    modifier = Modifier.padding(top = 8f, bottom = 24f),
                    textModifier = TextModifier.font(
                        17f,
                        Color.makeRGB(80, 88, 104),
                        this@ComposeImageSamplingManualTest.font
                    )
                )
                comparisonRow("12 × 12 放大到 220 × 180", smallSource, 220f, 180f, ImageOverflow.Stretch)
                comparisonRow("240 × 240 缩小到 72 × 72", largeSource, 72f, 72f, ImageOverflow.Stretch)
                row(
                    modifier = Modifier.padding(top = 22f),
                    verticalAlignment = VerticalAlignment.Center
                ) {
                    text("非方形头像 Crop + Circle", modifier = Modifier.size(260f, 30f), textModifier = labelStyle())
                    circleAvatar(avatar, true)
                    circleAvatar(avatar, false)
                }
            }
        }
    }

    @UiDsl
    private fun UiElement.comparisonRow(
        label: String,
        source: Image,
        width: Float,
        height: Float,
        overflow: ImageOverflow
    ) {
        row(
            modifier = Modifier.padding(bottom = 22f),
            verticalAlignment = VerticalAlignment.Center
        ) {
            text(label, modifier = Modifier.size(260f, 30f), textModifier = labelStyle())
            sample(source, width, height, overflow, true)
            sample(source, width, height, overflow, false)
        }
    }

    @UiDsl
    private fun UiElement.sample(
        source: Image,
        width: Float,
        height: Float,
        overflow: ImageOverflow,
        antiAlias: Boolean
    ) {
        column(
            modifier = Modifier.padding(right = 24f),
            horizontalAlignment = HorizontalAlignment.Center
        ) {
            text(
                if (antiAlias) "默认开启" else "关闭",
                modifier = Modifier.padding(bottom = 8f),
                textModifier = TextModifier.font(
                    15f,
                    Color.makeRGB(65, 73, 88),
                    this@ComposeImageSamplingManualTest.font
                )
            )
            image(
                source,
                Modifier
                    .size(width, height)
                    .antiAlias(antiAlias),
                overflow
            )
        }
    }

    @UiDsl
    private fun UiElement.circleAvatar(source: Image, antiAlias: Boolean) {
        column(
            modifier = Modifier.padding(right = 40f),
            horizontalAlignment = HorizontalAlignment.Center
        ) {
            text(
                if (antiAlias) "默认开启" else "关闭",
                modifier = Modifier.padding(bottom = 8f),
                textModifier = TextModifier.font(
                    15f,
                    Color.makeRGB(65, 73, 88),
                    this@ComposeImageSamplingManualTest.font
                )
            )
            image(
                source,
                Modifier
                    .size(132f)
                    .antiAlias(antiAlias)
                    .clip(Shape.Circle),
                ImageOverflow.Crop
            )
        }
    }

    private fun labelStyle() = TextModifier.font(
        18f,
        Color.makeRGB(35, 42, 56),
        this@ComposeImageSamplingManualTest.font
    )

    private fun checkerImage(size: Int, cell: Int): Image = Surface.makeRasterN32Premul(size, size).use { surface ->
        val canvas = surface.canvas
        val paint = Paint().apply { isAntiAlias = false }
        for (y in 0 until size step cell) {
            for (x in 0 until size step cell) {
                paint.color = if ((x / cell + y / cell) % 2 == 0) {
                    Color.makeRGB(28, 38, 58)
                } else {
                    Color.makeRGB(240, 89, 72)
                }
                canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), cell.toFloat(), cell.toFloat()), paint)
            }
        }
        surface.makeImageSnapshot()
    }
}
