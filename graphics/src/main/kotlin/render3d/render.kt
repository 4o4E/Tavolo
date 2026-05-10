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
    val enableShadows: Boolean = true, // 是否启用阴影贴图
    val shadowMapSize: Int = 1024, // 阴影图分辨率
    val shadowBias: Float = 0.002f, // 阴影偏移，防止波纹
    val shadowOrthoSize: Float = 30f // 阴影相机的正交投影范围
)

enum class MainRenderPass {
    ALL,
    OPAQUE_ONLY,
    TRANSPARENT_ONLY
}

private const val DEPTH_EPSILON = 1e-5f

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
    // 光源位置：为了生成正交投影，我们假设光源在很远的地方，看向原点
    val lightPos = lightDir * 50f
    val lightView = Mat4.lookAt(lightPos, Vec3(0f, 0f, 0f), Vec3(0f, 1f, 0f))
    val sSize = config.shadowOrthoSize
    val lightProj = Mat4.orthographic(-sSize, sSize, -sSize, sSize, 1f, 100f)
    val lightVP = lightProj * lightView

    val shadowMap = ShadowMap(config.shadowMapSize, config.shadowMapSize)

    // 渲染阴影：只渲染玩家产生阴影，背景通常不产生投射到玩家身上的阴影（根据需求可调整）
    // 优化：这里可以传入简化版的 Mesh (不带 3D Overlay) 以提升性能
    if (config.enableShadows) {
        renderShadowPass(scene.meshes, lightVP, shadowMap)
    }

    // 3. 主渲染通道 (Main Pass)
    val ssBitmap = Bitmap()
    ssBitmap.allocN32Pixels(renderWidth, renderHeight)
    val ssTarget = BitmapRenderTarget(ssBitmap)
    ssTarget.clear(bgColor)
    val zBuffer = FloatArray(renderWidth * renderHeight) { Float.POSITIVE_INFINITY }

    // 渲染
    scene.meshes.forEach { mesh ->
        renderMeshMainPass(
            mesh,
            vpMatrix,
            lightVP,
            renderWidth,
            renderHeight,
            zBuffer,
            ssTarget,
            shadowMap,
            config,
            cameraPosition,
            MainRenderPass.OPAQUE_ONLY
        )
    }
    scene.meshes.forEach { mesh ->
        renderMeshMainPass(
            mesh,
            vpMatrix,
            lightVP,
            renderWidth,
            renderHeight,
            zBuffer,
            ssTarget,
            shadowMap,
            config,
            cameraPosition,
            MainRenderPass.TRANSPARENT_ONLY
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

fun renderShadowPass(meshes: List<Mesh>, lightVP: Mat4, shadowMap: ShadowMap) {
    val width = shadowMap.width
    val height = shadowMap.height

    for (mesh in meshes) {
        if (!mesh.castsShadow) continue
        val texture = mesh.texture?.let(::BitmapRenderTexture)
        for (face in mesh.faces) {
            if (face.indices.size < 3) continue
            if (texture == null && Color.getA(face.baseColor) < 255) continue

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
                    texture = texture
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
        texture = null
    )
}

fun rasterizeTriangleShadow(
    vertices: List<ShadedVertex>,
    shadowMap: ShadowMap,
    texture: RenderTexture? = null
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
                    shadowMap.set(x, y, z)
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

    for (face in mesh.faces) {
        if (face.indices.size < 3) continue

        // 1. 计算面法线 (Flat Shading)
        val v0_w = mesh.vertices[face.indices[0]].position
        val v1_w = mesh.vertices[face.indices[1]].position
        val v2_w = mesh.vertices[face.indices[2]].position
        val faceNormal = (v1_w - v0_w).cross(v2_w - v0_w).normalized()
        val viewDir = (viewPos - v0_w).normalized()
        val faceVertices = face.indices.map(::shadeVertex)

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
            rasterizeTriangleMain(
                shadedVertices, width, height, zBuffer, target,
                texture, face.baseColor, faceNormal,
                lightVP, shadowMap, config, lightDir, viewDir, renderPass
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
    renderPass: MainRenderPass = MainRenderPass.ALL
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
    renderPass = renderPass
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
    renderPass: MainRenderPass = MainRenderPass.ALL
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
                    if (config.enableShadows && shadowU in 0f..1f && shadowV in 0f..1f && currentDepth < 1f) {
                        val shadowMapX = (shadowU * (shadowMap.width - 1)).toInt()
                        val shadowMapY = (shadowV * (shadowMap.height - 1)).toInt()
                        val ndotl = max(normal.normalized().dot(lightDir.normalized()), 0f)
                        val slopeBias = config.shadowBias * (1f + (1f - ndotl) * 8f)
                        var occludedSamples = 0
                        var validSamples = 0

                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                val closestDepth = shadowMap.get(shadowMapX + dx, shadowMapY + dy)
                                if (closestDepth.isFinite()) {
                                    validSamples++
                                    if (currentDepth - slopeBias > closestDepth) {
                                        occludedSamples++
                                    }
                                }
                            }
                        }

                        if (validSamples > 0) {
                            shadowFactor = 1f - 0.5f * (occludedSamples.toFloat() / validSamples)
                        }
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
