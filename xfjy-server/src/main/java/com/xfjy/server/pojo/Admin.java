package com.xfjy.server.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Admin extends BasePojo {

    private Long id;
    private String username; //登录账号

    @JsonIgnore
    private String password; //密码（MD5），json序列化时忽略

    private String nickname; //昵称
    private String avatar; //头像
    private String role; //角色：SUPER_ADMIN/ADMIN/OPERATOR
    private String status; //状态：NORMAL/DISABLED

}