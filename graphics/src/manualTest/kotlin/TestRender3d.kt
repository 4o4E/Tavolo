package top.e404.tavolo.draw.test

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.Test
import top.e404.tavolo.draw.render3d.*
import top.e404.tavolo.util.FontManager
import top.e404.tavolo.util.toBitmap
import java.io.File
import kotlin.math.pow
import kotlin.random.Random

typealias ColorProvider = (value: Int, week: Int, day: Int) -> Int

class TestRender3d {

    @Test
    fun heatmap() {
        // 生成随机的热力图数据
        fun generateBarData(numWeeks: Int, numDaysPerWeek: Int, maxValue: Int): List<List<Int>> {
            return (0 until numWeeks).map {
                (0 until numDaysPerWeek).map {
                    if (Random.nextInt(3) > 1) Random.nextInt(
                        0,
                        maxValue
                    ) else 0
                }
            }
        }

        // 创建一个非线性的颜色提供器，颜色根据周（色相）和值（亮度）变化
        fun createNonLinearColorProvider(totalWeeks: Int, maxValue: Int): ColorProvider {
            return provider@{ value, week, _ ->
                val hue = 1 - week.toFloat() / totalWeeks
                if (value == 0) return@provider java.awt.Color.HSBtoRGB(hue, 0.5f, 0.3f) or (0xff shl 24)
                val normalizedValue = value.toFloat() / maxValue.toFloat()
                val brightnessCurve = normalizedValue.pow(2.0f)
                val brightness = 0.5f + brightnessCurve * 0.3f
                java.awt.Color.HSBtoRGB(hue, 0.7f, brightness) or (0xff shl 24)
            }
        }
        // 定义热力图参数
        val TOTAL_WEEKS = 54
        val DAYS_PER_WEEK = 7
        val MAX_VALUE = 6
        val barData = generateBarData(TOTAL_WEEKS, DAYS_PER_WEEK, MAX_VALUE)
        val colorProvider = createNonLinearColorProvider(TOTAL_WEEKS, MAX_VALUE)
        val barSize = 1.0f
        val barSpacing = .15f
        // 为每个数据点创建一个长方体Mesh
        val componentMeshes = barData.flatMapIndexed { wi, weekData ->
            weekData.mapIndexed { di, value ->
                val height = if (value == 0) 0.1f else value * 0.3f
                val color = colorProvider(value, wi, di)
                val cuboid = createCuboid(Vec3(barSize, height, barSize), color)
                // 将长方体移动到其在网格中的正确位置
                val x = wi * (barSize + barSpacing)
                val y = height / 2f
                val z = di * (barSize + barSpacing)
                Mesh(cuboid.vertices.map { Vertex(it.position + Vec3(x, y, z), it.uv) }, cuboid.faces)
            }
        }
        // 将所有小长方体合并成一个大Mesh
        val chartMesh = combineMeshes(componentMeshes)
        // 计算图表中心，用于设置相机目标
        val chartWidth = (TOTAL_WEEKS - 1) * (barSize + barSpacing)
        val chartDepth = (DAYS_PER_WEEK - 1) * (barSize + barSpacing)
        val chartCenter = Vec3(chartWidth / 2f, MAX_VALUE * 0.3f / 2f, chartDepth / 2f)
        // 设置相机
        val camera = OrbitCamera(target = chartCenter, yaw = 45f, pitch = 35f, distance = 50f)
        val (viewMatrix, cameraForward) = camera.createViewMatrix()
        val solidBg = Color.makeRGB(0, 0, 15)
        val finalWidth = 1600
        val finalHeight = 1200
        renderSceneToImage(
            Scene(listOf(chartMesh)),
            RenderConfig(
                finalWidth,
                finalHeight,
                camera,
                true,
                false,
                solidBg,
                useBackFaceCulling = true
            )
        ).let { ManualTestSupport.saveImage("render3d/3D-热力图-正交投影实体.png", it) }
        println("--- 3D热力图渲染完成 ---\n")
    }

    /**
     * 将Minecraft模型所有复杂的、硬编码的数据（尺寸、位置、UV坐标）封装起来
     *
     * 实现了数据与逻辑的完全分离，极大地提高了代码的可读性、可维护性和可扩展性
     */
    enum class BodyPart {
        HEAD {
            override fun getDims(isSlim: Boolean): Vec3 {
                return Vec3(8f, 8f, 8f)
            }

            override fun getPos(isSlim: Boolean): Vec3 {
                return Vec3(0f, 20f, 0f)
            }

            override fun getBaseUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(16f, 8f, 8f, 8f),
                    FaceDirection.LEFT to Rect.makeXYWH(0f, 8f, 8f, 8f),
                    FaceDirection.TOP to Rect.makeXYWH(8f, 0f, 8f, 8f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(16f, 0f, 8f, 8f),
                    FaceDirection.FRONT to Rect.makeXYWH(8f, 8f, 8f, 8f),
                    FaceDirection.BACK to Rect.makeXYWH(24f, 8f, 8f, 8f)
                )
            }

