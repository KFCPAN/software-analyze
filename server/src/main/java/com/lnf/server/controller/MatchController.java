package com.lnf.server.controller;

import com.lnf.server.common.BizException;
import com.lnf.server.common.Result;
import com.lnf.server.dto.MatchFeedbackRequest;
import com.lnf.server.dto.MatchVO;
import com.lnf.server.security.LoginUser;
import com.lnf.server.service.MatchService;
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

import java.util.List;

/**
 * 智能匹配：候选列表与反馈
 */
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    /**
     * 某条信息的智能匹配候选列表（仅发布者本人，按综合分降序）
     */
    @GetMapping
    public Result<List<MatchVO>> list(@RequestParam Long itemId, Authentication authentication) {
        return Result.ok(matchService.listByItem(itemId, currentUserId(authentication)));
    }

    /**
     * 对匹配结果反馈（是它/不是它，负反馈回流调权）
     */
    @PostMapping("/{id}/feedback")
    public Result<Void> feedback(@PathVariable Long id,
                                 @Valid @RequestBody MatchFeedbackRequest request,
                                 Authentication authentication) {
        matchService.feedback(id, currentUserId(authentication), request.getConfirm());
        return Result.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return loginUser.getId();
    }
}
