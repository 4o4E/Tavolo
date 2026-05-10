package top.e404.tavolo.draw.render3d

import org.jetbrains.skia.*
import kotlin.math.*

/**
 * 轨道相机数据类，通过目标点、方位角、仰角和距离来定义相机位置
 * @property target 相机注视的目标点
 * @property yaw 方位角，绕Y轴旋转的角度（左右视角）
 * @property pitch 俯仰角，绕X轴旋转的角度（上下视角）
 * @property distance 相机到目标点的距离
 * @property upVector 相机的上方向向量，通常为(0, 1, 0)
 */
data class OrbitCamera(
    var target: Vec3 = Vec3(0f, 0f, 0f),
    var yaw: Float = 0f,
    var pitch: Float = 0f,
    var distance: Float = 10f,
    var upVector: Vec3 = Vec3(0f, 1f, 0f)
) {
    /**
     * 根据轨道相机参数创建视图矩阵和相机位置
     *
     * @return 返回一个包含视图矩阵和相机前向向量的数据类
     */
    fun createViewMatrix(): ViewMatrix {
        // 将角度转换为弧度
        val yaw = this@OrbitCamera.yaw * (PI / 180.0).toFloat()
        val pitch = this@OrbitCamera.pitch * (PI / 180.0).toFloat()
        // 通过球面坐标计算相机位置
        val eyeX = target.x + distance * cos(pitch) * sin(yaw)
        val eyeY = target.y + distance * sin(pitch)
        val eyeZ = target.z + distance * cos(pitch) * cos(yaw)
        val eyePosition = Vec3(eyeX, eyeY, eyeZ)
        val viewMatrix = Mat4.lookAt(eyePosition, target, upVector)
        val cameraForward = (target - eyePosition).normalized()
        return ViewMatrix(viewMatrix, cameraForward, eyePosition)
    }
}

/**
 * 包含视图矩阵和相机前向向量的数据类
 *
 * @property viewMatrix 视图矩阵
 * @property cameraForward 相机的前向向量
 * @property cameraPosition 相机的世界坐标
 */
data class ViewMatrix(
    val viewMatrix: Mat4,
    val cameraForward: Vec3,
    val cameraPosition: Vec3 = Vec3(0f, 0f, 0f)
)

/**
 * 场景数据类，包含所有需要渲染的物体
 */
data class Scene(
    val meshes: List<Mesh>
)

/**
 * 渲染配置数据类，封装了渲染所需的各种参数
 *
 * @param width 输出图像宽度
 * @param height 输出图像高度
 * @param viewMatrix 视图矩阵
 * @param cameraForward 相机前向向量，用于背面剔除
 * @param cameraDistance 相机距离，用于正交投影计算
 * @param renderFaces true渲染实体，false渲染线框
 * @param usePerspective true使用透视投影，false使用正交投影
 * @param backgroundColor 背景色
 * @param useBackFaceCulling 是否启用背面剔除
 * @param antiAliasingLevel 抗锯齿级别
 * @param lightDirection 光源方向
 * @param lightIntensity 环境光强度
 */
data class RenderConfig(
    val width: Int,
    val height: Int,
    val camera: OrbitCamera,
    val renderFaces: Boolean = true,
    val usePerspective: Boolean = true,
    val backgroundColor: Int = Color.TRANSPARENT,
    val useBackFaceCulling: Boolean = false,
    val antiAliasingLevel: Int = 2,
    // 光照配置
    val lightDirection: Vec3 = Vec3(0.5f, 1.0f, 0.5f).normalized(), // 光源方向
    val lightIntensity: Float = 0.6f,
    // 阴影配置
    val enableShadows: Boolean = true // 是否启用阴影贴图
)

enum class MainRenderPass {
    ALL,
    OPAQUE_ONLY,
    TRANSPARENT_ONLY
}

// 阴影贴图分辨率固定在引擎默认值中，避免调用点为细小模型误用低分辨率。
// 低于该值时，皮肤模型脚部、外层皮肤和细长面在多视角人工图中容易出现锯齿、断裂或局部漏投影。
internal const val DEFAULT_SHADOW_MAP_SIZE = 4096

// 阴影深度偏移固定为当前人工图确认正常的值。
// 0 或过小会带来自阴影波纹；0.001 及以上会让接触阴影从几何交点后退，表现为 peter-panning。
internal const val DEFAULT_SHADOW_BIAS = 0.0005f

// 空场景或异常包围盒使用的回退半径；正常渲染会按场景自动收紧光源正交范围。
// 自动范围会保留 margin，避免裁掉多视角图中的投影；范围过大则会摊薄 shadow map 精度，让细节阴影重新变粗糙。
internal const val DEFAULT_SHADOW_ORTHO_SIZE = 42f

private const val DEPTH_EPSILON = 1e-5f
private const val SHADOW_SLOPE_BIAS_SCALE = 0.5f
private const val NO_SHADOW_FACE_ID = -1
private val EMPTY_SHADOW_FACE_IDS = IntArray(0)
private const val SHADOW_ORTHO_MARGIN_RATIO = 0.12f
private const val SHADOW_MIN_ORTHO_EXTENT = 4f
private const val SHADOW_MIN_DEPTH_RANGE = 4f
private const val SHADOW_CAMERA_DISTANCE_MARGIN = 10f
private const val SHADOW_PCF_RADIUS = 0

