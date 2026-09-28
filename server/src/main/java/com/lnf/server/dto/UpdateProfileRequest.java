package com.lnf.server.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求（昵称/手机号）
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 50, message = "昵称最长 50 个字符")
    private String nickname;

    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
}
