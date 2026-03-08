package com.example.redis.rw.config;

import com.example.redis.rw.core.ReadWriteRedisConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "spring.redis.rw", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RedisRWProperties.class)
public class RedisRWAutoConfiguration {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory(RedisRWProperties properties) {
        RedisConnectionFactory masterFactory = createFactory(properties.getMaster());
        
        List<RedisConnectionFactory> slaveFactories = new ArrayList<>();
        if (properties.getSlaves() != null) {
            for (RedisProperties slaveProps : properties.getSlaves()) {
                slaveFactories.add(createFactory(slaveProps));
            }
        }
        
        return new ReadWriteRedisConnectionFactory(masterFactory, slaveFactories);
    }

    private RedisConnectionFactory createFactory(RedisProperties props) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(props.getHost());
        config.setPort(props.getPort());
        if (props.getPassword() != null) {
            config.setPassword(props.getPassword());
        }
        config.setDatabase(props.getDatabase());
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }
}
