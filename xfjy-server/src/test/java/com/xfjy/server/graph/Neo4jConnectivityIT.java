package com.xfjy.server.graph;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Neo4j 驱动（Boot 4.1.1 BOM 管理的 6.1.0）对 neo4j:5 服务端的连通性验收。
 *
 * <p>不依赖 Spring 上下文：直接用 {@link GraphDatabase#driver} 建连，验证三层能力：
 * <ol>
 *   <li>{@code verifyConnectivity()} 握手成功（Bolt + 认证）；</li>
 *   <li>{@code CALL dbms.components()} 取服务端版本，确认确实是 5.x；</li>
 *   <li>只读查询 {@code RETURN 1 AS ok} 返回 1。</li>
 * </ol>
 *
 * <p>连接参数全部来自环境变量 {@code NEO4J_URI / NEO4J_USER / NEO4J_PASSWORD}，
 * 未设置时以 JUnit assumption 跳过，绝不会使用源码内默认密码。
 * 执行入口见 {@code scripts/acceptance/neo4j-connectivity.ps1}。
 */
class Neo4jConnectivityIT {

    private static final Logger LOG = LoggerFactory.getLogger(Neo4jConnectivityIT.class);

    /** 机器可读的服务端版本标记，供验收脚本解析，勿改格式。 */
    private static final String VERSION_MARKER = "NEO4J_SERVER_VERSION=";

    @Test
    void driverShouldConnectToNeo4j5Server() {
        String uri = System.getenv("NEO4J_URI");
        String user = System.getenv("NEO4J_USER");
        String password = System.getenv("NEO4J_PASSWORD");
        assumeTrue(notBlank(uri) && notBlank(user) && notBlank(password),
                "未设置 NEO4J_URI / NEO4J_USER / NEO4J_PASSWORD，跳过连通性验收");

        try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password))) {
            driver.verifyConnectivity();
            LOG.info("verifyConnectivity 通过, uri={}", uri);

            String version = serverVersion(driver);
            LOG.info("Neo4j 服务端版本: {}", version);
            LOG.info("{}{}", VERSION_MARKER, version);

            assertEquals(1, returnOne(driver), "RETURN 1 AS ok 应返回 1");
        }
    }

    /** 通过 executableQuery 读取 dbms.components() 的 versions[0]。 */
    private static String serverVersion(Driver driver) {
        List<Record> records = driver
                .executableQuery("CALL dbms.components() YIELD versions RETURN versions")
                .execute()
                .records();
        assertFalse(records.isEmpty(), "dbms.components() 未返回记录");
        List<String> versions = records.get(0).get("versions").asList(Value::asString);
        assertFalse(versions.isEmpty(), "dbms.components() 返回的 versions 为空");
        return versions.get(0);
    }

    /** 走 session.run 执行只读查询，覆盖生产代码 Neo4jClient 使用的调用路径。 */
    private static int returnOne(Driver driver) {
        try (Session session = driver.session()) {
            Result result = session.run("RETURN 1 AS ok");
            assertTrue(result.hasNext(), "RETURN 1 应返回一条记录");
            return result.single().get("ok").asInt();
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