internal data class LightOrthoBounds(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float,
    val near: Float,
    val far: Float
)

private data class SceneBounds(
    val min: Vec3,
    val max: Vec3
) {
    val center: Vec3
        get() = (min + max) * 0.5f

    val radius: Float
        get() = (max - min).length() * 0.5f
}

internal fun calculateShadowDepthBias(normal: Vec3, lightDir: Vec3): Float {
    val ndotl = max(normal.normalized().dot(lightDir.normalized()), 0f)
    return DEFAULT_SHADOW_BIAS * (1f + (1f - ndotl) * SHADOW_SLOPE_BIAS_SCALE)
}

internal class ShadowFaceRegistry(
    private val faceIdsByMesh: List<IntArray>,
    private val ignoredFaceIdsByFaceId: List<IntArray>
) {
    fun faceId(meshIndex: Int, faceIndex: Int): Int =
        faceIdsByMesh.getOrNull(meshIndex)?.getOrNull(faceIndex) ?: NO_SHADOW_FACE_ID

    fun ignoredFaceIds(faceId: Int): IntArray =
        ignoredFaceIdsByFaceId.getOrNull(faceId) ?: EMPTY_SHADOW_FACE_IDS
}

private fun defaultIgnoredShadowFaceIds(receiverFaceId: Int): IntArray =
    if (receiverFaceId == NO_SHADOW_FACE_ID) EMPTY_SHADOW_FACE_IDS else intArrayOf(receiverFaceId)

private fun containsFaceId(faceIds: IntArray, faceId: Int): Boolean {
    for (id in faceIds) {
        if (id == faceId) return true
    }
    return false
}

internal fun calculateFilteredShadowFactor(
    shadowMap: ShadowMap,
    shadowMapX: Float,
    shadowMapY: Float,
    currentDepth: Float,
    slopeBias: Float,
    ignoredShadowFaceIds: IntArray = EMPTY_SHADOW_FACE_IDS,
    pcfRadius: Int = SHADOW_PCF_RADIUS
): Float {
    var occludedWeight = 0f
    var validWeight = 0f

    for (dy in -pcfRadius..pcfRadius) {
        for (dx in -pcfRadius..pcfRadius) {
            val sampleX = shadowMapX + dx
            val sampleY = shadowMapY + dy
            val baseX = floor(sampleX).toInt()
            val baseY = floor(sampleY).toInt()
            val fracX = sampleX - baseX
            val fracY = sampleY - baseY

            for (oy in 0..1) {
                val wy = if (oy == 0) 1f - fracY else fracY
                for (ox in 0..1) {
                    val wx = if (ox == 0) 1f - fracX else fracX
                    val weight = wx * wy
                    if (weight <= 0f) continue

                    val texelX = baseX + ox
                    val texelY = baseY + oy
                    val closestDepth = shadowMap.get(texelX, texelY)
                    val casterFaceId = shadowMap.getFaceId(texelX, texelY)
                    if (closestDepth.isFinite() && !containsFaceId(ignoredShadowFaceIds, casterFaceId)) {
                        validWeight += weight
                        if (currentDepth - slopeBias > closestDepth) {
                            occludedWeight += weight
                        }
                    }
                }
            }
        }
    }

    return if (validWeight > 0f) {
        1f - 0.5f * (occludedWeight / validWeight)
    } else {
        1f
    }
}

private fun edgeKey(first: Int, second: Int): Long {
    val min = min(first, second)
    val max = max(first, second)
    return (min.toLong() shl 32) or (max.toLong() and 0xffffffffL)
}

private fun shadowLightUp(lightDir: Vec3): Vec3 =
    if (abs(lightDir.normalized().dot(Vec3(0f, 1f, 0f))) > 0.95f) {
        Vec3(0f, 0f, 1f)
    } else {
        Vec3(0f, 1f, 0f)
    }

