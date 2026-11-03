package com.xfjy.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLikeListVo {

    private Long id;
    private String avatar;
    private String nickname;
    private String gender;
    private Integer age;
    private String city;
    private String education;
    private Integer marriage; //婚姻状态（0未婚 1已婚
    private Integer matchRate; //匹配度
    private Long visitTime; //来访时间（仅 type=4 谁看过我时有值，毫秒时间戳）

}
