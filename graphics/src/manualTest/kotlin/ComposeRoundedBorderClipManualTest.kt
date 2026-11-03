package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.StrokeStyle
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.row
import top.e404.tavolo.draw.compose.size

class ComposeRoundedBorderClipManualTest {
    @Test
    fun test_compose_rounded_border_clip_coverage() {
        ManualTestSupport.saveCompose("边框-圆角裁切覆盖率") {
            column(Modifier.padding(20f)) {
                for (offset in listOf(0f, 0.5f)) {
                    for (width in listOf(0.5f, 1f, 2f)) {
                        row(Modifier.padding(bottom = 16f)) {
                            box(
                                Modifier.padding(left = offset, top = offset)
                                    .size(180f, 72f)
                                    .clip(Shape.RoundedRect(12f))
                                    .border(width, Color.makeRGB(45, 115, 220), shape = Shape.RoundedRect(12f))
                            )
                            box(Modifier.size(24f))
                            box(
                                Modifier.padding(left = offset, top = offset)
                                    .size(180f, 72f)
                                    .clip(Shape.RoundedRect(12f))
                                    .background(Color.makeRGB(248, 244, 235))
                                    .border(width, Color.makeRGB(205, 92, 58), shape = Shape.RoundedRect(12f))
                            )
                        }
                    }
                }
            }
        }
        ManualTestSupport.saveCompose("边框-小圆角粗边框") {
            column(Modifier.padding(20f)) {
                for (width in listOf(4f, 6f)) {
                    row(Modifier.padding(bottom = 16f)) {
                        for (style in listOf(StrokeStyle.Solid, StrokeStyle.Dashed(listOf(6f, 3f)), StrokeStyle.Dotted(1f, 3f))) {
                            box(
                                Modifier.padding(left = 0.5f, top = 0.5f).size(100f, 60f)
                                    .clip(Shape.RoundedRect(2f))
                                    .border(width, Color.makeRGB(45, 115, 220), style, Shape.RoundedRect(2f))
                            )
                            box(Modifier.size(20f))
                        }
                    }
                }
            }
        }
    }
}
