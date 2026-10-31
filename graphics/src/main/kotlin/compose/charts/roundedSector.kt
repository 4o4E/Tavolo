/*
 * Copyright 2010-2022 Mike Bostock
 *
 * Adapted from d3-shape's arc implementation:
 * https://raw.githubusercontent.com/d3/d3-shape/main/src/arc.js
 *
 * Permission to use, copy, modify, and/or distribute this software for any purpose
 * with or without fee is hereby granted, provided that the above copyright notice
 * and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 * INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS
 * OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */

package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.Path
import org.jetbrains.skia.PathDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val SECTOR_EPSILON = 1e-10
private const val TAU = 2.0 * PI

/**
 * 构造带圆角和径向间隔的饼图扇区路径。角度以度为单位，正方向为屏幕坐标系顺时针。
 * 返回由调用方持有的闭合路径；保持布局角度，内外圆角分别取各自空间允许的最大半径。
 * 窄扇区允许同侧圆角相接；实心饼图保留路径内部的中线圆角上限。
 */
internal fun roundedPieSectorPath(
    centerX: Float,
    centerY: Float,
    outerRadius: Float,
    innerRadius: Float,
    startAngle: Double,
    sweepAngle: Double,
    cornerRadius: Float,
    sliceGap: Float
): Path {
    val path = Path()
    val r0 = innerRadius.toDouble().coerceAtLeast(0.0)
    val r1 = outerRadius.toDouble()
    if (!(r1 > r0) || !(r1 > 0.0) || sweepAngle == 0.0) return path

    val start = startAngle * PI / 180.0
    val sweep = sweepAngle * PI / 180.0
    val direction = if (sweep > 0.0) 1.0 else -1.0
    val da = abs(sweep)
    val end = start + sweep

    // 完整圆环用反向绕行的两个圆构造，避免起终点重合时被解释为整圈弧。
    if (da >= TAU - SECTOR_EPSILON) {
        path.addCircle(centerX, centerY, r1.toFloat(), PathDirection.CLOCKWISE)
        if (r0 > 0.0) path.addCircle(centerX, centerY, r0.toFloat(), PathDirection.COUNTER_CLOCKWISE)
        return path
    }

    val halfGap = max(0.0, sliceGap.toDouble()) / 2.0
    val innerOffset = if (r0 > 0.0 && halfGap > 0.0) asin((halfGap / r0).coerceAtMost(1.0)) else 0.0
    val outerOffset = if (halfGap > 0.0) asin((halfGap / r1).coerceAtMost(1.0)) else 0.0
    var a00 = start
    var a10 = end
    var a01 = start
    var a11 = end
    var da0 = if (halfGap > 0.0 && halfGap >= r0) 0.0 else da - 2.0 * innerOffset
    val da1 = if (halfGap >= r1) 0.0 else da - 2.0 * outerOffset

    if (da0 > SECTOR_EPSILON) {
        a00 += direction * innerOffset
        a10 -= direction * innerOffset
    } else {
        da0 = 0.0
        a00 = start + sweep / 2.0
        a10 = a00
    }
    if (da1 > SECTOR_EPSILON) {
        a01 += direction * outerOffset
        a11 -= direction * outerOffset
    } else {
        // 外弧也没有剩余角度时不输出退化线段，避免描边制造不存在的份额。
        return path
    }
    // 无内圆弧的原始扇区由偏移侧边交点闭合；正常布局为内圆弧预留正值角度。
    val innerBoundaryRadius = if (halfGap > 0.0 && da0 == 0.0) halfGap / sin(da / 2.0) else r0
    val x01 = r1 * cos(a01)
    val y01 = r1 * sin(a01)
    val x10 = innerBoundaryRadius * cos(a10)
    val y10 = innerBoundaryRadius * sin(a10)
    // 外圆角与侧边的切点半径满足 tangentRadius² = r1² - 2 * cornerRadius * (r1 + halfGap)。
    // 实心饼图保持中线切点限制；环形图在既定扇区内计算相切圆角。
    val pathCornerLimit = if (r0 > 0.0) {
        Double.POSITIVE_INFINITY
    } else {
        val coordinateUlp = Math.ulp(max(abs(centerX), abs(centerY)) + outerRadius).toDouble()
        val middleRadius = min(r1, (r0 + r1) / 2.0 + coordinateUlp * 8.0)
        (r1 * r1 - middleRadius * middleRadius) / (2.0 * (r1 + halfGap))
    }
    val thicknessLimit = (r1 - r0) / 2.0
    val innerCorner = min(thicknessLimit, max(0.0, cornerRadius.toDouble()))
    val outerCorner = min(innerCorner, pathCornerLimit)
    var rc0 = innerCorner
    var rc1 = outerCorner

    if (max(innerCorner, outerCorner) > SECTOR_EPSILON) {
        val x11 = r1 * cos(a11)
        val y11 = r1 * sin(a11)
        val x00 = innerBoundaryRadius * cos(a00)
        val y00 = innerBoundaryRadius * sin(a00)
        // 半圆侧边平行，无须角度限幅；不能让弧度换算的舍入误差进入交点退化分支。
        if (da < PI - SECTOR_EPSILON) {
            val intersection = intersect(x01, y01, x00, y00, x11, y11, x10, y10)
            if (intersection != null) {
                val ax = x01 - intersection.first
                val ay = y01 - intersection.second
                val bx = x11 - intersection.first
                val by = y11 - intersection.second
                val denominator = sqrt((ax * ax + ay * ay) * (bx * bx + by * by))
                val cosine = if (denominator == 0.0) 1.0 else ((ax * bx + ay * by) / denominator).coerceIn(-1.0, 1.0)
                val kc = 1.0 / sin(acos(cosine) / 2.0)
                val lc = sqrt(intersection.first * intersection.first + intersection.second * intersection.second)
                rc0 = min(innerCorner, max(0.0, (r0 - lc) / (kc - 1.0)))
                rc1 = min(outerCorner, max(0.0, (r1 - lc) / (kc + 1.0)))
            } else {
                rc0 = 0.0
                rc1 = 0.0
            }
        }
    }
    if (rc1 > SECTOR_EPSILON) {
        val x11 = r1 * cos(a11)
        val y11 = r1 * sin(a11)
        val x00 = innerBoundaryRadius * cos(a00)
        val y00 = innerBoundaryRadius * sin(a00)
        val t0 = cornerTangents(x00, y00, x01, y01, r1, rc1, direction)
        val t1 = cornerTangents(x11, y11, x10, y10, r1, rc1, direction)
        path.moveTo((centerX + t0.cx + t0.x01).toFloat(), (centerY + t0.cy + t0.y01).toFloat())
        if (rc1 < outerCorner - SECTOR_EPSILON) {
            appendCornerArc(path, centerX, centerY, t0.cx, t0.cy, t0.x01, t0.y01, t1.x01, t1.y01, direction)
        } else {
            appendCornerArc(path, centerX, centerY, t0.cx, t0.cy, t0.x01, t0.y01, t0.x11, t0.y11, direction)
            appendArc(path, centerX, centerY, r1, atan2(t0.cy + t0.y11, t0.cx + t0.x11), atan2(t1.cy + t1.y11, t1.cx + t1.x11), direction)
            appendCornerArc(path, centerX, centerY, t1.cx, t1.cy, t1.x11, t1.y11, t1.x01, t1.y01, direction)
        }
    } else {
        path.moveTo((centerX + x01).toFloat(), (centerY + y01).toFloat())
        appendArc(path, centerX, centerY, r1, a01, a11, direction)
    }

    if (!(r0 > SECTOR_EPSILON) || !(da0 > SECTOR_EPSILON)) {
        path.lineTo((centerX + x10).toFloat(), (centerY + y10).toFloat())
    } else if (rc0 > SECTOR_EPSILON) {
        val x11 = r1 * cos(a11)
        val y11 = r1 * sin(a11)
        val x00 = innerBoundaryRadius * cos(a00)
        val y00 = innerBoundaryRadius * sin(a00)
        val t0 = cornerTangents(x10, y10, x11, y11, r0, -rc0, direction)
        val t1 = cornerTangents(x01, y01, x00, y00, r0, -rc0, direction)
        path.lineTo((centerX + t0.cx + t0.x01).toFloat(), (centerY + t0.cy + t0.y01).toFloat())
        if (rc0 < innerCorner - SECTOR_EPSILON) {
            appendCornerArc(path, centerX, centerY, t0.cx, t0.cy, t0.x01, t0.y01, t1.x01, t1.y01, direction)
        } else {
            appendCornerArc(path, centerX, centerY, t0.cx, t0.cy, t0.x01, t0.y01, t0.x11, t0.y11, direction)
            appendArc(path, centerX, centerY, r0, atan2(t0.cy + t0.y11, t0.cx + t0.x11), atan2(t1.cy + t1.y11, t1.cx + t1.x11), -direction)
            appendCornerArc(path, centerX, centerY, t1.cx, t1.cy, t1.x11, t1.y11, t1.x01, t1.y01, direction)
        }
    } else {
        appendArc(path, centerX, centerY, r0, a10, a00, -direction)
    }
    path.closePath()
    return path
}

