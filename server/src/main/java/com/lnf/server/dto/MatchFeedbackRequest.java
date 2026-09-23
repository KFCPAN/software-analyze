package com.lnf.server.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 匹配结果反馈（是它/不是它）
 */
@Data
public class MatchFeedbackRequest {

    @NotNull(message = "confirm 不能为空")
    private Boolean confirm;
}
