package com.lnf.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 智能匹配候选记录，对应 matches 表（一条失物 × 一条招领及各因子得分）
 */
@Data
@TableName("matches")
public class Match {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lostItemId;

    private Long foundItemId;

    /** 文本相似度 0~1 */
    private BigDecimal textScore;

    /** 图像相似度 0~1（CLIP 接入前为 null） */
    private BigDecimal imageScore;

    /** 时间接近度 0~1 */
    private BigDecimal timeScore;

    /** 地点接近度 0~1 */
    private BigDecimal locationScore;

    /** 加权综合分 */
    private BigDecimal totalScore;

    /** PENDING / CONFIRMED / REJECTED */
    private String status;

    /** 确认/否认的用户（负反馈回流） */
    private Long feedbackBy;

    private OffsetDateTime createdAt;
}
