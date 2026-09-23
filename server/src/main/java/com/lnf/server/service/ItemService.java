package com.lnf.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lnf.server.common.AesUtil;
import com.lnf.server.common.BizException;
import com.lnf.server.dto.ItemCreateRequest;
import com.lnf.server.dto.ItemDetailVO;
import com.lnf.server.dto.ItemListVO;
import com.lnf.server.dto.PageResult;
import com.lnf.server.entity.Category;
import com.lnf.server.entity.Claim;
import com.lnf.server.entity.Item;
import com.lnf.server.entity.ItemFeature;
import com.lnf.server.entity.Location;
import com.lnf.server.entity.User;
import com.lnf.server.mapper.CategoryMapper;
import com.lnf.server.mapper.ClaimMapper;
import com.lnf.server.mapper.ItemFeatureMapper;
import com.lnf.server.mapper.ItemMapper;
import com.lnf.server.mapper.LocationMapper;
import com.lnf.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 失物/招领信息服务
 */
@Service
@RequiredArgsConstructor
public class ItemService extends ServiceImpl<ItemMapper, Item> {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneOffset CAMPUS_ZONE = ZoneOffset.ofHours(8);

    private final CategoryMapper categoryMapper;
    private final LocationMapper locationMapper;
    private final ItemFeatureMapper itemFeatureMapper;
    private final ClaimMapper claimMapper;
    private final UserMapper userMapper;
    private final CategoryService categoryService;
    private final LocationService locationService;
    private final AesUtil aesUtil;
    private final MatchService matchService;

    /**
     * 发布失物/招领信息
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, ItemCreateRequest request) {
        validateRequest(request);

        Item item = new Item();
        item.setUserId(userId);
        applyRequest(item, request);
        item.setStatus(STATUS_OPEN);
        item.setCreatedAt(OffsetDateTime.now());
        item.setUpdatedAt(OffsetDateTime.now());
        save(item);

        saveFeatures(item.getId(), request);

        // 事务提交后异步：文本向量化写回 + 触发智能匹配（matcher 不可用不阻塞发布）
        triggerVectorizeAfterCommit(item.getId());
        // TODO(CLIP 图像向量)：首图 image_vector 下周接入后在此一并触发
        return item.getId();
    }

    /**
     * 信息流检索：仅 OPEN 状态，按创建时间倒序分页
     */
    public PageResult<ItemListVO> pageItems(String type, Long categoryId, Long locationId,
                                            String keyword, long page, long size) {
        if (!"LOST".equals(type) && !"FOUND".equals(type)) {
            throw new BizException(400, "type 只能是 LOST 或 FOUND");
        }
        if (page < 1 || size < 1 || size > 100) {
            throw new BizException(400, "分页参数不合法（page≥1，1≤size≤100）");
        }

        Page<Item> pageParam = new Page<>(page, size);
        Page<Item> result = page(pageParam, new LambdaQueryWrapper<Item>()
                .eq(Item::getStatus, STATUS_OPEN)
                .eq(Item::getType, type)
                .eq(categoryId != null, Item::getCategoryId, categoryId)
                .eq(locationId != null, Item::getLocationId, locationId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Item::getTitle, keyword)
                        .or()
                        .like(Item::getDescription, keyword))
                .orderByDesc(Item::getCreatedAt));