private data class SectorTangent(
    val cx: Double, val cy: Double,
    val x01: Double, val y01: Double,
    val x11: Double, val y11: Double
)

private fun cornerTangents(x0: Double, y0: Double, x1: Double, y1: Double, radius: Double, corner: Double, direction: Double): SectorTangent {
    val x01 = x0 - x1
    val y01 = y0 - y1
    val lo = direction * corner / sqrt(x01 * x01 + y01 * y01)
    val ox = lo * y01
    val oy = -lo * x01
    val x11 = x0 + ox
    val y11 = y0 + oy
    val x10 = x1 + ox
    val y10 = y1 + oy
    val x00 = (x11 + x10) / 2.0
    val y00 = (y11 + y10) / 2.0
    val dx = x10 - x11
    val dy = y10 - y11
    val d2 = dx * dx + dy * dy
    val r = radius - corner
    val determinant = x11 * y10 - x10 * y11
    val d = (if (dy < 0.0) -1.0 else 1.0) * sqrt(max(0.0, r * r * d2 - determinant * determinant))
    var cx = (determinant * dy - dx * d) / d2
    var cy = (-determinant * dx - dy * d) / d2
    val cx1 = (determinant * dy + dx * d) / d2
    val cy1 = (-determinant * dx + dy * d) / d2
    if ((cx - x00) * (cx - x00) + (cy - y00) * (cy - y00) > (cx1 - x00) * (cx1 - x00) + (cy1 - y00) * (cy1 - y00)) {
        cx = cx1
        cy = cy1
    }
    val factor = radius / r - 1.0
    return SectorTangent(cx, cy, -ox, -oy, cx * factor, cy * factor)
}

