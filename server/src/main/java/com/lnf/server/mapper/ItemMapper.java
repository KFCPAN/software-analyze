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
     * 写回图像向量（CLIP，512 维）
     */
    @Update("UPDATE items SET image_vector = #{vector}::vector, updated_at = now() WHERE id = #{id}")
    int updateImageVector(@Param("id") Long id, @Param("vector") String vector);

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

    /**
     * 招领候选图像粗筛（给带图的新 LOST 用）：image_vector 余弦距离最近的 20 条 id
     */
    @Select("SELECT id FROM items WHERE type = 'FOUND' AND status = 'OPEN' AND image_vector IS NOT NULL "
            + "AND id != #{excludeId} AND event_time >= #{minEventTime} "
            + "ORDER BY image_vector <=> #{vector}::vector LIMIT 20")
    List<Long> findFoundCandidateIdsByImage(@Param("vector") String vector,
                                            @Param("excludeId") Long excludeId,
                                            @Param("minEventTime") OffsetDateTime minEventTime);

    /**
     * 失物候选图像粗筛（给带图的新 FOUND 用）
     */
    @Select("SELECT id FROM items WHERE type = 'LOST' AND status = 'OPEN' AND image_vector IS NOT NULL "
            + "AND id != #{excludeId} AND event_time <= #{maxEventTime} "
            + "ORDER BY image_vector <=> #{vector}::vector LIMIT 20")
    List<Long> findLostCandidateIdsByImage(@Param("vector") String vector,
                                           @Param("excludeId") Long excludeId,
                                           @Param("maxEventTime") OffsetDateTime maxEventTime);

    /**
     * 批量取候选的打分因子输入：text_score 在 SQL 里算（无文本向量时为 NULL），
     * image_vector 以文本形式带回 Java 侧算点积（避免 NULL 向量参数的 SQL 体操）
     */
    @Select("<script>"
            + "SELECT id, event_time, location_id, "
            + "CASE WHEN text_vector IS NOT NULL AND #{textVector,jdbcType=VARCHAR}::text IS NOT NULL "
            + "THEN 1 - (text_vector &lt;=&gt; #{textVector,jdbcType=VARCHAR}::vector) END AS text_score, "
            + "image_vector::text AS image_vector_text "
            + "FROM items WHERE id IN "
            + "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>"
            + "</script>")
    List<Map<String, Object>> selectFactorInputs(@Param("ids") List<Long> ids,
                                                 @Param("textVector") String textVector);

    /**
     * 热点地点 Top5（看板用）
     */
    @Select("SELECT l.name AS location_name, COUNT(*) AS cnt FROM items i "
            + "JOIN locations l ON l.id = i.location_id "
            + "WHERE i.location_id IS NOT NULL GROUP BY l.name ORDER BY cnt DESC LIMIT 5")
    List<Map<String, Object>> selectHotLocations();
}
