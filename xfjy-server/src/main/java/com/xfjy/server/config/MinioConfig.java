package com.xfjy.server.config;

import io.minio.MinioClient;
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

    @Bean
    @ConditionalOnProperty(prefix = "minio", name = "endpoint")
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKeyId, accessKeySecret)
                .build();
    }

}
