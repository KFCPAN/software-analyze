package com.lnf.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 核验认领申请请求
 */
@Data
public class ClaimReviewRequest {

    @NotBlank(message = "action 不能为空")
    @Pattern(regexp = "APPROVE|REJECT|DISPUTE", message = "action 只能是 APPROVE / REJECT / DISPUTE")
    private String action;

    /** 驳回或升级仲裁时必填 */
    private String reason;
}
