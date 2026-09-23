# lnf-matcher · 匹配引擎微服务

校园失物招领智能匹配平台的匹配引擎。当前范围：**BGE-small-zh-v1.5 中文文本相似度**（512 维向量）；CLIP 图像向量下周接入。

## 技术栈

- Python 3.13 + FastAPI + uvicorn
- sentence-transformers + torch（CPU 版）
- 模型：`BAAI/bge-small-zh-v1.5`（启动时加载一次，全局复用）

## 环境搭建（国内网络）

```bash
cd matcher
# 1. 建 venv（系统 Python 3.13）
C:\Python313\python.exe -m venv .venv

# 2. 装依赖（清华镜像；Windows 下 PyPI 的 torch 即 CPU 版）
.venv/Scripts/python.exe -m pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 3. 预下载模型（hf-mirror 镜像，约 100MB；首次启动也会自动下载）
export HF_ENDPOINT=https://hf-mirror.com
.venv/Scripts/python.exe -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('BAAI/bge-small-zh-v1.5', device='cpu')"
```

## 启动

```bash
cd matcher
.venv/Scripts/python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 9000
```

## 接口（与 Java 后端的 HTTP 对接约定）

服务监听 **9000** 端口，全部返回 JSON。文本预处理约定：**调用方传「标题 + 空格 + 描述」拼接好的字符串**，本服务不再做拼接。

| 方法 | 路径 | 请求体 | 响应 |
|------|------|--------|------|
| GET | `/health` | - | `{"status":"ok","model":"BAAI/bge-small-zh-v1.5"}` |
| POST | `/embed/text` | `{"text":"..."}` | `{"vector":[512 维浮点数组]}` |
| POST | `/similarity/text` | `{"textA":"...","textB":"..."}` | `{"score":0~1 余弦相似度}` |
| POST | `/similarity/batch` | `{"query":"...","candidates":["...","..."]}` | `{"scores":[...]}`，与 candidates 等长同序 |

向量已做 L2 归一化，score 为点积（=余弦相似度），并钳制到 [0,1]。

### 对接约定（Java server 侧）

1. **信息发布后**：Java 异步调 `POST /embed/text`（text = 标题 + 空格 + 描述），把返回的 512 维向量写回 `items.text_vector`（pgvector）。
2. **匹配候选粗筛**：用 pgvector 余弦距离做近邻检索（`idx_items_text_vec` HNSW 索引已建）。
3. **精排/补充打分**：对候选列表调 `POST /similarity/batch`。
4. 图像向量 `items.image_vector`（CLIP）下周接入后走同样模式。

## curl 自测示例

```bash
curl http://localhost:9000/health

curl -X POST http://localhost:9000/similarity/text \
  -H "Content-Type: application/json" \
  -d '{"textA":"藏青色长柄雨伞 藏青色长柄伞，伞柄用红色绝缘胶带缠了两圈","textB":"雨伞 日新楼 A 楼 403 第四排捡到一把深色雨伞"}'
# {"score":0.64...}

curl -X POST http://localhost:9000/embed/text \
  -H "Content-Type: application/json" -d '{"text":"黑色卡包 在清真食堂门口捡到"}'
# {"vector":[0.02..., ...]} 共 512 维

curl -X POST http://localhost:9000/similarity/batch \
  -H "Content-Type: application/json" \
  -d '{"query":"藏青色长柄雨伞 ...","candidates":["雨伞 ...","黑色卡包 ..."]}'
# {"scores":[0.64..., 0.37...]}
```

## 实测基准（dataset/模拟数据集模板.xlsx 真实样本）

| 用例 | 分数 |
|------|------|
| P04 正例（伞 vs 伞） | 0.6418 |
| P01 正例（卡包 vs 卡包） | 0.7131 |
| 负例（伞 vs 卡包） | 0.3762 |

正负例差距约 0.27~0.34，文本因子具备区分度。建议匹配阈值初定 **0.55**，上线后按反馈数据调权。
