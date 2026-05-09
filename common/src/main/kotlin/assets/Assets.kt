package top.e404.tavolo.assets

import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Image
import org.jetbrains.skia.Typeface
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.readText

data class AssetsConfig(
    val rootDir: Path = defaultRootDir()
) {
    companion object {
        fun defaultRootDir(): Path {
            val explicit = System.getProperty("tavolo.assets.dir")
                ?: System.getenv("TAVOLO_ASSETS_DIR")
            if (!explicit.isNullOrBlank()) return Paths.get(explicit)

            val cwd = Paths.get("").toAbsolutePath()
            val candidates = sequenceOf(
                cwd.resolve("assets"),
                cwd.resolve("../assets"),
                cwd.resolve("../../assets")
            )
            return candidates.firstOrNull { it.exists() } ?: cwd.resolve("assets")
        }
    }
}

object Assets {
    @Volatile
    var config: AssetsConfig = AssetsConfig()
        private set

    private val bytesCache = ConcurrentHashMap<String, ByteArray>()
    private val imageCache = ConcurrentHashMap<String, Image>()
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    fun configure(config: AssetsConfig) {
        this.config = config
        clearCache()
    }

    fun clearCache() {
        bytesCache.clear()
        imageCache.clear()
        typefaceCache.clear()
    }

    fun exists(path: String): Boolean =
        resolve(path).isRegularFile()

    fun bytes(path: String): ByteArray =
        bytesCache.getOrPut(path) { resolveExisting(path).readBytes() }

    fun text(path: String): String =
        resolveExisting(path).readText()

    fun data(path: String): Data =
        Data.makeFromBytes(bytes(path))

    fun image(path: String): Image =
        imageCache.getOrPut(path) {
            Image.makeFromEncoded(bytes(path))
                ?: error("资源不是有效图片: ${resolve(path).absolutePathString()}")
        }

    fun typeface(path: String, index: Int = 0): Typeface =
        typefaceCache.getOrPut("$path#$index") {
            val data = data(path)
            FontMgr.default.makeFromData(data, index)
                ?: error("资源不是有效字体: ${resolve(path).absolutePathString()}")
        }

    fun resolve(path: String): Path {
        require(path.isNotBlank()) { "资源路径不能为空" }
        val raw = Paths.get(path)
        require(!raw.isAbsolute) { "资源路径不能是绝对路径: $path" }

        val root = config.rootDir.toAbsolutePath().normalize()
        val resolved = root.resolve(path).normalize()
        require(resolved.startsWith(root)) { "资源路径不能越界: $path" }
        return resolved
    }

    private fun resolveExisting(path: String): Path {
        val resolved = resolve(path)
        if (!Files.isRegularFile(resolved)) {
            error("资源文件不存在: ${resolved.absolutePathString()}，请检查 tavolo.assets.dir 或 TAVOLO_ASSETS_DIR")
        }
        return resolved
    }
}
