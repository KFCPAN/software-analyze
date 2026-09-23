# lnf-server · 校园失物招领智能匹配平台（后端）

《软件系统分析与设计综合实践》课程项目后端工程。当前进度：**M1 工程骨架 + 用户认证**、**M2 信息发布模块**（基础数据 / 文件上传 / 失物招领发布与检索）、**M3 认领流程模块**（claims 状态机 + 核销码 + 信用分联动）、**M4 站内消息**（列表 / 未读数 / 标记已读）、**M5 智能匹配接入**（发布即匹配：文本向量化 → pgvector 粗筛 → 多因子打分 → 命中通知），CLIP 图像向量与后台仲裁后续迭代。

## 匹配引擎对接（M5）

依赖 matcher 微服务（`../matcher/`，FastAPI，端口 9000，见该目录 README）。

- 发布/编辑（标题或描述变更）后，事务提交后异步（`@Async` 线程池）调 matcher `POST /embed/text`，512 维向量写回 `items.text_vector`
- matcher 不可用时仅记 WARN 日志降级，**不阻塞发布**（待补算队列留 TODO）
- 匹配计算：pgvector 余弦距离粗筛 Top20（反向类型 + OPEN + 先丢后捡时间约束）→
  多因子打分 → `totalScore = 0.6*text + 0.2*time + 0.2*location`（权重/阈值见 yml `match.*`），
  ≥ 0.55 写入 matches（唯一约束冲突忽略），命中后双方各收到一条 `MATCH_HIT` 站内信
- 因子规则：timeScore = max(0.3, 1.0 - 0.1×天数)；locationScore = 同地点 1.0 / 同校区 0.6 / 跨校区 0.2 / 任一方为空 0.4

## 技术栈

- Java 17 + Spring Boot 3.3 + Maven
- MyBatis-Plus 3.5 + PostgreSQL 16（pgvector）
- Spring Security + JWT（jjwt 0.12.x）
- Lombok + spring-boot-starter-validation

## 目录结构

```
server/
├── pom.xml
└── src/main/
    ├── java/com/lnf/server/
    │   ├── ServerApplication.java        # 启动类
    │   ├── common/                       # Result / BizException / 全局异常处理 / AesUtil
    │   ├── config/                       # SecurityConfig（含 CORS）/ WebMvcConfig（/files 静态映射）/ MybatisPlusConfig（分页）
    │   ├── security/                     # JwtUtil / JwtAuthFilter / LoginUser
    │   ├── entity/  mapper/  service/    # User / Item / ItemFeature / Category / Location / Claim
    │   ├── dto/                          # 请求/响应对象
    │   └── controller/                   # Auth / User / Item / Category / Location / File
    └── resources/application.yml         # 数据源 / MyBatis-Plus / JWT 配置
```

## 前置条件

1. JDK 17 与 Maven 3.6+（`java -version`、`mvn -v` 自检）
2. 数据库就绪：Docker 容器 `lnf-postgres`（PostgreSQL 16 + pgvector），
   连接 `jdbc:postgresql://localhost:5432/lost_and_found`，用户 `lnf` / 密码 `lnf2026`，
   建表脚本见 `../db/init.sql`（已执行则无需重复）。

## 启动

```bash
cd server
mvn spring-boot:run
# 或先打包再运行
mvn package -DskipTests
java -jar target/lnf-server-0.0.1-SNAPSHOT.jar
```

服务监听 `http://localhost:8080`。前端 Vite 开发服务器（`http://localhost:5173`）已在 CORS 白名单中。

## 接口自测（curl）

### 1. 注册 `POST /api/auth/register`

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","password":"abc123456","email":"zs@muc.edu.cn","nickname":"张三"}'
```

成功响应：

```json
{"code":0,"message":"注册成功","data":null}
```

参数校验失败示例（用户名过短 / 邮箱非法）会返回 `{"code":400,"message":"用户名长度须为 3-20 位; ...","data":null}`；
用户名或邮箱重复返回 `code=1001 / 1002`。

### 2. 登录 `POST /api/auth/login`

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","password":"abc123456"}'
```

