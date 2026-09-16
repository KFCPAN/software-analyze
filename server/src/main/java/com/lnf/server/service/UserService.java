package com.lnf.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lnf.server.common.BizException;
import com.lnf.server.dto.LoginRequest;
import com.lnf.server.dto.LoginResponse;
import com.lnf.server.dto.RegisterRequest;
import com.lnf.server.dto.UserVO;
import com.lnf.server.entity.User;
import com.lnf.server.mapper.UserMapper;
import com.lnf.server.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户服务：注册、登录、当前用户信息
 */
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 注册：查重 + BCrypt 加密入库
     */
    public void register(RegisterRequest request) {
        long usernameCount = count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (usernameCount > 0) {
            throw new BizException(1001, "用户名已被注册");
        }
        long emailCount = count(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, request.getEmail()));
        if (emailCount > 0) {
            throw new BizException(1002, "邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname() : request.getUsername());
        user.setRole("USER");
        user.setCreditScore(100);
        user.setStatus("ACTIVE");
        save(user);
    }

    /**
     * 登录：校验账号密码，签发 JWT
     */
    public LoginResponse login(LoginRequest request) {
        User user = getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(1003, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(1004, "账号已被封禁，请联系管理员");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        LoginResponse.UserBrief brief = new LoginResponse.UserBrief(
                user.getId(), user.getUsername(), user.getNickname(),
                user.getRole(), user.getCreditScore());
        return new LoginResponse(token, brief);
    }

    /**
     * 当前登录用户信息（手机号脱敏）
     */
    public UserVO getCurrentUser(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在或已被删除");
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setRole(user.getRole());
        vo.setCreditScore(user.getCreditScore());
        return vo;
    }

    /**
     * 手机号脱敏：保留前 3 位与后 4 位，如 138****1234
     */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return phone;
        }
        if (phone.length() < 7) {
            return phone.charAt(0) + "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
