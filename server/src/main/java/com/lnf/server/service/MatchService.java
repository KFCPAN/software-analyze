package com.lnf.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lnf.server.common.BizException;
import com.lnf.server.dto.MatchVO;
import com.lnf.server.entity.Item;
import com.lnf.server.entity.Location;
import com.lnf.server.entity.Match;
import com.lnf.server.mapper.ItemMapper;
import com.lnf.server.mapper.LocationMapper;
import com.lnf.server.mapper.MatchMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能匹配服务：向量写回（异步）+ 候选粗筛 + 多因子打分 + 结果反馈
 */
@Slf4j
@Service
public class MatchService extends ServiceImpl<MatchMapper, Match> {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneOffset CAMPUS_ZONE = ZoneOffset.ofHours(8);
    private static final String TYPE_MATCH_HIT = "MATCH_HIT";

    private final ItemMapper itemMapper;
    private final LocationMapper locationMapper;
    private final MatcherClient matcherClient;
    private final MessageService messageService;

    private final double weightText;
    private final double weightTime;
    private final double weightLocation;
    private final double threshold;

    public MatchService(ItemMapper itemMapper,
                        LocationMapper locationMapper,
                        MatcherClient matcherClient,
                        MessageService messageService,
                        @Value("${match.weights.text}") double weightText,
                        @Value("${match.weights.time}") double weightTime,
                        @Value("${match.weights.location}") double weightLocation,
                        @Value("${match.threshold}") double threshold) {
        this.itemMapper = itemMapper;
        this.locationMapper = locationMapper;
        this.matcherClient = matcherClient;
        this.messageService = messageService;
        this.weightText = weightText;
        this.weightTime = weightTime;
        this.weightLocation = weightLocation;
        this.threshold = threshold;
    }

