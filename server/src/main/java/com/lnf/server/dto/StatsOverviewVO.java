package com.lnf.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 运营数据看板总览
 */
@Data
public class StatsOverviewVO {

    private Long totalItems;
    private Long totalLost;
    private Long totalFound;
    /** CONFIRMED 数 ÷ 已反馈总数（matches 中 status 非 PENDING 的确认率），无反馈时为 0 */
    private Double matchHitRate;
    /** COMPLETED 认领单从申请到核销的平均小时数 */
    private Double avgRecoverHours;
    private Long completedClaims;
    private List<HotLocation> hotLocations;

    @Data
    @AllArgsConstructor
    public static class HotLocation {
        private String locationName;
        private Long count;
    }
}
