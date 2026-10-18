package com.lnf.server.service;

import com.lnf.server.common.BizException;
import com.lnf.server.entity.Item;
import com.lnf.server.entity.Location;
import com.lnf.server.entity.Match;
import com.lnf.server.mapper.ItemMapper;
import com.lnf.server.mapper.LocationMapper;
import com.lnf.server.mapper.MatchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MatchService 单元测试：多因子打分、语义硬门槛、时间地板、阈值与反馈。
 * 权重与阈值取 application.yml 真实值：无图 0.7/0.15/0.15，有图 0.5/0.2/0.15/0.15，
 * threshold=0.55，minTextScore=0.50，strongImageScore=0.85。
 */
class MatchServiceTest {

    private ItemMapper itemMapper;
    private LocationMapper locationMapper;
    private MatcherClient matcherClient;
    private MessageService messageService;
    private MatchMapper matchMapper;
    private MatchService service;

    private final OffsetDateTime now = OffsetDateTime.now();

    @BeforeEach
    void setUp() {
        itemMapper = mock(ItemMapper.class);
        locationMapper = mock(LocationMapper.class);
        matcherClient = mock(MatcherClient.class);
        messageService = mock(MessageService.class);
        matchMapper = mock(MatchMapper.class);
        service = new MatchService(itemMapper, locationMapper, matcherClient, messageService,
                0.7, 0.15, 0.15,      // 无图权重：文本/时间/地点
                0.5, 0.2, 0.15, 0.15, // 有图权重：文本/图像/时间/地点
                0.55, 0.50, 0.85, "uploads");
        ReflectionTestUtils.setField(service, "baseMapper", matchMapper);
    }

    /** 新发布的 LOST 信息（触发方） */
    private Item lostItem() {
        Item item = new Item();
        item.setId(1L);
        item.setUserId(7L);
        item.setType("LOST");
        item.setTitle("黑色钱包");
        item.setEventTime(now);
        item.setLocationId(10L); // 海淀
        return item;
    }

    private void stubCampusMap() {
        Location hd1 = new Location();
        hd1.setId(10L);
        hd1.setCampus("海淀");
        hd1.setName("文华楼");
        Location hd2 = new Location();
        hd2.setId(11L);
        hd2.setCampus("海淀");
        hd2.setName("图书馆");
        Location ft = new Location();
        ft.setId(20L);
        ft.setCampus("丰台");
        ft.setName("日新楼");
        when(locationMapper.selectList(null)).thenReturn(List.of(hd1, hd2, ft));
    }

