package top.e404.tavolo.draw.test

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Rect
import org.junit.Test
import top.e404.tavolo.draw.render3d.BitmapRenderTarget
import top.e404.tavolo.draw.render3d.BitmapRenderTexture
import top.e404.tavolo.draw.render3d.DEFAULT_SHADOW_BIAS
import top.e404.tavolo.draw.render3d.DEFAULT_SHADOW_MAP_SIZE
import top.e404.tavolo.draw.render3d.DEFAULT_SHADOW_ORTHO_SIZE
import top.e404.tavolo.draw.render3d.Face
import top.e404.tavolo.draw.render3d.FaceDirection
import top.e404.tavolo.draw.render3d.Mat4
import top.e404.tavolo.draw.render3d.Mesh
import top.e404.tavolo.draw.render3d.MainRenderPass
import top.e404.tavolo.draw.render3d.OrbitCamera
import top.e404.tavolo.draw.render3d.RecordingRenderTarget
import top.e404.tavolo.draw.render3d.RenderCommand
import top.e404.tavolo.draw.render3d.RenderConfig
import top.e404.tavolo.draw.render3d.Scene
import top.e404.tavolo.draw.render3d.ShadedVertex
import top.e404.tavolo.draw.render3d.ShadowMap
import top.e404.tavolo.draw.render3d.Transformation
import top.e404.tavolo.draw.render3d.Vec2
import top.e404.tavolo.draw.render3d.Vec3
import top.e404.tavolo.draw.render3d.Vec4
import top.e404.tavolo.draw.render3d.Vertex
import top.e404.tavolo.draw.render3d.applyLight
import top.e404.tavolo.draw.render3d.buildShadowFaceRegistry
import top.e404.tavolo.draw.render3d.calculateFilteredShadowFactor
import top.e404.tavolo.draw.render3d.calculateBlinnPhong
import top.e404.tavolo.draw.render3d.calculateLightIntensity
import top.e404.tavolo.draw.render3d.calculateLightOrthoBounds
import top.e404.tavolo.draw.render3d.calculateShadowDepthBias
import top.e404.tavolo.draw.render3d.clamp
import top.e404.tavolo.draw.render3d.combineMeshes
import top.e404.tavolo.draw.render3d.createCuboid
import top.e404.tavolo.draw.render3d.createPlane
import top.e404.tavolo.draw.render3d.createUVCuboid
import top.e404.tavolo.draw.render3d.mix
import top.e404.tavolo.draw.render3d.ndcToScreen
import top.e404.tavolo.draw.render3d.rasterizeTriangleMain
import top.e404.tavolo.draw.render3d.rasterizeTriangleShadow
import top.e404.tavolo.draw.render3d.renderMeshMainPass
import top.e404.tavolo.draw.render3d.renderShadowPass
import top.e404.tavolo.draw.render3d.renderSceneToImage
import top.e404.tavolo.util.any
import top.e404.tavolo.util.toBitmap
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val EPSILON_3D = 0.001f

private fun assertFloatEquals3d(expected: Float, actual: Float) {
    assertTrue(abs(expected - actual) <= EPSILON_3D, "期望 $expected，实际 $actual")
}

private fun assertVecEquals(expected: Vec3, actual: Vec3) {
    assertFloatEquals3d(expected.x, actual.x)
    assertFloatEquals3d(expected.y, actual.y)
    assertFloatEquals3d(expected.z, actual.z)
}

private fun bitmap(width: Int, height: Int, color: Int = Color.TRANSPARENT): Bitmap =
    Bitmap().apply {
        allocN32Pixels(width, height)
        Canvas(this).clear(color)
    }

private fun simpleTriangleMesh(color: Int = Color.RED): Mesh =
    Mesh(
        vertices = listOf(
            Vertex(Vec3(-1f, -1f, 0f), Vec2(0f, 0f)),
            Vertex(Vec3(1f, -1f, 0f), Vec2(1f, 0f)),
            Vertex(Vec3(0f, 1f, 0f), Vec2(0.5f, 1f))
        ),
        faces = listOf(Face(listOf(0, 1, 2), color))
    )

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
        assertFalse(combineMeshes(listOf(first.copy(castsShadow = false), second.copy(castsShadow = false))).castsShadow)
        assertTrue(combineMeshes(listOf(first, second)).receivesShadow)
        assertFalse(combineMeshes(listOf(first.copy(receivesShadow = false), second.copy(receivesShadow = false))).receivesShadow)
        assertEquals(0, combineMeshes(emptyList()).vertices.size)
        assertEquals(0, combineMeshes(emptyList()).faces.size)
    }
}

class Render3dLightAndShadowUnitTest {
    @Test
    fun lightFunctionsClampAndPreserveAlpha() {
        assertFloatEquals3d(0.6f, RenderConfig(1, 1, OrbitCamera()).lightIntensity)
        assertTrue(RenderConfig(1, 1, OrbitCamera()).enableShadows)

        assertFloatEquals3d(
            1f,
            calculateLightIntensity(Vec3(0f, 0f, 1f), Vec3(0f, 0f, 1f), ambientIntensity = 0.25f)
        )
        assertFloatEquals3d(
            0.25f,
            calculateLightIntensity(Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f), ambientIntensity = 0.25f)
        )

        val lit = applyLight(Color.makeARGB(200, 100, 50, 25), 0.5f)
        assertEquals(200, Color.getA(lit))
        assertEquals(50, Color.getR(lit))
        assertEquals(25, Color.getG(lit))
        assertEquals(12, Color.getB(lit))

