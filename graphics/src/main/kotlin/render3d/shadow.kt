package top.e404.tavolo.draw.render3d

import org.jetbrains.skia.Color
import kotlin.math.max
import kotlin.math.pow

/**
 * 阴影贴图缓冲区
 * 存储从光源视角看到的深度值
 */
class ShadowMap(val width: Int, val height: Int) {
    val buffer = FloatArray(width * height) { Float.POSITIVE_INFINITY }
    val faceIds = IntArray(width * height) { -1 }

    fun clear() {
        buffer.fill(Float.POSITIVE_INFINITY)
        faceIds.fill(-1)
    }

    fun set(x: Int, y: Int, depth: Float, faceId: Int = -1) {
        if (x in 0 until width && y in 0 until height) {
            val index = y * width + x
            buffer[index] = depth
            faceIds[index] = faceId
        }
    }

    fun get(x: Int, y: Int): Float {
        if (x in 0 until width && y in 0 until height) {
            return buffer[y * width + x]
        }
        return Float.POSITIVE_INFINITY
    }

    fun getFaceId(x: Int, y: Int): Int {
        if (x in 0 until width && y in 0 until height) {
            return faceIds[y * width + x]
        }
        return -1
    }
}

/**
 * Blinn-Phong 光照模型计算
 */
fun calculateBlinnPhong(
    normal: Vec3,       // 面法线
    lightDir: Vec3,     // 指向光源的方向 (normalized)
    viewDir: Vec3,      // 指向相机的方向 (normalized)
    baseColor: Int,     // 材质颜色
    shadowFactor: Float,// 阴影因子 (0=全阴影, 1=无阴影)
    ambientStrength: Float = 0.6f, // 环境光强度，默认偏亮以保留纹理细节
    specularStrength: Float = 0.3f, // 高光强度
    shininess: Float = 32f          // 高光反光度
): Int {
    // 1. 环境光 (Ambient)
    // 环境光不受阴影影响，保证阴影处不是死黑
    val ambient = ambientStrength

    // 2. 漫反射 (Diffuse) - 兰伯特
    val diff = max(normal.dot(lightDir), 0f)
    val diffuse = diff * (1.0f - ambientStrength) // 能量守恒近似

    // 3. 镜面反射 (Specular) - Blinn-Phong
    // 半程向量 H = (L + V) / |L + V|
    val halfDir = (lightDir + viewDir).normalized()
    val specAngle = max(normal.dot(halfDir), 0f)
    val specular = specAngle.pow(shininess) * specularStrength

    // 综合光照 = 环境光 + (漫反射 + 高光) * 阴影因子
    // 注意：阴影只遮挡漫反射和高光
    val lightIntensity = ambient + (diffuse + specular) * shadowFactor

    val r = (Color.getR(baseColor) * lightIntensity).toInt().clamp(0, 255)
    val g = (Color.getG(baseColor) * lightIntensity).toInt().clamp(0, 255)
    val b = (Color.getB(baseColor) * lightIntensity).toInt().clamp(0, 255)

    return Color.makeARGB(Color.getA(baseColor), r, g, b)
}
