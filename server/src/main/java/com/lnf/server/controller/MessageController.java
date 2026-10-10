package com.lnf.server.controller;

import com.lnf.server.common.BizException;
import com.lnf.server.common.Result;
import com.lnf.server.dto.MessagePageVO;
import com.lnf.server.security.LoginUser;
import com.lnf.server.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内消息
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 消息列表（created_at 倒序分页，可按未读/类型过滤）
     * type 可选：MATCH_HIT / CLAIM_PROGRESS / REVIEW_RESULT / SYSTEM
     */
    @GetMapping
    public Result<MessagePageVO> page(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                      @RequestParam(required = false) String type,
                                      @RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "10") long size,
                                      Authentication authentication) {
        return Result.ok(messageService.pageMessages(currentUserId(authentication), unreadOnly, type, page, size));
    }

    /**
     * 标记已读（仅归属人本人，幂等）
     */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, Authentication authentication) {
        messageService.markRead(id, currentUserId(authentication));
        return Result.ok();
    }

    /**
     * 全部标记已读（批量），返回更新条数
     */
    @PostMapping("/read-all")
    public Result<Map<String, Integer>> markAllRead(Authentication authentication) {
        int updated = messageService.markAllRead(currentUserId(authentication));
        return Result.ok(Map.of("updated", updated));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return loginUser.getId();
    }
}
