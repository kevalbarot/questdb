package org.questdb;

import io.questdb.metrics.Counter;
import io.questdb.metrics.CounterImpl;
import io.questdb.metrics.LongGauge;
import io.questdb.metrics.LongGaugeImpl;
import io.questdb.std.Chars;
import io.questdb.std.ConcurrentAssociativeCache;
import io.questdb.std.Rnd;
import io.questdb.std.SimpleAssociativeCache;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@Threads(Threads.MAX)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class AssociativeCacheBenchmark {
    private static final int N_QUERIES = 50;
    private static final LongGauge cachedGauge = new LongGaugeImpl("bench");
    private static final Counter hitCounter = new CounterImpl("bench");
    private static final Counter missCounter = new CounterImpl("bench");
    private static final String[] queries = new String[N_QUERIES];
    private ConcurrentAssociativeCache<Integer> cacheNoMetrics;
    private ConcurrentAssociativeCache<Integer> cacheWithMetrics;

    static {
        Rnd rnd = new Rnd();
        for (int i = 0; i < queries.length; i++) {
            queries[i] = Chars.toString(rnd.nextChars(100));
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        final int cpus = Runtime.getRuntime().availableProcessors();
        cacheNoMetrics = new ConcurrentAssociativeCache<>(8 * cpus, 2 * cpus);
        cacheWithMetrics = new ConcurrentAssociativeCache<>(
                8 * cpus,
                2 * cpus,
                cachedGauge,
                hitCounter,
                missCounter
        );
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AssociativeCacheBenchmark.class.getSimpleName())
                .warmupIterations(3)
                .measurementIterations(3)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

    @Benchmark
    public void testConcurrentNoMetrics_randomKeys(RndState rndState) {
        CharSequence k = queries[rndState.rnd.nextInt(N_QUERIES)];
        Integer v = cacheNoMetrics.poll(k);
        cacheNoMetrics.put(k, v != null ? v : 42);
    }

    @Benchmark
    public void testConcurrentNoMetrics_sameKey() {
        Integer v = cacheNoMetrics.poll(queries[0]);
        cacheNoMetrics.put(queries[0], v != null ? v : 42);
    }

    @Benchmark
    public void testConcurrentWithMetrics_randomKeys(RndState rndState) {
        CharSequence k = queries[rndState.rnd.nextInt(N_QUERIES)];
        Integer v = cacheWithMetrics.poll(k);
        cacheWithMetrics.put(k, v != null ? v : 42);
    }

    @Benchmark
    public void testConcurrentWithMetrics_sameKey() {
        Integer v = cacheWithMetrics.poll(queries[0]);
        cacheWithMetrics.put(queries[0], v != null ? v : 42);
    }

    @Benchmark
    public void testSimpleWithMetrics_randomKeys(RndState rndState) {
        CharSequence k = queries[rndState.rnd.nextInt(N_QUERIES)];
        Integer v = rndState.localCache.poll(k);
        rndState.localCache.put(k, v != null ? v : 42);
    }

    @Benchmark
    public void testSimpleWithMetrics_sameKey(RndState rndState) {
        Integer v = rndState.localCache.poll(queries[0]);
        rndState.localCache.put(queries[0], v != null ? v : 42);
    }

    @State(Scope.Thread)
    public static class RndState {
        final SimpleAssociativeCache<Integer> localCache;
        final Rnd rnd = new Rnd();

        public RndState() {
            final int cpus = Runtime.getRuntime().availableProcessors();
            this.localCache = new SimpleAssociativeCache<>(
                    8 * cpus,
                    2 * cpus,
                    cachedGauge,
                    hitCounter,
                    missCounter
            );
        }
    }
}
