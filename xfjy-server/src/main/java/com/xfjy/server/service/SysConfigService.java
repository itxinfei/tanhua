package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.server.mapper.SysConfigMapper;
import com.xfjy.server.pojo.SysConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 通用系统配置服务
 *
 * 读路径：Redis 缓存（30s TTL）→ MySQL → 回填缓存
 * 写路径：MySQL upsert → 删除缓存（下次读自动回填）
 *
 * 改配置约 30 秒内全节点生效，无需重启服务
 */
@Slf4j
@Service
public class SysConfigService {

    /** Redis 缓存 key 前缀 */
    private static final String CACHE_PREFIX = "xfjy:sysconfig:";

    /** 缓存有效期 30 秒 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 读取配置值（带 30s Redis 缓存）
     *
     * @param key 配置键
     * @param defaultValue 配置不存在时返回的默认值
     */
    public String getConfig(String key, String defaultValue) {
        String cacheKey = CACHE_PREFIX + key;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("[系统配置] Redis读取失败，降级直查DB: key={}, err={}", key, e.getMessage());
        }

        QueryWrapper<SysConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("config_key", key);
        SysConfig config = sysConfigMapper.selectOne(wrapper);

        String value = (config != null && config.getConfigValue() != null)
                ? config.getConfigValue() : defaultValue;

        try {
            stringRedisTemplate.opsForValue().set(cacheKey, value, CACHE_TTL);
        } catch (Exception e) {
            log.warn("[系统配置] Redis回填失败: key={}, err={}", key, e.getMessage());
        }
        return value;
    }

    /**
     * 写入配置（存在则更新，不存在则插入），写后清缓存
     *
     * @param key 配置键
     * @param value 配置值
     * @param description 配置说明（仅首次插入生效）
     */
    public void setConfig(String key, String value, String description) {
        QueryWrapper<SysConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("config_key", key);
        SysConfig existing = sysConfigMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setConfigValue(value);
            sysConfigMapper.updateById(existing);
        } else {
            SysConfig config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description);
            sysConfigMapper.insert(config);
        }

        try {
            stringRedisTemplate.delete(CACHE_PREFIX + key);
        } catch (Exception e) {
            log.warn("[系统配置] Redis清除失败: key={}, err={}", key, e.getMessage());
        }
        log.info("[系统配置] 配置已更新: {} = {}", key, value);
    }

    /**
     * 列出全部配置（后台管理页使用）
     */
    public List<SysConfig> listAll() {
        return sysConfigMapper.selectList(null);
    }
}