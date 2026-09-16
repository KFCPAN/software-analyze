package com.lnf.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 交接核销码视图
 */
@Data
@AllArgsConstructor
public class ClaimCodeVO {

    private String verifyCode;
    /** yyyy-MM-dd HH:mm:ss */
    private String expiresAt;
}