private fun intersect(x0: Double, y0: Double, x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): Pair<Double, Double>? {
    val x10 = x1 - x0
    val y10 = y1 - y0
    val x32 = x3 - x2
    val y32 = y3 - y2
    var determinant = y32 * x10 - x32 * y10
    if (determinant * determinant < SECTOR_EPSILON) return null
    determinant = (x32 * (y0 - y2) - y32 * (x0 - x2)) / determinant
    return (x0 + determinant * x10) to (y0 + determinant * y10)
}

private fun appendCornerArc(path: Path, centerX: Float, centerY: Float, cx: Double, cy: Double, sx: Double, sy: Double, ex: Double, ey: Double, direction: Double) {
    appendArc(path, (centerX + cx).toDouble(), (centerY + cy).toDouble(), sqrt(sx * sx + sy * sy), atan2(sy, sx), atan2(ey, ex), direction)
}

private fun appendArc(path: Path, centerX: Float, centerY: Float, radius: Double, start: Double, end: Double, direction: Double) {
    appendArc(path, centerX.toDouble(), centerY.toDouble(), radius, start, end, direction)
}

private fun appendArc(path: Path, centerX: Double, centerY: Double, radius: Double, start: Double, end: Double, direction: Double) {
    if (!(radius > 0.0)) return
    var delta = if (direction > 0.0) (end - start) % TAU else (start - end) % TAU
    if (delta < 0.0) delta += TAU
    if (delta <= SECTOR_EPSILON || TAU - delta <= SECTOR_EPSILON) return
    // 圆弧用每段不超过90°的有理二次曲线精确表示，极小弧段也显式保留起终点。
    // 所有三角计算使用 Double，仅在提交 Skia 控制点时转换为 Float。
    val normalizedStart = Math.IEEEremainder(start, TAU)
    val segments = ceil(delta / (PI / 2.0)).toInt()
    val step = direction * delta / segments
    val weight = cos(step / 2.0)
    val controlRadius = radius / weight
    path.lineTo(
        (centerX + radius * cos(normalizedStart)).toFloat(),
        (centerY + radius * sin(normalizedStart)).toFloat()
    )
    for (index in 1..segments) {
        val next = normalizedStart + step * index
        val middle = next - step / 2.0
        path.conicTo(
            (centerX + controlRadius * cos(middle)).toFloat(),
            (centerY + controlRadius * sin(middle)).toFloat(),
            (centerX + radius * cos(next)).toFloat(),
            (centerY + radius * sin(next)).toFloat(),
            weight.toFloat()
        )
    }
}
