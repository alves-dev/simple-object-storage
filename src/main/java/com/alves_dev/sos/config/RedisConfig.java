package com.alves_dev.sos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(CacheConfig config) {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(config.host(), config.port());
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(config.timeout())
                .shutdownTimeout(config.timeout())
                .build();
        return new LettuceConnectionFactory(server, client);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
