package com.jelly.cinema.admin.controller;

import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件上传控制器
 *
 * @author Jelly Cinema
 */
@Slf4j
@Tag(name = "文件上传")
@RestController
@RequestMapping("/admin/upload")
@RequiredArgsConstructor
public class UploadController {

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @Value("${upload.url-prefix:/uploads}")
    private String urlPrefix;

    @Operation(summary = "上传图片")
    @PostMapping("/image")
    public R<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return uploadFile(file, "images", new String[]{"jpg", "jpeg", "png", "gif", "webp", "bmp"});
    }

    @Operation(summary = "上传视频")
    @PostMapping("/video")
    public R<String> uploadVideo(@RequestParam("file") MultipartFile file) {
        return uploadFile(file, "videos", new String[]{"mp4", "webm", "ogg", "mov", "avi", "mkv", "flv"});
    }

    private R<String> uploadFile(MultipartFile file, String subDir, String[] allowedExtensions) {
        if (file == null || file.isEmpty()) {
            return R.fail("请选择要上传的文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return R.fail("文件名不能为空");
        }

        // 获取文件扩展名
        String extension = getFileExtension(originalFilename).toLowerCase();
        
        // 验证文件类型
        boolean isAllowed = false;
        for (String ext : allowedExtensions) {
            if (ext.equals(extension)) {
                isAllowed = true;
                break;
            }
        }
        if (!isAllowed) {
            return R.fail("不支持的文件类型: " + extension);
        }

        try {
            // 生成新文件名
            String newFilename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            
            // 创建目录
            Path dirPath = Paths.get(uploadPath, subDir);
            Files.createDirectories(dirPath);
            
            // 保存文件
            Path filePath = dirPath.resolve(newFilename);
            file.transferTo(filePath.toFile());
            
            // 返回访问URL
            String fileUrl = urlPrefix + "/" + subDir + "/" + newFilename;
            log.info("文件上传成功: {} -> {}", originalFilename, fileUrl);
            
            return R.ok(fileUrl);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return R.fail("文件上传失败: " + e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }
}
