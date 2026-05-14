package top.e404.tavolo.draw.test

import org.junit.Test
import top.e404.tavolo.draw.compose.*

class ComposeSvgUnitTest {
    @Test
    fun svgUsesViewBoxAsNaturalSizeAndRecordsSvgCommand() {
        val commands = renderCommands {
            svg("""<svg viewBox="0 0 12 8"><polygon points="0,0 12,0 12,8" style="fill:#ff0000"/></svg>""")
        }

        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(12f, command.dst.width)
        assertFloatEquals(8f, command.dst.height)
    }

    @Test
    fun svgModifierSizeControlsTargetBounds() {
        val commands = renderCommands {
            svg(
                """<svg viewBox="0 0 12 8"><rect width="12" height="8" style="fill:#ff0000"/></svg>""",
                Modifier.size(24f, 16f)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(24f, command.dst.width)
        assertFloatEquals(16f, command.dst.height)
    }

    @Test
    fun svgSizeInKeepsAspectRatio() {
        val commands = renderCommands {
            svg(
                """<svg viewBox="0 0 100 50"><circle cx="25" cy="25" r="20" style="fill:#00ff00"/></svg>""",
                Modifier.sizeIn(maxWidth = 40f, maxHeight = 40f)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(40f, command.dst.width)
        assertFloatEquals(20f, command.dst.height)
    }

    @Test
    fun svgSingleWidthInfersHeightByAspectRatio() {
        val commands = renderCommands {
            svg(
                """<svg viewBox="0 0 100 50"><rect width="100" height="50" style="fill:#ff0000"/></svg>""",
                Modifier.width(40f)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(40f, command.dst.width)
        assertFloatEquals(20f, command.dst.height)
    }

    @Test
    fun svgSingleHeightInfersWidthByAspectRatio() {
        val commands = renderCommands {
            svg(
                """<svg viewBox="0 0 100 50"><rect width="100" height="50" style="fill:#ff0000"/></svg>""",
                Modifier.height(30f)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(60f, command.dst.width)
        assertFloatEquals(30f, command.dst.height)
    }

    @Test
    fun svgSizeInMinimumExpandsLayoutWithoutStretchingSvg() {
        val root = Column()
        root.apply {
            svg(
                """<svg viewBox="0 0 10 10"><rect width="10" height="10" style="fill:#ff0000"/></svg>""",
                Modifier.sizeIn(minWidth = 40f)
            )
        }
        root.measure(MeasureContext())
        root.layout(0f, 0f)

        assertFloatEquals(40f, root.width)
        assertFloatEquals(10f, root.height)

        val commands = renderCommands(root = root) {}
        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(10f, command.dst.width)
        assertFloatEquals(10f, command.dst.height)
    }
}

