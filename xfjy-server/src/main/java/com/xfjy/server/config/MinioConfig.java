package com.xfjy.server.config;

import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * MinIO 对象存储配置（替代原阿里云 OSS）。
 *
 * <p>配置项见 classpath:minio.properties，未配置 endpoint 时不创建客户端，
 * 上传服务会快速失败并提示，避免启动报错。</p>
 */
@Configuration
@PropertySource(value = "classpath:minio.properties", ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioConfig {

    /** MinIO 服务地址，如 http://192.168.20.133:9000 */
    private String endpoint;

    /** 访问密钥 ID */
    private String accessKeyId;

    /** 访问密钥 Secret */
    private String accessKeySecret;

    /** 存储桶名称 */
    private String bucketName;

    /** 公网访问 URL 前缀，如 http://192.168.20.133:9000/test-media/ */
    private String urlPrefix;

    /**
     * 部署环境（docker compose）只注入 MINIO_* 环境变量，而 Spring 的宽松绑定无法把
     * MINIO_ACCESS_KEY 映射到 accessKeyId（同理 SECRET_KEY/BUCKET/URL_PREFIX），
     * 且 minio.properties 已被 gitignore、不随 jar 发布，导致线上 minioClient 为 null、
     * 上传全部 500。这里统一在属性绑定完成后按「properties 优先、环境变量兜底」补齐。
     */
    @PostConstruct
    void applyEnvFallback() {
        this.endpoint = firstNonBlank(this.endpoint, System.getenv("MINIO_ENDPOINT"));
        this.accessKeyId = firstNonBlank(this.accessKeyId, System.getenv("MINIO_ACCESS_KEY"));
        this.accessKeySecret = firstNonBlank(this.accessKeySecret, System.getenv("MINIO_SECRET_KEY"));
        this.bucketName = firstNonBlank(this.bucketName, System.getenv("MINIO_BUCKET"));
        this.urlPrefix = firstNonBlank(this.urlPrefix, System.getenv("MINIO_URL_PREFIX"));
    }

    private static String firstNonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    @Bean
    @ConditionalOnProperty(prefix = "minio", name = "endpoint")
    public MinioClient minioClient() {
        // 凭证未配置（环境变量未注入）时不创建客户端，上传服务走快速失败路径
        if (accessKeyId == null || accessKeyId.isBlank()
                || accessKeySecret == null || accessKeySecret.isBlank()) {
            return null;
        }
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKeyId, accessKeySecret)
                .build();
    }

}
