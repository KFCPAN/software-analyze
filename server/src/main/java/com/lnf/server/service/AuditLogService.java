package com.lnf.server.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lnf.server.entity.AuditLog;
import com.lnf.server.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 后台操作审计服务：审核、封禁、仲裁全程留痕
 */
@Service
public class AuditLogService extends ServiceImpl<AuditLogMapper, AuditLog> {

    public void record(Long operatorId, String action, String targetType, Long targetId, String detailJson) {
        AuditLog log = new AuditLog();
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detailJson);
        log.setCreatedAt(OffsetDateTime.now());
        save(log);
    }
}