成功响应：

```json
{"code":0,"message":"success","data":{"token":"eyJhbGciOi...","user":{"id":2,"username":"zhangsan","nickname":"张三","role":"USER","creditScore":100}}}
```

### 3. 当前用户信息 `GET /api/users/me`（需 JWT）

```bash
TOKEN=上一步返回的token
curl http://localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
```

成功响应（手机号脱敏为 `138****1234` 形式）：

```json
{"code":0,"message":"success","data":{"id":2,"username":"zhangsan","nickname":"张三","email":"zs@muc.edu.cn","phone":null,"role":"USER","creditScore":100}}
```

未携带或携带非法 Token 时返回 HTTP 401 + `{"code":401,"message":"未登录或登录已过期","data":null}`。

## M2 信息发布模块接口

### 4. 分类列表 `GET /api/categories`（无需登录）

```bash
curl http://localhost:8080/api/categories
# {"code":0,"message":"success","data":[{"id":1,"name":"手机"},{"id":2,"name":"钱包"},...]}
```

### 5. 地点词表 `GET /api/locations?campus=`（无需登录，campus 可选：海淀/丰台/跨校区）

```bash
curl "http://localhost:8080/api/locations?campus=海淀"
# data 为两级树：level1 校区 → children level2 楼栋/区域
```

### 6. 上传图片 `POST /api/files`（需 JWT，multipart，字段名 file）

限制：单张 ≤10MB，仅 jpg/jpeg/png/webp。文件存到 `server/uploads/yyyyMM/`（UUID 文件名），
`/files/**` 由 ResourceHandler 映射到该目录，可直接访问。

```bash
curl -X POST http://localhost:8080/api/files -H "Authorization: Bearer $TOKEN" -F "file=@photo.jpg"
# {"code":0,"message":"success","data":{"path":"/files/202609/uuid.jpg"}}
```

### 7. 发布信息 `POST /api/items`（需 JWT）

```bash
curl -X POST http://localhost:8080/api/items \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"FOUND","title":"捡到黑色卡包","categoryId":2,"locationId":4,
       "eventTime":"2026-09-10 13:00:00","description":"在清真食堂门口捡到",
       "images":["/files/202609/uuid.jpg"],
       "hiddenFeatures":[{"featureKey":"卡内姓名","answer":"张三"}]}'
# {"code":0,"message":"发布成功，匹配进行中，命中后将通知您","data":{"id":2}}
```

规则：`type=FOUND` 时 `hiddenFeatures` 至少 1 条（答案 AES 加密入 `item_features` 表，不公开展示）；
`type=LOST` 不需要；`locationId` 可空但传了必须存在；`eventTime` 支持 `yyyy-MM-dd HH:mm:ss` 或 ISO 偏移格式。

### 8. 信息流检索 `GET /api/items`（需 JWT）

```bash
curl "http://localhost:8080/api/items?type=FOUND&keyword=卡包&page=1&size=10" -H "Authorization: Bearer $TOKEN"
# data: {total, page, size, list:[{id,type,title,categoryId,categoryName,locationName,eventTime,coverImage,status,createdAt}]}
```

仅返回 `OPEN` 状态，按创建时间倒序；`keyword` 对标题+描述模糊匹配。

### 9. 信息详情 `GET /api/items/{id}`（需 JWT）

返回全部字段 + `publisher{id,nickname,creditScore}` + `contactVisible`。
`contactVisible=true`（发布者本人，或认领单已 APPROVED/COMPLETED 的认领人）时 publisher 附带 `phone/email`；
为 false 时不带联系方式。`hiddenFeatures` 任何情况都不返回。

### 10. 编辑 `PUT /api/items/{id}` / 关闭 `POST /api/items/{id}/close`（需 JWT）

仅发布者本人，且仅 `OPEN` 状态可操作；编辑请求体同发布。

### 11. 我发布的 `GET /api/items/mine?status=`（需 JWT，status 可选）

```bash
curl "http://localhost:8080/api/items/mine?status=OPEN" -H "Authorization: Bearer $TOKEN"
```

