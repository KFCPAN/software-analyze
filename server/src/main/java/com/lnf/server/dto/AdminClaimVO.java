package com.lnf.server.dto;

import com.lnf.server.entity.Claim;
import lombok.Data;

import java.util.List;

/**
 * 后台争议认领单视图（含双方信息与作答明细）
 */
@Data
public class AdminClaimVO {

    private Long id;
    private ItemBrief foundItem;
    private Party claimant;
    private Party publisher;
    /** 特征作答明细（含 matched 标记） */
    private List<Claim.FeatureAnswer> featureAnswers;
    /** 争议原因（升级仲裁时填写的 reason） */
    private String disputeReason;
    private String status;
    private String createdAt;

    @Data
    public static class ItemBrief {
        private Long id;
        private String title;
    }

    @Data
    public static class Party {
        private Long id;
        private String username;
        private String nickname;
        private Integer creditScore;
    }
}
