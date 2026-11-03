package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.charts.RadarFixPolicy
import top.e404.tavolo.draw.compose.charts.RadarSizeMode
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
        ManualTestSupport.saveCompose("radar/04-五轴自然布局") {
            radar(base.copy(sizeMode = RadarSizeMode.NATURAL), axes.zip(mixedCounts.map(::logarithmicRate)))
        }
        ManualTestSupport.saveCompose("radar/05-全零自然布局") {
            radar(base.copy(sizeMode = RadarSizeMode.NATURAL), axes.map { it to 0f })
        }

        listOf(
            RadarFixPolicy.NONE,
            RadarFixPolicy.MOVE_OUTSIDE,
            RadarFixPolicy.RATED_FIX,
            RadarFixPolicy.TILT
        ).forEach { policy ->
            val natural = base.copy(
                width = 1f,
                height = 1f,
                radius = 110f,
                labelFontSize = 30f,
                labelFontFamily = ManualTestSupport.uiFont,
                labelOuterLength = 22f,
                labelFixPolicy = policy,
                sizeMode = RadarSizeMode.NATURAL,
                naturalPadding = 18f
            )
            ManualTestSupport.saveCompose("radar/自然布局-${policy.name}") {
                radar(
                    natural,
                    listOf(
                        "北向长标签" to 0.75f,
                        "East" to 0.9f,
                        "south-axis" to 0.3f,
                        "西" to 0.6f,
                        "Five" to 1f,
                        "additional" to 0.45f
                    )
                )
            }
        }
    }
    private fun logarithmicRate(count: Int): Float =
        if (count == 0) 0f else ((1f + kotlin.math.log10(count.toFloat())) / 5f).coerceAtMost(1f)
}
