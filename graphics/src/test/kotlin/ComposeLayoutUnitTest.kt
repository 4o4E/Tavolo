package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Typeface
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.util.FontManager
import kotlin.test.assertIs

class ComposeLayoutUnitTest {
    @Test
    fun columnMeasuresPaddingAndHorizontalAlignment() {
        val root = Column(horizontalAlignment = HorizontalAlignment.Center)
        val first = CanvasElement(20f, 10f) {}
        first.modifier = Modifier.padding(top = 1f, right = 4f, bottom = 2f, left = 3f)
        val second = CanvasElement(40f, 10f) {}

        root.add(first)
        root.add(second)
        root.measure(MeasureContext(FixedTextMeasurer()))
        root.layout(0f, 0f)

        assertFloatEquals(40f, root.width)
        assertFloatEquals(23f, root.height)
        assertElementBounds(first, x = 6.5f, y = 0f, width = 27f, height = 13f)
        assertElementBounds(second, x = 0f, y = 13f, width = 40f, height = 10f)
    }

    @Test
    fun rowUsesSizeOverrideAndVerticalAlignment() {
        val root = Row(verticalAlignment = VerticalAlignment.Center)
        root.modifier = Modifier.size(width = 30f, height = 40f)
        val first = CanvasElement(10f, 10f) {}
        first.modifier = Modifier.padding(top = 5f, bottom = 5f)
        val second = CanvasElement(5f, 30f) {}

        root.add(first)
        root.add(second)
        root.measure(MeasureContext(FixedTextMeasurer()))
        root.layout(0f, 0f)

        assertElementBounds(root, x = 0f, y = 0f, width = 30f, height = 40f)
        assertElementBounds(first, x = 0f, y = 10f, width = 10f, height = 20f)
        assertElementBounds(second, x = 10f, y = 5f, width = 5f, height = 30f)
    }

    @Test
    fun boxAlignsChildrenInsidePaddingAndBorder() {
        val root = Box(
            horizontalAlignment = HorizontalAlignment.Right,
            verticalAlignment = VerticalAlignment.Bottom
        )
        root.modifier = Modifier
            .size(100f, 80f)
            .padding(10f)
            .border(2f, Color.RED)
        val child = CanvasElement(20f, 10f) {}
        child.modifier = Modifier.padding(right = 5f, bottom = 3f)

        root.add(child)
        root.measure(MeasureContext(FixedTextMeasurer()))
        root.layout(0f, 0f)

        assertFloatEquals(100f, root.width)
        assertFloatEquals(80f, root.height)
        assertElementBounds(child, x = 63f, y = 55f, width = 25f, height = 13f)
    }

    @Test
    fun remainingAlignmentBranchesAreCovered() {
        val column = Column(horizontalAlignment = HorizontalAlignment.Right)
        val columnChild = CanvasElement(10f, 10f) {}
        column.modifier = Modifier.size(30f, 10f)
        column.add(columnChild)

        val row = Row(verticalAlignment = VerticalAlignment.Bottom)
        val rowChild = CanvasElement(10f, 10f) {}
        row.modifier = Modifier.size(10f, 30f)
        row.add(rowChild)

        val box = Box(
            horizontalAlignment = HorizontalAlignment.Center,
            verticalAlignment = VerticalAlignment.Center
        )
        val boxChild = CanvasElement(10f, 10f) {}
        box.modifier = Modifier.size(30f, 30f)
        box.add(boxChild)

        listOf(column, row, box).forEach {
            it.measure(MeasureContext(FixedTextMeasurer()))
            it.layout(0f, 0f)
        }

        assertElementBounds(columnChild, x = 20f, y = 0f, width = 10f, height = 10f)
        assertElementBounds(rowChild, x = 0f, y = 20f, width = 10f, height = 10f)
        assertElementBounds(boxChild, x = 10f, y = 10f, width = 10f, height = 10f)
    }

    @Test
    fun heatmapStyleLayoutUsesSingleAxisSizeAndHorizontalVerticalPadding() {
        val root = Column()
        val fontName = FontManager.register("unit-heatmap-empty", Typeface.makeEmpty())

        root.apply {
            row(Modifier.padding(top = 20f)) {
                column(Modifier.padding(right = 10f), horizontalAlignment = HorizontalAlignment.Right) {
                    text(" ", fontSize = 10f, fontFamily = fontName)
                    text("Mon", Modifier.padding(horizontal = 3f, vertical = 0f), fontSize = 10f, fontFamily = fontName)
                    box(Modifier.height(16f))
                    text("Wed", fontSize = 10f, fontFamily = fontName)
                }
                column {
                    box(
                        Modifier
                            .padding(3f)
                            .size(15f)
                            .background(Color.RED)
                            .border(.5f, Color.BLACK)
                            .clip(Shape.RoundedRect(3f))
                    )
                }
            }
        }

        root.measure(MeasureContext(FixedTextMeasurer()))
        root.layout(0f, 0f)

        val row = assertIs<Row>(root.children.single())
        val labels = assertIs<Column>(row.children[0])
        val heatmap = assertIs<Column>(row.children[1])
        assertElementBounds(row, x = 0f, y = 0f, width = 67f, height = 66f)
        assertElementBounds(labels.children[2], x = 36f, y = 40f, width = 0f, height = 16f)
        assertElementBounds(heatmap.children.single(), x = 46f, y = 20f, width = 21f, height = 21f)
    }
}

