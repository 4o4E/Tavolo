package top.e404.skiko.draw.render3d

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Path
import org.jetbrains.skia.Point
import org.jetbrains.skia.Surface
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

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
    val viewMatrix: Mat4,
    val cameraForward: Vec3,
    val cameraDistance: Float,
    val renderFaces: Boolean = true,
    val usePerspective: Boolean = true,
    val backgroundColor: Int = Color.TRANSPARENT,
    val useBackFaceCulling: Boolean = false,
    val antiAliasingLevel: Int = 2,
    val lightDirection: Vec3 = Vec3(0.7f, 1.0f, 0.5f).normalized(),
    val lightIntensity: Float = 1.9f
)

/**
 * 核心渲染函数，执行完整的渲染管线并保存结果到文件
 *
 * @param mesh 要渲染的网格
 * @return 渲染后的图像
 */
fun renderToImage(
    mesh: Mesh,
    config: RenderConfig
): Image {
    val (
        width,
        height,
        viewMatrix,
        cameraForward,
        cameraDistance,
        renderFaces,
        usePerspective,
        backgroundColor,
        useBackFaceCulling,
        antiAliasingLevel,
        lightDirection,
        ambientIntensity
    ) = config
    // 根据抗锯齿级别计算内部渲染尺寸
    val renderWidth = width * antiAliasingLevel
    val renderHeight = height * antiAliasingLevel
    val aspectRatio = width.toFloat() / height.toFloat()
    val fov = 45f * (PI / 180.0).toFloat()
    // 根据配置选择并创建投影矩阵
    val projection = if (usePerspective) Mat4.perspective(fov, aspectRatio, 0.1f, 200f) else {
        val orthoHeight = 2f * cameraDistance * tan(fov / 2f)
        val orthoWidth = orthoHeight * aspectRatio
        Mat4.orthographic(-orthoWidth / 2f, orthoWidth / 2f, -orthoHeight / 2f, orthoHeight / 2f, 0.1f, 200f)
    }
    // 模型矩阵（这里是单位矩阵，模型位于原点）
    val model = Mat4()
    // 计算MVP（模型-视图-投影）矩阵
    val mvp = projection * viewMatrix * model

    if (renderFaces) { // --- 实体渲染逻辑 ---
        // 创建超采样画布和Z-Buffer
        val ssBitmap = Bitmap()
        ssBitmap.allocN32Pixels(renderWidth, renderHeight)
        val canvas = Canvas(ssBitmap)
        canvas.clear(backgroundColor)
        val zBuffer = FloatArray(renderWidth * renderHeight) { Float.POSITIVE_INFINITY }
        // 遍历网格中的每一个面
        for (face in mesh.faces) {
            if (face.indices.size < 3) continue // 跳过无效的面
            // 获取面片前三个顶点以计算法线
            val v0_world = mesh.vertices[face.indices[0]].position
            val v1_world = mesh.vertices[face.indices[1]].position
            val v2_world = mesh.vertices[face.indices[2]].position
            // 计算面的法线（在模型空间中）
            val faceNormal = (v1_world - v0_world).cross(v2_world - v0_world).normalized()
            // 背面剔除：如果面的法线与相机前向向量的点积为正，说明面背对相机，剔除
            if (useBackFaceCulling && faceNormal.dot(cameraForward) > 0) continue
            // 计算光照强度
            val lightIntensity = calculateLightIntensity(faceNormal, lightDirection, ambientIntensity)
            // 将多边形面分割成三角形进行光栅化
            for (i in 0 until face.indices.size - 2) {
                val triIndices = listOf(face.indices[0], face.indices[i + 1], face.indices[i + 2])
                // --- 顶点着色器阶段 ---
                val shadedVertices = triIndices.map { index ->
                    val vertex = mesh.vertices[index]
                    // 1. 应用MVP变换，得到裁剪空间坐标
                    val clipPos = mvp.transform(vertex.position)
                    // 2. 透视除法，得到归一化设备坐标(NDC)，同时保存1/w用于透视校正
                    val oneOverW = 1.0f / clipPos.w
                    val ndc = Vec3(clipPos.x * oneOverW, clipPos.y * oneOverW, clipPos.z * oneOverW)
                    // 3. 视口变换，将NDC坐标映射到屏幕坐标
                    val screenX = (ndc.x + 1f) * 0.5f * renderWidth
                    val screenY = (-ndc.y + 1f) * 0.5f * renderHeight
                    ShadedVertex(Vec3(screenX, screenY, ndc.z), oneOverW, vertex.uv)
                }
                // --- 光栅化阶段 ---
                rasterizeTriangle(
                    shadedVertices,
                    renderWidth,
                    renderHeight,
                    zBuffer,
                    ssBitmap,
                    mesh.texture,
                    lightIntensity,
                    face.baseColor
                )
            }
        }
        // --- 抗锯齿下采样 ---
        val finalBitmap = Bitmap()
        finalBitmap.allocN32Pixels(width, height)
        if (antiAliasingLevel > 1) {
            // 遍历最终图像的每个像素
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var r = 0f
                    var g = 0f
                    var b = 0f
                    var a = 0f
                    // 对超采样画布上对应的4个像素进行求和
                    for (ssY in 0 until antiAliasingLevel) {
                        for (ssX in 0 until antiAliasingLevel) {
                            val color = ssBitmap.getColor(x * antiAliasingLevel + ssX, y * antiAliasingLevel + ssY)
                            a += Color.getA(color)
                            r += Color.getR(color)
                            g += Color.getG(color)
                            b += Color.getB(color)
                        }
                    }
                    // 计算平均颜色
                    val s2 = (antiAliasingLevel * antiAliasingLevel).toFloat()
                    val avgColor =
                        Color.makeARGB((a / s2).toInt(), (r / s2).toInt(), (g / s2).toInt(), (b / s2).toInt())
                    finalBitmap.erase(avgColor, IRect.makeXYWH(x, y, 1, 1))
                }
            }
        } else {
            // 如果不抗锯齿，直接复制
            Canvas(finalBitmap).drawImage(Image.makeFromBitmap(ssBitmap), 0f, 0f)
        }
        return Image.makeFromBitmap(finalBitmap)
    }
    // --- 线框渲染逻辑 ---
    val surface = Surface.makeRasterN32Premul(width, height)
    val canvas = surface.canvas
    canvas.clear(backgroundColor)
    val strokePaint = Paint().apply {
        color = Color.makeRGB(170, 220, 255)
        isAntiAlias = true
        mode = PaintMode.STROKE
        strokeWidth = 1.5f
    }

    // 辅助函数，将3D顶点投影到2D屏幕
    fun project(v: Vec3): Point {
        val p = mvp.transform(v)
        val invW = 1f / p.w
        return Point(((p.x * invW) + 1f) * 0.5f * width, (-(p.y * invW) + 1f) * 0.5f * height)
    }
    // 遍历所有面，绘制其边缘
    mesh.faces.forEach { face ->
        if (face.indices.size < 2) return@forEach
        val path = Path()
        val startPoint = project(mesh.vertices[face.indices[0]].position)
        path.moveTo(startPoint.x, startPoint.y)
        face.indices.drop(1)
            .forEach { path.lineTo(project(mesh.vertices[it].position).x, project(mesh.vertices[it].position).y) }
        path.lineTo(startPoint.x, startPoint.y)
        canvas.drawPath(path, strokePaint)
    }
    // 线框图
    return surface.makeImageSnapshot()
}

