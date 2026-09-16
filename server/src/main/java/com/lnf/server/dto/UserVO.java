package com.lnf.server.dto;

import lombok.Data;

/**
 * 当前用户信息视图（手机号已脱敏）
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    /** 脱敏后的手机号，如 138****1234 */
    private String phone;
    private String role;
    private Integer creditScore;
}
