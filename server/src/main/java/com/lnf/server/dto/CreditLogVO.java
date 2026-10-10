package com.lnf.server.dto;

import lombok.Data;

/**
 * 信用分流水记录
 */
@Data
public class CreditLogVO {

    private Long id;

    /** 正为加分、负为扣分 */
    private Integer delta;

    private String reason;

    /** 关联认领单 id，可能为 null */
    private Long claimId;

    /** yyyy-MM-dd HH:mm:ss */
    private String createdAt;
}
