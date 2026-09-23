"""
校园失物招领智能匹配平台 - 匹配引擎微服务（matcher）

本周范围：BGE-small-zh-v1.5 中文文本相似度（512 维向量）
下周计划：CLIP-ViT-B/32 图像向量（/embed/image、/similarity/image）

与 Java 后端的对接约定见 README.md。
"""
import os

# 国内环境：HuggingFace 走镜像站（需在 import sentence_transformers 之前设置）
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")

from contextlib import asynccontextmanager

import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer

MODEL_NAME = "BAAI/bge-small-zh-v1.5"
VECTOR_DIM = 512

model: SentenceTransformer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动时加载模型（不要每个请求现加载）"""
    global model
    model = SentenceTransformer(MODEL_NAME, device="cpu")
    yield


app = FastAPI(title="lnf-matcher", version="0.1.0", lifespan=lifespan)


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
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/embed/text")
def embed_text(req: TextRequest):
    vector = encode([req.text])[0]
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
