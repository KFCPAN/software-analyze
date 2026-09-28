package com.lnf.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnf.server.common.BizException;
import com.lnf.server.dto.AdminClaimVO;
import com.lnf.server.dto.AdminItemVO;
import com.lnf.server.dto.PageResult;
import com.lnf.server.dto.StatsOverviewVO;
import com.lnf.server.entity.Category;
import com.lnf.server.entity.Claim;
import com.lnf.server.entity.CreditLog;
import com.lnf.server.entity.Item;
import com.lnf.server.entity.Location;
import com.lnf.server.entity.Match;
import com.lnf.server.entity.User;
import com.lnf.server.mapper.CategoryMapper;
import com.lnf.server.mapper.ClaimMapper;
import com.lnf.server.mapper.CreditLogMapper;
import com.lnf.server.mapper.ItemMapper;
import com.lnf.server.mapper.LocationMapper;
import com.lnf.server.mapper.MatchMapper;
import com.lnf.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台管理服务：信息审核、用户封禁、争议仲裁队列、运营看板
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneOffset CAMPUS_ZONE = ZoneOffset.ofHours(8);
    private static final int BAN_CREDIT_PENALTY = -20;

    private final ItemMapper itemMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final LocationMapper locationMapper;
    private final ClaimMapper claimMapper;
    private final MatchMapper matchMapper;
    private final CreditLogMapper creditLogMapper;
    private final AuditLogService auditLogService;
    private final MessageService messageService;

    /**
     * 信息审核列表（全状态可查，含发布者信息）
     */
    public PageResult<AdminItemVO> pageItems(String status, String keyword, long page, long size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BizException(400, "分页参数不合法（page≥1，1≤size≤100）");
        }
        Page<Item> result = new Page<>(page, size);
        itemMapper.selectPage(result, new LambdaQueryWrapper<Item>()
                .eq(StringUtils.hasText(status), Item::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Item::getTitle, keyword).or().like(Item::getDescription, keyword))
                .orderByDesc(Item::getCreatedAt));

        Map<Long, String> categoryNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, String> locationNames = locationMapper.selectList(null).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName));

        List<AdminItemVO> list = result.getRecords().stream().map(item -> {
            AdminItemVO vo = new AdminItemVO();
            vo.setId(item.getId());
            vo.setType(item.getType());
            vo.setTitle(item.getTitle());
            vo.setCategoryId(item.getCategoryId());
            vo.setCategoryName(categoryNames.get(item.getCategoryId()));
            vo.setLocationName(item.getLocationId() == null ? null : locationNames.get(item.getLocationId()));
            vo.setEventTime(format(item.getEventTime()));
            vo.setStatus(item.getStatus());
            vo.setCreatedAt(format(item.getCreatedAt()));
            User publisher = userMapper.selectById(item.getUserId());
            if (publisher != null) {
                AdminItemVO.Publisher p = new AdminItemVO.Publisher();
                p.setId(publisher.getId());
                p.setUsername(publisher.getUsername());
                p.setNickname(publisher.getNickname());
                p.setCreditScore(publisher.getCreditScore());
                p.setStatus(publisher.getStatus());
                vo.setPublisher(p);
            }
            return vo;
        }).toList();
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    /**
     * 封禁/解封用户。封禁：status→BANNED + 信用分 -20 + 审计 + 通知；解封：status→ACTIVE（信用不回加）
     */
    @Transactional(rollbackFor = Exception.class)
    public void banUser(Long adminId, Long targetUserId, boolean ban, String reason) {
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BizException(6002, "用户不存在");
        }
        if ("ADMIN".equals(target.getRole())) {
            throw new BizException(6001, "不能封禁管理员账号");
        }
        String targetStatus = ban ? "BANNED" : "ACTIVE";
        if (targetStatus.equals(target.getStatus())) {
            throw new BizException(6005, ban ? "该用户已是封禁状态" : "该用户未处于封禁状态");
        }

        target.setStatus(targetStatus);
        target.setUpdatedAt(OffsetDateTime.now());
        if (ban) {
            target.setCreditScore(target.getCreditScore() + BAN_CREDIT_PENALTY);
        }
        userMapper.updateById(target);

        if (ban) {
            CreditLog log = new CreditLog();
            log.setUserId(targetUserId);
            log.setDelta(BAN_CREDIT_PENALTY);
            log.setReason(StringUtils.hasText(reason) ? reason : "违规封禁");
            log.setCreatedAt(OffsetDateTime.now());
            creditLogMapper.insert(log);

            messageService.send(targetUserId, "SYSTEM", "账号已被封禁",
                    "您的账号因「" + (StringUtils.hasText(reason) ? reason : "违规操作")
                            + "」被平台封禁，信用分 -20。如有异议请联系管理员。", null);
        } else {
            messageService.send(targetUserId, "SYSTEM", "账号已解封",
                    "您的账号已解除封禁，恢复正常使用。", null);
        }

        auditLogService.record(adminId, ban ? "BAN_USER" : "UNBAN_USER", "USER", targetUserId,
                "{\"reason\":\"" + (reason == null ? "" : reason.replace("\"", "'")) + "\"}");
    }

    /**
     * 争议认领单队列（默认 DISPUTED，含双方信息、作答明细、争议原因）
     */
    public PageResult<AdminClaimVO> pageDisputedClaims(String status, long page, long size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BizException(400, "分页参数不合法（page≥1，1≤size≤100）");
        }
        Page<Claim> result = new Page<>(page, size);
        claimMapper.selectPage(result, new LambdaQueryWrapper<Claim>()
                .eq(StringUtils.hasText(status), Claim::getStatus, status)
                .orderByDesc(Claim::getCreatedAt));

        List<AdminClaimVO> list = result.getRecords().stream().map(claim -> {
            AdminClaimVO vo = new AdminClaimVO();
            vo.setId(claim.getId());
            vo.setFeatureAnswers(claim.getFeatureAnswers() == null ? List.of() : claim.getFeatureAnswers());
            vo.setDisputeReason(claim.getRejectReason());
            vo.setStatus(claim.getStatus());
            vo.setCreatedAt(format(claim.getCreatedAt()));
            Item item = itemMapper.selectById(claim.getFoundItemId());
            if (item != null) {
                AdminClaimVO.ItemBrief brief = new AdminClaimVO.ItemBrief();
                brief.setId(item.getId());
                brief.setTitle(item.getTitle());
                vo.setFoundItem(brief);
                vo.setPublisher(toParty(item.getUserId()));
            }
            vo.setClaimant(toParty(claim.getClaimantId()));
            return vo;
        }).toList();
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    /**
     * 运营数据看板总览
     */
    public StatsOverviewVO statsOverview() {
        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setTotalItems(itemMapper.selectCount(null));
        vo.setTotalLost(itemMapper.selectCount(new LambdaQueryWrapper<Item>().eq(Item::getType, "LOST")));
        vo.setTotalFound(itemMapper.selectCount(new LambdaQueryWrapper<Item>().eq(Item::getType, "FOUND")));

        long confirmed = matchMapper.selectCount(new LambdaQueryWrapper<Match>().eq(Match::getStatus, "CONFIRMED"));
        long rejected = matchMapper.selectCount(new LambdaQueryWrapper<Match>().eq(Match::getStatus, "REJECTED"));
        long feedbackTotal = confirmed + rejected;
        vo.setMatchHitRate(feedbackTotal == 0 ? 0.0 : Math.round(confirmed * 10000.0 / feedbackTotal) / 10000.0);

        vo.setCompletedClaims(claimMapper.selectCount(
                new LambdaQueryWrapper<Claim>().eq(Claim::getStatus, "COMPLETED")));
        Double avgHours = claimMapper.avgRecoverHours();
        vo.setAvgRecoverHours(avgHours == null ? 0.0 : Math.round(avgHours * 10.0) / 10.0);

        vo.setHotLocations(itemMapper.selectHotLocations().stream()
                .map(row -> new StatsOverviewVO.HotLocation(
                        (String) row.get("location_name"), ((Number) row.get("cnt")).longValue()))
                .toList());
        return vo;
    }

    private AdminClaimVO.Party toParty(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        AdminClaimVO.Party p = new AdminClaimVO.Party();
        p.setId(user.getId());
        p.setUsername(user.getUsername());
        p.setNickname(user.getNickname());
        p.setCreditScore(user.getCreditScore());
        return p;
    }

    private String format(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(CAMPUS_ZONE).format(DISPLAY_FORMAT);
    }
}
