package top.e404.tavolo.draw.test

import org.junit.Test
import top.e404.tavolo.draw.render3d.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Render3dMathUnitTest {
    @Test
    fun vec3SupportsBasicOperationsAndTransforms() {
        val a = Vec3(1f, 2f, 3f)
        val b = Vec3(4f, -1f, 2f)

        assertEquals(Vec3(5f, 1f, 5f), a + b)
        assertEquals(Vec3(-3f, 3f, 1f), a - b)
        assertEquals(Vec3(2f, 4f, 6f), a * 2f)
        assertEquals(Vec3(4f, -2f, 6f), a * b)
        assertEquals(Vec3(0.5f, 1f, 1.5f), a / 2f)
        assertFloatEquals3d(8f, a.dot(b))
        assertEquals(Vec3(7f, 10f, -9f), a.cross(b))
        assertVecEquals(Vec3(0.6f, 0.8f, 0f), Vec3(3f, 4f, 0f).normalized())
        assertEquals(Vec3(1f, 1f, 0f), Vec3(1f, -1f, 0f).reflect(Vec3(0f, 1f, 0f)))

        assertVecEquals(Vec3(2f, 4f, 6f), a.scale(Transformation.Scale(2f, 2f, 2f)))
        assertVecEquals(Vec3(2f, 1f, 5f), a.translate(Transformation.Translate(1f, -1f, 2f)))
        assertVecEquals(Vec3(0f, 0f, -1f), Vec3(1f, 0f, 0f).rotate(Transformation.Rotate(y = 90f)))
    }

    @Test
    fun scalarHelpersAndVectorEdgeBranchesAreCovered() {
        assertFloatEquals3d(2f, 5f.clamp(0f, 2f))
        assertFloatEquals3d(0f, (-1f).clamp(0f, 2f))
        assertEquals(2, 5.clamp(0, 2))
        assertEquals(0, (-1).clamp(0, 2))
        assertFloatEquals3d(3f, mix(2f, 6f, 0.25f))

        assertEquals(Vec3(0f, 0f, 0f), Vec3(0f, 0f, 0f).normalized())
        assertEquals(Vec3(-1f, -2f, -3f), -Vec3(1f, 2f, 3f))
        assertVecEquals(Vec3(0f, 0f, 1f), Vec3(0f, 1f, 0f).rotateX(Math.toRadians(90.0).toFloat()))
        assertVecEquals(Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f).rotateZ(Math.toRadians(90.0).toFloat()))
    }

    @Test
    fun mat4LookAtAndProjectionTransformExpectedPoints() {
        val view = Mat4.lookAt(
            eye = Vec3(0f, 0f, 5f),
            center = Vec3(0f, 0f, 0f),
            up = Vec3(0f, 1f, 0f)
        )

        val eyeInView = view.transform(Vec3(0f, 0f, 5f))
        assertFloatEquals3d(0f, eyeInView.x)
        assertFloatEquals3d(0f, eyeInView.y)
        assertFloatEquals3d(0f, eyeInView.z)
        assertFloatEquals3d(1f, eyeInView.w)

        val ortho = Mat4.orthographic(-2f, 2f, -2f, 2f, 0f, 10f)
        val topRight = ortho.transform(Vec3(2f, 2f, -5f))
        assertFloatEquals3d(1f, topRight.x)
        assertFloatEquals3d(1f, topRight.y)
        assertFloatEquals3d(0f, topRight.z)
        assertFloatEquals3d(1f, topRight.w)

        val perspective = Mat4.perspective(Math.toRadians(90.0).toFloat(), 1f, 1f, 10f)
        val projected = perspective.transform(Vec3(0f, 0f, -5f))
        assertFloatEquals3d(5f, projected.w)
        assertTrue(projected.z < projected.w, "透视投影后的 z 应在裁剪空间内")

        val identity = Mat4()
        assertTrue(identity == Mat4())
        assertFalse(identity == Mat4.orthographic(-1f, 1f, -1f, 1f, 0f, 1f))
        assertEquals(identity.hashCode(), Mat4().hashCode())
    }

    @Test
    fun ndcToScreenMapsClipCoordinates() {
        val screen = ndcToScreen(Vec4(0.5f, -0.5f, 0.25f, 2f), 20, 10)

        assertVecEquals(Vec3(12.5f, 6.25f, 0.125f), screen)
    }
}

