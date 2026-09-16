package com.lnf.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户实体，对应 users 表
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名 */
    private String username;

    /** BCrypt 哈希 */
    private String passwordHash;

    /** 校园邮箱 */
    private String email;

    /** 联系方式 */
    private String phone;

    private String nickname;

    /** USER / REVIEWER / ADMIN */
    private String role;

    /** 信用分，初始 100 */
    private Integer creditScore;

    /** ACTIVE / BANNED */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