private fun calculateSceneBounds(meshes: List<Mesh>): SceneBounds? {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY
    var hasVertex = false

    for (mesh in meshes) {
        if (!mesh.castsShadow && !mesh.receivesShadow) continue
        for (vertex in mesh.vertices) {
            val position = vertex.position
            minX = min(minX, position.x)
            minY = min(minY, position.y)
            minZ = min(minZ, position.z)
            maxX = max(maxX, position.x)
            maxY = max(maxY, position.y)
            maxZ = max(maxZ, position.z)
            hasVertex = true
        }
    }

    if (!hasVertex) return null
    return SceneBounds(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
}

internal fun calculateLightOrthoBounds(meshes: List<Mesh>, lightView: Mat4): LightOrthoBounds {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY
    var hasVertex = false

    for (mesh in meshes) {
        if (!mesh.castsShadow && !mesh.receivesShadow) continue
        for (vertex in mesh.vertices) {
            val clip = lightView.transform(vertex.position)
            val x = clip.x / clip.w
            val y = clip.y / clip.w
            val z = clip.z / clip.w
            minX = min(minX, x)
            minY = min(minY, y)
            minZ = min(minZ, z)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
            maxZ = max(maxZ, z)
            hasVertex = true
        }
    }

    if (!hasVertex) {
        return LightOrthoBounds(
            -DEFAULT_SHADOW_ORTHO_SIZE,
            DEFAULT_SHADOW_ORTHO_SIZE,
            -DEFAULT_SHADOW_ORTHO_SIZE,
            DEFAULT_SHADOW_ORTHO_SIZE,
            1f,
            100f
        )
    }

    val width = max(maxX - minX, SHADOW_MIN_ORTHO_EXTENT)
    val height = max(maxY - minY, SHADOW_MIN_ORTHO_EXTENT)
    val depth = max(maxZ - minZ, SHADOW_MIN_DEPTH_RANGE)
    val centerX = (minX + maxX) * 0.5f
    val centerY = (minY + maxY) * 0.5f
    val marginX = max(width * SHADOW_ORTHO_MARGIN_RATIO, 0.5f)
    val marginY = max(height * SHADOW_ORTHO_MARGIN_RATIO, 0.5f)
    val marginZ = max(depth * SHADOW_ORTHO_MARGIN_RATIO, 1f)
    val halfWidth = width * 0.5f + marginX
    val halfHeight = height * 0.5f + marginY
    val near = max(0.1f, -maxZ - marginZ)
    val far = max(near + SHADOW_MIN_DEPTH_RANGE, -minZ + marginZ)

    return LightOrthoBounds(
        left = centerX - halfWidth,
        right = centerX + halfWidth,
        bottom = centerY - halfHeight,
        top = centerY + halfHeight,
        near = near,
        far = far
    )
}

internal fun buildShadowFaceRegistry(meshes: List<Mesh>): ShadowFaceRegistry {
    val faceIdsByMesh = meshes.map { IntArray(it.faces.size) { NO_SHADOW_FACE_ID } }
    val ignoredFaceIds = mutableListOf<MutableSet<Int>>()
    var nextId = 0

    for ((meshIndex, mesh) in meshes.withIndex()) {
        if (!mesh.castsShadow) continue
        val meshFaceIds = faceIdsByMesh[meshIndex]
        for ((faceIndex, face) in mesh.faces.withIndex()) {
            if (face.indices.size >= 3) {
                meshFaceIds[faceIndex] = nextId
                ignoredFaceIds += mutableSetOf(nextId)
                nextId++
            }
        }
    }

    for ((meshIndex, mesh) in meshes.withIndex()) {
        if (!mesh.castsShadow) continue
        val edgeOwners = HashMap<Long, MutableList<Int>>()
        mesh.faces.forEachIndexed { faceIndex, face ->
            val faceId = faceIdsByMesh[meshIndex][faceIndex]
            if (faceId == NO_SHADOW_FACE_ID) return@forEachIndexed
            for (index in face.indices.indices) {
                val key = edgeKey(face.indices[index], face.indices[(index + 1) % face.indices.size])
                val owners = edgeOwners.getOrPut(key) { mutableListOf() }
                for (otherFaceId in owners) {
                    ignoredFaceIds[faceId] += otherFaceId
                    ignoredFaceIds[otherFaceId] += faceId
                }
                owners += faceId
            }
        }
    }

    return ShadowFaceRegistry(faceIdsByMesh, ignoredFaceIds.map { it.toIntArray() })
}

fun renderSceneToImage(
    scene: Scene,
    config: RenderConfig
): Image {
    val width = config.width
    val height = config.height
    val camera = config.camera
    val usePerspective = config.usePerspective
    val bgColor = config.backgroundColor
    val aaLevel = config.antiAliasingLevel
    val lightDir = config.lightDirection

    // 1. 准备矩阵
    val renderWidth = width * aaLevel
    val renderHeight = height * aaLevel
    val aspectRatio = width.toFloat() / height.toFloat()

    // 主相机矩阵
    val (viewMatrix, _, cameraPosition) = camera.createViewMatrix()
    val fov = 45f * (PI / 180.0).toFloat()
    val projection = if (usePerspective) Mat4.perspective(fov, aspectRatio, 0.1f, 200f) else {
        val oh = 2f * camera.distance * tan(fov / 2f)
        val ow = oh * aspectRatio
        Mat4.orthographic(-ow / 2f, ow / 2f, -oh / 2f, oh / 2f, 0.1f, 200f)
    }
    val vpMatrix = projection * viewMatrix

    // 2. 阴影通道 (Shadow Pass)
    // 创建光源视角的 ViewProjection 矩阵
    // 光源位置：方向光只需要稳定的视图方向；相机看向场景中心，正交范围再按场景自动收紧。
    val sceneBounds = calculateSceneBounds(scene.meshes)
    val lightTarget = sceneBounds?.center ?: Vec3(0f, 0f, 0f)
    val lightDistance = max(50f, (sceneBounds?.radius ?: 0f) + SHADOW_CAMERA_DISTANCE_MARGIN)
    val lightPos = lightTarget + lightDir * lightDistance
    val lightView = Mat4.lookAt(lightPos, lightTarget, shadowLightUp(lightDir))
    val lightBounds = calculateLightOrthoBounds(scene.meshes, lightView)
    val lightProj = Mat4.orthographic(
        lightBounds.left,
        lightBounds.right,
        lightBounds.bottom,
        lightBounds.top,
        lightBounds.near,
        lightBounds.far
    )
    val lightVP = lightProj * lightView

    val shadowMap = ShadowMap(DEFAULT_SHADOW_MAP_SIZE, DEFAULT_SHADOW_MAP_SIZE)
    val shadowFaceRegistry = buildShadowFaceRegistry(scene.meshes)

    // 渲染阴影：只渲染玩家产生阴影，背景通常不产生投射到玩家身上的阴影（根据需求可调整）
    // 优化：这里可以传入简化版的 Mesh (不带 3D Overlay) 以提升性能
    if (config.enableShadows) {
        renderShadowPass(scene.meshes, lightVP, shadowMap, shadowFaceRegistry)
    }

    // 3. 主渲染通道 (Main Pass)
    val ssBitmap = Bitmap()
    ssBitmap.allocN32Pixels(renderWidth, renderHeight)
    val ssTarget = BitmapRenderTarget(ssBitmap)
    ssTarget.clear(bgColor)
    val zBuffer = FloatArray(renderWidth * renderHeight) { Float.POSITIVE_INFINITY }

    // 渲染
    scene.meshes.forEachIndexed { meshIndex, mesh ->
        renderMeshMainPassInternal(
            mesh = mesh,
            vpMatrix = vpMatrix,
            lightVP = lightVP,
            width = renderWidth,
            height = renderHeight,
            zBuffer = zBuffer,
            target = ssTarget,
            shadowMap = shadowMap,
            config = config,
            viewPos = cameraPosition,
            renderPass = MainRenderPass.OPAQUE_ONLY,
            meshIndex = meshIndex,
            shadowFaceRegistry = shadowFaceRegistry
        )
    }
    scene.meshes.forEachIndexed { meshIndex, mesh ->
        renderMeshMainPassInternal(
            mesh = mesh,
            vpMatrix = vpMatrix,
            lightVP = lightVP,
            width = renderWidth,
            height = renderHeight,
            zBuffer = zBuffer,
            target = ssTarget,
            shadowMap = shadowMap,
            config = config,
            viewPos = cameraPosition,
            renderPass = MainRenderPass.TRANSPARENT_ONLY,
            meshIndex = meshIndex,
            shadowFaceRegistry = shadowFaceRegistry
        )
    }

    // 4. 抗锯齿下采样 (保持原逻辑)
    val finalBitmap = Bitmap()
    finalBitmap.allocN32Pixels(width, height)
    val finalTarget = BitmapRenderTarget(finalBitmap)
    if (aaLevel > 1) {
        // 遍历最终图像的每个像素
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                var a = 0f
                // 对超采样画布上对应的4个像素进行求和
                for (ssY in 0 until aaLevel) {
                    for (ssX in 0 until aaLevel) {
                        val color = ssTarget.getColor(x * aaLevel + ssX, y * aaLevel + ssY)
                        a += Color.getA(color)
                        r += Color.getR(color)
                        g += Color.getG(color)
                        b += Color.getB(color)
                    }
                }
                // 计算平均颜色
                val s2 = (aaLevel * aaLevel).toFloat()
                val avgColor =
                    Color.makeARGB((a / s2).toInt(), (r / s2).toInt(), (g / s2).toInt(), (b / s2).toInt())
                finalTarget.setPixel(x, y, avgColor)
            }
        }
    } else {
        for (y in 0 until height) {
            for (x in 0 until width) {
                finalTarget.setPixel(x, y, ssTarget.getColor(x, y))
            }
        }
    }

    return Image.makeFromBitmap(finalBitmap)
}

