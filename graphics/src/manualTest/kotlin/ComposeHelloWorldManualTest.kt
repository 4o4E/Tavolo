package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.text

class ComposeHelloWorldManualTest {
    private val uiFont = ManualTestSupport.uiFont

    @Test
    fun test_readme_hello_world() {
        ManualTestSupport.saveCompose("README-01-Hello-World") {
            // README 示例保持足够小，确保图片和代码一一对应。
            box(
                modifier = Modifier
                    .size(860f, 360f)
                    .background(Color.makeRGB(26, 34, 48))
                    .padding(42f)
            ) {
                column(
                    modifier = Modifier
                        .background(Color.makeRGB(255, 255, 255))
                        .padding(36f)
                ) {
                    text(
                        "Hello, World!",
                        fontSize = 56f,
                        textColor = Color.makeRGB(38, 58, 92),
                        fontFamily = uiFont
                    )
                    text(
                        "Tavolo Compose DSL",
                        modifier = Modifier.padding(top = 18f),
                        fontSize = 28f,
                        textColor = Color.makeRGB(87, 103, 128),
                        fontFamily = uiFont
                    )
                }
            }
        }
    }
}
