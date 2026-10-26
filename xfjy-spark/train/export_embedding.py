"""Embedding 导出：训练产出 → Qdrant（纯 REST，无 qdrant-client 依赖）。

载荷与 Java ReciprocalScoringService 读取端对齐：
  point id = 用户 id（整数）
  payload  = {"userId": <int>}
  vector   = 64 维 L2 归一化浮点数组
距离度量 Cosine（向量已归一化，cosine = 点积，与 Java 端点积计算一致）。

用法：python -m xfjy-spark.train.export_embedding
"""
import json
import os

import requests

from . import config


def _base_url():
    return f"http://{config.QDRANT_HOST}:{config.QDRANT_PORT}"


def ensure_collection():
    """确保 collection 存在（已存在则跳过创建）。"""
    url = f"{_base_url()}/collections/{config.QDRANT_COLLECTION}"
    resp = requests.get(url, timeout=10)
    if resp.status_code == 200 and resp.json().get("result", {}).get("status") == "green":
        print(f"      collection 已存在: {config.QDRANT_COLLECTION}")
        return
    create_body = {
        "vectors": {"size": config.EMBEDDING_DIM, "distance": "Cosine"},
    }
    resp = requests.put(url, json=create_body, timeout=30)
    resp.raise_for_status()
    print(f"      collection 已创建: {config.QDRANT_COLLECTION} (size={config.EMBEDDING_DIM}, distance=Cosine)")


def upsert_batch(points):
    """批量 upsert 点位。points: [{"id": int, "vector": [...], "payload": {"userId": int}}, ...]"""
    url = f"{_base_url()}/collections/{config.QDRANT_COLLECTION}/points"
    resp = requests.put(url, json={"points": points}, timeout=30)
    resp.raise_for_status()


def load_embeddings(embeddings_path=None):
    """读取训练产出的 embeddings.json。"""
    path = embeddings_path or os.path.join(config.OUTPUT_DIR, "embeddings.json")
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def export(embeddings_path=None):
    """全量灌 Qdrant：ensure collection → 分批 upsert。"""
    print("[1/3] 读取 embedding 产出")
    embeddings = load_embeddings(embeddings_path)
    print(f"      {len(embeddings)} 个用户向量")

    print("[2/3] 确保 Qdrant collection")
    ensure_collection()

    print("[3/3] 分批 upsert")
    points = []
    for user_id_str, vector in embeddings.items():
        user_id = int(user_id_str)
        points.append({
            "id": user_id,
            "vector": vector,
            "payload": {"userId": user_id},
        })
    total = 0
    for i in range(0, len(points), config.QDRANT_UPSERT_BATCH):
        batch = points[i:i + config.QDRANT_UPSERT_BATCH]
        upsert_batch(batch)
        total += len(batch)
        print(f"      已写入 {total}/{len(points)}")
    print(f"      完成：{total} 个向量已灌入 {config.QDRANT_COLLECTION}")


if __name__ == "__main__":
    export()