        val fullLight = calculateBlinnPhong(
            normal = Vec3(0f, 0f, 1f),
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f),
            baseColor = Color.makeRGB(100, 80, 60),
            shadowFactor = 1f,
            ambientStrength = 0.2f,
            specularStrength = 0f
        )
        val shadowed = calculateBlinnPhong(
            normal = Vec3(0f, 0f, 1f),
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f),
            baseColor = Color.makeRGB(100, 80, 60),
            shadowFactor = 0f,
            ambientStrength = 0.2f,
            specularStrength = 0f
        )

        assertEquals(Color.makeRGB(100, 80, 60), fullLight)
        assertEquals(Color.makeRGB(20, 16, 12), shadowed)
    }

    @Test
    fun engineShadowParametersStayFixed() {
        assertEquals(4096, DEFAULT_SHADOW_MAP_SIZE)
        assertEquals(0.0005f, DEFAULT_SHADOW_BIAS)
        assertEquals(42f, DEFAULT_SHADOW_ORTHO_SIZE)
    }

    @Test
    fun lightOrthoBoundsFitShadowRelevantSceneGeometry() {
        val caster = Mesh(
            vertices = listOf(
                Vertex(Vec3(-1f, -2f, -3f), Vec2(0f, 0f)),
                Vertex(Vec3(1f, 2f, 3f), Vec2(0f, 0f))
            ),
            faces = emptyList(),
            castsShadow = true,
            receivesShadow = false
        )
        val ignored = Mesh(
            vertices = listOf(Vertex(Vec3(100f, 100f, 100f), Vec2(0f, 0f))),
            faces = emptyList(),
            castsShadow = false,
            receivesShadow = false
        )

        val bounds = calculateLightOrthoBounds(listOf(caster, ignored), Mat4())

        assertTrue(bounds.left <= -1f)
        assertTrue(bounds.right >= 1f)
        assertTrue(bounds.bottom <= -2f)
        assertTrue(bounds.top >= 2f)
        assertTrue(bounds.left > -10f, "自动收紧后不应被无关 mesh 或固定大范围拉宽")
        assertTrue(bounds.right < 10f, "自动收紧后不应被无关 mesh 或固定大范围拉宽")
        assertTrue(bounds.near < bounds.far)
    }

    @Test
    fun filteredShadowFactorUsesBilinearWeightsAndIgnoredFaces() {
        val shadowMap = ShadowMap(2, 2).apply {
            set(0, 0, -0.5f, 1)
            set(1, 0, 0.8f, 2)
            set(0, 1, 0.8f, 3)
            set(1, 1, 0.8f, 4)
        }

        val filtered = calculateFilteredShadowFactor(
            shadowMap = shadowMap,
            shadowMapX = 0.5f,
            shadowMapY = 0.5f,
            currentDepth = 0.2f,
            slopeBias = 0f,
            pcfRadius = 0
        )
        val ignored = calculateFilteredShadowFactor(
            shadowMap = shadowMap,
            shadowMapX = 0.5f,
            shadowMapY = 0.5f,
            currentDepth = 0.2f,
            slopeBias = 0f,
            ignoredShadowFaceIds = intArrayOf(1),
            pcfRadius = 0
        )

        assertEquals(0.875f, filtered, 0.000001f)
        assertEquals(1f, ignored, 0.000001f)
    }

    @Test
    fun shadowMapBoundsClearAndTriangleDepthUpdates() {
        val shadowMap = ShadowMap(5, 5)
        shadowMap.set(2, 2, 0.8f)
        shadowMap.set(-1, 2, 0.1f)
        shadowMap.set(2, -1, 0.1f)
        shadowMap.set(5, 2, 0.1f)
        shadowMap.set(2, 5, 0.1f)

        assertFloatEquals3d(0.8f, shadowMap.get(2, 2))
        assertTrue(shadowMap.get(-1, 2).isInfinite(), "越界读取应返回正无穷")
        assertTrue(shadowMap.get(2, -1).isInfinite(), "越界读取应返回正无穷")
        assertTrue(shadowMap.get(5, 2).isInfinite(), "越界读取应返回正无穷")
        assertTrue(shadowMap.get(2, 5).isInfinite(), "越界读取应返回正无穷")

        rasterizeTriangleShadow(
            p0 = Vec3(1f, 1f, 0.7f),
            p1 = Vec3(3f, 1f, 0.7f),
            p2 = Vec3(1f, 3f, 0.7f),
            shadowMap = shadowMap
        )
        assertFloatEquals3d(0.7f, shadowMap.get(1, 1))

        rasterizeTriangleShadow(
            p0 = Vec3(1f, 1f, 0.3f),
            p1 = Vec3(3f, 1f, 0.3f),
            p2 = Vec3(1f, 3f, 0.3f),
            shadowMap = shadowMap
        )
        assertFloatEquals3d(0.3f, shadowMap.get(1, 1))

        rasterizeTriangleShadow(
            p0 = Vec3(1f, 1f, 0.9f),
            p1 = Vec3(3f, 1f, 0.9f),
            p2 = Vec3(1f, 3f, 0.9f),
            shadowMap = shadowMap
        )
        assertFloatEquals3d(0.3f, shadowMap.get(1, 1))

        rasterizeTriangleShadow(
            p0 = Vec3(1f, 1f, 0.1f),
            p1 = Vec3(2f, 2f, 0.1f),
            p2 = Vec3(3f, 3f, 0.1f),
            shadowMap = shadowMap
        )
        assertFloatEquals3d(0.3f, shadowMap.get(1, 1))

        shadowMap.clear()
        assertTrue(shadowMap.get(1, 1).isInfinite(), "clear 后深度应重置")
    }

    @Test
    fun shadowFaceRegistryDistinguishesMeshOccurrencesAndSharedEdges() {
        val sharedFace = Face(listOf(0, 1, 2), Color.RED)
        val first = Mesh(
            vertices = listOf(
                Vertex(Vec3(0f, 0f, 0f), Vec2(0f, 0f)),
                Vertex(Vec3(1f, 0f, 0f), Vec2(0f, 0f)),
                Vertex(Vec3(0f, 1f, 0f), Vec2(0f, 0f))
            ),
            faces = listOf(sharedFace)
        )
        val second = first.copy(faces = listOf(sharedFace))
        val reusedFaceRegistry = buildShadowFaceRegistry(listOf(first, second))

        assertFalse(
            reusedFaceRegistry.faceId(0, 0) == reusedFaceRegistry.faceId(1, 0),
            "同一个 Face 实例在不同 mesh 出现时也应分配不同 shadow face id"
        )

        val cuboidRegistry = buildShadowFaceRegistry(listOf(createCuboid(Vec3(1f, 1f, 1f), Color.RED)))
        val firstFaceId = cuboidRegistry.faceId(0, 0)
        val adjacentFaceId = cuboidRegistry.faceId(0, 1)
        val oppositeFaceId = cuboidRegistry.faceId(0, 2)

        assertTrue(
            adjacentFaceId in cuboidRegistry.ignoredFaceIds(firstFaceId),
            "共享边的相邻面不应在硬边上互相制造自阴影"
        )
        assertFalse(
            oppositeFaceId in cuboidRegistry.ignoredFaceIds(firstFaceId),
            "不共享边的同一 mesh 面仍应允许形成真实遮挡"
        )
    }

    @Test
    fun shadowRasterizerSkipsTransparentTexturePixels() {
        val shadowMap = ShadowMap(4, 4)
        val transparentTexture = BitmapRenderTexture(bitmap(1, 1, Color.TRANSPARENT))
        val vertices = listOf(
            ShadedVertex(Vec3(1f, 1f, 0.2f), Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(3f, 1f, 0.2f), Vec3(1f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(1f, 3f, 0.2f), Vec3(0f, 1f, 0f), 1f, Vec2(0f, 0f))
        )

        rasterizeTriangleShadow(vertices, shadowMap, transparentTexture)

        assertTrue(shadowMap.buffer.all { it.isInfinite() }, "透明纹理像素不应写入阴影深度")
    }
}

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

