package top.e404.tavolo.draw.test

import org.junit.Test
import top.e404.tavolo.draw.compose.*
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun svgModifierSizeRespectsContentBoundsAfterPadding() {
        val commands = renderCommands {
            svg(
                """<svg viewBox="0 0 20 20"><rect width="20" height="20" style="fill:#ff0000"/></svg>""",
                Modifier
                    .size(20f)
                    .padding(5f)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(10f, command.dst.width)
        assertFloatEquals(10f, command.dst.height)
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
    fun svgSizeInWithOnlyMaxWidthKeepsAspectRatio() {
        val commands = renderCommands {
            svg(
                """<svg viewBox="0 0 100 50"><rect width="100" height="50" style="fill:#ff0000"/></svg>""",
                Modifier.sizeIn(maxWidth = 25f)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(25f, command.dst.width)
        assertFloatEquals(12.5f, command.dst.height)
    }

    @Test
    fun svgSizeInWithOnlyMaxHeightKeepsAspectRatio() {
        val commands = renderCommands {
            svg(
                """<svg viewBox="0 0 100 50"><rect width="100" height="50" style="fill:#ff0000"/></svg>""",
                Modifier.sizeIn(maxHeight = 25f)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(50f, command.dst.width)
        assertFloatEquals(25f, command.dst.height)
    }

    @Test
    fun svgUsesWidthAndHeightAsNaturalSize() {
        val commands = renderCommands {
            svg("""<svg width="30" height="20" xmlns="http://www.w3.org/2000/svg"><rect width="30" height="20"/></svg>""")
        }

        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(30f, command.dst.width)
        assertFloatEquals(20f, command.dst.height)
    }

    @Test
    fun svgByteArrayDslUsesBytesInput() {
        val bytes = """<svg viewBox="0 0 9 6"><rect width="9" height="6"/></svg>""".encodeToByteArray()
        val commands = renderCommands {
            svg(bytes)
        }

        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(9f, command.dst.width)
        assertFloatEquals(6f, command.dst.height)
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
    fun svgSizeInMinimumScalesSvgSizeByAspectRatio() {
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
        assertFloatEquals(40f, root.height)

        val commands = renderCommands(root = root) {}
        val command = commands.filterIsInstance<DrawCommand.Svg>().single()
        assertFloatEquals(40f, command.dst.width)
        assertFloatEquals(40f, command.dst.height)
    }

    @Test
    fun svgSizeInConflictingAspectRatioConstraintsFailClearly() {
        val error = assertFailsWith<IllegalArgumentException> {
            renderCommands {
                svg(
                    """<svg viewBox="0 0 10 10"><rect width="10" height="10" style="fill:#ff0000"/></svg>""",
                    Modifier.sizeIn(minWidth = 40f, maxHeight = 20f)
                )
            }
        }

        assertTrue(error.message.orEmpty().contains("SVG sizeIn 约束冲突"))
    }

    @Test
    fun svgWithoutAvailableSizeFailsClearly() {
        val error = assertFailsWith<IllegalStateException> {
            renderCommands {
                svg("""<svg xmlns="http://www.w3.org/2000/svg"><path d="M 0 0 L 10 10"/></svg>""")
            }
        }

        assertTrue(error.message.orEmpty().contains("SVG 缺少可用尺寸"))
    }

    @Test
    fun invalidSvgFailsClearly() {
        assertFailsWith<RuntimeException> {
            parseSvgDom("not svg")
        }
    }
}

