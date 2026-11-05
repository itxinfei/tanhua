package com.xfjy.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoVo {

    private String id;
    private Long userId;
    private String avatar; //头像
    private String nickname; //昵称
    private String cover; //封面
    private String videoUrl; //视频URL
    private String signature; //签名
    private Integer likeCount; //点赞数量
    private Integer hasLiked; //是否已赞（1是，0否）
    private Integer hasFocus; //是否关注（1是，0否）
    private Integer commentCount; //评论数量
    /** 位置名称（城市/区域，可为空：H5 端拿不到逆地理结果属正常路径） */
    private String locationName;
    /** 距当前用户的距离（米），仅「附近」查询有值；前端用它展示「1.2km」距离标签 */
    private Double distance;
}
