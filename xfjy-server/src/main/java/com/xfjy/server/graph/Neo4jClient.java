package com.xfjy.server.graph;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j 图数据库客户端（P2 二度好友推荐）。
 *
 * <p>基于 Neo4j Java Driver 5.x（Bolt 协议），封装用户节点和 FRIEND 关系操作。
 * <ul>
 *   <li>新建/更新 User 节点</li>
 *   <li>建立好友关系（FRIEND 边，幂等 MERGE）</li>
 *   <li>删除好友关系</li>
 *   <li>二度好友查询：MATCH (u:User)-[:FRIEND]-()-[:FRIEND]-(reco:User) 按共同好友数排序</li>
 * </ul>
 *
 * <p><b>降级保护</b>：enabled=false 时不初始化不调用；enabled=true 但 Neo4j 不可用时所有方法
 * try-catch 返回空结果/默认值，绝不抛出异常影响推荐链路。服务关闭时通过 @PreDestroy 释放 driver。
 */
@Component
public class Neo4jClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jClient.class);

    /** Neo4j 开关：默认关闭，中间件部署后改为 true */
    @Value("${xfjy.neo4j.enabled:false}")
    private boolean enabled;

    @Value("${xfjy.neo4j.bolt-uri:bolt://127.0.0.1:7687}")
    private String boltUri;

    @Value("${xfjy.neo4j.username:neo4j}")
    private String username;

    @Value("${xfjy.neo4j.password:}")
    private String password;

    /** Neo4j Java Driver（Bolt 协议）实例 */
    private volatile Driver driver;

    /** 有效标志：enabled=true 且 driver 初始化成功 */
    private volatile boolean available = false;

    /**
     * 延迟初始化 driver（Spring 不通过构造函数注入，而是在首次使用时初始化，
     * 避免 Neo4j 不可用时 Bean 创建失败导致整个应用启动失败）。
     */
    private synchronized void ensureDriver() {
        if (driver != null) {
            return;
        }
        if (!enabled) {
            return;
        }
        try {
            if (password != null && !password.isEmpty()) {
                this.driver = GraphDatabase.driver(boltUri, AuthTokens.basic(username, password));
            } else {
                this.driver = GraphDatabase.driver(boltUri);
            }
            // 验证连接
            driver.verifyConnectivity();
            this.available = true;
            LOGGER.info("Neo4j driver 初始化成功, bolt-uri={}", boltUri);
            // 创建约束和索引（幂等）
            createConstraints();
        } catch (Exception e) {
            this.available = false;
            LOGGER.warn("Neo4j driver 初始化失败,二度好友推荐将自动降级, bolt-uri={}, 原因: {}", boltUri, e.getMessage());
            // 清理失败的 driver
            if (driver != null) {
                try {
                    driver.close();
                } catch (Exception ignored) {
                    // ignore
                }
                driver = null;
            }
        }
    }

    /**
     * 创建 User 节点的唯一约束和索引（幂等）。
     */
    private void createConstraints() {
        if (!isAvailable()) {
            return;
        }
        try (Session session = driver.session()) {
            // 5.x 语法：CREATE CONSTRAINT IF NOT EXISTS（Neo4j 5.x 支持 IF NOT EXISTS）
            session.run("CREATE CONSTRAINT user_id_constraint IF NOT EXISTS FOR (u:User) REQUIRE u.userId IS UNIQUE");
            LOGGER.debug("Neo4j 约束创建/已存在: user_id_constraint");
        } catch (Exception e) {
            // 约束失败不影响核心功能（MERGE 仍然可用），仅打日志
            LOGGER.warn("Neo4j 约束创建失败（不影响核心功能，MERGE 仍可用）: {}", e.getMessage());
        }
    }

    /**
     * 判断 Neo4j 是否可用。
     */
    public boolean isAvailable() {
        return enabled && available && driver != null;
    }

    /**
     * 确保 User 节点存在（幂等 MERGE）。
     *
     * @param userId 用户 ID
     */
    public void upsertUserNode(Long userId) {
        if (userId == null) {
            return;
        }
        ensureDriver();
        if (!isAvailable()) {
            return;
        }
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            session.run(new Query(
                    "MERGE (u:User {userId: $userId})",
                    params));
            LOGGER.debug("Neo4j upsert User 节点成功, userId={}", userId);
        } catch (Exception e) {
            LOGGER.warn("Neo4j upsert User 节点失败,降级跳过, userId={}, 原因: {}", userId, e.getMessage());
        }
    }

    /**
     * 建立好友关系（FRIEND 边，幂等 MERGE）。
     *
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     */
    public void addFriendship(Long userId1, Long userId2) {
        if (userId1 == null || userId2 == null) {
            return;
        }
        ensureDriver();
        if (!isAvailable()) {
            return;
        }
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("userId1", userId1);
            params.put("userId2", userId2);
            session.run(new Query(
                    "MERGE (u:User {userId: $userId1}) "
                            + "MERGE (v:User {userId: $userId2}) "
                            + "MERGE (u)-[:FRIEND]->(v) "
                            + "MERGE (v)-[:FRIEND]->(u)",
                    params));
            LOGGER.debug("Neo4j 建立好友关系成功, {} <-> {}", userId1, userId2);
        } catch (Exception e) {
            LOGGER.warn("Neo4j 建立好友关系失败,降级跳过, userId1={}, userId2={}, 原因: {}",
                    userId1, userId2, e.getMessage());
        }
    }

    /**
     * 删除好友关系（双向 FRIEND 边）。
     *
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     */
    public void removeFriendship(Long userId1, Long userId2) {
        if (userId1 == null || userId2 == null) {
            return;
        }
        ensureDriver();
        if (!isAvailable()) {
            return;
        }
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("userId1", userId1);
            params.put("userId2", userId2);
            session.run(new Query(
                    "MATCH (u:User {userId: $userId1})-[r:FRIEND]->(v:User {userId: $userId2}) DELETE r "
                            + "WITH count(*) AS _ "
                            + "MATCH (v2:User {userId: $userId2})-[r2:FRIEND]->(u2:User {userId: $userId1}) DELETE r2",
                    params));
            LOGGER.debug("Neo4j 删除好友关系成功, {} <-> {}", userId1, userId2);
        } catch (Exception e) {
            LOGGER.warn("Neo4j 删除好友关系失败,降级跳过, userId1={}, userId2={}, 原因: {}",
                    userId1, userId2, e.getMessage());
        }
    }

    /**
     * 查询二度好友（朋友的朋友），排除已是一度好友的用户，按共同好友数降序排序。
     *
     * <p>Cypher 逻辑：
     * <pre>
     * MATCH (u:User {userId: $userId})-[:FRIEND]-()-[:FRIEND]-(reco:User)
     * WHERE reco.userId <> $userId
     *   AND NOT (u)-[:FRIEND]-(reco)
     * RETURN reco.userId AS friendId, COUNT(*) AS commonFriends
     * ORDER BY commonFriends DESC
     * LIMIT $limit
     * </pre>
     *
     * @param userId 当前用户 ID
     * @param limit  返回数量上限
     * @return 二度好友用户 ID 列表（按共同好友数降序），失败返回空列表
     */
    public List<Long> findSecondDegreeFriends(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return Collections.emptyList();
        }
        ensureDriver();
        if (!isAvailable()) {
            return Collections.emptyList();
        }
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            params.put("limit", limit);

            Result result = session.run(new Query(
                    "MATCH (u:User {userId: $userId})-[:FRIEND]-()-[:FRIEND]-(reco:User) "
                            + "WHERE reco.userId <> $userId "
                            + "  AND NOT (u)-[:FRIEND]-(reco) "
                            + "RETURN reco.userId AS friendId, COUNT(*) AS commonFriends "
                            + "ORDER BY commonFriends DESC "
                            + "LIMIT $limit",
                    params));

            List<Long> secondDegree = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                var friendIdValue = record.get("friendId");
                if (!friendIdValue.isNull()) {
                    secondDegree.add(friendIdValue.asLong());
                }
            }
            LOGGER.debug("Neo4j 二度好友查询完成, userId={}, 返回 {} 条", userId, secondDegree.size());
            return secondDegree;
        } catch (Exception e) {
            LOGGER.warn("Neo4j 二度好友查询失败,降级空召回, userId={}, 原因: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询二度好友并附带共同好友数（社交信号强度来源）。
     *
     * <p>与 {@link #findSecondDegreeFriends(Long, int)} 同查询逻辑，但额外返回每个二度好友的
     * 共同好友数，供 {@code RecommendScoringService} 将社交关系强度归一化为 0~1 的社交信号分，
     * 进而并入选混合评分公式（权重 w-social），让社交关系影响排序分值。</p>
     *
     * @param userId 当前用户 ID
     * @param limit  返回数量上限
     * @return Map&lt;二度好友用户ID, 共同好友数&gt;（按共同好友数降序），失败返回空 Map
     */
    public Map<Long, Integer> findSecondDegreeFriendsWithCommon(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return Collections.emptyMap();
        }
        ensureDriver();
        if (!isAvailable()) {
            return Collections.emptyMap();
        }
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            params.put("limit", limit);

            Result result = session.run(new Query(
                    "MATCH (u:User {userId: $userId})-[:FRIEND]-()-[:FRIEND]-(reco:User) "
                            + "WHERE reco.userId <> $userId "
                            + "  AND NOT (u)-[:FRIEND]-(reco) "
                            + "RETURN reco.userId AS friendId, COUNT(*) AS commonFriends "
                            + "ORDER BY commonFriends DESC "
                            + "LIMIT $limit",
                    params));

            Map<Long, Integer> secondDegree = new HashMap<>();
            while (result.hasNext()) {
                var record = result.next();
                var friendIdValue = record.get("friendId");
                var commonValue = record.get("commonFriends");
                if (!friendIdValue.isNull() && !commonValue.isNull()) {
                    secondDegree.put(friendIdValue.asLong(), commonValue.asInt());
                }
            }
            LOGGER.debug("Neo4j 二度好友(含共同好友)查询完成, userId={}, 返回 {} 条", userId, secondDegree.size());
            return secondDegree;
        } catch (Exception e) {
            LOGGER.warn("Neo4j 二度好友(含共同好友)查询失败,降级空召回, userId={}, 原因: {}", userId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 查询共同好友（两个用户之间的共同好友列表）。
     *
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     * @return 共同好友 ID 列表，失败返回空列表
     */
    public List<Long> findCommonFriends(Long userId1, Long userId2) {
        if (userId1 == null || userId2 == null) {
            return Collections.emptyList();
        }
        ensureDriver();
        if (!isAvailable()) {
            return Collections.emptyList();
        }
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("userId1", userId1);
            params.put("userId2", userId2);

            Result result = session.run(new Query(
                    "MATCH (u1:User {userId: $userId1})-[:FRIEND]-(common:User)-[:FRIEND]-(u2:User {userId: $userId2}) "
                            + "RETURN common.userId AS friendId",
                    params));

            List<Long> commonFriends = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                var friendIdValue = record.get("friendId");
                if (!friendIdValue.isNull()) {
                    commonFriends.add(friendIdValue.asLong());
                }
            }
            return commonFriends;
        } catch (Exception e) {
            LOGGER.warn("Neo4j 共同好友查询失败,降级空召回, userId1={}, userId2={}, 原因: {}",
                    userId1, userId2, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取用户的一度好友列表。
     *
     * @param userId 用户 ID
     * @return 好友 ID 列表，失败返回空列表
     */
    public List<Long> findDirectFriends(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        ensureDriver();
        if (!isAvailable()) {
            return Collections.emptyList();
        }
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);

            Result result = session.run(new Query(
                    "MATCH (u:User {userId: $userId})-[:FRIEND]-(f:User) "
                            + "RETURN f.userId AS friendId",
                    params));

            List<Long> friends = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                var friendIdValue = record.get("friendId");
                if (!friendIdValue.isNull()) {
                    friends.add(friendIdValue.asLong());
                }
            }
            return friends;
        } catch (Exception e) {
            LOGGER.warn("Neo4j 一度好友查询失败,降级空召回, userId={}, 原因: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 服务关闭时释放 Neo4j driver 资源。
     */
    @PreDestroy
    @Override
    public void close() {
        if (driver != null) {
            try {
                driver.close();
                LOGGER.info("Neo4j driver 已关闭");
            } catch (Exception e) {
                LOGGER.warn("Neo4j driver 关闭时异常", e);
            }
        }
    }
}
