package com.xfjy.service.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 性别枚举
 * DB 存储 getValue(): 1-男 2-女 3-未知
 * HTTP 传输 Jackson 默认序列化为 name(): "MAN"/"WOMAN"/"UNKNOWN"
 */
public enum SexEnum implements IEnum<Integer> {

    MAN(1, "男"),
    WOMAN(2, "女"),
    UNKNOWN(3, "未知");

    private final int value;
    private final String desc;

    SexEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Override
    public Integer getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return this.desc;
    }
}