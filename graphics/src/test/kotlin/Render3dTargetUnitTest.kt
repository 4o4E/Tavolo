package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.render3d.*
import top.e404.tavolo.util.any
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Render3dTargetUnitTest {
    @Test
    fun bitmapRenderTargetAndTextureDelegateToBitmap() {
        val bitmap = bitmap(4, 4)
        val target = BitmapRenderTarget(bitmap)

        target.clear(Color.BLUE)
        assertEquals(Color.BLUE, target.getColor(0, 0))

        target.setPixel(1, 1, Color.RED)
        assertEquals(Color.RED, bitmap.getColor(1, 1))

        target.drawLine(0f, 0f, 3f, 0f, Color.GREEN)
        assertEquals(Color.GREEN, bitmap.getColor(0, 0))

        val texture = BitmapRenderTexture(bitmap)
        assertEquals(4, texture.width)
        assertEquals(4, texture.height)
        assertEquals(Color.RED, texture.getColor(1, 1))
    }

    @Test
    fun recordingRenderTargetStoresCommandsAndReadablePixels() {
        val target = RecordingRenderTarget(3, 3, defaultColor = Color.BLACK)

        assertEquals(Color.BLACK, target.getColor(-1, 0))
        target.clear(Color.BLUE)
        target.setPixel(1, 1, Color.RED)
        target.setPixel(-1, 1, Color.GREEN)
        target.drawLine(0f, 0f, 2f, 2f, Color.WHITE)

        assertEquals(Color.BLUE, target.getColor(0, 0))
        assertEquals(Color.RED, target.getColor(1, 1))
        assertEquals(Color.BLACK, target.getColor(3, 1))
        assertEquals(RenderCommand.Clear(Color.BLUE), target.commands[0])
        assertEquals(RenderCommand.Pixel(1, 1, Color.RED), target.commands[1])
        assertEquals(RenderCommand.Pixel(-1, 1, Color.GREEN), target.commands[2])
        assertEquals(RenderCommand.Line(0f, 0f, 2f, 2f, Color.WHITE, 1f, false), target.commands[3])
    }

    @Test
    fun bitmapCompatibilityOverloadsDelegateToRenderTargetInterfaces() {
        val target = bitmap(5, 5)
        val zBuffer = FloatArray(25) { Float.POSITIVE_INFINITY }
        val vertices = listOf(
            ShadedVertex(Vec3(1f, 1f, 0.2f), Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(3f, 1f, 0.2f), Vec3(1f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(1f, 3f, 0.2f), Vec3(0f, 1f, 0f), 1f, Vec2(0f, 0f))
        )
        val texture = bitmap(1, 1, Color.RED)
        val config = RenderConfig(
            width = 5,
            height = 5,
            camera = OrbitCamera(distance = 5f),
            lightDirection = Vec3(0f, 0f, 1f),
            lightIntensity = 1f
        )

        rasterizeTriangleMain(
            vertices = vertices,
            width = 5,
            height = 5,
            zBuffer = zBuffer,
            bitmap = target,
            texture = texture,
            baseColor = Color.BLUE,
            normal = Vec3(0f, 0f, 1f),
            lightVP = Mat4(),
            shadowMap = ShadowMap(4, 4),
            config = config,
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f)
        )

        assertEquals(Color.RED, target.getColor(1, 1))

        val wireframeTarget = bitmap(16, 16)
        renderMeshMainPass(
            mesh = simpleTriangleMesh(Color.GREEN),
            vpMatrix = Mat4(),
            lightVP = Mat4(),
            width = 16,
            height = 16,
            zBuffer = FloatArray(16 * 16) { Float.POSITIVE_INFINITY },
            bitmap = wireframeTarget,
            shadowMap = ShadowMap(4, 4),
            config = config.copy(width = 16, height = 16, renderFaces = false),
            viewPos = Vec3(0f, 0f, 5f)
        )

        assertTrue(wireframeTarget.any { Color.getA(it) > 0 }, "Bitmap 兼容重载应能绘制线框")
    }
}

