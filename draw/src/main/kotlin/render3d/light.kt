package top.e404.skiko.draw.render3d

import org.jetbrains.skia.Color
import kotlin.math.max

// 定义一个全局的、标准化的平行光方向
val lightDirection = Vec3(0.7f, 1.0f, 0.5f).normalized()
// 定义环境光强度
val ambientIntensity = 0.5f

/**
 * 计算基于兰伯特余弦定律的光照强度
 * @param normal 面的法线向量
 * @return 返回一个0到1之间的光照强度值
 */
fun calculateLightIntensity(normal: Vec3): Float {
    // 漫反射强度 = max(0, 法线 · 光源方向)
    val diffuseIntensity = max(0f, normal.dot(lightDirection))
    // 最终光强 = 环境光 + 漫反射光
    return ambientIntensity + (1f - ambientIntensity) * diffuseIntensity
}

/**
 * 将计算出的光照强度应用到基础颜色上
 * @param baseColor 原始颜色值
 * @param intensity 光照强度
 * @return 返回应用光照后的最终颜色
 */
fun applyLight(baseColor: Int, intensity: Float): Int {
    val r = (Color.getR(baseColor) * intensity).toInt().coerceIn(0, 255)
    val g = (Color.getG(baseColor) * intensity).toInt().coerceIn(0, 255)
    val b = (Color.getB(baseColor) * intensity).toInt().coerceIn(0, 255)
    return Color.makeARGB(Color.getA(baseColor), r, g, b)
}
