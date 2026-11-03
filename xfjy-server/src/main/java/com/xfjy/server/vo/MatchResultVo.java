package com.xfjy.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 喜欢粉丝的结果：若对方也喜欢我则为配对成功（matched=true），
 * 前端据此弹出「配对成功」庆祝并引导进入聊天。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResultVo {

    /** 是否配对成功（对方也喜欢我） */
    private Boolean matched;

    /** 配对成功的对方简要信息（仅 matched=true 时有值） */
    private MatchedUser user;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchedUser {
        private Long id;
        private String nickname;
        private String avatar;
    }
}
