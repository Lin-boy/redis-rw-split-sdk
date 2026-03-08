package com.example.redis.rw;

import com.example.redis.rw.core.RWContextHolder;
import com.example.redis.rw.core.RWType;
import com.example.redis.rw.core.ReadWriteRedisConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

class RedisRWHighConcurrencyTest {

    private ReadWriteRedisConnectionFactory factory;

    @Mock
    private RedisConnectionFactory masterFactory;
    @Mock
    private RedisConnectionFactory slave1;
    @Mock
    private RedisConnectionFactory slave2;
    @Mock
    private RedisConnectionFactory slave3;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        List<RedisConnectionFactory> slaves = new ArrayList<>();
        slaves.add(slave1);
        slaves.add(slave2);
        slaves.add(slave3);
        factory = new ReadWriteRedisConnectionFactory(masterFactory, slaves);
    }

    @Test
    @DisplayName("High Concurrency Test with Virtual Threads (if available) or ThreadPool")
    void testHighConcurrencyRouting() throws InterruptedException {
        int threadCount = 2000; // Simulate 2k concurrent tasks
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = createExecutor();
        
        Map<RedisConnectionFactory, LongAdder> stats = new ConcurrentHashMap<>();
        stats.put(masterFactory, new LongAdder());
        stats.put(slave1, new LongAdder());
        stats.put(slave2, new LongAdder());
        stats.put(slave3, new LongAdder());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    // Randomly assign READ or WRITE to simulate mixed load
                    RWType type = (index % 2 == 0) ? RWType.READ : RWType.WRITE;
                    RWContextHolder.setRWType(type);
                    
                    // Simulate multiple calls within the same thread context
                    for (int j = 0; j < 5; j++) {
                        RedisConnectionFactory selected = factory.getActualFactory();
                        stats.get(selected).increment();
                    }
                } finally {
                    RWContextHolder.clear();
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "Test timed out under heavy load");
        executor.shutdown();

        // Verification
        System.out.println("--- Concurrency Stats ---");
        stats.forEach((f, count) -> System.out.println(f + ": " + count.sum()));

        // Assert that master received all WRITE requests (threadCount / 2 * 5)
        assertEquals(5000, stats.get(masterFactory).sum(), "Master should handle all WRITE requests");
        
        // Assert that slaves shared the READ requests (threadCount / 2 * 5 = 5000 total)
        long totalSlaveRequests = stats.get(slave1).sum() + stats.get(slave2).sum() + stats.get(slave3).sum();
        assertEquals(5000, totalSlaveRequests, "Slaves should handle all READ requests combined");

        // Verify distribution is somewhat balanced (ThreadLocalRandom check)
        assertTrue(stats.get(slave1).sum() > 0, "Slave 1 should have received requests");
        assertTrue(stats.get(slave2).sum() > 0, "Slave 2 should have received requests");
        assertTrue(stats.get(slave3).sum() > 0, "Slave 3 should have received requests");
    }

    @Test
    @DisplayName("Test Default Fallback under Stress")
    void testFallbackConsistency() throws InterruptedException {
        int threadCount = 500;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Purposefully DON'T set RWType, should default to WRITE (MASTER)
                    assertEquals(masterFactory, factory.getActualFactory(), "Should default to Master");
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
    }

    /**
     * Create Virtual Thread Executor if on JDK 21+, otherwise fallback to FixedThreadPool.
     */
    private ExecutorService createExecutor() {
        try {
            // Check if JDK 21 Virtual Threads are available via reflection
            // This allows the test to compile on JDK 17 but run with VT on JDK 21
            return (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
        } catch (Exception e) {
            System.out.println("Virtual Threads not available, falling back to FixedThreadPool");
            return Executors.newFixedThreadPool(100);
        }
    }
}
