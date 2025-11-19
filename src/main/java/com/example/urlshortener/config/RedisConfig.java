package com.example.urlshortener.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.RedisURI;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.url:}")
    private String redisUrl;

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<byte[], byte[]> statefulRedisConnection(
            @Autowired LettuceConnectionFactory lettuceConnectionFactory) {

        // Prefer Boot property spring.data.redis.url (works with Upstash rediss://)
        RedisURI redisURI;
        if (redisUrl != null && !redisUrl.isBlank()) {
            redisURI = RedisURI.create(redisUrl);
        } else {
            // Fallback to host/port (local dev)
            redisURI = RedisURI.builder()
                .withHost(lettuceConnectionFactory.getHostName())
                .withPort(lettuceConnectionFactory.getPort())
                .build();
        }

        return RedisClient.create(redisURI).connect(ByteArrayCodec.INSTANCE);
    }

    @Bean
    public ProxyManager<byte[]> bucket4jProxyManager(StatefulRedisConnection<byte[], byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection.async()).build();
    }
}
