package top.e404.tavolo.util

import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.FontStyleSet
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.TypefaceFontProvider
import top.e404.tavolo.TavoloFonts
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Tavolo 全局字体管理器。
 *
 * 对外统一使用稳定的字体名引用字体，调用方可以注册业务字体文件、字体字节或系统字体别名。
 * 渲染层只保存字体名，实际绘制时再解析为本地 `Typeface`。
 */
object FontManager {
    const val DEFAULT = TavoloFonts.LW

    private data class SystemFontRef(
        val familyName: String,
        val style: FontStyle
    )

    val fontMgr: FontMgr = FontMgr.default

    private val registeredFonts = ConcurrentHashMap<String, Typeface>()
    private val systemFontAliases = ConcurrentHashMap<String, SystemFontRef>()

    var fontProvider: TypefaceFontProvider = TypefaceFontProvider()
        private set

    var fonts: FontCollection = createFontCollection()
        private set

    var defaultFamily: String = DEFAULT

    var fallbackTypeface: Typeface = Typeface.makeEmpty()

    val defaultFont: Font by lazy { TavoloFonts.font(DEFAULT, 20F) }

    fun register(name: String, typeface: Typeface): String {
        requireValidName(name)
        registeredFonts[name] = typeface
        fontProvider.registerTypeface(typeface, name)
        return name
    }

    fun isRegistered(name: String): Boolean =
        registeredFonts.containsKey(name)

    fun registerFile(name: String, file: File, index: Int = 0): String =
        registerFile(name, file.absolutePath, index)

    fun registerFile(name: String, path: String, index: Int = 0): String {
        requireValidName(name)
        val typeface = fontMgr.makeFromFile(path, index)
            ?: error("无法加载字体文件: $path")
        return register(name, typeface)
    }

    fun registerBytes(name: String, bytes: ByteArray, index: Int = 0): String =
        registerData(name, Data.makeFromBytes(bytes), index)

    fun registerData(name: String, data: Data, index: Int = 0): String {
        requireValidName(name)
        val typeface = fontMgr.makeFromData(data, index)
            ?: error("无法从字体数据加载字体: $name")
        return register(name, typeface)
    }

    fun registerSystem(name: String, familyName: String = name, style: FontStyle = FontStyle.NORMAL): String {
        requireValidName(name)
        systemFontAliases[name] = SystemFontRef(familyName, style)
        return name
    }

    fun registerDirectory(directory: File, recursive: Boolean = true): List<String> {
        if (!directory.exists()) return emptyList()
        val files = if (recursive) directory.walkTopDown() else directory.listFiles()?.asSequence().orEmpty()
        return files
            .filter { it.isFile && it.extension.lowercase() in fontExtensions }
            .map { registerFile(it.nameWithoutExtension, it) }
            .toList()
    }

    fun resolve(name: String? = defaultFamily): Typeface {
        val family = name?.takeIf { it.isNotBlank() } ?: defaultFamily
        resolveRegisteredOrSystem(family)?.let { return it }
        if (family != defaultFamily) {
            resolveRegisteredOrSystem(defaultFamily)?.let { return it }
        }
        return fallbackTypeface
    }

    fun font(name: String? = defaultFamily, size: Float): Font =
        Font(resolve(name), size)

    fun matchFamily(familyName: String): FontStyleSet {
        val registered = fontProvider.matchFamily(familyName)
        if (registered.count() != 0) return registered
        return fontMgr.matchFamily(familyName)
    }

    fun systemFamilies(): List<String> =
        (0 until fontMgr.familiesCount).map { fontMgr.getFamilyName(it) }

    fun clearRegistered() {
        registeredFonts.clear()
        systemFontAliases.clear()
        fontProvider = TypefaceFontProvider()
        fonts = createFontCollection()
        defaultFamily = DEFAULT
        fallbackTypeface = Typeface.makeEmpty()
    }

    private fun resolveRegisteredOrSystem(name: String): Typeface? {
        registeredFonts[name]?.let { return it }
        systemFontAliases[name]?.let { ref ->
            fontMgr.matchFamilyStyle(ref.familyName, ref.style)?.let { return it }
        }
        return fontMgr.matchFamilyStyle(name, FontStyle.NORMAL)
    }

    private fun requireValidName(name: String) {
        require(name.isNotBlank()) { "字体名不能为空" }
    }

    private fun createFontCollection(): FontCollection =
        FontCollection()
            .setDynamicFontManager(fontProvider)
            .setDefaultFontManager(fontMgr)

    private val fontExtensions = setOf("ttf", "ttc", "otf")
}