// --- 阴影通道逻辑 ---

fun renderShadowPass(
    meshes: List<Mesh>,
    lightVP: Mat4,
    shadowMap: ShadowMap
) = renderShadowPass(
    meshes = meshes,
    lightVP = lightVP,
    shadowMap = shadowMap,
    shadowFaceRegistry = buildShadowFaceRegistry(meshes)
)

private fun renderShadowPass(
    meshes: List<Mesh>,
    lightVP: Mat4,
    shadowMap: ShadowMap,
    shadowFaceRegistry: ShadowFaceRegistry
) {
    val width = shadowMap.width
    val height = shadowMap.height

    for ((meshIndex, mesh) in meshes.withIndex()) {
        if (!mesh.castsShadow) continue
        val texture = mesh.texture?.let(::BitmapRenderTexture)
        for ((faceIndex, face) in mesh.faces.withIndex()) {
            if (face.indices.size < 3) continue
            if (texture == null && Color.getA(face.baseColor) < 255) continue
            val shadowFaceId = shadowFaceRegistry.faceId(meshIndex, faceIndex)

            // 三角形剖分
            for (i in 0 until face.indices.size - 2) {
                val idx0 = face.indices[0]
                val idx1 = face.indices[i + 1]
                val idx2 = face.indices[i + 2]

                val v0 = mesh.vertices[idx0]
                val v1 = mesh.vertices[idx1]
                val v2 = mesh.vertices[idx2]

                // 变换到光源裁剪空间
                val p0 = lightVP.transform(v0.position)
                val p1 = lightVP.transform(v1.position)
                val p2 = lightVP.transform(v2.position)

                // 简单的视锥剔除 (可选)
                if (p0.w < 0 && p1.w < 0 && p2.w < 0) continue

                // 透视除法 -> NDC -> 屏幕坐标 (ShadowMap 坐标)
                // 注意：正交投影 w 通常为 1，但为了通用性保留除法
                val sc0 = ndcToScreen(p0, width, height)
                val sc1 = ndcToScreen(p1, width, height)
                val sc2 = ndcToScreen(p2, width, height)

                rasterizeTriangleShadow(
                    vertices = listOf(
                        ShadedVertex(sc0, v0.position, 1.0f / p0.w, v0.uv),
                        ShadedVertex(sc1, v1.position, 1.0f / p1.w, v1.uv),
                        ShadedVertex(sc2, v2.position, 1.0f / p2.w, v2.uv)
                    ),
                    shadowMap = shadowMap,
                    texture = texture,
                    shadowFaceId = shadowFaceId
                )
            }
        }
    }
}

