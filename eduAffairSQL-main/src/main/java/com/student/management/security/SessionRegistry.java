package com.student.management.security;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.student.management.common.RedisCacheService;
import org.springframework.stereotype.Component;

/**
 * 会话注册中心，管理登录用户的 token。
 *
 * 双层存储设计：
 * 1. ConcurrentHashMap（内存）：快速读写，进程重启丢失
 * 2. Redis（分布式缓存）：支持多实例部署，进程重启后可恢复
 *
 * 查找时优先 Redis，命中后将数据回写到本地 Map 并刷新过期时间（8 小时滑动过期）。
 * Token 由两个 UUID（去掉连字符）拼接而成，长度 64 字符，不可猜测。
 */
@Component
public class SessionRegistry {
    private static final long TTL_SECONDS = 8 * 60 * 60;
    private static final TypeReference<SessionUser> SESSION_USER_TYPE = new TypeReference<>() {
    };
    private final ConcurrentHashMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final RedisCacheService cache;

    public SessionRegistry(RedisCacheService cache) {
        this.cache = cache;
    }

    /**
     * 创建会话：生成 64 字符随机 token，同时写入内存 Map 和 Redis。
     * 返回 token 供前端存储并在后续请求中通过 Authorization 头传递。
     */
    public String create(SessionUser user) {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new SessionEntry(user, Instant.now().plusSeconds(TTL_SECONDS)));
        cache.put(sessionKey(token), user, Duration.ofSeconds(TTL_SECONDS));
        return token;
    }

    /**
     * 查找并刷新会话：优先 Redis → 回退本地 Map。
     * 命中后自动续期（滑动过期 8 小时），过期则返回 empty。
     */
    public Optional<SessionUser> find(String token) {
        SessionUser redisUser = cache.getValue(sessionKey(token), SESSION_USER_TYPE);
        if (redisUser != null) {
            sessions.put(token, new SessionEntry(redisUser, Instant.now().plusSeconds(TTL_SECONDS)));
            cache.put(sessionKey(token), redisUser, Duration.ofSeconds(TTL_SECONDS));
            return Optional.of(redisUser);
        }
        SessionEntry entry = sessions.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        sessions.put(token, new SessionEntry(entry.user(), Instant.now().plusSeconds(TTL_SECONDS)));
        cache.put(sessionKey(token), entry.user(), Duration.ofSeconds(TTL_SECONDS));
        return Optional.of(entry.user());
    }

    /** 删除会话：同时清除内存和 Redis 中的记录。 */
    public void remove(String token) {
        sessions.remove(token);
        cache.evict(sessionKey(token));
    }

    /** 内存中的会话条目：用户信息 + 过期时间。 */
    private record SessionEntry(SessionUser user, Instant expiresAt) {
    }

    /** 会话缓存 Key，格式：session:<token>。 */
    private String sessionKey(String token) {
        return "session:" + token;
    }
}
