package top.e404.skiko.draw.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.*
import org.junit.Test
import top.e404.skiko.draw.compose.charts.BarTheme
import top.e404.skiko.draw.compose.charts.bar
import top.e404.skiko.draw.compose.*
import top.e404.skiko.draw.compose.charts.RadarTheme
import top.e404.skiko.draw.compose.charts.radar
import top.e404.skiko.util.Colors
import top.e404.skiko.util.hsb
import java.io.File
import java.net.URL

class TestRender {

    fun toImage(url: String) = Image.makeFromEncoded(URL(url).readBytes())

    val image1 = toImage("https://i1.hdslb.com/bfs/face/c1733474892caa45952b2c09a89323157df7129a.jpg@64w_64h.jpg")
    val image2 = toImage("https://i1.hdslb.com/bfs/face/16377ca32f0b4b801bc760862893d8cb986facf3.jpg@64w_64h.jpg")
    val images = listOf(
        "https://i1.hdslb.com/bfs/face/e36645536f50dd57382dc625d35dad029af199a8.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/70c8b553e79e42c2ca93084ffbfce20af7a5ac9a.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/fa29937ea0ded3aabcf829f02297f2f2afbc6c46.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/a324cc9783ef6aba742a5c6dbf055eda8372a30f.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/cf77e0afb1bb23b6ab3cbfe52aff3c269cff9a35.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/8e99f38a57020d77cc9e3f6f369104e85583bebb.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/f119348814f30c6bbbcc60bd63c12b8215d19d2f.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/201834c47130b96b2dc207e6042ff4765291d702.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/dcb24f8f5dd29ab819f6e0450663b0d0134e6e1b.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/f864ffb0264cfac8a1ad1364a337006763f15958.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/e9035c3a7089ce7e11d72cb0cf15fa92064b14ef.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/c133da90bbc40d332126353107085f81ba593a11.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/881134664f54e95cab471a314273627a7529a4f8.jpg@64w_64h.jpg",
        "https://i1.hdslb.com/bfs/face/66ff1b32a6b480279e0c4812f820d247305ac05c.jpg@64w_64h.jpg",
    ).map { toImage(it) }
    val names = listOf("Base64", "Sha256", "MD5", "URL Encode", "URL Decode", "HTML Encode", "HTML Decode", "Unicode Encode", "Unicode Decode")
    val cards = (1..20).map {
        val name = names.random()
        Triple(images.random(), name, (0..10).joinToString(" ") { name })
    }

    init {
        DefaultTypefaceProvider.default = FontMgr.default.makeFromData(Data.makeFromBytes(File("font/LXGWWenKai-Regular.ttf").readBytes()))!!
    }
    @UiDsl
    fun UiElement.profileCard(index: Int, name: String, desc: String, image: Image, modifier: Modifier = Modifier) {
        row(
            verticalAlignment = VerticalAlignment.Center,
            modifier = modifier
                // .border(all = 1f, color = Color.makeRGB(220, 220, 220))
                // .background(Color.WHITE)
                .padding(all = 12f)
        ) {
            // 序号
            box(Modifier.size(60f).margin(right = 10f),
                horizontalAlignment = HorizontalAlignment.Center,
                verticalAlignment = VerticalAlignment.Center
            ) {
                box(Modifier.size(60f),
                    horizontalAlignment = HorizontalAlignment.Center,
                    verticalAlignment = VerticalAlignment.Center
                ) {
                    text(
                        "$index",
                        Modifier
                            .fontSize(30F)
                    )
                }
                box(Modifier.size(60f),
                    horizontalAlignment = HorizontalAlignment.Left,
                    verticalAlignment = VerticalAlignment.Center
                ) {
                    text(
                        "[",
                        Modifier
                            .fontSize(30F)
                    )
                }
                box(Modifier.size(60f),
                    horizontalAlignment = HorizontalAlignment.Right,
                    verticalAlignment = VerticalAlignment.Center
                ) {
                    text(
                        "]",
                        Modifier
                            .fontSize(30F)
                    )
                }
            }
            image(
                image = image,
                modifier = Modifier
                    .clip(Shape.Circle) // 使用圆形裁剪
                    .margin(right = 12f)
            )
            column {
                text(
                    name, Modifier
                        .maxSize(maxWidth = 500f, maxHeight = 40f)
                        .fontSize(28f)
                        .textColor(Color.WHITE)
                )
                text(
                    desc, Modifier
                        .maxSize(maxWidth = 500f, maxHeight = 40f)
                        .fontSize(20f)
                        .textColor(Color.WHITE)
                )
            }
        }
    }