fun ndcToScreen(clip: Vec4, w: Int, h: Int): Vec3 {
    val invW = 1.0f / clip.w
    val ndcX = clip.x * invW
    val ndcY = clip.y * invW
    val ndcZ = clip.z * invW

    return Vec3(
        (ndcX + 1f) * 0.5f * w,
        (-ndcY + 1f) * 0.5f * h, // Y轴翻转
        ndcZ // 深度值保持 NDC 范围 (-1 to 1) 或 (0 to 1) 取决于矩阵，这里直接存
    )
}

fun rasterizeTriangleShadow(p0: Vec3, p1: Vec3, p2: Vec3, shadowMap: ShadowMap) {
    rasterizeTriangleShadow(
        vertices = listOf(
            ShadedVertex(p0, Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(p1, Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f)),
            ShadedVertex(p2, Vec3(0f, 0f, 0f), 1f, Vec2(0f, 0f))
        ),
        shadowMap = shadowMap,
        texture = null,
        shadowFaceId = NO_SHADOW_FACE_ID
    )
}

fun rasterizeTriangleShadow(
    vertices: List<ShadedVertex>,
    shadowMap: ShadowMap,
    texture: RenderTexture? = null,
    shadowFaceId: Int = NO_SHADOW_FACE_ID
) {
    val v0 = vertices[0]
    val v1 = vertices[1]
    val v2 = vertices[2]
    val p0 = v0.screenPos
    val p1 = v1.screenPos
    val p2 = v2.screenPos

    val minX = max(0, min(p0.x, min(p1.x, p2.x)).toInt())
    val maxX = min(shadowMap.width - 1, max(p0.x, max(p1.x, p2.x)).toInt())
    val minY = max(0, min(p0.y, min(p1.y, p2.y)).toInt())
    val maxY = min(shadowMap.height - 1, max(p0.y, max(p1.y, p2.y)).toInt())

    val denom = (p1.y - p2.y) * (p0.x - p2.x) + (p2.x - p1.x) * (p0.y - p2.y)
    if (denom == 0f) return

    for (y in minY..maxY) {
        for (x in minX..maxX) {
            val sampleX = x + 0.5f
            val sampleY = y + 0.5f
            val w0 = ((p1.y - p2.y) * (sampleX - p2.x) + (p2.x - p1.x) * (sampleY - p2.y)) / denom
            val w1 = ((p2.y - p0.y) * (sampleX - p2.x) + (p0.x - p2.x) * (sampleY - p2.y)) / denom
            val w2 = 1.0f - w0 - w1

            if (w0 >= 0 && w1 >= 0 && w2 >= 0) {
                if (texture != null) {
                    val oneOverW = v0.oneOverW * w0 + v1.oneOverW * w1 + v2.oneOverW * w2
                    val w = 1.0f / oneOverW
                    val uOverW = v0.uv.u * v0.oneOverW * w0 + v1.uv.u * v1.oneOverW * w1 + v2.uv.u * v2.oneOverW * w2
                    val vOverW = v0.uv.v * v0.oneOverW * w0 + v1.uv.v * v1.oneOverW * w1 + v2.uv.v * v2.oneOverW * w2
                    val tx = (uOverW * w * texture.width).toInt().clamp(0, texture.width - 1)
                    val ty = (vOverW * w * texture.height).toInt().clamp(0, texture.height - 1)
                    if (Color.getA(texture.getColor(tx, ty)) < 255) continue
                }

                // 插值深度
                val z = p0.z * w0 + p1.z * w1 + p2.z * w2
                // 写入深度缓冲 (越小越近)
                if (z < shadowMap.get(x, y)) {
                    shadowMap.set(x, y, z, shadowFaceId)
                }
            }
        }
    }
}

// --- 主渲染通道逻辑 ---

