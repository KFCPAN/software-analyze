package com.lnf.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 认领单，对应 claims 表。
 * 本阶段仅用于详情接口判断联系方式可见性，认领流程后续周次实现。
 */
@Data
@TableName("claims")
public class Claim {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long foundItemId;

    private Long claimantId;

    /** PENDING / APPROVED / REJECTED / DISPUTED / COMPLETED / EXPIRED */
    private String status;

    private String verifyCode;

    private Long reviewedBy;

    private String rejectReason;

    private OffsetDateTime completedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
