package com.jelly.cinema.admin.controller;

import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.oss.service.OssService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * 文件上传控制器（使用腾讯云 COS）
 *
 * @author Jelly Cinema
 */
@Slf4j
@Tag(name = "文件上传")
@RestController
@RequestMapping("/admin/upload")
@RequiredArgsConstructor
public class UploadController {

    private final OssService ossService;

    // 允许的图片扩展名
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"
    );

    // 允许的视频扩展名
    private static final List<String> ALLOWED_VIDEO_EXTENSIONS = Arrays.asList(
            "mp4", "webm", "ogg", "mov", "avi", "mkv", "flv", "m3u8", "ts"
    );

    @Operation(summary = "上传图片")
    @PostMapping("/image")
    public R<String> uploadImage(@RequestParam("file") MultipartFile file) {
        // 验证文件
        String error = validateFile(file, ALLOWED_IMAGE_EXTENSIONS);
        if (error != null) {
            return R.fail(error);
        }

        // 上传到 COS
        String url = ossService.upload(file, "images");
        log.info("图片上传成功: {} -> {}", file.getOriginalFilename(), url);
        return R.ok(url);
    }

    @Operation(summary = "上传视频")
    @PostMapping("/video")
    public R<String> uploadVideo(@RequestParam("file") MultipartFile file) {
        // 验证文件
        String error = validateFile(file, ALLOWED_VIDEO_EXTENSIONS);
        if (error != null) {
            return R.fail(error);
        }

        // 上传到 COS
        String url = ossService.upload(file, "videos");
        log.info("视频上传成功: {} -> {}", file.getOriginalFilename(), url);
        return R.ok(url);
    }

    @Operation(summary = "上传封面图片")
    @PostMapping("/cover")
    public R<String> uploadCover(@RequestParam("file") MultipartFile file) {
        // 验证文件
        String error = validateFile(file, ALLOWED_IMAGE_EXTENSIONS);
        if (error != null) {
            return R.fail(error);
        }

        // 上传到 COS covers 目录
        String url = ossService.upload(file, "covers");
        log.info("封面上传成功: {} -> {}", file.getOriginalFilename(), url);
        return R.ok(url);
    }

    @Operation(summary = "通用文件上传")
    @PostMapping("/file")
    public R<String> uploadFile(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "folder", defaultValue = "files") String folder) {
        if (file == null || file.isEmpty()) {
            return R.fail("请选择要上传的文件");
        }

        // 上传到 COS
        String url = ossService.upload(file, folder);
        log.info("文件上传成功: {} -> {}", file.getOriginalFilename(), url);
        return R.ok(url);
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/delete")
    public R<Boolean> deleteFile(@RequestParam("url") String url) {
        boolean result = ossService.delete(url);
        if (result) {
            log.info("文件删除成功: {}", url);
            return R.ok(true);
        } else {
            return R.fail("文件删除失败");
        }
    }

    /**
     * 验证文件
     */
    private String validateFile(MultipartFile file, List<String> allowedExtensions) {
        if (file == null || file.isEmpty()) {
            return "请选择要上传的文件";
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return "文件名不能为空";
        }

        // 获取文件扩展名
        String extension = getFileExtension(originalFilename).toLowerCase();

        // 验证文件类型
        if (!allowedExtensions.contains(extension)) {
            return "不支持的文件类型: " + extension + ", 仅支持: " + String.join(", ", allowedExtensions);
        }

        return null; // 验证通过
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }
}
