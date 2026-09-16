package com.lnf.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 扫码核销请求
 */
@Data
public class VerifyCodeRequest {

    @NotBlank(message = "核销码不能为空")
    private String verifyCode;
}
