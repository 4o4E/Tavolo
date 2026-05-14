package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.render3d.*
import top.e404.tavolo.util.any
import top.e404.tavolo.util.toBitmap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        listOf(
            withShadows to config,
            withoutShadows to config.copy(enableShadows = false)
        ).forEach { (target, renderConfig) ->
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

        listOf(
            sameFace to selfShadowFaceId,
            differentFace to selfShadowFaceId + 1
        ).forEach { (target, receiverFaceId) ->
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
