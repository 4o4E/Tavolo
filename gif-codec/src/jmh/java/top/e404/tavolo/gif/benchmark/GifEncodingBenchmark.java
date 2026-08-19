package top.e404.tavolo.gif.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import top.e404.tavolo.frame.Frame;
import top.e404.tavolo.frame.FramesKt;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 覆盖常用尺寸、帧数和透明度的完整 GIF 编码基准。
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms1g", "-Xmx8g"})
public class GifEncodingBenchmark {
    @Param({"small_opaque", "skin_opaque", "skin_alpha", "homo_alpha"})
    public String scenario;

    private List<Frame> frames;

    @Setup(Level.Trial)
    public void setup() {
        frames = GifBenchmarkFixtures.createFrames(scenario);
    }

    @Benchmark
    @Threads(1)
    public byte[] encode() {
        return FramesKt.encodeToBytes(frames);
    }
}
