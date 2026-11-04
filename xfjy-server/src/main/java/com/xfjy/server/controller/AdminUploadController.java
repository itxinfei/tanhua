package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.exception.BusinessException;
import com.xfjy.server.service.PicUploadService;
import com.xfjy.server.vo.PicUploadResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台通用文件上传。
 *
 * <p>C 端 {@code /upload/image} 走用户态 TokenInterceptor（排除 /admin/**），
 * 管理员 Sa-Token 无法调通，故为后台单独开放 /admin/upload/image，
 * 鉴权交给 AdminTokenInterceptor + {@code @RequireRole}，存储复用 PicUploadService。</p>
 */
@RestController
@RequireRole({"SUPER_ADMIN", "ADMIN"})
@RequestMapping("admin/upload")
public class AdminUploadController {

    @Autowired
    private PicUploadService picUploadService;

    /**
     * 上传单张图片（头像、照片墙等）。
     *
     * @param file 图片文件
     * @return PicUploadResult：status=done 时 name 为 MinIO 访问 URL
     */
    @PostMapping("image")
    public ResponseEntity<PicUploadResult> uploadImage(@RequestParam("file") MultipartFile file) {
        PicUploadResult result = this.picUploadService.upload(file);
        if (result == null || !"done".equals(result.getStatus())) {
            throw new BusinessException("图片上传失败，请重试");
        }
        return ResponseEntity.ok(result);
    }
}
