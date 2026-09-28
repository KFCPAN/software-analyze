package com.lnf.server.controller;

import com.lnf.server.common.BizException;
import com.lnf.server.common.Result;
import com.lnf.server.dto.UpdateProfileRequest;
import com.lnf.server.dto.UserVO;
import com.lnf.server.security.LoginUser;
import com.lnf.server.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：当前登录用户信息
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<UserVO> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return Result.ok(userService.getCurrentUser(loginUser.getId()));
    }

    /**
     * 修改个人资料（昵称/手机号）
     */
    @PutMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody UpdateProfileRequest request,
                                   Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return Result.ok(userService.updateProfile(loginUser.getId(), request));
    }
}