## M3 认领流程模块接口

状态机：`PENDING(待核验) → APPROVED(核验通过,生成核销码) → COMPLETED(扫码核销完成)`；
旁路 `REJECTED(驳回) / DISPUTED(争议仲裁) / EXPIRED(超时关闭)`。
items 联动：存在进行中的认领单（PENDING/APPROVED/DISPUTED）→ `CLAIMING`；核销完成 → `CLOSED`；
驳回/过期且无其他进行中认领单 → 回到 `OPEN`。

### 12. 提交认领申请 `POST /api/claims`（需 JWT）

```bash
curl -X POST http://localhost:8080/api/claims \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"foundItemId":2,"answers":[{"featureKey":"卡内姓名","answer":"张三"}]}'
# {"code":0,"message":"申请已提交，等待拾获者核验","data":{"claimId":1}}
```

规则：仅能对 FOUND 且 OPEN/MATCHED 的信息申请；不能认领自己的；同一用户同一信息仅一条进行中认领单。
服务端解密登记特征逐条比对，作答连同 `matched` 标记存入 `claims.feature_answers`（仅供拾获者参考，不自动通过）。
创建后 items → CLAIMING，并写站内信通知拾获者。

### 13. 我的认领申请 `GET /api/claims/mine`（需 JWT）

返回 `[{id, foundItemId, itemTitle, status, rejectReason, createdAt}]`。

### 14. 待我核验 `GET /api/claims/todo`（需 JWT，拾获者视角）

返回 PENDING 认领单：`[{id, foundItemId, itemTitle, status, claimant{id,nickname,creditScore}, featureAnswers[{featureKey,answer,matched}], createdAt}]`。

### 15. 核验 `POST /api/claims/{id}/review`（需 JWT，仅拾获者，仅 PENDING）

```bash
curl -X POST http://localhost:8080/api/claims/1/review \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"action":"APPROVE"}'                       # 或 {"action":"REJECT","reason":"特征不符"} / {"action":"DISPUTE","reason":"..."}
```

- `APPROVE`：生成核销码 `LNF-XXXXXXXX`（8 位大写字母数字，唯一），7 天有效；通知认领人
- `REJECT`：`reason` 必填；无其他进行中认领单则 items → OPEN；通知认领人
- `DISPUTE`：`reason` 必填；→ DISPUTED（仲裁逻辑第 9 周实现）

### 16. 获取核销码 `GET /api/claims/{id}/code`（需 JWT，仅认领人本人，仅 APPROVED）

```json
{"code":0,"message":"success","data":{"verifyCode":"LNF-OFI9YALL","expiresAt":"2026-09-23 18:00:00"}}
```

### 17. 扫码核销 `POST /api/claims/verify`（需 JWT，仅拾获者）

```bash
curl -X POST http://localhost:8080/api/claims/verify \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"verifyCode":"LNF-OFI9YALL"}'
```

事务内完成：claims → COMPLETED（写 completed_at）、items → CLOSED、
拾获者信用分 +5 / 认领人 +2（各写一条 credit_logs 并更新 users.credit_score）、站内信通知双方。
核销码错误/过期返回 3009（过期时认领单自动 → EXPIRED，items 视情况回 OPEN）。

## M4 站内消息接口

### 18. 消息列表 `GET /api/messages?unreadOnly=&page=&size=`（需 JWT）

```bash
curl "http://localhost:8080/api/messages?unreadOnly=true&page=1&size=10" -H "Authorization: Bearer $TOKEN"
```

```json
{"code":0,"message":"success","data":{"total":2,"page":1,"size":10,"unreadCount":2,
  "list":[{"id":3,"type":"CLAIM_PROGRESS","title":"认领申请已通过","content":"...","relatedId":5,"isRead":false,"createdAt":"2026-09-16 18:00:00"}]}}
```

当前用户消息按 created_at 倒序分页；`unreadOnly=true` 只返回未读；
`unreadCount` 为未读总数（不受 unreadOnly/分页影响，供前端小红点使用）。

### 19. 标记已读 `POST /api/messages/{id}/read`（需 JWT）

