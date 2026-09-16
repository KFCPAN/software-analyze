package com.lnf.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 分页结果
 */
@Data
@AllArgsConstructor
public class PageResult<T> {

    private Long total;
    private Long page;
    private Long size;
    private List<T> list;
}
