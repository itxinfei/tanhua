package com.xfjy.server.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report extends BasePojo {

    private Long id;
    private Long reporterId; //举报人用户ID
    private Long targetUserId; //被举报用户ID
    private String targetType; //USER/POST/COMMENT/MESSAGE
    private Long targetId;
    private String reason;
    private String description;
    private String evidence; //证据URL，逗号分隔
    private String status; //PENDING/PROCESSING/RESOLVED/DISMISSED
    private String handler;
    private Date handledAt;
    private String handleResult;

}