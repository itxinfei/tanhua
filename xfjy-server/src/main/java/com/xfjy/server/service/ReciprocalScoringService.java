package com.xfjy.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * T4 互惠匹配精排服务（cosine² 双向相似度）。
 *
 * <p>双塔权重共享 → cosine(u,v) = cosine(v,u)，互惠分 sim² 同时量化"我喜欢她"与"她喜欢我"的联合倾向。
 * Qdrant 不可用或无 embedding 时返回降级标识（loadEmbedding=null / computeReciprocalScore=-1），
 * 由调用方将互惠权重移出分母，不阻断推荐链路。
 */
@Service
public class ReciprocalScoringService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReciprocalScoringService.class);

    @Value("${xfjy.qdrant.host:127.0.0.1}")
    private String qdrantHost;

    @Value("${xfjy.qdrant.port:6333}")
    private int qdrantPort;

    @Value("${xfjy.qdrant.collection:user_embedding}")
    private String qdrantCollection;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 加载用户 embedding（Qdrant 不可用或无数据时返回 null → 互惠维度降级）。
     *
     * @param userId 用户 id（Qdrant point id 与用户 id 一致）
     * @return 64 维归一化向量，失败返回 null
     */
    public float[] loadEmbedding(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            String url = String.format("http://%s:%d/collections/%s/points", qdrantHost, qdrantPort, qdrantCollection);
            Map<String, Object> reqBody = Map.of(
                    "ids", List.of(userId),
                    "with_vector", true,
                    "with_payload", false);
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, reqBody, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return null;
            }
            Object result = resp.getBody().get("result");
            if (!(result instanceof Map)) {
                return null;
            }
            Object points = ((Map<?, ?>) result).get("points");
            if (!(points instanceof List) || ((List<?>) points).isEmpty()) {
                return null;
            }
            Object point = ((List<?>) points).get(0);
            if (!(point instanceof Map)) {
                return null;
            }
            Object vector = ((Map<?, ?>) point).get("vector");
            if (!(vector instanceof List)) {
                return null;
            }
            List<?> vecList = (List<?>) vector;
            float[] embedding = new float[vecList.size()];
            for (int i = 0; i < vecList.size(); i++) {
                embedding[i] = ((Number) vecList.get(i)).floatValue();
            }
            return embedding;
        } catch (Exception e) {
            LOGGER.warn("Qdrant embedding 加载失败,降级处理, userId={}", userId, e);
            return null;
        }
    }

    /**
     * 计算互惠匹配分：cosine(my, cand)²。
     * 向量已 L2 归一化 → cosine = 点积；平方后放大高相似度区分度。
     *
     * @param myEmbedding   当前用户 embedding（null 时直接降级）
     * @param candidateUserId 候选用户 id
     * @return 0~1 的互惠分；候选无 embedding 或维度不匹配返回 -1（降级标识）
     */
    public double computeReciprocalScore(float[] myEmbedding, Long candidateUserId) {
        if (myEmbedding == null || candidateUserId == null) {
            return -1;
        }
        float[] candEmbedding = loadEmbedding(candidateUserId);
        if (candEmbedding == null || candEmbedding.length != myEmbedding.length) {
            return -1;
        }
        double dot = 0;
        for (int i = 0; i < myEmbedding.length; i++) {
            dot += myEmbedding[i] * candEmbedding[i];
        }
        double cosine = Math.max(-1.0, Math.min(1.0, dot));
        return cosine * cosine;
    }
}