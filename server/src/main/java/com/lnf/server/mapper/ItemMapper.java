package com.lnf.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnf.server.entity.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {

    /**
     * 写回文本向量（pgvector，"["0.1,0.2,..."]" 字符串 + ::vector 强转）
     */
    @Update("UPDATE items SET text_vector = #{vector}::vector, updated_at = now() WHERE id = #{id}")
    int updateTextVector(@Param("id") Long id, @Param("vector") String vector);

    /**
     * 招领候选粗筛（给新 LOST 用）：余弦距离最近的 20 条。
     * 时间约束：FOUND.event_time >= LOST.event_time（先丢后捡）。
     */
    @Select("SELECT id, event_time, location_id, 1 - (text_vector <=> #{vector}::vector) AS text_score "
            + "FROM items WHERE type = 'FOUND' AND status = 'OPEN' AND text_vector IS NOT NULL "
            + "AND id != #{excludeId} AND event_time >= #{minEventTime} "
            + "ORDER BY text_vector <=> #{vector}::vector LIMIT 20")
    List<Map<String, Object>> findFoundCandidates(@Param("vector") String vector,
                                                  @Param("excludeId") Long excludeId,
                                                  @Param("minEventTime") OffsetDateTime minEventTime);

    /**
     * 失物候选粗筛（给新 FOUND 用）：LOST.event_time <= FOUND.event_time。
     */
    @Select("SELECT id, event_time, location_id, 1 - (text_vector <=> #{vector}::vector) AS text_score "
            + "FROM items WHERE type = 'LOST' AND status = 'OPEN' AND text_vector IS NOT NULL "
            + "AND id != #{excludeId} AND event_time <= #{maxEventTime} "
            + "ORDER BY text_vector <=> #{vector}::vector LIMIT 20")
    List<Map<String, Object>> findLostCandidates(@Param("vector") String vector,
                                                 @Param("excludeId") Long excludeId,
                                                 @Param("maxEventTime") OffsetDateTime maxEventTime);
}
