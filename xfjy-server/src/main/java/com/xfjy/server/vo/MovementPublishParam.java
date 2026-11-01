package com.xfjy.server.vo;

import lombok.Data;

@Data
public class MovementPublishParam {
    private String textContent;
    private String location;
    private String longitude;
    private String latitude;
    private String tags;
}
