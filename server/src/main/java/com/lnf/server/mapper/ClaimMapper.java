package com.lnf.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnf.server.entity.Claim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClaimMapper extends BaseMapper<Claim> {

    /**
     * COMPLETED 认领单从申请到核销的平均小时数（看板用）
     */
    @Select("SELECT AVG(EXTRACT(EPOCH FROM (completed_at - created_at)) / 3600.0) "
            + "FROM claims WHERE status = 'COMPLETED'")
    Double avgRecoverHours();
}
