package com.xfjy.service.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "video")
public class Video implements java.io.Serializable {

    private static final long serialVersionUID = -3136732836884933873L;

    private ObjectId id; //主键id
    private Long vid;
    private Long userId;
    private String text; //文字
    private String picUrl; //视频封面文件
    private String videoUrl; //视频文件
    private Long created; //创建时间
    private Integer seeType; // 谁可以看 1-公开 2-私密 3-部分可见 4-不给谁看
    private List<Long> seeList; //部分可见的列表
    private List<Long> notSeeList; //不给谁看的列表
    private String longitude; //经度（历史字段，客户端上传的字符串坐标）
    private String latitude; //纬度（历史字段，客户端上传的字符串坐标）
    private String locationName; //位置名称（可为空：H5 端 uni.getLocation 不支持逆地理，拿不到城市名属正常路径）

    /**
     * 地理坐标，坐标顺序固定为 [经度, 纬度]（GeoJSON 规范），用于「附近」按 2dsphere 距离查询。
     * 旧数据没有该字段，查询侧必须回退全站热门而不是返回空列表。
     */
    private GeoJsonPoint geo;

    /**
     * 由 $geoNear 聚合回填的距离（单位：米），仅查询结果里有值，不参与入库业务。
     */
    private Double distance;

    private Integer likeCount; //点赞数（点赞 +1 / 取消赞 -1，不允许出现负数）
    private Integer commentCount; //评论数（评论写入 +1，评论删除/隐藏 -1）
    private Integer playCount; //播放数（同一用户对同一视频仅计一次，由上报接口去重）

    /** 软删除标记：true=已删除（用户不可见，数据保留可恢复）；旧数据无此字段按未删除处理 */
    private Boolean deleted = false;
}
