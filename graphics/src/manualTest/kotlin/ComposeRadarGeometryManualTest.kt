package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.charts.RadarFixPolicy
import top.e404.tavolo.draw.compose.charts.RadarTheme
import top.e404.tavolo.draw.compose.charts.radar

class ComposeRadarGeometryManualTest {
    private val font = ManualTestSupport.monoFont

    @Test
    fun test_radar_geometry_examples() {
        val base = RadarTheme(
            width = 320f,
            height = 280f,
            radius = 105f,
            gridCount = 5,
            gridFontProvider = { listOf("1", "10", "100", "1k", "10k")[it] },
            gridFontSize = 14f,
            gridFontFamily = font,
            gridFontColor = Color.WHITE,
            labelFontSize = 14f,
            labelFontFamily = font,
            labelFontColor = Color.WHITE,
            labelFixPolicy = RadarFixPolicy.NONE,
            labelOuterLength = 20f,
            fillOutlineColor = Color.makeRGB(86, 185, 255),
            fillColor = Color.makeARGB(100, 86, 185, 255),
            gridLineColor = Color.makeARGB(170, 150, 170, 195)
        )
        val axes = listOf("Commit", "Issue", "PR", "Review", "Repo")

        ManualTestSupport.saveCompose("radar/01-全零") {
            radar(base, axes.map { it to 0f })
        }
        ManualTestSupport.saveCompose("radar/02-全一次") {
            radar(base, axes.map { it to logarithmicRate(1) })
        }
        val mixedCounts = listOf(0, 1, 10, 1_000, 10_000)
        ManualTestSupport.saveCompose("radar/03-混合比例") {
            radar(base, axes.zip(mixedCounts.map(::logarithmicRate)))
        }
    }
    private fun logarithmicRate(count: Int): Float =
        if (count == 0) 0f else ((1f + kotlin.math.log10(count.toFloat())) / 5f).coerceAtMost(1f)
}