class Render3dRasterAndRenderUnitTest {
    @Test
    fun mainRasterizerWritesTriangleAndKeepsNearestDepth() {
        val bitmap = bitmap(5, 5)
        val zBuffer = FloatArray(25) { Float.POSITIVE_INFINITY }
        val shadowMap = ShadowMap(4, 4)
        val config = RenderConfig(
            width = 5,
            height = 5,
            camera = OrbitCamera(distance = 5f),
            lightDirection = Vec3(0f, 0f, 1f),
            lightIntensity = 1f
        )

        val nearTriangle = listOf(
            ShadedVertex(Vec3(1f, 1f, 0.2f), Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(3f, 1f, 0.2f), Vec3(1f, 0f, 0f), 1f, Vec2(1f, 0f)),
            ShadedVertex(Vec3(1f, 3f, 0.2f), Vec3(0f, 1f, 0f), 1f, Vec2(0f, 1f))
        )
        rasterizeTriangleMain(
            vertices = nearTriangle,
            width = 5,
            height = 5,
            zBuffer = zBuffer,
            target = BitmapRenderTarget(bitmap),
            texture = null,
            baseColor = Color.RED,
            normal = Vec3(0f, 0f, 1f),
            lightVP = Mat4(),
            shadowMap = shadowMap,
            config = config,
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f)
        )

        assertEquals(Color.RED, bitmap.getColor(1, 1))
        assertFloatEquals3d(0.2f, zBuffer[1 * 5 + 1])

