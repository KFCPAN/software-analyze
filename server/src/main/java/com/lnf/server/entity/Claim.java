package com.lnf.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 认领单，对应 claims 表（状态机驱动）
 * PENDING(待核验) / APPROVED(核验通过) / REJECTED(驳回)
 * / DISPUTED(争议仲裁中) / COMPLETED(交接完成) / EXPIRED(超时关闭)
 */
@Data
@TableName(value = "claims", autoResultMap = true)
public class Claim {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被认领的招领信息 */
    private Long foundItemId;

    /** 认领人（失主） */
    private Long claimantId;

    /** 隐藏特征作答记录（JSONB：[{featureKey, answer, matched}]） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<FeatureAnswer> featureAnswers;

    private String status;

    /** 一次性交接核销码（APPROVED 时生成，7 天有效） */
    private String verifyCode;

    /** 核验/仲裁人 */
    private Long reviewedBy;

    private String rejectReason;

    /** 扫码核销时间 */
    private OffsetDateTime completedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /**
     * 单条特征作答记录，matched 为与登记特征的比对结果（供拾获者参考，不自动通过）
     */
    @Data
    public static class FeatureAnswer {
        private String featureKey;
        private String answer;
        private Boolean matched;
    }
}
