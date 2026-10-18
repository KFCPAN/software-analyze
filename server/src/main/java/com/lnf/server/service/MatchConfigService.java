package com.lnf.server.service;

import com.lnf.server.common.BizException;
import com.lnf.server.dto.MatchConfigVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统参数配置（后台"系统设置"页持久化）。
 * 使用 key-value 表 system_configs，启动时自动建表（CREATE TABLE IF NOT EXISTS），
 * 兼容已部署的旧数据库，无需手工迁移。
 */
@Service
@RequiredArgsConstructor
public class MatchConfigService {

    private static final double DEFAULT_W1 = 0.6;
    private static final double DEFAULT_W2 = 0.4;
    private static final double DEFAULT_W3 = 0.0;
    private static final double DEFAULT_W4 = 0.0;
    private static final double DEFAULT_THRESHOLD = 0.70;

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    @PostConstruct
    public void initTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS system_configs ("
                        + "config_key VARCHAR(64) PRIMARY KEY, "
                        + "config_value TEXT NOT NULL, "
                        + "updated_at TIMESTAMPTZ NOT NULL DEFAULT now())");
        // 首次启动写入默认值（与匹配引擎 MatchService 权重一致：文本 0.6 + 图像 0.4，阈值 0.70）
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM system_configs WHERE config_key LIKE 'match.%'", Long.class) == 0) {
            saveInternal(defaults());
        }
    }

    public MatchConfigVO get() {
        Map<String, String> kv = jdbcTemplate.queryForList(
                        "SELECT config_key, config_value FROM system_configs WHERE config_key LIKE 'match.%'")
                .stream()
                .collect(Collectors.toMap(r -> (String) r.get("config_key"), r -> (String) r.get("config_value")));
        MatchConfigVO vo = new MatchConfigVO();
        vo.setW1(parse(kv.get("match.w1"), DEFAULT_W1));
        vo.setW2(parse(kv.get("match.w2"), DEFAULT_W2));
        vo.setW3(parse(kv.get("match.w3"), DEFAULT_W3));
        vo.setW4(parse(kv.get("match.w4"), DEFAULT_W4));
        vo.setThreshold(parse(kv.get("match.threshold"), DEFAULT_THRESHOLD));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MatchConfigVO config, Long adminId) {
        if (config == null || config.getW1() == null || config.getW2() == null
                || config.getW3() == null || config.getW4() == null || config.getThreshold() == null) {
            throw new BizException(400, "配置项不能为空（w1/w2/w3/w4/threshold）");
        }
        saveInternal(config);
        auditLogService.record(adminId, "UPDATE_MATCH_CONFIG", "CONFIG", null,
                "{\"w1\":" + config.getW1() + ",\"w2\":" + config.getW2()
                        + ",\"w3\":" + config.getW3() + ",\"w4\":" + config.getW4()
                        + ",\"threshold\":" + config.getThreshold() + "}");
    }

    private void saveInternal(MatchConfigVO config) {
        upsert("match.w1", config.getW1());
        upsert("match.w2", config.getW2());
        upsert("match.w3", config.getW3());
        upsert("match.w4", config.getW4());
        upsert("match.threshold", config.getThreshold());
    }

    private void upsert(String key, Double value) {
        jdbcTemplate.update("INSERT INTO system_configs (config_key, config_value, updated_at) "
                        + "VALUES (?, ?, now()) ON CONFLICT (config_key) "
                        + "DO UPDATE SET config_value = EXCLUDED.config_value, updated_at = now()",
                key, String.valueOf(value));
    }

    private MatchConfigVO defaults() {
        MatchConfigVO vo = new MatchConfigVO();
        vo.setW1(DEFAULT_W1);
        vo.setW2(DEFAULT_W2);
        vo.setW3(DEFAULT_W3);
        vo.setW4(DEFAULT_W4);
        vo.setThreshold(DEFAULT_THRESHOLD);
        return vo;
    }

    private Double parse(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 避免未使用告警（预留：按配置读取权重列表） */
    @SuppressWarnings("unused")
    private List<Double> weights(MatchConfigVO config) {
        return List.of(config.getW1(), config.getW2(), config.getW3(), config.getW4());
    }
}