    @UiDsl
    fun UiElement.cards() {
        column(
            horizontalAlignment = HorizontalAlignment.Left,
            modifier = Modifier.padding(20f)
                .background(Colors.BG.argb)
        ) {
            cards.forEachIndexed { index, (image, name, desc) ->
                profileCard(index, name, desc, image)
            }
        }
    }

    fun testCompose(name: String, content: Composable) {
        val root = Column()
        render(Color.TRANSPARENT, root, content).let {
            val bytes = it.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("out/compose/$name.png").also { it.parentFile.mkdirs() }.writeBytes(bytes)
        }
        buildString {
            debugBaseElement(0, root, this)
        }.let {
            println(it)
        }
    }

    @Test
    fun testAll() {
        runBlocking(Dispatchers.IO) {
            listOf(
                ::test_align,
                ::test_compose,
                ::test_padding,
                ::test_clip,
                ::test_overflow,
                ::test_box_alignment,
                ::test_table_layout,
                ::test_table_cell_modifiers
            ).map {
                async {
                    it.invoke()
                }
            }.awaitAll()
        }
    }

    @Test
    fun test_align() = testCompose("align") {
        column(
            modifier = Modifier
                .padding(10f)
                .background(Colors.BG.argb)
        ) {
            // --- 演示 Column 的 horizontalAlignment ---
            text("Column 水平对齐演示", modifier = Modifier.fontSize(28f).margin(10f))

            // 居左对齐 (Start)
            column(
                horizontalAlignment = HorizontalAlignment.Left,
                modifier = Modifier.border(2f, Color.makeRGB(0, 150, 136)).padding(8f).margin(10f)
            ) {
                text("左对齐(Start)")
                text("短文本")
                text("这是一个非常非常长的文本")
            }

            // 居中对齐 (Center)
            column(
                horizontalAlignment = HorizontalAlignment.Center,
                modifier = Modifier.border(2f, Color.makeRGB(255, 87, 34)).padding(8f).margin(10f)
            ) {
                text("居中对齐(Center)")
                text("短文本")
                text("这是一个非常非常长的文本")
            }

            // 居右对齐 (End)
            column(
                horizontalAlignment = HorizontalAlignment.Right,
                modifier = Modifier.border(2f, Color.makeRGB(33, 150, 243)).padding(8f).margin(10f)
            ) {
                text("右对齐(End)")
                text("短文本")
                text("这是一个非常非常长的文本")
            }

            // --- 演示 Row 的 verticalAlignment ---
            text("Row 垂直对齐演示", modifier = Modifier.fontSize(28f).margin(10f))

            row(
                verticalAlignment = VerticalAlignment.Top,
                modifier = Modifier.border(2f, Color.makeRGB(156, 39, 176)).padding(8f).margin(10f)
            ) {
                text("顶对齐(Top)", modifier = Modifier.background(Color.makeARGB(50, 0, 0, 255)))
                image(image1)
                box(
                    modifier = Modifier
                        .padding(50F, 80f)
                        .background(Color.makeARGB(128, 255, 193, 7))
                        .clip(Shape.RoundedRect(15f))
                )
            }

            row(
                verticalAlignment = VerticalAlignment.Center,
                modifier = Modifier.border(2f, Color.makeRGB(233, 30, 99)).padding(8f).margin(10f)
            ) {
                text("中对齐(Center)", modifier = Modifier.background(Color.makeARGB(50, 0, 0, 255)))
                image(image1)
                box(
                    modifier = Modifier
                        .padding(50F, 80f)
                        .background(Color.makeARGB(128, 255, 193, 7))
                        .clip(Shape.RoundedRect(15f))
                )
            }

            row(
                verticalAlignment = VerticalAlignment.Bottom,
                modifier = Modifier.border(2f, Color.makeRGB(76, 175, 80)).padding(8f).margin(10f)
            ) {
                text("底对齐(Bottom)", modifier = Modifier.background(Color.makeARGB(50, 0, 0, 255)))
                image(image1)
                box(
                    modifier = Modifier
                        .padding(50F, 80f)
                        .background(Color.makeARGB(128, 255, 193, 7))
                        .clip(Shape.RoundedRect(15f))
                )
            }
        }
    }

