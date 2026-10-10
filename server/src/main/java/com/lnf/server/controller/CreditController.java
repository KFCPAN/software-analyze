package com.lnf.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnf.server.common.BizException;
import com.lnf.server.common.Result;
import com.lnf.server.dto.CreditLogVO;
import com.lnf.server.dto.PageResult;
import com.lnf.server.entity.CreditLog;
import com.lnf.server.mapper.CreditLogMapper;
import com.lnf.server.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 信用体系：信用分流水查询
 */
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditController {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneOffset CAMPUS_ZONE = ZoneOffset.ofHours(8);

    private final CreditLogMapper creditLogMapper;

    /**
     * 我的信用分流水（created_at 倒序分页）
     */
    @GetMapping("/mine")
    public Result<PageResult<CreditLogVO>> mine(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "未登录或登录已过期");
        }
        if (page < 1 || size < 1 || size > 100) {
            throw new BizException(400, "分页参数不合法（page≥1，1≤size≤100）");
        }
        Page<CreditLog> result = creditLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<CreditLog>()
                        .eq(CreditLog::getUserId, loginUser.getId())
                        .orderByDesc(CreditLog::getCreatedAt));
        List<CreditLogVO> list = result.getRecords().stream().map(this::toVO).toList();
        return Result.ok(new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), list));
    }

    private CreditLogVO toVO(CreditLog log) {
        CreditLogVO vo = new CreditLogVO();
        vo.setId(log.getId());
        vo.setDelta(log.getDelta());
        vo.setReason(log.getReason());
        vo.setClaimId(log.getClaimId());
        vo.setCreatedAt(log.getCreatedAt() == null ? null
                : log.getCreatedAt().atZoneSameInstant(CAMPUS_ZONE).format(DISPLAY_FORMAT));
        return vo;
    }
}
