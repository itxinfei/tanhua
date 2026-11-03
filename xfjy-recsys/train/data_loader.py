"""数据加载：MongoDB 行为/画像 或 mock 数据，统一输出格式。

真实模式：
  - 行为：MongoDB user_like 集合（userId, likeUserId），由 T5 行为明细冗余写入
  - 画像：MongoDB user_info 集合（与 Java UserInfo 字段对齐）
mock 模式：mock_data 生成的隐性偏好模拟数据。
"""
from . import config
from .mock_data import load_mock


def load_behaviors(mock=False):
    """加载 like 行为列表 [(userId, targetUserId), ...]。"""
    if mock:
        _, behaviors = load_mock()
        return behaviors
    from pymongo import MongoClient
    client = MongoClient(config.MONGO_URI)
    try:
        db = client[config.MONGO_DB]
        rows = db.user_like.find({}, {"userId": 1, "likeUserId": 1, "_id": 0})
        return [(r["userId"], r["likeUserId"]) for r in rows if r.get("userId") and r.get("likeUserId")]
    finally:
        client.close()


def load_profiles(mock=False):
    """加载用户画像列表 [{userId, sex, age, city, tags, edu, industry}, ...]。"""
    if mock:
        profiles, _ = load_mock()
        return profiles
    from pymongo import MongoClient
    client = MongoClient(config.MONGO_URI)
    try:
        db = client[config.MONGO_DB]
        rows = db.user_info.find({}, {"_id": 0})
        profiles = []
        for r in rows:
            tags = r.get("tags") or ""
            profiles.append({
                "userId": r.get("userId"),
                "sex": r.get("sex"),
                "age": r.get("age"),
                "city": r.get("city"),
                "tags": [t.strip() for t in str(tags).split(",") if t.strip()],
                "edu": r.get("edu"),
                "industry": r.get("industry"),
            })
        return [p for p in profiles if p["userId"] is not None]
    finally:
        client.close()


def build_train_pairs(behaviors, profiles, neg_sample=None):
    """构建训练三元组：每个正样本配 neg_sample 个负样本。

    :return: [(user_id, pos_id, neg_id), ...]
    """
    import random
    n_neg = neg_sample if neg_sample is not None else config.NEG_SAMPLE_SIZE
    rng = random.Random(42)
    profile_ids = [p["userId"] for p in profiles]
    id_set = set(profile_ids)
    pairs = []
    for user_id, target_id in behaviors:
        if user_id not in id_set or target_id not in id_set:
            continue
        for _ in range(n_neg):
            neg_id = rng.choice(profile_ids)
            # 负样本不能是自己，尽量避免是已喜欢的人（简化处理：只排除自己）
            while neg_id == user_id:
                neg_id = rng.choice(profile_ids)
            pairs.append((user_id, target_id, neg_id))
    return pairs