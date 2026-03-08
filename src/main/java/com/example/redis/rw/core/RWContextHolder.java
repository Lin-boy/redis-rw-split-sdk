package com.example.redis.rw.core;

public class RWContextHolder {
    private static final ThreadLocal<RWType> contextHolder = new ThreadLocal<>();

    public static void setRWType(RWType rwType) {
        contextHolder.set(rwType);
    }

    public static RWType getRWType() {
        return contextHolder.get();
    }

    public static void clear() {
        contextHolder.remove();
    }
}