            override fun getOverlayUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(48f, 8f, 8f, 8f),
                    FaceDirection.LEFT to Rect.makeXYWH(32f, 8f, 8f, 8f),
                    FaceDirection.TOP to Rect.makeXYWH(40f, 0f, 8f, 8f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(48f, 0f, 8f, 8f),
                    FaceDirection.FRONT to Rect.makeXYWH(40f, 8f, 8f, 8f),
                    FaceDirection.BACK to Rect.makeXYWH(56f, 8f, 8f, 8f)
                )
            }
        },
        BODY {
            override fun getDims(isSlim: Boolean): Vec3 {
                return Vec3(8f, 12f, 4f)
            }

            override fun getPos(isSlim: Boolean): Vec3 {
                return Vec3(0f, 10f, 0f)
            }

            override fun getBaseUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(16f, 20f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(28f, 20f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(20f, 16f, 8f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(28f, 16f, 8f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(20f, 20f, 8f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(32f, 20f, 8f, 12f)
                )
            }

            override fun getOverlayUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(16f, 36f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(28f, 36f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(20f, 32f, 8f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(28f, 32f, 8f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(20f, 36f, 8f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(32f, 36f, 8f, 12f)
                )
            }
        },
        RIGHT_ARM {
            override fun getDims(isSlim: Boolean): Vec3 {
                return if (isSlim) Vec3(3f, 12f, 4f) else Vec3(4f, 12f, 4f)
            }

            override fun getPos(isSlim: Boolean): Vec3 {
                return if (isSlim) Vec3(-5.5f, 10f, 0f) else Vec3(-6f, 10f, 0f)
            }

            override fun getBaseUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return if (isSlim) mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(47f, 20f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(40f, 20f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(44f, 16f, 3f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(47f, 16f, 3f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(44f, 20f, 3f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(51f, 20f, 3f, 12f)
                ) else mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(48f, 20f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(40f, 20f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(44f, 16f, 4f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(48f, 16f, 4f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(44f, 20f, 4f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(52f, 20f, 4f, 12f)
                )
            }

            override fun getOverlayUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return if (isSlim) mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(47f, 36f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(40f, 36f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(44f, 32f, 3f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(47f, 32f, 3f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(44f, 36f, 3f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(51f, 36f, 3f, 12f)
                ) else mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(48f, 36f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(40f, 36f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(44f, 32f, 4f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(48f, 32f, 4f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(44f, 36f, 4f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(52f, 36f, 4f, 12f)
                )
            }
        },
        LEFT_ARM {
            override fun getDims(isSlim: Boolean): Vec3 {
                return if (isSlim) Vec3(3f, 12f, 4f) else Vec3(4f, 12f, 4f)
            }

            override fun getPos(isSlim: Boolean): Vec3 {
                return if (isSlim) Vec3(5.5f, 10f, 0f) else Vec3(6f, 10f, 0f)
            }

            override fun getBaseUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return if (isSlim) mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(39f, 52f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(32f, 52f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(36f, 48f, 3f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(39f, 48f, 3f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(36f, 52f, 3f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(43f, 52f, 3f, 12f)
                ) else mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(40f, 52f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(32f, 52f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(36f, 48f, 4f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(40f, 48f, 4f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(36f, 52f, 4f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(44f, 52f, 4f, 12f)
                )
            }

            override fun getOverlayUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return if (isSlim) mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(55f, 52f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(48f, 52f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(52f, 48f, 3f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(55f, 48f, 3f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(52f, 52f, 3f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(59f, 52f, 3f, 12f)
                ) else mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(56f, 52f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(48f, 52f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(52f, 48f, 4f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(56f, 48f, 4f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(52f, 52f, 4f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(60f, 52f, 4f, 12f)
                )
            }
        },
        RIGHT_LEG {
            override fun getDims(isSlim: Boolean): Vec3 {
                return Vec3(4f, 12f, 4f)
            }

            override fun getPos(isSlim: Boolean): Vec3 {
                return Vec3(-2f, -2f, 0f)
            }

            override fun getBaseUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(8f, 20f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(0f, 20f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(4f, 16f, 4f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(8f, 16f, 4f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(4f, 20f, 4f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(12f, 20f, 4f, 12f)
                )
            }

            override fun getOverlayUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(8f, 36f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(0f, 36f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(4f, 32f, 4f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(8f, 32f, 4f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(4f, 36f, 4f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(12f, 36f, 4f, 12f)
                )
            }
        },
        LEFT_LEG {
            override fun getDims(isSlim: Boolean): Vec3 {
                return Vec3(4f, 12f, 4f)
            }

            override fun getPos(isSlim: Boolean): Vec3 {
                return Vec3(2f, -2f, 0f)
            }

            override fun getBaseUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(24f, 52f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(16f, 52f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(20f, 48f, 4f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(24f, 48f, 4f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(20f, 52f, 4f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(28f, 52f, 4f, 12f)
                )
            }

            override fun getOverlayUVs(isSlim: Boolean): Map<FaceDirection, Rect> {
                return mapOf(
                    FaceDirection.RIGHT to Rect.makeXYWH(8f, 52f, 4f, 12f),
                    FaceDirection.LEFT to Rect.makeXYWH(0f, 52f, 4f, 12f),
                    FaceDirection.TOP to Rect.makeXYWH(4f, 48f, 4f, 4f),
                    FaceDirection.BOTTOM to Rect.makeXYWH(8f, 48f, 4f, 4f),
                    FaceDirection.FRONT to Rect.makeXYWH(4f, 52f, 4f, 12f),
                    FaceDirection.BACK to Rect.makeXYWH(12f, 52f, 4f, 12f)
                )
            }
        };

        // 抽象方法，强制每个身体部件都必须提供自己的尺寸、位置和UV数据
        abstract fun getDims(isSlim: Boolean): Vec3
        abstract fun getPos(isSlim: Boolean): Vec3
        abstract fun getBaseUVs(isSlim: Boolean): Map<FaceDirection, Rect>
        abstract fun getOverlayUVs(isSlim: Boolean): Map<FaceDirection, Rect>?
    }

    @Test
    fun minecraftRightLimbUvsUseGeometricFaceDirections() {
        assertRectLeft(48f, BodyPart.RIGHT_ARM.getBaseUVs(isSlim = false).getValue(FaceDirection.RIGHT))
        assertRectLeft(40f, BodyPart.RIGHT_ARM.getBaseUVs(isSlim = false).getValue(FaceDirection.LEFT))
        assertRectLeft(47f, BodyPart.RIGHT_ARM.getBaseUVs(isSlim = true).getValue(FaceDirection.RIGHT))
        assertRectLeft(40f, BodyPart.RIGHT_ARM.getBaseUVs(isSlim = true).getValue(FaceDirection.LEFT))
        assertRectLeft(48f, BodyPart.RIGHT_ARM.getOverlayUVs(isSlim = false)!!.getValue(FaceDirection.RIGHT))
        assertRectLeft(40f, BodyPart.RIGHT_ARM.getOverlayUVs(isSlim = false)!!.getValue(FaceDirection.LEFT))
        assertRectLeft(47f, BodyPart.RIGHT_ARM.getOverlayUVs(isSlim = true)!!.getValue(FaceDirection.RIGHT))
        assertRectLeft(40f, BodyPart.RIGHT_ARM.getOverlayUVs(isSlim = true)!!.getValue(FaceDirection.LEFT))
        assertRectLeft(8f, BodyPart.RIGHT_LEG.getBaseUVs(isSlim = false).getValue(FaceDirection.RIGHT))
        assertRectLeft(0f, BodyPart.RIGHT_LEG.getBaseUVs(isSlim = false).getValue(FaceDirection.LEFT))
        assertRectLeft(8f, BodyPart.RIGHT_LEG.getOverlayUVs(isSlim = false)!!.getValue(FaceDirection.RIGHT))
        assertRectLeft(0f, BodyPart.RIGHT_LEG.getOverlayUVs(isSlim = false)!!.getValue(FaceDirection.LEFT))
    }

    private fun assertRectLeft(expected: Float, actual: Rect) {
        kotlin.test.assertEquals(expected, actual.left)
    }

    /**
     * 根据皮肤贴图和模型类型（标准/Slim）创建完整的Minecraft玩家模型。
     */
    fun createMinecraftPlayer(skin: Bitmap, isSlim: Boolean): Mesh {
        val texW = skin.width.toFloat()
        val texH = skin.height.toFloat()
        val componentMeshes = mutableListOf<Mesh>()
        // 外层皮肤的膨胀量
        val overlayAmount = 0.25f
        val headOverlayAmount = 1.0f // 头部外层膨胀量更接近 Minecraft 皮肤外层效果

        // 遍历所有身体部件
        for (part in BodyPart.entries) {
            val dims = part.getDims(isSlim)
            val pos = part.getPos(isSlim)
            val baseUVs = part.getBaseUVs(isSlim)
            val overlayUVs = part.getOverlayUVs(isSlim)

            // 创建并添加基础层
            val baseCuboid = createUVCuboid(dims, baseUVs, texW, texH)
            componentMeshes.add(Mesh(baseCuboid.vertices.map { Vertex(it.position + pos, it.uv) }, baseCuboid.faces))

            // 如果有外层定义，则创建并添加外层
            overlayUVs?.let { uvs ->
                val overlaySize = if (part == BodyPart.HEAD) headOverlayAmount else overlayAmount
                // 外层的尺寸比基础层稍大
                val overlayCuboid = createUVCuboid(dims + Vec3(overlaySize, overlaySize, overlaySize), uvs, texW, texH)
                componentMeshes.add(
                    Mesh(
                        overlayCuboid.vertices.map { Vertex(it.position + pos, it.uv) },
                        overlayCuboid.faces
                    )
                )
            }
        }
        // 将所有部件的Mesh合并成一个，并附上皮肤纹理
        return combineMeshes(componentMeshes, skin)
    }

    /**
     * 运行Minecraft皮肤渲染示例的函数。
     */
    fun runMinecraftSkinExample(modelName: String, skinFileName: String, isSlim: Boolean) {
        val skinFile = File(skinFileName)
        if (!skinFile.exists()) {
            error("错误: $skinFileName 文件未找到。请将皮肤贴图放置在运行目录下。")
        }
        // 从文件加载皮肤贴图
        val skinBitmap = Bitmap.makeFromImage(Image.makeFromEncoded(skinFile.readBytes()))
        // 创建玩家模型
        val playerMesh = createMinecraftPlayer(skinBitmap, isSlim)
        // 设置相机
        val camera = OrbitCamera(
            target = Vec3(0f, 12f, 0f),
            yaw = 45f,
            pitch = 20f,
            distance = 60f
        )
        val (_, cameraForward) = camera.createViewMatrix()
        val cameraRight = cameraForward.cross(camera.upVector).normalized()
        val cameraUp = cameraRight.cross(cameraForward).normalized()
        val skinLightDirection = ((-cameraForward) + (-cameraRight * 0.85f) + (cameraUp * 0.85f)).normalized()
        val solidBg = Color.makeRGB(80, 100, 130)
        val wireframeBg = Color.makeRGB(20, 25, 30)
        val finalWidth = 800
        val finalHeight = 1200
        // 渲染四种不同模式的图像
        // 注意：对于皮肤模型，背面剔除通常是关闭的(useBackFaceCulling = false)，因为外层皮肤的内侧也可能需要被看到。
        renderSceneToImage(
            Scene(listOf(playerMesh)),
            RenderConfig(
                finalWidth,
                finalHeight,
                camera,
                true,
                true,
                solidBg,
                useBackFaceCulling = false,
                lightDirection = skinLightDirection
            )
        ).let { ManualTestSupport.saveImage("render3d/3D-皮肤模型-${modelName}-透视投影实体.png", it) }
        renderSceneToImage(
            Scene(listOf(playerMesh)),
            RenderConfig(
                finalWidth,
                finalHeight,
                camera,
                false,
                true,
                wireframeBg,
                useBackFaceCulling = false,
                lightDirection = skinLightDirection
            )
        ).let { ManualTestSupport.saveImage("render3d/3D-皮肤模型-${modelName}-透视投影线框.png", it) }
    }

    private data class SkinView(val yaw: Float, val pitch: Float)

    private fun loadMinecraftPlayer(skinFileName: String, isSlim: Boolean): Mesh {
        val skinFile = File(skinFileName)
        if (!skinFile.exists()) {
            error("错误: $skinFileName 文件未找到。请将皮肤贴图放置在运行目录中。")
        }
        val skinBitmap = Bitmap.makeFromImage(Image.makeFromEncoded(skinFile.readBytes()))
        return createMinecraftPlayer(skinBitmap, isSlim)
    }

    private fun skinCamera(view: SkinView): OrbitCamera =
        OrbitCamera(target = Vec3(0f, 10f, 0f), yaw = view.yaw, pitch = view.pitch, distance = 48f)

    private fun skinFloor(y: Float = -8.2f): Mesh =
        createPlane(
            center = Vec3(0f, y, 0f),
            size = Vec2(24f, 24f),
            color = Color.makeRGB(90, 105, 125),
            normalDirection = Vec3(0f, 1f, 0f),
            segments = 1,
            castsShadow = false
        )

    private fun skinLightDirection(camera: OrbitCamera): Vec3 {
        val (_, cameraForward) = camera.createViewMatrix()
        val cameraRight = cameraForward.cross(camera.upVector).normalized()
        val cameraUp = cameraRight.cross(cameraForward).normalized()
        return ((-cameraForward) + (-cameraRight * 0.85f) + (cameraUp * 0.85f)).normalized()
    }

    private fun runMinecraftSkinGallery(modelName: String, skinFileName: String, isSlim: Boolean) {
        val playerMesh = loadMinecraftPlayer(skinFileName, isSlim)
        val panelWidth = 420
        val imageHeight = 620
        val titleHeight = 38
        val panelHeight = titleHeight + imageHeight
        val titleFont = Font(FontManager.resolve(ManualTestSupport.uiFont), 20f)
        val paint = Paint().apply { isAntiAlias = true }
        val yaws = listOf(-90f, -60f, -45f, -30f, 0f, 45f, 90f)
        val pitches = listOf(-20f, -10f, -5f, 5f, 10f, 20f, 35f)
        val views = pitches.flatMap { pitch -> yaws.map { yaw -> SkinView(yaw, pitch) } }
        val panels = views.map { view ->
            val camera = skinCamera(view)
            "yaw ${view.yaw.toInt()} / pitch ${view.pitch.toInt()}" to renderSceneToImage(
                Scene(listOf(playerMesh, skinFloor())),
                RenderConfig(
                    width = panelWidth,
                    height = imageHeight,
                    camera = camera,
                    renderFaces = true,
                    usePerspective = true,
                    backgroundColor = Color.makeRGB(74, 91, 112),
                    useBackFaceCulling = false,
                    antiAliasingLevel = 4,
                    lightDirection = skinLightDirection(camera),
                    lightIntensity = 0.42f,
                    enableShadows = true,
                    shadowMapSize = 4096,
                    shadowBias = 0.02f,
                    shadowOrthoSize = 42f
                )
            )
        }

        val columns = yaws.size
        val rows = pitches.size
        val image = Surface.makeRasterN32Premul(panelWidth * columns, panelHeight * rows).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.makeRGB(46, 61, 80))
            panels.forEachIndexed { index, (title, panel) ->
                val col = index % columns
                val row = index / columns
                val x = col * panelWidth
                val y = row * panelHeight
                paint.color = Color.makeRGB(35, 45, 60)
                canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), panelWidth.toFloat(), titleHeight.toFloat()), paint)
                paint.color = Color.makeRGB(232, 238, 246)
                canvas.drawString(title, x + 14f, y + 23f, titleFont, paint)
                canvas.drawImage(panel, x.toFloat(), y + titleHeight.toFloat())
            }
            surface.makeImageSnapshot()
        }
        ManualTestSupport.saveImage("render3d/3D-皮肤模型-${modelName}-多视角实体.png", image)
    }

    private fun renderSkinDiagnosticImage(scene: Scene, camera: OrbitCamera, antiAliasingLevel: Int = 4): Image =
        renderSceneToImage(
            scene,
            RenderConfig(
                width = 420,
                height = 620,
                camera = camera,
                renderFaces = true,
                usePerspective = true,
                backgroundColor = Color.makeRGB(74, 91, 112),
                useBackFaceCulling = false,
                antiAliasingLevel = antiAliasingLevel,
                lightDirection = skinLightDirection(camera),
                lightIntensity = 0.42f,
                enableShadows = true,
                shadowMapSize = 4096,
                shadowBias = 0.02f,
                shadowOrthoSize = 42f
            )
        )

    private fun createSkinDifferenceImage(combined: Image, playerOnly: Image, background: Int): Image {
        val combinedBitmap = combined.toBitmap()
        val playerBitmap = playerOnly.toBitmap()
        val diffBitmap = Bitmap()
        diffBitmap.allocN32Pixels(combinedBitmap.width, combinedBitmap.height)
        val target = BitmapRenderTarget(diffBitmap)
        target.clear(Color.makeRGB(24, 30, 38))
        for (y in 0 until combinedBitmap.height) {
            for (x in 0 until combinedBitmap.width) {
                val playerColor = playerBitmap.getColor(x, y)
                val combinedColor = combinedBitmap.getColor(x, y)
                val playerVisible = colorDistance(playerColor, background) > 12
                val changed = colorDistance(playerColor, combinedColor) > 24
                if (playerVisible && changed) {
                    target.setPixel(x, y, Color.makeARGB(220, 255, 40, 40))
                } else if (playerVisible) {
                    target.setPixel(x, y, Color.makeARGB(255, 210, 215, 220))
                } else {
                    target.setPixel(x, y, combinedColor)
                }
            }
        }
        return Image.makeFromBitmap(diffBitmap)
    }

    private fun colorDistance(a: Int, b: Int): Int =
        kotlin.math.abs(Color.getR(a) - Color.getR(b)) +
            kotlin.math.abs(Color.getG(a) - Color.getG(b)) +
            kotlin.math.abs(Color.getB(a) - Color.getB(b))

    @Test
    fun skinFootDebug() {
        val playerMesh = loadMinecraftPlayer("alex_skin.png", isSlim = true)
        val floorLevels = listOf(-8.125f, -8.2f)
        val views = listOf(SkinView(-45f, -20f), SkinView(-45f, 5f))
        val panelWidth = 420
        val imageHeight = 620
        val titleHeight = 38
        val panelHeight = titleHeight + imageHeight
        val titleFont = Font(FontManager.resolve(ManualTestSupport.uiFont), 20f)
        val paint = Paint().apply { isAntiAlias = true }
        val background = Color.makeRGB(74, 91, 112)
        val columns = listOf("AA4合成", "AA4仅玩家", "AA4差异", "AA1合成", "AA1仅玩家", "AA1差异")
        val rows = floorLevels.flatMap { floorY -> views.map { floorY to it } }.map { (floorY, view) ->
            val floorMesh = skinFloor(floorY)
            val camera = skinCamera(view)
            val combined = renderSkinDiagnosticImage(Scene(listOf(playerMesh, floorMesh)), camera)
            val playerOnly = renderSkinDiagnosticImage(Scene(listOf(playerMesh)), camera)
            val diff = createSkinDifferenceImage(combined, playerOnly, background)
            val combinedNoAa = renderSkinDiagnosticImage(Scene(listOf(playerMesh, floorMesh)), camera, antiAliasingLevel = 1)
            val playerOnlyNoAa = renderSkinDiagnosticImage(Scene(listOf(playerMesh)), camera, antiAliasingLevel = 1)
            val diffNoAa = createSkinDifferenceImage(combinedNoAa, playerOnlyNoAa, background)
            Triple(floorY, view, listOf(combined, playerOnly, diff, combinedNoAa, playerOnlyNoAa, diffNoAa))
        }

        val image = Surface.makeRasterN32Premul(panelWidth * columns.size, panelHeight * rows.size).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.makeRGB(46, 61, 80))
            rows.forEachIndexed { row, (floorY, view, images) ->
                images.forEachIndexed { col, panel ->
                    val x = col * panelWidth
                    val y = row * panelHeight
                    paint.color = Color.makeRGB(35, 45, 60)
                    canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), panelWidth.toFloat(), titleHeight.toFloat()), paint)
                    paint.color = Color.makeRGB(232, 238, 246)
                    canvas.drawString(
                        "floor $floorY / pitch ${view.pitch.toInt()} / ${columns[col]}",
                        x + 14f,
                        y + 23f,
                        titleFont,
                        paint
                    )
                    canvas.drawImage(panel, x.toFloat(), y + titleHeight.toFloat())
                }
            }
            surface.makeImageSnapshot()
        }
        ManualTestSupport.saveImage("render3d/3D-皮肤脚部覆盖诊断-yaw-45.png", image)
    }

    private fun renderMinecraftSkinWireframePanel(skinFileName: String, isSlim: Boolean): Image {
        val playerMesh = loadMinecraftPlayer(skinFileName, isSlim)
        val camera = skinCamera(SkinView(45f, 20f))
        return renderSceneToImage(
            Scene(listOf(playerMesh)),
            RenderConfig(
                width = 520,
                height = 760,
                camera = camera,
                renderFaces = false,
                usePerspective = true,
                backgroundColor = Color.makeRGB(20, 25, 30),
                useBackFaceCulling = false,
                antiAliasingLevel = 2,
                lightDirection = skinLightDirection(camera)
            )
        )
    }

    private fun saveSkinWireframeComparison() {
        val panelWidth = 520
        val imageHeight = 760
        val titleHeight = 38
        val titleFont = Font(FontManager.resolve(ManualTestSupport.uiFont), 20f)
        val paint = Paint().apply { isAntiAlias = true }
        val panels = listOf(
            "Steve 线框" to renderMinecraftSkinWireframePanel("steve_skin.png", isSlim = false),
            "Alex 线框" to renderMinecraftSkinWireframePanel("alex_skin.png", isSlim = true)
        )

        val image = Surface.makeRasterN32Premul(panelWidth * 2, titleHeight + imageHeight).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.makeRGB(20, 25, 30))
            panels.forEachIndexed { index, (title, panel) ->
                val x = index * panelWidth
                paint.color = Color.makeRGB(35, 45, 60)
                canvas.drawRect(Rect.makeXYWH(x.toFloat(), 0f, panelWidth.toFloat(), titleHeight.toFloat()), paint)
                paint.color = Color.makeRGB(232, 238, 246)
                canvas.drawString(title, x + 14f, 26f, titleFont, paint)
                canvas.drawImage(panel, x.toFloat(), titleHeight.toFloat())
            }
            surface.makeImageSnapshot()
        }
        ManualTestSupport.saveImage("render3d/3D-皮肤线框-Steve-Alex对比.png", image)
    }

    private fun createFacingQuad(center: Vec3, size: Vec2, color: Int, reversed: Boolean = false, texture: Bitmap? = null): Mesh {
        val halfW = size.u / 2f
        val halfH = size.v / 2f
        val vertices = listOf(
            Vertex(center + Vec3(-halfW, -halfH, 0f), Vec2(0f, 1f)),
            Vertex(center + Vec3(halfW, -halfH, 0f), Vec2(1f, 1f)),
            Vertex(center + Vec3(halfW, halfH, 0f), Vec2(1f, 0f)),
            Vertex(center + Vec3(-halfW, halfH, 0f), Vec2(0f, 0f))
        )
        val indices = if (reversed) listOf(0, 3, 2, 1) else listOf(0, 1, 2, 3)
        return Mesh(vertices, listOf(Face(indices, color)), texture)
    }

    private fun createTransparentHoleTexture(): Bitmap {
        val texture = Bitmap()
        texture.allocN32Pixels(16, 16)
        texture.erase(Color.makeARGB(255, 40, 210, 120), IRect.makeXYWH(0, 0, 16, 16))
        texture.erase(Color.TRANSPARENT, IRect.makeXYWH(4, 4, 8, 8))
        texture.erase(Color.makeARGB(255, 235, 235, 245), IRect.makeXYWH(0, 0, 16, 2))
        texture.erase(Color.makeARGB(255, 235, 235, 245), IRect.makeXYWH(0, 14, 16, 2))
        texture.erase(Color.makeARGB(255, 235, 235, 245), IRect.makeXYWH(0, 0, 2, 16))
        texture.erase(Color.makeARGB(255, 235, 235, 245), IRect.makeXYWH(14, 0, 2, 16))
        return texture
    }

    private fun renderSpecialScene(name: String, scene: Scene, camera: OrbitCamera, useBackFaceCulling: Boolean = false) {
        renderSceneToImage(
            scene,
            RenderConfig(
                width = 640,
                height = 480,
                camera = camera,
                renderFaces = true,
                usePerspective = false,
                backgroundColor = Color.makeRGB(18, 24, 34),
                useBackFaceCulling = useBackFaceCulling,
                antiAliasingLevel = 2,
                lightDirection = Vec3(0.25f, 0.8f, 1f).normalized(),
                lightIntensity = 0.72f,
                enableShadows = false,
                shadowMapSize = 256
            )
        ).let { ManualTestSupport.saveImage("render3d/3D-特殊-${name}.png", it) }
    }

    private fun translateMesh(mesh: Mesh, offset: Vec3): Mesh =
        Mesh(mesh.vertices.map { Vertex(it.position + offset, it.uv) }, mesh.faces, mesh.texture, mesh.castsShadow)

    private fun shadowFloor(): Mesh =
        createPlane(
            center = Vec3(0f, -0.65f, 0f),
            size = Vec2(6f, 4.8f),
            color = Color.makeRGB(125, 140, 155),
            normalDirection = Vec3(0f, 1f, 0f),
            segments = 1,
            castsShadow = false
        )

    private fun shadowBlock(color: Int = Color.makeRGB(230, 150, 70)): Mesh =
        translateMesh(createCuboid(Vec3(1.1f, 1.2f, 1.1f), color), Vec3(-0.9f, 0.35f, 0.1f))

    private fun renderShadowPanel(
        scene: Scene,
        usePerspective: Boolean,
        enableShadows: Boolean = true,
        shadowBias: Float = 0.01f,
        shadowMapSize: Int = 2048
    ): Image =
        renderSceneToImage(
            scene,
            RenderConfig(
                width = 520,
                height = 360,
                camera = OrbitCamera(target = Vec3(0f, -0.05f, 0f), yaw = 38f, pitch = 28f, distance = 7.2f),
                renderFaces = true,
                usePerspective = usePerspective,
                backgroundColor = Color.makeRGB(18, 24, 34),
                useBackFaceCulling = false,
                antiAliasingLevel = 2,
                lightDirection = Vec3(0.45f, 1.0f, 0.55f).normalized(),
                lightIntensity = 0.68f,
                enableShadows = enableShadows,
                shadowMapSize = shadowMapSize,
                shadowBias = shadowBias,
                shadowOrthoSize = 5f
            )
        )

    private fun saveShadowComparison(scene: Scene) {
        val panelWidth = 520
        val imageHeight = 360
        val titleHeight = 36
        val panelHeight = titleHeight + imageHeight
        val titleFont = Font(FontManager.resolve(ManualTestSupport.uiFont), 20f)
        val paint = Paint().apply { isAntiAlias = true }
        val panels = listOf(
            "透视 / 阴影关闭" to renderShadowPanel(scene, usePerspective = true, enableShadows = false),
            "透视 / 阴影开启" to renderShadowPanel(scene, usePerspective = true, enableShadows = true),
            "正交 / 阴影关闭" to renderShadowPanel(scene, usePerspective = false, enableShadows = false),
            "正交 / 阴影开启" to renderShadowPanel(scene, usePerspective = false, enableShadows = true)
        )

        val image = Surface.makeRasterN32Premul(panelWidth * 2, panelHeight * 2).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.makeRGB(10, 14, 22))
            panels.forEachIndexed { index, (title, panel) ->
                val col = index % 2
                val row = index / 2
                val x = col * panelWidth
                val y = row * panelHeight

                paint.color = Color.makeRGB(32, 39, 52)
                canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), panelWidth.toFloat(), titleHeight.toFloat()), paint)
                paint.color = Color.makeRGB(230, 235, 242)
                canvas.drawString(title, x + 18f, y + 25f, titleFont, paint)
                canvas.drawImage(panel, x.toFloat(), y + titleHeight.toFloat())
            }
            surface.makeImageSnapshot()
        }
        ManualTestSupport.saveImage("render3d/3D-阴影-正交透视开关对比.png", image)
    }

    @Test
    fun specialCases() {
        val frontCamera = OrbitCamera(target = Vec3(0f, 0f, 0f), yaw = 0f, pitch = 0f, distance = 8f)

        renderSpecialScene(
            name = "半透明面不写深度",
            scene = Scene(
                listOf(
                    createFacingQuad(Vec3(0f, 0f, -0.4f), Vec2(4.4f, 3.0f), Color.makeRGB(40, 105, 235)),
                    createFacingQuad(Vec3(0f, 0f, 0.5f), Vec2(3.0f, 2.0f), Color.makeARGB(128, 240, 60, 50)),
                    createCuboid(Vec3(0.8f, 0.8f, 0.8f), Color.makeRGB(245, 210, 60)).let { cube ->
                        Mesh(cube.vertices.map { Vertex(it.position + Vec3(1.6f, -0.75f, 0.9f), it.uv) }, cube.faces)
                    }
                )
            ),
            camera = frontCamera
        )

        renderSpecialScene(
            name = "透明贴图孔洞不遮挡",
            scene = Scene(
                listOf(
                    createFacingQuad(Vec3(0f, 0f, -0.4f), Vec2(4.2f, 3.0f), Color.makeRGB(40, 105, 235)),
                    createFacingQuad(
                        center = Vec3(0f, 0f, 0.5f),
                        size = Vec2(3.1f, 2.4f),
                        color = Color.WHITE,
                        texture = createTransparentHoleTexture()
                    )
                )
            ),
            camera = frontCamera
        )

        val cullingScene = Scene(
            listOf(
                createFacingQuad(Vec3(-1.3f, 0f, 0f), Vec2(1.8f, 2.4f), Color.makeRGB(70, 210, 110)),
                createFacingQuad(Vec3(1.3f, 0f, 0f), Vec2(1.8f, 2.4f), Color.makeRGB(230, 70, 70), reversed = true)
            )
        )
        renderSpecialScene("背面剔除关闭对照", cullingScene, frontCamera, useBackFaceCulling = false)
        renderSpecialScene("背面剔除开启", cullingScene, frontCamera, useBackFaceCulling = true)

    }

    @Test
    fun shadowCases() {
        val basicScene = Scene(
            listOf(
                shadowFloor(),
                createPlane(
                    center = Vec3(-0.1f, 1.1f, 0.25f),
                    size = Vec2(2.0f, 1.5f),
                    color = Color.makeRGB(235, 185, 70),
                    normalDirection = Vec3(0f, 1f, 0f),
                    segments = 1
                ),
                shadowBlock(),
                translateMesh(createCuboid(Vec3(0.7f, 1.8f, 0.7f), Color.makeRGB(85, 175, 230)), Vec3(1.1f, 0.65f, -0.45f))
            )
        )

        saveShadowComparison(basicScene)
    }

    @Test
    fun skin() {
        runMinecraftSkinGallery("Alex纤细皮肤", "alex_skin.png", isSlim = true)
    }

    @Test
    fun skinWireframe() {
        saveSkinWireframeComparison()
    }
}
