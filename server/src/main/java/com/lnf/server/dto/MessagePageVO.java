package com.lnf.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 消息分页结果，unreadCount 供前端小红点使用
 */
@Data
@AllArgsConstructor
public class MessagePageVO {

    private Long total;
    private Long page;
    private Long size;
    private Long unreadCount;
    private List<MessageVO> list;
}
