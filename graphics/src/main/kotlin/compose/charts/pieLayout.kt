package top.e404.tavolo.draw.compose.charts

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.sin

private const val TAU = 2.0 * PI

/** 扇区总角度包含固定间距；未倒圆角的内圆弧按数值占比分配。 */
internal class PieSectorLayout(
    val sweepAngles: DoubleArray,
    val sliceGap: Float
)

/**
 * 先预留固定间距，再按正值占比分配未倒圆角的内圆弧。
 * 圆角只裁切已分配的扇区，不参与角度分配；实心饼图保留原有中线口径。
 */
internal fun layoutPieSectors(
    slices: List<PieSlice>,
    total: Double,
    outerRadius: Float,
    innerRadius: Float,
    sliceGap: Float
): PieSectorLayout {
    val sweeps = DoubleArray(slices.size)
    val outer = outerRadius.toDouble()
    val inner = innerRadius.toDouble().coerceAtLeast(0.0)
    val count = slices.size
    if (count < 2 || !(outer > inner)) {
        for (index in slices.indices) sweeps[index] = slices[index].value.toDouble() / total * 360.0
        return PieSectorLayout(sweeps, 0f)
    }

    val offset = sliceGap.toDouble() / 2.0
    val referenceRadius = if (inner > 0.0) inner else outer / 2.0
    val cut = if (offset > 0.0) {
        // 固定间距必须给统计弧段留下空间，不能按份额缩小间距。
        require(offset < referenceRadius * sin(PI / count)) {
            "当前半径和扇区数量无法容纳指定间距，请减小间距或增大图表尺寸"
        }
        2.0 * asin(offset / referenceRadius)
    } else 0.0
    val visibleTotal = TAU - count * cut
    for (index in slices.indices) {
        sweeps[index] = (cut + slices[index].value.toDouble() / total * visibleTotal) * 180.0 / PI
    }
    return PieSectorLayout(sweeps, sliceGap)
}
