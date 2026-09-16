package com.lnf.server.dto;

import com.lnf.server.entity.Claim;
import lombok.Data;

import java.util.List;

/**
 * 待我核验的认领单（拾获者视角）
 */
@Data
public class ClaimTodoVO {

    private Long id;
    private Long foundItemId;
    private String itemTitle;
    private String status;
    private Claimant claimant;
    /** 特征作答明细（含 matched 标记） */
    private List<Claim.FeatureAnswer> featureAnswers;
    /** yyyy-MM-dd HH:mm:ss */
    private String createdAt;

    @Data
    public static class Claimant {
        private Long id;
        private String nickname;
        private Integer creditScore;
    }
}
