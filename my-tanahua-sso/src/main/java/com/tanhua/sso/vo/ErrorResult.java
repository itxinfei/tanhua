package com.tanhua.sso.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 编写ErrorResult
 */
@Data
@Builder
public class ErrorResult {
    private String errCode;
    private String errMessage;
}
