package top.e404.tavolo.wordcloud

import org.jetbrains.skia.Color
import top.e404.tavolo.TavoloFonts
import kotlin.random.Random

data class WordCloudEntry(
    val text: String,
    val weight: Int,
)

data class WordCloudOptions(
    val width: Int = 1600,
    val height: Int = 1200,
    val fontFamily: String = TavoloFonts.MI,
    val minFontSize: Float = 18f,
    val maxFontSize: Float = 180f,
    val padding: Int = 2,
    val backgroundColor: Int = 0xFFDDDDDD.toInt(),
    val maxWords: Int = 500,
    val spiralStep: Float = 6f,
    val angleStep: Float = 0.38f,
    val maxPlacementAttempts: Int = 2800,
    val randomSeed: Long = 404L,
    val palette: WordCloudPalette = WordCloudPalette.default(),
    val rotations: List<Float> = listOf(0f),
)

data class WordCloudPalette(
    val colors: List<Int>,
) {
    init {
        require(colors.isNotEmpty()) { "词云色板不能为空" }
    }

    fun pick(index: Int, random: Random): Int =
        colors[(index + random.nextInt(colors.size)) % colors.size]

    companion object {
        fun default(): WordCloudPalette =
            WordCloudPalette(
                listOf(
                    Color.makeRGB(38, 70, 83),
                    Color.makeRGB(42, 157, 143),
                    Color.makeRGB(233, 196, 106),
                    Color.makeRGB(231, 111, 81),
                    Color.makeRGB(87, 117, 144),
                    Color.makeRGB(144, 190, 109),
                )
            )
    }
}

data class WordCloudPlacedEntry(
    val text: String,
    val weight: Int,
    val fontSize: Float,
    val color: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val baselineY: Float,
    val rotation: Float,
    val drawWidth: Int = width,
    val drawHeight: Int = height,
    val drawX: Float = 0f,
    val drawBaselineY: Float = baselineY,
)
