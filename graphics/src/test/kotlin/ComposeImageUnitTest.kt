package top.e404.tavolo.draw.test

import org.junit.Test
import top.e404.tavolo.draw.compose.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeImageUnitTest {
    @Test
    fun imageScaleKeepsAspectRatioAndDoesNotCrop() {
        val image = testImage(100, 50)
        val commands = renderCommands {
            image(
                image,
                Modifier
                    .sizeIn(maxWidth = 50f, maxHeight = 50f),
                imageOverflow = ImageOverflow.Scale
            )
        }

        val command = commands.filterIsInstance<DrawCommand.ImageRect>().single()
        assertFloatEquals(100f, command.src.width)
        assertFloatEquals(50f, command.src.height)
        assertFloatEquals(50f, command.dst.width)
        assertFloatEquals(25f, command.dst.height)
    }

    @Test
    fun imageCropUsesCenteredSourceRect() {
        val image = testImage(100, 50)
        val commands = renderCommands {
            image(
                image,
                Modifier
                    .sizeIn(maxWidth = 40f, maxHeight = 30f),
                imageOverflow = ImageOverflow.Crop
            )
        }

        val command = commands.filterIsInstance<DrawCommand.ImageRect>().single()
        assertFloatEquals(100f / 6f, command.src.left)
        assertFloatEquals(0f, command.src.top)
        assertFloatEquals(200f / 3f, command.src.width)
        assertFloatEquals(50f, command.src.height)
        assertFloatEquals(40f, command.dst.width)
        assertFloatEquals(30f, command.dst.height)
    }

    @Test
    fun imageScaleUsesFinalModifierBounds() {
        val image = testImage(100, 50)
        val commands = renderCommands {
            image(image, Modifier.size(40f, 40f), ImageOverflow.Scale)
        }

        val command = commands.filterIsInstance<DrawCommand.ImageRect>().single()
        assertFloatEquals(100f, command.src.width)
        assertFloatEquals(50f, command.src.height)
        assertFloatEquals(0f, command.dst.left)
        assertFloatEquals(10f, command.dst.top)
        assertFloatEquals(40f, command.dst.width)
        assertFloatEquals(20f, command.dst.height)
    }

    @Test
    fun imageCropUsesFinalModifierBounds() {
        val image = testImage(100, 50)
        val commands = renderCommands {
            image(image, Modifier.size(40f, 40f), ImageOverflow.Crop)
        }

        val command = commands.filterIsInstance<DrawCommand.ImageRect>().single()
        assertFloatEquals(25f, command.src.left)
        assertFloatEquals(0f, command.src.top)
        assertFloatEquals(50f, command.src.width)
        assertFloatEquals(50f, command.src.height)
        assertFloatEquals(40f, command.dst.width)
        assertFloatEquals(40f, command.dst.height)
    }

    @Test
    fun imageAntiAliasIsEnabledByDefaultAndCanBeDisabled() {
        val source = testImage(8, 8)
        val defaultCommand = renderCommands {
            image(source, Modifier.size(16f), ImageOverflow.Stretch)
        }.filterIsInstance<DrawCommand.ImageRect>().single()
        val disabledCommand = renderCommands {
            image(source, Modifier.size(16f).antiAlias(false), ImageOverflow.Stretch)
        }.filterIsInstance<DrawCommand.ImageRect>().single()

        assertTrue(defaultCommand.paint.antiAlias)
        assertFalse(disabledCommand.paint.antiAlias)
    }

    @Test
    fun imageStretchUsesFullSourceAndTargetBounds() {
        val image = testImage(100, 50)
        val commands = renderCommands {
            image(
                image,
                Modifier
                    .sizeIn(maxWidth = 40f, maxHeight = 30f),
                imageOverflow = ImageOverflow.Stretch
            )
        }

        val command = commands.filterIsInstance<DrawCommand.ImageRect>().single()
        assertFloatEquals(100f, command.src.width)
        assertFloatEquals(50f, command.src.height)
        assertFloatEquals(40f, command.dst.width)
        assertFloatEquals(30f, command.dst.height)
    }

    @Test
    fun imageWithoutSizeInUsesOriginalSizeAndDslEntry() {
        val image = testImage(12, 8)
        val root = Column()

        root.apply {
            image(image)
        }
        root.measure(MeasureContext())
        root.layout(0f, 0f)

        assertFloatEquals(12f, root.width)
        assertFloatEquals(8f, root.height)
    }

    @Test
    fun backgroundImageScaleFitsInsideModifierBounds() {
        val image = testImage(100, 50)
        val commands = renderCommands {
            box(
                Modifier
                    .size(40f, 40f)
                    .background(image, ImageOverflow.Scale)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.ImageRect>().single()
        assertFloatEquals(100f, command.src.width)
        assertFloatEquals(50f, command.src.height)
        assertFloatEquals(40f, command.dst.width)
        assertFloatEquals(20f, command.dst.height)
        assertFloatEquals(0f, command.dst.left)
        assertFloatEquals(10f, command.dst.top)
        assertTrue(command.paint.antiAlias)
    }

    @Test
    fun backgroundImageAntiAliasCanBeDisabled() {
        val image = testImage(100, 50)
        val commands = renderCommands {
            box(
                Modifier
                    .size(40f, 40f)
                    .antiAlias(false)
                    .background(image, ImageOverflow.Scale)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.ImageRect>().single()
        assertFalse(command.paint.antiAlias)
    }

    @Test
    fun backgroundImageCropFillsModifierBoundsWithCenteredSourceRect() {
        val image = testImage(100, 50)
        val commands = renderCommands {
            box(
                Modifier
                    .size(40f, 40f)
                    .background(image, ImageOverflow.Crop)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.ImageRect>().single()
        assertFloatEquals(25f, command.src.left)
        assertFloatEquals(0f, command.src.top)
        assertFloatEquals(50f, command.src.width)
        assertFloatEquals(50f, command.src.height)
        assertFloatEquals(40f, command.dst.width)
        assertFloatEquals(40f, command.dst.height)
    }

    @Test
    fun backgroundImageStretchFillsModifierBoundsWithoutCropping() {
        val image = testImage(100, 50)
        val commands = renderCommands {
            box(
                Modifier
                    .size(40f, 40f)
                    .background(image, ImageOverflow.Stretch)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.ImageRect>().single()
        assertFloatEquals(0f, command.src.left)
        assertFloatEquals(0f, command.src.top)
        assertFloatEquals(100f, command.src.width)
        assertFloatEquals(50f, command.src.height)
        assertFloatEquals(40f, command.dst.width)
        assertFloatEquals(40f, command.dst.height)
    }
}

