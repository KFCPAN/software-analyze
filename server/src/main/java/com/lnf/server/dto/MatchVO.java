package com.lnf.server.dto;

import lombok.Data;

/**
 * 匹配候选视图（对方 item 摘要 + 各因子得分）
 */
@Data
public class MatchVO {

    private Long matchId;
    private CandidateItem item;
    private Double textScore;
    /** CLIP 接入前恒为 null */
    private Double imageScore;
    private Double timeScore;
    private Double locationScore;
    private Double totalScore;
    private String status;

    @Data
    public static class CandidateItem {
        private Long id;
        private String title;
        private String coverImage;
        private String locationName;
        /** yyyy-MM-dd HH:mm:ss */
        private String eventTime;
    }
}
