package com.lnf.server.controller;

import com.lnf.server.common.BizException;
import com.lnf.server.common.Result;
import com.lnf.server.dto.ClaimCodeVO;
import com.lnf.server.dto.ClaimCreateRequest;
import com.lnf.server.dto.ClaimMineVO;
import com.lnf.server.dto.ClaimReviewRequest;
import com.lnf.server.dto.ClaimTodoVO;
import com.lnf.server.dto.VerifyCodeRequest;
import com.lnf.server.security.LoginUser;
import com.lnf.server.service.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认领流程：申请、核验、核销
 */
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    /**
     * 提交认领申请（需回答隐藏特征）
     */
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody ClaimCreateRequest request,
                                            Authentication authentication) {
        Long claimId = claimService.create(currentUserId(authentication), request);
        return Result.ok("申请已提交，等待拾获者核验", Map.of("claimId", claimId));
    }

    /**
     * 我的认领申请列表（含状态）
     */
    @GetMapping("/mine")
    public Result<List<ClaimMineVO>> mine(Authentication authentication) {
        return Result.ok(claimService.mine(currentUserId(authentication)));
    }

    /**
     * 待我核验的认领申请（拾获者视角）
     */
    @GetMapping("/todo")
    public Result<List<ClaimTodoVO>> todo(Authentication authentication) {
        return Result.ok(claimService.todo(currentUserId(authentication)));
    }

    /**
     * 核验认领申请（通过/驳回/升级仲裁）
     */
    @PostMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id,
                               @Valid @RequestBody ClaimReviewRequest request,
                               Authentication authentication) {
        claimService.review(id, currentUserId(authentication), request);
        return Result.ok();
    }

    /**
     * 认领人获取交接核销码
     */
    @GetMapping("/{id}/code")
    public Result<ClaimCodeVO> code(@PathVariable Long id, Authentication authentication) {
        return Result.ok(claimService.getCode(id, currentUserId(authentication)));
    }

    /**
     * 拾获者扫码核销（交接完成）
     */
    @PostMapping("/verify")
    public Result<Map<String, Long>> verify(@Valid @RequestBody VerifyCodeRequest request,
                                            Authentication authentication) {
        Long claimId = claimService.verify(currentUserId(authentication), request.getVerifyCode());
        return Result.ok("交接完成", Map.of("claimId", claimId));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return loginUser.getId();
    }
}
