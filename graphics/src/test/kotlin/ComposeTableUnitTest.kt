package top.e404.tavolo.draw.test

import org.junit.Test
import top.e404.tavolo.draw.compose.*

class ComposeTableUnitTest {
    @Test
    fun tableUsesMaxColumnWidthsAndRowSpacing() {
        val table = Table(columnSpacing = 2f, rowSpacing = 3f)
        table.apply {
            tableRow {
                cell { text("aa") }
                cell { text("b") }
            }
            tableRow {
                cell { text("c") }
                cell { text("dddd") }
            }
        }

        table.measure(MeasureContext(FixedTextMeasurer()))
        table.layout(0f, 0f)

        assertFloatEquals(62f, table.width)
        assertFloatEquals(23f, table.height)
        assertElementBounds(table.rows[0].cells[0], x = 0f, y = 0f, width = 20f, height = 10f)
        assertElementBounds(table.rows[0].cells[1], x = 22f, y = 0f, width = 40f, height = 10f)
        assertElementBounds(table.rows[1].cells[0], x = 0f, y = 13f, width = 20f, height = 10f)
        assertElementBounds(table.rows[1].cells[1], x = 22f, y = 13f, width = 40f, height = 10f)
    }

    @Test
    fun tableCellAlignsContentAndTableRowFallbackMeasuresChildren() {
        val row = TableRow()
        row.cell(
            modifier = Modifier.size(30f, 30f),
            horizontalAlignment = HorizontalAlignment.Right,
            verticalAlignment = VerticalAlignment.Bottom
        ) {
            box(Modifier.size(10f))
        }

        row.measure(MeasureContext(FixedTextMeasurer()))
        row.layout(0f, 0f)

        val cell = row.cells.single()
        cell.layout(0f, 0f)
        val child = cell.content!!
        assertFloatEquals(30f, row.width)
        assertFloatEquals(30f, row.height)
        assertElementBounds(child, x = 20f, y = 20f, width = 10f, height = 10f)
    }
}

