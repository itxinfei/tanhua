package com.xfjy.server.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notice extends BasePojo {

    private Long id;
    private String title;
    private String content;
    private String type; //SYSTEM/ACTIVITY/UPDATE
    private String status; //DRAFT/PUBLISHED/OFFLINE
    private String publisher;
    private Date publishedAt;

}