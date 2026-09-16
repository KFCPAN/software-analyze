package com.lnf.server.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录用户上下文，认证成功后作为 SecurityContext 的 principal
 */
@Data
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long id;
    private String username;
    private String role;
}