fun renderMeshMainPass(
    mesh: Mesh,
    vpMatrix: Mat4,
    lightVP: Mat4,
    width: Int,
    height: Int,
    zBuffer: FloatArray,
    bitmap: Bitmap,
    shadowMap: ShadowMap,
    config: RenderConfig,
    viewPos: Vec3,
    renderPass: MainRenderPass = MainRenderPass.ALL
) = renderMeshMainPass(
    mesh = mesh,
    vpMatrix = vpMatrix,
    lightVP = lightVP,
    width = width,
    height = height,
    zBuffer = zBuffer,
    target = BitmapRenderTarget(bitmap),
    shadowMap = shadowMap,
    config = config,
    viewPos = viewPos,
    renderPass = renderPass
)

fun renderMeshMainPass(
    mesh: Mesh,
    vpMatrix: Mat4,
    lightVP: Mat4,
    width: Int,
    height: Int,
    zBuffer: FloatArray,
    target: RenderTarget,
    shadowMap: ShadowMap,
    config: RenderConfig,
    viewPos: Vec3,
    renderPass: MainRenderPass = MainRenderPass.ALL
) {
    renderMeshMainPassInternal(
        mesh = mesh,
        vpMatrix = vpMatrix,
        lightVP = lightVP,
        width = width,
        height = height,
        zBuffer = zBuffer,
        target = target,
        shadowMap = shadowMap,
        config = config,
        viewPos = viewPos,
        renderPass = renderPass,
        meshIndex = 0,
        shadowFaceRegistry = null
    )
}

private fun renderMeshMainPassInternal(
    mesh: Mesh,
    vpMatrix: Mat4,
    lightVP: Mat4,
    width: Int,
    height: Int,
    zBuffer: FloatArray,
    target: RenderTarget,
    shadowMap: ShadowMap,
    config: RenderConfig,
    viewPos: Vec3,
    renderPass: MainRenderPass = MainRenderPass.ALL,
    meshIndex: Int,
    shadowFaceRegistry: ShadowFaceRegistry?
) {
    val lightDir = config.lightDirection
    val texture = mesh.texture?.let(::BitmapRenderTexture)
    val wireframeStrokeWidth = max(1f, config.antiAliasingLevel.toFloat())

    fun shadeVertex(index: Int): ShadedVertex {
        val vertex = mesh.vertices[index]
        val clipPos = vpMatrix.transform(vertex.position)
        val oneOverW = 1.0f / clipPos.w
        val ndc = Vec3(clipPos.x * oneOverW, clipPos.y * oneOverW, clipPos.z * oneOverW)
        val screenX = (ndc.x + 1f) * 0.5f * width
        val screenY = (-ndc.y + 1f) * 0.5f * height

        return ShadedVertex(
            screenPos = Vec3(screenX, screenY, ndc.z),
            worldPos = vertex.position,
            oneOverW = oneOverW,
            uv = vertex.uv
        )
    }

    for ((faceIndex, face) in mesh.faces.withIndex()) {
        if (face.indices.size < 3) continue

        // 1. 计算面法线 (Flat Shading)
        val v0_w = mesh.vertices[face.indices[0]].position
        val v1_w = mesh.vertices[face.indices[1]].position
        val v2_w = mesh.vertices[face.indices[2]].position
        val faceNormal = (v1_w - v0_w).cross(v2_w - v0_w).normalized()
        val viewDir = (viewPos - v0_w).normalized()
        val faceVertices = face.indices.map(::shadeVertex)
        val receiverFaceId = shadowFaceRegistry?.faceId(meshIndex, faceIndex) ?: NO_SHADOW_FACE_ID
        val ignoredShadowFaceIds = shadowFaceRegistry?.ignoredFaceIds(receiverFaceId)
            ?: defaultIgnoredShadowFaceIds(receiverFaceId)

        // 背面剔除
        if (config.useBackFaceCulling) {
            if (isBackFacingInScreen(faceVertices)) continue
        }

        if (!config.renderFaces) {
            if (renderPass == MainRenderPass.TRANSPARENT_ONLY) continue
            drawWireframeFace(
                target = target,
                vertices = faceVertices,
                baseColor = face.baseColor,
                normal = faceNormal,
                lightDir = lightDir,
                ambientIntensity = config.lightIntensity,
                strokeWidth = wireframeStrokeWidth
            )
            continue
        }

        for (i in 0 until face.indices.size - 2) {
            val shadedVertices = listOf(faceVertices[0], faceVertices[i + 1], faceVertices[i + 2])
            rasterizeTriangleMainInternal(
                vertices = shadedVertices,
                width = width,
                height = height,
                zBuffer = zBuffer,
                target = target,
                texture = texture,
                baseColor = face.baseColor,
                normal = faceNormal,
                lightVP = lightVP,
                shadowMap = shadowMap,
                config = config,
                lightDir = lightDir,
                viewDir = viewDir,
                renderPass = renderPass,
                receivesShadow = mesh.receivesShadow,
                receiverFaceId = receiverFaceId,
                ignoredShadowFaceIds = ignoredShadowFaceIds
            )
        }
    }
}

private fun isBackFacingInScreen(vertices: List<ShadedVertex>): Boolean {
    if (vertices.size < 3) return true

    var signedArea = 0f
    for (i in vertices.indices) {
        val p0 = vertices[i].screenPos
        val p1 = vertices[(i + 1) % vertices.size].screenPos
        signedArea += p0.x * p1.y - p1.x * p0.y
    }

    // 屏幕坐标的 Y 轴向下，朝向相机的面投影后为负面积。
    return signedArea >= -0.0001f
}

