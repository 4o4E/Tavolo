package top.e404.tavolo.draw.compose

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale

internal data class GraphemeCluster(
    val text: String,
    val start: Int,
    val end: Int
) {
    val codePoints: IntArray by lazy { text.codePoints().toArray() }
}

internal data class LineBreakSegment(
    val start: Int,
    val end: Int
)

internal fun segmentGraphemeClusters(text: String): List<GraphemeCluster> {
    if (text.isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance(ULocale.ROOT)
    iterator.setText(text)
    val result = mutableListOf<GraphemeCluster>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        result += GraphemeCluster(text.substring(start, end), start, end)
        start = end
        end = iterator.next()
    }
    return result
}

internal fun segmentLineBreaks(text: String): List<LineBreakSegment> {
    if (text.isEmpty()) return emptyList()
    val iterator = BreakIterator.getLineInstance(ULocale.ROOT)
    iterator.setText(text)
    val result = mutableListOf<LineBreakSegment>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        if (start < end) result += LineBreakSegment(start, end)
        start = end
        end = iterator.next()
    }
    return result
}

internal fun GraphemeCluster.needsClusterFontChoice(): Boolean =
    codePoints.any { codePoint ->
        codePoint == ZERO_WIDTH_JOINER ||
            codePoint.isRegionalIndicator() ||
            codePoint.isVariationSelector() ||
            codePoint.isEmojiModifier() ||
            when (Character.getType(codePoint)) {
                Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt() -> true
                else -> false
            }
    }

internal fun GraphemeCluster.hasEnclosingMark(): Boolean =
    codePoints.any { Character.getType(it) == Character.ENCLOSING_MARK.toInt() }

internal fun GraphemeCluster.hasCombiningMark(): Boolean =
    codePoints.any {
        when (Character.getType(it)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt() -> true
            else -> false
        }
    }

internal fun GraphemeCluster.hasEmojiSequenceControl(): Boolean =
    codePoints.any {
        it == ZERO_WIDTH_JOINER ||
            it.isRegionalIndicator() ||
            it.isVariationSelector() ||
            it.isEmojiModifier()
    }

internal fun GraphemeCluster.hasZeroWidthJoiner(): Boolean =
    codePoints.any { it == ZERO_WIDTH_JOINER }

internal fun GraphemeCluster.hasRegionalIndicator(): Boolean =
    codePoints.any { it.isRegionalIndicator() }

internal fun GraphemeCluster.hasEmojiModifier(): Boolean =
    codePoints.any { it.isEmojiModifier() }

internal fun GraphemeCluster.hasVariationSelector(): Boolean =
    codePoints.any { it.isVariationSelector() }

internal fun GraphemeCluster.hasEmojiPresentationSelector(): Boolean =
    codePoints.any { it == EMOJI_PRESENTATION_SELECTOR }

internal fun codePointNeedsGlyph(codePoint: Int): Boolean =
    codePoint != ZERO_WIDTH_JOINER && !codePoint.isVariationSelector()

internal fun codePointIsVariationSelector(codePoint: Int): Boolean =
    codePoint.isVariationSelector()

private fun Int.isVariationSelector(): Boolean =
    this in 0xFE00..0xFE0F || this in 0xE0100..0xE01EF

private fun Int.isRegionalIndicator(): Boolean =
    this in 0x1F1E6..0x1F1FF

private fun Int.isEmojiModifier(): Boolean =
    this in 0x1F3FB..0x1F3FF

private const val ZERO_WIDTH_JOINER = 0x200D
private const val EMOJI_PRESENTATION_SELECTOR = 0xFE0F
