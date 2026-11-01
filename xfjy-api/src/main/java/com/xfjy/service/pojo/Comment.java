package com.xfjy.service.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 评论表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "quanzi_comment")
public class Comment implements java.io.Serializable{

    private static final long serialVersionUID = -291788258125767614L;

    private ObjectId id;

    private ObjectId publishId; //发布id
    private Integer commentType; //评论类型 1-点赞 2-评论 3-喜欢
    private String content; //评论内容
    private Long userId; //评论人
    private Long publishUserId; //发布人的用户id

    private Boolean isParent = false; //是否为父节点，默认是否
    private ObjectId parentId; // 父节点id

    private Long created; //发表时间

    /** 媒体类型：1-文字（默认）2-语音；与 commentType（1点赞/2评论/3喜欢）语义不同 */
    private Integer mediaType;
    /** 语音评论的音频文件地址（mediaType=2 时有值） */
    private String audioUrl;
    /** 语音时长（秒，mediaType=2 时有值） */
    private Integer duration;

}
