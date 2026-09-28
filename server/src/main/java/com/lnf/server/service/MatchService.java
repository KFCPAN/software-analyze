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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final double weightImgText;
    private final double weightImgImage;
    private final double weightImgTime;
    private final double weightImgLocation;
    private final double threshold;
    private final String uploadDir;

    public MatchService(ItemMapper itemMapper,
                        LocationMapper locationMapper,
                        MatcherClient matcherClient,
                        MessageService messageService,
                        @Value("${match.weights.text}") double weightText,
                        @Value("${match.weights.time}") double weightTime,
                        @Value("${match.weights.location}") double weightLocation,
                        @Value("${match.weights.with-image.text}") double weightImgText,
                        @Value("${match.weights.with-image.image}") double weightImgImage,
                        @Value("${match.weights.with-image.time}") double weightImgTime,
                        @Value("${match.weights.with-image.location}") double weightImgLocation,
                        @Value("${match.threshold}") double threshold,
                        @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.itemMapper = itemMapper;
        this.locationMapper = locationMapper;
        this.matcherClient = matcherClient;
        this.messageService = messageService;
        this.weightText = weightText;
        this.weightTime = weightTime;
        this.weightLocation = weightLocation;
        this.weightImgText = weightImgText;
        this.weightImgImage = weightImgImage;
        this.weightImgTime = weightImgTime;
        this.weightImgLocation = weightImgLocation;
        this.threshold = threshold;
        this.uploadDir = uploadDir;
    }

    /**
     * 异步：文本 + 图像（首图）向量化写回 + 匹配计算。
     * 两路各自独立降级：matcher 不可用或读取失败仅记日志，不影响发布；两路都失败才跳过匹配。
     * 须在事务提交后调用（由 ItemService 通过 afterCommit 触发）。
     */
    @Async("taskExecutor")
    public void vectorizeAndMatch(Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            return;
        }
        // 文本约定：标题 + 空格 + 描述
        List<Double> textVector = matcherClient.embedText(item.getTitle() + " " + item.getDescription());
        String textVectorString = null;
        if (textVector != null) {
            textVectorString = toVectorString(textVector);
            itemMapper.updateTextVector(itemId, textVectorString);
            log.info("item {} text_vector 已写回（{} 维）", itemId, textVector.size());
        } else {
            // TODO(可靠性)：matcher 不可用时记录待补算队列，恢复后批量重算 text_vector
            log.warn("item {} 文本向量化失败（matcher 不可用）", itemId);
        }

        // 图像约定：取首图做 CLIP 向量化
        List<Double> imageVector = null;
        String imageVectorString = null;
        if (!CollectionUtils.isEmpty(item.getImages())) {
            String firstImage = item.getImages().get(0);
            byte[] imageData = readImageBytes(firstImage);
            if (imageData != null) {
                imageVector = matcherClient.embedImage(imageData, firstImage);
                if (imageVector != null) {
                    imageVectorString = toVectorString(imageVector);
                    itemMapper.updateImageVector(itemId, imageVectorString);
                    log.info("item {} image_vector 已写回（{} 维）", itemId, imageVector.size());
                } else {
                    log.warn("item {} 图像向量化失败（matcher 不可用或图片无法解析）", itemId);
                }
            }
        }

        if (textVectorString == null && imageVectorString == null) {
            log.warn("item {} 文本/图像向量均不可用，跳过本次匹配", itemId);
            return;
        }
        computeMatches(item, textVectorString, imageVector);
    }

    /**
     * 匹配计算：文本/图像两路 pgvector 余弦粗筛（各 Top20，取并集）→ 多因子打分 → 超阈值写 matches + 通知双方。
     * 权重：双方都有图像向量时用含图像权重（text/image/time/location），否则用无图像权重且 imageScore 记 null。
     */
    public void computeMatches(Item item, String textVectorString, List<Double> imageVector) {
        boolean isLost = "LOST".equals(item.getType());

        // 候选 id 并集：文本粗筛 + 图像粗筛（各自独立，任一可用即参与）
        Set<Long> candidateIds = new LinkedHashSet<>();
        if (textVectorString != null) {
            List<Map<String, Object>> textCandidates = isLost
                    ? itemMapper.findFoundCandidates(textVectorString, item.getId(), item.getEventTime())
                    : itemMapper.findLostCandidates(textVectorString, item.getId(), item.getEventTime());
            textCandidates.forEach(c -> candidateIds.add(((Number) c.get("id")).longValue()));
        }
        String imageVectorString = imageVector == null ? null : toVectorString(imageVector);
        if (imageVectorString != null) {
            List<Long> imageCandidateIds = isLost
                    ? itemMapper.findFoundCandidateIdsByImage(imageVectorString, item.getId(), item.getEventTime())
                    : itemMapper.findLostCandidateIdsByImage(imageVectorString, item.getId(), item.getEventTime());
            candidateIds.addAll(imageCandidateIds);
        }
        if (candidateIds.isEmpty()) {
            return;
        }

        List<Map<String, Object>> candidates =
                itemMapper.selectFactorInputs(new ArrayList<>(candidateIds), textVectorString);
        Map<Long, String> campusMap = locationMapper.selectList(null).stream()
                .collect(Collectors.toMap(Location::getId, Location::getCampus));

        for (Map<String, Object> candidate : candidates) {
            Long candidateId = ((Number) candidate.get("id")).longValue();
            double textScore = candidate.get("text_score") == null
                    ? 0.0 : ((Number) candidate.get("text_score")).doubleValue();
            // 图像分在 Java 侧算点积（双方都有图像向量才有效）
            Double imageScore = null;
            String candidateImageVector = (String) candidate.get("image_vector_text");
            if (imageVector != null && candidateImageVector != null) {
                imageScore = clamp01(dotProduct(imageVector, parseVector(candidateImageVector)));
            }
            // 原生 SQL Map 返回中 timestamptz 默认映射为 java.sql.Timestamp，需手动转换
            OffsetDateTime candidateEventTime = toOffsetDateTime(candidate.get("event_time"));
            Long candidateLocationId = candidate.get("location_id") == null
                    ? null : ((Number) candidate.get("location_id")).longValue();

            double timeScore = timeScore(item.getEventTime(), candidateEventTime);
            double locationScore = locationScore(item.getLocationId(), candidateLocationId, campusMap);
            double totalScore = imageScore != null
                    ? weightImgText * textScore + weightImgImage * imageScore
                            + weightImgTime * timeScore + weightImgLocation * locationScore
                    : weightText * textScore + weightTime * timeScore + weightLocation * locationScore;
            if (totalScore < threshold) {
                continue;
            }

            Long lostItemId = isLost ? item.getId() : candidateId;
            Long foundItemId = isLost ? candidateId : item.getId();
            int inserted = baseMapper.insertIgnore(lostItemId, foundItemId,
                    scaled(textScore), imageScore == null ? null : scaled(imageScore),
                    scaled(timeScore), scaled(locationScore), scaled(totalScore));
            if (inserted == 0) {
                continue; // 已存在该对，忽略
            }
            Long matchId = baseMapper.selectIdByPair(lostItemId, foundItemId);
            log.info("新匹配命中: lost={} found={} total={} image={}", lostItemId, foundItemId,
                    String.format("%.4f", totalScore),
                    imageScore == null ? "null" : String.format("%.4f", imageScore));

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
            vo.setImageScore(toDouble(m.getImageScore()));
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

    /**
     * 读取首图字节：imageUrl 形如 /files/yyyyMM/uuid.ext，映射到本地上传目录
     */
    private byte[] readImageBytes(String imageUrl) {
        String relative = imageUrl.startsWith("/files/") ? imageUrl.substring("/files/".length()) : imageUrl;
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path path = base.resolve(relative).normalize();
        if (!path.startsWith(base)) {
            log.warn("非法图片路径（越界访问被拒绝）: {}", imageUrl);
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (Exception e) {
            log.warn("读取图片失败 {}：{}", imageUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 解析 pgvector 文本形式 "[0.1,0.2,...]" 为 double 数组
     */
    private double[] parseVector(String vectorText) {
        String body = vectorText.substring(1, vectorText.length() - 1);
        String[] parts = body.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i]);
        }
        return result;
    }

    /**
     * 点积（向量均已 L2 归一化，点积即余弦相似度）
     */
    private double dotProduct(List<Double> a, double[] b) {
        int n = Math.min(a.size(), b.length);
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += a.get(i) * b[i];
        }
        return sum;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
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
