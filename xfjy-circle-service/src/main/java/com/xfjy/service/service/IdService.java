package com.xfjy.service.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 原理：使用Redis的自增长类型，实现自增长的id。
 * 通过 Lua 脚本保证「查重→自增→写入」原子性，避免并发下同一 objectId 分配不同 id。
 */
@Service
public class IdService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * Lua 脚本：objectId 已存在则返回已有 id，否则 INCR 生成新 id 并写入 hash。
     * Redis 执行 Lua 脚本是原子的，彻底消除竞态窗口。
     */
    private static final DefaultRedisScript<Long> CREATE_ID_SCRIPT;
    static {
        String lua = ""
                + "local existing = redis.call('HGET', KEYS[1], ARGV[1]) "
                + "if existing then return existing end "
                + "local newId = redis.call('INCR', KEYS[2]) "
                + "redis.call('HSET', KEYS[1], ARGV[1], newId) "
                + "return newId";
        CREATE_ID_SCRIPT = new DefaultRedisScript<>(lua, Long.class);
    }

    /**
     * 生成自增长的id（原子操作，并发安全）
     *
     * @param type     业务类型（如 QUANZI）
     * @param objectId 业务对象标识（如 Mongo ObjectId）
     * @return 分配的自增 id
     */
    public Long createId(String type, String objectId) {
        type = StringUtils.upperCase(type);
        String hashKey = "XFJY_HASH_ID_" + type;
        String idKey = "XFJY_ID_" + type;
        List<String> keys = List.of(hashKey, idKey);
        Long result = this.redisTemplate.execute(CREATE_ID_SCRIPT, keys, objectId);
        return result != null ? result : 0L;
    }
}
