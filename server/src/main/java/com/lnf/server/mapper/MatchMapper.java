package com.lnf.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnf.server.entity.Match;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface MatchMapper extends BaseMapper<Match> {

    /**
     * 写入匹配记录；唯一约束 (lost_item_id, found_item_id) 冲突则忽略
     *
     * @return 1=新插入，0=已存在被忽略
     */
    @Insert("INSERT INTO matches (lost_item_id, found_item_id, text_score, image_score, "
            + "time_score, location_score, total_score, status, created_at) "
            + "VALUES (#{lostItemId}, #{foundItemId}, #{textScore}, NULL, "
            + "#{timeScore}, #{locationScore}, #{totalScore}, 'PENDING', now()) "
            + "ON CONFLICT (lost_item_id, found_item_id) DO NOTHING")
    int insertIgnore(@Param("lostItemId") Long lostItemId, @Param("foundItemId") Long foundItemId,
                     @Param("textScore") BigDecimal textScore, @Param("timeScore") BigDecimal timeScore,
                     @Param("locationScore") BigDecimal locationScore, @Param("totalScore") BigDecimal totalScore);

    /**
     * 按唯一配对查询匹配记录 id
     */
    @Select("SELECT id FROM matches WHERE lost_item_id = #{lostItemId} AND found_item_id = #{foundItemId}")
    Long selectIdByPair(@Param("lostItemId") Long lostItemId, @Param("foundItemId") Long foundItemId);
}
