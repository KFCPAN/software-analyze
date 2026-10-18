package com.lnf.server.dto;

import lombok.Data;

/**
 * 内容审核列表行（前端 audit.vue 契约：扁平字段 + 发布者字符串 + 风险等级）
 */
@Data
public class AdminAuditItemVO {

    private Long id;

    /** lost / found（小写，前端按小写判断标签颜色） */
    private String type;

    private String title;

    /** 发布者用户名（扁平展示用） */
    private String publisher;

    /** high / medium / low */
    private String riskLevel;

    private String createTime;

    private String description;

    /** 风险原因说明 */
    private String riskReason;
}
