package com.lnf.server.controller;

import com.lnf.server.common.BizException;
import com.lnf.server.common.Result;
import com.lnf.server.dto.AdminAuditItemVO;
import com.lnf.server.dto.AdminClaimVO;
import com.lnf.server.dto.AdminItemVO;
import com.lnf.server.dto.AdminUserVO;
import com.lnf.server.dto.ArbitrateRequest;
import com.lnf.server.dto.AuditRejectRequest;
import com.lnf.server.dto.BanUserRequest;
import com.lnf.server.dto.DashboardStatsVO;
import com.lnf.server.dto.MatchConfigVO;
import com.lnf.server.dto.PageResult;
import com.lnf.server.dto.StatsOverviewVO;
import com.lnf.server.entity.Category;
import com.lnf.server.entity.Location;
import com.lnf.server.security.LoginUser;
import com.lnf.server.service.AdminService;
import com.lnf.server.service.ClaimService;
import com.lnf.server.service.MatchConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台管理（仅 ADMIN / REVIEWER 角色，由 SecurityConfig 拦截）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ClaimService claimService;
    private final MatchConfigService matchConfigService;
    private final com.lnf.server.mapper.CategoryMapper categoryMapper;
    private final com.lnf.server.mapper.LocationMapper locationMapper;

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

    // ==================== 以下接口为前端管理后台（web/src/views/admin）联调补充 ====================

    /**
     * 数据看板统计（前端 dashboard.vue：stats 四指标 + locations 地点分布）
     */
    @GetMapping("/dashboard/stats")
    public Result<DashboardStatsVO> dashboardStats() {
        return Result.ok(adminService.dashboardStats());
    }

    /**
     * 内容审核列表（默认 OPEN 状态，扁平字段 + 风险等级）
     */
    @GetMapping("/audit/list")
    public Result<PageResult<AdminAuditItemVO>> auditList(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.pageAuditItems(status, keyword, page, size));
    }

    /**
     * 审核通过（恢复/保持 OPEN，站内信通知发布者）
     */
    @PutMapping("/audit/{id}/approve")
    public Result<Void> auditApprove(@PathVariable Long id,
                                     Authentication authentication) {
        adminService.reviewItem(currentUserId(authentication), id, true, null);
        return Result.ok();
    }

    /**
     * 审核驳回（下架为 ARCHIVED，必填原因）
     */
    @PutMapping("/audit/{id}/reject")
    public Result<Void> auditReject(@PathVariable Long id,
                                    @Valid @RequestBody AuditRejectRequest request,
                                    Authentication authentication) {
        adminService.reviewItem(currentUserId(authentication), id, false, request.getReason());
        return Result.ok();
    }

    /**
     * 用户列表（扁平字段，status 为 banned / normal）
     */
    @GetMapping("/users")
    public Result<PageResult<AdminUserVO>> userList(@RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.pageUsers(keyword, page, size));
    }

    /**
     * 封禁用户（PUT 版，前端 users.vue 契约：body 仅 {reason}）
     */
    @PutMapping("/users/{id}/ban")
    public Result<Void> banUserPut(@PathVariable Long id,
                                   @RequestBody(required = false) BanUserRequest request,
                                   Authentication authentication) {
        adminService.banUser(currentUserId(authentication), id, true,
                request == null ? null : request.getReason());
        return Result.ok();
    }

    /**
     * 解封用户（PUT 版）
     */
    @PutMapping("/users/{id}/unban")
    public Result<Void> unbanUser(@PathVariable Long id,
                                  Authentication authentication) {
        adminService.banUser(currentUserId(authentication), id, false, null);
        return Result.ok();
    }

    /**
     * 获取匹配参数配置
     */
    @GetMapping("/config/match")
    public Result<MatchConfigVO> getMatchConfig() {
        return Result.ok(matchConfigService.get());
    }

    /**
     * 更新匹配参数配置（持久化到 system_configs，写审计日志）
     */
    @PutMapping("/config/match")
    public Result<Void> updateMatchConfig(@Valid @RequestBody MatchConfigVO config,
                                          Authentication authentication) {
        matchConfigService.update(config, currentUserId(authentication));
        return Result.ok();
    }

    /**
     * 新增物品分类（body: {"name": "..."}）
     */
    @PostMapping("/config/category")
    public Result<Void> addCategory(@RequestBody Map<String, String> body,
                                    Authentication authentication) {
        String name = body == null ? null : body.get("name");
        if (name == null || name.isBlank()) {
            throw new BizException(400, "分类名称不能为空");
        }
        Category category = new Category();
        category.setName(name.trim());
        category.setEnabled(true);
        categoryMapper.insert(category);
        return Result.ok();
    }

    /**
     * 新增地点词表（body: {"name": "...", "campus": "可选"}）
     */
    @PostMapping("/config/location")
    public Result<Void> addLocation(@RequestBody Map<String, String> body,
                                    Authentication authentication) {
        String name = body == null ? null : body.get("name");
        if (name == null || name.isBlank()) {
            throw new BizException(400, "地点名称不能为空");
        }
        Location location = new Location();
        location.setName(name.trim());
        location.setCampus(body.getOrDefault("campus", null));
        location.setEnabled(true);
        locationMapper.insert(location);
        return Result.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return loginUser.getId();
    }
}
