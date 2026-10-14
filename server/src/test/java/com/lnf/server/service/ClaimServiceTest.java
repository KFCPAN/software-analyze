package com.lnf.server.service;

import com.lnf.server.common.AesUtil;
import com.lnf.server.common.BizException;
import com.lnf.server.dto.ClaimCreateRequest;
import com.lnf.server.dto.ClaimReviewRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ClaimService 单元测试：认领状态机（PENDING→APPROVED→COMPLETED，旁路 REJECTED/DISPUTED/EXPIRED）
 * 通过 mock baseMapper 使 MyBatis-Plus ServiceImpl 的 getById/save/updateById/count/getOne 走内存桩。
 */
class ClaimServiceTest {

    private ClaimMapper claimMapper;
    private ItemMapper itemMapper;
    private ItemFeatureMapper itemFeatureMapper;
    private UserMapper userMapper;
    private CreditLogMapper creditLogMapper;
    private MessageService messageService;
    private AesUtil aesUtil;
    private AuditLogService auditLogService;
    private ClaimService service;

    @BeforeEach
    void setUp() {
        claimMapper = mock(ClaimMapper.class);
        itemMapper = mock(ItemMapper.class);
        itemFeatureMapper = mock(ItemFeatureMapper.class);
        userMapper = mock(UserMapper.class);
        creditLogMapper = mock(CreditLogMapper.class);
        messageService = mock(MessageService.class);
        aesUtil = mock(AesUtil.class);
        auditLogService = mock(AuditLogService.class);
        service = new ClaimService(itemMapper, itemFeatureMapper, userMapper,
                creditLogMapper, messageService, aesUtil, auditLogService);
        ReflectionTestUtils.setField(service, "baseMapper", claimMapper);
    }

    private Item foundItem(long id, long ownerId, String status) {
        Item item = new Item();
        item.setId(id);
        item.setUserId(ownerId);
        item.setType("FOUND");
        item.setTitle("黑色钱包");
        item.setStatus(status);
        item.setUpdatedAt(OffsetDateTime.now());
        return item;
    }

    private Claim pendingClaim(long id, long foundItemId, long claimantId) {
        Claim claim = new Claim();
        claim.setId(id);
        claim.setFoundItemId(foundItemId);
        claim.setClaimantId(claimantId);
        claim.setStatus("PENDING");
        claim.setUpdatedAt(OffsetDateTime.now());
        return claim;
    }

    private ClaimReviewRequest reviewRequest(String action, String reason) {
        ClaimReviewRequest r = new ClaimReviewRequest();
        r.setAction(action);
        r.setReason(reason);
        return r;
    }

    // ---------------- create：提交认领申请 ----------------

    @Test
    @DisplayName("create：只能认领 FOUND 信息，否则 3002")
    void create_notFoundType_3002() {
        Item item = foundItem(1, 99, "OPEN");
        item.setType("LOST");
        when(itemMapper.selectById(1L)).thenReturn(item);
        ClaimCreateRequest req = new ClaimCreateRequest();
        req.setFoundItemId(1L);
        BizException e = assertThrows(BizException.class, () -> service.create(7L, req));
        assertEquals(3002, e.getCode());
    }

    @Test
    @DisplayName("create：不能认领自己发布的信息，3004")
    void create_ownItem_3004() {
        when(itemMapper.selectById(1L)).thenReturn(foundItem(1, 7, "OPEN"));
        ClaimCreateRequest req = new ClaimCreateRequest();
        req.setFoundItemId(1L);
        BizException e = assertThrows(BizException.class, () -> service.create(7L, req));
        assertEquals(3004, e.getCode());
    }

    @Test
    @DisplayName("create：已有进行中认领单时重复申请，3005")
    void create_duplicateActive_3005() {
        when(itemMapper.selectById(1L)).thenReturn(foundItem(1, 99, "CLAIMING"));
        when(claimMapper.selectCount(any())).thenReturn(1L);
        ClaimCreateRequest req = new ClaimCreateRequest();
        req.setFoundItemId(1L);
        BizException e = assertThrows(BizException.class, () -> service.create(7L, req));
        assertEquals(3005, e.getCode());
    }

