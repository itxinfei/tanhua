package com.xfjy.server.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量检索客户端（T3-1 互惠匹配增强）。
 *
 * <p>封装 Qdrant HTTP API，通过 RestClient（Spring 6+ 内置）直接调用，无需额外 Maven 依赖。
 * <ul>
 *   <li>用户向量 upsert：注册/资料变更时将用户画像编码为 64 维 float 向量写入 Qdrant</li>
 *   <li>互惠召回：给定 user_id + 候选集，计算 cosine 相似度补充互惠匹配分</li>
 *   <li>topK 检索：查找与指定用户向量最相似的用户列表</li>
 * </ul>
 *
 * <p><b>降级保护</b>：enabled=false 时不初始化不调用；enabled=true 但 Qdrant 不可用时所有方法 try-catch
 * 返回空结果/默认值，绝不抛出异常影响推荐链路。
 */
@Component
public class QdrantClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(QdrantClient.class);

    /** Qdrant 开关：默认关闭，中间件部署后改为 true */
    @Value("${xfjy.qdrant.enabled:false}")
    private boolean enabled;

    @Value("${xfjy.qdrant.host:127.0.0.1}")
    private String host;

    @Value("${xfjy.qdrant.port:6333}")
    private int port;

    @Value("${xfjy.qdrant.collection:user_embedding}")
    private String collection;

    @Value("${xfjy.qdrant.vector-size:64}")
    private int vectorSize;

    /** Qdrant collection 是否已确认存在（避免每次 upsert 都发 PUT 创建请求） */
    private volatile boolean collectionReady = false;

    /** 有效标志：enabled=true 且初始化成功 */
    private volatile boolean available = false;

    private String baseUrl;

    private final RestTemplate restTemplate;

    public QdrantClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * PostConstruct：确认 collection 存在（幂等）。
     * 全部 try-catch — Qdrant 不可用时绝不抛出导致服务启动失败。
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            LOGGER.info("Qdrant 未启用（xfjy.qdrant.enabled=false），跳过初始化，互惠匹配维度将自动降级");
            return;
        }
        try {
            this.baseUrl = "http://" + host + ":" + port;
            ensureCollection();
            this.available = true;
            LOGGER.info("Qdrant client 初始化成功，collection={} @ {}:{}", collection, host, port);
        } catch (Exception e) {
            this.available = false;
            LOGGER.warn("Qdrant client 初始化失败，互惠匹配维度将自动降级，原因: {}", e.getMessage());
        }
    }

    /**
     * 检查并创建 collection（幂等），64 维 Cosine 距离。
     */
    private void ensureCollection() {
        // 先 GET 检查 collection 是否存在
        try {
            String url = baseUrl + "/collections/" + collection;
            Map<?, ?> resp = restTemplate.getForObject(url, Map.class);
            if (resp != null) {
                LOGGER.debug("Qdrant collection {} 已存在，跳过创建", collection);
                this.collectionReady = true;
                return;
            }
        } catch (Exception e) {
            LOGGER.debug("Qdrant collection {} 不存在，将创建: {}", collection, e.getMessage());
        }

        // 创建 collection
        try {
            String url = baseUrl + "/collections/" + collection;
            Map<String, Object> body = new HashMap<>();
            Map<String, Object> vectors = new HashMap<>();
            vectors.put("size", vectorSize);
            vectors.put("distance", "Cosine");
            body.put("vectors", vectors);
            restTemplate.put(url, body);
            this.collectionReady = true;
            LOGGER.info("Qdrant collection {} 创建成功（{}维 Cosine）", collection, vectorSize);
        } catch (Exception e) {
            throw new RuntimeException("创建 Qdrant collection 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断 Qdrant 是否可用（enabled + 初始化成功）。
     */
    public boolean isAvailable() {
        return available && collectionReady;
    }

    /**
     * upsert 用户向量到 Qdrant。
     *
     * @param userId 用户 ID（作为 Qdrant point id）
     * @param vector 64 维 float 向量（已 L2 归一化）
     */
    public void upsertUser(Long userId, float[] vector) {
        if (!isAvailable() || userId == null || vector == null) {
            return;
        }
        try {
            String url = baseUrl + "/collections/" + collection + "/points";
            Map<String, Object> point = new HashMap<>();
            point.put("id", userId);
            // Float数组转List<Float>以便JSON序列化
            List<Float> vecList = new ArrayList<>(vector.length);
            for (float v : vector) {
                vecList.add(v);
            }
            point.put("vector", vecList);
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            point.put("payload", payload);

            Map<String, Object> body = new HashMap<>();
            body.put("points", Collections.singletonList(point));

            restTemplate.put(url, body);
            LOGGER.debug("Qdrant upsert 用户向量成功, userId={}", userId);
        } catch (Exception e) {
            LOGGER.warn("Qdrant upsert 用户向量失败,降级跳过, userId={}, 原因: {}", userId, e.getMessage());
        }
    }

    /**
     * 查询与指定用户最相似的前 K 个用户（排除自身）。
     *
     * @param userId 用户 ID（用其已存储的 embedding 做 query）
     * @param topK   返回数量上限
     * @return 相似用户 ID 列表（按相似度降序），失败返回空列表
     */
    public List<Long> searchSimilar(Long userId, int topK) {
        if (!isAvailable() || userId == null || topK <= 0) {
            return Collections.emptyList();
        }
        try {
            String url = baseUrl + "/collections/" + collection + "/points/search";
            Map<String, Object> body = new HashMap<>();
            body.put("collection", collection);
            // 用 userId 作为 query point id（Qdrant 支持 id 直接查 stored vector）
            body.put("query", userId);
            body.put("limit", topK + 1); // +1 排除自身
            body.put("with_payload", false);
            body.put("with_vector", false);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, newJsonEntity(body), Map.class);
            if (resp == null) {
                return Collections.emptyList();
            }

            Object result = resp.get("result");
            if (!(result instanceof List)) {
                return Collections.emptyList();
            }

            List<Long> similarUsers = new ArrayList<>();
            for (Object item : (List<?>) result) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Object idObj = ((Map<?, ?>) item).get("id");
                if (idObj instanceof Number) {
                    Long id = ((Number) idObj).longValue();
                    if (!id.equals(userId)) {
                        similarUsers.add(id);
                        if (similarUsers.size() >= topK) {
                            break;
                        }
                    }
                }
            }
            LOGGER.debug("Qdrant topK 检索完成, userId={}, 返回 {} 条", userId, similarUsers.size());
            return similarUsers;
        } catch (Exception e) {
            LOGGER.warn("Qdrant topK 检索失败,降级空召回, userId={}, 原因: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 批量计算 query 向量与候选集 points 的 cosine 相似度（互惠计算用）。
     *
     * <p>从 Qdrant 拉取候选用户的 embedding，与 queryVec 做点积（Cosine 距离下 L2 归一化向量点积=cosine）。
     *
     * @param queryVec    当前用户向量（L2 归一化）
     * @param candidateIds 候选用户 ID 集合
     * @return 候选 ID → cosine 相似度（0~1）映射，失败返回空 map
     */
    public Map<Long, Float> scoreCandidates(float[] queryVec, Collection<Long> candidateIds) {
        if (!isAvailable() || queryVec == null || candidateIds == null || candidateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            String url = baseUrl + "/collections/" + collection + "/points";
            List<Long> ids = new ArrayList<>(candidateIds);
            Map<String, Object> body = new HashMap<>();
            body.put("ids", ids);
            body.put("with_vector", true);
            body.put("with_payload", false);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, newJsonEntity(body), Map.class);
            if (resp == null) {
                return Collections.emptyMap();
            }

            Object result = resp.get("result");
            if (!(result instanceof Map)) {
                return Collections.emptyMap();
            }
            Object pointsObj = ((Map<?, ?>) result).get("points");
            if (!(pointsObj instanceof List)) {
                return Collections.emptyMap();
            }

            Map<Long, Float> scores = new HashMap<>();
            for (Object point : (List<?>) pointsObj) {
                if (!(point instanceof Map)) {
                    continue;
                }
                Object idObj = ((Map<?, ?>) point).get("id");
                Object vectorObj = ((Map<?, ?>) point).get("vector");
                if (!(idObj instanceof Number) || !(vectorObj instanceof List)) {
                    continue;
                }
                Long id = ((Number) idObj).longValue();
                List<?> vecList = (List<?>) vectorObj;
                if (vecList.size() != queryVec.length) {
                    continue;
                }
                float[] candVec = new float[vecList.size()];
                for (int i = 0; i < vecList.size(); i++) {
                    candVec[i] = ((Number) vecList.get(i)).floatValue();
                }
                float dot = 0;
                for (int i = 0; i < queryVec.length; i++) {
                    dot += queryVec[i] * candVec[i];
                }
                // Cosine 距离：cosine = 1 - |dot| when vectors normalized; Qdrant returns dot product
                // 已归一化 → dot 即 cosine 相似度
                scores.put(id, Math.max(-1.0f, Math.min(1.0f, dot)));
            }
            LOGGER.debug("Qdrant 候选集打分完成, 候选 {} 条, 有效打分 {} 条", candidateIds.size(), scores.size());
            return scores;
        } catch (Exception e) {
            LOGGER.warn("Qdrant 候选集打分失败,降级返回空分, 候选 {} 条, 原因: {}", candidateIds.size(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 加载单个用户的 embedding。
     *
     * @param userId 用户 ID
     * @return 64 维向量，失败或无数据返回 null
     */
    public float[] loadEmbedding(Long userId) {
        if (!isAvailable() || userId == null) {
            return null;
        }
        return loadEmbeddings(Collections.singletonList(userId)).get(userId);
    }

    /**
     * 批量加载用户 embedding。
     *
     * @param userIds 用户 ID 列表（不超过 1000，Qdrant 限制）
     * @return userId → float[] 映射
     */
    public Map<Long, float[]> loadEmbeddings(List<Long> userIds) {
        if (!isAvailable() || userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            String url = baseUrl + "/collections/" + collection + "/points";
            Map<String, Object> body = new HashMap<>();
            body.put("ids", userIds);
            body.put("with_vector", true);
            body.put("with_payload", false);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, newJsonEntity(body), Map.class);
            if (resp == null) {
                return Collections.emptyMap();
            }

            Object result = resp.get("result");
            Map<?, ?> resultMap = null;
            if (result instanceof Map) {
                resultMap = (Map<?, ?>) result;
            } else {
                return Collections.emptyMap();
            }

            Object pointsObj = resultMap.get("points");
            if (!(pointsObj instanceof List)) {
                return Collections.emptyMap();
            }

            Map<Long, float[]> embeddings = new HashMap<>();
            for (Object point : (List<?>) pointsObj) {
                if (!(point instanceof Map)) {
                    continue;
                }
                Object idObj = ((Map<?, ?>) point).get("id");
                Object vectorObj = ((Map<?, ?>) point).get("vector");
                if (!(idObj instanceof Number) || !(vectorObj instanceof List)) {
                    continue;
                }
                Long id = ((Number) idObj).longValue();
                List<?> vecList = (List<?>) vectorObj;
                float[] vec = new float[vecList.size()];
                for (int i = 0; i < vecList.size(); i++) {
                    vec[i] = ((Number) vecList.get(i)).floatValue();
                }
                embeddings.put(id, vec);
            }
            return embeddings;
        } catch (Exception e) {
            LOGGER.warn("Qdrant 批量加载 embedding 失败,降级返回空, userIds.size={}, 原因: {}", userIds.size(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 构建带 JSON Content-Type 的 HttpEntity（用于 POST/PUT 请求）。
     */
    private HttpEntity<Map<String, Object>> newJsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
