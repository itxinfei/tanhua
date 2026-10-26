package com.xfjy.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * P2 Neo4j 图推荐：喜欢关系同步 + 二度好友召回。
 *
 * <p>纯 REST 调 Neo4j HTTP API（无 driver 依赖），不可用时失败仅打日志，不阻断主链路。
 * 二度好友召回用于"朋友的朋友"推荐扩量，当前为骨架预留（中间件部署后接入召回链路）。
 */
@Service
public class GraphRecommendService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphRecommendService.class);

    /** Neo4j 事务提交端点（4.x/5.x 语法） */
    private static final String TX_COMMIT_PATH = "/db/neo4j/tx/commit";

    @Value("${xfjy.neo4j.uri:http://127.0.0.1:7474}")
    private String neo4jUri;

    @Value("${xfjy.neo4j.username:neo4j}")
    private String neo4jUsername;

    @Value("${xfjy.neo4j.password:}")
    private String neo4jPassword;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 同步喜欢关系：MERGE 两个 User 节点 + LIKES 关系（幂等）。
     * 失败仅打日志，不阻断 likeUser 主链路。
     */
    public void syncLikeRelation(Long userId, Long likeUserId) {
        String cypher = "MERGE (u:User {id: $userId}) "
                + "MERGE (v:User {id: $likeUserId}) "
                + "MERGE (u)-[:LIKES]->(v)";
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("likeUserId", likeUserId);
        execute(cypher, params, "syncLikeRelation");
    }

    /**
     * 删除喜欢关系：DELETE LIKES 边（节点保留）。
     * 失败仅打日志，不阻断 disLikeUser 主链路。
     */
    public void removeLikeRelation(Long userId, Long likeUserId) {
        String cypher = "MATCH (u:User {id: $userId})-[r:LIKES]->(v:User {id: $likeUserId}) DELETE r";
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("likeUserId", likeUserId);
        execute(cypher, params, "removeLikeRelation");
    }

    /**
     * 二度好友召回："我喜欢的人喜欢的人"（排除自己与已喜欢的人）。
     * Neo4j 不可用时返回空列表（降级，不阻断推荐链路）。
     *
     * @param userId 当前用户 id
     * @param limit  召回上限
     * @return 二度好友用户 id 列表
     */
    public List<Long> findSecondDegreeFriends(Long userId, int limit) {
        String cypher = "MATCH (u:User {id: $userId})-[:LIKES]->(:User)-[:LIKES]->(fof:User) "
                + "WHERE fof.id <> $userId "
                + "RETURN DISTINCT fof.id AS id LIMIT $limit";
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("limit", limit);
        try {
            Map<String, Object> resp = executeForQuery(cypher, params, "findSecondDegreeFriends");
            if (resp == null) {
                return Collections.emptyList();
            }
            List<?> results = (List<?>) resp.get("results");
            if (results == null || results.isEmpty()) {
                return Collections.emptyList();
            }
            Map<?, ?> first = (Map<?, ?>) results.get(0);
            List<?> data = (List<?>) first.get("data");
            if (data == null) {
                return Collections.emptyList();
            }
            return data.stream()
                    .map(row -> (Map<?, ?>) row)
                    .map(row -> (List<?>) row.get("row"))
                    .filter(row -> row != null && !row.isEmpty())
                    .map(row -> ((Number) row.get(0)).longValue())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.warn("二度好友召回失败,降级空召回, userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 执行写 Cypher（无返回值解析），失败仅打日志。
     */
    private void execute(String cypher, Map<String, Object> params, String action) {
        try {
            executeForQuery(cypher, params, action);
        } catch (Exception e) {
            LOGGER.warn("Neo4j {} 失败,跳过图同步, userId相关操作", action, e);
        }
    }

    /**
     * 执行 Cypher 并返回原始响应体，异常向上抛出。
     */
    private Map<String, Object> executeForQuery(String cypher, Map<String, Object> params, String action) {
        String url = neo4jUri + TX_COMMIT_PATH;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (neo4jPassword != null && !neo4jPassword.isEmpty()) {
            String auth = neo4jUsername + ":" + neo4jPassword;
            headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        }
        Map<String, Object> statement = new HashMap<>();
        statement.put("statement", cypher);
        statement.put("parameters", params);
        Map<String, Object> body = new HashMap<>();
        body.put("statements", Collections.singletonList(statement));
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
        if (resp != null) {
            List<?> errors = (List<?>) resp.get("errors");
            if (errors != null && !errors.isEmpty()) {
                LOGGER.warn("Neo4j {} 返回错误: {}", action, errors.get(0));
                return null;
            }
        }
        return resp;
    }
}