    @Test
    @DisplayName("create：信息非 OPEN/MATCHED 状态不可认领，3003")
    void create_statusNotAllowed_3003() {
        when(itemMapper.selectById(1L)).thenReturn(foundItem(1, 99, "CLOSED"));
        when(claimMapper.selectCount(any())).thenReturn(0L);
        ClaimCreateRequest req = new ClaimCreateRequest();
        req.setFoundItemId(1L);
        BizException e = assertThrows(BizException.class, () -> service.create(7L, req));
        assertEquals(3003, e.getCode());
    }

    @Test
    @DisplayName("create：比对隐藏特征（解密后逐条比对），认领单 PENDING，信息转 CLAIMING 并通知拾获者")
    void create_success_featureMatchAndStatusFlow() {
        when(itemMapper.selectById(1L)).thenReturn(foundItem(1, 99, "OPEN"));
        when(claimMapper.selectCount(any())).thenReturn(0L);
        ItemFeature feature = new ItemFeature();
        feature.setItemId(1L);
        feature.setFeatureKey("钱包颜色");
        feature.setAnswerEncrypted("ENC");
        when(itemFeatureMapper.selectList(any())).thenReturn(List.of(feature));
        when(aesUtil.decrypt("ENC")).thenReturn("黑色");

        ClaimCreateRequest req = new ClaimCreateRequest();
        req.setFoundItemId(1L);
        ClaimCreateRequest.FeatureAnswerInput input = new ClaimCreateRequest.FeatureAnswerInput();
        input.setFeatureKey("钱包颜色");
        input.setAnswer("黑色");
        req.setAnswers(List.of(input));

        service.create(7L, req);

        ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
        verify(claimMapper).insert(claimCaptor.capture());
        Claim saved = claimCaptor.getValue();
        assertEquals("PENDING", saved.getStatus());
        assertEquals(7L, saved.getClaimantId());
        assertTrue(saved.getFeatureAnswers().get(0).getMatched());

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).updateById(itemCaptor.capture());
        assertEquals("CLAIMING", itemCaptor.getValue().getStatus());
        verify(messageService).send(eq(99L), anyString(), contains("认领申请"), anyString(), any());
    }

    // ---------------- review：拾获者核验（状态机核心） ----------------

    @Test
    @DisplayName("review：认领单不存在，3001")
    void review_claimNotFound_3001() {
        when(claimMapper.selectById(1L)).thenReturn(null);
        BizException e = assertThrows(BizException.class,
                () -> service.review(1L, 99L, reviewRequest("APPROVE", null)));
        assertEquals(3001, e.getCode());
    }

    @Test
    @DisplayName("review：非拾获者无权核验，3006")
    void review_notOwner_3006() {
        when(claimMapper.selectById(1L)).thenReturn(pendingClaim(1, 10, 7));
        when(itemMapper.selectById(10L)).thenReturn(foundItem(10, 99, "CLAIMING"));
        BizException e = assertThrows(BizException.class,
                () -> service.review(1L, 55L, reviewRequest("APPROVE", null)));
        assertEquals(3006, e.getCode());
    }

    @Test
    @DisplayName("review：仅 PENDING 可核验，3007")
    void review_notPending_3007() {
        Claim claim = pendingClaim(1, 10, 7);
        claim.setStatus("APPROVED");
        when(claimMapper.selectById(1L)).thenReturn(claim);
        when(itemMapper.selectById(10L)).thenReturn(foundItem(10, 99, "CLAIMING"));
        BizException e = assertThrows(BizException.class,
                () -> service.review(1L, 99L, reviewRequest("APPROVE", null)));
        assertEquals(3007, e.getCode());
    }

    @Test
    @DisplayName("review：APPROVE → APPROVED 并生成 LNF- 前缀核销码，通知认领人")
    void review_approve_success() {
        Claim claim = pendingClaim(1, 10, 7);
        when(claimMapper.selectById(1L)).thenReturn(claim);
        when(itemMapper.selectById(10L)).thenReturn(foundItem(10, 99, "CLAIMING"));
        when(claimMapper.selectCount(any())).thenReturn(0L); // 核销码查重

        service.review(1L, 99L, reviewRequest("APPROVE", null));

        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(claimMapper).updateById(captor.capture());
        Claim updated = captor.getValue();
        assertEquals("APPROVED", updated.getStatus());
        assertNotNull(updated.getVerifyCode());
        assertTrue(updated.getVerifyCode().startsWith("LNF-"));
        assertEquals(99L, updated.getReviewedBy());
        verify(messageService).send(eq(7L), anyString(), contains("已通过"), anyString(), eq(1L));
    }

    @Test
    @DisplayName("review：REJECT 缺原因，3008")
    void review_rejectNoReason_3008() {
        when(claimMapper.selectById(1L)).thenReturn(pendingClaim(1, 10, 7));
        when(itemMapper.selectById(10L)).thenReturn(foundItem(10, 99, "CLAIMING"));
        BizException e = assertThrows(BizException.class,
                () -> service.review(1L, 99L, reviewRequest("REJECT", "  ")));
        assertEquals(3008, e.getCode());
    }

    @Test
    @DisplayName("review：REJECT → REJECTED，无其他进行中认领单时信息释放回 OPEN")
    void review_reject_releaseItem() {
        when(claimMapper.selectById(1L)).thenReturn(pendingClaim(1, 10, 7));
        Item item = foundItem(10, 99, "CLAIMING");
        when(itemMapper.selectById(10L)).thenReturn(item);
        when(claimMapper.selectCount(any())).thenReturn(0L);

        service.review(1L, 99L, reviewRequest("REJECT", "特征答案不符"));

        ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
        verify(claimMapper).updateById(claimCaptor.capture());
        assertEquals("REJECTED", claimCaptor.getValue().getStatus());
        assertEquals("特征答案不符", claimCaptor.getValue().getRejectReason());

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).updateById(itemCaptor.capture());
        assertEquals("OPEN", itemCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("review：DISPUTE → DISPUTED 进入仲裁队列")
    void review_dispute_success() {
        when(claimMapper.selectById(1L)).thenReturn(pendingClaim(1, 10, 7));
        when(itemMapper.selectById(10L)).thenReturn(foundItem(10, 99, "CLAIMING"));

        service.review(1L, 99L, reviewRequest("DISPUTE", "双方对特征认定有分歧"));

        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(claimMapper).updateById(captor.capture());
        assertEquals("DISPUTED", captor.getValue().getStatus());
    }

    // ---------------- verify：扫码核销（事务闭环 + 信用分） ----------------

    private Claim approvedClaim(long id, long foundItemId, long claimantId, OffsetDateTime approvedAt) {
        Claim claim = pendingClaim(id, foundItemId, claimantId);
        claim.setStatus("APPROVED");
        claim.setVerifyCode("LNF-ABCD1234");
        claim.setUpdatedAt(approvedAt);
        return claim;
    }

    @Test
    @DisplayName("verify：核销码无效，3009")
    void verify_invalidCode_3009() {
        when(claimMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.verify(99L, "LNF-NOPE0000"));
        assertEquals(3009, e.getCode());
    }

    @Test
    @DisplayName("verify：非拾获者无权核销，3011")
    void verify_notOwner_3011() {
        when(claimMapper.selectOne(any(), anyBoolean()))
                .thenReturn(approvedClaim(1, 10, 7, OffsetDateTime.now()));
        when(itemMapper.selectById(10L)).thenReturn(foundItem(10, 99, "CLAIMING"));
        BizException e = assertThrows(BizException.class, () -> service.verify(55L, "LNF-ABCD1234"));
        assertEquals(3011, e.getCode());
    }

    @Test
    @DisplayName("verify：核销码超 7 天 → 认领单 EXPIRED，信息释放回 OPEN，报 3009")
    void verify_expired() {
        when(claimMapper.selectOne(any(), anyBoolean()))
                .thenReturn(approvedClaim(1, 10, 7, OffsetDateTime.now().minusDays(8)));
        Item item = foundItem(10, 99, "CLAIMING");
        when(itemMapper.selectById(10L)).thenReturn(item);
        when(claimMapper.selectCount(any())).thenReturn(0L);

        BizException e = assertThrows(BizException.class, () -> service.verify(99L, "LNF-ABCD1234"));
        assertEquals(3009, e.getCode());

        ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
        verify(claimMapper).updateById(claimCaptor.capture());
        assertEquals("EXPIRED", claimCaptor.getValue().getStatus());

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).updateById(itemCaptor.capture());
        assertEquals("OPEN", itemCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("verify：核销成功 → 认领单 COMPLETED、信息 CLOSED、拾获者 +5 / 认领人 +2")
    void verify_success_creditFlow() {
        when(claimMapper.selectOne(any(), anyBoolean()))
                .thenReturn(approvedClaim(1, 10, 7, OffsetDateTime.now()));
        Item item = foundItem(10, 99, "CLAIMING");
        when(itemMapper.selectById(10L)).thenReturn(item);
        User owner = new User();
        owner.setId(99L);
        owner.setCreditScore(100);
        User claimant = new User();
        claimant.setId(7L);
        claimant.setCreditScore(80);
        when(userMapper.selectById(99L)).thenReturn(owner);
        when(userMapper.selectById(7L)).thenReturn(claimant);

        service.verify(99L, "LNF-ABCD1234");

        ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
        verify(claimMapper).updateById(claimCaptor.capture());
        assertEquals("COMPLETED", claimCaptor.getValue().getStatus());
        assertNotNull(claimCaptor.getValue().getCompletedAt());

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).updateById(itemCaptor.capture());
        assertEquals("CLOSED", itemCaptor.getValue().getStatus());

        assertEquals(105, owner.getCreditScore());
        assertEquals(82, claimant.getCreditScore());
        ArgumentCaptor<CreditLog> logCaptor = ArgumentCaptor.forClass(CreditLog.class);
        verify(creditLogMapper, times(2)).insert(logCaptor.capture());
        List<CreditLog> logs = logCaptor.getAllValues();
        assertEquals(5, logs.get(0).getDelta());
        assertEquals(2, logs.get(1).getDelta());
        verify(messageService, times(2)).send(anyLong(), anyString(), contains("交接完成"), anyString(), eq(1L));
    }

    // ---------------- adminArbitrate：管理员仲裁 ----------------

    @Test
    @DisplayName("adminArbitrate：非 DISPUTED 不可仲裁，6004")
    void arbitrate_notDisputed_6004() {
        when(claimMapper.selectById(1L)).thenReturn(pendingClaim(1, 10, 7));
        BizException e = assertThrows(BizException.class,
                () -> service.adminArbitrate(1L, 1L, true, "ok"));
        assertEquals(6004, e.getCode());
    }

    @Test
    @DisplayName("adminArbitrate：仲裁驳回 → REJECTED、认领人信用 -20、信息释放、写审计日志")
    void arbitrate_reject_creditPenalty() {
        Claim claim = pendingClaim(1, 10, 7);
        claim.setStatus("DISPUTED");
        when(claimMapper.selectById(1L)).thenReturn(claim);
        Item item = foundItem(10, 99, "CLAIMING");
        when(itemMapper.selectById(10L)).thenReturn(item);
        User claimant = new User();
        claimant.setId(7L);
        claimant.setCreditScore(80);
        when(userMapper.selectById(7L)).thenReturn(claimant);
        when(claimMapper.selectCount(any())).thenReturn(0L);

        service.adminArbitrate(1L, 1L, false, "冒领属实");

        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(claimMapper).updateById(captor.capture());
        assertEquals("REJECTED", captor.getValue().getStatus());
        assertEquals(60, claimant.getCreditScore());
        verify(auditLogService).record(eq(1L), eq("ARBITRATE"), eq("CLAIM"), eq(1L), anyString());
    }
}
