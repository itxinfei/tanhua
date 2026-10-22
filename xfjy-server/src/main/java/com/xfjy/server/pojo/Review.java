package com.xfjy.server.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review extends BasePojo {

    private Long id;
    private String type; //POST/PHOTO/VIDEO
    private Long userId; //发布者用户ID
    private String content;
    private String medias; //媒体URL，逗号分隔
    private String status; //PENDING/APPROVED/REJECTED
    private String rejectReason;
    private String reviewer;
    private Date reviewedAt;

}