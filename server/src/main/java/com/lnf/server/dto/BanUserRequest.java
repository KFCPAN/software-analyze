package com.lnf.server.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 封禁/解封用户请求
 */
@Data
public class BanUserRequest {

    @NotNull(message = "ban 不能为空")
    private Boolean ban;

    @Size(max = 255, message = "原因最长 255 个字符")
    private String reason;
}
