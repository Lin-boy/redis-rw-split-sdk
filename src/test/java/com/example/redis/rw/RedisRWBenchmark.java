package com.example.redis.rw;

import com.example.redis.rw.core.RWContextHolder;
import com.example.redis.rw.core.RWType;
import com.example.redis.rw.core.ReadWriteRedisConnectionFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * High-performance JMH benchmark for Redis Read/Write routing logic.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(Threads.MAX) // Use all CPU cores to test maximum contention
public class RedisRWBenchmark {

    private ReadWriteRedisConnectionFactory factory;

    @Setup
    public void setup() {
        // Simple dummy factories to avoid Mockito overhead
        RedisConnectionFactory master = createDummyFactory("master");
        List<RedisConnectionFactory> slaves = new ArrayList<>();
        slaves.add(createDummyFactory("slave1"));
        slaves.add(createDummyFactory("slave2"));
        slaves.add(createDummyFactory("slave3"));

        factory = new ReadWriteRedisConnectionFactory(master, slaves);
    }

    /**
     * Benchmark write routing (always master)
     */
    @Benchmark
    @Group("mixed_load")
    @GroupThreads(4) // 4 threads for write
    public void benchmarkWriteRouting(Blackhole bh) {
        RWContextHolder.setRWType(RWType.WRITE);
        try {
            bh.consume(factory.getActualFactory());
        } finally {
            RWContextHolder.clear();
        }
    }

    /**
     * Benchmark read routing (load balanced slaves)
     */
    @Benchmark
    @Group("mixed_load")
    @GroupThreads(16) // 16 threads for read (simulating read-heavy workload)
    public void benchmarkReadRouting(Blackhole bh) {
        RWContextHolder.setRWType(RWType.READ);
        try {
            bh.consume(factory.getActualFactory());
        } finally {
            RWContextHolder.clear();
        }
    }

    /**
     * Benchmark default routing (no context, defaults to master)
     */
    @Benchmark
    public void benchmarkDefaultRouting(Blackhole bh) {
        bh.consume(factory.getActualFactory());
    }

    private RedisConnectionFactory createDummyFactory(String id) {
        return new RedisConnectionFactory() {
            @Override public String toString() { return id; }
            // Implement other methods as NO-OP...
            @Override public org.springframework.data.redis.connection.RedisConnection getConnection() { return null; }
            @Override public org.springframework.data.redis.connection.RedisClusterConnection getClusterConnection() { return null; }
            @Override public boolean getConvertPipelineAndTxResults() { return false; }
            @Override public org.springframework.data.redis.connection.RedisSentinelConnection getSentinelConnection() { return null; }
            @Override public org.springframework.dao.DataAccessException translateExceptionIfPossible(RuntimeException ex) { return null; }
        };
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(RedisRWBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
