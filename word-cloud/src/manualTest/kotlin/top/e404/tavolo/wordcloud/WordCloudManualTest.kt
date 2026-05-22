package top.e404.tavolo.wordcloud

import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import top.e404.tavolo.util.FontManager
import java.io.File
import kotlin.test.Test

class WordCloudManualTest {
    private val fontFamily = FontManager.registerSystem("word-cloud-manual-ui", "Microsoft YaHei")

    @Test
    fun renderWordCloudExamples() {
        val outputDir = File("out/manual/word-cloud").also { it.mkdirs() }
        val entries = entries()
        val numberTemplate = numberTemplateImage("8")

        save(
            outputDir.resolve("word-cloud-rectangle.png"),
            TavoloWordCloud.render(
                entries,
                options(width = 2200, height = 1400, min = 22f, max = 280f, maxWords = 900)
            )
        )
        save(
            outputDir.resolve("word-cloud-comparison-8.png"),
            renderNumberComparison(numberTemplate, entries)
        )
    }

    private fun options(
        width: Int,
        height: Int,
        min: Float,
        max: Float,
        maxWords: Int = 900,
        maxPlacementAttempts: Int = 24000,
    ) =
        WordCloudOptions(
            width = width,
            height = height,
            fontFamily = fontFamily,
            minFontSize = min,
            maxFontSize = max,
            padding = 0,
            maxWords = maxWords,
            spiralStep = 1.35f,
            angleStep = 0.23f,
            maxPlacementAttempts = maxPlacementAttempts,
            randomSeed = 20260523L,
            backgroundColor = Color.WHITE,
        )

    private fun renderNumberComparison(template: Image, entries: List<WordCloudEntry>): Image {
        val maxSide = 1800
        val otsuMask = WordCloudMaskFactory.fromOtsu(template, OtsuMaskOptions(maxSide = maxSide))
        val edgeMask = WordCloudMaskFactory.fromEdges(template, EdgeMaskOptions(maxSide = maxSide, threshold = 34, dilateRadius = 3))
        val filledEdgeMask = WordCloudMaskFactory.fromEdges(
            template,
            EdgeMaskOptions(maxSide = maxSide, threshold = 34, dilateRadius = 3, fillBetweenEdges = true)
        )
        val otsuCloud = TavoloWordCloud.render(
            entries,
            otsuMask,
            options(width = otsuMask.width, height = otsuMask.height, min = 14f, max = 220f, maxWords = 1800)
        )
        val edgeCloud = TavoloWordCloud.render(
            entries,
            edgeMask,
            options(width = edgeMask.width, height = edgeMask.height, min = 7f, max = 28f, maxWords = 300, maxPlacementAttempts = 4200)
        )
        val filledEdgeCloud = TavoloWordCloud.render(
            entries,
            filledEdgeMask,
            options(width = filledEdgeMask.width, height = filledEdgeMask.height, min = 14f, max = 220f, maxWords = 1800)
        )
        return comparisonImage(
            listOf(
                ComparisonRow("原图", template, "原图基准", template),
                ComparisonRow("OTSU 蒙版", otsuMask.toDebugImage(), "OTSU 词云", otsuCloud),
                ComparisonRow("纯边缘蒙版", edgeMask.toDebugImage(), "纯边缘词云", edgeCloud),
                ComparisonRow("边缘填充蒙版", filledEdgeMask.toDebugImage(), "边缘填充词云", filledEdgeCloud),
            )
        )
    }

