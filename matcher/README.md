# lnf-matcher · 匹配引擎微服务

校园失物招领智能匹配平台的匹配引擎。当前能力：**BGE-small-zh-v1.5 中文文本向量** + **CLIP-ViT-B/32 图像向量**（均 512 维，与 pgvector 的 `text_vector` / `image_vector` 列对齐）。

## 技术栈

- Python 3.13 + FastAPI + uvicorn
- sentence-transformers + torch（CPU 版）+ Pillow（图像解码）
- 模型：`BAAI/bge-small-zh-v1.5`（文本）、`clip-ViT-B-32`（图像），启动时各加载一次，全局复用

## 环境搭建（国内网络）

```bash
cd matcher
# 1. 建 venv（系统 Python 3.13）
C:\Python313\python.exe -m venv .venv

# 2. 装依赖（清华镜像；Windows 下 PyPI 的 torch 即 CPU 版）
.venv/Scripts/python.exe -m pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 3. 预下载模型（hf-mirror 镜像；BGE 约 100MB，CLIP 约 600MB；首次启动也会自动下载）
export HF_ENDPOINT=https://hf-mirror.com
.venv/Scripts/python.exe -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('BAAI/bge-small-zh-v1.5', device='cpu')"
.venv/Scripts/python.exe -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('clip-ViT-B-32', device='cpu')"
```

## 启动

```bash
cd matcher
.venv/Scripts/python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 9000
```

双模型启动约 30~60 秒（CLIP 较大），`/health` 返回两个模型名即就绪。

## 接口（与 Java 后端的 HTTP 对接约定）

服务监听 **9000** 端口，全部返回 JSON。文本预处理约定：**调用方传「标题 + 空格 + 描述」拼接好的字符串**，本服务不再做拼接。

| 方法 | 路径 | 请求体 | 响应 |
|------|------|--------|------|
| GET | `/health` | - | `{"status":"ok","model":"BAAI/bge-small-zh-v1.5","models":{"text":"...","image":"clip-ViT-B-32"}}` |
| POST | `/embed/text` | `{"text":"..."}` | `{"vector":[512 维浮点数组]}` |
| POST | `/embed/image` | multipart 表单，字段名 `file`（图片文件） | `{"vector":[512 维浮点数组]}`；非图片/空文件/超 10MB 返回 400 |
| POST | `/similarity/text` | `{"textA":"...","textB":"..."}` | `{"score":0~1 余弦相似度}` |
| POST | `/similarity/batch` | `{"query":"...","candidates":["...","..."]}` | `{"scores":[...]}`，与 candidates 等长同序 |

向量均已做 L2 归一化，点积即余弦相似度；`/similarity/*` 的 score 钳制到 [0,1]。
图像预处理：服务端统一 `convert("RGB")`，像素总数上限 4000 万（PIL 防爆图保护），单文件最大 10MB。

### 对接约定（Java server 侧）

1. **信息发布/编辑后**：Java 异步调 `POST /embed/text`（text = 标题 + 空格 + 描述），把向量写回 `items.text_vector`；若 item 有图片，取**首图**字节调 `POST /embed/image`（multipart），写回 `items.image_vector`。两路各自独立容错，互不阻塞。
2. **匹配候选粗筛**：文本、图像两路各用 pgvector 余弦距离取 Top20（`idx_items_text_vec` / `idx_items_image_vec` HNSW 索引已建），候选 id 取并集后再算各因子。
3. **图像分计算**：图像向量以 `image_vector::text` 文本形式带回 Java 侧算点积（向量已归一化，点积 = 余弦相似度）。

## curl 自测示例

```bash
curl http://localhost:9000/health

curl -X POST http://localhost:9000/similarity/text \
  -H "Content-Type: application/json" \
  -d '{"textA":"藏青色长柄雨伞 藏青色长柄伞，伞柄用红色绝缘胶带缠了两圈","textB":"雨伞 日新楼 A 楼 403 第四排捡到一把深色雨伞"}'
# {"score":0.64...}

curl -X POST http://localhost:9000/embed/image -F "file=@/path/to/photo.jpg"
# {"vector":[...]} 共 512 维

curl -X POST http://localhost:9000/embed/image -F "file=@/path/to/not-image.txt"
# HTTP 400 {"detail":"无法解析为图片文件"}
```

## 实测基准

文本（dataset/模拟数据集模板.xlsx 真实样本）：

| 用例 | 分数 |
|------|------|
| P04 正例（伞 vs 伞） | 0.6418 |
| P01 正例（卡包 vs 卡包） | 0.7131 |
| 负例（伞 vs 卡包） | 0.3762 |

图像（CLIP，真实照片实测）：

| 用例 | 分数 |
|------|------|
| 同一张图上传两次 | 1.0000 |
| 同一物品变体（裁剪 85% + 缩放 + 亮度微调） | 0.9414 |
| 无关照片 A / B | 0.6738 / 0.6629 |

正负例均有明显区分度（文本约 0.27~0.34，图像约 0.27）。匹配阈值初定 **0.55**，上线后按反馈数据调权。