private fun drawWireframeFace(
    target: RenderTarget,
    vertices: List<ShadedVertex>,
    baseColor: Int,
    normal: Vec3,
    lightDir: Vec3,
    ambientIntensity: Float,
    strokeWidth: Float
) {
    val color = applyLight(baseColor, calculateLightIntensity(normal, lightDir, ambientIntensity))
    for (i in vertices.indices) {
        val p0 = vertices[i].screenPos
        val p1 = vertices[(i + 1) % vertices.size].screenPos
        target.drawLine(p0.x, p0.y, p1.x, p1.y, color, strokeWidth, antiAlias = true)
    }
}

fun rasterizeTriangleMain(
    vertices: List<ShadedVertex>,
    width: Int, height: Int,
    zBuffer: FloatArray,
    bitmap: Bitmap,
    texture: Bitmap?,
    baseColor: Int,
    normal: Vec3,
    lightVP: Mat4,
    shadowMap: ShadowMap,
    config: RenderConfig,
    lightDir: Vec3,
    viewDir: Vec3,
    renderPass: MainRenderPass = MainRenderPass.ALL,
    receivesShadow: Boolean = true,
    receiverFaceId: Int = NO_SHADOW_FACE_ID
) = rasterizeTriangleMain(
    vertices = vertices,
    width = width,
    height = height,
    zBuffer = zBuffer,
    target = BitmapRenderTarget(bitmap),
    texture = texture?.let(::BitmapRenderTexture),
    baseColor = baseColor,
    normal = normal,
    lightVP = lightVP,
    shadowMap = shadowMap,
    config = config,
    lightDir = lightDir,
    viewDir = viewDir,
    renderPass = renderPass,
    receivesShadow = receivesShadow,
    receiverFaceId = receiverFaceId
)

fun rasterizeTriangleMain(
    vertices: List<ShadedVertex>,
    width: Int, height: Int,
    zBuffer: FloatArray,
    target: RenderTarget,
    texture: RenderTexture?,
    baseColor: Int,
    normal: Vec3, // 面法线
    lightVP: Mat4, // 光源变换矩阵
    shadowMap: ShadowMap,
    config: RenderConfig,
    lightDir: Vec3,
    viewDir: Vec3,
    renderPass: MainRenderPass = MainRenderPass.ALL,
    receivesShadow: Boolean = true,
    receiverFaceId: Int = NO_SHADOW_FACE_ID
) = rasterizeTriangleMainInternal(
    vertices = vertices,
    width = width,
    height = height,
    zBuffer = zBuffer,
    target = target,
    texture = texture,
    baseColor = baseColor,
    normal = normal,
    lightVP = lightVP,
    shadowMap = shadowMap,
    config = config,
    lightDir = lightDir,
    viewDir = viewDir,
    renderPass = renderPass,
    receivesShadow = receivesShadow,
    receiverFaceId = receiverFaceId,
    ignoredShadowFaceIds = defaultIgnoredShadowFaceIds(receiverFaceId)
)

