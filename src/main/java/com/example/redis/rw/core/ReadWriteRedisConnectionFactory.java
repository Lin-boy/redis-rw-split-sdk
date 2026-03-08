package com.example.redis.rw.core;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Connection factory supporting read-write splitting with high-concurrency optimization.
 */
public class ReadWriteRedisConnectionFactory implements RedisConnectionFactory {

    private final RedisConnectionFactory masterFactory;
    private final List<RedisConnectionFactory> slaveFactories;
    private final int slaveCount;

    public ReadWriteRedisConnectionFactory(RedisConnectionFactory masterFactory, List<RedisConnectionFactory> slaveFactories) {
        this.masterFactory = masterFactory;
        this.slaveFactories = slaveFactories;
        this.slaveCount = slaveFactories != null ? slaveFactories.size() : 0;
    }

    @Override
    public RedisConnection getConnection() {
        // Return a proxy to handle routing on each connection usage
        return ReadWriteRedisConnectionProxy.create(this);
    }

    @Override
    public RedisClusterConnection getClusterConnection() {
        return getActualFactory().getClusterConnection();
    }

    @Override
    public boolean getConvertPipelineAndTxResults() {
        return masterFactory.getConvertPipelineAndTxResults();
    }

    @Override
    public RedisSentinelConnection getSentinelConnection() {
        return masterFactory.getSentinelConnection();
    }

    @Override
    public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
        return masterFactory.translateExceptionIfPossible(ex);
    }

    /**
     * Determines the actual factory to use based on the current context.
     * High-performance routing: O(1) complexity.
     */
    public RedisConnectionFactory getActualFactory() {
        RWType type = RWContextHolder.getRWType();
        if (type == RWType.READ && slaveCount > 0) {
            return getSlaveFactory();
        }
        return masterFactory;
    }

    /**
     * Selection logic for slave factories. 
     * Uses ThreadLocalRandom for zero-contention load balancing in high-concurrency environments.
     */
    public RedisConnectionFactory getSlaveFactory() {
        if (slaveCount == 0) {
            return masterFactory;
        }
        if (slaveCount == 1) {
            return slaveFactories.get(0);
        }
        // Use ThreadLocalRandom to avoid AtomicInteger CAS contention in ultra-high concurrency
        int index = ThreadLocalRandom.current().nextInt(slaveCount);
        return slaveFactories.get(index);
    }

    public RedisConnectionFactory getMasterFactory() {
        return masterFactory;
    }

    public List<RedisConnectionFactory> getSlaveFactories() {
        return slaveFactories;
    }
}
