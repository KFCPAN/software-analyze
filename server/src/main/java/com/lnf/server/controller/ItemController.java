package com.lnf.server.controller;

import com.lnf.server.common.BizException;
import com.lnf.server.common.Result;
import com.lnf.server.dto.ItemCreateRequest;
import com.lnf.server.dto.ItemDetailVO;
import com.lnf.server.dto.ItemListVO;
import com.lnf.server.dto.PageResult;
import com.lnf.server.security.LoginUser;
import com.lnf.server.service.ItemService;
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

import java.util.List;
import java.util.Map;

/**
 * 失物/招领信息
 */
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    /**
     * 发布失物/招领信息
     */
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody ItemCreateRequest request,
                                            Authentication authentication) {
        Long id = itemService.create(currentUserId(authentication), request);
        return Result.ok("发布成功，匹配进行中，命中后将通知您", Map.of("id", id));
    }

    /**
     * 信息流检索（多条件 + 分页）
     */
    @GetMapping
    public Result<PageResult<ItemListVO>> page(@RequestParam String type,
                                               @RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) Long locationId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.ok(itemService.pageItems(type, categoryId, locationId, keyword, page, size));
    }

    /**
     * 我发布的信息列表
     */
    @GetMapping("/mine")
    public Result<List<ItemListVO>> mine(@RequestParam(required = false) String status,
                                         Authentication authentication) {
        return Result.ok(itemService.mine(currentUserId(authentication), status));
    }

    /**
     * 信息详情
     */
    @GetMapping("/{id}")
    public Result<ItemDetailVO> detail(@PathVariable Long id, Authentication authentication) {
        return Result.ok(itemService.detail(id, currentUserId(authentication)));
    }

    /**
     * 编辑自己发布的信息
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody ItemCreateRequest request,
                               Authentication authentication) {
        itemService.update(id, currentUserId(authentication), request);
        return Result.ok();
    }

    /**
     * 关闭信息（已找回/已归还/放弃）
     */
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id, Authentication authentication) {
        itemService.close(id, currentUserId(authentication));
        return Result.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return loginUser.getId();
    }
}