private fun rasterizeTriangleMainInternal(
    vertices: List<ShadedVertex>,
    width: Int, height: Int,
    zBuffer: FloatArray,
    target: RenderTarget,
    texture: RenderTexture?,
    baseColor: Int,
    normal: Vec3,
    lightVP: Mat4,
    shadowMap: ShadowMap,
    config: RenderConfig,
    lightDir: Vec3,
    viewDir: Vec3,
    renderPass: MainRenderPass = MainRenderPass.ALL,
    receivesShadow: Boolean = true,
    receiverFaceId: Int = NO_SHADOW_FACE_ID,
    ignoredShadowFaceIds: IntArray = defaultIgnoredShadowFaceIds(receiverFaceId)
) {
    val v0 = vertices[0];
    val v1 = vertices[1];
    val v2 = vertices[2]
    val p0 = v0.screenPos;
    val p1 = v1.screenPos;
    val p2 = v2.screenPos

    val minX = max(0, min(p0.x, min(p1.x, p2.x)).toInt())
    val maxX = min(width - 1, max(p0.x, max(p1.x, p2.x)).toInt())
    val minY = max(0, min(p0.y, min(p1.y, p2.y)).toInt())
    val maxY = min(height - 1, max(p0.y, max(p1.y, p2.y)).toInt())

    val denom = (p1.y - p2.y) * (p0.x - p2.x) + (p2.x - p1.x) * (p0.y - p2.y)
    if (denom == 0f) return

    for (y in minY..maxY) {
        for (x in minX..maxX) {
            val sampleX = x + 0.5f
            val sampleY = y + 0.5f
            val w0 = ((p1.y - p2.y) * (sampleX - p2.x) + (p2.x - p1.x) * (sampleY - p2.y)) / denom
            val w1 = ((p2.y - p0.y) * (sampleX - p2.x) + (p0.x - p2.x) * (sampleY - p2.y)) / denom
            val w2 = 1.0f - w0 - w1

            if (w0 >= 0 && w1 >= 0 && w2 >= 0) {
                // 透视校正插值
                val oneOverW = v0.oneOverW * w0 + v1.oneOverW * w1 + v2.oneOverW * w2
                val w = 1.0f / oneOverW

                // NDC 深度已经完成透视除法，应在屏幕空间线性插值；UV/世界坐标才需要透视校正。
                val zDepth = p0.z * w0 + p1.z * w1 + p2.z * w2
                val bufferIndex = y * width + x

                if (zDepth < zBuffer[bufferIndex] - DEPTH_EPSILON) {
                    // 插值世界坐标 (用于阴影查找)
                    // 注意：WorldPos 也是线性插值，严格来说透视投影下也需要透视校正，但对于阴影查找误差可接受
                    // 为了精确，我们还是做透视校正
                    val worldX =
                        (v0.worldPos.x * v0.oneOverW * w0 + v1.worldPos.x * v1.oneOverW * w1 + v2.worldPos.x * v2.oneOverW * w2) * w
                    val worldY =
                        (v0.worldPos.y * v0.oneOverW * w0 + v1.worldPos.y * v1.oneOverW * w1 + v2.worldPos.y * v2.oneOverW * w2) * w
                    val worldZ =
                        (v0.worldPos.z * v0.oneOverW * w0 + v1.worldPos.z * v1.oneOverW * w1 + v2.worldPos.z * v2.oneOverW * w2) * w
                    val currentWorldPos = Vec3(worldX, worldY, worldZ)

                    // --- 阴影计算 ---
                    val lightClip = lightVP.transform(currentWorldPos)
                    // 转换到 [0,1] 纹理空间
                    val lightNDC = Vec3(lightClip.x / lightClip.w, lightClip.y / lightClip.w, lightClip.z / lightClip.w)
                    val shadowU = (lightNDC.x + 1f) * 0.5f
                    val shadowV = (-lightNDC.y + 1f) * 0.5f // 注意Y轴方向
                    val currentDepth = lightNDC.z

                    var shadowFactor = 1.0f
                    // 检查是否在阴影图范围内
                    if (receivesShadow && config.enableShadows && shadowU in 0f..1f && shadowV in 0f..1f && currentDepth < 1f) {
                        val shadowMapX = shadowU * (shadowMap.width - 1)
                        val shadowMapY = shadowV * (shadowMap.height - 1)
                        val slopeBias = calculateShadowDepthBias(normal, lightDir)
                        shadowFactor = calculateFilteredShadowFactor(
                            shadowMap = shadowMap,
                            shadowMapX = shadowMapX,
                            shadowMapY = shadowMapY,
                            currentDepth = currentDepth,
                            slopeBias = slopeBias,
                            ignoredShadowFaceIds = ignoredShadowFaceIds
                        )
                    }

                    // --- 纹理采样 ---
                    val texelColor = if (texture != null) {
                        val uOverW =
                            v0.uv.u * v0.oneOverW * w0 + v1.uv.u * v1.oneOverW * w1 + v2.uv.u * v2.oneOverW * w2
                        val vOverW =
                            v0.uv.v * v0.oneOverW * w0 + v1.uv.v * v1.oneOverW * w1 + v2.uv.v * v2.oneOverW * w2
                        val u = uOverW * w
                        val v = vOverW * w
                        val tx = (u * texture.width).toInt().clamp(0, texture.width - 1)
                        val ty = (v * texture.height).toInt().clamp(0, texture.height - 1)
                        texture.getColor(tx, ty)
                    } else {
                        baseColor
                    }

                    if (Color.getA(texelColor) == 0) continue

                    // --- Blinn-Phong 光照应用 ---
                    val finalColor = calculateBlinnPhong(
                        normal, lightDir, viewDir, texelColor, shadowFactor,
                        config.lightIntensity
                    )
                    val finalAlpha = Color.getA(finalColor)

                    if (renderPass == MainRenderPass.OPAQUE_ONLY && finalAlpha < 255) continue
                    if (renderPass == MainRenderPass.TRANSPARENT_ONLY && finalAlpha == 255) continue

                    if (finalAlpha == 255) {
                        zBuffer[bufferIndex] = zDepth
                        target.setPixel(x, y, finalColor)
                    } else {
                        target.setPixel(x, y, blendSourceOver(finalColor, target.getColor(x, y)))
                    }
                }
            }
        }
    }
}

private fun blendSourceOver(src: Int, dst: Int): Int {
    val srcA = Color.getA(src) / 255f
    val dstA = Color.getA(dst) / 255f
    val outA = srcA + dstA * (1f - srcA)
    if (outA <= 0f) return Color.TRANSPARENT

    fun blendChannel(srcChannel: Int, dstChannel: Int): Int {
        val value = (srcChannel * srcA + dstChannel * dstA * (1f - srcA)) / outA
        return value.roundToInt().clamp(0, 255)
    }

    return Color.makeARGB(
        (outA * 255f).roundToInt().clamp(0, 255),
        blendChannel(Color.getR(src), Color.getR(dst)),
        blendChannel(Color.getG(src), Color.getG(dst)),
        blendChannel(Color.getB(src), Color.getB(dst))
    )
}
