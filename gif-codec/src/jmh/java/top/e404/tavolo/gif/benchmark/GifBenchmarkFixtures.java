package top.e404.tavolo.gif.benchmark;

import org.jetbrains.skia.Color;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.Paint;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.Surface;
import top.e404.tavolo.frame.Frame;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成稳定且包含足够颜色变化的 GIF 编码基准素材。
 */
final class GifBenchmarkFixtures {
    private GifBenchmarkFixtures() {
    }

    static List<Frame> createFrames(String scenario) {
        switch (scenario) {
            case "small_opaque":
                return createFrames(256, 256, 10, false);
            case "skin_opaque":
                return createFrames(600, 900, 20, false);
            case "skin_alpha":
                return createFrames(600, 900, 20, true);
            case "homo_alpha":
                return createFrames(1024, 768, 20, true);
            default:
                throw new IllegalArgumentException("未知基准场景: " + scenario);
        }
    }

    private static List<Frame> createFrames(
        int width,
        int height,
        int frameCount,
        boolean transparent
    ) {
        List<Frame> frames = new ArrayList<>(frameCount);
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            frames.add(new Frame(40, createImage(width, height, frameIndex, transparent)));
        }
        return frames;
    }

    private static Image createImage(int width, int height, int frameIndex, boolean transparent) {
        Surface surface = Surface.Companion.makeRasterN32Premul(width, height);
        surface.getCanvas().clear(Color.TRANSPARENT);
        Paint paint = new Paint();
        int cellSize = 16;
        for (int y = 0; y < height; y += cellSize) {
            for (int x = 0; x < width; x += cellSize) {
                int red = (x * 5 + frameIndex * 17) & 0xFF;
                int green = (y * 3 + frameIndex * 29) & 0xFF;
                int blue = ((x + y) * 2 + frameIndex * 11) & 0xFF;
                int alpha = transparent && ((x / cellSize + y / cellSize + frameIndex) % 7 == 0)
                    ? 0
                    : 255;
                paint.setColor(Color.INSTANCE.makeARGB(alpha, red, green, blue));
                surface.getCanvas().drawRect(
                    Rect.makeXYWH(
                        x,
                        y,
                        Math.min(cellSize, width - x),
                        Math.min(cellSize, height - y)
                    ),
                    paint
                );
            }
        }
        return surface.makeImageSnapshot();
    }
}
