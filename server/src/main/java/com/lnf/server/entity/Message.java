package com.lnf.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 站内消息，对应 messages 表
 */
@Data
@TableName("messages")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** MATCH_HIT / CLAIM_PROGRESS / REVIEW_RESULT / SYSTEM */
    private String type;

    private String title;

    private String content;

    /** 关联业务 id（match_id 或 claim_id） */
    private Long relatedId;

    private Boolean isRead;

    private OffsetDateTime createdAt;
}
