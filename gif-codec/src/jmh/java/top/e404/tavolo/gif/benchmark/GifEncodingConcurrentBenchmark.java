package top.e404.tavolo.gif.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
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
 * 模拟四个动画请求同时编码时的吞吐和内存压力。
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms1g", "-Xmx8g"})
public class GifEncodingConcurrentBenchmark {
    private List<Frame> frames;

    @Setup(Level.Trial)
    public void setup() {
        frames = GifBenchmarkFixtures.createFrames("skin_alpha");
    }

    @Benchmark
    @Threads(4)
    public byte[] encodeFourRequests() {
        return FramesKt.encodeToBytes(frames);
    }
}
