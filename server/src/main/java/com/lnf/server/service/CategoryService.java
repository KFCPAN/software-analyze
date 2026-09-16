package com.lnf.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lnf.server.dto.CategoryVO;
import com.lnf.server.entity.Category;
import com.lnf.server.mapper.CategoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 物品分类服务
 */
@Service
public class CategoryService extends ServiceImpl<CategoryMapper, Category> {

    /**
     * 启用的分类列表（按 sort 升序）
     */
    public List<CategoryVO> listEnabled() {
        List<Category> categories = list(new LambdaQueryWrapper<Category>()
                .eq(Category::getEnabled, true)
                .orderByAsc(Category::getSort));
        return categories.stream()
                .map(c -> new CategoryVO(c.getId(), c.getName()))
                .toList();
    }

    /**
     * 分类是否存在且启用
     */
    public boolean existsEnabled(Long categoryId) {
        return categoryId != null && count(new LambdaQueryWrapper<Category>()
                .eq(Category::getId, categoryId)
                .eq(Category::getEnabled, true)) > 0;
    }
}
