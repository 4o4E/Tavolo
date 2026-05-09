@file:Suppress("UNUSED")

package top.e404.tavolo

import org.jetbrains.skia.Font
import org.jetbrains.skia.Typeface
import top.e404.tavolo.util.FontManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.awt.Font as AwtFont

/**
 * Tavolo 内置字体清单。
 *
 * 这里仅维护项目随包或部署目录约定提供的字体文件名；字体加载、注册和解析统一交给 `FontManager`。
 */
object TavoloFonts {
    const val LW_LIGHT = "lxgw-wenkai-light"
    const val LW = "lxgw-wenkai"
    const val LW_BOLD = "lxgw-wenkai-bold"
    const val DF_LEISHO_SB = "df-leisho-sb"
    const val YGODIY_MATRIXBOLDSMALLCAPS = "ygodiy-matrix-bold-small-caps"
    const val YGO_DIY_GB = "ygo-diy-gb"
    const val YGODIY_JP = "ygodiy-jp"
    const val YGO_DIY_2_BIG5 = "ygo-diy-2-big5"
    const val FOT_RODIN = "fot-rodin-pro-m"
    const val MONO_LIGHT = "mono-light"
    const val MONO = "mono"
    const val MONO_BOLD = "mono-bold"
    const val YAHEI_LIGHT = "microsoft-yahei-light"
    const val YAHEI = "microsoft-yahei"
    const val YAHEI_BOLD = "microsoft-yahei-bold"
    const val MI_LIGHT = "misans-light"
    const val MI = "misans"
    const val MI_BOLD = "misans-bold"
    const val HEI = "simhei"
    const val MINECRAFT = "minecraft"
    const val ZHONG_SONG = "stzhongsong"
    const val GNU_UNIFONT = "gnu-unifont-full"
    const val GLOW_SANS = "glow-sans-sc-normal-heavy"
    const val RO_G_SAN_SRF_STD = "rog-sans-srf-std-bold"
    const val LI_HEI = "lihei"

    var fontDir = "data/font"

    private val registered = ConcurrentHashMap.newKeySet<String>()
    private val awtFonts = ConcurrentHashMap<String, AwtFont>()

    private val fontFiles = mapOf(
        LW_LIGHT to "LXGWWenKai-Light.ttf",
        LW to "LXGWWenKai-Regular.ttf",
        LW_BOLD to "LXGWWenKai-Bold.ttf",
        DF_LEISHO_SB to "DFLeiSho-SB.ttf",
        YGODIY_MATRIXBOLDSMALLCAPS to "YGODIY-MatrixBoldSmallCaps.ttf",
        YGO_DIY_GB to "YGO-DIY-GB.ttf",
        YGODIY_JP to "YGODIY-JP.otf",
        YGO_DIY_2_BIG5 to "YGO-DIY-2-BIG5.ttf",
        FOT_RODIN to "FOT-Rodin Pro M.ttf",
        MONO_LIGHT to "Mono-Light.ttf",
        MONO to "Mono-Regular.ttf",
        MONO_BOLD to "Mono-Bold.ttf",
        YAHEI_LIGHT to "msyhl.ttc",
        YAHEI to "msyh.ttc",
        YAHEI_BOLD to "msyhbd.ttc",
        MI_LIGHT to "MiSans-Light.ttf",
        MI to "MiSans-Regular.ttf",
        MI_BOLD to "MiSans-Bold.ttf",
        HEI to "simhei.ttf",
        MINECRAFT to "Minecraft.ttf",
        ZHONG_SONG to "STZHONGS.TTF",
        GNU_UNIFONT to "gnu-unifont-full.ttf",
        GLOW_SANS to "GlowSansSC-Normal-Heavy.otf",
        RO_G_SAN_SRF_STD to "RoGSanSrfStd-Bd.otf",
        LI_HEI to "力黑体.otf",
    )

    fun register(name: String): String {
        if (registered.add(name) || !FontManager.isRegistered(name)) {
            FontManager.registerFile(name, file(name))
        }
        return name
    }

    fun registerAll(): List<String> =
        fontFiles.keys.map { register(it) }

    fun typeface(name: String): Typeface =
        FontManager.resolve(register(name))

    fun font(name: String, size: Float): Font =
        FontManager.font(register(name), size)

    fun awtFont(name: String): AwtFont =
        awtFonts.getOrPut(name) {
            file(name).inputStream().use {
                AwtFont.createFont(AwtFont.TRUETYPE_FONT, it)!!
            }
        }

    fun file(name: String): File {
        val fileName = fontFiles[name] ?: error("未知内置字体: $name")
        return File(fontDir, fileName)
    }
}
