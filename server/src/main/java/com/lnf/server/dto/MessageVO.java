package com.lnf.server.dto;

import lombok.Data;

/**
 * 站内消息视图
 */
@Data
public class MessageVO {

    private Long id;
    /** MATCH_HIT / CLAIM_PROGRESS / REVIEW_RESULT / SYSTEM */
    private String type;
    private String title;
    private String content;
    private Long relatedId;
    private Boolean isRead;
    /** yyyy-MM-dd HH:mm:ss */
    private String createdAt;
}