    private Map<String, Object> candidate(long id, double textScore, OffsetDateTime eventTime, Long locationId) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("text_score", textScore);
        m.put("image_vector_text", null);
        m.put("event_time", eventTime);
        m.put("location_id", locationId);
        return m;
    }

    private void stubCandidates(List<Map<String, Object>> candidates) {
        List<Map<String, Object>> ids = candidates.stream()
                .map(c -> Map.<String, Object>of("id", c.get("id"))).toList();
        when(itemMapper.findFoundCandidates(anyString(), eq(1L), any())).thenReturn(ids);
        List<Long> candidateIds = candidates.stream()
                .map(c -> ((Number) c.get("id")).longValue()).toList();
        when(itemMapper.selectFactorInputs(argThat(candidateIds::containsAll), anyString()))
                .thenReturn(candidates);
        when(matchMapper.insertIgnore(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(matchMapper.selectIdByPair(any(), any())).thenReturn(999L);
        Item found = new Item();
        found.setId(2L);
        found.setUserId(99L);
        found.setTitle("拾获物品");
        when(itemMapper.selectById(anyLong())).thenReturn(found);
    }

    @Test
    @DisplayName("语义硬门槛：文本分 0.457 的无关物品（足球）即使时间/地点满分也被拦截")
    void semanticGateBlocksIrrelevantItem() {
        stubCampusMap();
        stubCandidates(List.of(candidate(2, 0.457, now, 10L))); // 同地点当天

        service.computeMatches(lostItem(), "[1.0]", null);

        verify(matchMapper, never()).insertIgnore(any(), any(), any(), any(), any(), any(), any());
        verify(messageService, never()).send(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("语义门槛边界：文本分恰好 0.50 不被拦截（小于才淘汰）")
    void textScoreExactlyAtGatePasses() {
        stubCampusMap();
        stubCandidates(List.of(candidate(2, 0.50, now, 10L)));

        service.computeMatches(lostItem(), "[1.0]", null);

        verify(matchMapper, times(1)).insertIgnore(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("时间地板 0.5：跨 7 天的真实匹配不被时间因子拖死，总分 0.5908 过阈值")
    void timeFloorKeepsRealMatch() {
        stubCampusMap();
        // 黑色钱包：文本 0.694，7 天前（时间分地板 0.5），跨校区 0.2
        stubCandidates(List.of(candidate(2, 0.694, now.minusDays(7), 20L)));

        service.computeMatches(lostItem(), "[1.0]", null);

        ArgumentCaptor<BigDecimal> total = ArgumentCaptor.forClass(BigDecimal.class);
        verify(matchMapper).insertIgnore(eq(1L), eq(2L), any(), any(), any(), any(), total.capture());
        assertEquals(0.5908, total.getValue().doubleValue(), 0.001);
        verify(messageService, times(2)).send(anyLong(), eq("MATCH_HIT"), anyString(), anyString(), eq(999L));
    }

    @Test
    @DisplayName("总分低于阈值 0.55 不写入匹配")
    void belowThresholdNotInserted() {
        stubCampusMap();
        // 文本 0.52 过门槛，但 6 天前（时间 0.5）+ 跨校区 0.2 → 0.469 < 0.55
        stubCandidates(List.of(candidate(2, 0.52, now.minusDays(6), 20L)));

        service.computeMatches(lostItem(), "[1.0]", null);

        verify(matchMapper, never()).insertIgnore(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("排序：黑色钱包（文本高/时间久/跨校区）应排在棕色钱包（文本低/时间近/无地点）之前")
    void blackWalletRanksAboveBrown() {
        stubCampusMap();
        stubCandidates(List.of(
                candidate(2, 0.582, now.minusDays(2), null),  // 棕色钱包：0.4074+0.12+0.045=0.5724
                candidate(3, 0.694, now.minusDays(7), 20L)    // 黑色钱包：0.4858+0.075+0.03=0.5908
        ));
        Item found = new Item();
        found.setId(2L);
        found.setUserId(99L);
        when(itemMapper.selectById(anyLong())).thenReturn(found);

        service.computeMatches(lostItem(), "[1.0]", null);

        ArgumentCaptor<Long> foundIds = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<BigDecimal> totals = ArgumentCaptor.forClass(BigDecimal.class);
        verify(matchMapper, times(2)).insertIgnore(eq(1L), foundIds.capture(),
                any(), any(), any(), any(), totals.capture());
        Map<Long, Double> scoreByItem = new HashMap<>();
        for (int i = 0; i < 2; i++) {
            scoreByItem.put(foundIds.getAllValues().get(i), totals.getAllValues().get(i).doubleValue());
        }
        assertTrue(scoreByItem.get(3L) > scoreByItem.get(2L),
                "黑色钱包总分应高于棕色钱包: black=" + scoreByItem.get(3L) + " brown=" + scoreByItem.get(2L));
    }

    @Test
    @DisplayName("强图像证据（≥0.85）可豁免语义门槛，走含图权重 0.5/0.2/0.15/0.15")
    void strongImageEvidenceExemptsGate() {
        stubCampusMap();
        Map<String, Object> c = candidate(2, 0.30, now, 10L); // 文本 0.3 低于门槛
        c.put("image_vector_text", "[0.9]");                   // 图像 0.9 ≥ 0.85 豁免
        stubCandidates(List.of(c));
        when(itemMapper.findFoundCandidateIdsByImage(anyString(), eq(1L), any())).thenReturn(List.of(2L));

        service.computeMatches(lostItem(), "[1.0]", List.of(1.0)); // 点积 1.0*0.9 = 0.9

        ArgumentCaptor<BigDecimal> total = ArgumentCaptor.forClass(BigDecimal.class);
        verify(matchMapper).insertIgnore(eq(1L), eq(2L), any(), any(), any(), any(), total.capture());
        // 0.5*0.3 + 0.2*0.9 + 0.15*1.0 + 0.15*1.0 = 0.63
        assertEquals(0.63, total.getValue().doubleValue(), 0.001);
    }

    // ---------------- feedback：匹配反馈 ----------------

    private Match pendingMatch() {
        Match m = new Match();
        m.setId(5L);
        m.setLostItemId(1L);
        m.setFoundItemId(2L);
        m.setStatus("PENDING");
        return m;
    }

    @Test
    @DisplayName("feedback：匹配记录不存在，5001")
    void feedback_notFound_5001() {
        when(matchMapper.selectById(5L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.feedback(5L, 7L, true));
        assertEquals(5001, e.getCode());
    }

    @Test
    @DisplayName("feedback：非当事双方无权操作，5002")
    void feedback_notInvolved_5002() {
        when(matchMapper.selectById(5L)).thenReturn(pendingMatch());
        Item lost = new Item();
        lost.setUserId(7L);
        Item found = new Item();
        found.setUserId(99L);
        when(itemMapper.selectById(1L)).thenReturn(lost);
        when(itemMapper.selectById(2L)).thenReturn(found);
        BizException e = assertThrows(BizException.class, () -> service.feedback(5L, 55L, true));
        assertEquals(5002, e.getCode());
    }

    @Test
    @DisplayName("feedback：重复反馈，5003")
    void feedback_duplicate_5003() {
        Match m = pendingMatch();
        m.setStatus("CONFIRMED");
        when(matchMapper.selectById(5L)).thenReturn(m);
        Item lost = new Item();
        lost.setUserId(7L);
        when(itemMapper.selectById(1L)).thenReturn(lost);
        when(itemMapper.selectById(2L)).thenReturn(new Item());
        BizException e = assertThrows(BizException.class, () -> service.feedback(5L, 7L, true));
        assertEquals(5003, e.getCode());
    }

    @Test
    @DisplayName("feedback：确认 → CONFIRMED，记录反馈人")
    void feedback_confirm_success() {
        when(matchMapper.selectById(5L)).thenReturn(pendingMatch());
        Item lost = new Item();
        lost.setUserId(7L);
        when(itemMapper.selectById(1L)).thenReturn(lost);
        when(itemMapper.selectById(2L)).thenReturn(new Item());

        service.feedback(5L, 7L, true);

        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchMapper).updateById(captor.capture());
        assertEquals("CONFIRMED", captor.getValue().getStatus());
        assertEquals(7L, captor.getValue().getFeedbackBy());
    }
}
