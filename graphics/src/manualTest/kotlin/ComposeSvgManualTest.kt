package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.HorizontalAlignment
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.StrokeStyle
import top.e404.tavolo.draw.compose.TextModifier
import top.e404.tavolo.draw.compose.UiDsl
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.VerticalAlignment
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.font
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.row
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.sizeIn
import top.e404.tavolo.draw.compose.svg
import top.e404.tavolo.draw.compose.text

class ComposeSvgManualTest {
    private val uiFont = ManualTestSupport.uiFont
    private val titleText = TextModifier.font(fontSize = 31f, textColor = Color.WHITE, fontFamily = uiFont)
    private val bodyText = TextModifier.font(fontSize = 17f, textColor = muted, fontFamily = uiFont)
    private val cardTitleText = TextModifier.font(fontSize = 22f, textColor = ink, fontFamily = uiFont)
    private val captionText = TextModifier.font(fontSize = 14f, textColor = muted, fontFamily = uiFont)

    @Test
    fun test_compose_svg_component() {
        ManualTestSupport.saveCompose("SVG-01-标准组件渲染") {
            box(
                modifier = Modifier
                    .size(1500f, 980f)
                    .background(pageBg)
                    .padding(30f)
            ) {
                column {
                    text("SVG Compose 组件", textModifier = titleText)
                    text(
                        "覆盖标准 SVGDOM 渲染、viewBox 自然尺寸、width/height 自然尺寸、显式 size、sizeIn 等比例约束，以及 ByteArray 输入。",
                        modifier = Modifier.padding(top = 8f, bottom = 24f),
                        textModifier = TextModifier.font(18f, Color.makeRGB(201, 210, 224), uiFont)
                    )

                    row {
                        svgCard(
                            title = "viewBox 自然尺寸",
                            description = "不传 Modifier.size 时，组件应使用 viewBox 解析出的 240 x 160 尺寸。",
                            frameWidth = 330f,
                            frameHeight = 300f
                        ) {
                            svg(viewBoxSvg)
                        }
                        gap()
                        svgCard(
                            title = "显式 size 缩放",
                            description = "同一个 SVG 被压到 160 x 160，视觉上应完整缩放到方形区域。",
                            frameWidth = 330f,
                            frameHeight = 300f
                        ) {
                            svg(viewBoxSvg, Modifier.size(160f, 160f))
                        }
                        gap()
                        svgCard(
                            title = "sizeIn 保持比例",
                            description = "maxWidth / maxHeight 同时限制时，宽图应按比例缩到 260 x 130。",
                            frameWidth = 330f,
                            frameHeight = 300f
                        ) {
                            svg(wideSvg, Modifier.sizeIn(maxWidth = 260f, maxHeight = 190f))
                        }
                        gap()
                        svgCard(
                            title = "ByteArray 输入",
                            description = "验证二进制输入入口与字符串入口一致，适合外部资源读取后的渲染。",
                            frameWidth = 330f,
                            frameHeight = 300f
                        ) {
                            svg(byteArraySvg.encodeToByteArray(), Modifier.size(190f, 150f))
                        }
                    }

                    row(modifier = Modifier.padding(top = 24f)) {
                        svgCard(
                            title = "width / height 自然尺寸",
                            description = "没有 viewBox 依赖时，组件应读取根节点 width / height 作为自然尺寸。",
                            frameWidth = 450f,
                            frameHeight = 390f
                        ) {
                            svg(widthHeightSvg)
                        }
                        gap()
                        svgCard(
                            title = "defs + clipPath",
                            description = "渐变、裁剪路径和虚线描边应由 SVGDOM 完整处理，边缘不应溢出。",
                            frameWidth = 450f,
                            frameHeight = 390f
                        ) {
                            svg(clipPathSvg, Modifier.size(300f, 240f))
                        }
                        gap()
                        svgCard(
                            title = "g transform + path",
                            description = "多层 group transform、polygon、path 和透明度组合应保持相对位置。",
                            frameWidth = 450f,
                            frameHeight = 390f
                        ) {
                            svg(transformSvg, Modifier.sizeIn(maxWidth = 310f, maxHeight = 250f))
                        }
                    }
                }
            }
        }
    }

    @UiDsl
    private fun UiElement.svgCard(
        title: String,
        description: String,
        frameWidth: Float,
        frameHeight: Float,
        block: UiElement.() -> Unit
    ) {
        column(
            modifier = Modifier
                .size(frameWidth, frameHeight)
                .clip(Shape.RoundedRect(18f))
                .background(surface)
                .border(1.5f, border, shape = Shape.RoundedRect(18f))
                .padding(18f)
        ) {
            text(title, textModifier = cardTitleText)
            text(
                description,
                modifier = Modifier
                    .padding(top = 8f)
                    .sizeIn(maxWidth = frameWidth - 44f),
                textModifier = bodyText
            )
            box(
                modifier = Modifier
                    .padding(top = 18f)
                    .size(frameWidth - 44f, frameHeight - 134f)
                    .clip(Shape.RoundedRect(16f))
                    .background(Color.makeRGB(244, 247, 251))
                    .border(2f, Color.makeRGB(216, 224, 236), StrokeStyle.Dashed(listOf(12f, 8f)), Shape.RoundedRect(16f))
                    .padding(14f),
                horizontalAlignment = HorizontalAlignment.Center,
                verticalAlignment = VerticalAlignment.Center
            ) {
                block()
            }
            text(
                "浅色虚线框是宿主容器边界。",
                modifier = Modifier.padding(top = 12f),
                textModifier = captionText
            )
        }
    }

