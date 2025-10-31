package top.e404.skiko.draw.compose.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Image
import org.junit.Test
import top.e404.skiko.draw.compose.*
import top.e404.skiko.util.Colors
import java.io.File
import java.net.URL

class TestRender {
    @UiDsl
    fun UiElement.profileCard(name: String, desc: String, image: Image, modifier: Modifier = Modifier) {
        row(
            verticalAlignment = VerticalAlignment.Center,
            modifier = modifier
                // .border(all = 1f, color = Color.makeRGB(220, 220, 220))
                // .background(Color.WHITE)
                .padding(all = 12f)
        ) {
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
    val cards = (1..10).map {
        Triple(images[images.size % it], "StormBase", (0..10).joinToString(" ") { "StormBase" })
    }

    init {
        DefaultTypefaceProvider.default = FontMgr.default.makeFromData(Data.makeFromBytes(File("font/LXGWWenKai-Regular.ttf").readBytes()))!!
    }

    @UiDsl
    fun UiElement.cards() {
        column(
            horizontalAlignment = HorizontalAlignment.Left,
            modifier = Modifier.padding(20f)
                .background(Colors.BG.argb)
        ) {
            for ((image, name, desc) in cards) {
                profileCard(name, desc, image)
            }
        }
    }

    fun testCompose(name: String, content: Composable) {
        render(Color.WHITE, content).let {
            val bytes = it.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("out/compose/$name.png").also {
                it.parentFile.mkdirs()
                println(it.absolutePath)
            }.writeBytes(bytes)
        }
    }

    @Test
    fun testAll() {
        test1()
        test2()
        test3()
        test4()
        test5()
    }

    @Test
    fun test1() {
        testCompose("align") {
            column(
                modifier = Modifier
                    .padding(10f)
                    .background(Color.makeRGB(245, 245, 245))
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
                            .border(50F, 80f, Color.makeARGB(128, 255, 193, 7))
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
                            .border(50F, 80f, Color.makeARGB(128, 255, 193, 7))
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
                            .border(50F, 80f, Color.makeARGB(128, 255, 193, 7))
                            .clip(Shape.RoundedRect(15f))
                    )
                }
            }
        }
    }

    @Test
    fun test2() {
        testCompose("compose") {
            column(
                horizontalAlignment = HorizontalAlignment.Center,
                modifier = Modifier.padding(20f)
                    .background(Color.makeRGB(200, 200, 200))
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
                        .margin(top = 20F, bottom = 16f)
                )
            }
        }
    }

    @Test
    fun test3() {
        testCompose("padding") {
            column(
                horizontalAlignment = HorizontalAlignment.Left,
                modifier = Modifier.padding(20f).background(Color.makeRGB(240, 240, 240))
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
    }

    @Test
    fun test4() {
        testCompose("clip") {
            column(
                horizontalAlignment = HorizontalAlignment.Left,
                modifier = Modifier
                    .padding(20f)
                    .background(Color.makeRGB(240, 240, 240))
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
    }

    @Test
    fun test5() {
        testCompose("overflow") {
            column(
                modifier = Modifier
                    .padding(15f)
                    .background(Colors.WHITE.argb)
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
    }
}