    @Test
    fun test_compose() = testCompose("compose") {
        column(
            horizontalAlignment = HorizontalAlignment.Center,
            modifier = Modifier.padding(20f)
                .background(Colors.BG.argb)
        ) {
            // --- 演示封装的自定义组件 ---
            text(
                "自定义组件演示", modifier = Modifier
                    .fontSize(28f)
                    .margin(bottom = 16f)
            )

            cards()

            text(
                "按钮", modifier = Modifier
                    .background(Color.BLUE)
                    .padding(150F, 20F)
                    .clip(Shape.RoundedRect(15f))
                    .fontSize(28f)
                    .textColor(Color.WHITE)
                    .margin(50F)
            )
        }
    }

    @Test
    fun test_padding() = testCompose("padding") {
        column(
            horizontalAlignment = HorizontalAlignment.Left,
            modifier = Modifier.padding(20f).background(Colors.BG.argb)
        ) {
            // --- 演示分边距 Padding 和 Border ---
            text("分边距与裁剪演示", modifier = Modifier.fontSize(28f).margin(top = 30f, bottom = 16f))
            box(
                modifier = Modifier
                    .background(Color.makeRGB(207, 226, 255))
                    .border(top = 4f, bottom = 12f, color = Color.makeRGB(66, 133, 244))
                    .padding(left = 20f, right = 40f, top = 10f, bottom = 10f)
            ) {
                text("我有不同的边框和内边距")
            }
        }
    }

    @Test
    fun test_clip() = testCompose("clip") {
        column(
            horizontalAlignment = HorizontalAlignment.Left,
            modifier = Modifier
                .padding(20f)
                .background(Colors.BG.argb)
        ) {
            // --- 演示 Box 布局和圆角裁剪 ---
            box(
                modifier = Modifier
                    .margin(top = 20f)
                    .clip(Shape.RoundedRect(20f))
            ) {
                // Box 内的子元素会堆叠
                image(image1)
                box(
                    modifier = Modifier
                        .background(Color.makeARGB(128, 0, 0, 0))
                        .padding(12f)
                ) {
                    text("圆角裁剪 + 内容覆盖", modifier = Modifier.textColor(Color.WHITE))
                }
            }
        }
    }

