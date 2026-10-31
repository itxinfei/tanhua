package com.xfjy.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HuanXinUser {

    private String username;
    private String token;

    /** IM 通道标识：wukongim（默认开源）/ huanxin（环信），前端据此选择对应 SDK */
    private String provider;
}
