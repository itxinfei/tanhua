"""模拟数据生成：无 MongoDB 环境时的训练数据兜底（--mock 模式）。

隐性偏好规则模拟真实交友行为分布，保证双塔能学到有意义的向量：
  同城 +6 / 年龄差<=3 +4 / 标签重叠 +3×重叠数 / 异性 +5
分数作为配对采样权重，高偏好配对更可能成为 like 行为。
"""
import random

TAGS_POOL = ["旅行", "美食", "音乐", "电影", "运动", "读书", "摄影", "游戏", "宠物", "编程", "瑜伽", "舞蹈"]
CITIES = ["北京", "上海", "广州", "深圳", "杭州", "成都"]
EDUS = ["高中", "本科", "硕士", "博士"]
INDUSTRIES = ["互联网", "金融", "教育", "医疗", "传媒", "制造"]


def generate_profiles(n=100, seed=42):
    """生成 n 个用户画像，字段与 Java UserInfo 对齐。"""
    rng = random.Random(seed)
    profiles = []
    for i in range(1, n + 1):
        profiles.append({
            "userId": i,
            "sex": "男" if i % 2 == 1 else "女",
            "age": rng.randint(18, 40),
            "city": rng.choice(CITIES),
            "tags": rng.sample(TAGS_POOL, rng.randint(2, 4)),
            "edu": rng.choice(EDUS),
            "industry": rng.choice(INDUSTRIES),
        })
    return profiles


def _preference_score(me, cand):
    """计算隐性偏好分：越高越可能产生 like 行为。"""
    score = 0
    if me["city"] == cand["city"]:
        score += 6
    if abs(me["age"] - cand["age"]) <= 3:
        score += 4
    overlap = len(set(me["tags"]) & set(cand["tags"]))
    score += 3 * overlap
    if me["sex"] != cand["sex"]:
        score += 5
    return score


def generate_behaviors(profiles, n=500, seed=42):
    """按偏好分加权采样生成 like 行为 [(userId, targetUserId), ...]。"""
    rng = random.Random(seed)
    candidates = []
    weights = []
    for me in profiles:
        for cand in profiles:
            if me["userId"] == cand["userId"]:
                continue
            candidates.append((me["userId"], cand["userId"]))
            weights.append(_preference_score(me, cand) + 1)  # +1 避免零权重
    behaviors = []
    for _ in range(n):
        idx = rng.choices(range(len(candidates)), weights=weights, k=1)[0]
        behaviors.append(candidates[idx])
    return behaviors


def load_mock(n_users=100, n_behaviors=500):
    """返回 (profiles, behaviors)，供 data_loader 的 mock 模式使用。"""
    profiles = generate_profiles(n_users)
    behaviors = generate_behaviors(profiles, n_behaviors)
    return profiles, behaviors