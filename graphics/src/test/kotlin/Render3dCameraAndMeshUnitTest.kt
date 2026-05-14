package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Rect
import org.junit.Test
import top.e404.tavolo.draw.render3d.*
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Render3dCameraAndMeshUnitTest {
    @Test
    fun orbitCameraExposesViewMatrixForwardAndPosition() {
        val camera = OrbitCamera(distance = 10f)
        val view = camera.createViewMatrix()

        assertVecEquals(Vec3(0f, 0f, 10f), view.cameraPosition)
        assertVecEquals(Vec3(0f, 0f, -1f), view.cameraForward)

        val yawView = camera.copy(yaw = 90f).createViewMatrix()
        assertFloatEquals3d(10f, yawView.cameraPosition.x)
        assertFloatEquals3d(0f, yawView.cameraPosition.z)
        assertVecEquals(Vec3(-1f, 0f, 0f), yawView.cameraForward)
    }

    @Test
    fun meshFactoriesBuildExpectedGeometry() {
        val cuboid = createCuboid(Vec3(2f, 4f, 6f), Color.RED)
        assertEquals(8, cuboid.vertices.size)
        assertEquals(6, cuboid.faces.size)
        assertTrue(cuboid.vertices.any { it.position == Vec3(-1f, -2f, -3f) })
        assertTrue(cuboid.vertices.any { it.position == Vec3(1f, 2f, 3f) })
        cuboid.faces.forEach { face ->
            assertEquals(4, face.indices.size)
            assertTrue(face.indices.all { it in cuboid.vertices.indices })
            assertEquals(Color.RED, face.baseColor)
        }

        val uvCuboid = createUVCuboid(
            dims = Vec3(2f, 2f, 2f),
            faceUVs = mapOf(FaceDirection.FRONT to Rect.makeXYWH(0f, 0f, 4f, 4f)),
            textureWidth = 8f,
            textureHeight = 8f
        )
        assertEquals(4, uvCuboid.vertices.size)
        assertEquals(1, uvCuboid.faces.size)
        assertFloatEquals3d(0f, uvCuboid.vertices.first().uv.u)
        assertFloatEquals3d(0.5f, uvCuboid.vertices.first().uv.v)

        val plane = createPlane(center = Vec3(0f, 0f, 0f), size = Vec2(2f, 2f), segments = 2)
        assertEquals(9, plane.vertices.size)
        assertEquals(8, plane.faces.size)
        assertTrue(plane.castsShadow)
        assertTrue(plane.receivesShadow)
        assertTrue(plane.vertices.all { it.position.y == 0f })

        val verticalPlane = createPlane(
            center = Vec3(0f, 0f, 0f),
            size = Vec2(2f, 2f),
            normalDirection = Vec3(0f, 0f, 1f),
            segments = 1
        )
        assertEquals(4, verticalPlane.vertices.size)
        assertEquals(2, verticalPlane.faces.size)
        assertTrue(verticalPlane.vertices.all { abs(it.position.z) <= EPSILON_3D })

        val shadowReceiver = createPlane(
            center = Vec3(0f, 0f, 0f),
            size = Vec2(2f, 2f),
            castsShadow = false,
            receivesShadow = true
        )
        assertFalse(shadowReceiver.castsShadow)
        assertTrue(shadowReceiver.receivesShadow)
    }

    @Test
    fun uvCuboidCanBuildAllFacesOrNoFaces() {
        val uvRects = FaceDirection.entries.associateWith { direction ->
            val offset = direction.ordinal.toFloat()
            Rect.makeXYWH(offset, offset, 2f, 2f)
        }

        val full = createUVCuboid(Vec3(2f, 2f, 2f), uvRects, textureWidth = 16f, textureHeight = 16f)
        val empty = createUVCuboid(Vec3(2f, 2f, 2f), emptyMap(), textureWidth = 16f, textureHeight = 16f)

        assertEquals(24, full.vertices.size)
        assertEquals(6, full.faces.size)
        assertTrue(full.faces.all { it.baseColor == Color.WHITE })
        assertEquals(0, empty.vertices.size)
        assertEquals(0, empty.faces.size)
    }

    @Test
    fun combineMeshesOffsetsFaceIndicesAndKeepsTextureFallback() {
        val first = simpleTriangleMesh(Color.RED)
        val second = simpleTriangleMesh(Color.BLUE)

        val combined = combineMeshes(listOf(first, second))

        assertEquals(6, combined.vertices.size)
        assertEquals(2, combined.faces.size)
        assertEquals(listOf(0, 1, 2), combined.faces[0].indices)
        assertEquals(listOf(3, 4, 5), combined.faces[1].indices)
        assertEquals(Color.BLUE, combined.faces[1].baseColor)

        val explicitTexture = bitmap(1, 1, Color.GREEN)
        assertEquals(explicitTexture, combineMeshes(listOf(first), texture = explicitTexture).texture)
        assertTrue(combineMeshes(listOf(first, second)).castsShadow)
        assertFalse(
            combineMeshes(
                listOf(
                    first.copy(castsShadow = false),
                    second.copy(castsShadow = false)
                )
            ).castsShadow
        )
        assertTrue(combineMeshes(listOf(first, second)).receivesShadow)
        assertFalse(
            combineMeshes(
                listOf(
                    first.copy(receivesShadow = false),
                    second.copy(receivesShadow = false)
                )
            ).receivesShadow
        )
        assertEquals(0, combineMeshes(emptyList()).vertices.size)
        assertEquals(0, combineMeshes(emptyList()).faces.size)
    }
}

