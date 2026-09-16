package com.lnf.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lnf.server.common.AesUtil;
import com.lnf.server.common.BizException;
import com.lnf.server.dto.ClaimCodeVO;
import com.lnf.server.dto.ClaimCreateRequest;
import com.lnf.server.dto.ClaimMineVO;
import com.lnf.server.dto.ClaimReviewRequest;
import com.lnf.server.dto.ClaimTodoVO;
import com.lnf.server.entity.Claim;
import com.lnf.server.entity.CreditLog;
import com.lnf.server.entity.Item;
import com.lnf.server.entity.ItemFeature;
import com.lnf.server.entity.User;
import com.lnf.server.mapper.ClaimMapper;
import com.lnf.server.mapper.CreditLogMapper;
import com.lnf.server.mapper.ItemFeatureMapper;
import com.lnf.server.mapper.ItemMapper;
import com.lnf.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认领流程服务（状态机驱动）
 * PENDING → APPROVED → COMPLETED；旁路 REJECTED / DISPUTED / EXPIRED
 */
@Service
@RequiredArgsConstructor
public class ClaimService extends ServiceImpl<ClaimMapper, Claim> {

    /** 进行中的认领单状态（占用该信息的认领通道） */
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "APPROVED", "DISPUTED");
    /** 核销码有效期：7 天（自 APPROVED 时间起算，以 claims.updated_at 为基准） */
    private static final long CODE_VALID_DAYS = 7;
    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneOffset CAMPUS_ZONE = ZoneOffset.ofHours(8);

    private final ItemMapper itemMapper;
    private final ItemFeatureMapper itemFeatureMapper;
    private final UserMapper userMapper;
    private final CreditLogMapper creditLogMapper;
    private final MessageService messageService;
    private final AesUtil aesUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 提交认领申请
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, ClaimCreateRequest request) {
        Item item = itemMapper.selectById(request.getFoundItemId());
        if (item == null) {
            throw new BizException(2004, "信息不存在或已删除");
        }
        if (!"FOUND".equals(item.getType())) {
            throw new BizException(3002, "只能认领招领（FOUND）信息");
        }
        // 具体业务错误优先于状态错误：CLAIMING 状态下重复申请/认领自己的，应报 3005/3004 而非 3003
        if (item.getUserId().equals(userId)) {
            throw new BizException(3004, "不能认领自己发布的信息");
        }
        long activeCount = count(new LambdaQueryWrapper<Claim>()
                .eq(Claim::getFoundItemId, item.getId())
                .eq(Claim::getClaimantId, userId)
                .in(Claim::getStatus, ACTIVE_STATUSES));
        if (activeCount > 0) {
            throw new BizException(3005, "您已有一条进行中的认领单，请勿重复申请");
        }
        if (!List.of("OPEN", "MATCHED").contains(item.getStatus())) {
            throw new BizException(3003, "该信息当前状态不可认领");
        }

        // 比对隐藏特征：解密登记答案逐条比对，matched 仅供拾获者参考，不自动通过
        Map<String, String> registeredAnswers = itemFeatureMapper
                .selectList(new LambdaQueryWrapper<ItemFeature>().eq(ItemFeature::getItemId, item.getId()))
                .stream()
                .collect(Collectors.toMap(ItemFeature::getFeatureKey,
                        f -> aesUtil.decrypt(f.getAnswerEncrypted()), (a, b) -> a));
        List<Claim.FeatureAnswer> answers = request.getAnswers().stream()
                .map(input -> {
                    Claim.FeatureAnswer record = new Claim.FeatureAnswer();
                    record.setFeatureKey(input.getFeatureKey());
                    record.setAnswer(input.getAnswer());
                    String registered = registeredAnswers.get(input.getFeatureKey());
                    record.setMatched(registered != null && registered.equals(input.getAnswer()));
                    return record;
                })
                .toList();

        OffsetDateTime now = OffsetDateTime.now();
        Claim claim = new Claim();
        claim.setFoundItemId(item.getId());
        claim.setClaimantId(userId);
        claim.setFeatureAnswers(answers);
        claim.setStatus("PENDING");
        claim.setCreatedAt(now);
        claim.setUpdatedAt(now);
        save(claim);

        // items → CLAIMING
        item.setStatus("CLAIMING");
        item.setUpdatedAt(now);
        itemMapper.updateById(item);

        // 通知拾获者
        messageService.send(item.getUserId(), MessageService.TYPE_CLAIM_PROGRESS,
                "有新的认领申请",
                "您发布的「" + item.getTitle() + "」收到一条认领申请，请及时核验。",
                claim.getId());
        return claim.getId();
    }

    /**
     * 我提交的认领申请列表
     */
    public List<ClaimMineVO> mine(Long userId) {
        List<Claim> claims = list(new LambdaQueryWrapper<Claim>()
                .eq(Claim::getClaimantId, userId)
                .orderByDesc(Claim::getCreatedAt));
        Map<Long, String> itemTitles = itemTitleMap(
                claims.stream().map(Claim::getFoundItemId).distinct().toList());
        return claims.stream().map(c -> {
            ClaimMineVO vo = new ClaimMineVO();
            vo.setId(c.getId());
            vo.setFoundItemId(c.getFoundItemId());
            vo.setItemTitle(itemTitles.get(c.getFoundItemId()));
            vo.setStatus(c.getStatus());
            vo.setRejectReason(c.getRejectReason());
            vo.setCreatedAt(format(c.getCreatedAt()));
            return vo;
        }).toList();
    }

    /**
     * 待我核验的认领单（我是拾获者，仅 PENDING）
     */
    public List<ClaimTodoVO> todo(Long userId) {
        List<Long> myFoundItemIds = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                        .eq(Item::getUserId, userId).eq(Item::getType, "FOUND"))
                .stream().map(Item::getId).toList();
        if (myFoundItemIds.isEmpty()) {
            return List.of();
        }
        List<Claim> claims = list(new LambdaQueryWrapper<Claim>()
                .in(Claim::getFoundItemId, myFoundItemIds)
                .eq(Claim::getStatus, "PENDING")
                .orderByDesc(Claim::getCreatedAt));
        Map<Long, String> itemTitles = itemTitleMap(myFoundItemIds);
        return claims.stream().map(c -> {
            ClaimTodoVO vo = new ClaimTodoVO();
            vo.setId(c.getId());
            vo.setFoundItemId(c.getFoundItemId());
            vo.setItemTitle(itemTitles.get(c.getFoundItemId()));
            vo.setStatus(c.getStatus());
            vo.setFeatureAnswers(c.getFeatureAnswers() == null ? List.of() : c.getFeatureAnswers());
            vo.setCreatedAt(format(c.getCreatedAt()));
            User claimant = userMapper.selectById(c.getClaimantId());
            if (claimant != null) {
                ClaimTodoVO.Claimant cv = new ClaimTodoVO.Claimant();
                cv.setId(claimant.getId());
                cv.setNickname(claimant.getNickname());
                cv.setCreditScore(claimant.getCreditScore());
                vo.setClaimant(cv);
            }
            return vo;
        }).toList();
    }

    /**
     * 核验认领申请（通过 / 驳回 / 升级仲裁）
     */
    @Transactional(rollbackFor = Exception.class)
    public void review(Long claimId, Long userId, ClaimReviewRequest request) {
        Claim claim = getById(claimId);
        if (claim == null) {
            throw new BizException(3001, "认领单不存在");
        }
        Item item = itemMapper.selectById(claim.getFoundItemId());
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BizException(3006, "无权核验该认领单（仅拾获者可操作）");
        }
        if (!"PENDING".equals(claim.getStatus())) {
            throw new BizException(3007, "认领单当前状态不允许核验（仅 PENDING 可操作）");
        }

        OffsetDateTime now = OffsetDateTime.now();
        claim.setReviewedBy(userId);
        claim.setUpdatedAt(now);
        switch (request.getAction()) {
            case "APPROVE" -> {
                claim.setStatus("APPROVED");
                claim.setVerifyCode(generateUniqueCode());
                updateById(claim);
                messageService.send(claim.getClaimantId(), MessageService.TYPE_CLAIM_PROGRESS,
                        "认领申请已通过",
                        "您对「" + item.getTitle() + "」的认领申请已通过，请凭核销码与拾获者线下交接。",
                        claim.getId());
            }
            case "REJECT" -> {
                requireReason(request);
                claim.setStatus("REJECTED");
                claim.setRejectReason(request.getReason());
                updateById(claim);
                // 无其他进行中认领单 → items 回到 OPEN
                releaseItemIfNoActiveClaim(item, now);
                messageService.send(claim.getClaimantId(), MessageService.TYPE_CLAIM_PROGRESS,
                        "认领申请被驳回",
                        "您对「" + item.getTitle() + "」的认领申请被驳回，原因：" + request.getReason(),
                        claim.getId());
            }
            case "DISPUTE" -> {
                requireReason(request);
                claim.setStatus("DISPUTED");
                claim.setRejectReason(request.getReason());
                updateById(claim);
                // TODO(第9周后台仲裁)：DISPUTED 认领单进入后台仲裁队列，由管理员裁决并写 audit_logs
                messageService.send(claim.getClaimantId(), MessageService.TYPE_CLAIM_PROGRESS,
                        "认领申请进入争议仲裁",
                        "您对「" + item.getTitle() + "」的认领申请已升级仲裁，请等待平台处理。",
                        claim.getId());
            }
            default -> throw new BizException(400, "action 只能是 APPROVE / REJECT / DISPUTE");
        }
    }

    /**
     * 认领人获取交接核销码
     */
    public ClaimCodeVO getCode(Long claimId, Long userId) {
        Claim claim = getById(claimId);
        if (claim == null) {
            throw new BizException(3001, "认领单不存在");
        }
        if (!claim.getClaimantId().equals(userId)) {
            throw new BizException(3010, "无权查看该核销码（仅认领人本人可取）");
        }
        if (!"APPROVED".equals(claim.getStatus())) {
            throw new BizException(3007, "认领单当前状态不可获取核销码（需 APPROVED）");
        }
        return new ClaimCodeVO(claim.getVerifyCode(), format(codeExpiresAt(claim)));
    }

    /**
     * 拾获者扫码核销（交接完成）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long verify(Long userId, String verifyCode) {
        Claim claim = getOne(new LambdaQueryWrapper<Claim>()
                .eq(Claim::getVerifyCode, verifyCode)
                .eq(Claim::getStatus, "APPROVED"));
        if (claim == null) {
            throw new BizException(3009, "核销码无效");
        }
        Item item = itemMapper.selectById(claim.getFoundItemId());
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BizException(3011, "无权核销该认领单（仅拾获者可核销）");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isAfter(codeExpiresAt(claim))) {
            // 过期：认领单 → EXPIRED，无其他进行中认领单则 items 回到 OPEN
            claim.setStatus("EXPIRED");
            claim.setUpdatedAt(now);
            updateById(claim);
            releaseItemIfNoActiveClaim(item, now);
            throw new BizException(3009, "核销码已过期，认领单已关闭");
        }

        // 事务内完成：claims → COMPLETED、items → CLOSED、双方信用分、消息通知
        claim.setStatus("COMPLETED");
        claim.setCompletedAt(now);
        claim.setUpdatedAt(now);
        updateById(claim);

        item.setStatus("CLOSED");
        item.setUpdatedAt(now);
        itemMapper.updateById(item);

        changeCredit(item.getUserId(), 5, "完成归还 +5", claim.getId());
        changeCredit(claim.getClaimantId(), 2, "完成认领 +2", claim.getId());

        messageService.send(claim.getClaimantId(), MessageService.TYPE_CLAIM_PROGRESS,
                "交接完成",
                "您对「" + item.getTitle() + "」的认领已完成交接，感谢使用本平台。",
                claim.getId());
        messageService.send(item.getUserId(), MessageService.TYPE_CLAIM_PROGRESS,
                "交接完成",
                "您发布的「" + item.getTitle() + "」已完成交接，信用分 +5。",
                claim.getId());
        return claim.getId();
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    private void requireReason(ClaimReviewRequest request) {
        if (!StringUtils.hasText(request.getReason())) {
            throw new BizException(3008, "驳回或升级仲裁时必须填写原因");
        }
    }

    /**
     * 若该信息没有其他进行中的认领单，则从 CLAIMING 释放回 OPEN
     */
    private void releaseItemIfNoActiveClaim(Item item, OffsetDateTime now) {
        long activeCount = count(new LambdaQueryWrapper<Claim>()
                .eq(Claim::getFoundItemId, item.getId())
                .in(Claim::getStatus, ACTIVE_STATUSES));
        if (activeCount == 0 && "CLAIMING".equals(item.getStatus())) {
            item.setStatus("OPEN");
            item.setUpdatedAt(now);
            itemMapper.updateById(item);
        }
    }

    /**
     * 信用分变动：写流水 + 更新 users.credit_score
     */
    private void changeCredit(Long userId, int delta, String reason, Long claimId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setCreditScore(user.getCreditScore() + delta);
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.updateById(user);

        CreditLog log = new CreditLog();
        log.setUserId(userId);
        log.setDelta(delta);
        log.setReason(reason);
        log.setClaimId(claimId);
        log.setCreatedAt(OffsetDateTime.now());
        creditLogMapper.insert(log);
    }

    /**
     * 生成唯一核销码：LNF-XXXXXX（8 位大写字母数字）
     */
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("LNF-");
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            Long exists = baseMapper.selectCount(new LambdaQueryWrapper<Claim>()
                    .eq(Claim::getVerifyCode, code));
            if (exists == 0) {
                return code;
            }
        }
        throw new BizException(500, "核销码生成失败，请重试");
    }

    /**
     * 核销码过期时间 = APPROVED 时间（updated_at）+ 7 天
     */
    private OffsetDateTime codeExpiresAt(Claim claim) {
        return claim.getUpdatedAt().plusDays(CODE_VALID_DAYS);
    }

    private Map<Long, String> itemTitleMap(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        return itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Item::getTitle));
    }

    private String format(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(CAMPUS_ZONE).format(DISPLAY_FORMAT);
    }
}
