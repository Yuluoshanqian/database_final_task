package com.student.management.common;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Redis 缓存服务，封装缓存读写和降级逻辑。
 *
 * 设计要点：
 * - 所有 Key 带 "teaching-affairs:" 前缀，避免与其他应用冲突
 * - Redis 不可用时自动降级（backoff 30 秒），期间直接走数据库，不影响业务
 * - 写操作后由 Service 层调用 evictByPrefix 批量清除相关缓存
 * - 提供 "cache-aside" 模式：get(key, type, loader) 先查缓存，未命中则查库并回填
 */
@Component
public class RedisCacheService {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private static final String KEY_PREFIX = "teaching-affairs:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration defaultTtl;
    private final Duration failureBackoff;
    private volatile long retryAtMillis;

    /**
     * 构造器注入，objectMapper.copy() 避免外部修改 ObjectMapper 配置影响缓存序列化。
     */
    public RedisCacheService(StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper,
                             @Value("${app.cache.enabled:true}") boolean enabled,
                             @Value("${app.cache.ttl-seconds:300}") long ttlSeconds,
                             @Value("${app.cache.redis-backoff-seconds:30}") long redisBackoffSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy();
        this.enabled = enabled;
        this.defaultTtl = Duration.ofSeconds(Math.max(ttlSeconds, 1));
        this.failureBackoff = Duration.ofSeconds(Math.max(redisBackoffSeconds, 1));
    }

    /**
     * Cache-aside 模式：缓存命中直接返回，未命中则执行 loader 查询数据库并回填缓存。
     */
    public <T> T get(String key, TypeReference<T> type, Supplier<T> loader) {
        T cached = getValue(key, type);
        if (cached != null) {
            return cached;
        }
        T value = loader.get();
        put(key, value);
        return value;
    }

    /**
     * 仅从缓存读取，不触发 loader。Redis 不可用时返回 null（调用方自行降级）。
     * 反序列化失败或 Redis 异常均视为缓存未命中，不抛异常。
     */
    public <T> T getValue(String key, TypeReference<T> type) {
        if (!redisAllowed()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(redisKey(key));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            log.debug("Failed to deserialize Redis cache key {}", key, ex);
            return null;
        } catch (RuntimeException ex) {
            markRedisFailure(ex);
            return null;
        }
    }

    /** 写入缓存，使用默认 TTL（300 秒）。 */
    public boolean put(String key, Object value) {
        return put(key, value, defaultTtl);
    }

    /**
     * 写入缓存并指定 TTL。返回 boolean 而非抛异常，
     * 因为缓存写入失败不应阻断业务流程。
     */
    public boolean put(String key, Object value, Duration ttl) {
        if (value == null || !redisAllowed()) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(redisKey(key), objectMapper.writeValueAsString(value), ttl);
            return true;
        } catch (JsonProcessingException ex) {
            log.debug("Failed to serialize Redis cache key {}", key, ex);
            return false;
        } catch (RuntimeException ex) {
            markRedisFailure(ex);
            return false;
        }
    }

    /** 精确删除指定 Key 的缓存。 */
    public void evict(String... keys) {
        if (!redisAllowed()) {
            return;
        }
        for (String key : keys) {
            try {
                redisTemplate.delete(redisKey(key));
            } catch (RuntimeException ex) {
                markRedisFailure(ex);
                return;
            }
        }
    }

    /**
     * 按前缀模糊删除缓存。使用 keys 命令扫描匹配的 Key 后批量删除。
     * 仅用于写操作后清除关联缓存，不适用于高频场景（keys 命令会阻塞 Redis）。
     */
    public void evictByPrefix(String... prefixes) {
        if (!redisAllowed()) {
            return;
        }
        for (String prefix : prefixes) {
            try {
                Set<String> keys = redisTemplate.keys(redisKey(prefix) + "*");
                if (!CollectionUtils.isEmpty(keys)) {
                    redisTemplate.delete(keys);
                }
            } catch (RuntimeException ex) {
                markRedisFailure(ex);
                return;
            }
        }
    }

    /**
     * 将任意值转为缓存 Key 的安全片段。
     * null 或空字符串统一映射为 "all"，避免 Key 中出现特殊字符。
     * 中文等非 ASCII 字符通过 Base64 URL 安全编码。
     */
    public String keyPart(Object value) {
        if (value == null) {
            return "all";
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return "all";
        }
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** 缓存是否可用：全局开关启用且已过退避期。 */
    private boolean redisAllowed() {
        return enabled && System.currentTimeMillis() >= retryAtMillis;
    }

    /** 拼接全局前缀，隔离不同应用的缓存命名空间。 */
    private String redisKey(String key) {
        return KEY_PREFIX + key;
    }

    /**
     * Redis 连接失败时记录 retryAtMillis，之后 backoff 期间所有请求跳过缓存直接走数据库。
     * volatile 保证多线程可见性，不需要加锁。
     */
    private void markRedisFailure(RuntimeException ex) {
        retryAtMillis = System.currentTimeMillis() + failureBackoff.toMillis();
        log.debug("Redis unavailable, bypass cache for {} seconds", failureBackoff.toSeconds(), ex);
    }
}
