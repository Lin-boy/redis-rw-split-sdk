package com.example.redis.rw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "spring.redis.rw")
public class RedisRWProperties {
    /**
     * Whether to enable R/W splitting
     */
    private boolean enabled = false;

    /**
     * Master node configuration (for write operations)
     */
    private RedisProperties master;

    /**
     * Slave nodes configuration (for read operations)
     */
    private List<RedisProperties> slaves;
}
