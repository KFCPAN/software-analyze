package com.lnf.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnf.server.entity.Item;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {
}
