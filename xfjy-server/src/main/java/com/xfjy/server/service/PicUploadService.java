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
import java.io.InputStream;

@Service
public class PicUploadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PicUploadService.class);

    // 允许上传的格式
    private static final String[] IMAGE_TYPE = new String[]{".bmp", ".jpg",
            ".jpeg", ".gif", ".png"};

    // 允许上传的音频格式（聊天语音）
    private static final String[] AUDIO_TYPE = new String[]{".mp3", ".aac",
            ".m4a", ".wav", ".amr", ".mp4"};

    @Autowired(required = false)
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    public PicUploadResult upload(MultipartFile uploadFile) {
        return doUpload(uploadFile, IMAGE_TYPE, "images");
    }

    /**
     * 上传音频文件（聊天语音），目录 audio/yyyy/MM/dd
     */
    public PicUploadResult uploadAudio(MultipartFile uploadFile) {
        return doUpload(uploadFile, AUDIO_TYPE, "audio");
    }

    private PicUploadResult doUpload(MultipartFile uploadFile, String[] allowedTypes, String dir) {

        PicUploadResult fileUploadResult = new PicUploadResult();

        // MinIO 未配置（minio.properties 缺失或 endpoint 未填）时快速失败
        if (this.minioClient == null) {
            LOGGER.error("MinIO 未配置，无法上传文件，请检查 minio.properties");
            fileUploadResult.setStatus("error");
            return fileUploadResult;
        }

        //格式校验
        boolean isLegal = false;

        for (String type : allowedTypes) {
            if (StringUtils.endsWithIgnoreCase(uploadFile.getOriginalFilename(),
                    type)) {
                isLegal = true;
                break;
            }
        }

        if (!isLegal) {
            fileUploadResult.setStatus("error");
            return fileUploadResult;
        }

        // 文件新路径
        String fileName = uploadFile.getOriginalFilename();
        String filePath = getFilePath(fileName, dir);

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
        } catch (Exception e) {
            LOGGER.error("文件上传失败", e);
            //上传失败
            fileUploadResult.setStatus("error");
            return fileUploadResult;
        }

        // 上传成功
        fileUploadResult.setStatus("done");
        fileUploadResult.setName(this.minioConfig.getUrlPrefix() + filePath);
        fileUploadResult.setUid(String.valueOf(System.currentTimeMillis()));

        return fileUploadResult;
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
