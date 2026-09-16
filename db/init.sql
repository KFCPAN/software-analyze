-- =====================================================================
-- 校园失物招领智能匹配平台 · 数据库初始化脚本
-- 数据库：PostgreSQL 16 + pgvector
-- 字符集：UTF-8（容器默认）
-- 说明：本脚本可重复执行（幂等），表不存在才创建
-- 对应报告章节：2.4.1 数据库表 / 2.4.2 表关系 / 2.4.3 表结构
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------
-- 1. 用户表 users
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,           -- 登录名
    password_hash VARCHAR(100) NOT NULL,                  -- BCrypt 哈希
    email         VARCHAR(100) NOT NULL UNIQUE,           -- 校园邮箱（注册验证用）
    phone         VARCHAR(20),                            -- 联系方式（默认隐藏，匹配确认后才可见）
    nickname      VARCHAR(50),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',   -- USER / REVIEWER / ADMIN
    credit_score  INT          NOT NULL DEFAULT 100,      -- 信用分，初始 100
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / BANNED
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE users IS '用户表：师生注册账号，含信用分';

-- ---------------------------------------------------------------------
-- 2. 物品分类表 categories
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(30) NOT NULL UNIQUE,
    sort    INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
COMMENT ON TABLE categories IS '物品分类词表（后台可维护）';

-- ---------------------------------------------------------------------
-- 3. 地点词表 locations（双校区层级 + 班车虚拟地点）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS locations (
    id        BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES locations(id),            -- 层级：校区→楼栋/区域
    name      VARCHAR(50) NOT NULL,
    campus    VARCHAR(20) NOT NULL,                       -- 海淀 / 丰台 / 跨校区（班车）
    level     INT NOT NULL DEFAULT 1,                     -- 1=校区 2=楼栋/区域
    enabled   BOOLEAN NOT NULL DEFAULT TRUE
);
COMMENT ON TABLE locations IS '校园地点词表：结构化发布与匹配"地点接近度"因子的基础';

