package com.lnf.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 失物/招领信息，对应 items 表
 * images 为 JSONB 列，通过 JacksonTypeHandler 映射为 List<String>
 * （数据源 URL 已加 stringtype=unspecified 以兼容 jsonb 写入）
 */
@Data
@TableName(value = "items", autoResultMap = true)
public class Item {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** LOST（失物）/ FOUND（招领） */
    private String type;

    private String title;

    private Long categoryId;

    private Long locationId;

    /** 丢失/拾获发生时间 */
    private OffsetDateTime eventTime;

    private String description;

    /** 图片路径数组（JSONB） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /** OPEN / MATCHED / CLAIMING / CLOSED / ARCHIVED */
    private String status;

    // TODO(第6周匹配引擎)：text_vector / image_vector 由匹配引擎异步写入，本阶段不映射

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
