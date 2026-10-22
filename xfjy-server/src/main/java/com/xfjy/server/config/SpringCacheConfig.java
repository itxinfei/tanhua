package com.xfjy.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

/**
 * Spring Cache配置（替代旧版RedisCacheInterceptor请求级缓存）
 * <p>
 * 缓存策略：
 * - 默认TTL 1小时
 * - userInfo: 用户信息，按用户ID缓存（@Cacheable在UserInfoService中声明）
 * - announcement: 公告列表，按分页参数缓存（@Cacheable在AnnouncementService中声明）
 */
@Configuration
public class SpringCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .build();
    }
}