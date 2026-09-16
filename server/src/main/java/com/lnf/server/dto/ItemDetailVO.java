package com.lnf.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 信息详情。hiddenFeatures 不返回；contactVisible 标识联系方式是否可见
 */
@Data
public class ItemDetailVO {

    private Long id;
    private String type;
    private String title;
    private Long categoryId;
    private String categoryName;
    private Long locationId;
    private String locationName;
    /** yyyy-MM-dd HH:mm:ss */
    private String eventTime;
    private String description;
    private List<String> images;
    private String status;
    private Publisher publisher;
    private Boolean contactVisible;
    /** yyyy-MM-dd HH:mm:ss */
    private String createdAt;

    /**
     * 发布者信息；contactVisible=false 时 phone/email 为 null 且不出现在 JSON 中
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Publisher {
        private Long id;
        private String nickname;
        private Integer creditScore;
        private String phone;
        private String email;
    }
}
