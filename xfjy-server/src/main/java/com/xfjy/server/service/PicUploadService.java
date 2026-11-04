package com.xfjy.server.service;

import com.xfjy.server.config.MinioConfig;
import com.xfjy.server.vo.PicUploadResult;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PicUploadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PicUploadService.class);

    /** 图片大小上限：5MB（头像/照片墙场景无需更大，全局 multipart 30MB 兜底偏宽） */
    private static final long IMAGE_MAX_SIZE = 5 * 1024 * 1024L;
    /** 音频大小上限：10MB（聊天语音） */
    private static final long AUDIO_MAX_SIZE = 10 * 1024 * 1024L;

    // 允许上传的图片后缀（不含点，全小写精确匹配，防 shell.jsp.jpg 双扩展名绕过）
    private static final Set<String> IMAGE_EXT = Set.of("bmp", "jpg", "jpeg", "gif", "png");
    // 允许上传的音频后缀（不含点，全小写精确匹配）
    private static final Set<String> AUDIO_EXT = Set.of("mp3", "aac", "m4a", "wav", "amr", "mp4");

    // 图片魔术字节（文件头特征，防扩展名伪装上传非图片内容）
    private static final Map<String, byte[]> IMAGE_MAGIC = Map.of(
            "jpg",  new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "png",  new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "gif",  new byte[]{0x47, 0x49, 0x46, 0x38},
            "bmp",  new byte[]{0x42, 0x4D}
    );

    @Autowired(required = false)
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    public PicUploadResult upload(MultipartFile uploadFile) {
        return doUpload(uploadFile, IMAGE_EXT, "images", IMAGE_MAX_SIZE, true);
    }

    /**
     * 上传音频文件（聊天语音），目录 audio/yyyy/MM/dd
     */
    public PicUploadResult uploadAudio(MultipartFile uploadFile) {
        return doUpload(uploadFile, AUDIO_EXT, "audio", AUDIO_MAX_SIZE, false);
    }

    private PicUploadResult doUpload(MultipartFile uploadFile, Set<String> allowedExts,
                                     String dir, long maxSize, boolean checkMagic) {

        PicUploadResult fileUploadResult = new PicUploadResult();

        // MinIO 未配置（凭证环境变量未注入或 endpoint 未填）时快速失败
        if (this.minioClient == null) {
            LOGGER.error("MinIO 未配置，无法上传文件，请检查 MINIO_ACCESS_KEY/MINIO_SECRET_KEY 环境变量");
            fileUploadResult.setStatus("error");
            return fileUploadResult;
        }

        // 文件为空校验
        if (uploadFile == null || uploadFile.isEmpty()) {
            LOGGER.error("上传文件为空");
            fileUploadResult.setStatus("error");
            return fileUploadResult;
        }

        // 业务级大小校验（比全局 multipart 30MB 更严格）
        if (uploadFile.getSize() > maxSize) {
            LOGGER.error("上传文件超限: {} bytes > {} bytes, filename={}",
                    uploadFile.getSize(), maxSize, uploadFile.getOriginalFilename());
            fileUploadResult.setStatus("error");
            return fileUploadResult;
        }

        //格式校验（处理 originalFilename 为 null 的情况）
        String originalFilename = uploadFile.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            LOGGER.error("上传文件名为空，contentType={}", uploadFile.getContentType());
            fileUploadResult.setStatus("error");
            return fileUploadResult;
        }

        // 提取最后一个点后的后缀做精确匹配（防 shell.jsp.jpg 双扩展名绕过）
        String ext = StringUtils.substringAfterLast(originalFilename, ".").toLowerCase(Locale.ROOT);
        if (ext.isEmpty() || !allowedExts.contains(ext)) {
            LOGGER.error("不支持的文件格式: {}, 允许的后缀: {}", originalFilename, allowedExts);
            fileUploadResult.setStatus("error");
            return fileUploadResult;
        }

        // 图片魔术字节校验（防扩展名伪装：真实内容非图片则拒绝）
        if (checkMagic && !matchesMagicBytes(uploadFile, ext)) {
            LOGGER.error("文件头魔术字节与图片格式不符，疑似伪装上传: {}", originalFilename);
            fileUploadResult.setStatus("error");
            return fileUploadResult;
        }

        // 文件新路径
        String filePath = getFilePath(originalFilename, dir);
        LOGGER.info("开始上传文件: {} -> {}, size={} bytes", originalFilename, filePath, uploadFile.getSize());

        // 上传到 MinIO
        try (InputStream in = new ByteArrayInputStream(uploadFile.getBytes())) {
            // 确保桶存在
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioConfig.getBucketName()).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucketName()).build());
                LOGGER.info("MinIO 桶不存在，已自动创建: {}", minioConfig.getBucketName());
            }

            // 目录结构：images/2026/10/30/xxxx.jpg
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(filePath)
                    .stream(in, uploadFile.getSize(), -1)
                    .contentType(uploadFile.getContentType())
                    .build());
            LOGGER.info("文件上传成功: {}", filePath);
        } catch (Exception e) {
            LOGGER.error("文件上传失败: {}", originalFilename, e);
            fileUploadResult.setStatus("error");
            return fileUploadResult;
        }

        // 上传成功
        fileUploadResult.setStatus("done");
        fileUploadResult.setName(this.minioConfig.getUrlPrefix() + filePath);
        fileUploadResult.setUid(String.valueOf(System.currentTimeMillis()));

        return fileUploadResult;
    }

    /**
     * 校验文件头魔术字节是否与声明的图片格式一致
     */
    private boolean matchesMagicBytes(MultipartFile uploadFile, String ext) {
        byte[] expected = IMAGE_MAGIC.get(ext);
        if (expected == null) {
            return true;
        }
        byte[] header = new byte[expected.length];
        try (InputStream in = uploadFile.getInputStream()) {
            int read = in.read(header);
            if (read < expected.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if (header[i] != expected[i]) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("读取文件头失败: {}", uploadFile.getOriginalFilename(), e);
            return false;
        }
    }

    private String getFilePath(String sourceFileName, String dir) {
        LocalDate now = LocalDate.now();
        return dir + "/" + now.format(DateTimeFormatter.ofPattern("yyyy"))
                + "/" + now.format(DateTimeFormatter.ofPattern("MM")) + "/"
                + now.format(DateTimeFormatter.ofPattern("dd")) + "/" + System.currentTimeMillis() +
                RandomUtils.nextInt(100, 9999) + "." +
                StringUtils.substringAfterLast(sourceFileName, ".");
    }

}
