package com.lnf.server.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

/**
 * 智能匹配参数配置（前端 settings.vue 契约：w1 文本 / w2 图像 / w3 时间 / w4 地点 权重 + threshold）
 */
@Data
public class MatchConfigVO {

    /** 文本相似度权重 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double w1;

    /** 图像相似度权重 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double w2;

    /** 时间接近度权重 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double w3;

    /** 地点接近度权重 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double w4;

    /** 匹配触发阈值 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double threshold;
}
