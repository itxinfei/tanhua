"""训练入口：python -m xfjy-spark.train.train --mock

产出（OUTPUT_DIR）：
  model.pt        — 模型权重（续训用）
  embeddings.json — 全量用户向量 {userId: [64维浮点], ...}
  meta.json       — 训练元信息（维度/用户数/时间）
"""
import argparse
import json
import os
import random
from datetime import datetime

import numpy as np
import torch

from . import config
from .data_loader import load_behaviors, load_profiles, build_train_pairs
from .feature_builder import FeatureBuilder
from .two_tower_model import TwoTowerModel


def parse_args():
    parser = argparse.ArgumentParser(description="心飞交友双塔推荐模型训练")
    parser.add_argument("--mock", action="store_true", help="使用模拟数据（无 MongoDB 环境时）")
    parser.add_argument("--epochs", type=int, default=config.EPOCHS, help="训练轮数")
    parser.add_argument("--neg-sample", type=int, default=config.NEG_SAMPLE_SIZE, help="每个正样本的负采样数")
    return parser.parse_args()


def batch_features(builder, profile_batch):
    """画像 batch → tensor 特征 dict（与 Tower.forward 输入对齐）。"""
    feats = [builder.build(p) for p in profile_batch]
    return {
        "tags": torch.tensor(np.stack([f["tags"] for f in feats])),
        "age": torch.tensor([f["age"] for f in feats], dtype=torch.float32),
        "sex": torch.tensor([f["sex"] for f in feats], dtype=torch.long),
        "city": torch.tensor([f["city"] for f in feats], dtype=torch.long),
        "edu": torch.tensor([f["edu"] for f in feats], dtype=torch.long),
        "industry": torch.tensor([f["industry"] for f in feats], dtype=torch.long),
    }


def train(args):
    print(f"[1/5] 加载数据 (mock={args.mock})")
    profiles = load_profiles(mock=args.mock)
    behaviors = load_behaviors(mock=args.mock)
    profile_by_id = {p["userId"]: p for p in profiles}
    print(f"      画像 {len(profiles)} 人, 行为 {len(behaviors)} 条")

    print("[2/5] 构建特征与训练对")
    builder = FeatureBuilder(profiles)
    pairs = build_train_pairs(behaviors, profiles, neg_sample=args.neg_sample)
    print(f"      训练三元组 {len(pairs)} 条 (neg_sample={args.neg_sample})")

    print("[3/5] 训练双塔")
    model = TwoTowerModel(builder, output_dim=config.EMBEDDING_DIM)
    optimizer = torch.optim.Adam(model.parameters(), lr=config.LEARNING_RATE)
    rng = random.Random(42)
    for epoch in range(args.epochs):
        rng.shuffle(pairs)
        total_loss = 0.0
        n_batch = 0
        for i in range(0, len(pairs), config.BATCH_SIZE):
            batch = pairs[i:i + config.BATCH_SIZE]
            user_feat = batch_features(builder, [profile_by_id[u] for u, _, _ in batch])
            pos_feat = batch_features(builder, [profile_by_id[p] for _, p, _ in batch])
            neg_feat = batch_features(builder, [profile_by_id[n] for _, _, n in batch])
            loss = model(user_feat, pos_feat, neg_feat)
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
            n_batch += 1
        avg_loss = total_loss / max(1, n_batch)
        print(f"      epoch {epoch + 1}/{args.epochs}  loss={avg_loss:.4f}")

    print("[4/5] 导出全量 embedding")
    model.eval()
    embeddings = {}
    with torch.no_grad():
        for i in range(0, len(profiles), config.BATCH_SIZE):
            batch_profiles = profiles[i:i + config.BATCH_SIZE]
            feat = batch_features(builder, batch_profiles)
            vecs = model.encode(feat).numpy()
            for p, vec in zip(batch_profiles, vecs):
                embeddings[str(p["userId"])] = [round(float(v), 6) for v in vec]

    print("[5/5] 保存产出")
    os.makedirs(config.OUTPUT_DIR, exist_ok=True)
    torch.save(model.state_dict(), os.path.join(config.OUTPUT_DIR, "model.pt"))
    with open(os.path.join(config.OUTPUT_DIR, "embeddings.json"), "w", encoding="utf-8") as f:
        json.dump(embeddings, f, ensure_ascii=False)
    meta = {
        "dim": config.EMBEDDING_DIM,
        "userCount": len(embeddings),
        "trainedAt": datetime.now().isoformat(),
        "mock": args.mock,
        "epochs": args.epochs,
    }
    with open(os.path.join(config.OUTPUT_DIR, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    print(f"      产出已写入 {config.OUTPUT_DIR} (model.pt / embeddings.json / meta.json)")


if __name__ == "__main__":
    train(parse_args())