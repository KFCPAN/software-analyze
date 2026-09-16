package com.lnf.server.dto;

import lombok.Data;

import java.util.List;

/**
 * 地点视图（两级树：校区 → 楼栋/区域）
 */
@Data
public class LocationVO {

    private Long id;
    private String name;
    private String campus;
    private Integer level;
    private List<LocationVO> children;
}
