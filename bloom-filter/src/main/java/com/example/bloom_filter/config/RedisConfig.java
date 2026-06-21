package com.example.bloom_filter.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Value("${redis.url:redis://localhost:6379}")
    private String redisUrl;

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient() {
        return RedisClient.create(redisUrl);
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient) {
        return redisClient.connect();
    }

    @Bean
    public RedisCommands<String, String> redisCommands(
            StatefulRedisConnection<String, String> connection) {
        return connection.sync();
    }
}
