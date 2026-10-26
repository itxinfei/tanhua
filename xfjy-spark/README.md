# xfjy-spark — 双塔推荐模型训练

心飞交友推荐引擎的 AI 训练模块：权重共享对称双塔 + BPR loss，产出 64 维 L2 归一化用户向量，纯 REST 灌入 Qdrant 供线上互惠匹配精排使用。

## 训练流程

```bash
# 1. 安装依赖（Python 3.11+）
pip install -r requirements.txt

# 2. mock 数据训练（无需 MongoDB，验证链路）
python -m train.train --mock --epochs 20

# 3. 真实数据训练（需 MongoDB 中的 user_like 行为 + user_info 画像）
python -m train.train --epochs 20 --neg-sample 4

# 4. 向量灌入 Qdrant（需 Qdrant 已启动）
python -m train.export_embedding
```

产出位于 `output/`（已 gitignore）：

| 文件 | 说明 |
|------|------|
| `model.pt` | 模型权重（续训用） |
| `embeddings.json` | 全量用户向量 `{userId: [64维], ...}` |
| `meta.json` | 训练元信息（维度/用户数/时间） |

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MONGO_URI` | `mongodb://127.0.0.1:27017/xfjy` | MongoDB 连接 |
| `MONGO_DB` | `xfjy` | 库名 |
| `QDRANT_HOST` | `127.0.0.1` | Qdrant 地址 |
| `QDRANT_PORT` | `6333` | Qdrant 端口 |
| `QDRANT_COLLECTION` | `user_embedding` | 集合名（与 Java 一致） |
| `EMBEDDING_DIM` | `64` | 向量维度（与 Java `xfjy.qdrant.vector-size` 对齐） |
| `TRAIN_EPOCHS` | `20` | 训练轮数 |
| `TRAIN_BATCH_SIZE` | `256` | 批大小 |
| `TRAIN_LR` | `0.01` | 学习率 |
| `TRAIN_NEG_SAMPLE` | `4` | 每正样本负采样数 |

## 关键设计

- **权重共享对称双塔**：user/pos/neg 三路共用同一 Tower，cosine(u,v) = cosine(v,u)，支撑 Java 端 T4 互惠分 `sim²` 假设（"我喜欢她"与"她喜欢我"的联合倾向）
- **L2 归一化输出**：点积即 cosine，Qdrant Cosine 距离与 Java `ReciprocalScoringService` 点积计算完全一致
- **BPR loss**：`-logsigmoid(pos_score - neg_score)`，最大化正负样本分差
- **纯 REST 灌 Qdrant**：无 `qdrant-client` 依赖，载荷 `{"id": userId, "vector": [...], "payload": {"userId": userId}}` 与 Java 读取端对齐
- **mock 模式**：隐性偏好规则（同城+6/年龄差+4/标签重叠+3×重叠/异性+5）生成模拟数据，无中间件也能跑通全链路

## 与 Java 联动

```
xfjy-spark 训练 → embeddings.json → Qdrant(user_embedding)
                                          ↑ 读取
xfjy-server ReciprocalScoringService.loadEmbedding / computeReciprocalScore
  → RecommendScoringService 混合评分（w-recip 权重，无 embedding 自动降级）
```

- Java 端配置：`xfjy.qdrant.host/port/collection/vector-size`（`xfjy-server/src/main/resources/application.yml`）
- 互惠分公式：`cosine(my_vec, cand_vec)²`，候选无 embedding 时该维度降级（权重不计入分母）