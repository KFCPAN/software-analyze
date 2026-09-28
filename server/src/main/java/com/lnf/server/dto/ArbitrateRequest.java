package com.lnf.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 争议仲裁请求
 */
@Data
public class ArbitrateRequest {

    /** true=支持认领人（通过并生成核销码）；false=驳回 */
    @NotNull(message = "approve 不能为空")
    private Boolean approve;

    @NotBlank(message = "仲裁必须填写原因")
    private String reason;
}
