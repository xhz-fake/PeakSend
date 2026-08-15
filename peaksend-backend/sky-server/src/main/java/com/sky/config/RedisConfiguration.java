package com.sky.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.sky.json.JacksonObjectMapper;
import com.sky.properties.CacheTtlProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 和 Spring Cache 配置。
 */
@Configuration
@Slf4j
public class RedisConfiguration extends CachingConfigurerSupport {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashValueSerializer(jsonSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory, CacheTtlProperties cacheTtlProperties) {
        //它在告诉 Spring：以后项目里的缓存，不要完全用默认规则了。我们自己指定每类缓存怎么配。
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("categoryCache", createCacheConfiguration(cacheTtlProperties.getCategoryTtl()));
        cacheConfigurations.put("dishCache", createCacheConfiguration(cacheTtlProperties.getDishTtl()));
        cacheConfigurations.put("setmealCache", createCacheConfiguration(cacheTtlProperties.getSetmealTtl()));
        cacheConfigurations.put("addressBookByIdCache", createCacheConfiguration(cacheTtlProperties.getAddressBookByIdTtl()));
        cacheConfigurations.put("turnoverReportCache", createCacheConfiguration(cacheTtlProperties.getReportTtl()));
        cacheConfigurations.put("userReportCache", createCacheConfiguration(cacheTtlProperties.getReportTtl()));
        cacheConfigurations.put("orderReportCache", createCacheConfiguration(cacheTtlProperties.getReportTtl()));
        cacheConfigurations.put("salesTop10ReportCache", createCacheConfiguration(cacheTtlProperties.getReportTtl()));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(createCacheConfiguration(cacheTtlProperties.getDefaultTtl()))
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    private RedisCacheConfiguration createCacheConfiguration(Duration ttl) {
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));
    }

    private GenericJackson2JsonRedisSerializer createJsonSerializer() {
        ObjectMapper objectMapper = new JacksonObjectMapper();
        //Redis 这边不要再用一套陌生的默认规则了，直接复用我们项目原本就已经调好时间格式的这套对象映射器。

        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        //它是为了让 Redis 在反序列化时，知道缓存里这个 JSON 原来对应的 Java 类型是什么。

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {//Spring Cache 出错时的统一兜底处理器
        return new CacheErrorHandler() {
            @Override
            // 读缓存失败
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis 读取缓存失败，回退业务查询。cache={}, key={}", cache.getName(), key, exception);
                //缓存读失败了，别慌，后面继续走正常业务查询。
            }

            @Override
            // 写缓存失败
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis 写入缓存失败，忽略本次缓存写入。cache={}, key={}", cache.getName(), key, exception);
                // 这次缓存没写进去，但接口别炸，结果照样返回给前端。
            }

            @Override
            // 删缓存失败
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis 删除缓存失败，忽略本次缓存删除。cache={}, key={}", cache.getName(), key, exception);
                //- 先记日志,不让接口直接因为删缓存失败而崩掉
            }

            @Override
            // 清空缓存失败
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis 清空缓存失败，忽略本次缓存清空。cache={}", cache.getName(), exception);
            }
        };
    }

    @Override
    //显式告诉 Spring Cache：以后出异常时，就用我上面定义的这套处理器。
    public CacheErrorHandler errorHandler() {
        return cacheErrorHandler();
    }
}
