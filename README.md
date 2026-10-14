# 校园失物招领智能匹配平台

> 《软件系统分析与设计综合实践》课程项目 · 2026 秋
> 基于文本语义（BGE）+ 图像相似度（CLIP）双通道匹配的双校区失物招领系统

## 团队

见 [members.md](members.md)（每人第一次 push 时在这里登记自己的名字）。

## 目录结构

```
├── docs/            # 所有文档（不进代码目录）
│   ├── report/      #   报告初稿.docx
│   ├── uml/         #   用例图/类图/时序图/ER图（.drawio 源文件 + PNG）
│   ├── ui/          #   原型图、页面层级树图
│   ├── api/         #   Apifox 上手指南
│   ├── research/    #   问卷、竞品分析、匹配实验结果
│   ├── 启动指南.md   #   ★ 全栈启动步骤（新成员先看这个）
│   └── 联调问题清单*.md
├── api/             # openapi.json（接口契约，Apifox 可导入）
├── dataset/         # 匹配引擎模拟数据集
│   ├── sim_dataset.xlsx
│   └── photos/      #   照片，文件名 = 数据编号.jpg
├── db/              # 建表 SQL（init.sql，10 张表）
├── scripts/         # 数据导入、匹配实验评估脚本
├── server/          # Java 后端（Spring Boot 3，31 个接口）
├── matcher/         # Python 匹配引擎（FastAPI + BGE + CLIP）
├── web/             # Vue3 前端（Vite + Element Plus）
└── deploy/          # docker-compose.yml（一键起 PostgreSQL+pgvector）
```

## 当前进度（截至第 7 周）

- 后端 31 个接口全部实现并实测通过（含第 6 周新增：消息批量已读 / 类型筛选、信用流水、认领问题回显）
- 匹配引擎双模型（BGE 文本 + CLIP 图像）跑通，阈值实验完成（推荐 0.70，详见 docs/research/匹配实验结果.md）
- 前端 19 个页面完成并与真实接口联调
- 数据库已导入 205 条演示数据（102 失物 + 103 招领，含 205 张实物照片）
- UML 图（用例图 / 类图 / 时序图 / ER 图）见 docs/uml/

## 分支规范

- `main`：受保护，只接受 PR 合并，始终保持可运行状态；
- `dev`：开发主分支，每周的提交物从这里打 tag（如 `week7-submit`）；
- 个人开发分支：`dev-姓名拼音`（如 `dev-zhangsan`），日常提交到自己的分支，完成后合入 `dev`；
- 大功能可用 `feat/功能名` 短分支，合并后删除。

## Commit 规范

格式：`类型(范围): 一句话描述`

| 类型 | 用途 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(server): 发布招领信息接口` |
| `fix` | 修 bug | `fix(web): 修复详情页图片不显示` |
| `docs` | 文档/图 | `docs(uml): 添加 M2 模块用例图` |
| `data` | 数据集 | `data: 新增 30 条匹配对样本` |
| `test` | 测试 | `test: M5 认领流程测试用例` |
| `chore` | 构建/杂项 | `chore: 初始化 Spring Boot 工程` |

**要求**：做完一点提交一点，不要攒一周一个大 commit——commit 记录就是期末分工量的依据。

## 每周提交物对应

| 周次 | 课程提交物 | 对应仓库操作 |
|------|-----------|--------------|
| 第 7 周 | 源码（commit id） | 在 `dev` 打 tag `week7-submit` |
| 第 9 周 | 更新代码 + 问题清单 | tag `week9-submit` + issue 列表导出 |
| 第 12 周 | 最终版 | `main` 打 tag `final` |

## 快速开始（开发环境）

**详细步骤、验证方法、常见问题见 [docs/启动指南.md](docs/启动指南.md)。** 简版：

```bash
# 1. 起数据库（需要 Docker Desktop）
docker start lnf-postgres
#    首次搭建：cd deploy && docker compose up -d，再执行 db/init.sql
#    国内直连 Docker Hub 可能拉取失败，改用加速站拉一次再启动：
#    docker pull docker.m.daocloud.io/pgvector/pgvector:pg16
#    docker tag  docker.m.daocloud.io/pgvector/pgvector:pg16 pgvector/pgvector:pg16

# 2. 匹配引擎（端口 9000）
cd matcher && .venv/Scripts/python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 9000

# 3. 后端（端口 8080）—— ⚠️ 不要用 mvn spring-boot:run（中文路径必现 ClassNotFoundException）
#    方式 A：IDEA 打开 server/，Project SDK 选 17，运行 ServerApplication
#    方式 B：cd server && java -jar target/lnf-server-0.0.1-SNAPSHOT.jar（须用 JDK 17）

# 4. 前端（端口 5173）
cd web && pnpm install && pnpm dev
```

演示账号：`admin` / `demo01`~`demo10`，密码统一 `Demo123456`（D 大写，区分大小写）。