        Map<Long, String> categoryNames = categoryNameMap();
        Map<Long, String> locationNames = locationNameMap();
        List<ItemListVO> list = result.getRecords().stream()
                .map(item -> toListVO(item, categoryNames, locationNames))
                .toList();
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    /**
     * 信息详情（含发布者与联系方式可见性）
     */
    public ItemDetailVO detail(Long itemId, Long currentUserId) {
        Item item = getById(itemId);
        if (item == null) {
            throw new BizException(2004, "信息不存在或已删除");
        }

        boolean contactVisible = isContactVisible(item, currentUserId);

        ItemDetailVO vo = new ItemDetailVO();
        vo.setId(item.getId());
        vo.setType(item.getType());
        vo.setTitle(item.getTitle());
        vo.setCategoryId(item.getCategoryId());
        vo.setCategoryName(categoryNameMap().get(item.getCategoryId()));
        vo.setLocationId(item.getLocationId());
        vo.setLocationName(item.getLocationId() == null ? null : locationNameMap().get(item.getLocationId()));
        vo.setEventTime(format(item.getEventTime()));
        vo.setDescription(item.getDescription());
        vo.setImages(item.getImages() == null ? List.of() : item.getImages());
        vo.setStatus(item.getStatus());
        vo.setCreatedAt(format(item.getCreatedAt()));
        vo.setContactVisible(contactVisible);

        User publisher = userMapper.selectById(item.getUserId());
        if (publisher != null) {
            ItemDetailVO.Publisher p = new ItemDetailVO.Publisher();
            p.setId(publisher.getId());
            p.setNickname(publisher.getNickname());
            p.setCreditScore(publisher.getCreditScore());
            if (contactVisible) {
                p.setPhone(publisher.getPhone());
                p.setEmail(publisher.getEmail());
            }
            vo.setPublisher(p);
        }
        return vo;
    }

