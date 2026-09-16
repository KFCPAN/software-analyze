package com.lnf.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 地点词表，对应 locations 表（level 1=校区 2=楼栋/区域）
 */
@Data
@TableName("locations")
public class Location {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String name;

    /** 海淀 / 丰台 / 跨校区 */
    private String campus;

    private Integer level;

    private Boolean enabled;
}
