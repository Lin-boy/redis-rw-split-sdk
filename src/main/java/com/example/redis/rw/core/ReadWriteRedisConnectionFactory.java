package com.example.redis.rw.core;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ReadWriteRedisConnectionFactory implements RedisConnectionFactory {

    private final RedisConnectionFactory masterFactory;
    private final List<RedisConnectionFactory> slaveFactories;
    private final AtomicInteger counter = new AtomicInteger(0);

    public ReadWriteRedisConnectionFactory(RedisConnectionFactory masterFactory, List<RedisConnectionFactory> slaveFactories) {
        this.masterFactory = masterFactory;
        this.slaveFactories = slaveFactories;
    }

    @Override
    public RedisConnection getConnection() {
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

    public RedisConnectionFactory getActualFactory() {
        RWType type = RWContextHolder.getRWType();
        if (type == RWType.READ) {
            return getSlaveFactory();
        }
        return masterFactory;
    }

    public RedisConnectionFactory getSlaveFactory() {
        if (slaveFactories.isEmpty()) {
            return masterFactory;
        }
        int index = Math.abs(counter.getAndIncrement() % slaveFactories.size());
        return slaveFactories.get(index);
    }

    public RedisConnectionFactory getMasterFactory() {
        return masterFactory;
    }

    public List<RedisConnectionFactory> getSlaveFactories() {
        return slaveFactories;
    }
}