    /**
     * 异步：文本向量化写回 + 匹配计算。matcher 不可用时仅记日志，不影响发布。
     * 须在事务提交后调用（由 ItemService 通过 afterCommit 触发）。
     */
    @Async("taskExecutor")
    public void vectorizeAndMatch(Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            return;
        }
        // 文本约定：标题 + 空格 + 描述
        List<Double> vector = matcherClient.embedText(item.getTitle() + " " + item.getDescription());
        if (vector == null) {
            // TODO(可靠性)：matcher 不可用时记录待补算队列，恢复后批量重算 text_vector
            log.warn("item {} 文本向量化失败（matcher 不可用），跳过本次匹配", itemId);
            return;
        }
        String vectorString = toVectorString(vector);
        itemMapper.updateTextVector(itemId, vectorString);
        log.info("item {} text_vector 已写回（{} 维）", itemId, vector.size());
        computeMatches(item, vectorString);
    }

    /**
     * 匹配计算：pgvector 余弦粗筛 → 多因子打分 → 超阈值写 matches + 通知双方
     */
    public void computeMatches(Item item, String vectorString) {
        boolean isLost = "LOST".equals(item.getType());
        List<Map<String, Object>> candidates = isLost
                ? itemMapper.findFoundCandidates(vectorString, item.getId(), item.getEventTime())
                : itemMapper.findLostCandidates(vectorString, item.getId(), item.getEventTime());
        if (candidates.isEmpty()) {
            return;
        }
        Map<Long, String> campusMap = locationMapper.selectList(null).stream()
                .collect(Collectors.toMap(Location::getId, Location::getCampus));

        for (Map<String, Object> candidate : candidates) {
            Long candidateId = ((Number) candidate.get("id")).longValue();
            double textScore = ((Number) candidate.get("text_score")).doubleValue();
            // 原生 SQL Map 返回中 timestamptz 默认映射为 java.sql.Timestamp，需手动转换
            OffsetDateTime candidateEventTime = toOffsetDateTime(candidate.get("event_time"));
            Long candidateLocationId = candidate.get("location_id") == null
                    ? null : ((Number) candidate.get("location_id")).longValue();

            double timeScore = timeScore(item.getEventTime(), candidateEventTime);
            double locationScore = locationScore(item.getLocationId(), candidateLocationId, campusMap);
            double totalScore = weightText * textScore + weightTime * timeScore + weightLocation * locationScore;
            if (totalScore < threshold) {
                continue;
            }

            Long lostItemId = isLost ? item.getId() : candidateId;
            Long foundItemId = isLost ? candidateId : item.getId();
            int inserted = baseMapper.insertIgnore(lostItemId, foundItemId,
                    scaled(textScore), scaled(timeScore), scaled(locationScore), scaled(totalScore));
            if (inserted == 0) {
                continue; // 已存在该对，忽略
            }
            Long matchId = baseMapper.selectIdByPair(lostItemId, foundItemId);
            log.info("新匹配命中: lost={} found={} total={}", lostItemId, foundItemId,
                    String.format("%.4f", totalScore));

            // 命中后通知双方用户
            Item lostItem = isLost ? item : itemMapper.selectById(candidateId);
            Item foundItem = isLost ? itemMapper.selectById(candidateId) : item;
            if (lostItem != null && foundItem != null) {
                messageService.send(lostItem.getUserId(), TYPE_MATCH_HIT,
                        "找到疑似您丢失的物品",
                        "招领信息「" + foundItem.getTitle() + "」与您丢失的「" + lostItem.getTitle()
                                + "」高度相似（综合分 " + String.format("%.2f", totalScore) + "），快去确认吧。",
                        matchId);
                messageService.send(foundItem.getUserId(), TYPE_MATCH_HIT,
                        "您的招领信息有新匹配",
                        "失物信息「" + lostItem.getTitle() + "」与您捡到的「" + foundItem.getTitle()
                                + "」高度相似（综合分 " + String.format("%.2f", totalScore) + "）。",
                        matchId);
            }
        }
    }

    /**
     * 某条信息的匹配候选列表（仅发布者本人可查，按综合分降序，不返回 REJECTED）
     */
    public List<MatchVO> listByItem(Long itemId, Long userId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(2004, "信息不存在或已删除");
        }
        if (!item.getUserId().equals(userId)) {
            throw new BizException(5002, "无权查看他人信息的匹配结果");
        }
        List<Match> matches = list(new LambdaQueryWrapper<Match>()
                .and(w -> w.eq(Match::getLostItemId, itemId).or().eq(Match::getFoundItemId, itemId))
                .ne(Match::getStatus, "REJECTED")
                .orderByDesc(Match::getTotalScore));
        Map<Long, String> locationNames = locationMapper.selectList(null).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName));

        return matches.stream().map(m -> {
            Long otherItemId = m.getLostItemId().equals(itemId) ? m.getFoundItemId() : m.getLostItemId();
            Item other = itemMapper.selectById(otherItemId);
            MatchVO vo = new MatchVO();
            vo.setMatchId(m.getId());
            vo.setTextScore(toDouble(m.getTextScore()));
            vo.setImageScore(null); // TODO(CLIP 图像向量)：下周接入后返回 image_score
            vo.setTimeScore(toDouble(m.getTimeScore()));
            vo.setLocationScore(toDouble(m.getLocationScore()));
            vo.setTotalScore(toDouble(m.getTotalScore()));
            vo.setStatus(m.getStatus());
            if (other != null) {
                MatchVO.CandidateItem ci = new MatchVO.CandidateItem();
                ci.setId(other.getId());
                ci.setTitle(other.getTitle());
                ci.setCoverImage(CollectionUtils.isEmpty(other.getImages()) ? null : other.getImages().get(0));
                ci.setLocationName(other.getLocationId() == null ? null : locationNames.get(other.getLocationId()));
                ci.setEventTime(other.getEventTime() == null ? null
                        : other.getEventTime().atZoneSameInstant(CAMPUS_ZONE).format(DISPLAY_FORMAT));
                vo.setItem(ci);
            }
            return vo;
        }).toList();
    }

    /**
     * 匹配反馈：confirm=true → CONFIRMED，false → REJECTED（负反馈，不再推荐该对）
     */
    public void feedback(Long matchId, Long userId, boolean confirm) {
        Match match = getById(matchId);
        if (match == null) {
            throw new BizException(5001, "匹配记录不存在");
        }
        Item lostItem = itemMapper.selectById(match.getLostItemId());
        Item foundItem = itemMapper.selectById(match.getFoundItemId());
        boolean involved = (lostItem != null && lostItem.getUserId().equals(userId))
                || (foundItem != null && foundItem.getUserId().equals(userId));
        if (!involved) {
            throw new BizException(5002, "无权操作他人的匹配记录");
        }
        if (!"PENDING".equals(match.getStatus())) {
            throw new BizException(5003, "该匹配记录已反馈过");
        }
        match.setStatus(confirm ? "CONFIRMED" : "REJECTED");
        match.setFeedbackBy(userId);
        updateById(match);
        // TODO(调权回流)：REJECTED 负反馈样本用于后续调整各因子权重与阈值
    }

    // ------------------------------------------------------------------
    // 打分因子
    // ------------------------------------------------------------------

    /**
     * 时间接近度：0 天内 1.0，每天 -0.1，最低 0.3（7 天后保持 0.3）
     */
    private double timeScore(OffsetDateTime a, OffsetDateTime b) {
        double days = Math.abs(Duration.between(a, b).toHours()) / 24.0;
        return Math.max(0.3, 1.0 - 0.1 * days);
    }

    /**
     * 地点接近度：同 locationId 1.0 / 同 campus 0.6 / 跨校区 0.2 / 任一方为空 0.4
     */
    private double locationScore(Long locationA, Long locationB, Map<Long, String> campusMap) {
        if (locationA == null || locationB == null) {
            return 0.4;
        }
        if (locationA.equals(locationB)) {
            return 1.0;
        }
        String campusA = campusMap.get(locationA);
        String campusB = campusMap.get(locationB);
        if (campusA != null && campusA.equals(campusB)) {
            return 0.6;
        }
        return 0.2;
    }

    private String toVectorString(List<Double> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector.get(i));
        }
        return sb.append(']').toString();
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        throw new IllegalStateException("无法解析的 event_time 类型: " + (value == null ? "null" : value.getClass()));
    }

    private BigDecimal scaled(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