    private fun numberTemplateImage(text: String, width: Int = 2600, height: Int = 2600): Image =
        Surface.makeRasterN32Premul(width, height).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.WHITE)
            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.BLACK
            }
            val font = Font(FontManager.resolve(fontFamily), 2320f).apply {
                isEmboldened = true
            }
            val metrics = font.metrics
            val textWidth = font.measureTextWidth(text, paint)
            val x = (width - textWidth) / 2f
            val y = height / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.drawString(text, x, y, font, paint)
            surface.makeImageSnapshot()
        }

    private fun entries(): List<WordCloudEntry> {
        val baseWords = listOf(
            "Tavolo", "Skiko", "词云", "模板", "渲染", "二值图", "边缘检测", "OTSU", "蒙版", "布局",
            "碰撞检测", "字体", "PNG", "可选模块", "人工测试", "像素", "颜色", "词频", "数字模板", "中文",
            "模块化", "下游接入", "稳定输出", "服务端", "连通域", "边缘填充", "权重映射", "字号缩放", "随机种子", "调试图",
            "灰度", "阈值", "轮廓层级", "闭合边缘", "模板缩放", "图片编码", "透明像素", "画布", "字体度量", "中心布局",
            "螺旋搜索", "矩形占用", "采样检查", "色板", "深色区域", "浅色背景", "边缘膨胀", "最大词数", "中文排版", "可读性",
            "数字", "固定尺寸", "压力测试", "密度", "层级填充", "边界", "内洞", "外部区域", "泛洪", "连通关系",
            "权重跨度", "高频词", "中频词", "低频词", "词条过滤", "空文本", "非正权重", "渲染入口", "PNG字节", "本地API",
            "Compose", "图表", "指令", "资源", "平台无关", "离线渲染", "服务集成", "HTTP", "测试报告", "回归",
            "字体族", "微软雅黑", "抗锯齿", "画质", "边缘线", "闭运算", "轮廓追踪", "形态学", "性能", "缓存",
            "样例", "对比图", "原图", "蒙版图", "词云图", "黑白模板", "数字渲染", "固定画布", "自动阈值", "Sobel",
            "质量检查", "人工确认", "输出目录", "构建模块", "发布坐标", "依赖隔离", "Skia", "Kotlin", "Gradle",
            "测试用例", "像素占比", "尺寸断言", "异常提示", "清晰度", "布局失败", "尝试次数", "权重排序", "截断", "渲染完成",
        )
        val denseWords = listOf(
            "图", "字", "词", "云", "边", "线", "环", "洞", "层", "域", "点", "块", "色", "光", "深", "浅",
            "大词", "小词", "高频", "中频", "低频", "密集", "填充", "轮廓", "闭合", "阈值", "灰度", "采样",
            "外圈", "内圈", "中心", "边界", "间隙", "画布", "字体", "字号", "权重", "可读", "清晰", "稳定",
            "mask", "edge", "fill", "otsu", "sobel", "png", "api", "test", "skia", "kmp", "jvm", "ui",
        )
        val headlineWords = listOf("词云", "密度", "边缘", "模板", "服务端", "Skiko", "Tavolo", "边缘填充")
        val expanded = buildList {
            addAll(headlineWords)
            addAll(denseWords)
            repeat(46) { round ->
                for (word in denseWords) add("$word${round + 1}")
            }
            addAll(baseWords.filterNot { it in headlineWords })
            repeat(8) { round ->
                for (word in baseWords) add("$word${round + 1}")
            }
        }
        return expanded.mapIndexed { index, word ->
            val weight = when {
                index < 8 -> 2600 - index * 210
                index < 60 -> 520 - (index - 8) * 4
                index < 260 -> 150 - (index - 60) / 4
                index < 700 -> 76 - (index - 260) / 10
                else -> 32 - (index - 700) / 40
            }.coerceAtLeast(3)
            WordCloudEntry(word, weight)
        }
    }

    private data class ComparisonRow(
        val leftTitle: String,
        val leftImage: Image,
        val rightTitle: String,
        val rightImage: Image,
    )

    private fun comparisonImage(rows: List<ComparisonRow>): Image {
        val columns = 2
        val cellWidth = 1500
        val cellHeight = 1320
        val labelHeight = 64
        val padding = 32
        val width = columns * cellWidth + (columns + 1) * padding
        val height = rows.size * (cellHeight + labelHeight) + (rows.size + 1) * padding
        return Surface.makeRasterN32Premul(width, height).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.WHITE)
            val labelFont = Font(FontManager.resolve(fontFamily), 42f)
            val labelPaint = Paint().apply {
                color = Color.makeRGB(35, 44, 58)
                isAntiAlias = true
            }
            val borderPaint = Paint().apply {
                color = Color.makeRGB(213, 219, 228)
                mode = PaintMode.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }

            rows.forEachIndexed { rowIndex, row ->
                drawComparisonCell(canvas, row.leftTitle, row.leftImage, 0, rowIndex, cellWidth, cellHeight, labelHeight, padding, labelFont, labelPaint, borderPaint)
                drawComparisonCell(canvas, row.rightTitle, row.rightImage, 1, rowIndex, cellWidth, cellHeight, labelHeight, padding, labelFont, labelPaint, borderPaint)
            }
            surface.makeImageSnapshot()
        }
    }

    private fun drawComparisonCell(
        canvas: org.jetbrains.skia.Canvas,
        title: String,
        image: Image,
        col: Int,
        row: Int,
        cellWidth: Int,
        cellHeight: Int,
        labelHeight: Int,
        padding: Int,
        labelFont: Font,
        labelPaint: Paint,
        borderPaint: Paint,
    ) {
        val x = padding + col * (cellWidth + padding)
        val y = padding + row * (cellHeight + labelHeight + padding)
        canvas.drawString(title, x.toFloat(), y + 40f, labelFont, labelPaint)
        val imageRect = fitRect(
            image.width,
            image.height,
            Rect.makeXYWH(x.toFloat(), (y + labelHeight).toFloat(), cellWidth.toFloat(), cellHeight.toFloat())
        )
        canvas.drawRect(Rect.makeXYWH(x.toFloat(), (y + labelHeight).toFloat(), cellWidth.toFloat(), cellHeight.toFloat()), borderPaint)
        canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            imageRect,
            FilterMipmap(FilterMode.LINEAR, MipmapMode.NEAREST),
            null,
            false,
        )
    }

    private fun fitRect(imageWidth: Int, imageHeight: Int, bounds: Rect): Rect {
        val scale = minOf(bounds.width / imageWidth, bounds.height / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        val left = bounds.left + (bounds.width - width) / 2f
        val top = bounds.top + (bounds.height - height) / 2f
        return Rect.makeXYWH(left, top, width, height)
    }

    private fun save(file: File, image: Image) {
        file.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "图片编码失败" }.bytes)
        println("已输出: ${file.absolutePath}")
    }
}
