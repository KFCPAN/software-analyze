package com.lnf.server.controller;

import com.lnf.server.common.BizException;
import com.lnf.server.common.Result;
import com.lnf.server.dto.AdminClaimVO;
import com.lnf.server.dto.AdminItemVO;
import com.lnf.server.dto.ArbitrateRequest;
import com.lnf.server.dto.BanUserRequest;
import com.lnf.server.dto.PageResult;
import com.lnf.server.dto.StatsOverviewVO;
import com.lnf.server.security.LoginUser;
import com.lnf.server.service.AdminService;
import com.lnf.server.service.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台管理（仅 ADMIN / REVIEWER 角色，由 SecurityConfig 拦截）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ClaimService claimService;

    /**
     * 信息审核列表（全状态，含发布者信息）
     */
    @GetMapping("/items")
    public Result<PageResult<AdminItemVO>> items(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.pageItems(status, keyword, page, size));
    }

    /**
     * 封禁/解封用户
     */
    @PostMapping("/users/{id}/ban")
    public Result<Void> banUser(@PathVariable Long id,
                                @Valid @RequestBody BanUserRequest request,
                                Authentication authentication) {
        adminService.banUser(currentUserId(authentication), id, request.getBan(), request.getReason());
        return Result.ok();
    }

    /**
     * 争议认领单队列（默认 DISPUTED）
     */
    @GetMapping("/claims")
    public Result<PageResult<AdminClaimVO>> disputedClaims(@RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.pageDisputedClaims(status, page, size));
    }

    /**
     * 争议仲裁
     */
    @PostMapping("/claims/{id}/arbitrate")
    public Result<Void> arbitrate(@PathVariable Long id,
                                  @Valid @RequestBody ArbitrateRequest request,
                                  Authentication authentication) {
        claimService.adminArbitrate(id, currentUserId(authentication), request.getApprove(), request.getReason());
        return Result.ok();
    }

    /**
     * 运营数据看板总览
     */
    @GetMapping("/stats/overview")
    public Result<StatsOverviewVO> statsOverview() {
        return Result.ok(adminService.statsOverview());
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return loginUser.getId();
    }
}
