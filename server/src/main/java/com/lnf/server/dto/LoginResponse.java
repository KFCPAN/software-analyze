package com.lnf.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应：token + 用户概要
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private UserBrief user;

    @Data
    @AllArgsConstructor
    public static class UserBrief {
        private Long id;
        private String username;
        private String nickname;
        private String role;
        private Integer creditScore;
    }
}
