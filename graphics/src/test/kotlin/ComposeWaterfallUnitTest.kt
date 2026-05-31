package top.e404.tavolo.draw.test

import org.junit.Test
import top.e404.tavolo.draw.compose.BaseElement
import top.e404.tavolo.draw.compose.CanvasElement
import top.e404.tavolo.draw.compose.DrawContext
import top.e404.tavolo.draw.compose.MeasureContext
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Text
import top.e404.tavolo.draw.compose.WaterfallLayout
import top.e404.tavolo.draw.compose.Column
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.sizeIn
import top.e404.tavolo.draw.compose.waterfall
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class ComposeWaterfallUnitTest {
    @Test
    fun placeChildrenIntoShortestColumn() {
        val root = WaterfallLayout(columns = 2, width = 110f, columnSpacing = 10f, rowSpacing = 5f)
        val first = CanvasElement(80f, 100f) {}
        val second = CanvasElement(80f, 40f) {}
        val third = CanvasElement(80f, 30f) {}
        val fourth = CanvasElement(80f, 80f) {}

        listOf(first, second, third, fourth).forEach(root::add)
        root.measure(MeasureContext())
        root.layout(0f, 0f)

        assertElementBounds(root, x = 0f, y = 0f, width = 110f, height = 160f)
        assertElementBounds(first, x = 0f, y = 0f, width = 50f, height = 100f)
        assertElementBounds(second, x = 60f, y = 0f, width = 50f, height = 40f)
        assertElementBounds(third, x = 60f, y = 45f, width = 50f, height = 30f)
        assertElementBounds(fourth, x = 60f, y = 80f, width = 50f, height = 80f)
    }

    @Test
    fun supportSingleColumnWithRowSpacing() {
        val root = WaterfallLayout(columns = 1, width = 30f, rowSpacing = 4f)
        val first = CanvasElement(30f, 10f) {}
        val second = CanvasElement(20f, 12f) {}

        root.add(first)
        root.add(second)
        root.measure(MeasureContext())
        root.layout(5f, 7f)

        assertElementBounds(root, x = 5f, y = 7f, width = 30f, height = 26f)
        assertElementBounds(first, x = 5f, y = 7f, width = 30f, height = 10f)
        assertElementBounds(second, x = 5f, y = 21f, width = 20f, height = 12f)
    }

    @Test
    fun constrainChildOuterWidthAfterExistingModifiers() {
        val root = WaterfallLayout(columns = 2, width = 100f)
        val child = CanvasElement(80f, 10f) {}
        child.modifier = Modifier.padding(10f)

        root.add(child)
        root.measure(MeasureContext())
        root.layout(0f, 0f)

        assertElementBounds(root, x = 0f, y = 0f, width = 100f, height = 30f)
        assertElementBounds(child, x = 0f, y = 0f, width = 50f, height = 30f)
    }

    @Test
    fun measuredWidthCoversMinimumColumnsAndSpacing() {
        val root = WaterfallLayout(columns = 3, width = 0f, columnSpacing = 10f)
        val first = CanvasElement(10f, 5f) {}
        val second = CanvasElement(10f, 5f) {}
        val third = CanvasElement(10f, 5f) {}

        listOf(first, second, third).forEach(root::add)
        root.measure(MeasureContext())
        root.layout(0f, 0f)

        assertFloatEquals(1f, root.columnWidth)
        assertElementBounds(root, x = 0f, y = 0f, width = 23f, height = 5f)
        assertElementBounds(first, x = 0f, y = 0f, width = 1f, height = 5f)
        assertElementBounds(second, x = 11f, y = 0f, width = 1f, height = 5f)
        assertElementBounds(third, x = 22f, y = 0f, width = 1f, height = 5f)
    }

    @Test
    fun constrainDirectTextContentMeasurementAfterExistingSizeIn() {
        val root = WaterfallLayout(columns = 1, width = 30f)
        val child = Text("abcdef")
        child.modifier = Modifier.sizeIn(maxWidth = 100f)

        root.add(child)
        root.measure(MeasureContext(FixedTextMeasurer()))
        root.layout(0f, 0f)

        assertElementBounds(root, x = 0f, y = 0f, width = 30f, height = 20f)
        assertElementBounds(child, x = 0f, y = 0f, width = 30f, height = 20f)
    }

    @Test
    fun restoreChildModifierWhenMeasureFails() {
        val root = WaterfallLayout(columns = 1, width = 50f)
        val child = FailingMeasureElement()
        val originalModifier = Modifier.padding(3f)
        child.modifier = originalModifier

        root.add(child)

        assertFailsWith<IllegalStateException> {
            root.measure(MeasureContext())
        }
        assertSame(originalModifier, child.modifier)
    }

    @Test
    fun dslExposesColumnWidthInsideBlock() {
        val root = Column()

        root.waterfall(columns = 3, width = 150f, columnSpacing = 15f, modifier = Modifier.padding(2f)) {
            assertFloatEquals(40f, columnWidth)
            add(CanvasElement(columnWidth, 20f) {})
        }
        root.measure(MeasureContext())
        root.layout(0f, 0f)

        val waterfall = assertIs<WaterfallLayout>(root.children.single())
        assertEquals(1, waterfall.children.size)
        assertElementBounds(waterfall, x = 0f, y = 0f, width = 154f, height = 24f)
        assertElementBounds(waterfall.children.single(), x = 2f, y = 2f, width = 40f, height = 20f)
    }

    private class FailingMeasureElement : BaseElement() {
        override fun measureContent(context: MeasureContext) {
            error("测量失败")
        }

        override fun layoutChildren(content: Bounds) {}

        override fun drawContent(context: DrawContext) {}
    }
}
