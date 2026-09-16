package com.lnf.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 信用分流水，对应 credit_logs 表
 */
@Data
@TableName("credit_logs")
public class CreditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 正为加分、负为扣分 */
    private Integer delta;

    private String reason;

    private Long claimId;

    private OffsetDateTime createdAt;
}
