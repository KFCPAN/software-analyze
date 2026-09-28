package com.lnf.server.dto;

import lombok.Data;

/**
 * 后台信息审核列表项（含发布者信息）
 */
@Data
public class AdminItemVO {

    private Long id;
    private String type;
    private String title;
    private Long categoryId;
    private String categoryName;
    private String locationName;
    private String eventTime;
    private String status;
    private String createdAt;
    private Publisher publisher;

    @Data
    public static class Publisher {
        private Long id;
        private String username;
        private String nickname;
        private Integer creditScore;
        private String status;
    }
}
