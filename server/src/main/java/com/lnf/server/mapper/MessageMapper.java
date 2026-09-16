package com.lnf.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnf.server.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
