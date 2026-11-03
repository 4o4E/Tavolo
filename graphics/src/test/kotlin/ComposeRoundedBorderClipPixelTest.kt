package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathOp
import org.jetbrains.skia.PathUtils
import org.jetbrains.skia.Surface
import org.junit.Test
import top.e404.tavolo.draw.compose.Box
import top.e404.tavolo.draw.compose.DrawCanvas
import top.e404.tavolo.draw.compose.DrawContext
import top.e404.tavolo.draw.compose.MeasureContext
import top.e404.tavolo.draw.compose.SkiaDrawCanvas
import top.e404.tavolo.draw.compose.ImageOverflow
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.StrokeStyle
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.backgroundImage
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.render
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.rotate
import top.e404.tavolo.util.toBitmap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeRoundedBorderClipPixelTest {
    @Test
    fun matchingRoundedClipKeepsBorderPixelsEqualAtIntegerAndHalfPixelOffsets() {
        for (offset in listOf(0f, .5f)) {
            for (width in listOf(.5f, 1f, 2f, 4f, 6f)) {
                for (radius in listOf(2f, 12f)) {
                    val shape = Shape.RoundedRect(radius)
                    val withoutClip = renderBorder(offset, width, shape, includeClip = false).toBitmap()
                    val withClip = renderBorder(offset, width, shape, includeClip = true).toBitmap()
                    assertEquals(
                        pixels(withoutClip, 48, 48),
                        pixels(withClip, 48, 48),
                        "同形裁切不应削弱 offset=$offset width=$width radius=$radius 的边框覆盖率"
                    )
                }
            }
        }
    }

    @Test
    fun wideBordersStayInsideTheDeclaredSmallRoundedOutline() {
        val shape = Shape.RoundedRect(2f)
        for (offset in listOf(0f, .5f)) shape.createPath(32f, 32f).use { outer ->
            outer.transform(Matrix33.makeTranslate(offset, offset))
            for (width in listOf(4f, 6f)) {
                for (style in listOf(StrokeStyle.Solid, StrokeStyle.Dashed(listOf(6f, 3f)), StrokeStyle.Dotted(1f, 3f))) {
                    val root = Box().apply { modifier = Modifier.size(48f) }
                    root.add(Box().apply {
                        modifier = Modifier.padding(left = offset, top = offset).size(32f)
                            .clip(shape)
                            .border(width, Color.WHITE, style, shape)
                    })
                    val measureContext = MeasureContext()
                    root.measure(measureContext)
                    root.layout(0f, 0f)
                    val actual = Surface.makeRasterN32Premul(48, 48).use { surface ->
                        surface.canvas.clear(Color.TRANSPARENT)
                        val backend = SkiaDrawCanvas(surface.canvas)
                        val canvas = object : DrawCanvas by backend {
                            override fun drawPath(path: Path, paint: Paint) {
                                PathUtils.fillPathWithPaint(path, paint).use { ink ->
                                    val outside = Path.makeCombining(ink, outer, PathOp.DIFFERENCE)
                                        ?: error("无法测量边框外轮廓")
                                    outside.use {
                                        assertTrue(it.isEmpty, "边框不能越过声明的圆角外轮廓：offset=$offset width=$width style=$style")
                                    }
                                }
                                backend.drawPath(path, paint)
                            }
                        }
                        root.draw(DrawContext(canvas, measureContext))
                        surface.makeImageSnapshot().use { it.toBitmap() }
                    }
                    val farCorner = if (offset == 0f) 31 else 32
                    for (x in listOf(0, farCorner)) for (y in listOf(0, farCorner)) {
                        val alpha = Color.getA(actual.getColor(x, y))
                        if (offset == 0f) {
                            assertTrue(alpha < 255, "圆角像素不能退化为不透明方角")
                        } else {
                            assertEquals(0, alpha, "半像素位置的圆角外侧应保持透明")
                        }
                    }
                    if (style == StrokeStyle.Solid) {
                        assertEquals(Color.WHITE, actual.getColor(16, 2), "实线边框应保留完整宽度")
                    } else {
                        val alpha = (4..27).map { Color.getA(actual.getColor(it, 2)) }
                        assertTrue(alpha.any { it > 0 } && alpha.any { it == 0 }, "虚线和点线应同时保留笔画及间隔")
                    }
                }
            }
        }
    }

    @Test
    fun matchingClipStillClipsBackgroundImageAndChildren() {
        val image = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(20f) }
        ) {
            box(
                Modifier.size(20f)
                    .clip(Shape.RoundedRect(7f))
                    .background(Color.RED)
                    .border(1f, Color.WHITE, shape = Shape.RoundedRect(7f))
            )
        }.toBitmap()
        assertEquals(0, Color.getA(image.getColor(0, 0)))
        assertEquals(Color.RED, image.getColor(10, 10))

        val source = Surface.makeRasterN32Premul(4, 4).use { surface ->
            surface.canvas.clear(Color.GREEN)
            surface.makeImageSnapshot()
        }
        val imageBackground = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(20f) }
        ) {
            box(
                Modifier.size(20f)
                    .clip(Shape.RoundedRect(7f))
                    .backgroundImage(source, ImageOverflow.Stretch)
                    .border(1f, Color.WHITE, shape = Shape.RoundedRect(7f))
            )
        }.toBitmap()
        assertEquals(0, Color.getA(imageBackground.getColor(0, 0)))
        assertEquals(Color.GREEN, imageBackground.getColor(10, 10))

        val childClip = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(20f) }
        ) {
            box(
                Modifier.size(20f)
                    .clip(Shape.RoundedRect(7f))
                    .border(1f, Color.WHITE, shape = Shape.RoundedRect(7f))
            ) {
                box(Modifier.size(20f).background(Color.BLUE))
            }
        }.toBitmap()
        assertEquals(0, Color.getA(childClip.getColor(0, 0)))
        assertEquals(Color.BLUE, childClip.getColor(10, 10))
    }

    @Test
    fun ancestorAndDifferentShapeClipsContinueToConstrainBorder() {
        val ancestorClip = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(20f).clip(Shape.Circle) }
        ) {
            box(
                Modifier.size(20f)
                    .clip(Shape.RoundedRect(7f))
                    .border(2f, Color.RED, shape = Shape.RoundedRect(7f))
            )
        }.toBitmap()
        val withoutAncestorClip = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(20f) }
        ) {
            box(
                Modifier.size(20f)
                    .clip(Shape.RoundedRect(7f))
                    .border(2f, Color.RED, shape = Shape.RoundedRect(7f))
            )
        }.toBitmap()
        assertTrue(Color.getA(withoutAncestorClip.getColor(1, 3)) > 0)
        assertEquals(0, Color.getA(ancestorClip.getColor(1, 3)))

        val differentShapeClip = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(20f) }
        ) {
            box(
                Modifier.size(20f)
                    .clip(Shape.Circle)
                    .border(2f, Color.RED, shape = Shape.RoundedRect(7f))
            )
        }.toBitmap()
        val withoutClip = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(20f) }
        ) {
            box(Modifier.size(20f).border(2f, Color.RED, shape = Shape.RoundedRect(7f)))
        }.toBitmap()
        assertTrue(Color.getA(withoutClip.getColor(1, 3)) > 0)
        assertEquals(0, Color.getA(differentShapeClip.getColor(1, 3)))
    }

    @Test
    fun paddingAndPostClipRotationKeepTheirClipConstraints() {
        val paddedClip = renderConstrainedBorder(
            Modifier.size(20f)
                .clip(Shape.Circle)
                .padding(-1f)
                .border(2f, Color.RED, shape = Shape.Circle)
        ).toBitmap()
        val paddedWithoutClip = renderConstrainedBorder(
            Modifier.size(20f)
                .padding(-1f)
                .border(2f, Color.RED, shape = Shape.Circle)
        ).toBitmap()
        assertTrue(
            Color.getA(paddedClip.getColor(0, 6)) < Color.getA(paddedWithoutClip.getColor(0, 6)),
            "padding 改变轮廓后，本地 clip 仍应约束扩展的边框"
        )

        val rotatedClip = renderConstrainedBorder(
            Modifier.size(20f)
                .clip(Shape.RoundedRect(7f))
                .rotate(45f)
                .border(2f, Color.RED, shape = Shape.RoundedRect(7f))
        ).toBitmap()
        val rotatedWithoutClip = renderConstrainedBorder(
            Modifier.size(20f)
                .rotate(45f)
                .border(2f, Color.RED, shape = Shape.RoundedRect(7f))
        ).toBitmap()
        assertTrue(
            pixels(rotatedClip, 20, 20) != pixels(rotatedWithoutClip, 20, 20),
            "clip 后的旋转边框仍应受原 clip 约束"
        )
    }

    private fun renderConstrainedBorder(modifier: Modifier): Image = render(
        backgroundColor = Color.TRANSPARENT,
        root = Box().apply { this.modifier = Modifier.size(20f) }
    ) {
        box(modifier)
    }

    private fun renderBorder(offset: Float, width: Float, shape: Shape, includeClip: Boolean): Image = render(
        backgroundColor = Color.TRANSPARENT,
        root = Box().apply { modifier = Modifier.size(48f) }
    ) {
        var modifier = Modifier.padding(left = offset, top = offset)
            .size(32f)
        if (includeClip) modifier = modifier.clip(shape)
        box(modifier.border(width, Color.WHITE, shape = shape))
    }

    private fun pixels(bitmap: org.jetbrains.skia.Bitmap, width: Int, height: Int): List<Int> = buildList {
        for (y in 0 until height) for (x in 0 until width) add(bitmap.getColor(x, y))
    }
}
