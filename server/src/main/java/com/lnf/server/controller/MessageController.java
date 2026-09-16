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

/**
 * 站内消息
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 消息列表（created_at 倒序分页，可按未读过滤）
     */
    @GetMapping
    public Result<MessagePageVO> page(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                      @RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "10") long size,
                                      Authentication authentication) {
        return Result.ok(messageService.pageMessages(currentUserId(authentication), unreadOnly, page, size));
    }

    /**
     * 标记已读（仅归属人本人，幂等）
     */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, Authentication authentication) {
        messageService.markRead(id, currentUserId(authentication));
        return Result.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return loginUser.getId();
    }
}
