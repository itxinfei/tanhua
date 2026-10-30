package com.xfjy.server.controller;

import com.xfjy.server.service.PicUploadService;
import com.xfjy.server.vo.PicUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传
 *
 * <p>供头像、照片墙等单图上传场景复用，走 MinIO 存储。</p>
 */
@RequestMapping("upload")
@RestController
public class UploadController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadController.class);

    @Autowired
    private PicUploadService picUploadService;

    /**
     * 上传单张图片
     *
     * @param file 图片文件（支持 bmp/jpg/jpeg/gif/png）
     * @return PicUploadResult：status=done 时 name 为 MinIO 访问 URL
     */
    @PostMapping("image")
    public ResponseEntity<PicUploadResult> uploadImage(@RequestParam("file") MultipartFile file) {
        PicUploadResult result = this.picUploadService.upload(file);
        if (!"done".equals(result.getStatus())) {
            LOGGER.warn("图片上传失败: {}", file.getOriginalFilename());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 上传音频文件（聊天语音，支持 mp3/aac/m4a/wav/amr/mp4）
     *
     * @param file 音频文件
     * @return PicUploadResult：status=done 时 name 为 MinIO 访问 URL
     */
    @PostMapping("audio")
    public ResponseEntity<PicUploadResult> uploadAudio(@RequestParam("file") MultipartFile file) {
        PicUploadResult result = this.picUploadService.uploadAudio(file);
        if (!"done".equals(result.getStatus())) {
            LOGGER.warn("音频上传失败: {}", file.getOriginalFilename());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
        return ResponseEntity.ok(result);
    }
}