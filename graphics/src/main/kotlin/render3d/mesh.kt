package top.e404.tavolo.draw.render3d

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Rect

/**
 * 创建一个简单的、无纹理的长方体
 * @param dimensions 长方体的尺寸 (宽, 高, 深)
 * @param baseColor 长方体的纯色
 */
fun createCuboid(dimensions: Vec3, baseColor: Int): Mesh {
    val (w, h, d) = dimensions
    val vertices = listOf(
        Vertex(Vec3(-w / 2, -h / 2, -d / 2), Vec2(0f, 0f)), Vertex(Vec3(w / 2, -h / 2, -d / 2), Vec2(0f, 0f)),
        Vertex(Vec3(w / 2, h / 2, -d / 2), Vec2(0f, 0f)), Vertex(Vec3(-w / 2, h / 2, -d / 2), Vec2(0f, 0f)),
        Vertex(Vec3(-w / 2, -h / 2, d / 2), Vec2(0f, 0f)), Vertex(Vec3(w / 2, -h / 2, d / 2), Vec2(0f, 0f)),
        Vertex(Vec3(w / 2, h / 2, d / 2), Vec2(0f, 0f)), Vertex(Vec3(-w / 2, h / 2, d / 2), Vec2(0f, 0f))
    )
    // 定义6个面，每个面由4个顶点索引组成
    val faces = listOf(
        Face(listOf(0, 3, 2, 1), baseColor), Face(listOf(1, 2, 6, 5), baseColor), Face(listOf(5, 6, 7, 4), baseColor),
        Face(listOf(4, 7, 3, 0), baseColor), Face(listOf(3, 7, 6, 2), baseColor), Face(listOf(4, 0, 1, 5), baseColor)
    )
    return Mesh(vertices, faces)
}

/**
 * 枚举，定义长方体的六个面方向。
 */
enum class FaceDirection { RIGHT, LEFT, TOP, BOTTOM, FRONT, BACK }

/**
 * 创建一个带UV坐标的长方体，用于纹理映射。
 * @param dims 长方体尺寸
 * @param faceUVs 一个Map，指定每个面的UV坐标在纹理图上的矩形区域
 * @param textureWidth 纹理图的总宽度
 * @param textureHeight 纹理图的总高度
 */
fun createUVCuboid(dims: Vec3, faceUVs: Map<FaceDirection, Rect>, textureWidth: Float, textureHeight: Float): Mesh {
    val (w, h, d) = dims
    val vertices = mutableListOf<Vertex>()
    val faces = mutableListOf<Face>()
    // 定义长方体的8个顶点
    val v = listOf(
        Vec3(-w / 2, -h / 2, d / 2),
        Vec3(w / 2, -h / 2, d / 2),
        Vec3(w / 2, h / 2, d / 2),
        Vec3(-w / 2, h / 2, d / 2),
        Vec3(w / 2, -h / 2, -d / 2),
        Vec3(-w / 2, -h / 2, -d / 2),
        Vec3(-w / 2, h / 2, -d / 2),
        Vec3(w / 2, h / 2, -d / 2)
    )

    // 辅助函数，将像素坐标转换为0-1范围的UV坐标
    fun u(px: Float) = px / textureWidth
    fun v(py: Float) = py / textureHeight

    fun addFace(vIndices: List<Int>, uvRect: Rect) {
        val faceIndices = mutableListOf<Int>()
        // 使用像素边界映射，让 UV 区域内每个纹素占据相同宽度。
        // 右/下边界略微回退，避免 nearest + floor 采样在边界处落入相邻 atlas 区域。
        val edgeEpsilon = 0.001f
        val samplingRect = Rect.makeLTRB(
            uvRect.left,
            uvRect.top,
            uvRect.right - edgeEpsilon,
            uvRect.bottom - edgeEpsilon
        )
        // 定义当前面的4个顶点的UV坐标
        val uvs = listOf(
            Vec2(u(samplingRect.left), v(samplingRect.bottom)), Vec2(u(samplingRect.right), v(samplingRect.bottom)),
            Vec2(u(samplingRect.right), v(samplingRect.top)), Vec2(u(samplingRect.left), v(samplingRect.top))
        )
        // 为每个顶点创建Vertex对象并添加到列表中
        for (i in vIndices.indices) {
            vertices.add(Vertex(v[vIndices[i]], uvs[i]))
            faceIndices.add(vertices.size - 1)
        }
        faces.add(Face(faceIndices, Color.WHITE))
    }
    // 根据faceUVs Map为存在的面生成几何数据
    faceUVs[FaceDirection.FRONT]?.let { addFace(listOf(0, 1, 2, 3), it) }
    faceUVs[FaceDirection.BACK]?.let { addFace(listOf(4, 5, 6, 7), it) }
    faceUVs[FaceDirection.RIGHT]?.let { addFace(listOf(1, 4, 7, 2), it) }
    faceUVs[FaceDirection.LEFT]?.let { addFace(listOf(5, 0, 3, 6), it) }
    faceUVs[FaceDirection.TOP]?.let { addFace(listOf(3, 2, 7, 6), it) }
    faceUVs[FaceDirection.BOTTOM]?.let { addFace(listOf(5, 4, 1, 0), it) }
    return Mesh(vertices, faces)
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
    return Mesh(
        combinedVertices,
        combinedFaces,
        texture ?: meshes.firstOrNull()?.texture,
        castsShadow = meshes.any { it.castsShadow },
        receivesShadow = meshes.any { it.receivesShadow }
    )
}
