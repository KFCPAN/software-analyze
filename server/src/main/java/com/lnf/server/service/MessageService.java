package com.lnf.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lnf.server.common.BizException;
import com.lnf.server.dto.MessagePageVO;
import com.lnf.server.dto.MessageVO;
import com.lnf.server.entity.Message;
import com.lnf.server.mapper.MessageMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 站内消息服务
 */
@Service
public class MessageService extends ServiceImpl<MessageMapper, Message> {

    public static final String TYPE_CLAIM_PROGRESS = "CLAIM_PROGRESS";

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneOffset CAMPUS_ZONE = ZoneOffset.ofHours(8);

    /**
     * 发送站内信
     */
    public void send(Long userId, String type, String title, String content, Long relatedId) {
        Message message = new Message();
        message.setUserId(userId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setRelatedId(relatedId);
        message.setIsRead(false);
        message.setCreatedAt(OffsetDateTime.now());
        save(message);
    }

    /**
     * 当前用户消息列表：created_at 倒序分页，可按未读过滤，附未读总数
     */
    public MessagePageVO pageMessages(Long userId, boolean unreadOnly, long page, long size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BizException(400, "分页参数不合法（page≥1，1≤size≤100）");
        }
        Page<Message> result = page(new Page<>(page, size), new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(unreadOnly, Message::getIsRead, false)
                .orderByDesc(Message::getCreatedAt));
        Long unreadCount = baseMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, false));
        List<MessageVO> list = result.getRecords().stream().map(this::toVO).toList();
        return new MessagePageVO(result.getTotal(), result.getCurrent(), result.getSize(), unreadCount, list);
    }

    /**
     * 标记已读：仅归属人本人，幂等（已读也返回成功）
     */
    public void markRead(Long messageId, Long userId) {
        Message message = getById(messageId);
        if (message == null) {
            throw new BizException(4001, "消息不存在");
        }
        if (!message.getUserId().equals(userId)) {
            throw new BizException(4002, "无权操作他人的消息");
        }
        if (Boolean.TRUE.equals(message.getIsRead())) {
            return; // 幂等：已是已读直接成功
        }
        message.setIsRead(true);
        updateById(message);
    }

    private MessageVO toVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setType(message.getType());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setRelatedId(message.getRelatedId());
        vo.setIsRead(message.getIsRead());
        vo.setCreatedAt(message.getCreatedAt() == null ? null
                : message.getCreatedAt().atZoneSameInstant(CAMPUS_ZONE).format(DISPLAY_FORMAT));
        return vo;
    }
}
