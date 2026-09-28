"""
校园失物招领智能匹配平台 - 匹配引擎微服务（matcher）

模型：
- BGE-small-zh-v1.5 中文文本相似度（512 维向量）
- CLIP-ViT-B-32 图像向量（512 维，与文本向量同维度）

与 Java 后端的对接约定见 README.md。
"""
import os

# 国内环境：HuggingFace 走镜像站（需在 import sentence_transformers 之前设置）
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")

from contextlib import asynccontextmanager
from io import BytesIO

import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer

TEXT_MODEL_NAME = "BAAI/bge-small-zh-v1.5"
IMAGE_MODEL_NAME = "clip-ViT-B-32"
VECTOR_DIM = 512
# 上传防护：单文件最大 10MB；像素总数上限（PIL 默认约 0.5 亿，这里收紧到 4000 万）
MAX_UPLOAD_BYTES = 10 * 1024 * 1024
MAX_IMAGE_PIXELS = 40_000_000

Image.MAX_IMAGE_PIXELS = MAX_IMAGE_PIXELS

model: SentenceTransformer | None = None
clip_model: SentenceTransformer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动时加载模型（不要每个请求现加载）"""
    global model, clip_model
    model = SentenceTransformer(TEXT_MODEL_NAME, device="cpu")
    clip_model = SentenceTransformer(IMAGE_MODEL_NAME, device="cpu")
    yield


app = FastAPI(title="lnf-matcher", version="0.2.0", lifespan=lifespan)


class TextRequest(BaseModel):
    # 调用方约定：传「标题 + 空格 + 描述」拼接好的字符串
    text: str = Field(min_length=1)


class TextPairRequest(BaseModel):
    textA: str = Field(min_length=1)
    textB: str = Field(min_length=1)


class BatchRequest(BaseModel):
    query: str = Field(min_length=1)
    candidates: list[str] = Field(min_length=1)


def encode(texts: list[str]) -> np.ndarray:
    """归一化向量，点积即余弦相似度"""
    return model.encode(texts, normalize_embeddings=True)


def clamp01(score: float) -> float:
    return max(0.0, min(1.0, score))


@app.get("/health")
def health():
    return {
        "status": "ok",
        # 旧字段保留兼容
        "model": TEXT_MODEL_NAME,
        "models": {"text": TEXT_MODEL_NAME, "image": IMAGE_MODEL_NAME},
    }


@app.post("/embed/text")
def embed_text(req: TextRequest):
    vector = encode([req.text])[0]
    return {"vector": vector.tolist()}


@app.post("/embed/image")
async def embed_image(file: UploadFile = File(...)):
    """multipart 表单上传图片文件（字段名 file），返回 512 维向量"""
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="空文件")
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=400, detail="图片超过 10MB 限制")
    try:
        image = Image.open(BytesIO(data))
        image.load()
    except Exception:
        raise HTTPException(status_code=400, detail="无法解析为图片文件")
    try:
        image = image.convert("RGB")
    except Exception:
        raise HTTPException(status_code=400, detail="图片格式不支持")
    vector = clip_model.encode(image, normalize_embeddings=True)
    return {"vector": vector.tolist()}


@app.post("/similarity/text")
def similarity_text(req: TextPairRequest):
    vec_a, vec_b = encode([req.textA, req.textB])
    return {"score": clamp01(float(np.dot(vec_a, vec_b)))}


@app.post("/similarity/batch")
def similarity_batch(req: BatchRequest):
    vectors = encode([req.query, *req.candidates])
    query_vec, candidate_vecs = vectors[0], vectors[1:]
    scores = [clamp01(float(np.dot(query_vec, c))) for c in candidate_vecs]
    return {"scores": scores}
