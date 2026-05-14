package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.render3d.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