    @UiDsl
    private fun UiElement.gap(width: Float = 22f) {
        box(Modifier.size(width, 1f))
    }

    private companion object {
        val pageBg: Int = Color.makeRGB(27, 34, 46)
        val surface: Int = Color.makeRGB(255, 255, 255)
        val border: Int = Color.makeRGB(216, 224, 236)
        val ink: Int = Color.makeRGB(39, 49, 66)
        val muted: Int = Color.makeRGB(96, 111, 132)

        const val viewBoxSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 160">
                <rect x="8" y="8" width="224" height="144" rx="28" fill="#3068ff"/>
                <circle cx="78" cy="80" r="42" fill="#2bc37a"/>
                <path d="M120 120 L172 38 L218 120 Z" fill="#f5b130"/>
                <path d="M32 126 C72 98 100 98 140 126 S208 154 228 118" fill="none" stroke="#ffffff" stroke-width="10" stroke-linecap="round"/>
            </svg>
        """

        const val wideSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 160">
                <rect width="320" height="160" rx="24" fill="#212a3a"/>
                <rect x="22" y="24" width="80" height="112" rx="18" fill="#ef4c58"/>
                <rect x="120" y="24" width="80" height="112" rx="18" fill="#f5b130"/>
                <rect x="218" y="24" width="80" height="112" rx="18" fill="#2bb27b"/>
                <path d="M38 112 L72 62 L92 92" fill="none" stroke="#ffffff" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/>
                <circle cx="160" cy="80" r="28" fill="#ffffff" opacity="0.85"/>
                <path d="M238 112 L268 48 L292 112 Z" fill="#ffffff" opacity="0.9"/>
            </svg>
        """

        const val byteArraySvg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 190 150">
                <rect x="6" y="6" width="178" height="138" rx="24" fill="#fff7df" stroke="#f5b130" stroke-width="10"/>
                <circle cx="70" cy="72" r="34" fill="#3068ff"/>
                <circle cx="118" cy="72" r="34" fill="#ef4c58" opacity="0.82"/>
                <path d="M52 112 H138" stroke="#2bb27b" stroke-width="12" stroke-linecap="round"/>
            </svg>
        """

        const val widthHeightSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="260" height="180">
                <rect x="0" y="0" width="260" height="180" rx="22" fill="#f8fafc"/>
                <path d="M34 128 L78 58 L122 128 Z" fill="#3068ff"/>
                <path d="M104 128 L148 58 L192 128 Z" fill="#2bb27b"/>
                <path d="M174 128 L218 58 L244 128 Z" fill="#ef4c58"/>
                <rect x="30" y="136" width="202" height="12" rx="6" fill="#1f2937"/>
            </svg>
        """

        const val clipPathSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 240">
                <defs>
                    <linearGradient id="sky" x1="0" y1="0" x2="1" y2="1">
                        <stop offset="0" stop-color="#3068ff"/>
                        <stop offset="0.55" stop-color="#2bb27b"/>
                        <stop offset="1" stop-color="#f5b130"/>
                    </linearGradient>
                    <clipPath id="badgeClip">
                        <path d="M150 18 C214 18 266 70 266 134 C266 198 214 222 150 222 C86 222 34 198 34 134 C34 70 86 18 150 18 Z"/>
                    </clipPath>
                </defs>
                <g clip-path="url(#badgeClip)">
                    <rect width="300" height="240" fill="url(#sky)"/>
                    <circle cx="76" cy="78" r="58" fill="#ffffff" opacity="0.28"/>
                    <circle cx="228" cy="178" r="78" fill="#ffffff" opacity="0.22"/>
                    <path d="M20 176 C78 118 122 118 168 176 S246 230 292 170" fill="none" stroke="#ffffff" stroke-width="18" stroke-linecap="round"/>
                </g>
                <path d="M150 18 C214 18 266 70 266 134 C266 198 214 222 150 222 C86 222 34 198 34 134 C34 70 86 18 150 18 Z" fill="none" stroke="#1f2937" stroke-width="8" stroke-dasharray="18 10"/>
            </svg>
        """

        const val transformSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 240">
                <rect x="12" y="12" width="296" height="216" rx="24" fill="#202938"/>
                <g transform="translate(78 118) rotate(-12)">
                    <rect x="-48" y="-48" width="96" height="96" rx="18" fill="#3068ff"/>
                    <path d="M-26 22 L0 -24 L28 22 Z" fill="#ffffff" opacity="0.9"/>
                </g>
                <g transform="translate(164 110) scale(1.16)">
                    <polygon points="0,-52 46,-18 28,42 -28,42 -46,-18" fill="#2bb27b"/>
                    <circle cx="0" cy="0" r="22" fill="#ffffff" opacity="0.82"/>
                </g>
                <g transform="translate(242 124) rotate(14)">
                    <path d="M0 -54 C34 -54 58 -28 58 2 C58 36 26 58 0 58 C-26 58 -58 36 -58 2 C-58 -28 -34 -54 0 -54 Z" fill="#ef4c58"/>
                    <path d="M-24 12 C-8 -8 8 -8 24 12" fill="none" stroke="#ffffff" stroke-width="10" stroke-linecap="round"/>
                </g>
                <path d="M52 196 H268" stroke="#f5b130" stroke-width="12" stroke-linecap="round" opacity="0.9"/>
            </svg>
        """
    }
}