仅消息归属人本人可操作（他人返回 4002）；幂等——已是已读也返回成功。

## M5 智能匹配接口

### 20. 匹配候选列表 `GET /api/matches?itemId=`（需 JWT，仅该 item 发布者本人）

```bash
curl "http://localhost:8080/api/matches?itemId=1" -H "Authorization: Bearer $TOKEN"
# [{"matchId":1,"item":{"id":2,"title":"雨伞","coverImage":null,"locationName":"清真食堂","eventTime":"2026-09-15 18:00:00"},
#   "textScore":0.6418,"imageScore":null,"timeScore":0.9792,"locationScore":1.0,"totalScore":0.7809,"status":"PENDING"}]
```

按 totalScore 降序；REJECTED 不返回；`item` 为**对方**信息的摘要。

### 21. 匹配反馈 `POST /api/matches/{id}/feedback`（需 JWT，仅相关 item 发布者）

```bash
curl -X POST http://localhost:8080/api/matches/1/feedback \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"confirm":true}'
```

`confirm=true` → CONFIRMED（前端应跳转认领申请页）；`false` → REJECTED（负反馈，后续不再推荐该对）。仅 PENDING 可反馈。

## 统一返回体与错误码约定

所有接口返回 `{code, message, data}`：`0` = 成功，非 0 = 失败。

| code | 含义 |
|------|------|
| 0    | 成功 |
| 400  | 参数校验失败 |
| 401  | 未登录 / Token 无效或过期 |
| 403  | 无访问权限 |
| 1001 | 用户名已被注册 |
| 1002 | 邮箱已被注册 |
| 1003 | 用户名或密码错误 |
| 1004 | 账号已被封禁 |
| 2001 | 分类不存在或已停用 |
| 2002 | 地点不存在或已停用 |
| 2003 | 招领信息必须填写至少 1 条隐藏特征 |
| 2004 | 信息不存在或已删除 |
| 2005 | 无权操作他人发布的信息 |
| 2006 | 当前状态不允许该操作（仅 OPEN 可编辑/关闭） |
| 2007 | 仅支持 jpg/jpeg/png/webp 格式图片 |
| 2008 | 文件大小超过 10MB 限制 |
| 2009 | 上传文件不能为空 |
| 2010 | 文件保存失败 |
| 3001 | 认领单不存在 |
| 3002 | 只能认领招领（FOUND）信息 |
| 3003 | 该信息当前状态不可认领 |
| 3004 | 不能认领自己发布的信息 |
| 3005 | 已有一条进行中的认领单 |
| 3006 | 无权核验该认领单（仅拾获者） |
| 3007 | 认领单当前状态不允许该操作 |
| 3008 | 驳回/升级仲裁必须填写原因 |
| 3009 | 核销码无效或已过期 |
| 3010 | 无权查看核销码（仅认领人本人） |
| 3011 | 无权核销（仅拾获者） |
| 4001 | 消息不存在 |
| 4002 | 无权操作他人的消息 |
| 5001 | 匹配记录不存在 |
| 5002 | 无权查看/操作他人的匹配记录 |
| 5003 | 该匹配记录已反馈过 |
| 500  | 系统异常 |

## 配置说明（application.yml）

- `spring.datasource`：PostgreSQL 连接信息（URL 含 `stringtype=unspecified`，兼容 jsonb 列写入）
- `spring.servlet.multipart`：上传限制 10MB
- `mybatis-plus.configuration.map-underscore-to-camel-case: true`：下划线转驼峰
- `jwt.secret`：Base64 编码的 HS256 密钥（≥256bit），**生产环境务必用环境变量覆盖**
- `jwt.expire`：Token 有效期，默认 86400000ms（24h）
- `app.upload-dir`：图片上传目录（默认 `uploads`，已加入 .gitignore）
- `app.aes-key`：隐藏特征答案 AES 密钥（16/24/32 字节），**生产环境务必用环境变量覆盖**

> 注意：数据库中预置的 admin 账号 `password_hash` 为占位符（非 BCrypt 哈希），无法直接登录，需后续通过重置密码流程处理。
