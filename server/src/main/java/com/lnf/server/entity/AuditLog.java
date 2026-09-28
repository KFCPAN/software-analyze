package com.lnf.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 后台操作审计，对应 audit_logs 表
 */
@Data
@TableName("audit_logs")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人（审核员/管理员） */
    private Long operatorId;

    /** BAN_USER / UNBAN_USER / ARBITRATE / CLOSE_ITEM 等 */
    private String action;

    private String targetType;

    private Long targetId;

    /** JSONB 字符串 */
    private String detail;

    private OffsetDateTime createdAt;
}
