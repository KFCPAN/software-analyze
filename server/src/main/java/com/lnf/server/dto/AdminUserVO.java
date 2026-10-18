package com.lnf.server.dto;

import lombok.Data;

/**
 * 后台用户列表行（前端 users.vue 契约：status 为 banned / normal）
 */
@Data
public class AdminUserVO {

    private Long id;

    private String username;

    private String email;

    /** 信用分 */
    private Integer credit;

    /** banned / normal */
    private String status;

    /** 注册时间（yyyy-MM-dd） */
    private String registerTime;
}