-- ---------------------------------------------------------------------
-- 4. 失物/招领信息表 items（统一表，type 区分）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS items (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    type         VARCHAR(10)  NOT NULL,                   -- LOST（失物）/ FOUND（招领）
    title        VARCHAR(100) NOT NULL,
    category_id  BIGINT       NOT NULL REFERENCES categories(id),
    location_id  BIGINT       REFERENCES locations(id),   -- 允许为空（"记不清楚了"场景）
    event_time   TIMESTAMPTZ  NOT NULL,                   -- 丢失/拾获发生时间
    description  TEXT         NOT NULL,                   -- 口语化描述
    images       JSONB        NOT NULL DEFAULT '[]',      -- 图片文件路径数组
    status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN',    -- OPEN / MATCHED / CLAIMING / CLOSED / ARCHIVED
    text_vector  vector(512),                             -- BGE-small-zh 文本向量（匹配引擎异步写入）
    image_vector vector(512),                             -- CLIP-ViT-B/32 图像向量（首图）
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_items_type CHECK (type IN ('LOST','FOUND')),
    CONSTRAINT chk_items_status CHECK (status IN ('OPEN','MATCHED','CLAIMING','CLOSED','ARCHIVED'))
);
COMMENT ON TABLE items IS '失物启事与招领信息统一表，向量字段供智能匹配';
CREATE INDEX IF NOT EXISTS idx_items_type_status ON items(type, status);
CREATE INDEX IF NOT EXISTS idx_items_category ON items(category_id);
CREATE INDEX IF NOT EXISTS idx_items_event_time ON items(event_time);
-- 向量索引（余弦距离，HNSW）：匹配引擎近邻检索用
CREATE INDEX IF NOT EXISTS idx_items_text_vec  ON items USING hnsw (text_vector  vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_items_image_vec ON items USING hnsw (image_vector vector_cosine_ops);

-- ---------------------------------------------------------------------
-- 5. 隐藏特征表 item_features（防冒领核心）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS item_features (
    id              BIGSERIAL PRIMARY KEY,
    item_id         BIGINT       NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    feature_key     VARCHAR(100) NOT NULL,                -- 特征问题，如"卡内姓名"
    answer_encrypted TEXT        NOT NULL,                -- 答案密文（应用层加密，不明文入库）
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE item_features IS '只有失主/拾获者知道的隐藏特征，认领核验用，不公开展示';
CREATE INDEX IF NOT EXISTS idx_features_item ON item_features(item_id);

-- ---------------------------------------------------------------------
-- 6. 匹配记录表 matches
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS matches (
    id             BIGSERIAL PRIMARY KEY,
    lost_item_id   BIGINT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    found_item_id  BIGINT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    text_score     NUMERIC(5,4),                          -- 文本相似度 0~1
    image_score    NUMERIC(5,4),                          -- 图像相似度 0~1
    time_score     NUMERIC(5,4),                          -- 时间接近度 0~1
    location_score NUMERIC(5,4),                          -- 地点接近度 0~1
    total_score    NUMERIC(5,4) NOT NULL,                 -- 加权综合分
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',-- PENDING / CONFIRMED / REJECTED
    feedback_by    BIGINT REFERENCES users(id),           -- 确认/否认的用户（负反馈回流）
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_match_pair UNIQUE (lost_item_id, found_item_id),
    CONSTRAINT chk_match_status CHECK (status IN ('PENDING','CONFIRMED','REJECTED'))
);
COMMENT ON TABLE matches IS '智能匹配候选记录：一条失物 × 一条招领 及其各因子得分';
CREATE INDEX IF NOT EXISTS idx_matches_lost ON matches(lost_item_id, status);
CREATE INDEX IF NOT EXISTS idx_matches_found ON matches(found_item_id, status);

-- ---------------------------------------------------------------------
-- 7. 认领单表 claims（状态机驱动）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS claims (
    id              BIGSERIAL PRIMARY KEY,
    found_item_id   BIGINT NOT NULL REFERENCES items(id), -- 被认领的招领信息
    claimant_id     BIGINT NOT NULL REFERENCES users(id), -- 认领人（失主）
    feature_answers JSONB   NOT NULL DEFAULT '[]',        -- 隐藏特征作答记录
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
        -- PENDING(待核验) / APPROVED(核验通过) / REJECTED(驳回)
        -- / DISPUTED(争议仲裁中) / COMPLETED(交接完成) / EXPIRED(超时关闭)
    verify_code     VARCHAR(64),                          -- 一次性交接核销码（APPROVED 时生成）
    reviewed_by     BIGINT REFERENCES users(id),          -- 核验/仲裁人
    reject_reason   VARCHAR(255),
    completed_at    TIMESTAMPTZ,                          -- 扫码核销时间
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_claims_status CHECK (status IN
        ('PENDING','APPROVED','REJECTED','DISPUTED','COMPLETED','EXPIRED'))
);
COMMENT ON TABLE claims IS '认领流程状态机：申请→特征核验→生成核销码→线下交接→完结';
CREATE INDEX IF NOT EXISTS idx_claims_item ON claims(found_item_id, status);
CREATE INDEX IF NOT EXISTS idx_claims_user ON claims(claimant_id, status);

-- ---------------------------------------------------------------------
-- 8. 站内消息表 messages
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS messages (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(30) NOT NULL,   -- MATCH_HIT / CLAIM_PROGRESS / REVIEW_RESULT / SYSTEM
    title      VARCHAR(100) NOT NULL,
    content    TEXT,
    related_id BIGINT,                 -- 关联业务 id（match_id 或 claim_id）
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE messages IS '站内信：匹配命中、认领进度、审核结果通知';
CREATE INDEX IF NOT EXISTS idx_messages_user ON messages(user_id, is_read);

-- ---------------------------------------------------------------------
-- 9. 信用分流水表 credit_logs
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS credit_logs (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    delta      INT    NOT NULL,                           -- 正为加分、负为扣分
    reason     VARCHAR(100) NOT NULL,                     -- 如"如实归还 +5""疑似冒领 -20"
    claim_id   BIGINT REFERENCES claims(id),              -- 关联认领单（可空）
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE credit_logs IS '信用分变动流水，users.credit_score 为其累计结果';
CREATE INDEX IF NOT EXISTS idx_credit_user ON credit_logs(user_id);

-- ---------------------------------------------------------------------
-- 10. 审计日志表 audit_logs
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    operator_id BIGINT REFERENCES users(id),              -- 操作人（审核员/管理员）
    action      VARCHAR(50) NOT NULL,                     -- 如 CLOSE_ITEM / BAN_USER / ARBITRATE
    target_type VARCHAR(30),
    target_id   BIGINT,
    detail      JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE audit_logs IS '后台操作审计：审核、封禁、仲裁全程留痕';

-- =====================================================================
-- 初始化数据
-- =====================================================================

-- 物品分类（与官方系统分类对齐，便于对比实验）
INSERT INTO categories (name, sort) VALUES
    ('手机',1),('钱包',2),('卡类',3),('包',4),('钥匙',5),
    ('书籍',6),('生活用品',7),('电脑',8),('证件',9),('其他',99)
ON CONFLICT (name) DO NOTHING;

-- 地点词表（两校区 + 班车，后续后台可维护扩充）
INSERT INTO locations (id, parent_id, name, campus, level) VALUES
    (1, NULL, '海淀校区', '海淀', 1),
    (2, NULL, '丰台校区', '丰台', 1),
    (3, NULL, '校区班车', '跨校区', 1)
ON CONFLICT DO NOTHING;
INSERT INTO locations (parent_id, name, campus, level) VALUES
    (1,'文华楼','海淀',2),(1,'日新楼','海淀',2),(1,'图书馆','海淀',2),
    (1,'清真食堂','海淀',2),(1,'荟贤楼','海淀',2),(1,'操场','海淀',2),
    (2,'明德楼','丰台',2),(2,'共美厅','丰台',2),(2,'食堂','丰台',2),(2,'操场','丰台',2),
    (3,'丰台-海淀班车','跨校区',2),(3,'海淀-丰台班车','跨校区',2)
ON CONFLICT DO NOTHING;

-- 管理员账号（密码哈希为占位，首次部署后务必通过接口重置密码）
INSERT INTO users (username, password_hash, email, nickname, role) VALUES
    ('admin', 'RESET_ME_ON_FIRST_LOGIN', 'admin@muc.edu.cn', '系统管理员', 'ADMIN')
ON CONFLICT (username) DO NOTHING;
