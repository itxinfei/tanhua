"""双塔模型：权重共享对称双塔 + BPR loss。

核心设计：
  - 单 Tower 共享权重 → cosine(u,v) = cosine(v,u)，支撑 T4 互惠分 sim² 假设
  - 输出 L2 归一化 → 点积即 cosine，Qdrant 检索与 Java ReciprocalScoringService 直接点积计算
  - BPR loss：-logsigmoid(pos_score - neg_score)，最大化正负样本分差
"""
import torch
import torch.nn as nn
import torch.nn.functional as F


class Tower(nn.Module):
    """单塔：画像特征 → output_dim 维 L2 归一化向量。

    输入特征（与 FeatureBuilder.build 输出对齐）：
      tags multi-hot + age 归一化标量 + sex/city/edu/industry 类别 id
    结构：tags_proj(Linear→16) + age(1) + 4×Embedding(16) → concat(81) → MLP(128→output_dim) → L2 normalize
    """

    def __init__(self, tag_vocab_size, sex_num, city_num, edu_num, industry_num,
                 embed_dim=16, output_dim=64):
        super().__init__()
        self.tags_proj = nn.Linear(tag_vocab_size, embed_dim)
        self.sex_emb = nn.Embedding(sex_num, embed_dim)
        self.city_emb = nn.Embedding(city_num, embed_dim)
        self.edu_emb = nn.Embedding(edu_num, embed_dim)
        self.industry_emb = nn.Embedding(industry_num, embed_dim)
        concat_dim = embed_dim + 1 + embed_dim * 4  # 16 + 1 + 64 = 81
        self.mlp = nn.Sequential(
            nn.Linear(concat_dim, 128),
            nn.ReLU(),
            nn.Linear(128, output_dim),
        )

    def forward(self, feat):
        """feat: {"tags":(B,V), "age":(B,), "sex":(B,), "city":(B,), "edu":(B,), "industry":(B,)}"""
        tags_vec = self.tags_proj(feat["tags"])
        age = feat["age"].unsqueeze(-1)
        sex_vec = self.sex_emb(feat["sex"])
        city_vec = self.city_emb(feat["city"])
        edu_vec = self.edu_emb(feat["edu"])
        industry_vec = self.industry_emb(feat["industry"])
        concat = torch.cat([tags_vec, age, sex_vec, city_vec, edu_vec, industry_vec], dim=1)
        return F.normalize(self.mlp(concat), p=2, dim=1)


class TwoTowerModel(nn.Module):
    """权重共享对称双塔。

    user/pos/neg 三路输入共用同一 Tower；向量 L2 归一化后点积即 cosine。
    """

    def __init__(self, feature_builder, output_dim=64):
        super().__init__()
        self.tower = Tower(
            tag_vocab_size=feature_builder.tag_vocab_size,
            sex_num=feature_builder.sex_num,
            city_num=feature_builder.city_num,
            edu_num=feature_builder.edu_num,
            industry_num=feature_builder.industry_num,
            output_dim=output_dim,
        )

    def forward(self, user_feat, pos_feat, neg_feat):
        """计算 BPR loss。三路特征分别过共享 Tower，点积为 cosine。"""
        user_vec = self.tower(user_feat)
        pos_vec = self.tower(pos_feat)
        neg_vec = self.tower(neg_feat)
        pos_score = (user_vec * pos_vec).sum(dim=1)
        neg_score = (user_vec * neg_vec).sum(dim=1)
        return -F.logsigmoid(pos_score - neg_score).mean()

    def encode(self, feat):
        """推理：画像特征 → 64 维归一化向量（灌 Qdrant 用）。"""
        return self.tower(feat)