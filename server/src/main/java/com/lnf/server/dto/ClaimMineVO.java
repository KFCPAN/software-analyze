package com.lnf.server.dto;

import lombok.Data;

/**
 * 我提交的认领申请列表项
 */
@Data
public class ClaimMineVO {

    private Long id;
    private Long foundItemId;
    private String itemTitle;
    private String status;
    private String rejectReason;
    /** yyyy-MM-dd HH:mm:ss */
    private String createdAt;
}
