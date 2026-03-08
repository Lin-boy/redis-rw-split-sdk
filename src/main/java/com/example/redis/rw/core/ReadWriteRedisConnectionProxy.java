package com.example.redis.rw.core;

import org.springframework.data.redis.connection.RedisConnection;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ReadWriteRedisConnectionProxy implements InvocationHandler {

    private final ReadWriteRedisConnectionFactory factory;
    private RedisConnection masterConnection;
    private RedisConnection slaveConnection;

    private static final Set<String> READ_METHODS = new HashSet<>(Arrays.asList(
            "get", "mGet", "exists", "ttl", "pTtl", "type", "keys", "scan", "randomKey",
            "hGet", "hMGet", "hGetAll", "hKeys", "hVals", "hLen", "hExists", "hScan",
            "lIndex", "lLen", "lRange", "sMembers", "sIsMember", "sCard", "sScan",
            "zRange", "zRevRange", "zRangeByScore", "zRevRangeByScore", "zCount", "zCard", "zScore", "zRank", "zRevRank", "zScan"
    ));

    public ReadWriteRedisConnectionProxy(ReadWriteRedisConnectionFactory factory) {
        this.factory = factory;
    }

    public static RedisConnection create(ReadWriteRedisConnectionFactory factory) {
        return (RedisConnection) Proxy.newProxyInstance(
                RedisConnection.class.getClassLoader(),
                new Class[]{RedisConnection.class},
                new ReadWriteRedisConnectionProxy(factory));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Handle Object methods
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        if (method.getName().equals("close")) {
            if (masterConnection != null) masterConnection.close();
            if (slaveConnection != null) slaveConnection.close();
            return null;
        }

        RWType contextType = RWContextHolder.getRWType();
        RedisConnection connection;

        if (contextType == RWType.WRITE) {
            connection = getMasterConnection();
        } else if (contextType == RWType.READ) {
            connection = getSlaveConnection();
        } else {
            // Auto decide based on method name
            if (isReadOperation(method.getName())) {
                connection = getSlaveConnection();
            } else {
                connection = getMasterConnection();
            }
        }

        try {
            return method.invoke(connection, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private boolean isReadOperation(String methodName) {
        // If method name starts with any read method prefix (case-insensitive)
        String lowerMethodName = methodName.toLowerCase();
        return READ_METHODS.stream().anyMatch(readMethod -> lowerMethodName.startsWith(readMethod.toLowerCase()));
    }

    private RedisConnection getMasterConnection() {
        if (masterConnection == null) {
            masterConnection = factory.getMasterFactory().getConnection();
        }
        return masterConnection;
    }

    private RedisConnection getSlaveConnection() {
        if (slaveConnection == null) {
            // Select a slave based on load balancing
            slaveConnection = factory.getSlaveFactory().getConnection();
        }
        return slaveConnection;
    }
}
