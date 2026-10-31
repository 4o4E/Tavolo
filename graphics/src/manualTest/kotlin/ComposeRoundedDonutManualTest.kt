package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.UiDsl
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.VerticalAlignment
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.font
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.row
import top.e404.tavolo.draw.compose.charts.ChartInsets
import top.e404.tavolo.draw.compose.charts.ChartLegendPosition
import top.e404.tavolo.draw.compose.charts.ChartLegendTheme
import top.e404.tavolo.draw.compose.charts.ChartPalette
import top.e404.tavolo.draw.compose.charts.ChartTextStyle
import top.e404.tavolo.draw.compose.charts.PieChartTheme
import top.e404.tavolo.draw.compose.charts.PieSlice
import top.e404.tavolo.draw.compose.charts.donutChart
import top.e404.tavolo.draw.compose.text

class ComposeRoundedDonutManualTest {
    private val uiFont = ManualTestSupport.uiFont
    private val darkBg = Color.makeRGB(13, 17, 23)
    private val lightBg = Color.makeRGB(248, 250, 253)
    private val ink = Color.makeRGB(232, 237, 245)
    private val palette = ChartPalette(listOf(
        Color.makeRGB(75, 132, 235), Color.makeRGB(63, 183, 139), Color.makeRGB(244, 179, 67),
        Color.makeRGB(234, 99, 111), Color.makeRGB(155, 116, 230), Color.makeRGB(52, 184, 205),
        Color.makeRGB(226, 113, 173), Color.makeRGB(136, 149, 170)
    ))

    private val snapshots = listOf(
        Snapshot("Languages", listOf("Kotlin" to 36.8f, "Java" to 16.87f, "YAML" to 7.03f, "Vue.js" to 6.87f, "Markdown" to 6f, "Others" to 26.36f)),
        Snapshot("Editors", listOf("IntelliJ IDEA" to 68.85f, "VS Code" to 20.56f, "Codex Vscode" to 4.23f, "WebStorm" to 2.01f, "Android Studio" to 1.63f, "Others" to 2.74f)),
        Snapshot("Operating Systems", listOf("Windows" to 92.79f, "Linux" to 5.64f, "Mac" to 1.54f, "Unknown OS" to 0.04f)),
        Snapshot("Categories", listOf("Coding" to 85.35f, "AI Coding" to 13.67f, "Writing Docs" to 0.79f, "Writing Tests" to 0.18f, "Building" to 0.01f)),
        Snapshot("AI Models", listOf("GPT" to 91.87f, "Claude" to 3.49f, "OMP" to 2.49f, "Codex-Vscode" to 0.85f, "Sonnet" to 0.63f, "Others" to 0.67f))
    )

    @Test
    fun test_compose_rounded_donut_snapshots() {
        saveSnapshotSheet("圆角Donut-01-原始快照-浅色", lightBg)
        saveSnapshotSheet("圆角Donut-02-原始快照-深色", darkBg)
        saveSnapshotSheet("圆角Donut-03-原始快照-透明背景", Color.TRANSPARENT)
        saveSnapshotSheet("圆角Donut-04-四倍几何放大", darkBg, 4f)
        saveBoundarySheet()
    }