    @Test
    fun test_overflow() = testCompose("overflow") {
        column(
            modifier = Modifier
                .padding(15f)
                .background(Colors.BG.argb)
        ) {
            val longText = "这是一段非常非常非常长的文本，它需要足够的空间来展示，否则就会触发溢出策略。"
            val imageForOverflow = image2 // 使用一个已加载的图片

            // --- 文本溢出 ---
            text("文本溢出策略", modifier = Modifier.fontSize(28f).margin(bottom = 10f))

            // 1. 自动换行 (Wrap)
            text("1. Wrap: 自动换行", modifier = Modifier.fontSize(20f).margin(bottom = 5f))
            text(
                longText,
                modifier = Modifier
                    .maxSize(maxWidth = 300f) // 限制最大宽度
                    .textOverflow(TextOverflow.Wrap)
                    .border(all = 1f, color = Colors.LIGHT_BLUE.argb)
                    .padding(5f)
                    .margin(bottom = 15f)
            )

            // 2. 省略号截断 (Ellipsis)
            text("2. Ellipsis: 省略号截断", modifier = Modifier.fontSize(20f).margin(bottom = 5f))
            text(
                longText,
                modifier = Modifier
                    .fontSize(30f)
                    .maxSize(maxWidth = 300f) // 限制最大宽度
                    .textOverflow(TextOverflow.Ellipsis)
                    .border(all = 1f, color = Colors.ORANGE.argb)
                    .padding(5f)
                    .margin(bottom = 15f)
            )

            // 3. 换行 + 高度限制 (导致行数截断)
            text("3. Wrap + MaxHeight: 限制行数", modifier = Modifier.fontSize(20f).margin(bottom = 5f))
            text(
                longText + longText, // 使用更长的文本
                modifier = Modifier
                    .fontSize(30f)
                    .maxSize(maxWidth = 300f, maxHeight = 70f) // 同时限制宽度和高度
                    .textOverflow(TextOverflow.Wrap) // 策略仍是换行，但会被高度截断
                    .border(all = 1f, color = Colors.PURPLE.argb)
                    .padding(5f)
                    .margin(bottom = 25f)
            )

            // --- 图片溢出 ---
            text("图片溢出策略 (原图 64x64)", modifier = Modifier.fontSize(28f).margin(bottom = 10f))
            row(verticalAlignment = VerticalAlignment.Center) {
                // 1. 按比例缩放 (Scale)
                column(horizontalAlignment = HorizontalAlignment.Center, modifier = Modifier.margin(right = 20f)) {
                    text("1. Scale to 40x40", modifier = Modifier.margin(bottom = 5f))
                    image(
                        image = imageForOverflow,
                        modifier = Modifier
                            .maxSize(maxWidth = 40f, maxHeight = 40f)
                            .imageOverflow(ImageOverflow.Scale)
                            .border(all = 1f, color = Colors.GREEN.argb)
                    )
                }

                // 2. 居中裁剪 (Crop)
                column(horizontalAlignment = HorizontalAlignment.Center) {
                    text("2. Crop to 40x40", modifier = Modifier.margin(bottom = 5f))
                    image(
                        image = imageForOverflow,
                        modifier = Modifier
                            .maxSize(maxWidth = 40f, maxHeight = 40f)
                            .imageOverflow(ImageOverflow.Crop)
                            .border(all = 1f, color = Colors.RED.argb)
                    )
                }
            }
        }
    }