        val farTriangle = nearTriangle.map {
            it.copy(screenPos = it.screenPos.copy(z = 0.8f))
        }
        rasterizeTriangleMain(
            vertices = farTriangle,
            width = 5,
            height = 5,
            zBuffer = zBuffer,
            target = BitmapRenderTarget(bitmap),
            texture = null,
            baseColor = Color.BLUE,
            normal = Vec3(0f, 0f, 1f),
            lightVP = Mat4(),
            shadowMap = shadowMap,
            config = config,
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f)
        )

        assertEquals(Color.RED, bitmap.getColor(1, 1))
        assertFloatEquals3d(0.2f, zBuffer[1 * 5 + 1])
    }

    @Test
    fun mainRasterizerInterpolatesNdcDepthLinearlyInScreenSpace() {
        val target = bitmap(5, 5, Color.BLUE)
        val zBuffer = FloatArray(25) { Float.POSITIVE_INFINITY }
        zBuffer[1 * 5 + 1] = 0.4f
        val vertices = listOf(
            ShadedVertex(Vec3(0f, 0f, 0f), Vec3(0f, 0f, 0f), 10f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(4f, 0f, 0.8f), Vec3(1f, 0f, 0f), 1f, Vec2(1f, 0f)),
            ShadedVertex(Vec3(0f, 4f, 0.8f), Vec3(0f, 1f, 0f), 1f, Vec2(0f, 1f))
        )

        rasterizeTriangleMain(
            vertices = vertices,
            width = 5,
            height = 5,
            zBuffer = zBuffer,
            target = BitmapRenderTarget(target),
            texture = null,
            baseColor = Color.RED,
            normal = Vec3(0f, 0f, 1f),
            lightVP = Mat4(),
            shadowMap = ShadowMap(4, 4),
            config = RenderConfig(
                width = 5,
                height = 5,
                camera = OrbitCamera(distance = 5f),
                lightDirection = Vec3(0f, 0f, 1f),
                lightIntensity = 1f
            ),
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f)
        )

        assertEquals(Color.BLUE, target.getColor(1, 1), "深度应按屏幕空间线性插值，不能被 oneOverW 再次透视校正")
        assertFloatEquals3d(0.4f, zBuffer[1 * 5 + 1])
    }

    @Test
    fun degenerateTrianglesAndSkippedFacesDoNotWritePixels() {
        val target = bitmap(5, 5)
        val zBuffer = FloatArray(25) { Float.POSITIVE_INFINITY }
        val config = RenderConfig(
            width = 5,
            height = 5,
            camera = OrbitCamera(distance = 5f),
            lightDirection = Vec3(0f, 0f, 1f),
            lightIntensity = 1f
        )
        val collinear = listOf(
            ShadedVertex(Vec3(1f, 1f, 0.2f), Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(2f, 2f, 0.2f), Vec3(1f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(3f, 3f, 0.2f), Vec3(0f, 1f, 0f), 1f, Vec2(0f, 0f))
        )

        rasterizeTriangleMain(
            vertices = collinear,
            width = 5,
            height = 5,
            zBuffer = zBuffer,
            target = BitmapRenderTarget(target),
            texture = null,
            baseColor = Color.RED,
            normal = Vec3(0f, 0f, 1f),
            lightVP = Mat4(),
            shadowMap = ShadowMap(4, 4),
            config = config,
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f)
        )
        rasterizeTriangleShadow(
            p0 = Vec3(1f, 1f, 0.2f),
            p1 = Vec3(2f, 2f, 0.2f),
            p2 = Vec3(3f, 3f, 0.2f),
            shadowMap = ShadowMap(4, 4)
        )

        renderMeshMainPass(
            mesh = Mesh(
                vertices = listOf(Vertex(Vec3(0f, 0f, 0f), Vec2(0f, 0f))),
                faces = listOf(Face(listOf(0), Color.BLUE))
            ),
            vpMatrix = Mat4(),
            lightVP = Mat4(),
            width = 5,
            height = 5,
            zBuffer = zBuffer,
            target = BitmapRenderTarget(target),
            shadowMap = ShadowMap(4, 4),
            config = config,
            viewPos = Vec3(0f, 0f, 5f)
        )

        assertFalse(target.any { Color.getA(it) > 0 }, "退化三角形和无效面不应写入像素")
        assertTrue(zBuffer.all { it.isInfinite() }, "退化三角形和无效面不应写入深度")
    }

    @Test
    fun mainRasterizerSkipsTransparentTexturePixels() {
        val target = bitmap(5, 5)
        val zBuffer = FloatArray(25) { Float.POSITIVE_INFINITY }
        val transparentTexture = bitmap(1, 1, Color.TRANSPARENT)
        val vertices = listOf(
            ShadedVertex(Vec3(1f, 1f, 0.2f), Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(3f, 1f, 0.2f), Vec3(1f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(1f, 3f, 0.2f), Vec3(0f, 1f, 0f), 1f, Vec2(0f, 0f))
        )

        rasterizeTriangleMain(
            vertices = vertices,
            width = 5,
            height = 5,
            zBuffer = zBuffer,
            target = BitmapRenderTarget(target),
            texture = BitmapRenderTexture(transparentTexture),
            baseColor = Color.RED,
            normal = Vec3(0f, 0f, 1f),
            lightVP = Mat4(),
            shadowMap = ShadowMap(4, 4),
            config = RenderConfig(
                width = 5,
                height = 5,
                camera = OrbitCamera(distance = 5f),
                lightDirection = Vec3(0f, 0f, 1f),
                lightIntensity = 1f
            ),
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f)
        )

        assertEquals(Color.TRANSPARENT, target.getColor(1, 1))
        assertTrue(zBuffer[1 * 5 + 1].isInfinite(), "透明纹理像素不应写入深度")
    }

    @Test
    fun mainRasterizerBlendsSemitransparentPixelsWithoutReplacingDepth() {
        val target = bitmap(5, 5)
        val zBuffer = FloatArray(25) { Float.POSITIVE_INFINITY }
        val vertices = listOf(
            ShadedVertex(Vec3(1f, 1f, 0.8f), Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(3f, 1f, 0.8f), Vec3(1f, 0f, 0f), 1f, Vec2(1f, 0f)),
            ShadedVertex(Vec3(1f, 3f, 0.8f), Vec3(0f, 1f, 0f), 1f, Vec2(0f, 1f))
        )
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
            target = BitmapRenderTarget(target),
            texture = null,
            baseColor = Color.BLUE,
            normal = Vec3(0f, 0f, 1f),
            lightVP = Mat4(),
            shadowMap = ShadowMap(4, 4),
            config = config,
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f)
        )

        val semitransparentNear = vertices.map {
            it.copy(screenPos = it.screenPos.copy(z = 0.2f))
        }
        rasterizeTriangleMain(
            vertices = semitransparentNear,
            width = 5,
            height = 5,
            zBuffer = zBuffer,
            target = BitmapRenderTarget(target),
            texture = null,
            baseColor = Color.makeARGB(128, 255, 0, 0),
            normal = Vec3(0f, 0f, 1f),
            lightVP = Mat4(),
            shadowMap = ShadowMap(4, 4),
            config = config,
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f)
        )

        val mixed = target.getColor(1, 1)
        assertTrue(Color.getR(mixed) > 0 && Color.getB(mixed) > 0, "半透明像素应与已有颜色混合")
        assertFloatEquals3d(0.8f, zBuffer[1 * 5 + 1])
    }

    @Test
    fun renderPassesDrawSemitransparentFacesAfterOpaqueFaces() {
        fun triangleAtDepth(depth: Float, color: Int) = Mesh(
            vertices = listOf(
                Vertex(Vec3(-1f, -1f, depth), Vec2(0f, 0f)),
                Vertex(Vec3(1f, -1f, depth), Vec2(1f, 0f)),
                Vertex(Vec3(0f, 1f, depth), Vec2(0.5f, 1f))
            ),
            faces = listOf(Face(listOf(0, 1, 2), color))
        )

        val target = bitmap(16, 16)
        val zBuffer = FloatArray(16 * 16) { Float.POSITIVE_INFINITY }
        val config = RenderConfig(
            width = 16,
            height = 16,
            camera = OrbitCamera(distance = 5f),
            lightDirection = Vec3(0f, 0f, 1f),
            lightIntensity = 1f
        )
        val transparentNear = triangleAtDepth(0.2f, Color.makeARGB(128, 255, 0, 0))
        val opaqueFar = triangleAtDepth(0.8f, Color.BLUE)

        listOf(transparentNear, opaqueFar).forEach {
            renderMeshMainPass(
                mesh = it,
                vpMatrix = Mat4(),
                lightVP = Mat4(),
                width = 16,
                height = 16,
                zBuffer = zBuffer,
                target = BitmapRenderTarget(target),
                shadowMap = ShadowMap(4, 4),
                config = config,
                viewPos = Vec3(0f, 0f, 5f),
                renderPass = MainRenderPass.OPAQUE_ONLY
            )
        }
        listOf(transparentNear, opaqueFar).forEach {
            renderMeshMainPass(
                mesh = it,
                vpMatrix = Mat4(),
                lightVP = Mat4(),
                width = 16,
                height = 16,
                zBuffer = zBuffer,
                target = BitmapRenderTarget(target),
                shadowMap = ShadowMap(4, 4),
                config = config,
                viewPos = Vec3(0f, 0f, 5f),
                renderPass = MainRenderPass.TRANSPARENT_ONLY
            )
        }

        val mixed = target.getColor(8, 8)
        assertTrue(Color.getR(mixed) > 0 && Color.getB(mixed) > 0, "半透明面应在不透明面之后混合")
        assertFloatEquals3d(0.8f, zBuffer[8 * 16 + 8])
    }

    @Test
    fun mainRasterizerAppliesShadowFactorAndIgnoresOutOfRangeShadowLookup() {
        val vertices = listOf(
            ShadedVertex(Vec3(1f, 1f, 0.2f), Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(3f, 1f, 0.2f), Vec3(1f, 0f, 0f), 1f, Vec2(1f, 0f)),
            ShadedVertex(Vec3(1f, 3f, 0.2f), Vec3(0f, 1f, 0f), 1f, Vec2(0f, 1f))
        )
        val config = RenderConfig(
            width = 5,
            height = 5,
            camera = OrbitCamera(distance = 5f),
            lightDirection = Vec3(0f, 0f, 1f),
            lightIntensity = 0.2f
        )
        val shadowMap = ShadowMap(4, 4).apply { set(2, 2, -0.5f) }
        val shadowed = bitmap(5, 5)
        rasterizeTriangleMain(
            vertices = vertices,
            width = 5,
            height = 5,
            zBuffer = FloatArray(25) { Float.POSITIVE_INFINITY },
            target = BitmapRenderTarget(shadowed),
            texture = null,
            baseColor = Color.makeRGB(100, 100, 100),
            normal = Vec3(0f, 0f, 1f),
            lightVP = Mat4(),
            shadowMap = shadowMap,
            config = config,
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f)
        )

        val outOfRange = bitmap(5, 5)
        val outsideShadowLookup = Mat4(FloatArray(16).apply {
            this[0] = 1f
            this[5] = 1f
            this[10] = 1f
            this[12] = 4f
            this[15] = 1f
        })
        rasterizeTriangleMain(
            vertices = vertices,
            width = 5,
            height = 5,
            zBuffer = FloatArray(25) { Float.POSITIVE_INFINITY },
            target = BitmapRenderTarget(outOfRange),
            texture = null,
            baseColor = Color.makeRGB(100, 100, 100),
            normal = Vec3(0f, 0f, 1f),
            lightVP = outsideShadowLookup,
            shadowMap = shadowMap,
            config = config,
            lightDir = Vec3(0f, 0f, 1f),
            viewDir = Vec3(0f, 0f, 1f)
        )

        assertTrue(
            Color.getR(shadowed.getColor(1, 1)) < Color.getR(outOfRange.getColor(1, 1)),
            "命中阴影贴图时像素应比越界阴影查询更暗"
        )
    }

    @Test
    fun mainRasterizerCanDisableShadowLookup() {
        val vertices = listOf(
            ShadedVertex(Vec3(1f, 1f, 0.2f), Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(3f, 1f, 0.2f), Vec3(1f, 0f, 0f), 1f, Vec2(1f, 0f)),
            ShadedVertex(Vec3(1f, 3f, 0.2f), Vec3(0f, 1f, 0f), 1f, Vec2(0f, 1f))
        )
        val shadowMap = ShadowMap(4, 4).apply { set(2, 2, -0.5f) }
        val withShadows = bitmap(5, 5)
        val withoutShadows = bitmap(5, 5)
        val config = RenderConfig(
            width = 5,
            height = 5,
            camera = OrbitCamera(distance = 5f),
            lightDirection = Vec3(0f, 0f, 1f),
            lightIntensity = 0.2f
        )

        listOf(withShadows to config, withoutShadows to config.copy(enableShadows = false)).forEach { (target, renderConfig) ->
            rasterizeTriangleMain(
                vertices = vertices,
                width = 5,
                height = 5,
                zBuffer = FloatArray(25) { Float.POSITIVE_INFINITY },
                target = BitmapRenderTarget(target),
                texture = null,
                baseColor = Color.makeRGB(100, 100, 100),
                normal = Vec3(0f, 0f, 1f),
                lightVP = Mat4(),
                shadowMap = shadowMap,
                config = renderConfig,
                lightDir = Vec3(0f, 0f, 1f),
                viewDir = Vec3(0f, 0f, 1f)
            )
        }

        assertTrue(
            Color.getR(withoutShadows.getColor(1, 1)) > Color.getR(withShadows.getColor(1, 1)),
            "关闭阴影后应忽略 shadowMap 遮挡"
        )
    }

    @Test
    fun mainRasterizerCanDisableShadowReceivingPerMesh() {
        val vertices = listOf(
            ShadedVertex(Vec3(1f, 1f, 0.2f), Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(3f, 1f, 0.2f), Vec3(1f, 0f, 0f), 1f, Vec2(1f, 0f)),
            ShadedVertex(Vec3(1f, 3f, 0.2f), Vec3(0f, 1f, 0f), 1f, Vec2(0f, 1f))
        )
        val shadowMap = ShadowMap(4, 4).apply { set(2, 2, -0.5f) }
        val receiver = bitmap(5, 5)
        val nonReceiver = bitmap(5, 5)
        val config = RenderConfig(
            width = 5,
            height = 5,
            camera = OrbitCamera(distance = 5f),
            lightDirection = Vec3(0f, 0f, 1f),
            lightIntensity = 0.2f
        )

        listOf(receiver to true, nonReceiver to false).forEach { (target, receivesShadow) ->
            rasterizeTriangleMain(
                vertices = vertices,
                width = 5,
                height = 5,
                zBuffer = FloatArray(25) { Float.POSITIVE_INFINITY },
                target = BitmapRenderTarget(target),
                texture = null,
                baseColor = Color.makeRGB(100, 100, 100),
                normal = Vec3(0f, 0f, 1f),
                lightVP = Mat4(),
                shadowMap = shadowMap,
                config = config,
                lightDir = Vec3(0f, 0f, 1f),
                viewDir = Vec3(0f, 0f, 1f),
                receivesShadow = receivesShadow
            )
        }

        assertTrue(
            Color.getR(nonReceiver.getColor(1, 1)) > Color.getR(receiver.getColor(1, 1)),
            "不接收阴影的网格不应被 shadowMap 变暗"
        )
    }

    @Test
    fun mainRasterizerIgnoresSelfShadowFromSameFace() {
        val vertices = listOf(
            ShadedVertex(Vec3(1f, 1f, 0.2f), Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(Vec3(3f, 1f, 0.2f), Vec3(1f, 0f, 0f), 1f, Vec2(1f, 0f)),
            ShadedVertex(Vec3(1f, 3f, 0.2f), Vec3(0f, 1f, 0f), 1f, Vec2(0f, 1f))
        )
        val selfShadowFaceId = 12
        val shadowMap = ShadowMap(4, 4).apply { set(2, 2, -0.5f, selfShadowFaceId) }
        val sameFace = bitmap(5, 5)
        val differentFace = bitmap(5, 5)
        val config = RenderConfig(
            width = 5,
            height = 5,
            camera = OrbitCamera(distance = 5f),
            lightDirection = Vec3(0f, 0f, 1f),
            lightIntensity = 0.2f
        )

        listOf(sameFace to selfShadowFaceId, differentFace to selfShadowFaceId + 1).forEach { (target, receiverFaceId) ->
            rasterizeTriangleMain(
                vertices = vertices,
                width = 5,
                height = 5,
                zBuffer = FloatArray(25) { Float.POSITIVE_INFINITY },
                target = BitmapRenderTarget(target),
                texture = null,
                baseColor = Color.makeRGB(100, 100, 100),
                normal = Vec3(0f, 0f, 1f),
                lightVP = Mat4(),
                shadowMap = shadowMap,
                config = config,
                lightDir = Vec3(0f, 0f, 1f),
                viewDir = Vec3(0f, 0f, 1f),
                receiverFaceId = receiverFaceId
            )
        }

        assertTrue(
            Color.getR(sameFace.getColor(1, 1)) > Color.getR(differentFace.getColor(1, 1)),
            "同一面写入的 shadowMap 深度不应让该面产生自阴影波纹"
        )
    }

    @Test
    fun shadowDepthBiasOnlySlightlyIncreasesOnGrazingReceivers() {
        val lightDir = Vec3(0.45f, 1f, 0.55f).normalized()

        assertEquals(DEFAULT_SHADOW_BIAS, calculateShadowDepthBias(lightDir, lightDir), 0.000001f)
        assertTrue(
            calculateShadowDepthBias(Vec3(1f, 0f, 0f), lightDir) < 0.001f,
            "侧面接收阴影时不应把基础 bias 放大到产生明显接触偏移"
        )
        assertEquals(
            DEFAULT_SHADOW_BIAS * 1.5f,
            calculateShadowDepthBias(Vec3(-1f, 0f, 0f), lightDir),
            0.000001f
        )
    }

    @Test
    fun renderShadowPassSkipsInvalidAndBehindLightFaces() {
        val shadowMap = ShadowMap(8, 8)
        val negativeW = Mat4(FloatArray(16).apply {
            this[0] = 1f
            this[5] = 1f
            this[10] = 1f
            this[15] = -1f
        })
        val mesh = Mesh(
            vertices = listOf(
                Vertex(Vec3(-0.5f, -0.5f, 0f), Vec2(0f, 0f)),
                Vertex(Vec3(0.5f, -0.5f, 0f), Vec2(0f, 0f)),
                Vertex(Vec3(0f, 0.5f, 0f), Vec2(0f, 0f))
            ),
            faces = listOf(
                Face(listOf(0), Color.RED),
                Face(listOf(0, 1, 2), Color.RED)
            )
        )

        renderShadowPass(listOf(mesh), negativeW, shadowMap)

        assertTrue(shadowMap.buffer.all { it.isInfinite() }, "无效面和光源背后的面不应写入阴影深度")
    }

    @Test
    fun renderShadowPassSkipsMeshesThatDoNotCastShadow() {
        val shadowMap = ShadowMap(8, 8)
        val receiverOnly = simpleTriangleMesh(Color.RED).copy(castsShadow = false)

        renderShadowPass(listOf(receiverOnly), Mat4(), shadowMap)

        assertTrue(shadowMap.buffer.all { it.isInfinite() }, "不投影的网格不应写入 shadowMap")
    }

    @Test
    fun backFaceCullingKeepsFrontFacesAndSkipsBackFaces() {
        val frontTarget = bitmap(16, 16)
        val frontZBuffer = FloatArray(16 * 16) { Float.POSITIVE_INFINITY }
        val config = RenderConfig(
            width = 16,
            height = 16,
            camera = OrbitCamera(distance = 5f),
            useBackFaceCulling = true,
            lightDirection = Vec3(0f, 0f, 1f),
            lightIntensity = 1f
        )

        renderMeshMainPass(
            mesh = simpleTriangleMesh(Color.RED),
            vpMatrix = Mat4(),
            lightVP = Mat4(),
            width = 16,
            height = 16,
            zBuffer = frontZBuffer,
            target = BitmapRenderTarget(frontTarget),
            shadowMap = ShadowMap(4, 4),
            config = config,
            viewPos = Vec3(0f, 0f, 5f)
        )

        assertTrue(frontTarget.any { Color.getA(it) > 0 }, "开启背面剔除后朝向相机的面应保留")

        val backTarget = bitmap(16, 16)
        val backZBuffer = FloatArray(16 * 16) { Float.POSITIVE_INFINITY }
        val backFacingMesh = Mesh(
            vertices = simpleTriangleMesh().vertices,
            faces = listOf(Face(listOf(0, 2, 1), Color.RED))
        )

        renderMeshMainPass(
            mesh = backFacingMesh,
            vpMatrix = Mat4(),
            lightVP = Mat4(),
            width = 16,
            height = 16,
            zBuffer = backZBuffer,
            target = BitmapRenderTarget(backTarget),
            shadowMap = ShadowMap(4, 4),
            config = config,
            viewPos = Vec3(0f, 0f, 5f)
        )

        assertFalse(backTarget.any { Color.getA(it) > 0 }, "开启背面剔除后背向相机的面应跳过")
        assertTrue(backZBuffer.all { it.isInfinite() })
    }

    @Test
    fun backFaceCullingDoesNotDependOnSingleVertexViewDirection() {
        val target = bitmap(16, 16)
        val zBuffer = FloatArray(16 * 16) { Float.POSITIVE_INFINITY }

        renderMeshMainPass(
            mesh = simpleTriangleMesh(Color.RED),
            vpMatrix = Mat4(),
            lightVP = Mat4(),
            width = 16,
            height = 16,
            zBuffer = zBuffer,
            target = BitmapRenderTarget(target),
            shadowMap = ShadowMap(4, 4),
            config = RenderConfig(
                width = 16,
                height = 16,
                camera = OrbitCamera(distance = 5f),
                useBackFaceCulling = true,
                lightDirection = Vec3(0f, 0f, 1f),
                lightIntensity = 1f
            ),
            viewPos = Vec3(-1f, -1f, 0f)
        )

        assertTrue(target.any { Color.getA(it) > 0 }, "背面剔除应基于投影 winding，而不是单个顶点到相机的方向")
    }

    @Test
    fun recordingRenderTargetCapturesPixelAndLineCommands() {
        val config = RenderConfig(
            width = 16,
            height = 16,
            camera = OrbitCamera(distance = 5f),
            lightDirection = Vec3(0f, 0f, 1f),
            lightIntensity = 1f
        )
        val filledTarget = RecordingRenderTarget(16, 16)

        renderMeshMainPass(
            mesh = simpleTriangleMesh(Color.RED),
            vpMatrix = Mat4(),
            lightVP = Mat4(),
            width = 16,
            height = 16,
            zBuffer = FloatArray(16 * 16) { Float.POSITIVE_INFINITY },
            target = filledTarget,
            shadowMap = ShadowMap(4, 4),
            config = config,
            viewPos = Vec3(0f, 0f, 5f)
        )

        assertTrue(
            filledTarget.commands.any { it is RenderCommand.Pixel },
            "实体模式应向 RenderTarget 写入像素命令"
        )
        assertEquals(0, filledTarget.commands.count { it is RenderCommand.Line })

        val wireframeTarget = RecordingRenderTarget(16, 16)
        renderMeshMainPass(
            mesh = simpleTriangleMesh(Color.RED),
            vpMatrix = Mat4(),
            lightVP = Mat4(),
            width = 16,
            height = 16,
            zBuffer = FloatArray(16 * 16) { Float.POSITIVE_INFINITY },
            target = wireframeTarget,
            shadowMap = ShadowMap(4, 4),
            config = config.copy(renderFaces = false),
            viewPos = Vec3(0f, 0f, 5f)
        )

        assertEquals(0, wireframeTarget.commands.count { it is RenderCommand.Pixel })
        assertEquals(3, wireframeTarget.commands.count { it is RenderCommand.Line })
        wireframeTarget.commands.filterIsInstance<RenderCommand.Line>().forEach {
            assertEquals(2f, it.strokeWidth)
            assertEquals(true, it.antiAlias)
        }
    }

    @Test
    fun wireframeDrawsOriginalFaceEdgesWithoutTriangulationDiagonals() {
        val quad = Mesh(
            vertices = listOf(
                Vertex(Vec3(-1f, -1f, 0f), Vec2(0f, 0f)),
                Vertex(Vec3(1f, -1f, 0f), Vec2(0f, 0f)),
                Vertex(Vec3(1f, 1f, 0f), Vec2(0f, 0f)),
                Vertex(Vec3(-1f, 1f, 0f), Vec2(0f, 0f))
            ),
            faces = listOf(Face(listOf(0, 1, 2, 3), Color.WHITE))
        )
        val target = RecordingRenderTarget(16, 16)

        renderMeshMainPass(
            mesh = quad,
            vpMatrix = Mat4(),
            lightVP = Mat4(),
            width = 16,
            height = 16,
            zBuffer = FloatArray(16 * 16) { Float.POSITIVE_INFINITY },
            target = target,
            shadowMap = ShadowMap(4, 4),
            config = RenderConfig(
                width = 16,
                height = 16,
                camera = OrbitCamera(distance = 5f),
                renderFaces = false,
                antiAliasingLevel = 1,
                lightDirection = Vec3(0f, 0f, 1f),
                lightIntensity = 1f
            ),
            viewPos = Vec3(0f, 0f, 5f)
        )

        val lines = target.commands.filterIsInstance<RenderCommand.Line>()
        assertEquals(4, lines.size)
        assertFalse(
            lines.any { it.x0 == 4f && it.y0 == 12f && it.x1 == 12f && it.y1 == 4f },
            "线框模式不应绘制 fan triangulation 产生的对角线"
        )
    }

    @Test
    fun renderSceneKeepsBackgroundForEmptySceneAndDrawsTriangleForBothProjections() {
        val empty = renderSceneToImage(
            Scene(emptyList()),
            RenderConfig(
                width = 8,
                height = 8,
                camera = OrbitCamera(distance = 5f),
                backgroundColor = Color.BLUE,
                antiAliasingLevel = 1
            )
        ).toBitmap()
        assertEquals(Color.BLUE, empty.getColor(0, 0))

        listOf(true, false).forEach { usePerspective ->
            val image = renderSceneToImage(
                scene = Scene(listOf(simpleTriangleMesh(Color.GREEN))),
                config = RenderConfig(
                    width = 32,
                    height = 32,
                    camera = OrbitCamera(distance = 5f),
                    usePerspective = usePerspective,
                    backgroundColor = Color.TRANSPARENT,
                    antiAliasingLevel = 2,
                    lightDirection = Vec3(0f, 0f, 1f),
                    lightIntensity = 1f,
                    enableShadows = false
                )
            ).toBitmap()

            assertEquals(32, image.width)
            assertEquals(32, image.height)
            assertTrue(
                image.any { Color.getA(it) > 0 },
                "透视开关为 $usePerspective 时应渲染出非背景像素"
            )
        }
    }

    @Test
    fun renderFacesFalseDrawsWireframeInsteadOfFilledFaces() {
        val filled = renderSceneToImage(
            scene = Scene(listOf(simpleTriangleMesh(Color.RED))),
            config = RenderConfig(
                width = 32,
                height = 32,
                camera = OrbitCamera(distance = 5f),
                renderFaces = true,
                usePerspective = false,
                backgroundColor = Color.TRANSPARENT,
                antiAliasingLevel = 1,
                lightDirection = Vec3(0f, 0f, 1f),
                lightIntensity = 1f,
                enableShadows = false
            )
        ).toBitmap()
        val wireframe = renderSceneToImage(
            scene = Scene(listOf(simpleTriangleMesh(Color.RED))),
            config = RenderConfig(
                width = 32,
                height = 32,
                camera = OrbitCamera(distance = 5f),
                renderFaces = false,
                usePerspective = false,
                backgroundColor = Color.TRANSPARENT,
                antiAliasingLevel = 1,
                lightDirection = Vec3(0f, 0f, 1f),
                lightIntensity = 1f,
                enableShadows = false
            )
        ).toBitmap()

        assertTrue(Color.getA(filled.getColor(16, 16)) > 0, "实体模式应填充三角形中心")
        assertEquals(Color.TRANSPARENT, wireframe.getColor(16, 16))
        assertTrue(
            wireframe.any { Color.getA(it) > 0 },
            "线框模式仍应绘制边线像素"
        )
        assertFalse(
            filled.getColor(16, 16) == wireframe.getColor(16, 16),
            "线框模式不应和实体模式渲染出相同中心像素"
        )
    }
}
