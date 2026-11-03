"""特征构建：画像 → 双塔输入特征。

输出与 two_tower_model.Tower 输入对齐：
  tags multi-hot + age 归一化 + sex/city/edu/industry 类别 id（Embedding 映射）
"""
import numpy as np


class FeatureBuilder:
    """从画像列表构建词表，并将单条画像转为特征 dict。"""

    def __init__(self, profiles):
        # 标签词表（multi-hot 维度）
        tag_set = sorted({t for p in profiles for t in (p.get("tags") or [])})
        self.tag_vocab = {t: i for i, t in enumerate(tag_set)}
        # 类别映射（0 保留给 UNK，实际类别从 1 起）
        self.sex_map = self._build_map(profiles, "sex")
        self.city_map = self._build_map(profiles, "city")
        self.edu_map = self._build_map(profiles, "edu")
        self.industry_map = self._build_map(profiles, "industry")
        # 年龄归一化基准
        ages = [p.get("age") or 25 for p in profiles]
        self.age_min = min(ages)
        self.age_max = max(ages) or (self.age_min + 1)

    @staticmethod
    def _build_map(profiles, key):
        values = sorted({p.get(key) for p in profiles if p.get(key) is not None})
        return {v: i + 1 for i, v in enumerate(values)}  # 0 = UNK

    def build(self, profile):
        """单条画像 → 特征 dict（值均为 Python 基础类型，批量转 tensor 在训练侧）。"""
        tags = profile.get("tags") or []
        multi_hot = np.zeros(len(self.tag_vocab), dtype=np.float32)
        for t in tags:
            idx = self.tag_vocab.get(t)
            if idx is not None:
                multi_hot[idx] = 1.0
        age = profile.get("age") or 25
        age_norm = (age - self.age_min) / max(1, self.age_max - self.age_min)
        return {
            "tags": multi_hot,
            "age": float(age_norm),
            "sex": self.sex_map.get(profile.get("sex"), 0),
            "city": self.city_map.get(profile.get("city"), 0),
            "edu": self.edu_map.get(profile.get("edu"), 0),
            "industry": self.industry_map.get(profile.get("industry"), 0),
        }

    # ==================== 词表大小（供 Tower 构建 Embedding） ====================
    @property
    def tag_vocab_size(self):
        return max(1, len(self.tag_vocab))

    @property
    def sex_num(self):
        return len(self.sex_map) + 1  # +1 UNK

    @property
    def city_num(self):
        return len(self.city_map) + 1

    @property
    def edu_num(self):
        return len(self.edu_map) + 1

    @property
    def industry_num(self):
        return len(self.industry_map) + 1