    @Test
    fun test_box_alignment() = testCompose("box_alignment") {
        column(modifier = Modifier.padding(10f).background(Colors.BG.argb)) {
            text("Box 对齐演示 (大框 100x100, 小框 30x30)", modifier = Modifier.fontSize(20f).margin(bottom = 10f))

            // Top Row
            row(modifier = Modifier.margin(bottom = 10f)) {
                // Top-Left
                box(
                    modifier = Modifier.size(100f).border(1f, Colors.GRAY.argb).margin(right = 10f),
                    horizontalAlignment = HorizontalAlignment.Left,
                    verticalAlignment = VerticalAlignment.Top
                ) { box(modifier = Modifier.size(30f).background(Colors.LIGHT_BLUE.argb)) }

                // Top-Center
                box(
                    modifier = Modifier.size(100f).border(1f, Colors.GRAY.argb).margin(right = 10f),
                    horizontalAlignment = HorizontalAlignment.Center,
                    verticalAlignment = VerticalAlignment.Top
                ) { box(modifier = Modifier.size(30f).background(Colors.LIGHT_BLUE.argb)) }

                // Top-Right
                box(
                    modifier = Modifier.size(100f).border(1f, Colors.GRAY.argb),
                    horizontalAlignment = HorizontalAlignment.Right,
                    verticalAlignment = VerticalAlignment.Top
                ) { box(modifier = Modifier.size(30f).background(Colors.LIGHT_BLUE.argb)) }
            }

            // Center Row
            row(modifier = Modifier.margin(bottom = 10f)) {
                // Center-Left
                box(
                    modifier = Modifier.size(100f).border(1f, Colors.GRAY.argb).margin(right = 10f),
                    horizontalAlignment = HorizontalAlignment.Left,
                    verticalAlignment = VerticalAlignment.Center
                ) { box(modifier = Modifier.size(30f).background(Colors.ORANGE.argb)) }

                // Center-Center
                box(
                    modifier = Modifier.size(100f).border(1f, Colors.GRAY.argb).margin(right = 10f),
                    horizontalAlignment = HorizontalAlignment.Center,
                    verticalAlignment = VerticalAlignment.Center
                ) { box(modifier = Modifier.size(30f).background(Colors.ORANGE.argb)) }

                // Center-Right
                box(
                    modifier = Modifier.size(100f).border(1f, Colors.GRAY.argb),
                    horizontalAlignment = HorizontalAlignment.Right,
                    verticalAlignment = VerticalAlignment.Center
                ) { box(modifier = Modifier.size(30f).background(Colors.ORANGE.argb)) }
            }

            // Bottom Row
            row {
                // Bottom-Left
                box(
                    modifier = Modifier.size(100f).border(1f, Colors.GRAY.argb).margin(right = 10f),
                    horizontalAlignment = HorizontalAlignment.Left,
                    verticalAlignment = VerticalAlignment.Bottom
                ) { box(modifier = Modifier.size(30f).background(Colors.PURPLE.argb)) }

                // Bottom-Center
                box(
                    modifier = Modifier.size(100f).border(1f, Colors.GRAY.argb).margin(right = 10f),
                    horizontalAlignment = HorizontalAlignment.Center,
                    verticalAlignment = VerticalAlignment.Bottom
                ) { box(modifier = Modifier.size(30f).background(Colors.PURPLE.argb)) }

                // Bottom-Right
                box(
                    modifier = Modifier.size(100f).border(1f, Colors.GRAY.argb),
                    horizontalAlignment = HorizontalAlignment.Right,
                    verticalAlignment = VerticalAlignment.Bottom
                ) { box(modifier = Modifier.size(30f).background(Colors.PURPLE.argb)) }
            }
        }
    }

