package com.lnf.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 物品分类视图
 */
@Data
@AllArgsConstructor
public class CategoryVO {

    private Long id;
    private String name;
}
