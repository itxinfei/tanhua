package com.xfjy.server.vo;

import lombok.Data;

/**
 * C 端地图服务配置（只下发前端可见字段，服务端 Web 服务 Key 绝不下发）
 */
@Data
public class MapConfigVo {

    /** 是否启用地图能力：1=启用，0=关闭 */
    private String enabled;

    /** 地图服务商，当前仅 amap（高德） */
    private String provider;

    /** 前端 JS API Key（本身即公开信息，用于 H5/小程序地图组件） */
    private String jsKey;

    /** JS API 2.0 安全密钥（公网可见，需与 JS Key 配套） */
    private String securityJsCode;
}
