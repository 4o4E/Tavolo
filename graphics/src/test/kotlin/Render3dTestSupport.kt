package top.e404.tavolo.draw.test

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import top.e404.tavolo.draw.render3d.*
import kotlin.math.abs
import kotlin.test.assertTrue

internal const val EPSILON_3D = 0.001f

internal fun assertFloatEquals3d(expected: Float, actual: Float) {
    assertTrue(abs(expected - actual) <= EPSILON_3D, "期望 $expected，实际 $actual")
}

internal fun assertVecEquals(expected: Vec3, actual: Vec3) {
    assertFloatEquals3d(expected.x, actual.x)
    assertFloatEquals3d(expected.y, actual.y)
    assertFloatEquals3d(expected.z, actual.z)
}

internal fun bitmap(width: Int, height: Int, color: Int = Color.TRANSPARENT): Bitmap =
    Bitmap().apply {
        allocN32Pixels(width, height)
        Canvas(this).clear(color)
    }

internal fun simpleTriangleMesh(color: Int = Color.RED): Mesh =
    Mesh(
        vertices = listOf(
            Vertex(Vec3(-1f, -1f, 0f), Vec2(0f, 0f)),
            Vertex(Vec3(1f, -1f, 0f), Vec2(1f, 0f)),
            Vertex(Vec3(0f, 1f, 0f), Vec2(0.5f, 1f))
        ),
        faces = listOf(Face(listOf(0, 1, 2), color))
    )
