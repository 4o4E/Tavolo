package top.e404.tavolo.draw.test

import org.jetbrains.skia.*
import org.junit.Test
import top.e404.tavolo.draw.compose.DrawCanvas
import top.e404.tavolo.draw.compose.SkiaDrawCanvas
import top.e404.tavolo.draw.compose.charts.*
import top.e404.tavolo.util.toBitmap
import kotlin.math.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeRoundedDonutTest {
    @Test
    fun fourCornersRemoveSharpTipsAndJoinTangentially() {
        sector(sweep = 180.0, gap = 0f).use { rounded ->
            sector(sweep = 180.0, corner = 0f, gap = 0f).use { sharp ->
                // 半圆两端各有内外两个角；中段侧边仍为直线，不是整段圆头。
                for (x in listOf(-64f, -36.1f, 36.1f, 64f)) {
                    assertTrue(sharp.contains(x, 1f))
                    assertFalse(rounded.contains(x, 1f), "四个尖角都必须被圆角裁去：$x")
                }
                assertTrue(rounded.contains(50f, 0.1f))
                assertTrue(rounded.contains(-50f, 0.1f))
            }
        }
        for (sweep in listOf(90.0, 180.0, 270.0, 359.0)) {
            sector(start = 37.0, sweep = sweep).use { path ->
                val edges = path.mapNotNull { segment ->
                    when (segment?.verb) {
                        PathVerb.LINE -> Edge(segment.p0!!, segment.p1!!, segment.p1!! - segment.p0!!, segment.p1!! - segment.p0!!)
                        PathVerb.CONIC -> Edge(segment.p0!!, segment.p2!!, segment.p1!! - segment.p0!!, segment.p2!! - segment.p1!!)
                        else -> null
                    }
                }.filter { it.startTangent.length() > 0.001f && it.endTangent.length() > 0.001f }
                for (index in edges.indices) {
                    val a = edges[index]
                    val b = edges[(index + 1) % edges.size]
                    assertTrue((a.end - b.start).length() < 0.002f, "边界必须连续")
                    val dot = (a.endTangent.x * b.startTangent.x + a.endTangent.y * b.startTangent.y) /
                        (a.endTangent.length() * b.startTangent.length())
                    assertTrue(dot > 0.999f, "圆角必须与侧边及主圆弧相切：$sweep / $dot")
                }
            }
        }
    }

    @Test
    fun mainArcsRemainConcentricAcrossHalfMajorAndWrappedSectors() {
        for (sweep in listOf(90.0, 180.0, 270.0, 359.0)) {
            for (start in listOf(-90.0, 350.0)) {
                sector(start = start, sweep = sweep).use { path ->
                    for (step in 0..20) {
                        val angle = Math.toRadians(start + 20 + (sweep - 40) * step / 20)
                        fun contains(radius: Double) = path.contains((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
                        assertFalse(contains(35.8))
                        assertTrue(contains(36.2), "内圆必须保持同心：$start / $sweep")
                        assertTrue(contains(64.8), "外圆必须保持同心：$start / $sweep")
                        assertFalse(contains(65.2))
                    }
                }
            }
        }
    }

    @Test
    fun fixedPixelGapHasParallelSidesAndRemainsTransparentWithStroke() {
        sector(sweep = 180.0, gap = 6f).use { path ->
            for (x in listOf(44f, 50f, 56f)) {
                assertFalse(path.contains(x, 2.9f))
                assertTrue(path.contains(x, 3.1f), "内外位置应具有相同的侧边偏移")
            }
        }
        for (background in listOf(Color.TRANSPARENT, Color.WHITE, Color.BLACK)) {
            for (stroke in listOf(0f, 1.5f, 6f)) {
                render(listOf(PieSlice("A", 50f, Color.RED), PieSlice("B", 50f, Color.BLUE)), theme().copy(sliceGap = 6f, strokeWidth = stroke), background).use { bitmap ->
                    for (x in 114..126) {
                        for (y in 68..71) assertEquals(background, bitmap.getColor(x, y), "描边不得填入透明间隔")
                    }
                    assertTrue(Color.getA(bitmap.getColor(120, 76)) > 0)
                    assertEquals(background, bitmap.getColor(70, 70))
                }
            }
        }
    }

    @Test
    fun singleSliceIsACompleteRingWithoutRadialSeam() {
        for (start in listOf(-90f, 17f, 350f)) {
            render(listOf(PieSlice("全部", 100f, Color.RED)), theme().copy(startAngle = start, strokeWidth = 2f, sliceGap = 20f, cornerRadius = 40f)).use { bitmap ->
                for (degree in 0 until 360) {
                    val angle = Math.toRadians(degree.toDouble())
                    val x = (70 + 50 * cos(angle)).roundToInt()
                    val y = (70 + 50 * sin(angle)).roundToInt()
                    assertEquals(Color.RED, bitmap.getColor(x, y), "100% 的环带中段不能有接缝")
                }
                assertEquals(0, Color.getA(bitmap.getColor(70, 70)))
                assertEquals(0, Color.getA(bitmap.getColor(70, 3)))
            }
        }
    }

    @Test
    fun constrainedSectorsStayInsideTheirAngularShareWithoutSelfIntersections() {
        val cases = listOf(
            Case(36f, 4f, 0f, 0.036), Case(36f, 4f, 3f, 0.036, empty = true),
            Case(36f, 4f, 0f, 0.144), Case(36f, 4f, 3f, 0.144, empty = true),
            Case(36f, 100f, 3f, 3.0), Case(36f, 100f, 3f, 8.0),
            Case(64.5f, 100f, 0f, 90.0), Case(64.5f, 100f, 3f, 270.0),
            Case(1f, 100f, 3f, 90.0), Case(0f, 100f, 3f, 270.0),
            Case(36f, 4f, 1000f, 90.0, empty = true), Case(36f, 4f, 3f, 359.964)
        )
        for (case in cases) {
            sector(inner = case.inner, corner = case.corner, gap = case.gap, sweep = case.sweep).use { path ->
                assertTrue(path.isFinite, "受限扇区坐标必须有限：$case")
                assertEquals(case.empty, path.isEmpty, "间距耗尽整个扇区时应为空，其余用例必须保留形状：$case")
                if (case.empty) return@use
                PathMeasure(path).use { measure ->
                    assertTrue(measure.length > 0f, "可容纳的扇区必须具有非退化边界：$case")
                    val points = (0 until 256).map { measure.getPosition(measure.length * it / 256)!! }
                    for (point in points) {
                        val radius = point.length()
                        assertTrue(radius >= case.inner - 0.003f && radius <= 65.003f, "边界必须留在环带内：$case / $point")
                        if (radius > 0.01f) {
                            val angle = (Math.toDegrees(atan2(point.y.toDouble(), point.x.toDouble())) + 360) % 360
                            assertTrue(angle <= case.sweep + 0.003 || angle >= 359.997, "路径不得越出已分配角度：$case / $angle")
                        }
                    }
                    for (i in points.indices) {
                        for (j in i + 2 until points.size) {
                            if (i == 0 && j == points.lastIndex) continue
                            assertFalse(crosses(points[i], points[(i + 1) % points.size], points[j], points[(j + 1) % points.size]), "边界不得自交：$case")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun tinySlicesKeepOriginalLegendAndLabelValues() {
        val slices = listOf(PieSlice("Coding", 99.95f), PieSlice("Unknown OS", 0.04f), PieSlice("Building", 0.01f))
        val labelValues = mutableListOf<Pair<String, Float>>()
        val legendValues = mutableListOf<Pair<String, Float>>()
        fun display(slice: PieSlice, fraction: Float, total: Float): String {
            assertEquals(100f, total)
            assertEquals(slice.value / total, fraction)
            return "${slice.label} ${slice.value}%"
        }
        render(slices, theme().copy(
            showLabels = true, minLabelPercent = 0f, legend = ChartLegendTheme(),
            labelFormatter = { slice, fraction, total ->
                labelValues += slice.label to slice.value
                display(slice, fraction, total)
            },
            legendLabelFormatter = { slice, fraction, total ->
                legendValues += slice.label to slice.value
                display(slice, fraction, total)
            }
        )).close()
        val expected = slices.map { it.label to it.value }
        assertEquals(expected, labelValues)
        assertEquals(expected, legendValues)
    }

    @Test
    fun allAiModelSharesProduceVisiblePixelsAtActualSize() {
        val values = listOf(91.87f, 3.49f, 2.49f, 0.85f, 0.63f, 0.67f)
        val colors = listOf(Color.BLUE, Color.GREEN, Color.YELLOW, Color.RED, Color.MAGENTA, Color.CYAN)
        val data = values.mapIndexed { index, value -> PieSlice("Model $index", value, colors[index]) }
        for (corner in listOf(0f, 4f)) for (gap in listOf(0f, 3f)) {
            render(data, theme().copy(startAngle = -90f, cornerRadius = corner, sliceGap = gap)).use { bitmap ->
                for (expected in colors) {
                    val visible = (0 until 140).any { x ->
                        (0 until 140).any { y ->
                            val actual = bitmap.getColor(x, y)
                            Color.getA(actual) >= 128 &&
                                abs(Color.getR(expected) - Color.getR(actual)) <= 2 &&
                                abs(Color.getG(expected) - Color.getG(actual)) <= 2 &&
                                abs(Color.getB(expected) - Color.getB(actual)) <= 2
                        }
                    }
                    assertTrue(visible, "140×140 中每个 AI Models 份额都应有可见填充：圆角$corner / 间距$gap / 颜色$expected")
                }
            }
        }
    }

    @Test
    fun cornerAndGapSwitchesIndependentlyControlRenderedBoundaries() {
        val data = listOf(PieSlice("A", 50f, Color.RED), PieSlice("B", 50f, Color.BLUE))
        for (corner in listOf(0f, 4f)) for (gap in listOf(0f, 3f)) {
            for (stroke in listOf(0f, 1.5f)) {
                render(data, theme().copy(cornerRadius = corner, sliceGap = gap, strokeWidth = stroke)).use { bitmap ->
                    assertEquals(Color.TRANSPARENT, bitmap.getColor(70, 70), "圆心必须保持透明")
                    assertEquals(Color.TRANSPARENT, bitmap.getColor(0, 0), "圆环外部必须保持透明")
                    assertEquals(Color.RED, bitmap.getColor(120, 90), "半圆内部保留调用方填充色")
                    assertEquals(Color.BLUE, bitmap.getColor(120, 50))
                    val sideAlpha = Color.getA(bitmap.getColor(120, 70))
                    if (gap == 0f) {
                        assertTrue(sideAlpha > 240, "关闭间距时相邻侧边必须接合")
                    } else {
                        assertEquals(0, sideAlpha, "开启间距时侧边之间必须透明")
                    }
                    // 在侧边与外圆相交的尖角处取样，区分圆角开关，不受环厚变化影响。
                    val tipAlpha = Color.getA(bitmap.getColor(134, 70 + ceil(gap / 2).toInt()))
                    if (corner == 0f) {
                        assertTrue(tipAlpha > 128, "关闭圆角时保留尖角：间距$gap / 描边$stroke")
                    } else {
                        assertEquals(0, tipAlpha, "开启圆角时裁去尖角：间距$gap / 描边$stroke")
                    }
                }
            }
        }
    }

    @Test
    fun zeroValuesAndEmptyInputPreserveTransparentOutput() {
        for (data in listOf(emptyList(), listOf(PieSlice("零", 0f), PieSlice("负", -1f)))) {
            render(data, theme()).use { bitmap ->
                for (x in 0 until 140) for (y in 0 until 140) assertEquals(0, Color.getA(bitmap.getColor(x, y)))
            }
        }
        sector(inner = 65f).use { assertTrue(it.isEmpty) }
        sector(inner = 70f).use { assertTrue(it.isEmpty) }
    }

    @Test
    fun unroundedPieAndDonutKeepTheirFilledSharesAndHole() {
        for (inner in listOf(0f, 36f)) {
            val legacy = PieChartTheme(140f, 140f, 65f, innerRadius = inner,
                insets = ChartInsets(5f, 5f, 5f, 5f), strokeWidth = 0f, startAngle = 0f,
                showLabels = false, legend = ChartLegendTheme(position = ChartLegendPosition.NONE))
            render(listOf(PieSlice("A", 75f, Color.RED), PieSlice("B", 25f, Color.BLUE)), legacy).use { bitmap ->
                assertEquals(Color.RED, bitmap.getColor(110, 90))
                assertEquals(Color.RED, bitmap.getColor(30, 50))
                assertEquals(Color.BLUE, bitmap.getColor(110, 50))
                assertEquals(if (inner == 0f) Color.RED else Color.TRANSPARENT, bitmap.getColor(70, 75))
                assertEquals(Color.TRANSPARENT, bitmap.getColor(0, 0))
            }
        }
    }

    @Test
    fun smallSharesUseTheLargestInnerAndOuterCornersThatFit() {
        // 默认无装饰绘制由兼容性回归覆盖；这里测量启用装饰后的真实闭合路径。
        val styles = listOf(0f to 3f, 4f to 0f, 4f to 3f)
        for (share in listOf(20f, 2f, 0.63f, 0.04f, 0.01f)) for ((corner, gap) in styles) {
            val data = listOf(PieSlice("主要", 100f - share), PieSlice("小份额", share))
            val measured = mutableListOf<List<CornerCircle>>()
            Surface.makeRasterN32Premul(140, 140).use { surface ->
                val backend = SkiaDrawCanvas(surface.canvas)
                val canvas = object : DrawCanvas by backend {
                    override fun drawPath(path: Path, paint: Paint) {
                        if (paint.mode == PaintMode.FILL) measured += cornerCircles(path, 70.0, 70.0, 36.0, 65.0)
                        backend.drawPath(path, paint)
                    }
                }
                drawPieChart(canvas, 0f, 0f,
                    data,
                    theme().copy(startAngle = 350f, cornerRadius = corner, sliceGap = gap))
            }
            assertEquals(2, measured.size)
            val circles = measured[1]
            if (corner == 0f) {
                assertTrue(circles.isEmpty(), "关闭圆角时不应存在内外圆角")
            } else {
                val halfGap = gap / 2.0
                val gapAngle = 2 * asin(halfGap / 36)
                val proportion = share / data.sumOf { it.value.toDouble() }
                val halfAngle = (gapAngle + proportion * (2 * PI - 2 * gapAngle)) / 2
                // 由固定侧边与内外圆的相切关系独立求出上限，不增加统计扇区的角度。
                val innerMaximum = min(corner.toDouble(), (36 * sin(halfAngle) - halfGap) / (1 - sin(halfAngle)))
                val outerMaximum = min(corner.toDouble(), (65 * sin(halfAngle) - halfGap) / (1 + sin(halfAngle)))
                val innerCircles = circles.filter { hypot(it.x - 70, it.y - 70) < 50.5 }
                val outerCircles = circles.filter { hypot(it.x - 70, it.y - 70) > 50.5 }
                for ((actual, maximum) in listOf(innerCircles to innerMaximum, outerCircles to outerMaximum)) {
                    assertEquals(if (maximum < corner - 1e-6) 1 else 2, actual.size,
                        "空间不足时两圆角应相接，空间足够时保留两个独立圆角：$share / $gap / $maximum")
                    actual.forEach { assertEquals(maximum, it.radius, 0.001,
                        "内外圆角必须各自使用允许的最大半径：$share / $gap") }
                }
            }
        }
    }

    @Test
    fun oversizedCornersReachHalfThicknessOnThinAndThickRings() {
        for (inner in listOf(1f, 14f, 60f, 64.5f)) {
            sector(inner = inner, sweep = 180.0, corner = 100f, gap = 0f).use { path ->
                val circles = cornerCircles(path, 0.0, 0.0, inner.toDouble(), 65.0)
                // 半环厚时，每一侧的内外圆角共享圆心，形成一个完整相切圆头。
                assertEquals(2, circles.size, "半圆两侧均应达到环厚允许的最大圆角：$inner")
                for (circle in circles) {
                    assertEquals((65.0 - inner) / 2, circle.radius, 0.001, "圆角半径应达到半环厚")
                    assertEquals((65.0 + inner) / 2, hypot(circle.x, circle.y), 0.001,
                        "最大圆角必须同时与内外圆相切")
                }
            }
        }
    }

    /** 从原生圆弧两端的法线交点测量圆心、半径，识别与内圆或外圆相切的圆角。 */
    private fun cornerCircles(path: Path, cx: Double, cy: Double, innerRadius: Double, outerRadius: Double): List<CornerCircle> {
        val circles = mutableListOf<CornerCircle>()
        for (segment in path) {
            if (segment?.verb != PathVerb.CONIC) continue
            val p = segment.p0!!
            val control = segment.p1!!
            val q = segment.p2!!
            val dx0 = control.x.toDouble() - p.x
            val dy0 = control.y.toDouble() - p.y
            val dx1 = q.x.toDouble() - control.x
            val dy1 = q.y.toDouble() - control.y
            val determinant = dx0 * dy1 - dy0 * dx1
            if (determinant == 0.0) continue
            val offset = ((q.x.toDouble() - p.x) * dx1 + (q.y.toDouble() - p.y) * dy1) / determinant
            val circle = CornerCircle(p.x - dy0 * offset, p.y + dx0 * offset, hypot(dx0, dy0) * abs(offset))
            val distance = hypot(circle.x - cx, circle.y - cy)
            val touchesOuter = abs(distance + circle.radius - outerRadius) < 0.001
            val touchesInner = abs(distance - circle.radius - innerRadius) < 0.001
            if (circle.radius >= outerRadius / 2 || !(touchesOuter || touchesInner)) continue
            if (circles.none { hypot(it.x - circle.x, it.y - circle.y) < 0.001 }) circles += circle
        }
        return circles
    }

    private data class CornerCircle(val x: Double, val y: Double, val radius: Double)

    private fun theme() = PieChartTheme(
        width = 140f, height = 140f, radius = 65f, innerRadius = 36f,
        insets = ChartInsets(5f, 5f, 5f, 5f), startAngle = 0f, strokeWidth = 0f,
        showLabels = false, legend = ChartLegendTheme(position = ChartLegendPosition.NONE),
        cornerRadius = 4f, sliceGap = 3f
    )

    private fun sector(inner: Float = 36f, start: Double = 0.0, sweep: Double = 90.0, corner: Float = 4f, gap: Float = 3f) =
        roundedPieSectorPath(0f, 0f, 65f, inner, start, sweep, corner, gap)

    private fun render(data: List<PieSlice>, theme: PieChartTheme, background: Int = Color.TRANSPARENT): Bitmap =
        Surface.makeRasterN32Premul(140, 140).use { surface ->
            surface.canvas.clear(background)
            drawPieChart(SkiaDrawCanvas(surface.canvas), 0f, 0f, data, theme)
            surface.makeImageSnapshot().use { it.toBitmap() }
        }

    private data class Case(val inner: Float, val corner: Float, val gap: Float, val sweep: Double, val empty: Boolean = false)
    private data class Edge(val start: Point, val end: Point, val startTangent: Point, val endTangent: Point)
    private operator fun Point.minus(other: Point) = Point(x - other.x, y - other.y)
    private fun Point.length() = hypot(x, y)

    private fun crosses(a: Point, b: Point, c: Point, d: Point): Boolean {
        fun side(p: Point, q: Point, r: Point) = (q.x - p.x).toDouble() * (r.y - p.y) - (q.y - p.y).toDouble() * (r.x - p.x)
        return side(a, b, c) * side(a, b, d) < -1e-10 && side(c, d, a) * side(c, d, b) < -1e-10
    }
}
