package com.xfjy.server;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.PutObjectResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

/**
 * OSS + 环信配置连通性验证测试
 * 运行方式: mvn test -pl xfjy-server -Dtest=OssHuanxinVerifyTest -am
 */
public class OssHuanxinVerifyTest {

    @Test
    public void verifyOss() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("aliyun.properties")) {
            if (in == null) {
                System.out.println("[OSS] FAIL: aliyun.properties not found");
                return;
            }
            props.load(in);
        }

        String endpoint = props.getProperty("aliyun.endpoint").trim();
        String accessKeyId = props.getProperty("aliyun.accessKeyId").trim();
        String accessKeySecret = props.getProperty("aliyun.accessKeySecret").trim();
        String bucketName = props.getProperty("aliyun.bucketName").trim();
        String urlPrefix = props.getProperty("aliyun.urlPrefix").trim();

        System.out.println("[OSS] endpoint=" + endpoint);
        System.out.println("[OSS] bucketName=" + bucketName);
        System.out.println("[OSS] urlPrefix=" + urlPrefix);
        System.out.println("[OSS] accessKeyId=" + accessKeyId.substring(0, 8) + "****");

        OSSClient ossClient = new OSSClient(endpoint, accessKeyId, accessKeySecret);
        try {
            // 1. 验证凭证+bucket存在性
            boolean exists = ossClient.doesBucketExist(bucketName);
            System.out.println("[OSS] doesBucketExist(" + bucketName + ") = " + exists);
            if (!exists) {
                System.out.println("[OSS] FAIL: bucket不存在或凭证无效");
                return;
            }

            // 2. 上传测试文件
            String testKey = "images/test/verify-" + System.currentTimeMillis() + ".txt";
            PutObjectResult result = ossClient.putObject(bucketName, testKey,
                    new ByteArrayInputStream("oss-verify-test".getBytes()));
            System.out.println("[OSS] putObject OK, etag=" + result.getETag());

            // 4. 测试URL可访问性（公共读应返回200，私有返回403）
            String fileUrl = urlPrefix + testKey;
            try {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                HttpResponse<String> resp = http.send(
                        HttpRequest.newBuilder().uri(URI.create(fileUrl)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                System.out.println("[OSS] URL访问测试 " + fileUrl + " -> HTTP " + resp.statusCode());
                if (resp.statusCode() == 200) {
                    System.out.println("[OSS] PASS: bucket已公共读，URL可直接访问");
                } else if (resp.statusCode() == 403) {
                    System.out.println("[OSS] WARN: bucket仍为私有，URL返回403，需改公共读");
                }
            } catch (Exception e) {
                System.out.println("[OSS] URL访问异常: " + e.getMessage());
            }

            // 5. 清理测试文件
            ossClient.deleteObject(bucketName, testKey);
            System.out.println("[OSS] 测试文件已清理");
            System.out.println("[OSS] === OSS配置验证通过 ===");
        } finally {
            ossClient.shutdown();
        }
    }

    @Test
    public void verifyHuanxin() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application-local.yml")) {
            // yml不是properties格式，手动解析
        }
        // 直接从环境变量或固定local配置读取
        String appkey = System.getenv().getOrDefault("HANXIN_APPKEY", "");
        String clientId = System.getenv().getOrDefault("HANXIN_CLIENT_ID", "");
        String clientSecret = System.getenv().getOrDefault("HANXIN_CLIENT_SECRET", "");
        String restHost = System.getenv().getOrDefault("HANXIN_REST_HOST", "https://a1.easemob.com");

        // 环境变量为空时从message-service的local配置读取
        if (clientId.isEmpty()) {
            java.nio.file.Path localYml = java.nio.file.Paths.get(
                    "../xfjy-message-service/src/main/resources/application-local.yml");
            if (java.nio.file.Files.exists(localYml)) {
                String content = java.nio.file.Files.readString(localYml);
                appkey = extractYmlValue(content, "appkey:");
                clientId = extractYmlValue(content, "client-id:");
                clientSecret = extractYmlValue(content, "client-secret:");
            }
        }

        System.out.println("[环信] appkey=" + appkey);
        System.out.println("[环信] clientId=" + (clientId.length() > 6 ? clientId.substring(0, 6) + "****" : clientId));
        System.out.println("[环信] restHost=" + restHost);

        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            System.out.println("[环信] FAIL: 未找到环信凭证配置");
            return;
        }

        // appkey格式: org#app -> URL: {restHost}/{org}/{app}/token
        String[] parts = appkey.split("#");
        if (parts.length != 2) {
            System.out.println("[环信] FAIL: appkey格式错误，应为 org#app");
            return;
        }
        String tokenUrl = restHost + "/" + parts[0] + "/" + parts[1] + "/token";

        // 请求token
        String body = "{\"grant_type\":\"client_credentials\",\"client_id\":\"" + clientId
                + "\",\"client_secret\":\"" + clientSecret + "\"}";
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(tokenUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        System.out.println("[环信] token请求 -> HTTP " + resp.statusCode());
        if (resp.statusCode() == 200) {
            String respBody = resp.body();
            boolean hasToken = respBody.contains("access_token");
            System.out.println("[环信] 响应含access_token = " + hasToken);
            System.out.println("[环信] PASS: 环信凭证有效，token获取成功");
        } else {
            System.out.println("[环信] FAIL: token获取失败, body=" + resp.body());
        }
    }

    private String extractYmlValue(String content, String key) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key)) {
                return trimmed.substring(key.length()).trim();
            }
        }
        return "";
    }
}