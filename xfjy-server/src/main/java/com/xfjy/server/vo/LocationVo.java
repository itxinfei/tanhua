package com.xfjy.server.vo;

import lombok.Data;

/**
 * 定位/逆地理结果（经纬度 + 可读地址名）
 */
@Data
public class LocationVo {

    /** 经度（gcj02） */
    private Double longitude;

    /** 纬度（gcj02） */
    private Double latitude;

    /** 展示用短名称，如「上海市浦东新区」 */
    private String name;

    /** 完整地址，如「上海市浦东新区张江镇xx路xx号」 */
    private String address;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区/县 */
    private String district;

    /** 行政区划编码 */
    private String adcode;

    /** 定位来源：gps=设备定位，ip=IP 兜底定位 */
    private String source;
}
