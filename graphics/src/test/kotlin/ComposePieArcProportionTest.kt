package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathMeasure
import org.jetbrains.skia.Surface
import org.junit.Test
import org.jetbrains.skia.PathVerb
import top.e404.tavolo.draw.compose.DrawCanvas
import top.e404.tavolo.draw.compose.SkiaDrawCanvas
import top.e404.tavolo.draw.compose.charts.*
import kotlin.math.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ComposePieArcProportionTest {
    private val snapshots = listOf(
        listOf(36.8f, 16.87f, 7.03f, 6.87f, 6f, 26.36f),
        listOf(68.85f, 20.56f, 4.23f, 2.01f, 1.63f, 2.74f),
        listOf(92.79f, 5.64f, 1.54f, 0.04f),
        listOf(85.35f, 13.67f, 0.79f, 0.18f, 0.01f),
        listOf(91.87f, 3.49f, 2.49f, 0.85f, 0.63f, 0.67f)
    )

    @Test
    fun solidPieMidlineArcsMatchValues() {
        for (values in snapshots) {
            val slices = values.mapIndexed { i, value -> PieSlice("$i", value) }
            val total = values.sumOf { it.toDouble() }
            for (corner in listOf(0f, 4f, 100f)) for (gap in listOf(0f, 3f, 30f)) {
                val layout = layoutPieSectors(slices, total, 65f, 0f, gap)
                assertEquals(360.0, layout.sweepAngles.sum(), 1e-10)
                var start = 350.0
                val visible = DoubleArray(slices.size)
                for (i in slices.indices) {
                    roundedPieSectorPath(70f, 70f, 65f, 0f, start, layout.sweepAngles[i], corner, layout.sliceGap).use { path ->
                        assertTrue(path.isFinite)
                        visible[i] = visibleAngle(path, 70.0, 70.0, 32.5, start, layout.sweepAngles[i])
                    }
                    start += layout.sweepAngles[i]
                }
                for (i in slices.indices) assertEquals(values[i] / total, visible[i] / visible.sum(), 2e-7,
                    "实心饼图中线弧比例必须匹配：圆角$corner / 间距$gap / 数值${values[i]}")
            }
        }
    }

    @Test
    fun roundingPreservesTheUnroundedInnerArcAllocation() {
        for (values in snapshots) {
            val slices = values.mapIndexed { i, value -> PieSlice("$i", value) }
            val total = values.sumOf { it.toDouble() }
            for (inner in listOf(14f, 36f, 60f)) for (gap in listOf(0f, 3f, 10f)) for (center in listOf(0f, 70f)) {
                val layout = layoutPieSectors(slices, total, 65f, inner, gap)
                assertEquals(360.0, layout.sweepAngles.sum(), 1e-10)
                var start = 350.0
                val actual = DoubleArray(slices.size)
                for (i in slices.indices) {
                    roundedPieSectorPath(center, center, 65f, inner, start, layout.sweepAngles[i], 0f, gap).use { path ->
                        actual[i] = innerMainArcAngle(path, center.toDouble(), center.toDouble(), inner.toDouble())
                        assertTrue(actual[i] > 0.0, "未倒圆角的极小份额仍应保留内圆弧")
                    }
                    for (corner in listOf(4f, 100f)) {
                        roundedPieSectorPath(center, center, 65f, inner, start, layout.sweepAngles[i], corner, gap).use { path ->
                            assertWithinUnroundedSector(path, center.toDouble(), center.toDouble(),
                                inner.toDouble(), 65.0, start, layout.sweepAngles[i], gap.toDouble())
                        }
                    }
                    start += layout.sweepAngles[i]
                }
                val measuredTotal = actual.sum()
                val angleError = 2.0 * Math.ulp(center + inner).toDouble() / inner
                for (i in slices.indices) {
                    val expected = values[i] / total
                    val tolerance = if (center == 0f) 2e-7 else angleError * (1.0 + expected * slices.size) / measuredTotal
                    assertEquals(expected, actual[i] / measuredTotal, tolerance,
                        "未倒圆角的内圆弧比例必须匹配：圆心$center / 内半径$inner / 间距$gap / 数值${values[i]}")
                }
            }
        }
    }

    @Test
    fun everyBoundaryKeepsTheConfiguredGapIncludingTinySharesAndTheSeam() {
        for (values in snapshots) {
            val slices = values.mapIndexed { i, value -> PieSlice("$i", value) }
            val total = values.sumOf { it.toDouble() }
            for (inner in listOf(0f, 14f, 36f, 60f)) for (corner in if (inner == 60f) listOf(0f) else listOf(0f, 4f)) {
                val radius = (65.0 + inner) / 2
                // 保留完整直线的配置测量侧边间距；薄环的大圆角由内弧回归覆盖。
                val layout = layoutPieSectors(slices, total, 65f, inner, 3f)
                var start = 350.0
                val intervals = slices.indices.map { i ->
                    val interval = roundedPieSectorPath(
                        70f, 70f, 65f, inner, start, layout.sweepAngles[i], corner, layout.sliceGap
                    ).use { path -> visibleInterval(path, 70.0, 70.0, radius, start, layout.sweepAngles[i]) }
                    start += layout.sweepAngles[i]
                    interval
                }
                for (i in intervals.indices) {
                    val next = if (i == intervals.lastIndex) intervals.first().first + 2 * PI else intervals[i + 1].first
                    val measuredGap = 2 * radius * sin((next - intervals[i].second) / 2)
                    assertEquals(3.0, measuredGap, 1e-4,
                        "直线侧边的固定间距必须保持配置宽度：内半径$inner / 数值${values[i]}")
                }
            }
        }
    }

    @Test
    fun gapsThatConsumeTheEntireCircumferenceAreRejected() {
        val slices = List(6) { PieSlice("$it", 1f) }
        // 内半径为 35，六处间距达到 35 已占满内圆周的可用角度。
        for (gap in listOf(35f, 50f, 1000f)) {
            assertFailsWith<IllegalArgumentException> {
                layoutPieSectors(slices, 6.0, 65f, 35f, gap)
            }
        }
    }

    @Test
    fun renderedPathsAndLabelsKeepTheirAllocationAcrossCornerOptions() {
        for (values in listOf(listOf(50f, 50f), snapshots.last())) {
            val data = values.mapIndexed { i, value -> PieSlice("Model $i", value, Color.makeRGB(30 + i * 30, 80, 170)) }
            val total = values.sumOf { it.toDouble() }
            val layout = layoutPieSectors(data, total, 65f, 36f, 3f)
            for (corner in listOf(0f, 4f, 100f)) {
                val measured = mutableListOf<Double>()
                val lineStarts = mutableListOf<Pair<Float, Float>>()
                var drawn = 0
                var start = 350.0
                Surface.makeRasterN32Premul(600, 400).use { surface ->
                    val backend = SkiaDrawCanvas(surface.canvas)
                    // 观察原生路径与标签，同时原样交给 Skia 绘制。
                    val canvas = object : DrawCanvas by backend {
                        override fun drawPath(path: Path, paint: Paint) {
                            if (paint.mode == PaintMode.FILL) {
                                val sweep = layout.sweepAngles[drawn]
                                assertWithinUnroundedSector(path, 165.0, 165.0, 36.0, 65.0, start, sweep, 3.0)
                                if (corner == 0f) measured += innerMainArcAngle(path, 165.0, 165.0, 36.0)
                                start += sweep
                                drawn++
                            }
                            backend.drawPath(path, paint)
                        }
                        override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) {
                            lineStarts += x0 to y0
                            backend.drawLine(x0, y0, x1, y1, paint)
                        }
                    }
                    drawPieChart(canvas, 0f, 0f, data, PieChartTheme(
                        width = 600f, height = 400f, radius = 65f, innerRadius = 36f,
                        insets = ChartInsets(100f, 100f, 20f, 20f), startAngle = 350f,
                        cornerRadius = corner, sliceGap = 3f, strokeWidth = 0f,
                        labelPosition = PieLabelPosition.OUTSIDE, minLabelPercent = 0f,
                        legend = ChartLegendTheme(position = ChartLegendPosition.NONE)
                    ))
                }
                assertEquals(values.size, drawn)
                if (corner == 0f) {
                    val tolerance = 4 * Math.ulp(230f).toDouble() / 36 / measured.sum()
                    for (i in values.indices) assertEquals(values[i] / total, measured[i] / measured.sum(), tolerance)
                }
                var angle = 350.0
                val expectedMidpoints = layout.sweepAngles.map { sweep ->
                    val middle = Math.toRadians(angle + sweep / 2)
                    angle += sweep
                    (165 + 65 * cos(middle)) to (165 + 65 * sin(middle))
                }
                val radialStarts = lineStarts.filter { (x, y) -> abs(hypot(x - 165f, y - 165f) - 65f) < 0.01f }
                assertEquals(values.size, radialStarts.size)
                for ((x, y) in expectedMidpoints) {
                    assertTrue(radialStarts.any { hypot(it.first - x, it.second - y) < 0.001 },
                        "圆角开关不能移动扇区及其引线位置：圆角$corner")
                }
            }
        }
    }

    @Test
    fun fullRingKeepsTheEntireReferenceCircle() {
        val data = listOf(PieSlice("Full", 100f))
        for (gap in listOf(0f, 3f, 1000f)) {
            val layout = layoutPieSectors(data, 100.0, 65f, 36f, gap)
            roundedPieSectorPath(70f, 70f, 65f, 36f, 17.0, layout.sweepAngles.single(), 100f, layout.sliceGap).use { path ->
                assertEquals(2 * PI, visibleAngle(path, 70.0, 70.0, 50.5, 17.0, 360.0), 1e-10)
            }
        }
    }

    /** 按端点、控制点及逆时针方向测量内圆主弧，不将顺时针圆角计入弧长。 */
    private fun innerMainArcAngle(path: Path, cx: Double, cy: Double, radius: Double): Double {
        var angle = 0.0
        var count = 0
        for (segment in path) {
            // 极小圆弧的权重舍入为 1 时，Skia 将有理二次曲线保存为普通二次曲线。
            if (segment?.verb != PathVerb.CONIC && segment?.verb != PathVerb.QUAD) continue
            val p = segment.p0!!
            val control = segment.p1!!
            val q = segment.p2!!
            val ax = p.x - cx
            val ay = p.y - cy
            val bx = q.x - cx
            val by = q.y - cy
            // 路径坐标是 Float，按中心点处单 ULP 的量级留出舍入误差。
            if (abs(hypot(ax, ay) - radius) > 0.00005 || abs(hypot(bx, by) - radius) > 0.00005) continue
            val start = atan2(ay, ax)
            val end = atan2(by, bx)
            val delta = atan2(sin(end - start), cos(end - start))
            if (delta >= 0.0) continue
            val controlX = control.x - cx
            val controlY = control.y - cy
            val controlAngle = atan2(controlY, controlX)
            val middle = start + delta / 2
            val centeredControl = abs(atan2(sin(controlAngle - middle), cos(controlAngle - middle))) < 0.00005 &&
                abs(hypot(controlX, controlY) * cos(delta / 2) - radius) < 0.0001
            if (centeredControl) {
                angle -= delta
                count++
            }
        }
        assertTrue(count > 0, "原生路径必须保留内圆主弧：圆心($cx,$cy)，半径$radius")
        return angle
    }

    /** 实际圆角边界只能裁切未倒角扇区，不能扩大角度、偏移圆心或消除整项填充。 */
    private fun assertWithinUnroundedSector(
        path: Path, cx: Double, cy: Double, inner: Double, outer: Double,
        start: Double, sweep: Double, gap: Double
    ) {
        assertTrue(path.isFinite)
        val middle = Math.toRadians(start + sweep / 2)
        val radius = (inner + outer) / 2
        assertTrue(path.contains((cx + radius * cos(middle)).toFloat(), (cy + radius * sin(middle)).toFloat()),
            "圆角应保留扇区中部填充")
        PathMeasure(path).use { measure ->
            repeat(64) { index ->
                val point = measure.getPosition(measure.length * index / 64)!!
                val dx = point.x - cx
                val dy = point.y - cy
                val r = hypot(dx, dy)
                assertTrue(r >= inner - 0.002 && r <= outer + 0.002, "圆角边界必须留在同心环带内")
                val angle = atan2(dy, dx) - middle
                val distance = abs(atan2(sin(angle), cos(angle)))
                val available = Math.toRadians(sweep) / 2 - asin(gap / (2 * r))
                assertTrue(distance <= available + 1e-5, "圆角不得扩张原有扇区角度")
            }
        }
        // 圆角达到半环厚时侧边可退化为切点；接触点还须位于对应径向射线，区分半圆的两端。
        val contacts = path.flatMap { segment ->
            when (segment?.verb) {
                org.jetbrains.skia.PathVerb.LINE -> listOf(segment.p0!! to segment.p1!!)
                org.jetbrains.skia.PathVerb.CONIC, org.jetbrains.skia.PathVerb.QUAD ->
                    listOf(segment.p0!! to segment.p1!!, segment.p2!! to segment.p1!!)
                else -> emptyList()
            }
        }
        for ((angle, direction) in listOf(start to 1.0, (start + sweep) to -1.0)) {
            val radians = Math.toRadians(angle)
            val nx = -sin(radians) * direction
            val ny = cos(radians) * direction
            assertTrue(contacts.any { (p, q) ->
                val tx = q.x.toDouble() - p.x
                val ty = q.y.toDouble() - p.y
                val length = hypot(tx, ty)
                length > 1e-6 &&
                    (p.x - cx) * cos(radians) + (p.y - cy) * sin(radians) > 0 &&
                    abs((p.x - cx) * nx + (p.y - cy) * ny - gap / 2) < 1e-4 &&
                    abs(tx * nx + ty * ny) < length * 1e-4
            }, "圆角后的两侧边界必须保持既定角度与固定间距：$start / $sweep / $gap / $angle")
        }
    }

    private fun visibleAngle(path: Path, cx: Double, cy: Double, radius: Double, start: Double, sweep: Double): Double {
        val interval = visibleInterval(path, cx, cy, radius, start, sweep)
        return interval.second - interval.first
    }

    /** 从实际 Skia 路径的线段坐标求中线交点，不复用布局公式，也不依赖边界包含判定的容差。 */
    private fun visibleInterval(path: Path, cx: Double, cy: Double, radius: Double, start: Double, sweep: Double): Pair<Double, Double> {
        val a = Math.toRadians(start)
        val b = Math.toRadians(start + sweep)
        val middle = (a + b) / 2
        fun contains(angle: Double) = path.contains((cx + radius * cos(angle)).toFloat(), (cy + radius * sin(angle)).toFloat())
        assertTrue(contains(middle), "非零份额在中线中点必须具有填充：$start / $sweep")
        if (sweep == 360.0) {
            repeat(360) { assertTrue(contains(a + Math.toRadians(it.toDouble())), "完整圆环中线不得出现缺口") }
            return a to b
        }
        val intersections = mutableListOf<Double>()
        for (segment in path) {
            if (segment?.verb != PathVerb.LINE) continue
            val p = segment.p0!!
            val q = segment.p1!!
            val x = p.x - cx
            val y = p.y - cy
            val dx = q.x.toDouble() - p.x
            val dy = q.y.toDouble() - p.y
            val aa = dx * dx + dy * dy
            if (aa == 0.0) continue
            val bb = 2 * (x * dx + y * dy)
            val cc = x * x + y * y - radius * radius
            val discriminant = bb * bb - 4 * aa * cc
            if (discriminant < 0.0) continue
            for (sign in listOf(-1, 1)) {
                val t = (-bb + sign * sqrt(discriminant)) / (2 * aa)
                if (t !in 0.0..1.0) continue
                val angle = atan2(y + t * dy, x + t * dx)
                intersections += middle + atan2(sin(angle - middle), cos(angle - middle))
            }
        }
        assertEquals(2, intersections.size, "两侧直线必须穿过中线，圆角不得侵入中线：$start / $sweep")
        return intersections.min() to intersections.max()
    }
}
