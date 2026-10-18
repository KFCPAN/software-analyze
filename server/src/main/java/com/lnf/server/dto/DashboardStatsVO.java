package com.lnf.server.dto;

import lombok.Data;

import java.util.List;

/**
 * 管理后台数据看板（前端 dashboard.vue 契约：data.stats + data.locations）
 */
@Data
public class DashboardStatsVO {

    private DashboardStat stats;

    private List<LocationCount> locations;

    @Data
    public static class DashboardStat {
        /** 总发布量 */
        private long totalPosts;
        /** 已找回/认领 */
        private long resolvedCount;
        /** 匹配命中率（百分比整数） */
        private long matchRate;
        /** 平均找回时长（小时） */
        private double avgTime;
    }

    @Data
    public static class LocationCount {
        private String name;
        private long count;

        public LocationCount(String name, long count) {
            this.name = name;
            this.count = count;
        }
    }
}
