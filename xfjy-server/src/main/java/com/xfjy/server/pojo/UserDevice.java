package com.xfjy.server.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户设备绑定（极光推送 registrationId）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice extends BasePojo {

    private Long id;
    private Long userId;
    private String registrationId;
    private String platform; // ANDROID / IOS

}