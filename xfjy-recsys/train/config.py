"""训练配置：环境变量驱动，键名与 Java application.yml 对齐。"""
import os

# ==================== 数据源 ====================
# MongoDB 连接（行为明细 user_like + 特征宽表 user_feature）
MONGO_URI = os.getenv("MONGO_URI", "mongodb://127.0.0.1:27017/xfjy")
MONGO_DB = os.getenv("MONGO_DB", "xfjy")

# ==================== Qdrant（纯 REST，无 qdrant-client 依赖） ====================
QDRANT_HOST = os.getenv("QDRANT_HOST", "127.0.0.1")
QDRANT_PORT = int(os.getenv("QDRANT_PORT", "6333"))
QDRANT_COLLECTION = os.getenv("QDRANT_COLLECTION", "user_embedding")

# ==================== 模型（与 Java xfjy.qdrant.vector-size 对齐） ====================
EMBEDDING_DIM = int(os.getenv("EMBEDDING_DIM", "64"))

# ==================== 训练超参 ====================
EPOCHS = int(os.getenv("TRAIN_EPOCHS", "20"))
BATCH_SIZE = int(os.getenv("TRAIN_BATCH_SIZE", "256"))
LEARNING_RATE = float(os.getenv("TRAIN_LR", "0.01"))
# 每个正样本采样的负样本数
NEG_SAMPLE_SIZE = int(os.getenv("TRAIN_NEG_SAMPLE", "4"))

# ==================== 输出 ====================
# 训练产出目录（model.pt / embeddings.json / meta.json）
OUTPUT_DIR = os.getenv("OUTPUT_DIR", os.path.join(os.path.dirname(os.path.dirname(__file__)), "output"))

# Qdrant 批量 upsert 大小
QDRANT_UPSERT_BATCH = int(os.getenv("QDRANT_UPSERT_BATCH", "100"))