    @Test
    fun test_table_layout() = testCompose("table_layout") {
        column(modifier = Modifier.padding(15f).background(Colors.BG.argb)) {
            text("自动列宽表格演示", modifier = Modifier.fontSize(28f).margin(bottom = 15f))

            table(
                modifier = Modifier.border(1f, Colors.GRAY.argb),
                columnSpacing = 10f,
                rowSpacing = 10f
            ) {
                // 表头
                tableRow {
                    val cellModifier = Modifier
                        .background(Colors.GRAY.argb)
                        .padding(8f)
                        .border(1f, Colors.YELLOW_GREEN.argb)
                    cell(modifier = cellModifier, verticalAlignment = VerticalAlignment.Center) {
                        text("ID", modifier = Modifier.fontSize(20f))
                    }
                    cell(modifier = cellModifier, verticalAlignment = VerticalAlignment.Center) {
                        text("用户名", modifier = Modifier.fontSize(20f))
                    }
                    cell(modifier = cellModifier, verticalAlignment = VerticalAlignment.Center) {
                        text("备注", modifier = Modifier.fontSize(20f))
                    }
                }

                // 数据行
                val users = listOf(
                    Triple(1, "Alice", "这是一个比较长的备注，需要自动换行来正确显示。"),
                    Triple(2, "Bob", "短备注。"),
                    Triple(3, "Charlie a very long name", "这条备注也不短。"),
                    Triple(4, "David", "正常。")
                )

                for ((id, name, note) in users) {
                    tableRow {
                        val cellModifier = Modifier
                            .padding(8f)
                            .border(1f, Colors.YELLOW_GREEN.argb)
                        cell(modifier = cellModifier, verticalAlignment = VerticalAlignment.Center) {
                            text(id.toString())
                        }
                        cell(modifier = cellModifier, verticalAlignment = VerticalAlignment.Center) {
                            text(name)
                        }
                        cell(modifier = cellModifier, verticalAlignment = VerticalAlignment.Center) {
                            // Text 默认是换行模式，所以当列宽确定后会自动换行
                            text(note)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun test_table_cell_modifiers() = testCompose("table_cell_modifiers") {
        @UiDsl
        fun TableRow.rowHead() = cell(modifier = Modifier.background(Colors.LIGHT_GRAY.argb)) {
            box(Modifier.size(70f))
        }
        column(modifier = Modifier.padding(15f).background(Colors.BG.argb)) {
            text("单元格修饰与对齐演示", modifier = Modifier.fontSize(28f).margin(bottom = 15f))

            table(columnSpacing = 10f, rowSpacing = 10f) {
                tableRow {
                    rowHead()
                    // 这个单元格的背景会填满整个区域
                    cell(
                        modifier = Modifier.background(Colors.LIGHT_GRAY.argb).padding(18f),
                        verticalAlignment = VerticalAlignment.Center
                    ) {
                        text("ID")
                    }
                    // 这个单元格的内容会水平居中
                    cell(
                        modifier = Modifier.background(Colors.LIGHT_GRAY.argb).padding(18f),
                        horizontalAlignment = HorizontalAlignment.Center
                    ) {
                        text("分数")
                    }
                    cell(
                        modifier = Modifier.background(Colors.LIGHT_GRAY.argb).padding(18f)
                    ) {
                        text("优")
                    }
                }
                tableRow {
                    rowHead()
                    cell(
                        modifier = Modifier.background(Colors.PINK.argb),
                        horizontalAlignment = HorizontalAlignment.Left,
                        verticalAlignment = VerticalAlignment.Top,
                    ) {
                        text("1")
                    }
                    cell(
                        modifier = Modifier.background(Colors.LIGHT_BLUE.argb),
                        horizontalAlignment = HorizontalAlignment.Center,
                        verticalAlignment = VerticalAlignment.Top,
                    ) {
                        text("95")
                    }
                    // 这个单元格的内容会垂直居中
                    cell(
                        modifier = Modifier.background(Colors.GREEN.argb),
                        horizontalAlignment = HorizontalAlignment.Right,
                        verticalAlignment = VerticalAlignment.Top
                    ) {
                        text("正常")
                    }
                }
                tableRow {
                    rowHead()
                    cell(
                        modifier = Modifier.background(Colors.PINK.argb),
                        horizontalAlignment = HorizontalAlignment.Left,
                        verticalAlignment = VerticalAlignment.Center,
                    ) {
                        text("2")
                    }
                    cell(
                        modifier = Modifier.background(Colors.LIGHT_BLUE.argb),
                        horizontalAlignment = HorizontalAlignment.Center,
                        verticalAlignment = VerticalAlignment.Center,
                    ) {
                        text("75")
                    }
                    // 这个单元格的内容会垂直居中
                    cell(
                        modifier = Modifier.background(Colors.GREEN.argb),
                        horizontalAlignment = HorizontalAlignment.Right,
                        verticalAlignment = VerticalAlignment.Center
                    ) {
                        text("良")
                    }
                }
                tableRow {
                    rowHead()
                    cell(
                        modifier = Modifier.background(Colors.PINK.argb),
                        horizontalAlignment = HorizontalAlignment.Left,
                        verticalAlignment = VerticalAlignment.Bottom,
                    ) {
                        text("3")
                    }
                    cell(
                        modifier = Modifier.background(Colors.LIGHT_BLUE.argb),
                        horizontalAlignment = HorizontalAlignment.Center,
                        verticalAlignment = VerticalAlignment.Bottom,
                    ) {
                        text("65")
                    }
                    // 这个单元格的内容会垂直居中
                    cell(
                        modifier = Modifier.background(Colors.GREEN.argb),
                        horizontalAlignment = HorizontalAlignment.Right,
                        verticalAlignment = VerticalAlignment.Bottom
                    ) {
                        text("及格")
                    }
                }
            }
        }
    }

    @Test
    fun test_icon_text() = testCompose("icon_text") {
        column(
            modifier = Modifier
                .padding(10f)
                .background(Colors.BG.argb),
        ) {
            // --- 演示 Column 的 horizontalAlignment ---
            text("iconText 演示", modifier = Modifier.fontSize(28f).margin(10f))
            iconText("带icon的text", modifier = Modifier.fontSize(28f).margin(10f))
        }
    }

    @Test
    fun test_size_overflow() = testCompose("size_overflow") {
        column(
            modifier = Modifier
                .padding(10f)
                .background(Colors.BG.argb)
                .padding(25f)
                .clip(Shape.RoundedRect(4.5f))
                .border(.5f, Colors.LIGHT_BLUE.argb)
        ) {
            box(Modifier.size(500.9f))
        }
    }

    @Test
    fun test_bar() = testCompose("bar") {
        val theme = BarTheme(outerRadius = 200f, strokeWidth = 5f)
        val fontSize = 25f
        val iconSize = 20f
        val colors = listOf(
            Colors.RED.argb,
            Colors.ORANGE.argb,
            Colors.YELLOW.argb,
            Colors.GREEN.argb,
            Colors.CYAN.argb,
            Colors.BLUE.argb,
            Colors.PURPLE.argb,
        ).map {
            val (h, s, b) = it.hsb()
            hsb(h, s - .3f, b - .5f)
        }

        @UiDsl
        fun UiElement.barWithLabels(theme: BarTheme, items: List<Pair<Int, Float>>) {
            row(Modifier.margin(10f), VerticalAlignment.Center) {
                bar(theme, items)
                column(Modifier.margin(left = 20f)) {
                    for ((color, value) in items) {
                        row(Modifier.margin(10f), VerticalAlignment.Center) {
                            box(Modifier
                                .size(iconSize)
                                .background(color)
                                .clip(Shape.RoundedRect(50f))
                            )
                            text("${color.toHexString()} - $value", Modifier
                                .fontSize(fontSize)
                                .margin(left = 15f)
                            )
                        }
                    }
                }
            }
        }

        column(Modifier.background(Colors.BG.argb).border(.5f, Colors.GRAY.argb).clip(Shape.RoundedRect(5f))) {
            barWithLabels(theme, colors.map { it to 1f })
            barWithLabels(theme, colors.mapIndexed { index, color ->
                color to (colors.size - index).toFloat()
            })
        }
    }

    @Test
    fun test_radar() = testCompose("radar") {
        val list = listOf(
            "js",
            "c#",
            "rust",
            "scala",
            "sql",
            "typescript",
            "markdown",
            "java",
            "kotlin",
        )
        val data = list.mapIndexed { index, s -> s to index.toFloat() / list.size }

        val theme = RadarTheme(800f, 600f)

        column(Modifier
            .background(Colors.BG.argb)
            .border(.5f, Colors.GRAY.argb)
            .clip(Shape.RoundedRect(5f))
        ) {
            radar(theme, data)
        }
    }

    @Test
    fun testIcon() = testCompose("icon") {
        val svgContent = """<svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 -960 960 960" width="24px" fill="#e3e3e3"><path d="m296-105-56-56 240-240 240 240-56 56-184-183-184 183Zm0-240-56-56 240-240 240 240-56 56-184-183-184 183Zm0-240-56-56 240-240 240 240-56 56-184-183-184 183Z"/></svg>"""
        column(Modifier.background(Colors.BG.argb).padding(30f)) {
            row(Modifier.border(1f, Color.WHITE), VerticalAlignment.Center) {
                box(
                    Modifier
                        .background(Color.makeRGB(132, 93, 181))
                        .clip(Shape.RoundedRect(50f))
                ) {
                    icon(IconTheme(50f), svgContent)
                }
                text("测试图标", Modifier.fontSize(40f).margin(left = 10f))
            }
        }
    }
}