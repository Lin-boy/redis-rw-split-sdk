package com.example.redis.rw;

import com.example.redis.rw.core.RWContextHolder;
import com.example.redis.rw.core.RWType;
import com.example.redis.rw.core.ReadWriteRedisConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

public class RedisRWRoutingTest {

    private RedisConnectionFactory masterFactory;
    private RedisConnectionFactory slaveFactory;
    private RedisConnection masterConnection;
    private RedisConnection slaveConnection;
    private ReadWriteRedisConnectionFactory routingFactory;

    @BeforeEach
    void setUp() {
        masterFactory = mock(RedisConnectionFactory.class);
        slaveFactory = mock(RedisConnectionFactory.class);
        masterConnection = mock(RedisConnection.class);
        slaveConnection = mock(RedisConnection.class);

        when(masterFactory.getConnection()).thenReturn(masterConnection);
        when(slaveFactory.getConnection()).thenReturn(slaveConnection);

        routingFactory = new ReadWriteRedisConnectionFactory(masterFactory, Collections.singletonList(slaveFactory));
    }

    @Test
    void testWriteRouting() {
        RedisConnection proxy = routingFactory.getConnection();
        
        // 执行写操作
        proxy.set("key".getBytes(), "value".getBytes());
        
        // 验证主库被调用
        verify(masterConnection, times(1)).set(any(), any());
        verify(slaveConnection, never()).set(any(), any());
    }

    @Test
    void testReadRouting() {
        RedisConnection proxy = routingFactory.getConnection();
        
        // 执行读操作
        proxy.get("key".getBytes());
        
        // 验证从库被调用
        verify(slaveConnection, times(1)).get(any());
        verify(masterConnection, never()).get(any());
    }

    @Test
    void testForcedWriteRouting() {
        RedisConnection proxy = routingFactory.getConnection();
        
        RWContextHolder.setRWType(RWType.WRITE);
        try {
            proxy.get("key".getBytes());
            // 即使是 get，但由于强制写，应该路由到主库
            verify(masterConnection, times(1)).get(any());
            verify(slaveConnection, never()).get(any());
        } finally {
            RWContextHolder.clear();
        }
    }

    @Test
    void testForcedReadRouting() {
        RedisConnection proxy = routingFactory.getConnection();
        
        RWContextHolder.setRWType(RWType.READ);
        try {
            proxy.set("key".getBytes(), "value".getBytes());
            // 即使是 set，但由于强制读，应该路由到从库
            verify(slaveConnection, times(1)).set(any(), any());
            verify(masterConnection, never()).set(any(), any());
        } finally {
            RWContextHolder.clear();
        }
    }
}
