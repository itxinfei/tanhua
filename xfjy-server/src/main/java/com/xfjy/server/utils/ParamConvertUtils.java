package com.xfjy.server.utils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 请求参数转换工具
 *
 * Jackson 反序列化 @RequestBody Map 时，JSON 整数数组默认为 List<Integer>，
 * 直接强转 (List<Long>) 会抛 ClassCastException。统一走本工具做元素级转换。
 */
public final class ParamConvertUtils {

    private ParamConvertUtils() {
    }

    /**
     * 将 Map 中取出的数组值安全转换为 List<Long>。
     * 兼容 Integer/Long/String 等 Number 子类及 null 值。
     *
     * @param raw Map.get(...) 的原始值，预期为 List
     * @return 转换后的 List<Long>；入参为 null 时返回空 List
     */
    @SuppressWarnings("unchecked")
    public static List<Long> toLongList(Object raw) {
        if (!(raw instanceof List<?>)) {
            return List.of();
        }
        return ((List<Object>) raw).stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> {
                    if (item instanceof Number) {
                        return ((Number) item).longValue();
                    }
                    return Long.valueOf(item.toString());
                })
                .collect(Collectors.toList());
    }

    /**
     * 分页大小防御：null/<=0 给默认值，超过上限截断。
     * 防止恶意传入超大 pageSize 导致全表拉取 OOM。
     *
     * @param pageSize     原始分页大小
     * @param defaultSize  默认值（pageSize 无效时使用）
     * @param maxSize      允许的最大值
     * @return 校验后的分页大小
     */
    public static int capPageSize(Integer pageSize, int defaultSize, int maxSize) {
        if (pageSize == null || pageSize <= 0) {
            return defaultSize;
        }
        return Math.min(pageSize, maxSize);
    }
}