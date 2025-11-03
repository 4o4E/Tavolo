package top.e404.skiko.draw.render3d

import org.jetbrains.skia.Bitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 轨道相机数据类，通过目标点、方位角、仰角和距离来定义相机位置
 * @property target 相机注视的目标点
 * @property azimuthDegrees 方位角，绕Y轴旋转的角度（左右视角）
 * @property elevationDegrees 仰角，绕X轴旋转的角度（上下视角）
 * @property distance 相机到目标点的距离
 * @property upVector 相机的上方向向量，通常为(0, 1, 0)
 */
data class OrbitCamera(
    var target: Vec3 = Vec3(0f, 0f, 0f),
    var azimuthDegrees: Float = 0f,
    var elevationDegrees: Float = 0f,
    var distance: Float = 10f,
    var upVector: Vec3 = Vec3(0f, 1f, 0f)
)

/**
 * 根据轨道相机参数创建视图矩阵和相机位置
 * @param camera 轨道相机实例
 * @return 返回一个包含视图矩阵和相机世界坐标的Pair
 */
fun createViewMatrix(camera: OrbitCamera): Pair<Mat4, Vec3> {
    val azimuth = camera.azimuthDegrees * (PI / 180.0).toFloat()
    val elevation = camera.elevationDegrees * (PI / 180.0).toFloat()
    // 通过球面坐标计算相机位置
    val eyeX = camera.target.x + camera.distance * cos(elevation) * sin(azimuth)
    val eyeY = camera.target.y + camera.distance * sin(elevation)
    val eyeZ = camera.target.z + camera.distance * cos(elevation) * cos(azimuth)
    val eyePosition = Vec3(eyeX, eyeY, eyeZ)
    // 使用Mat4.lookAt创建视图矩阵
    val viewMatrix = Mat4.lookAt(eyePosition, camera.target, camera.upVector)
    return Pair(viewMatrix, eyePosition)
}

/**
 * 将多个独立的Mesh合并成一个大的Mesh
 * 这可以提高渲染效率，因为只需要处理一个绘制调用
 */
fun combineMeshes(meshes: List<Mesh>, texture: Bitmap? = null): Mesh {
    val combinedVertices = mutableListOf<Vertex>()
    val combinedFaces = mutableListOf<Face>()
    var vertexOffset = 0
    for (mesh in meshes) {
        combinedVertices.addAll(mesh.vertices)
        for (face in mesh.faces) {
            // 重新计算面的顶点索引，加上偏移量
            val newIndices = face.indices.map { it + vertexOffset }
            combinedFaces.add(Face(newIndices, face.baseColor))
        }
        vertexOffset += mesh.vertices.size
    }
    return Mesh(combinedVertices, combinedFaces, texture ?: meshes.firstOrNull()?.texture)
}
