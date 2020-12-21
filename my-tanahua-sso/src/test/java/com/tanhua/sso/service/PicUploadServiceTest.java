package com.tanhua.sso.service;

import com.tanhua.sso.vo.PicUploadResult;
import org.apache.http.entity.ContentType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class PicUploadServiceTest {


    @Autowired
    private PicUploadService picUploadService;

    /**
     * 测试图片上传到阿里OSS
     */
    @Test
    public void upload() {
        String filePath = "D:\\aaa.jpg";
        File file = new File(filePath);
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            MultipartFile multipartFile = null;
            multipartFile = new MockMultipartFile("copy" + file.getName(), file.getName(), ContentType.APPLICATION_OCTET_STREAM.toString(), fileInputStream);
            PicUploadResult upload = picUploadService.upload(multipartFile);
            System.out.println(upload);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}