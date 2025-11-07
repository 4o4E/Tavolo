package top.e404.skiko.draw.render3d

import org.jetbrains.skia.Bitmap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// --- 核心3D数学与数据结构 ---

/**
 * 二维向量，主要用于存储UV纹理坐标
 * @property u U坐标（横向）
 * @property v V坐标（纵向）
 */
data class Vec2(val u: Float, val v: Float)

/**
 * 三维向量，用于表示空间中的点、方向或颜色
 * 提供了丰富的运算符重载以简化向量运算
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vec3(x * scalar, y * scalar, z * scalar)
    operator fun times(other: Vec3) = Vec3(x * other.x, y * other.y, z * other.z) // 逐元素相乘
    operator fun div(scalar: Float) = Vec3(x / scalar, y / scalar, z / scalar)
    fun cross(other: Vec3) = Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x) // 叉乘
    fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z // 点乘
    fun length() = sqrt(x * x + y * y + z * z) // 计算向量长度（模）
    fun normalized() = if (length() > 0) this / length() else this // 单位化向量

    fun rotateX(angle: Float): Vec3 {
        val cos = cos(angle)
        val sin = sin(angle)
        return Vec3(x, y * cos - z * sin, y * sin + z * cos)
    }

    fun rotateY(angle: Float): Vec3 {
        val cos = cos(angle)
        val sin = sin(angle)
        return Vec3(x * cos + z * sin, y, -x * sin + z * cos)
    }

    fun rotateZ(angle: Float): Vec3 {
        val cos = cos(angle)
        val sin = sin(angle)
        return Vec3(x * cos - y * sin, x * sin + y * cos, z)
    }

    fun rotate(rotation: Transformation.Rotate): Vec3 {
        val radX = Math.toRadians(rotation.x.toDouble()).toFloat()
        val radY = Math.toRadians(rotation.y.toDouble()).toFloat()
        val radZ = Math.toRadians(rotation.z.toDouble()).toFloat()
        return this.rotateZ(radZ).rotateY(radY).rotateX(radX)
    }

    fun scale(scaling: Transformation.Scale): Vec3 {
        return Vec3(x * scaling.x, y * scaling.y, z * scaling.z)
    }

    fun translate(translation: Transformation.Translate): Vec3 {
        return this.plus(Vec3(translation.x, translation.y, translation.z))
    }
}

/**
 * 四维向量，主要用于表示齐次坐标，在3D变换中至关重要
 */
data class Vec4(val x: Float, val y: Float, val z: Float, val w: Float)

/**
 * 4x4矩阵，用于执行3D变换（模型、视图、投影）
 *
 * 默认初始化为单位矩阵
 * @property m 一个16元素的浮点数组，按列主序存储矩阵。
 */
data class Mat4(val m: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f }) {
    // 矩阵乘法
    operator fun times(other: Mat4): Mat4 {
        val result = Mat4(FloatArray(16))
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += this.m[i + k * 4] * other.m[k + j * 4]
                }
                result.m[i + j * 4] = sum
            }
        }
        return result
    }

    // 将矩阵应用于一个3D向量，返回一个4D齐次坐标向量
    fun transform(v: Vec3): Vec4 {
        val x = m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12]
        val y = m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13]
        val z = m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14]
        val w = m[3] * v.x + m[7] * v.y + m[11] * v.z + m[15]
        return Vec4(x, y, z, w)
    }

    companion object {
        /**
         * 创建透视投影矩阵
         * @param fov 视野角度（弧度）
         * @param aspect 宽高比
         * @param near 近裁剪面
         * @param far 远裁剪面
         */
        fun perspective(fov: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val f = 1.0f / tan(fov / 2.0f)
            return Mat4(
                floatArrayOf(
                    f / aspect, 0f, 0f, 0f,
                    0f, f, 0f, 0f,
                    0f, 0f, (far + near) / (near - far), -1f,
                    0f, 0f, (2 * far * near) / (near - far), 0f
                )
            )
        }

        /**
         * 创建视图矩阵（LookAt矩阵）
         * @param eye 相机位置
         * @param center 目标观察点
         * @param up 相机的上方向向量
         */
        fun lookAt(eye: Vec3, center: Vec3, up: Vec3): Mat4 {
            val f = (center - eye).normalized()
            val s = f.cross(up).normalized()
            val u = s.cross(f)
            return Mat4(
                floatArrayOf(
                    s.x, u.x, -f.x, 0f,
                    s.y, u.y, -f.y, 0f,
                    s.z, u.z, -f.z, 0f,
                    -s.dot(eye), -u.dot(eye), f.dot(eye), 1f
                )
            )
        }

        /**
         * 创建正交投影矩阵
         */
        fun orthographic(left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float): Mat4 {
            val m = FloatArray(16) { 0f }
            m[0] = 2f / (right - left)
            m[5] = 2f / (top - bottom)
            m[10] = -2f / (far - near)
            m[12] = -(right + left) / (right - left)
            m[13] = -(top + bottom) / (top - bottom)
            m[14] = -(far + near) / (far - near)
            m[15] = 1f
            return Mat4(m)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Mat4

        if (!m.contentEquals(other.m)) return false

        return true
    }

    override fun hashCode(): Int {
        return m.contentHashCode()
    }
}

/**
 * 顶点数据结构，包含位置和UV坐标
 */
data class Vertex(val position: Vec3, val uv: Vec2)

/**
 * 面（多边形）数据结构，包含顶点索引和基础颜色
 */
data class Face(val indices: List<Int>, val baseColor: Int)

/**
 * 网格模型，是渲染的基本单位，由顶点、面和可选的纹理组成
 */
data class Mesh(val vertices: List<Vertex>, val faces: List<Face>, val texture: Bitmap? = null)

/**
 * 着色后的顶点，存储屏幕坐标、用于透视校正的1/w值和UV坐标
 */
data class ShadedVertex(val screenPos: Vec3, val oneOverW: Float, val uv: Vec2)

/**
 * 封装所有可能的变换操作
 */
sealed class Transformation {
    data class Rotate(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) : Transformation()
    data class Scale(val x: Float = 1f, val y: Float = 1f, val z: Float = 1f) : Transformation()
    data class Translate(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) : Transformation()
}
