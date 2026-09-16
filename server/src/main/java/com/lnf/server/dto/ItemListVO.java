package com.lnf.server.dto;

import lombok.Data;

/**
 * 信息流列表项
 */
@Data
public class ItemListVO {

    private Long id;
    private String type;
    private String title;
    private Long categoryId;
    private String categoryName;
    private String locationName;
    /** yyyy-MM-dd HH:mm:ss */
    private String eventTime;
    /** images 第一张，无图时为 null */
    private String coverImage;
    private String status;
    /** yyyy-MM-dd HH:mm:ss */
    private String createdAt;
}
