package com.xfjy.sso;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 一次性工具：向 192.168.20.130 导入 docs/mytanhua.sql（建库 xfjy + 建表 + 初始数据）
 * 内网开发虚拟机，凭证 xfjy/123456
 */
public class SqlImportTool {

    @Test
    public void importSql() throws Exception {
        String host = "192.168.20.130";
        int port = 3306;
        String user = "xfjy";
        String pass = "123456";
        String dbName = "xfjy";

        // 1. 建库
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                user, pass);
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + dbName + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            System.out.println("[OK] database created: " + dbName);
        }

        // 2. 读取 SQL 文件
        Path sqlFile = Path.of("..", "docs", "mytanhua.sql");
        String content = Files.readString(sqlFile, StandardCharsets.UTF_8);
        // 去掉 BOM
        if (content.startsWith("\uFEFF")) content = content.substring(1);

        // 3. 按分号分割逐条执行（跳过纯注释行）
        String[] statements = content.split(";\\s*\\r?\\n");
        int count = 0;
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&allowMultiQueries=true",
                user, pass);
             Statement st = conn.createStatement()) {
            for (String stmt : statements) {
                String trimmed = stmt.trim();
                if (trimmed.isEmpty()) continue;
                // 跳过整段都是注释的
                String stripped = trimmed.replaceAll("(?m)^\\s*--.*$", "").trim();
                if (stripped.isEmpty()) continue;
                st.execute(trimmed);
                count++;
            }
        }
        System.out.println("[OK] imported " + count + " statements from mytanhua.sql");
    }
}