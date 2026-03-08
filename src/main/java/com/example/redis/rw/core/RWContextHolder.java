package com.example.redis.rw.core;

import java.util.Optional;

/**
 * Holder for the current Read/Write routing type.
 * 
 * Optimized for JDK 21+ Virtual Threads and high-concurrency scenarios.
 * While ScopedValue is preferred in JDK 21, we use a robust ThreadLocal 
 * implementation here for broad compatibility, ensuring proper cleanup.
 */
public class RWContextHolder {
    // Note: In a pure JDK 21 project, you would use:
    // private static final ScopedValue<RWType> SCOPED_RW_TYPE = ScopedValue.newInstance();
    
    private static final ThreadLocal<RWType> contextHolder = new ThreadLocal<>();

    /**
     * Set the current routing type.
     * @param rwType READ or WRITE
     */
    public static void setRWType(RWType rwType) {
        if (rwType == null) {
            clear();
        } else {
            contextHolder.set(rwType);
        }
    }

    /**
     * Get the current routing type. Defaults to WRITE if not set to ensure data consistency.
     * @return The active RWType
     */
    public static RWType getRWType() {
        RWType type = contextHolder.get();
        // Default to WRITE for safety in high-concurrency scenarios where state might be lost
        return type != null ? type : RWType.WRITE;
    }

    /**
     * Clear the context to prevent memory leaks in thread pools.
     */
    public static void clear() {
        contextHolder.remove();
    }
}