/**
 * 光栅化一个三角形。
 * @param vertices 三角形的三个着色后顶点。
 * @param width/height 画布尺寸。
 * @param zBuffer 深度缓冲。
 * @param bitmap 要绘制到的位图。
 * @param texture 可选的纹理。
 * @param lightIntensity 应用于该三角形的光照强度。
 * @param baseColor 基础颜色（当无纹理时使用）。
 */
fun rasterizeTriangle(
    vertices: List<ShadedVertex>,
    width: Int,
    height: Int,
    zBuffer: FloatArray,
    bitmap: Bitmap,
    texture: Bitmap?,
    lightIntensity: Float,
    baseColor: Int
) {
    val v0 = vertices[0]
    val v1 = vertices[1]
    val v2 = vertices[2]
    val p0 = v0.screenPos
    val p1 = v1.screenPos
    val p2 = v2.screenPos
    // 计算三角形的2D包围盒，减少需要检查的像素数量
    val minX = max(0, min(p0.x, min(p1.x, p2.x)).toInt())
    val maxX = min(width - 1, max(p0.x, max(p1.x, p2.x)).toInt())
    val minY = max(0, min(p0.y, min(p1.y, p2.y)).toInt())
    val maxY = min(height - 1, max(p0.y, max(p1.y, p2.y)).toInt())
    // 遍历包围盒内的每个像素
    for (y in minY..maxY) {
        for (x in minX..maxX) {
            // 计算当前像素(x, y)的重心坐标(w0, w1, w2)
            val denominator = ((p1.y - p2.y) * (p0.x - p2.x) + (p2.x - p1.x) * (p0.y - p2.y))
            if (denominator == 0f) continue
            val w0 = ((p1.y - p2.y) * (x - p2.x) + (p2.x - p1.x) * (y - p2.y)) / denominator
            val w1 = ((p2.y - p0.y) * (x - p2.x) + (p0.x - p2.x) * (y - p2.y)) / denominator
            val w2 = 1.0f - w0 - w1
            // 如果重心坐标都在[0,1]范围内，说明像素在三角形内
            if (w0 >= 0 && w1 >= 0 && w2 >= 0) {
                // --- 透视校正插值 ---
                // 1. 插值 1/w
                val oneOverW = v0.oneOverW * w0 + v1.oneOverW * w1 + v2.oneOverW * w2
                val w = 1.0f / oneOverW
                // 2. 插值 z/w
                val zOverW =
                    v0.screenPos.z * v0.oneOverW * w0 + v1.screenPos.z * v1.oneOverW * w1 + v2.screenPos.z * v2.oneOverW * w2
                // 3. 恢复真实的z值: z = (z/w) / (1/w)
                val zCorrect = zOverW * w
                val bufferIndex = y * width + x
                // --- 深度测试 ---
                if (zCorrect < zBuffer[bufferIndex]) {
                    // --- 纹理采样 ---
                    val texelColor = if (texture != null) {
                        // 4. 插值 u/w 和 v/w
                        val uOverW =
                            v0.uv.u * v0.oneOverW * w0 + v1.uv.u * v1.oneOverW * w1 + v2.uv.u * v2.oneOverW * w2
                        val vOverW =
                            v0.uv.v * v0.oneOverW * w0 + v1.uv.v * v1.oneOverW * w1 + v2.uv.v * v2.oneOverW * w2
                        // 5. 恢复真实的u, v: u = (u/w) / (1/w)
                        val u = uOverW * w
                        val v = vOverW * w
                        // 6. 将u,v坐标转换为纹理像素坐标并采样
                        val tx = (u * (texture.width)).toInt().coerceIn(0, texture.width - 1)
                        val ty = (v * (texture.height)).toInt().coerceIn(0, texture.height - 1)
                        texture.getColor(tx, ty)
                    } else {
                        baseColor
                    } // 如果没有纹理，使用面的基础颜色
                    // --- Alpha测试 ---
                    // 如果纹素的Alpha值低于阈值，则丢弃该片元，不更新Z-Buffer和颜色缓冲
                    if (Color.getA(texelColor) < 200) {
                        continue
                    }
                    // 深度测试通过，更新Z-Buffer
                    zBuffer[bufferIndex] = zCorrect
                    // 应用光照并写入最终颜色
                    val finalColor = applyLight(texelColor, lightIntensity)
                    bitmap.erase(finalColor, IRect.makeXYWH(x, y, 1, 1))
                }
            }
        }
    }
}
