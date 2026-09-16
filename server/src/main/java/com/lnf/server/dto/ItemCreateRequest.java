package com.lnf.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布/编辑失物招领信息请求
 */
@Data
public class ItemCreateRequest {

    @NotBlank(message = "类型不能为空")
    @Pattern(regexp = "LOST|FOUND", message = "类型只能是 LOST 或 FOUND")
    private String type;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长 100 个字符")
    private String title;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    /** 记不清可不传 */
    private Long locationId;

    /** 丢失/拾获时间，格式 yyyy-MM-dd HH:mm:ss */
    @NotBlank(message = "发生时间不能为空")
    private String eventTime;

    @NotBlank(message = "描述不能为空")
    private String description;

    /** 先调 /api/files 上传得到的路径 */
    private List<@NotBlank(message = "图片路径不能为空") String> images;

    /** 仅 FOUND 类型必填：防冒领的隐藏特征，不公开展示 */
    @Valid
    private List<HiddenFeature> hiddenFeatures;

    @Data
    public static class HiddenFeature {
        @NotBlank(message = "特征问题不能为空")
        @Size(max = 100, message = "特征问题最长 100 个字符")
        private String featureKey;

        @NotBlank(message = "特征答案不能为空")
        private String answer;
    }
}
