package top.e404.tavolo.draw.compose

import org.jetbrains.skia.Image
import org.jetbrains.skia.Path
import org.jetbrains.skia.RRect

interface Modifier {
    fun then(other: Modifier): Modifier = if (other === Modifier) this else CombinedModifier(this, other)
    fun <R> fold(initial: R, operation: (R, Modifier) -> R): R
    fun toList() = fold(mutableListOf<Modifier>()) { acc, mod ->
        acc.add(mod)
        acc
    }

    companion object : Modifier {
        override fun <R> fold(initial: R, operation: (R, Modifier) -> R): R = initial
        override fun then(other: Modifier): Modifier = other
    }
}

private class CombinedModifier(private val outer: Modifier, private val inner: Modifier) : Modifier {
    override fun <R> fold(initial: R, operation: (R, Modifier) -> R): R {
        return inner.fold(outer.fold(initial, operation), operation)
    }
}

interface ElementModifier : Modifier {
    override fun <R> fold(initial: R, operation: (R, Modifier) -> R): R {
        return operation(initial, this)
    }
}

/**
 * 定义可用于裁剪的形状
 */
sealed interface Shape {
    fun createPath(width: Float, height: Float): Path

    /** 圆角矩形 */
    data class RoundedRect(val radius: Float) : Shape {
        override fun createPath(width: Float, height: Float): Path {
            return Path().addRRect(RRect.makeXYWH(0f, 0f, width, height, radius))
        }
    }

    /** 圆形 */
    object Circle : Shape {
        override fun createPath(width: Float, height: Float): Path {
            val radius = minOf(width, height) / 2f
            return Path().addCircle(radius, radius, radius)
        }
    }
}

data class Background(val color: Int) : ElementModifier
data class BackgroundImage(
    val image: Image,
    val overflow: ImageOverflow = ImageOverflow.Crop
) : ElementModifier
data class Clip(val shape: Shape) : ElementModifier

sealed interface StrokeStyle {
    data object Solid : StrokeStyle

    data class Dashed(
        val intervals: List<Float> = listOf(8f, 4f),
        val phase: Float = 0f
    ) : StrokeStyle

    data class Dotted(
        val dot: Float = 1f,
        val gap: Float = 3f,
        val phase: Float = 0f
    ) : StrokeStyle
}

data class Shadow(
    val blurRadius: Float,
    val color: Int = 0x66000000,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val spread: Float = 0f,
    val shape: Shape = Shape.RoundedRect(0f)
) : ElementModifier

data class Rotate(
    val degrees: Float,
    val pivotX: Float? = null,
    val pivotY: Float? = null
) : ElementModifier

data class Padding(
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f
) : ElementModifier

data class Border(
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
    val color: Int,
    val strokeStyle: StrokeStyle = StrokeStyle.Solid,
    val shape: Shape? = null
) : ElementModifier

data class AntiAlias(
    val enabled: Boolean = true,
) : ElementModifier

data class SizeIn(
    val minWidth: Float = 0f,
    val maxWidth: Float = Float.POSITIVE_INFINITY,
    val minHeight: Float = 0f,
    val maxHeight: Float = Float.POSITIVE_INFINITY
) : ElementModifier

data class Size(
    val width: Float = Float.NaN,
    val height: Float = Float.NaN
) : ElementModifier