    @Test
    fun test_compose_rounded_donut_styles() {
        val styles = listOf(
            DonutStyle("A · 圆角关闭 · 间距关闭", 0f, 0f),
            DonutStyle("B · 圆角开启 · 间距关闭", 4f, 0f),
            DonutStyle("C · 圆角关闭 · 间距开启", 0f, 3f),
            DonutStyle("D · 圆角开启 · 间距开启", 4f, 3f)
        )
        ManualTestSupport.saveCompose("圆角Donut-06-样式选择") {
            column(Modifier.background(darkBg).padding(20f)) {
                styles.forEach { style ->
                    text(style.name, textModifier = top.e404.tavolo.draw.compose.TextModifier.font(20f, ink, uiFont))
                    row(verticalAlignment = VerticalAlignment.Top) {
                        snapshots.forEach { snapshot ->
                            column(Modifier.padding(12f)) {
                                text(snapshot.name, textModifier = top.e404.tavolo.draw.compose.TextModifier.font(13f, ink, uiFont))
                                donutChart(styleTheme(style).copy(
                                    width = 140f, height = 140f,
                                    legend = ChartLegendTheme(position = ChartLegendPosition.NONE)
                                ), snapshot.slices.map { PieSlice(it.first, it.second) })
                            }
                        }
                    }
                }
            }
        }
        val ai = snapshots.last()
        ManualTestSupport.saveCompose("圆角Donut-07-AI-Models-样式放大") {
            column(Modifier.background(darkBg).padding(20f)) {
                styles.chunked(2).forEach { group ->
                    row(verticalAlignment = VerticalAlignment.Top) {
                        group.forEach { style ->
                            column(Modifier.padding(12f)) {
                                text(style.name, textModifier = top.e404.tavolo.draw.compose.TextModifier.font(20f, ink, uiFont))
                                donutChart(styleTheme(style, 4f), ai.slices.map { PieSlice(it.first, it.second) })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun styleTheme(style: DonutStyle, scale: Float = 1f) = theme(scale).copy(
        cornerRadius = style.corner * scale, sliceGap = style.gap * scale
    )

    private data class DonutStyle(val name: String, val corner: Float, val gap: Float)

    private fun saveSnapshotSheet(name: String, bg: Int, scale: Float = 1f) {
        val fg = if (bg == darkBg) ink else Color.makeRGB(45, 55, 70)
        ManualTestSupport.saveCompose(name) {
            column(Modifier.background(bg).padding(20f)) {
                text("WakaTime", textModifier = top.e404.tavolo.draw.compose.TextModifier.font(24f, fg, uiFont))
                snapshots.chunked(2).forEach { pair ->
                    row(verticalAlignment = VerticalAlignment.Top) {
                        pair.forEach { item -> snapshotPanel(item, fg, scale) }
                    }
                }
            }
        }
    }

    @UiDsl
    private fun UiElement.snapshotPanel(snapshot: Snapshot, fg: Int, scale: Float) {
        column(Modifier.padding(12f)) {
            text(snapshot.name, textModifier = top.e404.tavolo.draw.compose.TextModifier.font(17f, fg, uiFont))
            donutChart(theme(scale, fg), snapshot.slices.map { PieSlice(it.first, it.second) })
        }
    }

    private fun saveBoundarySheet() {
        val thirds = listOf("A" to 45f, "B" to 35f, "C" to 20f)
        val base = theme()
        val cases = listOf(
            Boundary("100%", listOf("Full" to 100f), base),
            Boundary("Half", listOf("Half" to 50f, "Rest" to 50f), base),
            Boundary("> half", listOf("Major" to 62f, "Rest" to 38f), base),
            Boundary("Nearly full", listOf("Almost" to 99.9f, "Remainder" to 0.1f), base),
            Boundary("Rotated across 0°", listOf("A" to 37f, "B" to 63f), base.copy(startAngle = 350f)),
            Boundary("Tiny values", listOf("Tiny A" to 0.04f, "Tiny B" to 0.01f, "Rest" to 99.95f), base),
            Boundary("Zero value", listOf("Zero" to 0f, "Live" to 100f), base),
            Boundary("Empty", emptyList(), base),
            Boundary("Thin ring", thirds, base.copy(innerRadius = 60f, cornerRadius = 100f)),
            Boundary("Thick ring", listOf("A" to 72f, "B" to 28f), base.copy(innerRadius = 14f, cornerRadius = 100f)),
            Boundary("No rounding", thirds, base.copy(cornerRadius = 0f)),
            Boundary("No gap", thirds, base.copy(sliceGap = 0f)),
            Boundary("Zero corner and gap", thirds, base.copy(cornerRadius = 0f, sliceGap = 0f)),
            Boundary("Default stroke", thirds, base.copy(strokeWidth = 1.5f)),
            Boundary("Pie", thirds, base.copy(innerRadius = 0f)),
            Boundary("Pie > half", listOf("Major" to 85.35f, "Rest" to 14.65f), base.copy(innerRadius = 0f)),
            Boundary("Pie nearly full", listOf("Almost" to 99.9f, "Remainder" to 0.1f), base.copy(innerRadius = 0f))
        )
        ManualTestSupport.saveCompose("圆角Donut-05-几何边界") {
            column(Modifier.background(darkBg).padding(20f)) {
                text("几何边界与兼容性", textModifier = top.e404.tavolo.draw.compose.TextModifier.font(24f, ink, uiFont))
                cases.chunked(3).forEach { group ->
                    row(verticalAlignment = VerticalAlignment.Top) {
                        group.forEach { item ->
                            column(Modifier.padding(12f)) {
                                text(item.name, textModifier = top.e404.tavolo.draw.compose.TextModifier.font(16f, ink, uiFont))
                                donutChart(item.theme, item.slices.map { PieSlice(it.first, it.second) })
                            }
                        }
                    }
                }
            }
        }
    }

    // 只放大圆环几何，图例字体维持可读尺寸；140×140 的圆环区域在四倍图中为 560×560。
    private fun theme(scale: Float = 1f, fg: Int = ink) = PieChartTheme(
        width = 140f * scale + 200f, height = maxOf(140f * scale, 166f),
        radius = 65f * scale, innerRadius = 36f * scale,
        insets = ChartInsets(5f * scale, 5f * scale, 0f, 0f),
        palette = palette,
        legend = ChartLegendTheme(position = ChartLegendPosition.RIGHT, textStyle = ChartTextStyle(12f, fg, uiFont), swatchSize = 8f, itemGap = 5f, maxWidth = 185f),
        strokeWidth = 0f, showLabels = false,
        cornerRadius = 4f * scale, sliceGap = 3f * scale,
        legendLabelFormatter = { slice, _, _ -> "${slice.label} ${slice.value}%" }
    )

    private data class Boundary(val name: String, val slices: List<Pair<String, Float>>, val theme: PieChartTheme)

    private data class Snapshot(val name: String, val slices: List<Pair<String, Float>>)
}