    /**
     * 编辑：仅发布者本人，且仅 OPEN 状态可改
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long itemId, Long userId, ItemCreateRequest request) {
        Item item = checkOwnerAndEditable(itemId, userId);
        validateRequest(request);

        // 标题/描述变化会影响文本向量，需异步重算并重新匹配
        boolean textChanged = !item.getTitle().equals(request.getTitle())
                || !item.getDescription().equals(request.getDescription());

        applyRequest(item, request);
        item.setUpdatedAt(OffsetDateTime.now());
        updateById(item);

        // 重建隐藏特征：先删后插
        itemFeatureMapper.delete(new LambdaQueryWrapper<ItemFeature>()
                .eq(ItemFeature::getItemId, itemId));
        saveFeatures(itemId, request);

        if (textChanged) {
            triggerVectorizeAfterCommit(itemId);
        }
    }

    /**
     * 关闭：仅发布者本人，OPEN → CLOSED
     */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long itemId, Long userId) {
        Item item = checkOwnerAndEditable(itemId, userId);
        item.setStatus(STATUS_CLOSED);
        item.setUpdatedAt(OffsetDateTime.now());
        updateById(item);
    }

    /**
     * 我发布的信息列表（可选状态过滤）
     */
    public List<ItemListVO> mine(Long userId, String status) {
        List<Item> items = list(new LambdaQueryWrapper<Item>()
                .eq(Item::getUserId, userId)
                .eq(StringUtils.hasText(status), Item::getStatus, status)
                .orderByDesc(Item::getCreatedAt));
        Map<Long, String> categoryNames = categoryNameMap();
        Map<Long, String> locationNames = locationNameMap();
        return items.stream()
                .map(item -> toListVO(item, categoryNames, locationNames))
                .toList();
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    /**
     * 事务提交后异步触发文本向量化 + 匹配（避免异步线程在事务提交前读不到数据）
     */
    private void triggerVectorizeAfterCommit(Long itemId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    matchService.vectorizeAndMatch(itemId);
                }
            });
        } else {
            matchService.vectorizeAndMatch(itemId);
        }
    }

    private void validateRequest(ItemCreateRequest request) {
        if (!categoryService.existsEnabled(request.getCategoryId())) {
            throw new BizException(2001, "分类不存在或已停用");
        }
        if (request.getLocationId() != null && !locationService.existsEnabled(request.getLocationId())) {
            throw new BizException(2002, "地点不存在或已停用");
        }
        if ("FOUND".equals(request.getType()) && CollectionUtils.isEmpty(request.getHiddenFeatures())) {
            throw new BizException(2003, "招领信息必须填写至少 1 条隐藏特征（防冒领）");
        }
    }

    private void applyRequest(Item item, ItemCreateRequest request) {
        item.setType(request.getType());
        item.setTitle(request.getTitle());
        item.setCategoryId(request.getCategoryId());
        item.setLocationId(request.getLocationId());
        item.setEventTime(parseEventTime(request.getEventTime()));
        item.setDescription(request.getDescription());
        item.setImages(request.getImages() == null ? List.of() : request.getImages());
    }

    private void saveFeatures(Long itemId, ItemCreateRequest request) {
        if (!"FOUND".equals(request.getType()) || CollectionUtils.isEmpty(request.getHiddenFeatures())) {
            return;
        }
        for (ItemCreateRequest.HiddenFeature feature : request.getHiddenFeatures()) {
            ItemFeature entity = new ItemFeature();
            entity.setItemId(itemId);
            entity.setFeatureKey(feature.getFeatureKey());
            entity.setAnswerEncrypted(aesUtil.encrypt(feature.getAnswer()));
            entity.setCreatedAt(OffsetDateTime.now());
            itemFeatureMapper.insert(entity);
        }
    }

    private Item checkOwnerAndEditable(Long itemId, Long userId) {
        Item item = getById(itemId);
        if (item == null) {
            throw new BizException(2004, "信息不存在或已删除");
        }
        if (!item.getUserId().equals(userId)) {
            throw new BizException(2005, "无权操作他人发布的信息");
        }
        if (!STATUS_OPEN.equals(item.getStatus())) {
            throw new BizException(2006, "当前状态不允许该操作（仅 OPEN 状态可编辑/关闭）");
        }
        return item;
    }

    /**
     * 联系方式可见性：发布者本人，或对该招领信息有 APPROVED/COMPLETED 认领单的认领人
     */
    private boolean isContactVisible(Item item, Long currentUserId) {
        if (currentUserId == null) {
            return false;
        }
        if (item.getUserId().equals(currentUserId)) {
            return true;
        }
        return claimMapper.selectCount(new LambdaQueryWrapper<Claim>()
                .eq(Claim::getFoundItemId, item.getId())
                .eq(Claim::getClaimantId, currentUserId)
                .in(Claim::getStatus, List.of("APPROVED", "COMPLETED"))) > 0;
    }

    private ItemListVO toListVO(Item item, Map<Long, String> categoryNames, Map<Long, String> locationNames) {
        ItemListVO vo = new ItemListVO();
        vo.setId(item.getId());
        vo.setType(item.getType());
        vo.setTitle(item.getTitle());
        vo.setCategoryId(item.getCategoryId());
        vo.setCategoryName(categoryNames.get(item.getCategoryId()));
        vo.setLocationName(item.getLocationId() == null ? null : locationNames.get(item.getLocationId()));
        vo.setEventTime(format(item.getEventTime()));
        vo.setCoverImage(CollectionUtils.isEmpty(item.getImages()) ? null : item.getImages().get(0));
        vo.setStatus(item.getStatus());
        vo.setCreatedAt(format(item.getCreatedAt()));
        return vo;
    }

    private Map<Long, String> categoryNameMap() {
        return categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private Map<Long, String> locationNameMap() {
        return locationMapper.selectList(null).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName));
    }

    /**
     * 解析事件时间：支持 "yyyy-MM-dd HH:mm:ss" 与 ISO_OFFSET_DATE_TIME
     */
    private OffsetDateTime parseEventTime(String eventTime) {
        try {
            return OffsetDateTime.parse(eventTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(eventTime, DISPLAY_FORMAT).atOffset(CAMPUS_ZONE);
        } catch (DateTimeParseException e) {
            throw new BizException(400, "eventTime 格式错误，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    /**
     * TIMESTAMPTZ 取出后为 UTC 偏移，统一转成 +08:00（ campus 时区）再格式化返回前端
     */
    private String format(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(CAMPUS_ZONE).format(DISPLAY_FORMAT);
    }
}
